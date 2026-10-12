package com.librivox.mobile.catalog

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WolneLekturyIndexCacheTest {
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
    fun indexCacheExpiresAfterThirtyMinutesSoCoordinatorRevalidationUsesNetwork() = runBlocking {
        val indexRequests = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.requestUrl?.encodedPath.orEmpty()
                return when (path) {
                    "/api/parent_books/" -> {
                        indexRequests.incrementAndGet()
                        MockResponse().setResponseCode(200).setBody(fixture("wolne/index-antek.json"))
                    }
                    "/katalog/audiobooki/" ->
                        MockResponse().setResponseCode(200).setBody("<html><body></body></html>")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        var now = 1L
        val source = WolneLekturyCatalogSource(rewritingClient(), nowMillis = { now })

        source.search("Antek", CatalogSearchField.Title, 10, 0, "Polish")
        now += CatalogPositiveFreshMillis
        source.search("Antek", CatalogSearchField.Title, 10, 0, "Polish")
        assertEquals(1, indexRequests.get())

        now += 1L
        source.search("Antek", CatalogSearchField.Title, 10, 0, "Polish")
        assertEquals(2, indexRequests.get())
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
}
