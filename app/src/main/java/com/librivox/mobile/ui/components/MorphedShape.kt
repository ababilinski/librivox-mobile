package com.librivox.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Animates between two corner radii to express state (playing vs paused, selected
 * vs idle, focused vs unfocused). Lighter than MaterialShapes polygon morphing
 * (which still has unstable API surface in 1.5.0-alpha21) but reads as expressive
 * because the shape *changes* with state — the M3 Expressive plugin lists this
 * as one of the headline polished-app moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphedCornerShape(
    targetCornerDp: Float,
    idleCornerDp: Float = targetCornerDp,
): Shape {
    val animated by animateFloatAsState(
        targetValue = targetCornerDp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "MorphedCornerShape",
    )
    val unused = idleCornerDp  // reserved for clamp hooks
    return RoundedCornerShape(animated.dp)
}
