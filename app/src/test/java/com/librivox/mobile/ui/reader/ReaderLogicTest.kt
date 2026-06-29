package com.librivox.mobile.ui.reader

import com.librivox.mobile.playback.ReaderHighlightMode
import com.librivox.mobile.playback.ReaderSettings
import com.librivox.mobile.readalong.ReadAlongSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLogicTest {
    @Test
    fun openScroll_prefersCurrentPlaybackSegmentOverInitialChapter() {
        val target = resolveReaderOpenScrollIndex(
            activeSegmentIndex = 12,
            initialChapterIndex = 3,
            isCurrentPlaybackBook = true,
            didUseInitialScroll = false,
        )

        assertEquals(12, target)
    }

    @Test
    fun openScroll_usesInitialChapterWhenPlaybackBookDoesNotMatch() {
        val target = resolveReaderOpenScrollIndex(
            activeSegmentIndex = 12,
            initialChapterIndex = 3,
            isCurrentPlaybackBook = false,
            didUseInitialScroll = false,
        )

        assertEquals(3, target)
    }

    @Test
    fun followScroll_requestsScrollWhenScrubMovesHighlightOutsideView() {
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = 20,
            activeSegmentId = "segment-20",
            followPlayback = true,
            selectedSegmentId = null,
            deferUntilActiveSegmentChangesFrom = null,
            visibleRange = ReaderVisibleRange(firstVisibleIndex = 2, lastVisibleIndex = 8),
        )

        assertEquals(20, target)
    }

    @Test
    fun lazyListIndex_accountsForReaderHeader() {
        assertEquals(
            6,
            readerLazyListIndex(
                readerItemIndex = 5,
                leadingItemCount = 1,
            ),
        )
    }

    @Test
    fun pageNumberUsesLastStartedPageAtOrBeforeVisibleListIndex() {
        val pageStarts = listOf(1, 12, 24)

        assertEquals(1, readerPageNumberForListIndex(listIndex = 0, pageStartIndexes = pageStarts))
        assertEquals(1, readerPageNumberForListIndex(listIndex = 8, pageStartIndexes = pageStarts))
        assertEquals(2, readerPageNumberForListIndex(listIndex = 12, pageStartIndexes = pageStarts))
        assertEquals(3, readerPageNumberForListIndex(listIndex = 40, pageStartIndexes = pageStarts))
    }

    @Test
    fun scrollProgressUsesMeasuredItemHeights() {
        val progress = readerScrollProgressForListPosition(
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 450,
            estimatedItemSizesByIndex = mapOf(
                0 to 100,
                1 to 100,
                2 to 100,
                3 to 100,
            ),
            measuredItemSizesByIndex = mapOf(
                1 to 900,
            ),
            viewportHeight = 500,
            totalItemCount = 4,
            canScrollBackward = true,
            canScrollForward = true,
        )

        assertEquals(550f / 700f, progress, 0.0001f)
    }

    @Test
    fun scrollProgressEstimatesUnmeasuredItemHeights() {
        val progress = readerScrollProgressForListPosition(
            firstVisibleItemIndex = 5,
            firstVisibleItemScrollOffset = 50,
            estimatedItemSizesByIndex = (0 until 20).associateWith { 100 },
            measuredItemSizesByIndex = emptyMap(),
            viewportHeight = 500,
            totalItemCount = 20,
            canScrollBackward = true,
            canScrollForward = true,
        )

        assertEquals(550f / 1500f, progress, 0.0001f)
    }

    @Test
    fun scrollProgressUsesExactEdges() {
        assertEquals(
            0f,
            readerScrollProgressForListPosition(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 80,
                estimatedItemSizesByIndex = mapOf(0 to 100),
                measuredItemSizesByIndex = mapOf(0 to 100),
                viewportHeight = 500,
                totalItemCount = 20,
                canScrollBackward = false,
                canScrollForward = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            readerScrollProgressForListPosition(
                firstVisibleItemIndex = 15,
                firstVisibleItemScrollOffset = 0,
                estimatedItemSizesByIndex = mapOf(15 to 100),
                measuredItemSizesByIndex = mapOf(15 to 100),
                viewportHeight = 500,
                totalItemCount = 20,
                canScrollBackward = true,
                canScrollForward = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun scrollTargetUsesMeasuredItemHeights() {
        val target = readerListScrollTargetForProgress(
            progress = 550f / 700f,
            estimatedItemSizesByIndex = mapOf(
                0 to 100,
                1 to 100,
                2 to 100,
                3 to 100,
            ),
            measuredItemSizesByIndex = mapOf(
                1 to 900,
            ),
            viewportHeight = 500,
            totalItemCount = 4,
        )

        assertEquals(1, target.index)
        assertEquals(450, target.scrollOffset)
    }

    @Test
    fun scrollTargetSupportsSmoothPositionsBetweenPages() {
        val target = readerListScrollTargetForProgress(
            progress = 0.5f,
            estimatedItemSizesByIndex = (0 until 20).associateWith { 100 },
            measuredItemSizesByIndex = emptyMap(),
            viewportHeight = 500,
            totalItemCount = 20,
        )

        assertEquals(7, target.index)
        assertEquals(50, target.scrollOffset)
    }

    @Test
    fun scrollTargetUsesExactEdges() {
        assertEquals(
            ReaderListScrollTarget(index = 0, scrollOffset = 0),
            readerListScrollTargetForProgress(
                progress = 0f,
                estimatedItemSizesByIndex = (0 until 20).associateWith { 100 },
                measuredItemSizesByIndex = emptyMap(),
                viewportHeight = 500,
                totalItemCount = 20,
            ),
        )
        assertEquals(
            ReaderListScrollTarget(index = 15, scrollOffset = 0),
            readerListScrollTargetForProgress(
                progress = 1f,
                estimatedItemSizesByIndex = (0 until 20).associateWith { 100 },
                measuredItemSizesByIndex = emptyMap(),
                viewportHeight = 500,
                totalItemCount = 20,
            ),
        )
    }

    @Test
    fun activePosition_prefersScrubPreviewWhileDragging() {
        assertEquals(
            42_000L,
            readerActivePositionMs(
                playerPositionMs = 8_000L,
                scrubPreviewPositionMs = 42_000L,
            ),
        )
        assertEquals(
            8_000L,
            readerActivePositionMs(
                playerPositionMs = 8_000L,
                scrubPreviewPositionMs = null,
            ),
        )
    }

    @Test
    fun followScroll_skipsScrollWhenActiveItemIsComfortablyVisible() {
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = 5,
            activeSegmentId = "segment-5",
            followPlayback = true,
            selectedSegmentId = null,
            deferUntilActiveSegmentChangesFrom = null,
            visibleRange = ReaderVisibleRange(firstVisibleIndex = 2, lastVisibleIndex = 8),
        )

        assertNull(target)
    }

    @Test
    fun followScroll_requestsScrollWhenActiveItemIsAtVisibleEdge() {
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = 8,
            activeSegmentId = "segment-8",
            followPlayback = true,
            selectedSegmentId = null,
            deferUntilActiveSegmentChangesFrom = null,
            visibleRange = ReaderVisibleRange(firstVisibleIndex = 2, lastVisibleIndex = 8),
        )

        assertEquals(8, target)
    }

    @Test
    fun followScroll_requestsScrollWhenActiveItemIsInLowerChromeZone() {
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = 7,
            activeSegmentId = "segment-7",
            followPlayback = true,
            selectedSegmentId = null,
            deferUntilActiveSegmentChangesFrom = null,
            visibleRange = ReaderVisibleRange(firstVisibleIndex = 2, lastVisibleIndex = 8),
        )

        assertEquals(7, target)
    }

    @Test
    fun followScroll_isSuppressedWhileTextIsSelected() {
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = 20,
            activeSegmentId = "segment-20",
            followPlayback = true,
            selectedSegmentId = "segment-4",
            deferUntilActiveSegmentChangesFrom = null,
            visibleRange = ReaderVisibleRange(firstVisibleIndex = 2, lastVisibleIndex = 8),
        )

        assertNull(target)
    }

    @Test
    fun clearingSelectionDefersFollowUntilActiveSegmentChanges() {
        assertFalse(
            shouldAllowReaderFollowScroll(
                followPlayback = true,
                activeSegmentId = "segment-4",
                selectedSegmentId = null,
                deferUntilActiveSegmentChangesFrom = "segment-4",
            ),
        )
        assertTrue(
            shouldAllowReaderFollowScroll(
                followPlayback = true,
                activeSegmentId = "segment-5",
                selectedSegmentId = null,
                deferUntilActiveSegmentChangesFrom = "segment-4",
            ),
        )
    }

    @Test
    fun selectedTextBookmarkUsesSelectedTextAndTimedSegmentPosition() {
        val segment = ReadAlongSegment(
            id = "segment-1",
            chapterId = "chapter-1",
            text = "Antek urodził się we wsi nad Wisłą.",
            textRef = "chapter.xhtml#s1",
            audioRef = "audio.mp3",
            clipBeginMs = 12_345L,
            clipEndMs = 15_000L,
        )

        val selection = selectionForSegment(segment, 0, 5)
        val target = bookmarkTargetForSelectedText("wolnelektury-antek", selection)

        assertEquals("wolnelektury-antek", target?.bookId)
        assertEquals("chapter-1", target?.chapterId)
        assertEquals(12_345L, target?.positionMs)
        assertEquals("\"Antek\" —", target?.note)
    }

    @Test
    fun currentAudioBookmarkRequiresSamePlayingBook() {
        assertNull(
            bookmarkTargetForCurrentAudio(
                readerBookId = "wolnelektury-antek",
                playerBookId = "other-book",
                playerChapterId = "chapter-1",
                playerPositionMs = 99L,
                isPlaying = true,
            ),
        )
        assertNull(
            bookmarkTargetForCurrentAudio(
                readerBookId = "wolnelektury-antek",
                playerBookId = "wolnelektury-antek",
                playerChapterId = "chapter-1",
                playerPositionMs = 99L,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun sourceSegmentModeUsesActiveBoxHighlight() {
        val settings = ReaderSettings(highlightMode = ReaderHighlightMode.SourceSegment)

        assertEquals(
            ReaderSegmentHighlightRole.Current,
            readerHighlightRoleForSegment(
                segmentId = "segment-2",
                activeSegmentId = "segment-2",
                settings = settings,
            ),
        )
    }

    @Test
    fun breakIteratorModeSuppressesParagraphBoxHighlight() {
        val settings = ReaderSettings()

        assertEquals(
            ReaderSegmentHighlightRole.None,
            readerHighlightRoleForSegment(
                segmentId = "segment-2",
                activeSegmentId = "segment-2",
                settings = settings,
            ),
        )
    }

    @Test
    fun proportionalSplitCreatesSentenceTimedRangesByCharacterWeight() {
        val segment = ReadAlongSegment(
            id = "segment-1",
            chapterId = "chapter-1",
            text = "Ala. Bbbb.",
            textRef = "chapter.xhtml#s1",
            audioRef = "audio.mp3",
            clipBeginMs = 1_000L,
            clipEndMs = 10_000L,
        )

        val ranges = proportionalSentenceRanges(segment)

        assertEquals(2, ranges.size)
        assertEquals(
            ReaderTimedTextRange(
                textRange = ReaderTextRange(0, 4),
                clipBeginMs = 1_000L,
                clipEndMs = 5_000L,
            ),
            ranges[0],
        )
        assertEquals(
            ReaderTimedTextRange(
                textRange = ReaderTextRange(5, 10),
                clipBeginMs = 5_000L,
                clipEndMs = 10_000L,
            ),
            ranges[1],
        )
    }

    @Test
    fun proportionalSentenceHighlightPicksSentenceForPlaybackPosition() {
        val segment = ReadAlongSegment(
            id = "segment-1",
            chapterId = "chapter-1",
            text = "Ala. Bbbb.",
            textRef = "chapter.xhtml#s1",
            audioRef = "audio.mp3",
            clipBeginMs = 1_000L,
            clipEndMs = 10_000L,
        )

        val range = activeReaderTextRangeForSegment(
            segment = segment,
            activeSegmentId = "segment-1",
            activePositionMs = 7_500L,
            settings = ReaderSettings(highlightMode = ReaderHighlightMode.BreakIteratorProportional),
        )

        assertEquals(ReaderTextRange(5, 10), range)
    }

    @Test
    fun oldReaderHighlightPreferencesMapToCurrentTwoModeModel() {
        assertEquals(
            ReaderHighlightMode.BreakIteratorProportional,
            ReaderHighlightMode.fromPreference("sentence"),
        )
        assertEquals(
            ReaderHighlightMode.BreakIteratorProportional,
            ReaderHighlightMode.fromPreference("few_sentences"),
        )
        assertEquals(
            ReaderHighlightMode.SourceSegment,
            ReaderHighlightMode.fromPreference("segment"),
        )
    }
}
