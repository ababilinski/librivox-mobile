package com.librivox.mobile.catalog

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DiscoverRepositoryOwnershipTest {
    @Test fun delayedSuccessAndFailureCannotReplaceTheLatestRepeatedQuery() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.repository.loadDiscoverPage(request("Prus"))
            val firstA = fixture.source.next()
            fixture.repository.loadDiscoverPage(request("Dickens"))
            val b = fixture.source.next()
            fixture.repository.loadDiscoverPage(request("Prus"))
            val currentA = fixture.source.next()
            currentA.result.complete(Result.success(listOf(book("latest", "Prus"))))
            withTimeout(10_000) {
                fixture.repository.discoverCache.first { it.isComplete && it.results.any { book -> book.id == "latest" } }
            }
            firstA.result.complete(Result.success(listOf(book("obsolete", "Prus"))))
            b.result.complete(Result.failure(IllegalStateException("old request failed")))
            firstA.finished.await()
            b.finished.await()
            // Give canceled callers a dispatcher turn after their non-cooperative source returns.
            delay(100)
            val state = fixture.repository.discoverCache.value
            assertEquals("Prus", state.query)
            assertEquals(CatalogSearchField.Author, state.searchField)
            assertEquals(listOf("latest"), state.results.map { it.id })
            assertFalse(state.isLoading)
            assertEquals(null, state.errorMessage)
        } finally {
            fixture.close()
        }
    }

    @Test fun clearingSearchRevokesAnInFlightRequestsRightToPublish() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.repository.loadDiscoverPage(request("Prus"))
            val pending = fixture.source.next()
            fixture.repository.clearDiscoverState()
            pending.result.complete(Result.success(listOf(book("obsolete", "Prus"))))
            pending.finished.await()
            delay(100)
            assertEquals(DiscoverCacheState(), fixture.repository.discoverCache.value)
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val settings = PlaybackSettingsStore(context).apply { saveAutomaticSearchCachingEnabled(false) }
        private val job = SupervisorJob()
        val source = DelayedCatalog()
        val repository = DiscoverRepository(
            scope = CoroutineScope(job + Dispatchers.Default),
            catalogClient = source,
            catalogCache = CatalogAutocompleteRepository(context, settings),
            libraryRepository = LibraryRepository(context),
            settings = settings,
        )

        suspend fun close() {
            source.pending.forEach { it.result.complete(Result.success(emptyList())) }
            job.cancelAndJoin()
        }
    }

    private class Pending {
        val result = CompletableDeferred<Result<List<AudioBook>>>()
        val finished = CompletableDeferred<Unit>()
    }

    private class DelayedCatalog : CatalogSource {
        private val requests = Channel<Pending>(Channel.UNLIMITED)
        val pending = CopyOnWriteArrayList<Pending>()
        suspend fun next(): Pending = withTimeout(10_000) { requests.receive() }

        override suspend fun search(query: String, field: CatalogSearchField, limit: Int, offset: Int, language: String): List<AudioBook> {
            val request = Pending().also(pending::add)
            requests.send(request)
            // Simulate a source which completes despite cancellation; publication still belongs
            // exclusively to the current repository request, including an A -> B -> A sequence.
            return withContext(NonCancellable) {
                try {
                    request.result.await().getOrThrow()
                } finally {
                    request.finished.complete(Unit)
                }
            }
        }

        override suspend fun featuredBooks() = emptyList<AudioBook>()
        override suspend fun fetchByIds(vararg ids: String) = emptyList<AudioBook>()
        override suspend fun browse(limit: Int, offset: Int, language: String) = emptyList<AudioBook>()
        override suspend fun recent(sinceEpochSeconds: Long, limit: Int) = emptyList<AudioBook>()
        override suspend fun byGenre(genre: String, limit: Int, language: String) = emptyList<AudioBook>()
        override suspend fun byAuthor(author: String, limit: Int, language: String) = emptyList<AudioBook>()
    }

    companion object {
        private fun request(query: String) = DiscoverLoadRequest(
            query = query,
            field = CatalogSearchField.Author,
            languages = listOf(""),
            languageKey = "",
            sourcePreference = BookSourcePreference.LibriVoxOnly,
            sortOrder = DiscoverSortOrder.Alphabetical,
            reset = true,
            offset = 0,
        )

        private fun book(id: String, author: String) = AudioBook(
            id = id,
            title = "A book by $author",
            author = author,
            chapters = listOf(AudioBookChapter("$id-1", "Chapter 1", 1, listenUrl = "https://example.test/$id.mp3")),
        )
    }
}
