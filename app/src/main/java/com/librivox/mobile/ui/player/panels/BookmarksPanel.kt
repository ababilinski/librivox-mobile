@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.player.panels

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.player.bookmarks.buildBookmarkPanelSections
import com.librivox.mobile.ui.player.bookmarks.nearestBookmarkPositionAtPlayhead
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onContentAtTopChanged: (Boolean) -> Unit = {},
    headerDragModifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val playerState by graph.playerStateRepository.state.collectAsState()
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val bookmarks by graph.app.bookmarkRepository.bookmarks.collectAsState(initial = emptyList())
    val book = remember(libraryState, playerState.bookId) {
        libraryState?.bookById(playerState.bookId)
    }
    val sections = remember(book, bookmarks, playerState.bookId, playerState.chapterId) {
        book?.let {
            buildBookmarkPanelSections(
                book = it,
                bookmarks = bookmarks,
                activeBookId = playerState.bookId,
                activeChapterId = playerState.chapterId,
            )
        }
    }
    val bookmarkCount = sections?.let { section ->
        (section.currentChapter?.bookmarks.orEmpty() + section.remainingChapters.flatMap { it.bookmarks }).size
    } ?: 0
    val listState = rememberLazyListState()
    val latestOnContentAtTopChanged by rememberUpdatedState(onContentAtTopChanged)
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var actionBookmark by remember { mutableStateOf<Bookmark?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun playFromBookmark(bookmark: Bookmark) {
        val activeBook = book ?: return
        val chapterIndex = activeBook.chapters.indexOfFirst { it.id == bookmark.chapterId }
        val mediaItems = activeBook.toMediaItems(context)
        if (mediaItems.isNotEmpty() && chapterIndex >= 0) {
            graph.playerStateRepository.playMediaItems(
                items = mediaItems,
                startIndex = chapterIndex,
                startPositionMs = bookmark.positionMs,
            )
        } else {
            graph.playerStateRepository.seekTo(bookmark.positionMs)
        }
        onClose()
    }

    fun removeBookmark(bookmark: Bookmark) {
        haptics.confirm()
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

    fun createBookmarkAtPlayhead() {
        val bookId = playerState.bookId?.takeIf { it.isNotBlank() } ?: return
        val chapterId = playerState.chapterId?.takeIf { it.isNotBlank() } ?: return
        val chapterBookmarks = bookmarks
            .filter { it.bookId == bookId && it.chapterId == chapterId }
            .sortedBy { it.positionMs }
        val nearbyPosition = nearestBookmarkPositionAtPlayhead(
            positionMs = playerState.positionMs,
            bookmarkPositionsMs = chapterBookmarks.map { it.positionMs },
        )
        val nearbyBookmark = nearbyPosition?.let { position ->
            chapterBookmarks.firstOrNull { it.positionMs == position }
        }
        if (nearbyBookmark != null) {
            haptics.tick()
            editingBookmark = nearbyBookmark
            scope.launch {
                snackbarHostState.showSnackbar("Bookmark already exists near here")
            }
            return
        }
        haptics.confirm()
        scope.launch {
            val bookmark = graph.app.bookmarkRepository.add(bookId, chapterId, playerState.positionMs, "")
            editingBookmark = bookmark
        }
    }

    LaunchedEffect(sections?.isEmpty) {
        if (sections?.isEmpty != false) {
            latestOnContentAtTopChanged(true)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { latestOnContentAtTopChanged(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(headerDragModifier)
                    .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bookmarks",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { createBookmarkAtPlayhead() },
                    enabled = !playerState.bookId.isNullOrBlank() && !playerState.chapterId.isNullOrBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create bookmark")
                }
            }

            if (book == null) {
                EmptyBookmarksMessage("Start a book to see its bookmarks here.")
                return@Column
            }

            if (sections == null || sections.isEmpty) {
                EmptyBookmarksMessage("No bookmarks yet for ${book.title}.")
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "book-header") {
                    BookmarkBookHeader(book = book, bookmarkCount = bookmarkCount)
                }

                sections.currentChapter?.let { currentChapter ->
                    item(key = "current-header") {
                        BookmarkSectionHeader("Current chapter", currentChapter.chapterTitle)
                    }
                    items(currentChapter.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            chapterTitle = currentChapter.chapterTitle,
                            highlighted = true,
                            onClick = { playFromBookmark(bookmark) },
                            onLongClick = {
                                haptics.longPress()
                                actionBookmark = bookmark
                            },
                            onEdit = {
                                haptics.tick()
                                editingBookmark = bookmark
                            },
                            onDelete = { removeBookmark(bookmark) },
                        )
                    }
                }

                if (sections.showDivider) {
                    item(key = "current-divider") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                sections.remainingChapters.forEach { section ->
                    item(key = "section-${section.chapterId}") {
                        BookmarkSectionHeader(
                            overline = if (sections.currentChapter == null) "Chapter" else "More bookmarks",
                            title = section.chapterTitle,
                        )
                    }
                    items(section.bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            chapterTitle = section.chapterTitle,
                            highlighted = false,
                            onClick = { playFromBookmark(bookmark) },
                            onLongClick = {
                                haptics.longPress()
                                actionBookmark = bookmark
                            },
                            onEdit = {
                                haptics.tick()
                                editingBookmark = bookmark
                            },
                            onDelete = { removeBookmark(bookmark) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    actionBookmark?.let { bookmark ->
        BookmarkActionsSheet(
            bookmark = bookmark,
            chapterTitle = sections.chapterTitleFor(bookmark),
            onDismiss = { actionBookmark = null },
            onPlay = {
                actionBookmark = null
                playFromBookmark(bookmark)
            },
            onEdit = {
                actionBookmark = null
                editingBookmark = bookmark
            },
            onDelete = {
                actionBookmark = null
                removeBookmark(bookmark)
            },
        )
    }

    editingBookmark?.let { bookmark ->
        BookmarkNoteEditorSheet(
            bookmark = bookmark,
            onDismiss = { editingBookmark = null },
            onSave = { note ->
                haptics.confirm()
                scope.launch { graph.app.bookmarkRepository.updateNote(bookmark.id, note) }
                editingBookmark = null
            },
        )
    }
}

@Composable
private fun EmptyBookmarksMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun BookmarkBookHeader(book: AudioBook, bookmarkCount: Int) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            BookCover(
                book = book,
                modifier = Modifier.size(48.dp),
                cornerRadius = 10,
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
            Text(
                text = book.author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Text(
                text = bookmarkCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BookmarkSectionHeader(overline: String, title: String) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(
            text = overline,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    chapterTitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowShape = RoundedCornerShape(18.dp)
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.clip(rowShape),
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = rowShape,
        tonalElevation = if (highlighted) 1.dp else 0.dp,
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            leadingContent = {
                Icon(Icons.Filled.Bookmark, contentDescription = null)
            },
            headlineContent = {
                Text(
                    text = formatHmsFromMs(bookmark.positionMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = chapterTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (bookmark.note.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Note attached",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = bookmark.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .basicMarquee(iterations = Int.MAX_VALUE),
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                    }
                }
            },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkActionsSheet(
    bookmark: Bookmark,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
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
            BookmarkActionRow(Icons.Filled.PlayArrow, "Jump to bookmark", onPlay)
            BookmarkActionRow(Icons.Filled.Edit, "Edit note", onEdit)
            BookmarkActionRow(Icons.Filled.Delete, "Delete", onDelete)
        }
    }
}

@Composable
private fun BookmarkActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkNoteEditorSheet(
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

private fun com.librivox.mobile.ui.player.bookmarks.BookmarkPanelSections?.chapterTitleFor(
    bookmark: Bookmark,
): String {
    val sections = this ?: return "Chapter"
    return sequenceOf(sections.currentChapter)
        .filterNotNull()
        .plus(sections.remainingChapters.asSequence())
        .firstOrNull { section -> section.bookmarks.any { it.id == bookmark.id } }
        ?.chapterTitle
        ?: "Chapter"
}
