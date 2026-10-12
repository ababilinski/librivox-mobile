@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.download.DownloadUndoToken
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.localAudioEpubFile
import com.librivox.mobile.model.numberedTitle
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.shareAudiobook
import com.librivox.mobile.ui.components.shareAudiobookChapter
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onOpenBook: (String) -> Unit,
    onBack: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val tonal = LocalTonalSurfaces.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val library = libraryState ?: return
    var selectedKeys by remember { mutableStateOf<Set<DownloadedChapterKey>>(emptySet()) }
    var expandedBookIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    val inProgress = remember(library) {
        library.books.flatMap { book ->
            book.chapters
                .filter {
                    it.downloadState == DownloadState.Queued ||
                        it.downloadState == DownloadState.Downloading
                }
                .map { book to it }
        }
    }
    val downloaded = remember(library) {
        library.books.flatMap { book ->
            book.chapters.mapNotNull { chapter ->
                val localFileName = chapter.localFileName ?: return@mapNotNull null
                val file = File(graph.app.filesDir, localFileName)
                DownloadedChapterItem(
                    key = DownloadedChapterKey(book.id, chapter.id),
                    book = book,
                    chapter = chapter,
                    bytes = file.takeIf(File::isFile)?.length() ?: 0L,
                )
            }
        }.sortedWith(
            compareBy<DownloadedChapterItem> { it.book.title.lowercase() }
                .thenBy { it.chapter.number }
                .thenBy { it.chapter.title.lowercase() },
        )
    }
    val downloadedByKey = remember(downloaded) { downloaded.associateBy { it.key } }
    val downloadedBookGroups = remember(downloaded, library) {
        val chaptersByBook = downloaded.groupBy { it.book.id }
        library.books.mapNotNull { book ->
            val items = chaptersByBook[book.id].orEmpty()
            val audioEpubBytes = book.localAudioEpubFile(graph.app.filesDir)
                ?.takeIf(File::isFile)
                ?.length()
                ?: 0L
            if (items.isEmpty() && audioEpubBytes <= 0L) {
                null
            } else {
                DownloadedBookGroup(
                    book = items.firstOrNull()?.book ?: book,
                    chapters = items,
                    audioEpubBytes = audioEpubBytes,
                )
            }
        }
            .sortedBy { it.book.title.lowercase() }
    }
    val totalBytes = remember(downloadedBookGroups) { downloadedBookGroups.sumOf { it.bytes } }
    val selectionMode = selectedKeys.isNotEmpty()

    LaunchedEffect(downloadedByKey) {
        selectedKeys = selectedKeys.filterTo(mutableSetOf()) { it in downloadedByKey }
    }
    LaunchedEffect(downloadedBookGroups) {
        val bookIds = downloadedBookGroups.mapTo(mutableSetOf()) { it.book.id }
        expandedBookIds = expandedBookIds.filterTo(mutableSetOf()) { it in bookIds }
    }

    fun runStagedDelete(items: List<DownloadedChapterItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val tokens = items
                .groupBy { it.book }
                .mapNotNull { (book, bookItems) ->
                    graph.app.downloadManager.stageDeleteDownloads(
                        book = book,
                        chapters = bookItems.map { it.chapter },
                    ).takeIf { it.items.isNotEmpty() }
                }
            selectedKeys = emptySet()
            if (tokens.isEmpty()) return@launch
            val token = DownloadUndoToken(tokens.flatMap { it.items })
            val count = items.size
            val result = snackbarHostState.showSnackbar(
                message = if (count == 1) "Download removed" else "$count downloads removed",
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                graph.app.downloadManager.restoreStagedDownloads(token)
            } else {
                graph.app.downloadManager.discardStagedDownloads(token)
            }
        }
    }

    fun runDeleteBookDownloads(group: DownloadedBookGroup) {
        scope.launch {
            graph.app.downloadManager.deleteDownloads(group.book)
            selectedKeys = selectedKeys - group.chapters.mapTo(mutableSetOf()) { it.key }
            snackbarHostState.showSnackbar(
                message = "Downloads deleted",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        if (selectionMode) {
                            "${selectedKeys.size} selected"
                        } else {
                            "Downloads"
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectionMode) selectedKeys = emptySet() else onBack()
                        },
                    ) {
                        Icon(
                            imageVector = if (selectionMode) {
                                Icons.Filled.Close
                            } else {
                                Icons.AutoMirrored.Filled.ArrowBack
                            },
                            contentDescription = if (selectionMode) "Clear selection" else "Back",
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                runStagedDelete(selectedKeys.mapNotNull { downloadedByKey[it] })
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    } else if (totalBytes > 0L) {
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("${formatStorageBytes(totalBytes)} used") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (inProgress.isEmpty() && downloaded.isEmpty()) {
            EmptyDownloads(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            if (inProgress.isNotEmpty()) {
                item {
                    SectionHeader(text = "In progress (${inProgress.size})")
                }
                items(
                    items = inProgress,
                    key = { (b, c) -> "progress:${b.id}:${c.id}" },
                ) { (book, chapter) ->
                    InProgressRow(
                        book = book,
                        chapter = chapter,
                        onCancel = { graph.app.downloadManager.cancelDownload(book, chapter) },
                        onOpen = { onOpenBook(book.id) },
                        onShare = { shareAudiobookChapter(context, book, chapter) },
                    )
                    HorizontalDivider()
                }
            }
            if (downloadedBookGroups.isNotEmpty()) {
                item {
                    SectionHeader(text = "Downloaded books (${downloadedBookGroups.size})")
                }
                downloadedBookGroups.forEach { group ->
                    item(key = "book:${group.book.id}") {
                        val expanded = group.book.id in expandedBookIds
                        val groupKeys = group.chapters.mapTo(mutableSetOf()) { it.key }
                        val fullySelected = groupKeys.isNotEmpty() && groupKeys.all { it in selectedKeys }
                        val partiallySelected = groupKeys.any { it in selectedKeys }
                        DownloadedBookRow(
                            group = group,
                            expanded = expanded,
                            selected = fullySelected,
                            partiallySelected = partiallySelected,
                            selectionMode = selectionMode,
                            onToggleExpanded = {
                                expandedBookIds = if (expanded) {
                                    expandedBookIds - group.book.id
                                } else {
                                    expandedBookIds + group.book.id
                                }
                            },
                            onToggleSelected = {
                                selectedKeys = if (fullySelected) {
                                    selectedKeys - groupKeys
                                } else {
                                    selectedKeys + groupKeys
                                }
                            },
                            onStartSelection = { selectedKeys = selectedKeys + groupKeys },
                            onDelete = {
                                if (group.audioEpubBytes > 0L) {
                                    runDeleteBookDownloads(group)
                                } else {
                                    runStagedDelete(group.chapters)
                                }
                            },
                            onShare = { shareAudiobook(context, group.book) },
                        )
                        HorizontalDivider()
                    }
                    if (group.book.id in expandedBookIds) {
                        items(
                            items = group.chapters,
                            key = { "chapter:${it.key.stableKey}" },
                        ) { item ->
                            val selected = item.key in selectedKeys
                            DownloadedChapterRow(
                                item = item,
                                selected = selected,
                                selectionMode = selectionMode,
                                onOpen = { onOpenBook(item.book.id) },
                                onToggleSelected = {
                                    selectedKeys = if (selected) {
                                        selectedKeys - item.key
                                    } else {
                                        selectedKeys + item.key
                                    }
                                },
                                onStartSelection = { selectedKeys = selectedKeys + item.key },
                                onDelete = { runStagedDelete(listOf(item)) },
                                onShare = { shareAudiobookChapter(context, item.book, item.chapter) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Saved chapters will appear here with their storage use.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InProgressRow(
    book: AudioBook,
    chapter: AudioBookChapter,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        leadingContent = {
            BookCover(book = book, modifier = Modifier.size(48.dp), cornerRadius = 10)
        },
        headlineContent = {
            Text(
                text = chapter.numberedTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share chapter")
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = tonal.listItem),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadedBookRow(
    group: DownloadedBookGroup,
    expanded: Boolean,
    selected: Boolean,
    partiallySelected: Boolean,
    selectionMode: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.settledValue, selectionMode) {
        if (!selectionMode && dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = { DeleteSwipeBackground() },
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelected() else onToggleExpanded()
                    },
                    onLongClick = onStartSelection,
                ),
            leadingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                    }
                    BookCover(book = group.book, modifier = Modifier.size(56.dp), cornerRadius = 12)
                }
            },
            headlineContent = {
                Text(
                    text = group.book.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected || partiallySelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            supportingContent = {
                Text(
                    text = downloadBookSummary(group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                if (selectionMode) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = null,
                        tint = if (selected || partiallySelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = "Share audiobook")
                        }
                        IconButton(onClick = onToggleExpanded) {
                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Filled.KeyboardArrowUp
                                } else {
                                    Icons.Filled.KeyboardArrowDown
                                },
                                contentDescription = if (expanded) "Hide chapters" else "Show chapters",
                                tint = if (selected || partiallySelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = tonal.listItem),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadedChapterRow(
    item: DownloadedChapterItem,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.settledValue, selectionMode) {
        if (!selectionMode && dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = { DeleteSwipeBackground() },
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelected() else onOpen()
                    },
                    onLongClick = onStartSelection,
                )
                .padding(start = 16.dp),
            leadingContent = {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                } else {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            headlineContent = {
                Text(
                    text = item.chapter.numberedTitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Text(
                    text = formatStorageBytes(item.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                if (selectionMode) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = "Share chapter")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = tonal.listItem),
        )
    }
}

@Composable
private fun DeleteSwipeBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Delete",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private data class DownloadedBookGroup(
    val book: AudioBook,
    val chapters: List<DownloadedChapterItem>,
    val audioEpubBytes: Long = 0L,
) {
    val bytes: Long = chapters.sumOf { it.bytes } + audioEpubBytes
}

private data class DownloadedChapterKey(
    val bookId: String,
    val chapterId: String,
) {
    val stableKey: String = "$bookId::$chapterId"
}

private data class DownloadedChapterItem(
    val key: DownloadedChapterKey,
    val book: AudioBook,
    val chapter: AudioBookChapter,
    val bytes: Long,
)

private fun downloadBookSummary(group: DownloadedBookGroup): String =
    buildString {
        append(formatStorageBytes(group.bytes))
        if (group.chapters.isNotEmpty()) {
            append(" • ")
            append(group.chapters.size)
            append(" ")
            append(chapterCountLabel(group.chapters.size))
        }
        if (group.audioEpubBytes > 0L) {
            append(" • text sync")
        }
    }

private fun chapterCountLabel(count: Int): String =
    if (count == 1) "chapter" else "chapters"

private fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "Size unavailable"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}
