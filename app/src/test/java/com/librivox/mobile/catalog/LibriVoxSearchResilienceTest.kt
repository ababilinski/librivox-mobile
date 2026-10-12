package com.librivox.mobile.catalog

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibriVoxSearchResilienceTest {
    @Test
    fun documentedNotFoundResponseIsAnEmptySearchRatherThanAnOutage() = runBlocking {
        val source = source { request ->
            if (request.url.host == "archive.org") 200 to """{"response":{"docs":[]}}"""
            else if (request.url.encodedPath.startsWith("/api/feed/audiobooks")) 404 to """{"error":"Audiobooks could not be found"}"""
            else 500 to "server failure"
        }
        assertTrue(source.search("NoSuchBook", CatalogSearchField.Title, 10, 0, "English").isEmpty())
    }

    @Test
    fun allFieldsRetainsValidMatchesWhenOtherFieldsReturnServerErrors() = runBlocking {
        val source = source { request ->
            if (request.url.queryParameter("title") == "^Alice") 200 to alice
            else 500 to "server failure"
        }
        assertEquals(listOf("alice"), source.search("Alice", CatalogSearchField.All, 10, 0, "English").map { it.id })
    }

    @Test
    fun slowBranchCannotHoldSuccessfulAllFieldsResultsIndefinitely() = runBlocking {
        val source = source(timeoutMillis = 200) { request ->
            if (request.url.queryParameter("title") == "^Alice") 200 to alice
            else {
                Thread.sleep(700)
                500 to "server failure"
            }
        }
        val started = System.nanoTime()
        val books = source.search("Alice", CatalogSearchField.All, 10, 0, "English")
        assertEquals(listOf("alice"), books.map { it.id })
        assertTrue("Slow branches must be cancelled within the search budget", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 600)
    }

    @Test
    fun totalOutageIsReportedAsUnavailableRatherThanNoMatches() = runBlocking {
        val source = source { 500 to "server failure" }
        val failure = runCatching { source.search("Alice", CatalogSearchField.All, 10, 0, "English") }.exceptionOrNull()
        assertTrue(failure is CatalogSourceUnavailableException)
    }

    @Test
    fun parentCancellationIsNotConvertedIntoAnUnavailableCatalog() = runBlocking {
        val source = source { Thread.sleep(500); 200 to alice }
        val failure = runCatching {
            withTimeout(50) { source.search("Alice", CatalogSearchField.All, 10, 0, "English") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    private fun source(timeoutMillis: Long = 1_000, result: (okhttp3.Request) -> Pair<Int, String>): LibriVoxCatalogSource =
        LibriVoxCatalogSource(
            OkHttpClient.Builder().addInterceptor { chain ->
                val (code, body) = result(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message(if (code == 200) "OK" else "Failure")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build(),
            timeoutMillis,
        )

    private val alice = """{"books":[{"id":"alice","title":"Alice's Adventures in Wonderland","language":"English","authors":[{"first_name":"Lewis","last_name":"Carroll"}],"sections":[]}]}"""
}
