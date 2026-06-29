@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.librivox.mobile.catalog.asSingleSourcePreference
import com.librivox.mobile.catalog.currentDailyFeaturedDayKey
import com.librivox.mobile.catalog.featuredCatalogSource
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookProgressSnapshot
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.activeDownloadBookAssetCount
import com.librivox.mobile.model.activeDownloadChapterCount
import com.librivox.mobile.model.authorSearchQuery
import com.librivox.mobile.model.canReadSourceText
import com.librivox.mobile.model.donationLink
import com.librivox.mobile.model.downloadProgressFraction
import com.librivox.mobile.model.downloadableBookAssetCount
import com.librivox.mobile.model.downloadedBookAssetCount
import com.librivox.mobile.model.downloadedChapterCount
import com.librivox.mobile.model.hasDownloadedBookAssets
import com.librivox.mobile.model.hasDownloadedChapters
import com.librivox.mobile.model.isDownloaded
import com.librivox.mobile.model.missingDownloadAssetCount
import com.librivox.mobile.model.normalizedDurationMs
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.ui.components.BookCardPressOverlay
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.BookSourceInfoRow
import com.librivox.mobile.ui.components.bookCardPressBorder
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.coverArtAspectRatio
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.formatHmsFromSeconds
import com.librivox.mobile.ui.components.rememberBookCardPressFeedback
import com.librivox.mobile.ui.components.rememberCoverArtDisplayMode
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.navigation.BookSharedElementSource
import com.librivox.mobile.ui.navigation.BookSharedElementType
import com.librivox.mobile.ui.navigation.BookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalActiveBookSharedTransitionKey
import com.librivox.mobile.ui.navigation.bookSharedBounds
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.Routes
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HomeDetailHydrationDelayMillis = 4_000L
private const val HomeInitialRevealDelayMillis = 160L

private fun homeHeroCarouselHeight(displayMode: CoverArtDisplayMode) =
    maxOf(300.dp, 168.dp / displayMode.frameAspectRatio + 64.dp)

private fun homeShelfCarouselHeight(displayMode: CoverArtDisplayMode) =
    maxOf(248.dp, 160.dp / displayMode.frameAspectRatio + 72.dp)

private enum class HomeSectionState {
    Loading,
    Content,
}

private data class HomeStatusMessage(
    val title: String,
    val message: String,
    val actionLabel: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    listState: LazyListState = rememberLazyListState(),
    lazyLoadRequest: Int = 0,
    onOpenBook: (String, BookSharedTransitionKey?) -> Unit,
    onOpenReader: (String, String?) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenAuthorSearch: (AudioBook) -> Unit,
    onOpenDiscover: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeSharedTransitionKey = LocalActiveBookSharedTransitionKey.current
    val latestActiveSharedTransitionKey = rememberUpdatedState(activeSharedTransitionKey)
    val libraryRepository = graph.app.libraryRepository
    val initialLibraryState = remember(libraryRepository) {
        libraryRepository.cachedState() ?: LibraryState()
    }
    val libraryState by libraryRepository.state.collectAsState(initial = initialLibraryState)
    val stagedBookDetails by graph.app.stagedBookDetails.collectAsState()
    val playerState by graph.playerStateRepository.state.collectAsState()
    val featuredCache by graph.app.discoverRepository.dailyFeaturedCache.collectAsState()
    val networkStatus by graph.app.networkStatusMonitor.status.collectAsState()
    val progressRevision by graph.app.progressStore.revision.collectAsState()
    val coverArtDisplayMode = rememberCoverArtDisplayMode()
    var actionBook by remember { mutableStateOf<AudioBook?>(null) }
    var initialRevealComplete by rememberSaveable { mutableStateOf(false) }
    var completedLazyLoadRequest by rememberSaveable { mutableStateOf(0) }
    var retainedFeaturedAudiobooks by remember { mutableStateOf<List<AudioBook>>(emptyList()) }
    var hasRenderedHomeContent by rememberSaveable { mutableStateOf(false) }
    var bookSourcePreference by remember {
        mutableStateOf(graph.app.playbackSettingsStore.bookSourcePreference)
    }
    var downloadsOnlyModeEnabled by remember {
        mutableStateOf(graph.app.playbackSettingsStore.downloadsOnlyModeEnabled)
    }
    val library = libraryState
    val homeHasWarmContent = hasWarmHomeContent(
        hasRenderedContent = hasRenderedHomeContent,
        hasLibraryBooks = library.books.isNotEmpty(),
        hasStagedBookDetails = stagedBookDetails.isNotEmpty(),
        hasFeaturedCacheBooks = featuredCache.books.isNotEmpty(),
        hasRetainedFeaturedBooks = retainedFeaturedAudiobooks.isNotEmpty(),
    )
    val lazyLoadPending = shouldDeferHomeContent(
        lazyLoadRequest = lazyLoadRequest,
        completedLazyLoadRequest = completedLazyLoadRequest,
        hasWarmContent = homeHasWarmContent,
        deferWarmContent = false,
    )
    var lazyContentReady by remember(lazyLoadRequest) {
        mutableStateOf(!lazyLoadPending)
    }
    val deferHomeContent = !lazyContentReady
    val featuredDayKey = currentDailyFeaturedDayKey()
    val isOffline = !networkStatus.isOnline
    val downloadsOnlyActive = isOffline || downloadsOnlyModeEnabled
    val progressBooks = remember(deferHomeContent, library.books, stagedBookDetails, downloadsOnlyActive) {
        if (deferHomeContent) {
            emptyList()
        } else {
            (library.books + stagedBookDetails.values)
                .distinctBy { it.id }
                .filter { book -> !downloadsOnlyActive || book.hasDownloadedBookAssets() }
        }
    }
    val progressBookIds = remember(progressBooks) {
        progressBooks
            .asSequence()
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
    val progressBookIdsKey = remember(progressBookIds) {
        progressBookIds.joinToString("|")
    }
    val progressByBookId = remember(deferHomeContent, progressBookIdsKey, progressRevision) {
        if (deferHomeContent || progressBookIds.isEmpty()) {
            emptyMap()
        } else {
            graph.app.progressStore.savedProgressSnapshots(progressBookIds)
        }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val sourceListener = graph.app.playbackSettingsStore.registerBookSourceListener {
            bookSourcePreference = it
        }
        val downloadListener = graph.app.playbackSettingsStore.registerDownloadSettingsListener {
            downloadsOnlyModeEnabled = graph.app.playbackSettingsStore.downloadsOnlyModeEnabled
        }
        onDispose {
            graph.app.playbackSettingsStore.unregisterListener(sourceListener)
            graph.app.playbackSettingsStore.unregisterListener(downloadListener)
        }
    }

    LaunchedEffect(initialRevealComplete) {
        if (!initialRevealComplete) {
            delay(HomeInitialRevealDelayMillis)
            initialRevealComplete = true
        }
    }

    LaunchedEffect(lazyLoadRequest, homeHasWarmContent) {
        if (lazyLoadRequest > 0 && lazyLoadRequest != completedLazyLoadRequest) {
            if (lazyLoadPending) {
                withFrameNanos { }
            }
            lazyContentReady = true
            completedLazyLoadRequest = lazyLoadRequest
        }
    }

    LaunchedEffect(deferHomeContent, bookSourcePreference, featuredDayKey, networkStatus.isOnline, downloadsOnlyModeEnabled) {
        if (deferHomeContent || !networkStatus.isOnline || downloadsOnlyModeEnabled) return@LaunchedEffect
        graph.app.discoverRepository.loadDailyFeatured(
            sourcePreference = bookSourcePreference,
            dayKey = featuredDayKey,
            limit = FeaturedAudiobookLimit,
        )
    }

    fun openBook(book: AudioBook, transitionKey: BookSharedTransitionKey? = null) {
        onOpenBook(book.id, transitionKey)
        scope.launch { graph.app.stageBookDetail(book) }
    }

    fun progressForBook(book: AudioBook): BookProgressSnapshot {
        val saved = progressByBookId[book.id] ?: BookProgressSnapshot()
        val activeMs = if (book.id == playerState.bookId && playerState.hasMedia) {
            playerState.positionMs.coerceAtLeast(1L)
        } else {
            0L
        }
        return saved.copy(positionMs = maxOf(saved.positionMs, activeMs))
    }

    fun positionForBook(book: AudioBook): Long {
        return progressForBook(book).positionMs
    }

    val inProgress = remember(
        deferHomeContent,
        progressBooks,
        progressByBookId,
        playerState.bookId,
        playerState.positionMs,
        playerState.hasMedia,
    ) {
        if (deferHomeContent) {
            emptyList()
        } else {
            val currentBookId = playerState.bookId
            continueShelfBooks(
                books = progressBooks,
                progressByBookId = progressBooks.associate { book ->
                    book.id to progressForBook(book)
                },
                currentBookId = currentBookId,
                hasCurrentMedia = playerState.hasMedia,
                limit = 10,
            )
        }
    }
    val recentlyAdded = remember(deferHomeContent, library, downloadsOnlyActive) {
        if (deferHomeContent) {
            emptyList()
        } else {
            library.libraryBooks
                .filter { book -> !downloadsOnlyActive || book.hasDownloadedBookAssets() }
                .sortedByDescending { it.addedAtMillis }
                .take(10)
        }
    }
    val featuredSource = bookSourcePreference.featuredCatalogSource()
    val featuredBookSourcePreference = featuredSource?.asSingleSourcePreference()
    val noSourcesSelected = bookSourcePreference.enabledSources.isEmpty()
    val featuredSectionTitle = when (featuredSource) {
        CatalogSourcePreference.WolneLektury -> "Zakazane książki"
        else -> "Featured audiobooks"
    }
    val featuredCacheMatches = !deferHomeContent &&
        featuredBookSourcePreference != null &&
        featuredCache.matches(featuredDayKey, featuredBookSourcePreference, featuredSource)
    val featuredAudiobooks = remember(deferHomeContent, featuredCacheMatches, featuredCache.books, library) {
        if (deferHomeContent || !featuredCacheMatches) {
            emptyList()
        } else {
            featuredCache.books.map { book -> library.bookById(book.id) ?: book }
        }
    }
    LaunchedEffect(featuredAudiobooks) {
        if (featuredAudiobooks.isNotEmpty()) {
            retainedFeaturedAudiobooks = featuredAudiobooks
        }
    }
    val canUseRetainedFeatured = !deferHomeContent &&
        featuredBookSourcePreference != null &&
        !noSourcesSelected &&
        (featuredCache.isLoading || activeSharedTransitionKey?.source?.route == Routes.HOME)
    val visibleFeaturedAudiobooks = if (downloadsOnlyActive) {
        emptyList()
    } else if (featuredAudiobooks.isNotEmpty()) {
        featuredAudiobooks
    } else if (canUseRetainedFeatured) {
        retainedFeaturedAudiobooks
    } else {
        emptyList()
    }
    val featuredLoadingIndicator = shouldShowFeaturedLoadingIndicator(
        deferHomeContent = deferHomeContent,
        hasFeaturedSource = featuredBookSourcePreference != null,
        noSourcesSelected = noSourcesSelected,
        downloadsOnlyActive = downloadsOnlyActive,
        featuredCacheIsLoading = featuredCache.isLoading,
        featuredCacheMatches = featuredCacheMatches,
        featuredCacheLoadedAtMillis = featuredCache.loadedAtMillis,
    )
    val featuredLoading = featuredLoadingIndicator && visibleFeaturedAudiobooks.isEmpty()
    val featuredStatusMessage = if (deferHomeContent) {
        null
    } else {
        when {
            noSourcesSelected -> HomeStatusMessage(
                title = "No catalog sources selected",
                message = "Choose at least one catalog source in Settings to see recommendations.",
            )
            downloadsOnlyModeEnabled -> HomeStatusMessage(
                title = "Downloads only mode",
                message = "Only books saved on this device are shown. Discovery and recommendations are paused.",
                actionLabel = "Exit Downloads only",
            )
            isOffline -> HomeStatusMessage(
                title = "You're offline",
                message = if (visibleFeaturedAudiobooks.isEmpty()) {
                    "Recommendations and Discover are paused. Downloaded books are still available in Library."
                } else {
                    "Showing cached recommendations. Discover needs an internet connection for fresh results."
                },
            )
            featuredCache.errorMessage != null && visibleFeaturedAudiobooks.isEmpty() -> HomeStatusMessage(
                title = "Recommendations unavailable",
                message = featuredCache.errorMessage ?: "Could not load today's picks.",
            )
            else -> null
        }
    }
    LaunchedEffect(
        deferHomeContent,
        visibleFeaturedAudiobooks,
        inProgress,
        recentlyAdded,
        featuredLoading,
        featuredStatusMessage,
    ) {
        if (
            !deferHomeContent &&
            !hasRenderedHomeContent &&
            (
                visibleFeaturedAudiobooks.isNotEmpty() ||
                    inProgress.isNotEmpty() ||
                    recentlyAdded.isNotEmpty() ||
                    featuredStatusMessage != null ||
                    !featuredLoading
            )
        ) {
            hasRenderedHomeContent = true
        }
    }
    val featuredLoadingMessage = featuredSource?.let { source ->
        "Loading ${source.label} recommendations."
    } ?: "Loading recommendations."
    val featuredCarouselState = rememberCarouselState { visibleFeaturedAudiobooks.size }
    val continueCarouselState = rememberCarouselState { inProgress.size }
    val recentCarouselState = rememberCarouselState { recentlyAdded.size }
    val homeShelfDetailBooks = remember(visibleFeaturedAudiobooks, inProgress) {
        (visibleFeaturedAudiobooks + inProgress)
            .distinctBy { it.id }
            .take(HomeShelfDetailHydrationLimit)
    }
    val homeShelfDetailKey = remember(homeShelfDetailBooks) {
        homeShelfDetailBooks.joinToString("|") { it.id }
    }

    LaunchedEffect(deferHomeContent, homeShelfDetailKey) {
        if (deferHomeContent || homeShelfDetailBooks.isEmpty()) return@LaunchedEffect
        delay(HomeDetailHydrationDelayMillis)
        if (latestActiveSharedTransitionKey.value != null) return@LaunchedEffect
        graph.app.discoverRepository.hydrateVisibleBookDetails(homeShelfDetailBooks)
    }

    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = "LibriVox")
                },
                actions = {
                    IconButton(onClick = onOpenDiscover) {
                        Icon(Icons.Filled.Explore, contentDescription = "Discover")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = 232.dp,
            ),
        ) {
            val isHomeSharedTransition = activeSharedTransitionKey?.source?.route == Routes.HOME
            val showInitialSkeleton = shouldShowHomeInitialSkeleton(
                deferHomeContent = deferHomeContent,
                initialRevealComplete = initialRevealComplete,
                hasWarmContent = homeHasWarmContent,
                isHomeSharedTransition = isHomeSharedTransition,
            )
            featuredStatusMessage?.let { status ->
                item(key = "featured-status") {
                    HomeStatusBanner(
                        status = status,
                        onAction = if (downloadsOnlyModeEnabled && status.actionLabel != null) {
                            {
                                downloadsOnlyModeEnabled = false
                                graph.app.playbackSettingsStore.saveDownloadsOnlyModeEnabled(false)
                            }
                        } else {
                            null
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val canShowFeaturedSkeleton = !noSourcesSelected &&
                !(downloadsOnlyActive && visibleFeaturedAudiobooks.isEmpty())
            if (
                (showInitialSkeleton && canShowFeaturedSkeleton) ||
                visibleFeaturedAudiobooks.isNotEmpty() ||
                featuredLoading
            ) {
                val featuredState = if (showInitialSkeleton || featuredLoading) {
                    HomeSectionState.Loading
                } else {
                    HomeSectionState.Content
                }
                item(key = "featured") {
                    HomeSectionTransition(
                        targetState = featuredState,
                        label = "featured",
                    ) { state ->
                        if (state == HomeSectionState.Loading) {
                            HeroCarouselSkeleton(
                                title = featuredSectionTitle,
                                status = featuredLoadingMessage.takeIf { featuredLoadingIndicator },
                            )
                        } else {
                            HeroCarousel(
                                title = featuredSectionTitle,
                                loadingStatus = featuredLoadingMessage.takeIf { featuredLoadingIndicator },
                                sourceSlot = "featured",
                                books = visibleFeaturedAudiobooks,
                                carouselState = featuredCarouselState,
                                coverArtDisplayMode = coverArtDisplayMode,
                                positionForBook = ::positionForBook,
                                onOpenBook = ::openBook,
                                onOpenReader = { book -> onOpenReader(book.id, null) },
                                onLongPressBook = { actionBook = it },
                                onShareBook = { shareAudiobook(context, it) },
                            )
                        }
                    }
                }
            }
            if (showInitialSkeleton || inProgress.isNotEmpty()) {
                val continueState = if (showInitialSkeleton) {
                    HomeSectionState.Loading
                } else {
                    HomeSectionState.Content
                }
                item(key = "continue") {
                    HomeSectionTransition(
                        targetState = continueState,
                        label = "continue",
                    ) { state ->
                        if (state == HomeSectionState.Loading) {
                            ShelfSkeleton(title = "Continue listening")
                        } else {
                            Shelf(
                                title = "Continue listening",
                                sourceSlot = "continue",
                                books = inProgress,
                                carouselState = continueCarouselState,
                                coverArtDisplayMode = coverArtDisplayMode,
                                positionForBook = ::positionForBook,
                                onOpenBook = ::openBook,
                                onOpenReader = { book -> onOpenReader(book.id, null) },
                                onLongPressBook = { actionBook = it },
                                onShareBook = { shareAudiobook(context, it) },
                            )
                        }
                    }
                }
            }
            if (showInitialSkeleton || recentlyAdded.isNotEmpty()) {
                val recentState = if (showInitialSkeleton) {
                    HomeSectionState.Loading
                } else {
                    HomeSectionState.Content
                }
                item(key = "recent") {
                    HomeSectionTransition(
                        targetState = recentState,
                        label = "recent",
                    ) { state ->
                        if (state == HomeSectionState.Loading) {
                            ShelfSkeleton(title = "Recently added")
                        } else {
                            Shelf(
                                title = "Recently added",
                                sourceSlot = "recent",
                                books = recentlyAdded,
                                carouselState = recentCarouselState,
                                coverArtDisplayMode = coverArtDisplayMode,
                                positionForBook = ::positionForBook,
                                onOpenBook = ::openBook,
                                onOpenReader = { book -> onOpenReader(book.id, null) },
                                onLongPressBook = { actionBook = it },
                                onShareBook = { shareAudiobook(context, it) },
                            )
                        }
                    }
                }
            }
            item(key = "browse") {
                BrowseByGenreCard(onOpenDiscover = onOpenDiscover)
            }
        }
    }

    actionBook?.let { selected ->
        val latestBook = libraryState.bookById(selected.id) ?: selected
        HomeBookActionSheet(
            book = latestBook,
            coverArtDisplayMode = coverArtDisplayMode,
            onDismiss = { actionBook = null },
            onOpenBook = {
                actionBook = null
                openBook(latestBook)
            },
            onOpenReader = if (latestBook.canReadSourceText()) {
                {
                    actionBook = null
                    onOpenReader(latestBook.id, null)
                }
            } else {
                null
            },
            onGoToAuthor = {
                actionBook = null
                onOpenAuthorSearch(latestBook)
            },
            onToggleLike = {
                scope.launch { graph.app.libraryRepository.toggleFavorite(latestBook.id) }
            },
            onAddToLibrary = {
                scope.launch { graph.app.libraryRepository.addToLibrary(latestBook) }
                actionBook = null
            },
            onRemoveFromLibrary = {
                scope.launch { graph.app.libraryRepository.removeFromLibrary(latestBook.id) }
                actionBook = null
            },
            onDownloadBook = {
                scope.launch {
                    graph.app.libraryRepository.addToLibrary(latestBook)
                    graph.app.downloadManager.downloadAll(latestBook)
                }
                actionBook = null
            },
            onDeleteDownloads = {
                scope.launch { graph.app.downloadManager.deleteDownloads(latestBook) }
                actionBook = null
            },
            onPlayNext = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) graph.playerStateRepository.addAfterCurrent(items)
                actionBook = null
            },
            onAddToQueue = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) graph.playerStateRepository.appendToQueue(items)
                actionBook = null
            },
            onCastAudio = {
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeSectionTransition(
    targetState: HomeSectionState,
    label: String,
    content: @Composable (HomeSectionState) -> Unit,
) {
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec)
        },
        label = "home-$label-section",
    ) { state ->
        content(state)
    }
}

@Composable
private fun HomeStatusBanner(
    status: HomeStatusMessage,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = tonal.listItem,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = status.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction) {
                    Text(status.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ClampCarouselScrollPosition(
    carouselState: CarouselState,
    itemCount: Int,
) {
    LaunchedEffect(carouselState, itemCount) {
        if (itemCount > 0 && carouselState.currentItem >= itemCount) {
            carouselState.scrollToItem(itemCount - 1)
        }
    }
}

@Composable
private fun HeroCarousel(
    title: String,
    loadingStatus: String?,
    sourceSlot: String,
    books: List<AudioBook>,
    carouselState: CarouselState,
    coverArtDisplayMode: CoverArtDisplayMode,
    positionForBook: (AudioBook) -> Long,
    onOpenBook: (AudioBook, BookSharedTransitionKey) -> Unit,
    onOpenReader: (AudioBook) -> Unit,
    onLongPressBook: (AudioBook) -> Unit,
    onShareBook: (AudioBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    ClampCarouselScrollPosition(carouselState, books.size)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        loadingStatus?.let {
            FeaturedLoadingStatus(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 168.dp,
            itemSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(homeHeroCarouselHeight(coverArtDisplayMode)),
        ) { index ->
            val book = books[index]
            val transitionKey = remember(book.id, sourceSlot) {
                BookSharedTransitionKey(
                    bookId = book.id,
                    source = BookSharedElementSource(Routes.HOME, sourceSlot),
                )
            }
            HeroCarouselItem(
                book = book,
                positionMs = positionForBook(book),
                sharedTransitionKey = transitionKey,
                coverArtDisplayMode = coverArtDisplayMode,
                onClick = { onOpenBook(book, transitionKey) },
                onOpenReader = if (book.canReadSourceText()) {
                    { onOpenReader(book) }
                } else {
                    null
                },
                onLongPress = { onLongPressBook(book) },
                onShare = { onShareBook(book) },
            )
        }
    }
}

@Composable
private fun HeroCarouselSkeleton(
    title: String,
    status: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        status?.let {
            FeaturedLoadingStatus(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { 4 },
            preferredItemWidth = 168.dp,
            itemSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            HomeSkeletonCard()
        }
    }
}

@Composable
private fun FeaturedLoadingStatus(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShelfSkeleton(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalUncontainedCarousel(
            state = rememberCarouselState { 5 },
            itemWidth = 160.dp,
            itemSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp),
        ) {
            HomeSkeletonCard()
        }
    }
}

@Composable
private fun HomeSkeletonCard() {
    val tonal = LocalTonalSurfaces.current
    Card(
        colors = CardDefaults.cardColors(containerColor = tonal.listItem),
        modifier = Modifier
            .width(160.dp)
            .fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(16.dp),
                )
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(12.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroCarouselItem(
    book: AudioBook,
    positionMs: Long,
    sharedTransitionKey: BookSharedTransitionKey,
    coverArtDisplayMode: CoverArtDisplayMode,
    onClick: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val pressFeedback = rememberBookCardPressFeedback()
    val durationMs = normalizedDurationMs(book.totalDurationSeconds * 1000L)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val cardShape = MaterialTheme.shapes.large

    Card(
        modifier = Modifier
            .scale(pressFeedback.scale)
            .width(168.dp)
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = pressFeedback.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = tonal.listItem),
        border = bookCardPressBorder(pressFeedback.isPressed),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Box {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxWidth()
                            .coverArtAspectRatio(coverArtDisplayMode),
                        cornerRadius = 0,
                        displayMode = coverArtDisplayMode,
                        onOpenReader = onOpenReader,
                    )
                    CardShareButton(
                        onShare = onShare,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
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

@Composable
private fun Shelf(
    title: String,
    sourceSlot: String,
    books: List<AudioBook>,
    carouselState: CarouselState,
    coverArtDisplayMode: CoverArtDisplayMode,
    positionForBook: (AudioBook) -> Long,
    onOpenBook: (AudioBook, BookSharedTransitionKey) -> Unit,
    onOpenReader: (AudioBook) -> Unit,
    onLongPressBook: (AudioBook) -> Unit,
    onShareBook: (AudioBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    ClampCarouselScrollPosition(carouselState, books.size)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = 160.dp,
            itemSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(homeShelfCarouselHeight(coverArtDisplayMode)),
        ) { index ->
            val book = books[index]
            val transitionKey = remember(book.id, sourceSlot) {
                BookSharedTransitionKey(
                    bookId = book.id,
                    source = BookSharedElementSource(Routes.HOME, sourceSlot),
                )
            }
            ShelfCard(
                book = book,
                positionMs = positionForBook(book),
                sharedTransitionKey = transitionKey,
                coverArtDisplayMode = coverArtDisplayMode,
                onClick = { onOpenBook(book, transitionKey) },
                onOpenReader = if (book.canReadSourceText()) {
                    { onOpenReader(book) }
                } else {
                    null
                },
                onLongPress = { onLongPressBook(book) },
                onShare = { onShareBook(book) },
            )
        }
    }
}

@Composable
private fun ShelfCard(
    book: AudioBook,
    positionMs: Long,
    sharedTransitionKey: BookSharedTransitionKey,
    coverArtDisplayMode: CoverArtDisplayMode,
    onClick: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onLongPress: () -> Unit,
    onShare: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val pressFeedback = rememberBookCardPressFeedback()
    val durationMs = normalizedDurationMs(book.totalDurationSeconds * 1000L)
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val cardShape = MaterialTheme.shapes.medium

    Card(
        modifier = Modifier
            .scale(pressFeedback.scale)
            .width(160.dp)
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = pressFeedback.interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = tonal.listItem),
        border = bookCardPressBorder(pressFeedback.isPressed),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Box {
                    BookCover(
                        book = book,
                        modifier = Modifier
                            .fillMaxWidth()
                            .coverArtAspectRatio(coverArtDisplayMode),
                        displayMode = coverArtDisplayMode,
                        onOpenReader = onOpenReader,
                    )
                    CardShareButton(
                        onShare = onShare,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (book.libraryStatus == LibraryStatus.NotInLibrary) {
                        Text(
                            text = formatHmsFromSeconds(book.totalDurationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun HomeBookActionSheet(
    book: AudioBook,
    coverArtDisplayMode: CoverArtDisplayMode,
    onDismiss: () -> Unit,
    onOpenBook: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onGoToAuthor: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToLibrary: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
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
    val canRemoveFromLibrary = true
    val hasDownloads = book.hasDownloadedBookAssets()
    val missingDownloads = book.missingDownloadAssetCount()
    val canDownload = missingDownloads > 0 && book.activeDownloadBookAssetCount() == 0
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
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
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
                    val downloaded = book.downloadedBookAssetCount()
                    val total = book.downloadableBookAssetCount()
                    val active = book.activeDownloadBookAssetCount()
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
                        if (book.activeDownloadBookAssetCount() > 0) {
                            CircularProgressIndicator(
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
            HomeBookActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                label = "Go to audiobook",
                onClick = onOpenBook,
            )
            onOpenReader?.let {
                HomeBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = "Read book",
                    onClick = it,
                )
            }
            if (book.authorSearchQuery().isNotBlank()) {
                HomeBookActionRow(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Go to author",
                    onClick = onGoToAuthor,
                )
            }
            if (canQueue) {
                HomeBookActionRow(
                    icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                    label = "Cast audio",
                    onClick = onCastAudio,
                )
            }
            HomeBookActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                label = "Share audiobook",
                onClick = onShare,
            )
            if (canQueue) {
                HomeBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    label = "Play next",
                    onClick = onPlayNext,
                )
                HomeBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    label = "Add to queue",
                    onClick = onAddToQueue,
                )
            }
            if (!inLibrary) {
                HomeBookActionRow(
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = "Save book to library",
                    onClick = onAddToLibrary,
                )
            } else if (canRemoveFromLibrary) {
                HomeBookActionRow(
                    icon = { Icon(Icons.Filled.RemoveCircle, contentDescription = null) },
                    label = "Remove from library",
                    onClick = onRemoveFromLibrary,
                )
            }
            when {
                hasDownloads && book.source != BookSource.LocalAsset -> {
                    HomeBookActionRow(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = "Delete downloads",
                        onClick = onDeleteDownloads,
                    )
                }
                canDownload -> {
                    HomeBookActionRow(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "Download book",
                        onClick = onDownloadBook,
                    )
                }
            }
            onDonate?.let {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                HomeBookActionRow(
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
private fun CardShareButton(
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onShare,
        modifier = modifier
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

@Composable
private fun HomeBookActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = icon,
        headlineContent = { Text(label) },
    )
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

@Composable
private fun BrowseByGenreCard(
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    Card(
        onClick = onOpenDiscover,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = tonal.sectionHighlight),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Browse audiobooks",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Search titles, authors, readers, and genres.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
    Spacer(modifier = Modifier.fillMaxWidth())
}

private const val FeaturedAudiobookLimit = 15
private const val HomeShelfDetailHydrationLimit = 20
