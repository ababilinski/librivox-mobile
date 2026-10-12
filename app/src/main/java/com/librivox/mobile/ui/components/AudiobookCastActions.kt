package com.librivox.mobile.ui.components

import android.content.Context
import android.widget.Toast
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.chapterIndexOrZero
import com.librivox.mobile.model.isCastable
import com.librivox.mobile.model.isLocalCastCandidate
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.LocalCastHttpServer
import com.librivox.mobile.ui.navigation.AppGraph

fun castAudiobookAudio(
    context: Context,
    graph: AppGraph,
    book: AudioBook,
    chapterId: String? = null,
    positionMs: Long? = null,
): Boolean {
    val mediaItems = book.toMediaItems(context)
    if (mediaItems.isEmpty()) {
        Toast.makeText(context, "No playable audio for this audiobook.", Toast.LENGTH_SHORT).show()
        return false
    }

    val currentState = graph.playerStateRepository.state.value
    val targetChapterId = chapterId
        ?: currentState.chapterId.takeIf { currentState.bookId == book.id && !it.isNullOrBlank() }
        ?: graph.app.progressStore.lastChapterId(book.id)
        ?: book.chapters.firstOrNull()?.id
    val targetChapter = book.chapters.firstOrNull { it.id == targetChapterId }
    val localNetworkAvailable = LocalCastHttpServer.hasUsableLocalNetwork(context)
    if (targetChapter?.isCastable(localNetworkAvailable) != true) {
        Toast.makeText(
            context,
            targetChapter.castUnavailableReason(context),
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    val startPositionMs = positionMs
        ?: targetChapterId?.let { graph.app.progressStore.savedPositionMs(book.id, it) }
        ?: 0L
    if (currentState.bookId == book.id && currentState.chapterId == targetChapterId) {
        positionMs?.let { graph.playerStateRepository.seekTo(it) }
        if (!currentState.isPlaying) {
            graph.playerStateRepository.playPause()
        }
    } else {
        graph.playerStateRepository.playMediaItems(
            items = mediaItems,
            startIndex = book.chapterIndexOrZero(targetChapterId),
            startPositionMs = startPositionMs,
        )
    }
    graph.openCastPicker()
    return true
}

private fun AudioBookChapter?.castUnavailableReason(context: Context): String {
    val chapter = this ?: return "Unsupported for this audiobook."
    if (!chapter.isLocalCastCandidate()) return "Unsupported for this audiobook."
    return if (LocalCastHttpServer.hasLocalNetworkAccess(context)) {
        "Connect this phone and Cast device to the same Wi-Fi network."
    } else {
        "Allow local network access to cast downloaded audio."
    }
}
