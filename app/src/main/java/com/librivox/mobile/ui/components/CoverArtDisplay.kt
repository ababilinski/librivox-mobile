package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.ui.navigation.LocalAppGraph

val CoverArtDisplayMode.coverArtContentScale: ContentScale
    get() = if (cropsImage) ContentScale.Crop else ContentScale.Fit

fun Modifier.coverArtAspectRatio(displayMode: CoverArtDisplayMode): Modifier =
    aspectRatio(displayMode.frameAspectRatio)

fun Modifier.coverArtRowSize(
    displayMode: CoverArtDisplayMode,
    targetHeight: Dp,
): Modifier =
    height(targetHeight).width(targetHeight * displayMode.frameAspectRatio)

@Composable
fun rememberCoverArtDisplayMode(): CoverArtDisplayMode {
    val graph = LocalAppGraph.current
    val store = graph.app.playbackSettingsStore
    var displayMode by remember(store) { mutableStateOf(store.coverArtDisplayMode) }
    DisposableEffect(store) {
        val listener = store.registerCoverArtDisplayModeListener { displayMode = it }
        onDispose { store.unregisterListener(listener) }
    }
    return displayMode
}
