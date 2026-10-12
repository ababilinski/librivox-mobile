package com.librivox.mobile.ui.discover

import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.withSeedDefaults
import com.librivox.mobile.playback.BookSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverLocalSearchTest {

    @Test
    fun localSearchFindsSeededJungleByTitle() {
        val libraryState = LibraryState().withSeedDefaults()

        val results = discoverLocalSearchBooks(
            libraryState = libraryState,
            query = "jungle",
            field = CatalogSearchField.All,
            languages = listOf("English"),
        )

        val jungle = results.single()
        assertEquals(AudioBookLibrary.THE_JUNGLE_ID, jungle.id)
        assertEquals(BookSource.LibriVox, jungle.source)
        assertEquals(LibraryStatus.InLibrary, jungle.libraryStatus)
    }

    @Test
    fun localSearchHonorsLanguageFilter() {
        val results = discoverLocalSearchBooks(
            libraryState = LibraryState().withSeedDefaults(),
            query = "jungle",
            field = CatalogSearchField.Title,
            languages = listOf("Polish"),
        )

        assertEquals(0, results.size)
    }

    @Test
    fun localSearchHonorsSourceFilter() {
        val librivoxMatch = searchBook(id = "librivox", title = "River", author = "Someone")
        val gutendexMatch = searchBook(
            id = "gutendex-1",
            title = "River",
            author = "Someone",
            chapters = listOf(
                AudioBookChapter(
                    id = "gutendex-1-audio",
                    title = "Complete audiobook",
                    number = 1,
                    listenUrl = "https://www.gutenberg.org/files/1/mp3/1-01.mp3",
                ),
            ),
        ).copy(source = BookSource.Gutendex)

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(gutendexMatch, librivoxMatch)),
            query = "River",
            field = CatalogSearchField.Title,
            languages = listOf("English"),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        )

        assertEquals(listOf("librivox"), results.map { it.id })
    }

    @Test
    fun localSearchFindsCatalogCollectionsByChapterTitle() {
        val collection = AudioBook(
            id = "1986",
            title = "Short Story Collection Vol. 026",
            author = "Various",
            source = BookSource.LibriVox,
            language = "English",
            chapters = listOf(
                AudioBookChapter(
                    id = "2010",
                    title = "The Story of an Hour",
                    number = 10,
                    reader = "Alan Davis Drake",
                    listenUrl = "https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_storyofanhour_add.mp3",
                ),
            ),
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(collection)),
            query = "The Story of an Hour",
            field = CatalogSearchField.Chapter,
            languages = listOf("English"),
        )

        assertEquals(listOf("1986"), results.map { it.id })
    }

    @Test
    fun localSearchAllRanksTitleChapterAuthorThenReader() {
        val titleMatch = searchBook(id = "title", title = "River Stories", author = "Someone")
        val chapterMatch = searchBook(
            id = "chapter",
            title = "A Collection",
            author = "Someone",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "River Crossing",
                    number = 1,
                    reader = "Reader",
                    listenUrl = "https://example.com/chapter.mp3",
                ),
            ),
        )
        val authorMatch = searchBook(id = "author", title = "Other Book", author = "River Person")
        val readerMatch = searchBook(
            id = "reader",
            title = "Read Aloud",
            author = "Someone",
            chapters = listOf(
                AudioBookChapter(
                    id = "reader-1",
                    title = "Opening",
                    number = 1,
                    reader = "River Voice",
                    listenUrl = "https://example.com/reader.mp3",
                ),
            ),
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(readerMatch, authorMatch, chapterMatch, titleMatch)),
            query = "River",
            field = CatalogSearchField.All,
            languages = listOf("English"),
        )

        assertEquals(listOf("title", "chapter", "author", "reader"), results.map { it.id })
    }

    @Test
    fun localSearchAllIncludesMetadataAfterReaderMatches() {
        val readerMatch = searchBook(
            id = "reader",
            title = "Read Aloud",
            author = "Someone",
            chapters = listOf(
                AudioBookChapter(
                    id = "reader-1",
                    title = "Opening",
                    number = 1,
                    reader = "Romanticism Voice",
                    listenUrl = "https://example.com/reader.mp3",
                ),
            ),
        )
        val metadataMatch = searchBook(
            id = "metadata",
            title = "Ballads",
            author = "Someone",
        ).copy(
            genres = listOf("Romanticism"),
            literaryEpochs = listOf(CatalogTag("Romanticism", "romanticism")),
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(metadataMatch, readerMatch)),
            query = "Romanticism",
            field = CatalogSearchField.All,
            languages = listOf("English"),
        )

        assertEquals(listOf("reader", "metadata"), results.map { it.id })
    }

    @Test
    fun localSearchTitleFilterDoesNotIncludeChapterMatches() {
        val collection = searchBook(
            id = "chapter",
            title = "A Collection",
            author = "Someone",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "The Story of an Hour",
                    number = 1,
                    listenUrl = "https://example.com/chapter.mp3",
                ),
            ),
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(collection)),
            query = "The Story of an Hour",
            field = CatalogSearchField.Title,
            languages = listOf("English"),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun localSearchDoesNotShowStaleGutendexTextOnlyCatalogRows() {
        val staleGatsby = AudioBook(
            id = "gutendex-64317",
            title = "The Great Gatsby",
            author = "F. Scott Fitzgerald",
            source = BookSource.Gutendex,
            language = "English",
            gutenbergUrl = "https://www.gutenberg.org/ebooks/64317",
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(staleGatsby)),
            query = "The Great Gatsby",
            field = CatalogSearchField.Title,
            languages = listOf("English"),
            sourcePreference = BookSourcePreference.GutendexOnly,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun localSearchShowsPlayableGutendexAudioRows() {
        val playableGutenbergAudio = AudioBook(
            id = "gutendex-20687",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.Gutendex,
            language = "English",
            chapters = listOf(
                AudioBookChapter(
                    id = "gutendex-20687-audio",
                    title = "Complete audiobook",
                    number = 1,
                    listenUrl = "https://www.gutenberg.org/files/20687/mp3/20687-01.mp3",
                ),
            ),
        )

        val results = discoverLocalSearchBooks(
            libraryState = LibraryState(books = listOf(playableGutenbergAudio)),
            query = "Pride",
            field = CatalogSearchField.Title,
            languages = listOf("English"),
            sourcePreference = BookSourcePreference.GutendexOnly,
        )

        assertEquals(listOf("gutendex-20687"), results.map { it.id })
    }

    private fun searchBook(
        id: String,
        title: String,
        author: String,
        chapters: List<AudioBookChapter> = emptyList(),
    ): AudioBook =
        AudioBook(
            id = id,
            title = title,
            author = author,
            source = BookSource.LibriVox,
            language = "English",
            chapters = chapters,
        )
}
