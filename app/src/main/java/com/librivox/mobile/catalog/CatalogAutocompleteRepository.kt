package com.librivox.mobile.catalog

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.model.FallbackCover
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.isPlayable
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogAutocompleteRepository(
    context: Context,
    private val settings: PlaybackSettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val helper = CatalogAutocompleteDatabase(context.applicationContext).apply {
        setWriteAheadLoggingEnabled(true)
    }
    private val bookRevision = MutableStateFlow(0)
    private val snapshotRevision = MutableStateFlow(0)

    fun suggestions(
        query: String,
        field: CatalogSearchField = CatalogSearchField.All,
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        limit: Int = DefaultSuggestionLimit,
    ): Flow<List<AudioBook>> {
        val normalizedQuery = normalizeForAutocomplete(query)
        if (normalizedQuery.isBlank()) return flowOf(emptyList())
        val safeLimit = limit.coerceIn(1, MaxSuggestionLimit)
        return bookRevision
            .map {
                if (!settings.automaticSearchCachingEnabled) {
                    emptyList()
                } else {
                    queryCachedBooks(
                        normalizedQuery = normalizedQuery,
                        field = field,
                        languages = languages,
                        sourcePreference = sourcePreference,
                        limit = safeLimit,
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun cacheSnapshots(): Flow<CatalogAutocompleteCacheSnapshot> =
        snapshotRevision
            .map { cacheSnapshot() }
            .flowOn(Dispatchers.IO)

    suspend fun searchBooks(
        query: String,
        field: CatalogSearchField,
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        limit: Int = DefaultSearchResultLimit,
    ): List<AudioBook> {
        val normalizedQuery = normalizeForAutocomplete(query)
        if (normalizedQuery.isBlank()) return emptyList()
        if (!settings.automaticSearchCachingEnabled) return emptyList()
        val safeLimit = limit.coerceIn(1, MaxSearchResultLimit)
        return withContext(Dispatchers.IO) {
            queryCachedBooks(
                normalizedQuery = normalizedQuery,
                field = field,
                languages = languages,
                sourcePreference = sourcePreference,
                limit = safeLimit,
            )
        }
    }

    suspend fun browseCachedBooks(
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        limit: Int = DefaultSearchResultLimit,
        offset: Int = 0,
    ): List<AudioBook> {
        val sourceNames = sourcePreference.autocompleteSourceNames()
        if (sourceNames.isEmpty()) return emptyList()
        if (!settings.automaticSearchCachingEnabled) return emptyList()
        val safeLimit = limit.coerceIn(1, MaxSearchResultLimit)
        val safeOffset = offset.coerceAtLeast(0)
        return withContext(Dispatchers.IO) {
            queryCachedBrowseBooks(
                languages = languages,
                sourcePreference = sourcePreference,
                limit = safeLimit,
                offset = safeOffset,
            )
        }
    }

    suspend fun cachedDiscoverState(
        query: String,
        field: CatalogSearchField,
        language: String,
        sourcePreference: BookSourcePreference,
        sortOrder: DiscoverSortOrder,
    ): DiscoverCacheState? =
        if (!settings.automaticSearchCachingEnabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                readDiscoverState(
                    key = discoverResultCacheKey(
                        query = query,
                        field = field,
                        language = language,
                        sourcePreference = sourcePreference,
                        sortOrder = sortOrder,
                    ),
                )
            }
        }

    suspend fun saveDiscoverState(state: DiscoverCacheState) {
        if (!settings.automaticSearchCachingEnabled) return
        val key = discoverResultCacheKey(
            query = state.query,
            field = state.searchField,
            language = state.language,
            sourcePreference = state.bookSource,
            sortOrder = state.sortOrder,
        )
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            ensurePersistentCacheTables(database)
            database.beginTransaction()
            try {
                upsertBooksInTransaction(
                    database = database,
                    books = state.results,
                    observedAtMillis = state.loadedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    rankOffset = 0,
                )
                database.insertWithOnConflict(
                    DiscoverResultCacheTable,
                    null,
                    ContentValues().apply {
                        put(DiscoverCacheKey, key)
                        put(DiscoverQuery, state.query)
                        put(DiscoverSearchField, state.searchField.name)
                        put(DiscoverSortOrderColumn, state.sortOrder.name)
                        put(DiscoverLanguage, state.language)
                        put(DiscoverSourcePreference, state.bookSource.preferenceValue)
                        put(DiscoverIsDefaultBrowse, state.isDefaultBrowse.toInt())
                        put(DiscoverNextOffset, state.nextOffset)
                        put(DiscoverCanLoadMore, state.canLoadMore.toInt())
                        put(DiscoverLoadedAt, state.loadedAtMillis)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                database.delete(DiscoverResultItemsTable, "$DiscoverCacheKey = ?", arrayOf(key))
                state.results.forEachIndexed { index, book ->
                    database.insertWithOnConflict(
                        DiscoverResultItemsTable,
                        null,
                        ContentValues().apply {
                            put(DiscoverCacheKey, key)
                            put(DiscoverBookId, book.id)
                            put(DiscoverPosition, index)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            bookRevision.update { it + 1 }
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun cachedDailyFeaturedState(
        dayKey: Long,
        sourcePreference: BookSourcePreference,
    ): DailyFeaturedCacheState? =
        if (!settings.automaticSearchCachingEnabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                readDailyFeaturedState(dailyFeaturedCacheKey(dayKey, sourcePreference))
            }
        }

    suspend fun saveDailyFeaturedState(state: DailyFeaturedCacheState) {
        if (!settings.automaticSearchCachingEnabled) return
        val key = dailyFeaturedCacheKey(state.dayKey, state.bookSource)
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            ensurePersistentCacheTables(database)
            database.beginTransaction()
            try {
                upsertBooksInTransaction(
                    database = database,
                    books = state.books,
                    observedAtMillis = state.loadedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    rankOffset = 0,
                )
                database.insertWithOnConflict(
                    DailyFeaturedCacheTable,
                    null,
                    ContentValues().apply {
                        put(DailyFeaturedCacheKey, key)
                        put(DailyFeaturedDayKey, state.dayKey)
                        put(DailyFeaturedSourcePreference, state.bookSource.preferenceValue)
                        put(DailyFeaturedSource, state.featuredSource?.name.orEmpty())
                        put(DailyFeaturedLoadedAt, state.loadedAtMillis)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                database.delete(DailyFeaturedItemsTable, "$DailyFeaturedCacheKey = ?", arrayOf(key))
                state.books.forEachIndexed { index, book ->
                    database.insertWithOnConflict(
                        DailyFeaturedItemsTable,
                        null,
                        ContentValues().apply {
                            put(DailyFeaturedCacheKey, key)
                            put(DailyFeaturedBookId, book.id)
                            put(DailyFeaturedPosition, index)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            bookRevision.update { it + 1 }
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun cacheSnapshot(): CatalogAutocompleteCacheSnapshot =
        withContext(Dispatchers.IO) {
            val database = helper.readableDatabase
            val sourceCounts = linkedMapOf<BookSource, Int>()
            val sourceDataBytes = linkedMapOf<BookSource, Long>()
            database.rawQuery(
                """
                SELECT
                    $BookSourceColumn,
                    COUNT(*),
                    COALESCE(SUM(
                        LENGTH($BookId) +
                        LENGTH($BookTitle) +
                        LENGTH($BookAuthor) +
                        LENGTH($BookDescription) +
                        LENGTH(COALESCE($BookLanguage, '')) +
                        LENGTH(COALESCE($BookCoverImageUrl, '')) +
                        LENGTH(COALESCE($BookLibrivoxUrl, '')) +
                        LENGTH(COALESCE($BookLit2GoUrl, '')) +
                        LENGTH(COALESCE($BookWolneLekturyUrl, '')) +
                        LENGTH(COALESCE($BookGutenbergUrl, '')) +
                        LENGTH($BookNarratorsJson) +
                        LENGTH($BookGenresJson) +
                        LENGTH($BookAuthorTagsJson) +
                        LENGTH($BookLiteraryEpochsJson) +
                        LENGTH($BookLiteraryKindsJson) +
                        LENGTH($BookLiteraryGenresJson) +
                        LENGTH($BookTitleSearchText) +
                        LENGTH($BookChapterSearchText) +
                        LENGTH($BookAuthorSearchText) +
                        LENGTH($BookReaderSearchText) +
                        LENGTH($BookSearchText) +
                        LENGTH($BookMatchKey)
                    ), 0)
                FROM $BooksTable
                GROUP BY $BookSourceColumn
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val source = sourceFromName(cursor.getString(0))
                    sourceCounts[source] = cursor.getInt(1)
                    sourceDataBytes[source] = cursor.getLong(2)
                }
            }
            val sizeBytes = databaseFiles().sumOf { file -> file.lengthIfExists() }
            val sourceSizeBytes = sourceSizeEstimates(
                totalSizeBytes = sizeBytes,
                sourceCounts = sourceCounts,
                sourceDataBytes = sourceDataBytes,
            )
            val syncSnapshot = database.rawQuery(
                """
                SELECT
                    COUNT(*),
                    COALESCE(MAX($SyncLastStartedAt), 0),
                    COALESCE(MAX($SyncLastCompletedAt), 0),
                    COALESCE(SUM($SyncFailureCount), 0)
                FROM $SyncTable
                """.trimIndent(),
                emptyArray(),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    SyncSnapshot(
                        entryCount = cursor.getInt(0),
                        lastStartedAt = cursor.getLong(1),
                        lastCompletedAt = cursor.getLong(2),
                        failureCount = cursor.getInt(3),
                    )
                } else {
                    SyncSnapshot()
                }
            }
            CatalogAutocompleteCacheSnapshot(
                bookCount = queryLong(database, "SELECT COUNT(*) FROM $BooksTable").toInt(),
                sourceCounts = sourceCounts,
                syncEntryCount = syncSnapshot.entryCount,
                lastStartedAtMillis = syncSnapshot.lastStartedAt,
                lastCompletedAtMillis = syncSnapshot.lastCompletedAt,
                failureCount = syncSnapshot.failureCount,
                sizeBytes = sizeBytes,
                sourceSizeBytes = sourceSizeBytes,
            )
        }

    suspend fun clearCache(source: BookSource? = null) {
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            ensurePersistentCacheTables(database)
            database.beginTransaction()
            try {
                database.delete(DiscoverResultItemsTable, null, null)
                database.delete(DiscoverResultCacheTable, null, null)
                database.delete(DailyFeaturedItemsTable, null, null)
                database.delete(DailyFeaturedCacheTable, null, null)
                if (source == null) {
                    database.delete(BookSearchTable, null, null)
                    database.delete(BooksTable, null, null)
                    database.delete(SyncTable, null, null)
                } else {
                    database.execSQL(
                        """
                        DELETE FROM $BookSearchTable
                        WHERE $BookId IN (
                            SELECT $BookId FROM $BooksTable WHERE $BookSourceColumn = ?
                        )
                        """.trimIndent(),
                        arrayOf(source.name),
                    )
                    database.delete(BooksTable, "$BookSourceColumn = ?", arrayOf(source.name))
                    database.delete(SyncTable, "$SyncKey LIKE ?", arrayOf("%:${source.name.lowercase(Locale.ROOT)}:%"))
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            compactDatabase(database)
            bookRevision.update { it + 1 }
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun enforceSizeLimit(maxBytes: Long) {
        if (maxBytes <= 0L) return
        if (!settings.automaticSearchCachingEnabled) return
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            var currentSize = databaseFiles().sumOf { file -> file.lengthIfExists() }
            var pass = 0
            while (currentSize > maxBytes && pass < MaxPrunePasses) {
                val bookCount = queryLong(database, "SELECT COUNT(*) FROM $BooksTable").toInt()
                if (bookCount <= 0) break
                val targetRows = ((bookCount.toDouble() * maxBytes.toDouble()) / currentSize.toDouble())
                    .toInt()
                    .coerceIn(0, bookCount - 1)
                val rowsToDelete = (bookCount - targetRows).coerceAtLeast(MinPruneRows)
                val idsToDelete = database.rawQuery(
                    """
                    SELECT $BookId
                    FROM $BooksTable
                    ORDER BY $BookLastSeenAt ASC, $BookPopularityRank DESC
                    LIMIT $rowsToDelete
                    """.trimIndent(),
                    emptyArray(),
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0))
                        }
                    }
                }
                if (idsToDelete.isEmpty()) break
                idsToDelete.chunked(SqliteMaxVariableNumber).forEach { chunk ->
                    val placeholders = chunk.joinToString(",") { "?" }
                    database.delete(BookSearchTable, "$BookId IN ($placeholders)", chunk.toTypedArray())
                    database.delete(BooksTable, "$BookId IN ($placeholders)", chunk.toTypedArray())
                }
                compactDatabase(database)
                currentSize = databaseFiles().sumOf { file -> file.lengthIfExists() }
                pass++
            }
            bookRevision.update { it + 1 }
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun upsertBooks(
        books: List<AudioBook>,
        observedAtMillis: Long = System.currentTimeMillis(),
        rankOffset: Int = 0,
    ) {
        if (!settings.automaticSearchCachingEnabled) return
        val catalogBooks = books
            .filter { it.source in AutocompleteSources && it.id.isNotBlank() && it.title.isNotBlank() }
            .filter { it.isPlayableSearchResult() }
            .distinctBy { it.id }
        if (catalogBooks.isEmpty()) return
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            database.beginTransaction()
            try {
                upsertBooksInTransaction(database, catalogBooks, observedAtMillis, rankOffset)
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            bookRevision.update { it + 1 }
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun nextRefreshOffset(syncKey: String): Int =
        withContext(Dispatchers.IO) {
            readSyncState(helper.readableDatabase, syncKey)?.nextOffset ?: 0
        }

    suspend fun lastRefreshCompletedAt(): Long =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.rawQuery(
                "SELECT MAX($SyncLastCompletedAt) FROM $SyncTable",
                emptyArray(),
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            }
        }

    suspend fun markRefreshStarted(syncKey: String, nowMillis: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            val current = readSyncState(database, syncKey)
            replaceSyncState(
                database = database,
                state = SyncState(
                    syncKey = syncKey,
                    nextOffset = current?.nextOffset ?: 0,
                    lastStartedAt = nowMillis,
                    lastCompletedAt = current?.lastCompletedAt ?: 0L,
                    failureCount = current?.failureCount ?: 0,
                ),
            )
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun markRefreshSucceeded(
        syncKey: String,
        requestedOffset: Int,
        fetchedCount: Int,
        pageSize: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            val current = readSyncState(database, syncKey)
            replaceSyncState(
                database = database,
                state = SyncState(
                    syncKey = syncKey,
                    nextOffset = if (fetchedCount >= pageSize && fetchedCount > 0) {
                        requestedOffset + pageSize
                    } else {
                        0
                    },
                    lastStartedAt = current?.lastStartedAt ?: nowMillis,
                    lastCompletedAt = nowMillis,
                    failureCount = 0,
                ),
            )
            snapshotRevision.update { it + 1 }
        }
    }

    suspend fun markRefreshFailed(syncKey: String, nowMillis: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            val database = helper.writableDatabase
            val current = readSyncState(database, syncKey)
            replaceSyncState(
                database = database,
                state = SyncState(
                    syncKey = syncKey,
                    nextOffset = current?.nextOffset ?: 0,
                    lastStartedAt = current?.lastStartedAt ?: nowMillis,
                    lastCompletedAt = current?.lastCompletedAt ?: 0L,
                    failureCount = (current?.failureCount ?: 0) + 1,
                ),
            )
            snapshotRevision.update { it + 1 }
        }
    }

    fun refreshSyncKey(sourcePreference: BookSourcePreference, language: String): String =
        "${sourcePreference.preferenceValue}:${language.lowercase(Locale.ROOT)}"

    fun refreshSyncKey(source: CatalogSourcePreference, language: String): String =
        "search:${source.name.lowercase(Locale.ROOT)}:${language.lowercase(Locale.ROOT)}"

    private fun queryCachedBooks(
        normalizedQuery: String,
        field: CatalogSearchField,
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        limit: Int,
    ): List<AudioBook> {
        val sourceNames = sourcePreference.autocompleteSourceNames()
        if (sourceNames.isEmpty()) return emptyList()
        val ftsQuery = autocompleteFtsPrefixQuery(normalizedQuery) ?: return emptyList()
        val ftsColumn = when (field) {
            CatalogSearchField.Title -> BookTitleSearchText
            CatalogSearchField.Chapter -> BookChapterSearchText
            CatalogSearchField.Author -> BookAuthorSearchText
            CatalogSearchField.Reader -> BookReaderSearchText
            CatalogSearchField.All,
            CatalogSearchField.Genre,
            CatalogSearchField.Epoch,
            CatalogSearchField.Kind -> BookSearchText
        }
        val languageFilters = languages
            .filter { it.isNotBlank() }
            .map { normalizeForAutocomplete(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        where += "idx.$ftsColumn MATCH ?"
        args += ftsQuery
        where += "books.$BookSourceColumn IN (${sourceNames.joinToString(",") { "?" }})"
        args += sourceNames
        if (languageFilters.isNotEmpty()) {
            where += "books.$BookLanguageSearchText IN (${languageFilters.joinToString(",") { "?" }})"
            args += languageFilters
        }

        val candidateLimit = (limit * 4).coerceAtLeast(limit)
        val sql = """
            SELECT ${BookColumns.joinToString(",") { "books.$it" }}
            FROM $BooksTable books
            JOIN $BookSearchTable idx ON books.$BookId = idx.$BookId
            WHERE ${where.joinToString(" AND ")}
            ORDER BY
                CASE
                    WHEN books.$BookTitleSearchText LIKE ? THEN 0
                    WHEN books.$BookChapterSearchText LIKE ? THEN 1
                    WHEN books.$BookAuthorSearchText LIKE ? THEN 2
                    WHEN books.$BookReaderSearchText LIKE ? THEN 3
                    ELSE 4
                END,
                CASE books.$BookSourceColumn
                    WHEN '${BookSource.Lit2Go.name}' THEN 0
                    WHEN '${BookSource.LibriVox.name}' THEN 1
                    WHEN '${BookSource.WolneLektury.name}' THEN 2
                    WHEN '${BookSource.Gutendex.name}' THEN 3
                    ELSE 4
                END,
                books.$BookPopularityRank ASC,
                books.$BookTitle COLLATE NOCASE ASC
            LIMIT $candidateLimit
        """.trimIndent()
        args += "$normalizedQuery%"
        args += "$normalizedQuery%"
        args += "$normalizedQuery%"
        args += "$normalizedQuery%"

        val seenKeys = linkedSetOf<String>()
        val suggestions = mutableListOf<AudioBook>()
        helper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            while (cursor.moveToNext() && suggestions.size < limit) {
                val book = cursor.toAudioBook()
                if (seenKeys.add(BookMerger.matchKey(book))) {
                    suggestions += book
                }
            }
        }
        return suggestions.rankForCatalogSearch(normalizedQuery, field)
    }

    private fun queryCachedBrowseBooks(
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        limit: Int,
        offset: Int,
    ): List<AudioBook> {
        val sourceNames = sourcePreference.autocompleteSourceNames()
        if (sourceNames.isEmpty()) return emptyList()
        val languageFilters = languages
            .filter { it.isNotBlank() }
            .map { normalizeForAutocomplete(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        where += "$BookSourceColumn IN (${sourceNames.joinToString(",") { "?" }})"
        args += sourceNames
        if (languageFilters.isNotEmpty()) {
            where += "$BookLanguageSearchText IN (${languageFilters.joinToString(",") { "?" }})"
            args += languageFilters
        }
        val sql = """
            SELECT ${BookColumns.joinToString(",")}
            FROM $BooksTable
            WHERE ${where.joinToString(" AND ")}
            ORDER BY
                CASE $BookSourceColumn
                    WHEN '${BookSource.Lit2Go.name}' THEN 0
                    WHEN '${BookSource.LibriVox.name}' THEN 1
                    WHEN '${BookSource.WolneLektury.name}' THEN 2
                    WHEN '${BookSource.Gutendex.name}' THEN 3
                    ELSE 4
                END,
                $BookPopularityRank ASC,
                $BookTitle COLLATE NOCASE ASC
            LIMIT ? OFFSET ?
        """.trimIndent()
        args += limit.toString()
        args += offset.toString()
        return helper.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAudioBook())
                }
            }
        }
    }

    private fun readDiscoverState(key: String): DiscoverCacheState? {
        val database = helper.writableDatabase
        ensurePersistentCacheTables(database)
        val metadata = database.query(
            DiscoverResultCacheTable,
            arrayOf(
                DiscoverQuery,
                DiscoverSearchField,
                DiscoverSortOrderColumn,
                DiscoverLanguage,
                DiscoverSourcePreference,
                DiscoverIsDefaultBrowse,
                DiscoverNextOffset,
                DiscoverCanLoadMore,
                DiscoverLoadedAt,
            ),
            "$DiscoverCacheKey = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                DiscoverMetadata(
                    query = cursor.getString(0).orEmpty(),
                    searchField = runCatching { CatalogSearchField.valueOf(cursor.getString(1).orEmpty()) }
                        .getOrDefault(CatalogSearchField.All),
                    sortOrder = runCatching { DiscoverSortOrder.valueOf(cursor.getString(2).orEmpty()) }
                        .getOrDefault(DiscoverSortOrder.MostPopular),
                    language = cursor.getString(3).orEmpty(),
                    sourcePreference = BookSourcePreference.fromPreference(cursor.getString(4)),
                    isDefaultBrowse = cursor.getInt(5) != 0,
                    nextOffset = cursor.getInt(6),
                    canLoadMore = cursor.getInt(7) != 0,
                    loadedAtMillis = cursor.getLong(8),
                )
            }
        } ?: return null
        val books = readOrderedBooks(
            database = database,
            itemsTable = DiscoverResultItemsTable,
            cacheKeyColumn = DiscoverCacheKey,
            bookIdColumn = DiscoverBookId,
            positionColumn = DiscoverPosition,
            key = key,
        )
        return DiscoverCacheState(
            searchField = metadata.searchField,
            query = metadata.query,
            sortOrder = metadata.sortOrder,
            results = books,
            nextOffset = metadata.nextOffset,
            canLoadMore = metadata.canLoadMore,
            loadedAtMillis = metadata.loadedAtMillis,
            isDefaultBrowse = metadata.isDefaultBrowse,
            language = metadata.language,
            bookSource = metadata.sourcePreference,
        )
    }

    private fun readDailyFeaturedState(key: String): DailyFeaturedCacheState? {
        val database = helper.writableDatabase
        ensurePersistentCacheTables(database)
        val metadata = database.query(
            DailyFeaturedCacheTable,
            arrayOf(
                DailyFeaturedDayKey,
                DailyFeaturedSourcePreference,
                DailyFeaturedSource,
                DailyFeaturedLoadedAt,
            ),
            "$DailyFeaturedCacheKey = ?",
            arrayOf(key),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                DailyFeaturedMetadata(
                    dayKey = cursor.getLong(0),
                    sourcePreference = BookSourcePreference.fromPreference(cursor.getString(1)),
                    featuredSource = cursor.getString(2)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { raw -> runCatching { CatalogSourcePreference.valueOf(raw) }.getOrNull() },
                    loadedAtMillis = cursor.getLong(3),
                )
            }
        } ?: return null
        val books = readOrderedBooks(
            database = database,
            itemsTable = DailyFeaturedItemsTable,
            cacheKeyColumn = DailyFeaturedCacheKey,
            bookIdColumn = DailyFeaturedBookId,
            positionColumn = DailyFeaturedPosition,
            key = key,
        )
        return DailyFeaturedCacheState(
            dayKey = metadata.dayKey,
            bookSource = metadata.sourcePreference,
            books = books,
            loadedAtMillis = metadata.loadedAtMillis,
            featuredSource = metadata.featuredSource,
        )
    }

    private fun readOrderedBooks(
        database: SQLiteDatabase,
        itemsTable: String,
        cacheKeyColumn: String,
        bookIdColumn: String,
        positionColumn: String,
        key: String,
    ): List<AudioBook> {
        val columns = BookColumns.joinToString(",") { "books.$it" }
        val sql = """
            SELECT $columns
            FROM $itemsTable items
            JOIN $BooksTable books ON items.$bookIdColumn = books.$BookId
            WHERE items.$cacheKeyColumn = ?
            ORDER BY items.$positionColumn ASC
        """.trimIndent()
        return database.rawQuery(sql, arrayOf(key)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAudioBook())
                }
            }
        }
    }

    private fun upsertBooksInTransaction(
        database: SQLiteDatabase,
        books: List<AudioBook>,
        observedAtMillis: Long,
        rankOffset: Int,
    ) {
        books
            .filter { it.source in AutocompleteSources && it.id.isNotBlank() && it.title.isNotBlank() }
            .filter { it.isPlayableSearchResult() }
            .distinctBy { it.id }
            .forEachIndexed { index, book ->
                database.insertWithOnConflict(
                    BooksTable,
                    null,
                    book.toValues(
                        observedAtMillis = observedAtMillis,
                        popularityRank = book.sourcePopularityRank ?: rankOffset + index,
                    ),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                database.delete(BookSearchTable, "$BookId = ?", arrayOf(book.id))
                database.insertWithOnConflict(
                    BookSearchTable,
                    null,
                    book.toSearchValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
    }

    private fun AudioBook.toValues(
        observedAtMillis: Long,
        popularityRank: Int,
    ): ContentValues = ContentValues().apply {
        put(BookId, id)
        put(BookTitle, title)
        put(BookAuthor, author)
        put(BookDescription, description.take(MaxDescriptionCharacters))
        put(BookSourceColumn, source.name)
        put(BookLanguage, language)
        put(BookLanguageSearchText, normalizeForAutocomplete(language.orEmpty()))
        put(BookCoverImageUrl, coverImageUrl)
        put(BookLibrivoxUrl, librivoxUrl)
        put(BookLit2GoUrl, lit2goUrl)
        put(BookWolneLekturyUrl, wolneLekturyUrl)
        put(BookGutenbergUrl, gutenbergUrl)
        put(BookTotalDurationSeconds, totalDurationSeconds)
        put(BookNarratorsJson, json.encodeToString(narrators))
        put(BookGenresJson, json.encodeToString(genres))
        put(BookAuthorTagsJson, json.encodeToString(authorTags))
        put(BookLiteraryEpochsJson, json.encodeToString(literaryEpochs))
        put(BookLiteraryKindsJson, json.encodeToString(literaryKinds))
        put(BookLiteraryGenresJson, json.encodeToString(literaryGenres))
        put(BookTitleSearchText, normalizeForAutocomplete(title))
        put(BookChapterSearchText, chapterSearchText())
        put(BookAuthorSearchText, normalizeForAutocomplete(author))
        put(BookReaderSearchText, readerSearchText())
        put(BookSearchText, autocompleteSearchText())
        put(BookMatchKey, BookMerger.matchKey(this@toValues))
        put(BookPopularityRank, popularityRank)
        put(BookLastSeenAt, observedAtMillis)
        put(BookUpdatedAt, observedAtMillis)
    }

    private fun AudioBook.toSearchValues(): ContentValues = ContentValues().apply {
        put(BookId, id)
        put(BookSearchText, autocompleteSearchText())
        put(BookTitleSearchText, normalizeForAutocomplete(title))
        put(BookChapterSearchText, chapterSearchText())
        put(BookAuthorSearchText, normalizeForAutocomplete(author))
        put(BookReaderSearchText, readerSearchText())
    }

    private fun Cursor.toAudioBook(): AudioBook {
        val source = sourceFromName(string(BookSourceColumn))
        return AudioBook(
            id = string(BookId).orEmpty(),
            title = string(BookTitle).orEmpty(),
            author = string(BookAuthor).orEmpty(),
            description = string(BookDescription).orEmpty(),
            source = source,
            libraryStatus = LibraryStatus.NotInLibrary,
            coverImageUrl = string(BookCoverImageUrl),
            fallbackCover = FallbackCover.Generated,
            totalDurationSeconds = long(BookTotalDurationSeconds),
            librivoxUrl = string(BookLibrivoxUrl),
            lit2goUrl = string(BookLit2GoUrl),
            wolneLekturyUrl = string(BookWolneLekturyUrl),
            gutenbergUrl = string(BookGutenbergUrl),
            language = string(BookLanguage),
            narrators = decodeList(string(BookNarratorsJson)),
            genres = decodeList(string(BookGenresJson)),
            authorTags = decodeList(string(BookAuthorTagsJson)),
            literaryEpochs = decodeList(string(BookLiteraryEpochsJson)),
            literaryKinds = decodeList(string(BookLiteraryKindsJson)),
            literaryGenres = decodeList(string(BookLiteraryGenresJson)),
            sourcePopularityRank = intOrNull(BookPopularityRank),
        )
    }

    private fun AudioBook.autocompleteSearchText(): String =
        normalizeForAutocomplete(
            listOf(
                title,
                author,
                description.take(MaxSearchDescriptionCharacters),
                language.orEmpty(),
                narrators.joinToString(" "),
                chapters.joinToString(" ") { chapter ->
                    listOf(
                        chapter.title,
                        chapter.reader.orEmpty(),
                        chapter.director.orEmpty(),
                    ).joinToString(" ")
                },
                genres.joinToString(" "),
                authorTags.joinToString(" ") { "${it.name} ${it.slug}" },
                literaryEpochs.joinToString(" ") { "${it.name} ${it.slug}" },
                literaryKinds.joinToString(" ") { "${it.name} ${it.slug}" },
                literaryGenres.joinToString(" ") { "${it.name} ${it.slug}" },
            ).joinToString(" "),
        )

    private fun AudioBook.chapterSearchText(): String =
        normalizeForAutocomplete(chapters.joinToString(" ") { it.title })

    private fun AudioBook.readerSearchText(): String =
        normalizeForAutocomplete(
            (narrators + chapters.flatMap { chapter ->
                listOf(chapter.reader.orEmpty(), chapter.director.orEmpty())
            }).joinToString(" "),
        )

    private fun queryLong(database: SQLiteDatabase, sql: String): Long =
        database.rawQuery(sql, emptyArray()).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }

    private fun databaseFiles(): List<File> {
        val database = helper.readableDatabase.path?.let(::File)
            ?: return emptyList()
        return listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
        )
    }

    private fun File.lengthIfExists(): Long =
        if (exists()) length() else 0L

    private fun compactDatabase(database: SQLiteDatabase) {
        runCatching { database.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        runCatching { database.execSQL("VACUUM") }
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> =
        runCatching {
            json.decodeFromString<List<T>>(raw.orEmpty().ifBlank { "[]" })
        }.getOrDefault(emptyList())

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) 0L else getLong(index)
    }

    private fun Cursor.intOrNull(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun readSyncState(database: SQLiteDatabase, syncKey: String): SyncState? =
        database.query(
            SyncTable,
            arrayOf(SyncKey, SyncNextOffset, SyncLastStartedAt, SyncLastCompletedAt, SyncFailureCount),
            "$SyncKey = ?",
            arrayOf(syncKey),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                SyncState(
                    syncKey = cursor.getString(0),
                    nextOffset = cursor.getInt(1),
                    lastStartedAt = cursor.getLong(2),
                    lastCompletedAt = cursor.getLong(3),
                    failureCount = cursor.getInt(4),
                )
            }
        }

    private fun replaceSyncState(database: SQLiteDatabase, state: SyncState) {
        database.insertWithOnConflict(
            SyncTable,
            null,
            ContentValues().apply {
                put(SyncKey, state.syncKey)
                put(SyncNextOffset, state.nextOffset)
                put(SyncLastStartedAt, state.lastStartedAt)
                put(SyncLastCompletedAt, state.lastCompletedAt)
                put(SyncFailureCount, state.failureCount)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private data class SyncState(
        val syncKey: String,
        val nextOffset: Int,
        val lastStartedAt: Long,
        val lastCompletedAt: Long,
        val failureCount: Int,
    )

    private data class DiscoverMetadata(
        val query: String,
        val searchField: CatalogSearchField,
        val sortOrder: DiscoverSortOrder,
        val language: String,
        val sourcePreference: BookSourcePreference,
        val isDefaultBrowse: Boolean,
        val nextOffset: Int,
        val canLoadMore: Boolean,
        val loadedAtMillis: Long,
    )

    private data class DailyFeaturedMetadata(
        val dayKey: Long,
        val sourcePreference: BookSourcePreference,
        val featuredSource: CatalogSourcePreference?,
        val loadedAtMillis: Long,
    )

    private data class SyncSnapshot(
        val entryCount: Int = 0,
        val lastStartedAt: Long = 0L,
        val lastCompletedAt: Long = 0L,
        val failureCount: Int = 0,
    )

    private fun ensurePersistentCacheTables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $DiscoverResultCacheTable (
                $DiscoverCacheKey TEXT PRIMARY KEY NOT NULL,
                $DiscoverQuery TEXT NOT NULL,
                $DiscoverSearchField TEXT NOT NULL,
                $DiscoverSortOrderColumn TEXT NOT NULL,
                $DiscoverLanguage TEXT NOT NULL,
                $DiscoverSourcePreference TEXT NOT NULL,
                $DiscoverIsDefaultBrowse INTEGER NOT NULL,
                $DiscoverNextOffset INTEGER NOT NULL,
                $DiscoverCanLoadMore INTEGER NOT NULL,
                $DiscoverLoadedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $DiscoverResultItemsTable (
                $DiscoverCacheKey TEXT NOT NULL,
                $DiscoverBookId TEXT NOT NULL,
                $DiscoverPosition INTEGER NOT NULL,
                PRIMARY KEY($DiscoverCacheKey, $DiscoverBookId)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS ${DiscoverResultItemsTable}_position_idx
            ON $DiscoverResultItemsTable($DiscoverCacheKey, $DiscoverPosition)
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $DailyFeaturedCacheTable (
                $DailyFeaturedCacheKey TEXT PRIMARY KEY NOT NULL,
                $DailyFeaturedDayKey INTEGER NOT NULL,
                $DailyFeaturedSourcePreference TEXT NOT NULL,
                $DailyFeaturedSource TEXT NOT NULL,
                $DailyFeaturedLoadedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $DailyFeaturedItemsTable (
                $DailyFeaturedCacheKey TEXT NOT NULL,
                $DailyFeaturedBookId TEXT NOT NULL,
                $DailyFeaturedPosition INTEGER NOT NULL,
                PRIMARY KEY($DailyFeaturedCacheKey, $DailyFeaturedBookId)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE INDEX IF NOT EXISTS ${DailyFeaturedItemsTable}_position_idx
            ON $DailyFeaturedItemsTable($DailyFeaturedCacheKey, $DailyFeaturedPosition)
            """.trimIndent(),
        )
    }

    private class CatalogAutocompleteDatabase(context: Context) : SQLiteOpenHelper(
        context,
        DatabaseName,
        null,
        DatabaseVersion,
    ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $BooksTable (
                    $BookId TEXT PRIMARY KEY NOT NULL,
                    $BookTitle TEXT NOT NULL,
                    $BookAuthor TEXT NOT NULL,
                    $BookDescription TEXT NOT NULL,
                    $BookSourceColumn TEXT NOT NULL,
                    $BookLanguage TEXT,
                    $BookLanguageSearchText TEXT NOT NULL,
                    $BookCoverImageUrl TEXT,
                    $BookLibrivoxUrl TEXT,
                    $BookLit2GoUrl TEXT,
                    $BookWolneLekturyUrl TEXT,
                    $BookGutenbergUrl TEXT,
                    $BookTotalDurationSeconds INTEGER NOT NULL,
                    $BookNarratorsJson TEXT NOT NULL,
                    $BookGenresJson TEXT NOT NULL,
                    $BookAuthorTagsJson TEXT NOT NULL,
                    $BookLiteraryEpochsJson TEXT NOT NULL,
                    $BookLiteraryKindsJson TEXT NOT NULL,
                    $BookLiteraryGenresJson TEXT NOT NULL,
                    $BookTitleSearchText TEXT NOT NULL,
                    $BookChapterSearchText TEXT NOT NULL,
                    $BookAuthorSearchText TEXT NOT NULL,
                    $BookReaderSearchText TEXT NOT NULL,
                    $BookSearchText TEXT NOT NULL,
                    $BookMatchKey TEXT NOT NULL,
                    $BookPopularityRank INTEGER NOT NULL,
                    $BookLastSeenAt INTEGER NOT NULL,
                    $BookUpdatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX ${BooksTable}_search_idx ON $BooksTable($BookSearchText)")
            database.execSQL("CREATE INDEX ${BooksTable}_source_language_idx ON $BooksTable($BookSourceColumn, $BookLanguageSearchText)")
            database.execSQL("CREATE INDEX ${BooksTable}_match_idx ON $BooksTable($BookMatchKey)")
            database.execSQL("CREATE INDEX ${BooksTable}_rank_idx ON $BooksTable($BookPopularityRank)")
            database.execSQL(
                """
                CREATE VIRTUAL TABLE $BookSearchTable USING fts4(
                    $BookId,
                    $BookSearchText,
                    $BookTitleSearchText,
                    $BookChapterSearchText,
                    $BookAuthorSearchText,
                    $BookReaderSearchText
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE $SyncTable (
                    $SyncKey TEXT PRIMARY KEY NOT NULL,
                    $SyncNextOffset INTEGER NOT NULL,
                    $SyncLastStartedAt INTEGER NOT NULL,
                    $SyncLastCompletedAt INTEGER NOT NULL,
                    $SyncFailureCount INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < DatabaseVersion) {
                database.execSQL("DROP TABLE IF EXISTS $SyncTable")
                database.execSQL("DROP TABLE IF EXISTS $BookSearchTable")
                database.execSQL("DROP TABLE IF EXISTS $BooksTable")
                onCreate(database)
            }
        }
    }

    private companion object {
        const val DefaultSuggestionLimit = 8
        const val MaxSuggestionLimit = 20
        const val DefaultSearchResultLimit = 50
        const val MaxSearchResultLimit = 120
        const val MaxDescriptionCharacters = 2_000
        const val MaxSearchDescriptionCharacters = 600
        const val MinPruneRows = 50
        const val MaxPrunePasses = 4
        const val SqliteMaxVariableNumber = 900
        const val DatabaseName = "catalog_autocomplete.db"
        const val DatabaseVersion = 4

        const val BooksTable = "autocomplete_books"
        const val BookSearchTable = "autocomplete_book_search"
        const val BookId = "id"
        const val BookTitle = "title"
        const val BookAuthor = "author"
        const val BookDescription = "description"
        const val BookSourceColumn = "source"
        const val BookLanguage = "language"
        const val BookLanguageSearchText = "language_search_text"
        const val BookCoverImageUrl = "cover_image_url"
        const val BookLibrivoxUrl = "librivox_url"
        const val BookLit2GoUrl = "lit2go_url"
        const val BookWolneLekturyUrl = "wolne_lektury_url"
        const val BookGutenbergUrl = "gutenberg_url"
        const val BookTotalDurationSeconds = "total_duration_seconds"
        const val BookNarratorsJson = "narrators_json"
        const val BookGenresJson = "genres_json"
        const val BookAuthorTagsJson = "author_tags_json"
        const val BookLiteraryEpochsJson = "literary_epochs_json"
        const val BookLiteraryKindsJson = "literary_kinds_json"
        const val BookLiteraryGenresJson = "literary_genres_json"
        const val BookTitleSearchText = "title_search_text"
        const val BookChapterSearchText = "chapter_search_text"
        const val BookAuthorSearchText = "author_search_text"
        const val BookReaderSearchText = "reader_search_text"
        const val BookSearchText = "search_text"
        const val BookMatchKey = "match_key"
        const val BookPopularityRank = "popularity_rank"
        const val BookLastSeenAt = "last_seen_at"
        const val BookUpdatedAt = "updated_at"

        const val SyncTable = "autocomplete_sync_state"
        const val SyncKey = "sync_key"
        const val SyncNextOffset = "next_offset"
        const val SyncLastStartedAt = "last_started_at"
        const val SyncLastCompletedAt = "last_completed_at"
        const val SyncFailureCount = "failure_count"

        const val DiscoverResultCacheTable = "discover_result_cache"
        const val DiscoverResultItemsTable = "discover_result_items"
        const val DiscoverCacheKey = "cache_key"
        const val DiscoverQuery = "query"
        const val DiscoverSearchField = "search_field"
        const val DiscoverSortOrderColumn = "sort_order"
        const val DiscoverLanguage = "language"
        const val DiscoverSourcePreference = "source_preference"
        const val DiscoverIsDefaultBrowse = "is_default_browse"
        const val DiscoverNextOffset = "next_offset"
        const val DiscoverCanLoadMore = "can_load_more"
        const val DiscoverLoadedAt = "loaded_at"
        const val DiscoverBookId = "book_id"
        const val DiscoverPosition = "position"

        const val DailyFeaturedCacheTable = "daily_featured_cache"
        const val DailyFeaturedItemsTable = "daily_featured_items"
        const val DailyFeaturedCacheKey = "cache_key"
        const val DailyFeaturedDayKey = "day_key"
        const val DailyFeaturedSourcePreference = "source_preference"
        const val DailyFeaturedSource = "featured_source"
        const val DailyFeaturedLoadedAt = "loaded_at"
        const val DailyFeaturedBookId = "book_id"
        const val DailyFeaturedPosition = "position"

        val BookColumns = arrayOf(
            BookId,
            BookTitle,
            BookAuthor,
            BookDescription,
            BookSourceColumn,
            BookLanguage,
            BookCoverImageUrl,
            BookLibrivoxUrl,
            BookLit2GoUrl,
            BookWolneLekturyUrl,
            BookGutenbergUrl,
            BookTotalDurationSeconds,
            BookNarratorsJson,
            BookGenresJson,
            BookAuthorTagsJson,
            BookLiteraryEpochsJson,
            BookLiteraryKindsJson,
            BookLiteraryGenresJson,
            BookPopularityRank,
        )

        val AutocompleteSources = setOf(
            BookSource.Lit2Go,
            BookSource.LibriVox,
            BookSource.WolneLektury,
            BookSource.Gutendex,
        )

        fun normalizeForAutocomplete(value: String): String =
            normalizedAutocompleteSearchText(value)

        fun sourceFromName(value: String?): BookSource =
            runCatching { BookSource.valueOf(value.orEmpty()) }.getOrDefault(BookSource.LibriVox)

        fun discoverResultCacheKey(
            query: String,
            field: CatalogSearchField,
            language: String,
            sourcePreference: BookSourcePreference,
            sortOrder: DiscoverSortOrder,
        ): String =
            listOf(
                "discover-v1",
                sourcePreference.preferenceValue,
                language,
                sortOrder.name,
                field.name,
                query,
            ).joinToString("|")

        fun dailyFeaturedCacheKey(dayKey: Long, sourcePreference: BookSourcePreference): String =
            listOf("daily-featured-v1", sourcePreference.preferenceValue, dayKey.toString())
                .joinToString("|")
    }
}

private val AutocompleteCombiningMarks = Regex("\\p{Mn}+")
private val AutocompleteNonSearchCharacters = Regex("[^\\p{L}\\p{Nd}]+")

internal fun normalizedAutocompleteSearchText(value: String): String {
    if (value.isBlank()) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("ł", "l")
        .replace("Ł", "L")
        .replace(AutocompleteCombiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace(AutocompleteNonSearchCharacters, " ")
        .trim()
}

internal fun autocompleteFtsPrefixQuery(normalizedQuery: String): String? =
    normalizedQuery
        .split(' ')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { token -> token.all { it.isLetterOrDigit() } }
        .take(6)
        .joinToString(" ") { "$it*" }
        .takeIf { it.isNotBlank() }

data class CatalogAutocompleteCacheSnapshot(
    val bookCount: Int = 0,
    val sourceCounts: Map<BookSource, Int> = emptyMap(),
    val sourceSizeBytes: Map<BookSource, Long> = emptyMap(),
    val syncEntryCount: Int = 0,
    val lastStartedAtMillis: Long = 0L,
    val lastCompletedAtMillis: Long = 0L,
    val failureCount: Int = 0,
    val sizeBytes: Long = 0L,
)

private fun sourceSizeEstimates(
    totalSizeBytes: Long,
    sourceCounts: Map<BookSource, Int>,
    sourceDataBytes: Map<BookSource, Long>,
): Map<BookSource, Long> {
    if (totalSizeBytes <= 0L || sourceCounts.isEmpty()) return emptyMap()
    val totalDataBytes = sourceDataBytes.values.sum()
    val totalWeight = totalDataBytes.takeIf { it > 0L }
        ?: sourceCounts.values.sum().toLong().takeIf { it > 0L }
        ?: return emptyMap()
    return sourceCounts.mapValues { (source, count) ->
        val weight = sourceDataBytes[source]
            ?.takeIf { totalDataBytes > 0L && it > 0L }
            ?: count.toLong().coerceAtLeast(1L)
        ((totalSizeBytes * weight) / totalWeight).coerceAtLeast(0L)
    }
}

private fun AudioBook.isPlayableSearchResult(): Boolean =
    source != BookSource.Gutendex || chapters.any { it.isPlayable() }

private fun BookSourcePreference.autocompleteSourceNames(): List<String> =
    listOf(
        CatalogSourcePreference.Lit2Go to BookSource.Lit2Go,
        CatalogSourcePreference.LibriVox to BookSource.LibriVox,
        CatalogSourcePreference.WolneLektury to BookSource.WolneLektury,
        CatalogSourcePreference.Gutendex to BookSource.Gutendex,
    ).mapNotNull { (catalogSource, bookSource) ->
        bookSource.name.takeIf { catalogSource in enabledSources }
    }

private fun Boolean.toInt(): Int = if (this) 1 else 0
