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
    fun externalAudiobookLinkTarget_doesNotImportFromRetiredCatalog() {
        val target = externalAudiobookLinkTarget("https://www.gutenberg.org/ebooks/1342")

        assertEquals(null, target)
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
        assertTrue(text.contains("https://ababilinski.github.io/librivox-mobile/open/?"))
        assertTrue(text.contains("bookId=lit2go-100__pride-and-prejudice"))
        assertTrue(text.contains("source=https%3A%2F%2Fetc.usf.edu%2Flit2go%2F100%2Fpride-and-prejudice%2F"))
        assertTrue(!text.contains("intent://"))
    }

    @Test
    fun verifiedLinks_openBothRequestedBooks() {
        for (slug in listOf("antek", "katarynka")) {
            val target = externalAudiobookLinkTarget(
                "https://ababilinski.github.io/librivox-mobile/open/?bookId=wolnelektury-$slug",
            )
            assertEquals("wolnelektury-$slug", target?.bookId)
        }
    }

    @Test
    fun shareDefaultsToAppLinkAndPreservesExplicitSourcePreference() {
        val book = AudioBook(
            id = "wolnelektury-antek", title = "Antek", author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            wolneLekturyUrl = "https://wolnelektury.pl/katalog/lektura/antek/",
        )
        assertEquals(book.appLinkUrl(), book.shareUrl())
        assertEquals(ShareLinkMode.AppLink, ShareLinkMode.fromPreference(null))
        assertEquals(book.publicWebUrl(), book.shareUrl(ShareLinkMode.fromPreference("source_website")))
    }

    @Test
    fun publisherReaderHtmlLink_resolvesSameBook() {
        assertEquals("wolnelektury-katarynka", externalAudiobookLinkTarget(
            "https://wolnelektury.pl/katalog/lektura/katarynka.html",
        )?.bookId)
    }

    @Test
    fun links_rejectWrongHostPathSchemeAndRecursiveFallbacks() {
        for (url in listOf(
            "https://ababilinski.github.io.evil.test/librivox-mobile/open/?bookId=x",
            "https://ababilinski.github.io/unrelated/open/?bookId=x",
            "https://audiobook.example.com/open?bookId=x",
            "javascript://wolnelektury.pl/katalog/lektura/antek/",
            "https://ababilinski.github.io/librivox-mobile/open/?source=https%3A%2F%2Fababilinski.github.io%2Flibrivox-mobile%2Fopen%2F%3FbookId%3Dx",
            "https://wolnelektury.pl/katalog/lektura/..%2F..%2Fetc/",
        )) assertEquals(url, null, externalAudiobookLinkTarget(url))
    }

    @Test
    fun legacyAppLinks_stillResolve() {
        for (url in listOf(
            "audiobook://open?bookId=wolnelektury-antek&chapterId=antek-1",
            "intent://open?bookId=wolnelektury-antek&chapterId=antek-1#Intent;scheme=audiobook;package=com.librivox.mobile;end",
        )) {
            val target = externalAudiobookLinkTarget(url)
            assertEquals("wolnelektury-antek", target?.bookId)
            assertEquals("antek-1", target?.chapterId)
        }
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
