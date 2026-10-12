package com.librivox.mobile.playback

/** A queue slot, including its generation so reordering cannot redirect a pending seek. */
data class QueuePosition(val version: Long, val index: Int, val mediaId: String)

data class QueueChapter(
    val position: QueuePosition,
    val bookId: String?,
    val chapterId: String?,
    val title: String,
    val bookTitle: String?,
    val author: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val queueSize: Int,
)

/** Controller facts only: never populated from the optimistic chapter preview. */
data class CommittedPlayback(
    val connected: Boolean = false,
    val position: QueuePosition? = null,
    val hasError: Boolean = false,
)

internal const val ChapterNavigationTimeoutMillis = 2_000L

internal data class PendingChapterNavigation(
    val requestId: Long,
    val source: QueuePosition,
    val target: QueuePosition,
    val animationFinished: Boolean = false,
    val controllerAcknowledged: Boolean = false,
) {
    val complete: Boolean get() = animationFinished && controllerAcknowledged

    fun reconcile(committed: CommittedPlayback): PendingChapterNavigation? {
        if (!committed.connected || committed.hasError) return null
        val current = committed.position ?: return null
        if (current.version != source.version || target.version != source.version) return null
        return when (current) {
            target -> copy(controllerAcknowledged = true)
            source -> if (controllerAcknowledged) null else this
            else -> null
        }
    }
}

internal fun queueSeekIsValid(
    actualSource: QueuePosition?,
    expectedSource: QueuePosition,
    actualTarget: QueuePosition?,
    expectedTarget: QueuePosition,
): Boolean = actualSource == expectedSource && actualTarget == expectedTarget &&
    expectedSource.version == expectedTarget.version
