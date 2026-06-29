package com.librivox.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookLinksTest {

    @Test
    fun externalAudiobookLinkTarget_mapsWolneLekturyBookUrlToCatalogId() {
        val target = externalAudiobookLinkTarget("https://wolnelektury.pl/katalog/lektura/antek/")

        assertEquals("wolnelektury-antek", target?.bookId)
        assertEquals("antek", target?.chapterSlug)
    }

    @Test
    fun externalAudiobookLinkTarget_mapsLit2GoUrlToCatalogId() {
        val target = externalAudiobookLinkTarget("https://etc.usf.edu/lit2go/100/pride-and-prejudice/")

        assertEquals("lit2go-100__pride-and-prejudice", target?.bookId)
    }

    @Test
    fun externalAudiobookLinkTarget_mapsGutenbergUrlToGutendexId() {
        val target = externalAudiobookLinkTarget("https://www.gutenberg.org/ebooks/1342")

        assertEquals("gutendex-1342", target?.bookId)
    }

    @Test
    fun externalAudiobookLinkTarget_unwrapsAppLinkToBookAndChapter() {
        val book = AudioBook(
            id = "lit2go-100__pride-and-prejudice",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.Lit2Go,
            lit2goUrl = "https://etc.usf.edu/lit2go/100/pride-and-prejudice/",
        )
        val chapter = AudioBookChapter(
            id = "lit2go-100__pride-and-prejudice-1",
            title = "Chapter 1",
            number = 1,
        )

        val target = externalAudiobookLinkTarget(chapter.appLinkUrl(book).orEmpty())

        assertEquals(book.id, target?.bookId)
        assertEquals(chapter.id, target?.chapterId)
    }

    @Test
    fun externalAudiobookLinkTarget_preparesLibriVoxSearchFromSlug() {
        val target = externalAudiobookLinkTarget("https://librivox.org/pride-and-prejudice-by-jane-austen/")

        assertEquals("https://librivox.org/pride-and-prejudice-by-jane-austen", target?.lookupUrl)
        assertEquals("pride and prejudice", target?.searchQuery)
    }

    @Test
    fun publicWebUrl_prefersTheCurrentSource() {
        val book = AudioBook(
            id = "lit2go-100__pride-and-prejudice",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.Lit2Go,
            librivoxUrl = "https://librivox.org/pride-and-prejudice-by-jane-austen/",
            lit2goUrl = "https://etc.usf.edu/lit2go/100/pride-and-prejudice/",
            gutenbergUrl = "https://www.gutenberg.org/ebooks/1342",
        )

        assertEquals("https://etc.usf.edu/lit2go/100/pride-and-prejudice/", book.publicWebUrl())
    }

    @Test
    fun shareText_usesAppLinkWhenSelected() {
        val book = AudioBook(
            id = "lit2go-100__pride-and-prejudice",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.Lit2Go,
            lit2goUrl = "https://etc.usf.edu/lit2go/100/pride-and-prejudice/",
        )

        val text = book.shareText(ShareLinkMode.AppLink)

        assertTrue(text.contains("Pride and Prejudice by Jane Austen"))
        assertTrue(text.contains("intent://open?"))
        assertTrue(text.contains("bookId=lit2go-100__pride-and-prejudice"))
        assertTrue(text.contains("source=https%3A%2F%2Fetc.usf.edu%2Flit2go%2F100%2Fpride-and-prejudice%2F"))
        assertTrue(text.contains("S.browser_fallback_url=https%3A%2F%2Fetc.usf.edu%2Flit2go%2F100%2Fpride-and-prejudice%2F"))
    }

    @Test
    fun chapterPublicWebUrl_derivesWolneLekturyChildPageWhenAvailable() {
        val book = AudioBook(
            id = "wolnelektury-baczynski-1942-1943-1944",
            title = "1942, 1943, 1944",
            author = "Krzysztof Kamil Baczyński",
            source = BookSource.WolneLektury,
            wolneLekturyUrl = "https://wolnelektury.pl/katalog/lektura/baczynski-1942-1943-1944/",
            chapters = listOf(
                AudioBookChapter(
                    id = "baczynski-snieg-jak-wieko-zelazne-1",
                    title = "Śnieg jak wieko żelazne...",
                    number = 1,
                ),
            ),
        )

        val chapter = book.chapters.single()

        assertEquals(
            "https://wolnelektury.pl/katalog/lektura/baczynski-snieg-jak-wieko-zelazne/",
            chapter.publicWebUrl(book),
        )
        assertEquals(chapter.id, book.chapterIdForSourceSlug("baczynski-snieg-jak-wieko-zelazne"))
    }

    @Test
    fun matchesPublicWebUrl_ignoresWwwAndTrailingSlash() {
        val book = AudioBook(
            id = "1342",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            gutenbergUrl = "https://www.gutenberg.org/ebooks/1342/",
        )

        assertTrue(book.matchesPublicWebUrl("https://gutenberg.org/ebooks/1342"))
    }
}
