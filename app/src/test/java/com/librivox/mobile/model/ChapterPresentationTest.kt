package com.librivox.mobile.model

import org.junit.Assert.*
import org.junit.Test

class ChapterPresentationTest {
    private val chapter = AudioBookChapter(
        id = "lalka-1", number = 1,
        title = "Tom II, Rozdział I — Pamiętnik starego subiekta",
        reader = " Albert Osik ", durationSeconds = 600,
        localFileName = "saved.mp3", downloadState = DownloadState.Downloaded,
    )
    private val book = AudioBook(
        id = "lalka", title = "Lalka", author = " Bolesław Prus ",
        source = BookSource.WolneLektury, chapters = listOf(chapter),
    )

    @Test fun wolneHeadingSeparatesContextWithoutMutatingStoredChapter() {
        val before = book.copy()
        assertEquals(ChapterPresentation("Pamiętnik starego subiekta", "Tom II, Rozdział I", "Bolesław Prus", "Albert Osik"), book.chapterPresentation(chapter))
        assertEquals(before, book)
        assertEquals("saved.mp3", book.chapters.single().localFileName)
    }

    @Test fun ordinaryDashAndUnrecognizedFormatsArePreserved() {
        listOf("Arrival — Departure", "Tom II — Pamiętnik", "Rozdział I — Pamiętnik", "Tom II, Rozdział I - Pamiętnik").forEach { title ->
            val result = book.chapterPresentation(chapter.copy(title = title))
            assertEquals(title, result.title)
            assertNull(result.context)
        }
        assertEquals(chapter.title, book.copy(source = BookSource.LibriVox).chapterPresentation(chapter).title)
    }

    @Test fun exactlyOneDistinctNarratorIsASafeFallback() {
        val noReader = chapter.copy(reader = " ")
        assertEquals("Albert Osik", book.copy(narrators = listOf("Albert Osik", " Albert Osik ", "")).chapterPresentation(noReader).reader)
        assertNull(book.copy(narrators = listOf("Albert Osik", "Other Reader")).chapterPresentation(noReader).reader)
        assertNull(book.chapterPresentation(noReader).reader)
        assertEquals("Albert Osik", book.copy(narrators = listOf("Other Reader")).chapterPresentation(chapter).reader)
    }

    @Test fun missingAndLongAttributionUsesOnlyAvailableNames() {
        val missing = book.copy(author = " ", authorTags = emptyList()).chapterPresentation(chapter.copy(reader = null))
        assertNull(missing.author)
        assertNull(missing.reader)
        val longName = "A very long author name that must remain intact across multiple lines"
        assertEquals(longName, book.copy(author = longName).chapterPresentation(chapter).author)
        assertEquals("One, Two", book.copy(author = "", authorTags = listOf(CatalogTag("One"), CatalogTag(" Two "), CatalogTag("One"))).chapterPresentation(chapter).author)
    }
}
