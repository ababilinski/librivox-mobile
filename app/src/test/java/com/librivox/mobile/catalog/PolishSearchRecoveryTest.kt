package com.librivox.mobile.catalog

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolishSearchRecoveryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun titleAndAllSearchesFindLalkaAndAntekWithoutLoadingPopularityOrDetails() = runBlocking {
        val indexRequests = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("/api/parent_books/", request.requestUrl?.encodedPath)
                indexRequests.incrementAndGet()
                return MockResponse().setBody(index)
            }
        }
        val source = WolneLekturyCatalogSource(client())
        for (field in listOf(CatalogSearchField.All, CatalogSearchField.Title)) {
            for (title in listOf("Lalka", "Antek")) {
                val books = source.search(title, field, 50, 0, "Polish")
                assertEquals("wolnelektury-${title.lowercase()}", books.single().id)
            }
        }
        assertEquals(1, indexRequests.get())
    }

    @Test fun switchingFromAnUnfinishedLibriVoxSearchStartsPolishSearchImmediately() = runBlocking {
        val firstRequest = CompletableDeferred<Unit>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl?.encodedPath == "/api/parent_books/") {
                    return MockResponse().setBody(index)
                }
                firstRequest.complete(Unit)
                return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }
        val httpClient = client()
        val previous = async {
            LibriVoxCatalogSource(httpClient).search("Lalka", CatalogSearchField.All, 50, 0, "")
        }
        withTimeout(5_000) { firstRequest.await() }
        withTimeout(2_000) { previous.cancelAndJoin() }
        val source = WolneLekturyCatalogSource(httpClient)
        for (title in listOf("Lalka", "Antek")) {
            val books = withTimeout(2_000) { source.search(title, CatalogSearchField.Title, 50, 0, "Polish") }
            assertTrue(books.any { it.title == title })
        }
    }

    @Test fun concurrentPolishFieldSearchesShareOneIndexFetch() = runBlocking {
        server.enqueue(MockResponse().setBody(index))
        val source = WolneLekturyCatalogSource(client())
        val lalka = async { source.search("Lalka", CatalogSearchField.Title, 50, 0, "Polish") }
        val antek = async { source.search("Antek", CatalogSearchField.All, 50, 0, "Polish") }
        assertEquals("Lalka", lalka.await().single().title)
        assertEquals("Antek", antek.await().single().title)
        assertEquals(1, server.requestCount)
    }

    @Test fun browsingNeverCrawlsTheRemainingPopularityPages() = runBlocking {
        server.enqueue(MockResponse().setBody(index))
        server.enqueue(MockResponse().setBody("""
            <article class="l-books__item"><h2><a href="/katalog/lektura/lalka/">Lalka</a></h2></article>
            <div id="paginator"><a href="?page=16">16</a></div>
        """.trimIndent()))
        val books = withTimeout(2_000) { WolneLekturyCatalogSource(client()).browse(50, 0, "Polish") }
        assertEquals(setOf("Lalka", "Antek"), books.map { it.title }.toSet())
        assertEquals(2, server.requestCount)
    }

    private fun client(): OkHttpClient {
        val destination = server.url("/")
        return OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.newBuilder().scheme(destination.scheme)
                .host(destination.host).port(destination.port).build()
            chain.proceed(request.newBuilder().url(url).build())
        }.build()
    }

    private val index = """
        [
          {"slug":"lalka","title":"Lalka","author":"Bolesław Prus","has_audio":true},
          {"slug":"antek","title":"Antek","author":"Bolesław Prus","has_audio":true}
        ]
    """.trimIndent()
}
