package com.librivox.mobile.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SheetSettleVelocityThresholdPx = 160f
private const val SheetSettleExpandDragFraction = 0.04f
private const val SheetSettleCollapseDragFraction = 0.18f
private const val SheetSettleAnchorFraction = 0.10f

/**
 * Owns the player-sheet expansion fraction. 0f = mini bar at bottom,
 * 1f = full-screen player. Drag gestures and predictive-back both write
 * through the same [Animatable] so motion stays consistent.
 */
class PlayerSheetState internal constructor(
    private val scope: CoroutineScope,
    private val animatable: Animatable<Float, AnimationVector1D>,
    private val animationSpec: FiniteAnimationSpec<Float>,
) {
    val value: Float get() = animatable.value
    val isExpanded: Boolean get() = animatable.value >= 0.99f
    val isCollapsed: Boolean get() = animatable.value <= 0.01f
    val isAnimating: Boolean get() = animatable.isRunning

    fun expand() {
        scope.launch { animatable.animateTo(1f, animationSpec = animationSpec) }
    }

    fun collapse() {
        scope.launch { animatable.animateTo(0f, animationSpec = animationSpec) }
    }

    suspend fun snapTo(value: Float) {
        animatable.snapTo(value.coerceIn(0f, 1f))
    }

    suspend fun animateTo(value: Float) {
        animatable.animateTo(value.coerceIn(0f, 1f), animationSpec = animationSpec)
    }

    suspend fun settle(
        velocityPxPerSecond: Float = 0f,
        dragDeltaFraction: Float = 0f,
    ) {
        val target = when {
            velocityPxPerSecond < -SheetSettleVelocityThresholdPx -> 1f
            velocityPxPerSecond > SheetSettleVelocityThresholdPx -> 0f
            dragDeltaFraction > SheetSettleExpandDragFraction -> 1f
            dragDeltaFraction < -SheetSettleCollapseDragFraction -> 0f
            animatable.value >= SheetSettleAnchorFraction -> 1f
            else -> 0f
        }
        animatable.animateTo(target, animationSpec = animationSpec)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPlayerSheetState(initial: Float = 0f): PlayerSheetState {
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(initial.coerceIn(0f, 1f)) }
    val animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember(scope, animatable, animationSpec) {
        PlayerSheetState(scope, animatable, animationSpec)
    }
}
