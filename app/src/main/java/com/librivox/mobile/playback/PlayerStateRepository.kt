package com.librivox.mobile.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.media.session.MediaSession as PlatformMediaSession
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.MEDIA_METADATA_CAST_DURATION_MS
import com.librivox.mobile.model.MediaChapterIds
import com.librivox.mobile.model.cleanTitle
import com.librivox.mobile.model.isLocalCastCandidate
import com.librivox.mobile.model.isCastable
import com.librivox.mobile.model.isCastableAudiobookItem
import com.librivox.mobile.model.isLocalCastCandidateAudiobookItem
import com.librivox.mobile.model.numberedTitle
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.SleepTimerState
import com.librivox.mobile.model.chapterDisplayTitle
import com.librivox.mobile.model.parseMediaId
import com.librivox.mobile.model.systemArtworkUri
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single source of truth for the UI's view of playback. The Mini Player, the
 * Now Playing screen, and any Glasses companion all read from this so they
 * never disagree about state. Wraps a Media3 [MediaController] and merges the
 * process-shared [PlaybackBus] (sleep timer, effective speed).
 */
class PlayerStateRepository(
    private val context: Context,
    private val playbackBus: PlaybackBus,
    private val libraryRepository: LibraryRepository,
    private val serviceClass: Class<*>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _controllerState = MutableStateFlow(ControllerState())
    private val _state = MutableStateFlow(PlayerState())
    private val _scrubPreview = MutableStateFlow<ScrubPreview?>(null)
    private val _chapterSwitchPreview = MutableStateFlow<ChapterSwitchPreview?>(null)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollerJob: Job? = null
    private var queueRevision: Int = 0
    private var libraryRevision: Int = 0
    private var libraryState: LibraryState = LibraryState()
    private var retainedMediaItem: MediaItem? = null
    private var retainedIds: MediaChapterIds? = null
    private var platformSessionToken: PlatformMediaSession.Token? = null
    private val queueItemDisplayMetadata = LinkedHashMap<String, QueueItemDisplayMetadata>()
    private var cachedQueueKey: QueueSnapshotKey? = null
    private var cachedQueueAfterCurrent: List<QueueListItem> = emptyList()

    init {
        combine(
            _controllerState,
            playbackBus.sleepTimer,
            playbackBus.speed,
            _scrubPreview,
            _chapterSwitchPreview,
        ) { controllerState, sleep, speed, scrubPreview, chapterSwitchPreview ->
            val playerState = controllerState.toPlayerState(sleep, speed)
            val previewState = chapterSwitchPreview
                ?.takeIf { it.bookId == playerState.bookId }
                ?.applyTo(playerState)
                ?: playerState
            previewState.copy(
                scrubPreviewPositionMs = scrubPreview
                    ?.takeIf {
                        it.bookId == previewState.bookId &&
                            it.chapterId == previewState.chapterId
                    }
                    ?.positionMs,
            )
        }.onEach { _state.value = it }.launchIn(scope)
        libraryRepository.state
            .onEach {
                if (libraryState != it) {
                    libraryState = it
                    libraryRevision++
                    cachedQueueKey = null
                }
                refreshFromController()
            }
            .launchIn(scope)
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, serviceClass))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(Runnable {
            val newController = try {
                future.get()
            } catch (_: CancellationException) {
                return@Runnable
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@Runnable
            }
            controller = newController
            platformSessionToken = newController.connectedToken?.platformTokenOrNull()
            newController.addListener(playerListener)
            refreshFromController()
            startPoller()
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        pollerJob?.cancel()
        pollerJob = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        platformSessionToken = null
        _controllerState.value = _controllerState.value.copy(
            connected = false,
            platformSessionToken = null,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun dismissPlayback() {
        val c = controller ?: return
        _chapterSwitchPreview.value = null
        clearRetainedMedia()
        c.stop()
        c.clearMediaItems()
        queueRevision++
        refreshFromController()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun previewSeekTo(positionMs: Long?) {
        val ids = _controllerState.value.ids
        _scrubPreview.value = positionMs?.let { previewPosition ->
            ScrubPreview(
                bookId = ids?.bookId,
                chapterId = ids?.chapterId,
                positionMs = previewPosition.coerceAtLeast(0L),
            )
        }
    }

    fun seekBack() {
        val c = controller ?: return
        c.seekTo((c.currentPosition - SEEK_BACK_MS).coerceAtLeast(0L))
    }

    fun seekForward() {
        val c = controller ?: return
        val targetMs = c.currentPosition + SEEK_FORWARD_MS
        val durationMs = c.duration.takeIf { it > 0L }
        c.seekTo(durationMs?.let { targetMs.coerceAtMost(it) } ?: targetMs)
    }

    fun seekToNextChapter() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            seekToChapter(c.currentMediaItemIndex + 1)
        }
    }

    fun seekToPreviousChapter() {
        val c = controller ?: return
        if (c.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS || !c.hasPreviousMediaItem()) {
            c.seekTo(0L)
        } else {
            seekToChapter((c.currentMediaItemIndex - 1).coerceAtLeast(0))
        }
    }

    fun seekToChapter(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        showChapterSwitchPreview(index)
        c.seekTo(index, 0L)
        refreshFromController()
    }

    private fun showChapterSwitchPreview(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        val item = c.getMediaItemAt(index)
        val ids = parseMediaId(item.mediaId) ?: return
        val book = libraryState.bookById(ids.bookId)
        val chapter = book?.chapters?.firstOrNull { it.id == ids.chapterId }
        val chapterIndex = book
            ?.chapters
            ?.indexOfFirst { it.id == ids.chapterId }
            ?.takeIf { it >= 0 }
            ?: index
        val castAvailability = castAvailability(ids, item)
        _chapterSwitchPreview.value = ChapterSwitchPreview(
            bookId = ids.bookId,
            chapterId = ids.chapterId,
            title = chapter?.cleanTitle()
                ?: item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                ?: "Chapter ${chapterIndex + 1}",
            author = book?.author?.takeIf { it.isNotBlank() }
                ?: item.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
            albumTitle = book?.title?.takeIf { it.isNotBlank() }
                ?: item.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            artworkUri = book?.systemArtworkUri(context)?.toString()
                ?: item.mediaMetadata.artworkUri?.toString(),
            durationMs = chapter
                ?.durationSeconds
                ?.takeIf { it > 0L }
                ?.times(1000L)
                ?: item.mediaMetadata.extras?.getLong(MEDIA_METADATA_CAST_DURATION_MS, 0L)
                    ?.takeIf { it > 0L }
                ?: _state.value.durationMs,
            mediaItemIndex = index,
            mediaItemCount = c.mediaItemCount,
            hasPrevious = index > 0,
            hasNext = index < c.mediaItemCount - 1,
            canCast = castAvailability.canCast,
            castUnavailableReason = castAvailability.unavailableReason,
        )
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        val c = controller ?: return
        _chapterSwitchPreview.value = null
        rememberQueueDisplayMetadata(items)
        retainMediaForUi(items.getOrNull(startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))))
        c.setMediaItems(items, startIndex, startPositionMs)
        c.prepare()
        c.play()
    }

    fun addAfterCurrent(items: List<MediaItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        rememberQueueDisplayMetadata(items)
        val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItems(index, items)
        queueRevision++
        refreshFromController()
    }

    fun appendToQueue(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val c = controller ?: return
        rememberQueueDisplayMetadata(items)
        c.addMediaItems(items)
        queueRevision++
        refreshFromController()
    }

    fun appendQueueItemCopy(absoluteIndex: Int) {
        val c = controller ?: return
        if (absoluteIndex !in 0 until c.mediaItemCount) return
        val item = c.getMediaItemAt(absoluteIndex)
        rememberQueueDisplayMetadata(item)
        c.addMediaItem(item)
        queueRevision++
        refreshFromController()
    }

    fun removeQueueItem(absoluteIndex: Int) {
        val c = controller ?: return
        if (absoluteIndex in 0 until c.mediaItemCount && absoluteIndex != c.currentMediaItemIndex) {
            c.removeMediaItem(absoluteIndex)
            queueRevision++
            refreshFromController()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val c = controller ?: return
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until c.mediaItemCount) return
        val clampedTo = toIndex.coerceIn(0, c.mediaItemCount - 1)
        // Don't displace the currently-playing item.
        if (fromIndex == c.currentMediaItemIndex || clampedTo == c.currentMediaItemIndex) return
        c.moveMediaItem(fromIndex, clampedTo)
        queueRevision++
        refreshFromController()
        scope.launch {
            delay(QUEUE_REFRESH_DELAY_MS)
            refreshFromController()
        }
    }

    /** Snapshot of media items after the current one, with display labels. */
    fun queueAfterCurrent(): List<QueueListItem> {
        val c = controller ?: return emptyList()
        return c.cachedQueueAfterCurrent()
    }

    private fun Player.queueAfterCurrent(): List<QueueListItem> {
        val current = currentMediaItemIndex
        val startIndex = if (current in 0 until mediaItemCount) current + 1 else 0
        if (mediaItemCount <= startIndex) return emptyList()
        return (startIndex until mediaItemCount).map { idx ->
            val item = getMediaItemAt(idx)
            rememberQueueDisplayMetadata(item)
            val ids = parseMediaId(item.mediaId)
            val book = ids?.let { libraryState.bookById(it.bookId) }
            val chapter = ids?.let { parsed ->
                book?.chapters?.firstOrNull { it.id == parsed.chapterId }
            }
            val rememberedMetadata = queueItemDisplayMetadata[item.mediaId]
            val displayTitle = chapter?.numberedTitle()
                ?: rememberedMetadata?.title
                ?: item.queueDisplayTitleOrNull()
                ?: "Chapter ${idx + 1}"
            val subtitle = listOfNotNull(
                book?.title ?: item.mediaMetadata.albumTitle?.toString(),
                book?.author ?: item.mediaMetadata.artist?.toString(),
                chapter?.reader?.takeIf { it.isNotBlank() }?.let { "Read by $it" },
            )
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" • ")
                .ifBlank { rememberedMetadata?.subtitle.orEmpty() }
            QueueListItem(
                id = "${item.mediaId}#$idx",
                mediaId = item.mediaId,
                title = displayTitle,
                subtitle = subtitle,
                absoluteIndex = idx,
                artworkUri = book?.systemArtworkUri(context)?.toString()
                    ?: item.mediaMetadata.artworkUri?.toString()
                    ?: rememberedMetadata?.artworkUri,
            )
        }
    }

    fun setSpeed(speed: PlaybackSpeed) {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.setSpeed(speed.value)
        c.sendCustomCommand(command, args)
    }

    fun setTemporarySpeed(speed: Float) {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.setTemporarySpeed(speed)
        c.sendCustomCommand(command, args)
    }

    fun setSleepTimer(durationMs: Long) {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.setSleepTimerDuration(durationMs)
        c.sendCustomCommand(command, args)
    }

    fun setSleepTimerEndOfChapter() {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.setSleepTimerEndOfChapter()
        c.sendCustomCommand(command, args)
    }

    fun cancelSleepTimer() {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.cancelSleepTimer()
        c.sendCustomCommand(command, args)
    }

    fun toggleLike() {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.toggleLike()
        c.sendCustomCommand(command, args)
    }

    fun toggleDislike() {
        val c = controller ?: return
        val (command, args) = PlayerCustomCommands.toggleDislike()
        c.sendCustomCommand(command, args)
    }

    fun setDeviceVolume(volume: Int, flags: Int = 0): Boolean {
        val c = controller ?: return false
        if (!c.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS)) return false
        val minVolume = c.deviceInfo.minVolume
        val maxVolume = c.deviceInfo.maxVolume.coerceAtLeast(minVolume)
        c.setDeviceVolume(volume.coerceIn(minVolume, maxVolume), flags)
        refreshFromController()
        return true
    }

    fun adjustDeviceVolume(delta: Int, flags: Int = 0): Boolean {
        if (delta == 0) return false
        val c = controller ?: return false
        if (c.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)) {
            if (delta > 0) {
                c.increaseDeviceVolume(flags)
            } else {
                c.decreaseDeviceVolume(flags)
            }
            refreshFromController()
            return true
        }
        return setDeviceVolume(c.deviceVolume + delta, flags)
    }

    fun shouldHandleHardwareVolume(): Boolean {
        val current = state.value
        return current.isRemotePlayback
    }

    fun stopCasting() {
        CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
            .addOnSuccessListener { castContext ->
                castContext.sessionManager.endCurrentSession(true)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshFromController()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshFromController()
        }

        override fun onPlayerError(error: PlaybackException) {
            refreshFromController()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            queueRevision++
            refreshFromController()
        }
    }

    private fun refreshFromController() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem
        rememberQueueDisplayMetadata(mediaItem)
        val ids = parseMediaId(mediaItem?.mediaId)
        if (c.playbackState == Player.STATE_IDLE && mediaItem == null && c.mediaItemCount == 0) {
            clearRetainedMedia()
        } else {
            retainMediaForUi(mediaItem)
        }
        val displayMediaItem = mediaItem ?: retainedMediaItem
        val displayIds = ids ?: retainedIds
        val deviceInfo = c.deviceInfo
        val minDeviceVolume = deviceInfo.minVolume
        val maxDeviceVolume = deviceInfo.maxVolume.coerceAtLeast(minDeviceVolume)
        val deviceVolume = c.deviceVolume.coerceIn(minDeviceVolume, maxDeviceVolume)
        val castAvailability = castAvailability(displayIds, displayMediaItem)
        val displayArtworkUri = displayIds
            ?.let { libraryState.bookById(it.bookId)?.systemArtworkUri(context)?.toString() }
            ?: displayMediaItem?.mediaMetadata?.artworkUri?.toString()
        val nextState = ControllerState(
            connected = true,
            mediaItem = displayMediaItem,
            ids = displayIds,
            artworkUri = displayArtworkUri,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.takeUnless { it == C.TIME_UNSET }
                ?.coerceAtLeast(0L)
                ?: _controllerState.value.durationMs,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            mediaItemIndex = c.currentMediaItemIndex,
            mediaItemCount = c.mediaItemCount,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
            canSeekPrevious = c.hasPreviousMediaItem() || c.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS,
            canCast = castAvailability.canCast,
            castUnavailableReason = castAvailability.unavailableReason,
            platformSessionToken = platformSessionToken,
            devicePlaybackType = deviceInfo.playbackType,
            deviceVolume = deviceVolume,
            deviceMinVolume = minDeviceVolume,
            deviceMaxVolume = maxDeviceVolume,
            deviceMuted = c.isDeviceMuted,
            canSetDeviceVolume = c.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS),
            canAdjustDeviceVolume = c.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS) ||
                c.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS),
            queueRevision = queueRevision,
            upNextQueue = emptyList(),
            playbackError = c.playerError?.toDisplayMessage(),
        )
        _controllerState.value = nextState
        _chapterSwitchPreview.value?.let { preview ->
            val nextIds = nextState.ids
            val settledOnPreview =
                nextIds?.bookId == preview.bookId &&
                    nextIds.chapterId == preview.chapterId
            val switchedToDifferentBook =
                nextIds != null &&
                    nextIds.bookId != preview.bookId
            if (settledOnPreview || switchedToDifferentBook) {
                _chapterSwitchPreview.value = null
            }
        }
        _scrubPreview.value?.let { preview ->
            if (
                preview.bookId != nextState.ids?.bookId ||
                preview.chapterId != nextState.ids?.chapterId ||
                kotlin.math.abs(nextState.positionMs - preview.positionMs) <= SCRUB_PREVIEW_SETTLED_THRESHOLD_MS
            ) {
                _scrubPreview.value = null
            }
        }
    }

    private fun Player.cachedQueueAfterCurrent(): List<QueueListItem> {
        val key = QueueSnapshotKey(
            revision = queueRevision,
            currentIndex = currentMediaItemIndex,
            count = mediaItemCount,
            libraryRevision = libraryRevision,
        )
        cachedQueueKey?.let { cachedKey ->
            if (cachedKey == key) return cachedQueueAfterCurrent
        }
        val queueMediaItems = (0 until mediaItemCount).map { getMediaItemAt(it) }
        rememberQueueDisplayMetadata(queueMediaItems)
        trimQueueDisplayMetadata(queueMediaItems.map { it.mediaId }.toSet())
        return queueAfterCurrent().also { queue ->
            cachedQueueKey = key
            cachedQueueAfterCurrent = queue
        }
    }

    private fun retainMediaForUi(mediaItem: MediaItem?) {
        if (mediaItem == null) return
        rememberQueueDisplayMetadata(mediaItem)
        val ids = parseMediaId(mediaItem.mediaId)
        if (ids != null) {
            retainedIds = ids
        }
        val hasDisplayMetadata =
            !mediaItem.mediaMetadata.title?.toString().isNullOrBlank() ||
                !mediaItem.mediaMetadata.albumTitle?.toString().isNullOrBlank() ||
                !mediaItem.mediaMetadata.artist?.toString().isNullOrBlank() ||
                mediaItem.mediaMetadata.artworkUri != null
        if (ids != null || hasDisplayMetadata) {
            retainedMediaItem = mediaItem
        }
    }

    private fun clearRetainedMedia() {
        retainedMediaItem = null
        retainedIds = null
        queueItemDisplayMetadata.clear()
    }

    private fun rememberQueueDisplayMetadata(items: List<MediaItem>) {
        items.forEach(::rememberQueueDisplayMetadata)
    }

    private fun rememberQueueDisplayMetadata(mediaItem: MediaItem?) {
        val metadata = mediaItem?.queueItemDisplayMetadata() ?: return
        val previous = queueItemDisplayMetadata[metadata.mediaId]
        queueItemDisplayMetadata[metadata.mediaId] = QueueItemDisplayMetadata(
            mediaId = metadata.mediaId,
            title = metadata.title.preferOver(previous?.title),
            subtitle = metadata.subtitle.ifBlank { previous?.subtitle.orEmpty() },
            artworkUri = metadata.artworkUri ?: previous?.artworkUri,
        )
    }

    private fun trimQueueDisplayMetadata(activeMediaIds: Set<String>) {
        val iterator = queueItemDisplayMetadata.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() !in activeMediaIds) {
                iterator.remove()
            }
        }
    }

    private fun castAvailability(ids: MediaChapterIds?, mediaItem: MediaItem?): CastAvailability {
        val localNetworkAvailable = LocalCastHttpServer.hasUsableLocalNetwork(context)
        val chapter = ids?.let { parsedIds ->
            libraryState
                .bookById(parsedIds.bookId)
                ?.chapters
                ?.firstOrNull { it.id == parsedIds.chapterId }
        }
        val canCast = chapter?.isCastable(localNetworkAvailable) == true ||
            mediaItem?.isCastableAudiobookItem(localNetworkAvailable) == true
        if (canCast) return CastAvailability(canCast = true, unavailableReason = null)

        val localCandidate = chapter?.isLocalCastCandidate() == true ||
            mediaItem?.isLocalCastCandidateAudiobookItem() == true
        val reason = if (localCandidate) {
            if (LocalCastHttpServer.hasLocalNetworkAccess(context)) {
                "Connect this phone and Cast device to the same Wi-Fi network."
            } else {
                "Allow local network access to cast downloaded audio."
            }
        } else {
            "Unsupported for this audiobook."
        }
        return CastAvailability(canCast = false, unavailableReason = reason)
    }

    private fun startPoller() {
        pollerJob?.cancel()
        pollerJob = scope.launch {
            while (isActive) {
                refreshFromController()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    private data class ControllerState(
        val connected: Boolean = false,
        val mediaItem: MediaItem? = null,
        val ids: MediaChapterIds? = null,
        val artworkUri: String? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val durationMs: Long = 0L,
        val positionMs: Long = 0L,
        val mediaItemIndex: Int = 0,
        val mediaItemCount: Int = 0,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false,
        val canSeekPrevious: Boolean = false,
        val canCast: Boolean = false,
        val castUnavailableReason: String? = null,
        val platformSessionToken: PlatformMediaSession.Token? = null,
        val devicePlaybackType: Int = DeviceInfo.PLAYBACK_TYPE_LOCAL,
        val deviceVolume: Int = 0,
        val deviceMinVolume: Int = 0,
        val deviceMaxVolume: Int = 0,
        val deviceMuted: Boolean = false,
        val canSetDeviceVolume: Boolean = false,
        val canAdjustDeviceVolume: Boolean = false,
        val queueRevision: Int = 0,
        val upNextQueue: List<QueueListItem> = emptyList(),
        val playbackError: String? = null,
    ) {
        fun toPlayerState(sleep: SleepTimerState, speed: PlaybackSpeed): PlayerState =
            PlayerState(
                connected = connected,
                bookId = ids?.bookId,
                chapterId = ids?.chapterId,
                title = mediaItem?.mediaMetadata?.title?.toString(),
                author = mediaItem?.mediaMetadata?.artist?.toString(),
                albumTitle = mediaItem?.mediaMetadata?.albumTitle?.toString(),
                artworkUri = artworkUri,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                durationMs = durationMs,
                positionMs = positionMs,
                mediaItemIndex = mediaItemIndex,
                mediaItemCount = mediaItemCount,
                hasNext = hasNext,
                hasPrevious = hasPrevious,
                canSeekPrevious = canSeekPrevious,
                canCast = canCast,
                castUnavailableReason = castUnavailableReason,
                platformSessionToken = platformSessionToken,
                isRemotePlayback = devicePlaybackType == DeviceInfo.PLAYBACK_TYPE_REMOTE,
                deviceVolume = deviceVolume,
                deviceMinVolume = deviceMinVolume,
                deviceMaxVolume = deviceMaxVolume,
                deviceMuted = deviceMuted,
                canSetDeviceVolume = canSetDeviceVolume,
                canAdjustDeviceVolume = canAdjustDeviceVolume,
                queueRevision = queueRevision,
                upNextQueue = upNextQueue,
                playbackError = playbackError,
                speed = speed,
                sleepTimer = sleep,
            )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        private const val QUEUE_REFRESH_DELAY_MS = 80L
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
        private const val SEEK_BACK_MS = 10_000L
        private const val SEEK_FORWARD_MS = 10_000L
        private const val SCRUB_PREVIEW_SETTLED_THRESHOLD_MS = 250L
    }
}

private data class ScrubPreview(
    val bookId: String?,
    val chapterId: String?,
    val positionMs: Long,
)

private data class QueueSnapshotKey(
    val revision: Int,
    val currentIndex: Int,
    val count: Int,
    val libraryRevision: Int,
)

private data class ChapterSwitchPreview(
    val bookId: String,
    val chapterId: String,
    val title: String,
    val author: String?,
    val albumTitle: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val mediaItemIndex: Int,
    val mediaItemCount: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val canCast: Boolean,
    val castUnavailableReason: String?,
) {
    fun applyTo(state: PlayerState): PlayerState =
        state.copy(
            bookId = bookId,
            chapterId = chapterId,
            title = title,
            author = author ?: state.author,
            albumTitle = albumTitle ?: state.albumTitle,
            artworkUri = artworkUri ?: state.artworkUri,
            isBuffering = true,
            durationMs = durationMs.takeIf { it > 0L } ?: state.durationMs,
            positionMs = 0L,
            mediaItemIndex = mediaItemIndex,
            mediaItemCount = mediaItemCount,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            canSeekPrevious = hasPrevious,
            canCast = canCast,
            castUnavailableReason = castUnavailableReason,
            playbackError = null,
            scrubPreviewPositionMs = null,
        )
}

data class QueueListItem(
    val id: String,
    val mediaId: String,
    val title: String,
    val subtitle: String,
    val absoluteIndex: Int,
    val artworkUri: String? = null,
)

data class PlayerState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val chapterId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val albumTitle: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val mediaItemIndex: Int = 0,
    val mediaItemCount: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val canSeekPrevious: Boolean = false,
    val canCast: Boolean = false,
    val castUnavailableReason: String? = null,
    val platformSessionToken: PlatformMediaSession.Token? = null,
    val isRemotePlayback: Boolean = false,
    val deviceVolume: Int = 0,
    val deviceMinVolume: Int = 0,
    val deviceMaxVolume: Int = 0,
    val deviceMuted: Boolean = false,
    val canSetDeviceVolume: Boolean = false,
    val canAdjustDeviceVolume: Boolean = false,
    val queueRevision: Int = 0,
    val upNextQueue: List<QueueListItem> = emptyList(),
    val playbackError: String? = null,
    val speed: PlaybackSpeed = PlaybackSpeed.Normal,
    val sleepTimer: SleepTimerState = SleepTimerState.Idle,
    val scrubPreviewPositionMs: Long? = null,
) {
    val hasMedia: Boolean get() = !bookId.isNullOrBlank() && !title.isNullOrBlank()
}

private data class CastAvailability(
    val canCast: Boolean,
    val unavailableReason: String?,
)

private data class QueueItemDisplayMetadata(
    val mediaId: String,
    val title: String?,
    val subtitle: String,
    val artworkUri: String?,
)

private fun MediaItem.queueItemDisplayMetadata(): QueueItemDisplayMetadata? {
    val id = mediaId.takeIf { it.isNotBlank() } ?: return null
    val subtitle = listOfNotNull(
        mediaMetadata.albumTitle?.toString(),
        mediaMetadata.artist?.toString(),
    )
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" • ")
    val metadata = QueueItemDisplayMetadata(
        mediaId = id,
        title = queueDisplayTitleOrNull(),
        subtitle = subtitle,
        artworkUri = mediaMetadata.artworkUri?.toString(),
    )
    return metadata.takeIf {
        it.title != null || it.subtitle.isNotBlank() || it.artworkUri != null
    }
}

private fun MediaItem.queueDisplayTitleOrNull(): String? =
    chapterDisplayTitle()
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Chapter", ignoreCase = true) }

private fun String?.preferOver(previous: String?): String? =
    when {
        this == null -> previous
        previous == null -> this
        isGenericQueueChapterTitle() -> previous
        else -> this
    }

private fun String.isGenericQueueChapterTitle(): Boolean {
    val normalized = trim().lowercase()
    return normalized == "chapter" || GenericQueueChapterTitleRegex.matches(normalized)
}

private val GenericQueueChapterTitleRegex = Regex("""^\d+\s*:\s*chapter$""")

private fun PlaybackException.toDisplayMessage(): String =
    errorCodeName
        .removePrefix("ERROR_CODE_")
        .lowercase()
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun SessionToken.platformTokenOrNull(): PlatformMediaSession.Token? =
    runCatching {
        // Media3 keeps this package-private, but Android's output switcher needs the platform token
        // to bind route and volume UI to the correct media session.
        val method = SessionToken::class.java.getDeclaredMethod("getPlatformToken")
        method.isAccessible = true
        method.invoke(this) as? PlatformMediaSession.Token
    }.getOrNull()
