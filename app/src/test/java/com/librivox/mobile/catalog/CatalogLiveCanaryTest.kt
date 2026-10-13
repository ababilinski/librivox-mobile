package com.librivox.mobile.catalog

import com.librivox.mobile.playback.CatalogSourcePreference
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Opt-in live checks for upstream contract drift. Deterministic fixture tests remain the PR gate;
 * run these manually or nightly with RUN_LIVE_CATALOG_TESTS=1.
 */
class CatalogLiveCanaryTest {
    @Before
    fun requireLiveOptIn() {
        assumeTrue(System.getenv("RUN_LIVE_CATALOG_TESTS") == "1")
    }

    @Test
    fun librivoxAliceTitleStillIncludesCanonicalId200() = runBlocking {
        val books = withLiveClient { client ->
            LibriVoxCatalogSource(client).search(
                query = "alice ",
                field = CatalogSearchField.Title,
                limit = 50,
                offset = 0,
                language = "",
            )
        }

        assertTrue(books.any { it.id == "200" })
    }

    @Test
    fun librivoxPartialAuthorReaderAndChapterSearchesResolve() = runBlocking {
        val (author, reader, chapter) = withLiveClient { client ->
            val source = LibriVoxCatalogSource(client)
            coroutineScope {
                val author = async { source.search("Alice", CatalogSearchField.Author, 5, 0, "") }
                val reader = async { source.search("Alice", CatalogSearchField.Reader, 5, 0, "") }
                val chapter = async {
                    source.search("Advice from a Caterpillar", CatalogSearchField.Chapter, 5, 0, "")
                }
                Triple(author.await(), reader.await(), chapter.await())
            }
        }

        assertTrue(author.isNotEmpty())
        assertTrue(reader.isNotEmpty())
        assertTrue(chapter.isNotEmpty())
    }

    @Test
    fun wolneFindsAntek() = runBlocking {
        val books = withLiveClient { client ->
            WolneLekturyCatalogSource(client).search(
                query = "Antek",
                field = CatalogSearchField.All,
                limit = 20,
                offset = 0,
                language = "Polish",
            )
        }

        assertTrue(books.any { normalizedCatalogSearchText(it.title).contains("antek") })
    }

    @Test
    fun wolneFindsLalkaAndAntekInTitleAndAllFields() = runBlocking {
        withLiveClient { client ->
            val source = WolneLekturyCatalogSource(client)
            for (field in listOf(CatalogSearchField.All, CatalogSearchField.Title)) {
                for (title in listOf("Lalka", "Antek")) {
                    val books = source.search(title, field, 50, 0, "Polish")
                    assertTrue("$field search must find $title", books.any { it.id == "wolnelektury-${title.lowercase()}" })
                }
            }
        }
    }

    @Test
    fun lit2GoAliceEitherParsesOrReportsUnavailable() = runBlocking {
        val result = runCatching {
            withLiveClient { client ->
                Lit2GoCatalogSource(client).search(
                    query = "Alice",
                    field = CatalogSearchField.All,
                    limit = 20,
                    offset = 0,
                    language = "English",
                )
            }
        }

        result.onSuccess { assertTrue(it.isNotEmpty()) }
        result.onFailure { assertEquals(CatalogSourceUnavailableException::class.java, it.javaClass) }
        Unit
    }

    @Test
    fun selectedCatalogRootPagesHaveValidBrandedTitles() = runBlocking {
        val statuses = withLiveClient { client ->
            CatalogSourceHealthProbe(
                transport = OkHttpCatalogHttpTransport(client),
            ).probe(CatalogSourcePreference.ActiveSources)
        }

        val failures = statuses.values
            .filterNot { it.state == CatalogSourceHealth.Available }
            .joinToString { status ->
                "${status.source.label}: ${status.state} (${status.message.orEmpty()})"
            }
        assertTrue("Catalog root health failures: $failures", failures.isEmpty())
    }

    private suspend fun <T> withLiveClient(block: suspend (OkHttpClient) -> T): T {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(65, TimeUnit.SECONDS)
            .build()
        return try {
            block(client)
        } finally {
            client.dispatcher.cancelAll()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
