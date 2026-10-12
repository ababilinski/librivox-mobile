@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.librivox.mobile.ui.player.panels

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.librivox.mobile.model.parseMediaId
import com.librivox.mobile.playback.FeedbackControlScope
import com.librivox.mobile.playback.PlaybackFeedback
import com.librivox.mobile.playback.QueueListItem
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.navigation.LocalAppGraph
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onContentAtTopChanged: (Boolean) -> Unit = {},
    headerDragModifier: Modifier = Modifier,
    bodyDragModifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state by graph.playerStateRepository.state.collectAsState()
    val feedbackRevision by graph.app.playbackFeedbackStore.revision.collectAsState()
    var sourceItems by remember { mutableStateOf<List<QueueListItem>>(emptyList()) }
    val listState = rememberLazyListState()
    val latestOnContentAtTopChanged by rememberUpdatedState(onContentAtTopChanged)
    var displayItems by remember { mutableStateOf(sourceItems) }
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragStartAbsoluteIndex by remember { mutableIntStateOf(-1) }
    var dragBaseAbsoluteIndex by remember { mutableIntStateOf(-1) }
    var actionSheetItem by remember { mutableStateOf<QueueListItem?>(null) }
    var contentAtTop by remember { mutableStateOf(true) }
    var feedbackControlScope by remember {
        mutableStateOf(graph.app.playbackSettingsStore.feedbackControlScope)
    }
    val sourceOrderKey = remember(sourceItems) {
        sourceItems.joinToString(separator = "|") { "${it.id}:${it.absoluteIndex}" }
    }

    DisposableEffect(graph.app.playbackSettingsStore) {
        val listener = graph.app.playbackSettingsStore.registerPlaybackUiSettingsListener {
            feedbackControlScope = graph.app.playbackSettingsStore.feedbackControlScope
        }
        onDispose { graph.app.playbackSettingsStore.unregisterListener(listener) }
    }

    LaunchedEffect(
        state.queueRevision,
        state.mediaItemIndex,
        state.mediaItemCount,
        state.bookId,
        state.chapterId,
    ) {
        sourceItems = graph.playerStateRepository.queueAfterCurrent()
    }

    LaunchedEffect(state.queueRevision, sourceOrderKey, sourceItems) {
        if (draggingItemId == null) {
            displayItems = sourceItems
        }
    }
    LaunchedEffect(displayItems.isEmpty()) {
        if (displayItems.isEmpty()) {
            contentAtTop = true
            latestOnContentAtTopChanged(true)
        }
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

    fun moveVisibleItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in displayItems.indices || toIndex !in displayItems.indices) return
        displayItems = displayItems.moveItem(fromIndex, toIndex)
        haptics.tick()
    }

    fun bookmarkQueuedItem(item: QueueListItem) {
        val parts = item.mediaId.split("::", limit = 2)
        if (parts.size == 2) {
            scope.launch {
                graph.app.bookmarkRepository.add(
                    bookId = parts[0],
                    chapterId = parts[1],
                    positionMs = 0L,
                    note = "Queued: ${item.title}",
                )
            }
            haptics.confirm()
        }
    }

    fun finishDrag() {
        val draggedId = draggingItemId
        if (draggedId != null) {
            val toDisplayIndex = displayItems.indexOfFirst { it.id == draggedId }
            val fromAbsoluteIndex = dragStartAbsoluteIndex.takeIf { it >= 0 }
            val baseAbsoluteIndex = dragBaseAbsoluteIndex.takeIf { it >= 0 }
            val toAbsoluteIndex = if (toDisplayIndex >= 0 && baseAbsoluteIndex != null) {
                baseAbsoluteIndex + toDisplayIndex
            } else {
                fromAbsoluteIndex
            }

            haptics.gestureEnd()
            draggingItemId = null
            dragStartAbsoluteIndex = -1
            dragBaseAbsoluteIndex = -1

            if (
                fromAbsoluteIndex != null &&
                toAbsoluteIndex != null &&
                fromAbsoluteIndex != toAbsoluteIndex
            ) {
                graph.playerStateRepository.moveQueueItem(
                    fromIndex = fromAbsoluteIndex,
                    toIndex = toAbsoluteIndex,
                )
            }
        }
    }

    val reorderableListState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThresholdPadding = PaddingValues(bottom = 96.dp),
    ) { from, to ->
        moveVisibleItem(from.index, to.index)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().then(headerDragModifier)) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(bodyDragModifier),
                ) {
                    Text(
                        text = "Queue is empty. From a chapter row in Book Detail, open More, then choose Play next or Add to queue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            val firstAbsoluteIndex = displayItems.minOf { it.absoluteIndex }
            val lastAbsoluteIndex = displayItems.maxOf { it.absoluteIndex }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (contentAtTop && draggingItemId == null) bodyDragModifier else Modifier),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(displayItems, key = { _, item -> item.id }) { index, item ->
                    ReorderableItem(
                        state = reorderableListState,
                        key = item.id,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = displayItems.size > 1,
                    ) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }
                        QueueRow(
                            item = item,
                            isDragging = isDragging,
                            canMoveUp = index > 0,
                            canMoveDown = index < displayItems.lastIndex,
                            feedback = remember(item.id, feedbackRevision, feedbackControlScope) {
                                if (feedbackControlScope != FeedbackControlScope.Chapter) {
                                    return@remember PlaybackFeedback.None
                                }
                                val ids = parseMediaId(item.mediaId)
                                graph.app.playbackFeedbackStore.feedbackFor(
                                    ids?.bookId,
                                    ids?.chapterId,
                                    feedbackControlScope,
                                )
                            },
                            showFeedbackIndicator = feedbackControlScope == FeedbackControlScope.Chapter,
                            dragHandleModifier = Modifier.draggableHandle(
                                enabled = displayItems.size > 1,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    draggingItemId = item.id
                                    dragStartAbsoluteIndex = item.absoluteIndex
                                    dragBaseAbsoluteIndex = firstAbsoluteIndex
                                    haptics.dragStart()
                                },
                                onDragStopped = { finishDrag() },
                                dragGestureDetector = DragGestureDetector.Press,
                            ),
                            onPlay = {
                                haptics.key()
                                graph.playerStateRepository.seekToChapter(item.absoluteIndex)
                                onClose()
                            },
                            onShowActions = {
                                haptics.longPress()
                                actionSheetItem = item
                            },
                            modifier = Modifier.semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Move up") {
                                        if (item.absoluteIndex > firstAbsoluteIndex) {
                                            graph.playerStateRepository.moveQueueItem(
                                                fromIndex = item.absoluteIndex,
                                                toIndex = item.absoluteIndex - 1,
                                            )
                                            haptics.tick()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    CustomAccessibilityAction("Move down") {
                                        if (item.absoluteIndex < lastAbsoluteIndex) {
                                            graph.playerStateRepository.moveQueueItem(
                                                fromIndex = item.absoluteIndex,
                                                toIndex = item.absoluteIndex + 1,
                                            )
                                            haptics.tick()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        actionSheetItem?.let { item ->
            val currentIndex = displayItems.indexOfFirst { it.id == item.id }
            val ids = parseMediaId(item.mediaId)
            val showFeedbackActions = feedbackControlScope == FeedbackControlScope.Chapter
            val feedback = if (showFeedbackActions) {
                graph.app.playbackFeedbackStore.feedbackFor(
                    ids?.bookId,
                    ids?.chapterId,
                    feedbackControlScope,
                )
            } else {
                PlaybackFeedback.None
            }
            QueueItemActionSheet(
                item = item,
                feedback = feedback,
                showFeedbackActions = showFeedbackActions,
                canMoveUp = currentIndex > 0,
                canMoveDown = currentIndex >= 0 && currentIndex < displayItems.lastIndex,
                onDismiss = { actionSheetItem = null },
                onPlay = {
                    haptics.key()
                    graph.playerStateRepository.seekToChapter(item.absoluteIndex)
                    actionSheetItem = null
                    onClose()
                },
                onMoveUp = {
                    graph.playerStateRepository.moveQueueItem(
                        fromIndex = item.absoluteIndex,
                        toIndex = item.absoluteIndex - 1,
                    )
                    haptics.tick()
                    actionSheetItem = null
                },
                onMoveDown = {
                    graph.playerStateRepository.moveQueueItem(
                        fromIndex = item.absoluteIndex,
                        toIndex = item.absoluteIndex + 1,
                    )
                    haptics.tick()
                    actionSheetItem = null
                },
                onBookmark = {
                    bookmarkQueuedItem(item)
                    actionSheetItem = null
                },
                onAddToQueue = {
                    graph.playerStateRepository.appendQueueItemCopy(item.absoluteIndex)
                    haptics.confirm()
                    actionSheetItem = null
                },
                onLike = {
                    val ids = parseMediaId(item.mediaId)
                    graph.app.playbackFeedbackStore.toggleFeedback(
                        ids?.bookId,
                        ids?.chapterId,
                        PlaybackFeedback.Liked,
                        feedbackControlScope,
                    )
                    haptics.confirm()
                    actionSheetItem = null
                },
                onDislike = {
                    val ids = parseMediaId(item.mediaId)
                    graph.app.playbackFeedbackStore.toggleFeedback(
                        ids?.bookId,
                        ids?.chapterId,
                        PlaybackFeedback.Disliked,
                        feedbackControlScope,
                    )
                    haptics.confirm()
                    actionSheetItem = null
                },
                onRemove = {
                    graph.playerStateRepository.removeQueueItem(item.absoluteIndex)
                    haptics.tick()
                    actionSheetItem = null
                },
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueListItem,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    feedback: PlaybackFeedback,
    showFeedbackIndicator: Boolean,
    dragHandleModifier: Modifier,
    onPlay: () -> Unit,
    onShowActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dragSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.018f else 1f,
        animationSpec = dragSpring,
        label = "queue row scale",
    )
    val corner by animateDpAsState(
        targetValue = if (isDragging) 22.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queue row corners",
    )
    val tonalElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queue row tonal elevation",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queue row shadow elevation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isDragging) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "queue row container",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(corner),
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onShowActions,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                QueueArtwork(item = item)
            },
            trailingContent = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showFeedbackIndicator) {
                        QueueFeedbackIndicator(feedback = feedback)
                    }
                    QueueDragHandle(
                        canDrag = canMoveUp || canMoveDown,
                        modifier = dragHandleModifier,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun QueueArtwork(item: QueueListItem) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!item.artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(30.dp)
                    .padding(2.dp),
            )
        }
    }
}

@Composable
private fun QueueFeedbackIndicator(feedback: PlaybackFeedback) {
    if (feedback == PlaybackFeedback.None) return
    val liked = feedback == PlaybackFeedback.Liked
    Icon(
        imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Filled.ThumbDown,
        contentDescription = if (liked) "Liked chapter" else "Disliked chapter",
        tint = if (liked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        modifier = Modifier
            .size(34.dp)
            .padding(7.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemActionSheet(
    item: QueueListItem,
    feedback: PlaybackFeedback,
    showFeedbackActions: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onBookmark: () -> Unit,
    onAddToQueue: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRemove: () -> Unit,
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { QueueArtwork(item = item) },
                headlineContent = {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            QueueActionRow(
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                label = "Play now",
                onClick = onPlay,
            )
            QueueActionRow(
                icon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                label = "Move up",
                enabled = canMoveUp,
                onClick = onMoveUp,
            )
            QueueActionRow(
                icon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                label = "Move down",
                enabled = canMoveDown,
                onClick = onMoveDown,
            )
            QueueActionRow(
                icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                label = "Bookmark start",
                onClick = onBookmark,
            )
            QueueActionRow(
                icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                label = "Add to end of queue",
                onClick = onAddToQueue,
            )
            if (showFeedbackActions) {
                QueueActionRow(
                    icon = {
                        Icon(
                            Icons.Filled.ThumbUp,
                            contentDescription = null,
                            tint = if (feedback == PlaybackFeedback.Liked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    label = if (feedback == PlaybackFeedback.Liked) "Remove like" else "Like",
                    onClick = onLike,
                )
                QueueActionRow(
                    icon = {
                        Icon(
                            Icons.Filled.ThumbDown,
                            contentDescription = null,
                            tint = if (feedback == PlaybackFeedback.Disliked) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    label = if (feedback == PlaybackFeedback.Disliked) "Remove dislike" else "Dislike",
                    onClick = onDislike,
                )
            }
            QueueActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                label = "Remove from queue",
                onClick = onRemove,
            )
            Box(modifier = Modifier.size(1.dp).padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun QueueActionRow(
    icon: @Composable () -> Unit,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon,
        headlineContent = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun QueueDragHandle(
    canDrag: Boolean,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = {},
        enabled = canDrag,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder chapter",
            tint = if (canDrag) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.padding(4.dp),
        )
    }
}

private fun List<QueueListItem>.moveItem(fromIndex: Int, toIndex: Int): List<QueueListItem> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        val item = removeAt(fromIndex)
        add(toIndex, item)
    }
}
