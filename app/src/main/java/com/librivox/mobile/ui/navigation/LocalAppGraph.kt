package com.librivox.mobile.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.librivox.mobile.AudiobookApplication
import com.librivox.mobile.playback.PlayerStateRepository

/**
 * App-wide singletons exposed to Compose without prop drilling.
 * Provided once at the top of the composition by [com.librivox.mobile.MainActivity].
 */
data class AppGraph(
    val app: AudiobookApplication,
    val playerStateRepository: PlayerStateRepository,
    val openCastPicker: () -> Unit = {},
)

val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph not provided. Wrap content in CompositionLocalProvider(LocalAppGraph provides ...).")
}
