@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.core.net.toUri
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.FallbackCover
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryStatus
import com.librivox.mobile.model.activeDownloadBookAssetCount
import com.librivox.mobile.model.activeDownloadChapterCount
import com.librivox.mobile.model.authorSearchQuery
import com.librivox.mobile.model.canReadSourceText
import com.librivox.mobile.model.chapterIndexOrZero
import com.librivox.mobile.model.coverResId
import com.librivox.mobile.model.coverUri
import com.librivox.mobile.model.donationLink
import com.librivox.mobile.model.downloadProgressFraction
import com.librivox.mobile.model.downloadableBookAssetCount
import com.librivox.mobile.model.downloadableChapterCount
import com.librivox.mobile.model.downloadedBookAssetCount
import com.librivox.mobile.model.downloadedChapterCount
import com.librivox.mobile.model.hasDownloadedBookAssets
import com.librivox.mobile.model.hasDownloadedChapters
import com.librivox.mobile.model.isDownloaded
import com.librivox.mobile.model.localCoverFile
import com.librivox.mobile.model.missingDownloadAssetCount
import com.librivox.mobile.model.numberedTitle
import com.librivox.mobile.model.remoteCoverImageUrl
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.playback.FeedbackControlScope
import com.librivox.mobile.playback.PlaybackFeedback
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.BookSourceInfoRow
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.coverArtRowSize
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.formatHmsFromSeconds
import com.librivox.mobile.ui.components.rememberCoverArtDisplayMode
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.shareAudiobookChapter
import com.librivox.mobile.ui.components.SkeletonBlock
import com.librivox.mobile.ui.components.stripHtml
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.AppGraph
import com.librivox.mobile.ui.player.bookmarks.BookmarkChapterSection
import com.librivox.mobile.ui.player.bookmarks.BookmarkPanelSections
import com.librivox.mobile.ui.player.bookmarks.buildBookmarkPanelSections
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import com.librivox.mobile.ui.theme.PlayerSheetTheme
import com.librivox.mobile.ui.theme.rememberPlayerSheetColorScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp
import kotlin.math.roundToInt

private const val MissingDetailRefreshDelayMillis = 300L
private const val DetailRepairAfterFirstPaintDelayMillis = 2_400L
private const val DetailContentDeferralFrames = 2
private val DetailHeroBackdropHeight = 480.dp

private val GeneratedDetailSeedColors = listOf(
    Color(0xFF7B3F5A),
    Color(0xFF5969A6),
    Color(0xFF3E6B63),
    Color(0xFF765A36),
    Color(0xFF5F5B7D),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    initialChapterId: String? = null,
    onBack: () -> Unit,
    onOpenReader: (String, String?) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenDiscoverFilter: (String, CatalogSearchField, String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val stagedBookDetails by graph.app.stagedBookDetails.collectAsState()
    val bookmarks by graph.app.bookmarkRepository.bookmarks.collectAsState(initial = emptyList())
    val playerState by graph.playerStateRepository.state.collectAsState()
    val feedbackRevision by graph.app.playbackFeedbackStore.revision.collectAsState()
    val coverArtDisplayMode = rememberCoverArtDisplayMode()
    var feedbackControlScope by remember {
        mutableStateOf(graph.app.playbackSettingsStore.feedbackControlScope)
    }
    var chapterActionSheet by remember { mutableStateOf<AudioBookChapter?>(null) }
    var bookActionSheetOpen by remember { mutableStateOf(false) }
    var bookmarkActionSheet by remember { mutableStateOf<Bookmark?>(null) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var detailRefreshAttempt by remember(bookId) { mutableStateOf(0) }
    var detailRefreshLoading by remember(bookId) { mutableStateOf(false) }
    var detailRefreshError by remember(bookId) { mutableStateOf<String?>(null) }
    var coverPreviewOpen by remember(bookId) { mutableStateOf(false) }
    var coverPreviewDismissRequested by remember(bookId) { mutableStateOf(false) }
    var coverPreviewSourceBounds by remember(bookId) { mutableStateOf<Rect?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var useArtworkColors by remember {
        mutableStateOf(graph.app.playbackSettingsStore.bookDetailUseArtworkColorScheme)
    }
    var useCoverBackdrop by remember {
        mutableStateOf(graph.app.playbackSettingsStore.bookDetailUseCoverBackdrop)
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerBookDetailColorSchemeListener {
            useArtworkColors = it
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerBookDetailCoverBackdropListener {
            useCoverBackdrop = it
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerPlaybackUiSettingsListener {
            feedbackControlScope = graph.app.playbackSettingsStore.feedbackControlScope
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    val persistedBook = libraryState?.bookById(bookId)
    val stagedBook = stagedBookDetails[bookId]
    val book = persistedBook ?: stagedBook
    val stagedBookCanPaintDetail = persistedBook == null && stagedBook?.canPaintDetailImmediately() == true
    val bookNeedsCatalogRefresh =
        book != null &&
            book.source.supportsCatalogRefresh() &&
            book.needsCatalogRefresh()
    val needsDetailRefresh = libraryState != null &&
        ((persistedBook == null && !stagedBookCanPaintDetail) || bookNeedsCatalogRefresh)
    val detailRefreshDelayMillis = if (stagedBookCanPaintDetail) {
        DetailRepairAfterFirstPaintDelayMillis
    } else {
        MissingDetailRefreshDelayMillis
    }
    LaunchedEffect(bookId, needsDetailRefresh, detailRefreshAttempt, detailRefreshDelayMillis) {
        if (!needsDetailRefresh) {
            detailRefreshLoading = false
            detailRefreshError = null
            return@LaunchedEffect
        }
        detailRefreshLoading = true
        detailRefreshError = null
        delay(detailRefreshDelayMillis)
        val refreshed = runCatching {
            graph.app.discoverRepository.hydrateSelectedBookDetail(bookId, book)
        }
            .getOrElse {
                detailRefreshError = it.message ?: "Could not load audiobook details."
                null
            }
        if (refreshed == null) {
            detailRefreshError = detailRefreshError ?: missingAudiobookDetailsMessage(bookId, book)
        } else {
            if (refreshed.source.supportsCatalogRefresh() && refreshed.needsCatalogRefresh()) {
                detailRefreshError = missingAudiobookDetailsMessage(
                    bookId = bookId,
                    book = refreshed,
                    noPlayableChapters = true,
                )
            }
        }
        detailRefreshLoading = false
    }
    if (book == null) {
        BackHandler { onBack() }
        BookDetailLoadingScaffold(
            title = "Audiobook",
            message = detailRefreshError ?: "Loading audiobook details...",
            isLoading = libraryState == null || detailRefreshLoading || detailRefreshError == null,
            onBack = onBack,
            onRetry = { detailRefreshAttempt++ },
        )
        return
    }
    var detailContentReady by remember(book.id) { mutableStateOf(false) }
    var detailContentVisible by remember(book.id) { mutableStateOf(false) }
    LaunchedEffect(book.id) {
        repeat(DetailContentDeferralFrames) {
            withFrameNanos { }
        }
        detailContentReady = true
    }
    LaunchedEffect(detailContentReady) {
        if (detailContentReady) {
            withFrameNanos { }
            detailContentVisible = true
        }
    }
    if (!detailContentReady) {
        BackHandler { onBack() }
        BookDetailLoadingScaffold(
            title = book.title.ifBlank { "Audiobook" },
            message = "Loading audiobook details...",
            isLoading = true,
            onBack = onBack,
            onRetry = { detailRefreshAttempt++ },
        )
        return
    }
    val detailContentAlpha by animateFloatAsState(
        targetValue = if (detailContentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "book-detail-content-alpha",
    )
    val detailCoverColorSource = remember(useArtworkColors, book, context) {
        if (useArtworkColors) book.coverUri(context).toString() else null
    }
    val detailColorScheme = rememberPlayerSheetColorScheme(detailCoverColorSource)

    PlayerSheetTheme(scheme = detailColorScheme) {
    val tonal = LocalTonalSurfaces.current
    BackHandler(enabled = coverPreviewOpen) { coverPreviewDismissRequested = true }
    BackHandler(enabled = !coverPreviewOpen) { onBack() }
    val bookmarkSections = remember(book, bookmarks, playerState.bookId, playerState.chapterId) {
        buildBookmarkPanelSections(
            book = book,
            bookmarks = bookmarks,
            activeBookId = playerState.bookId,
            activeChapterId = playerState.chapterId,
        )
    }
    val bookBookmarksCount = bookmarkSections.currentChapter?.bookmarks.orEmpty().size +
        bookmarkSections.remainingChapters.sumOf { it.bookmarks.size }
    val inLibrary = book.libraryStatus == LibraryStatus.InLibrary
    val missingDownloadCount = book.missingDownloadAssetCount()
    val downloadedCount = book.downloadedBookAssetCount()
    val activeDownloadCount = book.activeDownloadBookAssetCount()
    val plainDescription = remember(book.description) { stripHtml(book.description) }
    val literaryTags = remember(book) { book.detailCatalogTags() }
    val showTranslatedPill = remember(book) { book.showTranslatedMetadataPill() }
    val showLiteraryMetadata = literaryTags.isNotEmpty() || showTranslatedPill
    val sourceLink = book.sourceLink()
    val donationLink = book.donationLink()
    val fallbackDetailSeed = remember(book.id, book.fallbackCover) { book.detailSeedColor() }
    val detailSeed = if (useArtworkColors) MaterialTheme.colorScheme.primary else fallbackDetailSeed
    val detailBackground = lerp(tonal.screenBackground, detailSeed, 0.18f)
    val detailScrolledBackground = lerp(detailBackground, detailSeed, 0.08f)
    val detailBodyDarkenAmount = if (tonal.screenBackground.luminance() < 0.5f) 0.34f else 0.16f
    val detailBodyBackground = lerp(
        detailBackground,
        MaterialTheme.colorScheme.scrim,
        detailBodyDarkenAmount,
    )
    val screenBackground = if (useCoverBackdrop) detailBodyBackground else detailBackground
    val topBarContainerColor = if (useCoverBackdrop) Color.Transparent else detailBackground
    val topBarScrolledContainerColor = if (useCoverBackdrop) Color.Transparent else detailScrolledBackground
    val listState = rememberLazyListState()
    val chapterStartIndex = 2 +
        (if (plainDescription.isNotBlank()) 1 else 0) +
        (if (showLiteraryMetadata) 1 else 0) +
        (if (sourceLink != null || donationLink != null) 1 else 0)
    val latestBookForInitialScroll = rememberUpdatedState(book)
    val latestChapterStartIndex = rememberUpdatedState(chapterStartIndex)
    var didJumpToChapter by remember(book.id, initialChapterId) { mutableStateOf(false) }
    val canShowChapters = book.chapters.isNotEmpty() || book.source.supportsCatalogRefresh()

    LaunchedEffect(book.id, initialChapterId) {
        val targetChapterId = initialChapterId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (didJumpToChapter) return@LaunchedEffect
        val chapters = snapshotFlow { latestBookForInitialScroll.value.chapters }
            .first { it.isNotEmpty() }
        val chapterIndex = chapters.indexOfFirst { it.id == targetChapterId }
        if (chapterIndex >= 0) {
            listState.animateScrollToItem(latestChapterStartIndex.value + chapterIndex)
            didJumpToChapter = true
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        scope.launch {
            graph.app.bookmarkRepository.remove(bookmark.id)
            val result = snackbarHostState.showSnackbar(
                message = "Bookmark removed",
                actionLabel = "Undo",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                graph.app.bookmarkRepository.restore(bookmark)
            }
        }
    }

    fun showChapters() {
        scope.launch {
            listState.animateScrollToItem(chapterStartIndex)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = detailContentAlpha },
        ) {
            if (useCoverBackdrop) {
                DetailHeroArtworkBackdrop(
                    book = book,
                    backgroundColor = detailBackground,
                    fadeToColor = detailBodyBackground,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(DetailHeroBackdropHeight),
                )
            }
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = if (useCoverBackdrop) Color.Transparent else detailBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = topBarContainerColor,
                            scrolledContainerColor = topBarScrolledContainerColor,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { shareAudiobook(context, book) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share audiobook")
                            }
                            if (canShowChapters) {
                                IconButton(onClick = { showChapters() }) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show chapters")
                                }
                            }
                            IconButton(onClick = { bookActionSheetOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Audiobook options")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { padding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 232.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                item {
                    DetailHeroSection {
                        Header(
                            book = book,
                            coverArtDisplayMode = coverArtDisplayMode,
                            onShowActions = { bookActionSheetOpen = true },
                            onCoverBoundsChanged = { coverPreviewSourceBounds = it },
                            onOpenCoverPreview = {
                                coverPreviewDismissRequested = false
                                coverPreviewOpen = true
                            },
                            onOpenReader = if (book.canReadSourceText()) {
                                { onOpenReader(book.id, null) }
                            } else {
                                null
                            },
                            onShowChapters = if (canShowChapters) {
                                { showChapters() }
                            } else {
                                null
                            },
                            onAuthorClick = {
                                onOpenDiscoverFilter(book.authorSearchQuery(), CatalogSearchField.Author, book.language.orEmpty())
                            },
                            onReaderClick = { reader ->
                                onOpenDiscoverFilter(reader, CatalogSearchField.Reader, book.language.orEmpty())
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val mediaItems = book.toMediaItems(context)
                                    if (mediaItems.isEmpty()) return@FilledTonalButton
                                    val chapterId = graph.app.progressStore.lastChapterId(book.id)
                                    val startIndex = book.chapterIndexOrZero(chapterId)
                                    val startPositionMs = chapterId?.let {
                                        graph.app.progressStore.savedPositionMs(book.id, it)
                                    } ?: 0L
                                    val targetChapterId = book.chapters.getOrNull(startIndex)?.id
                                    if (playerState.bookId == book.id && playerState.chapterId == targetChapterId) {
                                        if (!playerState.isPlaying) {
                                            graph.playerStateRepository.playPause()
                                        }
                                        return@FilledTonalButton
                                    }
                                    graph.playerStateRepository.playMediaItems(mediaItems, startIndex, startPositionMs)
                                    scope.launch { graph.app.downloadManager.downloadCover(book) }
                                },
                                modifier = Modifier.widthIn(min = 164.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text("  Play")
                            }
                            BookDownloadAction(
                                missingDownloadCount = missingDownloadCount,
                                downloadedCount = downloadedCount,
                                activeDownloadCount = activeDownloadCount,
                                totalDownloadableCount = book.downloadableBookAssetCount(),
                                progress = book.downloadProgressFraction(),
                                onDownloadBook = {
                                    scope.launch { graph.app.downloadManager.downloadAll(book) }
                                },
                                onDeleteDownloads = {
                                    scope.launch { graph.app.downloadManager.deleteDownloads(book) }
                                },
                            )
                        }
                    }
                }

                if (plainDescription.isNotBlank()) {
                    item {
                        CollapsibleDescription(
                            text = plainDescription,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                }
                if (showLiteraryMetadata) {
                    item {
                        LiteraryMetadataChips(
                            tags = literaryTags,
                            showTranslatedPill = showTranslatedPill,
                            onTagClick = { tag ->
                                onOpenDiscoverFilter(tag.tag.name, tag.field, book.language.orEmpty())
                            },
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }
                }
                if (sourceLink != null || donationLink != null) {
                    item {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                        ) {
                            ExternalLinkRow(
                                sourceLink = sourceLink,
                                donationLink = donationLink,
                                onOpenUrl = { url -> openExternalUrl(context, url) },
                            )
                        }
                    }
                }

            item {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (book.chapters.isEmpty() && book.source.supportsCatalogRefresh()) {
                item {
                    DetailRefreshStatus(
                        message = detailRefreshError ?: "Getting chapters and details...",
                        isLoading = detailRefreshLoading || detailRefreshError == null,
                        onRetry = { detailRefreshAttempt++ },
                    )
                }
            }
            itemsIndexed(book.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                val isCurrentChapter = playerState.bookId == book.id &&
                    playerState.chapterId == chapter.id
                val isThisPlaying = isCurrentChapter && playerState.isPlaying
                val canRemoveDownload = chapter.localFileName != null
                val canDownloadChapter = chapter.canDownloadManually()
                val feedback = remember(book.id, chapter.id, feedbackControlScope, feedbackRevision) {
                    graph.app.playbackFeedbackStore.feedbackFor(
                        book.id,
                        chapter.id,
                        feedbackControlScope,
                    )
                }
                ChapterListRowContainer(
                    index = index,
                    lastIndex = book.chapters.lastIndex,
                ) {
                    ChapterRow(
                        book = book,
                        chapter = chapter,
                        isPlaying = isThisPlaying,
                        isCurrent = isCurrentChapter,
                        feedback = feedback,
                        onOpenReader = if (book.canReadSourceText()) {
                            { onOpenReader(book.id, chapter.id) }
                        } else {
                            null
                        },
                        onPlay = {
                            if (isCurrentChapter) {
                                // Tap on the playing chapter → toggle play/pause.
                                graph.playerStateRepository.playPause()
                                return@ChapterRow
                            }
                            val mediaItems = book.toMediaItems(context)
                            if (mediaItems.isEmpty()) return@ChapterRow
                            val chapterIndex = book.chapters.indexOfFirst { it.id == chapter.id }
                                .coerceAtLeast(0)
                            val savedMs = graph.app.progressStore.savedPositionMs(book.id, chapter.id)
                            graph.playerStateRepository.playMediaItems(mediaItems, chapterIndex, savedMs)
                            scope.launch { graph.app.downloadManager.downloadCover(book) }
                            // Intentionally do NOT navigate to NowPlaying — MiniPlayer appears
                            // and the user can tap to expand if they want.
                        },
                        onDownload = {
                            scope.launch { graph.app.downloadManager.downloadChapter(book, chapter) }
                        },
                        canDownload = canDownloadChapter,
                        canRemoveDownload = canRemoveDownload,
                        onRemoveDownload = {
                            scope.launch { graph.app.downloadManager.deleteDownload(book, chapter) }
                        },
                        onPlayNext = {
                            val items = book.toMediaItems(context).filter {
                                val mediaId = it.mediaId
                                mediaId.endsWith("::${chapter.id}")
                            }
                            if (items.isNotEmpty()) {
                                graph.playerStateRepository.addAfterCurrent(items)
                            }
                        },
                        onAddToQueue = {
                            val items = book.toMediaItems(context).filter {
                                val mediaId = it.mediaId
                                mediaId.endsWith("::${chapter.id}")
                            }
                            if (items.isNotEmpty()) {
                                graph.playerStateRepository.appendToQueue(items)
                            }
                        },
                        onLike = {
                            graph.app.playbackFeedbackStore.toggleFeedback(
                                book.id,
                                chapter.id,
                                PlaybackFeedback.Liked,
                                feedbackControlScope,
                            )
                        },
                        onDislike = {
                            graph.app.playbackFeedbackStore.toggleFeedback(
                                book.id,
                                chapter.id,
                                PlaybackFeedback.Disliked,
                                feedbackControlScope,
                            )
                        },
                        showFeedbackActions = feedbackControlScope == FeedbackControlScope.Chapter,
                        onCastAudio = {
                            castAudiobookAudio(context, graph, book, chapter.id)
                        },
                        onShare = {
                            shareAudiobookChapter(context, book, chapter)
                        },
                        onShowActions = { chapterActionSheet = chapter },
                        isDeepLinked = chapter.id == initialChapterId,
                    )
                    if (index < book.chapters.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 88.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }

            if (bookBookmarksCount > 0) {
                item {
                    Text(
                        text = "Bookmarks ($bookBookmarksCount)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                bookmarkSections.currentChapter?.let { section ->
                    bookmarkSectionItems(
                        section = section,
                        currentChapterLabel = true,
                        onBookmarkClick = { bookmark ->
                            playFromBookmark(book, bookmark, context, graph)
                        },
                        onLongPress = { bookmark -> bookmarkActionSheet = bookmark },
                        onDelete = { bookmark ->
                            removeBookmark(bookmark)
                        },
                        containerColor = tonal.listItem,
                    )
                }
                if (bookmarkSections.showDivider) {
                    item { HorizontalDivider() }
                }
                bookmarkSections.remainingChapters.forEach { section ->
                    bookmarkSectionItems(
                        section = section,
                        currentChapterLabel = false,
                        onBookmarkClick = { bookmark ->
                            playFromBookmark(book, bookmark, context, graph)
                        },
                        onLongPress = { bookmark -> bookmarkActionSheet = bookmark },
                        onDelete = { bookmark ->
                            removeBookmark(bookmark)
                        },
                        containerColor = tonal.listItem,
                    )
                }
            }
        }
    }
    }

    if (coverPreviewOpen) {
        CoverPreviewDialog(
            book = book,
            sourceBounds = coverPreviewSourceBounds,
            dismissRequested = coverPreviewDismissRequested,
            onDismissRequest = { coverPreviewDismissRequested = true },
            onDismissed = {
                coverPreviewOpen = false
                coverPreviewDismissRequested = false
            },
        )
    }

    if (bookActionSheetOpen) {
        DetailBookActionSheet(
            book = book,
            coverArtDisplayMode = coverArtDisplayMode,
            onDismiss = { bookActionSheetOpen = false },
            onGoToAuthor = {
                onOpenDiscoverFilter(book.authorSearchQuery(), CatalogSearchField.Author, book.language.orEmpty())
                bookActionSheetOpen = false
            },
            onToggleLike = {
                scope.launch { graph.app.libraryRepository.toggleFavorite(book.id) }
            },
            onOpenReader = if (book.canReadSourceText()) {
                {
                    onOpenReader(book.id, null)
                    bookActionSheetOpen = false
                }
            } else {
                null
            },
            onShowChapters = if (canShowChapters) {
                {
                    bookActionSheetOpen = false
                    showChapters()
                }
            } else {
                null
            },
            onAddToLibrary = {
                scope.launch {
                    graph.app.libraryRepository.addToLibrary(book)
                    graph.app.downloadManager.downloadCover(book)
                }
                bookActionSheetOpen = false
            },
            onRemoveFromLibrary = {
                scope.launch { graph.app.libraryRepository.removeFromLibrary(book.id) }
                bookActionSheetOpen = false
            },
            onDownloadBook = {
                scope.launch { graph.app.downloadManager.downloadAll(book) }
                bookActionSheetOpen = false
            },
            onDeleteDownloads = {
                scope.launch { graph.app.downloadManager.deleteDownloads(book) }
                bookActionSheetOpen = false
            },
            onPlayNext = {
                val items = book.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.addAfterCurrent(items)
                }
                bookActionSheetOpen = false
            },
            onAddToQueue = {
                val items = book.toMediaItems(context)
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.appendToQueue(items)
                }
                bookActionSheetOpen = false
            },
            onCastAudio = {
                castAudiobookAudio(context, graph, book)
                bookActionSheetOpen = false
            },
            onShare = {
                shareAudiobook(context, book)
                bookActionSheetOpen = false
            },
            onDonate = donationLink?.let { link ->
                {
                    openExternalUrl(context, link.url)
                    bookActionSheetOpen = false
                }
            },
        )
    }

    bookmarkActionSheet?.let { bookmark ->
        BookmarkActionsSheet(
            bookmark = bookmark,
            chapterTitle = bookmarkSections.chapterTitleFor(bookmark),
            onDismiss = { bookmarkActionSheet = null },
            onJumpTo = {
                bookmarkActionSheet = null
                playFromBookmark(book, bookmark, context, graph)
            },
            onEdit = {
                bookmarkActionSheet = null
                editingBookmark = bookmark
            },
            onDelete = {
                bookmarkActionSheet = null
                removeBookmark(bookmark)
            },
        )
    }

    editingBookmark?.let { bookmark ->
        BookmarkNoteEditorSheet(
            bookmark = bookmark,
            onDismiss = { editingBookmark = null },
            onSave = { note ->
                scope.launch { graph.app.bookmarkRepository.updateNote(bookmark.id, note) }
                editingBookmark = null
            },
        )
    }

    chapterActionSheet?.let { chapter ->
        val currentChapter = book.chapters.firstOrNull { it.id == chapter.id } ?: chapter
        val canRemoveDownload = currentChapter.localFileName != null
        val canDownloadChapter = currentChapter.canDownloadManually()
        val feedback = graph.app.playbackFeedbackStore.feedbackFor(
            book.id,
            chapter.id,
            feedbackControlScope,
        )
        ChapterActionSheet(
            book = book,
            chapter = currentChapter,
            coverArtDisplayMode = coverArtDisplayMode,
            feedback = feedback,
            canDownload = canDownloadChapter,
            canRemoveDownload = canRemoveDownload,
            showFeedbackActions = feedbackControlScope == FeedbackControlScope.Chapter,
            onOpenReader = if (book.canReadSourceText()) {
                {
                    onOpenReader(book.id, currentChapter.id)
                    chapterActionSheet = null
                }
            } else {
                null
            },
            onDismiss = { chapterActionSheet = null },
            onPlayNext = {
                val items = book.toMediaItems(context).filter {
                    it.mediaId.endsWith("::${chapter.id}")
                }
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.addAfterCurrent(items)
                }
                chapterActionSheet = null
            },
            onAddToQueue = {
                val items = book.toMediaItems(context).filter {
                    it.mediaId.endsWith("::${chapter.id}")
                }
                if (items.isNotEmpty()) {
                    graph.playerStateRepository.appendToQueue(items)
                }
                chapterActionSheet = null
            },
            onDownload = {
                scope.launch { graph.app.downloadManager.downloadChapter(book, currentChapter) }
                chapterActionSheet = null
            },
            onRemoveDownload = {
                scope.launch { graph.app.downloadManager.deleteDownload(book, currentChapter) }
                chapterActionSheet = null
            },
            onCastAudio = {
                castAudiobookAudio(context, graph, book, currentChapter.id)
                chapterActionSheet = null
            },
            onLike = {
                graph.app.playbackFeedbackStore.toggleFeedback(
                    book.id,
                    chapter.id,
                    PlaybackFeedback.Liked,
                    feedbackControlScope,
                )
                chapterActionSheet = null
            },
            onDislike = {
                graph.app.playbackFeedbackStore.toggleFeedback(
                    book.id,
                    chapter.id,
                    PlaybackFeedback.Disliked,
                    feedbackControlScope,
                )
                chapterActionSheet = null
            },
            onShare = {
                shareAudiobookChapter(context, book, currentChapter)
                chapterActionSheet = null
            },
        )
    }
    }
}
}

private fun LazyListScope.bookmarkSectionItems(
    section: BookmarkChapterSection,
    currentChapterLabel: Boolean,
    onBookmarkClick: (Bookmark) -> Unit,
    onLongPress: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    containerColor: Color,
) {
    item(key = "bookmark-section-${section.chapterId}-${currentChapterLabel}") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = if (currentChapterLabel) "Current chapter" else "Chapter",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = section.chapterTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    items(section.bookmarks, key = { it.id }) { bookmark ->
        val rowShape = RoundedCornerShape(18.dp)
        val dismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(dismissState.settledValue) {
            if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete(bookmark)
                dismissState.reset()
            }
        }
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(rowShape),
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(rowShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        ) {
            Surface(
                color = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = rowShape,
            ) {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onBookmarkClick(bookmark) },
                            onLongClick = { onLongPress(bookmark) },
                        ),
                    leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    headlineContent = {
                        Text(formatHmsFromMs(bookmark.positionMs))
                    },
                    supportingContent = {
                        Text(
                            text = bookmark.note.ifBlank { section.chapterTitle },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        HorizontalDivider()
    }
}

private fun playFromBookmark(
    book: AudioBook,
    bookmark: Bookmark,
    context: android.content.Context,
    graph: AppGraph,
) {
    val mediaItems = book.toMediaItems(context)
    val index = book.chapters.indexOfFirst { it.id == bookmark.chapterId }
    if (mediaItems.isNotEmpty() && index >= 0) {
        graph.playerStateRepository.playMediaItems(
            mediaItems,
            index,
            bookmark.positionMs,
        )
    }
}

@Composable
private fun BookDownloadAction(
    missingDownloadCount: Int,
    downloadedCount: Int,
    activeDownloadCount: Int,
    totalDownloadableCount: Int,
    progress: Float,
    onDownloadBook: () -> Unit,
    onDeleteDownloads: () -> Unit,
) {
    if (totalDownloadableCount == 0 && missingDownloadCount == 0 && downloadedCount == 0) {
        return
    }

    val isDownloading = activeDownloadCount > 0
    val isDownloaded = missingDownloadCount == 0 && totalDownloadableCount > 0
    val canDownload = missingDownloadCount > 0 && !isDownloading
    val label = when {
        isDownloading -> "Downloading audiobook"
        isDownloaded -> "Downloaded audiobook"
        downloadedCount > 0 -> "Download remaining audiobook files"
        else -> "Download audiobook"
    }
    var confirmDeleteOpen by remember { mutableStateOf(false) }

    if (confirmDeleteOpen) {
        AlertDialog(
            onDismissRequest = { confirmDeleteOpen = false },
            title = { Text("Delete download?") },
            text = {
                Text("Remove the downloaded audio for this book from this device?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteOpen = false
                        onDeleteDownloads()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloading) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            )
        }
        FilledTonalIconButton(
            onClick = {
                when {
                    isDownloaded -> confirmDeleteOpen = true
                    canDownload -> onDownloadBook()
                }
            },
            modifier = Modifier.size(44.dp),
        ) {
            when {
                isDownloading -> AnimatedDownloadIcon(contentDescription = label)
                isDownloaded -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = label,
                )
                else -> Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = label,
                )
            }
        }
    }
}

@Composable
private fun AnimatedDownloadIcon(contentDescription: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "BookDownloadIconTransition")
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "BookDownloadIconOffset",
    )
    Icon(
        imageVector = Icons.Filled.Download,
        contentDescription = contentDescription,
        modifier = Modifier.graphicsLayer {
            translationY = yOffset
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailLoadingScaffold(
    title: String,
    message: String,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    Scaffold(
        containerColor = tonal.screenBackground,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading) {
            BookDetailInitialSkeleton(
                message = message,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
    }

@Composable
private fun BookDetailInitialSkeleton(
    message: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 232.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(128.dp),
                    shape = RoundedCornerShape(16.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.9f).height(28.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.64f).height(18.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.48f).height(18.dp))
                }
            }
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth().height(16.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.92f).height(16.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.72f).height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        items(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(18.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.78f).height(14.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.36f).height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailHeroSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun DetailHeroArtworkBackdrop(
    book: AudioBook,
    backgroundColor: Color,
    fadeToColor: Color = backgroundColor,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coverModel = remember(book, context) {
        book.localCoverFile(context.filesDir) ?: book.remoteCoverImageUrl(preferFullQuality = true)
    }
    val fallbackCoverRes = if (coverModel == null) book.coverResId() else 0
    Box(
        modifier = modifier
            .background(fadeToColor)
            .clipToBounds(),
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = 0.58f
                    }
                    .blur(36.dp),
            )
        } else if (fallbackCoverRes != 0) {
            Image(
                painter = painterResource(fallbackCoverRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                        alpha = 0.48f
                    }
                    .blur(36.dp),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to backgroundColor.copy(alpha = 0.18f),
                            0.48f to backgroundColor.copy(alpha = 0.42f),
                            0.78f to fadeToColor.copy(alpha = 0.82f),
                            1f to fadeToColor,
                        ),
                    ),
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Header(
    book: AudioBook,
    coverArtDisplayMode: CoverArtDisplayMode,
    onShowActions: () -> Unit,
    onCoverBoundsChanged: (Rect) -> Unit,
    onOpenCoverPreview: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onShowChapters: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    onReaderClick: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .coverArtRowSize(coverArtDisplayMode, 192.dp)
                    .onGloballyPositioned { coordinates ->
                        onCoverBoundsChanged(coordinates.boundsInWindow())
                    },
                cornerRadius = 16,
                preferFullImage = true,
                displayMode = coverArtDisplayMode,
                onOpenReader = onOpenReader,
                onCoverClick = onOpenCoverPreview,
                onCoverLongClick = onShowActions,
            )
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val linkedTextColor = detailLinkedTextColor()
                    val supportingTextColor = detailSupportingTextColor()
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.titleSmall,
                        color = linkedTextColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAuthorClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    book.source.bookDetailSourceLabel()?.let { sourceLabel ->
                        SourcePill(label = sourceLabel)
                    }
                    onShowChapters?.let { showChapters ->
                        SuggestionChip(
                            onClick = showChapters,
                            colors = detailSuggestionChipColors(),
                            border = detailChipBorder(),
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = if (book.chapters.isNotEmpty()) {
                                        "Chapters ${book.chapters.size}"
                                    } else {
                                        "Chapters"
                                    },
                                )
                            },
                        )
                    }
                    val readerList = book.chapters.mapNotNull { it.reader }
                        .distinct()
                        .take(3)
                    val directorList = book.chapters.mapNotNull { it.director }
                        .distinct()
                        .take(2)
                    val translatedByLabel = book.translatedByLabel()
                    if (readerList.isNotEmpty() || directorList.isNotEmpty() || translatedByLabel != null) {
                        ReaderCredits(
                            readers = readerList,
                            directors = directorList,
                            translatedByLabel = translatedByLabel,
                            onReaderClick = onReaderClick,
                        )
                    }
                    val secondary = buildString {
                        if (!book.language.isNullOrBlank()) append(book.language)
                        if (book.totalDurationSeconds > 0L) {
                            if (isNotEmpty()) append(" • ")
                            append(formatHmsFromSeconds(book.totalDurationSeconds))
                        }
                    }
                    if (secondary.isNotBlank()) {
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = supportingTextColor,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CoverPreviewDialog(
    book: AudioBook,
    sourceBounds: Rect?,
    dismissRequested: Boolean,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val progress = remember { Animatable(0f) }
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    val popupInteractionSource = remember { MutableInteractionSource() }
    val coverModel = book.localCoverFile(context.filesDir)
        ?: book.remoteCoverImageUrl(preferFullQuality = true)
    val fallbackCoverRes = book.coverResId()

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = motionSpec)
    }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            progress.animateTo(0f, animationSpec = motionSpec)
            latestOnDismissed()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = progress.value.coerceIn(0f, 1f)))
                .clickable(
                    interactionSource = popupInteractionSource,
                    indication = null,
                    onClick = onDismissRequest,
                ),
        ) {
            val progressValue = progress.value.coerceIn(0f, 1f)
            val maxWidthPx = constraints.maxWidth.toFloat()
            val maxHeightPx = constraints.maxHeight.toFloat()
            val horizontalMarginPx = with(density) { 16.dp.toPx() }
            val verticalMarginPx = with(density) { 64.dp.toPx() }
            val topInsetPx = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            val targetRect = Rect(
                left = horizontalMarginPx,
                top = topInsetPx + verticalMarginPx,
                right = (maxWidthPx - horizontalMarginPx).coerceAtLeast(horizontalMarginPx),
                bottom = (maxHeightPx - bottomInsetPx - verticalMarginPx)
                    .coerceAtLeast(topInsetPx + verticalMarginPx),
            )
            val startRect = sourceBounds
                ?.takeIf { it.width > 1f && it.height > 1f }
                ?: centeredFallbackRect(maxWidthPx, maxHeightPx)
            val currentRect = interpolateRect(startRect, targetRect, progressValue)
            val currentWidth = with(density) { currentRect.width.toDp() }
            val currentHeight = with(density) { currentRect.height.toDp() }
            val currentShape = RoundedCornerShape((16f * (1f - progressValue)).dp)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            currentRect.left.roundToInt(),
                            currentRect.top.roundToInt(),
                        )
                    }
                    .size(currentWidth, currentHeight)
                    .clip(currentShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = "${book.title} cover art",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(fallbackCoverRes),
                        contentDescription = "${book.title} cover art",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .graphicsLayer { alpha = progressValue },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Filled.Close, contentDescription = "Close cover preview")
                }
            }
        }
    }
}

private fun centeredFallbackRect(maxWidthPx: Float, maxHeightPx: Float): Rect {
    val size = minOf(maxWidthPx, maxHeightPx) * 0.36f
    val left = (maxWidthPx - size) / 2f
    val top = (maxHeightPx - size) / 2f
    return Rect(left, top, left + size, top + size)
}

private fun interpolateRect(start: Rect, end: Rect, progress: Float): Rect {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return Rect(
        left = interpolateFloat(start.left, end.left, clampedProgress),
        top = interpolateFloat(start.top, end.top, clampedProgress),
        right = interpolateFloat(start.right, end.right, clampedProgress),
        bottom = interpolateFloat(start.bottom, end.bottom, clampedProgress),
    )
}

private fun interpolateFloat(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

@Composable
private fun DetailRefreshStatus(
    message: String,
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            },
            headlineContent = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                if (!isLoading) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            },
        )
        if (isLoading) {
            DetailLoadingPlaceholder()
        }
    }
}

@Composable
private fun DetailLoadingPlaceholder() {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailSkeletonLine(widthFraction = 1f)
        DetailSkeletonLine(widthFraction = 0.86f)
        DetailSkeletonLine(widthFraction = 0.68f)
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DetailSkeletonLine(widthFraction = 0.82f)
                    DetailSkeletonLine(widthFraction = 0.38f, heightDp = 10)
                }
            }
        }
    }
}

@Composable
private fun DetailSkeletonLine(widthFraction: Float, heightDp: Int = 12) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.56f)),
    )
}

@Composable
private fun ReaderCredits(
    readers: List<String>,
    directors: List<String>,
    translatedByLabel: String?,
    onReaderClick: (String) -> Unit,
) {
    val linkedTextColor = detailLinkedTextColor()
    val supportingTextColor = detailSupportingTextColor()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        readers.forEachIndexed { index, reader ->
            Text(
                text = if (index == 0) "Read by $reader" else reader,
                style = MaterialTheme.typography.bodySmall,
                color = linkedTextColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReaderClick(reader) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (directors.isNotEmpty()) {
            Text(
                text = "Directed by ${directors.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        translatedByLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SourcePill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = detailChipContainerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = detailChipBorder(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private data class DetailCatalogTag(
    val label: String,
    val tag: CatalogTag,
    val field: CatalogSearchField,
)

private fun AudioBook.detailCatalogTags(): List<DetailCatalogTag> =
    literaryEpochs.map { DetailCatalogTag("Epoch", it, CatalogSearchField.Epoch) } +
        literaryKinds.map { DetailCatalogTag("Kind", it, CatalogSearchField.Kind) } +
        literaryGenres.map { DetailCatalogTag("Genre", it, CatalogSearchField.Genre) }

internal fun AudioBook.showTranslatedMetadataPill(): Boolean =
    source == BookSource.WolneLektury &&
        (translators.any { it.isNotBlank() } || originalLanguage.isKnownNonPolishLanguage())

internal fun AudioBook.translatedByLabel(): String? {
    val names = translators
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return names
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = ", ", prefix = "Translated by ")
}

private fun String?.isKnownNonPolishLanguage(): Boolean =
    this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { !it.equals("Polish", ignoreCase = true) && !it.equals("pol", ignoreCase = true) }
        ?: false

@Composable
private fun LiteraryMetadataChips(
    tags: List<DetailCatalogTag>,
    showTranslatedPill: Boolean,
    onTagClick: (DetailCatalogTag) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Wolne Lektury metadata",
            style = MaterialTheme.typography.labelLarge,
            color = detailSupportingTextColor(),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTranslatedPill) {
                item(key = "translated") {
                    MetadataPill(label = "Translated")
                }
            }
            items(tags, key = { "${it.label}:${it.tag.slug}:${it.tag.name}" }) { tag ->
                SuggestionChip(
                    onClick = { onTagClick(tag) },
                    colors = detailSuggestionChipColors(),
                    border = detailChipBorder(),
                    label = { Text("${tag.label}: ${tag.tag.name}") },
                )
            }
        }
    }
}

@Composable
private fun MetadataPill(label: String) {
    Surface(
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = detailChipContainerColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = detailChipBorder(),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun detailLinkedTextColor(): Color =
    lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, 0.42f)

@Composable
private fun detailSupportingTextColor(): Color =
    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)

@Composable
private fun detailChipContainerColor(): Color =
    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.84f)

@Composable
private fun detailChipBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.64f))

@Composable
private fun detailSuggestionChipColors() =
    SuggestionChipDefaults.suggestionChipColors(
        containerColor = detailChipContainerColor(),
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

private fun BookSource.bookDetailSourceLabel(): String? =
    when (this) {
        BookSource.LibriVox -> "Source: LibriVox"
        BookSource.Lit2Go -> "Source: Lit2Go"
        BookSource.WolneLektury -> "Source: Wolne Lektury"
        BookSource.Gutendex -> "Source: Project Gutenberg"
        BookSource.LocalAsset,
        BookSource.CustomLocal -> null
    }

private fun BookSource.supportsCatalogRefresh(): Boolean =
    this == BookSource.LibriVox ||
        this == BookSource.Lit2Go ||
        this == BookSource.WolneLektury ||
        this == BookSource.Gutendex

private fun missingAudiobookDetailsMessage(
    bookId: String,
    book: AudioBook?,
    noPlayableChapters: Boolean = false,
): String =
    when {
        book?.source == BookSource.Gutendex || bookId.startsWith("gutendex-") ->
            "This Project Gutenberg record does not include an audiobook version."
        noPlayableChapters -> "No playable chapters were found for this audiobook."
        else -> "Could not load audiobook details."
    }

private data class SourceLink(
    val label: String,
    val url: String,
)

private fun AudioBook.sourceLink(): SourceLink? =
    when {
        !librivoxUrl.isNullOrBlank() -> SourceLink("LibriVox.org", librivoxUrl)
        !lit2goUrl.isNullOrBlank() -> SourceLink("Lit2Go", lit2goUrl)
        !wolneLekturyUrl.isNullOrBlank() -> SourceLink("WolneLektury.pl", wolneLekturyUrl)
        !gutenbergUrl.isNullOrBlank() -> SourceLink("Project Gutenberg", gutenbergUrl)
        else -> null
    }

@Composable
private fun ExternalLinkRow(
    sourceLink: SourceLink?,
    donationLink: com.librivox.mobile.model.SourceDonationLink?,
    onOpenUrl: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sourceLink?.let {
            TextButton(
                onClick = { onOpenUrl(it.url) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(" Open in ${it.label}")
            }
        }
        if (sourceLink != null && donationLink != null) {
            Text(
                text = "|",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        donationLink?.let {
            TextButton(
                onClick = { onOpenUrl(it.url) },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(" Donate to ${it.sourceName}")
            }
        }
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailBookActionSheet(
    book: AudioBook,
    coverArtDisplayMode: CoverArtDisplayMode,
    onDismiss: () -> Unit,
    onGoToAuthor: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenReader: (() -> Unit)?,
    onShowChapters: (() -> Unit)?,
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
                    val downloaded = book.downloadedBookAssetCount()
                    val total = book.downloadableBookAssetCount()
                    val active = book.activeDownloadBookAssetCount()
                    Text(
                        text = when {
                            active > 0 -> "Downloading $downloaded/$total"
                            downloaded > 0 && downloaded == total -> "Fully downloaded"
                            downloaded > 0 -> "Partially downloaded"
                            else -> book.author
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
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
                },
            )
            BookSourceInfoRow(book = book)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            onShowChapters?.let {
                DetailBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = "View chapters",
                    onClick = it,
                )
            }
            onOpenReader?.let {
                DetailBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = "Read book",
                    onClick = it,
                )
            }
            if (canQueue) {
                DetailBookActionRow(
                    icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                    label = "Cast audio",
                    onClick = onCastAudio,
                )
            }
            DetailBookActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                label = "Share audiobook",
                onClick = onShare,
            )
            if (book.authorSearchQuery().isNotBlank()) {
                DetailBookActionRow(
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = "Go to author",
                    onClick = onGoToAuthor,
                )
            }
            if (canQueue) {
                DetailBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    label = "Play next",
                    onClick = onPlayNext,
                )
                DetailBookActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    label = "Add to queue",
                    onClick = onAddToQueue,
                )
            }
            if (!inLibrary) {
                DetailBookActionRow(
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = "Save book to library",
                    onClick = onAddToLibrary,
                )
            } else if (canRemoveFromLibrary) {
                DetailBookActionRow(
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    label = "Remove from library",
                    onClick = onRemoveFromLibrary,
                )
            }
            when {
                hasDownloads && book.source != BookSource.LocalAsset -> {
                    DetailBookActionRow(
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        label = "Delete downloads",
                        onClick = onDeleteDownloads,
                    )
                }
                canDownload -> {
                    DetailBookActionRow(
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = "Download book",
                        onClick = onDownloadBook,
                    )
                }
            }
            onDonate?.let {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                DetailBookActionRow(
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
private fun DetailBookActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon,
        headlineContent = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun AudioBook.needsCatalogRefresh(): Boolean =
    chapters.isEmpty() ||
        description.isBlank() ||
        (source == BookSource.WolneLektury && !translationMetadataChecked) ||
        description.startsWith("Featured LibriVox audiobook") ||
        description.startsWith("A LibriVox recording read")

private fun AudioBook.canPaintDetailImmediately(): Boolean =
    title.isNotBlank() &&
        (chapters.isNotEmpty() || description.isNotBlank())

private fun AudioBookChapter.canDownloadManually(): Boolean =
    !isDownloaded() &&
        !listenUrl.isNullOrBlank() &&
        downloadState != DownloadState.Queued &&
        downloadState != DownloadState.Downloading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkActionsSheet(
    bookmark: Bookmark,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onJumpTo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                headlineContent = { Text(formatHmsFromMs(bookmark.positionMs)) },
                supportingContent = {
                    Text(
                        text = chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            HorizontalDivider()
            BookmarkActionRow(Icons.Filled.PlayArrow, "Jump to bookmark", onJumpTo)
            BookmarkActionRow(Icons.Filled.Edit, "Edit note", onEdit)
            BookmarkActionRow(Icons.Filled.Delete, "Delete", onDelete)
        }
    }
}

@Composable
private fun BookmarkActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkNoteEditorSheet(
    bookmark: Bookmark,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(bookmark.id) { mutableStateOf(bookmark.note) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Bookmark note",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = formatHmsFromMs(bookmark.positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(1000) },
                label = { Text("Note") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(onClick = { onSave(draft) }) {
                    Text("Save")
                }
            }
        }
    }
}

private fun BookmarkPanelSections.chapterTitleFor(bookmark: Bookmark): String =
    sequenceOf(currentChapter)
        .filterNotNull()
        .plus(remainingChapters.asSequence())
        .firstOrNull { section -> section.bookmarks.any { it.id == bookmark.id } }
        ?.chapterTitle
        ?: "Chapter"

@Composable
private fun CollapsibleDescription(text: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (text.length > 220) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text(if (expanded) "Show less" else "Show more")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListRowContainer(
    index: Int,
    lastIndex: Int,
    content: @Composable () -> Unit,
) {
    val shape = chapterListRowShape(index, lastIndex)
    val tonal = LocalTonalSurfaces.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = tonal.listItem,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.clip(shape)) {
            content()
        }
    }
}

private fun chapterListRowShape(
    index: Int,
    lastIndex: Int,
): RoundedCornerShape {
    val radius = 18.dp
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == lastIndex) radius else 0.dp
    return RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterRow(
    book: AudioBook,
    chapter: AudioBookChapter,
    isPlaying: Boolean,
    isCurrent: Boolean,
    feedback: PlaybackFeedback,
    onOpenReader: (() -> Unit)?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    canDownload: Boolean,
    canRemoveDownload: Boolean,
    onRemoveDownload: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    showFeedbackActions: Boolean,
    onCastAudio: () -> Unit,
    onShare: () -> Unit,
    onShowActions: () -> Unit,
    isDeepLinked: Boolean,
) {
    val durationLabel = chapter.durationSeconds
        .takeIf { it > 0L }
        ?.let { formatHmsFromSeconds(it) }
    val supporting = buildString {
        append("Chapter ${chapter.number}")
        durationLabel?.let { append(" • $it remaining") }
        chapter.reader?.let { append(" • $it") }
        chapter.director?.let { append(" • dir. $it") }
        if (isCurrent && !isPlaying) append(" • Paused")
        if (isPlaying) append(" • Now playing")
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onShowActions,
            ),
        leadingContent = {
            FilledIconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = when {
                        isPlaying -> "Pause chapter ${chapter.number}"
                        else -> "Play chapter ${chapter.number}"
                    },
                )
            }
        },
        headlineContent = {
            Text(
                text = chapter.numberedTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (showFeedbackActions) {
                    ChapterFeedbackIndicator(feedback = feedback)
                }
                DownloadAffordance(chapter = chapter, onDownload = onDownload)
                ChapterOverflowMenu(
                    chapterNumber = chapter.number,
                    durationLabel = durationLabel,
                    onOpenReader = onOpenReader,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onCastAudio = onCastAudio,
                    canDownload = canDownload,
                    onDownload = onDownload,
                    canRemoveDownload = canRemoveDownload,
                    onRemoveDownload = onRemoveDownload,
                    onLike = onLike,
                    onDislike = onDislike,
                    showFeedbackActions = showFeedbackActions,
                    onShare = onShare,
                    likeSelected = feedback == PlaybackFeedback.Liked,
                    dislikeSelected = feedback == PlaybackFeedback.Disliked,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isDeepLinked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        ),
    )
}

@Composable
private fun DownloadAffordance(
    chapter: AudioBookChapter,
    onDownload: () -> Unit,
) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            chapter.isDownloaded() -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            chapter.downloadState == DownloadState.Queued ||
                chapter.downloadState == DownloadState.Downloading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            chapter.listenUrl.isNullOrBlank() -> Unit
            else -> {
                FilledTonalIconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download chapter")
                }
            }
        }
    }
}

@Composable
private fun ChapterFeedbackIndicator(feedback: PlaybackFeedback) {
    if (feedback == PlaybackFeedback.None) return
    val selectedLike = feedback == PlaybackFeedback.Liked
    Icon(
        imageVector = if (selectedLike) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
        contentDescription = if (selectedLike) "Liked chapter" else "Disliked chapter",
        tint = if (selectedLike) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        modifier = Modifier
            .size(32.dp)
            .padding(6.dp),
    )
}

@Composable
private fun ChapterOverflowMenu(
    chapterNumber: Int,
    durationLabel: String?,
    onOpenReader: (() -> Unit)?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onCastAudio: () -> Unit,
    canDownload: Boolean,
    onDownload: () -> Unit,
    canRemoveDownload: Boolean,
    onRemoveDownload: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    showFeedbackActions: Boolean,
    onShare: () -> Unit,
    likeSelected: Boolean,
    dislikeSelected: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (durationLabel != null) {
                DropdownMenuItem(
                    text = { Text("Chapter $chapterNumber duration: $durationLabel") },
                    onClick = {},
                    enabled = false,
                )
                HorizontalDivider()
            }
            onOpenReader?.let {
                DropdownMenuItem(
                    text = { Text("Read from here") },
                    onClick = { it(); expanded = false },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                )
            }
            DropdownMenuItem(
                text = { Text("Play next") },
                onClick = { onPlayNext(); expanded = false },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                onClick = { onAddToQueue(); expanded = false },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Cast audio") },
                onClick = { onCastAudio(); expanded = false },
                leadingIcon = { Icon(Icons.Filled.Cast, contentDescription = null) },
            )
            if (canDownload) {
                DropdownMenuItem(
                    text = { Text("Download chapter") },
                    onClick = { onDownload(); expanded = false },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                )
            }
            if (canRemoveDownload) {
                DropdownMenuItem(
                    text = { Text("Remove from downloads") },
                    onClick = { onRemoveDownload(); expanded = false },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                )
            }
            DropdownMenuItem(
                text = { Text("Share chapter") },
                onClick = { onShare(); expanded = false },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
            )
            if (showFeedbackActions) {
                DropdownMenuItem(
                    text = { Text(if (likeSelected) "Remove like" else "Like") },
                    onClick = { onLike(); expanded = false },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = if (likeSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (dislikeSelected) "Remove dislike" else "Dislike") },
                    onClick = { onDislike(); expanded = false },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = if (dislikeSelected) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterActionSheet(
    book: AudioBook,
    chapter: AudioBookChapter,
    coverArtDisplayMode: CoverArtDisplayMode,
    feedback: PlaybackFeedback,
    canDownload: Boolean,
    canRemoveDownload: Boolean,
    showFeedbackActions: Boolean,
    onOpenReader: (() -> Unit)?,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onCastAudio: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
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
                        text = chapter.numberedTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            onOpenReader?.let {
                ChapterActionRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    label = "Read from here",
                    onClick = it,
                )
            }
            ChapterActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                label = "Play next",
                onClick = onPlayNext,
            )
            ChapterActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                label = "Add to queue",
                onClick = onAddToQueue,
            )
            ChapterActionRow(
                icon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                label = "Cast audio",
                onClick = onCastAudio,
            )
            if (canDownload) {
                ChapterActionRow(
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = "Download chapter",
                    onClick = onDownload,
                )
            }
            if (canRemoveDownload) {
                ChapterActionRow(
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    label = "Remove from downloads",
                    onClick = onRemoveDownload,
                )
            }
            ChapterActionRow(
                icon = { Icon(Icons.Filled.Share, contentDescription = null) },
                label = "Share chapter",
                onClick = onShare,
            )
            if (showFeedbackActions) {
                ChapterActionRow(
                    icon = {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = if (feedback == PlaybackFeedback.Liked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    label = if (feedback == PlaybackFeedback.Liked) "Remove like" else "Like",
                    onClick = onLike,
                )
                ChapterActionRow(
                    icon = {
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = if (feedback == PlaybackFeedback.Disliked) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    label = if (feedback == PlaybackFeedback.Disliked) "Remove dislike" else "Dislike",
                    onClick = onDislike,
                )
            }
            Box(modifier = Modifier.size(1.dp).padding(bottom = 24.dp))
        }
    }
}

private fun AudioBook.detailSeedColor(): Color =
    when (fallbackCover) {
        FallbackCover.Cathedral -> Color(0xFF4E626D)
        FallbackCover.Jungle -> Color(0xFF3E6B63)
        FallbackCover.Pride -> Color(0xFF7B5360)
        FallbackCover.Generated -> {
            val index = (id.hashCode() and Int.MAX_VALUE) % GeneratedDetailSeedColors.size
            GeneratedDetailSeedColors[index]
        }
    }

@Composable
private fun ChapterActionRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = icon,
        headlineContent = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
