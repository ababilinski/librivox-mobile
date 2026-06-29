package com.librivox.mobile.ui.player.bookmarks

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkPresentationTest {
    @Test
    fun currentChapterBookmarks_comeFirstForPlayingBook() {
        val sections = buildBookmarkPanelSections(
            book = testBook(),
            bookmarks = listOf(
                bookmark("c3-later", "c3", 30_000),
                bookmark("c2-later", "c2", 20_000),
                bookmark("c1", "c1", 10_000),
                bookmark("c2-earlier", "c2", 5_000),
            ),
            activeBookId = "book",
            activeChapterId = "c2",
        )

        assertEquals("c2", sections.currentChapter?.chapterId)
        assertEquals(listOf(5_000L, 20_000L), sections.currentChapter?.bookmarks?.map { it.positionMs })
        assertEquals(listOf("c1", "c3"), sections.remainingChapters.map { it.chapterId })
        assertTrue(sections.showDivider)
    }

    @Test
    fun dividerOnlyAppearsWhenCurrentAndRemainingBookmarksExist() {
        val currentOnly = buildBookmarkPanelSections(
            book = testBook(),
            bookmarks = listOf(bookmark("current", "c2", 5_000)),
            activeBookId = "book",
            activeChapterId = "c2",
        )

        val remainingOnly = buildBookmarkPanelSections(
            book = testBook(),
            bookmarks = listOf(bookmark("other", "c1", 5_000)),
            activeBookId = "book",
            activeChapterId = "c2",
        )

        assertFalse(currentOnly.showDivider)
        assertFalse(remainingOnly.showDivider)
    }

    @Test
    fun nonPlayingBookGroupsByChapterOrderThenTimestamp() {
        val sections = buildBookmarkPanelSections(
            book = testBook(),
            bookmarks = listOf(
                bookmark("c3", "c3", 30_000),
                bookmark("c1-later", "c1", 20_000),
                bookmark("c2", "c2", 15_000),
                bookmark("c1-earlier", "c1", 5_000),
            ),
            activeBookId = "other-book",
            activeChapterId = "c2",
        )

        assertEquals(null, sections.currentChapter)
        assertEquals(listOf("c1", "c2", "c3"), sections.remainingChapters.map { it.chapterId })
        assertEquals(listOf(5_000L, 20_000L), sections.remainingChapters.first().bookmarks.map { it.positionMs })
    }

    private fun testBook() = AudioBook(
        id = "book",
        title = "Test Book",
        author = "Test Author",
        chapters = listOf(
            AudioBookChapter(id = "c1", title = "One", number = 1),
            AudioBookChapter(id = "c2", title = "Two", number = 2),
            AudioBookChapter(id = "c3", title = "Three", number = 3),
        ),
    )

    private fun bookmark(id: String, chapterId: String, positionMs: Long) = Bookmark(
        id = id,
        bookId = "book",
        chapterId = chapterId,
        positionMs = positionMs,
        createdAtMillis = positionMs,
    )
}
