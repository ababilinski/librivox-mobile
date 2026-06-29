package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBookLibrary
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibriVoxCatalogSourceTest {
    private val client = LibriVoxCatalogSource()

    @Test
    fun archiveCoverUrl_derivesFromArchiveDetailsUrl() {
        val url = LibriVoxCatalogSource.archiveCoverUrl("https://archive.org/details/pride_and_prejudice_librivox")
        assertEquals("https://archive.org/services/img/pride_and_prejudice_librivox", url)
    }

    @Test
    fun archiveCoverUrl_returnsNullWhenInputIsBlank() {
        assertNull(LibriVoxCatalogSource.archiveCoverUrl(null))
        assertNull(LibriVoxCatalogSource.archiveCoverUrl(""))
        assertNull(LibriVoxCatalogSource.archiveCoverUrl("https://example.com/no-identifier"))
    }

    @Test
    fun searchRequestValues_anchorsTitleQueriesBeforeFallback() {
        assertEquals(
            listOf("title" to "^Alice", "title" to "Alice"),
            LibriVoxCatalogSource.searchRequestValues("Alice", CatalogSearchField.Title),
        )
    }

    @Test
    fun searchRequestValues_supportsReaderQueries() {
        assertEquals(
            listOf("reader" to "Tom Weiss", "reader" to "^Tom Weiss"),
            LibriVoxCatalogSource.searchRequestValues("Tom Weiss", CatalogSearchField.Reader),
        )
    }

    @Test
    fun searchRequestValues_expandsCompactVolumeNotation() {
        val values = LibriVoxCatalogSource.searchRequestValues(
            "short story collection vol.026",
            CatalogSearchField.Title,
        )

        assertTrue(values.contains("title" to "^short story collection Vol. 026"))
        assertTrue(values.contains("title" to "short story collection Vol. 026"))
    }

    @Test
    fun searchRequestValues_expandsLeadingArticleTitleLookups() {
        val values = LibriVoxCatalogSource.searchRequestValues(
            "The Great Gatsby",
            CatalogSearchField.Title,
        )

        assertTrue(values.contains("title" to "^Great Gatsby"))
        assertTrue(values.contains("title" to "Great Gatsby"))
    }

    @Test
    fun titleSearch_filtersOutUnrelatedApiFallbackPages() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(prideAndPrejudiceJson().toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "chlopi",
            field = CatalogSearchField.Title,
            limit = 10,
            offset = 0,
            language = "English",
        )

        assertTrue(books.isEmpty())
    }

    @Test
    fun allSearch_usesArchiveTitleFallbackForMiddleWordTitleQueries() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = when {
                        url.host == "archive.org" -> {
                            assertEquals("collection:librivoxaudio AND title:gatsby", url.queryParameter("q"))
                            greatGatsbyArchiveSearchJson()
                        }
                        url.queryParameter("title") == "^Great Gatsby" -> greatGatsbyJson()
                        else -> """{"error":"Audiobooks could not be found"}"""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "gatsby",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "English",
        )

        val book = books.single()
        assertEquals("16113", book.id)
        assertEquals("Great Gatsby", book.title)
        assertEquals("F. Scott Fitzgerald", book.author)
    }

    @Test
    fun allSearchFallsThroughToGenreWhenCoreFieldsDoNotMatch() = runBlocking {
        val genreQueries = mutableListOf<String>()
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = when {
                        url.host == "archive.org" -> """{"response":{"docs":[]}}"""
                        url.queryParameter("genre") == "Romance" -> {
                            genreQueries += "Romance"
                            romanceCatalogJson()
                        }
                        else -> """{"error":"Audiobooks could not be found"}"""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "Romance",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "English",
        )

        assertEquals(listOf("romance-book"), books.map { it.id })
        assertEquals(listOf("Romance"), genreQueries)
    }

    @Test
    fun chapterSearch_matchesSectionTitlesWhenCandidateContainsChapter() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(shortStoryCollection026Json().toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "The Story of an Hour",
            field = CatalogSearchField.Chapter,
            limit = 10,
            offset = 0,
            language = "English",
        )

        val book = books.single()
        assertEquals("1986", book.id)
        assertEquals("Short Story Collection Vol. 026", book.title)
        assertEquals("The Story of an Hour", book.chapters.last().title)
    }

    @Test
    fun chapterSearch_usesArchiveChapterMarkdownAsCollectionCandidate() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val title = chain.request().url.queryParameter("title").orEmpty()
                    val body = if ("Short Story Collection Vol. 026" in title) {
                        shortStoryCollection026Json()
                    } else {
                        """{"error":"Audiobooks could not be found"}"""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "[The Story of an Hour](https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_storyofanhour_add.mp3)",
            field = CatalogSearchField.Chapter,
            limit = 10,
            offset = 0,
            language = "English",
        )

        assertEquals("1986", books.single().id)
    }

    @Test
    fun authorSearch_usesLibriVoxAuthorSectionResultsForCollections() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = when {
                        url.encodedPath.contains("/api/feed/authors/") -> {
                            """{"authors":[{"id":"433","first_name":"Kate","last_name":"Chopin","dob":"1850","dod":"1904"}]}"""
                        }
                        url.encodedPath == "/author/get_results" -> authorResultsJson()
                        url.queryParameter("title").orEmpty().contains("Short Story Collection Vol. 026") -> {
                            shortStoryCollection026Json()
                        }
                        else -> """{"error":"Audiobooks could not be found"}"""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "Kate Chopin",
            field = CatalogSearchField.All,
            limit = 10,
            offset = 0,
            language = "English",
        )

        assertEquals("1986", books.single().id)
    }

    @Test
    fun readerSearch_usesLibriVoxReaderSectionResultsForCollections() = runBlocking {
        val source = LibriVoxCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = when {
                        url.queryParameter("reader") == "Alan Davis Drake" -> readerLookupJson()
                        url.encodedPath == "/reader/get_results" -> readerResultsJson()
                        url.queryParameter("title").orEmpty().contains("Short Story Collection Vol. 026") -> {
                            shortStoryCollection026Json()
                        }
                        else -> """{"error":"Audiobooks could not be found"}"""
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build(),
        )

        val books = source.search(
            query = "Alan Davis Drake",
            field = CatalogSearchField.Reader,
            limit = 10,
            offset = 0,
            language = "English",
        )

        assertEquals("1986", books.single().id)
    }


    @Test
    fun parsesBookWithMissingCoverart_fallsBackToArchiveServicesImg() {
        val books = client.parseBooksJson(
            """
            {
              "books": [
                {
                  "id": "9999",
                  "title": "Some LibriVox Title",
                  "totaltimesecs": 1000,
                  "url_iarchive": "https://archive.org/details/some_librivox_recording",
                  "authors": [
                    {"first_name": "Test", "last_name": "Author"}
                  ],
                  "sections": [
                    {
                      "id": "100001",
                      "section_number": "1",
                      "title": "Chapter 1",
                      "listen_url": "https://www.archive.org/download/some_librivox_recording/chapter_1.mp3",
                      "playtime": "500"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val book = books.single()
        assertEquals(
            "https://archive.org/services/img/some_librivox_recording",
            book.coverImageUrl,
        )
    }

    @Test
    fun parsesGenresAndLanguageWhenPresent() {
        val books = client.parseBooksJson(
            """
            {
              "books": [
                {
                  "id": "42",
                  "title": "Worked Example",
                  "totaltimesecs": 600,
                  "language": "English",
                  "url_librivox": "https://librivox.org/example",
                  "url_iarchive": "https://archive.org/details/example_iarchive",
                  "authors": [{"first_name": "An", "last_name": "Author"}],
                  "genres": [
                    {"name": "Mystery"},
                    {"name": "Adventure"}
                  ],
                  "sections": [
                    {
                      "id": "999",
                      "section_number": "1",
                      "title": "Only chapter",
                      "listen_url": "https://www.archive.org/download/example_iarchive/only.mp3",
                      "playtime": "600"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val book = books.single()
        assertEquals("English", book.language)
        assertTrue("Should expose Mystery", book.genres.contains("Mystery"))
        assertTrue("Should expose Adventure", book.genres.contains("Adventure"))
    }

    @Test
    fun parsesTheJungleProjectWithCoverArtAndSections() {
        val books = client.parseBooksJson(
            """
            {
              "books": [
                {
                  "id": "3269",
                  "title": "Jungle",
                  "description": "A LibriVox project",
                  "totaltimesecs": 57768,
                  "url_librivox": "https://librivox.org/the-jungle-by-upton-sinclair/",
                  "authors": [
                    {"first_name": "Upton", "last_name": "Sinclair"}
                  ],
                  "coverart_thumbnail": "https://www.archive.org/download/LibrivoxCdCoverArt/Jungle_the_1003_thumb.jpg",
                  "coverart_jpg": "https://www.archive.org/download/LibrivoxCdCoverArt/Jungle_the_1003.jpg",
                  "sections": [
                    {
                      "id": "182898",
                      "section_number": "1",
                      "title": "Chapter 1",
                      "listen_url": "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_01_sinclair_64kb.mp3",
                      "playtime": "3424",
                      "readers": [{"display_name": "Tom Weiss"}]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val book = books.single()
        assertNotNull(book)
        val chapter = book.chapters.single()
        assertNotNull(chapter)
        assertEquals(AudioBookLibrary.THE_JUNGLE_ID, book.id)
        assertEquals("The Jungle", book.title)
        assertEquals("Upton Sinclair", book.author)
        assertEquals(57768, book.totalDurationSeconds)
        assertEquals("https://www.archive.org/download/LibrivoxCdCoverArt/Jungle_the_1003_thumb.jpg", book.coverImageUrl)
        assertEquals("https://www.archive.org/download/LibrivoxCdCoverArt/Jungle_the_1003.jpg", book.fullCoverImageUrl)
        assertEquals("182898", chapter.id)
        assertEquals("Chapter 1", chapter.title)
        assertEquals(1, chapter.number)
        assertEquals("Tom Weiss", chapter.reader)
        assertEquals(3424, chapter.durationSeconds)
    }

    @Test
    fun parsesPrideAndPrejudiceProjectWithMultiChapterSections() {
        val books = client.parseBooksJson(prideAndPrejudiceJson())

        val book = books.single()
        assertNotNull(book)
        assertEquals(AudioBookLibrary.PRIDE_AND_PREJUDICE_ID, book.id)
        assertEquals("Pride and Prejudice", book.title)
        assertEquals("Jane Austen", book.author)
        assertEquals(47204, book.totalDurationSeconds)
        assertEquals(2, book.chapters.size)
        assertEquals("Chapters 1-3", book.chapters[0].title)
        assertEquals("Chapters 4-5", book.chapters[1].title)
    }

    private fun prideAndPrejudiceJson(): String =
        """
        {
          "books": [
            {
              "id": "253",
              "title": "Pride and Prejudice",
              "description": "A LibriVox project",
              "totaltimesecs": 47204,
              "authors": [
                {"first_name": "Jane", "last_name": "Austen"}
              ],
              "coverart_jpg": "https://archive.org/download/pride_and_prejudice_librivox/Pride_Prejudice_1104.jpg",
              "sections": [
                {
                  "id": "124135",
                  "section_number": "1",
                  "title": "Chapters 1-3",
                  "listen_url": "https://www.archive.org/download/pride_and_prejudice_librivox/prideandprejudice_01-03_austen_64kb.mp3",
                  "playtime": "1132",
                  "readers": [{"display_name": "Chris Goringe"}]
                },
                {
                  "id": "124136",
                  "section_number": "2",
                  "title": "Chapters 4-5",
                  "listen_url": "https://www.archive.org/download/pride_and_prejudice_librivox/prideandprejudice_04-05_austen_64kb.mp3",
                  "playtime": "865",
                  "readers": [{"display_name": "Kara Shallenberg"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun shortStoryCollection026Json(): String =
        """
        {
          "books": [
            {
              "id": "1986",
              "title": "Short Story Collection Vol. 026",
              "description": "A collection of short works.",
              "language": "English",
              "totaltimesecs": 10365,
              "url_librivox": "https://librivox.org/short-story-collection-vol-026/",
              "url_iarchive": "https://archive.org/details/short_story_026_0804_librivox",
              "authors": [
                {"first_name": "", "last_name": "Various"}
              ],
              "sections": [
                {
                  "id": "2001",
                  "section_number": "2",
                  "title": "Doctor Chevalier's Lie",
                  "listen_url": "https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_doctorchevalierslie_chopin_add.mp3",
                  "playtime": "204",
                  "readers": [{"reader_id": "254", "display_name": "Alan Davis Drake (1945-2010)"}]
                },
                {
                  "id": "2010",
                  "section_number": "10",
                  "title": "The Story of an Hour",
                  "listen_url": "https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_storyofanhour_add.mp3",
                  "playtime": "485",
                  "readers": [{"reader_id": "254", "display_name": "Alan Davis Drake (1945-2010)"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun greatGatsbyArchiveSearchJson(): String =
        """
        {
          "response": {
            "docs": [
              { "title": "The Great Gatsby" }
            ]
          }
        }
        """.trimIndent()

    private fun greatGatsbyJson(): String =
        """
        {
          "books": [
            {
              "id": "16113",
              "title": "Great Gatsby",
              "description": "A LibriVox project",
              "language": "English",
              "totaltimesecs": 32000,
              "url_librivox": "https://librivox.org/the-great-gatsby-by-f-scott-fitzgerald/",
              "url_iarchive": "https://archive.org/details/greatgatsby_2101_librivox",
              "authors": [
                {"first_name": "F. Scott", "last_name": "Fitzgerald"}
              ],
              "sections": [
                {
                  "id": "9001",
                  "section_number": "1",
                  "title": "Chapter 1",
                  "listen_url": "https://www.archive.org/download/greatgatsby_2101_librivox/greatgatsby_01_fitzgerald_64kb.mp3",
                  "playtime": "3200",
                  "readers": [{"reader_id": "1", "display_name": "Kara Shallenberg"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun romanceCatalogJson(): String =
        """
        {
          "books": [
            {
              "id": "romance-book",
              "title": "Letters from Home",
              "description": "A LibriVox project",
              "language": "English",
              "totaltimesecs": 1200,
              "url_librivox": "https://librivox.org/letters-from-home/",
              "authors": [
                {"first_name": "Test", "last_name": "Author"}
              ],
              "genres": [
                {"name": "Romance"}
              ],
              "sections": [
                {
                  "id": "romance-book-1",
                  "section_number": "1",
                  "title": "Chapter 1",
                  "listen_url": "https://example.com/romance.mp3",
                  "readers": [{"reader_id": "1", "display_name": "Reader"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun authorResultsJson(): String =
        """
        {
          "status": "SUCCESS",
          "results": "<li class=\"catalog-result\"><div class=\"result-data\"><h3><a href=\"https://librivox.org/short-story-collection-vol-026/\">The Story of an Hour (in  Short Story Collection Vol. 026)</a></h3><p class=\"book-author\"><a href=\"https://librivox.org/author/433\">Kate Chopin</a></p></div></li>",
          "pagination": ""
        }
        """.trimIndent()

    private fun readerLookupJson(): String =
        """
        {
          "books": [
            {
              "id": "47",
              "title": "Count of Monte Cristo",
              "authors": [{"first_name": "Alexandre", "last_name": "Dumas"}],
              "sections": [
                {
                  "id": "1",
                  "section_number": "1",
                  "title": "Chapter",
                  "listen_url": "https://example.com/chapter.mp3",
                  "readers": [{"reader_id": "254", "display_name": "Alan Davis Drake (1945-2010)"}]
                }
              ]
            }
          ]
        }
        """.trimIndent()

    private fun readerResultsJson(): String =
        """
        {
          "status": "SUCCESS",
          "results": "<li class=\"catalog-result\"><div class=\"result-data\"><h3><a href=\"https://librivox.org/short-story-collection-vol-026/\">Short Story Collection Vol. 026</a></h3><p class=\"book-author\"><a href=\"https://librivox.org/author/18\">Various</a></p></div></li>",
          "pagination": ""
        }
        """.trimIndent()
}
