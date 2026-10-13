package com.librivox.mobile.catalog

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.ProgressStore
import com.librivox.mobile.model.sanitized
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class RetiredCatalogCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun retiredSelectionsAndIdsNeverDispatchCatalogRequests() = runBlocking {
        val settings = legacySettings()
        val remaining = RecordingCatalog()
        val client = AggregateCatalogClient(remaining, remaining, remaining, settings)
        assertEquals(BookSourcePreference.None, settings.bookSourcePreference)
        assertTrue(client.fetchByIds("gutendex-20687").isEmpty())
        assertTrue(client.browseSource(CatalogSourcePreference.Gutendex, 10, 0, "").isEmpty())
        assertTrue(client.searchSource(CatalogSourcePreference.Gutendex, "Pride", CatalogSearchField.Title, 10, 0, "").isEmpty())
        assertTrue(client.featuredBooksSourceOrThrow(CatalogSourcePreference.Gutendex).isEmpty())
        client.featuredBooks()
        client.browse(10, 0, "")
        client.search("Pride", CatalogSearchField.All, 10, 0, "")
        client.recent(0, 10)
        client.byAuthor("Austen", 10, "")
        client.byGenre("Fiction", 10, "")
        assertTrue(remaining.calls.isEmpty())

        client.fetchByIds("gutendex-20687", "200")
        assertEquals(listOf("ids:200"), remaining.calls)
    }

    @Test fun retiredHealthChecksDoNotUseTheTransport() = runBlocking {
        val probe = CatalogSourceHealthProbe(transport = CatalogHttpTransport {
            error("A retired source must not make a health request")
        })
        assertTrue(probe.probe(listOf(CatalogSourcePreference.Gutendex)).isEmpty())
        assertEquals(CatalogSourceHealth.Broken, probe.probeSource(CatalogSourcePreference.Gutendex).state)
        assertFalse(DefaultCatalogSourceHealthEndpoints.containsKey(CatalogSourcePreference.Gutendex))
    }

    @Test fun savedLibraryDownloadBookmarkAndProgressIdentifiersSurviveMigration() {
        val saved = decodedLegacyBook()
        assertEquals(BookSource.Gutendex, saved.source)
        assertEquals(LibraryStatus.InLibrary, saved.libraryStatus)
        assertEquals("gutendex-20687", saved.id)
        assertEquals("https://www.gutenberg.org/ebooks/20687", saved.gutenbergUrl)
        assertEquals("gutenberg-chapter.mp3", saved.chapters.single().localFileName)
        assertEquals(DownloadState.Downloaded, saved.chapters.single().downloadState)
        val bookmark = Json.decodeFromString<Bookmark>("""
            {"id":"saved-mark","bookId":"gutendex-20687","chapterId":"gutendex-20687-audio",
             "positionMs":14000,"note":"Keep this passage","createdAtMillis":1}
        """.trimIndent())
        val progress = ProgressStore(context)
        progress.savePosition(saved.id, saved.chapters.single().id, 0, bookmark.positionMs)
        val migrated = legacySettings()
        migrated.saveBookSourcePreference(migrated.bookSourcePreference)
        assertEquals(BookSourcePreference.None, PlaybackSettingsStore(context).bookSourcePreference)
        assertEquals(saved.id, bookmark.bookId)
        assertEquals(saved.chapters.single().id, bookmark.chapterId)
        assertEquals(bookmark.positionMs, ProgressStore(context).savedPositionMs(saved.id, bookmark.chapterId))
        assertEquals(bookmark.chapterId, ProgressStore(context).lastChapterId(saved.id))
    }

    @Test fun gutendexOnlyMigrationStillFindsSavedBooksWithoutAnyRemoteCall() = runBlocking {
        assertSavedBookSearch(legacySettings(), expectedCalls = 0)
    }

    @Test fun successfulEmptyActiveCatalogResponseDoesNotEraseSavedGutenbergMatch() = runBlocking {
        val settings = legacySettings().apply { saveBookSourcePreference(BookSourcePreference.LibriVoxOnly) }
        assertSavedBookSearch(settings, expectedCalls = 1)
    }

    private suspend fun assertSavedBookSearch(settings: PlaybackSettingsStore, expectedCalls: Int) {
        settings.saveAutomaticSearchCachingEnabled(false)
        val library = LibraryRepository(context)
        library.addToLibrary(decodedLegacyBook())
        val source = RecordingCatalog()
        val job = SupervisorJob()
        val repository = DiscoverRepository(
            scope = CoroutineScope(job + Dispatchers.Default),
            catalogClient = source,
            catalogCache = CatalogAutocompleteRepository(context, settings),
            libraryRepository = library,
            settings = settings,
        )
        try {
            repository.loadDiscoverPage(DiscoverLoadRequest(
                query = "Pride",
                field = CatalogSearchField.Title,
                languages = listOf(""),
                languageKey = "",
                sourcePreference = settings.bookSourcePreference,
                sortOrder = DiscoverSortOrder.Alphabetical,
                reset = true,
                offset = 0,
            ))
            val state = withTimeout(10_000) {
                repository.discoverCache.first { it.query == "Pride" && it.isComplete && !it.isLoading }
            }
            assertTrue(state.results.any { it.id == "gutendex-20687" })
            assertEquals(expectedCalls, source.calls.size)
            assertEquals(null, state.errorMessage)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun legacySettings(): PlaybackSettingsStore {
        context.getSharedPreferences(PlaybackSettingsStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(PlaybackSettingsStore.KEY_BOOK_SOURCE, "gutendex_only").commit()
        return PlaybackSettingsStore(context)
    }

    private fun decodedLegacyBook(): AudioBook = Json.decodeFromString<LibraryState>("""
        {"books":[{"id":"gutendex-20687","title":"Pride and Prejudice","author":"Jane Austen",
          "source":"Gutendex","libraryStatus":"InLibrary","language":"English",
          "gutenbergUrl":"https://www.gutenberg.org/ebooks/20687",
          "chapters":[{"id":"gutendex-20687-audio","title":"Opening","number":1,
            "listenUrl":"https://www.gutenberg.org/files/20687/mp3/20687-01.mp3",
            "localFileName":"gutenberg-chapter.mp3","downloadState":"Downloaded"}]}]}
    """.trimIndent()).sanitized().books.single()

    private class RecordingCatalog : CatalogSource {
        val calls = CopyOnWriteArrayList<String>()
        private fun record(call: String): List<AudioBook> { calls += call; return emptyList() }
        override suspend fun featuredBooks() = record("featured")
        override suspend fun fetchByIds(vararg ids: String) = record("ids:${ids.joinToString()}")
        override suspend fun search(query: String, field: CatalogSearchField, limit: Int, offset: Int, language: String) = record("search")
        override suspend fun browse(limit: Int, offset: Int, language: String) = record("browse")
        override suspend fun recent(sinceEpochSeconds: Long, limit: Int) = record("recent")
        override suspend fun byGenre(genre: String, limit: Int, language: String) = record("genre")
        override suspend fun byAuthor(author: String, limit: Int, language: String) = record("author")
    }
}
