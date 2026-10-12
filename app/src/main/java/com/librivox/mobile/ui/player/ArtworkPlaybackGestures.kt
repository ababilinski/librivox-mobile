package com.librivox.mobile.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/** One owner arbitrates artwork paging, seek taps and the temporary-speed hold. */
@Composable
internal fun Modifier.artworkPlaybackGestures(
    identity: Any?,
    chapterSwipeEnabled: Boolean,
    onDoubleTap: (forward: Boolean) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onChapterDragStart: () -> Unit,
    onChapterDrag: (distance: Float) -> Unit,
    onChapterDragEnd: (distance: Float, velocity: Float) -> Unit,
    onChapterDragCancel: () -> Unit,
): Modifier {
    val callbacks by rememberUpdatedState(
        ArtworkGestureCallbacks(
            chapterSwipeEnabled, onDoubleTap, onHoldStart, onHoldEnd,
            onChapterDragStart, onChapterDrag, onChapterDragEnd, onChapterDragCancel,
        ),
    )
    return pointerInput(identity) {
        var lastTapAt = Long.MIN_VALUE
        var lastTapPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var dragging = false
            var held = false
            var finished = false
            var finishHold: (() -> Unit)? = null
            try {
                while (true) {
                    val remainingHold = viewConfiguration.longPressTimeoutMillis -
                        (currentEvent.changes.firstOrNull { it.id == down.id }?.uptimeMillis
                            ?.minus(down.uptimeMillis) ?: 0L)
                    val event = if (!dragging && !held && remainingHold > 0L) {
                        withTimeoutOrNull(remainingHold) { awaitPointerEvent() }
                    } else if (!dragging && !held) {
                        null
                    } else {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        held = true
                        lastTapAt = Long.MIN_VALUE
                        finishHold = callbacks.onHoldEnd
                        callbacks.onHoldStart()
                        continue
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - down.position
                    if (change.isConsumed && !dragging && !held) break
                    if (!change.pressed) {
                        when {
                            held -> change.consume()
                            dragging -> {
                                change.consume()
                                callbacks.onChapterDragEnd(delta.x, velocity.calculateVelocity().x)
                            }
                            else -> {
                                change.consume()
                                val sinceLastTap = change.uptimeMillis - lastTapAt
                                val nearby = (change.position - lastTapPosition).getDistance() <= viewConfiguration.touchSlop * 4f
                                if (lastTapAt != Long.MIN_VALUE && nearby &&
                                    sinceLastTap in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis
                                ) {
                                    callbacks.onDoubleTap(change.position.x >= size.width / 2f)
                                    lastTapAt = Long.MIN_VALUE
                                } else {
                                    lastTapAt = change.uptimeMillis
                                    lastTapPosition = change.position
                                }
                            }
                        }
                        finished = true
                        break
                    }
                    if (held) {
                        change.consume()
                    } else if (dragging) {
                        change.consume()
                        callbacks.onChapterDrag(delta.x)
                    } else if (delta.getDistance() > viewConfiguration.touchSlop) {
                        lastTapAt = Long.MIN_VALUE
                        if (abs(delta.x) > abs(delta.y) && callbacks.swipeEnabled) {
                            dragging = true
                            change.consume()
                            callbacks.onChapterDragStart()
                            callbacks.onChapterDrag(delta.x)
                        } else {
                            // Vertical movement belongs to the scroll body / player dismiss owner.
                            break
                        }
                    }
                }
            } finally {
                finishHold?.invoke()
                if (dragging && !finished) callbacks.onChapterDragCancel()
            }
        }
    }
}

private data class ArtworkGestureCallbacks(
    val swipeEnabled: Boolean,
    val onDoubleTap: (Boolean) -> Unit,
    val onHoldStart: () -> Unit,
    val onHoldEnd: () -> Unit,
    val onChapterDragStart: () -> Unit,
    val onChapterDrag: (Float) -> Unit,
    val onChapterDragEnd: (Float, Float) -> Unit,
    val onChapterDragCancel: () -> Unit,
)
