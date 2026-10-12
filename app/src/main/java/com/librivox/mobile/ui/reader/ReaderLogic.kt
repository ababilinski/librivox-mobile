package com.librivox.mobile.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import com.librivox.mobile.playback.ReaderHighlightMode
import com.librivox.mobile.playback.ReaderSettings
import com.librivox.mobile.readalong.ReadAlongSegment
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalFoundationApi::class)
internal val ReaderNoOpBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

internal enum class ReaderSegmentHighlightRole {
    None,
    Current,
}

internal data class ReaderTextRange(
    val start: Int,
    val endExclusive: Int,
)

internal data class ReaderTimedTextRange(
    val textRange: ReaderTextRange,
    val clipBeginMs: Long,
    val clipEndMs: Long,
)

internal data class ReaderVisibleRange(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
)

internal data class ReaderSelection(
    val segmentId: String,
    val chapterId: String,
    val clipBeginMs: Long?,
    val selectedText: String,
)

internal data class ReaderBookmarkTarget(
    val bookId: String,
    val chapterId: String,
    val positionMs: Long,
    val note: String,
)

internal data class ReaderListScrollTarget(
    val index: Int,
    val scrollOffset: Int,
)

internal fun resolveReaderOpenScrollIndex(
    activeSegmentIndex: Int?,
    initialChapterIndex: Int?,
    isCurrentPlaybackBook: Boolean,
    didUseInitialScroll: Boolean,
): Int? =
    when {
        isCurrentPlaybackBook && activeSegmentIndex != null -> activeSegmentIndex
        !didUseInitialScroll && initialChapterIndex != null -> initialChapterIndex
        else -> null
    }

internal fun shouldAllowReaderFollowScroll(
    followPlayback: Boolean,
    activeSegmentId: String?,
    selectedSegmentId: String?,
    deferUntilActiveSegmentChangesFrom: String?,
): Boolean =
    followPlayback &&
        activeSegmentId != null &&
        selectedSegmentId == null &&
        (deferUntilActiveSegmentChangesFrom == null || deferUntilActiveSegmentChangesFrom != activeSegmentId)

internal fun shouldScrollToReaderItem(
    index: Int,
    visibleRange: ReaderVisibleRange?,
    leadingComfortItems: Int = 1,
    trailingComfortItems: Int = 2,
): Boolean {
    if (visibleRange == null) return true
    val first = visibleRange.firstVisibleIndex
    val last = visibleRange.lastVisibleIndex
    if (first < 0 || last < first) return true
    if (index !in first..last) return true
    val visibleCount = last - first + 1
    val leadingComfort = leadingComfortItems.coerceAtLeast(0).coerceAtMost((visibleCount - 1) / 2)
    val trailingComfort = trailingComfortItems
        .coerceAtLeast(0)
        .coerceAtMost((visibleCount - 1 - leadingComfort).coerceAtLeast(0))
    val comfortableStart = first + leadingComfort
    val comfortableEnd = last - trailingComfort
    return index !in comfortableStart..comfortableEnd
}

internal fun readerFollowScrollTargetIndex(
    activeSegmentIndex: Int?,
    activeSegmentId: String?,
    followPlayback: Boolean,
    selectedSegmentId: String?,
    deferUntilActiveSegmentChangesFrom: String?,
    visibleRange: ReaderVisibleRange?,
): Int? {
    val index = activeSegmentIndex ?: return null
    if (
        !shouldAllowReaderFollowScroll(
            followPlayback = followPlayback,
            activeSegmentId = activeSegmentId,
            selectedSegmentId = selectedSegmentId,
            deferUntilActiveSegmentChangesFrom = deferUntilActiveSegmentChangesFrom,
        )
    ) {
        return null
    }
    if (!shouldScrollToReaderItem(index, visibleRange)) {
        return null
    }
    return index
}

internal fun readerLazyListIndex(
    readerItemIndex: Int,
    leadingItemCount: Int,
): Int = readerItemIndex + leadingItemCount.coerceAtLeast(0)

internal fun readerPageNumberForListIndex(
    listIndex: Int,
    pageStartIndexes: List<Int>,
): Int {
    if (pageStartIndexes.isEmpty()) return 1
    val pageIndex = pageStartIndexes.indexOfLast { it <= listIndex.coerceAtLeast(0) }
    return if (pageIndex < 0) 1 else pageIndex + 1
}

internal fun readerScrollProgressForListPosition(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
    viewportHeight: Int,
    totalItemCount: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Float {
    if (totalItemCount <= 0 || (!canScrollBackward && !canScrollForward)) return 0f
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f

    val fallbackItemSize = readerFallbackItemSize(
        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
        measuredItemSizesByIndex = measuredItemSizesByIndex,
    )
    fun itemSize(index: Int): Float =
        readerScrollItemSize(
            index = index,
            estimatedItemSizesByIndex = estimatedItemSizesByIndex,
            measuredItemSizesByIndex = measuredItemSizesByIndex,
            fallbackItemSize = fallbackItemSize,
        )

    val clampedFirstIndex = firstVisibleItemIndex.coerceIn(0, totalItemCount - 1)
    val scrolledBeforeFirstItem = (0 until clampedFirstIndex).sumOf { itemSize(it).toDouble() }.toFloat()
    val firstItemSize = itemSize(clampedFirstIndex).coerceAtLeast(1f)
    val firstItemOffset = firstVisibleItemScrollOffset
        .coerceAtLeast(0)
        .toFloat()
        .coerceAtMost(firstItemSize)
    val scrolledPx = scrolledBeforeFirstItem + firstItemOffset
    val maxScrollPx = readerMaxScrollPx(
        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
        measuredItemSizesByIndex = measuredItemSizesByIndex,
        viewportHeight = viewportHeight,
        totalItemCount = totalItemCount,
    ).coerceAtLeast(1f)
    return (scrolledPx / maxScrollPx).coerceIn(0f, 1f)
}

internal fun readerListScrollTargetForProgress(
    progress: Float,
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
    viewportHeight: Int,
    totalItemCount: Int,
): ReaderListScrollTarget {
    if (totalItemCount <= 0) return ReaderListScrollTarget(index = 0, scrollOffset = 0)

    val targetScrollPx = readerMaxScrollPx(
        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
        measuredItemSizesByIndex = measuredItemSizesByIndex,
        viewportHeight = viewportHeight,
        totalItemCount = totalItemCount,
    ) * progress.coerceIn(0f, 1f)
    val fallbackItemSize = readerFallbackItemSize(
        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
        measuredItemSizesByIndex = measuredItemSizesByIndex,
    )
    var consumedPx = 0f
    for (index in 0 until totalItemCount) {
        val itemSize = readerScrollItemSize(
            index = index,
            estimatedItemSizesByIndex = estimatedItemSizesByIndex,
            measuredItemSizesByIndex = measuredItemSizesByIndex,
            fallbackItemSize = fallbackItemSize,
        ).coerceAtLeast(1f)
        val nextConsumedPx = consumedPx + itemSize
        if (targetScrollPx < nextConsumedPx || index == totalItemCount - 1) {
            return ReaderListScrollTarget(
                index = index,
                scrollOffset = (targetScrollPx - consumedPx)
                    .coerceIn(0f, itemSize)
                    .roundToInt(),
            )
        }
        consumedPx = nextConsumedPx
    }
    return ReaderListScrollTarget(index = totalItemCount - 1, scrollOffset = 0)
}

private fun readerFallbackItemSize(
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
): Float =
    (estimatedItemSizesByIndex.values + measuredItemSizesByIndex.values)
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
        ?: 1f

private fun readerScrollItemSize(
    index: Int,
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
    fallbackItemSize: Float,
): Float =
    measuredItemSizesByIndex[index]
        ?.takeIf { it > 0 }
        ?.toFloat()
        ?: estimatedItemSizesByIndex[index]
            ?.takeIf { it > 0 }
            ?.toFloat()
        ?: fallbackItemSize

private fun readerMaxScrollPx(
    estimatedItemSizesByIndex: Map<Int, Int>,
    measuredItemSizesByIndex: Map<Int, Int>,
    viewportHeight: Int,
    totalItemCount: Int,
): Float {
    val fallbackItemSize = readerFallbackItemSize(
        estimatedItemSizesByIndex = estimatedItemSizesByIndex,
        measuredItemSizesByIndex = measuredItemSizesByIndex,
    )
    val totalContentHeight = (0 until totalItemCount)
        .sumOf { index ->
            readerScrollItemSize(
                index = index,
                estimatedItemSizesByIndex = estimatedItemSizesByIndex,
                measuredItemSizesByIndex = measuredItemSizesByIndex,
                fallbackItemSize = fallbackItemSize,
            ).toDouble()
        }
        .toFloat()
    return (totalContentHeight - viewportHeight.coerceAtLeast(0)).coerceAtLeast(0f)
}

internal fun readerActivePositionMs(
    playerPositionMs: Long,
    scrubPreviewPositionMs: Long?,
): Long = scrubPreviewPositionMs ?: playerPositionMs

internal fun selectionForSegment(
    segment: ReadAlongSegment,
    selectionStart: Int,
    selectionEnd: Int,
): ReaderSelection? {
    val start = selectionStart.coerceIn(0, segment.text.length)
    val end = selectionEnd.coerceIn(0, segment.text.length)
    val selectedText = segment.text.substring(start.coerceAtMost(end), start.coerceAtLeast(end)).trim()
    if (selectedText.isBlank()) return null
    return ReaderSelection(
        segmentId = segment.id,
        chapterId = segment.chapterId,
        clipBeginMs = segment.clipBeginMs,
        selectedText = selectedText,
    )
}

internal fun bookmarkTargetForSelectedText(
    bookId: String,
    selection: ReaderSelection?,
): ReaderBookmarkTarget? {
    val positionMs = selection?.clipBeginMs ?: return null
    val note = selectedTextBookmarkNote(selection.selectedText)
    return ReaderBookmarkTarget(
        bookId = bookId,
        chapterId = selection.chapterId,
        positionMs = positionMs,
        note = note,
    )
}

internal fun selectedTextBookmarkNote(selectedText: String): String =
    "\"${selectedText.trim().take(960)}\" —"

internal fun bookmarkTargetForCurrentAudio(
    readerBookId: String,
    playerBookId: String?,
    playerChapterId: String?,
    playerPositionMs: Long,
    isPlaying: Boolean,
): ReaderBookmarkTarget? {
    if (!isPlaying || readerBookId != playerBookId || playerChapterId.isNullOrBlank()) return null
    return ReaderBookmarkTarget(
        bookId = readerBookId,
        chapterId = playerChapterId,
        positionMs = playerPositionMs.coerceAtLeast(0L),
        note = "",
    )
}

internal fun readerHighlightRoleForSegment(
    segmentId: String,
    activeSegmentId: String?,
    settings: ReaderSettings,
): ReaderSegmentHighlightRole {
    if (!settings.highlightCurrentText || activeSegmentId == null) {
        return ReaderSegmentHighlightRole.None
    }
    if (settings.highlightMode != ReaderHighlightMode.SourceSegment) {
        return ReaderSegmentHighlightRole.None
    }
    return if (segmentId == activeSegmentId) {
        ReaderSegmentHighlightRole.Current
    } else {
        ReaderSegmentHighlightRole.None
    }
}

internal fun activeReaderTextRangeForSegment(
    segment: ReadAlongSegment,
    activeSegmentId: String?,
    activePositionMs: Long,
    settings: ReaderSettings,
    locale: Locale = PolishReaderLocale,
): ReaderTextRange? {
    if (!settings.highlightCurrentText) return null
    if (settings.highlightMode != ReaderHighlightMode.BreakIteratorProportional) return null
    if (segment.id != activeSegmentId) return null
    return proportionalSentenceRanges(segment, locale)
        .firstOrNull { range ->
            activePositionMs >= range.clipBeginMs && activePositionMs < range.clipEndMs
        }
        ?.textRange
}

internal fun proportionalSentenceRanges(
    segment: ReadAlongSegment,
    locale: Locale = PolishReaderLocale,
): List<ReaderTimedTextRange> {
    val clipBeginMs = segment.clipBeginMs ?: return emptyList()
    val clipEndMs = segment.clipEndMs ?: return emptyList()
    val durationMs = clipEndMs - clipBeginMs
    if (durationMs <= 0L || segment.text.isBlank()) return emptyList()

    val sentenceRanges = sentenceTextRanges(segment.text, locale)
    if (sentenceRanges.isEmpty()) return emptyList()
    if (sentenceRanges.size == 1) {
        return listOf(
            ReaderTimedTextRange(
                textRange = sentenceRanges.single(),
                clipBeginMs = clipBeginMs,
                clipEndMs = clipEndMs,
            ),
        )
    }

    val weights = sentenceRanges.map { range ->
        segment.text
            .substring(range.start, range.endExclusive)
            .count { !it.isWhitespace() }
            .coerceAtLeast(1)
    }
    val totalWeight = weights.sum().coerceAtLeast(1)
    var sliceBeginMs = clipBeginMs
    var accumulatedWeight = 0
    return sentenceRanges.mapIndexed { index, range ->
        accumulatedWeight += weights[index]
        val sliceEndMs = if (index == sentenceRanges.lastIndex) {
            clipEndMs
        } else {
            clipBeginMs + ((durationMs.toDouble() * accumulatedWeight.toDouble()) / totalWeight.toDouble()).roundToLong()
        }.coerceAtLeast(sliceBeginMs)
        ReaderTimedTextRange(
            textRange = range,
            clipBeginMs = sliceBeginMs,
            clipEndMs = sliceEndMs,
        ).also {
            sliceBeginMs = sliceEndMs
        }
    }
}

private fun sentenceTextRanges(
    text: String,
    locale: Locale,
): List<ReaderTextRange> {
    val iterator = BreakIterator.getSentenceInstance(locale)
    iterator.setText(text)
    val ranges = mutableListOf<ReaderTextRange>()
    var start = iterator.first()
    while (start != BreakIterator.DONE) {
        val end = iterator.next()
        if (end == BreakIterator.DONE) break
        val trimmed = text.trimmedRange(start, end)
        if (trimmed != null) {
            ranges += trimmed
        }
        start = end
    }
    return ranges
}

private fun String.trimmedRange(
    start: Int,
    end: Int,
): ReaderTextRange? {
    var trimmedStart = start.coerceIn(0, length)
    var trimmedEnd = end.coerceIn(trimmedStart, length)
    while (trimmedStart < trimmedEnd && this[trimmedStart].isWhitespace()) {
        trimmedStart += 1
    }
    while (trimmedEnd > trimmedStart && this[trimmedEnd - 1].isWhitespace()) {
        trimmedEnd -= 1
    }
    if (trimmedStart >= trimmedEnd) return null
    return ReaderTextRange(trimmedStart, trimmedEnd)
}

private val PolishReaderLocale: Locale = Locale.forLanguageTag("pl")
