@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.player.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.model.numberedTitle
import com.librivox.mobile.model.toMediaItems
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.formatHmsFromSeconds
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.components.stripHtml
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.player.bookmarks.buildBookmarkPanelSections
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun DetailsPanel(
    onClose: () -> Unit,
    onOpenDiscoverFilter: (String, CatalogSearchField, String) -> Unit,
    modifier: Modifier = Modifier,
    onContentAtTopChanged: (Boolean) -> Unit = {},
    headerDragModifier: Modifier = Modifier,
    bodyDragModifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by graph.playerStateRepository.state.collectAsState()
    val library by graph.app.libraryRepository.state.collectAsState(initial = null)
    val bookmarks by graph.app.bookmarkRepository.bookmarks.collectAsState(initial = emptyList())
    val book = library?.bookById(state.bookId)
    val plainDescription = remember(book?.description) { stripHtml(book?.description.orEmpty()) }
    val bookBookmarks = remember(book, bookmarks, state.bookId, state.chapterId) {
        book?.let {
            val sections = buildBookmarkPanelSections(
                book = it,
                bookmarks = bookmarks,
                activeBookId = state.bookId,
                activeChapterId = state.chapterId,
            )
            sections.currentChapter?.bookmarks.orEmpty() +
                sections.remainingChapters.flatMap { section -> section.bookmarks }
        }.orEmpty()
    }
    val listState = rememberLazyListState()
    val latestOnContentAtTopChanged by rememberUpdatedState(onContentAtTopChanged)
    var contentAtTop by remember { mutableStateOf(true) }
    var actionBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var editingBookmark by remember { mutableStateOf<Bookmark?>(null) }

    fun openFilter(query: String, field: CatalogSearchField) {
        onOpenDiscoverFilter(query, field, book?.language.orEmpty())
    }

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

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                contentAtTop = atTop
                latestOnContentAtTopChanged(atTop)
            }
    }

    if (book == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading book details…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(if (contentAtTop) bodyDragModifier else Modifier),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth().then(headerDragModifier)) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
        item {
            MetadataLinkRow(
                label = "Author",
                links = listOf(MetadataLink(book.author, CatalogSearchField.Author)),
                onOpenFilter = ::openFilter,
            )
        }
        val readers = book.chapters.mapNotNull { it.reader }.distinct().take(5)
        if (readers.isNotEmpty()) {
            item {
                MetadataLinkRow(
                    label = "Reader",
                    links = readers.map { MetadataLink(it, CatalogSearchField.Reader) },
                    onOpenFilter = ::openFilter,
                )
            }
        }
        val directors = book.chapters.mapNotNull { it.director }
            .distinct().take(5).joinToString(", ")
        if (directors.isNotBlank()) {
            item { MetadataRow(label = "Director", value = directors) }
        }
        if (book.literaryEpochs.isNotEmpty()) {
            item {
                MetadataTagRow(
                    label = "Epoch",
                    tags = book.literaryEpochs,
                    field = CatalogSearchField.Epoch,
                    onOpenFilter = ::openFilter,
                )
            }
        }
        if (book.literaryKinds.isNotEmpty()) {
            item {
                MetadataTagRow(
                    label = "Kind",
                    tags = book.literaryKinds,
                    field = CatalogSearchField.Kind,
                    onOpenFilter = ::openFilter,
                )
            }
        }
        if (book.literaryGenres.isNotEmpty()) {
            item {
                MetadataTagRow(
                    label = "Genre",
                    tags = book.literaryGenres,
                    field = CatalogSearchField.Genre,
                    onOpenFilter = ::openFilter,
                )
            }
        }
        if (!book.language.isNullOrBlank()) {
            item { MetadataRow(label = "Language", value = book.language) }
        }
        if (book.totalDurationSeconds > 0L) {
            item {
                MetadataRow(
                    label = "Total length",
                    value = formatHmsFromSeconds(book.totalDurationSeconds),
                )
            }
        }
        if (plainDescription.isNotBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "About this book",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    SelectionContainer {
                        Text(
                            text = plainDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        items(book.chapters, key = { it.id }) { chapter ->
            val isCurrent = chapter.id == state.chapterId
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val idx = book.chapters.indexOfFirst { it.id == chapter.id }
                        if (idx >= 0) graph.playerStateRepository.seekToChapter(idx)
                        onClose()
                    },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chapter.number.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                headlineContent = {
                    Text(
                        text = chapter.numberedTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    val parts = mutableListOf<String>()
                    if (chapter.durationSeconds > 0L) {
                        parts.add(formatHmsFromSeconds(chapter.durationSeconds))
                    }
                    chapter.reader?.let { parts.add(it) }
                    chapter.director?.let { parts.add("dir. $it") }
                    if (parts.isNotEmpty()) {
                        Text(
                            text = parts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        }
        if (bookBookmarks.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Bookmarks (${bookBookmarks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            items(bookBookmarks, key = { it.id }) { bookmark ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { playFromBookmark(bookmark) },
                                onLongClick = {
                                    haptics.longPress()
                                    actionBookmark = bookmark
                                },
                            ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                        headlineContent = {
                            Text(bookmark.note.ifBlank { "Bookmark" })
                        },
                        supportingContent = {
                            val chapter = book.chapters.firstOrNull { it.id == bookmark.chapterId }
                            Text("${chapter?.numberedTitle().orEmpty()} • ${formatHmsFromMs(bookmark.positionMs)}")
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch { graph.app.bookmarkRepository.remove(bookmark.id) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                    )
                }
            }
        }
    }

    actionBookmark?.let { bookmark ->
        BookmarkActionsSheet(
            bookmark = bookmark,
            chapterTitle = book.chapters.firstOrNull { it.id == bookmark.chapterId }?.numberedTitle() ?: "Chapter",
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
                scope.launch { graph.app.bookmarkRepository.remove(bookmark.id) }
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
private fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private data class MetadataLink(
    val label: String,
    val field: CatalogSearchField,
)

@Composable
private fun MetadataLinkRow(
    label: String,
    links: List<MetadataLink>,
    onOpenFilter: (String, CatalogSearchField) -> Unit,
) {
    MetadataChipRow(
        label = label,
        chips = links
            .filter { it.label.isNotBlank() }
            .map { link -> link.label to { onOpenFilter(link.label, link.field) } },
    )
}

@Composable
private fun MetadataTagRow(
    label: String,
    tags: List<CatalogTag>,
    field: CatalogSearchField,
    onOpenFilter: (String, CatalogSearchField) -> Unit,
) {
    MetadataChipRow(
        label = label,
        chips = tags
            .filter { it.name.isNotBlank() }
            .map { tag -> tag.name to { onOpenFilter(tag.name, field) } },
    )
}

@Composable
private fun MetadataChipRow(
    label: String,
    chips: List<Pair<String, () -> Unit>>,
) {
    if (chips.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { (chipLabel, onClick) ->
                AssistChip(
                    onClick = onClick,
                    label = { Text(chipLabel) },
                )
            }
        }
    }
}
