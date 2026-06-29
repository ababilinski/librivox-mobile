package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookMergerTest {

    @Test
    fun matchKey_normalizesCasePunctuationAndLeadingThe() {
        val a = book(title = "The Jungle", author = "Upton Sinclair")
        val b = book(title = "Jungle", author = "Sinclair")
        assertEquals(BookMerger.matchKey(a), BookMerger.matchKey(b))
    }

    @Test
    fun matchKey_handlesAccentsAndMultipleAuthors() {
        val a = book(title = "Les Misérables", author = "Victor Hugo")
        val b = book(title = "Les Miserables", author = "Hugo, Victor")
        assertEquals(BookMerger.matchKey(a), BookMerger.matchKey(b))
    }

    @Test
    fun matchKey_ignoresSubtitlePunctuation() {
        val a = book(title = "Frankenstein; Or, The Modern Prometheus", author = "Mary Wollstonecraft Shelley")
        val b = book(title = "Frankenstein", author = "Mary Shelley")
        assertEquals(BookMerger.matchKey(a), BookMerger.matchKey(b))
    }

    @Test
    fun merge_prefersLit2GoPrimaryAndFillsMissingMetadataFromLibriVox() {
        val librivox = book(
            id = "253",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            description = "A LibriVox description.",
            source = BookSource.LibriVox,
            coverImageUrl = "https://librivox.example/cover.jpg",
            language = "English",
            genres = listOf("Romance"),
            librivoxUrl = "https://librivox.org/pride/",
        )
        val lit2goPrimary = book(
            id = "lit2go-100",
            title = "Pride and Prejudice",
            author = "Austen",
            description = "",
            source = BookSource.Lit2Go,
            coverImageUrl = null,
            language = null,
            genres = emptyList(),
            chapters = listOf(playableChapter()),
            lit2goUrl = "https://etc.usf.edu/lit2go/100/pride-and-prejudice/",
        )

        val merged = BookMerger.merge(listOf(librivox), listOf(lit2goPrimary))

        assertEquals("Expected exactly one merged book", 1, merged.size)
        val result = merged.single()
        assertEquals("Lit2Go id stays primary", "lit2go-100", result.id)
        assertEquals(BookSource.Lit2Go, result.source)
        assertEquals("Description filled from LibriVox", "A LibriVox description.", result.description)
        assertEquals("Cover filled from LibriVox", "https://librivox.example/cover.jpg", result.coverImageUrl)
        assertEquals("English", result.language)
        assertTrue("Genres filled from LibriVox", result.genres.contains("Romance"))
        assertEquals("https://librivox.org/pride/", result.librivoxUrl)
        assertEquals("https://etc.usf.edu/lit2go/100/pride-and-prejudice/", result.lit2goUrl)
        assertEquals(1, result.chapters.size)
    }

    @Test
    fun merge_fillsMissingPrimaryChaptersFromLibriVox() {
        val librivox = book(
            id = "253",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.LibriVox,
            chapters = listOf(
                AudioBookChapter(
                    id = "124135",
                    title = "Chapters 1-3",
                    number = 1,
                    listenUrl = "https://www.archive.org/download/pride_and_prejudice_librivox/prideandprejudice_01-03_austen_64kb.mp3",
                ),
            ),
        )
        val lit2goPrimary = book(
            id = "lit2go-100",
            title = "Pride and Prejudice",
            author = "Austen",
            source = BookSource.Lit2Go,
            chapters = emptyList(),
        )

        val merged = BookMerger.merge(listOf(librivox), listOf(lit2goPrimary))

        val result = merged.single()
        assertEquals("lit2go-100", result.id)
        assertEquals(1, result.chapters.size)
        assertEquals("124135", result.chapters.single().id)
        assertEquals(
            "https://www.archive.org/download/pride_and_prejudice_librivox/prideandprejudice_01-03_austen_64kb.mp3",
            result.chapters.single().listenUrl,
        )
    }

    @Test
    fun merge_keepsUnmatchedLibriVoxAndLit2GoBooksSeparate() {
        val librivoxOnly = book(id = "1", title = "Moby Dick", author = "Herman Melville")
        val lit2goOnly = book(id = "lit2go-2", title = "Walden", author = "Henry Thoreau", source = BookSource.Lit2Go)

        val merged = BookMerger.merge(listOf(librivoxOnly), listOf(lit2goOnly))

        assertEquals(2, merged.size)
        assertTrue("LibriVox-only present", merged.any { it.id == "1" })
        assertTrue("Lit2Go-only present", merged.any { it.id == "lit2go-2" })
    }

    @Test
    fun merge_emptyInputsReturnTheOtherList() {
        val librivox = book(id = "9", title = "Solo", author = "Author")
        assertEquals(listOf(librivox), BookMerger.merge(listOf(librivox), emptyList()))
        assertEquals(listOf(librivox), BookMerger.merge(emptyList(), listOf(librivox)))
    }

    @Test
    fun merge_doesNotOverrideExistingLit2GoFields() {
        val librivox = book(
            title = "Walden",
            author = "Thoreau",
            description = "LibriVox description",
            coverImageUrl = "https://librivox.example/walden.jpg",
        )
        val lit2go = book(
            id = "lit2go-77",
            title = "Walden",
            author = "Thoreau",
            description = "Lit2Go description",
            coverImageUrl = "https://lit2go.example/walden.png",
            source = BookSource.Lit2Go,
        )

        val merged = BookMerger.merge(listOf(librivox), listOf(lit2go)).single()

        assertEquals("Lit2Go description preserved", "Lit2Go description", merged.description)
        assertEquals(
            "Lit2Go cover preserved",
            "https://lit2go.example/walden.png",
            merged.coverImageUrl,
        )
    }

    @Test
    fun merge_filledLibriVoxUrlEnablesCrossReference() {
        val librivox = book(title = "Persuasion", author = "Austen", librivoxUrl = "https://librivox.org/persuasion/")
        val lit2go = book(
            id = "lit2go-44",
            title = "Persuasion",
            author = "Austen",
            source = BookSource.Lit2Go,
        )

        val merged = BookMerger.merge(listOf(librivox), listOf(lit2go)).single()

        assertEquals("https://librivox.org/persuasion/", merged.librivoxUrl)
        assertNull("No lit2goUrl on this fixture", merged.lit2goUrl)
    }

    @Test
    fun merge_prefersLit2GoThenLibriVoxThenGutendexForDuplicates() {
        val gutendex = book(
            id = "gutendex-1342",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            description = "Gutendex summary.",
            source = BookSource.Gutendex,
            coverImageUrl = "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
            gutenbergUrl = "https://www.gutenberg.org/ebooks/1342",
        )
        val librivox = book(
            id = "253",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.LibriVox,
            librivoxUrl = "https://librivox.org/pride-and-prejudice-by-jane-austen/",
        )
        val lit2go = book(
            id = "lit2go-100",
            title = "Pride and Prejudice",
            author = "Austen",
            source = BookSource.Lit2Go,
            chapters = listOf(playableChapter()),
            lit2goUrl = "https://etc.usf.edu/lit2go/100/pride-and-prejudice/",
        )

        val merged = BookMerger.merge(
            librivox = listOf(librivox),
            lit2go = listOf(lit2go),
            wolneLektury = emptyList(),
            gutendex = listOf(gutendex),
        )

        assertEquals(1, merged.size)
        val result = merged.single()
        assertEquals("lit2go-100", result.id)
        assertEquals(BookSource.Lit2Go, result.source)
        assertEquals("Gutendex summary.", result.description)
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg", result.coverImageUrl)
        assertEquals("https://librivox.org/pride-and-prejudice-by-jane-austen/", result.librivoxUrl)
        assertEquals("https://etc.usf.edu/lit2go/100/pride-and-prejudice/", result.lit2goUrl)
        assertEquals("https://www.gutenberg.org/ebooks/1342", result.gutenbergUrl)
        assertEquals(1, result.chapters.size)
    }

    @Test
    fun merge_prefersLibriVoxOverGutendexWhenLit2GoIsMissing() {
        val gutendex = book(
            id = "gutendex-84",
            title = "Frankenstein; Or, The Modern Prometheus",
            author = "Mary Wollstonecraft Shelley",
            source = BookSource.Gutendex,
            gutenbergUrl = "https://www.gutenberg.org/ebooks/84",
        )
        val librivox = book(
            id = "52",
            title = "Frankenstein",
            author = "Mary Shelley",
            source = BookSource.LibriVox,
            chapters = listOf(playableChapter()),
        )

        val merged = BookMerger.merge(
            librivox = listOf(librivox),
            lit2go = emptyList(),
            wolneLektury = emptyList(),
            gutendex = listOf(gutendex),
        )

        val result = merged.single()
        assertEquals("52", result.id)
        assertEquals(BookSource.LibriVox, result.source)
        assertEquals("https://www.gutenberg.org/ebooks/84", result.gutenbergUrl)
        assertEquals(1, result.chapters.size)
    }

    private fun book(
        id: String = "id-${++bookCounter}",
        title: String = "Title",
        author: String = "Author Name",
        description: String = "",
        source: BookSource = BookSource.LibriVox,
        coverImageUrl: String? = null,
        language: String? = null,
        genres: List<String> = emptyList(),
        librivoxUrl: String? = null,
        lit2goUrl: String? = null,
        gutenbergUrl: String? = null,
        chapters: List<AudioBookChapter> = emptyList(),
    ): AudioBook = AudioBook(
        id = id,
        title = title,
        author = author,
        description = description,
        source = source,
        coverImageUrl = coverImageUrl,
        language = language,
        genres = genres,
        librivoxUrl = librivoxUrl,
        lit2goUrl = lit2goUrl,
        gutenbergUrl = gutenbergUrl,
        chapters = chapters,
    )

    private fun playableChapter(): AudioBookChapter = AudioBookChapter(
        id = "ch-1",
        title = "Chapter 1",
        number = 1,
        listenUrl = "https://etc.usf.edu/audio/1.mp3",
    )

    companion object {
        private var bookCounter = 0
    }
}
