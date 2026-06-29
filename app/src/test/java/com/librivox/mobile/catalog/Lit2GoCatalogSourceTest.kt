package com.librivox.mobile.catalog

import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.downloadableCoverImageUrl
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Lit2GoCatalogSourceTest {
    private val source = Lit2GoCatalogSource()

    @Test
    fun letterIndex_extractsBooksFromBookIconAnchors() {
        val html = """
            <html><body>
              <h1><a href="https://etc.usf.edu/lit2go/">Lit2Go</a></h1>
              <figure>
                <a href="https://etc.usf.edu/lit2go/21/the-adventures-of-huckleberry-finn/" class="book_icon">
                  <img height="150" width="150"
                    src="https://etc.usf.edu/lit2go/images/book-place-holder.jpg"
                    data-src="https://etc.usf.edu/lit2go/static/thumbnails/books/21.png"
                    alt="The Adventures of Huckleberry Finn">
                </a>
                <a href="https://etc.usf.edu/lit2go/21/the-adventures-of-huckleberry-finn/">The Adventures of Huckleberry Finn</a>
                <figcaption class="author">
                  by <a href="https://etc.usf.edu/lit2go/authors/5/mark-twain/">Mark Twain</a>
                </figcaption>
              </figure>
              <figure>
                <a href="https://etc.usf.edu/lit2go/1/alices-adventures-in-wonderland/" class="book_icon">
                  <img height="150" width="150"
                    src="https://etc.usf.edu/lit2go/images/book-place-holder.jpg"
                    data-src="https://etc.usf.edu/lit2go/static/thumbnails/books/1.png"
                    alt="Alice's Adventures in Wonderland">
                </a>
                <a href="https://etc.usf.edu/lit2go/1/alices-adventures-in-wonderland/">Alice's Adventures in Wonderland</a>
                <figcaption class="author">
                  by <a href="https://etc.usf.edu/lit2go/authors/1/lewis-carroll/">Lewis Carroll</a>
                </figcaption>
              </figure>
            </body></html>
        """.trimIndent()

        val stubs = source.parseLetterIndex(html)

        assertEquals(2, stubs.size)
        val huck = stubs.first { it.id == "21" }
        assertEquals("the-adventures-of-huckleberry-finn", huck.slug)
        assertEquals("The Adventures of Huckleberry Finn", huck.title)
        assertEquals(
            "https://etc.usf.edu/lit2go/static/thumbnails/books/21.png",
            huck.coverUrl,
        )
        assertEquals("Mark Twain", huck.author)
        val alice = stubs.first { it.id == "1" }
        assertEquals("Alice's Adventures in Wonderland", alice.title)
        assertEquals("Lewis Carroll", alice.author)
    }

    @Test
    fun letterIndex_skipsBrandH1AndNavigationLinks() {
        val html = """
            <html><body>
              <header>
                <h1><a href="https://etc.usf.edu/lit2go/">Lit<span class="blue">2</span>Go</a></h1>
                <nav>
                  <ul>
                    <li><a href="https://etc.usf.edu/lit2go/books/">Books</a></li>
                    <li><a href="https://etc.usf.edu/lit2go/authors/">Authors</a></li>
                  </ul>
                </nav>
              </header>
              <figure>
                <a href="https://etc.usf.edu/lit2go/35/aesops-fables/" class="book_icon">
                  <img alt="Aesop's Fables" data-src="https://etc.usf.edu/lit2go/static/thumbnails/books/35.png" />
                </a>
              </figure>
            </body></html>
        """.trimIndent()

        val stubs = source.parseLetterIndex(html)

        assertEquals(1, stubs.size)
        assertEquals("35", stubs.single().id)
        assertEquals("Aesop's Fables", stubs.single().title)
    }

    @Test
    fun bookDetail_pullsTitleFromH2NotBrandH1() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://etc.usf.edu/lit2go/static/ogimages/books/108.jpg">
              </head>
              <body>
                <h1><a href="https://etc.usf.edu/lit2go/">Lit<span class="blue">2</span>Go</a></h1>
                <div id="column_primary">
                  <h2>Winesburg, Ohio</h2>
                  <h3>by <a href="https://etc.usf.edu/lit2go/authors/14/sherwood-anderson/">Sherwood Anderson</a></h3>
                  <div id="page_thumbnail">
                    <img src="https://etc.usf.edu/lit2go/static/thumbnails/books/108.png" alt="Winesburg, Ohio">
                  </div>
                  <p>
                    <p><em>Winesburg, Ohio</em> is a 1919 collection of short stories by Sherwood Anderson.</p>
                  </p>
                  <p><strong>Source:</strong> Anderson, S. (1919) Winesburg, Ohio. New York, NY: B.W. Huebsch.</p>
                  <dl>
                    <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1915/introduction-by-irving-howe/">Introduction by Irving Howe</a></dt>
                    <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1916/the-book-of-the-grotesque/">The Book of the Grotesque</a></dt>
                    <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1917/hands-concerning-wing-biddlebaum/">Hands, concerning Wing Biddlebaum</a></dt>
                  </dl>
                </div>
              </body>
            </html>
        """.trimIndent()

        val book = source.parseBookDetail("108", "winesburg-ohio", html)

        assertEquals("lit2go-108__winesburg-ohio", book.id)
        assertEquals("Winesburg, Ohio", book.title)
        assertEquals("Sherwood Anderson", book.author)
        assertEquals(BookSource.Lit2Go, book.source)
        assertTrue(
            "Expected description to mention Sherwood Anderson, got '${book.description}'",
            book.description.contains("Sherwood Anderson"),
        )
        assertTrue(
            "Description should not be the citation",
            !book.description.startsWith("Source:"),
        )
        assertEquals(
            "https://etc.usf.edu/lit2go/static/thumbnails/books/108.png",
            book.coverImageUrl,
        )
        assertEquals(
            "https://etc.usf.edu/lit2go/static/ogimages/books/108.jpg",
            book.fullCoverImageUrl,
        )
        assertEquals("English", book.language)
        assertEquals(3, book.chapters.size)
        assertEquals("108-1915", book.chapters[0].id)
        assertEquals("Introduction by Irving Howe", book.chapters[0].title)
        assertEquals(1, book.chapters[0].number)
    }

    @Test
    fun bookDetail_computesAudioUrlsUsingPositionalIndex() {
        val html = """
            <html><body>
              <div id="column_primary">
                <h2>Winesburg, Ohio</h2>
                <h3>by <a href="https://etc.usf.edu/lit2go/authors/14/sherwood-anderson/">Sherwood Anderson</a></h3>
                <dl>
                  <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1915/introduction-by-irving-howe/">Introduction by Irving Howe</a></dt>
                  <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1916/the-book-of-the-grotesque/">The Book of the Grotesque</a></dt>
                </dl>
              </div>
            </body></html>
        """.trimIndent()

        val book = source.parseBookDetail("108", "winesburg-ohio", html)

        val first = book.chapters[0]
        val second = book.chapters[1]
        assertEquals(
            "https://etc.usf.edu/lit2go/audio/mp3/winesburg-ohio-001-introduction-by-irving-howe.1915.mp3",
            first.listenUrl,
        )
        assertEquals(
            "https://etc.usf.edu/lit2go/audio/mp3/winesburg-ohio-002-the-book-of-the-grotesque.1916.mp3",
            second.listenUrl,
        )
        assertEquals("audio/mpeg", first.mimeType)
    }

    @Test
    fun bookDetail_fallsBackToThumbnailPatternWhenMetaImageMissing() {
        val html = """
            <html><body>
              <div id="column_primary">
                <h2>Some Book</h2>
              </div>
            </body></html>
        """.trimIndent()

        val book = source.parseBookDetail("999", "some-book", html)

        assertEquals(
            "https://etc.usf.edu/lit2go/static/thumbnails/books/999.png",
            book.coverImageUrl,
        )
        assertNotNull(book)
    }

    @Test
    fun bookDetail_skipsBrandH1EvenWithoutColumnPrimaryH2() {
        val html = """
            <html><body>
              <h1>Lit2Go</h1>
              <h2>Aesop's Fables</h2>
            </body></html>
        """.trimIndent()

        val book = source.parseBookDetail("35", "aesops-fables", html)

        assertEquals("Aesop's Fables", book.title)
    }

    @Test
    fun parseAppleArtworkUrl_upscalesMatchingEbookArtwork() {
        val rawJson = """
            {
              "resultCount": 2,
              "results": [
                {
                  "trackName": "The Jungle",
                  "artistName": "Upton Sinclair",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/source/100x100bb.jpg"
                },
                {
                  "trackName": "The Jungle Book",
                  "artistName": "Rudyard Kipling",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/other/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()

        val coverUrl = source.parseAppleArtworkUrl(rawJson, title = "The Jungle", author = "Upton Sinclair")

        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/source/1000x1000bb.jpg",
            coverUrl,
        )
    }

    @Test
    fun parseAppleArtworkUrl_ignoresLooseTitleWhenAuthorDoesNotMatch() {
        val rawJson = """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackName": "The Jungle Book",
                  "artistName": "Rudyard Kipling",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/other/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()

        val coverUrl = source.parseAppleArtworkUrl(rawJson, title = "The Jungle", author = "Upton Sinclair")

        assertEquals(null, coverUrl)
    }

    @Test
    fun fetchByIds_usesAppleArtworkForLit2GoCoverDownloads() = runBlocking {
        val source = Lit2GoCatalogSource(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val url = request.url
                    val body = when (url.host) {
                        "etc.usf.edu" -> theJungleDetailHtml() to "text/html"
                        "itunes.apple.com" -> {
                            assertEquals("/search", url.encodedPath)
                            assertEquals("The Jungle Upton Sinclair", url.queryParameter("term"))
                            assertEquals("ebook", url.queryParameter("media"))
                            assertEquals("ebook", url.queryParameter("entity"))
                            appleJungleArtworkJson() to "application/json"
                        }
                        else -> error("Unexpected request: $url")
                    }
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.first.toResponseBody(body.second.toMediaType()))
                        .build()
                }
                .build(),
        )

        val book = source.fetchByIds("lit2go-200__the-jungle").single()

        val expectedCover = "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/jungle/1000x1000bb.jpg"
        assertEquals(expectedCover, book.coverImageUrl)
        assertEquals(expectedCover, book.fullCoverImageUrl)
        assertEquals(expectedCover, book.downloadableCoverImageUrl())
    }

    private fun theJungleDetailHtml(): String =
        """
            <html>
              <head>
                <meta property="og:image" content="https://etc.usf.edu/lit2go/static/ogimages/books/200.jpg">
              </head>
              <body>
                <div id="column_primary">
                  <h2>The Jungle</h2>
                  <h3>by <a href="https://etc.usf.edu/lit2go/authors/22/upton-sinclair/">Upton Sinclair</a></h3>
                  <div id="page_thumbnail">
                    <img src="https://etc.usf.edu/lit2go/static/thumbnails/books/200.png" alt="The Jungle">
                  </div>
                  <dl>
                    <dt><a href="https://etc.usf.edu/lit2go/200/the-jungle/1/chapter-1/">Chapter 1</a></dt>
                  </dl>
                </div>
              </body>
            </html>
        """.trimIndent()

    private fun appleJungleArtworkJson(): String =
        """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackName": "The Jungle",
                  "artistName": "Upton Sinclair",
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Publication/v4/jungle/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()
}
