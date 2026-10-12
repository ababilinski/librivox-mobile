package com.librivox.mobile.readalong

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import java.text.Normalizer
import java.util.Locale

data class ReadAlongBook(
    val bookId: String,
    val title: String,
    val chapters: List<ReadAlongChapter>,
    val hasTimedSync: Boolean,
    val tableOfContents: List<ReadAlongTocItem> = emptyList(),
    val isPartial: Boolean = false,
) {
    fun chapter(chapterId: String?): ReadAlongChapter? =
        chapters.firstOrNull { it.chapterId == chapterId } ?: chapters.firstOrNull()

    fun search(query: String): List<ReadAlongSearchHit> {
        val normalizedQuery = query.normalizedForReaderSearch()
        if (normalizedQuery.isBlank()) return emptyList()
        return chapters.flatMap { chapter -> chapter.search(normalizedQuery, query) }
    }
}

data class ReadAlongChapter(
    val chapterId: String,
    val title: String,
    val displayIndex: Int,
    val fullText: String,
    val segments: List<ReadAlongSegment>,
) {
    fun activeSegment(positionMs: Long): ReadAlongSegment? =
        segments.firstOrNull { segment ->
            val end = segment.clipEndMs ?: Long.MAX_VALUE
            segment.clipBeginMs != null && positionMs in segment.clipBeginMs until end
        } ?: segments.lastOrNull { segment ->
            segment.clipBeginMs != null && segment.clipBeginMs <= positionMs
        }

    fun segmentForSelection(selectionStart: Int, selectionEnd: Int): ReadAlongSegment? {
        val start = selectionStart.coerceAtMost(selectionEnd)
        val end = selectionStart.coerceAtLeast(selectionEnd)
        if (start == end) return null
        return segments.firstOrNull { segment ->
            segment.clipBeginMs != null &&
                segment.textEnd > start &&
                segment.textStart < end
        }
    }

    internal fun search(normalizedQuery: String, rawQuery: String): List<ReadAlongSearchHit> {
        val normalizedText = fullText.normalizedForReaderSearch()
        val hits = mutableListOf<ReadAlongSearchHit>()
        var index = normalizedText.indexOf(normalizedQuery)
        while (index >= 0) {
            val end = index + normalizedQuery.length
            val segment = segments.firstOrNull { it.textEnd > index && it.textStart < end }
            val sentence = fullText.sentenceAround(index, end)
            val sentenceStart = sentence.startOffset
            hits += ReadAlongSearchHit(
                id = "$chapterId:$index",
                chapterId = chapterId,
                segmentId = segment?.id,
                chapterTitle = title,
                displayIndex = displayIndex,
                timestampMs = segment?.clipBeginMs,
                sentence = sentence.text,
                matchStart = (index - sentenceStart).coerceAtLeast(0),
                matchEnd = (end - sentenceStart).coerceAtLeast(0).coerceAtMost(sentence.text.length),
                query = rawQuery,
                textOffset = index,
            )
            index = normalizedText.indexOf(normalizedQuery, startIndex = end.coerceAtLeast(index + 1))
        }
        return hits
    }
}

data class ReadAlongSegment(
    val id: String,
    val chapterId: String,
    val text: String,
    val textRef: String,
    val audioRef: String?,
    val clipBeginMs: Long?,
    val clipEndMs: Long?,
    val textStart: Int = 0,
    val textEnd: Int = 0,
    val footnotes: List<ReadAlongFootnote> = emptyList(),
    val role: ReadAlongSegmentRole = ReadAlongSegmentRole.Paragraph,
    val firstLineIndent: Boolean = false,
    val sourceStyle: ReadAlongTextStyle = ReadAlongTextStyle(),
    val sourceSpans: List<ReadAlongTextSpan> = emptyList(),
    val textBlockRef: String? = null,
)

data class ReadAlongTocItem(
    val id: String,
    val title: String,
    val level: Int,
    val href: String?,
    val chapterId: String? = null,
    val segmentId: String? = null,
)

enum class ReadAlongSegmentRole {
    Paragraph,
    Heading1,
    Heading2,
    Heading3,
    Quote,
    Verse,
}

data class ReadAlongTextSpan(
    val start: Int,
    val end: Int,
    val style: ReadAlongTextStyle,
)

data class ReadAlongTextStyle(
    val fontWeight: ReadAlongFontWeight? = null,
    val fontStyle: ReadAlongFontStyle? = null,
    val textSizeMultiplier: Float? = null,
    val textAlign: ReadAlongTextAlign? = null,
    val firstLineIndent: Boolean? = null,
) {
    fun merge(overrides: ReadAlongTextStyle): ReadAlongTextStyle =
        ReadAlongTextStyle(
            fontWeight = overrides.fontWeight ?: fontWeight,
            fontStyle = overrides.fontStyle ?: fontStyle,
            textSizeMultiplier = overrides.textSizeMultiplier ?: textSizeMultiplier,
            textAlign = overrides.textAlign ?: textAlign,
            firstLineIndent = overrides.firstLineIndent ?: firstLineIndent,
        )

    fun hasInlineTextStyle(): Boolean =
        fontWeight != null || fontStyle != null || textSizeMultiplier != null
}

enum class ReadAlongFontWeight {
    Normal,
    Bold,
}

enum class ReadAlongFontStyle {
    Normal,
    Italic,
}

enum class ReadAlongTextAlign {
    Start,
    Center,
    End,
    Justify,
}

data class ReadAlongFootnote(
    val number: String,
    val text: String,
    val textOffset: Int? = null,
)

data class ReadAlongSearchHit(
    val id: String,
    val chapterId: String,
    val segmentId: String?,
    val chapterTitle: String,
    val displayIndex: Int,
    val timestampMs: Long?,
    val sentence: String,
    val matchStart: Int,
    val matchEnd: Int,
    val query: String,
    val textOffset: Int,
)

internal fun buildReadAlongChapter(
    chapter: AudioBookChapter,
    displayIndex: Int,
    segments: List<ReadAlongSegment>,
): ReadAlongChapter {
    val textBuilder = StringBuilder()
    val offsetSegments = segments.mapNotNull { segment ->
        val trimmed = segment.trimmedForReader()
        val text = trimmed.text
        if (text.isBlank()) return@mapNotNull null
        if (textBuilder.isNotEmpty()) {
            textBuilder.append("\n\n")
        }
        val start = textBuilder.length
        textBuilder.append(text)
        segment.copy(
            text = text,
            textStart = start,
            textEnd = textBuilder.length,
            sourceSpans = trimmed.spans,
        )
    }
    return ReadAlongChapter(
        chapterId = chapter.id,
        title = chapter.title.ifBlank { "Chapter ${chapter.number}" },
        displayIndex = displayIndex,
        fullText = textBuilder.toString(),
        segments = offsetSegments,
    )
}

private data class TrimmedReaderSegmentText(
    val text: String,
    val spans: List<ReadAlongTextSpan>,
)

private fun ReadAlongSegment.trimmedForReader(): TrimmedReaderSegmentText {
    val trimStart = text.indexOfFirst { !it.isWhitespace() }
    if (trimStart < 0) return TrimmedReaderSegmentText("", emptyList())
    val trimEndExclusive = text.indexOfLast { !it.isWhitespace() } + 1
    val trimmedText = text.substring(trimStart, trimEndExclusive)
    val trimmedSpans = sourceSpans.mapNotNull { span ->
        val start = (span.start - trimStart).coerceIn(0, trimmedText.length)
        val end = (span.end - trimStart).coerceIn(0, trimmedText.length)
        if (start < end && span.style.hasInlineTextStyle()) {
            span.copy(start = start, end = end)
        } else {
            null
        }
    }
    return TrimmedReaderSegmentText(trimmedText, trimmedSpans)
}

internal fun buildStaticReadAlongBook(book: AudioBook, textChunks: List<String>): ReadAlongBook {
    val fallbackChapter = book.chapters.firstOrNull()
        ?: AudioBookChapter(id = book.id, title = book.title, number = 1)
    val segments = textChunks
        .mapIndexed { index, text ->
            ReadAlongSegment(
                id = "static-$index",
                chapterId = fallbackChapter.id,
                text = text,
                textRef = "static-$index",
                audioRef = null,
                clipBeginMs = null,
                clipEndMs = null,
            )
        }
    return ReadAlongBook(
        bookId = book.id,
        title = book.title,
        chapters = listOf(buildReadAlongChapter(fallbackChapter, 1, segments)),
        hasTimedSync = false,
    )
}

internal fun ReadAlongBook.withFootnotesFrom(source: ReadAlongBook?): ReadAlongBook {
    source ?: return this
    val mergedTableOfContents = tableOfContents.mergedReaderTableOfContents(
        source = source.tableOfContents,
        chapters = chapters,
    )
    val sourceSegments = source.chapters.flatMap { chapter ->
        chapter.segments.map { segment -> SourceReaderSegment(chapter.chapterId, segment) }
    }
    val sourceSegmentsByText = sourceSegments
        .groupBy { sourceSegment -> sourceSegment.segment.readerSourceTextKey() }
        .mapValues { (_, sourceSegments) -> sourceSegments.map { it.segment } }
    val sourceSegmentsByChapterAndText = sourceSegments
        .groupBy { sourceSegment -> sourceSegment.chapterTextKey }
        .mapValues { (_, sourceSegments) -> sourceSegments.map { it.segment } }
    val sourceFootnotesByText = sourceSegmentsByText
        .mapValues { (_, segments) -> segments.flatMap { it.footnotes }.distinct() }
        .filterValues { footnotes -> footnotes.isNotEmpty() }
    val sourceFootnotesByChapterAndText = sourceSegmentsByChapterAndText
        .mapValues { (_, segments) -> segments.flatMap { it.footnotes }.distinct() }
        .filterValues { footnotes -> footnotes.isNotEmpty() }
    val sourceFormattingByText = sourceSegmentsByText
        .mapValues { (_, segments) -> segments.firstOrNull { it.hasSourceReaderFormatting() } }
        .filterValues { segment -> segment != null }
    val sourceFormattingByChapterAndText = sourceSegmentsByChapterAndText
        .mapValues { (_, segments) -> segments.firstOrNull { it.hasSourceReaderFormatting() } }
        .filterValues { segment -> segment != null }
    if (
        sourceFootnotesByText.isEmpty() &&
        sourceFootnotesByChapterAndText.isEmpty() &&
        sourceFormattingByText.isEmpty() &&
        sourceFormattingByChapterAndText.isEmpty()
    ) {
        return if (mergedTableOfContents == tableOfContents) this else copy(tableOfContents = mergedTableOfContents)
    }
    return copy(
        tableOfContents = mergedTableOfContents,
        chapters = chapters.map { chapter ->
            chapter.copy(
                segments = chapter.segments.map { segment ->
                    val textKey = segment.readerSourceTextKey()
                    val chapterTextKey = chapter.readerChapterTextKey(textKey)
                    val sourceFormatting = sourceFormattingByChapterAndText[chapterTextKey]
                        ?: sourceFormattingByText[textKey]
                    val sourceFootnotes = sourceFootnotesByChapterAndText[chapterTextKey]
                        ?: sourceFootnotesByText[textKey]
                        ?: emptyList()
                    segment.copy(
                        footnotes = segment.footnotes.takeIf { it.isNotEmpty() } ?: sourceFootnotes,
                        role = sourceFormatting?.role ?: segment.role,
                        firstLineIndent = sourceFormatting?.firstLineIndent ?: segment.firstLineIndent,
                        sourceStyle = sourceFormatting?.sourceStyle ?: segment.sourceStyle,
                        sourceSpans = sourceFormatting
                            ?.sourceSpans
                            ?.takeIf { spans -> spans.all { span -> span.start >= 0 && span.end <= segment.text.length } }
                            ?: segment.sourceSpans,
                    )
                },
            )
        },
    )
}

private data class SourceReaderSegment(
    val chapterId: String,
    val segment: ReadAlongSegment,
) {
    val chapterTextKey: String = "$chapterId\u0000${segment.readerSourceTextKey()}"
}

private fun ReadAlongChapter.readerChapterTextKey(textKey: String): String =
    "$chapterId\u0000$textKey"

private fun ReadAlongSegment.readerSourceTextKey(): String =
    text.normalizedForReaderSearch().compactReaderTextKey()

private fun ReadAlongSegment.hasSourceReaderFormatting(): Boolean =
    role != ReadAlongSegmentRole.Paragraph ||
        firstLineIndent ||
        sourceStyle != ReadAlongTextStyle() ||
        sourceSpans.isNotEmpty()

internal fun List<ReadAlongChapter>.readerChapterTableOfContents(): List<ReadAlongTocItem> =
    mapNotNull { chapter ->
        val firstSegment = chapter.segments.firstOrNull() ?: return@mapNotNull null
        ReadAlongTocItem(
            id = "chapter:${chapter.chapterId}",
            title = chapter.title,
            level = 0,
            href = firstSegment.textRef.takeIf { it.isNotBlank() },
            chapterId = chapter.chapterId,
            segmentId = null,
        )
    }

internal fun List<ReadAlongTocItem>.hasCompleteReaderChapterCoverage(
    chapters: List<ReadAlongChapter>,
): Boolean {
    if (isEmpty() || chapters.isEmpty()) return false
    val expectedChapterIds = chapters.map { chapter -> chapter.chapterId }.toSet()
    val targetChapterIds = readerTargetChapterIds()
    return targetChapterIds.isNotEmpty() && targetChapterIds.containsAll(expectedChapterIds)
}

private fun List<ReadAlongTocItem>.mergedReaderTableOfContents(
    source: List<ReadAlongTocItem>,
    chapters: List<ReadAlongChapter>,
): List<ReadAlongTocItem> {
    val primary = retargetReaderTableOfContents(chapters)
    val fallback = source.retargetReaderTableOfContents(chapters)
    val synthesized = chapters.readerChapterTableOfContents()
    val primaryOrSynthesized = primary
        .takeIf { items -> items.isNotEmpty() }
        ?: synthesized
    val primaryChapterTargets = primaryOrSynthesized.readerTargetChapterIds().size
    val fallbackChapterTargets = fallback.readerTargetChapterIds().size
    return when {
        primaryOrSynthesized.isEmpty() -> fallback
        fallback.isEmpty() -> primaryOrSynthesized
        primaryOrSynthesized.isSynthesizedReaderChapterTableOfContents() &&
            fallback.isSourceReaderTableOfContents() -> fallback
        fallback.hasCompleteReaderChapterCoverage(chapters) &&
            fallbackChapterTargets > primaryChapterTargets -> fallback
        fallback.hasCompleteReaderChapterCoverage(chapters) &&
            fallback.size > primaryOrSynthesized.size -> fallback
        else -> primaryOrSynthesized
    }
}

private fun List<ReadAlongTocItem>.retargetReaderTableOfContents(
    chapters: List<ReadAlongChapter>,
): List<ReadAlongTocItem> {
    if (isEmpty() || chapters.isEmpty()) return this
    val chaptersById = chapters.associateBy { chapter -> chapter.chapterId }
    val segments = chapters.flatMap { chapter -> chapter.segments }
    val segmentsById = segments.associateBy { segment -> segment.id }
    val segmentsByRef = segments.associateBy { segment -> segment.textRef }
    val firstSegmentByPath = segments
        .groupBy { segment -> segment.textRef.substringBefore('#') }
        .mapValues { (_, pathSegments) -> pathSegments.firstOrNull() }
    return map { item ->
        val isSynthesizedChapterItem = item.id.startsWith("chapter:")
        val existingSegment = item.segmentId?.let(segmentsById::get)
        val hrefSegment = item.href?.let { href ->
            segmentsByRef[href] ?: firstSegmentByPath[href.substringBefore('#')]
        }
        val targetChapter = existingSegment?.chapterId?.let(chaptersById::get)
            ?: item.chapterId?.let(chaptersById::get)
            ?: hrefSegment?.chapterId?.let(chaptersById::get)
            ?: chapters.readerChapterMatchingTocTitle(item.title)
        val targetSegment = existingSegment
            ?: if (isSynthesizedChapterItem && item.chapterId != null) {
                null
            } else {
                hrefSegment ?: targetChapter?.segments?.firstOrNull()
            }
        item.copy(
            chapterId = targetChapter?.chapterId,
            segmentId = targetSegment?.id,
        )
    }
}

private fun List<ReadAlongTocItem>.readerTargetChapterIds(): Set<String> =
    mapNotNull { item -> item.chapterId }
        .toSet()

private fun List<ReadAlongTocItem>.isSynthesizedReaderChapterTableOfContents(): Boolean =
    isNotEmpty() && all { item -> item.id.startsWith("chapter:") }

private fun List<ReadAlongTocItem>.isSourceReaderTableOfContents(): Boolean =
    any { item -> !item.id.startsWith("chapter:") }

private fun List<ReadAlongChapter>.readerChapterMatchingTocTitle(title: String): ReadAlongChapter? {
    val tocTitle = title.readerTocTitleKey()
    if (tocTitle.isBlank()) return null
    return firstOrNull { chapter -> chapter.title.readerTocTitleKey() == tocTitle }
        ?: firstOrNull { chapter ->
            val chapterTitle = chapter.title.readerTocTitleKey()
            tocTitle.length >= 5 &&
                chapterTitle.length >= 5 &&
                (chapterTitle.contains(tocTitle) || tocTitle.contains(chapterTitle))
        }
}

private fun String.readerTocTitleKey(): String =
    normalizedForReaderSearch()
        .replace(Regex("""\s+"""), " ")
        .trim()

internal fun String.normalizedForReaderSearch(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    return decomposed
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
}

private data class SentenceContext(
    val text: String,
    val startOffset: Int,
)

private fun String.sentenceAround(start: Int, end: Int): SentenceContext {
    val safeStart = start.coerceIn(0, length)
    val safeEnd = end.coerceIn(safeStart, length)
    val before = lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = (safeStart - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val after = indexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = safeEnd)
        .let { if (it < 0) length else (it + 1).coerceAtMost(length) }
    val contextStart = before.coerceAtLeast((safeStart - 90).coerceAtLeast(0))
    val contextEnd = after.coerceAtMost((safeEnd + 120).coerceAtMost(length))
    return SentenceContext(
        text = substring(contextStart, contextEnd).trim(),
        startOffset = contextStart,
    )
}

private fun String.compactReaderTextKey(): String =
    replace(Regex("""\s+"""), " ")
        .trim()
        .take(180)
