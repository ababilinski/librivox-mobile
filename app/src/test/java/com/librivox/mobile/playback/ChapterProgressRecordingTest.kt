package com.librivox.mobile.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.librivox.mobile.model.MEDIA_METADATA_CAST_DURATION_MS
import com.librivox.mobile.model.ProgressStore
import com.librivox.mobile.model.mediaId
import java.lang.reflect.Proxy
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
class ChapterProgressRecordingTest {
    private lateinit var store: ProgressStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("audiobook_progress", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ProgressStore(context)
    }

    @Test
    fun automaticAdvanceCompletesOnlyDepartingChapterAndRetainsItsDuration() {
        store.savePosition("book", "first", 0, 58_000L, 60_000L)
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 60_000L),
            position("second", index = 1, positionMs = 0L),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        )
        store.savePosition("book", "second", 1, 0L)

        assertTrue(store.savedChapterProgress("book", "first").isCompleted)
        assertEquals(60_000L, store.savedChapterProgress("book", "first").durationMs)
        assertFalse(store.savedChapterProgress("book", "second").isCompleted)
        assertEquals("second", store.lastChapterId("book"))
    }

    @Test
    fun skippingOrReplacingChapterKeepsResumePositionWithoutCompletion() {
        for (reason in listOf(
            Player.DISCONTINUITY_REASON_SEEK,
            Player.DISCONTINUITY_REASON_SKIP,
            Player.DISCONTINUITY_REASON_REMOVE,
            Player.DISCONTINUITY_REASON_INTERNAL,
        )) {
            val chapter = "first-$reason"
            store.saveDepartingChapterProgress(
                position(chapter, index = 0, positionMs = 45_000L),
                position("second", index = 1, positionMs = 0L),
                reason,
            )

            assertFalse("reason=$reason", store.savedChapterProgress("book", chapter).isCompleted)
            assertEquals(45_000L, store.savedPositionMs("book", chapter))
        }
    }

    @Test
    fun seekDiscontinuityAloneDoesNotCompleteDepartingChapter() {
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 10_000L),
            position("first", index = 0, positionMs = 60_000L),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        assertFalse(store.savedChapterProgress("book", "first").isCompleted)
    }

    @Test
    fun clearingQueuePreservesDepartingChapterAndDoesNotCompleteIt() {
        val emptyPosition = Player.PositionInfo(
            null, 0, null, null, 0, 0L, 0L, C.INDEX_UNSET, C.INDEX_UNSET,
        )
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 45_000L),
            emptyPosition,
            Player.DISCONTINUITY_REASON_REMOVE,
        )

        assertEquals(45_000L, store.savedPositionMs("book", "first"))
        assertFalse(store.savedChapterProgress("book", "first").isCompleted)
    }

    @Test
    fun automaticRepeatCompletesListenAndZeroResetKeepsHistory() {
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 60_000L),
            position("first", index = 0, positionMs = 0L),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        )
        store.savePosition("book", "first", 0, 0L)

        assertTrue(store.savedChapterProgress("book", "first").isCompleted)
        assertEquals(60_000L, store.savedChapterProgress("book", "first").durationMs)
    }

    @Test
    fun finalChapterEndStoresCompletionAndMeasuredDuration() {
        store.saveCompletedFinalChapter(chapter("final"), 90_000L, 90_000L)

        assertTrue(store.savedChapterProgress("book", "final").isCompleted)
        assertEquals(90_000L, store.savedChapterProgress("book", "final").durationMs)
    }

    @Test
    fun finalChapterWithUnknownDurationUsesItsFinishedPosition() {
        store.saveCompletedFinalChapter(chapter("final"), 90_000L, C.TIME_UNSET)

        assertTrue(store.savedChapterProgress("book", "final").isCompleted)
        assertEquals(90_000L, store.savedChapterProgress("book", "final").durationMs)
    }

    @Test
    fun emptyQueueOrUnplayedEndCannotCompleteChapter() {
        store.saveCompletedFinalChapter(null, 90_000L, 90_000L)
        store.saveCompletedFinalChapter(chapter("final"), 0L, C.TIME_UNSET)

        assertFalse(store.savedChapterProgress("book", "final").isCompleted)
    }

    @Test
    fun transitionToAdDoesNotCompleteChapter() {
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 20_000L),
            position("first", index = 0, positionMs = 0L, adGroupIndex = 0),
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
        )

        assertFalse(store.savedChapterProgress("book", "first").isCompleted)
    }

    @Test
    fun metadataDurationIsRetainedWhenSkippingBeforePlayerHasMeasuredIt() {
        store.saveDepartingChapterProgress(
            position("first", index = 0, positionMs = 10_000L, durationMs = 120_000L),
            position("second", index = 1, positionMs = 0L),
            Player.DISCONTINUITY_REASON_SEEK,
        )

        assertEquals(120_000L, store.savedChapterProgress("book", "first").durationMs)
        assertFalse(store.savedChapterProgress("book", "first").isCompleted)
    }

    @Test
    fun markingCurrentEndedChapterUnfinishedPausesBeforeResetAndLaterSavesKeepZero() {
        store.savePosition("book", "first", 0, 60_000L, 60_000L)
        store.markChapterCompleted("book", "first")
        val controller = RecordingPlayer(60_000L, isPlaying = false, playbackState = Player.STATE_ENDED)

        markChapterUnfinishedWithoutStartingPlayback(store, controller.player, "book", "first")
        // A later service-destruction snapshot uses the reset controller position.
        store.savePosition("book", "first", 0, controller.positionMs, 60_000L)

        assertEquals(listOf("pause", "seek:0"), controller.actions)
        assertFalse(controller.playWhenReady)
        assertEquals(0L, store.savedPositionMs("book", "first"))
        assertFalse(store.savedChapterProgress("book", "first").isCompleted)
    }

    @Test
    fun markingPausedKnownEndpointUnfinishedAlsoResetsReadyController() {
        store.savePosition("book", "first", 0, 59_999L, 60_000L)
        store.markChapterCompleted("book", "first")
        val controller = RecordingPlayer(59_999L, isPlaying = false, playbackState = Player.STATE_READY)

        markChapterUnfinishedWithoutStartingPlayback(store, controller.player, "book", "first")

        assertEquals(listOf("pause", "seek:0"), controller.actions)
        assertEquals(0L, controller.positionMs)
    }

    @Test
    fun markingUnfinishedDoesNotInterruptPlayingOrPartiallyPausedChapter() {
        for (isPlaying in listOf(true, false)) {
            store.savePosition("book", "first", 0, 15_000L, 60_000L)
            store.markChapterCompleted("book", "first")
            val controller = RecordingPlayer(15_000L, isPlaying, Player.STATE_READY)

            markChapterUnfinishedWithoutStartingPlayback(store, controller.player, "book", "first")

            assertTrue(controller.actions.isEmpty())
            assertEquals(15_000L, controller.positionMs)
            assertFalse(store.savedChapterProgress("book", "first").isCompleted)
        }
    }

    @Test
    fun markingDifferentChapterUnfinishedDoesNotMoveCurrentEndedController() {
        store.savePosition("book", "second", 1, 60_000L, 60_000L)
        store.markChapterCompleted("book", "second")
        val controller = RecordingPlayer(60_000L, isPlaying = false, playbackState = Player.STATE_ENDED)

        markChapterUnfinishedWithoutStartingPlayback(store, controller.player, "book", "second")

        assertTrue(controller.actions.isEmpty())
        assertEquals(60_000L, controller.positionMs)
        assertFalse(store.savedChapterProgress("book", "second").isCompleted)
    }

    private inner class RecordingPlayer(
        var positionMs: Long,
        isPlaying: Boolean,
        playbackState: Int,
    ) {
        val actions = mutableListOf<String>()
        var playWhenReady = true
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "isPlaying" -> isPlaying
                "getPlaybackState" -> playbackState
                "getCurrentMediaItem" -> chapter("first")
                "getDuration" -> 60_000L
                "getCurrentPosition" -> positionMs
                "pause" -> {
                    actions += "pause"
                    playWhenReady = false
                    null
                }
                "seekTo" -> {
                    val target = arguments!!.single() as Long
                    actions += "seek:$target"
                    store.savePosition("book", "first", 0, positionMs, 60_000L)
                    positionMs = target
                    store.savePosition("book", "first", 0, target, 60_000L)
                    null
                }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    private fun chapter(id: String, durationMs: Long = 0L): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId("book", id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(Bundle().apply { putLong(MEDIA_METADATA_CAST_DURATION_MS, durationMs) })
                    .build(),
            )
            .build()

    private fun position(
        chapterId: String,
        index: Int,
        positionMs: Long,
        durationMs: Long = 0L,
        adGroupIndex: Int = C.INDEX_UNSET,
    ): Player.PositionInfo = Player.PositionInfo(
        chapterId,
        index,
        chapter(chapterId, durationMs),
        chapterId,
        index,
        positionMs,
        positionMs,
        adGroupIndex,
        if (adGroupIndex == C.INDEX_UNSET) C.INDEX_UNSET else 0,
    )
}
