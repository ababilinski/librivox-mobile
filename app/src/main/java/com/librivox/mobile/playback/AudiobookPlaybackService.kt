package com.librivox.mobile.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.cast.CastPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.librivox.mobile.AudiobookApplication
import com.librivox.mobile.MainActivity
import com.librivox.mobile.R
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.ProgressStore
import com.librivox.mobile.model.SleepTimerState
import com.librivox.mobile.model.chapterIndexOrZero
import com.librivox.mobile.model.parseMediaId
import com.librivox.mobile.model.toMediaItems
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.SessionState
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionTransferCallback
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs

class AudiobookPlaybackService : MediaSessionService() {
    private lateinit var localPlayer: ExoPlayer
    private lateinit var remoteCastPlayer: RemoteCastPlayer
    private lateinit var player: Player
    private var mediaSession: MediaSession? = null
    private lateinit var progressStore: ProgressStore
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var playbackSettingsStore: PlaybackSettingsStore
    private lateinit var playbackBus: PlaybackBus
    private lateinit var sleepTimerController: SleepTimerController
    private lateinit var localCastHttpServer: LocalCastHttpServer
    private lateinit var castMediaItemConverter: AudiobookCastMediaItemConverter
    private var castContext: CastContext? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressSaveJob: Job? = null
    private var notificationRefreshJob: Job? = null
    private var castSessionCallbackJob: Job? = null
    private var autoDownloadGateJob: Job? = null
    private var autoDownloadGateBookId: String? = null
    private val autoDownloadReadyBookIds = mutableSetOf<String>()
    private var currentBookIdForProgress: String = AudioBookLibrary.defaultBook.id
    private var currentChapterIdForProgress: String =
        AudioBookLibrary.defaultBook.chapters.first().id
    private var playbackSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var notificationControlsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastNotificationCommandLayoutKey: NotificationCommandLayoutKey? = null
    private var suppressNextSpeedPersistence = false

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as AudiobookApplication
        progressStore = app.progressStore
        libraryRepository = app.libraryRepository
        playbackSettingsStore = app.playbackSettingsStore
        playbackBus = app.playbackBus
        localCastHttpServer = LocalCastHttpServer(this)
        castMediaItemConverter = AudiobookCastMediaItemConverter(
            localCastUrlProvider = localCastHttpServer::prepareUrl,
            coverUrlProvider = { mediaItem ->
                localCastHttpServer.prepareCoverUrl(mediaItem.mediaMetadata.artworkUri, mediaItem)
            },
            preloadTimeSeconds = { playbackSettingsStore.castPreloadTime.seconds.toDouble() },
        )
        localPlayer = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
            .apply {
                applyAudioContentType(playbackSettingsStore.audioContentType)
                setHandleAudioBecomingNoisy(true)
            }
        remoteCastPlayer = RemoteCastPlayer.Builder(this)
            .setMediaItemConverter(castMediaItemConverter)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        player = CastPlayer.Builder(this)
            .setLocalPlayer(localPlayer)
            .setRemotePlayer(remoteCastPlayer)
            .setTransferCallback(::transferCastPlaybackState)
            .build()
            .apply {
                addListener(playerListener)
            }
        sleepTimerController = SleepTimerController(
            scope = serviceScope,
            player = player,
            onStateChanged = { state -> playbackBus.updateSleepTimer(state) },
        )
        playbackSettingsListener =
            playbackSettingsStore.registerAudioContentTypeListener { contentType ->
                serviceScope.launch { localPlayer.applyAudioContentType(contentType) }
            }
        notificationControlsListener =
            playbackSettingsStore.registerNotificationControlsListener {
                serviceScope.launch { applyNotificationCommandLayout() }
            }

        setMediaNotificationProvider(ThemedMediaNotificationProvider(this))
        scheduleCastSessionCallbacks()

        val notificationButtons = notificationCommandButtons()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .setSessionActivity(sessionActivityPendingIntent())
            .setCustomLayout(notificationButtons)
            .setMediaButtonPreferences(notificationButtons)
            .build()
        Log.i(TAG, "Audiobook playback service created.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    private fun sessionActivityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveCurrentProgress("service destroy")
        progressSaveJob?.cancel()
        notificationRefreshJob?.cancel()
        castSessionCallbackJob?.cancel()
        autoDownloadGateJob?.cancel()
        playbackSettingsListener?.let(playbackSettingsStore::unregisterListener)
        playbackSettingsListener = null
        notificationControlsListener?.let(playbackSettingsStore::unregisterListener)
        notificationControlsListener = null
        unregisterCastSessionCallbacks()
        if (::localCastHttpServer.isInitialized) {
            localCastHttpServer.stop()
        }
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) {
            player.release()
        }
        if (::localPlayer.isInitialized) {
            localPlayer.release()
        }
        serviceScope.cancel()
        Log.i(TAG, "Audiobook playback service destroyed.")
        super.onDestroy()
    }

    private fun restoredQueueSnapshot(): RestoredQueueSnapshot {
        return runBlocking(Dispatchers.IO) {
            restoredQueueSnapshotAsync()
        }
    }

    private suspend fun restoredQueueSnapshotAsync(): RestoredQueueSnapshot =
        withContext(Dispatchers.IO) {
            val libraryState = libraryRepository.snapshot()
            val lastBook = libraryState.bookById(progressStore.lastBookId())
                ?: AudioBookLibrary.defaultBook
            val startChapterId = progressStore.lastChapterId(lastBook.id)
                ?: lastBook.chapters.firstOrNull()?.id
            val requestedStartIndex = lastBook.chapterIndexOrZero(startChapterId)
            val mediaItems = lastBook.safeMediaItems()
            val startIndex = if (mediaItems.isEmpty()) {
                0
            } else {
                requestedStartIndex.coerceIn(0, mediaItems.lastIndex)
            }
            val startPositionMs = startChapterId?.let {
                progressStore.savedPositionMs(lastBook.id, it)
            } ?: 0L
            RestoredQueueSnapshot(
                book = lastBook,
                mediaItems = mediaItems,
                startChapterId = startChapterId,
                startIndex = startIndex,
                startPositionMs = startPositionMs,
            )
        }

    private fun applyPersistedSpeed(bookId: String) {
        val speed = playbackSettingsStore.speedFor(bookId)
        applySpeedInternal(speed)
    }

    private fun applySpeedInternal(speed: Float) {
        val clamped = PlaybackSpeed.clamp(speed).value
        player.playbackParameters = PlaybackParameters(clamped, 1f)
        playbackBus.updateSpeed(PlaybackSpeed.clamp(clamped))
        Log.i(TAG, "Applied playback speed=$clamped")
    }

    private fun applyTemporarySpeedInternal(speed: Float) {
        val clamped = PlaybackSpeed.clamp(speed).value
        if (abs(player.playbackParameters.speed - clamped) > SPEED_EPSILON) {
            suppressNextSpeedPersistence = true
        }
        applySpeedInternal(clamped)
        applyNotificationCommandLayout()
    }

    private fun adjustSpeedInternal(delta: Float) {
        val nextSpeed = PlaybackSpeed.clamp(player.playbackParameters.speed + delta)
        applySpeedInternal(nextSpeed.value)
        playbackSettingsStore.saveSpeed(currentBookIdForProgress, nextSpeed.value)
        applyNotificationCommandLayout()
    }

    private fun cycleSpeedInternal() {
        val nextSpeed = nextNotificationSpeed(PlaybackSpeed.clamp(player.playbackParameters.speed))
        applySpeedInternal(nextSpeed.value)
        playbackSettingsStore.saveSpeed(currentBookIdForProgress, nextSpeed.value)
        applyNotificationCommandLayout()
    }

    private fun AudioBook.safeMediaItems(): List<MediaItem> =
        toMediaItems(this@AudiobookPlaybackService).ifEmpty {
            AudioBookLibrary.defaultBook.toMediaItems(this@AudiobookPlaybackService)
        }

    /**
     * Enqueue auto-download work only after playback has reached READY and the
     * listener has stayed with the book for the minimum listening window.
     * Manual downloads keep using DownloadManager directly and remain immediate.
     */
    private fun autoDownloadCurrentChapter(bookId: String, chapterId: String) {
        if (bookId.isBlank() || chapterId.isBlank()) {
            cancelAutoDownloadGate()
            return
        }
        val autoMode = playbackSettingsStore.autoDownloadMode
        if (!autoMode.enabled || !isAutoDownloadPlaybackReady(bookId)) {
            cancelAutoDownloadGate()
            return
        }

        if (bookId in autoDownloadReadyBookIds) {
            serviceScope.launch {
                autoDownloadChaptersFrom(bookId, chapterId, autoMode)
            }
            return
        }

        if (autoDownloadGateBookId == bookId && autoDownloadGateJob?.isActive == true) {
            return
        }

        autoDownloadGateJob?.cancel()
        autoDownloadGateBookId = bookId
        autoDownloadGateJob = serviceScope.launch {
            try {
                delay(AUTO_DOWNLOAD_MIN_LISTEN_MS)
                if (!isAutoDownloadPlaybackReady(bookId)) return@launch
                val latestAutoMode = playbackSettingsStore.autoDownloadMode
                if (!latestAutoMode.enabled) return@launch
                val latestChapterId = currentChapterIdForProgress
                    .takeIf { currentBookIdForProgress == bookId }
                    ?: chapterId
                autoDownloadReadyBookIds += bookId
                autoDownloadChaptersFrom(bookId, latestChapterId, latestAutoMode)
            } finally {
                if (autoDownloadGateBookId == bookId) {
                    autoDownloadGateBookId = null
                }
            }
        }
    }

    private fun cancelAutoDownloadGate() {
        autoDownloadGateJob?.cancel()
        autoDownloadGateJob = null
        autoDownloadGateBookId = null
    }

    private fun isAutoDownloadPlaybackReady(bookId: String): Boolean =
        currentBookIdForProgress == bookId &&
            player.isPlaying &&
            player.playbackState == Player.STATE_READY &&
            player.mediaItemCount > 0

    private suspend fun autoDownloadChaptersFrom(
        bookId: String,
        chapterId: String,
        autoMode: AutoDownloadMode,
    ) {
        val app = applicationContext as? com.librivox.mobile.AudiobookApplication ?: return
        val library = app.libraryRepository.snapshot()
        val book = library.bookById(bookId) ?: return
        val currentIndex = book.chapters.indexOfFirst { it.id == chapterId }
            .takeIf { it >= 0 } ?: return
        book.chapters
            .drop(currentIndex)
            .take(autoMode.lookAheadCount)
            .forEach { chapter ->
                if (chapter.localFileName != null) return@forEach
                if (chapter.assetFileName != null) return@forEach
                if (chapter.listenUrl.isNullOrBlank()) return@forEach
                if (chapter.downloadState == DownloadState.Downloading ||
                    chapter.downloadState == DownloadState.Queued
                ) return@forEach
                Log.i(
                    TAG,
                    "Auto-downloading chapter book=$bookId chapter=${chapter.id} " +
                        "mode=${autoMode.preferenceValue}",
                )
                runCatching { app.downloadManager.downloadChapter(book, chapter) }
            }
    }

    private fun ExoPlayer.applyAudioContentType(contentType: PlaybackAudioContentType) {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(contentType.media3ContentType)
                .build(),
            true,
        )
        Log.i(
            TAG,
            "Applied audio attributes. usage=USAGE_MEDIA contentType=${contentType.label}",
        )
    }

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "isPlaying=$isPlaying state=${player.playbackStateName()}")
                if (isPlaying) {
                    startProgressSaver()
                    autoDownloadCurrentChapter(currentBookIdForProgress, currentChapterIdForProgress)
                } else {
                    saveCurrentProgress("isPlaying=false")
                    cancelAutoDownloadGate()
                    stopProgressSaver()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val ids = parseMediaId(mediaItem?.mediaId)
                val previousBook = currentBookIdForProgress
                if (ids != null) {
                    currentBookIdForProgress = ids.bookId
                    currentChapterIdForProgress = ids.chapterId
                    progressStore.saveLastBook(
                        bookId = ids.bookId,
                        chapterId = ids.chapterId,
                        chapterIndex = player.currentMediaItemIndex,
                    )
                    if (ids.bookId != previousBook) {
                        applyPersistedSpeed(ids.bookId)
                    }
                    autoDownloadCurrentChapter(ids.bookId, ids.chapterId)
                }
                applyNotificationCommandLayout()
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    sleepTimerController.onChapterTransition()
                }
                Log.i(TAG, "Media item transition. ids=$ids reason=$reason")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val oldIds = parseMediaId(oldPosition.mediaItem?.mediaId)
                val oldBookId = oldIds?.bookId ?: currentBookIdForProgress
                val oldChapterId = oldIds?.chapterId ?: currentChapterIdForProgress
                saveProgressSnapshot(
                    bookId = oldBookId,
                    chapterId = oldChapterId,
                    chapterIndex = oldPosition.mediaItemIndex,
                    positionMs = oldPosition.positionMs,
                    reason = "old position on discontinuity=$reason",
                )

                val newIds = parseMediaId(newPosition.mediaItem?.mediaId)
                val previousBook = currentBookIdForProgress
                val newBookId = newIds?.bookId ?: currentBookIdForProgress
                val newChapterId = newIds?.chapterId ?: currentChapterIdForProgress
                if (newIds != null) {
                    currentBookIdForProgress = newIds.bookId
                    currentChapterIdForProgress = newIds.chapterId
                    progressStore.saveLastBook(
                        bookId = newIds.bookId,
                        chapterId = newIds.chapterId,
                        chapterIndex = newPosition.mediaItemIndex,
                    )
                    if (newIds.bookId != previousBook) {
                        applyPersistedSpeed(newIds.bookId)
                    }
                    autoDownloadCurrentChapter(newIds.bookId, newIds.chapterId)
                }
                saveProgressSnapshot(
                    bookId = newBookId,
                    chapterId = newChapterId,
                    chapterIndex = newPosition.mediaItemIndex,
                    positionMs = newPosition.positionMs,
                    reason = "new position on discontinuity=$reason",
                )
                requestNotificationUpdate()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.i(
                    TAG,
                    "playWhenReady=$playWhenReady reason=$reason " +
                        "state=${player.playbackStateName()}",
                )
                if (!playWhenReady) {
                    saveCurrentProgress("playWhenReady=false")
                    cancelAutoDownloadGate()
                }
                requestNotificationUpdate()
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                Log.i(TAG, "isLoading=$isLoading state=${player.playbackStateName()}")
                if (!isLoading) {
                    saveCurrentProgress("isLoading=false")
                }
                requestNotificationUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.i(TAG, "Playback state changed. state=${player.playbackStateName()}")
                when (playbackState) {
                    Player.STATE_BUFFERING,
                    Player.STATE_READY,
                    Player.STATE_ENDED,
                    Player.STATE_IDLE -> saveCurrentProgress("state=${player.playbackStateName()}")
                }
                autoDownloadCurrentChapter(currentBookIdForProgress, currentChapterIdForProgress)
                requestNotificationUpdate()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackBus.updateSpeed(PlaybackSpeed.clamp(playbackParameters.speed))
                if (suppressNextSpeedPersistence) {
                    suppressNextSpeedPersistence = false
                } else {
                    playbackSettingsStore.saveSpeed(currentBookIdForProgress, playbackParameters.speed)
                }
                applyNotificationCommandLayout()
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                Log.i(
                    TAG,
                    "Playback suppression changed. reason=" +
                        playbackSuppressionReason.suppressionReasonName(),
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                saveCurrentProgress("player error")
                Log.e(TAG, "Player error. code=${error.errorCodeName}", error)
            }
        }

    private val castSessionManagerListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                Log.i(TAG, "Cast session starting. device=${session.castDevice?.friendlyName}")
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                Log.i(
                    TAG,
                    "Cast session started. sessionId=$sessionId device=${session.castDevice?.friendlyName}",
                )
                scheduleRemoteCastLoadIfNeeded(session, "session started")
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session start failed. error=$error")
                localCastHttpServer.stop()
            }

            override fun onSessionEnding(session: CastSession) {
                saveCurrentProgress("cast session ending")
                Log.i(TAG, "Cast session ending. device=${session.castDevice?.friendlyName}")
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                saveCurrentProgress("cast session ended")
                Log.i(TAG, "Cast session ended. error=$error")
                localCastHttpServer.stop()
            }

            override fun onSessionResuming(session: CastSession, sessionId: String) {
                Log.i(TAG, "Cast session resuming. sessionId=$sessionId")
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                Log.i(TAG, "Cast session resumed. wasSuspended=$wasSuspended")
                scheduleRemoteCastLoadIfNeeded(session, "session resumed")
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                Log.w(TAG, "Cast session resume failed. error=$error")
                localCastHttpServer.stop()
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                Log.i(TAG, "Cast session suspended. reason=$reason")
                localCastHttpServer.stop()
            }
        }

    private val castSessionTransferCallback =
        object : SessionTransferCallback() {
            override fun onTransferring(transferType: Int) {
                saveCurrentProgress("cast transfer starting")
                Log.i(TAG, "Cast session transfer starting. transferType=$transferType")
            }

            override fun onTransferred(transferType: Int, sessionState: SessionState) {
                Log.i(TAG, "Cast session transferred. transferType=$transferType")
                if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) {
                    saveCurrentProgress("cast transferred to local")
                    localCastHttpServer.stop()
                }
            }

            override fun onTransferFailed(transferType: Int, reason: Int) {
                Log.w(TAG, "Cast session transfer failed. transferType=$transferType reason=$reason")
                saveCurrentProgress("cast transfer failed")
            }
        }

    private fun registerCastSessionCallbacks() {
        CastContext.getSharedInstance(this, ContextCompat.getMainExecutor(this))
            .addOnSuccessListener { context ->
                castContext = context
                context.sessionManager.addSessionManagerListener(
                    castSessionManagerListener,
                    CastSession::class.java,
                )
                context.addSessionTransferCallback(castSessionTransferCallback)
                Log.i(TAG, "Registered Cast session callbacks.")
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Unable to register Cast session callbacks.", exception)
            }
    }

    private fun scheduleCastSessionCallbacks() {
        castSessionCallbackJob?.cancel()
        castSessionCallbackJob = serviceScope.launch {
            delay(CAST_CALLBACK_REGISTRATION_DELAY_MS)
            registerCastSessionCallbacks()
        }
    }

    private fun unregisterCastSessionCallbacks() {
        castContext?.let { context ->
            context.sessionManager.removeSessionManagerListener(
                castSessionManagerListener,
                CastSession::class.java,
            )
            context.removeSessionTransferCallback(castSessionTransferCallback)
        }
        castContext = null
    }

    private fun scheduleRemoteCastLoadIfNeeded(session: CastSession, reason: String) {
        serviceScope.launch {
            delay(CAST_LOAD_SETTLE_DELAY_MS)
            val remoteClient = session.remoteMediaClient ?: run {
                Log.w(TAG, "Remote Cast load skipped. reason=$reason remoteClient=null")
                return@launch
            }
            if (remoteClient.hasMediaSession() || remoteClient.mediaInfo != null) {
                Log.i(TAG, "Remote Cast already has media. reason=$reason")
                return@launch
            }
            val itemCount = player.mediaItemCount
            if (itemCount <= 0) {
                Log.w(TAG, "Remote Cast load skipped. reason=$reason mediaItemCount=0")
                return@launch
            }
            val currentIndex = player.currentMediaItemIndex.coerceIn(0, itemCount - 1)
            val queueItems = mutableListOf<MediaQueueItem>()
            for (idx in 0 until itemCount) {
                val item = player.getMediaItemAt(idx)
                val queueItem = runCatching { castMediaItemConverter.toMediaQueueItem(item) }
                    .onFailure { Log.w(TAG, "Cast queue item conversion failed at index=$idx", it) }
                    .getOrNull()
                if (queueItem != null) {
                    queueItems.add(queueItem)
                }
            }
            if (queueItems.isEmpty()) {
                Log.w(TAG, "Remote Cast load skipped. reason=$reason emptyConvertedQueue")
                return@launch
            }
            val safeStartIndex = currentIndex.coerceIn(0, queueItems.size - 1)
            val itemsArray = queueItems.toTypedArray()
            Log.i(
                TAG,
                "Loading audiobook queue on Cast receiver. reason=$reason " +
                    "items=${itemsArray.size} startIndex=$safeStartIndex positionMs=${player.currentPosition}",
            )
            remoteClient.queueLoad(
                itemsArray,
                safeStartIndex,
                com.google.android.gms.cast.MediaStatus.REPEAT_MODE_REPEAT_OFF,
                player.currentPosition.coerceAtLeast(0L),
                null,
            ).setResultCallback { result ->
                if (result.status.isSuccess) {
                    Log.i(TAG, "Remote Cast queueLoad succeeded. reason=$reason")
                } else {
                    Log.w(
                        TAG,
                        "Remote Cast queueLoad failed. reason=$reason " +
                            "status=${result.status.statusCode} ${result.status.statusMessage}",
                    )
                }
            }
        }
    }

    private fun transferCastPlaybackState(fromPlayer: Player, toPlayer: Player) {
        if (::remoteCastPlayer.isInitialized && fromPlayer === remoteCastPlayer && toPlayer === localPlayer) {
            restoreLocalPlaybackFromRemote(fromPlayer)
            return
        }
        PlayerTransferState.fromPlayer(fromPlayer).setToPlayer(toPlayer)
    }

    private fun restoreLocalPlaybackFromRemote(remotePlayer: Player) {
        val restored = restoredQueueSnapshot()
        val remotePositionMs = remotePlayer.currentPosition.coerceAtLeast(0L)
        val currentMediaId = remotePlayer.currentMediaItem?.mediaId
        val startIndexFromMediaId = restored.mediaItems.indexOfFirst { it.mediaId == currentMediaId }
        val startIndex = if (startIndexFromMediaId >= 0) {
            startIndexFromMediaId
        } else {
            remotePlayer.currentMediaItemIndex
                .takeIf { it in restored.mediaItems.indices }
                ?: restored.startIndex
        }
        val positionMs = remotePositionMs.takeIf { it > 0L } ?: restored.startPositionMs
        val safeItems = restored.mediaItems.filter { it.localConfiguration?.uri != null }
        if (safeItems.isEmpty()) {
            Log.w(TAG, "Remote-to-local Cast restore skipped: no playable local media items.")
            return
        }

        currentBookIdForProgress = restored.book.id
        currentChapterIdForProgress = restored.mediaItems
            .getOrNull(startIndex)
            ?.mediaId
            ?.let(::parseMediaId)
            ?.chapterId
            ?: restored.startChapterId.orEmpty()
        localPlayer.setMediaItems(
            safeItems,
            startIndex.coerceIn(0, safeItems.lastIndex),
            positionMs,
        )
        localPlayer.repeatMode = remotePlayer.repeatMode
        localPlayer.shuffleModeEnabled = remotePlayer.shuffleModeEnabled
        localPlayer.playbackParameters = remotePlayer.playbackParameters
        localPlayer.playWhenReady = false
        Log.i(
            TAG,
            "Restored local playback after Cast. book=${restored.book.id} " +
                "index=$startIndex positionMs=$positionMs",
        )
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .apply {
                    PlayerCustomCommands.ALL_COMMAND_ACTIONS.forEach { action ->
                        add(SessionCommand(action, android.os.Bundle.EMPTY))
                    }
                }
                .build()
            val notificationButtons = notificationCommandButtons()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(notificationButtons)
                .setMediaButtonPreferences(notificationButtons)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                PlayerCustomCommands.SET_SPEED -> {
                    val speed = args.getFloat(PlayerCustomCommands.EXTRA_SPEED, 1f)
                    applySpeedInternal(speed)
                    playbackSettingsStore.saveSpeed(currentBookIdForProgress, speed)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.SET_TEMPORARY_SPEED -> {
                    val speed = args.getFloat(PlayerCustomCommands.EXTRA_SPEED, 1f)
                    applyTemporarySpeedInternal(speed)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.ADJUST_SPEED -> {
                    val delta = args.getFloat(PlayerCustomCommands.EXTRA_SPEED_DELTA, 0f)
                    adjustSpeedInternal(delta)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.CYCLE_SPEED -> {
                    cycleSpeedInternal()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.SET_SLEEP_TIMER_DURATION -> {
                    val durationMs = args.getLong(PlayerCustomCommands.EXTRA_DURATION_MS)
                    if (durationMs > 0L) sleepTimerController.start(durationMs)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.SET_SLEEP_TIMER_END_OF_CHAPTER -> {
                    sleepTimerController.startEndOfChapter()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.CANCEL_SLEEP_TIMER -> {
                    sleepTimerController.cancel()
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlayerCustomCommands.TOGGLE_LIKE -> handleFeedbackCommand(PlaybackFeedback.Liked)
                PlayerCustomCommands.TOGGLE_DISLIKE -> handleFeedbackCommand(PlaybackFeedback.Disliked)
                else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Plain UI controllers should connect to an empty session after a stopped/killed
            // paused task. Restore the saved queue only when Media3 asks for playback resumption.
            val snapshot = restoredQueueSnapshot()
            currentBookIdForProgress = snapshot.book.id
            currentChapterIdForProgress = snapshot.startChapterId.orEmpty()
            applyPersistedSpeed(snapshot.book.id)
            if (isForPlayback && snapshot.startChapterId != null) {
                autoDownloadCurrentChapter(snapshot.book.id, snapshot.startChapterId)
            }
            Log.i(
                TAG,
                "Playback resumption requested. isForPlayback=$isForPlayback " +
                    "book=${snapshot.book.id} chapter=${snapshot.startChapterId} " +
                    "positionMs=${snapshot.startPositionMs}",
            )
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    snapshot.mediaItems,
                    snapshot.startIndex,
                    snapshot.startPositionMs,
                ),
            )
        }
    }

    private fun handleFeedbackCommand(feedback: PlaybackFeedback): ListenableFuture<SessionResult> {
        val scope = playbackSettingsStore.feedbackControlScope
        if (scope == FeedbackControlScope.Hidden) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
        val ids = parseMediaId(player.currentMediaItem?.mediaId)
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
        val next = (applicationContext as AudiobookApplication)
            .playbackFeedbackStore
            .toggleFeedback(ids.bookId, ids.chapterId, feedback, scope)
        Log.i(
            TAG,
            "Playback feedback updated. book=${ids.bookId} chapter=${ids.chapterId} " +
                "scope=${scope.preferenceValue} feedback=$next",
        )
        applyNotificationCommandLayout()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private fun applyNotificationCommandLayout() {
        val key = notificationCommandLayoutKey()
        if (lastNotificationCommandLayoutKey == key) {
            requestNotificationUpdate()
            return
        }
        lastNotificationCommandLayoutKey = key
        val buttons = notificationCommandButtons()
        mediaSession?.setCustomLayout(buttons)
        mediaSession?.setMediaButtonPreferences(buttons)
        requestNotificationUpdate(immediate = true)
    }

    private fun notificationCommandLayoutKey(): NotificationCommandLayoutKey {
        val mode = playbackSettingsStore.notificationControlsMode
        val feedbackScope = playbackSettingsStore.feedbackControlScope
        val mediaItem = player.currentMediaItem
        val ids = parseMediaId(mediaItem?.mediaId)
        val includeFeedback = feedbackScope == FeedbackControlScope.Chapter &&
            (mode == NotificationControlsMode.Like || mode == NotificationControlsMode.Both)
        val includeSpeed = mode == NotificationControlsMode.Speed || mode == NotificationControlsMode.Both
        return NotificationCommandLayoutKey(
            mediaId = mediaItem?.mediaId,
            mode = mode,
            feedbackScope = feedbackScope,
            speed = if (includeSpeed) PlaybackSpeed.clamp(player.playbackParameters.speed).value else 0f,
            feedback = if (includeFeedback) {
                (applicationContext as AudiobookApplication)
                    .playbackFeedbackStore
                    .feedbackFor(ids?.bookId, ids?.chapterId, feedbackScope)
            } else {
                PlaybackFeedback.None
            },
        )
    }

    private fun requestNotificationUpdate(immediate: Boolean = false) {
        if (mediaSession == null) return
        notificationRefreshJob?.cancel()
        if (immediate) {
            triggerNotificationUpdate()
            return
        }
        notificationRefreshJob = serviceScope.launch {
            delay(NOTIFICATION_UPDATE_DEBOUNCE_MS)
            if (mediaSession != null) {
                triggerNotificationUpdate()
            }
        }
    }

    private fun notificationCommandButtons(): List<CommandButton> =
        notificationCommandButtons(
            context = applicationContext,
            currentMediaItem = player.currentMediaItem,
            mode = playbackSettingsStore.notificationControlsMode,
            feedbackScope = playbackSettingsStore.feedbackControlScope,
            currentSpeed = PlaybackSpeed.clamp(player.playbackParameters.speed),
        )

    private fun startProgressSaver() {
        if (progressSaveJob?.isActive == true) {
            return
        }

        progressSaveJob = serviceScope.launch {
            while (isActive) {
                saveCurrentProgress("periodic")
                delay(PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressSaver() {
        progressSaveJob?.cancel()
        progressSaveJob = null
    }

    private fun saveCurrentProgress(reason: String) {
        val mediaItem = player.currentMediaItem ?: return
        val ids = parseMediaId(mediaItem.mediaId) ?: return
        saveProgressSnapshot(
            bookId = ids.bookId,
            chapterId = ids.chapterId,
            chapterIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            reason = reason,
        )
    }

    private fun saveProgressSnapshot(
        bookId: String,
        chapterId: String,
        chapterIndex: Int,
        positionMs: Long,
        reason: String,
    ) {
        if (bookId.isBlank() || chapterId.isBlank()) {
            return
        }

        val savedPositionMs = positionMs.coerceAtLeast(0L)
        progressStore.savePosition(
            bookId = bookId,
            chapterId = chapterId,
            chapterIndex = chapterIndex,
            positionMs = savedPositionMs,
        )
        Log.d(
            TAG,
            "Saved progress. reason=$reason bookId=$bookId " +
                "chapterId=$chapterId positionMs=$savedPositionMs",
        )
    }

    private fun Player.playbackStateName(): String =
        when (playbackState) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN_$playbackState"
        }

    private fun Int.suppressionReasonName(): String =
        when (this) {
            Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS ->
                "TRANSIENT_AUDIO_FOCUS_LOSS"
            Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT ->
                "UNSUITABLE_AUDIO_OUTPUT"
            else -> "UNKNOWN_$this"
        }

    private companion object {
        const val TAG = "AudiobookPlayback"
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 10_000L
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
        const val AUTO_DOWNLOAD_MIN_LISTEN_MS = 50_000L
        const val SESSION_ACTIVITY_REQUEST_CODE = 1001
        const val CAST_LOAD_SETTLE_DELAY_MS = 750L
        const val NOTIFICATION_UPDATE_DEBOUNCE_MS = 90L
        const val CAST_CALLBACK_REGISTRATION_DELAY_MS = 2_500L
    }

    private data class RestoredQueueSnapshot(
        val book: AudioBook,
        val mediaItems: List<MediaItem>,
        val startChapterId: String?,
        val startIndex: Int,
        val startPositionMs: Long,
    )

    private data class NotificationCommandLayoutKey(
        val mediaId: String?,
        val mode: NotificationControlsMode,
        val feedbackScope: FeedbackControlScope,
        val speed: Float,
        val feedback: PlaybackFeedback,
    )
}

private class ThemedMediaNotificationProvider(context: Context) : MediaNotification.Provider {
    private val appContext = context.applicationContext
    private val delegate = ThemedDefaultMediaNotificationProvider(appContext)
        .apply {
            setSmallIcon(R.drawable.ic_notification_audio)
        }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val notification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback,
        )
        notification.notification.color = ContextCompat.getColor(
            appContext,
            R.color.notification_accent,
        )
        return notification
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo
}

private class ThemedDefaultMediaNotificationProvider(context: Context) :
    DefaultMediaNotificationProvider(
        context,
        { _ -> DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID },
        DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
        R.string.playback_notification_channel_name,
    ) {
    private val appContext = context.applicationContext
    private val app = appContext as AudiobookApplication

    override fun getMediaButtons(
        mediaSession: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val notificationCustomLayout = customLayout.withNotificationActionButtons(
            actionButtons = notificationCommandButtons(
                context = appContext,
                currentMediaItem = mediaSession.player.currentMediaItem,
                mode = app.playbackSettingsStore.notificationControlsMode,
                feedbackScope = app.playbackSettingsStore.feedbackControlScope,
                currentSpeed = PlaybackSpeed.clamp(mediaSession.player.playbackParameters.speed),
            ),
        )
        val buttons = super.getMediaButtons(
            mediaSession,
            playerCommands,
            notificationCustomLayout,
            showPauseButton,
        )
        return ImmutableList.copyOf(buttons.map { button ->
            button.withNotificationIconOverride() ?: button
        })
    }
}

private fun notificationCommandButtons(
    context: Context,
    currentMediaItem: MediaItem?,
    mode: NotificationControlsMode,
    feedbackScope: FeedbackControlScope,
    currentSpeed: PlaybackSpeed,
): List<CommandButton> =
    when (mode) {
        NotificationControlsMode.None -> emptyList()
        NotificationControlsMode.Like -> likeCommandButtons(context, currentMediaItem, feedbackScope)
        NotificationControlsMode.Speed -> speedCommandButtons(currentSpeed)
        NotificationControlsMode.Both -> likeCommandButtons(context, currentMediaItem, feedbackScope) +
            speedCommandButtons(currentSpeed)
    }

private fun likeCommandButtons(
    context: Context,
    currentMediaItem: MediaItem?,
    feedbackScope: FeedbackControlScope,
): List<CommandButton> {
    if (feedbackScope != FeedbackControlScope.Chapter) return emptyList()
    val ids = parseMediaId(currentMediaItem?.mediaId)
    val feedback = (context.applicationContext as AudiobookApplication)
        .playbackFeedbackStore
        .feedbackFor(ids?.bookId, ids?.chapterId, feedbackScope)
    return listOf(
        CommandButton.Builder(
            if (feedback == PlaybackFeedback.Liked) {
                CommandButton.ICON_THUMB_UP_FILLED
            } else {
                CommandButton.ICON_THUMB_UP_UNFILLED
            },
        )
            .setCustomIconResId(
                if (feedback == PlaybackFeedback.Liked) {
                    R.drawable.ic_notification_thumb_up_filled
                } else {
                    R.drawable.ic_notification_thumb_up
                },
            )
            .setSessionCommand(SessionCommand(PlayerCustomCommands.TOGGLE_LIKE, Bundle.EMPTY))
            .setDisplayName(if (feedback == PlaybackFeedback.Liked) "Liked" else "Like")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )
}

private fun speedCommandButtons(currentSpeed: PlaybackSpeed): List<CommandButton> {
    val (command, args) = PlayerCustomCommands.cycleSpeed()
    val nextSpeed = nextNotificationSpeed(currentSpeed)
    return listOf(
        CommandButton.Builder(speedIconFor(currentSpeed))
            .setCustomIconResId(R.drawable.ic_notification_speed)
            .setSessionCommand(command, args)
            .setDisplayName("Speed ${currentSpeed.label()}, next ${nextSpeed.label()}")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )
}

private fun nextNotificationSpeed(currentSpeed: PlaybackSpeed): PlaybackSpeed =
    PlaybackSpeed.NotificationCycle.firstOrNull { speed ->
        speed.value > currentSpeed.value + SPEED_EPSILON
    } ?: PlaybackSpeed.NotificationCycle.first()

private fun speedIconFor(speed: PlaybackSpeed): Int =
    when {
        speed.value <= 0.25f + SPEED_EPSILON -> CommandButton.ICON_PLAYBACK_SPEED
        speed.value <= 0.5f + SPEED_EPSILON -> CommandButton.ICON_PLAYBACK_SPEED_0_5
        speed.value <= 1.0f + SPEED_EPSILON -> CommandButton.ICON_PLAYBACK_SPEED_1_0
        speed.value <= 1.25f + SPEED_EPSILON -> CommandButton.ICON_PLAYBACK_SPEED_1_2
        speed.value <= 1.5f + SPEED_EPSILON -> CommandButton.ICON_PLAYBACK_SPEED_1_5
        else -> CommandButton.ICON_PLAYBACK_SPEED_2_0
    }

private fun ImmutableList<CommandButton>.withNotificationActionButtons(
    actionButtons: List<CommandButton>,
): ImmutableList<CommandButton> {
    val notificationActions = setOf(
        PlayerCustomCommands.TOGGLE_DISLIKE,
        PlayerCustomCommands.TOGGLE_LIKE,
        PlayerCustomCommands.ADJUST_SPEED,
        PlayerCustomCommands.CYCLE_SPEED,
    )
    val otherButtons = filterNot { it.sessionCommand?.customAction in notificationActions }
    return ImmutableList.copyOf(otherButtons + actionButtons)
}

private fun CommandButton.withNotificationIconOverride(): CommandButton? {
    val iconResId = when (icon) {
        CommandButton.ICON_PREVIOUS -> R.drawable.ic_notification_previous
        CommandButton.ICON_PLAY -> R.drawable.ic_notification_play
        CommandButton.ICON_PAUSE -> R.drawable.ic_notification_pause
        CommandButton.ICON_NEXT -> R.drawable.ic_notification_next
        CommandButton.ICON_THUMB_DOWN_FILLED -> R.drawable.ic_notification_thumb_down_filled
        CommandButton.ICON_THUMB_DOWN_UNFILLED -> R.drawable.ic_notification_thumb_down
        CommandButton.ICON_THUMB_UP_FILLED -> R.drawable.ic_notification_thumb_up_filled
        CommandButton.ICON_THUMB_UP_UNFILLED -> R.drawable.ic_notification_thumb_up
        CommandButton.ICON_MINUS_CIRCLE_UNFILLED -> R.drawable.ic_notification_speed_down
        CommandButton.ICON_PLUS_CIRCLE_UNFILLED -> R.drawable.ic_notification_speed_up
        CommandButton.ICON_PLAYBACK_SPEED,
        CommandButton.ICON_PLAYBACK_SPEED_0_5,
        CommandButton.ICON_PLAYBACK_SPEED_0_8,
        CommandButton.ICON_PLAYBACK_SPEED_1_0,
        CommandButton.ICON_PLAYBACK_SPEED_1_2,
        CommandButton.ICON_PLAYBACK_SPEED_1_5,
        CommandButton.ICON_PLAYBACK_SPEED_1_8,
        CommandButton.ICON_PLAYBACK_SPEED_2_0 -> R.drawable.ic_notification_speed
        else -> return null
    }
    val builder = CommandButton.Builder(icon)
        .setCustomIconResId(iconResId)
        .setDisplayName(displayName)
        .setEnabled(isEnabled)
        .setExtras(extras)
        .setSlots(*slots.toArray())

    val command = sessionCommand
    return if (command != null) {
        builder.setSessionCommand(command, parameter).build()
    } else {
        builder.setPlayerCommand(playerCommand, parameter).build()
    }
}

private const val SPEED_EPSILON = 0.001f

// Ignore the unused state-conversion helper; kept as documentation that we
// considered exposing a SleepTimerState→bundle bridge before settling on the
// process-shared PlaybackBus.
@Suppress("unused")
private fun SleepTimerState.toLabel(): String = when (this) {
    SleepTimerState.Idle -> "idle"
    is SleepTimerState.Running -> "running"
    SleepTimerState.EndOfChapter -> "endOfChapter"
    SleepTimerState.Expired -> "expired"
}
