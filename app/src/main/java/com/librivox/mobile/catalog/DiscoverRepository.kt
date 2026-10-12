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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    val sourcePriority: List<CatalogSourcePreference> = DefaultCatalogSourcePriority,
)

private data class RemoteDiscoverLoadResult(
    val books: List<AudioBook>,
    val hits: List<CatalogSearchHit>,
    val outcomes: Map<CatalogPlannedOperation, CatalogOperationOutcome>,
    val canLoadMore: Boolean,
    val hadFailure: Boolean,
    val isComplete: Boolean,
    val canPersistAsFresh: Boolean,
)

class DiscoverRepository(
    private val scope: CoroutineScope,
    private val catalogClient: CatalogSource,
    private val catalogCache: CatalogAutocompleteRepository,
    private val libraryRepository: LibraryRepository,
    private val settings: PlaybackSettingsStore,
    connectivity: CatalogConnectivity = CatalogConnectivity { true },
    private val sourceHealthProbe: CatalogSourceHealthProbe? = null,
) {
    private val searchCoordinator = CatalogSearchCoordinator(
        cache = catalogCache,
        connectivity = connectivity,
    )
    private val _discoverCache = MutableStateFlow(DiscoverCacheState())
    val discoverCache: StateFlow<DiscoverCacheState> = _discoverCache.asStateFlow()

    private val _dailyFeaturedCache = MutableStateFlow(DailyFeaturedCacheState())
    val dailyFeaturedCache: StateFlow<DailyFeaturedCacheState> = _dailyFeaturedCache.asStateFlow()

    private val _sourceHealth = MutableStateFlow(CatalogSourceHealthState())
    val sourceHealth: StateFlow<CatalogSourceHealthState> = _sourceHealth.asStateFlow()

    private val discoverLoadLock = Any()
    private var discoverLoadGeneration = 0L
    private var activeDiscoverJob: Job? = null
    private var activeDiscoverJobKey: String? = null
    private var lastDiscoverRequest: DiscoverLoadRequest? = null
    private val sourceHealthLifecycleLock = Any()
    private var activeSourceHealthJob: Job? = null
    private var activeSourceHealthKey: Set<CatalogSourcePreference> = emptySet()
    private var sourceHealthGeneration: Long = 0L
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

    private fun invalidateDiscoverLoad() = synchronized(discoverLoadLock) {
        discoverLoadGeneration++
        activeDiscoverJob?.cancel()
        activeDiscoverJob = null
        activeDiscoverJobKey = null
    }

    private fun ownsDiscoverLoad(generation: Long): Boolean = synchronized(discoverLoadLock) {
        discoverLoadGeneration == generation
    }

    private suspend fun publishDiscoverState(
        generation: Long,
        update: (DiscoverCacheState) -> DiscoverCacheState,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        return synchronized(discoverLoadLock) {
            if (discoverLoadGeneration != generation) return@synchronized false
            _discoverCache.value = update(_discoverCache.value)
            true
        }
    }

    fun clearDiscoverState() {
        invalidateDiscoverLoad()
        cancelActiveSourceHealthProbe()
        synchronized(detailHydrationLock) {
            activeVisibleDetailHydrationJobs.forEach { it.cancel() }
            activeVisibleDetailHydrationJobs.clear()
        }
        lastDiscoverRequest = null
        _discoverCache.value = DiscoverCacheState()
    }

    fun resetSourceHealth() {
        synchronized(sourceHealthLifecycleLock) {
            sourceHealthGeneration++
            activeSourceHealthJob?.cancel()
            activeSourceHealthJob = null
            activeSourceHealthKey = emptySet()
            _sourceHealth.value = CatalogSourceHealthState()
        }
    }

    fun pauseForOffline() {
        val offlineReplay = lastDiscoverRequest?.copy(reset = true, offset = 0)
        invalidateDiscoverLoad()
        activeDailyFeaturedJob?.cancel()
        activeDailyFeaturedJob = null
        activeDailyFeaturedJobKey = null
        cancelActiveSourceHealthProbe()
        _discoverCache.value = _discoverCache.value.copy(
            isLoading = false,
            loadingReset = false,
            errorMessage = null,
        )
        _dailyFeaturedCache.value = _dailyFeaturedCache.value.copy(
            isLoading = false,
            errorMessage = null,
        )
        // Connectivity has already flipped before this callback. Replaying the latest immutable
        // request therefore reads fresh/stale operation cache only and cannot start new HTTP work.
        // This closes the race where cancellation happened after the UI's offline effect.
        offlineReplay?.let(::loadDiscoverPage)
    }

    fun prepareForCatalogRetry() {
        val sourcesToRetry = settings.bookSourcePreference.enabledSources
        resetSourceHealth()
        sourceHealthProbe?.invalidate(sourcesToRetry)
        invalidateDiscoverLoad()
        activeDailyFeaturedJob?.cancel()
        activeDailyFeaturedJobKey = null
        _discoverCache.value = _discoverCache.value.copy(
            isLoading = false,
            loadingReset = false,
            errorMessage = null,
            loadedAtMillis = 0L,
        )
        _dailyFeaturedCache.value = _dailyFeaturedCache.value.copy(
            isLoading = false,
            errorMessage = null,
            loadedAtMillis = 0L,
        )
        probeSourceHealth(sourcesToRetry, force = true)
    }

    /** Refreshes stable source roots even when a fresh search snapshot avoids catalog requests. */
    internal fun checkSourceRoots(
        sources: Collection<CatalogSourcePreference>,
        force: Boolean = false,
    ) {
        probeSourceHealth(sources, force)
    }

    fun loadDiscoverPage(request: DiscoverLoadRequest) {
        lastDiscoverRequest = request
        val normalizedQuery = sanitizeCatalogSearchQuery(request.query).take(MaxDiscoverQueryLength)
        val isDefaultBrowse = normalizedQuery.isBlank()
        val offset = if (request.reset) 0 else request.offset.coerceAtLeast(0)
        val sourcePreference = request.sourcePreference
        val languageKey = request.languageKey
        val current = _discoverCache.value
        if (request.reset && current.canReuseInMemory(request, normalizedQuery)) {
            // A fresh A snapshot may still be visible while B is starting. Returning to A must
            // revoke B's ownership even though no new network request is needed.
            invalidateDiscoverLoad()
            _discoverCache.value = current
            return
        }
        if (sourcePreference.enabledSources.isEmpty()) {
            invalidateDiscoverLoad()
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
                sourcePriority = request.sourcePriority,
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
            sourcePriority = request.sourcePriority,
        )
        synchronized(discoverLoadLock) {
            if (activeDiscoverJob?.isActive == true && activeDiscoverJobKey == jobKey) return
            activeDiscoverJob?.cancel()
            val generation = ++discoverLoadGeneration
            activeDiscoverJobKey = jobKey
            activeDiscoverJob = scope.launch {
                runDiscoverLoad(
                    request = request,
                    normalizedQuery = normalizedQuery,
                    isDefaultBrowse = isDefaultBrowse,
                    offset = offset,
                    languageKey = languageKey,
                    generation = generation,
                )
            }
        }
    }

    fun loadDailyFeatured(
        sourcePreference: BookSourcePreference,
        dayKey: Long,
        limit: Int,
        forceRefresh: Boolean = false,
    ) {
        // Featured-data refreshes do not own root retry. The application reconnect gate does.
        probeSourceHealth(sourcePreference.enabledSources)
        val availableSourcePreference = _sourceHealth.value.availablePreference(sourcePreference)
        val featuredSource = availableSourcePreference.featuredCatalogSource()
        if (featuredSource == null) {
            activeDailyFeaturedJob?.cancel()
            activeDailyFeaturedJobKey = null
            _dailyFeaturedCache.value = DailyFeaturedCacheState(
                dayKey = dayKey,
                bookSource = sourcePreference,
                errorMessage = if (sourcePreference.enabledSources.isEmpty()) {
                    null
                } else {
                    "Selected catalog sources are unavailable right now."
                },
                featuredSource = null,
            )
            return
        }
        val singleSourcePreference = featuredSource.asSingleSourcePreference()
        val jobKey = dailyFeaturedJobKey(dayKey, singleSourcePreference)
        val current = _dailyFeaturedCache.value
        if (
            !forceRefresh &&
            current.matches(dayKey, singleSourcePreference, featuredSource) &&
            (current.books.isNotEmpty() || current.isLoading)
        ) {
            return
        }
        if (activeDailyFeaturedJob?.isActive == true && activeDailyFeaturedJobKey == jobKey) return
        if (!forceRefresh) {
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
        }
        activeDailyFeaturedJob?.cancel()
        activeDailyFeaturedJobKey = jobKey
        activeDailyFeaturedJob = scope.launch {
            val cacheEnabled = searchCachingEnabled
            val cached = if (!forceRefresh && cacheEnabled) {
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

            val candidatesResult = runCatching {
                (catalogClient as? AggregateCatalogClient)
                    ?.featuredBooksSourceOrThrow(featuredSource)
                    ?: catalogClient.featuredBooks()
            }
            val candidates = candidatesResult
                .onSuccess { markSourceAvailable(featuredSource) }
                .onFailure { markSourceUnavailable(featuredSource, it) }
                .getOrDefault(emptyList())
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
                errorMessage = when {
                    candidatesResult.isFailure -> "${featuredSource.label} is unavailable right now."
                    selected.isEmpty() -> "Could not load today's picks."
                    else -> null
                },
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
        generation: Long,
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
            sourcePriority = request.sourcePriority,
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
            isDefaultBrowse &&
            cachedState.sourcePriority == request.sourcePriority &&
            cachedState.canUseWithoutRefresh(System.currentTimeMillis()) &&
            ownsDiscoverLoad(generation)
        ) {
            publishDiscoverState(generation) { cachedState }
            return
        }

        if (!publishDiscoverState(generation) { current ->
                if (request.reset) {
                    cachedState?.copy(isLoading = true, loadingReset = true, errorMessage = null)
                        ?: current.takeIf {
                            it.describes(
                                query = normalizedQuery,
                                field = request.field,
                                language = languageKey,
                                bookSource = request.sourcePreference,
                                sortOrder = request.sortOrder,
                            ) && it.results.isNotEmpty()
                        }?.copy(isLoading = true, loadingReset = true, errorMessage = null)
                        ?: loadingBase
                } else {
                    current.copy(isLoading = true, loadingReset = false, errorMessage = null)
                }
            }
        ) return

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
            if (!publishDiscoverState(generation) {
                    loadingBase.copy(
                        results = cachedInterim,
                        canLoadMore = !isDefaultBrowse || cachedInterim.size >= request.pageSize,
                    )
                }
            ) return
        }

        // A prior transient source failure must not permanently remove that source from later
        // plans. Every query gets a fresh capability-aware plan; the per-operation cache handles
        // fresh/stale reuse without suppressing network retries.
        val remoteSourcePreference = request.sourcePreference
        val searchPlan = CatalogSearchPlanner.plan(
            query = normalizedQuery,
            field = request.field,
            languages = request.languages,
            sourcePreference = remoteSourcePreference,
            sourcePriority = request.sourcePriority,
            offset = offset,
            pageSize = request.pageSize,
            mode = if (isDefaultBrowse) CatalogSearchMode.Browse else CatalogSearchMode.Search,
        )
        val plannedSearchProgress = if (isDefaultBrowse) {
            emptyList()
        } else {
            searchPlan.progressItems()
        }
        if (ownsDiscoverLoad(generation) && plannedSearchProgress.isNotEmpty()) {
            if (!publishDiscoverState(generation) { it.copy(searchProgressItems = plannedSearchProgress) }) return
        }
        val catalogQuery = request.query.trim().take(MaxRawCatalogQueryLength).ifBlank { normalizedQuery }
        try {
            val partialBaseResults = if (request.reset) {
                cachedInterim
            } else {
                _discoverCache.value.results
            }
            val previousVariants = if (request.reset) {
                emptyList()
            } else {
                _discoverCache.value.searchResultVariants
            }
            val previousHits = if (request.reset) {
                emptyList()
            } else {
                _discoverCache.value.searchHits
            }
            val partialLock = Any()
            var partialRemoteResults = emptyList<AudioBook>()
            var partialHits = emptyList<CatalogSearchHit>()
            var partialSearchProgress = plannedSearchProgress
            suspend fun publishSnapshot(snapshot: CatalogSearchSnapshot) {
                if (!ownsDiscoverLoad(generation)) return
                val (nextRemoteResults, nextProgress) = synchronized(partialLock) {
                    partialHits = snapshot.hits
                    partialRemoteResults = snapshot.hits.map { it.book }
                    partialSearchProgress = snapshot.progressItems()
                    partialRemoteResults to partialSearchProgress
                }
                val sorted = withContext(Dispatchers.Default) {
                    mergeCatalogBooksBySourcePriority(
                        partialBaseResults + nextRemoteResults,
                        request.sourcePriority,
                    )
                        .sortedForDiscover(request.sortOrder, normalizedQuery, request.field)
                }
                publishDiscoverState(generation) {
                    loadingBase.copy(
                        results = sorted,
                        nextOffset = offset,
                        canLoadMore = true,
                        isLoading = true,
                        loadingReset = request.reset,
                        errorMessage = null,
                        searchProgressItems = nextProgress,
                        searchResultVariants = previousVariants + nextRemoteResults,
                        searchHits = previousHits + partialHits,
                        isComplete = false,
                    )
                }
            }

            val remoteLoad = withContext(Dispatchers.IO) {
                loadRemoteDiscoverPage(
                    query = catalogQuery,
                    isDefaultBrowse = isDefaultBrowse,
                    limit = request.pageSize,
                    offset = offset,
                    plan = searchPlan,
                    onSnapshot = ::publishSnapshot,
                )
            }
            val page = remoteLoad.books
            if (!ownsDiscoverLoad(generation)) return
            val retainInterimAfterFailure = request.reset &&
                cachedInterim.isNotEmpty() &&
                remoteLoad.hadFailure
            val retainedInterim = if (retainInterimAfterFailure) cachedInterim else emptyList()
            val retainedInterimHits = retainedInterim.map { book ->
                book.asInterimCatalogHit(request.field)
            }
            val browseFallback = if (request.reset && page.isEmpty() && isDefaultBrowse) {
                discoverFallbackBooks(
                    librarySnapshot,
                    request.languages,
                    request.sortOrder,
                    request.sourcePreference,
                )
            } else {
                emptyList()
            }
            val browseFallbackHits = browseFallback.map { it.asInterimCatalogHit(request.field) }
            val pageResults = when {
                browseFallback.isNotEmpty() -> browseFallback
                retainInterimAfterFailure -> retainedInterim + page
                else -> page
            }
            val currentResults = _discoverCache.value.results
            val merged = withContext(Dispatchers.Default) {
                if (request.reset) {
                    mergeCatalogBooksBySourcePriority(pageResults, request.sourcePriority)
                        .sortedForDiscover(request.sortOrder, normalizedQuery, request.field)
                } else {
                    mergeCatalogBooksBySourcePriority(
                        currentResults + pageResults,
                        request.sourcePriority,
                    )
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
                sourcePriority = request.sourcePriority,
                searchProgressItems = synchronized(partialLock) { partialSearchProgress },
                searchResultVariants = if (request.reset) {
                    (browseFallback + retainedInterim + page).distinctBy { "${it.source}:${it.id}" }
                } else {
                    previousVariants + page
                },
                searchHits = if (request.reset) {
                    (browseFallbackHits + retainedInterimHits + remoteLoad.hits).distinctBy {
                        "${it.book.source}:${it.book.id}"
                    }
                } else {
                    previousHits + remoteLoad.hits
                },
                isComplete = remoteLoad.isComplete,
                errorMessage = if (merged.isEmpty() && remoteLoad.hadFailure) {
                    "Could not reach audiobook catalogs"
                } else {
                    null
                },
            )
            if (cacheEnabled) {
                libraryRepository.upsertCatalogBooks(merged)
                catalogCache.upsertBooks(merged)
                // A partial response is useful on screen but can never become a fresh aggregate
                // snapshot. Successful operation entries were already cached independently.
                if (!remoteLoad.hadFailure && remoteLoad.isComplete && remoteLoad.canPersistAsFresh) {
                    catalogCache.saveDiscoverState(finalState)
                }
            }
            publishDiscoverState(generation) { finalState }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            if (!ownsDiscoverLoad(generation)) return
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
                publishDiscoverState(generation) { finalState }
            } else {
                publishDiscoverState(generation) { current ->
                    current.copy(
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
        isDefaultBrowse: Boolean,
        limit: Int,
        offset: Int,
        plan: CatalogSearchPlan,
        onSnapshot: suspend (CatalogSearchSnapshot) -> Unit,
    ): RemoteDiscoverLoadResult {
        val aggregateClient = catalogClient as? AggregateCatalogClient
        val finalSnapshot = searchCoordinator.execute(
            plan = plan,
            loader = CatalogOperationLoader { planned ->
                val source = planned.operation.source
                val operationField = planned.operation.field
                val loaded = if (isDefaultBrowse) {
                    if (aggregateClient != null) {
                        aggregateClient.browseSource(
                            source = source,
                            limit = limit,
                            offset = offset,
                            language = planned.language,
                        )
                    } else {
                        catalogClient.browse(
                            limit = limit,
                            offset = offset,
                            language = planned.language,
                        )
                    }
                } else if (aggregateClient != null) {
                    aggregateClient.searchSource(
                        source = source,
                        query = query,
                        field = operationField,
                        limit = limit,
                        offset = offset,
                        language = planned.language,
                    )
                } else {
                    catalogClient.search(
                        query = query,
                        field = operationField,
                        limit = limit,
                        offset = offset,
                        language = planned.language,
                    )
                }
                if (isDefaultBrowse && offset == 0 && loaded.isEmpty()) {
                    throw CatalogSourceUnavailableException(
                        "${source.label} did not return catalog content.",
                    )
                }
                loaded
            },
            onSnapshot = onSnapshot,
        )
        // Root health and field outcomes are separate signals. With a root probe installed, a
        // successful field must not overwrite a broken root and a failed field must not make a
        // reachable source look wholly broken. The field failure stays in searchProgressItems.
        if (sourceHealthProbe == null) {
            val outcomesBySource = finalSnapshot.outcomes.entries.groupBy { it.key.operation.source }
            outcomesBySource.forEach { (source, entries) ->
                when {
                    entries.any {
                        it.value is CatalogOperationOutcome.Success ||
                            it.value == CatalogOperationOutcome.AuthoritativeEmpty
                    } -> markSourceAvailable(source)
                    entries.all { it.value is CatalogOperationOutcome.Failed } &&
                        entries.none {
                            (it.value as? CatalogOperationOutcome.Failed)?.error == OfflineCatalogError
                        } -> {
                        val failure = entries.firstNotNullOfOrNull { (_, outcome) ->
                            (outcome as? CatalogOperationOutcome.Failed)?.error
                        }
                        _sourceHealth.value = _sourceHealth.value.markUnavailable(source, failure)
                    }
                }
            }
        }
        val hadFailure = finalSnapshot.outcomes.values.any { it is CatalogOperationOutcome.Failed }
        return RemoteDiscoverLoadResult(
            books = finalSnapshot.hits.map { it.book },
            hits = finalSnapshot.hits,
            outcomes = finalSnapshot.outcomes,
            canLoadMore = finalSnapshot.hits.size >= limit,
            hadFailure = hadFailure,
            isComplete = finalSnapshot.isComplete,
            canPersistAsFresh = finalSnapshot.canPersistAsFresh,
        )
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

    private fun markSourceAvailable(source: CatalogSourcePreference) {
        if (sourceHealthProbe != null) return
        _sourceHealth.value = _sourceHealth.value.markAvailable(source)
    }

    private fun markSourceUnavailable(source: CatalogSourcePreference, throwable: Throwable) {
        if (sourceHealthProbe != null) return
        _sourceHealth.value = _sourceHealth.value.markUnavailable(
            source = source,
            message = throwable.message?.takeIf { it.isNotBlank() },
        )
    }

    private fun probeSourceHealth(
        sources: Collection<CatalogSourcePreference>,
        force: Boolean = false,
    ) {
        val probe = sourceHealthProbe ?: return
        val requested = sources.toSet()
        if (requested.isEmpty()) return
        synchronized(sourceHealthLifecycleLock) {
            val activeProbe = activeSourceHealthJob?.isActive == true
            if (!force && activeProbe && requested.all(activeSourceHealthKey::contains)) return
            val selected = if (!force && activeProbe) activeSourceHealthKey + requested else requested
            if (force || activeProbe) {
                activeSourceHealthJob?.cancel()
            }
            sourceHealthGeneration++
            val generation = sourceHealthGeneration
            activeSourceHealthKey = selected
            activeSourceHealthJob = scope.launch {
                probe.probe(selected, force = force) { status ->
                    synchronized(sourceHealthLifecycleLock) {
                        if (generation == sourceHealthGeneration) {
                            _sourceHealth.update { it.withStatus(status) }
                        }
                    }
                }
            }
        }
    }

    private fun cancelActiveSourceHealthProbe() {
        synchronized(sourceHealthLifecycleLock) {
            sourceHealthGeneration++
            activeSourceHealthJob?.cancel()
            activeSourceHealthJob = null
            activeSourceHealthKey = emptySet()
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
        sourcePriority: List<CatalogSourcePreference>,
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
            sourcePriority.joinToString(",") { it.name },
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
        const val OfflineCatalogError = "Offline"
    }
}

private fun AudioBook.asInterimCatalogHit(field: CatalogSearchField): CatalogSearchHit {
    val source = when (source) {
        BookSource.LibriVox -> CatalogSourcePreference.LibriVox
        BookSource.Lit2Go -> CatalogSourcePreference.Lit2Go
        BookSource.Gutendex -> CatalogSourcePreference.Gutendex
        BookSource.WolneLektury -> CatalogSourcePreference.WolneLektury
        BookSource.LocalAsset,
        BookSource.CustomLocal -> null
    }
    val bundledIds = AudioBookLibrary.seededBooks.asSequence().map { it.id }.toSet()
    return CatalogSearchHit(
        book = this,
        matchedFields = setOf(field),
        contributingSources = setOfNotNull(source),
        provenance = if (id in bundledIds) {
            CatalogResultProvenance.BundledFallback
        } else {
            CatalogResultProvenance.StaleCache
        },
    )
}

internal fun DiscoverCacheState.canReuseInMemory(
    request: DiscoverLoadRequest,
    normalizedQuery: String,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (
        !matches(
            query = normalizedQuery,
            field = request.field,
            language = request.languageKey,
            bookSource = request.sourcePreference,
            sortOrder = request.sortOrder,
        ) ||
        sourcePriority != request.sourcePriority ||
        !isComplete ||
        isLoading ||
        errorMessage != null
    ) {
        return false
    }
    val age = (nowMillis - loadedAtMillis).coerceAtLeast(0L)
    val ttl = if (results.isEmpty()) CatalogEmptyFreshMillis else CatalogPositiveFreshMillis
    return age <= ttl
}

internal fun catalogSearchOperations(
    sourcePreference: BookSourcePreference,
    field: CatalogSearchField,
): List<CatalogSearchOperation> =
    CatalogSourcePreference.entries
        .filter { it in sourcePreference.enabledSources }
        .flatMap { source ->
            val fields = if (field == CatalogSearchField.All) {
                listOf(
                    CatalogSearchField.Title,
                    CatalogSearchField.Author,
                    CatalogSearchField.Reader,
                    CatalogSearchField.Chapter,
                    CatalogSearchField.Genre,
                )
            } else {
                listOf(field)
            }
            fields.map { operationField -> CatalogSearchOperation(source, operationField) }
        }

private fun mergeCatalogBooksBySourcePriority(
    books: List<AudioBook>,
    sourcePriority: List<CatalogSourcePreference>,
): List<AudioBook> {
    val mappedPriority = sourcePriority.map { source ->
        when (source) {
            CatalogSourcePreference.LibriVox -> BookSource.LibriVox
            CatalogSourcePreference.Lit2Go -> BookSource.Lit2Go
            CatalogSourcePreference.Gutendex -> BookSource.Gutendex
            CatalogSourcePreference.WolneLektury -> BookSource.WolneLektury
        }
    }
    val remote = books.filter {
        it.source != BookSource.LocalAsset && it.source != BookSource.CustomLocal
    }
    return BookMerger.mergeBySourcePriority(remote, mappedPriority) + books.filter {
        it.source == BookSource.LocalAsset || it.source == BookSource.CustomLocal
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
                needsWolneChapterNames() ||
                description.isBlank() ||
                description.startsWith("Featured LibriVox audiobook") ||
                description.startsWith("A LibriVox recording read")
            )

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
