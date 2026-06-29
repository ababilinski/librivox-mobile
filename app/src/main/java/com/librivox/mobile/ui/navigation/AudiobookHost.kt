package com.librivox.mobile.ui.navigation

import android.widget.Toast
import android.util.Log
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.DiscoverPrefill
import com.librivox.mobile.catalog.sanitizeCatalogSearchQuery
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.authorSearchQuery
import com.librivox.mobile.model.chapterIdForSourceSlug
import com.librivox.mobile.model.externalAudiobookLinkTarget
import com.librivox.mobile.model.matchesPublicWebUrl
import com.librivox.mobile.playback.AnimationSpeed
import com.librivox.mobile.ui.detail.BookDetailScreen
import com.librivox.mobile.ui.discover.DiscoverScreen
import com.librivox.mobile.ui.downloads.DownloadsScreen
import com.librivox.mobile.ui.home.HomeScreen
import com.librivox.mobile.ui.library.LibraryScreen
import com.librivox.mobile.ui.player.PlayerSheet
import com.librivox.mobile.ui.player.PlayerSheetState
import com.librivox.mobile.ui.player.rememberPlayerSheetState
import com.librivox.mobile.ui.reader.BookReaderScreen
import com.librivox.mobile.ui.cast.CastSheetHost
import com.librivox.mobile.ui.cast.CastSheetTarget
import com.librivox.mobile.ui.settings.SettingsScreen
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val TOP_DESTINATION_ROUTES = TopDestination.entries.map { it.route }

private fun topIndex(route: String?): Int =
    TopDestination.fromRoute(route)?.let { TOP_DESTINATION_ROUTES.indexOf(it.route) } ?: -1

private fun isTopLevelSwap(fromRoute: String?, toRoute: String?): Boolean =
    topIndex(fromRoute) >= 0 && topIndex(toRoute) >= 0

private fun shouldSnapIntoLibrary(fromRoute: String?, toRoute: String?): Boolean =
    toRoute == Routes.LIBRARY && isTopLevelSwap(fromRoute, toRoute)

private fun isBookDetailRoute(route: String?): Boolean =
    route == Routes.BOOK_DETAIL || route?.startsWith("${Routes.BOOK_DETAIL_PREFIX}/") == true

private fun usesBookContainerTransform(
    activeTransitionKey: BookSharedTransitionKey?,
    fromRoute: String?,
    toRoute: String?,
): Boolean {
    val sourceRoute = activeTransitionKey?.source?.route ?: return false
    return (fromRoute == sourceRoute && isBookDetailRoute(toRoute)) ||
        (isBookDetailRoute(fromRoute) && toRoute == sourceRoute)
}

private fun routeUsesNavigationSuite(route: String?): Boolean =
    route == null || TopDestination.fromRoute(route) != null || route == Routes.BOOK_DETAIL

internal fun shouldSaveTopLevelBackStackState(
    targetRoute: String,
    currentRoute: String?,
): Boolean =
    targetRoute != Routes.HOME && !isBookDetailRoute(currentRoute)

internal fun shouldRestoreTopLevelBackStackState(
    targetRoute: String,
    reselectingCurrentTopRoute: Boolean,
): Boolean =
    targetRoute != Routes.HOME && !reselectingCurrentTopRoute

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topAwareEnter(
    topLevelEnterEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
): EnterTransition =
    when {
        shouldSnapIntoLibrary(initialState.destination.route, targetState.destination.route) ->
            EnterTransition.None
        isTopLevelSwap(initialState.destination.route, targetState.destination.route) ->
            fadeIn(animationSpec = topLevelEnterEffectsSpec)
        else -> directionForwardEnter(hierarchySpatialSpec, hierarchyEffectsSpec)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topAwareExit(
    topLevelExitEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
): ExitTransition =
    when {
        shouldSnapIntoLibrary(initialState.destination.route, targetState.destination.route) ->
            ExitTransition.None
        isTopLevelSwap(initialState.destination.route, targetState.destination.route) ->
            fadeOut(animationSpec = topLevelExitEffectsSpec)
        else -> directionForwardExit(hierarchySpatialSpec, hierarchyEffectsSpec)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topAwarePopEnter(
    topLevelEnterEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
): EnterTransition =
    when {
        shouldSnapIntoLibrary(initialState.destination.route, targetState.destination.route) ->
            EnterTransition.None
        isTopLevelSwap(initialState.destination.route, targetState.destination.route) ->
            fadeIn(animationSpec = topLevelEnterEffectsSpec)
        else -> directionPopEnter(hierarchySpatialSpec, hierarchyEffectsSpec)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.topAwarePopExit(
    topLevelExitEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
): ExitTransition =
    when {
        shouldSnapIntoLibrary(initialState.destination.route, targetState.destination.route) ->
            ExitTransition.None
        isTopLevelSwap(initialState.destination.route, targetState.destination.route) ->
            fadeOut(animationSpec = topLevelExitEffectsSpec)
        else -> directionPopExit(hierarchySpatialSpec, hierarchyEffectsSpec)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.directionForwardEnter(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): EnterTransition {
    val from = topIndex(initialState.destination.route)
    val to = topIndex(targetState.destination.route)
    val bothTabs = from >= 0 && to >= 0
    val forwardSlide = if (bothTabs) to > from else true
    return slideInHorizontally(animationSpec = spatialSpec) { width ->
        if (forwardSlide) width else -width
    } + fadeIn(animationSpec = effectsSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.directionForwardExit(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): ExitTransition {
    val from = topIndex(initialState.destination.route)
    val to = topIndex(targetState.destination.route)
    val bothTabs = from >= 0 && to >= 0
    val forwardSlide = if (bothTabs) to > from else true
    return slideOutHorizontally(animationSpec = spatialSpec) { width ->
        if (forwardSlide) -width else width
    } + fadeOut(animationSpec = effectsSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.directionPopEnter(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): EnterTransition {
    val from = topIndex(initialState.destination.route)
    val to = topIndex(targetState.destination.route)
    val bothTabs = from >= 0 && to >= 0
    val popFromHigher = if (bothTabs) to < from else true
    return slideInHorizontally(animationSpec = spatialSpec) { width ->
        if (popFromHigher) -width else width
    } + fadeIn(animationSpec = effectsSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.directionPopExit(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): ExitTransition {
    val from = topIndex(initialState.destination.route)
    val to = topIndex(targetState.destination.route)
    val bothTabs = from >= 0 && to >= 0
    val popFromHigher = if (bothTabs) to < from else true
    return slideOutHorizontally(animationSpec = spatialSpec) { width ->
        if (popFromHigher) width else -width
    } + fadeOut(animationSpec = effectsSpec)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudiobookHost(
    incomingLinkUrl: String? = null,
    incomingBookOpenRequest: BookOpenIntentRequest? = null,
    onIncomingLinkHandled: (String) -> Unit = {},
    onIncomingBookOpenRequestHandled: (BookOpenIntentRequest) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val playbackState by graph.playerStateRepository.state.collectAsState()
    val tonal = LocalTonalSurfaces.current
    var selectedTopRoute by remember { mutableStateOf(Routes.HOME) }
    var animationSpeed by remember {
        mutableStateOf(graph.app.playbackSettingsStore.animationSpeed)
    }
    var activeBookSharedTransitionKey by remember { mutableStateOf<BookSharedTransitionKey?>(null) }
    var homeLazyLoadRequest by remember { mutableStateOf(0) }
    val homeListState = remember { LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0) }
    val libraryListState = remember { LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0) }
    val discoverGridState = remember { LazyGridState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0) }
    val settingsListState = remember { LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0) }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerAnimationSpeedListener {
            animationSpeed = it
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    val showSuiteScaffold = routeUsesNavigationSuite(currentRoute)

    val playerSheetState = rememberPlayerSheetState()
    var castSheetTarget by remember { mutableStateOf<CastSheetTarget>(CastSheetTarget.None) }
    val castAwareGraph = remember(graph) {
        graph.copy(openCastPicker = { castSheetTarget = CastSheetTarget.Picker })
    }

    fun navigateTopLevel(route: String) {
        val previousTopRoute = selectedTopRoute
        if (activeBookSharedTransitionKey?.source?.route != route) {
            activeBookSharedTransitionKey = null
        }
        if (!playerSheetState.isCollapsed) {
            playerSheetState.collapse()
        }
        val reselectingCurrentTopRoute = previousTopRoute == route && currentRoute != route
        if (route == Routes.HOME && currentRoute != Routes.HOME) {
            homeLazyLoadRequest += 1
        }
        selectedTopRoute = route
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = shouldSaveTopLevelBackStackState(route, currentRoute)
            }
            launchSingleTop = true
            restoreState = shouldRestoreTopLevelBackStackState(route, reselectingCurrentTopRoute)
        }
    }

    fun openBookDetail(bookId: String, transitionKey: BookSharedTransitionKey?) {
        val matchedTransitionKey = transitionKey?.takeIf { it.bookId == bookId }
        if (matchedTransitionKey == null) {
            activeBookSharedTransitionKey = null
            navController.navigate(Routes.bookDetail(bookId))
            return
        }
        activeBookSharedTransitionKey = matchedTransitionKey
        navController.navigate(Routes.bookDetail(bookId)) {
            launchSingleTop = true
        }
    }

    fun clearBookSharedTransition() {
        activeBookSharedTransitionKey = null
    }

    LaunchedEffect(currentRoute) {
        TopDestination.fromRoute(currentRoute)?.let { selectedTopRoute = it.route }
        if (playerSheetState.value > 0.01f) {
            playerSheetState.animateTo(0f)
        }
    }

    LaunchedEffect(currentRoute, activeBookSharedTransitionKey) {
        val activeKey = activeBookSharedTransitionKey ?: return@LaunchedEffect
        val sourceRoute = activeKey.source.route
        if (
            currentRoute != null &&
            currentRoute != sourceRoute &&
            !isBookDetailRoute(currentRoute)
        ) {
            activeBookSharedTransitionKey = null
        }
    }

    LaunchedEffect(
        playbackState.bookId,
        playbackState.isPlaying,
        playbackState.artworkUri,
        playbackState.queueRevision,
    ) {
        if (!playbackState.isPlaying) return@LaunchedEffect
        val bookId = playbackState.bookId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val book = graph.app.libraryRepository.snapshot().bookById(bookId) ?: return@LaunchedEffect
        graph.app.downloadManager.downloadCover(book)
    }

    // The sheet sits OUTSIDE the navigation scaffold so it can cover the
    // nav bar at fraction = 1. The scaffold renders normally underneath;
    // when the sheet is at the mini state the user sees both.
    CompositionLocalProvider(LocalAppGraph provides castAwareGraph) {
        Box(modifier = Modifier.fillMaxSize()) {
        if (showSuiteScaffold) {
            NavigationSuiteScaffold(
                containerColor = tonal.screenBackground,
                contentColor = MaterialTheme.colorScheme.onSurface,
                navigationSuiteColors = NavigationSuiteDefaults.colors(
                    navigationBarContainerColor = tonal.bottomChrome,
                    navigationBarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationRailContainerColor = tonal.bottomChrome,
                    navigationRailContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationDrawerContainerColor = tonal.bottomChrome,
                    navigationDrawerContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                navigationSuiteItems = {
                    TopDestination.entries.forEach { dest ->
                        item(
                            selected = selectedTopRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route) {
                                    navigateTopLevel(dest.route)
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                },
            ) {
                NavHostContent(
                    navController = navController,
                    playerSheetState = playerSheetState,
                    incomingLinkUrl = incomingLinkUrl,
                    incomingBookOpenRequest = incomingBookOpenRequest,
                    onIncomingLinkHandled = onIncomingLinkHandled,
                    onIncomingBookOpenRequestHandled = onIncomingBookOpenRequestHandled,
                    activeBookSharedTransitionKey = activeBookSharedTransitionKey,
                    animationSpeed = animationSpeed,
                    homeListState = homeListState,
                    libraryListState = libraryListState,
                    discoverGridState = discoverGridState,
                    settingsListState = settingsListState,
                    onClearBookSharedTransition = ::clearBookSharedTransition,
                    onOpenBookDetail = ::openBookDetail,
                    homeLazyLoadRequest = homeLazyLoadRequest,
                )
            }
        } else {
            NavHostContent(
                navController = navController,
                playerSheetState = playerSheetState,
                incomingLinkUrl = incomingLinkUrl,
                incomingBookOpenRequest = incomingBookOpenRequest,
                onIncomingLinkHandled = onIncomingLinkHandled,
                onIncomingBookOpenRequestHandled = onIncomingBookOpenRequestHandled,
                activeBookSharedTransitionKey = activeBookSharedTransitionKey,
                animationSpeed = animationSpeed,
                homeListState = homeListState,
                libraryListState = libraryListState,
                discoverGridState = discoverGridState,
                settingsListState = settingsListState,
                onClearBookSharedTransition = ::clearBookSharedTransition,
                onOpenBookDetail = ::openBookDetail,
                homeLazyLoadRequest = homeLazyLoadRequest,
            )
        }

        // Player sheet at the root, overlays the nav bar at full expansion.
        PlayerSheet(
            sheetState = playerSheetState,
            navController = navController,
            bottomNavigationVisible = showSuiteScaffold,
            onOpenCastPicker = castAwareGraph.openCastPicker,
        )
        CastSheetHost(
            repository = graph.app.castRouteRepository,
            target = castSheetTarget,
            onTargetChange = { castSheetTarget = it },
        )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NavHostContent(
    navController: NavHostController,
    playerSheetState: PlayerSheetState,
    incomingLinkUrl: String?,
    incomingBookOpenRequest: BookOpenIntentRequest?,
    onIncomingLinkHandled: (String) -> Unit,
    onIncomingBookOpenRequestHandled: (BookOpenIntentRequest) -> Unit,
    activeBookSharedTransitionKey: BookSharedTransitionKey?,
    animationSpeed: AnimationSpeed,
    homeListState: LazyListState,
    libraryListState: LazyListState,
    discoverGridState: LazyGridState,
    settingsListState: LazyListState,
    onClearBookSharedTransition: () -> Unit,
    onOpenBookDetail: (String, BookSharedTransitionKey?) -> Unit,
    homeLazyLoadRequest: Int,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme
    val topLevelExitEffectsSpec = motionScheme.fastEffectsSpec<Float>()
    val topLevelEnterEffectsSpec = topLevelExitEffectsSpec
    val hierarchySpatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val hierarchyEffectsSpec = motionScheme.defaultEffectsSpec<Float>()
    val containerTransformExitEffectsSpec = motionScheme.fastEffectsSpec<Float>()
    val containerTransformEnterEffectsSpec = containerTransformExitEffectsSpec
    var hasRevealedLibraryContent by remember { mutableStateOf(false) }

    suspend fun resolveIncomingBookLink(url: String): Pair<String, String?>? {
        val snapshot = graph.app.libraryRepository.snapshot()
        snapshot.books.firstOrNull { it.matchesPublicWebUrl(url) }?.let { book ->
            return book.id to null
        }

        val target = externalAudiobookLinkTarget(url) ?: return null
        target.bookId?.let { targetBookId ->
            val existing = snapshot.bookById(targetBookId)
            val resolved = existing
                ?: graph.app.catalogClient.fetchByIds(targetBookId).firstOrNull()?.also { book ->
                    graph.app.libraryRepository.upsertCatalogBooks(listOf(book))
                }
            if (resolved != null) {
                return resolved.id to (target.chapterId ?: resolved.chapterIdForSourceSlug(target.chapterSlug))
            }
        }

        target.lookupUrl?.let { lookup ->
            graph.app.libraryRepository.snapshot().books
                .firstOrNull { it.matchesPublicWebUrl(lookup) }
                ?.let { return it.id to null }
        }

        target.searchQuery?.let { query ->
            val matches = graph.app.catalogClient.search(
                query = query,
                field = CatalogSearchField.Title,
                limit = 12,
                offset = 0,
                language = "",
            )
            val match = target.lookupUrl?.let { lookup ->
                matches.firstOrNull { it.matchesPublicWebUrl(lookup) }
            } ?: matches.firstOrNull()
            if (match != null) {
                graph.app.libraryRepository.upsertCatalogBooks(listOf(match))
                return match.id to null
            }
        }

        return null
    }

    suspend fun resolveOpenBookIntentBook(
        bookId: String,
        stagedBook: AudioBook? = null,
    ): AudioBook? {
        val existing = graph.app.libraryRepository.snapshot().bookById(bookId)
        return graph.app.discoverRepository.hydrateSelectedBookDetail(
            bookId = bookId,
            stagedBook = existing ?: stagedBook,
        ) ?: existing ?: stagedBook
    }

    suspend fun resolveOpenBookIntentRequest(
        request: BookOpenIntentRequest,
    ): Pair<AudioBook, String?>? {
        request.bookId?.let { bookId ->
            val book = resolveOpenBookIntentBook(bookId) ?: return null
            return book to request.chapterId
        }

        request.sourceUrl?.let { url ->
            resolveIncomingBookLink(url)?.let { (bookId, linkChapterId) ->
                val book = resolveOpenBookIntentBook(bookId) ?: return null
                return book to (request.chapterId ?: linkChapterId)
            }
        }

        val query = sanitizeCatalogSearchQuery(request.query.orEmpty()).take(120)
        if (query.isBlank()) return null
        val match = withContext(Dispatchers.IO) {
            graph.app.catalogClient.search(
                query = query,
                field = request.field,
                limit = 12,
                offset = 0,
                language = request.language,
            ).firstOrNull()
        } ?: return null
        graph.app.stageBookDetail(match)
        graph.app.libraryRepository.upsertCatalogBooks(listOf(match))
        val hydrated = resolveOpenBookIntentBook(match.id, match) ?: match
        graph.app.libraryRepository.upsertCatalogBooks(listOf(hydrated))
        return hydrated to request.chapterId
    }

    LaunchedEffect(incomingLinkUrl) {
        val url = incomingLinkUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        resolveIncomingBookLink(url)?.let { (bookId, chapterId) ->
            playerSheetState.collapse()
            onClearBookSharedTransition()
            navController.navigate(Routes.bookDetail(bookId, chapterId)) {
                launchSingleTop = true
            }
        }
        onIncomingLinkHandled(url)
    }

    LaunchedEffect(incomingBookOpenRequest?.requestId) {
        val request = incomingBookOpenRequest ?: return@LaunchedEffect
        Log.d("BookOpenIntent", "Handling open request: $request")
        Toast.makeText(context, "Opening ${request.targetLabel}...", Toast.LENGTH_SHORT).show()
        val resolved = runCatching {
            withTimeoutOrNull(request.timeoutMillis) {
                resolveOpenBookIntentRequest(request)
            }
        }
        val target = resolved.getOrNull()
        if (target == null) {
            Log.w("BookOpenIntent", "Open request failed: $request", resolved.exceptionOrNull())
            Toast.makeText(
                context,
                "Could not open ${request.targetLabel}.",
                Toast.LENGTH_LONG,
            ).show()
            onIncomingBookOpenRequestHandled(request)
            return@LaunchedEffect
        }

        val (book, chapterId) = target
        Log.d(
            "BookOpenIntent",
            "Open request resolved to ${book.id}, destination=${request.destination}, chapter=$chapterId",
        )
        playerSheetState.collapse()
        onClearBookSharedTransition()
        graph.app.stageBookDetail(book)
        val route = when (request.destination) {
            BookOpenIntentDestination.Detail -> Routes.bookDetail(book.id, chapterId)
            BookOpenIntentDestination.Reader -> Routes.bookReader(book.id, chapterId)
        }
        navController.navigate(route) {
            launchSingleTop = true
        }
        Toast.makeText(context, "Opened ${book.title}.", Toast.LENGTH_SHORT).show()
        onIncomingBookOpenRequestHandled(request)
    }

    fun openDiscoverFilter(query: String, field: CatalogSearchField, language: String = "") {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        graph.app.discoverPrefill.value = DiscoverPrefill(trimmed, field, language)
        playerSheetState.collapse()
        onClearBookSharedTransition()
        navController.clearBackStack(Routes.DISCOVER)
        navController.popBackStack(Routes.DISCOVER, inclusive = true)
        navController.navigate(Routes.DISCOVER) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
    }

    fun openAuthorSearch(book: AudioBook) {
        openDiscoverFilter(book.authorSearchQuery(), CatalogSearchField.Author, book.language.orEmpty())
    }

    fun openReader(bookId: String, chapterId: String? = null) {
        playerSheetState.collapse()
        navController.navigate(Routes.bookReader(bookId, chapterId)) {
            launchSingleTop = true
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalActiveBookSharedTransitionKey provides activeBookSharedTransitionKey,
            LocalAnimationSpeed provides animationSpeed,
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (
                        usesBookContainerTransform(
                            activeBookSharedTransitionKey,
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                    ) {
                        fadeIn(animationSpec = containerTransformEnterEffectsSpec)
                    } else {
                        topAwareEnter(
                            topLevelEnterEffectsSpec,
                            hierarchySpatialSpec,
                            hierarchyEffectsSpec,
                        )
                    }
                },
                exitTransition = {
                    if (
                        usesBookContainerTransform(
                            activeBookSharedTransitionKey,
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                    ) {
                        ExitTransition.None
                    } else {
                        topAwareExit(topLevelExitEffectsSpec, hierarchySpatialSpec, hierarchyEffectsSpec)
                    }
                },
                popEnterTransition = {
                    if (
                        usesBookContainerTransform(
                            activeBookSharedTransitionKey,
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                    ) {
                        EnterTransition.None
                    } else {
                        topAwarePopEnter(
                            topLevelEnterEffectsSpec,
                            hierarchySpatialSpec,
                            hierarchyEffectsSpec,
                        )
                    }
                },
                popExitTransition = {
                    if (
                        usesBookContainerTransform(
                            activeBookSharedTransitionKey,
                            initialState.destination.route,
                            targetState.destination.route,
                        )
                    ) {
                        fadeOut(animationSpec = containerTransformExitEffectsSpec)
                    } else {
                        topAwarePopExit(topLevelExitEffectsSpec, hierarchySpatialSpec, hierarchyEffectsSpec)
                    }
                },
            ) {
                composable(Routes.HOME) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        HomeScreen(
                            listState = homeListState,
                            lazyLoadRequest = homeLazyLoadRequest,
                            onOpenBook = onOpenBookDetail,
                            onOpenReader = ::openReader,
                            onOpenNowPlaying = { playerSheetState.expand() },
                            onOpenAuthorSearch = ::openAuthorSearch,
                            onOpenDiscover = {
                                playerSheetState.collapse()
                                navController.navigate(Routes.DISCOVER) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
                composable(Routes.LIBRARY) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        LibraryScreen(
                            listState = libraryListState,
                            animateLoadedContent = !hasRevealedLibraryContent,
                            onLoadedContentRevealStarted = { hasRevealedLibraryContent = true },
                            onOpenBook = onOpenBookDetail,
                            onOpenReader = ::openReader,
                            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                            onOpenAuthorSearch = ::openAuthorSearch,
                            onSearchDiscover = { query ->
                                openDiscoverFilter(query, CatalogSearchField.All)
                            },
                        )
                    }
                }
                composable(Routes.DISCOVER) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        DiscoverScreen(
                            gridState = discoverGridState,
                            onOpenBook = onOpenBookDetail,
                            onOpenAuthorSearch = ::openAuthorSearch,
                            onOpenCatalogSettings = {
                                playerSheetState.collapse()
                                navController.navigate(Routes.SETTINGS_CATALOG) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
                composable(Routes.DOWNLOADS) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        DownloadsScreen(
                            onOpenBook = { bookId -> onOpenBookDetail(bookId, null) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable(Routes.SETTINGS) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        SettingsScreen(
                            mainListState = settingsListState,
                            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        )
                    }
                }
                composable(Routes.SETTINGS_CATALOG) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        SettingsScreen(
                            initialOpenCatalog = true,
                            onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        )
                    }
                }
                composable(Routes.BOOK_DETAIL) { entry ->
                    val bookId = entry.arguments?.getString(Routes.BOOK_DETAIL_ARG)
                        ?: return@composable
                    val chapterId = entry.arguments?.getString(Routes.BOOK_DETAIL_CHAPTER_ARG)
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        BookDetailScreen(
                            bookId = bookId,
                            initialChapterId = chapterId,
                            onBack = { navController.popBackStack() },
                            onOpenReader = ::openReader,
                            onOpenNowPlaying = { playerSheetState.expand() },
                            onOpenDiscoverFilter = ::openDiscoverFilter,
                        )
                    }
                }
                composable(Routes.BOOK_READER) { entry ->
                    val bookId = entry.arguments?.getString(Routes.BOOK_DETAIL_ARG)
                        ?: return@composable
                    val chapterId = entry.arguments?.getString(Routes.BOOK_DETAIL_CHAPTER_ARG)
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        BookReaderScreen(
                            bookId = bookId,
                            initialChapterId = chapterId,
                            nowPlayingOverlayOpen = playerSheetState.value > 0.35f,
                            onBack = { navController.popBackStack() },
                            onOpenBookDetails = { targetBookId ->
                                onOpenBookDetail(targetBookId, null)
                            },
                        )
                    }
                }
            }
        }
    }
}
