package com.librivox.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Tonal surface tokens for Google-style separation between page canvas,
 * repeated rows, persistent bottom chrome, floating panes, and inline
 * controls. Recipe from the M3 Expressive plugin (tonal-surface-separation):
 * lerp from the canonical Material role toward [MaterialTheme.colorScheme.surfaceVariant]
 * to strengthen the contrast in Dynamic Color and dark themes without
 * leaving the token system.
 */
@Immutable
data class TonalSurfaces(
    val screenBackground: Color,
    val listItem: Color,
    val bottomChrome: Color,
    val floatingPane: Color,
    val inlineAction: Color,
    val sectionHighlight: Color,
)

val LocalTonalSurfaces = staticCompositionLocalOf<TonalSurfaces> {
    error("TonalSurfaces not provided. Wrap content in AudioPlayerTheme.")
}

@Composable
fun rememberTonalSurfaces(): TonalSurfaces {
    val c = MaterialTheme.colorScheme
    val bottomChrome = lerp(c.surfaceContainerHighest, c.secondaryContainer, 0.34f)
    return TonalSurfaces(
        screenBackground = c.surfaceContainerLow,
        listItem = lerp(c.surfaceContainerHighest, c.surfaceVariant, 0.38f),
        bottomChrome = bottomChrome,
        floatingPane = lerp(c.surfaceContainerHighest, c.surfaceVariant, 0.56f),
        inlineAction = lerp(c.surfaceContainerHigh, c.surfaceVariant, 0.24f),
        sectionHighlight = c.secondaryContainer,
    )
}
