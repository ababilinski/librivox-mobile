package com.librivox.mobile.ui.player

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** The same scroll state owns the automatic movement and user drag/fling. */
@Composable
internal fun InteractiveChapterTitle(
    text: String,
    identity: Any?,
    modifier: Modifier = Modifier,
    automaticScrollEnabled: Boolean = true,
) {
    key(identity) {
        ChapterTitleContent(text, modifier, automaticScrollEnabled)
    }
}

@Composable
private fun ChapterTitleContent(text: String, modifier: Modifier, automaticScrollEnabled: Boolean) {
    var expanded by remember() { mutableStateOf(false) }
    var manuallyMoved by remember() { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var pressed by remember { mutableStateOf(false) }
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val accessibility = LocalContext.current.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(lifecycle, accessibility) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        lifecycle.addObserver(observer)
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            accessibility.removeTouchExplorationStateChangeListener(listener)
        }
    }
    LaunchedEffect(Unit) { scroll.scrollTo(0) }
    LaunchedEffect(pressed, dragged) {
        if (pressed || dragged) manuallyMoved = true
    }
    LaunchedEffect(text, expanded, pressed, dragged, active, touchExploration, automaticScrollEnabled) {
        if (expanded || pressed || dragged || !active || touchExploration || !automaticScrollEnabled) return@LaunchedEffect
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@LaunchedEffect
        snapshotFlow { scroll.maxValue }.first { it > 0 && it != Int.MAX_VALUE }
        awaitContinuousTitleIdle(
            snapshotFlow { scroll.isScrollInProgress },
            if (manuallyMoved) 3_000L else 1_000L,
        )
        while (isActive) {
            val distance = (scroll.maxValue - scroll.value).coerceAtLeast(0)
            if (distance > 0) {
                val duration = (distance / (30f * density.density) * 1_000f).roundToInt().coerceAtLeast(1)
                scroll.animateScrollTo(scroll.maxValue, tween(duration, easing = LinearEasing))
            }
            delay(1_000L)
            scroll.scrollTo(0)
            delay(1_000L)
        }
    }
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("now-playing-chapter-title")
            .semantics(mergeDescendants = true) {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                role = Role.Button
                onClick(if (expanded) "Collapse chapter title" else "Expand chapter title") {
                    expanded = !expanded
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    event.key in listOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar)
                ) {
                    expanded = !expanded
                    true
                } else false
            }
            .focusable()
            .pointerInput(Unit) {
                // A scrollable claims down immediately while flinging. Observe physical tap
                // distance before it consumes events; horizontalScroll remains the drag owner.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    var moved = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            moved = moved || event.changes.size > 1 ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            if (!change.pressed) {
                                if (!moved && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    expanded = !expanded
                                    change.consume()
                                }
                                break
                            }
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            softWrap = expanded,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().then(
                if (expanded) Modifier else Modifier.horizontalScroll(scroll),
            ),
        )
    }
}

/** Restart the whole pause if a delayed fling starts during the idle window. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun awaitContinuousTitleIdle(scrolling: Flow<Boolean>, delayMillis: Long) {
    scrolling.distinctUntilChanged().transformLatest { moving ->
        if (!moving) {
            delay(delayMillis)
            emit(Unit)
        }
    }.first()
}
