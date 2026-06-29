package com.librivox.mobile.glasses

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import com.librivox.mobile.model.chapterDisplayTitle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.ListItem
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.TitleChip
import androidx.xr.glimmer.list.VerticalList
import androidx.xr.glimmer.onIndirectPointerGesture
import androidx.xr.projected.ProjectedActivityCompat
import androidx.xr.projected.ProjectedDisplayController
import androidx.xr.projected.ProjectedDisplayController.PresentationMode.Companion.VISUALS_ON
import androidx.xr.projected.ProjectedInputEvent
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import com.librivox.mobile.AudiobookApplication
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.SleepTimerState
import com.librivox.mobile.model.formatDuration
import com.librivox.mobile.model.normalizedDurationMs
import com.librivox.mobile.playback.AudiobookPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import java.util.function.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalProjectedApi::class)
class GlassesPlayerActivity : ComponentActivity() {
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressTickerJob: Job? = null
    private var projectedInputJob: Job? = null
    private var projectedDisplayJob: Job? = null
    private var projectedActivityCompat: ProjectedActivityCompat? = null
    private var projectedDisplayController: ProjectedDisplayController? = null
    private var presentationModeListener: Consumer<ProjectedDisplayController.PresentationModeFlags>? =
        null
    private val projectedInputDispatcher: CoroutineDispatcher = Dispatchers.Default

    private var playerUiState by mutableStateOf(GlassesPlayerUiState())
    private var isDisplayOn by mutableStateOf(true)

    private val playerListener =
        object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshPlayerState()
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                Log.i(TAG, "Playback suppression reason=$playbackSuppressionReason")
                refreshPlayerState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Playback error. code=${error.errorCodeName}", error)
                playerUiState = playerUiState.copy(status = error.errorCodeName)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectMediaController()
        observeProjectedInputEvents()
        observeProjectedDisplayState()
        observePlaybackBus()
        setContent {
            GlassesPlayerScreen(
                state = playerUiState,
                isDisplayOn = isDisplayOn,
                onTogglePlay = ::togglePlayPause,
                onSeekBack = { seekBy(-SEEK_BACK_MS) },
                onSeekForward = { seekBy(SEEK_FORWARD_MS) },
                onTouchpadTogglePlay = ::handleTouchpadTogglePlay,
                onTouchpadSeekBack = ::handleTouchpadSeekBack,
                onTouchpadSeekForward = ::handleTouchpadSeekForward,
                onClose = { finish() },
            )
        }
    }

    override fun onDestroy() {
        progressTickerJob?.cancel()
        projectedInputJob?.cancel()
        projectedDisplayJob?.cancel()
        clearPresentationListener()
        projectedActivityCompat?.close()
        projectedActivityCompat = null
        projectedDisplayController?.close()
        projectedDisplayController = null
        mediaController?.removeListener(playerListener)
        mediaControllerFuture?.let(MediaController::releaseFuture)
        mediaControllerFuture = null
        mediaController = null
        super.onDestroy()
    }

    private fun connectMediaController() {
        val sessionToken = SessionToken(
            this,
            ComponentName(this, AudiobookPlaybackService::class.java),
        )
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener(
                {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    refreshPlayerState()
                    startProgressTicker()
                    Log.i(TAG, "Connected to audiobook MediaController.")
                },
                ContextCompat.getMainExecutor(this),
            )
        }
    }

    private fun refreshPlayerState() {
        val controller = mediaController ?: return
        val durationMs = normalizedDurationMs(controller.duration)
        val metadata = controller.mediaMetadata
        val currentMediaItem = controller.currentMediaItem
        val chapterTitle = currentMediaItem?.chapterDisplayTitle()
            ?: metadata.title?.toString()
        playerUiState = GlassesPlayerUiState(
            bookTitle = metadata.albumTitle?.toString().orEmpty().ifBlank { "Audiobook" },
            chapterTitle = chapterTitle.orEmpty().ifBlank { "Current chapter" },
            author = metadata.artist?.toString().orEmpty(),
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = durationMs,
            isPlaying = controller.isPlaying,
            status = playbackStatus(controller),
        )
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = lifecycleScope.launch {
            while (isActive) {
                refreshPlayerState()
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
            return
        }

        if (controller.playbackState == Player.STATE_ENDED) {
            controller.seekTo(controller.currentMediaItemIndex, 0L)
        }
        if (controller.playbackState == Player.STATE_IDLE) {
            controller.prepare()
        }
        controller.play()
    }

    private fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val durationMs = normalizedDurationMs(controller.duration)
        val upperBound = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        controller.seekTo((controller.currentPosition + deltaMs).coerceIn(0L, upperBound))
    }

    private fun handleTouchpadTogglePlay() {
        Log.i(TAG, "Touchpad tap received. Toggling playback.")
        togglePlayPause()
    }

    private fun handleTouchpadSeekBack() {
        Log.i(TAG, "Touchpad swipe backward received. Rewinding 10 seconds.")
        seekBy(-SEEK_BACK_MS)
    }

    private fun handleTouchpadSeekForward() {
        Log.i(TAG, "Touchpad swipe forward received. Skipping forward 10 seconds.")
        seekBy(SEEK_FORWARD_MS)
    }

    private fun observeProjectedInputEvents() {
        projectedInputJob?.cancel()
        projectedInputJob =
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    withContext(projectedInputDispatcher) {
                        val projectedActivity = ensureProjectedActivityCompat() ?: return@withContext
                        try {
                            projectedActivity.projectedInputEvents.collect(::handleProjectedInputEvent)
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            Log.w(TAG, "Projected input listener failed: ${throwable.message}", throwable)
                        }
                    }
                }
            }
    }

    private suspend fun ensureProjectedActivityCompat(): ProjectedActivityCompat? {
        projectedActivityCompat?.let { return it }
        return runCatching {
            ProjectedActivityCompat.create(this)
        }.onFailure { throwable ->
            Log.w(TAG, "Projected input setup failed: ${throwable.message}", throwable)
        }.getOrNull()
            ?.also { projectedActivityCompat = it }
    }

    private fun handleProjectedInputEvent(inputEvent: ProjectedInputEvent) {
        val inputAction = inputEvent.inputAction
        val actionName = if (
            inputAction == ProjectedInputEvent.ProjectedInputAction.TOGGLE_APP_CAMERA
        ) {
            "TOGGLE_APP_CAMERA"
        } else {
            "UNKNOWN_PROJECTED_INPUT_ACTION"
        }

        Log.i(TAG, "Projected input received. action=$actionName code=${inputAction.code}")

        if (inputAction != ProjectedInputEvent.ProjectedInputAction.TOGGLE_APP_CAMERA) {
            return
        }

        lifecycleScope.launch(Dispatchers.Main.immediate) {
            Log.i(TAG, "Camera/action button double press received. Toggling playback.")
            togglePlayPause()
        }
    }

    private fun observeProjectedDisplayState() {
        projectedDisplayJob?.cancel()
        projectedDisplayJob =
            lifecycleScope.launch {
                clearPresentationListener()
                projectedDisplayController?.close()
                projectedDisplayController = null

                val displayController =
                    runCatching {
                        ProjectedDisplayController.create(this@GlassesPlayerActivity)
                    }.onFailure { throwable ->
                        Log.w(TAG, "Projected display setup failed: ${throwable.message}", throwable)
                    }.getOrNull()
                        ?: return@launch

                projectedDisplayController = displayController
                val listener =
                    Consumer<ProjectedDisplayController.PresentationModeFlags> { flags ->
                        isDisplayOn = flags.hasPresentationMode(VISUALS_ON)
                        Log.i(TAG, "Projected display visuals on=$isDisplayOn")
                    }
                presentationModeListener = listener
                displayController.addPresentationModeChangedListener(listener = listener)
            }
    }

    private fun clearPresentationListener() {
        presentationModeListener?.let { listener ->
            projectedDisplayController?.removePresentationModeChangedListener(listener)
        }
        presentationModeListener = null
    }

    private fun observePlaybackBus() {
        val app = applicationContext as AudiobookApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.playbackBus.speed.collect { speed ->
                        playerUiState = playerUiState.copy(speed = speed)
                    }
                }
                launch {
                    app.playbackBus.sleepTimer.collect { sleep ->
                        playerUiState = playerUiState.copy(sleepTimer = sleep)
                    }
                }
            }
        }
    }

    private fun playbackStatus(controller: MediaController): String =
        when {
            controller.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE ->
                "Audio focus"
            controller.isPlaying -> "Playing"
            controller.playbackState == Player.STATE_BUFFERING -> "Buffering"
            controller.playbackState == Player.STATE_ENDED -> "Finished"
            else -> "Paused"
        }

    private companion object {
        const val TAG = "GlassesAudiobook"
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 10_000L
        const val PROGRESS_TICK_MS = 1_000L
    }
}

private data class GlassesPlayerUiState(
    val bookTitle: String = "Cathedral",
    val chapterTitle: String = "Cathedral",
    val author: String = "Raymond Carver",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val status: String = "Paused",
    val speed: PlaybackSpeed = PlaybackSpeed.Normal,
    val sleepTimer: SleepTimerState = SleepTimerState.Idle,
)

@Composable
private fun GlassesPlayerScreen(
    state: GlassesPlayerUiState,
    isDisplayOn: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onTouchpadTogglePlay: () -> Unit,
    onTouchpadSeekBack: () -> Unit,
    onTouchpadSeekForward: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    GlimmerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusTarget()
                .touchpadControls(
                    onTogglePlay = onTouchpadTogglePlay,
                    onSeekBack = onTouchpadSeekBack,
                    onSeekForward = onTouchpadSeekForward,
                ),
        ) {
            VerticalList(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "title") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        TitleChip {
                            Text(text = "Audiobook")
                        }
                    }
                }

                item(key = "now_playing") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(text = state.bookTitle)
                                Text(text = state.chapterTitle)
                                if (state.author.isNotBlank()) {
                                    Text(text = state.author)
                                }
                                Text(text = state.status)
                                Text(
                                    text = progressText(
                                        positionMs = state.positionMs,
                                        durationMs = state.durationMs,
                                    ),
                                )
                                if (state.speed.value != 1f) {
                                    Text(text = "Speed ${state.speed.label()}")
                                }
                                sleepBadge(state.sleepTimer)?.let { Text(text = it) }
                                if (!isDisplayOn) {
                                    Text(text = "Display off")
                                }
                            }
                        }
                    }
                }

                item(key = "play_pause") {
                    ListItem(onClick = onTogglePlay) {
                        Text(text = if (state.isPlaying) "Pause" else "Play")
                    }
                }
                item(key = "seek") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            onClick = onSeekBack,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "-30s")
                        }
                        ListItem(
                            onClick = onSeekForward,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "+15s")
                        }
                    }
                }
                item(key = "hint") {
                    Text(text = "Tap/double press: play. Swipe seeks.")
                }
                item(key = "close") {
                    ListItem(onClick = onClose) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }
}

private fun Modifier.touchpadControls(
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
): Modifier =
    onIndirectPointerGesture(
        enabled = true,
        onClick = onTogglePlay,
        onSwipeBackward = onSeekBack,
        onSwipeForward = onSeekForward,
    )

private fun progressText(positionMs: Long, durationMs: Long): String =
    if (durationMs > 0L) {
        "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
    } else {
        formatDuration(positionMs)
    }

private fun sleepBadge(state: SleepTimerState): String? = when (state) {
    SleepTimerState.Idle, SleepTimerState.Expired -> null
    SleepTimerState.EndOfChapter -> "Sleep: end of chapter"
    is SleepTimerState.Running -> {
        val minutes = state.remainingMs / 60_000
        val seconds = (state.remainingMs % 60_000) / 1000
        "Sleep %d:%02d left".format(minutes, seconds)
    }
}
