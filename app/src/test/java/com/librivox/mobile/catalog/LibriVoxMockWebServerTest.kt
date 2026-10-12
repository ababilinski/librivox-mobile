package com.librivox.mobile.catalog

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

class LibriVoxMockWebServerTest {
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
    fun titleSearchUsesAnchoredFeedRequestFirst() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody(aliceFeedJson())
        }
        val source = LibriVoxCatalogSource(rewritingClient())

        val books = source.search("Alice ", CatalogSearchField.Title, 20, 0, "")

        assertTrue(books.any { it.id == "200" })
        val request = server.takeRequest()
        assertEquals("/api/feed/audiobooks/", request.requestUrl?.encodedPath)
        assertEquals("^Alice", request.requestUrl?.queryParameter("title"))
    }

    @Test
    fun partialAuthorSearchPrimesSessionAndSendsAdvancedFormFields() = runBlocking {
        val requests = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                return when (request.requestUrl?.encodedPath) {
                    "/search" -> MockResponse()
                        .setResponseCode(200)
                        .addHeader("Set-Cookie", "PHPSESSID=test-session; Path=/; Secure; HttpOnly")
                        .setBody("<html></html>")
                    "/advanced_search" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(advancedAliceAuthorJson())
                    "/author/get_results" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"status":"SUCCESS","results":"","pagination":""}""")
                    else -> MockResponse().setResponseCode(200).setBody(aliceGerstenbergFeedJson())
                }
            }
        }
        val source = LibriVoxCatalogSource(rewritingClient())

        val books = source.search("Alice", CatalogSearchField.Author, 5, 0, "English")

        assertEquals(listOf("alice-drama"), books.map { it.id })
        val advanced = synchronized(requests) {
            requests.first { it.requestUrl?.encodedPath == "/advanced_search" }
        }
        assertEquals("PHPSESSID=test-session", advanced.getHeader("Cookie"))
        assertEquals("Alice", advanced.requestUrl?.queryParameter("author"))
        assertEquals("", advanced.requestUrl?.queryParameter("title"))
        assertEquals("advanced", advanced.requestUrl?.queryParameter("search_form"))
        assertEquals("either", advanced.requestUrl?.queryParameter("project_type"))
    }

    @Test
    fun blankAdvancedResponseRetriesWithANewSessionThenFailsExplicitly() = runBlocking {
        var sessionNumber = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestUrl?.encodedPath == "/search") {
                    sessionNumber++
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Set-Cookie", "PHPSESSID=session-$sessionNumber; Path=/")
                        .setBody("<html></html>")
                } else {
                    MockResponse().setResponseCode(200).setBody("")
                }
        }
        val source = LibriVoxCatalogSource(rewritingClient())

        val failure = runCatching {
            source.search("Alice", CatalogSearchField.Author, 5, 0, "English")
        }.exceptionOrNull()

        // The direct feed fallbacks also return blank, so the whole operation is unavailable;
        // importantly it is not converted into an authoritative zero.
        assertTrue(failure is CatalogSourceUnavailableException)
        assertEquals(2, sessionNumber)
    }

    @Test
    fun concurrentAdvancedFieldsUseIndependentPhpSessions() = runBlocking {
        val nextSession = AtomicInteger()
        val advancedCookies = ConcurrentHashMap.newKeySet<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.requestUrl?.encodedPath) {
                    "/search" -> {
                        val id = nextSession.incrementAndGet()
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "PHPSESSID=session-$id; Path=/")
                            .setBody("<html></html>")
                    }
                    "/advanced_search" -> {
                        request.getHeader("Cookie")?.let(advancedCookies::add)
                        MockResponse().setResponseCode(200)
                            .setBody("""{"status":"SUCCESS","results":"","pagination":""}""")
                    }
                    else -> MockResponse().setResponseCode(200).setBody("""{"books":[]}""")
                }
        }
        val source = LibriVoxCatalogSource(rewritingClient())

        listOf(
            CatalogSearchField.Author,
            CatalogSearchField.Reader,
            CatalogSearchField.Chapter,
        ).map { field ->
            async { source.search("Alice", field, 5, 0, "English") }
        }.awaitAll()

        assertEquals(3, nextSession.get())
        assertEquals(
            setOf("PHPSESSID=session-1", "PHPSESSID=session-2", "PHPSESSID=session-3"),
            advancedCookies,
        )
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

    private fun advancedAliceAuthorJson(): String =
        """
        {
          "status":"SUCCESS",
          "results":"<li class=\"catalog-result\"><div class=\"catalog-type\">Title</div><div class=\"result-data\"><h3><a href=\"https://librivox.org/alice-in-wonderland-by-alice-gerstenberg/\">Alice in Wonderland (Drama)</a></h3><p class=\"book-author\"><a href=\"https://librivox.org/author/2770\">Alice Gerstenberg</a></p></div></li>",
          "pagination":""
        }
        """.trimIndent()

    private fun aliceFeedJson(): String =
        """
        {"books":[{"id":"200","title":"Alice's Adventures in Wonderland","language":"English","authors":[{"first_name":"Lewis","last_name":"Carroll"}],"sections":[{"id":"200-1","section_number":"1","title":"Down the Rabbit-Hole","listen_url":"https://example.com/200.mp3"}]}]}
        """.trimIndent()

    private fun aliceGerstenbergFeedJson(): String =
        """
        {"books":[{"id":"alice-drama","title":"Alice in Wonderland (Drama)","language":"English","authors":[{"first_name":"Alice","last_name":"Gerstenberg"}],"sections":[{"id":"alice-drama-1","section_number":"1","title":"Act One","listen_url":"https://example.com/drama.mp3"}]}]}
        """.trimIndent()
}
