package com.librivox.mobile.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.librivox.mobile.catalog.normalizedCatalogSearchText
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
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
import com.librivox.mobile.model.isDownloaded
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.missingAudioDownloadCount
import com.librivox.mobile.model.missingDownloadAssetCount
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.model.normalizedDurationMs
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.BookSourceInfoRow
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.formatHmsFromSeconds
import com.librivox.mobile.ui.components.rememberCoverArtDisplayMode
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.navigation.BookSharedElementSource
import com.librivox.mobile.ui.navigation.BookSharedElementType
import com.librivox.mobile.ui.navigation.BookSharedTransitionKey
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.Routes
import com.librivox.mobile.ui.navigation.bookSharedBounds
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import com.librivox.mobile.playback.CoverArtDisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState

private val LibraryBottomChromePadding = 232.dp
enum class LibrarySort(val label: String) {
    RecentlyAdded("Recently added"),
    Title("Title"),
    Author("Author"),
    InProgress("In progress"),
    Duration("Duration"),
}

enum class LibraryFilter(val label: String) {
    All("All"),
    Liked("Liked"),
    Downloaded("Downloaded"),
    InProgress("In progress"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    listState: LazyListState = rememberLazyListState(),
    animateLoadedContent: Boolean = true,
    onLoadedContentRevealStarted: () -> Unit = {},
    onOpenBook: (String, BookSharedTransitionKey?) -> Unit,
    onOpenReader: (String, String?) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenAuthorSearch: (AudioBook) -> Unit,
    onSearchDiscover: (String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val initialLibraryState = remember(graph.app.libraryRepository) {
        graph.app.libraryRepository.cachedState()
    }
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = initialLibraryState)
    val feedbackRevision by graph.app.playbackFeedbackStore.revision.collectAsState()
    val networkStatus by graph.app.networkStatusMonitor.status.collectAsState()
    var downloadsOnlyModeEnabled by remember {
        mutableStateOf(graph.app.playbackSettingsStore.downloadsOnlyModeEnabled)
    }
    val coverArtDisplayMode = rememberCoverArtDisplayMode()
    var sort by remember { mutableStateOf(LibrarySort.RecentlyAdded) }
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<AudioBook?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val library = libraryState
    var loadedContentVisible by remember { mutableStateOf(!animateLoadedContent) }
    LaunchedEffect(library != null, animateLoadedContent) {
        if (library != null && !loadedContentVisible) {
            loadedContentVisible = true
            if (animateLoadedContent) {
                onLoadedContentRevealStarted()
            }
        }
    }
    val loadedContentAlpha by animateFloatAsState(
        targetValue = if (loadedContentVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>(),
        label = "library-loaded-content-alpha",
    )
    val tonal = LocalTonalSurfaces.current
    val isOffline = !networkStatus.isOnline
    val downloadsOnlyActive = isOffline || downloadsOnlyModeEnabled
    val effectiveFilter = if (downloadsOnlyActive) LibraryFilter.Downloaded else filter
    val visibleFilters = remember(downloadsOnlyActive) {
        if (downloadsOnlyActive) listOf(LibraryFilter.Downloaded) else LibraryFilter.entries.toList()
    }
    val libraryProgressBookIds = remember(library?.libraryBooks) {
        library?.libraryBooks.orEmpty()
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val libraryProgressBookIdsKey = remember(libraryProgressBookIds) {
        libraryProgressBookIds.joinToString("|")
    }
    val progressByBookId = remember(libraryProgressBookIdsKey) {
        if (library == null || libraryProgressBookIds.isEmpty()) {
            emptyMap()
        } else {
            graph.app.progressStore.savedPositionsMs(libraryProgressBookIds)
        }
    }

    fun openBook(book: AudioBook, transitionKey: BookSharedTransitionKey? = null) {
        onOpenBook(book.id, transitionKey)
        scope.launch { graph.app.stageBookDetail(book) }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerDownloadSettingsListener {
            downloadsOnlyModeEnabled = graph.app.playbackSettingsStore.downloadsOnlyModeEnabled
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    val books = remember(library, sort, effectiveFilter, query, feedbackRevision, progressByBookId) {
        val pool = library?.libraryBooks.orEmpty()
        val filtered = when (effectiveFilter) {
            LibraryFilter.All -> pool
            LibraryFilter.Liked -> pool.filter { book ->
                book.isFavorite || graph.app.playbackFeedbackStore.hasLikedFeedback(book.id)
            }
            LibraryFilter.Downloaded -> pool.filter { book ->
                book.hasDownloadedBookAssets()
            }
            LibraryFilter.InProgress -> pool.filter { book ->
                (progressByBookId[book.id] ?: 0L) > 0L
            }
        }
        val searched = if (query.isBlank()) {
            filtered
        } else {
            filtered.filter { book ->
                book.matchesLibrarySearch(query)
            }
        }
        when (sort) {
            LibrarySort.RecentlyAdded -> searched.sortedByDescending { it.addedAtMillis }
            LibrarySort.Title -> searched.sortedBy { it.title.lowercase() }
            LibrarySort.Author -> searched.sortedBy { it.author.lowercase() }
            LibrarySort.InProgress -> searched.sortedByDescending {
                progressByBookId[it.id] ?: 0L
            }
            LibrarySort.Duration -> searched.sortedByDescending { it.totalDurationSeconds }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Library") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
                actions = {
                    IconButton(onClick = onOpenDownloads) {
                        Icon(Icons.Filled.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                    ) {
                        LibrarySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sort = option
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = { query = it.take(60) },
                            onSearch = { searchExpanded = false },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            placeholder = { Text("Search your library") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                        )
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    colors = SearchBarDefaults.colors(containerColor = tonal.listItem),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    // Inline list of in-library hits as the user types.
                    books.take(8).forEach { book ->
                        val matchSummary = book.librarySearchSummary(query)
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchExpanded = false
                                    openBook(book)
                                },
                            leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                            headlineContent = { Text(book.title) },
                            supportingContent = { Text(matchSummary ?: book.author) },
                        )
                    }
                }
                FilterChipRow(
                    current = effectiveFilter,
                    filters = visibleFilters,
                    onSelected = { filter = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (downloadsOnlyActive) {
                    LibraryOfflineBanner(
                        isOffline = isOffline,
                        downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
                        onDownloadsOnlyModeChange = {
                            downloadsOnlyModeEnabled = it
                            graph.app.playbackSettingsStore.saveDownloadsOnlyModeEnabled(it)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (library == null) 1f else loadedContentAlpha
                        },
                    contentPadding = PaddingValues(bottom = LibraryBottomChromePadding),
                ) {
                    if (library == null) {
                        items(8, key = { "library-skeleton-$it" }) {
                            LibrarySkeletonRow()
                            HorizontalDivider()
                        }
                    } else if (books.isEmpty()) {
                        item {
                            EmptyState(
                                query = query,
                                hasLibraryBooks = library.libraryBooks.isNotEmpty(),
                                isOffline = downloadsOnlyActive,
                                onSearchDiscover = {
                                    searchExpanded = false
                                    onSearchDiscover(query)
                                },
                            )
                        }
                    } else {
                        items(books, key = { it.id }) { book ->
                            val positionMs = progressByBookId[book.id] ?: 0L
                            val transitionKey = remember(book.id) {
                                BookSharedTransitionKey(
                                    bookId = book.id,
                                    source = BookSharedElementSource(Routes.LIBRARY, "row"),
                                )
                            }
                            val onClick: () -> Unit = { openBook(book, transitionKey) }
                            val onLongPress: () -> Unit = { actionBook = book }
                            val onMenu: () -> Unit = { actionBook = book }
                            val onOpenReader = if (book.canReadSourceText()) {
                                { onOpenReader(book.id, null) }
                            } else {
                                null
                            }
                            val onDownload: () -> Unit = {
                                scope.launch {
                                    graph.app.downloadManager.downloadAll(book)
                                }
                            }
                            val onRemove: () -> Unit = {
                                scope.launch {
                                    val latest = graph.app.libraryRepository.snapshot().bookById(book.id) ?: book
                                    val hadDownloads = latest.hasDownloadedBookAssets()
                                    graph.app.downloadManager.deleteDownloads(latest)
                                    graph.app.libraryRepository.removeFromLibrary(latest.id)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed ${latest.title}",
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        graph.app.libraryRepository.addToLibrary(latest)
                                        if (hadDownloads) {
                                            graph.app.downloadManager.downloadAll(latest)
                                        }
                                    }
                                }
                            }
                            Column {
                                SwipeableLibraryRow(
                                    book = book,
                                    positionMs = positionMs,
                                    sharedTransitionKey = transitionKey,
                                    coverArtDisplayMode = coverArtDisplayMode,
                                    enabled = true,
                                    onClick = onClick,
                                    onLongPress = onLongPress,
                                    onMenu = onMenu,
                                    onOpenReader = onOpenReader,
                                    onDownload = onDownload,
                                    onRemove = onRemove,
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = LibraryBottomChromePadding),
            )
        }
    }

    actionBook?.let { selected ->
        val latestBook = library?.bookById(selected.id) ?: selected
        LibraryBookActionSheet(
            book = latestBook,
            coverArtDisplayMode = coverArtDisplayMode,
            onDismiss = { actionBook = null },
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
            onDownloadBook = {
                scope.launch { graph.app.downloadManager.downloadAll(latestBook) }
                actionBook = null
            },
            onDeleteDownloads = {
                scope.launch { graph.app.downloadManager.deleteDownloads(latestBook) }
                actionBook = null
            },
            onRemoveFromLibrary = {
                scope.launch { graph.app.libraryRepository.removeFromLibrary(latestBook.id) }
                actionBook = null
            },
            onDeleteDownloadsAndRemove = {
                scope.launch {
                    graph.app.downloadManager.deleteDownloads(latestBook)
                    graph.app.libraryRepository.removeFromLibrary(latestBook.id)
                }
                actionBook = null
            },
            onPlayNext = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.addAfterCurrent(items)
                }
                actionBook = null
            },
            onAddToQueue = {
                val items = latestBook.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.appendToQueue(items)
                }
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

@Composable
private fun FilterChipRow(
    current: LibraryFilter,
    filters: List<LibraryFilter>,
    onSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.name }) { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun LibraryOfflineBanner(
    isOffline: Boolean,
    downloadsOnlyModeEnabled: Boolean,
    onDownloadsOnlyModeChange: (Boolean) -> Unit,
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isOffline) "You're offline" else "Downloads only mode",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isOffline) {
                    "Showing downloaded books only while this device is offline."
                } else {
                    "Only books saved on this device are visible until you exit offline mode."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (downloadsOnlyModeEnabled) {
                FilledTonalButton(onClick = { onDownloadsOnlyModeChange(false) }) {
                    Text("Exit Downloads only")
                }
            }
        }
    }
}

@Composable
private fun LibrarySkeletonRow() {
    val tonal = LocalTonalSurfaces.current
    ListItem(
        leadingContent = {
            SkeletonBlock(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
            )
        },
        headlineContent = {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(18.dp),
            )
        },
        supportingContent = {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.48f)
                    .height(14.dp),
            )
        },
        trailingContent = {
            SkeletonBlock(
                modifier = Modifier.size(width = 56.dp, height = 24.dp),
                shape = RoundedCornerShape(12.dp),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = tonal.listItem,
        ),
        tonalElevation = 0.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableLibraryRow(
    book: AudioBook,
    positionMs: Long,
    sharedTransitionKey: BookSharedTransitionKey,
    coverArtDisplayMode: CoverArtDisplayMode,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val haptics = rememberHaptics()
    LaunchedEffect(dismissState.settledValue, enabled) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            if (enabled) {
                haptics.gestureEnd()
                onRemove()
            }
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
    ) {
        LibraryRow(
            book = book,
            positionMs = positionMs,
            sharedTransitionKey = sharedTransitionKey,
            coverArtDisplayMode = coverArtDisplayMode,
            onClick = onClick,
            onLongPress = onLongPress,
            onMenu = onMenu,
            onOpenReader = onOpenReader,
            onDownload = onDownload,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRow(
    book: AudioBook,
    positionMs: Long,
    sharedTransitionKey: BookSharedTransitionKey,
    coverArtDisplayMode: CoverArtDisplayMode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMenu: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onDownload: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val durationMs = normalizedDurationMs(book.totalDurationSeconds * 1000L)
    val supporting = buildString {
        append(book.author)
        if (positionMs > 0L && durationMs > 0L) {
            val percent = ((positionMs.toFloat() / durationMs) * 100).toInt()
            append(" • $percent% complete")
        } else if (book.totalDurationSeconds > 0L) {
            append(" • ${formatHmsFromSeconds(book.totalDurationSeconds)}")
        }
    }
    val activeDownloads = book.activeDownloadBookAssetCount()
    val canDownload = book.missingAudioDownloadCount() > 0 && activeDownloads == 0
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        headlineContent = {
            Text(
                text = book.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            BookCover(
                book = book,
                modifier = Modifier.coverArtRowSize(coverArtDisplayMode, 56.dp),
                cornerRadius = 12,
                displayMode = coverArtDisplayMode,
                onOpenReader = onOpenReader,
                showReadShortcut = false,
            )
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    activeDownloads > 0 -> {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { book.downloadProgressFraction() },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    canDownload -> {
                        IconButton(onClick = onDownload) {
                            Icon(Icons.Filled.Download, contentDescription = "Download audiobook")
                        }
                    }
                }
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Audiobook options")
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = tonal.listItem,
        ),
        tonalElevation = 0.dp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryBookActionSheet(
    book: AudioBook,
    coverArtDisplayMode: CoverArtDisplayMode,
    onDismiss: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onGoToAuthor: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadBook: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDeleteDownloadsAndRemove: () -> Unit,
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
    val canRemoveFromLibrary = true
    val hasDownloads = book.hasDownloadedBookAssets()
    val missingDownloadCount = book.missingDownloadAssetCount()
    val canDownload = missingDownloadCount > 0 &&
        book.activeDownloadBookAssetCount() == 0
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
                    Text(
                        text = book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
            onOpenReader?.let {
                LibraryBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = "Read book",
                    onClick = it,
                )
            }
            LibraryBookActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                label = "Share audiobook",
                onClick = onShare,
            )
            if (canQueue) {
                LibraryBookActionRow(
                    icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                    label = "Cast audio",
                    onClick = onCastAudio,
                )
            }
            if (book.authorSearchQuery().isNotBlank()) {
                LibraryBookActionRow(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Go to author",
                    onClick = onGoToAuthor,
                )
            }
            if (canQueue) {
                LibraryBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    label = "Play next",
                    onClick = onPlayNext,
                )
                LibraryBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    label = "Add to queue",
                    onClick = onAddToQueue,
                )
            }
            when {
                hasDownloads && book.source != BookSource.LocalAsset -> {
                    LibraryBookActionRow(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = "Delete downloads",
                        onClick = onDeleteDownloads,
                    )
                }
                canDownload -> {
                    LibraryBookActionRow(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "Download book",
                        onClick = onDownloadBook,
                    )
                }
            }
            if (hasDownloads && canRemoveFromLibrary) {
                LibraryBookActionRow(
                    icon = { Icon(Icons.Filled.RemoveCircle, contentDescription = null) },
                    label = "Delete downloads and remove",
                    onClick = onDeleteDownloadsAndRemove,
                )
            }
            if (canRemoveFromLibrary) {
                LibraryBookActionRow(
                    icon = { Icon(Icons.Filled.RemoveCircle, contentDescription = null) },
                    label = "Remove from library",
                    onClick = onRemoveFromLibrary,
                )
            }
            onDonate?.let {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                LibraryBookActionRow(
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
private fun LibraryBookActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        leadingContent = icon,
        headlineContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

@Composable
private fun EmptyState(
    query: String,
    hasLibraryBooks: Boolean,
    isOffline: Boolean,
    onSearchDiscover: () -> Unit,
) {
    val normalizedQuery = query.normalizedLibrarySearchText()
    val isSearchEmpty = normalizedQuery.isNotBlank() && !isOffline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when {
                isOffline -> "No downloaded books"
                isSearchEmpty -> "No library results"
                hasLibraryBooks -> "No books match this filter"
                else -> "Your library is empty"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = when {
                isOffline -> "Downloaded books appear here while you are offline."
                isSearchEmpty -> "No saved books match \"$query\". Library search checks titles, authors, chapters, readers, and descriptions."
                hasLibraryBooks -> "Try another filter or search your saved books."
                else -> "Add a book from Discover or import a local file to get started."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isSearchEmpty) {
            FilledTonalButton(onClick = onSearchDiscover) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(
                    text = "Search Discover",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

internal fun AudioBook.matchesLibrarySearch(query: String): Boolean {
    val normalizedQuery = query.normalizedLibrarySearchText()
    if (normalizedQuery.isBlank()) return true
    return librarySearchValues().any { value ->
        value.normalizedLibrarySearchText().contains(normalizedQuery)
    }
}

private fun AudioBook.librarySearchSummary(query: String): String? {
    val normalizedQuery = query.normalizedLibrarySearchText()
    if (normalizedQuery.isBlank()) return null
    val chapter = chapters.firstOrNull { chapter ->
        listOf(
            chapter.title,
            chapter.reader.orEmpty(),
            chapter.director.orEmpty(),
        ).any { it.normalizedLibrarySearchText().contains(normalizedQuery) }
    }
    if (chapter != null) {
        val reader = chapter.reader
            ?.takeIf { it.isNotBlank() }
            ?.let { " • $it" }
            .orEmpty()
        val prefix = if (chapter.number > 0) "Chapter ${chapter.number}: " else "Chapter: "
        return "$prefix${chapter.title}$reader"
    }
    return when {
        author.normalizedLibrarySearchText().contains(normalizedQuery) -> author
        narrators.any { it.normalizedLibrarySearchText().contains(normalizedQuery) } ->
            "Read by ${narrators.first { it.normalizedLibrarySearchText().contains(normalizedQuery) }}"
        description.normalizedLibrarySearchText().contains(normalizedQuery) -> "Description match"
        else -> null
    }
}

private fun AudioBook.librarySearchValues(): List<String> =
    buildList {
        add(title)
        add(author)
        add(description)
        addAll(narrators)
        chapters.forEach { chapter ->
            add(chapter.title)
            chapter.reader?.takeIf { it.isNotBlank() }?.let(::add)
            chapter.director?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

private fun String.normalizedLibrarySearchText(): String =
    normalizedCatalogSearchText(this)
