package com.librivox.mobile.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * Returns a [ColorScheme] for the player sheet that animates toward the
 * album-art-derived scheme for [coverUrl]. Interpolation runs through ONE
 * [Animatable<Float>] so we're not running 30+ parallel `animateColorAsState`
 * coroutines per frame — that's PixelPlayer's documented perf trick.
 *
 * Falls back to the global [MaterialTheme.colorScheme] when [coverUrl] is null,
 * blank, or extraction fails.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPlayerSheetColorScheme(coverUrl: String?): ColorScheme {
    val context = LocalContext.current
    val baseScheme = MaterialTheme.colorScheme
    val dark = baseScheme.background.luminance() < 0.5f

    var startScheme by remember { mutableStateOf(baseScheme) }
    var targetScheme by remember { mutableStateOf(baseScheme) }
    val progress = remember { Animatable(1f) }
    val colorEffectsSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

    LaunchedEffect(coverUrl, dark, baseScheme) {
        val url = coverUrl?.takeIf { it.isNotBlank() }
        val schemes = url?.let { AlbumArtColorExtractor.extract(context, it) }
        val nextTarget = schemes?.let { if (dark) it.dark else it.light } ?: baseScheme
        if (nextTarget == targetScheme) return@LaunchedEffect
        startScheme = blend(startScheme, targetScheme, progress.value)
        targetScheme = nextTarget
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = colorEffectsSpec)
    }

    val current by remember {
        derivedStateOf { blend(startScheme, targetScheme, progress.value) }
    }
    return current
}

private fun blend(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme {
    val ratio = t.coerceIn(0f, 1f)
    return from.copy(
        primary = blendColor(from.primary, to.primary, ratio),
        onPrimary = blendColor(from.onPrimary, to.onPrimary, ratio),
        primaryContainer = blendColor(from.primaryContainer, to.primaryContainer, ratio),
        onPrimaryContainer = blendColor(from.onPrimaryContainer, to.onPrimaryContainer, ratio),
        secondary = blendColor(from.secondary, to.secondary, ratio),
        onSecondary = blendColor(from.onSecondary, to.onSecondary, ratio),
        secondaryContainer = blendColor(from.secondaryContainer, to.secondaryContainer, ratio),
        onSecondaryContainer = blendColor(from.onSecondaryContainer, to.onSecondaryContainer, ratio),
        tertiary = blendColor(from.tertiary, to.tertiary, ratio),
        onTertiary = blendColor(from.onTertiary, to.onTertiary, ratio),
        tertiaryContainer = blendColor(from.tertiaryContainer, to.tertiaryContainer, ratio),
        onTertiaryContainer = blendColor(from.onTertiaryContainer, to.onTertiaryContainer, ratio),
        background = blendColor(from.background, to.background, ratio),
        onBackground = blendColor(from.onBackground, to.onBackground, ratio),
        surface = blendColor(from.surface, to.surface, ratio),
        onSurface = blendColor(from.onSurface, to.onSurface, ratio),
        surfaceVariant = blendColor(from.surfaceVariant, to.surfaceVariant, ratio),
        onSurfaceVariant = blendColor(from.onSurfaceVariant, to.onSurfaceVariant, ratio),
        surfaceContainer = blendColor(from.surfaceContainer, to.surfaceContainer, ratio),
        surfaceContainerLow = blendColor(from.surfaceContainerLow, to.surfaceContainerLow, ratio),
        surfaceContainerLowest = blendColor(from.surfaceContainerLowest, to.surfaceContainerLowest, ratio),
        surfaceContainerHigh = blendColor(from.surfaceContainerHigh, to.surfaceContainerHigh, ratio),
        surfaceContainerHighest = blendColor(from.surfaceContainerHighest, to.surfaceContainerHighest, ratio),
        outline = blendColor(from.outline, to.outline, ratio),
        outlineVariant = blendColor(from.outlineVariant, to.outlineVariant, ratio),
    )
}

private fun blendColor(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

@Suppress("unused")
private fun fallback(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme() else lightColorScheme()
