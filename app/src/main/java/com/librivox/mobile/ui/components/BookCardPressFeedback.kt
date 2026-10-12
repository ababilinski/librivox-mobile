package com.librivox.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class BookCardPressFeedback(
    val interactionSource: MutableInteractionSource,
    val isPressed: Boolean,
    val scale: Float,
)

@Composable
fun rememberBookCardPressFeedback(): BookCardPressFeedback {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 720f,
        ),
        label = "book-card-press-scale",
    )
    return BookCardPressFeedback(
        interactionSource = interactionSource,
        isPressed = isPressed,
        scale = scale,
    )
}

@Composable
fun bookCardPressBorder(isPressed: Boolean): BorderStroke? =
    if (isPressed) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
    } else {
        null
    }

@Composable
fun BookCardPressOverlay(isPressed: Boolean, modifier: Modifier = Modifier) {
    if (!isPressed) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
    )
}
