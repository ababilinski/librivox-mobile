@file:Suppress("DEPRECATION")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.player

import android.widget.Toast
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown as FilledThumbDown
import androidx.compose.material.icons.filled.ThumbUp as FilledThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbDown as OutlinedThumbDown
import androidx.compose.material.icons.outlined.ThumbUp as OutlinedThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import coil3.imageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.DiscoverPrefill
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.SleepTimerPreset
import com.librivox.mobile.model.SleepTimerState
import com.librivox.mobile.model.authorSearchQuery
import com.librivox.mobile.model.canReadSourceText
import com.librivox.mobile.model.cleanTitle
import com.librivox.mobile.model.downloadedBookAssetCount
import com.librivox.mobile.model.isDownloaded
import com.librivox.mobile.model.missingDownloadAssetCount
import com.librivox.mobile.model.numberedChapterTitle
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.playback.FeedbackControlScope
import com.librivox.mobile.playback.MiniPlayerSkipMode
import com.librivox.mobile.playback.PlaybackFeedback
import com.librivox.mobile.playback.PlaybackSettingsStore
import com.librivox.mobile.playback.PlayerState
import com.librivox.mobile.ui.cast.CastRouteRepository
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.components.WavySlider
import com.librivox.mobile.ui.components.coverArtContentScale
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.components.rememberMorphedCornerShape
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.shareAudiobookChapter
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.Routes
import com.librivox.mobile.ui.player.bookmarks.ScrubSnapTarget
import com.librivox.mobile.ui.player.bookmarks.nearestBookmarkPositionAtPlayhead
import com.librivox.mobile.ui.player.bookmarks.snapScrubPosition
import com.librivox.mobile.ui.player.panels.BookmarksPanel
import com.librivox.mobile.ui.player.panels.ChaptersPanel
import com.librivox.mobile.ui.player.panels.DetailsPanel
import com.librivox.mobile.ui.player.panels.UpNextPanel
import com.librivox.mobile.ui.theme.PlayerSheetTheme
import com.librivox.mobile.ui.theme.rememberPlayerSheetColorScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val MiniHeightWithProgress: Dp = 104.dp
private val MiniHeightWithoutProgress: Dp = 72.dp
private val MiniDismissActivationDistance: Dp = 24.dp
private val MiniDismissThreshold: Dp = 96.dp
private val MiniScrubDismissExclusion: Dp = 24.dp
private val MiniDismissControlExclusion: Dp = 184.dp
private val PanelDividerDragHeight: Dp = 32.dp
private const val MiniDismissVelocityThresholdPx = 1100f
private val FullPlayerDismissCommitDistance: Dp = 56.dp
private const val FullPlayerDismissCommitVelocityPx = 700f
private const val BookmarkHintDismissMillis = 6_000L
private const val PlayerSheetActiveFraction = 0.02f
private const val MiniPlayerFadeOutFraction = 0.24f
private const val FullPlayerContentRevealStartFraction = 0.22f
private const val FullPlayerContentRevealDistance = 0.22f
private const val SecondaryPanelContentMountDelayMillis = 180L
private const val SecondaryPanelContentReadyFraction = 0.88f
private const val SubPanelHandleCommitFraction = 0.04f
private const val CoverSeekHintDismissMillis = 700L
private const val CoverHoldSpeed = 2f
private val CoverGestureHitPadding: Dp = 28.dp
private val CoverChapterSwipeMinThreshold: Dp = 56.dp
private const val CoverChapterSwipeThresholdFraction = 0.18f
private const val CoverChapterSwipeCommitVelocityPx = 850f
private const val CoverChapterSwipeCommitReadyFraction = 0.92f
private const val CoverChapterSwipeCommitTimeoutMillis = 420L

private enum class InnerSheet { None, Speed, Sleep, Bookmark }

private enum class CoverSeekHint {
    Backward,
    Forward,
}

private const val SkipBackSeconds = 10
private const val SkipForwardSeconds = 10

internal fun miniPlayerHeight(showProgressBar: Boolean): Dp =
    if (showProgressBar) MiniHeightWithProgress else MiniHeightWithoutProgress

private data class PlaybackUiSettings(
    val miniPlayerSkipMode: MiniPlayerSkipMode,
    val showMiniPlayerSecondaryControls: Boolean,
    val showMiniPlayerCastButton: Boolean,
    val showMiniPlayerProgressBar: Boolean,
    val feedbackControlScope: FeedbackControlScope,
    val coverArtDisplayMode: CoverArtDisplayMode,
)

@Composable
fun PlayerSheet(
    sheetState: PlayerSheetState,
    navController: NavHostController? = null,
    bottomNavigationVisible: Boolean = true,
    onOpenCastPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val rawState by graph.playerStateRepository.state.collectAsState()
    val castConnectionState by graph.app.castRouteRepository.connectionState.collectAsState()
    // Hold the last non-blank PlayerState while the local↔remote player swap is
    // mid-flight so the sheet doesn't blink out during a cast handoff.
    var latchedState by remember { mutableStateOf(rawState) }
    LaunchedEffect(rawState.hasMedia) {
        if (rawState.hasMedia) latchedState = rawState
    }
    val isCastHandoff = castConnectionState != CastRouteRepository.ConnectionState.Idle
    val isCastConnecting = castConnectionState == CastRouteRepository.ConnectionState.Connecting
    val state = when {
        rawState.hasMedia -> rawState
        isCastHandoff && latchedState.hasMedia -> latchedState
        else -> rawState
    }
    val sheetColors = rememberPlayerSheetColorScheme(state.artworkUri)
    val sheetHaptics = rememberHaptics()

    if (!state.hasMedia) return
    PrefetchPlayerArtwork(state.artworkUri)

    val scrubBarData = rememberScrubBarData(state)
    val playbackUiSettings = rememberPlaybackUiSettings()
    val fullPlayerMedia = rememberFullPlayerMedia(
        state = state,
        scrubBarData = scrubBarData,
        feedbackControlScope = playbackUiSettings.feedbackControlScope,
    )
    val miniHeight = miniPlayerHeight(playbackUiSettings.showMiniPlayerProgressBar)
    val fraction = sheetState.value
    val scrimAlpha = (fraction * 0.55f).coerceIn(0f, 0.55f)
    val isExpanded = fraction > 0.5f
    var childPanelActive by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) childPanelActive = false
    }

    val startFraction = remember(fraction > PlayerSheetActiveFraction) { fraction }
    PredictiveBackHandler(enabled = fraction > PlayerSheetActiveFraction) { progress ->
        try {
            progress.collect { event ->
                sheetState.snapTo((startFraction * (1f - event.progress)).coerceIn(0f, 1f))
            }
            sheetState.collapse()
        } catch (_: kotlinx.coroutines.CancellationException) {
            sheetState.animateTo(startFraction)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                    .clickable(enabled = isExpanded) { sheetState.collapse() }
                    .zIndex(0.5f),
            )
        }

        val dragScope = rememberCoroutineScope()
        BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(1f)) {
            val density = LocalDensity.current
            val systemNavigationInset = with(density) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            val navBarLift = if (bottomNavigationVisible) {
                80.dp + systemNavigationInset
            } else {
                systemNavigationInset
            }
            val miniHeightPx = with(density) { miniHeight.toPx() }
            val fullHeightPx = constraints.maxHeight.toFloat()
            val sheetDragRangePx = (fullHeightPx - miniHeightPx).coerceAtLeast(1f)
            val currentHeightPx = miniHeightPx + (fullHeightPx - miniHeightPx) * fraction
            val currentHeightDp = with(density) { currentHeightPx.toDp() }
            val navLiftPx = with(density) { navBarLift.toPx() }
            val verticalLiftPx = navLiftPx * (1f - fraction).coerceIn(0f, 1f)
            val sheetTopCornerDp = 24f * (1f - fraction).coerceIn(0f, 1f)
            val miniAlpha = (1f - (fraction / MiniPlayerFadeOutFraction)).coerceIn(0f, 1f)
            val fullAlpha = (
                (fraction - FullPlayerContentRevealStartFraction) /
                    FullPlayerContentRevealDistance
                ).coerceIn(0f, 1f)
            var miniDismissOffsetPx by remember { mutableFloatStateOf(0f) }
            var miniDismissThresholdPx by remember { mutableFloatStateOf(1f) }
            var sheetDragActive by remember { mutableStateOf(false) }
            var bodySheetDragInProgress by remember { mutableStateOf(false) }
            var suppressNextParentSheetStop by remember { mutableStateOf(false) }
            var sheetDragDeltaFraction by remember { mutableFloatStateOf(0f) }
            var sheetSnapJob by remember { mutableStateOf<Job?>(null) }
            var sheetSettleJob by remember { mutableStateOf<Job?>(null) }
            val miniDismissEnabled = miniAlpha > 0.01f && fraction < 0.36f
            val miniDismissProgress = if (miniDismissEnabled) {
                (abs(miniDismissOffsetPx) / miniDismissThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            } else {
                0f
            }

            LaunchedEffect(state.bookId, state.chapterId, miniDismissEnabled) {
                if (!miniDismissEnabled) {
                    miniDismissOffsetPx = 0f
                }
            }

            fun settleSheet(velocityPxPerSecond: Float) {
                val dragDeltaFraction = sheetDragDeltaFraction
                sheetDragDeltaFraction = 0f
                sheetDragActive = false
                sheetSettleJob?.cancel()
                sheetSettleJob = dragScope.launch {
                    sheetSnapJob?.cancelAndJoin()
                    sheetSnapJob = null
                    sheetState.settle(
                        velocityPxPerSecond = velocityPxPerSecond,
                        dragDeltaFraction = dragDeltaFraction,
                    )
                    sheetSettleJob = null
                }
            }

            fun beginSheetDrag() {
                if (!sheetDragActive) {
                    sheetDragDeltaFraction = 0f
                    sheetHaptics.dragStart()
                }
                sheetDragActive = true
                sheetSettleJob?.cancel()
                sheetSettleJob = null
                sheetSnapJob?.cancel()
            }

            fun dragSheetBy(dyPx: Float): Float {
                sheetSettleJob?.cancel()
                sheetSettleJob = null
                val previous = sheetState.value
                val next = (previous - (dyPx / sheetDragRangePx)).coerceIn(0f, 1f)
                val consumedFraction = next - previous
                if (consumedFraction == 0f) return 0f
                sheetDragDeltaFraction += consumedFraction
                sheetSnapJob?.cancel()
                sheetSnapJob = dragScope.launch { sheetState.snapTo(next) }
                return -consumedFraction * sheetDragRangePx
            }

            val draggableState = rememberDraggableState { dyPx ->
                dragSheetBy(dyPx)
            }

            PlayerSheetTheme(scheme = sheetColors) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(
                        topStart = sheetTopCornerDp.dp,
                        topEnd = sheetTopCornerDp.dp,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { androidx.compose.ui.unit.IntOffset(0, -verticalLiftPx.toInt()) }
                        .fillMaxWidth()
                        .height(currentHeightDp)
                        .graphicsLayer {
                            if (miniDismissEnabled) {
                                translationX = miniDismissOffsetPx
                                alpha = 1f - (miniDismissProgress * 0.36f)
                                rotationZ = (
                                    miniDismissOffsetPx / miniDismissThresholdPx.coerceAtLeast(1f)
                                ).coerceIn(-1f, 1f) * 1.4f
                            }
                        }
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            enabled = !childPanelActive,
                            onDragStarted = {
                                if (!bodySheetDragInProgress) {
                                    suppressNextParentSheetStop = false
                                }
                                beginSheetDrag()
                            },
                            onDragStopped = { velocity ->
                                if (bodySheetDragInProgress || suppressNextParentSheetStop) {
                                    suppressNextParentSheetStop = false
                                } else {
                                    settleSheet(velocity)
                                }
                            },
                        ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FullPlayerContent(
                            state = state,
                            media = fullPlayerMedia,
                            settings = playbackUiSettings,
                            onCollapse = { sheetState.collapse() },
                            onEnsureExpanded = { sheetState.expand() },
                            navController = navController,
                            onPanelActiveChanged = { childPanelActive = it },
                            onOpenCastPicker = onOpenCastPicker,
                            isCastConnecting = isCastConnecting,
                            onSheetDragStarted = {
                                bodySheetDragInProgress = true
                                beginSheetDrag()
                            },
                            onSheetDrag = { dyPx -> dragSheetBy(dyPx) },
                            onSheetDragStopped = { velocity ->
                                bodySheetDragInProgress = false
                                suppressNextParentSheetStop = true
                                settleSheet(velocity)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = fullAlpha },
                        )
                        if (miniAlpha > 0.01f) {
                            MiniPlayerContent(
                                state = state,
                                scrubBarData = scrubBarData,
                                settings = playbackUiSettings,
                                onExpand = { sheetState.expand() },
                                onDismiss = { graph.playerStateRepository.dismissPlayback() },
                                enabled = fraction < 0.36f,
                                onDismissOffsetChange = { offsetPx, thresholdPx ->
                                    miniDismissOffsetPx = offsetPx
                                    miniDismissThresholdPx = thresholdPx.coerceAtLeast(1f)
                                },
                                onOpenCastPicker = onOpenCastPicker,
                                isCastConnecting = isCastConnecting,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = miniAlpha
                                        val scale = 1f - (0.04f * fraction.coerceIn(0f, 1f))
                                        scaleX = scale
                                        scaleY = scale
                                    },
                            )
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun rememberPlaybackUiSettings(): PlaybackUiSettings {
    val graph = LocalAppGraph.current
    val store = graph.app.playbackSettingsStore
    var settings by remember(store) { mutableStateOf(store.playbackUiSettings()) }
    DisposableEffect(store) {
        val listener = store.registerPlaybackUiSettingsListener {
            settings = store.playbackUiSettings()
        }
        onDispose { store.unregisterListener(listener) }
    }
    return settings
}

private fun PlaybackSettingsStore.playbackUiSettings(): PlaybackUiSettings =
    PlaybackUiSettings(
        miniPlayerSkipMode = miniPlayerSkipMode,
        showMiniPlayerSecondaryControls = showMiniPlayerSecondaryControls,
        showMiniPlayerCastButton = showMiniPlayerCastButton,
        showMiniPlayerProgressBar = showMiniPlayerProgressBar,
        feedbackControlScope = feedbackControlScope,
        coverArtDisplayMode = coverArtDisplayMode,
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayerContent(
    state: PlayerState,
    scrubBarData: ScrubBarData,
    settings: PlaybackUiSettings,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onDismissOffsetChange: (offsetPx: Float, thresholdPx: Float) -> Unit,
    onOpenCastPicker: () -> Unit,
    isCastConnecting: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val castSelectedRoute by graph.app.castRouteRepository.selectedRoute.collectAsState()
    var dismissOffsetPx by remember { mutableFloatStateOf(0f) }
    var dismissSettleJob by remember { mutableStateOf<Job?>(null) }
    val dismissSettleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val dismissActivationDistancePx = with(density) { MiniDismissActivationDistance.toPx() }
    val dismissThresholdPx = with(density) { MiniDismissThreshold.toPx() }
    val scrubExclusionPx = with(density) {
        if (settings.showMiniPlayerProgressBar) MiniScrubDismissExclusion.toPx() else 0f
    }
    val controlExclusionPx = with(density) { MiniDismissControlExclusion.toPx() }
    val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis

    fun updateDismissOffset(value: Float) {
        dismissOffsetPx = value
        onDismissOffsetChange(value, dismissThresholdPx)
    }

    LaunchedEffect(state.bookId, state.chapterId, enabled) {
        dismissSettleJob?.cancel()
        updateDismissOffset(0f)
    }

    fun animateDismissOffset(targetValue: Float, afterAnimation: (() -> Unit)? = null) {
        dismissSettleJob?.cancel()
        val startValue = dismissOffsetPx
        dismissSettleJob = scope.launch {
            Animatable(startValue).animateTo(
                targetValue = targetValue,
                animationSpec = dismissSettleSpec,
            ) {
                updateDismissOffset(value)
            }
            updateDismissOffset(targetValue)
            afterAnimation?.invoke()
        }
    }

    fun Modifier.miniDismissSwipe(): Modifier = pointerInput(
        enabled,
        dismissActivationDistancePx,
        dismissThresholdPx,
        scrubExclusionPx,
        controlExclusionPx,
        longPressTimeoutMillis,
    ) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            if (
                down.position.y >= size.height - scrubExclusionPx ||
                down.position.x >= size.width - controlExclusionPx
            ) {
                return@awaitEachGesture
            }
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var lastPosition = down.position
            var pendingDx = 0f
            var pendingDy = 0f
            var dragging = false
            var longPressHapticSent = false
            var thresholdHapticSent = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    break
                }

                val delta = change.position - lastPosition
                lastPosition = change.position
                if (
                    !longPressHapticSent &&
                    change.uptimeMillis - down.uptimeMillis >= longPressTimeoutMillis
                ) {
                    haptics.longPress()
                    longPressHapticSent = true
                }
                var activatedThisEvent = false
                if (!dragging) {
                    pendingDx += delta.x
                    pendingDy += delta.y
                    val horizontal = abs(pendingDx)
                    val vertical = abs(pendingDy)
                    when {
                        horizontal > dismissActivationDistancePx && horizontal > vertical * 1.25f -> {
                            dragging = true
                            if (!longPressHapticSent) {
                                haptics.longPress()
                                longPressHapticSent = true
                            }
                            haptics.dragStart()
                            dismissSettleJob?.cancel()
                            val direction = if (pendingDx < 0f) -1f else 1f
                            updateDismissOffset(pendingDx - (direction * dismissActivationDistancePx))
                            activatedThisEvent = true
                        }
                        vertical > dismissActivationDistancePx && vertical > horizontal -> break
                    }
                }
                if (dragging) {
                    if (!activatedThisEvent) {
                        updateDismissOffset(dismissOffsetPx + delta.x)
                    }
                    change.consume()
                    if (!thresholdHapticSent && abs(dismissOffsetPx) >= dismissThresholdPx) {
                        haptics.tick()
                        thresholdHapticSent = true
                    }
                }
            }

            if (dragging) {
                val velocityX = velocityTracker.calculateVelocity().x
                val releaseOffset = dismissOffsetPx
                val shouldDismiss = abs(releaseOffset) >= dismissThresholdPx ||
                    abs(velocityX) >= MiniDismissVelocityThresholdPx
                if (shouldDismiss) {
                    haptics.confirm()
                    val direction = if ((velocityX.takeIf { abs(it) > 1f } ?: releaseOffset) < 0f) -1f else 1f
                    animateDismissOffset(direction * (size.width.toFloat() + dismissThresholdPx), onDismiss)
                } else {
                    haptics.gestureEnd()
                    animateDismissOffset(0f)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .miniDismissSwipe()
            .clickable(enabled = enabled) {
                haptics.key()
                onExpand()
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .coverArtRowSize(settings.coverArtDisplayMode, 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                AnimatedCoverImage(
                    model = state.artworkUri,
                    contentDescription = state.title,
                    contentScale = settings.coverArtDisplayMode.coverArtContentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = miniBookTitle(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                miniChapterTitle(state)?.let { chapter ->
                    Text(
                        text = chapter,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val castStatus = when {
                    isCastConnecting -> castSelectedRoute?.let { "Connecting to ${it.name}…" }
                    state.isRemotePlayback -> castSelectedRoute?.let { "Playing on ${it.name}" }
                    else -> null
                }
                val combinedStatus = listOfNotNull(miniStatusLine(state), castStatus)
                    .joinToString(" • ")
                    .takeIf { it.isNotBlank() }
                combinedStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (settings.showMiniPlayerCastButton && state.canCast) {
                IconButton(
                    onClick = {
                        haptics.key()
                        onOpenCastPicker()
                    },
                ) {
                    if (isCastConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isRemotePlayback) Icons.Filled.CastConnected else Icons.Filled.Cast,
                            contentDescription = if (state.isRemotePlayback) "Cast settings" else "Cast",
                        )
                    }
                }
            }
            if (settings.showMiniPlayerSecondaryControls) {
                when (settings.miniPlayerSkipMode) {
                    MiniPlayerSkipMode.Chapter -> {
                        IconButton(
                            onClick = {
                                haptics.key()
                                graph.playerStateRepository.seekToPreviousChapter()
                            },
                            enabled = state.canSeekPrevious,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous chapter",
                            )
                        }
                    }
                    MiniPlayerSkipMode.TenSeconds -> {
                        IconButton(
                            onClick = {
                                haptics.key()
                                graph.playerStateRepository.seekBack()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Skip back $SkipBackSeconds seconds",
                            )
                        }
                    }
                }
            }
            val playShape = rememberMorphedCornerShape(
                targetCornerDp = if (state.isPlaying) 14f else 28f,
            )
            FilledIconButton(
                onClick = {
                    haptics.key()
                    graph.playerStateRepository.playPause()
                },
                shape = playShape,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
            if (settings.showMiniPlayerSecondaryControls) {
                when (settings.miniPlayerSkipMode) {
                    MiniPlayerSkipMode.Chapter -> {
                        IconButton(
                            onClick = {
                                haptics.key()
                                graph.playerStateRepository.seekToNextChapter()
                            },
                            enabled = state.hasNext,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next chapter",
                            )
                        }
                    }
                    MiniPlayerSkipMode.TenSeconds -> {
                        IconButton(
                            onClick = {
                                haptics.key()
                                graph.playerStateRepository.seekForward()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Skip forward $SkipForwardSeconds seconds",
                            )
                        }
                    }
                }
            }
        }
        if (settings.showMiniPlayerProgressBar) {
            MiniScrubBar(
                state = state,
                scrubBarData = scrubBarData,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun PrefetchPlayerArtwork(artworkUri: String?) {
    val context = LocalContext.current
    LaunchedEffect(artworkUri) {
        val model = artworkUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        runCatching {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(model)
                    .build(),
            )
        }
    }
}

@Composable
private fun rememberFullPlayerMedia(
    state: PlayerState,
    scrubBarData: ScrubBarData,
    feedbackControlScope: FeedbackControlScope,
): FullPlayerMedia {
    val graph = LocalAppGraph.current
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val feedbackRevision by graph.app.playbackFeedbackStore.revision.collectAsState()
    return remember(
        libraryState,
        state.bookId,
        state.chapterId,
        state.author,
        feedbackControlScope,
        feedbackRevision,
        scrubBarData,
    ) {
        val bookId = state.bookId?.takeIf { it.isNotBlank() }
        val currentBook = libraryState?.bookById(bookId)
        val currentChapter = currentBook?.chapters?.firstOrNull { it.id == state.chapterId }
        val authorSearchQuery = (currentBook?.authorSearchQuery() ?: state.author)
            ?.takeIf { it.isNotBlank() }
        val missingDownloadCount = currentBook?.missingDownloadAssetCount() ?: 0
        val downloadedCount = currentBook?.downloadedBookAssetCount() ?: 0
        FullPlayerMedia(
            currentBook = currentBook,
            currentChapter = currentChapter,
            authorSearchQuery = authorSearchQuery,
            missingDownloadCount = missingDownloadCount,
            downloadedCount = downloadedCount,
            inLibrary = currentBook?.libraryStatus == LibraryStatus.InLibrary,
            playbackFeedback = graph.app.playbackFeedbackStore.feedbackFor(
                state.bookId,
                state.chapterId,
                feedbackControlScope,
            ),
            feedbackControlScope = feedbackControlScope,
            scrubBarData = scrubBarData,
        )
    }
}

private data class FullPlayerMedia(
    val currentBook: AudioBook?,
    val currentChapter: AudioBookChapter?,
    val authorSearchQuery: String?,
    val missingDownloadCount: Int,
    val downloadedCount: Int,
    val inLibrary: Boolean,
    val playbackFeedback: PlaybackFeedback,
    val feedbackControlScope: FeedbackControlScope,
    val scrubBarData: ScrubBarData,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerContent(
    state: PlayerState,
    media: FullPlayerMedia,
    settings: PlaybackUiSettings,
    onCollapse: () -> Unit,
    onEnsureExpanded: () -> Unit,
    navController: NavHostController?,
    onPanelActiveChanged: (Boolean) -> Unit,
    onOpenCastPicker: () -> Unit,
    isCastConnecting: Boolean,
    onSheetDragStarted: () -> Unit,
    onSheetDrag: (dyPx: Float) -> Float,
    onSheetDragStopped: (velocityPxPerSecond: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var innerSheet by remember { mutableStateOf(InnerSheet.None) }
    var bookmarkHintBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkForNoteSheet by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkSnackbarJob by remember { mutableStateOf<Job?>(null) }
    var overflowSheetOpen by remember { mutableStateOf(false) }
    val subPanelState = rememberPlayerSubPanelState()
    var subPanelContentAtTop by remember { mutableStateOf(true) }
    val currentBook = media.currentBook
    val currentChapter = media.currentChapter
    val authorSearchQuery = media.authorSearchQuery
    val missingDownloadCount = media.missingDownloadCount
    val downloadedCount = media.downloadedCount
    val inLibrary = media.inLibrary
    val playbackFeedback = media.playbackFeedback
    val feedbackControlScope = media.feedbackControlScope
    val scrubBarData = media.scrubBarData
    val coverArtDisplayMode = settings.coverArtDisplayMode
    var scrubPreviewPositionMs by remember(state.bookId, state.chapterId) {
        mutableStateOf<Long?>(null)
    }
    var coverHoldRestoreSpeed by remember(state.bookId, state.chapterId) {
        mutableStateOf<PlaybackSpeed?>(null)
    }
    val playheadBookmark = remember(scrubBarData, scrubPreviewPositionMs, state.positionMs) {
        scrubBarData.bookmarkAtPlayhead(scrubPreviewPositionMs ?: state.positionMs)
    }
    LaunchedEffect(scrubPreviewPositionMs, state.positionMs) {
        val previewPositionMs = scrubPreviewPositionMs ?: return@LaunchedEffect
        if (abs(state.positionMs - previewPositionMs) <= 250L) {
            scrubPreviewPositionMs = null
        }
    }

    val panelOpen by remember {
        derivedStateOf { subPanelState.target != SubPanel.None || subPanelState.fraction > 0.01f }
    }
    var subPanelBodyReady by remember { mutableStateOf(false) }

    LaunchedEffect(panelOpen) {
        onPanelActiveChanged(panelOpen)
        if (panelOpen) {
            onEnsureExpanded()
        }
    }
    LaunchedEffect(subPanelState.target) {
        subPanelContentAtTop = true
        subPanelBodyReady = false
        val openingPanel = subPanelState.target
        if (openingPanel != SubPanel.None) {
            delay(SecondaryPanelContentMountDelayMillis)
            snapshotFlow { subPanelState.fraction }
                .first { fraction ->
                    fraction >= SecondaryPanelContentReadyFraction ||
                        subPanelState.target != openingPanel
                }
            if (subPanelState.target == openingPanel) {
                subPanelBodyReady = true
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalHeight = constraints.maxHeight.toFloat()
        val topChromePx = WindowInsets.statusBars.getTop(density) + with(density) { 72.dp.toPx() }
        val maxPanelHeightPx = (totalHeight - topChromePx).coerceAtLeast(totalHeight * 0.64f)
        val panelHeightDp = with(density) { maxPanelHeightPx.toDp() }
        LaunchedEffect(maxPanelHeightPx) {
            subPanelState.updateAnchors(maxPanelHeightPx)
        }
        val panelOffsetPx = subPanelState
            .panelOffsetPx(maxPanelHeightPx)
            .coerceIn(0f, maxPanelHeightPx)
        val panelFraction = subPanelState.openFraction(maxPanelHeightPx)
        val panelHandleCommitThresholdPx = maxPanelHeightPx * SubPanelHandleCommitFraction
        val fullPlayerDismissCommitDistancePx = with(density) {
            FullPlayerDismissCommitDistance.toPx()
        }
        val fullPlayerScrollState = rememberScrollState()
        var fullPlayerDismissDragActive by remember { mutableStateOf(false) }
        var fullPlayerDismissDragDistancePx by remember { mutableFloatStateOf(0f) }
        fun finishFullPlayerDismissDrag(velocityY: Float) {
            if (!fullPlayerDismissDragActive) {
                return
            }
            val releaseVelocityY = when {
                velocityY > FullPlayerDismissCommitVelocityPx -> velocityY
                fullPlayerDismissDragDistancePx >= fullPlayerDismissCommitDistancePx ->
                    FullPlayerDismissCommitVelocityPx
                else -> velocityY.coerceAtLeast(0f)
            }
            fullPlayerDismissDragActive = false
            fullPlayerDismissDragDistancePx = 0f
            onSheetDragStopped(releaseVelocityY)
        }
        val fullPlayerDismissScroll = remember(
            panelOpen,
            onSheetDragStarted,
            onSheetDrag,
            onSheetDragStopped,
        ) {
            object : NestedScrollConnection {
                private var dragActive = false

                private fun reset() {
                    dragActive = false
                    fullPlayerDismissDragActive = false
                    fullPlayerDismissDragDistancePx = 0f
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || panelOpen) {
                        return Offset.Zero
                    }
                    if (!dragActive && available.y <= 0f) {
                        return Offset.Zero
                    }
                    if (!dragActive) {
                        dragActive = true
                        fullPlayerDismissDragActive = true
                        fullPlayerDismissDragDistancePx = 0f
                        onSheetDragStarted()
                    }
                    val consumedY = onSheetDrag(available.y)
                    fullPlayerDismissDragDistancePx =
                        (fullPlayerDismissDragDistancePx + consumedY).coerceAtLeast(0f)
                    return if (consumedY == 0f) {
                        Offset.Zero
                    } else {
                        Offset(0f, consumedY)
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (panelOpen) {
                        reset()
                        return Velocity.Zero
                    }
                    if (!dragActive) {
                        return Velocity.Zero
                    }
                    finishFullPlayerDismissDrag(available.y)
                    reset()
                    return Velocity(0f, available.y)
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!dragActive) {
                        return Velocity.Zero
                    }
                    finishFullPlayerDismissDrag(available.y)
                    reset()
                    return Velocity.Zero
                }
            }
        }
        fun Modifier.trackFullPlayerDismissRelease(enabled: Boolean): Modifier {
            if (!enabled) return this
            return pointerInput(panelOpen, onSheetDragStopped) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })

                    finishFullPlayerDismissDrag(velocityTracker.calculateVelocity().y)
                }
            }
        }

        fun closePanelToNowPlaying() {
            scope.launch {
                subPanelState.settleClosed()
                onEnsureExpanded()
            }
        }
        LaunchedEffect(subPanelState) {
            snapshotFlow {
                subPanelState.anchoredState.currentValue to subPanelState.anchoredState.targetValue
            }.collect {
                subPanelState.clearPanelIfClosed()
            }
        }
        val widthBoundArtworkHeight = maxWidth * 0.82f
        val heightBoundArtworkHeight = maxHeight * 0.34f
        val heroArtworkHeightCandidate = if (widthBoundArtworkHeight < heightBoundArtworkHeight) {
            widthBoundArtworkHeight
        } else {
            heightBoundArtworkHeight
        }
        val heroArtworkHeight = if (heroArtworkHeightCandidate < 200.dp) {
            200.dp
        } else {
            heroArtworkHeightCandidate
        }
        val chapterSwipeMinThresholdPx = with(density) { CoverChapterSwipeMinThreshold.toPx() }
        var fullPlayerPageWidthPx by remember { mutableFloatStateOf(1f) }
        val fullPlayerPageWidthDp = with(density) { fullPlayerPageWidthPx.toDp() }
        var pageSwipeOffsetPx by remember { mutableFloatStateOf(0f) }
        var pageSwipeDragging by remember { mutableStateOf(false) }
        var pageSwipeCommitActive by remember { mutableStateOf(false) }
        var pageSwipeSnapOffsetChange by remember { mutableStateOf(false) }
        var pageSwipeSettledOverrideState by remember { mutableStateOf<PlayerState?>(null) }
        var pageSwipeFrozenBaseState by remember { mutableStateOf<PlayerState?>(null) }
        var pageSwipeDragTotalPx by remember { mutableFloatStateOf(0f) }
        val swipePageBaseState = pageSwipeSettledOverrideState ?: pageSwipeFrozenBaseState ?: state
        val pageSwipeDisplayOffsetPx by animateFloatAsState(
            targetValue = pageSwipeOffsetPx,
            animationSpec = if (pageSwipeDragging || pageSwipeSnapOffsetChange) {
                snap()
            } else {
                MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            },
            label = "NowPlayingChapterSwipeOffset",
        )
        val pageSwipeLayoutOffsetPx = if (pageSwipeSnapOffsetChange) {
            pageSwipeOffsetPx
        } else {
            pageSwipeDisplayOffsetPx
        }
        val currentChapterIndex = remember(
            currentBook,
            swipePageBaseState.chapterId,
            swipePageBaseState.mediaItemIndex,
        ) {
            currentBook
                ?.chapters
                ?.indexOfFirst { it.id == swipePageBaseState.chapterId }
                ?.takeIf { it >= 0 }
                ?: swipePageBaseState.mediaItemIndex
        }
        val previousSwipePageState = remember(currentBook, currentChapterIndex, swipePageBaseState) {
            currentBook
                ?.chapters
                ?.getOrNull(currentChapterIndex - 1)
                ?.let { chapter ->
                    swipePageBaseState.chapterSwipePreviewState(
                        book = currentBook,
                        chapter = chapter,
                        chapterIndex = currentChapterIndex - 1,
                    )
                }
        }
        val nextSwipePageState = remember(currentBook, currentChapterIndex, swipePageBaseState) {
            currentBook
                ?.chapters
                ?.getOrNull(currentChapterIndex + 1)
                ?.let { chapter ->
                    swipePageBaseState.chapterSwipePreviewState(
                        book = currentBook,
                        chapter = chapter,
                        chapterIndex = currentChapterIndex + 1,
                    )
                }
        }

        fun resetPageChapterSwipe() {
            pageSwipeDragging = false
            pageSwipeCommitActive = false
            pageSwipeSnapOffsetChange = false
            pageSwipeSettledOverrideState = null
            pageSwipeFrozenBaseState = null
            pageSwipeOffsetPx = 0f
            pageSwipeDragTotalPx = 0f
        }

        suspend fun awaitCommittedPageOffset(targetWidthPx: Float) {
            val readyOffsetPx = targetWidthPx * CoverChapterSwipeCommitReadyFraction
            withTimeoutOrNull(CoverChapterSwipeCommitTimeoutMillis) {
                snapshotFlow { abs(pageSwipeDisplayOffsetPx) }
                    .first { it >= readyOffsetPx }
            }
        }

        suspend fun snapToSettledChapter(
            settledPageState: PlayerState?,
        ) {
            pageSwipeSnapOffsetChange = true
            pageSwipeSettledOverrideState = settledPageState
            pageSwipeFrozenBaseState = null
            pageSwipeOffsetPx = 0f
            pageSwipeDragTotalPx = 0f
            withFrameNanos { }
            pageSwipeCommitActive = false
            pageSwipeSnapOffsetChange = false
        }

        fun pageSwipeOffsetForDrag(dx: Float): Float {
            val maxOffsetPx = fullPlayerPageWidthPx
            val canMoveInDirection = if (dx < 0f) {
                state.hasNext
            } else {
                state.hasPrevious
            }
            val clamped = dx.coerceIn(-maxOffsetPx, maxOffsetPx)
            return if (canMoveInDirection) clamped else clamped * 0.18f
        }

        fun updatePageChapterSwipe(dx: Float) {
            if (!state.hasPrevious && !state.hasNext) return
            pageSwipeDragging = true
            pageSwipeCommitActive = false
            pageSwipeOffsetPx = pageSwipeOffsetForDrag(dx)
        }

        fun finishPageChapterSwipe(dx: Float, dy: Float = 0f, velocityX: Float = 0f) {
            val thresholdPx = (fullPlayerPageWidthPx * CoverChapterSwipeThresholdFraction)
                .coerceAtLeast(chapterSwipeMinThresholdPx)
            val horizontalIntent =
                abs(dx) > abs(dy) * 1.05f ||
                    abs(velocityX) >= CoverChapterSwipeCommitVelocityPx
            val shouldGoNext =
                horizontalIntent &&
                    state.hasNext &&
                    (dx <= -thresholdPx || velocityX <= -CoverChapterSwipeCommitVelocityPx)
            val shouldGoPrevious =
                horizontalIntent &&
                    state.hasPrevious &&
                    (dx >= thresholdPx || velocityX >= CoverChapterSwipeCommitVelocityPx)
            pageSwipeDragging = false
            pageSwipeDragTotalPx = 0f
            when {
                shouldGoNext -> {
                    pageSwipeCommitActive = true
                    pageSwipeFrozenBaseState = state
                    val targetWidthPx = fullPlayerPageWidthPx
                    pageSwipeOffsetPx = -targetWidthPx
                    haptics.tick()
                    graph.playerStateRepository.seekToNextChapter()
                    scope.launch {
                        awaitCommittedPageOffset(targetWidthPx)
                        snapToSettledChapter(nextSwipePageState)
                    }
                }
                shouldGoPrevious -> {
                    pageSwipeCommitActive = true
                    pageSwipeFrozenBaseState = state
                    val targetWidthPx = fullPlayerPageWidthPx
                    val targetIndex = (state.mediaItemIndex - 1).coerceAtLeast(0)
                    pageSwipeOffsetPx = targetWidthPx
                    haptics.tick()
                    graph.playerStateRepository.seekToChapter(targetIndex)
                    scope.launch {
                        awaitCommittedPageOffset(targetWidthPx)
                        snapToSettledChapter(previousSwipePageState)
                    }
                }
                else -> {
                    resetPageChapterSwipe()
                }
            }
        }

        LaunchedEffect(state.bookId, state.chapterId) {
            val settledOverride = pageSwipeSettledOverrideState
            if (
                settledOverride != null &&
                settledOverride.bookId == state.bookId &&
                settledOverride.chapterId == state.chapterId
            ) {
                pageSwipeSettledOverrideState = null
            } else if (!pageSwipeCommitActive && !pageSwipeSnapOffsetChange) {
                resetPageChapterSwipe()
            }
        }
        val pageChapterSwipeEnabled = !panelOpen &&
            !pageSwipeCommitActive &&
            pageSwipeSettledOverrideState == null &&
            (state.hasPrevious || state.hasNext)
        val pageChapterSwipeDraggableState = rememberDraggableState { deltaPx ->
            if (!pageChapterSwipeEnabled) return@rememberDraggableState
            pageSwipeDragTotalPx += deltaPx
            updatePageChapterSwipe(pageSwipeDragTotalPx)
        }

        fun Modifier.panelDismissDrag(): Modifier = pointerInput(
            panelOpen,
            panelHandleCommitThresholdPx,
            subPanelState.target,
        ) {
            if (!panelOpen) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                var lastY = down.position.y
                var pendingDy = 0f
                var dragDeltaPx = 0f
                var dragging = false
                var thresholdHapticSent = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        break
                    }

                    val dy = change.position.y - lastY
                    lastY = change.position.y
                    if (!dragging) {
                        pendingDy += dy
                        when {
                            pendingDy > 3f -> {
                                dragging = true
                                haptics.dragStart()
                            }
                            pendingDy < -8f -> break
                        }
                    }
                    if (dragging) {
                        val consumed = subPanelState.dispatchRawDelta(dy)
                        if (consumed != 0f) {
                            dragDeltaPx += consumed
                            change.consume()
                        }
                        if (!thresholdHapticSent && dragDeltaPx >= panelHandleCommitThresholdPx) {
                            haptics.tick()
                            thresholdHapticSent = true
                        }
                    }
                }
                if (dragging) {
                    val velocityY = velocityTracker.calculateVelocity().y
                    scope.launch {
                        subPanelState.settleRawDrag(
                            velocityPxPerSecond = velocityY,
                            dragDeltaPx = dragDeltaPx,
                            commitFraction = SubPanelHandleCommitFraction,
                        )
                        onEnsureExpanded()
                        haptics.gestureEnd()
                    }
                }
            }
        }

        fun openDiscoverFilter(query: String?, field: CatalogSearchField, language: String = "") {
            val trimmed = query?.trim().orEmpty()
            if (trimmed.isBlank()) return
            haptics.key()
            graph.app.discoverPrefill.value = DiscoverPrefill(trimmed, field, language)
            onCollapse()
            val controller = navController ?: return
            controller.clearBackStack(Routes.DISCOVER)
            controller.popBackStack(Routes.DISCOVER, inclusive = true)
            controller.navigate(Routes.DISCOVER) {
                popUpTo(controller.graph.findStartDestination().id) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }

        fun openCurrentBook() {
            val bookId = state.bookId?.takeIf { it.isNotBlank() } ?: return
            haptics.key()
            onCollapse()
            navController?.navigate(Routes.bookDetail(bookId)) {
                launchSingleTop = true
            }
        }

        fun openCurrentReader() {
            val book = currentBook?.takeIf { it.canReadSourceText() } ?: return
            haptics.key()
            onCollapse()
            navController?.navigate(Routes.bookReader(book.id, state.chapterId)) {
                launchSingleTop = true
            }
        }

        fun showBookmarkNoteHint(bookmark: Bookmark) {
            bookmarkSnackbarJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            bookmarkHintBookmark = bookmark
            bookmarkSnackbarJob = scope.launch {
                delay(BookmarkHintDismissMillis)
                if (bookmarkHintBookmark?.id == bookmark.id) {
                    bookmarkHintBookmark = null
                }
                bookmarkSnackbarJob = null
            }
        }

        fun addCurrentChapterBookmark(openNoteSheet: Boolean = false) {
            val bookId = state.bookId?.takeIf { it.isNotBlank() } ?: return
            val chapterId = state.chapterId?.takeIf { it.isNotBlank() } ?: return
            haptics.confirm()
            scope.launch {
                val bookmark = graph.app.bookmarkRepository.add(bookId, chapterId, state.positionMs, "")
                if (openNoteSheet) {
                    bookmarkSnackbarJob?.cancel()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    bookmarkHintBookmark = null
                    bookmarkForNoteSheet = bookmark
                } else {
                    showBookmarkNoteHint(bookmark)
                }
            }
        }

        fun removeCurrentBookmark(bookmark: Bookmark) {
            haptics.confirm()
            bookmarkSnackbarJob?.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
            bookmarkHintBookmark = null
            bookmarkForNoteSheet = null
            bookmarkSnackbarJob = scope.launch {
                graph.app.bookmarkRepository.remove(bookmark.id)
                val result = snackbarHostState.showSnackbar(
                    message = "Bookmark removed",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    graph.app.bookmarkRepository.restore(bookmark)
                }
            }
        }

        fun saveCurrentBookToLibrary() {
            val book = currentBook ?: return
            haptics.confirm()
            scope.launch {
                graph.app.libraryRepository.addToLibrary(book)
                snackbarHostState.showSnackbar(
                    if (inLibrary) "Already in your library" else "Saved to library",
                )
            }
        }

        fun downloadCurrentBook() {
            val book = currentBook ?: return
            haptics.confirm()
            scope.launch {
                if (missingDownloadCount == 0) {
                    snackbarHostState.showSnackbar("All available downloads are saved")
                } else {
                    graph.app.downloadManager.downloadAll(book)
                    snackbarHostState.showSnackbar("Download queued")
                }
            }
        }

        fun deleteCurrentBookDownloads() {
            val book = currentBook ?: return
            haptics.confirm()
            scope.launch {
                graph.app.downloadManager.deleteDownloads(book)
                snackbarHostState.showSnackbar("Downloads deleted")
            }
        }

        fun playCurrentChapterNext() {
            val book = currentBook ?: return
            val chapterId = state.chapterId?.takeIf { it.isNotBlank() } ?: currentChapter?.id ?: return
            val items = book.toMediaItems(context).filter { it.mediaId.endsWith("::$chapterId") }
            if (items.isEmpty()) return
            haptics.confirm()
            graph.playerStateRepository.addAfterCurrent(items)
            scope.launch {
                graph.app.queueRepository.playNext(book.id, chapterId)
                snackbarHostState.showSnackbar("Added next in queue")
            }
        }

        fun shareCurrentMedia() {
            val book = currentBook ?: return
            val chapter = currentChapter
            haptics.key()
            if (chapter != null) {
                shareAudiobookChapter(context, book, chapter)
            } else {
                shareAudiobook(context, book)
            }
        }

        fun startCoverSpeedHold() {
            if (coverHoldRestoreSpeed != null) return
            coverHoldRestoreSpeed = state.speed
            haptics.longPress()
            graph.playerStateRepository.setTemporarySpeed(CoverHoldSpeed)
        }

        fun endCoverSpeedHold() {
            val restoreSpeed = coverHoldRestoreSpeed ?: return
            coverHoldRestoreSpeed = null
            graph.playerStateRepository.setTemporarySpeed(restoreSpeed.value)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top app row: collapse + marquee book title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (panelOpen) closePanelToNowPlaying() else onCollapse()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (panelOpen) "Back to now playing" else "Collapse",
                    )
                }
                Text(
                    text = state.albumTitle ?: state.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(iterations = Int.MAX_VALUE),
                )
                CastRouteAction(
                    canCast = state.canCast,
                    unavailableReason = state.castUnavailableReason,
                    isRemotePlayback = state.isRemotePlayback,
                    isConnecting = isCastConnecting,
                    onClick = onOpenCastPicker,
                )
                IconButton(
                    onClick = {
                        haptics.key()
                        overflowSheetOpen = true
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More audiobook actions",
                    )
                }
            }

            // Standard full player layout stays mounted behind the sub-panel.
            // The child panel animates like the main sheet instead of replacing
            // the whole screen and handing drags back to the mini-player sheet.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .trackFullPlayerDismissRelease(enabled = !panelOpen)
                    .nestedScroll(fullPlayerDismissScroll)
                    .verticalScroll(fullPlayerScrollState)
                    .padding(horizontal = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { size ->
                            fullPlayerPageWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        },
                ) {
                    previousSwipePageState?.let { previousState ->
                        LateralChapterPreviewPage(
                            state = previousState,
                            heroArtworkHeight = heroArtworkHeight,
                            pageWidth = fullPlayerPageWidthDp,
                            coverArtDisplayMode = coverArtDisplayMode,
                            modifier = Modifier
                                .width(fullPlayerPageWidthDp)
                                .offset {
                                    IntOffset(
                                        x = (pageSwipeLayoutOffsetPx - fullPlayerPageWidthPx).roundToInt(),
                                        y = 0,
                                    )
                                },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .width(fullPlayerPageWidthDp)
                            .offset {
                                IntOffset(
                                    x = pageSwipeLayoutOffsetPx.roundToInt(),
                                    y = 0,
                                )
                            }
                            .draggable(
                                state = pageChapterSwipeDraggableState,
                                orientation = Orientation.Horizontal,
                                enabled = pageChapterSwipeEnabled,
                                onDragStarted = {
                                    pageSwipeDragTotalPx = 0f
                                    pageSwipeDragging = true
                                    pageSwipeCommitActive = false
                                    pageSwipeFrozenBaseState = state
                                    haptics.dragStart()
                                },
                                onDragStopped = { velocity ->
                                    finishPageChapterSwipe(pageSwipeDragTotalPx, velocityX = velocity)
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        HeroArtwork(
                            state = swipePageBaseState,
                            coverArtDisplayMode = coverArtDisplayMode,
                            onOpenReader = currentBook
                                ?.takeIf { it.canReadSourceText() }
                                ?.let { { openCurrentReader() } },
                            speedHoldActive = coverHoldRestoreSpeed != null,
                            onDoubleTapLeft = {
                                haptics.tick()
                                graph.playerStateRepository.seekBack()
                            },
                            onDoubleTapRight = {
                                haptics.tick()
                                graph.playerStateRepository.seekForward()
                            },
                            onHoldStart = { startCoverSpeedHold() },
                            onHoldEnd = { endCoverSpeedHold() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroArtworkHeight + CoverGestureHitPadding + CoverGestureHitPadding),
                        )
                        HeroIdentity(
                            state = swipePageBaseState,
                            onBookClick = { openCurrentBook() },
                            onAuthorClick = {
                                openDiscoverFilter(
                                    swipePageBaseState.author,
                                    CatalogSearchField.Author,
                                    currentBook?.language.orEmpty(),
                                )
                            },
                        )
                        FeedbackAndBookmarkRow(
                            state = swipePageBaseState,
                            playbackFeedback = playbackFeedback,
                            feedbackControlScope = feedbackControlScope,
                            bookmarkSelected = playheadBookmark != null,
                            onSpeedClick = { innerSheet = InnerSheet.Speed },
                            onSleepClick = { innerSheet = InnerSheet.Sleep },
                            onBookmark = {
                                val bookmark = playheadBookmark
                                if (bookmark == null) {
                                    addCurrentChapterBookmark()
                                } else {
                                    removeCurrentBookmark(bookmark)
                                }
                            },
                            onLongPress = {
                                haptics.longPress()
                                subPanelState.open(SubPanel.Bookmarks)
                            },
                        )
                        swipePageBaseState.playbackError?.let { error ->
                            PlaybackErrorBanner(message = error)
                        }
                        ScrubBar(
                            state = swipePageBaseState,
                            scrubBarData = scrubBarData,
                            onPreviewPositionChange = { scrubPreviewPositionMs = it },
                        )
                        TransportRowWithPills(
                            state = swipePageBaseState,
                            haptics = haptics,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    nextSwipePageState?.let { nextState ->
                        LateralChapterPreviewPage(
                            state = nextState,
                            heroArtworkHeight = heroArtworkHeight,
                            pageWidth = fullPlayerPageWidthDp,
                            coverArtDisplayMode = coverArtDisplayMode,
                            modifier = Modifier
                                .width(fullPlayerPageWidthDp)
                                .offset {
                                    IntOffset(
                                        x = (pageSwipeLayoutOffsetPx + fullPlayerPageWidthPx).roundToInt(),
                                        y = 0,
                                    )
                                },
                        )
                    }
                }
            }
            ThreeOptionRow(
                subPanelState = subPanelState,
                revealDistancePx = maxPanelHeightPx,
            )
        }

        if (panelOpen) {
            val panelCornerDp = 36f - (8f * panelFraction.coerceIn(0f, 1f))
            val panelTitle = when (subPanelState.target) {
                SubPanel.UpNext -> "Up Next"
                SubPanel.Chapters -> "Chapters"
                SubPanel.Details -> "Details"
                SubPanel.Bookmarks -> "Bookmarks"
                SubPanel.None -> ""
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(
                    topStart = panelCornerDp.dp,
                    topEnd = panelCornerDp.dp,
                ),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, panelOffsetPx.roundToInt()) }
                    .fillMaxWidth()
                    .height(panelHeightDp)
                    .zIndex(2f),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    PanelDragHandle(
                        onClose = { closePanelToNowPlaying() },
                        dragModifier = Modifier.panelDismissDrag(),
                    )
                    Box {
                        CompactPlayBar(
                            state = state,
                            scrubBarData = scrubBarData,
                            haptics = haptics,
                            coverArtDisplayMode = coverArtDisplayMode,
                        )
                    }
                    PanelDividerDragTarget(dragModifier = Modifier.panelDismissDrag())
                    Box(
                        modifier = Modifier
                            .weight(1f),
                    ) {
                        when {
                            subPanelState.target != SubPanel.None && !subPanelBodyReady -> {
                                SecondaryPanelBodySkeleton(title = panelTitle)
                            }
                            subPanelState.target == SubPanel.UpNext -> UpNextPanel(
                                onClose = { closePanelToNowPlaying() },
                                onContentAtTopChanged = { subPanelContentAtTop = it },
                                headerDragModifier = Modifier.panelDismissDrag(),
                            )
                            subPanelState.target == SubPanel.Chapters -> ChaptersPanel(
                                onClose = { closePanelToNowPlaying() },
                                onContentAtTopChanged = { subPanelContentAtTop = it },
                                headerDragModifier = Modifier.panelDismissDrag(),
                            )
                            subPanelState.target == SubPanel.Details -> DetailsPanel(
                                onClose = { closePanelToNowPlaying() },
                                onOpenDiscoverFilter = { query, field, language ->
                                    openDiscoverFilter(query, field, language)
                                },
                                onContentAtTopChanged = { subPanelContentAtTop = it },
                                headerDragModifier = Modifier.panelDismissDrag(),
                            )
                            subPanelState.target == SubPanel.Bookmarks -> BookmarksPanel(
                                onClose = { closePanelToNowPlaying() },
                                onContentAtTopChanged = { subPanelContentAtTop = it },
                                headerDragModifier = Modifier.panelDismissDrag(),
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        bookmarkHintBookmark?.let { bookmark ->
            BookmarkAddedHint(
                bookmark = bookmark,
                onAddNote = {
                    haptics.key()
                    bookmarkSnackbarJob?.cancel()
                    bookmarkSnackbarJob = null
                    bookmarkHintBookmark = null
                    snackbarHostState.currentSnackbarData?.dismiss()
                    bookmarkForNoteSheet = bookmark
                },
                onUndo = {
                    haptics.confirm()
                    bookmarkSnackbarJob?.cancel()
                    bookmarkSnackbarJob = null
                    bookmarkHintBookmark = null
                    bookmarkForNoteSheet = null
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        graph.app.bookmarkRepository.remove(bookmark.id)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f),
            )
        }

        if (overflowSheetOpen) {
            PlayerOverflowSheet(
                state = state,
                book = currentBook,
                coverArtDisplayMode = coverArtDisplayMode,
                inLibrary = inLibrary,
                missingDownloadCount = missingDownloadCount,
                downloadedCount = downloadedCount,
                authorSearchQuery = authorSearchQuery,
                bookmarkSelected = playheadBookmark != null,
                onDismiss = { overflowSheetOpen = false },
                onGoToBook = {
                    overflowSheetOpen = false
                    openCurrentBook()
                },
                onOpenReader = {
                    overflowSheetOpen = false
                    openCurrentReader()
                },
                onGoToAuthorSearch = {
                    overflowSheetOpen = false
                    openDiscoverFilter(authorSearchQuery, CatalogSearchField.Author)
                },
                onPlayNext = {
                    overflowSheetOpen = false
                    playCurrentChapterNext()
                },
                onSaveToLibrary = {
                    overflowSheetOpen = false
                    saveCurrentBookToLibrary()
                },
                onDownloadBook = ::downloadCurrentBook,
                onDeleteDownloads = ::deleteCurrentBookDownloads,
                onBookmark = {
                    overflowSheetOpen = false
                    val bookmark = playheadBookmark
                    if (bookmark == null) {
                        addCurrentChapterBookmark()
                    } else {
                        removeCurrentBookmark(bookmark)
                    }
                },
                onSleepTimer = {
                    overflowSheetOpen = false
                    haptics.key()
                    innerSheet = InnerSheet.Sleep
                },
                onShare = {
                    overflowSheetOpen = false
                    shareCurrentMedia()
                },
            )
        }
    }

    when (innerSheet) {
        InnerSheet.None -> Unit
        InnerSheet.Speed -> SpeedSheetModal(state) { innerSheet = InnerSheet.None }
        InnerSheet.Sleep -> SleepTimerSheetModal(state) { innerSheet = InnerSheet.None }
        InnerSheet.Bookmark -> BookmarkSheetModal(state) { innerSheet = InnerSheet.None }
    }
    bookmarkForNoteSheet?.let { bookmark ->
        BookmarkNoteSheetModal(
            bookmark = bookmark,
            onDismiss = { bookmarkForNoteSheet = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkAddedHint(
    bookmark: Bookmark,
    onAddNote: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Bookmark added at ${formatHmsFromMs(bookmark.positionMs)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddNote) {
                Text("Add note")
            }
            TextButton(onClick = onUndo) {
                Text("Undo")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerOverflowSheet(
    state: PlayerState,
    book: AudioBook?,
    coverArtDisplayMode: CoverArtDisplayMode,
    inLibrary: Boolean,
    missingDownloadCount: Int,
    downloadedCount: Int,
    authorSearchQuery: String?,
    bookmarkSelected: Boolean,
    onDismiss: () -> Unit,
    onGoToBook: () -> Unit,
    onOpenReader: () -> Unit,
    onGoToAuthorSearch: () -> Unit,
    onPlayNext: () -> Unit,
    onSaveToLibrary: () -> Unit,
    onDownloadBook: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onBookmark: () -> Unit,
    onSleepTimer: () -> Unit,
    onShare: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scrollState = rememberScrollState()
    val title = state.albumTitle?.takeIf { it.isNotBlank() }
        ?: book?.title
        ?: state.title.orEmpty()
    val subtitle = listOfNotNull(
        state.author?.takeIf { it.isNotBlank() } ?: book?.author?.takeIf { it.isNotBlank() },
        state.title?.takeIf { it.isNotBlank() && it != title },
    ).joinToString(" • ")
    val canUseBookActions = book != null
    val canUseChapterActions = book != null && !state.chapterId.isNullOrBlank()
    val canUseDownloadActions = canUseBookActions && (missingDownloadCount > 0 || downloadedCount > 0)
    var downloadOptionsExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .coverArtRowSize(coverArtDisplayMode, 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    AnimatedCoverImage(
                        model = state.artworkUri,
                        contentDescription = title,
                        contentScale = coverArtDisplayMode.coverArtContentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close menu")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OverflowQuickAction(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Play next",
                    enabled = canUseChapterActions,
                    onClick = onPlayNext,
                    modifier = Modifier.weight(1f),
                )
                OverflowQuickAction(
                    icon = Icons.Filled.Bookmarks,
                    label = if (inLibrary) "Saved" else "Save",
                    enabled = canUseBookActions,
                    onClick = onSaveToLibrary,
                    modifier = Modifier.weight(1f),
                )
                OverflowQuickAction(
                    icon = if (missingDownloadCount == 0 && downloadedCount > 0) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Download
                    },
                    label = if (downloadedCount > 0) "Delete" else "Download",
                    enabled = canUseDownloadActions,
                    onClick = { downloadOptionsExpanded = !downloadOptionsExpanded },
                    modifier = Modifier.weight(1f),
                )
                OverflowQuickAction(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    enabled = canUseBookActions,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
            }

            AnimatedVisibility(visible = downloadOptionsExpanded && canUseDownloadActions) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (missingDownloadCount > 0) {
                        OverflowActionRow(
                            icon = Icons.Filled.Download,
                            label = if (downloadedCount > 0) "Download remaining" else "Download book",
                            supportingText = "$missingDownloadCount item${if (missingDownloadCount == 1) "" else "s"} available",
                            enabled = true,
                            onClick = onDownloadBook,
                        )
                    }
                    if (downloadedCount > 0) {
                        OverflowActionRow(
                            icon = Icons.Filled.Delete,
                            label = "Delete downloads",
                            supportingText = "$downloadedCount item${if (downloadedCount == 1) "" else "s"} saved",
                            enabled = true,
                            onClick = onDeleteDownloads,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }
            }

            OverflowActionRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Go to audiobook",
                enabled = !state.bookId.isNullOrBlank(),
                onClick = onGoToBook,
            )
            OverflowActionRow(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Read book",
                enabled = book?.canReadSourceText() == true,
                onClick = onOpenReader,
            )
            OverflowActionRow(
                icon = Icons.Filled.Person,
                label = "Go to author",
                supportingText = authorSearchQuery,
                enabled = !authorSearchQuery.isNullOrBlank(),
                onClick = onGoToAuthorSearch,
            )
            OverflowActionRow(
                icon = Icons.Filled.Share,
                label = if (canUseChapterActions) "Share chapter" else "Share audiobook",
                enabled = canUseBookActions,
                onClick = onShare,
            )
            OverflowActionRow(
                icon = if (bookmarkSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (bookmarkSelected) "Remove bookmark" else "Create bookmark",
                supportingText = "At ${formatHmsFromMs(state.positionMs)}",
                enabled = canUseChapterActions,
                selected = bookmarkSelected,
                onClick = onBookmark,
            )
            OverflowActionRow(
                icon = Icons.Filled.Timer,
                label = "Sleep timer",
                supportingText = sleepTimerActionLabel(state.sleepTimer),
                enabled = true,
                onClick = onSleepTimer,
            )
        }
    }
}

@Composable
private fun OverflowQuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (enabled) 1.dp else 0.dp,
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OverflowActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null,
    selected: Boolean = false,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = contentColor)
        },
        headlineContent = {
            Text(
                text = label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = supportingText?.takeIf { it.isNotBlank() }?.let { text ->
            {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun OverflowSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun CastRouteAction(
    canCast: Boolean,
    unavailableReason: String?,
    isRemotePlayback: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (canCast) {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (isRemotePlayback) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    contentDescription = if (isRemotePlayback) "Cast settings" else "Cast",
                )
            }
        }
    } else {
        IconButton(
            onClick = {
                Toast.makeText(
                    context,
                    unavailableReason ?: "Unsupported for this audiobook.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            modifier = modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Cast,
                contentDescription = "Casting unsupported for this audiobook",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun PanelDragHandle(
    onClose: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(28.dp)
                .then(dragModifier)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp),
            ) { }
        }
    }
}

@Composable
private fun PanelDividerDragTarget(
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PanelDividerDragHeight)
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider()
    }
}

@Composable
private fun SecondaryPanelBodySkeleton(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        repeat(6) { index ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(
                            when (index % 3) {
                                0 -> 0.46f
                                1 -> 0.64f
                                else -> 0.38f
                            },
                        )
                        .height(14.dp),
                    shape = RoundedCornerShape(50),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.86f else 0.72f)
                        .height(42.dp),
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}

@Composable
private fun CompactPlayBar(
    state: PlayerState,
    scrubBarData: ScrubBarData,
    haptics: com.librivox.mobile.ui.components.Haptics,
    coverArtDisplayMode: CoverArtDisplayMode,
) {
    val graph = LocalAppGraph.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .coverArtRowSize(coverArtDisplayMode, 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                AnimatedCoverImage(
                    model = state.artworkUri,
                    contentDescription = state.title,
                    contentScale = coverArtDisplayMode.coverArtContentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.albumTitle ?: state.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Text(
                    text = state.author.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = {
                    haptics.key()
                    graph.playerStateRepository.seekToPreviousChapter()
                },
                enabled = state.canSeekPrevious,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous chapter")
            }
            val playShape = rememberMorphedCornerShape(
                targetCornerDp = if (state.isPlaying) 14f else 28f,
            )
            FilledIconButton(
                onClick = {
                    haptics.key()
                    graph.playerStateRepository.playPause()
                },
                shape = playShape,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
            IconButton(
                onClick = {
                    haptics.key()
                    graph.playerStateRepository.seekToNextChapter()
                },
                enabled = state.hasNext,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next chapter")
            }
        }
        MiniScrubBar(
            state = state,
            scrubBarData = scrubBarData,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MiniScrubBar(
    state: PlayerState,
    scrubBarData: ScrubBarData,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val totalMs = state.durationMs.takeIf { it > 0L } ?: 1L
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember(state.bookId, state.chapterId) {
        mutableFloatStateOf(state.positionMs.toFloat())
    }
    var preScrubPositionMs by remember(state.bookId, state.chapterId) { mutableStateOf<Long?>(null) }
    var lastSnapTarget by remember(state.bookId, state.chapterId) {
        mutableStateOf<ScrubSnapTarget?>(null)
    }
    val displayedMs = if (scrubbing) scrubMs else state.positionMs.toFloat()

    WavySlider(
        value = displayedMs.coerceIn(0f, totalMs.toFloat()),
        valueRange = 0f..totalMs.toFloat(),
        onValueChange = { rawValue ->
            if (!scrubbing) {
                scrubbing = true
                preScrubPositionMs = state.positionMs
                lastSnapTarget = null
            }
            val snapResult = snapScrubPosition(
                rawPositionMs = rawValue.toLong(),
                durationMs = totalMs,
                bookmarkPositionsMs = scrubBarData.bookmarkPositionsMs,
                preScrubPositionMs = preScrubPositionMs,
            )
            scrubMs = snapResult.positionMs.toFloat()
            if (snapResult.target != null && snapResult.target != lastSnapTarget) {
                haptics.tick()
            }
            lastSnapTarget = snapResult.target
            graph.playerStateRepository.previewSeekTo(snapResult.positionMs)
        },
        onValueChangeFinished = {
            scrubbing = false
            val targetMs = scrubMs.toLong()
            preScrubPositionMs = null
            lastSnapTarget = null
            graph.playerStateRepository.previewSeekTo(targetMs)
            graph.playerStateRepository.seekTo(targetMs)
        },
        modifier = modifier,
        isPlaying = state.isPlaying,
        isBuffering = state.isBuffering,
        bookmarkMarkers = scrubBarData.bookmarkMarkerFractions,
        bookmarkInteractionFraction = markerFraction(displayedMs.toLong(), totalMs),
        enableScrubHaptics = false,
    )
}

@Composable
private fun HeroArtwork(
    state: PlayerState,
    coverArtDisplayMode: CoverArtDisplayMode,
    onOpenReader: (() -> Unit)? = null,
    speedHoldActive: Boolean,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = rememberMorphedCornerShape(
        targetCornerDp = if (state.isPlaying) 24f else 56f,
    )
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val movementCancelThresholdPx = with(density) { 28.dp.toPx() }
    var coverHoldJob by remember { mutableStateOf<Job?>(null) }
    var coverHoldActive by remember { mutableStateOf(false) }
    var coverDownX by remember { mutableFloatStateOf(0f) }
    var coverDownY by remember { mutableFloatStateOf(0f) }
    var coverLastTapUpAtMs by remember { mutableStateOf(Long.MIN_VALUE) }
    var coverGestureWidthPx by remember { mutableIntStateOf(0) }
    var seekHint by remember(state.bookId, state.chapterId) {
        mutableStateOf<CoverSeekHint?>(null)
    }
    var seekHintRevision by remember(state.bookId, state.chapterId) {
        mutableIntStateOf(0)
    }

    fun finishCoverHold() {
        coverHoldJob?.cancel()
        coverHoldJob = null
        if (coverHoldActive) {
            coverHoldActive = false
            onHoldEnd()
        }
    }

    DisposableEffect(Unit) {
        onDispose { finishCoverHold() }
    }
    LaunchedEffect(state.bookId, state.chapterId) {
        finishCoverHold()
        coverLastTapUpAtMs = Long.MIN_VALUE
    }
    LaunchedEffect(seekHintRevision) {
        val revision = seekHintRevision
        if (seekHint != null) {
            delay(CoverSeekHintDismissMillis)
            if (seekHintRevision == revision) {
                seekHint = null
            }
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .padding(CoverGestureHitPadding)
                .onSizeChanged { size -> coverGestureWidthPx = size.width },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                AnimatedCoverImage(
                    model = state.artworkUri,
                    contentDescription = state.title,
                    contentScale = coverArtDisplayMode.coverArtContentScale,
                    contentAlignment = Alignment.Center,
                    animateEntrance = false,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInteropFilter { event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    finishCoverHold()
                                    coverDownX = event.x
                                    coverDownY = event.y
                                    coverHoldActive = false
                                    coverHoldJob = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        coverHoldActive = true
                                        onHoldStart()
                                    }
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    if (!coverHoldActive) {
                                        val dx = event.x - coverDownX
                                        val dy = event.y - coverDownY
                                        if (dx * dx + dy * dy > movementCancelThresholdPx * movementCancelThresholdPx) {
                                            coverHoldJob?.cancel()
                                            coverHoldJob = null
                                        }
                                    }
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    val wasHoldActive = coverHoldActive
                                    finishCoverHold()
                                    if (!wasHoldActive) {
                                        val dx = event.x - coverDownX
                                        val dy = event.y - coverDownY
                                        val stayedTapLike =
                                            dx * dx + dy * dy <= movementCancelThresholdPx * movementCancelThresholdPx
                                        if (stayedTapLike) {
                                            if (
                                                coverLastTapUpAtMs >= 0L &&
                                                event.eventTime - coverLastTapUpAtMs <=
                                                    viewConfiguration.doubleTapTimeoutMillis
                                            ) {
                                                if (event.x < coverGestureWidthPx / 2f) {
                                                    seekHint = CoverSeekHint.Backward
                                                    seekHintRevision++
                                                    onDoubleTapLeft()
                                                } else {
                                                    seekHint = CoverSeekHint.Forward
                                                    seekHintRevision++
                                                    onDoubleTapRight()
                                                }
                                                coverLastTapUpAtMs = Long.MIN_VALUE
                                            } else {
                                                coverLastTapUpAtMs = event.eventTime
                                            }
                                        } else {
                                            coverLastTapUpAtMs = Long.MIN_VALUE
                                        }
                                    }
                                    true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    finishCoverHold()
                                    coverLastTapUpAtMs = Long.MIN_VALUE
                                    true
                                }
                                else -> true
                            }
                        },
                )
                CoverGestureOverlay(
                    seekHint = seekHint,
                    speedHoldActive = speedHoldActive,
                    modifier = Modifier.matchParentSize(),
                )
            }
            onOpenReader?.let { openReader ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 16.dp)
                        .size(52.dp)
                        .zIndex(2f)
                        .clip(CircleShape)
                        .clickable(onClick = openReader),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 4.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Read book",
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun PlayerState.chapterSwipePreviewState(
    book: AudioBook,
    chapter: AudioBookChapter,
    chapterIndex: Int,
): PlayerState {
    val durationMs = chapter.durationSeconds
        .takeIf { it > 0L }
        ?.times(1000L)
        ?: 0L
    return copy(
        chapterId = chapter.id,
        title = chapter.cleanTitle(),
        albumTitle = albumTitle ?: book.title,
        author = author ?: book.author,
        durationMs = durationMs,
        positionMs = 0L,
        mediaItemIndex = chapterIndex,
        mediaItemCount = book.chapters.size,
        hasPrevious = chapterIndex > 0,
        hasNext = chapterIndex < book.chapters.lastIndex,
        canSeekPrevious = chapterIndex > 0,
        isBuffering = false,
        playbackError = null,
        scrubPreviewPositionMs = null,
    )
}

@Composable
private fun LateralChapterPreviewPage(
    state: PlayerState?,
    heroArtworkHeight: Dp,
    pageWidth: Dp,
    coverArtDisplayMode: CoverArtDisplayMode,
    modifier: Modifier = Modifier,
) {
    if (state == null) {
        Spacer(modifier = modifier.width(pageWidth))
        return
    }
    Column(
        modifier = modifier
            .width(pageWidth)
            .clearAndSetSemantics {
                contentDescription = state.nowPlayingChapterTitle()
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StaticHeroArtwork(
            state = state,
            coverArtDisplayMode = coverArtDisplayMode,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroArtworkHeight + CoverGestureHitPadding + CoverGestureHitPadding),
        )
        HeroIdentityStatic(state = state)
        StaticFeedbackAndBookmarkRow(state = state)
        StaticScrubBar(state = state)
        StaticTransportRow(state = state)
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun StaticHeroArtwork(
    state: PlayerState,
    coverArtDisplayMode: CoverArtDisplayMode,
    modifier: Modifier = Modifier,
) {
    val shape = rememberMorphedCornerShape(
        targetCornerDp = if (state.isPlaying) 24f else 56f,
    )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .padding(CoverGestureHitPadding)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            AnimatedCoverImage(
                model = state.artworkUri,
                contentDescription = state.title,
                contentScale = coverArtDisplayMode.coverArtContentScale,
                contentAlignment = Alignment.Center,
                animateEntrance = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HeroIdentityStatic(state: PlayerState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = state.nowPlayingChapterTitle(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
        state.albumTitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        state.author?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StaticFeedbackAndBookmarkRow(state: PlayerState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StaticRoundActionIcon(Icons.Outlined.OutlinedThumbDown)
            StaticRoundActionIcon(Icons.Outlined.OutlinedThumbUp)
            StaticTextPill { Text(state.speed.label(), style = MaterialTheme.typography.labelLarge) }
            StaticTextPill {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                sleepPillLabel(state.sleepTimer)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        StaticRoundActionIcon(Icons.Outlined.BookmarkBorder)
    }
}

@Composable
private fun StaticRoundActionIcon(imageVector: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = imageVector, contentDescription = null)
        }
    }
}

@Composable
private fun StaticTextPill(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun StaticScrubBar(state: PlayerState) {
    val totalMs = state.durationMs.takeIf { it > 0L } ?: 1L
    val displayedMs = state.positionMs.coerceIn(0L, totalMs)
    val progress = (displayedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.74f)),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatHmsFromMs(displayedMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatHmsFromMs((state.durationMs - displayedMs).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StaticTransportRow(state: PlayerState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StaticTransportIcon(Icons.Filled.SkipPrevious, enabled = state.hasPrevious)
            StaticTransportIcon(Icons.Filled.Replay10)
            Surface(
                modifier = Modifier.size(72.dp),
                shape = rememberMorphedCornerShape(targetCornerDp = if (state.isPlaying) 18f else 36f),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            StaticTransportIcon(Icons.Filled.Forward10)
            StaticTransportIcon(Icons.Filled.SkipNext, enabled = state.hasNext)
        }
    }
}

@Composable
private fun StaticTransportIcon(
    imageVector: ImageVector,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.82f else 0.34f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoverGestureOverlay(
    seekHint: CoverSeekHint?,
    speedHoldActive: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastSeekHint by remember { mutableStateOf(seekHint) }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            lastSeekHint = seekHint
        }
    }
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = seekHint != null,
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()),
            modifier = Modifier.matchParentSize(),
        ) {
            val hint = lastSeekHint ?: return@AnimatedVisibility
            val isForward = hint == CoverSeekHint.Forward
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    CoverGestureSeekChip(
                        isForward = isForward,
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = if (isForward) {
                                "Skip forward $SkipForwardSeconds seconds"
                            } else {
                                "Skip back $SkipBackSeconds seconds"
                            }
                        },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = speedHoldActive,
            enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()),
            exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    CoverGestureSpeedChip(
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = "Playing at 2 times speed"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverGestureSeekChip(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!isForward) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = if (isForward) "+$SkipForwardSeconds" else "-$SkipBackSeconds",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (isForward) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CoverGestureSpeedChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "2x",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedCoverImage(
    model: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    contentAlignment: Alignment = Alignment.Center,
    animateEntrance: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (model.isNullOrBlank()) return
    val coverEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fillUncroppedBackdrop = contentScale == ContentScale.Fit

    Box(modifier = modifier) {
        if (fillUncroppedBackdrop) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                loading = { },
                error = { },
                success = {
                    SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                },
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                    }
                    .blur(18.dp),
            )
        }
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = contentAlignment,
            loading = { },
            error = { },
            success = {
                if (!animateEntrance) {
                    SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                } else {
                    var visible by remember(model) { mutableStateOf(false) }
                    LaunchedEffect(model) {
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = coverEffectsSpec),
                        exit = fadeOut(animationSpec = coverEffectsSpec),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun HeroIdentity(
    state: PlayerState,
    onBookClick: () -> Unit,
    onAuthorClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val chapterTitle = state.nowPlayingChapterTitle()
        val bookTitle = state.albumTitle?.takeIf { it.isNotBlank() }
        Text(
            text = chapterTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
        bookTitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onBookClick() }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        val authorText = state.author
        if (!authorText.isNullOrBlank()) {
            Text(
                text = authorText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAuthorClick() }
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FeedbackAndBookmarkRow(
    state: PlayerState,
    playbackFeedback: PlaybackFeedback,
    feedbackControlScope: FeedbackControlScope,
    bookmarkSelected: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onBookmark: () -> Unit,
    onLongPress: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (feedbackControlScope != FeedbackControlScope.Hidden) {
                FeedbackButton(
                    selectedImageVector = Icons.Filled.FilledThumbDown,
                    unselectedImageVector = Icons.Outlined.OutlinedThumbDown,
                    selected = playbackFeedback == PlaybackFeedback.Disliked,
                    selectedContentDescription = "Remove dislike",
                    contentDescription = "Dislike",
                    onClick = {
                        haptics.confirm()
                        graph.playerStateRepository.toggleDislike()
                    },
                )
                FeedbackButton(
                    selectedImageVector = Icons.Filled.FilledThumbUp,
                    unselectedImageVector = Icons.Outlined.OutlinedThumbUp,
                    selected = playbackFeedback == PlaybackFeedback.Liked,
                    selectedContentDescription = "Remove like",
                    contentDescription = "Like",
                    onClick = {
                        haptics.confirm()
                        graph.playerStateRepository.toggleLike()
                    },
                )
            }
            SpeedActionButton(
                speed = state.speed,
                onClick = {
                    haptics.tick()
                    onSpeedClick()
                },
            )
            SleepTimerActionButton(
                sleepTimer = state.sleepTimer,
                onClick = {
                    haptics.tick()
                    onSleepClick()
                },
            )
        }
        Surface(
            shape = CircleShape,
            color = if (bookmarkSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (bookmarkSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            tonalElevation = 2.dp,
            modifier = Modifier
                .size(40.dp)
                .combinedClickable(
                    onClick = {
                        onBookmark()
                    },
                    onLongClick = {
                        haptics.longPress()
                        onLongPress()
                    },
                ),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (bookmarkSelected) {
                        Icons.Filled.Bookmark
                    } else {
                        Icons.Outlined.BookmarkBorder
                    },
                    contentDescription = if (bookmarkSelected) {
                        "Remove bookmark"
                    } else {
                        "Create bookmark"
                    },
                )
            }
        }
    }
}

@Composable
private fun SpeedActionButton(
    speed: PlaybackSpeed,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Text(speed.label(), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SleepTimerActionButton(
    sleepTimer: SleepTimerState,
    onClick: () -> Unit,
) {
    val label = sleepPillLabel(sleepTimer)
    FilledTonalButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Timer,
            contentDescription = "Sleep timer ${sleepTimerActionLabel(sleepTimer)}",
            modifier = Modifier.size(18.dp),
        )
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun PlaybackErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Playback error: $message",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun FeedbackButton(
    selectedImageVector: androidx.compose.ui.graphics.vector.ImageVector,
    unselectedImageVector: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    selectedContentDescription: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (selected) selectedImageVector else unselectedImageVector,
            contentDescription = if (selected) selectedContentDescription else contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

@Composable
private fun rememberScrubBarData(
    state: PlayerState,
): ScrubBarData {
    val graph = LocalAppGraph.current
    val bookmarks by graph.app.bookmarkRepository.bookmarks.collectAsState(initial = emptyList())
    return remember(bookmarks, state.bookId, state.chapterId, state.durationMs) {
        val safeDurationMs = state.durationMs.coerceAtLeast(0L)
        val currentChapterBookmarks = bookmarks
            .asSequence()
            .filter { it.bookId == state.bookId && it.chapterId == state.chapterId }
            .toList()
        val bookmarkByPosition = linkedMapOf<Long, Bookmark>()
        currentChapterBookmarks
            .sortedWith(
                compareBy<Bookmark> { it.positionMs.coerceIn(0L, safeDurationMs) }
                    .thenBy { it.createdAtMillis },
            )
            .forEach { bookmark ->
                bookmarkByPosition.putIfAbsent(
                    bookmark.positionMs.coerceIn(0L, safeDurationMs),
                    bookmark,
                )
            }
        val bookmarkPositions = bookmarkByPosition.keys.toList()
        ScrubBarData(
            bookmarkByPositionMs = bookmarkByPosition,
            bookmarkPositionsMs = bookmarkPositions,
            bookmarkMarkerFractions = bookmarkPositions.mapNotNull { position ->
                markerFraction(position, state.durationMs)
            },
        )
    }
}

private data class ScrubBarData(
    val bookmarkByPositionMs: Map<Long, Bookmark>,
    val bookmarkPositionsMs: List<Long>,
    val bookmarkMarkerFractions: List<Float>,
) {
    fun bookmarkAtPlayhead(positionMs: Long): Bookmark? {
        val bookmarkPosition = nearestBookmarkPositionAtPlayhead(
            positionMs = positionMs,
            bookmarkPositionsMs = bookmarkPositionsMs,
        ) ?: return null
        return bookmarkByPositionMs[bookmarkPosition]
    }
}

private fun markerFraction(positionMs: Long, durationMs: Long): Float? {
    val safeDuration = durationMs.takeIf { it > 0L } ?: return null
    return (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun ScrubBar(
    state: PlayerState,
    scrubBarData: ScrubBarData,
    onPreviewPositionChange: (Long?) -> Unit,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val totalMs = state.durationMs.takeIf { it > 0L } ?: 1L
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember(state.bookId, state.chapterId) {
        mutableFloatStateOf(state.positionMs.toFloat())
    }
    var preScrubPositionMs by remember(state.bookId, state.chapterId) { mutableStateOf<Long?>(null) }
    var lastSnapTarget by remember(state.bookId, state.chapterId) {
        mutableStateOf<ScrubSnapTarget?>(null)
    }
    val displayedMs = if (scrubbing) scrubMs.toLong() else state.positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        WavySlider(
            value = displayedMs.toFloat().coerceIn(0f, totalMs.toFloat()),
            valueRange = 0f..totalMs.toFloat(),
            onValueChange = { rawValue ->
                if (!scrubbing) {
                    scrubbing = true
                    preScrubPositionMs = state.positionMs
                    lastSnapTarget = null
                }
                val snapResult = snapScrubPosition(
                    rawPositionMs = rawValue.toLong(),
                    durationMs = totalMs,
                    bookmarkPositionsMs = scrubBarData.bookmarkPositionsMs,
                    preScrubPositionMs = preScrubPositionMs,
                )
                scrubMs = snapResult.positionMs.toFloat()
                onPreviewPositionChange(snapResult.positionMs)
                graph.playerStateRepository.previewSeekTo(snapResult.positionMs)
                if (snapResult.target != null && snapResult.target != lastSnapTarget) {
                    haptics.tick()
                }
                lastSnapTarget = snapResult.target
            },
            onValueChangeFinished = {
                scrubbing = false
                val targetMs = scrubMs.toLong()
                preScrubPositionMs = null
                lastSnapTarget = null
                onPreviewPositionChange(targetMs)
                graph.playerStateRepository.previewSeekTo(targetMs)
                graph.playerStateRepository.seekTo(targetMs)
            },
            isPlaying = state.isPlaying,
            isBuffering = state.isBuffering,
            bookmarkMarkers = scrubBarData.bookmarkMarkerFractions,
            bookmarkInteractionFraction = markerFraction(displayedMs, totalMs),
            enableScrubHaptics = false,
            interactionHeight = 56.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (state.isBuffering && !scrubbing) "Buffering…" else formatHmsFromMs(displayedMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "-${formatHmsFromMs((state.durationMs - displayedMs).coerceAtLeast(0L))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportRowWithPills(
    state: PlayerState,
    haptics: com.librivox.mobile.ui.components.Haptics,
) {
    val graph = LocalAppGraph.current
    val playShape = rememberMorphedCornerShape(targetCornerDp = if (state.isPlaying) 18f else 36f)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { haptics.tick(); graph.playerStateRepository.seekToPreviousChapter() },
                enabled = state.canSeekPrevious,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous chapter")
            }
            SkipIntervalButton(
                forward = false,
                contentDescription = "Skip back $SkipBackSeconds seconds",
                onClick = { haptics.tick(); graph.playerStateRepository.seekBack() },
            )
            FilledIconButton(
                onClick = { haptics.key(); graph.playerStateRepository.playPause() },
                modifier = Modifier.size(72.dp),
                shape = playShape,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
            }
            SkipIntervalButton(
                forward = true,
                contentDescription = "Skip forward $SkipForwardSeconds seconds",
                onClick = { haptics.tick(); graph.playerStateRepository.seekForward() },
            )
            IconButton(
                onClick = { haptics.tick(); graph.playerStateRepository.seekToNextChapter() },
                enabled = state.hasNext,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next chapter")
            }
        }
    }
}

@Composable
private fun SkipIntervalButton(
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
            contentDescription = contentDescription,
            modifier = Modifier.clearAndSetSemantics {
                this.contentDescription = contentDescription
            },
        )
    }
}

@Composable
private fun ThreeOptionRow(
    subPanelState: PlayerSubPanelState,
    revealDistancePx: Float,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionCard(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "Up Next",
            panel = SubPanel.UpNext,
            subPanelState = subPanelState,
            revealDistancePx = revealDistancePx,
            haptics = haptics,
            modifier = Modifier.weight(1f),
        )
        OptionCard(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = "Chapters",
            panel = SubPanel.Chapters,
            subPanelState = subPanelState,
            revealDistancePx = revealDistancePx,
            haptics = haptics,
            modifier = Modifier.weight(1f),
        )
        OptionCard(
            icon = Icons.Filled.Info,
            label = "Details",
            panel = SubPanel.Details,
            subPanelState = subPanelState,
            revealDistancePx = revealDistancePx,
            haptics = haptics,
            modifier = Modifier.weight(1f),
        )
        OptionCard(
            icon = Icons.Filled.Bookmarks,
            label = "Bookmarks",
            panel = SubPanel.Bookmarks,
            subPanelState = subPanelState,
            revealDistancePx = revealDistancePx,
            haptics = haptics,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    panel: SubPanel,
    subPanelState: PlayerSubPanelState,
    revealDistancePx: Float,
    haptics: com.librivox.mobile.ui.components.Haptics,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var optionDragDeltaPx by remember { mutableFloatStateOf(0f) }
    val revealDistance = revealDistancePx.coerceAtLeast(1f)
    val commitThresholdPx = revealDistance * SubPanelCommitFraction
    var thresholdHapticSent by remember { mutableStateOf(false) }
    val isTarget = subPanelState.target == panel && subPanelState.fraction > 0.01f
    val shape = rememberMorphedCornerShape(
        targetCornerDp = if (isTarget) 28f else 16f,
    )
    fun Modifier.optionPanelDrag(): Modifier = pointerInput(panel, revealDistance) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            var lastY = down.position.y
            var pendingDy = 0f
            var dragging = false
            optionDragDeltaPx = 0f
            thresholdHapticSent = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    break
                }

                val dy = change.position.y - lastY
                lastY = change.position.y
                if (!dragging) {
                    pendingDy += dy
                    when {
                        pendingDy < -3f -> {
                            dragging = true
                            subPanelState.beginDrag(panel)
                            haptics.dragStart()
                        }
                        pendingDy > 8f -> break
                    }
                }
                if (dragging) {
                    val consumed = subPanelState.dispatchRawDelta(dy)
                    if (consumed != 0f) {
                        optionDragDeltaPx += consumed
                        change.consume()
                    }
                    if (!thresholdHapticSent && -optionDragDeltaPx >= commitThresholdPx) {
                        haptics.tick()
                        thresholdHapticSent = true
                    }
                }
            }

            if (dragging) {
                val velocityY = velocityTracker.calculateVelocity().y
                scope.launch {
                    if (optionDragDeltaPx < 0f || velocityY < 0f) {
                        subPanelState.open(panel)
                    } else {
                        subPanelState.settleRawDrag(
                            velocityPxPerSecond = velocityY,
                            dragDeltaPx = optionDragDeltaPx,
                        )
                    }
                    haptics.gestureEnd()
                    optionDragDeltaPx = 0f
                    thresholdHapticSent = false
                }
            }
        }
    }
    Surface(
        color = if (isTarget) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = shape,
        tonalElevation = if (isTarget) 4.dp else 0.dp,
        modifier = modifier
            .height(72.dp)
            .optionPanelDrag()
            .clickable {
                haptics.tick()
                subPanelState.open(panel)
            }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp),
            ) { }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun miniBookTitle(state: PlayerState): String =
    state.albumTitle ?: state.title.orEmpty()

private fun miniChapterTitle(state: PlayerState): String? =
    state.nowPlayingChapterTitle().takeUnless { it == miniBookTitle(state) }

private fun PlayerState.nowPlayingChapterTitle(): String {
    val rawTitle = title?.takeIf { it.isNotBlank() } ?: return "Current chapter"
    val chapterNumber = if (mediaItemCount > 1 || hasPrevious || hasNext) {
        mediaItemIndex + 1
    } else {
        0
    }
    return numberedChapterTitle(
        title = rawTitle,
        number = chapterNumber,
        fallback = "Current chapter",
    )
}

private fun miniStatusLine(state: PlayerState): String? {
    state.playbackError?.let { return "Playback error: $it" }
    val parts = mutableListOf<String>()
    miniTimeLeft(state)?.let { parts.add(it) }
    if (state.speed.value != 1f) parts.add(state.speed.label())
    when (val sleep = state.sleepTimer) {
        SleepTimerState.Idle, SleepTimerState.Expired -> Unit
        SleepTimerState.EndOfChapter -> parts.add("Sleep: end of chapter")
        is SleepTimerState.Running -> {
            val minutes = sleep.remainingMs / 60_000
            val seconds = (sleep.remainingMs % 60_000) / 1000
            parts.add("Sleep ${"%d:%02d".format(minutes, seconds)}")
        }
    }
    return parts.joinToString(" • ").takeIf { it.isNotBlank() }
}

private fun miniTimeLeft(state: PlayerState): String? {
    if (state.durationMs <= 0L) return null
    val remainingMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)
    return "${formatHmsFromMs(remainingMs)} left"
}

private fun sleepPillLabel(state: SleepTimerState): String? = when (state) {
    SleepTimerState.Idle, SleepTimerState.Expired -> null
    SleepTimerState.EndOfChapter -> "EoC"
    is SleepTimerState.Running -> {
        val minutes = state.remainingMs / 60_000
        val seconds = (state.remainingMs % 60_000) / 1000
        "%d:%02d".format(minutes, seconds)
    }
}

private fun sleepTimerActionLabel(state: SleepTimerState): String = when (state) {
    SleepTimerState.Idle, SleepTimerState.Expired -> "Off"
    SleepTimerState.EndOfChapter -> "End of chapter"
    is SleepTimerState.Running -> {
        val minutes = state.remainingMs / 60_000
        val seconds = (state.remainingMs % 60_000) / 1000
        "Ends in %d:%02d".format(minutes, seconds)
    }
}

// ---------- Inner modal sheets (Speed / Sleep / Bookmark) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheetModal(state: PlayerState, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    var current by remember { mutableFloatStateOf(state.speed.value) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Playback speed", style = MaterialTheme.typography.titleLarge)
            Text(
                text = PlaybackSpeed.clamp(current).label(),
                style = MaterialTheme.typography.displaySmall,
            )
            Slider(
                value = current,
                onValueChange = { current = it },
                onValueChangeFinished = {
                    graph.playerStateRepository.setSpeed(PlaybackSpeed.clamp(current))
                },
                valueRange = PlaybackSpeed.MIN..PlaybackSpeed.MAX,
            )
            val rows = PlaybackSpeed.Presets.chunked(5)
            rows.forEach { rowSpeeds ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSpeeds.forEach { preset ->
                        TextButton(onClick = {
                            current = preset.value
                            graph.playerStateRepository.setSpeed(preset)
                        }) { Text(preset.label()) }
                    }
                }
            }
            TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheetModal(state: PlayerState, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            SleepTimerPreset.entries.forEach { preset ->
                ListItem(
                    headlineContent = { Text(preset.label) },
                    leadingContent = {
                        RadioButton(
                            selected = (state.sleepTimer as? SleepTimerState.Running)?.totalMs == preset.durationMs,
                            onClick = {
                                graph.playerStateRepository.setSleepTimer(preset.durationMs)
                                scope.launch { sheetState.hide(); onDismiss() }
                            },
                        )
                    },
                    modifier = Modifier.clickable {
                        graph.playerStateRepository.setSleepTimer(preset.durationMs)
                        scope.launch { sheetState.hide(); onDismiss() }
                    },
                )
            }
            HorizontalDivider()
            ListItem(
                modifier = Modifier.clickable {
                    graph.playerStateRepository.setSleepTimerEndOfChapter()
                    scope.launch { sheetState.hide(); onDismiss() }
                },
                headlineContent = { Text("End of chapter") },
                leadingContent = {
                    RadioButton(
                        selected = state.sleepTimer == SleepTimerState.EndOfChapter,
                        onClick = {
                            graph.playerStateRepository.setSleepTimerEndOfChapter()
                            scope.launch { sheetState.hide(); onDismiss() }
                        },
                    )
                },
            )
            ListItem(
                modifier = Modifier.clickable {
                    graph.playerStateRepository.cancelSleepTimer()
                    scope.launch { sheetState.hide(); onDismiss() }
                },
                headlineContent = { Text("Off") },
                leadingContent = {
                    RadioButton(
                        selected = state.sleepTimer is SleepTimerState.Idle ||
                            state.sleepTimer is SleepTimerState.Expired,
                        onClick = {
                            graph.playerStateRepository.cancelSleepTimer()
                            scope.launch { sheetState.hide(); onDismiss() }
                        },
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkSheetModal(state: PlayerState, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create bookmark",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Bookmark at ${formatHmsFromMs(state.positionMs)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(1000) },
                        label = { Text("Note") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                            Text("Cancel")
                        }
                        FilledTonalButton(onClick = {
                            val b = state.bookId
                            val c = state.chapterId
                            if (!b.isNullOrBlank() && !c.isNullOrBlank()) {
                                scope.launch {
                                    graph.app.bookmarkRepository.add(b, c, state.positionMs, note)
                                    sheetState.hide()
                                    onDismiss()
                                }
                                haptics.confirm()
                                note = ""
                            }
                        }) {
                            Text("Save bookmark")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkNoteSheetModal(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    var note by remember(bookmark.id) { mutableStateOf(bookmark.note) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
                leadingContent = {
                    Icon(Icons.Filled.Bookmark, contentDescription = null)
                },
                headlineContent = { Text("Add note") },
                supportingContent = {
                    Text("Bookmark at ${formatHmsFromMs(bookmark.positionMs)}")
                },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(1000) },
                label = { Text("Note") },
                placeholder = { Text("Add what you want to remember here") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                    Text("Not now")
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            graph.app.bookmarkRepository.updateNote(bookmark.id, note)
                            sheetState.hide()
                            onDismiss()
                        }
                        haptics.confirm()
                    },
                ) {
                    Text("Save note")
                }
            }
        }
    }
}
