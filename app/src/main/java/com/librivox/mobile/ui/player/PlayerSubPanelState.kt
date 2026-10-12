@file:OptIn(ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class SubPanel { None, UpNext, Chapters, Details, Bookmarks }
enum class SubPanelAnchor { Closed, Open }

private const val SubPanelFlingVelocityPxPerSecond = 160f
const val SubPanelCommitFraction = 0.08f

/**
 * Owns which secondary panel is currently expanded on top of the full player
 * content. The panel is rendered at a fixed height and moved between pixel
 * anchors so drag motion stays 1:1 with the user's finger.
 */
class PlayerSubPanelState internal constructor(
    private val scope: CoroutineScope,
    val anchoredState: AnchoredDraggableState<SubPanelAnchor>,
    private val targetState: androidx.compose.runtime.MutableState<SubPanel>,
    private val animationSpec: FiniteAnimationSpec<Float>,
) {
    val target: SubPanel get() = targetState.value
    val isAnimating: Boolean get() = anchoredState.isAnimationRunning
    private var closedOffsetPx by mutableFloatStateOf(Float.NaN)

    val fraction: Float
        get() = openFraction()

    val isClosed: Boolean
        get() = targetState.value == SubPanel.None &&
            anchoredState.currentValue == SubPanelAnchor.Closed &&
            openFraction() <= 0.01f

    val isOpen: Boolean
        get() = anchoredState.currentValue == SubPanelAnchor.Open && openFraction() >= 0.99f

    fun updateAnchors(maxPanelHeightPx: Float) {
        val closed = maxPanelHeightPx.coerceAtLeast(1f)
        closedOffsetPx = closed
        anchoredState.updateAnchors(
            newAnchors = DraggableAnchors {
                SubPanelAnchor.Open at 0f
                SubPanelAnchor.Closed at closed
            },
            newTarget = if (targetState.value == SubPanel.None) {
                SubPanelAnchor.Closed
            } else {
                SubPanelAnchor.Open
            },
        )
    }

    fun openFraction(closedOffsetOverridePx: Float = closedOffsetPx): Float {
        if (!closedOffsetOverridePx.isUsableOffset() || closedOffsetOverridePx <= 0f) {
            return if (targetState.value == SubPanel.None) 0f else 1f
        }
        val offset = anchoredState.offset.takeIf { it.isUsableOffset() } ?: closedOffsetOverridePx
        return (1f - (offset / closedOffsetOverridePx)).coerceIn(0f, 1f)
    }

    fun panelOffsetPx(closedOffsetOverridePx: Float = closedOffsetPx): Float {
        val fallback = if (targetState.value == SubPanel.None) {
            closedOffsetOverridePx
        } else {
            0f
        }
        return anchoredState.offset.takeIf { it.isUsableOffset() } ?: fallback
    }

    fun open(panel: SubPanel) {
        if (panel == SubPanel.None) {
            close()
            return
        }
        targetState.value = panel
        scope.launch {
            anchoredState.animateTo(SubPanelAnchor.Open, animationSpec = animationSpec)
        }
    }

    fun beginDrag(panel: SubPanel) {
        if (panel != SubPanel.None) {
            targetState.value = panel
        }
    }

    fun close() {
        scope.launch {
            anchoredState.animateTo(SubPanelAnchor.Closed, animationSpec = animationSpec)
            targetState.value = SubPanel.None
        }
    }

    fun clearPanelIfClosed() {
        if (
            anchoredState.currentValue == SubPanelAnchor.Closed &&
            anchoredState.targetValue == SubPanelAnchor.Closed &&
            !anchoredState.isAnimationRunning
        ) {
            targetState.value = SubPanel.None
        }
    }

    fun dispatchRawDelta(deltaPx: Float): Float = anchoredState.dispatchRawDelta(deltaPx)

    suspend fun settleRawDrag(
        velocityPxPerSecond: Float = 0f,
        dragDeltaPx: Float = 0f,
        commitFraction: Float = SubPanelCommitFraction,
    ) {
        val thresholdPx = (closedOffsetPx.takeIf { it.isUsableOffset() } ?: 1f) * commitFraction
        val targetAnchor = when {
            velocityPxPerSecond < -SubPanelFlingVelocityPxPerSecond -> SubPanelAnchor.Open
            velocityPxPerSecond > SubPanelFlingVelocityPxPerSecond -> SubPanelAnchor.Closed
            dragDeltaPx < -thresholdPx -> SubPanelAnchor.Open
            dragDeltaPx > thresholdPx -> SubPanelAnchor.Closed
            else -> anchoredState.targetValue
        }
        anchoredState.animateTo(targetAnchor, animationSpec = animationSpec)
        if (targetAnchor == SubPanelAnchor.Closed) {
            targetState.value = SubPanel.None
        }
    }

    suspend fun settleClosed() {
        anchoredState.animateTo(SubPanelAnchor.Closed, animationSpec = animationSpec)
        targetState.value = SubPanel.None
    }
}

private fun Float.isUsableOffset(): Boolean = !isNaN() && !isInfinite()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPlayerSubPanelState(): PlayerSubPanelState {
    val scope = rememberCoroutineScope()
    val anchoredState = remember { AnchoredDraggableState(SubPanelAnchor.Closed) }
    val target = remember { mutableStateOf(SubPanel.None) }
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember(scope, anchoredState, target, animationSpec) {
        PlayerSubPanelState(scope, anchoredState, target, animationSpec)
    }
}
