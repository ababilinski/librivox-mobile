package com.librivox.mobile.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterNavigationTest {
    private val source = QueuePosition(7, 2, "book:chapter-three")
    private val target = QueuePosition(7, 3, "book:chapter-four")
    private val pending = PendingChapterNavigation(1, source, target)

    @Test fun controllerAcknowledgementBeforeAnimationDoesNotLeaveASwipeLock() {
        val acknowledged = pending.reconcile(CommittedPlayback(true, target))!!
        assertFalse(acknowledged.complete)
        assertTrue(acknowledged.copy(animationFinished = true).complete)
    }

    @Test fun animationBeforeControllerAcknowledgementWaitsForTheActualTarget() {
        val animated = pending.copy(animationFinished = true)
        assertFalse(animated.reconcile(CommittedPlayback(true, source))!!.complete)
        assertTrue(animated.reconcile(CommittedPlayback(true, target))!!.complete)
    }

    @Test fun duplicatedMediaIdAtAnotherIndexCannotAcknowledgeTheSeek() {
        val repeatedTarget = target.copy(mediaId = source.mediaId)
        val repeated = pending.copy(target = repeatedTarget)
        assertFalse(repeated.reconcile(CommittedPlayback(true, source))!!.controllerAcknowledged)
        assertTrue(repeated.reconcile(CommittedPlayback(true, repeatedTarget))!!.controllerAcknowledged)
    }

    @Test fun reorderedQueueCancelsEvenWhenTheSameChapterOccupiesTheSameIndex() {
        assertNull(pending.reconcile(CommittedPlayback(true, target.copy(version = 8))))
        assertFalse(queueSeekIsValid(source.copy(version = 8), source, target.copy(version = 8), target))
    }

    @Test fun unrelatedControllerTransitionCancelsThePendingPreview() {
        assertNull(pending.reconcile(CommittedPlayback(true, target.copy(index = 4, mediaId = "other"))))
    }

    @Test fun errorAndDisconnectCancelThePendingPreview() {
        assertNull(pending.reconcile(CommittedPlayback(true, source, hasError = true)))
        assertNull(pending.reconcile(CommittedPlayback()))
    }

    @Test fun staleGestureCannotSeekFromAnAlreadyChangedSource() {
        assertFalse(queueSeekIsValid(target, source, target, target))
        assertFalse(queueSeekIsValid(source, source, null, target))
        assertTrue(queueSeekIsValid(source, source, target, target))
    }

    @Test fun subsequentNavigationHasIndependentCompletionFlags() {
        val completed = pending.copy(animationFinished = true)
            .reconcile(CommittedPlayback(true, target))!!
        assertTrue(completed.complete)
        val returning = PendingChapterNavigation(2, target, source)
        assertFalse(returning.reconcile(CommittedPlayback(true, target))!!.complete)
        assertTrue(returning.copy(animationFinished = true)
            .reconcile(CommittedPlayback(true, source))!!.complete)
    }
}
