package com.librivox.mobile.ui.components

import com.librivox.mobile.model.ChapterProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterListeningProgressTest {
    @Test fun pausedChapterUsesLivePositionEvenAfterSeekingBackwards() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 80_000L, durationMs = 100_000L),
            isCurrent = true,
            currentPositionMs = 20_000L,
        )

        assertEquals("Paused", result.statusLabel)
        assertEquals("1:20 left", result.timingLabel)
        assertEquals(0.2f, result.progressFraction!!, 0.001f)
        assertTrue(result.isHighlighted)
    }

    @Test fun restoredHistoryIdentifiesLastListenedWithoutAnActivePlayer() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 30_000L, durationMs = 120_000L),
            isLastListened = true,
        )

        assertEquals("Last listened", result.statusLabel)
        assertEquals("1:30 left", result.timingLabel)
        assertEquals(0.25f, result.progressFraction!!, 0.001f)
        assertTrue(result.isHighlighted)
    }

    @Test fun everyPartiallyListenedChapterKeepsItsOwnRemainingTime() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 45_000L, durationMs = 60_000L),
        )

        assertEquals("In progress", result.statusLabel)
        assertEquals("0:15 left", result.timingLabel)
        assertFalse(result.isHighlighted)
    }

    @Test fun completedChapterHasAFullBarEvenWhenItsLastSavedPositionWasBeforeTheEnd() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 59_000L, durationMs = 60_000L, isCompleted = true),
            isLastListened = true,
        )

        assertEquals("Finished", result.statusLabel)
        assertEquals("1:00 total", result.timingLabel)
        assertEquals(1f, result.progressFraction!!, 0f)
        assertTrue(result.isFinished)
    }

    @Test fun reachingTheKnownDurationDoesNotInventCompletion() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 60_000L, durationMs = 60_000L),
        )

        assertFalse(result.isFinished)
        assertEquals("In progress", result.statusLabel)
        assertEquals("0:00 left", result.timingLabel)
    }

    @Test fun manuallyCompletedPlayingChapterStaysFinishedUntilExplicitReplay() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 60_000L, durationMs = 60_000L, isCompleted = true),
            isCurrent = true,
            isPlaying = true,
            currentPositionMs = 15_000L,
        )

        assertEquals("Finished", result.statusLabel)
        assertEquals("1:00 total", result.timingLabel)
        assertEquals(1f, result.progressFraction!!, 0f)
        assertEquals(15_000L, result.positionMs)
        assertTrue(result.isHighlighted)
    }

    @Test fun manuallyCompletedPausedChapterStaysFinishedBeforeTheEnd() {
        val result = progress(
            saved = ChapterProgressSnapshot(positionMs = 60_000L, durationMs = 60_000L, isCompleted = true),
            isCurrent = true,
            currentPositionMs = 15_000L,
        )

        assertEquals("Finished", result.statusLabel)
        assertEquals("1:00 total", result.timingLabel)
        assertTrue(result.isHighlighted)
    }

    @Test fun explicitlyClearedCompletionShowsLiveReplayProgress() {
        val result = progress(
            saved = ChapterProgressSnapshot(durationMs = 60_000L, isCompleted = false),
            isCurrent = true,
            isPlaying = true,
            currentPositionMs = 15_000L,
        )

        assertEquals("Now playing", result.statusLabel)
        assertEquals("0:45 left", result.timingLabel)
        assertEquals(0.25f, result.progressFraction!!, 0.001f)
    }

    @Test fun currentCompletedChapterStaysFinishedAtTheEnd() {
        val result = progress(
            saved = ChapterProgressSnapshot(durationMs = 60_000L, isCompleted = true),
            isCurrent = true,
            currentPositionMs = 60_000L,
        )

        assertTrue(result.isFinished)
    }

    @Test fun unknownDurationShowsElapsedHistoryWithoutAFabricatedFraction() {
        val result = progress(saved = ChapterProgressSnapshot(positionMs = 75_000L))

        assertEquals("1:15 listened • Duration unknown", result.timingLabel)
        assertNull(result.progressFraction)
    }

    @Test fun completedChapterWithUnknownDurationNeedsNoEstimatedProgress() {
        val result = progress(saved = ChapterProgressSnapshot(isCompleted = true))

        assertTrue(result.isFinished)
        assertEquals("Finished", result.statusLabel)
        assertNull(result.timingLabel)
        assertNull(result.progressFraction)
    }

    @Test fun durationUsesLiveThenRecordedThenCatalogSources() {
        val saved = ChapterProgressSnapshot(positionMs = 10_000L, durationMs = 60_000L)
        assertEquals(90_000L, progress(saved, 120_000L, isCurrent = true, currentDurationMs = 90_000L).durationMs)
        assertEquals(60_000L, progress(saved, 120_000L, currentDurationMs = 90_000L).durationMs)
        assertEquals(120_000L, progress(ChapterProgressSnapshot(), 120_000L).durationMs)
    }

    @Test fun unstartedChapterShowsTotalWithoutCompetingWithStartedProgressBars() {
        val result = progress(chapterDurationMs = 60_000L)

        assertNull(result.statusLabel)
        assertEquals("1:00 total", result.timingLabel)
        assertNull(result.progressFraction)
        assertFalse(result.isHighlighted)
    }

    @Test fun outOfRangePositionNeverProducesNegativeRemainingOrOverflowingProgress() {
        val result = progress(saved = ChapterProgressSnapshot(positionMs = 65_000L, durationMs = 60_000L))

        assertEquals("0:00 left", result.timingLabel)
        assertEquals(1f, result.progressFraction!!, 0f)
        assertEquals(0L, progress(saved = ChapterProgressSnapshot(positionMs = -1L)).positionMs)
    }

    private fun progress(
        saved: ChapterProgressSnapshot = ChapterProgressSnapshot(),
        chapterDurationMs: Long = 0L,
        isCurrent: Boolean = false,
        isPlaying: Boolean = false,
        isLastListened: Boolean = false,
        currentPositionMs: Long = 0L,
        currentDurationMs: Long = 0L,
    ) = chapterListeningProgress(
        savedProgress = saved,
        chapterDurationMs = chapterDurationMs,
        isCurrent = isCurrent,
        isPlaying = isPlaying,
        isLastListened = isLastListened,
        currentPositionMs = currentPositionMs,
        currentDurationMs = currentDurationMs,
    )
}
