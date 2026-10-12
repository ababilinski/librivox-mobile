package com.librivox.mobile.catalog

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class CatalogOperationCacheDatabaseTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DatabaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DatabaseName)
    }

    @Test
    fun versionFourDatabaseMigratesAndInvalidatesDiscoverV2() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DatabaseName), null).use { database ->
            database.execSQL(
                """
                CREATE TABLE discover_result_cache (
                    cache_key TEXT PRIMARY KEY NOT NULL,
                    query TEXT NOT NULL,
                    search_field TEXT NOT NULL,
                    sort_order TEXT NOT NULL,
                    language TEXT NOT NULL,
                    source_preference TEXT NOT NULL,
                    is_default_browse INTEGER NOT NULL,
                    next_offset INTEGER NOT NULL,
                    can_load_more INTEGER NOT NULL,
                    loaded_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO discover_result_cache VALUES(
                    'discover-v2|librivox_only|||Title|alice',
                    'alice', 'Title', 'MostPopular', '', 'librivox_only', 0, 0, 0, 1000
                )
                """.trimIndent(),
            )
            database.version = 4
        }

        val repository = repository()
        val cached = repository.cachedDiscoverState(
            query = "alice",
            field = CatalogSearchField.Title,
            language = "",
            sourcePreference = BookSourcePreference.LibriVoxOnly,
            sortOrder = DiscoverSortOrder.MostPopular,
        )

        assertNull(cached)
        assertEquals(6, readableDatabase().version)
    }

    @Test
    fun completePositiveAndAuthoritativeEmptyRoundTrip() = runBlocking {
        val repository = repository()
        val positiveOperation = titleOperation("alice")
        val emptyOperation = titleOperation("no match")
        repository.write(
            CatalogCachedOperation(
                operation = positiveOperation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(alice()),
                storedAtMillis = 1_000L,
            ),
        )
        repository.write(
            CatalogCachedOperation(
                operation = emptyOperation,
                outcome = CatalogOperationOutcome.AuthoritativeEmpty,
                books = emptyList(),
                storedAtMillis = 1_000L,
            ),
        )

        val positive = repository.read(positiveOperation, 1_001L)
        val empty = repository.read(emptyOperation, 1_001L)
        assertEquals("200", positive?.books?.single()?.id)
        assertEquals(CatalogOperationOutcome.Success(1), positive?.outcome)
        assertEquals(CatalogOperationOutcome.AuthoritativeEmpty, empty?.outcome)
    }

    @Test
    fun failedAndPartialOutcomesNeverOverwriteCompleteEntry() = runBlocking {
        val repository = repository()
        val operation = titleOperation("alice")
        val complete = CatalogCachedOperation(
            operation = operation,
            outcome = CatalogOperationOutcome.Success(1),
            books = listOf(alice()),
            storedAtMillis = 1_000L,
        )
        repository.write(complete)
        repository.write(
            complete.copy(
                outcome = CatalogOperationOutcome.Failed("timeout"),
                books = emptyList(),
                storedAtMillis = 2_000L,
            ),
        )

        val cached = repository.read(operation, 2_001L)
        assertEquals("200", cached?.books?.single()?.id)
        assertEquals(1_000L, cached?.storedAtMillis)
    }

    @Test
    fun corruptedJsonIsDiscardedWithoutCrashing() = runBlocking {
        val repository = repository()
        val operation = titleOperation("alice")
        repository.write(
            CatalogCachedOperation(
                operation = operation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(alice()),
                storedAtMillis = 1_000L,
            ),
        )
        writableDatabase().use { database ->
            database.execSQL(
                "UPDATE catalog_operation_cache SET books_json = '{' WHERE cache_key = ?",
                arrayOf(operation.cacheKey),
            )
        }

        assertNull(repository.read(operation, 1_001L))
        assertEquals(0L, rowCount())
    }

    @Test
    fun deterministicLruKeepsAtMostOneHundredOperationSnapshots() = runBlocking {
        val repository = repository()
        repeat(105) { index ->
            val operation = titleOperation("alice $index")
            repository.write(
                CatalogCachedOperation(
                    operation = operation,
                    outcome = CatalogOperationOutcome.Success(1),
                    books = listOf(alice().copy(id = index.toString())),
                    storedAtMillis = index.toLong(),
                    lastAccessedAtMillis = index.toLong(),
                ),
            )
        }

        assertEquals(100L, rowCount())
        assertNull(repository.read(titleOperation("alice 0"), 105L))
        assertTrue(repository.read(titleOperation("alice 104"), 105L) != null)
    }

    @Test
    fun deterministicLruKeepsAtMostOneHundredAggregateDiscoverSnapshotsAndItems() = runBlocking {
        val repository = repository()
        repeat(105) { index ->
            repository.saveDiscoverState(
                DiscoverCacheState(
                    query = "query $index",
                    searchField = CatalogSearchField.Title,
                    results = listOf(alice().copy(id = "book-$index")),
                    loadedAtMillis = index.toLong(),
                    bookSource = BookSourcePreference.LibriVoxOnly,
                    isComplete = true,
                ),
            )
        }

        readableDatabase().use { database ->
            assertEquals(100L, tableRowCount(database, "discover_result_cache"))
            assertEquals(100L, tableRowCount(database, "discover_result_items"))
            assertEquals(
                0L,
                database.rawQuery(
                    """
                    SELECT COUNT(*)
                    FROM discover_result_items items
                    LEFT JOIN discover_result_cache snapshots
                      ON snapshots.cache_key = items.cache_key
                    WHERE snapshots.cache_key IS NULL
                    """.trimIndent(),
                    emptyArray(),
                ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) },
            )
        }
        assertNull(
            repository.cachedDiscoverState(
                query = "query 0",
                field = CatalogSearchField.Title,
                language = "",
                sourcePreference = BookSourcePreference.LibriVoxOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
        assertNotNull(
            repository.cachedDiscoverState(
                query = "query 104",
                field = CatalogSearchField.Title,
                language = "",
                sourcePreference = BookSourcePreference.LibriVoxOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
    }

    @Test
    fun remoteInterimRowsAreUsableThroughSevenDayBoundaryButNotBeyondIt() = runBlocking {
        val now = 1_000_000_000L
        val repository = CatalogAutocompleteRepository(
            context,
            PlaybackSettingsStore(context),
            nowMillis = { now },
        )
        repository.upsertBooks(
            listOf(alice().copy(id = "inside", title = "Alice Inside Boundary")),
            observedAtMillis = now - CatalogStaleUsableMillis,
        )
        repository.upsertBooks(
            listOf(alice().copy(id = "outside", title = "Alice Outside Boundary")),
            observedAtMillis = now - CatalogStaleUsableMillis - 1L,
        )

        val searchIds = repository.searchBooks(
            query = "Alice",
            field = CatalogSearchField.Title,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        ).map { it.id }
        val browseIds = repository.browseCachedBooks(
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        ).map { it.id }

        assertTrue("inside" in searchIds)
        assertTrue("outside" !in searchIds)
        assertTrue("inside" in browseIds)
        assertTrue("outside" !in browseIds)
    }

    @Test
    fun indexCoveragePersistsIncompleteCompleteAndRetryState() = runBlocking {
        val repository = repository()
        val syncKey = repository.refreshSyncKey(
            com.librivox.mobile.playback.CatalogSourcePreference.LibriVox,
            "English",
        )
        repository.markRefreshStarted(syncKey, nowMillis = 100L)
        repository.markRefreshSucceeded(
            syncKey = syncKey,
            requestedOffset = 0,
            fetchedCount = 24,
            pageSize = 24,
            nowMillis = 200L,
        )
        var coverage = repository.cacheSnapshot().indexCoverage.getValue(syncKey)
        assertEquals(24, coverage.nextOffset)
        assertTrue(!coverage.isComplete)

        repository.markRefreshFailed(syncKey, nowMillis = 250L)
        coverage = repository.cacheSnapshot().indexCoverage.getValue(syncKey)
        assertEquals(1, coverage.failureCount)
        assertEquals(24, coverage.nextOffset)

        repository.markRefreshSucceeded(
            syncKey = syncKey,
            requestedOffset = 24,
            fetchedCount = 7,
            pageSize = 24,
            nowMillis = 300L,
        )
        coverage = repository.cacheSnapshot().indexCoverage.getValue(syncKey)
        assertEquals(0, coverage.nextOffset)
        assertEquals(0, coverage.failureCount)
        assertTrue(coverage.isComplete)
    }

    private fun repository(): CatalogAutocompleteRepository =
        CatalogAutocompleteRepository(context, PlaybackSettingsStore(context))

    private fun titleOperation(query: String): CatalogPlannedOperation =
        CatalogSearchPlanner.plan(
            query = query,
            field = CatalogSearchField.Title,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        ).operations.single()

    private fun alice(): AudioBook = AudioBook(
        id = "200",
        title = "Alice's Adventures in Wonderland",
        author = "Lewis Carroll",
        source = BookSource.LibriVox,
        language = "English",
    )

    private fun readableDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(context.getDatabasePath(DatabaseName).path, null, SQLiteDatabase.OPEN_READONLY)

    private fun writableDatabase(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(context.getDatabasePath(DatabaseName).path, null, SQLiteDatabase.OPEN_READWRITE)

    private fun rowCount(): Long = readableDatabase().use { database ->
        database.rawQuery("SELECT COUNT(*) FROM catalog_operation_cache", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
    }

    private fun tableRowCount(database: SQLiteDatabase, table: String): Long =
        database.rawQuery("SELECT COUNT(*) FROM $table", emptyArray()).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private companion object {
        const val DatabaseName = "catalog_autocomplete.db"
    }
}
