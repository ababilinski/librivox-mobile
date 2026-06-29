package com.librivox.mobile.catalog

import com.librivox.mobile.model.BookSource
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GutendexCatalogSourceTest {

    @Test
    fun parseBooksJson_mapsGutendexMetadata() {
        val rawJson = """
            {
              "count": 1,
              "next": null,
              "previous": null,
              "results": [
                {
                  "id": 1342,
                  "title": "Pride and Prejudice",
                  "authors": [
                    { "name": "Austen, Jane", "birth_year": 1775, "death_year": 1817 }
                  ],
                  "summaries": [
                    "A comedy of manners about family, class, and marriage."
                  ],
                  "subjects": [
                    "Courtship -- Fiction",
                    "England -- Fiction"
                  ],
                  "bookshelves": [
                    "Best Books Ever Listings",
                    "Harvard Classics"
                  ],
                  "languages": ["en"],
                  "formats": {
                    "image/jpeg": "https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg",
                    "audio/mpeg": "https://www.gutenberg.org/files/1342/mp3/1342-01.mp3",
                    "text/plain": "https://www.gutenberg.org/files/1342/1342-0.txt"
                  },
                  "download_count": 12345
                }
              ]
            }
        """.trimIndent()

        val book = GutendexCatalogSource().parseBooksJson(rawJson).single()

        assertEquals("gutendex-1342", book.id)
        assertEquals("Pride and Prejudice", book.title)
        assertEquals("Jane Austen", book.author)
        assertEquals("A comedy of manners about family, class, and marriage.", book.description)
        assertEquals(BookSource.Gutendex, book.source)
        assertEquals("https://www.gutenberg.org/cache/epub/1342/pg1342.cover.medium.jpg", book.coverImageUrl)
        assertEquals("https://www.gutenberg.org/ebooks/1342", book.gutenbergUrl)
        assertEquals("English", book.language)
        assertEquals(1, book.chapters.size)
        assertEquals("gutendex-1342-audio", book.chapters.single().id)
        assertEquals("Complete audiobook", book.chapters.single().title)
        assertEquals("https://www.gutenberg.org/files/1342/mp3/1342-01.mp3", book.chapters.single().listenUrl)
        assertEquals("audio/mpeg", book.chapters.single().mimeType)
        assertTrue(book.genres.contains("Best Books Ever Listings"))
        assertTrue(book.genres.contains("Courtship -- Fiction"))
    }

    @Test
    fun parseBooksJson_acceptsSingleBookResponse() {
        val rawJson = """
            {
              "id": 84,
              "title": "Frankenstein; Or, The Modern Prometheus",
              "authors": [{ "name": "Shelley, Mary Wollstonecraft" }],
              "summaries": [],
              "subjects": [],
              "bookshelves": [],
              "languages": ["en"],
              "formats": {
                "audio/ogg": "https://www.gutenberg.org/files/84/ogg/84-01.ogg"
              },
              "download_count": 1000
            }
        """.trimIndent()

        val book = GutendexCatalogSource().parseBooksJson(rawJson).single()

        assertEquals("gutendex-84", book.id)
        assertEquals("Mary Wollstonecraft Shelley", book.author)
        assertEquals("https://www.gutenberg.org/ebooks/84", book.gutenbergUrl)
        assertEquals("https://www.gutenberg.org/files/84/ogg/84-01.ogg", book.chapters.single().listenUrl)
        assertEquals("audio/ogg", book.chapters.single().mimeType)
    }

    @Test
    fun parseBooksJson_usesTranslatorWhenAuthorsAreMissing() {
        val book = GutendexCatalogSource().parseBooksJson(
            creatorFallbackJson(
                people = """
                    "authors": [],
                    "translators": [{ "name": "Translator, Example" }],
                    "editors": [{ "name": "Editor, Example" }]
                """.trimIndent(),
            ),
        ).single()

        assertEquals("Translated by Example Translator", book.author)
    }

    @Test
    fun parseBooksJson_usesEditorWhenAuthorsAndTranslatorsAreMissing() {
        val book = GutendexCatalogSource().parseBooksJson(
            creatorFallbackJson(
                people = """
                    "authors": [],
                    "translators": [],
                    "editors": [{ "name": "Editor, Example" }]
                """.trimIndent(),
            ),
        ).single()

        assertEquals("Edited by Example Editor", book.author)
    }

    @Test
    fun parseBooksJson_usesUnknownAuthorWhenCreatorArraysAreEmpty() {
        val book = GutendexCatalogSource().parseBooksJson(
            creatorFallbackJson(
                people = """
                    "authors": [],
                    "translators": [],
                    "editors": []
                """.trimIndent(),
            ),
        ).single()

        assertEquals("Unknown Author", book.author)
    }

    @Test
    fun parseBooksJson_ignoresTextOnlyGutenbergBooks() {
        val rawJson = """
            {
              "count": 1,
              "next": null,
              "previous": null,
              "results": [
                {
                  "id": 2701,
                  "title": "Moby Dick; Or, The Whale",
                  "authors": [{ "name": "Melville, Herman" }],
                  "summaries": [],
                  "subjects": [],
                  "bookshelves": [],
                  "languages": ["en"],
                  "media_type": "Text",
                  "formats": {
                    "image/jpeg": "https://www.gutenberg.org/cache/epub/2701/pg2701.cover.medium.jpg",
                    "text/plain; charset=utf-8": "https://www.gutenberg.org/ebooks/2701.txt.utf-8"
                  }
                }
              ]
            }
        """.trimIndent()

        val books = GutendexCatalogSource().parseBooksJson(rawJson)

        assertTrue(books.isEmpty())
    }

    @Test
    fun fetchByIds_resolvesStaleTextOnlyIdToMatchingAudioBook() = runBlocking {
        val source = GutendexCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = if (url.encodedPath.endsWith("/books/2701")) {
                        mobyDickTextBookJson()
                    } else {
                        assertEquals("audio", url.queryParameter("mime_type"))
                        assertEquals("Moby Dick; Or, The Whale", url.queryParameter("search"))
                        mobyDickAudioSearchJson()
                    }.toResponseBody("application/json".toMediaType())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
                .build(),
        )

        val book = source.fetchByIds("gutendex-2701").single()

        assertEquals("gutendex-2701", book.id)
        assertEquals("Moby Dick; Or, The Whale", book.title)
        assertEquals("https://www.gutenberg.org/ebooks/28794", book.gutenbergUrl)
        assertEquals(1, book.chapters.size)
        assertEquals(
            "https://www.gutenberg.org/files/28794/mp3/28794-01.mp3",
            book.chapters.single().listenUrl,
        )
    }

    @Test
    fun fetchByIds_ignoresStaleTextOnlyIdWhenNoAudioBookExists() = runBlocking {
        val source = GutendexCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val body = if (url.encodedPath.endsWith("/books/64317")) {
                        greatGatsbyTextBookJson()
                    } else {
                        assertEquals("audio", url.queryParameter("mime_type"))
                        assertEquals("The Great Gatsby", url.queryParameter("search"))
                        """
                            {
                              "count": 0,
                              "next": null,
                              "previous": null,
                              "results": []
                            }
                        """.trimIndent()
                    }.toResponseBody("application/json".toMediaType())
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                }
                .build(),
        )

        assertTrue(source.fetchByIds("gutendex-64317").isEmpty())
    }

    @Test
    fun fetchByIds_expandsGutenbergHtmlIndexIntoChapters() = runBlocking {
        val source = GutendexCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val (body, contentType) = when {
                        url.encodedPath.endsWith("/books/20687") -> {
                            prideAndPrejudiceAudioBookJson() to "application/json"
                        }
                        url.toString() == "https://www.gutenberg.org/files/20687/20687-index.html" -> {
                            prideAndPrejudiceIndexHtml() to "text/html"
                        }
                        else -> error("Unexpected URL: $url")
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody(contentType.toMediaType()))
                        .build()
                }
                .build(),
        )

        val book = source.fetchByIds("gutendex-20687").single()

        assertEquals("gutendex-20687", book.id)
        assertEquals(listOf("Chris Goringe", "Kara Shallenberg"), book.narrators)
        assertEquals(2, book.chapters.size)
        assertEquals("Chapters 1-3", book.chapters[0].title)
        assertEquals(1_132, book.chapters[0].durationSeconds)
        assertEquals(
            "https://www.gutenberg.org/files/20687/mp3/20687-01.mp3",
            book.chapters[0].listenUrl,
        )
        assertEquals("audio/mpeg", book.chapters[0].mimeType)
        assertEquals("Chapters 4-5", book.chapters[1].title)
        assertEquals(865, book.chapters[1].durationSeconds)
        assertEquals(
            "https://www.gutenberg.org/files/20687/mp3/20687-02.mp3",
            book.chapters[1].listenUrl,
        )
        assertEquals(1_997, book.totalDurationSeconds)
    }

    @Test
    fun fetchByIds_usesAudiobookCoversWhenGutendexHasNoImage() = runBlocking {
        val source = GutendexCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    val (body, contentType) = when {
                        url.encodedPath.endsWith("/books/999") -> {
                            noImageAudioBookJson() to "application/json"
                        }
                        url.host == "audiobookcovers.com" -> {
                            assertEquals("/cover/bytext", url.encodedPath)
                            assertEquals("No Cover Book Example Author", url.queryParameter("q"))
                            """
                                [
                                  {
                                    "versions": {
                                      "png": {
                                        "original": "https://images.audiobookcovers.com/original/no-cover-book.jpg"
                                      }
                                    }
                                  }
                                ]
                            """.trimIndent() to "application/json"
                        }
                        else -> error("Unexpected URL: $url")
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody(contentType.toMediaType()))
                        .build()
                }
                .build(),
        )

        val book = source.fetchByIds("gutendex-999").single()

        assertEquals("https://images.audiobookcovers.com/original/no-cover-book.jpg", book.coverImageUrl)
    }

    @Test
    fun languageCode_mapsSupportedCatalogLanguages() {
        assertEquals("en", GutendexCatalogSource.languageCode("English"))
        assertEquals("fr", GutendexCatalogSource.languageCode("fr"))
        assertEquals("pl", GutendexCatalogSource.languageCode("Polish"))
    }

    @Test
    fun browse_fetchesAcrossGutendexPagesToFillRequestedLimit() = runBlocking {
        val source = GutendexCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor(gutendexPageInterceptor())
                .build(),
        )

        val books = source.browse(limit = 50, offset = 0, language = "")

        assertEquals(50, books.size)
        assertEquals("gutendex-1", books.first().id)
        assertEquals("gutendex-50", books.last().id)
    }

    private fun mobyDickTextBookJson(): String =
        """
            {
              "id": 2701,
              "title": "Moby Dick; Or, The Whale",
              "authors": [{ "name": "Melville, Herman" }],
              "summaries": [],
              "subjects": ["Whales -- Fiction"],
              "bookshelves": ["Best Books Ever Listings"],
              "languages": ["en"],
              "media_type": "Text",
              "formats": {
                "text/plain; charset=utf-8": "https://www.gutenberg.org/ebooks/2701.txt.utf-8"
              }
            }
        """.trimIndent()

    private fun greatGatsbyTextBookJson(): String =
        """
            {
              "id": 64317,
              "title": "The Great Gatsby",
              "authors": [{ "name": "Fitzgerald, F. Scott" }],
              "summaries": [],
              "subjects": ["Wealth -- Fiction"],
              "bookshelves": [],
              "languages": ["en"],
              "media_type": "Text",
              "formats": {
                "text/plain; charset=utf-8": "https://www.gutenberg.org/ebooks/64317.txt.utf-8",
                "text/html": "https://www.gutenberg.org/ebooks/64317.html.images",
                "image/jpeg": "https://www.gutenberg.org/cache/epub/64317/pg64317.cover.medium.jpg"
              }
            }
        """.trimIndent()

    private fun prideAndPrejudiceAudioBookJson(): String =
        """
            {
              "id": 20687,
              "title": "Pride and Prejudice",
              "authors": [{ "name": "Austen, Jane" }],
              "summaries": [],
              "subjects": ["Courtship -- Fiction"],
              "bookshelves": ["Best Books Ever Listings"],
              "languages": ["en"],
              "media_type": "Sound",
              "formats": {
                "text/html": "https://www.gutenberg.org/files/20687/20687-index.html",
                "audio/ogg": "https://www.gutenberg.org/files/20687/ogg/20687-01.ogg",
                "audio/mp4": "https://www.gutenberg.org/files/20687/m4b/20687-01.m4b",
                "audio/mpeg": "https://www.gutenberg.org/files/20687/mp3/20687-01.mp3",
                "image/jpeg": "https://www.gutenberg.org/cache/epub/20687/pg20687.cover.medium.jpg"
              }
            }
        """.trimIndent()

    private fun creatorFallbackJson(people: String): String =
        """
            {
              "id": 9101,
              "title": "Creator Fallback",
              $people,
              "summaries": [],
              "subjects": [],
              "bookshelves": [],
              "languages": ["en"],
              "formats": {
                "audio/mpeg": "https://www.gutenberg.org/files/9101/mp3/9101-01.mp3"
              }
            }
        """.trimIndent()

    private fun noImageAudioBookJson(): String =
        """
            {
              "id": 999,
              "title": "No Cover Book",
              "authors": [{ "name": "Author, Example" }],
              "summaries": [],
              "subjects": [],
              "bookshelves": [],
              "languages": ["en"],
              "media_type": "Sound",
              "formats": {
                "audio/mpeg": "https://www.gutenberg.org/files/999/mp3/999-01.mp3"
              }
            }
        """.trimIndent()

    private fun prideAndPrejudiceIndexHtml(): String =
        """
            <html>
              <body>
                <h1>Pride and Prejudice</h1>
                <p>This audio reading of Pride and Prejudice is read by</p>
                <p>Chris Goringe, Kara Shallenberg</p>
                <h3>Contents</h3>
                <ul>
                  <li>Chapters 1-3 - 00:18:52
                    <ul>
                      <li><a href="mp3/20687-01.mp3">20687-01.mp3</a></li>
                      <li><a href="ogg/20687-01.ogg">20687-01.ogg</a></li>
                      <li><a href="m4b/20687-01.m4b">20687-01.m4b</a></li>
                    </ul>
                  </li>
                  <li>Chapters 4-5 - 00:14:25
                    <ul>
                      <li><a href="mp3/20687-02.mp3">20687-02.mp3</a></li>
                      <li><a href="ogg/20687-02.ogg">20687-02.ogg</a></li>
                      <li><a href="m4b/20687-02.m4b">20687-02.m4b</a></li>
                    </ul>
                  </li>
                </ul>
              </body>
            </html>
        """.trimIndent()

    private fun mobyDickAudioSearchJson(): String =
        """
            {
              "count": 1,
              "next": null,
              "previous": null,
              "results": [
                {
                  "id": 28794,
                  "title": "Moby Dick; Or, The Whale",
                  "authors": [{ "name": "Melville, Herman" }],
                  "summaries": [],
                  "subjects": ["Whales -- Fiction"],
                  "bookshelves": ["Best Books Ever Listings"],
                  "languages": ["en"],
                  "media_type": "Sound",
                  "formats": {
                    "audio/ogg": "https://www.gutenberg.org/files/28794/ogg/28794-01.ogg",
                    "audio/mp4": "https://www.gutenberg.org/files/28794/m4b/28794-01.m4b",
                    "audio/mpeg": "https://www.gutenberg.org/files/28794/mp3/28794-01.mp3"
                  }
                }
              ]
            }
        """.trimIndent()

    private fun gutendexPageInterceptor(): Interceptor =
        Interceptor { chain ->
            assertEquals("audio", chain.request().url.queryParameter("mime_type"))
            val page = chain.request().url.queryParameter("page")?.toIntOrNull() ?: 1
            val startId = ((page - 1) * 32) + 1
            val body = gutendexPageJson(startId, 32)
                .toResponseBody("application/json".toMediaType())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }

    private fun gutendexPageJson(startId: Int, count: Int): String {
        val results = (startId until startId + count).joinToString(",") { id ->
            """
                {
                  "id": $id,
                  "title": "Book $id",
                  "authors": [{ "name": "Author, Example" }],
                  "summaries": [],
                  "subjects": [],
                  "bookshelves": [],
                  "languages": ["en"],
                  "formats": {
                    "audio/mpeg": "https://www.gutenberg.org/files/$id/mp3/$id-01.mp3"
                  }
                }
            """.trimIndent()
        }
        return """
            {
              "count": 100,
              "next": "https://gutendex.com/books/?page=2",
              "previous": null,
              "results": [$results]
            }
        """.trimIndent()
    }
}
