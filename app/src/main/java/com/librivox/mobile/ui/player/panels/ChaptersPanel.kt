package com.librivox.mobile.ui.player.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.numberedTitle
import com.librivox.mobile.ui.components.BookCover
import com.librivox.mobile.ui.components.formatHmsFromMs
import com.librivox.mobile.ui.components.formatHmsFromSeconds
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.navigation.LocalAppGraph
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChaptersPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onContentAtTopChanged: (Boolean) -> Unit = {},
    headerDragModifier: Modifier = Modifier,
    bodyDragModifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val state by graph.playerStateRepository.state.collectAsState()
    val libraryState by graph.app.libraryRepository.state.collectAsState(initial = null)
    val book = libraryState?.bookById(state.bookId)
    val listState = rememberLazyListState()
    val latestOnContentAtTopChanged by rememberUpdatedState(onContentAtTopChanged)

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { latestOnContentAtTopChanged(it) }
    }

    if (book == null) {
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().then(headerDragModifier)) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            Text(
                text = "Start a book to choose its chapters here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .then(
                if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    bodyDragModifier
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "chapter-panel-header") {
            Column(modifier = Modifier.fillMaxWidth().then(headerDragModifier)) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
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
                            text = "${book.chapters.size} chapters • ${book.author}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        itemsIndexed(book.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
            val isCurrent = chapter.id == state.chapterId
            ChapterRow(
                chapter = chapter,
                isCurrent = isCurrent,
                currentPositionMs = if (isCurrent) state.positionMs else 0L,
                currentDurationMs = if (isCurrent) state.durationMs else 0L,
                onClick = {
                    haptics.key()
                    graph.playerStateRepository.seekToChapter(index)
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: AudioBookChapter,
    isCurrent: Boolean,
    currentPositionMs: Long,
    currentDurationMs: Long,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (isCurrent) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (isCurrent) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chapter.number.toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                val metadata = chapterMetadata(chapter, isCurrent, currentPositionMs, currentDurationMs)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Now",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else if (chapter.durationSeconds > 0L) {
                        Text(
                            text = formatHmsFromSeconds(chapter.durationSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

private fun chapterMetadata(
    chapter: AudioBookChapter,
    isCurrent: Boolean,
    currentPositionMs: Long,
    currentDurationMs: Long,
): String {
    val parts = mutableListOf<String>()
    if (isCurrent && currentDurationMs > 0L) {
        val remainingMs = (currentDurationMs - currentPositionMs).coerceAtLeast(0L)
        parts.add("${formatHmsFromMs(remainingMs)} left")
    } else if (chapter.durationSeconds > 0L) {
        parts.add(formatHmsFromSeconds(chapter.durationSeconds))
    }
    chapter.reader?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    return parts.joinToString(" • ")
}
