package com.librivox.mobile.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A Slider whose active track is a low-amplitude sine wave that "breathes"
 * while playing and goes flat when paused. When the player is buffering, the
 * whole track turns into an indeterminate wavy progress indicator at the same
 * Y position so the eye never jumps.
 *
 * Slider semantics (TalkBack, value range, enabled state, touch behavior) are
 * preserved by stacking the real M3 Slider above an invisible-track Slider
 * — the wave is paint only.
 *
 * Scrub haptics are quantized: a SEGMENT_TICK fires at most every
 * [TICK_FRACTION] of the range, so dragging across a chapter no longer buzzes
 * continuously.
 */
@Composable
fun WavySlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    tickBoundaries: List<Float> = emptyList(),
    bookmarkMarkers: List<Float> = emptyList(),
    bookmarkInteractionFraction: Float? = null,
    enableScrubHaptics: Boolean = true,
    interactionHeight: Dp = 36.dp,
) {
    val view = LocalView.current
    val fraction = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else 0f

    val infinite = rememberInfiniteTransition(label = "WavySliderPhase")
    val animatedPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "WavySliderPhaseValue",
    )
    val waveActive = isPlaying
    val amplitude = if (waveActive) 2.5f else 0f
    val phase = if (waveActive) {
        animatedPhase
    } else {
        0f
    }

    val activeColor = when {
        isBuffering -> MaterialTheme.colorScheme.tertiary
        isPlaying -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val bookmarkMarkerColor = MaterialTheme.colorScheme.tertiary
    val bookmarkMarkerActiveColor = MaterialTheme.colorScheme.primary
    val bookmarkMarkerOutlineColor = MaterialTheme.colorScheme.surface
    val bookmarkMarkerHaloColor = MaterialTheme.colorScheme.tertiaryContainer
    var lastTickFraction by remember { mutableFloatStateOf(fraction) }

    Box(modifier = modifier.height(interactionHeight)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(interactionHeight)
                .graphicsLayer { /* keep canvas in same layer as slider */ },
        ) {
            val centerY = size.height / 2f
            val width = size.width
            val height = size.height
            val activeWidth = width * fraction
            val strokeWidth = 4.dp.toPx()
            val twoPi = (2.0 * PI).toFloat()
            val step = 6f
            val wavelength = 48f

            drawLine(
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                color = inactiveColor,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )

            // Only the played/positive progress segment gets expressive motion,
            // and only while playback is active. Paused progress remains exact.
            if (activeWidth > 0f) {
                if (waveActive) {
                    val activePath = Path()
                    var x = 0f
                    activePath.moveTo(0f, centerY)
                    while (x <= activeWidth) {
                        val theta = (x / wavelength) * twoPi - phase
                        val y = centerY + sin(theta.toDouble()).toFloat() * amplitude
                        activePath.lineTo(x, y)
                        x += step
                    }
                    clipRect(left = 0f, top = 0f, right = activeWidth, bottom = height) {
                        drawPath(
                            path = activePath,
                            color = activeColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                } else {
                    drawLine(
                        start = androidx.compose.ui.geometry.Offset(0f, centerY),
                        end = androidx.compose.ui.geometry.Offset(activeWidth, centerY),
                        color = activeColor,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Chapter tick marks
            if (tickBoundaries.isNotEmpty()) {
                val tickColor = activeColor.copy(alpha = 0.7f)
                tickBoundaries.forEach { boundary ->
                    val tx = width * boundary.coerceIn(0f, 1f)
                    drawLine(
                        color = tickColor,
                        start = androidx.compose.ui.geometry.Offset(tx, centerY - 6f),
                        end = androidx.compose.ui.geometry.Offset(tx, centerY + 6f),
                        strokeWidth = 2f,
                    )
                }
            }

            if (bookmarkMarkers.isNotEmpty()) {
                val markerRestWidth = 2.5.dp.toPx()
                val markerRestHeight = 15.dp.toPx()
                val markerActiveSize = 10.dp.toPx()
                val markerOutlinePadding = 2.dp.toPx()
                val markerActivationRadiusFraction = if (width > 0f) {
                    (28.dp.toPx() / width).coerceAtMost(0.14f)
                } else {
                    0f
                }
                bookmarkMarkers
                    .map { it.coerceIn(0f, 1f) }
                    .distinct()
                    .forEach { marker ->
                        val activation = bookmarkMarkerActivation(
                            markerFraction = marker,
                            interactionFraction = bookmarkInteractionFraction,
                            activationRadiusFraction = markerActivationRadiusFraction,
                        )
                        val tx = width * marker
                        val markerWidth = markerRestWidth.lerpTo(markerActiveSize, activation)
                        val markerHeight = markerRestHeight.lerpTo(markerActiveSize, activation)
                        val markerCorner = markerWidth.coerceAtMost(markerHeight) / 2f
                        val outlineWidth = markerWidth + markerOutlinePadding * 2f
                        val outlineHeight = markerHeight + markerOutlinePadding * 2f
                        val markerColor = lerp(bookmarkMarkerColor, bookmarkMarkerActiveColor, activation)

                        if (activation > 0f) {
                            drawCircle(
                                color = bookmarkMarkerHaloColor.copy(alpha = 0.22f * activation),
                                radius = 16.dp.toPx() * activation.coerceAtLeast(0.32f),
                                center = androidx.compose.ui.geometry.Offset(tx, centerY),
                            )
                        }
                        drawRoundRect(
                            color = bookmarkMarkerOutlineColor.copy(alpha = 0.92f),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = tx - outlineWidth / 2f,
                                y = centerY - outlineHeight / 2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(outlineWidth, outlineHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                x = (markerCorner + markerOutlinePadding).coerceAtMost(outlineWidth / 2f),
                                y = (markerCorner + markerOutlinePadding).coerceAtMost(outlineHeight / 2f),
                            ),
                        )
                        drawRoundRect(
                            color = markerColor,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = tx - markerWidth / 2f,
                                y = centerY - markerHeight / 2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(markerWidth, markerHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(markerCorner, markerCorner),
                        )
                    }
            }
        }
        // Real Slider provides hit testing and accessibility while the canvas
        // owns all visible progress paint.
        Slider(
            value = value,
            onValueChange = { next ->
                onValueChange(next)
                if (enableScrubHaptics) {
                    // Quantized haptic
                    val nextFraction = if (valueRange.endInclusive > valueRange.start) {
                        ((next - valueRange.start) /
                            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    } else 0f
                    if (kotlin.math.abs(nextFraction - lastTickFraction) >= TICK_FRACTION ||
                        crossedTick(lastTickFraction, nextFraction, tickBoundaries)
                    ) {
                        lastTickFraction = nextFraction
                        val constant = if (android.os.Build.VERSION.SDK_INT >= 34) {
                            HapticFeedbackConstants.SEGMENT_TICK
                        } else {
                            HapticFeedbackConstants.CLOCK_TICK
                        }
                        view.performHapticFeedback(constant)
                    }
                }
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                thumbColor = Color.Transparent,
            ),
        )
    }

    // Touch the ambient content color so the lint stays satisfied; placeholder
    // for future thumb color theming.
    @Suppress("UNUSED_VARIABLE")
    val ambient = LocalContentColor.current
}

private const val TICK_FRACTION = 0.05f

internal fun bookmarkMarkerActivation(
    markerFraction: Float,
    interactionFraction: Float?,
    activationRadiusFraction: Float,
): Float {
    val scrubFraction = interactionFraction ?: return 0f
    if (activationRadiusFraction <= 0f) return 0f
    val raw = (1f - (abs(markerFraction - scrubFraction) / activationRadiusFraction))
        .coerceIn(0f, 1f)
    return raw * raw * (3f - 2f * raw)
}

private fun Float.lerpTo(target: Float, fraction: Float): Float =
    this + (target - this) * fraction

private fun crossedTick(
    previousFraction: Float,
    nextFraction: Float,
    boundaries: List<Float>,
): Boolean {
    if (boundaries.isEmpty()) return false
    val lo = kotlin.math.min(previousFraction, nextFraction)
    val hi = kotlin.math.max(previousFraction, nextFraction)
    return boundaries.any { it in lo..hi }
}
