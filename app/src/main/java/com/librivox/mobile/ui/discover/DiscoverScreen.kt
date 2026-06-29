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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogGenreTranslations
import com.librivox.mobile.catalog.DiscoverLoadRequest
import com.librivox.mobile.catalog.DiscoverSortOrder
import com.librivox.mobile.catalog.BookMerger
import com.librivox.mobile.catalog.catalogLanguageCacheKey
import com.librivox.mobile.catalog.catalogLanguageRequestFilters
import com.librivox.mobile.catalog.catalogSearchMatch
import com.librivox.mobile.catalog.dedupeCatalogBooks
import com.librivox.mobile.catalog.normalizedCatalogSearchText
import com.librivox.mobile.catalog.rankForCatalogSearch
import com.librivox.mobile.catalog.sanitizeCatalogSearchQuery
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
import com.librivox.mobile.ui.components.BookCardPressOverlay
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.BookSourceInfoRow
import com.librivox.mobile.ui.components.bookCardPressBorder
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.coverArtAspectRatio
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.rememberBookCardPressFeedback
import com.librivox.mobile.ui.components.rememberCoverArtDisplayMode
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.navigation.BookSharedElementSource
import com.librivox.mobile.ui.navigation.BookSharedElementType
import com.librivox.mobile.ui.navigation.BookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalActiveBookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.Routes
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
private const val CollapsedSearchSuggestions = 3

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

private val QuickSearchFields = listOf(
    CatalogSearchField.Title,
    CatalogSearchField.Author,
    CatalogSearchField.Chapter,
    CatalogSearchField.Reader,
    CatalogSearchField.All,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    gridState: LazyGridState = rememberLazyGridState(),
    onOpenBook: (String, BookSharedTransitionKey?) -> Unit,
    onOpenAuthorSearch: (AudioBook) -> Unit,
    onOpenCatalogSettings: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val activeSharedTransitionKey = LocalActiveBookSharedTransitionKey.current
    val latestActiveSharedTransitionKey = rememberUpdatedState(activeSharedTransitionKey)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val historyEntries by graph.app.searchHistoryRepository.entries.collectAsState(initial = emptyList())
    val discoverCache by graph.app.discoverRepository.discoverCache.collectAsState()
    val networkStatus by graph.app.networkStatusMonitor.status.collectAsState()
    val tonal = LocalTonalSurfaces.current
    val coverArtDisplayMode = rememberCoverArtDisplayMode()

    val initialCache = remember { graph.app.discoverRepository.discoverCache.value }
    val initialSearchField = remember {
        if (initialCache.query.isBlank() && initialCache.searchField == CatalogSearchField.All) {
            CatalogSearchField.Title
        } else {
            initialCache.searchField
        }
    }
    var searchField by remember { mutableStateOf(initialSearchField) }
    var query by remember { mutableStateOf(initialCache.query) }
    var searchExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadingReset by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(initialCache.results) }
    var nextOffset by remember { mutableIntStateOf(initialCache.nextOffset) }
    var canLoadMore by remember { mutableStateOf(initialCache.canLoadMore) }
    var sortOrder by remember { mutableStateOf(initialCache.sortOrder) }
    var prefillSearchRevision by remember { mutableIntStateOf(0) }
    var activeSearchQuery by remember { mutableStateOf(initialCache.query) }
    var activeSearchField by remember { mutableStateOf(initialCache.searchField) }
    var preferredLanguages by remember {
        mutableStateOf(graph.app.playbackSettingsStore.preferredLanguages)
    }
    var bookSourcePreference by remember {
        mutableStateOf(graph.app.playbackSettingsStore.bookSourcePreference)
    }
    var downloadsOnlyModeEnabled by remember {
        mutableStateOf(graph.app.playbackSettingsStore.downloadsOnlyModeEnabled)
    }
    var prefillLanguageFilter by remember { mutableStateOf("") }
    var actionBook by remember { mutableStateOf<AudioBook?>(null) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var autocompleteQuery by remember { mutableStateOf("") }
    var searchRecommendationsExpanded by remember { mutableStateOf(false) }
    val languageFilters = prefillLanguageFilter
        .takeIf { it.isNotBlank() }
        ?.let { listOf(it) }
        ?: preferredLanguages.catalogLanguageRequestFilters(bookSourcePreference)
    val languageFilterKey = languageFilters.catalogLanguageCacheKey()
    val effectiveSortOrder = sortOrder
    val noSourcesSelected = bookSourcePreference.enabledSources.isEmpty()
    val isOffline = !networkStatus.isOnline
    val discoveryPaused = isOffline || downloadsOnlyModeEnabled
    val normalizedInputQuery = sanitizeCatalogSearchQuery(query).take(120)

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

    fun collapseSearchUi() {
        searchExpanded = false
        focusManager.clearFocus(force = true)
    }

    fun updateQueryInput(input: String) {
        query = input.take(240)
        searchRecommendationsExpanded = false
    }

    fun loadPage(
        reset: Boolean,
        q: String = activeSearchQuery,
        field: CatalogSearchField = activeSearchField,
        languages: List<String> = languageFilters,
        order: DiscoverSortOrder = effectiveSortOrder,
    ) {
        val languageKey = languages.catalogLanguageCacheKey()
        if (!reset && loading) return
        val normalizedQuery = sanitizeCatalogSearchQuery(q).take(120)
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
        val visibleResultsMatchRequest = discoverCache.describes(
            query = normalizedQuery,
            field = field,
            language = languageKey,
            bookSource = bookSourcePreference,
            sortOrder = order,
        )
        if (
            reset &&
            visibleResultsMatchRequest &&
            discoverCache.results.isNotEmpty() &&
            discoverCache.loadedAtMillis > 0L &&
            !discoverCache.isLoading
        ) {
            results = discoverCache.results
            nextOffset = discoverCache.nextOffset
            canLoadMore = discoverCache.canLoadMore
            loading = false
            loadingReset = false
            error = discoverCache.errorMessage
            activeSearchQuery = normalizedQuery
            activeSearchField = field
            return
        }
        loading = true
        loadingReset = reset
        error = null
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
                field = field,
                languages = languages,
                languageKey = languageKey,
                sourcePreference = bookSourcePreference,
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
                language = languageFilterKey,
                bookSource = bookSourcePreference,
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
        activeSearchField = discoverCache.searchField
    }
    val discoverHydrationKey = remember(results) {
        results.joinToString("|") { it.id }
    }
    LaunchedEffect(discoverHydrationKey) {
        if (results.isEmpty()) return@LaunchedEffect
        delay(DiscoverDetailHydrationDelayMillis)
        if (latestActiveSharedTransitionKey.value != null) return@LaunchedEffect
        graph.app.discoverRepository.hydrateVisibleBookDetails(
            books = results,
            limit = DiscoverResultDetailHydrationLimit,
        )
    }

    fun runSearch(
        q: String,
        field: CatalogSearchField,
        recordHistory: Boolean = false,
    ) {
        val normalizedQuery = sanitizeCatalogSearchQuery(q).take(120)
        searchRecommendationsExpanded = false
        query = normalizedQuery
        searchField = field
        activeSearchQuery = normalizedQuery
        activeSearchField = field
        if (normalizedQuery.isBlank()) {
            prefillLanguageFilter = ""
        } else if (recordHistory) {
            scope.launch { graph.app.searchHistoryRepository.add(normalizedQuery) }
        }
        loadPage(reset = true, q = normalizedQuery, field = field, order = effectiveSortOrder)
    }

    // Consume a one-shot prefill from book/player metadata taps.
    val prefill by graph.app.discoverPrefill.collectAsState()
    LaunchedEffect(prefill) {
        val p = prefill ?: return@LaunchedEffect
        searchField = p.field
        prefillLanguageFilter = p.language
        prefillSearchRevision++
        searchExpanded = false
        runSearch(p.query, p.field)
        graph.app.discoverPrefill.value = null
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

    LaunchedEffect(activeSearchQuery, activeSearchField, languageFilterKey, bookSourcePreference, effectiveSortOrder, prefillSearchRevision) {
        if (discoverCache.describes(
                query = activeSearchQuery,
                field = activeSearchField,
                language = languageFilterKey,
                bookSource = bookSourcePreference,
                sortOrder = effectiveSortOrder,
            ) && (discoverCache.loadedAtMillis > 0L || discoverCache.isLoading)
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
    LaunchedEffect(normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            autocompleteQuery = ""
            return@LaunchedEffect
        }
        delay(140)
        autocompleteQuery = normalizedQuery
    }
    val isSearchBusy =
        activeSearchQuery.isNotBlank() && loading && loadingReset
    val isInitialDiscoverLoad = !noSourcesSelected &&
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
    val autocompleteFlow = remember(autocompleteQuery, searchField, languageFilterKey, bookSourcePreference) {
        graph.app.catalogAutocompleteRepository.suggestions(
            query = autocompleteQuery,
            field = searchField,
            languages = languageFilters,
            sourcePreference = bookSourcePreference,
        )
    }
    val autocompleteSuggestions by autocompleteFlow.collectAsState(initial = emptyList())
    val searchSuggestions = remember(
        autocompleteQuery,
        results,
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
                results.asSequence()
                    .filter { it.matchesSuggestion(autocompleteQuery, searchField) }
                    .take(MaxSearchSuggestions)
                    .toList()
            }
            (visibleResultSuggestions + autocompleteSuggestions)
                .dedupeSuggestionBooks()
                .take(MaxSearchSuggestions)
        }
    }
    LaunchedEffect(autocompleteQuery, searchField) {
        searchRecommendationsExpanded = false
    }
    val visibleSearchSuggestions = remember(searchSuggestions, searchRecommendationsExpanded) {
        if (searchRecommendationsExpanded) {
            searchSuggestions
        } else {
            searchSuggestions.take(CollapsedSearchSuggestions)
        }
    }
    val hiddenSearchSuggestionCount = searchSuggestions.size - visibleSearchSuggestions.size
    val searchHistoryVisible = normalizedQuery.isBlank() && historyEntries.isNotEmpty()
    val searchDropdownExpanded = searchExpanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp),
    ) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { updateQueryInput(it) },
                    onSearch = {
                        collapseSearchUi()
                        runSearch(query, searchField, recordHistory = true)
                    },
                    expanded = searchDropdownExpanded,
                    onExpandedChange = { expanded ->
                        searchExpanded = expanded
                        if (!expanded) focusManager.clearFocus(force = true)
                    },
                    placeholder = { Text(searchHint(searchField)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSearchBusy || isUpdatingSearchSuggestions) {
                                ContainedLoadingIndicator(
                                    modifier = Modifier
                                        .padding(end = if (query.isNotBlank()) 0.dp else 12.dp)
                                        .size(28.dp),
                                )
                            }
                            if (query.isNotBlank()) {
                                IconButton(onClick = {
                                    collapseSearchUi()
                                    runSearch("", CatalogSearchField.Title)
                                }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    },
                )
            },
            expanded = searchDropdownExpanded,
            onExpandedChange = { expanded ->
                searchExpanded = expanded
                if (!expanded) focusManager.clearFocus(force = true)
            },
            colors = SearchBarDefaults.colors(containerColor = tonal.listItem),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            // Search history when focused with empty input.
            if (searchHistoryVisible) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .background(tonal.listItem),
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    historyEntries.take(MaxRecentSearches).forEach { entry ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchField = CatalogSearchField.Title
                                    query = entry.query
                                    collapseSearchUi()
                                    runSearch(entry.query, CatalogSearchField.Title, recordHistory = true)
                                },
                            leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                            headlineContent = { Text(entry.query) },
                            supportingContent = { Text("Recent search") },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch { graph.app.searchHistoryRepository.remove(entry) }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete recent search")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    TextButton(
                        onClick = { scope.launch { graph.app.searchHistoryRepository.clear() } },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Clear history") }
                }
            } else if (searchSuggestions.isNotEmpty()) {
                // Inline suggestions: typeahead off current visible results.
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .background(tonal.listItem),
                ) {
                    visibleSearchSuggestions.forEach { book ->
                        val suggestionSummary = book.discoverMatchSummary(normalizedQuery, searchField)
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val suggestionQuery = book.title.ifBlank { autocompleteQuery }
                                    query = suggestionQuery
                                    collapseSearchUi()
                                    runSearch(suggestionQuery, searchField, recordHistory = true)
                                },
                            leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                            headlineContent = { Text(book.title) },
                            supportingContent = { Text(suggestionSummary ?: book.author) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    if (hiddenSearchSuggestionCount > 0) {
                        TextButton(
                            onClick = { searchRecommendationsExpanded = true },
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text("Show more")
                        }
                    }
                }
            }
        }

        QuickSearchFieldRow(
            selectedField = searchField,
            onFieldSelected = { field ->
                searchField = field
                if (activeSearchQuery.isNotBlank() && normalizedQuery == activeSearchQuery) {
                    activeSearchField = field
                    loadPage(reset = true, q = activeSearchQuery, field = field, order = effectiveSortOrder)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
                    onClear = { runSearch("", CatalogSearchField.Title) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                )
            } else if (
                results.isEmpty() &&
                !showSkeletonResults &&
                (!loading || discoveryPaused) &&
                (error == null || discoveryPaused)
            ) {
                DiscoverEmptyState(
                    query = visibleSearchQuery,
                    field = visibleSearchField,
                    noSourcesSelected = noSourcesSelected,
                    isOffline = isOffline,
                    downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                    onDownloadsOnlyModeChange = {
                        downloadsOnlyModeEnabled = it
                        graph.app.playbackSettingsStore.saveDownloadsOnlyModeEnabled(it)
                    },
                    onClearSearch = { runSearch("", CatalogSearchField.Title) },
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
                                count = results.size,
                                canLoadMore = canLoadMore,
                                isLoading = showSkeletonResults,
                                isOffline = isOffline,
                                downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                                bookSource = bookSourcePreference,
                                languageSummary = preferredLanguages.discoverLanguageSummary(bookSourcePreference),
                                sortOrder = effectiveSortOrder,
                                onOpenFilters = { filterSheetOpen = true },
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DiscoverSearchHeader(
                                query = visibleSearchQuery,
                                field = visibleSearchField,
                                count = results.size,
                                canLoadMore = canLoadMore,
                                isLoading = isSearchBusy || showSkeletonResults,
                                isOffline = isOffline,
                                downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                                sortOrder = effectiveSortOrder,
                                onOpenFilters = { filterSheetOpen = true },
                            )
                        }
                    }
                    if (showSkeletonResults) {
                        items(count = 12, key = { "discover-skeleton-$it" }) {
                            DiscoverSkeletonCard()
                        }
                    } else {
                        items(results, key = { it.id }) { book ->
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
                        if (results.isNotEmpty()) {
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
            error?.let {
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
                    graph.app.downloadManager.downloadCover(committedBook)
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

    if (filterSheetOpen) {
        DiscoverFilterSheet(
            sortOrder = effectiveSortOrder,
            searchField = searchField,
            query = visibleSearchQuery,
            onDismiss = { filterSheetOpen = false },
            onSortOrderChange = { sortOrder = it },
            onSearchFieldChange = { field ->
                searchField = field
                if (activeSearchQuery.isNotBlank()) {
                    activeSearchField = field
                    loadPage(reset = true, q = activeSearchQuery, field = field, order = effectiveSortOrder)
                }
            },
            onClearSearch = {
                runSearch("", CatalogSearchField.Title)
                filterSheetOpen = false
            },
            onGenreSelected = { genre ->
                searchField = CatalogSearchField.Genre
                query = genre
                searchExpanded = false
                filterSheetOpen = false
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
    CatalogSearchField.All -> "Search title, chapter, author, or reader"
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

@Composable
private fun QuickSearchFieldRow(
    selectedField: CatalogSearchField,
    onFieldSelected: (CatalogSearchField) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(QuickSearchFields, key = { it.name }) { field ->
            FilterChip(
                selected = selectedField == field,
                onClick = { onFieldSelected(field) },
                label = { Text(searchFieldLabel(field)) },
            )
        }
    }
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
    noSourcesSelected: Boolean,
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    onDownloadsOnlyModeChange: (Boolean) -> Unit,
    onClearSearch: () -> Unit,
    onOpenCatalogSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    val catalogPaused = isOffline || downloadsOnlyModeEnabled
    val isSearch = query.isNotBlank() && !noSourcesSelected && !catalogPaused
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
                imageVector = if (isSearch || noSourcesSelected || catalogPaused) {
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
            } else if (noSourcesSelected && !isOffline) {
                FilledTonalButton(onClick = onOpenCatalogSettings) {
                    Text("Select sources")
                }
            } else if (isSearch) {
                FilledTonalButton(onClick = onClearSearch) {
                    Text("Clear search")
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
                    DiscoverHeaderFilterChip(
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
                    DiscoverHeaderFilterChip(
                        sortOrder = sortOrder,
                        onOpenFilters = onOpenFilters,
                    )
                }
            }
            Text(
                text = when {
                    downloadsOnlyModeEnabled -> "Downloads only mode. Discovery is paused."
                    isOffline -> "Offline. Showing cached results."
                    else -> "$count titles loaded${if (canLoadMore) " · more available" else ""}"
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
private fun DiscoverHeaderFilterChip(
    sortOrder: DiscoverSortOrder,
    onOpenFilters: () -> Unit,
) {
    FilterChip(
        selected = true,
        onClick = onOpenFilters,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        label = { Text(discoverSortLabel(sortOrder)) },
    )
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverFilterSheet(
    sortOrder: DiscoverSortOrder,
    searchField: CatalogSearchField,
    query: String,
    onDismiss: () -> Unit,
    onSortOrderChange: (DiscoverSortOrder) -> Unit,
    onSearchFieldChange: (CatalogSearchField) -> Unit,
    onClearSearch: () -> Unit,
    onGenreSelected: (String) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            FilterSection(title = "Order") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DiscoverSortOrder.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = sortOrder == option,
                            onClick = { onSortOrderChange(option) },
                            label = { Text(discoverSortLabel(option)) },
                        )
                    }
                }
            }
            FilterSection(title = "Filter results by") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchFields, key = { it.name }) { field ->
                        FilterChip(
                            selected = searchField == field,
                            onClick = { onSearchFieldChange(field) },
                            label = { Text(searchFieldLabel(field)) },
                        )
                    }
                }
            }
            if (query.isNotBlank()) {
                TextButton(onClick = onClearSearch) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Clear search")
                }
            }
            HorizontalDivider()
            FilterSection(title = "Explore genres") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PopularGenres, key = { it }) { genre ->
                        AssistChip(
                            onClick = { onGenreSelected(genre) },
                            label = { Text(genre) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
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
                headlineContent = {
                    Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            )
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
                    label = "Save book to library",
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
        headlineContent = { Text(label) },
    )
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
