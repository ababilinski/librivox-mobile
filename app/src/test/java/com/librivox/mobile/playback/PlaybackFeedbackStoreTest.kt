package com.librivox.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFeedbackStoreTest {
    @Test
    fun chapterScopeUsesChapterFeedbackKey() {
        assertEquals(
            "feedback::book-1::chapter-2",
            playbackFeedbackPreferenceKey(
                bookId = "book-1",
                chapterId = "chapter-2",
                scope = FeedbackControlScope.Chapter,
            ),
        )
    }

    @Test
    fun bookScopeUsesBookFeedbackKey() {
        assertEquals(
            "feedback_book::book-1",
            playbackFeedbackPreferenceKey(
                bookId = "book-1",
                chapterId = "chapter-2",
                scope = FeedbackControlScope.Book,
            ),
        )
    }

    @Test
    fun hiddenScopeDoesNotExposeAFeedbackKey() {
        assertNull(
            playbackFeedbackPreferenceKey(
                bookId = "book-1",
                chapterId = "chapter-2",
                scope = FeedbackControlScope.Hidden,
            ),
        )
    }

    @Test
    fun feedbackTargetsAreMutuallyExclusive() {
        assertEquals(
            PlaybackFeedback.Disliked,
            toggledPlaybackFeedback(
                current = PlaybackFeedback.Liked,
                target = PlaybackFeedback.Disliked,
            ),
        )
        assertEquals(
            PlaybackFeedback.Liked,
            toggledPlaybackFeedback(
                current = PlaybackFeedback.Disliked,
                target = PlaybackFeedback.Liked,
            ),
        )
    }

    @Test
    fun selectingExistingFeedbackClearsIt() {
        assertEquals(
            PlaybackFeedback.None,
            toggledPlaybackFeedback(
                current = PlaybackFeedback.Liked,
                target = PlaybackFeedback.Liked,
            ),
        )
        assertEquals(
            PlaybackFeedback.None,
            toggledPlaybackFeedback(
                current = PlaybackFeedback.Disliked,
                target = PlaybackFeedback.Disliked,
            ),
        )
    }
}
