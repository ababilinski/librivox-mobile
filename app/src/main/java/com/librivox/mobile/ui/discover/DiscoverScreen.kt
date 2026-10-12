package com.librivox.mobile.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogSearchFacetCounts
import com.librivox.mobile.catalog.CatalogSearchPlanner
import com.librivox.mobile.catalog.CatalogSearchProgressItem
import com.librivox.mobile.catalog.CatalogSourceHealth
import com.librivox.mobile.catalog.CatalogSourceHealthState
import com.librivox.mobile.catalog.CatalogOperationOutcome
import com.librivox.mobile.catalog.CatalogOperationSkipReason
import com.librivox.mobile.catalog.CatalogGenreTranslations
import com.librivox.mobile.catalog.DiscoverLoadRequest
import com.librivox.mobile.catalog.DiscoverCacheState
import com.librivox.mobile.catalog.DiscoverSortOrder
import com.librivox.mobile.catalog.BookMerger
import com.librivox.mobile.catalog.catalogLanguageCacheKey
import com.librivox.mobile.catalog.catalogLanguageRequestFilters
import com.librivox.mobile.catalog.catalogSearchMatch
import com.librivox.mobile.catalog.catalogSearchFacetCounts
import com.librivox.mobile.catalog.catalogSearchFacetCountsFromHits
import com.librivox.mobile.catalog.dedupeCatalogBooks
import com.librivox.mobile.catalog.filterForCatalogSearch
import com.librivox.mobile.catalog.normalizedCatalogSearchText
import com.librivox.mobile.catalog.rankForCatalogSearch
import com.librivox.mobile.catalog.sanitizeCatalogSearchQuery
import com.librivox.mobile.catalog.resolveCatalogSearchSourcePreference
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.activeDownloadChapterCount
import com.librivox.mobile.model.authorSearchQuery
import com.librivox.mobile.model.donationLink
import com.librivox.mobile.model.downloadProgressFraction
import com.librivox.mobile.model.downloadedChapterCount
import com.librivox.mobile.model.hasDownloadedChapters
import com.librivox.mobile.model.isDownloaded
import com.librivox.mobile.model.isPlayable
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.playback.isAllCatalogLanguagesSelected
import com.librivox.mobile.playback.normalizeForCatalogSource
import com.librivox.mobile.playback.selectableLanguages
import com.librivox.mobile.ui.components.BookCardPressOverlay
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.BookSourceInfoRow
import com.librivox.mobile.ui.components.bookCardPressBorder
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.coverArtAspectRatio
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.rememberBookCardPressFeedback
import com.librivox.mobile.ui.components.rememberCoverArtDisplayMode
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.navigation.BookSharedElementSource
import com.librivox.mobile.ui.navigation.BookSharedElementType
import com.librivox.mobile.ui.navigation.BookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalActiveBookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.Routes
import com.librivox.mobile.ui.navigation.DiscoverSearchRequest
import com.librivox.mobile.ui.navigation.bookSharedBounds
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CatalogPageSize = 50
private const val DiscoverResultDetailHydrationLimit = 50
private const val DiscoverDetailHydrationDelayMillis = 4_000L
private const val MaxRecentSearches = 5
private const val MaxSearchSuggestions = 5
private const val SearchDebounceMillis = 350L
private const val SearchPositiveSnapshotFreshMillis = 30L * 60L * 1000L
private const val SearchEmptySnapshotFreshMillis = 5L * 60L * 1000L

private val PopularGenres = listOf(
    "Mystery",
    "Romance",
    "Adventure",
    "Children",
    "Poetry",
    "History",
    "Science",
    "Philosophy",
    "Short stories",
    "Novel",
    "Poem",
    "Fairy tale",
)

private val SearchFields = listOf(
    CatalogSearchField.All,
    CatalogSearchField.Title,
    CatalogSearchField.Chapter,
    CatalogSearchField.Author,
    CatalogSearchField.Reader,
    CatalogSearchField.Genre,
    CatalogSearchField.Epoch,
    CatalogSearchField.Kind,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenBook: (String, BookSharedTransitionKey?) -> Unit,
    onOpenAuthorSearch: (AudioBook) -> Unit,
    onOpenCatalogSettings: () -> Unit,
    navigationRequest: DiscoverSearchRequest? = null,
    onNavigationRequestApplied: (String) -> Unit = {},
    isResumed: Boolean = true,
    ownsSearchRequests: () -> Boolean = { isResumed },
) {
    val graph = LocalAppGraph.current
    val activeSharedTransitionKey = LocalActiveBookSharedTransitionKey.current
    val latestActiveSharedTransitionKey = rememberUpdatedState(activeSharedTransitionKey)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val historyEntries by graph.app.searchHistoryRepository.entries.collectAsState(initial = emptyList())
    val discoverCache by graph.app.discoverRepository.discoverCache.collectAsState()
    val sourceHealth by graph.app.discoverRepository.sourceHealth.collectAsState()
    val networkStatus by graph.app.networkStatusMonitor.status.collectAsState()
    val tonal = LocalTonalSurfaces.current
    val coverArtDisplayMode = rememberCoverArtDisplayMode()

    val initialCache = remember {
        navigationRequest?.let { request ->
            DiscoverCacheState(
                query = sanitizeCatalogSearchQuery(request.query).take(120),
                searchField = request.field,
                language = request.language,
            )
        } ?: graph.app.discoverRepository.discoverCache.value
    }
    val initialSearchField = initialCache.searchField
    var searchField by rememberSaveable { mutableStateOf(initialSearchField) }
    var query by rememberSaveable { mutableStateOf(initialCache.query) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchCollapseRevision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadingReset by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sortOrder by rememberSaveable { mutableStateOf(initialCache.sortOrder) }
    var lastAppliedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSearchQuery by rememberSaveable { mutableStateOf(initialCache.query) }
    var activeSearchField by rememberSaveable { mutableStateOf(initialSearchField) }
    var preferredLanguages by remember {
        mutableStateOf(graph.app.playbackSettingsStore.preferredLanguages)
    }
    var bookSourcePreference by remember {
        mutableStateOf(graph.app.playbackSettingsStore.bookSourcePreference)
    }
    var downloadsOnlyModeEnabled by remember {
        mutableStateOf(graph.app.playbackSettingsStore.downloadsOnlyModeEnabled)
    }
    var searchSourceFilter by rememberSaveable { mutableStateOf<CatalogSourcePreference?>(null) }
    var searchLanguageFilter by rememberSaveable { mutableStateOf(initialCache.language.takeIf { it.isNotBlank() }) }
    var actionBook by remember { mutableStateOf<AudioBook?>(null) }
    var openFilterSheetSection by remember {
        mutableStateOf<DiscoverFilterSheetSection?>(null)
    }
    var autocompleteQuery by remember { mutableStateOf("") }
    var wasOnline by remember { mutableStateOf(networkStatus.isOnline) }
    var connectivityRefreshRevision by remember { mutableIntStateOf(0) }
    val browseLanguageFilters = preferredLanguages.catalogLanguageRequestFilters(bookSourcePreference)
    val availableSearchSources = CatalogSourcePreference.entries
        .filter { it in bookSourcePreference.enabledSources }
    val availableSearchLanguages = bookSourcePreference
        .selectableLanguages()
        .map { it.apiValue }
        .filter { it.isNotBlank() }
        .distinct()
    val activeLanguageFilters = if (activeSearchQuery.isBlank()) {
        browseLanguageFilters
    } else {
        searchLanguageFilter?.let(::listOf) ?: listOf("")
    }
    val activeSourcePreference = resolveCatalogSearchSourcePreference(
        query = activeSearchQuery,
        configured = bookSourcePreference,
        selectedSource = searchSourceFilter,
    )
    val activeLanguageFilterKey = activeLanguageFilters.catalogLanguageCacheKey()
    val effectiveSortOrder = sortOrder
    val matchingCache = discoverCache.takeIf {
        it.describes(activeSearchQuery, activeSearchField, activeLanguageFilterKey, activeSourcePreference, effectiveSortOrder)
    }
    val initialCacheMatchesRequest = initialCache.describes(
        activeSearchQuery, activeSearchField, activeLanguageFilterKey, activeSourcePreference, effectiveSortOrder,
    )
    var results by remember { mutableStateOf(if (initialCacheMatchesRequest) initialCache.results else emptyList()) }
    var nextOffset by remember { mutableIntStateOf(if (initialCacheMatchesRequest) initialCache.nextOffset else 0) }
    var canLoadMore by remember { mutableStateOf(!initialCacheMatchesRequest || initialCache.canLoadMore) }
    val hasPendingNavigationRequest = navigationRequest != null && navigationRequest.id != lastAppliedRequestId
    val canDispatchSearch = isResumed && !hasPendingNavigationRequest
    val noSourcesSelected = bookSourcePreference.enabledSources.isEmpty()
    val isOffline = !networkStatus.isOnline
    // Offline searches still enter the repository so fresh or stale operation snapshots can be
    // restored. Network loss itself cancels active calls in AudiobookApplication.
    val discoveryPaused = downloadsOnlyModeEnabled
    val unavailableSelectedSources = sourceHealth.unavailableSelectedSources(activeSourcePreference)
    val normalizedInputQuery = sanitizeCatalogSearchQuery(query).take(120)
    val displayedResults = remember(
        results,
        activeSearchQuery,
        activeSearchField,
        searchSourceFilter,
        searchLanguageFilter,
    ) {
        if (activeSearchQuery.isBlank()) {
            results
        } else {
            results.filterForCatalogSearch(
                query = activeSearchQuery,
                field = activeSearchField,
                source = searchSourceFilter,
                language = searchLanguageFilter,
            )
        }
    }
    val facetResultVariants = if (activeSearchQuery.isNotBlank()) {
        matchingCache?.searchResultVariants.orEmpty()
    } else {
        results
    }
    val searchFacetCounts = remember(
        facetResultVariants,
        matchingCache?.searchHits.orEmpty(),
        activeSearchQuery,
        activeSearchField,
        searchSourceFilter,
        searchLanguageFilter,
        availableSearchSources,
        availableSearchLanguages,
    ) {
        if (matchingCache?.searchHits.orEmpty().isNotEmpty()) {
            matchingCache?.searchHits.orEmpty().catalogSearchFacetCountsFromHits(
                selectedField = activeSearchField,
                selectedSource = searchSourceFilter,
                selectedLanguage = searchLanguageFilter,
                availableSources = availableSearchSources,
                availableLanguages = availableSearchLanguages,
            )
        } else {
            facetResultVariants.catalogSearchFacetCounts(
                query = activeSearchQuery,
                selectedField = activeSearchField,
                selectedSource = searchSourceFilter,
                selectedLanguage = searchLanguageFilter,
                availableSources = availableSearchSources,
                availableLanguages = availableSearchLanguages,
            )
        }
    }
    val sourceStatusItems = remember(
        sourceHealth,
        matchingCache?.searchProgressItems.orEmpty(),
        searchFacetCounts.sourceCounts,
        activeSourcePreference,
        isOffline,
    ) {
        discoverSourceStatusItems(
            sourceHealth = sourceHealth,
            progressItems = matchingCache?.searchProgressItems.orEmpty(),
            sourceCounts = searchFacetCounts.sourceCounts,
            sourcePreference = activeSourcePreference,
            isOffline = isOffline,
        )
    }
    val visibleSourceStatusItems = remember(sourceStatusItems, activeSearchQuery) {
        if (activeSearchQuery.isNotBlank()) {
            sourceStatusItems
        } else {
            sourceStatusItems.filterNot { it.status is DiscoverSourceStatusPresentation.Ready }
        }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val languageListener = graph.app.playbackSettingsStore.registerBookLanguageListener {
            preferredLanguages = it
        }
        val sourceListener = graph.app.playbackSettingsStore.registerBookSourceListener {
            bookSourcePreference = it
        }
        val downloadListener = graph.app.playbackSettingsStore.registerDownloadSettingsListener {
            downloadsOnlyModeEnabled = graph.app.playbackSettingsStore.downloadsOnlyModeEnabled
        }
        onDispose {
            graph.app.playbackSettingsStore.unregisterListener(languageListener)
            graph.app.playbackSettingsStore.unregisterListener(sourceListener)
            graph.app.playbackSettingsStore.unregisterListener(downloadListener)
        }
    }

    LaunchedEffect(
        isResumed,
        bookSourcePreference,
        availableSearchLanguages,
        availableSearchSources,
        searchField,
        searchLanguageFilter,
    ) {
        if (!isResumed || !ownsSearchRequests()) return@LaunchedEffect
        val compatibleSourceFilter = searchSourceFilter?.takeIf { source ->
            source in bookSourcePreference.enabledSources &&
                isCatalogSourceFilterOptionAvailable(
                    source = source,
                    field = searchField,
                    language = searchLanguageFilter,
                )
        }
        if (compatibleSourceFilter != searchSourceFilter) {
            searchSourceFilter = null
        }
        val languageSources = compatibleSourceFilter?.let(::listOf) ?: availableSearchSources
        if (
            searchLanguageFilter != null &&
            (
                searchLanguageFilter !in availableSearchLanguages ||
                    !isCatalogLanguageFilterOptionAvailable(
                        language = requireNotNull(searchLanguageFilter),
                        field = searchField,
                        sources = languageSources,
                    )
                )
        ) {
            searchLanguageFilter = null
        }
    }

    fun collapseSearchUi() {
        searchExpanded = false
        searchCollapseRevision++
        focusManager.clearFocus(force = true)
    }

    fun updateQueryInput(input: String) {
        query = input.take(240)
    }

    fun selectSearchSource(source: CatalogSourcePreference?) {
        searchSourceFilter = source
        searchLanguageFilter = searchLanguageFilter?.takeIf { it in availableSearchLanguages }
    }

    fun loadPage(
        reset: Boolean,
        q: String = activeSearchQuery,
        field: CatalogSearchField = activeSearchField,
        languages: List<String> = if (sanitizeCatalogSearchQuery(q).isBlank()) {
            browseLanguageFilters
        } else {
            searchLanguageFilter?.let(::listOf) ?: listOf("")
        },
        order: DiscoverSortOrder = effectiveSortOrder,
    ) {
        if (!canDispatchSearch || !ownsSearchRequests()) return
        val requestedSourcePreference = resolveCatalogSearchSourcePreference(
            query = q,
            configured = bookSourcePreference,
            selectedSource = searchSourceFilter,
        )
        val languageKey = languages.catalogLanguageCacheKey()
        if (!reset && loading) return
        val normalizedQuery = sanitizeCatalogSearchQuery(q).take(120)
        val repositoryField = field
        val offset = if (reset) 0 else nextOffset
        if (discoveryPaused) {
            loading = false
            loadingReset = false
            error = null
            if (reset) {
                activeSearchQuery = normalizedQuery
                activeSearchField = field
            }
            return
        }
        graph.app.discoverRepository.checkSourceRoots(bookSourcePreference.enabledSources)
        val visibleResultsMatchRequest = discoverCache.describes(
            query = normalizedQuery,
            field = repositoryField,
            language = languageKey,
            bookSource = requestedSourcePreference,
            sortOrder = order,
        )
        val reuseSnapshot = reset && visibleResultsMatchRequest && discoverCache.isFreshCompleteSnapshot()
        loading = !reuseSnapshot
        loadingReset = reset && !reuseSnapshot
        error = null
        if (reuseSnapshot) {
            results = discoverCache.results
            nextOffset = discoverCache.nextOffset
            canLoadMore = discoverCache.canLoadMore
        }
        if (reset) {
            activeSearchQuery = normalizedQuery
            activeSearchField = field
            if (!visibleResultsMatchRequest) {
                results = emptyList()
                nextOffset = 0
                canLoadMore = false
            }
        }
        graph.app.discoverRepository.loadDiscoverPage(
            DiscoverLoadRequest(
                query = q,
                field = repositoryField,
                languages = languages,
                languageKey = languageKey,
                sourcePreference = requestedSourcePreference,
                sortOrder = order,
                reset = reset,
                offset = offset,
                pageSize = CatalogPageSize,
            ),
        )
    }

    LaunchedEffect(discoverCache) {
        if (!discoverCache.describes(
                query = activeSearchQuery,
                field = activeSearchField,
                language = activeLanguageFilterKey,
                bookSource = activeSourcePreference,
                sortOrder = effectiveSortOrder,
            )
        ) {
            return@LaunchedEffect
        }
        results = discoverCache.results
        nextOffset = discoverCache.nextOffset
        canLoadMore = discoverCache.canLoadMore
        loading = discoverCache.isLoading
        loadingReset = discoverCache.loadingReset
        error = discoverCache.errorMessage
        activeSearchQuery = discoverCache.query
    }

    LaunchedEffect(networkStatus.isOnline, isResumed) {
        if (!isResumed || !ownsSearchRequests()) return@LaunchedEffect
        val cameOnline = networkStatus.isOnline && !wasOnline
        wasOnline = networkStatus.isOnline
        if (cameOnline) {
            // AudiobookApplication owns the single reconnect retry/reset for this transition.
            connectivityRefreshRevision++
        }
    }

    val discoverHydrationKey = remember(displayedResults) {
        displayedResults.joinToString("|") { it.id }
    }
    LaunchedEffect(discoverHydrationKey, canDispatchSearch) {
        if (!canDispatchSearch || !ownsSearchRequests() || displayedResults.isEmpty()) return@LaunchedEffect
        delay(DiscoverDetailHydrationDelayMillis)
        if (!ownsSearchRequests() || latestActiveSharedTransitionKey.value != null) return@LaunchedEffect
        graph.app.discoverRepository.hydrateVisibleBookDetails(
            books = displayedResults,
            limit = DiscoverResultDetailHydrationLimit,
        )
    }

    fun runSearch(
        q: String,
        field: CatalogSearchField,
        recordHistory: Boolean = false,
    ) {
        if (!isResumed || !ownsSearchRequests()) return
        val normalizedQuery = sanitizeCatalogSearchQuery(q).take(120)
        query = normalizedQuery
        searchField = field
        activeSearchQuery = normalizedQuery
        activeSearchField = field
        if (normalizedQuery.isBlank()) {
            searchSourceFilter = null
            searchLanguageFilter = null
        } else if (recordHistory) {
            scope.launch { graph.app.searchHistoryRepository.add(normalizedQuery) }
        }
        loadPage(reset = true, q = normalizedQuery, field = field, order = effectiveSortOrder)
    }

    // Navigation delivers to this back-stack entry only. A newer request cannot be cleared by
    // the acknowledgment of an older one, and an outgoing screen cannot dispatch it.
    LaunchedEffect(navigationRequest?.id, isResumed) {
        if (!isResumed || !ownsSearchRequests()) return@LaunchedEffect
        val request = navigationRequest ?: return@LaunchedEffect
        if (request.id != lastAppliedRequestId) {
            val normalized = sanitizeCatalogSearchQuery(request.query).take(120)
            query = normalized
            searchField = request.field
            activeSearchQuery = normalized
            activeSearchField = request.field
            searchLanguageFilter = request.language.takeIf { it.isNotBlank() }
            searchSourceFilter = null
            searchExpanded = false
            results = emptyList()
            nextOffset = 0
            canLoadMore = true
            lastAppliedRequestId = request.id
            onNavigationRequestApplied(request.id)
            gridState.scrollToItem(0)
        } else {
            onNavigationRequestApplied(request.id)
        }
    }

    fun openCatalogBook(book: AudioBook, transitionKey: BookSharedTransitionKey? = null) {
        onOpenBook(book.id, transitionKey)
        scope.launch { graph.app.stageBookDetail(book) }
    }

    suspend fun committedCatalogBook(book: AudioBook): AudioBook {
        return withContext(Dispatchers.IO) {
            val refreshed = runCatching { graph.app.catalogClient.fetchByIds(book.id).firstOrNull() }
                .getOrNull()
                ?: return@withContext book
            graph.app.libraryRepository.upsertCatalogBooks(listOf(refreshed))
            refreshed
        }
    }

    LaunchedEffect(
        canDispatchSearch,
        activeSearchQuery,
        activeSearchField,
        activeLanguageFilterKey,
        bookSourcePreference,
        searchSourceFilter,
        effectiveSortOrder,
        lastAppliedRequestId,
        networkStatus.isOnline,
        downloadsOnlyModeEnabled,
        connectivityRefreshRevision,
    ) {
        if (!canDispatchSearch || !ownsSearchRequests()) return@LaunchedEffect
        if (!discoveryPaused) {
            graph.app.discoverRepository.checkSourceRoots(bookSourcePreference.enabledSources)
        }
        if (discoverCache.describes(
                query = activeSearchQuery,
                field = activeSearchField,
                language = activeLanguageFilterKey,
                bookSource = activeSourcePreference,
                sortOrder = effectiveSortOrder,
            ) && discoverCache.isLoading
        ) {
            return@LaunchedEffect
        }
        loadPage(reset = true, q = activeSearchQuery, field = activeSearchField, order = effectiveSortOrder)
    }

    LaunchedEffect(gridState, searchExpanded) {
        if (!searchExpanded) return@LaunchedEffect
        snapshotFlow { gridState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) collapseSearchUi()
            }
    }

    val normalizedQuery = normalizedInputQuery
    LaunchedEffect(normalizedQuery, searchField, canDispatchSearch) {
        if (!canDispatchSearch || !ownsSearchRequests()) return@LaunchedEffect
        if (normalizedQuery.isBlank()) {
            autocompleteQuery = ""
            if (activeSearchQuery.isNotBlank()) {
                delay(SearchDebounceMillis)
                runSearch("", CatalogSearchField.All)
            }
            return@LaunchedEffect
        }
        delay(SearchDebounceMillis)
        autocompleteQuery = normalizedQuery
        if (normalizedQuery != activeSearchQuery || searchField != activeSearchField) {
            runSearch(normalizedQuery, searchField)
        }
    }
    val isSearchBusy =
        activeSearchQuery.isNotBlank() && loading && loadingReset
    val isInitialDiscoverLoad = !noSourcesSelected &&
        unavailableSelectedSources.size < bookSourcePreference.enabledSources.size &&
        results.isEmpty() &&
        error == null &&
        discoverCache.loadedAtMillis == 0L
    val showSkeletonResults = !discoveryPaused && (loading || isInitialDiscoverLoad) && results.isEmpty()
    val visibleSearchQuery = activeSearchQuery
    val visibleSearchField = activeSearchField
    val hasUncommittedSearch =
        normalizedQuery.isNotBlank() && normalizedQuery != visibleSearchQuery
    val isUpdatingSearchSuggestions =
        normalizedQuery.isNotBlank() && autocompleteQuery != normalizedQuery
    val showingSearchResults = visibleSearchQuery.isNotBlank()
    val autocompleteFlow = remember(autocompleteQuery, searchField, activeLanguageFilterKey, bookSourcePreference) {
        graph.app.catalogAutocompleteRepository.suggestions(
            query = autocompleteQuery,
            field = searchField,
            languages = if (normalizedQuery.isBlank()) browseLanguageFilters else activeLanguageFilters,
            sourcePreference = bookSourcePreference,
        )
    }
    val autocompleteSuggestions by autocompleteFlow.collectAsState(initial = emptyList())
    val searchSuggestions = remember(
        autocompleteQuery,
        displayedResults,
        autocompleteSuggestions,
        searchField,
        hasUncommittedSearch,
    ) {
        if (autocompleteQuery.isBlank()) {
            emptyList()
        } else {
            val visibleResultSuggestions = if (hasUncommittedSearch) {
                emptyList()
            } else {
                displayedResults.asSequence()
                    .filter { it.matchesSuggestion(autocompleteQuery, searchField) }
                    .take(MaxSearchSuggestions)
                    .toList()
            }
            (visibleResultSuggestions + autocompleteSuggestions)
                .dedupeSuggestionBooks()
                .take(MaxSearchSuggestions)
        }
    }
    val visibleSearchSuggestions = searchSuggestions
    val searchDropdownExpanded = searchExpanded
    val expandedSuggestionRows = visibleSearchSuggestions.map { book ->
        DiscoverSearchRow(
            key = book.id,
            title = book.title,
            supportingText = book.discoverMatchSummary(normalizedQuery, searchField) ?: book.author,
        )
    }
    val expandedResultRows = displayedResults.take(12).map { book ->
        DiscoverSearchRow(
            key = book.id,
            title = book.title,
            supportingText = book.discoverMatchSummary(visibleSearchQuery, visibleSearchField)
                ?: listOf(book.author, book.source.name)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
        )
    }
    val completedSearchOperations = matchingCache?.searchProgressItems.orEmpty().count { it.isComplete }
    val totalSearchOperations = matchingCache?.searchProgressItems.orEmpty().size
    val expandedUnavailableMessage = when {
        isOffline -> "Offline. Cached matches remain available and unfinished searches will retry once after reconnection."
        unavailableSelectedSources.isNotEmpty() ->
            "${unavailableSelectedSources.discoverSourceNames()} root page appears unavailable. Catalog searches continue independently."
        else -> error
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp),
    ) {
        DiscoverSearchChrome(
            query = query,
            hint = searchHint(searchField),
            isBusy = isSearchBusy || isUpdatingSearchSuggestions,
            collapseRevision = searchCollapseRevision,
            onQueryChange = ::updateQueryInput,
            onSearch = { submittedQuery ->
                runSearch(submittedQuery, searchField, recordHistory = true)
            },
            onClear = {
                updateQueryInput("")
                runSearch("", CatalogSearchField.All)
            },
            onExpandedChange = { expanded -> searchExpanded = expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            DiscoverSourceStatusRow(
                sources = visibleSourceStatusItems,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            DiscoverExpandedSearchContent(
                query = normalizedQuery,
                fieldLabel = searchFieldLabel(searchField).lowercase(Locale.ROOT),
                history = historyEntries.take(MaxRecentSearches).map { it.query },
                suggestions = expandedSuggestionRows,
                results = expandedResultRows,
                totalResultCount = displayedResults.size,
                hasExecutedSearch = normalizedQuery.isNotBlank() && !hasUncommittedSearch,
                isLoading = loading || isUpdatingSearchSuggestions,
                completedOperations = completedSearchOperations,
                totalOperations = totalSearchOperations,
                unavailableMessage = expandedUnavailableMessage,
                modifier = Modifier.weight(1f),
                onHistorySelected = { historyQuery ->
                    query = historyQuery
                    runSearch(historyQuery, searchField, recordHistory = true)
                },
                onHistoryDeleted = { historyQuery ->
                    historyEntries.firstOrNull { it.query == historyQuery }?.let { entry ->
                        scope.launch { graph.app.searchHistoryRepository.remove(entry) }
                    }
                },
                onClearHistory = { scope.launch { graph.app.searchHistoryRepository.clear() } },
                onSuggestionSelected = { row ->
                    val book = searchSuggestions.firstOrNull { it.id == row.key }
                    val suggestionQuery = book?.title.orEmpty().ifBlank { normalizedQuery }
                    query = suggestionQuery
                    runSearch(suggestionQuery, searchField, recordHistory = true)
                },
                onResultSelected = { row ->
                    displayedResults.firstOrNull { it.id == row.key }?.let { book ->
                        collapseSearchUi()
                        openCatalogBook(book)
                    }
                },
                onSubmitQuery = {
                    runSearch(normalizedQuery, searchField, recordHistory = true)
                },
            )
        }

        DiscoverFilterSummaryRow(
            field = searchFieldLabel(searchField),
            source = when {
                availableSearchSources.isEmpty() -> "No sources"
                else -> searchSourceFilter?.label ?: "All sources"
            },
            language = searchLanguageFilter ?: "Any language",
            onOpenFilters = { section ->
                openFilterSheetSection = section
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        DiscoverSourceStatusRow(
            sources = visibleSourceStatusItems,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(searchDropdownExpanded) {
                    if (!searchDropdownExpanded) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        collapseSearchUi()
                    }
                },
        ) {
            if (hasUncommittedSearch) {
                PendingSearchState(
                    query = normalizedQuery,
                    field = searchField,
                    isLoadingSuggestions = isUpdatingSearchSuggestions,
                    onSearch = {
                        collapseSearchUi()
                        runSearch(normalizedQuery, searchField, recordHistory = true)
                    },
                    onClear = { runSearch("", CatalogSearchField.All) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                )
            } else if (
                displayedResults.isEmpty() &&
                !showSkeletonResults &&
                (!loading || discoveryPaused) &&
                (error == null || isOffline || discoveryPaused || unavailableSelectedSources.isNotEmpty())
            ) {
                DiscoverEmptyState(
                    query = visibleSearchQuery,
                    field = visibleSearchField,
                    searchCompletedWithoutFailure = error == null,
                    noSourcesSelected = noSourcesSelected,
                    isOffline = isOffline,
                    downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                    unavailableSources = unavailableSelectedSources,
                    onDownloadsOnlyModeChange = {
                        downloadsOnlyModeEnabled = it
                        graph.app.playbackSettingsStore.saveDownloadsOnlyModeEnabled(it)
                    },
                    onRetryCatalog = {
                        graph.app.discoverRepository.prepareForCatalogRetry()
                        loadPage(reset = true, q = activeSearchQuery, field = activeSearchField, order = effectiveSortOrder)
                    },
                    onClearSearch = { runSearch("", CatalogSearchField.All) },
                    onOpenCatalogSettings = onOpenCatalogSettings,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 8.dp,
                        end = 12.dp,
                        bottom = 232.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (!showingSearchResults) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DiscoverHeader(
                                count = displayedResults.size,
                                canLoadMore = canLoadMore,
                                isLoading = showSkeletonResults,
                                isOffline = isOffline,
                                downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                                unavailableSources = unavailableSelectedSources,
                                bookSource = bookSourcePreference,
                                languageSummary = preferredLanguages.discoverLanguageSummary(bookSourcePreference),
                                sortOrder = effectiveSortOrder,
                                onOpenFilters = {
                                    openFilterSheetSection = DiscoverFilterSheetSection.Sort
                                },
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DiscoverSearchHeader(
                                query = visibleSearchQuery,
                                field = visibleSearchField,
                                count = displayedResults.size,
                                canLoadMore = canLoadMore,
                                isLoading = isSearchBusy || showSkeletonResults,
                                isOffline = isOffline,
                                downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                                unavailableSources = unavailableSelectedSources,
                                progressItems = matchingCache?.searchProgressItems.orEmpty(),
                                sortOrder = effectiveSortOrder,
                                onOpenFilters = {
                                    openFilterSheetSection = DiscoverFilterSheetSection.Sort
                                },
                            )
                        }
                    }
                    if (showSkeletonResults) {
                        items(count = 12, key = { "discover-skeleton-$it" }) {
                            DiscoverSkeletonCard()
                        }
                    } else {
                        items(
                            items = displayedResults,
                            key = { it.id },
                            span = { book ->
                                if (showingSearchResults) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                            },
                        ) { book ->
                            val transitionKey = remember(book.id) {
                                BookSharedTransitionKey(
                                    bookId = book.id,
                                    source = BookSharedElementSource(Routes.DISCOVER, "result"),
                                )
                            }
                            val matchSummary = book.discoverMatchSummary(
                                query = visibleSearchQuery,
                                field = visibleSearchField,
                            )
                            if (showingSearchResults) {
                                DiscoverSearchResultRow(
                                    book = book,
                                    matchSummary = matchSummary,
                                    coverArtDisplayMode = coverArtDisplayMode,
                                    onClick = { openCatalogBook(book, transitionKey) },
                                    onLongPress = { actionBook = book },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                DiscoverCard(
                                    book = book,
                                    matchSummary = matchSummary,
                                    sharedTransitionKey = transitionKey,
                                    coverArtDisplayMode = coverArtDisplayMode,
                                    onClick = { openCatalogBook(book, transitionKey) },
                                    onLongPress = { actionBook = book },
                                    onShare = { shareAudiobook(context, book) },
                                )
                            }
                        }
                        if (displayedResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                DiscoverLoadMoreFooter(
                                    loading = loading,
                                    canLoadMore = canLoadMore,
                                    isSearch = showingSearchResults,
                                    onLoadMore = {
                                        loadPage(reset = false, order = effectiveSortOrder)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            error?.takeIf { unavailableSelectedSources.isEmpty() }?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }

    actionBook?.let { selected ->
        val sheetLibraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
        val latestBook = sheetLibraryState?.bookById(selected.id) ?: selected
        DiscoverBookActionSheet(
            book = latestBook,
            coverArtDisplayMode = coverArtDisplayMode,
            onDismiss = { actionBook = null },
            onOpenBook = {
                actionBook = null
                openCatalogBook(latestBook)
            },
            onGoToAuthor = {
                actionBook = null
                onOpenAuthorSearch(latestBook)
            },
            onToggleLike = {
                scope.launch {
                    graph.app.libraryRepository.upsertCatalogBooks(listOf(latestBook))
                    graph.app.libraryRepository.toggleFavorite(latestBook.id)
                }
            },
            onAddToLibrary = {
                scope.launch {
                    val committedBook = committedCatalogBook(latestBook)
                    graph.app.libraryRepository.addToLibrary(committedBook)
                }
                actionBook = null
            },
            onDownloadBook = {
                scope.launch {
                    val committedBook = committedCatalogBook(latestBook)
                    graph.app.libraryRepository.addToLibrary(committedBook)
                    graph.app.downloadManager.downloadAll(committedBook)
                }
                actionBook = null
            },
            onDeleteDownloads = {
                scope.launch { graph.app.downloadManager.deleteDownloads(latestBook) }
                actionBook = null
            },
            onPlayNext = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.addAfterCurrent(items)
                    scope.launch { graph.app.libraryRepository.upsertCatalogBooks(listOf(latestBook)) }
                }
                actionBook = null
            },
            onAddToQueue = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.appendToQueue(items)
                    scope.launch { graph.app.libraryRepository.upsertCatalogBooks(listOf(latestBook)) }
                }
                actionBook = null
            },
            onCastAudio = {
                scope.launch {
                    graph.app.libraryRepository.upsertCatalogBooks(listOf(latestBook))
                }
                castAudiobookAudio(context, graph, latestBook)
                actionBook = null
            },
            onShare = {
                shareAudiobook(context, latestBook)
                actionBook = null
            },
            onDonate = latestBook.donationLink()?.let { link ->
                {
                    openExternalUrl(context, link.url)
                    actionBook = null
                }
            },
        )
    }

    openFilterSheetSection?.let { initialSection ->
        DiscoverFilterSheet(
            sortOrder = effectiveSortOrder,
            searchField = searchField,
            availableSources = availableSearchSources,
            sourceFilter = searchSourceFilter,
            availableLanguages = availableSearchLanguages,
            languageFilter = searchLanguageFilter,
            facetCounts = searchFacetCounts,
            showCounts = activeSearchQuery.isNotBlank(),
            progressItems = matchingCache?.searchProgressItems.orEmpty(),
            sourceHealth = sourceHealth,
            isOffline = isOffline,
            query = visibleSearchQuery,
            initialSection = initialSection,
            onDismiss = { openFilterSheetSection = null },
            onSortOrderChange = { sortOrder = it },
            onSearchFieldChange = { field ->
                searchField = field
                if (activeSearchQuery.isNotBlank()) {
                    activeSearchField = field
                }
            },
            onSourceFilterChange = ::selectSearchSource,
            onLanguageFilterChange = { searchLanguageFilter = it },
            onClearSearch = {
                runSearch("", CatalogSearchField.All)
                openFilterSheetSection = null
            },
            onGenreSelected = { genre ->
                searchField = CatalogSearchField.Genre
                query = genre
                searchExpanded = false
                openFilterSheetSection = null
                runSearch(genre, CatalogSearchField.Genre, recordHistory = true)
            },
        )
    }
}

private fun discoverFallbackBooks(
    libraryState: LibraryState?,
    languages: List<String>,
    sortOrder: DiscoverSortOrder,
    sourcePreference: BookSourcePreference,
): List<AudioBook> {
    val libraryBooks = libraryState?.books.orEmpty()
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
    libraryState: LibraryState?,
    query: String,
    field: CatalogSearchField,
    languages: List<String>,
    sourcePreference: BookSourcePreference = BookSourcePreference.Default,
): List<AudioBook> {
    val normalizedQuery = query.normalizedSearchText()
    if (normalizedQuery.isBlank()) return emptyList()
    return (libraryState?.books.orEmpty() + AudioBookLibrary.seededBooks)
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

private fun Set<BookLanguagePreference>.discoverLanguageSummary(
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

private fun List<AudioBook>.sortedForDiscover(
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

private fun DiscoverCacheState.isFreshCompleteSnapshot(
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val ttl = if (results.isEmpty()) {
        SearchEmptySnapshotFreshMillis
    } else {
        SearchPositiveSnapshotFreshMillis
    }
    val age = nowMillis - loadedAtMillis
    return isComplete &&
        errorMessage == null &&
        loadedAtMillis > 0L &&
        age in 0L..ttl
}

private fun AudioBook.matchesLocalSearch(
    normalizedQuery: String,
    field: CatalogSearchField,
): Boolean {
    fun String.matchesQuery(): Boolean = normalizedSearchText().contains(normalizedQuery)
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

private fun String.normalizedSearchText(): String =
    normalizedCatalogSearchText(this)

private fun searchHint(field: CatalogSearchField): String = when (field) {
    CatalogSearchField.All -> "Search titles, chapters, people, genres, and metadata"
    CatalogSearchField.Title -> "Search by title"
    CatalogSearchField.Chapter -> "Search by chapter"
    CatalogSearchField.Author -> "Search by author"
    CatalogSearchField.Reader -> "Search by reader"
    CatalogSearchField.Genre -> "Search by genre"
    CatalogSearchField.Epoch -> "Search by epoch"
    CatalogSearchField.Kind -> "Search by kind"
}

private fun searchFieldLabel(field: CatalogSearchField): String = when (field) {
    CatalogSearchField.All -> "All fields"
    CatalogSearchField.Title -> "Title"
    CatalogSearchField.Chapter -> "Chapter"
    CatalogSearchField.Author -> "Author"
    CatalogSearchField.Reader -> "Reader"
    CatalogSearchField.Genre -> "Genre"
    CatalogSearchField.Epoch -> "Epoch"
    CatalogSearchField.Kind -> "Kind"
}

private fun discoverSortLabel(sortOrder: DiscoverSortOrder): String = when (sortOrder) {
    DiscoverSortOrder.Alphabetical -> "Alphabetical"
    DiscoverSortOrder.MostPopular -> "Most popular"
}

private fun discoverBrowseStatusText(
    count: Int,
    canLoadMore: Boolean,
    isLoading: Boolean,
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    unavailableSources: List<CatalogSourcePreference>,
    bookSource: BookSourcePreference,
    languageSummary: String,
    sortOrder: DiscoverSortOrder,
): String {
    val sourceNames = bookSource.discoverSourceNames()
    if (downloadsOnlyModeEnabled) {
        return "Downloads only mode. Discovery is paused until you exit offline mode."
    }
    if (isOffline) {
        return if (count > 0) {
            "Offline. Showing cached books from $sourceNames."
        } else {
            "Offline. Connect to the internet to browse $sourceNames."
        }
    }
    if (unavailableSources.isNotEmpty()) {
        val unavailableNames = unavailableSources.discoverSourceNames()
        return if (count > 0) {
            "$unavailableNames root page unavailable. Showing $count catalog results."
        } else {
            "$unavailableNames root page unavailable. Its catalog search still runs independently."
        }
    }
    if (!isLoading) {
        return "$count from $sourceNames${if (canLoadMore) " · more available" else ""}"
    }
    val browseTarget = if (languageSummary == "all selected languages") {
        "audiobooks in $languageSummary"
    } else {
        "$languageSummary audiobooks"
    }
    val orderedTarget = when (sortOrder) {
        DiscoverSortOrder.MostPopular -> "the most popular $browseTarget"
        DiscoverSortOrder.Alphabetical -> "$browseTarget in alphabetical order"
    }
    return "Loading $orderedTarget from $sourceNames."
}

private fun BookSourcePreference.discoverSourceNames(): String =
    CatalogSourcePreference.entries
        .filter { it in enabledSources }
        .joinToString { it.label }
        .ifBlank { label }

private fun List<CatalogSourcePreference>.discoverSourceNames(): String =
    when (size) {
        0 -> "Catalog source"
        1 -> first().label
        2 -> joinToString(" and ") { it.label }
        else -> "${take(2).joinToString(", ") { it.label }} and ${size - 2} more"
    }

internal enum class DiscoverFilterSheetSection {
    Overview,
    Sort,
    SearchField,
    Source,
    Language,
}

@Composable
internal fun DiscoverFilterSummaryRow(
    field: String,
    source: String,
    language: String,
    onOpenFilters: (DiscoverFilterSheetSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("discover-filter-summary"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "field") {
            ActiveSearchFilterChip(
                label = field,
                accessibilityLabel = "Search field: $field",
                testTag = "discover-filter-chip-field",
                onClick = { onOpenFilters(DiscoverFilterSheetSection.SearchField) },
            )
        }
        item(key = "source") {
            ActiveSearchFilterChip(
                label = source,
                accessibilityLabel = "Search source: $source",
                testTag = "discover-filter-chip-source",
                onClick = { onOpenFilters(DiscoverFilterSheetSection.Source) },
            )
        }
        item(key = "language") {
            ActiveSearchFilterChip(
                label = language,
                accessibilityLabel = "Search language: $language",
                testTag = "discover-filter-chip-language",
                onClick = { onOpenFilters(DiscoverFilterSheetSection.Language) },
            )
        }
        item(key = "filters") {
            FilterChip(
                selected = false,
                onClick = { onOpenFilters(DiscoverFilterSheetSection.Overview) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("discover-filter-chip-overview"),
                shape = MaterialTheme.shapes.extraLarge,
                label = {
                    Text(
                        text = "Filters",
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ActiveSearchFilterChip(
    label: String,
    accessibilityLabel: String,
    testTag: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = true,
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics { contentDescription = accessibilityLabel },
        shape = MaterialTheme.shapes.extraLarge,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun PendingSearchState(
    query: String,
    field: CatalogSearchField,
    isLoadingSuggestions: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = tonal.listItem,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoadingSuggestions) {
                ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = "Ready to search",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (field == CatalogSearchField.All) {
                    "Press Search to look for \"$query\" across title, chapter, author, and reader. Recommendations update while you type."
                } else {
                    "Press Search to look for \"$query\" with the ${searchFieldLabel(field).lowercase()} filter."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                FilledTonalButton(onClick = onSearch) {
                    Text("Search")
                }
            }
        }
    }
}

@Composable
private fun DiscoverEmptyState(
    query: String,
    field: CatalogSearchField,
    searchCompletedWithoutFailure: Boolean,
    noSourcesSelected: Boolean,
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    unavailableSources: List<CatalogSourcePreference>,
    onDownloadsOnlyModeChange: (Boolean) -> Unit,
    onRetryCatalog: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenCatalogSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    val catalogPaused = isOffline || downloadsOnlyModeEnabled
    val hasUnavailableSources = unavailableSources.isNotEmpty() && !catalogPaused && !noSourcesSelected
    val isSearch = query.isNotBlank() &&
        searchCompletedWithoutFailure &&
        !noSourcesSelected &&
        !catalogPaused
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = tonal.listItem,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isSearch || noSourcesSelected || catalogPaused || hasUnavailableSources) {
                    Icons.Filled.SearchOff
                } else {
                    Icons.Filled.Search
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = when {
                    downloadsOnlyModeEnabled -> "Downloads only mode"
                    isOffline -> "You're offline"
                    noSourcesSelected -> "Choose catalog sources"
                    isSearch -> "No results found"
                    hasUnavailableSources -> "Source homepage unavailable"
                    else -> "Search audiobook catalogs"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    downloadsOnlyModeEnabled -> "Discovery is paused while the app shows downloaded books only."
                    isOffline -> "Discovery needs an internet connection. Downloaded books are still available in Library."
                    noSourcesSelected -> "Discovery is paused because no catalog source is selected. Select a source to browse books."
                    isSearch && field == CatalogSearchField.All -> "No matches for \"$query\". Try another spelling or add a filter."
                    isSearch -> "No matches for \"$query\" with the ${searchFieldLabel(field).lowercase()} filter. Clear or switch the filter."
                    hasUnavailableSources -> {
                        "${unavailableSources.discoverSourceNames()} root page did not validate. Search endpoints still run; retry to check the homepage again."
                    }
                    else -> "Search by title, chapter, author, or reader. Use filters for genre, epoch, or kind."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (downloadsOnlyModeEnabled) {
                FilledTonalButton(onClick = { onDownloadsOnlyModeChange(false) }) {
                    Text("Exit Downloads only")
                }
            } else if (isSearch) {
                FilledTonalButton(onClick = onClearSearch) {
                    Text("Clear search")
                }
            } else if (hasUnavailableSources) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenCatalogSettings) {
                        Text("Sources")
                    }
                    FilledTonalButton(onClick = onRetryCatalog) {
                        Text("Retry")
                    }
                }
            } else if (noSourcesSelected && !isOffline) {
                FilledTonalButton(onClick = onOpenCatalogSettings) {
                    Text("Select sources")
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeader(
    count: Int,
    canLoadMore: Boolean,
    isLoading: Boolean,
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    unavailableSources: List<CatalogSourcePreference>,
    bookSource: BookSourcePreference,
    languageSummary: String,
    sortOrder: DiscoverSortOrder,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Browse audiobooks",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isLoading) {
                    DiscoverSortButton(
                        sortOrder = sortOrder,
                        onOpenFilters = onOpenFilters,
                    )
                }
            }
            Text(
                text = discoverBrowseStatusText(
                    count = count,
                    canLoadMore = canLoadMore,
                    isLoading = isLoading,
                    isOffline = isOffline,
                    downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                    unavailableSources = unavailableSources,
                    bookSource = bookSource,
                    languageSummary = languageSummary,
                    sortOrder = sortOrder,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoading) {
            ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun DiscoverSearchHeader(
    query: String,
    field: CatalogSearchField,
    count: Int,
    canLoadMore: Boolean,
    isLoading: Boolean,
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    unavailableSources: List<CatalogSourcePreference>,
    progressItems: List<CatalogSearchProgressItem>,
    sortOrder: DiscoverSortOrder,
    onOpenFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        isLoading && field == CatalogSearchField.All -> "Searching for \"$query\""
                        isLoading -> "Searching for \"$query\" with ${searchFieldLabel(field)}"
                        field == CatalogSearchField.All -> "Results for \"$query\""
                        else -> "Results for \"$query\" with ${searchFieldLabel(field)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!isLoading) {
                    DiscoverSortButton(
                        sortOrder = sortOrder,
                        onOpenFilters = onOpenFilters,
                    )
                }
            }
            Text(
                text = when {
                    downloadsOnlyModeEnabled -> "Downloads only mode. Discovery is paused."
                    isOffline -> "Offline. Showing cached results."
                    unavailableSources.isNotEmpty() -> {
                        "${unavailableSources.discoverSourceNames()} root page unavailable · $count results"
                    }
                    isLoading && progressItems.isNotEmpty() -> {
                        val completeCount = progressItems.count { it.isComplete }
                        "$count results so far · $completeCount of ${progressItems.size} searches complete"
                    }
                    else -> "$count results${if (canLoadMore) " · more available" else ""}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoading) {
            ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun DiscoverSortButton(
    sortOrder: DiscoverSortOrder,
    onOpenFilters: () -> Unit,
) {
    TextButton(onClick = onOpenFilters) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text("Sort: ${discoverSortLabel(sortOrder)}")
    }
}

@Composable
private fun DiscoverLoadMoreFooter(
    loading: Boolean,
    canLoadMore: Boolean,
    isSearch: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (canLoadMore || loading) {
            FilledTonalButton(
                onClick = onLoadMore,
                enabled = !loading && canLoadMore,
            ) {
                Text(
                    if (loading) {
                        "Loading more"
                    } else if (isSearch) {
                        "Load more results"
                    } else {
                        "Load more books"
                    },
                )
            }
        } else {
            Text(
                text = "End of available results",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverSearchResultRow(
    book: AudioBook,
    matchSummary: String?,
    coverArtDisplayMode: CoverArtDisplayMode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.extraLarge,
        color = tonal.listItem,
        tonalElevation = 1.dp,
    ) {
        ListItem(
            leadingContent = {
                BookCover(
                    book = book,
                    modifier = Modifier.coverArtRowSize(coverArtDisplayMode, 64.dp),
                    cornerRadius = 14,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = book.author,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    matchSummary?.let { summary ->
                        Text(
                            text = summary,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    text = when (book.source) {
                        BookSource.LibriVox -> "LibriVox"
                        BookSource.Lit2Go -> "Lit2Go"
                        BookSource.Gutendex -> "Gutendex"
                        BookSource.WolneLektury -> "Wolne"
                        BookSource.LocalAsset,
                        BookSource.CustomLocal -> "On device"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverCard(
    book: AudioBook,
    matchSummary: String?,
    sharedTransitionKey: BookSharedTransitionKey,
    coverArtDisplayMode: CoverArtDisplayMode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    val pressFeedback = rememberBookCardPressFeedback()
    val cardShape = MaterialTheme.shapes.large
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = tonal.listItem),
        border = bookCardPressBorder(pressFeedback.isPressed),
        modifier = modifier
            .scale(pressFeedback.scale)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = pressFeedback.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Box {
            Column {
                Box {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxWidth()
                            .coverArtAspectRatio(coverArtDisplayMode),
                        displayMode = coverArtDisplayMode,
                    )
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                RoundedCornerShape(16.dp),
                            )
                            .size(40.dp),
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share audiobook",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    matchSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            BookCardPressOverlay(
                isPressed = pressFeedback.isPressed,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun DiscoverFilterSheet(
    sortOrder: DiscoverSortOrder,
    searchField: CatalogSearchField,
    availableSources: List<CatalogSourcePreference>,
    sourceFilter: CatalogSourcePreference?,
    availableLanguages: List<String>,
    languageFilter: String?,
    facetCounts: CatalogSearchFacetCounts,
    showCounts: Boolean,
    progressItems: List<CatalogSearchProgressItem>,
    sourceHealth: CatalogSourceHealthState,
    isOffline: Boolean,
    query: String,
    initialSection: DiscoverFilterSheetSection = DiscoverFilterSheetSection.Overview,
    onDismiss: () -> Unit,
    onSortOrderChange: (DiscoverSortOrder) -> Unit,
    onSearchFieldChange: (CatalogSearchField) -> Unit,
    onSourceFilterChange: (CatalogSourcePreference?) -> Unit,
    onLanguageFilterChange: (String?) -> Unit,
    onClearSearch: () -> Unit,
    onGenreSelected: (String) -> Unit,
    contentOnly: Boolean = false,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val haptics = rememberHaptics()
    val sectionOrder = discoverFilterSheetSectionOrder()
    val resolvedInitialSection = when {
        initialSection == DiscoverFilterSheetSection.Overview -> DiscoverFilterSheetSection.Sort
        initialSection in sectionOrder -> initialSection
        else -> DiscoverFilterSheetSection.Sort
    }
    val initialItemIndex = sectionOrder.indexOf(resolvedInitialSection)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val targetAlignmentSpacer = with(density) {
        if (
            viewportHeightPx > 0 &&
            (
                resolvedInitialSection == DiscoverFilterSheetSection.Source ||
                    resolvedInitialSection == DiscoverFilterSheetSection.Language
            )
        ) {
            (viewportHeightPx * 0.75f).toDp()
        } else {
            20.dp
        }
    }

    LaunchedEffect(resolvedInitialSection, sectionOrder, viewportHeightPx) {
        if (viewportHeightPx > 0) {
            listState.scrollToItem(initialItemIndex)
        }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("discover-filter-section-overview"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Search & filters",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .onSizeChanged { viewportHeightPx = it.height }
                    .testTag("discover-filter-sheet-scroll"),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    end = 20.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = DiscoverFilterSheetSection.Sort) {
                    FilterSection(
                        title = "Sort",
                        modifier = Modifier.testTag("discover-filter-section-sort"),
                    ) {
                        DiscoverSortOrder.entries.forEach { option ->
                            FilterRadioRow(
                                label = discoverSortLabel(option),
                                selected = sortOrder == option,
                                onClick = {
                                    haptics.tick()
                                    onSortOrderChange(option)
                                },
                            )
                        }
                    }
                }

                item(key = DiscoverFilterSheetSection.SearchField) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        FilterSection(
                            title = "Search field",
                            modifier = Modifier.testTag("discover-filter-section-search-field"),
                        ) {
                            val fieldSources = sourceFilter?.let(::listOf) ?: availableSources
                            SearchFields.filter { field ->
                                field == CatalogSearchField.All || isCatalogFieldFilterOptionAvailable(
                                    field = field,
                                    sources = fieldSources,
                                    language = languageFilter,
                                )
                            }.forEach { field ->
                                val operations = progressItems.filter { item ->
                                    field == CatalogSearchField.All || item.operation.field == field
                                }
                                val status = filterFacetPresentation(
                                    count = facetCounts.fieldCounts[field] ?: 0,
                                    operations = operations,
                                    showCounts = showCounts,
                                    isOffline = isOffline,
                                    isCovered = shouldPresentFieldFacetStatus(searchField, field),
                                )
                                if (field == CatalogSearchField.All || shouldShowFacetOption(status)) {
                                    FilterRadioRow(
                                        label = searchFieldLabel(field),
                                        selected = searchField == field,
                                        status = status.takeUnless { it is FacetPresentation.Skipped },
                                        onClick = {
                                            haptics.tick()
                                            onSearchFieldChange(field)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = DiscoverFilterSheetSection.Source) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        FilterSection(
                            title = "Source",
                            modifier = Modifier.testTag("discover-filter-section-source"),
                        ) {
                            if (availableSources.isEmpty()) {
                                ListItem(
                                    supportingContent = {
                                        Text("Choose catalog sources in Settings to filter results.")
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                                ) {
                                    Text("No sources selected")
                                }
                            } else {
                                val allSourcesStatus = filterFacetPresentation(
                                    count = facetCounts.total,
                                    operations = progressItems,
                                    showCounts = showCounts,
                                    isOffline = isOffline,
                                    isCovered = shouldPresentNullableFacetStatus(sourceFilter, null),
                                )
                                FilterRadioRow(
                                    label = "All sources",
                                    selected = sourceFilter == null,
                                    status = allSourcesStatus.takeUnless { it is FacetPresentation.Skipped },
                                    onClick = {
                                        haptics.tick()
                                        onSourceFilterChange(null)
                                    },
                                )
                                availableSources.filter { source ->
                                    isCatalogSourceFilterOptionAvailable(
                                        source = source,
                                        field = searchField,
                                        language = languageFilter,
                                    )
                                }.forEach { source ->
                                    val count = facetCounts.sourceCounts[source] ?: 0
                                    val rootHealth = sourceHealth.statusFor(source)
                                    val searchStatus = filterFacetPresentation(
                                        count = count,
                                        operations = progressItems.filter { it.operation.source == source },
                                        showCounts = showCounts,
                                        isOffline = isOffline,
                                        isCovered = shouldPresentNullableFacetStatus(sourceFilter, source),
                                    )
                                    val status = when {
                                        isOffline || rootHealth.isOffline ||
                                            rootHealth.state == CatalogSourceHealth.Offline -> searchStatus
                                        rootHealth.state == CatalogSourceHealth.Broken ->
                                            FacetPresentation.RootBroken(searchStatus)
                                        rootHealth.state == CatalogSourceHealth.Checking ->
                                            FacetPresentation.Pending(count)
                                        shouldPresentNullableFacetStatus(sourceFilter, source) ->
                                            searchStatus
                                        else -> null
                                    }
                                    if (shouldShowFacetOption(status)) {
                                        FilterRadioRow(
                                            label = source.label,
                                            selected = sourceFilter == source,
                                            status = status,
                                            unavailableLabel = "Broken",
                                            onClick = {
                                                haptics.tick()
                                                onSourceFilterChange(source)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = DiscoverFilterSheetSection.Language) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        FilterSection(
                            title = "Language",
                            modifier = Modifier.testTag("discover-filter-section-language"),
                        ) {
                            val anyLanguageStatus = filterFacetPresentation(
                                count = anyLanguageFacetCount(facetCounts),
                                operations = progressItems,
                                showCounts = showCounts,
                                isOffline = isOffline,
                                isCovered = shouldPresentNullableFacetStatus(languageFilter, null),
                            )
                            FilterRadioRow(
                                label = "Any language",
                                supporting = "Search every language supported by each source",
                                selected = languageFilter == null,
                                status = anyLanguageStatus.takeUnless { it is FacetPresentation.Skipped },
                                onClick = {
                                    haptics.tick()
                                    onLanguageFilterChange(null)
                                },
                            )
                            val languageSources = sourceFilter?.let(::listOf) ?: availableSources
                            availableLanguages.filter { language ->
                                isCatalogLanguageFilterOptionAvailable(
                                    language = language,
                                    field = searchField,
                                    sources = languageSources,
                                )
                            }.forEach { language ->
                                val status = filterFacetPresentation(
                                    count = facetCounts.languageCounts[language] ?: 0,
                                    operations = progressItems,
                                    showCounts = showCounts,
                                    isOffline = isOffline,
                                    isCovered = shouldPresentNullableFacetStatus(languageFilter, language),
                                )
                                if (shouldShowFacetOption(status)) {
                                    FilterRadioRow(
                                        label = language,
                                        selected = languageFilter == language,
                                        status = status,
                                        onClick = {
                                            haptics.tick()
                                            onLanguageFilterChange(language)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (query.isNotBlank()) {
                    item(key = "clear-search") {
                        TextButton(onClick = onClearSearch) {
                            Icon(Icons.Filled.Clear, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Clear search")
                        }
                    }
                }
                item(key = "genre-divider") { HorizontalDivider() }
                item(key = "genres") {
                    FilterSection(title = "Explore genres") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PopularGenres, key = { it }) { genre ->
                                AssistChip(
                                    onClick = {
                                        haptics.tick()
                                        onGenreSelected(genre)
                                    },
                                    label = { Text(genre) },
                                )
                            }
                        }
                    }
                }
                item(key = "bottom-space") {
                    Spacer(modifier = Modifier.height(targetAlignmentSpacer))
                }
            }
        }
    }
    if (contentOnly) {
        content()
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            content()
        }
    }
}

internal fun discoverFilterSheetSectionOrder(): List<DiscoverFilterSheetSection> = listOf(
    DiscoverFilterSheetSection.Sort,
    DiscoverFilterSheetSection.SearchField,
    DiscoverFilterSheetSection.Source,
    DiscoverFilterSheetSection.Language,
)

internal sealed interface FacetPresentation {
    data class Pending(val interimCount: Int) : FacetPresentation
    data class Ready(val count: Int) : FacetPresentation
    data class Skipped(val reason: String) : FacetPresentation
    data class RootBroken(val searchStatus: FacetPresentation?) : FacetPresentation
    data object Unavailable : FacetPresentation
}

internal fun shouldShowFacetOption(status: FacetPresentation?): Boolean =
    status !is FacetPresentation.Skipped

internal fun shouldPresentFieldFacetStatus(
    selected: CatalogSearchField,
    option: CatalogSearchField,
): Boolean = selected == CatalogSearchField.All || selected == option

internal fun <T> shouldPresentNullableFacetStatus(selected: T?, option: T?): Boolean =
    selected == null || selected == option

internal fun anyLanguageFacetCount(facetCounts: CatalogSearchFacetCounts): Int = facetCounts.total

internal fun filterFacetPresentation(
    count: Int,
    operations: List<CatalogSearchProgressItem>,
    showCounts: Boolean,
    isOffline: Boolean,
    isCovered: Boolean,
): FacetPresentation? {
    if (!isCovered) return null
    val presentation = facetPresentation(count, operations, showCounts)
    return if (isOffline) presentation as? FacetPresentation.Ready else presentation
}

internal fun facetStatusLabel(
    status: FacetPresentation,
    unavailableLabel: String = "Unavailable",
): String? = when (status) {
    is FacetPresentation.Pending -> {
        if (status.interimCount > 0) {
            "Loading · ${status.interimCount}"
        } else {
            "Loading…"
        }
    }
    is FacetPresentation.Ready -> status.count.toString()
    is FacetPresentation.Skipped -> null
    is FacetPresentation.RootBroken -> buildString {
        append("Broken")
        when (val searchStatus = status.searchStatus) {
            is FacetPresentation.Pending -> {
                append(" · Loading")
                searchStatus.interimCount.takeIf { it > 0 }?.let { append(" · $it") }
            }
            is FacetPresentation.Ready -> append(" · ${searchStatus.count}")
            else -> Unit
        }
    }
    FacetPresentation.Unavailable -> unavailableLabel
}

internal fun isCatalogFieldFilterOptionAvailable(
    field: CatalogSearchField,
    sources: List<CatalogSourcePreference>,
    language: String?,
): Boolean = sources.any { source ->
    catalogSourceSupportsFilter(source, field, language)
}

internal fun isCatalogSourceFilterOptionAvailable(
    source: CatalogSourcePreference,
    field: CatalogSearchField,
    language: String?,
): Boolean = catalogSourceSupportsFilter(source, field, language)

internal fun isCatalogLanguageFilterOptionAvailable(
    language: String,
    field: CatalogSearchField,
    sources: List<CatalogSourcePreference>,
): Boolean = sources.any { source ->
    catalogSourceSupportsFilter(source, field, language)
}

private fun catalogSourceSupportsFilter(
    source: CatalogSourcePreference,
    field: CatalogSearchField,
    language: String?,
): Boolean {
    val capabilities = CatalogSearchPlanner.capabilities[source] ?: return false
    return field in capabilities.supportedFields &&
        (language.isNullOrBlank() || capabilities.supportsLanguage(language))
}

internal fun facetPresentation(
    count: Int,
    operations: List<CatalogSearchProgressItem>,
    showCounts: Boolean,
): FacetPresentation? {
    if (!showCounts) return null
    if (operations.isEmpty()) return null
    if (operations.any { it.outcome is CatalogOperationOutcome.Pending }) {
        return FacetPresentation.Pending(count)
    }
    if (operations.all { it.outcome is CatalogOperationOutcome.Skipped }) {
        val languageMismatch = operations.any {
            (it.outcome as? CatalogOperationOutcome.Skipped)?.reason ==
                CatalogOperationSkipReason.IncompatibleLanguage
        }
        return FacetPresentation.Skipped(
            if (languageMismatch) "Incompatible language" else "Not supported",
        )
    }
    val executedOperations = operations.filterNot {
        it.outcome is CatalogOperationOutcome.Skipped
    }
    if (
        executedOperations.isNotEmpty() &&
        executedOperations.all { it.outcome is CatalogOperationOutcome.Failed }
    ) {
        return FacetPresentation.Unavailable
    }
    return FacetPresentation.Ready(count)
}

@Composable
private fun FilterRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
    status: FacetPresentation? = null,
    unavailableLabel: String = "Unavailable",
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        supportingContent = supporting?.let { text ->
            { Text(text) }
        },
        trailingContent = status?.let { facetStatus ->
            facetStatusLabel(facetStatus, unavailableLabel)?.let { label ->
                {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (facetStatus) {
                            FacetPresentation.Unavailable,
                            is FacetPresentation.RootBroken -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.End,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(label)
    }
}

@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverBookActionSheet(
    book: AudioBook,
    coverArtDisplayMode: CoverArtDisplayMode,
    onDismiss: () -> Unit,
    onOpenBook: () -> Unit,
    onGoToAuthor: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToLibrary: () -> Unit,
    onDownloadBook: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onCastAudio: () -> Unit,
    onShare: () -> Unit,
    onDonate: (() -> Unit)?,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scrollState = rememberScrollState()
    val inLibrary = book.libraryStatus == LibraryStatus.InLibrary
    val hasDownloads = book.hasDownloadedChapters()
    val missingDownloads = book.chapters.count { !it.isDownloaded() && !it.listenUrl.isNullOrBlank() }
    val canDownload = missingDownloads > 0 && book.activeDownloadChapterCount() == 0
    val canQueue = book.chapters.isNotEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    BookCover(
                        book = book,
                        modifier = Modifier.coverArtRowSize(coverArtDisplayMode, 56.dp),
                        cornerRadius = 14,
                        displayMode = coverArtDisplayMode,
                    )
                },
                supportingContent = {
                    val downloaded = book.downloadedChapterCount()
                    val total = book.chapters.size
                    val active = book.activeDownloadChapterCount()
                    val status = when {
                        active > 0 -> "Downloading $downloaded/$total"
                        downloaded > 0 && downloaded == total -> "Fully downloaded"
                        downloaded > 0 -> "Partially downloaded"
                        else -> book.author
                    }
                    Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (book.activeDownloadChapterCount() > 0) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { book.downloadProgressFraction() },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                            )
                        }
                        IconButton(onClick = onToggleLike) {
                            Icon(
                                imageVector = if (book.isFavorite) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = if (book.isFavorite) "Unlike audiobook" else "Like audiobook",
                                tint = if (book.isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            ) {
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BookSourceInfoRow(book = book)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DiscoverActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                label = "Go to audiobook",
                onClick = onOpenBook,
            )
            if (book.authorSearchQuery().isNotBlank()) {
                DiscoverActionRow(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Go to author",
                    onClick = onGoToAuthor,
                )
            }
            DiscoverActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                label = "Share audiobook",
                onClick = onShare,
            )
            if (canQueue) {
                DiscoverActionRow(
                    icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                    label = "Cast audio",
                    onClick = onCastAudio,
                )
            }
            if (canQueue) {
                DiscoverActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    label = "Play next",
                    onClick = onPlayNext,
                )
                DiscoverActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    label = "Add to queue",
                    onClick = onAddToQueue,
                )
            }
            if (!inLibrary) {
                DiscoverActionRow(
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = "Add to library",
                    onClick = onAddToLibrary,
                )
            }
            when {
                hasDownloads && book.source != BookSource.LocalAsset -> {
                    DiscoverActionRow(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = "Delete downloads",
                        onClick = onDeleteDownloads,
                    )
                }
                canDownload -> {
                    DiscoverActionRow(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "Download book",
                        onClick = onDownloadBook,
                    )
                }
            }
            onDonate?.let {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                DiscoverActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    label = "Donate",
                    onClick = it,
                )
            }
            Box(modifier = Modifier.size(1.dp).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun DiscoverActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon,
    ) {
        Text(label)
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

@Composable
private fun DiscoverSkeletonCard(modifier: Modifier = Modifier) {
    val tonal = LocalTonalSurfaces.current
    Surface(
        color = tonal.listItem,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(18.dp)
                    .padding(horizontal = 10.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(14.dp)
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

private fun AudioBook.discoverMatchSummary(
    query: String,
    field: CatalogSearchField,
): String? {
    val normalizedQuery = query.normalizedSearchText()
    if (normalizedQuery.isBlank()) return null
    fun String.matchesQuery(): Boolean = normalizedSearchText().contains(normalizedQuery)
    val chapter = chapters.firstOrNull { chapter ->
        when (field) {
            CatalogSearchField.All -> chapter.title.matchesQuery() ||
                chapter.reader.orEmpty().matchesQuery() ||
                chapter.director.orEmpty().matchesQuery()
            CatalogSearchField.Chapter -> chapter.title.matchesQuery()
            CatalogSearchField.Reader -> chapter.reader.orEmpty().matchesQuery()
            CatalogSearchField.Title,
            CatalogSearchField.Author,
            CatalogSearchField.Genre,
            CatalogSearchField.Epoch,
            CatalogSearchField.Kind -> false
        }
    } ?: return null
    val reader = chapter.reader
        ?.takeIf { it.isNotBlank() }
        ?.let { " • $it" }
        .orEmpty()
    val prefix = if (chapter.number > 0) "Chapter ${chapter.number}: " else "Chapter: "
    return "$prefix${chapter.title}$reader"
}

private fun AudioBook.matchesSuggestion(query: String, field: CatalogSearchField): Boolean {
    val normalizedQuery = query.normalizedSearchText()
    if (normalizedQuery.isBlank()) return false
    fun String.matchesQuery(): Boolean = normalizedSearchText().contains(normalizedQuery)
    return when (field) {
        CatalogSearchField.All -> catalogSearchMatch(normalizedQuery, CatalogSearchField.All) != null
        CatalogSearchField.Title -> title.matchesQuery()
        CatalogSearchField.Chapter -> chapters.any { it.title.matchesQuery() }
        CatalogSearchField.Author -> author.matchesQuery()
        CatalogSearchField.Reader -> chapters.any { chapter ->
            chapter.reader.orEmpty().matchesQuery() ||
                chapter.director.orEmpty().matchesQuery()
        } || narrators.any { it.matchesQuery() }
        CatalogSearchField.Genre -> genres.any { genre ->
            CatalogGenreTranslations.searchTermsForName(genre).any { it.matchesQuery() }
        } || literaryGenres.any { tag ->
            CatalogGenreTranslations.searchTermsForName(tag.name).any { it.matchesQuery() } ||
                tag.slug.matchesQuery()
        }
        CatalogSearchField.Epoch -> literaryEpochs.any { tag ->
            CatalogGenreTranslations.searchTermsForName(tag.name).any { it.matchesQuery() } ||
                tag.slug.matchesQuery()
        }
        CatalogSearchField.Kind -> literaryKinds.any { tag ->
            CatalogGenreTranslations.searchTermsForName(tag.name).any { it.matchesQuery() } ||
                tag.slug.matchesQuery()
        }
    }
}

private fun List<AudioBook>.dedupeSuggestionBooks(): List<AudioBook> {
    val seenIds = linkedSetOf<String>()
    val seenBooks = linkedSetOf<String>()
    return filter { book ->
        seenIds.add(book.id) && seenBooks.add(BookMerger.matchKey(book))
    }
}
