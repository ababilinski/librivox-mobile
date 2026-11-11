package com.librivox.mobile.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ProgressStoreTest {
    private lateinit var context: Context
    private lateinit var store: ProgressStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("audiobook_progress", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ProgressStore(context)
    }

    @Test
    fun legacyPositionsAndLastChapterAreKeptWithoutInventingCompletion() {
        context.getSharedPreferences("audiobook_progress", Context.MODE_PRIVATE).edit()
            .putLong("position_book_chapter-2", 42_000L)
            .putString("last_chapter_book", "chapter-2")
            .putInt("last_chapter_index_book", 1)
            .apply()

        assertEquals(42_000L, store.savedPositionMs("book"))
        assertEquals("chapter-2", store.lastChapterId("book"))
        assertEquals(1, store.lastChapterIndex("book"))
        assertEquals(
            ChapterProgressSnapshot(positionMs = 42_000L),
            store.savedChapterProgress("book", "chapter-2"),
        )
    }

    @Test
    fun completionSurvivesPassivePositionResetAndReopeningStore() {
        store.savePosition("book", "chapter", 0, 60_000L, 60_000L)
        store.markChapterCompleted("book", "chapter", 60_000L)
        store.savePosition("book", "chapter", 0, 0L)

        val reopened = ProgressStore(context)
        assertEquals(0L, reopened.savedPositionMs("book", "chapter"))
        assertEquals(
            ChapterProgressSnapshot(positionMs = 0L, durationMs = 60_000L, isCompleted = true),
            reopened.savedChapterProgress("book", "chapter"),
        )
    }

    @Test
    fun explicitReplayClearsCompletionAndKeepsDurationAndOtherChapterActivity() {
        store.savePosition("book", "chapter-1", 0, 60_000L, 60_000L)
        store.markChapterCompleted("book", "chapter-1", 60_000L)
        store.savePosition("book", "chapter-2", 1, 30_000L)
        val lastActivity = store.lastActivityMillis("book")

        store.startChapterReplay("book", "chapter-1")

        val reopened = ProgressStore(context)
        assertEquals(
            ChapterProgressSnapshot(positionMs = 0L, durationMs = 60_000L, isCompleted = false),
            reopened.savedChapterProgress("book", "chapter-1"),
        )
        assertEquals("chapter-2", reopened.lastChapterId("book"))
        assertEquals(30_000L, reopened.savedPositionMs("book"))
        assertEquals(lastActivity, reopened.lastActivityMillis("book"))

        reopened.savePosition("book", "chapter-1", 0, 15_000L)
        assertEquals(
            ChapterProgressSnapshot(positionMs = 15_000L, durationMs = 60_000L, isCompleted = false),
            reopened.savedChapterProgress("book", "chapter-1"),
        )
    }

    @Test
    fun manualCompletionPreservesResumeAndRemainsFinishedAfterPlayerProgressUpdates() {
        store.savePosition("book", "chapter-1", 0, 15_000L, 60_000L)
        store.savePosition("book", "chapter-2", 1, 30_000L)
        val lastActivity = store.lastActivityMillis("book")

        store.markChapterCompleted("book", "chapter-1")

        assertEquals(15_000L, store.savedPositionMs("book", "chapter-1"))
        assertEquals("chapter-2", store.lastChapterId("book"))
        assertEquals(lastActivity, store.lastActivityMillis("book"))

        store.savePosition("book", "chapter-1", 0, 16_000L, 60_000L)
        val reopened = ProgressStore(context)
        assertTrue(reopened.savedChapterProgress("book", "chapter-1").isCompleted)
        assertEquals(16_000L, reopened.savedPositionMs("book", "chapter-1"))
    }

    @Test
    fun markUnfinishedPreservesPartialPositionDurationAndLastChapter() {
        store.savePosition("book", "chapter-1", 0, 15_000L, 60_000L)
        store.markChapterCompleted("book", "chapter-1")
        store.savePosition("book", "chapter-2", 1, 30_000L)
        val lastActivity = store.lastActivityMillis("book")

        store.markChapterUnfinished("book", "chapter-1")

        assertEquals(
            ChapterProgressSnapshot(positionMs = 15_000L, durationMs = 60_000L, isCompleted = false),
            ProgressStore(context).savedChapterProgress("book", "chapter-1"),
        )
        assertEquals("chapter-2", store.lastChapterId("book"))
        assertEquals(lastActivity, store.lastActivityMillis("book"))
        store.savePosition("book", "chapter-1", 0, 60_000L, 60_000L)
        assertFalse(store.savedChapterProgress("book", "chapter-1").isCompleted)
    }

    @Test
    fun markUnfinishedResetsOnlySavedEndpointSoChapterCanBePlayedAgain() {
        store.savePosition("book", "chapter", 0, 59_999L, 60_000L)
        store.markChapterUnfinished("book", "chapter")

        assertEquals(
            ChapterProgressSnapshot(positionMs = 0L, durationMs = 60_000L, isCompleted = false),
            ProgressStore(context).savedChapterProgress("book", "chapter"),
        )
    }

    @Test
    fun exactLegacyEndpointIsRecoveredAsComplete() {
        store.savePosition("book", "chapter", 0, 60_000L, 60_000L)

        assertTrue(store.savedChapterProgress("book", "chapter").isCompleted)
    }

    @Test
    fun legacyEndpointWithinOneMillisecondOfMetadataDurationIsComplete() {
        store.savePosition("book", "chapter", 0, 1_407_555L, 1_407_556L)

        assertTrue(store.savedChapterProgress("book", "chapter").isCompleted)
    }

    @Test
    fun legacyPositionWithOneSecondRemainingIsStillIncomplete() {
        store.savePosition("book", "chapter", 0, 59_000L, 60_000L)

        assertFalse(store.savedChapterProgress("book", "chapter").isCompleted)
    }

    @Test
    fun explicitReplayFalseOverridesLegacyEndpointInference() {
        store.savePosition("book", "chapter", 0, 60_000L, 60_000L)
        store.startChapterReplay("book", "chapter")
        store.savePosition("book", "chapter", 0, 60_000L, 60_000L)

        assertFalse(ProgressStore(context).savedChapterProgress("book", "chapter").isCompleted)
    }

    @Test
    fun unknownDurationDuringReloadDoesNotLoseMeasuredDuration() {
        store.savePosition("book", "chapter", 0, 15_000L, 60_000L)
        store.savePosition("book", "chapter", 0, 16_000L, Long.MIN_VALUE + 1)

        assertEquals(60_000L, store.savedChapterProgress("book", "chapter").durationMs)
        assertEquals(16_000L, store.savedPositionMs("book", "chapter"))
    }

    @Test
    fun durationBackfillDoesNotChangeResumeChapterOrActivity() {
        store.savePosition("book", "chapter-2", 1, 30_000L)
        val lastActivity = store.lastActivityMillis("book")
        val revision = store.revision.value

        store.saveChapterDuration("book", "chapter-1", 90_000L)
        store.saveChapterDuration("book", "chapter-1", 90_000L)
        store.saveChapterDuration("book", "chapter-1", 0L)

        assertEquals("chapter-2", store.lastChapterId("book"))
        assertEquals(30_000L, store.savedPositionMs("book"))
        assertEquals(lastActivity, store.lastActivityMillis("book"))
        assertEquals(revision + 1L, store.revision.value)
        assertEquals(90_000L, store.savedChapterProgress("book", "chapter-1").durationMs)
    }

    @Test
    fun completingPreviousChapterDoesNotMoveLastListenedChapterBack() {
        store.savePosition("book", "chapter-1", 0, 59_000L, 60_000L)
        store.savePosition("book", "chapter-2", 1, 1_000L)
        store.markChapterCompleted("book", "chapter-1", 60_000L)

        assertEquals("chapter-2", store.lastChapterId("book"))
        assertEquals(1, store.lastChapterIndex("book"))
        assertEquals(1_000L, store.savedPositionMs("book"))
        assertTrue(store.savedChapterProgress("book", "chapter-1").isCompleted)
        assertFalse(store.savedChapterProgress("book", "chapter-2").isCompleted)
    }
}
