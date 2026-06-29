@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.librivox.mobile.ui.reader

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.SleepTimerPreset
import com.librivox.mobile.model.SleepTimerState
import com.librivox.mobile.model.chapterIndexOrZero
import com.librivox.mobile.model.canReadSourceText
import com.librivox.mobile.model.downloadableSourceSyncAssetUrl
import com.librivox.mobile.model.localCoverFile
import com.librivox.mobile.model.remoteCoverImageUrl
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.playback.PlayerState
import com.librivox.mobile.playback.ReaderHighlightMode
import com.librivox.mobile.playback.ReaderSettings
import com.librivox.mobile.readalong.ReadAlongBook
import com.librivox.mobile.readalong.ReadAlongChapter
import com.librivox.mobile.readalong.ReadAlongFootnote
import com.librivox.mobile.readalong.ReadAlongFontStyle as SourceFontStyle
import com.librivox.mobile.readalong.ReadAlongFontWeight as SourceFontWeight
import com.librivox.mobile.readalong.ReadAlongSearchHit
import com.librivox.mobile.readalong.ReadAlongSegment
import com.librivox.mobile.readalong.ReadAlongSegmentRole
import com.librivox.mobile.readalong.ReadAlongTextAlign as SourceTextAlign
import com.librivox.mobile.readalong.ReadAlongTextStyle as SourceTextStyle
import com.librivox.mobile.readalong.ReadAlongTocItem
import com.librivox.mobile.readalong.normalizedForReaderSearch
import com.librivox.mobile.ui.components.castAudiobookAudio
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private object ReadContextMenuKey
private object ReaderCopyContextMenuKey
private object BookmarkContextMenuKey
private object CastContextMenuKey

private val ReaderTextScales = listOf(0.90f, 1.00f, 1.15f, 1.30f)
private val ReaderStaticBottomPadding = 232.dp
private val ReaderMiniPlayerBottomPadding = 320.dp
private val ReaderMiniPlayerHandleBottomPadding = 132.dp
private val ReaderPageHandleActivationWidth = 12.dp
private val ReaderPageHandleThumbWidth = 8.dp
private const val ReaderEstimatedCharsPerPage = 1_560
private const val ReaderPageHandleCollapseDelayMillis = 1_200L

@Composable
fun BookReaderScreen(
    bookId: String,
    initialChapterId: String?,
    nowPlayingOverlayOpen: Boolean = false,
    onBack: () -> Unit,
    onOpenBookDetails: (String) -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val tonal = LocalTonalSurfaces.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsStore = graph.app.playbackSettingsStore
    var readerSettings by remember(settingsStore) { mutableStateOf(settingsStore.readerSettings) }
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val stagedBookDetails by graph.app.stagedBookDetails.collectAsState()
    val playerState by graph.playerStateRepository.state.collectAsState()
    val book = libraryState?.bookById(bookId) ?: stagedBookDetails[bookId]
    var readAlongResult by remember(book?.id, book?.localAudioEpubFileName, book?.epubUrl, book?.audioEpubUrl, book?.daisyUrl) {
        mutableStateOf<Result<ReadAlongBook>?>(null)
    }
    var loadingFullReadAlong by remember(book?.id, book?.localAudioEpubFileName, book?.epubUrl, book?.audioEpubUrl, book?.daisyUrl) {
        mutableStateOf(false)
    }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeHitIndex by remember { mutableIntStateOf(-1) }
    var showFindAll by remember { mutableStateOf(false) }
    var showReaderSettings by remember { mutableStateOf(false) }
    var showReaderContents by remember { mutableStateOf(false) }
    var showReaderHighlightMode by remember { mutableStateOf(false) }
    var showReaderSpeed by remember { mutableStateOf(false) }
    var showReaderSleep by remember { mutableStateOf(false) }
    var didResolveInitialScroll by remember(bookId, initialChapterId) { mutableStateOf(false) }
    var didUseManualReaderJump by remember(bookId) { mutableStateOf(false) }
    var selectedTextSelection by remember(bookId) { mutableStateOf<ReaderSelection?>(null) }
    var deferFollowUntilActiveSegmentChangesFrom by remember(bookId) { mutableStateOf<String?>(null) }
    var wasReaderBookPlaying by remember(bookId) { mutableStateOf(playerState.bookId == bookId && playerState.isPlaying) }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    DisposableEffect(settingsStore) {
        val listener = settingsStore.registerReaderSettingsListener { readerSettings = it }
        onDispose { settingsStore.unregisterListener(listener) }
    }

    LaunchedEffect(book?.id, initialChapterId, playerState.bookId, playerState.chapterId) {
        val current = book ?: return@LaunchedEffect
        val activePlaybackChapterId = if (playerState.bookId == current.id) {
            playerState.chapterId
        } else {
            null
        }
        val chapterId = initialChapterId
            ?: activePlaybackChapterId
            ?: graph.app.progressStore.lastChapterId(current.id)
            ?: current.chapters.firstOrNull()?.id
        graph.app.progressStore.markBookActivity(
            bookId = current.id,
            chapterId = chapterId,
            chapterIndex = current.chapterIndexOrZero(chapterId),
        )
    }

    fun updateReaderSettings(next: ReaderSettings) {
        readerSettings = next
        settingsStore.saveReaderSettings(next)
    }

    LaunchedEffect(book, initialChapterId) {
        val current = book ?: return@LaunchedEffect
        if (!current.canReadSourceText()) {
            loadingFullReadAlong = false
            readAlongResult = Result.failure(IllegalStateException("This Wolne Lektury book does not include source-provided read-along text."))
            return@LaunchedEffect
        }
        readAlongResult = null
        loadingFullReadAlong = false
        val readableBook = if (
            current.source == BookSource.WolneLektury &&
            current.downloadableSourceSyncAssetUrl() == null
        ) {
            withContext(Dispatchers.IO) {
                graph.app.catalogClient.fetchByIds(current.id).firstOrNull()
            }?.also { hydrated ->
                graph.app.stageBookDetail(hydrated)
                graph.app.libraryRepository.upsertCatalogBooks(listOf(hydrated))
            } ?: current
        } else {
            current
        }
        val activePlaybackChapterId = if (playerState.bookId == readableBook.id) {
            playerState.chapterId
        } else {
            null
        }
        val preferredChapterId = initialChapterId
            ?: activePlaybackChapterId
            ?: graph.app.progressStore.lastChapterId(readableBook.id)
            ?: readableBook.chapters.firstOrNull()?.id
        val preferredResult = graph.app.readAlongRepository.loadPreferredChapter(
            book = readableBook,
            preferredChapterId = preferredChapterId,
        )
        if (
            preferredResult.isSuccess &&
            preferredResult.getOrNull()?.chapters?.isNotEmpty() == true
        ) {
            readAlongResult = preferredResult
            loadingFullReadAlong = preferredResult.getOrNull()?.isPartial == true
        }
        val fullResult = graph.app.readAlongRepository.load(readableBook)
        val hadPartialResult = readAlongResult?.getOrNull()?.isPartial == true
        loadingFullReadAlong = false
        if (fullResult.isSuccess && hadPartialResult && !didUseManualReaderJump) {
            didResolveInitialScroll = false
        }
        readAlongResult = if (fullResult.isSuccess || readAlongResult == null) {
            fullResult
        } else {
            readAlongResult
        }
    }

    val readAlong = readAlongResult?.getOrNull()
    val loadError = readAlongResult?.exceptionOrNull()
    val searchHits = remember(readAlong, searchQuery) { readAlong?.search(searchQuery).orEmpty() }
    val activeHit = searchHits.getOrNull(activeHitIndex)
    val activePositionMs = readerActivePositionMs(
        playerPositionMs = playerState.positionMs,
        scrubPreviewPositionMs = playerState.scrubPreviewPositionMs,
    )
    val currentBookLoaded = book?.id == playerState.bookId && playerState.hasMedia
    val activeSegmentId = remember(readAlong, playerState.bookId, playerState.chapterId, activePositionMs) {
        if (book?.id != playerState.bookId) {
            null
        } else {
            readAlong
                ?.chapter(playerState.chapterId)
                ?.activeSegment(activePositionMs)
                ?.id
        }
    }
    val items = remember(readAlong) { readAlong?.readerItems().orEmpty() }
    val leadingReaderItemCount = if (book != null && readAlong != null) {
        1 + if (searchActive) 1 else 0
    } else {
        0
    }
    val tocJumpIndexes = remember(items, leadingReaderItemCount) {
        items.readerTocJumpIndexes(leadingReaderItemCount)
    }
    val itemIndexBySegment = tocJumpIndexes.itemIndexBySegment
    val itemIndexByChapter = tocJumpIndexes.itemIndexByChapter
    val pageStartIndexes = remember(items, leadingReaderItemCount) {
        items.mapIndexedNotNull { index, item ->
            (item as? ReaderItem.PageBreak)?.let { readerLazyListIndex(index, leadingReaderItemCount) }
        }
    }
    val estimatedReaderItemHeights = remember(bookId, items, leadingReaderItemCount, searchActive, readerSettings.textScale, density) {
        estimatedReaderListItemHeights(
            items = items,
            leadingReaderItemCount = leadingReaderItemCount,
            searchActive = searchActive,
            textScale = readerSettings.textScale,
            density = density,
        )
    }
    val measuredReaderItemHeights = remember(bookId, items, searchActive) { mutableStateMapOf<Int, Int>() }
    val currentReaderPageNumber by remember(listState, pageStartIndexes) {
        derivedStateOf {
            readerPageNumberForListIndex(
                listIndex = listState.firstVisibleItemIndex,
                pageStartIndexes = pageStartIndexes,
            )
        }
    }
    val readerScrollProgress by remember(listState, estimatedReaderItemHeights, measuredReaderItemHeights) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            readerScrollProgressForListPosition(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                estimatedItemSizesByIndex = estimatedReaderItemHeights,
                measuredItemSizesByIndex = measuredReaderItemHeights,
                viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
                totalItemCount = layoutInfo.totalItemsCount,
                canScrollBackward = listState.canScrollBackward,
                canScrollForward = listState.canScrollForward,
            )
        }
    }
    val selectedBookmarkTarget = remember(book?.id, selectedTextSelection) {
        book?.id?.let { bookmarkTargetForSelectedText(it, selectedTextSelection) }
    }
    val currentAudioBookmarkTarget = remember(
        book?.id,
        playerState.bookId,
        playerState.chapterId,
        playerState.positionMs,
        playerState.isPlaying,
    ) {
        book?.id?.let { readerBookId ->
            bookmarkTargetForCurrentAudio(
                readerBookId = readerBookId,
                playerBookId = playerState.bookId,
                playerChapterId = playerState.chapterId,
                playerPositionMs = playerState.positionMs,
                isPlaying = playerState.isPlaying,
            )
        }
    }

    fun visibleRange(): ReaderVisibleRange? {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val first = visibleItems.firstOrNull()?.index ?: return null
        val last = visibleItems.lastOrNull()?.index ?: return null
        return ReaderVisibleRange(first, last)
    }

    LaunchedEffect(
        listState,
        measuredReaderItemHeights,
    ) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { visibleItems ->
                visibleItems.forEach { (index, size) ->
                    if (size > 0) {
                        measuredReaderItemHeights[index] = size
                    }
                }
            }
    }

    LaunchedEffect(
        items,
        initialChapterId,
        activeSegmentId,
        playerState.bookId,
        book?.id,
        didResolveInitialScroll,
    ) {
        if (didResolveInitialScroll || items.isEmpty()) return@LaunchedEffect
        val activeIndex = activeSegmentId?.let { itemIndexBySegment[it] }
        val initialIndex = initialChapterId?.let { itemIndexByChapter[it] }
        val target = resolveReaderOpenScrollIndex(
            activeSegmentIndex = activeIndex,
            initialChapterIndex = initialIndex,
            isCurrentPlaybackBook = book?.id == playerState.bookId,
            didUseInitialScroll = didResolveInitialScroll,
        ) ?: return@LaunchedEffect
        didResolveInitialScroll = true
        listState.scrollToItem(
            index = target,
            scrollOffset = 0,
        )
    }

    LaunchedEffect(
        activeSegmentId,
        readerSettings.followPlayback,
        selectedTextSelection?.segmentId,
        deferFollowUntilActiveSegmentChangesFrom,
        itemIndexBySegment,
    ) {
        val deferredSegment = deferFollowUntilActiveSegmentChangesFrom
        if (deferredSegment != null && activeSegmentId != deferredSegment) {
            deferFollowUntilActiveSegmentChangesFrom = null
        }
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = activeSegmentId?.let { itemIndexBySegment[it] },
            activeSegmentId = activeSegmentId,
            followPlayback = readerSettings.followPlayback,
            selectedSegmentId = selectedTextSelection?.segmentId,
            deferUntilActiveSegmentChangesFrom = deferFollowUntilActiveSegmentChangesFrom,
            visibleRange = visibleRange(),
        ) ?: return@LaunchedEffect
        listState.animateScrollToItem(
            index = target,
            scrollOffset = 0,
        )
    }

    LaunchedEffect(
        currentBookLoaded,
        playerState.isPlaying,
        activeSegmentId,
        readerSettings.followPlayback,
        selectedTextSelection?.segmentId,
        deferFollowUntilActiveSegmentChangesFrom,
        itemIndexBySegment,
    ) {
        val isReaderBookPlaying = currentBookLoaded && playerState.isPlaying
        val startedPlayback = isReaderBookPlaying && !wasReaderBookPlaying
        wasReaderBookPlaying = isReaderBookPlaying
        if (!startedPlayback) return@LaunchedEffect
        val target = readerFollowScrollTargetIndex(
            activeSegmentIndex = activeSegmentId?.let { itemIndexBySegment[it] },
            activeSegmentId = activeSegmentId,
            followPlayback = readerSettings.followPlayback,
            selectedSegmentId = selectedTextSelection?.segmentId,
            deferUntilActiveSegmentChangesFrom = deferFollowUntilActiveSegmentChangesFrom,
            visibleRange = visibleRange(),
        ) ?: return@LaunchedEffect
        listState.animateScrollToItem(
            index = target,
            scrollOffset = 0,
        )
    }

    LaunchedEffect(nowPlayingOverlayOpen) {
        if (nowPlayingOverlayOpen) {
            selectedTextSelection = null
            focusManager.clearFocus(force = true)
        }
    }

    fun jumpToHit(hit: ReadAlongSearchHit?) {
        val index = hit?.segmentId?.let { itemIndexBySegment[it] } ?: return
        didUseManualReaderJump = true
        didResolveInitialScroll = true
        deferFollowUntilActiveSegmentChangesFrom = activeSegmentId
        scope.launch { listState.scrollToItem(index) }
    }

    fun jumpToTocItem(item: ReadAlongTocItem): Boolean {
        val index = item.readerTocJumpListIndex(tocJumpIndexes)
            ?: return false
        didUseManualReaderJump = true
        didResolveInitialScroll = true
        selectedTextSelection = null
        deferFollowUntilActiveSegmentChangesFrom = activeSegmentId
        scope.launch {
            listState.scrollToItem(index = index, scrollOffset = 0)
        }
        return true
    }

    fun scrollToReadAlongPosition(chapterId: String?, positionMs: Long) {
        val target = readAlong
            ?.chapter(chapterId)
            ?.activeSegment(positionMs)
            ?.id
            ?.let { itemIndexBySegment[it] }
            ?: chapterId?.let { itemIndexByChapter[it] }
            ?: return
        scope.launch { listState.animateScrollToItem(target) }
    }

    fun readFromSegment(chapterId: String, positionMs: Long?) {
        val currentBook = book ?: return
        val targetMs = positionMs ?: return
        val mediaItems = currentBook.toMediaItems(context)
        if (mediaItems.isEmpty()) return
        if (playerState.bookId == currentBook.id && playerState.chapterId == chapterId) {
            graph.playerStateRepository.seekTo(targetMs)
            if (!playerState.isPlaying) {
                graph.playerStateRepository.playPause()
            }
            scrollToReadAlongPosition(chapterId, targetMs)
            return
        }
        val index = currentBook.chapterIndexOrZero(chapterId)
        graph.playerStateRepository.playMediaItems(mediaItems, index, targetMs)
        scrollToReadAlongPosition(chapterId, targetMs)
        scope.launch { graph.app.downloadManager.downloadCover(currentBook) }
    }

    fun castAudioFromReader(chapterId: String?, positionMs: Long?) {
        val currentBook = book ?: return
        castAudiobookAudio(context, graph, currentBook, chapterId, positionMs)
        positionMs?.let { scrollToReadAlongPosition(chapterId, it) }
    }

    fun startBookAudioFromReader() {
        val currentBook = book ?: return
        val mediaItems = currentBook.toMediaItems(context)
        if (mediaItems.isEmpty()) return
        val chapterId = initialChapterId
            ?: graph.app.progressStore.lastChapterId(currentBook.id)
            ?: currentBook.chapters.firstOrNull()?.id
        val startIndex = currentBook.chapterIndexOrZero(chapterId)
        val startPositionMs = chapterId?.let {
            graph.app.progressStore.savedPositionMs(currentBook.id, it)
        } ?: 0L
        graph.playerStateRepository.playMediaItems(mediaItems, startIndex, startPositionMs)
        scrollToReadAlongPosition(chapterId, startPositionMs)
        scope.launch { graph.app.downloadManager.downloadCover(currentBook) }
    }

    fun saveBookmark(target: ReaderBookmarkTarget, message: String) {
        scope.launch {
            graph.app.bookmarkRepository.add(
                bookId = target.bookId,
                chapterId = target.chapterId,
                positionMs = target.positionMs,
                note = target.note,
            )
            snackbarHostState.showSnackbar(message)
        }
    }

    fun onTextBlockSelectionChanged(textBlock: ReaderItem.TextBlock, selection: TextRange) {
        val nextSelection = textBlock.selection(selection.start, selection.end)
        if (nextSelection != null) {
            selectedTextSelection = nextSelection
            deferFollowUntilActiveSegmentChangesFrom = null
            return
        }
        if (selectedTextSelection?.segmentId?.let(textBlock::containsSegment) == true) {
            selectedTextSelection = null
            deferFollowUntilActiveSegmentChangesFrom = activeSegmentId ?: textBlock.segments.firstOrNull()?.id
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tonal.screenBackground),
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (searchActive) {
                                "Search book"
                            } else {
                                book?.title ?: "Book text"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(
                                enabled = book != null && !searchActive,
                                role = Role.Button,
                            ) {
                                book?.id?.let(onOpenBookDetails)
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (book != null && (!playerState.hasMedia || playerState.bookId != book.id)) {
                            IconButton(
                                onClick = {
                                    haptics.key()
                                    startBookAudioFromReader()
                                },
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play this book")
                            }
                        }
                        IconButton(
                            enabled = readAlong?.tableOfContents?.isNotEmpty() == true,
                            onClick = {
                                haptics.key()
                                showReaderContents = true
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Chapters")
                        }
                        IconButton(
                            onClick = {
                                haptics.key()
                                searchActive = !searchActive
                            },
                        ) {
                            Icon(
                                imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = if (searchActive) "Close search" else "Search book",
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.key()
                                showReaderSettings = true
                            },
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Reader options")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = tonal.screenBackground,
                        scrolledContainerColor = tonal.screenBackground,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    scrollBehavior = scrollBehavior,
                )
                readAlong?.let {
                    ReaderSyncAndPlaybackControls(
                        readAlong = it,
                        loadingRemainder = loadingFullReadAlong,
                        settings = readerSettings,
                        onSettingsChange = ::updateReaderSettings,
                        playerState = playerState,
                        includePlaybackControls = currentBookLoaded,
                        onSkipBack = {
                            haptics.tick()
                            graph.playerStateRepository.seekBack()
                        },
                        onSkipForward = {
                            haptics.tick()
                            graph.playerStateRepository.seekForward()
                        },
                        onSpeedClick = {
                            haptics.key()
                            showReaderSpeed = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
            }
        },
    ) { padding ->
        when {
            book == null -> ReaderLoading(padding)
            book.source != BookSource.WolneLektury || !book.canReadSourceText() -> ReaderUnavailable(
                message = "Read-along text is available for Wolne Lektury titles with audio EPUB or DAISY sync files.",
                modifier = Modifier.padding(padding),
            )
            readAlong == null && loadError == null -> ReaderLoading(padding)
            loadError != null -> ReaderUnavailable(
                message = loadError.message ?: "Could not open the book text.",
                modifier = Modifier.padding(padding),
            )
            readAlong != null -> {
                val colorScheme = MaterialTheme.colorScheme
                val readerSelectionColors = remember(colorScheme.tertiary) {
                    TextSelectionColors(
                        handleColor = colorScheme.tertiary,
                        backgroundColor = colorScheme.tertiary.copy(alpha = 0.4f),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    CompositionLocalProvider(
                        LocalTextSelectionColors provides readerSelectionColors,
                        LocalBringIntoViewSpec provides ReaderNoOpBringIntoViewSpec,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 56.dp,
                                top = 8.dp,
                                bottom = if (playerState.hasMedia) {
                                    ReaderMiniPlayerBottomPadding
                                } else {
                                    ReaderStaticBottomPadding
                                },
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            book.let { currentBook ->
                                item(key = "reader-book-header") {
                                    ReaderBookHeader(
                                        book = currentBook,
                                        onOpenBookDetails = onOpenBookDetails,
                                    )
                                }
                            }
                            if (searchActive) {
                                item(key = "reader-search") {
                                    ReaderSearchBar(
                                        query = searchQuery,
                                        onQueryChange = {
                                            searchQuery = it
                                            activeHitIndex = -1
                                        },
                                        hitCount = searchHits.size,
                                        activeHitNumber = activeHitIndex.takeIf { it >= 0 }?.plus(1),
                                        onPrevious = {
                                            if (searchHits.isNotEmpty()) {
                                                activeHitIndex = if (activeHitIndex <= 0) searchHits.lastIndex else activeHitIndex - 1
                                                jumpToHit(searchHits[activeHitIndex])
                                            }
                                        },
                                        onNext = {
                                            if (searchHits.isNotEmpty()) {
                                                activeHitIndex = if (activeHitIndex < 0 || activeHitIndex >= searchHits.lastIndex) {
                                                    0
                                                } else {
                                                    activeHitIndex + 1
                                                }
                                                jumpToHit(searchHits[activeHitIndex])
                                            }
                                        },
                                        onFindAll = { showFindAll = true },
                                    )
                                }
                            }
                            itemsIndexed(
                                items = items,
                                key = { _, item -> item.key },
                            ) { _, item ->
                                when (item) {
                                    is ReaderItem.PageBreak -> ReaderPageBreak(
                                        pageNumber = item.pageNumber,
                                        pageCount = item.pageCount,
                                    )
                                    is ReaderItem.TableOfContents -> ReaderTableOfContents(
                                        onClick = { showReaderContents = true },
                                    )
                                    is ReaderItem.ChapterHeader -> ChapterHeader(
                                        chapter = item.chapter,
                                        textScale = readerSettings.textScale,
                                    )
                                    is ReaderItem.TextBlock -> SelectableTextBlock(
                                        textBlock = item,
                                        highlightRole = readerHighlightRoleForTextBlock(
                                            textBlock = item,
                                            activeSegmentId = activeSegmentId,
                                            settings = readerSettings,
                                        ),
                                        activeTextRange = activeReaderTextRangeForTextBlock(
                                            textBlock = item,
                                            activeSegmentId = activeSegmentId,
                                            activePositionMs = activePositionMs,
                                            settings = readerSettings,
                                        ),
                                        selectedSegmentId = selectedTextSelection?.segmentId,
                                        isSearchHit = activeHit?.segmentId?.let(item::containsSegment) == true,
                                        searchQuery = searchQuery.takeIf { searchActive },
                                        textScale = readerSettings.textScale,
                                        onRead = {
                                            val target = selectedTextSelection
                                                ?.takeIf { item.containsSegment(it.segmentId) }
                                            readFromSegment(
                                                target?.chapterId ?: item.chapter.chapterId,
                                                target?.clipBeginMs ?: item.firstTimedPositionMs,
                                            )
                                        },
                                        onCast = {
                                            val target = selectedTextSelection
                                                ?.takeIf { item.containsSegment(it.segmentId) }
                                            castAudioFromReader(
                                                target?.chapterId ?: item.chapter.chapterId,
                                                target?.clipBeginMs ?: item.firstTimedPositionMs,
                                            )
                                        },
                                        onBookmark = {
                                            val target = selectedBookmarkTarget
                                                ?.takeIf { selectedTextSelection?.segmentId?.let(item::containsSegment) == true }
                                                ?: return@SelectableTextBlock
                                            haptics.confirm()
                                            saveBookmark(target, "Selected text bookmarked")
                                        },
                                        onSelectionChange = { selection -> onTextBlockSelectionChanged(item, selection) },
                                    )
                                }
                            }
                        }
                    }
                    ReaderPageScrollHandle(
                        listState = listState,
                        pageStartIndexes = pageStartIndexes,
                        currentPageNumber = currentReaderPageNumber,
                        pageCount = pageStartIndexes.size.coerceAtLeast(1),
                        scrollProgress = readerScrollProgress,
                        estimatedItemSizesByIndex = estimatedReaderItemHeights,
                        measuredItemSizesByIndex = measuredReaderItemHeights,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(bottom = if (playerState.hasMedia) ReaderMiniPlayerHandleBottomPadding else 0.dp)
                            .zIndex(1f),
                    )
                }
            }
        }
    }

    if (showFindAll && readAlong != null) {
        FindAllSheet(
            hits = searchHits,
            query = searchQuery,
            onDismiss = { showFindAll = false },
            onHitClick = { hit ->
                activeHitIndex = searchHits.indexOfFirst { it.id == hit.id }
                showFindAll = false
                jumpToHit(hit)
            },
        )
    }

	    if (showReaderSettings) {
	        ReaderSettingsSheet(
            readAlong = readAlong,
            settings = readerSettings,
            canOpenBookDetails = book != null,
            canOpenContents = readAlong?.tableOfContents?.isNotEmpty() == true,
            canCastAudio = book != null && readAlong?.hasTimedSync == true,
            castAudioSummary = when {
                currentAudioBookmarkTarget != null -> "From ${formatHmsFromMs(currentAudioBookmarkTarget.positionMs)}"
                selectedBookmarkTarget != null -> "From selected text"
                readAlong?.hasTimedSync == true -> "From this reader position"
                else -> "Timed audio is unavailable."
            },
            currentAudioBookmarkTarget = currentAudioBookmarkTarget,
            selectedBookmarkTarget = selectedBookmarkTarget,
            selectedTextPreview = selectedTextSelection?.selectedText,
            sleepTimerSummary = if (currentBookLoaded) {
                readerSleepTimerSummary(playerState.sleepTimer)
            } else {
                "Start this book's audio to set a timer."
            },
            canOpenSleepTimer = currentBookLoaded,
            onDismiss = { showReaderSettings = false },
            onOpenBookDetails = {
                haptics.key()
                showReaderSettings = false
                book?.id?.let(onOpenBookDetails)
            },
            onOpenContents = {
                haptics.key()
                showReaderSettings = false
                showReaderContents = true
            },
            onCastAudio = {
                haptics.key()
                showReaderSettings = false
                val target = currentAudioBookmarkTarget ?: selectedBookmarkTarget
                castAudioFromReader(
                    chapterId = target?.chapterId
                        ?: playerState.chapterId.takeIf { currentBookLoaded }
                        ?: initialChapterId,
                    positionMs = target?.positionMs,
                )
            },
            onBookmarkCurrentAudio = {
                haptics.confirm()
                showReaderSettings = false
                currentAudioBookmarkTarget?.let { saveBookmark(it, "Bookmark added at ${formatHmsFromMs(it.positionMs)}") }
            },
            onBookmarkSelectedText = {
                haptics.confirm()
                showReaderSettings = false
                selectedBookmarkTarget?.let { saveBookmark(it, "Selected text bookmarked") }
            },
            onSleepTimer = {
                haptics.key()
                showReaderSettings = false
                showReaderSleep = true
            },
            onHighlightMode = {
                haptics.key()
                showReaderSettings = false
                showReaderHighlightMode = true
            },
            onSettingsChange = { updateReaderSettings(it) },
        )
    }

    val readerContentsItems = remember(readAlong) { readAlong?.readerChapterPickerItems().orEmpty() }
    if (showReaderContents && readerContentsItems.isNotEmpty()) {
        ReaderContentsSheet(
            items = readerContentsItems,
            onDismiss = { showReaderContents = false },
            onItemClick = { item ->
                haptics.key()
                if (jumpToTocItem(item)) {
                    showReaderContents = false
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("Chapter text is still loading.")
                    }
                }
            },
        )
    }

    if (showReaderHighlightMode) {
        ReaderHighlightModeSheet(
            selectedMode = readerSettings.highlightMode,
            onDismiss = { showReaderHighlightMode = false },
            onModeSelected = { mode ->
                haptics.key()
                updateReaderSettings(readerSettings.copy(highlightMode = mode))
                showReaderHighlightMode = false
            },
        )
    }

    if (showReaderSpeed) {
        ReaderSpeedSheet(
            state = playerState,
            onDismiss = { showReaderSpeed = false },
        )
    }

    if (showReaderSleep) {
        ReaderSleepTimerSheet(
            state = playerState,
            onDismiss = { showReaderSleep = false },
        )
    }
	}

@Composable
private fun ReaderBookHeader(
    book: AudioBook,
    onOpenBookDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fullCoverUrl = book.remoteCoverImageUrl(preferFullQuality = true)
    val localCoverFile = book.localCoverFile(context.filesDir)
    val coverModel = if (book.localCoverSourceUrl == fullCoverUrl && localCoverFile != null) {
        localCoverFile
    } else {
        fullCoverUrl ?: localCoverFile
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button) { onOpenBookDetails(book.id) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = book.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReaderSyncAndPlaybackControls(
    readAlong: ReadAlongBook,
    loadingRemainder: Boolean,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    playerState: PlayerState,
    includePlaybackControls: Boolean,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loadingRemainder) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Loading rest of book") },
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                },
            )
        }
        if (readAlong.hasTimedSync) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
	                FilterChip(
	                    selected = settings.followPlayback,
	                    onClick = { onSettingsChange(settings.copy(followPlayback = !settings.followPlayback)) },
                    label = { Text("Source sync") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                )
                if (includePlaybackControls) {
                    ReaderSkipIntervalButton(
                        forward = false,
                        contentDescription = "Skip back 10 seconds",
                        onClick = onSkipBack,
                    )
                    ReaderSkipIntervalButton(
                        forward = true,
                        contentDescription = "Skip forward 10 seconds",
                        onClick = onSkipForward,
                    )
                    FilledTonalButton(
                        onClick = onSpeedClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = playerState.speed.label(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSkipIntervalButton(
    forward: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
            contentDescription = contentDescription,
            modifier = Modifier.clearAndSetSemantics {
                this.contentDescription = contentDescription
            },
        )
    }
}

@Composable
private fun ReaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hitCount: Int,
    activeHitNumber: Int?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFindAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            label = { Text("Find in book") },
            supportingText = {
                Text(
                    when {
                        query.isBlank() -> "Search text in this book"
                        hitCount == 0 -> "No matches"
                        activeHitNumber != null -> "$activeHitNumber of $hitCount"
                        else -> "$hitCount matches"
                    },
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = hitCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Find previous")
            }
            IconButton(onClick = onNext, enabled = hitCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Find next")
            }
            TextButton(onClick = onFindAll, enabled = hitCount > 0) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Text(" Find all")
            }
        }
    }
}

@Composable
private fun ReaderPageBreak(
    pageNumber: Int,
    pageCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = "Page ${pageNumber.readerPageLabel(pageCount)}/$pageCount",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f))
	}
}

@Composable
private fun ReaderContentsSheet(
    items: List<ReadAlongTocItem>,
    onDismiss: () -> Unit,
    onItemClick: (ReadAlongTocItem) -> Unit,
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
                .heightIn(max = 720.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                    ListItem(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(role = Role.Button) { onItemClick(item) }
                            .padding(start = (item.level * 16).dp),
                        leadingContent = {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        },
                        headlineContent = {
                            Text(
                                text = item.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderTableOfContents(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.wrapContentWidth(Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun ReaderPageScrollHandle(
    listState: LazyListState,
    pageStartIndexes: List<Int>,
    currentPageNumber: Int,
    pageCount: Int,
    scrollProgress: Float,
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
    modifier: Modifier = Modifier,
) {
    if (pageStartIndexes.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(Float.NaN) }
    var collapseJob by remember { mutableStateOf<Job?>(null) }
    var dragScrollJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val interactionActive by remember {
        derivedStateOf { expanded || dragging }
    }
    val labelVisible by remember(listState) {
        derivedStateOf { interactionActive || listState.isScrollInProgress }
    }
    fun expandHandle() {
        collapseJob?.cancel()
        expanded = true
    }
    fun scheduleHandleCollapse() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            delay(ReaderPageHandleCollapseDelayMillis)
            expanded = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            collapseJob?.cancel()
            dragScrollJob?.cancel()
        }
    }
    val expandedLabelWidth = when {
        pageCount >= 1_000 -> 112.dp
        pageCount >= 100 -> 92.dp
        else -> 72.dp
    }
    val labelWidth by animateDpAsState(
        targetValue = if (labelVisible) expandedLabelWidth else 0.dp,
        label = "readerPageLabelWidth",
    )
    val handleLaneWidth = ReaderPageHandleActivationWidth
    val handleHeight = 44.dp
    BoxWithConstraints(
        modifier = modifier
            .width(handleLaneWidth)
            .pointerInput(estimatedItemSizesByIndex, measuredItemSizesByIndex) {
                val handleHeightPx = handleHeight.toPx()
                fun progressForY(y: Float): Float =
                    ((y - handleHeightPx / 2f) / (size.height.toFloat() - handleHeightPx).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)

                fun scrollToProgressForY(y: Float) {
                    val fraction = progressForY(y)
                    dragProgress = fraction
                    val layoutInfo = listState.layoutInfo
                    val target = readerListScrollTargetForProgress(
                        progress = fraction,
                        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
                        measuredItemSizesByIndex = measuredItemSizesByIndex,
                        viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
                        totalItemCount = layoutInfo.totalItemsCount,
                    )
                    dragScrollJob?.cancel()
                    dragScrollJob = scope.launch {
                        listState.scrollToItem(
                            index = target.index,
                            scrollOffset = target.scrollOffset,
                        )
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    expandHandle()
                    dragging = true
                    scrollToProgressForY(down.position.y)
                    down.consume()
                    drag(down.id) { change ->
                        if (change.pressed) {
                            scrollToProgressForY(change.position.y)
                        }
                        change.consume()
                    }
                    dragging = false
                    dragProgress = Float.NaN
                    scheduleHandleCollapse()
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        val handleProgress = if (dragging && !dragProgress.isNaN()) {
            dragProgress
        } else {
            scrollProgress.coerceIn(0f, 1f)
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = (maxHeight - handleHeight).coerceAtLeast(0.dp) * handleProgress)
                .wrapContentWidth(align = Alignment.End, unbounded = true)
                .requiredWidth(labelWidth + ReaderPageHandleThumbWidth)
                .height(handleHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = if (labelVisible) 3.dp else 0.dp,
                modifier = Modifier
                    .width(labelWidth)
                    .fillMaxHeight(),
            ) {
                if (labelVisible) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$currentPageNumber/$pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = if (labelVisible) 3.dp else 0.dp,
                modifier = Modifier
                    .width(ReaderPageHandleThumbWidth)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {}
            }
        }
    }
}

@Composable
private fun ChapterHeader(chapter: ReadAlongChapter, textScale: Float) {
    Text(
        text = "${chapter.displayIndex.toString().padStart(2, '0')}: ${chapter.title}",
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize * textScale,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun SelectableTextBlock(
    textBlock: ReaderItem.TextBlock,
    highlightRole: ReaderSegmentHighlightRole,
    activeTextRange: ReaderTextRange?,
    selectedSegmentId: String?,
    isSearchHit: Boolean,
    searchQuery: String?,
    textScale: Float,
    onRead: () -> Unit,
    onCast: () -> Unit,
    onBookmark: () -> Unit,
    onSelectionChange: (TextRange) -> Unit,
) {
    val displaySegment = textBlock.displaySegment
    var selection by remember(textBlock.key) { mutableStateOf(TextRange(0, 0)) }
    val colorScheme = MaterialTheme.colorScheme
    val highlightColors = ReaderTextHighlightColors(
        searchBackground = colorScheme.tertiaryContainer.copy(alpha = 0.76f),
        activeSearchBackground = colorScheme.tertiary.copy(alpha = 0.22f),
        activeReadBackground = colorScheme.primaryContainer.copy(alpha = 0.96f),
        activeReadForeground = colorScheme.onPrimaryContainer,
        footnoteForeground = colorScheme.primary,
    )
    val footnoteFontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale * 0.72f
    var textLayoutResult by remember(textBlock.key) { mutableStateOf<TextLayoutResult?>(null) }
    var activeFootnote by remember(textBlock.key) { mutableStateOf<ReadAlongFootnote?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val clipboard = LocalClipboard.current
    val footnoteTouchSize = 44.dp
    val container = when (highlightRole) {
        ReaderSegmentHighlightRole.Current -> colorScheme.secondaryContainer
        ReaderSegmentHighlightRole.None -> Color.Transparent
    }
    val contentColor = when (highlightRole) {
        ReaderSegmentHighlightRole.Current -> colorScheme.onSecondaryContainer
        else -> colorScheme.onSurface
    }
    val border = when (highlightRole) {
        ReaderSegmentHighlightRole.Current -> BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.42f))
        else -> null
    }
    val rendersAsSourceHeading = displaySegment.role.isReaderHeading()
    val textStyle = displaySegment.readerTextStyle(
        textScale = textScale,
        color = contentColor,
    )
    val displayText = remember(
        displaySegment.text,
        displaySegment.sourceSpans,
        displaySegment.footnotes,
        activeTextRange,
        isSearchHit,
        searchQuery,
        highlightColors,
        textScale,
        textStyle.fontSize,
    ) {
        displaySegment.displayedReaderText(
            emphasized = isSearchHit,
            query = searchQuery.orEmpty(),
            activeReadRange = activeTextRange,
            colors = highlightColors,
            footnoteFontSize = footnoteFontSize,
            baseTextStyle = textStyle,
        )
    }
    val visualTransformation = remember(displayText) { displayText.visualTransformation() }

    LaunchedEffect(selectedSegmentId, textBlock.key) {
        if ((selectedSegmentId == null || !textBlock.containsSegment(selectedSegmentId)) && !selection.collapsed) {
            selection = TextRange(0, 0)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = contentColor,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .filterTextContextMenuComponents { component ->
                    component.key != TextContextMenuKeys.CopyKey
                }
                .appendTextContextMenuComponents {
                    if (!selection.collapsed) {
                        item(
                            key = ReaderCopyContextMenuKey,
                            label = "Copy",
                            leadingIcon = 0,
                        ) {
                            val start = selection.start.coerceIn(0, displaySegment.text.length)
                            val end = selection.end.coerceIn(0, displaySegment.text.length)
                            val selected = displaySegment.text.substring(start.coerceAtMost(end), start.coerceAtLeast(end))
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Book text", selected)),
                                )
                            }
                            close()
                        }
                    }
                    if (!selection.collapsed && textBlock.firstTimedPositionMs != null) {
                        item(
                            key = ReadContextMenuKey,
                            label = "Read",
                            leadingIcon = 0,
                        ) {
                            onRead()
                            close()
                        }
                        item(
                            key = CastContextMenuKey,
                            label = "Cast audio",
                            leadingIcon = 0,
                        ) {
                            onCast()
                            close()
                        }
                        item(
                            key = BookmarkContextMenuKey,
                            label = "Bookmark",
                            leadingIcon = 0,
                        ) {
                            onBookmark()
                            close()
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (rendersAsSourceHeading) {
                SelectionContainer {
                    Text(
                        text = displayText.transformedAnnotatedText,
                        style = textStyle,
                        color = contentColor,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                BasicTextField(
                    value = TextFieldValue(annotatedString = displayText.sourceAnnotatedText, selection = selection),
                    onValueChange = {
                        selection = it.selection
                        onSelectionChange(it.selection)
                    },
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    textStyle = textStyle,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Color.Transparent),
                )
                ReaderFootnoteTapTargets(
                    displayText = displayText,
                    textLayoutResult = textLayoutResult,
                    touchSize = footnoteTouchSize,
                    density = density,
                    onFootnoteClick = { footnote ->
                        activeFootnote = if (activeFootnote == footnote) {
                            null
                        } else {
                            footnote
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
    activeFootnote?.let { footnote ->
        ReaderFootnoteSheet(
            footnote = footnote,
            onDismiss = { activeFootnote = null },
        )
    }
}

@Composable
private fun ReadAlongSegment.readerTextStyle(
    textScale: Float,
    color: Color,
): androidx.compose.ui.text.TextStyle {
    val bodyFontSize = MaterialTheme.typography.bodyLarge.fontSize
    val sourceHeadingSizeMultiplier = sourceStyle.textSizeMultiplier
    val sourceBodySizeMultiplier = sourceStyle.textSizeMultiplier ?: 1f
    val sourceFontWeight = sourceStyle.fontWeight.toComposeFontWeight()
    val sourceFontStyle = sourceStyle.fontStyle.toComposeFontStyle()
    val sourceTextAlign = sourceStyle.textAlign.toComposeTextAlign()
    return when (role) {
        ReadAlongSegmentRole.Heading1 -> MaterialTheme.typography.headlineLarge.copy(
            color = color,
            fontSize = readerHeadingFontSize(
                defaultFontSize = MaterialTheme.typography.headlineLarge.fontSize,
                bodyFontSize = bodyFontSize,
                textScale = textScale,
                sourceMultiplier = sourceHeadingSizeMultiplier,
                fallbackMultiplier = 2.15f,
            ),
            fontWeight = sourceFontWeight ?: FontWeight.SemiBold,
            fontStyle = sourceFontStyle,
            textIndent = TextIndent.None,
        ).withSourceTextAlign(sourceTextAlign)
        ReadAlongSegmentRole.Heading2 -> MaterialTheme.typography.titleLarge.copy(
            color = color,
            fontSize = readerHeadingFontSize(
                defaultFontSize = MaterialTheme.typography.titleLarge.fontSize,
                bodyFontSize = bodyFontSize,
                textScale = textScale,
                sourceMultiplier = sourceHeadingSizeMultiplier,
                fallbackMultiplier = 1.65f,
            ),
            fontWeight = sourceFontWeight ?: FontWeight.SemiBold,
            fontStyle = sourceFontStyle,
            textIndent = TextIndent.None,
        ).withSourceTextAlign(sourceTextAlign)
        ReadAlongSegmentRole.Heading3 -> MaterialTheme.typography.titleMedium.copy(
            color = color,
            fontSize = readerHeadingFontSize(
                defaultFontSize = MaterialTheme.typography.titleMedium.fontSize,
                bodyFontSize = bodyFontSize,
                textScale = textScale,
                sourceMultiplier = sourceHeadingSizeMultiplier,
                fallbackMultiplier = 1.35f,
            ),
            fontWeight = sourceFontWeight ?: FontWeight.SemiBold,
            fontStyle = sourceFontStyle,
            textIndent = TextIndent.None,
        ).withSourceTextAlign(sourceTextAlign)
        ReadAlongSegmentRole.Quote -> MaterialTheme.typography.bodyLarge.copy(
            color = color,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale * sourceBodySizeMultiplier,
            fontWeight = sourceFontWeight,
            fontStyle = sourceFontStyle ?: FontStyle.Italic,
            textIndent = TextIndent.None,
        ).withSourceTextAlign(sourceTextAlign)
        ReadAlongSegmentRole.Verse -> MaterialTheme.typography.bodyLarge.copy(
            color = color,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale * sourceBodySizeMultiplier,
            fontWeight = sourceFontWeight,
            fontStyle = sourceFontStyle,
            textIndent = TextIndent.None,
        ).withSourceTextAlign(sourceTextAlign)
        ReadAlongSegmentRole.Paragraph -> MaterialTheme.typography.bodyLarge.copy(
            color = color,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale * sourceBodySizeMultiplier,
            fontWeight = sourceFontWeight,
            fontStyle = sourceFontStyle,
            textIndent = if (firstLineIndent) {
                TextIndent(firstLine = 18.sp)
            } else {
                TextIndent.None
            },
        ).withSourceTextAlign(sourceTextAlign)
    }
}

private fun androidx.compose.ui.text.TextStyle.withSourceTextAlign(
    textAlign: TextAlign?,
): androidx.compose.ui.text.TextStyle =
    if (textAlign == null) this else copy(textAlign = textAlign)

private fun readerHeadingFontSize(
    defaultFontSize: TextUnit,
    bodyFontSize: TextUnit,
    textScale: Float,
    sourceMultiplier: Float?,
    fallbackMultiplier: Float,
): TextUnit {
    val defaultScaled = defaultFontSize * textScale
    val headingMultiplier = (sourceMultiplier ?: fallbackMultiplier).coerceAtLeast(fallbackMultiplier)
    val sourceScaled = bodyFontSize * textScale * headingMultiplier
    return if (sourceScaled.value > defaultScaled.value) sourceScaled else defaultScaled
}

@Composable
private fun ReaderFootnoteSheet(
    footnote: ReadAlongFootnote,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ReaderFootnoteSheetContent(footnote = footnote)
    }
}

@Composable
private fun ReaderFootnoteSheetContent(
    footnote: ReadAlongFootnote,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonal = LocalTonalSurfaces.current
    val footnoteSelectionColors = remember(colorScheme.primary) {
        TextSelectionColors(
            handleColor = colorScheme.primary,
            backgroundColor = colorScheme.primary.copy(alpha = 0.28f),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Przypis ${footnote.number}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = tonal.floatingPane,
            contentColor = colorScheme.onSurface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.42f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides footnoteSelectionColors) {
                SelectionContainer {
                    Text(
                        text = footnote.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderFootnoteTapTargets(
    displayText: ReaderDisplayText,
    textLayoutResult: TextLayoutResult?,
    touchSize: androidx.compose.ui.unit.Dp,
    density: Density,
    onFootnoteClick: (ReadAlongFootnote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutResult = textLayoutResult ?: return
    if (displayText.inlineFootnotes.isEmpty() || displayText.transformedAnnotatedText.isEmpty()) return
    Box(modifier = modifier) {
        val touchSizePx = with(density) { touchSize.toPx() }
        displayText.inlineFootnotes.forEach { inline ->
            val offset = inline.startOffset.coerceIn(0, displayText.transformedAnnotatedText.lastIndex)
            val bounds = layoutResult.getBoundingBox(offset)
            val x = ((bounds.left + bounds.right) / 2f - touchSizePx / 2f).roundToInt()
            val y = ((bounds.top + bounds.bottom) / 2f - touchSizePx / 2f).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .size(touchSize)
                    .zIndex(1f)
                    .semantics {
                        contentDescription = "Footnote ${inline.footnote.number}"
                    }
                    .clickable(role = Role.Button) {
                        onFootnoteClick(inline.footnote)
                    },
            )
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    readAlong: ReadAlongBook?,
    settings: ReaderSettings,
    canOpenBookDetails: Boolean,
    canOpenContents: Boolean,
    canCastAudio: Boolean,
    castAudioSummary: String,
    currentAudioBookmarkTarget: ReaderBookmarkTarget?,
    selectedBookmarkTarget: ReaderBookmarkTarget?,
    selectedTextPreview: String?,
    sleepTimerSummary: String,
    canOpenSleepTimer: Boolean,
    onDismiss: () -> Unit,
    onOpenBookDetails: () -> Unit,
    onOpenContents: () -> Unit,
    onCastAudio: () -> Unit,
    onBookmarkCurrentAudio: () -> Unit,
    onBookmarkSelectedText: () -> Unit,
    onSleepTimer: () -> Unit,
    onHighlightMode: () -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
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
                .heightIn(max = 720.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Reader options",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column {
	                    ReaderActionRow(
	                        icon = Icons.AutoMirrored.Filled.MenuBook,
	                        title = "Open book details",
	                        summary = "Return to this Wolne Lektury title.",
	                        enabled = canOpenBookDetails,
	                        onClick = onOpenBookDetails,
	                    )
                    ReaderActionRow(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "Chapters",
                        summary = readAlong?.tableOfContents?.size?.let { "$it sections in this reader." }
                            ?: "Chapters are still loading.",
                        enabled = canOpenContents,
                        onClick = onOpenContents,
                    )
	                    ReaderActionRow(
                        icon = Icons.Filled.Cast,
                        title = "Cast audio",
                        summary = castAudioSummary,
                        enabled = canCastAudio,
                        onClick = onCastAudio,
                    )
                    ReaderActionRow(
                        icon = Icons.Filled.Bookmark,
                        title = "Bookmark current audio",
                        summary = currentAudioBookmarkTarget?.let { "At ${formatHmsFromMs(it.positionMs)}" }
                            ?: "Start this book's audio to bookmark the playhead.",
                        enabled = currentAudioBookmarkTarget != null,
                        onClick = onBookmarkCurrentAudio,
                    )
                    ReaderActionRow(
                        icon = Icons.Filled.Bookmark,
                        title = "Bookmark selected text",
                        summary = selectedTextPreview?.take(96)
                            ?: "Select timed text in the reader first.",
                        enabled = selectedBookmarkTarget != null,
                        onClick = onBookmarkSelectedText,
                    )
                    ReaderActionRow(
                        icon = Icons.Filled.Timer,
                        title = "Sleep timer",
                        summary = sleepTimerSummary,
                        enabled = canOpenSleepTimer,
                        onClick = onSleepTimer,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column {
                    ReaderSwitchRow(
                        title = "Source sync",
                        summary = "Keep the current spoken text in view.",
                        checked = settings.followPlayback,
                        onCheckedChange = { checked -> onSettingsChange(settings.copy(followPlayback = checked)) },
                    )
                    ReaderSwitchRow(
                        title = "Highlight current text",
                        summary = if (readAlong?.hasTimedSync == true) {
                            "Show the source text currently being read."
                        } else {
                            "Timed highlight needs source sync."
                        },
                        checked = settings.highlightCurrentText && readAlong?.hasTimedSync == true,
                        enabled = readAlong?.hasTimedSync == true,
                        onCheckedChange = { checked -> onSettingsChange(settings.copy(highlightCurrentText = checked)) },
                    )
                    ReaderActionRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Highlight range",
                        summary = if (settings.highlightCurrentText && readAlong?.hasTimedSync == true) {
                            settings.highlightMode.label
                        } else {
                            "Turn on current text highlight first."
                        },
                        enabled = settings.highlightCurrentText && readAlong?.hasTimedSync == true,
                        onClick = onHighlightMode,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReaderTextSizeControl(
                        textScale = settings.textScale,
                        onTextScaleChange = { onSettingsChange(settings.copy(textScale = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderHighlightModeSheet(
    selectedMode: ReaderHighlightMode,
    onDismiss: () -> Unit,
    onModeSelected: (ReaderHighlightMode) -> Unit,
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
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Highlight range",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column {
                    ReaderHighlightMode.entries.forEach { mode ->
                        val selected = mode == selectedMode
                        ListItem(
                            modifier = Modifier.clickable(role = Role.RadioButton) {
                                onModeSelected(mode)
                            },
                            headlineContent = { Text(mode.label) },
                            supportingContent = { Text(mode.description) },
                            trailingContent = {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onModeSelected(mode) },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

private fun readerSleepTimerSummary(state: SleepTimerState): String =
    when (state) {
        SleepTimerState.Idle -> "Off"
        SleepTimerState.EndOfChapter -> "End of chapter"
        SleepTimerState.Expired -> "Expired"
        is SleepTimerState.Running -> {
            val minutes = (state.remainingMs / 60_000L).coerceAtLeast(0L)
            val seconds = ((state.remainingMs / 1_000L) % 60L).coerceAtLeast(0L)
            "%d:%02d remaining".format(minutes, seconds)
        }
    }

@Composable
private fun ReaderActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ReaderSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button) { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ReaderTextSizeControl(
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
) {
    val selectedIndex = ReaderTextScales
        .indices
        .minBy { index -> kotlin.math.abs(ReaderTextScales[index] - textScale) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.FormatSize, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text("Text size", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${(ReaderTextScales[selectedIndex] * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val index = raw.roundToInt().coerceIn(ReaderTextScales.indices)
                onTextScaleChange(ReaderTextScales[index])
            },
            valueRange = 0f..(ReaderTextScales.lastIndex.toFloat()),
            steps = (ReaderTextScales.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ReaderSpeedSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    var current by remember(state.speed.value) { mutableFloatStateOf(state.speed.value) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Playback speed",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = PlaybackSpeed.clamp(current).label(),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Slider(
                value = current,
                onValueChange = { value ->
                    current = value
                    graph.playerStateRepository.setSpeed(PlaybackSpeed.clamp(value))
                },
                valueRange = PlaybackSpeed.MIN..PlaybackSpeed.MAX,
                steps = 6,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaybackSpeed.Presets.forEach { speed ->
                    FilterChip(
                        selected = PlaybackSpeed.clamp(current).value == speed.value,
                        onClick = {
                            current = speed.value
                            graph.playerStateRepository.setSpeed(speed)
                        },
                        label = { Text(speed.label()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSleepTimerSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
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
                .heightIn(max = 640.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column {
                    SleepTimerPreset.entries.forEach { preset ->
                        ReaderSelectableActionRow(
                            icon = Icons.Filled.Timer,
                            title = preset.label,
                            summary = "Pause after ${preset.label.lowercase()}",
                            selected = (state.sleepTimer as? SleepTimerState.Running)?.totalMs == preset.durationMs,
                            onClick = {
                                graph.playerStateRepository.setSleepTimer(preset.durationMs)
                                onDismiss()
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ReaderSelectableActionRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "End of chapter",
                        summary = "Pause when this chapter finishes.",
                        selected = state.sleepTimer == SleepTimerState.EndOfChapter,
                        onClick = {
                            graph.playerStateRepository.setSleepTimerEndOfChapter()
                            onDismiss()
                        },
                    )
                    ReaderSelectableActionRow(
                        icon = Icons.Filled.Close,
                        title = "Off",
                        summary = "Keep playing until you stop it.",
                        selected = state.sleepTimer is SleepTimerState.Idle ||
                            state.sleepTimer is SleepTimerState.Expired,
                        onClick = {
                            graph.playerStateRepository.cancelSleepTimer()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderSelectableActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected")
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private data class ReaderTextHighlightColors(
    val searchBackground: Color,
    val activeSearchBackground: Color,
    val activeReadBackground: Color,
    val activeReadForeground: Color,
    val footnoteForeground: Color,
)

private data class ReaderInlineFootnote(
    val footnote: ReadAlongFootnote,
    val startOffset: Int,
    val endOffset: Int,
)

private data class ReaderDisplayText(
    val sourceAnnotatedText: AnnotatedString,
    val transformedAnnotatedText: AnnotatedString,
    val originalToTransformedOffsets: IntArray,
    val transformedToOriginalOffsets: IntArray,
    val inlineFootnotes: List<ReaderInlineFootnote>,
) {
    fun visualTransformation(): VisualTransformation =
        VisualTransformation {
            TransformedText(
                text = transformedAnnotatedText,
                offsetMapping = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int =
                        originalToTransformedOffsets[
                            offset.coerceIn(0, originalToTransformedOffsets.lastIndex)
                        ]

                    override fun transformedToOriginal(offset: Int): Int =
                        transformedToOriginalOffsets[
                            offset.coerceIn(0, transformedToOriginalOffsets.lastIndex)
                        ]
                },
            )
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderDisplayText) return false
        return sourceAnnotatedText == other.sourceAnnotatedText &&
            transformedAnnotatedText == other.transformedAnnotatedText &&
            originalToTransformedOffsets.contentEquals(other.originalToTransformedOffsets) &&
            transformedToOriginalOffsets.contentEquals(other.transformedToOriginalOffsets) &&
            inlineFootnotes == other.inlineFootnotes
    }

    override fun hashCode(): Int {
        var result = sourceAnnotatedText.hashCode()
        result = 31 * result + transformedAnnotatedText.hashCode()
        result = 31 * result + originalToTransformedOffsets.contentHashCode()
        result = 31 * result + transformedToOriginalOffsets.contentHashCode()
        result = 31 * result + inlineFootnotes.hashCode()
        return result
    }
}

private fun ReadAlongSegment.displayedReaderText(
    emphasized: Boolean,
    query: String,
    activeReadRange: ReaderTextRange?,
    colors: ReaderTextHighlightColors,
    footnoteFontSize: androidx.compose.ui.unit.TextUnit,
    baseTextStyle: androidx.compose.ui.text.TextStyle,
): ReaderDisplayText {
    val displayBuilder = StringBuilder()
    val transformedToOriginalOffsets = mutableListOf(0)
    val originalToTransformedOffsets = IntArray(text.length + 1)
    val inlineFootnotes = mutableListOf<ReaderInlineFootnote>()
    val footnotesByOffset = footnotes
        .mapNotNull { footnote ->
            footnote.textOffset
                ?.coerceIn(0, text.length)
                ?.let { offset -> offset to footnote }
        }
        .sortedWith(compareBy<Pair<Int, ReadAlongFootnote>> { it.first }.thenBy { it.second.number })
        .groupBy({ it.first }, { it.second })
    var sourceIndex = 0

    fun appendSourceUntil(target: Int) {
        val safeTarget = target.coerceIn(sourceIndex, text.length)
        while (sourceIndex < safeTarget) {
            displayBuilder.append(text[sourceIndex])
            sourceIndex += 1
            transformedToOriginalOffsets += sourceIndex
            originalToTransformedOffsets[sourceIndex] = displayBuilder.length
        }
    }

    fun appendFootnoteMarker(footnote: ReadAlongFootnote) {
        val marker = "[${footnote.number}]"
        val start = displayBuilder.length
        marker.forEach { char ->
            displayBuilder.append(char)
            transformedToOriginalOffsets += sourceIndex
        }
        inlineFootnotes += ReaderInlineFootnote(
            footnote = footnote,
            startOffset = start,
            endOffset = displayBuilder.length,
        )
    }

    footnotesByOffset.forEach { (offset, notes) ->
        appendSourceUntil(offset)
        notes.forEach(::appendFootnoteMarker)
    }
    appendSourceUntil(text.length)

    fun displayOffsetForSource(sourceOffset: Int): Int =
        originalToTransformedOffsets[sourceOffset.coerceIn(0, text.length)]
    val baseSpanStyle = baseTextStyle.toReaderBaseSpanStyle()
    val sourceBaseFontSize = baseTextStyle.fontSize

    val sourceAnnotated = buildAnnotatedString {
        append(text)
        if (text.isNotEmpty()) {
            addStyle(baseSpanStyle, start = 0, end = text.length)
        }
        sourceSpans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start < end) {
                addStyle(
                    style = span.style.toComposeSpanStyle(sourceBaseFontSize),
                    start = start,
                    end = end,
                )
            }
        }
        activeReadRange?.let { range ->
            val start = range.start.coerceIn(0, text.length)
            val end = range.endExclusive.coerceIn(start, text.length)
            if (start < end) {
                addStyle(
                    style = SpanStyle(
                        background = colors.activeReadBackground,
                        color = colors.activeReadForeground,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    start = start,
                    end = end,
                )
            }
        }
        if (query.isNotBlank()) {
            val normalizedText = text.normalizedForReaderSearch()
            val normalizedQuery = query.normalizedForReaderSearch()
            var index = normalizedText.indexOf(normalizedQuery)
            while (index >= 0) {
                val end = (index + normalizedQuery.length).coerceAtMost(text.length)
                if (index < end) {
                    addStyle(
                        style = SpanStyle(
                            background = if (emphasized) colors.activeSearchBackground else colors.searchBackground,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        start = index,
                        end = end,
                    )
                }
                index = normalizedText.indexOf(normalizedQuery, startIndex = (index + normalizedQuery.length).coerceAtLeast(index + 1))
            }
        }
    }

    val transformedAnnotated = buildAnnotatedString {
        append(displayBuilder.toString())
        if (displayBuilder.isNotEmpty()) {
            addStyle(baseSpanStyle, start = 0, end = displayBuilder.length)
        }
        sourceSpans.forEach { span ->
            val start = displayOffsetForSource(span.start)
            val end = displayOffsetForSource(span.end).coerceAtLeast(start)
            if (start < end) {
                addStyle(
                    style = span.style.toComposeSpanStyle(sourceBaseFontSize),
                    start = start,
                    end = end,
                )
            }
        }
        inlineFootnotes.forEach { inline ->
            addStyle(
                style = SpanStyle(
                    color = colors.footnoteForeground,
                    fontWeight = FontWeight.Bold,
                    baselineShift = BaselineShift.Superscript,
                    fontSize = footnoteFontSize,
                ),
                start = inline.startOffset,
                end = inline.endOffset,
            )
        }
        activeReadRange?.let { range ->
            val start = displayOffsetForSource(range.start)
            val end = displayOffsetForSource(range.endExclusive).coerceAtLeast(start)
            if (start < end) {
                addStyle(
                    style = SpanStyle(
                        background = colors.activeReadBackground,
                        color = colors.activeReadForeground,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    start = start,
                    end = end,
                )
            }
        }
        if (query.isNotBlank()) {
            val normalizedText = text.normalizedForReaderSearch()
            val normalizedQuery = query.normalizedForReaderSearch()
            var index = normalizedText.indexOf(normalizedQuery)
            while (index >= 0) {
                val end = (index + normalizedQuery.length).coerceAtMost(text.length)
                val displayStart = displayOffsetForSource(index.coerceAtMost(text.length))
                val displayEnd = displayOffsetForSource(end).coerceAtLeast(displayStart)
                if (displayStart < displayEnd) {
                    addStyle(
                        style = SpanStyle(
                            background = if (emphasized) colors.activeSearchBackground else colors.searchBackground,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        start = displayStart,
                        end = displayEnd,
                    )
                }
                index = normalizedText.indexOf(normalizedQuery, startIndex = (index + normalizedQuery.length).coerceAtLeast(index + 1))
            }
        }
    }
    return ReaderDisplayText(
        sourceAnnotatedText = sourceAnnotated,
        transformedAnnotatedText = transformedAnnotated,
        originalToTransformedOffsets = originalToTransformedOffsets,
        transformedToOriginalOffsets = transformedToOriginalOffsets.toIntArray(),
        inlineFootnotes = inlineFootnotes,
    )
}

private fun SourceTextStyle.toComposeSpanStyle(baseFontSize: TextUnit): SpanStyle {
    var style = SpanStyle(
        fontWeight = fontWeight.toComposeFontWeight(),
        fontStyle = fontStyle.toComposeFontStyle(),
    )
    textSizeMultiplier?.let { multiplier ->
        style = style.copy(fontSize = baseFontSize * multiplier)
    }
    return style
}

private fun androidx.compose.ui.text.TextStyle.toReaderBaseSpanStyle(): SpanStyle =
    SpanStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
    )

private fun SourceFontWeight?.toComposeFontWeight(): FontWeight? =
    when (this) {
        SourceFontWeight.Normal -> FontWeight.Normal
        SourceFontWeight.Bold -> FontWeight.Bold
        null -> null
    }

private fun SourceFontStyle?.toComposeFontStyle(): FontStyle? =
    when (this) {
        SourceFontStyle.Normal -> FontStyle.Normal
        SourceFontStyle.Italic -> FontStyle.Italic
        null -> null
    }

private fun SourceTextAlign?.toComposeTextAlign(): TextAlign? =
    when (this) {
        SourceTextAlign.Start -> TextAlign.Start
        SourceTextAlign.Center -> TextAlign.Center
        SourceTextAlign.End -> TextAlign.End
        SourceTextAlign.Justify -> TextAlign.Justify
        null -> null
    }

private fun readerHighlightRoleForTextBlock(
    textBlock: ReaderItem.TextBlock,
    activeSegmentId: String?,
    settings: ReaderSettings,
): ReaderSegmentHighlightRole =
    if (textBlock.segments.size == 1 && activeSegmentId != null && textBlock.containsSegment(activeSegmentId)) {
        readerHighlightRoleForSegment(
            segmentId = activeSegmentId,
            activeSegmentId = activeSegmentId,
            settings = settings,
        )
    } else {
        ReaderSegmentHighlightRole.None
    }

private fun activeReaderTextRangeForTextBlock(
    textBlock: ReaderItem.TextBlock,
    activeSegmentId: String?,
    activePositionMs: Long,
    settings: ReaderSettings,
): ReaderTextRange? =
    textBlock.activeTextRange(
        activeSegmentId = activeSegmentId,
        activePositionMs = activePositionMs,
        settings = settings,
    )

@Composable
private fun FindAllSheet(
    hits: List<ReadAlongSearchHit>,
    query: String,
    onDismiss: () -> Unit,
    onHitClick: (ReadAlongSearchHit) -> Unit,
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
                .heightIn(max = 640.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "${hits.size} matches",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            LazyColumn {
                itemsIndexed(hits, key = { _, hit -> hit.id }) { _, hit ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                            .clickable { onHitClick(hit) },
                        leadingContent = {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        },
                        headlineContent = {
                            Text(
                                text = hit.locationLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Text(
                                hit.highlightedSentence(
                                    query = query,
                                    highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        tonalElevation = 0.dp,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        TextButton(onClick = { onHitClick(hit) }) {
                            Text("Jump to section")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun ReadAlongSearchHit.locationLabel(): String =
    buildString {
        append("Section ")
        append(displayIndex)
        timestampMs?.let {
            append(" • ")
            append(formatHmsFromMs(it))
        }
    }

private fun ReadAlongSearchHit.highlightedSentence(
    query: String,
    highlightColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(sentence)
        val safeStart = matchStart.coerceIn(0, sentence.length)
        val safeEnd = matchEnd.coerceIn(safeStart, sentence.length)
        if (safeStart < safeEnd || query.isNotBlank()) {
            addStyle(
                SpanStyle(
                    background = highlightColor.copy(alpha = 0.72f),
                    fontWeight = FontWeight.SemiBold,
                ),
                safeStart,
                safeEnd,
            )
        }
    }

@Composable
private fun ReaderLoading(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReaderUnavailable(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal sealed interface ReaderItem {
    val key: String

    data class PageBreak(
        val pageNumber: Int,
        val pageCount: Int,
        val anchorKey: String,
    ) : ReaderItem {
        override val key: String = "page:$pageNumber:$anchorKey"
    }

    data class ChapterHeader(val chapter: ReadAlongChapter) : ReaderItem {
        override val key: String = "chapter:${chapter.chapterId}"
    }

    data class TableOfContents(val items: List<ReadAlongTocItem>) : ReaderItem {
        override val key: String = "toc"
    }

    data class TextBlock(
        val chapter: ReadAlongChapter,
        val segments: List<ReadAlongSegment>,
    ) : ReaderItem {
        private val ranges: List<ReaderTextBlockSegmentRange>
        val displaySegment: ReadAlongSegment
        val segmentIds: Set<String> = segments.mapTo(mutableSetOf()) { segment -> segment.id }
        val firstTimedPositionMs: Long? = segments.firstNotNullOfOrNull { segment -> segment.clipBeginMs }

        override val key: String = "text-block:${segments.firstOrNull()?.id.orEmpty()}"

        init {
            require(segments.isNotEmpty()) { "Reader text blocks must include at least one segment." }
            val blockText = StringBuilder()
            val textRanges = mutableListOf<ReaderTextBlockSegmentRange>()
            val sourceSpans = mutableListOf<com.librivox.mobile.readalong.ReadAlongTextSpan>()
            val footnotes = mutableListOf<ReadAlongFootnote>()
            segments.forEach { segment ->
                if (blockText.isNotEmpty()) {
                    blockText.appendReaderSegmentSeparator(segment.text)
                }
                val start = blockText.length
                blockText.append(segment.text)
                val end = blockText.length
                textRanges += ReaderTextBlockSegmentRange(
                    segment = segment,
                    start = start,
                    endExclusive = end,
                )
                segment.sourceSpans.forEach { span ->
                    sourceSpans += span.copy(
                        start = (span.start + start).coerceIn(start, end),
                        end = (span.end + start).coerceIn(start, end),
                    )
                }
                segment.footnotes.forEach { footnote ->
                    footnotes += footnote.copy(textOffset = footnote.textOffset?.plus(start))
                }
            }
            ranges = textRanges
            displaySegment = segments.first().copy(
                id = key,
                text = blockText.toString(),
                clipBeginMs = firstTimedPositionMs,
                clipEndMs = segments.asReversed().firstOrNull { segment -> segment.clipEndMs != null }?.clipEndMs,
                sourceSpans = sourceSpans,
                footnotes = footnotes,
            )
        }

        fun containsSegment(segmentId: String): Boolean =
            segmentIds.contains(segmentId)

        fun activeTextRange(
            activeSegmentId: String?,
            activePositionMs: Long,
            settings: ReaderSettings,
        ): ReaderTextRange? {
            if (!settings.highlightCurrentText) return null
            val activeRange = ranges.firstOrNull { range -> range.segment.id == activeSegmentId }
                ?: return null
            if (settings.highlightMode == ReaderHighlightMode.SourceSegment) {
                return ReaderTextRange(
                    start = activeRange.start,
                    endExclusive = activeRange.endExclusive,
                )
            }
            return activeReaderTextRangeForSegment(
                segment = activeRange.segment,
                activeSegmentId = activeSegmentId,
                activePositionMs = activePositionMs,
                settings = settings,
            )?.let { range ->
                ReaderTextRange(
                    start = range.start + activeRange.start,
                    endExclusive = range.endExclusive + activeRange.start,
                )
            }
        }

        fun selection(selectionStart: Int, selectionEnd: Int): ReaderSelection? {
            val start = selectionStart.coerceIn(0, displaySegment.text.length)
            val end = selectionEnd.coerceIn(0, displaySegment.text.length)
            val selectedText = displaySegment.text
                .substring(start.coerceAtMost(end), start.coerceAtLeast(end))
                .trim()
            if (selectedText.isBlank()) return null
            val selectionStartOffset = start.coerceAtMost(end)
            val selectedSegment = ranges.firstOrNull { range ->
                range.endExclusive > selectionStartOffset
            }?.segment ?: segments.first()
            return ReaderSelection(
                segmentId = selectedSegment.id,
                chapterId = selectedSegment.chapterId,
                clipBeginMs = selectedSegment.clipBeginMs,
                selectedText = selectedText,
            )
        }
    }
}

private data class ReaderTextBlockSegmentRange(
    val segment: ReadAlongSegment,
    val start: Int,
    val endExclusive: Int,
)

private fun ReadAlongChapter.readerTextBlocks(): List<ReaderItem.TextBlock> =
    buildList {
        val pendingSegments = mutableListOf<ReadAlongSegment>()
        var pendingKey: String? = null

        fun flush() {
            if (pendingSegments.isNotEmpty()) {
                add(ReaderItem.TextBlock(this@readerTextBlocks, pendingSegments.toList()))
                pendingSegments.clear()
                pendingKey = null
            }
        }

        segments.forEach { segment ->
            val groupingKey = segment.readerTextBlockGroupingKey()
            if (groupingKey != null && groupingKey == pendingKey) {
                pendingSegments += segment
            } else {
                flush()
                pendingSegments += segment
                pendingKey = groupingKey
            }
            if (groupingKey == null) {
                flush()
            }
        }
        flush()
    }

private fun ReadAlongSegment.readerTextBlockGroupingKey(): String? =
    textBlockRef
        ?.takeIf { role == ReadAlongSegmentRole.Paragraph && it.isNotBlank() }
        ?.let { blockRef -> "$chapterId:$blockRef" }

private fun ReadAlongSegmentRole.isReaderHeading(): Boolean =
    when (this) {
        ReadAlongSegmentRole.Heading1,
        ReadAlongSegmentRole.Heading2,
        ReadAlongSegmentRole.Heading3 -> true
        ReadAlongSegmentRole.Paragraph,
        ReadAlongSegmentRole.Quote,
        ReadAlongSegmentRole.Verse -> false
    }

private fun StringBuilder.appendReaderSegmentSeparator(nextText: String) {
    if (isEmpty()) return
    val next = nextText.firstOrNull() ?: return
    val previous = last()
    if (
        previous.isWhitespace() ||
        next.isWhitespace() ||
        next in setOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '”', '»')
    ) {
        return
    }
    append(' ')
}

internal fun ReadAlongBook.readerItems(): List<ReaderItem> =
    buildList {
        val pageCount = estimatedReaderPageCount()
        var pageNumber = 1
        var nextPageStart = 0
        var consumedChars = 0
        fun addPageBreak(anchorKey: String) {
            add(
                ReaderItem.PageBreak(
                    pageNumber = pageNumber,
                    pageCount = pageCount,
                    anchorKey = anchorKey,
                ),
            )
            pageNumber += 1
            nextPageStart += ReaderEstimatedCharsPerPage
        }
        if (tableOfContents.isNotEmpty()) {
            add(ReaderItem.TableOfContents(tableOfContents))
        }
        addPageBreak("start")
        chapters.forEach { chapter ->
            while (pageNumber <= pageCount && consumedChars >= nextPageStart) {
                addPageBreak("chapter:${chapter.chapterId}")
            }
            if (chapter.shouldShowReaderChapterHeader(this@readerItems)) {
                add(ReaderItem.ChapterHeader(chapter))
            }
            chapter.readerTextBlocks().forEach { textBlock ->
                while (pageNumber <= pageCount && consumedChars >= nextPageStart) {
                    addPageBreak(textBlock.key)
                }
                add(textBlock)
                consumedChars += textBlock.displaySegment.text.readerPageCharCount()
            }
        }
    }

internal data class ReaderTocJumpIndexes(
    val itemIndexBySegment: Map<String, Int>,
    val itemIndexByChapter: Map<String, Int>,
    val itemIndexByChapterTitle: Map<String, Int>,
    val itemIndexByTextRef: Map<String, Int>,
    val itemIndexByTextPath: Map<String, Int>,
    val items: List<ReaderItem>,
    val leadingReaderItemCount: Int,
)

internal fun List<ReaderItem>.readerTocJumpIndexes(
    leadingReaderItemCount: Int,
): ReaderTocJumpIndexes =
    ReaderTocJumpIndexes(
        itemIndexBySegment = mapIndexedNotNull { index, item ->
            val textBlock = item as? ReaderItem.TextBlock ?: return@mapIndexedNotNull null
            textBlock.segments.map { segment ->
                segment.id to readerLazyListIndex(index, leadingReaderItemCount)
            }
        }.flatten().toMap(),
        itemIndexByChapter = buildMap {
            this@readerTocJumpIndexes.forEachIndexed { index, item ->
                when (item) {
                    is ReaderItem.ChapterHeader ->
                        put(item.chapter.chapterId, readerLazyListIndex(index, leadingReaderItemCount))
                    is ReaderItem.TextBlock -> if (!containsKey(item.chapter.chapterId)) {
                        put(item.chapter.chapterId, readerLazyListIndex(index, leadingReaderItemCount))
                    }
                    is ReaderItem.PageBreak,
                    is ReaderItem.TableOfContents -> Unit
                }
            }
        },
        itemIndexByChapterTitle = buildMap {
            this@readerTocJumpIndexes.forEachIndexed { index, item ->
                val listIndex = readerLazyListIndex(index, leadingReaderItemCount)
                when (item) {
                    is ReaderItem.ChapterHeader ->
                        putIfAbsent(item.chapter.title.readerTocTitleKey(), listIndex)
                    is ReaderItem.TextBlock -> item.segments.forEach { segment ->
                        if (segment.role != ReadAlongSegmentRole.Paragraph) {
                            putIfAbsent(segment.text.readerTocTitleKey(), listIndex)
                        }
                    }
                    is ReaderItem.PageBreak,
                    is ReaderItem.TableOfContents -> Unit
                }
            }
        },
        itemIndexByTextRef = buildMap {
            this@readerTocJumpIndexes.forEachIndexed { index, item ->
                val textBlock = item as? ReaderItem.TextBlock ?: return@forEachIndexed
                textBlock.segments.forEach { segment ->
                    putIfAbsent(segment.textRef, readerLazyListIndex(index, leadingReaderItemCount))
                }
            }
        },
        itemIndexByTextPath = buildMap {
            this@readerTocJumpIndexes.forEachIndexed { index, item ->
                val textBlock = item as? ReaderItem.TextBlock ?: return@forEachIndexed
                textBlock.segments.forEach { segment ->
                    val path = segment.textRef.readerTextPathKey()
                    if (path.isNotBlank()) {
                        putIfAbsent(path, readerLazyListIndex(index, leadingReaderItemCount))
                    }
                }
            }
        },
        items = this,
        leadingReaderItemCount = leadingReaderItemCount,
    )

internal fun ReadAlongTocItem.readerTocJumpListIndex(indexes: ReaderTocJumpIndexes): Int? =
    segmentId?.let { indexes.itemIndexBySegment[it] }
        ?: chapterId?.let { indexes.itemIndexByChapter[it] }
        ?: readerHrefListIndex(indexes.itemIndexByTextRef, indexes.itemIndexByTextPath)
        ?: readerTitleFallbackListIndex(indexes.itemIndexByChapterTitle)
        ?: readerFallbackListIndex(indexes.items, indexes.leadingReaderItemCount)

internal fun ReadAlongBook.readerChapterPickerItems(): List<ReadAlongTocItem> {
    val chapterItems = tableOfContents
        .filter { item -> item.chapterId != null && !item.title.isReaderSupplementalTocTitle() }
    return chapterItems.ifEmpty { tableOfContents }
}

private fun ReadAlongTocItem.readerFallbackListIndex(
    items: List<ReaderItem>,
    leadingReaderItemCount: Int,
): Int? {
    val fileName = href
        ?.substringBefore('#')
        ?.substringAfterLast('/')
        ?.lowercase()
        ?: return null
    return when (fileName) {
        "cover.xhtml",
        "title.xhtml" -> 0
        "nav.xhtml" -> items.indexOfFirst { item -> item is ReaderItem.TableOfContents }
            .takeIf { it >= 0 }
            ?.let { index -> readerLazyListIndex(index, leadingReaderItemCount) }
        "annotations.xhtml" -> items.indexOfFirst { item ->
            item is ReaderItem.TextBlock && item.segments.any { segment -> segment.footnotes.isNotEmpty() }
        }
            .takeIf { it >= 0 }
            ?.let { index -> readerLazyListIndex(index, leadingReaderItemCount) }
            ?: items.lastReaderContentListIndex(leadingReaderItemCount)
        "support.xhtml",
        "last.xhtml" -> items.lastReaderContentListIndex(leadingReaderItemCount)
        else -> if (Regex("""^fund\d+\.xhtml$""").matches(fileName)) {
            items.lastReaderContentListIndex(leadingReaderItemCount)
        } else {
            null
        }
    }
}

private fun ReadAlongTocItem.readerHrefListIndex(
    itemIndexByTextRef: Map<String, Int>,
    itemIndexByTextPath: Map<String, Int>,
): Int? {
    val href = href?.takeIf { it.isNotBlank() } ?: return null
    return itemIndexByTextRef[href]
        ?: itemIndexByTextPath[href.readerTextPathKey()]
}

private fun ReadAlongTocItem.readerTitleFallbackListIndex(
    itemIndexByChapterTitle: Map<String, Int>,
): Int? {
    val key = title.readerTocTitleKey()
    if (key.isBlank()) return null
    return itemIndexByChapterTitle[key]
        ?: itemIndexByChapterTitle.entries
            .firstOrNull { (chapterKey, _) ->
                chapterKey.length >= 5 &&
                    key.length >= 5 &&
                    (chapterKey.contains(key) || key.contains(chapterKey))
            }
            ?.value
}

private fun String.readerTocTitleKey(): String =
    normalizedForReaderSearch()
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun String.isReaderSupplementalTocTitle(): Boolean =
    when (readerTocTitleKey()) {
        "strona tytulowa",
        "spis tresci",
        "poczatek utworu",
        "przypisy",
        "wesprzyj wolne lektury",
        "strona redakcyjna" -> true
        else -> false
    }

private fun String.readerTextPathKey(): String =
    substringBefore('#')

private fun List<ReaderItem>.lastReaderContentListIndex(leadingReaderItemCount: Int): Int? =
    indexOfLast { item -> item is ReaderItem.TextBlock }
        .takeIf { it >= 0 }
        ?.let { index -> readerLazyListIndex(index, leadingReaderItemCount) }
        ?: indexOfLast { item -> item is ReaderItem.ChapterHeader }
            .takeIf { it >= 0 }
            ?.let { index -> readerLazyListIndex(index, leadingReaderItemCount) }

private fun ReadAlongBook.estimatedReaderPageCount(): Int =
    kotlin.math.ceil(
        chapters.sumOf { chapter ->
            chapter.segments.sumOf { segment -> segment.text.readerPageCharCount() }
        }.coerceAtLeast(1).toDouble() / ReaderEstimatedCharsPerPage.toDouble(),
    ).roundToInt().coerceAtLeast(1)

private fun ReadAlongChapter.shouldShowReaderChapterHeader(book: ReadAlongBook): Boolean {
    val firstSegment = segments.firstOrNull() ?: return true
    val firstSegmentIsHeading = when (firstSegment.role) {
        ReadAlongSegmentRole.Heading1,
        ReadAlongSegmentRole.Heading2,
        ReadAlongSegmentRole.Heading3 -> true
        ReadAlongSegmentRole.Paragraph,
        ReadAlongSegmentRole.Quote,
        ReadAlongSegmentRole.Verse -> false
    }
    if (firstSegmentIsHeading && sourceHeadingsMatchChapterTitle()) {
        return false
    }
    if (leadingShortSegmentsMatchChapterTitle()) {
        return false
    }
    if (book.hasTimedSync) {
        val sourceTocItems = book.tableOfContents.filterNot { item -> item.id.startsWith("chapter:") }
        if (sourceTocItems.isNotEmpty()) {
            return sourceTocItems.any { item -> item.matchesReaderChapterHeader(this) }
        }
        return true
    }
    if (!firstSegmentIsHeading) return true
    return !sourceHeadingsMatchChapterTitle()
}

private fun ReadAlongChapter.sourceHeadingsMatchChapterTitle(): Boolean {
    val chapterTitle = title.readerHeaderKey()
    val sourceHeadingTitle = leadingReaderHeadingTitle().readerHeaderKey()
    return sourceHeadingTitle.isNotBlank() &&
        (
            chapterTitle == sourceHeadingTitle ||
                chapterTitle.contains(sourceHeadingTitle) ||
                sourceHeadingTitle.contains(chapterTitle)
            )
}

private fun ReadAlongChapter.leadingShortSegmentsMatchChapterTitle(): Boolean {
    val chapterTitle = title.readerHeaderKey()
    if (chapterTitle.isBlank()) return false
    val candidate = StringBuilder()
    segments.take(4).forEach { segment ->
        val text = segment.text.trim().trimEnd('.', ';', ':')
        if (text.isBlank() || text.length > 96) return false
        if (candidate.isNotEmpty()) {
            candidate.append(". ")
        }
        candidate.append(text)
        val candidateKey = candidate.toString().readerHeaderKey()
        if (candidateKey == chapterTitle || candidateKey.contains(chapterTitle) || chapterTitle.contains(candidateKey)) {
            return true
        }
        if (!chapterTitle.contains(candidateKey)) {
            return false
        }
    }
    return false
}

private fun ReadAlongChapter.leadingReaderHeadingTitle(): String =
    segments
        .takeWhile { segment ->
            when (segment.role) {
                ReadAlongSegmentRole.Heading1,
                ReadAlongSegmentRole.Heading2,
                ReadAlongSegmentRole.Heading3 -> true
                ReadAlongSegmentRole.Paragraph,
                ReadAlongSegmentRole.Quote,
                ReadAlongSegmentRole.Verse -> false
            }
        }
        .joinToString(". ") { segment -> segment.text.trim().trimEnd('.', ';', ':') }

private fun ReadAlongTocItem.matchesReaderChapterHeader(chapter: ReadAlongChapter): Boolean {
    if (chapterId != chapter.chapterId) return false
    val tocTitle = title.readerHeaderKey()
    val chapterTitle = chapter.title.readerHeaderKey()
    return tocTitle == chapterTitle ||
        (
            tocTitle.length >= 5 &&
                chapterTitle.length >= 5 &&
                (tocTitle.contains(chapterTitle) || chapterTitle.contains(tocTitle))
            )
}

private fun String.readerHeaderKey(): String =
    replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase()

private fun String.readerPageCharCount(): Int =
    replace(Regex("""\s+"""), " ")
        .trim()
        .length

private fun Int.readerPageLabel(pageCount: Int): String =
    toString().padStart(pageCount.toString().length.coerceAtLeast(2), '0')

private fun estimatedReaderListItemHeights(
    items: List<ReaderItem>,
    leadingReaderItemCount: Int,
    searchActive: Boolean,
    textScale: Float,
    density: Density,
): Map<Int, Int> = with(density) {
    val itemSpacingPx = 10.dp.roundToPx()
    fun withItemSpacing(heightPx: Int): Int = heightPx + itemSpacingPx
    buildMap {
        if (leadingReaderItemCount > 0) {
            put(0, withItemSpacing(170.dp.roundToPx()))
        }
        if (searchActive && leadingReaderItemCount > 1) {
            put(1, withItemSpacing(132.dp.roundToPx()))
        }
        items.forEachIndexed { index, item ->
            val listIndex = readerLazyListIndex(index, leadingReaderItemCount)
            val estimatedHeight = when (item) {
                is ReaderItem.PageBreak -> 42.dp.roundToPx()
                is ReaderItem.TableOfContents -> 56.dp.roundToPx()
                is ReaderItem.ChapterHeader -> (56f * textScale).dp.roundToPx()
                is ReaderItem.TextBlock -> {
                    val charsPerLine = (44f / textScale).roundToInt().coerceAtLeast(20)
                    val lineCount = ((item.displaySegment.text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1)
                    val lineHeightPx = (25f * textScale).dp.roundToPx()
                    val verticalPaddingPx = 20.dp.roundToPx()
                    (lineCount * lineHeightPx + verticalPaddingPx).coerceAtLeast(48.dp.roundToPx())
                }
            }
            put(listIndex, withItemSpacing(estimatedHeight))
        }
    }
}
