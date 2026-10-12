package com.librivox.mobile.catalog

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Lit2GoMockWebServerTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun http200OutageDetailPageIsUnavailableInsteadOfAnAuthoritativeEmpty() = runBlocking {
        server.dispatcher = lit2GoDispatcher(
            indexBody = fixture("lit2go/index-a.html"),
            details = mapOf(
                "/lit2go/1/alices-adventures-in-wonderland/" to
                    MockResponse().setResponseCode(200)
                        .setBody(fixture("failures/http-200-error-page.html")),
            ),
        )
        val source = Lit2GoCatalogSource(rewritingClient())

        val failure = runCatching {
            source.search("Alice", CatalogSearchField.Title, 10, 0, "English")
        }.exceptionOrNull()

        assertTrue(failure is CatalogSourceUnavailableException)
    }

    @Test
    fun validIndexWithAllDetailPages404IsUnavailable() = runBlocking {
        server.dispatcher = lit2GoDispatcher(
            indexBody = fixture("lit2go/index-a.html"),
            details = mapOf(
                "/lit2go/1/alices-adventures-in-wonderland/" to
                    MockResponse().setResponseCode(404),
            ),
        )
        val source = Lit2GoCatalogSource(rewritingClient())

        val failure = runCatching {
            source.search("Alice", CatalogSearchField.Title, 10, 0, "English")
        }.exceptionOrNull()

        assertTrue(failure is CatalogSourceUnavailableException)
    }

    @Test
    fun partialDetailFailureKeepsSuccessfulBooks() = runBlocking {
        server.dispatcher = lit2GoDispatcher(
            indexBody = twoAliceBooksIndex(),
            details = mapOf(
                "/lit2go/1/alices-adventures-in-wonderland/" to
                    MockResponse().setResponseCode(200).setBody(fixture("lit2go/detail-alice.html")),
                "/lit2go/2/alice-through-the-looking-glass/" to
                    MockResponse().setResponseCode(404),
            ),
        )
        val source = Lit2GoCatalogSource(rewritingClient())

        val books = source.search("Alice", CatalogSearchField.Title, 10, 0, "English")

        assertEquals(listOf("lit2go-1__alices-adventures-in-wonderland"), books.map { it.id })
        assertEquals(2, books.single().chapters.size)
    }

    private fun lit2GoDispatcher(
        indexBody: String,
        details: Map<String, MockResponse>,
    ): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath.orEmpty()
            return when {
                path == "/lit2go/books/index/a/" ->
                    MockResponse().setResponseCode(200).setBody(indexBody)
                path.startsWith("/lit2go/books/index/") ->
                    MockResponse().setResponseCode(200).setBody("<html><body><main></main></body></html>")
                path == "/search" ->
                    MockResponse().setResponseCode(200).setBody("""{"resultCount":0,"results":[]}""")
                path in details -> details.getValue(path)
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun rewritingClient(): OkHttpClient {
        val destination = server.url("/")
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val rewritten = original.url.newBuilder()
                    .scheme(destination.scheme)
                    .host(destination.host)
                    .port(destination.port)
                    .build()
                chain.proceed(original.newBuilder().url(rewritten).build())
            }
            .build()
    }

    private fun fixture(relative: String): String {
        val fixturePath = "catalog-contract/fixtures/v1/$relative"
        return generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .map { File(it, fixturePath) }
            .firstOrNull(File::isFile)
            ?.readText()
            ?: error("Could not locate $fixturePath")
    }

    private fun twoAliceBooksIndex(): String =
        """
        <!doctype html>
        <html><body><main>
          <figure>
            <a class="book_icon" href="https://etc.usf.edu/lit2go/1/alices-adventures-in-wonderland/">
              <img alt="Alice's Adventures in Wonderland" src="/lit2go/static/thumbnails/books/1.png">
            </a>
          </figure>
          <figure>
            <a class="book_icon" href="https://etc.usf.edu/lit2go/2/alice-through-the-looking-glass/">
              <img alt="Alice Through the Looking-Glass" src="/lit2go/static/thumbnails/books/2.png">
            </a>
          </figure>
        </main></body></html>
        """.trimIndent()
}
