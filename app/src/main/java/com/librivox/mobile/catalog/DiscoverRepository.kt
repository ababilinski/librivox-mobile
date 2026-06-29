package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.isPlayable
import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import com.librivox.mobile.playback.isAllCatalogLanguagesSelected
import com.librivox.mobile.playback.normalizeForCatalogSource
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

data class DiscoverLoadRequest(
    val query: String,
    val field: CatalogSearchField,
    val languages: List<String>,
    val languageKey: String,
    val sourcePreference: BookSourcePreference,
    val sortOrder: DiscoverSortOrder,
    val reset: Boolean,
    val offset: Int,
    val pageSize: Int = CatalogSource.DEFAULT_PAGE_SIZE,
)

private data class RemoteDiscoverLoadResult(
    val books: List<AudioBook>,
    val canLoadMore: Boolean,
    val hadFailure: Boolean,
)

class DiscoverRepository(
    private val scope: CoroutineScope,
    private val catalogClient: CatalogSource,
    private val catalogCache: CatalogAutocompleteRepository,
    private val libraryRepository: LibraryRepository,
    private val settings: PlaybackSettingsStore,
) {
    private val _discoverCache = MutableStateFlow(DiscoverCacheState())
    val discoverCache: StateFlow<DiscoverCacheState> = _discoverCache.asStateFlow()

    private val _dailyFeaturedCache = MutableStateFlow(DailyFeaturedCacheState())
    val dailyFeaturedCache: StateFlow<DailyFeaturedCacheState> = _dailyFeaturedCache.asStateFlow()

    private var activeDiscoverJob: Job? = null
    private var activeDiscoverJobKey: String? = null
    private var activeDailyFeaturedJob: Job? = null
    private var activeDailyFeaturedJobKey: String? = null
    private val activeVisibleDetailHydrationJobs = mutableSetOf<Job>()
    private val dailyFeaturedMemoryCacheLock = Any()
    private val dailyFeaturedMemoryCache = mutableMapOf<String, DailyFeaturedCacheState>()
    private val detailHydrationLock = Any()
    private val hydratingDetailIds = mutableSetOf<String>()
    private val hydratedDetailIds = mutableSetOf<String>()
    private val prioritizedDetailIds = mutableSetOf<String>()

    private val searchCachingEnabled: Boolean
        get() = settings.automaticSearchCachingEnabled

    fun clearDiscoverState() {
        activeDiscoverJob?.cancel()
        synchronized(detailHydrationLock) {
            activeVisibleDetailHydrationJobs.forEach { it.cancel() }
            activeVisibleDetailHydrationJobs.clear()
        }
        activeDiscoverJobKey = null
        _discoverCache.value = DiscoverCacheState()
    }

    fun loadDiscoverPage(request: DiscoverLoadRequest) {
        val normalizedQuery = sanitizeCatalogSearchQuery(request.query).take(MaxDiscoverQueryLength)
        val isDefaultBrowse = normalizedQuery.isBlank()
        val offset = if (request.reset) 0 else request.offset.coerceAtLeast(0)
        val sourcePreference = request.sourcePreference
        val languageKey = request.languageKey
        val current = _discoverCache.value
        if (
            request.reset &&
            current.matches(
                query = normalizedQuery,
                field = request.field,
                language = languageKey,
                bookSource = sourcePreference,
                sortOrder = request.sortOrder,
            ) &&
            current.results.isNotEmpty() &&
            !current.isLoading
        ) {
            return
        }
        if (sourcePreference.enabledSources.isEmpty()) {
            val emptyState = DiscoverCacheState(
                searchField = request.field,
                query = normalizedQuery,
                sortOrder = request.sortOrder,
                nextOffset = 0,
                canLoadMore = false,
                loadedAtMillis = System.currentTimeMillis(),
                isDefaultBrowse = isDefaultBrowse,
                language = languageKey,
                bookSource = sourcePreference,
            )
            _discoverCache.value = emptyState
            if (searchCachingEnabled) {
                scope.launch { catalogCache.saveDiscoverState(emptyState) }
            }
            return
        }

        val jobKey = discoverJobKey(
            query = normalizedQuery,
            field = request.field,
            languageKey = languageKey,
            sourcePreference = sourcePreference,
            sortOrder = request.sortOrder,
            reset = request.reset,
            offset = offset,
        )
        if (activeDiscoverJob?.isActive == true && activeDiscoverJobKey == jobKey) return
        if (request.reset) {
            activeDiscoverJob?.cancel()
        }
        activeDiscoverJobKey = jobKey
        activeDiscoverJob = scope.launch {
            runDiscoverLoad(
                request = request,
                normalizedQuery = normalizedQuery,
                isDefaultBrowse = isDefaultBrowse,
                offset = offset,
                languageKey = languageKey,
                jobKey = jobKey,
            )
        }
    }

    fun loadDailyFeatured(
        sourcePreference: BookSourcePreference,
        dayKey: Long,
        limit: Int,
    ) {
        val featuredSource = sourcePreference.featuredCatalogSource()
        if (featuredSource == null) {
            activeDailyFeaturedJob?.cancel()
            activeDailyFeaturedJobKey = null
            _dailyFeaturedCache.value = DailyFeaturedCacheState(
                dayKey = dayKey,
                bookSource = BookSourcePreference.None,
                featuredSource = null,
            )
            return
        }
        val singleSourcePreference = featuredSource.asSingleSourcePreference()
        val jobKey = dailyFeaturedJobKey(dayKey, singleSourcePreference)
        val current = _dailyFeaturedCache.value
        if (
            current.matches(dayKey, singleSourcePreference, featuredSource) &&
            (current.books.isNotEmpty() || current.isLoading)
        ) {
            return
        }
        if (activeDailyFeaturedJob?.isActive == true && activeDailyFeaturedJobKey == jobKey) return
        cachedDailyFeaturedMemoryState(dayKey, singleSourcePreference)?.let { cached ->
            activeDailyFeaturedJob?.cancel()
            activeDailyFeaturedJobKey = null
            _dailyFeaturedCache.value = cached.copy(
                isLoading = false,
                errorMessage = null,
                featuredSource = featuredSource,
            )
            return
        }
        activeDailyFeaturedJob?.cancel()
        activeDailyFeaturedJobKey = jobKey
        activeDailyFeaturedJob = scope.launch {
            val cacheEnabled = searchCachingEnabled
            val cached = if (cacheEnabled) {
                catalogCache.cachedDailyFeaturedState(dayKey, singleSourcePreference)
            } else {
                null
            }
            if (cached != null && cached.books.isNotEmpty()) {
                val cachedState = cached.copy(
                    isLoading = false,
                    errorMessage = null,
                    featuredSource = featuredSource,
                )
                rememberDailyFeaturedState(cachedState)
                _dailyFeaturedCache.value = cachedState
                return@launch
            }

            _dailyFeaturedCache.value = (cached ?: DailyFeaturedCacheState(
                dayKey = dayKey,
                bookSource = singleSourcePreference,
                featuredSource = featuredSource,
            )).copy(isLoading = true, errorMessage = null)

            val candidates = runCatching {
                (catalogClient as? AggregateCatalogClient)
                    ?.featuredBooksSource(featuredSource)
                    ?: catalogClient.featuredBooks()
            }.getOrDefault(emptyList())
            val selected = selectDailyFeaturedAudiobooks(
                candidates = candidates,
                bookSource = singleSourcePreference,
                dayKey = dayKey,
                limit = limit,
                preserveSourceOrder = featuredSource == CatalogSourcePreference.WolneLektury,
            )
            if (selected.isNotEmpty()) {
                if (cacheEnabled) {
                    libraryRepository.upsertCatalogBooks(selected)
                }
            }
            val finalState = DailyFeaturedCacheState(
                dayKey = dayKey,
                bookSource = singleSourcePreference,
                books = selected,
                isLoading = false,
                errorMessage = if (selected.isEmpty()) "Could not load today's picks." else null,
                loadedAtMillis = System.currentTimeMillis(),
                featuredSource = featuredSource,
            )
            if (cacheEnabled) {
                catalogCache.saveDailyFeaturedState(finalState)
            }
            rememberDailyFeaturedState(finalState)
            _dailyFeaturedCache.value = finalState
        }
    }

    fun hydrateVisibleBookDetails(books: List<AudioBook>, limit: Int = VisibleDetailHydrationLimit) {
        if (!searchCachingEnabled || books.isEmpty() || hasPrioritizedDetailHydration()) return
        val candidates = books
            .asSequence()
            .filter { it.supportsDetailHydration() }
            .distinctBy { it.id }
            .take(limit)
            .toList()
        if (candidates.isEmpty()) return

        val idsToRequest = synchronized(detailHydrationLock) {
            val priorityIds = prioritizedDetailIds.toSet()
            candidates
                .map { it.id }
                .filter { it !in priorityIds && it !in hydratingDetailIds && it !in hydratedDetailIds }
                .also { hydratingDetailIds.addAll(it) }
        }
        if (idsToRequest.isEmpty()) return

        val hydrationJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                hydrateBookDetails(idsToRequest, candidates.associateBy { it.id })
            } finally {
                synchronized(detailHydrationLock) {
                    hydratingDetailIds.removeAll(idsToRequest.toSet())
                }
            }
        }
        synchronized(detailHydrationLock) {
            activeVisibleDetailHydrationJobs += hydrationJob
        }
        hydrationJob.invokeOnCompletion {
            synchronized(detailHydrationLock) {
                activeVisibleDetailHydrationJobs.remove(hydrationJob)
            }
        }
        hydrationJob.start()
    }

    suspend fun hydrateSelectedBookDetail(
        bookId: String,
        stagedBook: AudioBook? = null,
    ): AudioBook? {
        if (bookId.isBlank()) return null
        synchronized(detailHydrationLock) {
            activeVisibleDetailHydrationJobs.forEach { it.cancel() }
            activeVisibleDetailHydrationJobs.clear()
            prioritizedDetailIds += bookId
            hydratingDetailIds.remove(bookId)
        }
        return try {
            val snapshotBook = runCatching { libraryRepository.snapshot().bookById(bookId) }.getOrNull()
            val current = snapshotBook ?: stagedBook
            if (current != null && !current.needsDetailHydration()) {
                markDetailHydrated(listOf(current.id))
                current
            } else {
                val refreshed = withContext(Dispatchers.IO) {
                    catalogClient.fetchByIds(bookId).firstOrNull()
                } ?: current
                if (refreshed != null) {
                    libraryRepository.upsertCatalogBooks(listOf(refreshed))
                    if (searchCachingEnabled) {
                        catalogCache.upsertBooks(listOf(refreshed))
                    }
                    if (!refreshed.needsDetailHydration()) {
                        markDetailHydrated(listOf(refreshed.id))
                    }
                }
                refreshed
            }
        } finally {
            synchronized(detailHydrationLock) {
                prioritizedDetailIds.remove(bookId)
                hydratingDetailIds.remove(bookId)
            }
        }
    }

    private suspend fun hydrateBookDetails(
        idsToRequest: List<String>,
        candidatesById: Map<String, AudioBook>,
    ) {
        if (hasPrioritizedDetailHydration()) return
        val snapshot = runCatching { libraryRepository.snapshot() }.getOrDefault(LibraryState())
        val priorityIds = synchronized(detailHydrationLock) { prioritizedDetailIds.toSet() }
        val booksNeedingDetails = idsToRequest.mapNotNull { id ->
            if (id in priorityIds) return@mapNotNull null
            val current = snapshot.bookById(id) ?: candidatesById[id]
            current?.takeIf { it.needsDetailHydration() }
        }
        val alreadyDetailedIds = idsToRequest - booksNeedingDetails.map { it.id }.toSet()
        if (alreadyDetailedIds.isNotEmpty()) {
            markDetailHydrated(alreadyDetailedIds)
        }
        if (booksNeedingDetails.isEmpty() || hasPrioritizedDetailHydration()) return

        val hydrated = withContext(Dispatchers.IO) {
            catalogClient.fetchByIds(*booksNeedingDetails.map { it.id }.toTypedArray())
        }
        if (hydrated.isEmpty()) return

        libraryRepository.upsertCatalogBooks(hydrated)
        catalogCache.upsertBooks(hydrated)
        markDetailHydrated(hydrated.filterNot { it.needsDetailHydration() }.map { it.id })
    }

    private fun hasPrioritizedDetailHydration(): Boolean =
        synchronized(detailHydrationLock) { prioritizedDetailIds.isNotEmpty() }

    private fun markDetailHydrated(ids: Collection<String>) {
        synchronized(detailHydrationLock) {
            hydratedDetailIds.addAll(ids)
        }
    }

    private suspend fun runDiscoverLoad(
        request: DiscoverLoadRequest,
        normalizedQuery: String,
        isDefaultBrowse: Boolean,
        offset: Int,
        languageKey: String,
        jobKey: String,
    ) {
        val loadingBase = DiscoverCacheState(
            searchField = request.field,
            query = normalizedQuery,
            sortOrder = request.sortOrder,
            nextOffset = offset,
            canLoadMore = true,
            isDefaultBrowse = isDefaultBrowse,
            language = languageKey,
            bookSource = request.sourcePreference,
            isLoading = true,
            loadingReset = request.reset,
        )
        val cacheEnabled = searchCachingEnabled
        val cachedState = if (request.reset && cacheEnabled) {
            catalogCache.cachedDiscoverState(
                query = normalizedQuery,
                field = request.field,
                language = languageKey,
                sourcePreference = request.sourcePreference,
                sortOrder = request.sortOrder,
            )
        } else {
            null
        }
        if (
            cachedState != null &&
            cachedState.canUseWithoutRefresh(System.currentTimeMillis()) &&
            activeDiscoverJobKey == jobKey
        ) {
            _discoverCache.value = cachedState
            return
        }

        if (request.reset) {
            _discoverCache.value = cachedState
                ?.copy(isLoading = true, loadingReset = true, errorMessage = null)
                ?: _discoverCache.value
                .takeIf {
                    it.describes(
                        query = normalizedQuery,
                        field = request.field,
                        language = languageKey,
                        bookSource = request.sourcePreference,
                        sortOrder = request.sortOrder,
                    ) && it.results.isNotEmpty()
                }
                ?.copy(isLoading = true, loadingReset = true, errorMessage = null)
                ?: loadingBase
        } else {
            _discoverCache.value = _discoverCache.value.copy(
                isLoading = true,
                loadingReset = false,
                errorMessage = null,
            )
        }

        val librarySnapshot = runCatching { libraryRepository.snapshot() }.getOrDefault(LibraryState())
        val cachedInterim = if (request.reset) {
            buildInterimResults(
                cachedState = cachedState,
                librarySnapshot = librarySnapshot,
                normalizedQuery = normalizedQuery,
                field = request.field,
                languages = request.languages,
                sourcePreference = request.sourcePreference,
                sortOrder = request.sortOrder,
                isDefaultBrowse = isDefaultBrowse,
                pageSize = request.pageSize,
                includeSearchCache = cacheEnabled,
            )
        } else {
            emptyList()
        }
        if (cachedInterim.isNotEmpty()) {
            _discoverCache.value = loadingBase.copy(
                results = cachedInterim,
                canLoadMore = !isDefaultBrowse || cachedInterim.size >= request.pageSize,
            )
        }

        val catalogQuery = request.query.trim().take(MaxRawCatalogQueryLength).ifBlank { normalizedQuery }
        try {
            val partialBaseResults = if (request.reset) {
                cachedInterim
            } else {
                _discoverCache.value.results
            }
            val partialLock = Any()
            var partialRemoteResults = emptyList<AudioBook>()
            suspend fun publishPartialPage(sourcePage: List<AudioBook>) {
                if (sourcePage.isEmpty() || activeDiscoverJobKey != jobKey) return
                val nextRemoteResults = synchronized(partialLock) {
                    partialRemoteResults = partialRemoteResults + sourcePage
                    partialRemoteResults
                }
                val sorted = withContext(Dispatchers.Default) {
                    (partialBaseResults + nextRemoteResults)
                        .dedupeCatalogBooks()
                        .sortedForDiscover(request.sortOrder, normalizedQuery, request.field)
                }
                if (activeDiscoverJobKey == jobKey) {
                    _discoverCache.value = loadingBase.copy(
                        results = sorted,
                        nextOffset = offset,
                        canLoadMore = true,
                        isLoading = true,
                        loadingReset = request.reset,
                        errorMessage = null,
                    )
                }
            }

            val remoteLoad = withContext(Dispatchers.IO) {
                loadRemoteDiscoverPage(
                    query = catalogQuery,
                    field = request.field,
                    languages = request.languages,
                    isDefaultBrowse = isDefaultBrowse,
                    limit = request.pageSize,
                    offset = offset,
                    sourcePreference = request.sourcePreference,
                    onSourcePageLoaded = { sourcePage -> publishPartialPage(sourcePage) },
                )
            }
            val page = remoteLoad.books
            if (activeDiscoverJobKey != jobKey) return
            val pageResults = when {
                request.reset && page.isEmpty() && isDefaultBrowse ->
                    discoverFallbackBooks(librarySnapshot, request.languages, request.sortOrder, request.sourcePreference)
                request.reset && cachedInterim.isNotEmpty() -> cachedInterim + page
                else -> page
            }
            val currentResults = _discoverCache.value.results
            val merged = withContext(Dispatchers.Default) {
                if (request.reset) {
                    pageResults
                        .dedupeCatalogBooks()
                        .sortedForDiscover(request.sortOrder, normalizedQuery, request.field)
                } else {
                    (currentResults + pageResults)
                        .dedupeCatalogBooks()
                        .sortedForDiscover(
                            request.sortOrder,
                            normalizedQuery,
                            request.field,
                        )
                }
            }
            val next = if (page.isNotEmpty()) offset + request.pageSize else offset
            val finalState = DiscoverCacheState(
                searchField = request.field,
                query = normalizedQuery,
                sortOrder = request.sortOrder,
                results = merged,
                nextOffset = next,
                canLoadMore = remoteLoad.canLoadMore,
                loadedAtMillis = System.currentTimeMillis(),
                isDefaultBrowse = isDefaultBrowse,
                language = languageKey,
                bookSource = request.sourcePreference,
                errorMessage = if (merged.isEmpty() && remoteLoad.hadFailure) {
                    "Could not reach audiobook catalogs"
                } else {
                    null
                },
            )
            if (cacheEnabled) {
                libraryRepository.upsertCatalogBooks(merged)
                catalogCache.upsertBooks(merged)
                catalogCache.saveDiscoverState(finalState)
            }
            _discoverCache.value = finalState
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (activeDiscoverJobKey != jobKey) return
            val fallback = if (request.reset && isDefaultBrowse) {
                discoverFallbackBooks(librarySnapshot, request.languages, request.sortOrder, request.sourcePreference)
            } else {
                emptyList()
            }
            if (fallback.isNotEmpty()) {
                val finalState = loadingBase.copy(
                    results = fallback,
                    nextOffset = fallback.size,
                    canLoadMore = false,
                    loadedAtMillis = System.currentTimeMillis(),
                    isLoading = false,
                    loadingReset = false,
                )
                if (cacheEnabled) {
                    catalogCache.saveDiscoverState(finalState)
                }
                _discoverCache.value = finalState
            } else {
                val current = _discoverCache.value
                _discoverCache.value = current.copy(
                    isLoading = false,
                    loadingReset = false,
                    errorMessage = if (current.results.isEmpty()) {
                        throwable.message ?: "Could not reach audiobook catalogs"
                    } else {
                        null
                    },
                )
            }
        }
    }

    private suspend fun buildInterimResults(
        cachedState: DiscoverCacheState?,
        librarySnapshot: LibraryState,
        normalizedQuery: String,
        field: CatalogSearchField,
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        sortOrder: DiscoverSortOrder,
        isDefaultBrowse: Boolean,
        pageSize: Int,
        includeSearchCache: Boolean,
    ): List<AudioBook> {
        if (cachedState != null && cachedState.results.isNotEmpty()) return cachedState.results
        return if (isDefaultBrowse) {
            val cachedBrowse = if (includeSearchCache) {
                catalogCache.browseCachedBooks(
                    languages = languages,
                    sourcePreference = sourcePreference,
                    limit = pageSize,
                )
            } else {
                emptyList()
            }
            cachedBrowse.ifEmpty {
                discoverFallbackBooks(librarySnapshot, languages, sortOrder, sourcePreference)
            }.sortedForDiscover(sortOrder)
        } else {
            val localSearchResults = withContext(Dispatchers.Default) {
                discoverLocalSearchBooks(librarySnapshot, normalizedQuery, field, languages, sourcePreference)
            }
            val cachedSearchResults = if (includeSearchCache) {
                catalogCache.searchBooks(
                    query = normalizedQuery,
                    field = field,
                    languages = languages,
                    sourcePreference = sourcePreference,
                    limit = pageSize,
                )
            } else {
                emptyList()
            }
            (localSearchResults + cachedSearchResults)
                .sortedForDiscover(sortOrder, normalizedQuery, field)
        }
    }

    private suspend fun loadRemoteDiscoverPage(
        query: String,
        field: CatalogSearchField,
        languages: List<String>,
        isDefaultBrowse: Boolean,
        limit: Int,
        offset: Int,
        sourcePreference: BookSourcePreference,
        onSourcePageLoaded: suspend (List<AudioBook>) -> Unit,
    ): RemoteDiscoverLoadResult =
        supervisorScope {
            val aggregateClient = catalogClient as? AggregateCatalogClient
            val resultLock = Any()
            val results = mutableListOf<AudioBook>()
            var canLoadMore = false
            var hadFailure = false

            suspend fun loadCatalogPage(
                source: CatalogSourcePreference?,
                language: String,
            ): List<AudioBook> =
                if (isDefaultBrowse) {
                    if (aggregateClient != null && source != null) {
                        aggregateClient.browseSource(
                            source = source,
                            limit = limit,
                            offset = offset,
                            language = language,
                        )
                    } else {
                        catalogClient.browse(
                            limit = limit,
                            offset = offset,
                            language = language,
                        )
                    }
                } else {
                    if (aggregateClient != null && source != null) {
                        aggregateClient.searchSource(
                            source = source,
                            query = query,
                            field = field,
                            limit = limit,
                            offset = offset,
                            language = language,
                        )
                    } else {
                        catalogClient.search(
                            query = query,
                            field = field,
                            limit = limit,
                            offset = offset,
                            language = language,
                        )
                    }
                }

            val jobs = if (aggregateClient != null) {
                CatalogSourcePreference.entries
                    .filter { it in sourcePreference.enabledSources }
                    .flatMap { source ->
                        languages.forSource(source).map { language ->
                            launch(Dispatchers.IO) {
                                val page = runCatching {
                                    loadCatalogPage(source, language)
                                }.getOrElse {
                                    synchronized(resultLock) { hadFailure = true }
                                    emptyList()
                                }
                                if (page.isNotEmpty()) {
                                    synchronized(resultLock) {
                                        results += page
                                        canLoadMore = canLoadMore || page.size >= limit
                                    }
                                    onSourcePageLoaded(page)
                                }
                            }
                        }
                    }
            } else {
                languages.ifEmpty { listOf("") }.map { language ->
                    launch(Dispatchers.IO) {
                        val page = runCatching {
                            loadCatalogPage(source = null, language = language)
                        }.getOrElse {
                            synchronized(resultLock) { hadFailure = true }
                            emptyList()
                        }
                        if (page.isNotEmpty()) {
                            synchronized(resultLock) {
                                results += page
                                canLoadMore = canLoadMore || page.size >= limit
                            }
                            onSourcePageLoaded(page)
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
            synchronized(resultLock) {
                RemoteDiscoverLoadResult(
                    books = results.toList(),
                    canLoadMore = canLoadMore,
                    hadFailure = hadFailure,
                )
            }
        }

    private fun DiscoverCacheState.canUseWithoutRefresh(nowMillis: Long): Boolean {
        if (loadedAtMillis <= 0L || results.isEmpty()) return false
        val age = nowMillis - loadedAtMillis
        return if (isDefaultBrowse) {
            results.size >= DefaultBrowseMinUsefulResults && age <= DefaultBrowseCacheTtlMillis
        } else {
            age <= SearchCacheTtlMillis
        }
    }

    private fun discoverJobKey(
        query: String,
        field: CatalogSearchField,
        languageKey: String,
        sourcePreference: BookSourcePreference,
        sortOrder: DiscoverSortOrder,
        reset: Boolean,
        offset: Int,
    ): String =
        listOf(
            "discover",
            sourcePreference.preferenceValue,
            languageKey,
            sortOrder.name,
            field.name,
            query,
            reset.toString(),
            offset.toString(),
        ).joinToString("|")

    private fun dailyFeaturedJobKey(dayKey: Long, sourcePreference: BookSourcePreference): String =
        listOf("featured", sourcePreference.preferenceValue, dayKey.toString()).joinToString("|")

    private fun cachedDailyFeaturedMemoryState(
        dayKey: Long,
        sourcePreference: BookSourcePreference,
    ): DailyFeaturedCacheState? =
        synchronized(dailyFeaturedMemoryCacheLock) {
            dailyFeaturedMemoryCache[dailyFeaturedJobKey(dayKey, sourcePreference)]
        }
            ?.takeIf { it.books.isNotEmpty() }

    private fun rememberDailyFeaturedState(state: DailyFeaturedCacheState) {
        if (state.books.isEmpty()) return
        synchronized(dailyFeaturedMemoryCacheLock) {
            dailyFeaturedMemoryCache[dailyFeaturedJobKey(state.dayKey, state.bookSource)] =
                state.copy(isLoading = false, errorMessage = null)
        }
    }

    private companion object {
        const val MaxDiscoverQueryLength = 120
        const val MaxRawCatalogQueryLength = 240
        const val DefaultBrowseMinUsefulResults = 8
        const val DefaultBrowseCacheTtlMillis = 30L * 60L * 1000L
        const val SearchCacheTtlMillis = 30L * 60L * 1000L
        const val VisibleDetailHydrationLimit = 20
        const val DiscoverResultDetailHydrationLimit = 50
    }
}

private fun AudioBook.supportsDetailHydration(): Boolean =
    id.isNotBlank() &&
        (
            source == BookSource.LibriVox ||
                source == BookSource.Lit2Go ||
                source == BookSource.WolneLektury ||
                source == BookSource.Gutendex
            )

private fun AudioBook.needsDetailHydration(): Boolean =
    supportsDetailHydration() &&
        (
            chapters.isEmpty() ||
                description.isBlank() ||
                description.startsWith("Featured LibriVox audiobook") ||
                description.startsWith("A LibriVox recording read")
            )

private fun List<String>.forSource(source: CatalogSourcePreference): List<String> {
    if (isEmpty() || any { it.isBlank() }) return listOf("")
    return when (source) {
        CatalogSourcePreference.WolneLektury -> filter { it.equals("Polish", ignoreCase = true) }
        CatalogSourcePreference.LibriVox,
        CatalogSourcePreference.Lit2Go,
        CatalogSourcePreference.Gutendex -> filterNot { it.equals("Polish", ignoreCase = true) }
    }.distinct()
}

private fun discoverFallbackBooks(
    libraryState: LibraryState,
    languages: List<String>,
    sortOrder: DiscoverSortOrder,
    sourcePreference: BookSourcePreference,
): List<AudioBook> {
    val libraryBooks = libraryState.books
        .filterBySourcePreference(sourcePreference)
        .filter { it.isDiscoverPlayableResult() }
        .filterByLanguagePreference(languages)
        .distinctBy { it.id }
        .sortedForDiscover(sortOrder)
    return libraryBooks.ifEmpty {
        AudioBookLibrary.seededBooks
            .filterByLanguagePreference(languages)
            .sortedForDiscover(sortOrder)
    }
}

internal fun discoverLocalSearchBooks(
    libraryState: LibraryState,
    query: String,
    field: CatalogSearchField,
    languages: List<String>,
    sourcePreference: BookSourcePreference = BookSourcePreference.Default,
): List<AudioBook> {
    val normalizedQuery = query.normalizedDiscoverSearchText()
    if (normalizedQuery.isBlank()) return emptyList()
    return (libraryState.books + AudioBookLibrary.seededBooks)
        .filterBySourcePreference(sourcePreference)
        .dedupeCatalogBooks()
        .filter { it.isDiscoverPlayableResult() }
        .filterByLanguagePreference(languages)
        .filter { it.matchesLocalSearch(normalizedQuery, field) }
        .rankForCatalogSearch(normalizedQuery, field)
}

private fun AudioBook.isDiscoverPlayableResult(): Boolean =
    source != BookSource.Gutendex || chapters.any { it.isPlayable() }

private fun List<AudioBook>.filterBySourcePreference(sourcePreference: BookSourcePreference): List<AudioBook> =
    filter { book ->
        when (book.source) {
            BookSource.LibriVox -> CatalogSourcePreference.LibriVox in sourcePreference.enabledSources
            BookSource.Lit2Go -> CatalogSourcePreference.Lit2Go in sourcePreference.enabledSources
            BookSource.WolneLektury -> CatalogSourcePreference.WolneLektury in sourcePreference.enabledSources
            BookSource.Gutendex -> CatalogSourcePreference.Gutendex in sourcePreference.enabledSources
            BookSource.LocalAsset,
            BookSource.CustomLocal -> true
        }
    }

private fun List<AudioBook>.filterByLanguagePreference(languages: List<String>): List<AudioBook> {
    if (languages.any { it.isBlank() }) return this
    val selected = languages.map { it.lowercase(Locale.ROOT) }.toSet()
    return filter { book -> book.language?.lowercase(Locale.ROOT) in selected }
}

fun Set<BookLanguagePreference>.discoverLanguageSummary(
    source: BookSourcePreference,
): String {
    if (isAllCatalogLanguagesSelected(source)) return "all selected languages"
    val labels = normalizeForCatalogSource(source).map { it.label }
    return when (labels.size) {
        0 -> "selected languages"
        1 -> labels.single()
        2 -> labels.joinToString(" and ")
        else -> "${labels.take(2).joinToString(", ")} and ${labels.size - 2} more"
    }
}

internal fun List<AudioBook>.sortedForDiscover(
    sortOrder: DiscoverSortOrder,
    query: String = "",
    field: CatalogSearchField = CatalogSearchField.All,
): List<AudioBook> =
    if (query.isNotBlank()) {
        rankForCatalogSearch(query, field)
    } else {
        sortedForBrowse(sortOrder)
    }

private fun List<AudioBook>.sortedForBrowse(sortOrder: DiscoverSortOrder): List<AudioBook> =
    when (sortOrder) {
        DiscoverSortOrder.Alphabetical -> sortedWith(
            compareBy<AudioBook> { it.title.normalizedDiscoverSortText() }
                .thenBy { it.author.normalizedDiscoverSortText() }
                .thenBy { it.id },
        )
        DiscoverSortOrder.MostPopular -> if (any { it.sourcePopularityRank != null }) {
            mapIndexed { index, book -> IndexedValue(index, book) }
                .sortedWith(
                    compareBy<IndexedValue<AudioBook>> { it.value.sourcePopularityRank ?: Int.MAX_VALUE }
                        .thenBy { it.index },
                )
                .map { it.value }
        } else {
            this
        }
    }

private fun String.normalizedDiscoverSortText(): String =
    lowercase(Locale.ROOT)
        .removePrefix("the ")
        .removePrefix("a ")
        .removePrefix("an ")
        .trim()

private fun AudioBook.matchesLocalSearch(
    normalizedQuery: String,
    field: CatalogSearchField,
): Boolean {
    fun String.matchesQuery(): Boolean = normalizedDiscoverSearchText().contains(normalizedQuery)
    fun Iterable<String>.anyMatches(): Boolean = any { it.matchesQuery() }
    fun Iterable<String>.anyTranslatedMatches(): Boolean =
        any { value -> CatalogGenreTranslations.searchTermsForName(value).any { it.matchesQuery() } }
    fun Iterable<com.librivox.mobile.model.CatalogTag>.anyTagMatches(): Boolean =
        any { tag ->
            CatalogGenreTranslations.searchTermsForName(tag.name).any { it.matchesQuery() } ||
                tag.slug.matchesQuery()
        }

    return when (field) {
        CatalogSearchField.All -> catalogSearchMatch(normalizedQuery, CatalogSearchField.All) != null
        CatalogSearchField.Title -> title.matchesQuery()
        CatalogSearchField.Chapter -> chapters.any { it.title.matchesQuery() }
        CatalogSearchField.Author -> author.matchesQuery() || authorTags.anyTagMatches()
        CatalogSearchField.Reader -> narrators.anyMatches() ||
            chapters.any { it.reader.orEmpty().matchesQuery() }
        CatalogSearchField.Genre -> genres.anyTranslatedMatches() || literaryGenres.anyTagMatches()
        CatalogSearchField.Epoch -> literaryEpochs.anyTagMatches()
        CatalogSearchField.Kind -> literaryKinds.anyTagMatches()
    }
}

private fun String.normalizedDiscoverSearchText(): String =
    normalizedCatalogSearchText(this)
