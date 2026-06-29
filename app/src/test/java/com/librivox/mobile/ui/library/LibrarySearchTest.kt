package com.librivox.mobile.ui.library

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private val book = AudioBook(
        id = "library-search-book",
        title = "Quiet Harbor",
        author = "Mara Stone",
        description = "A meditation on winter gardens and hidden letters.",
        narrators = listOf("Nina Vale"),
        chapters = listOf(
            AudioBookChapter(
                id = "chapter-1",
                title = "The Lantern Room",
                number = 1,
                reader = "Alan Drake",
                director = "Mina Reed",
            ),
        ),
    )

    @Test
    fun matchesLibraryMetadataAndChapterDetails() {
        assertTrue(book.matchesLibrarySearch("Quiet Harbor"))
        assertTrue(book.matchesLibrarySearch("Mara Stone"))
        assertTrue(book.matchesLibrarySearch("Lantern Room"))
        assertTrue(book.matchesLibrarySearch("Alan Drake"))
        assertTrue(book.matchesLibrarySearch("Mina Reed"))
        assertTrue(book.matchesLibrarySearch("Nina Vale"))
        assertTrue(book.matchesLibrarySearch("hidden letters"))
    }

    @Test
    fun doesNotMatchUnrelatedQuery() {
        assertFalse(book.matchesLibrarySearch("spaceship"))
    }
}
