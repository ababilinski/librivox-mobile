package com.librivox.mobile.readalong

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToLong

class WolneAudioEpubParser {
    fun parse(
        book: AudioBook,
        readAlongFile: File,
        preferredChapterId: String? = null,
        onlyPreferredChapter: Boolean = false,
    ): ReadAlongBook =
        ZipFile(readAlongFile).use { zip ->
            val opfPath = zip.opfPathOrNull()
            if (opfPath != null) {
                zip.parseAudioEpub(book, opfPath, preferredChapterId, onlyPreferredChapter)
            } else {
                zip.parseDaisy(book, preferredChapterId, onlyPreferredChapter)
            }
        }

    private fun ZipFile.parseAudioEpub(
        book: AudioBook,
        opfPath: String,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): ReadAlongBook {
        val opf = xmlDocument(opfPath)
        val manifest = opf.select("manifest item").associateBy { item -> item.attr("id") }
        val rawTableOfContents = parseEpubTableOfContents(opfPath, manifest)
        val cssRules = parseEpubTextStyleRules(opfPath, manifest)
        val smilPaths = manifest.values
            .filter { item ->
                item.attr("media-type").equals("application/smil+xml", ignoreCase = true) ||
                    item.attr("href").endsWith(".smil", ignoreCase = true)
            }
            .mapNotNull { item -> item.attr("href").takeIf { it.isNotBlank() } }
            .map { href -> resolveZipPath(opfPath, href) }
            .distinct()
        val preferredSmilPaths = filterPreferredSmilPaths(
            smilPaths = smilPaths,
            book = book,
            preferredChapterId = preferredChapterId,
            onlyPreferredChapter = onlyPreferredChapter,
        )
        return timedOrStaticBook(
            book = book,
            timedSegments = preferredSmilPaths.flatMap { smilPath -> parseSmilSegments(book, smilPath, cssRules) },
            staticBook = { parseStaticBook(book, opfPath, opf, manifest, cssRules, preferredChapterId, onlyPreferredChapter) },
            tableOfContents = { chapters ->
                rawTableOfContents.resolveAgainst(
                    chapters = chapters,
                    includeSupplementalEntries = !onlyPreferredChapter,
                    retargetStartToFirstSegment = !onlyPreferredChapter,
                ).takeIf { items -> items.isNotEmpty() }
                    ?: chapters.readerChapterTableOfContents()
            },
            preferredChapterId = preferredChapterId,
            onlyPreferredChapter = onlyPreferredChapter,
        )
    }

    private fun ZipFile.parseDaisy(
        book: AudioBook,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): ReadAlongBook {
        val smilPaths = entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".smil", ignoreCase = true) }
            .distinct()
            .toList()
        val preferredSmilPaths = filterPreferredSmilPaths(
            smilPaths = smilPaths,
            book = book,
            preferredChapterId = preferredChapterId,
            onlyPreferredChapter = onlyPreferredChapter,
        )
        return timedOrStaticBook(
            book = book,
            timedSegments = preferredSmilPaths.flatMap { smilPath -> parseSmilSegments(book, smilPath, emptyList()) },
            staticBook = { buildStaticReadAlongBook(book, parseDaisyStaticText()) },
            tableOfContents = { chapters -> chapters.readerChapterTableOfContents() },
            preferredChapterId = preferredChapterId,
            onlyPreferredChapter = onlyPreferredChapter,
        )
    }

    private fun ZipFile.filterPreferredSmilPaths(
        smilPaths: List<String>,
        book: AudioBook,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): List<String> {
        if (!onlyPreferredChapter || preferredChapterId.isNullOrBlank() || smilPaths.size <= 1) {
            return smilPaths
        }
        val preferredAudioFileName = book.chapters
            .firstOrNull { it.id == preferredChapterId }
            ?.listenUrl
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: return smilPaths
        val matching = smilPaths.filter { smilPath ->
            runCatching {
                rawEntryText(smilPath).contains(preferredAudioFileName, ignoreCase = true)
            }.getOrDefault(false)
        }
        return matching.takeIf { it.isNotEmpty() } ?: smilPaths
    }

    private fun timedOrStaticBook(
        book: AudioBook,
        timedSegments: List<ReadAlongSegment>,
        staticBook: () -> ReadAlongBook,
        tableOfContents: (List<ReadAlongChapter>) -> List<ReadAlongTocItem>,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): ReadAlongBook {
        if (timedSegments.isNotEmpty()) {
            val mapped = timedSegments.mapToAppChapters(
                book = book,
                preferredChapterId = preferredChapterId,
                onlyPreferredChapter = onlyPreferredChapter,
            )
            if (mapped != null) {
                val chapters = mapped.preferredChapters(preferredChapterId, onlyPreferredChapter)
                val readableChapterCount = book.chapters
                    .count { !it.title.isSupplementalWolneAudioTitle() }
                return ReadAlongBook(
                    bookId = book.id,
                    title = book.title,
                    chapters = chapters,
                    hasTimedSync = true,
                    tableOfContents = tableOfContents(chapters),
                    isPartial = onlyPreferredChapter && chapters.size < readableChapterCount,
                )
            }
        }
        return staticBook()
    }

    internal fun parseClipTimeMillis(value: String?): Long? {
        val raw = value
            ?.trim()
            ?.removePrefix("npt=")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when {
            raw.endsWith("ms", ignoreCase = true) ->
                raw.dropLast(2).toDoubleOrNull()?.roundToLong()
            raw.endsWith("s", ignoreCase = true) ->
                raw.dropLast(1).toDoubleOrNull()?.times(1_000.0)?.roundToLong()
            raw.contains(":") -> {
                val parts = raw.split(':')
                if (parts.isEmpty()) return null
                val seconds = parts.lastOrNull()?.toDoubleOrNull() ?: return null
                val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
                val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
                ((hours * 3_600L + minutes * 60L) * 1_000L + seconds * 1_000.0).roundToLong()
            }
            else -> raw.toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }
    }

    private fun ZipFile.parseSmilSegments(
        book: AudioBook,
        smilPath: String,
        cssRules: List<EpubCssRule>,
    ): List<ReadAlongSegment> {
        val smil = xmlDocument(smilPath)
        val xhtmlCache = mutableMapOf<String, Document>()
        return smil.select("par").mapIndexedNotNull { index, par ->
            val textSrc = par.selectFirst("text")?.attr("src")?.takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val audio = par.selectFirst("audio")
            val audioSrc = audio?.attr("src")?.takeIf { it.isNotBlank() }
            val textPath = resolveZipPath(smilPath, textSrc.substringBefore('#'))
            val textAnchor = textSrc.substringAfter('#', missingDelimiterValue = "")
            val document = xhtmlCache.getOrPut(textPath) { xmlDocument(textPath) }
            val anchorElement = document.elementForAnchor(textAnchor)
            val readerElement = anchorElement?.closestReaderTextElement() ?: anchorElement
            val textBlockRef = readerElement?.readerTextBlockRef(textPath)
            val text = (
                anchorElement?.cleanedReaderText()
                    ?: document.textForAnchor(textAnchor)
                )
                .takeIf { it.isNotBlank() }
                ?: return@mapIndexedNotNull null
            val sourceStyle = (readerElement?.readerElementTextStyle(cssRules) ?: ReadAlongTextStyle())
                .merge(anchorElement?.readerElementTextStyle(cssRules)?.inlineOnly() ?: ReadAlongTextStyle())
            ReadAlongSegment(
                id = "${book.id}:$smilPath:$index",
                chapterId = "",
                text = text,
                textRef = "$textPath#$textAnchor",
                audioRef = audioSrc?.let { resolveZipPath(smilPath, it) },
                clipBeginMs = parseClipTimeMillis(audio?.attrAny("clipBegin", "clip-begin")),
                clipEndMs = parseClipTimeMillis(audio?.attrAny("clipEnd", "clip-end")),
                role = readerElement?.readerSegmentRole(cssRules) ?: ReadAlongSegmentRole.Paragraph,
                firstLineIndent = readerElement?.readerHasFirstLineIndent(
                    cssRules = cssRules,
                    anchorElement = anchorElement,
                ) == true,
                sourceStyle = sourceStyle,
                sourceSpans = anchorElement?.readerInlineSpans(cssRules).orEmpty(),
                textBlockRef = textBlockRef,
            )
        }
    }

    private fun List<ReadAlongSegment>.mapToAppChapters(
        book: AudioBook,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): List<ReadAlongChapter>? {
        val readableChapters = book.chapters.filterNot { it.title.isSupplementalWolneAudioTitle() }
        if (readableChapters.isEmpty()) return null
        val preferredChapter = if (onlyPreferredChapter) {
            readableChapters.firstOrNull { it.id == preferredChapterId }
        } else {
            null
        }
        val audioRefs = mapNotNull { it.audioRef }
            .distinct()
        val directAudioToChapter = directTimedAudioChapterMatches(readableChapters)
        val directMatchedChapterCount = directAudioToChapter.values
            .map { it.id }
            .distinct()
            .size
        val useDirectTimedMapping =
            directMatchedChapterCount == readableChapters.size ||
                (
                    preferredChapter != null &&
                        directAudioToChapter.values.any { it.id == preferredChapter.id }
                    )
        val usePreferredChapterMapping =
            preferredChapter != null &&
                audioRefs.isNotEmpty() &&
                audioRefs.size < readableChapters.size
        val preferredChapterForMapping = preferredChapter.takeIf { usePreferredChapterMapping }
        val chapterAudioRefs = when {
            useDirectTimedMapping -> audioRefs
            preferredChapterForMapping != null -> audioRefs
            audioRefs.size == readableChapters.size -> audioRefs
            audioRefs.size > readableChapters.size -> audioRefs.takeLast(readableChapters.size)
            readableChapters.size == 1 -> audioRefs
            else -> return null
        }
        val audioToChapter = when {
            useDirectTimedMapping -> directAudioToChapter
            preferredChapterForMapping != null ->
                chapterAudioRefs.associateWith { preferredChapterForMapping }
            chapterAudioRefs.size == readableChapters.size ->
                chapterAudioRefs.mapIndexed { index, audioRef -> audioRef to readableChapters[index] }.toMap()
            readableChapters.size == 1 ->
                chapterAudioRefs.associateWith { readableChapters.single() }
            else -> return null
        }
        val chaptersToBuild = preferredChapterForMapping
            ?.let(::listOf)
            ?: readableChapters
        return chaptersToBuild.mapNotNull { chapter ->
            val chapterSegments = mapNotNull { segment ->
                val mappedChapter = segment.audioRef?.let { audioToChapter[it] }
                    ?: return@mapNotNull null
                if (mappedChapter.id == chapter.id) {
                    segment.copy(chapterId = chapter.id)
                } else {
                    null
                }
            }
            val visibleSegments = chapterSegments.dropLeadingRepeatedBookTitleHeadings(
                bookTitle = book.title,
            )
            if (visibleSegments.isEmpty()) {
                null
            } else {
                buildReadAlongChapter(
                    chapter = chapter,
                    displayIndex = chaptersDisplayIndex(
                        sourceIndex = 0,
                        readableChapters = readableChapters,
                        appChapter = chapter,
                    ),
                    segments = visibleSegments,
                )
            }
        }.takeIf { it.isNotEmpty() }
    }

    private fun List<ReadAlongSegment>.directTimedAudioChapterMatches(
        readableChapters: List<AudioBookChapter>,
    ): Map<String, AudioBookChapter> =
        groupBy { it.audioRef }
            .mapNotNull { (audioRef, segments) ->
                audioRef ?: return@mapNotNull null
                readableChapters
                    .firstOrNull { chapter -> chapter.matchesTimedSegments(audioRef, segments) }
                    ?.let { chapter -> audioRef to chapter }
            }
            .toMap()

    private fun AudioBookChapter.matchesTimedSegments(
        audioRef: String,
        segments: List<ReadAlongSegment>,
    ): Boolean {
        val titleKey = title.timedMatchKey()
        val audioKey = audioRef
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .timedMatchKey()
        val chapterAudioKey = listenUrl
            ?.substringBefore('?')
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.timedMatchKey()
            .orEmpty()
        val candidates = buildList {
            add(audioRef.substringAfterLast('/').substringBeforeLast('.'))
            segments.asSequence()
                .map { segment ->
                    segment.textRef
                        .substringBefore('#')
                        .substringAfterLast('/')
                        .substringBeforeLast('.')
                }
                .distinct()
                .forEach(::add)
        }
        val compactCandidates = candidates.map { it.timedMatchKey() }
            .filter { it.length >= 3 }
        val numericChapterPattern = Regex("""(?:^|\D)0*${Regex.escape(number.toString())}(?:\D|$)""")
        val audioRefMatchesChapterAudio =
            audioKey.length >= 5 &&
                chapterAudioKey.length >= 5 &&
                (audioKey == chapterAudioKey || audioKey.contains(chapterAudioKey) || chapterAudioKey.contains(audioKey))
        return audioRefMatchesChapterAudio || compactCandidates.any { candidate ->
            candidate.length >= 5 && (titleKey.contains(candidate) || candidate.contains(titleKey))
        } || candidates.any { candidate ->
            val normalized = candidate.normalizedForReaderSearch()
            normalized.contains("ksiega") && numericChapterPattern.containsMatchIn(normalized)
        }
    }

    private fun ZipFile.parseStaticBook(
        book: AudioBook,
        opfPath: String,
        opf: Document,
        manifest: Map<String, org.jsoup.nodes.Element>,
        cssRules: List<EpubCssRule>,
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): ReadAlongBook {
        val spineRefs = opf.select("spine itemref")
            .filterNot { itemRef -> itemRef.attr("linear").equals("no", ignoreCase = true) }
            .mapNotNull { itemRef -> itemRef.attr("idref").takeIf { it.isNotBlank() } }
        val rawTableOfContents = parseEpubTableOfContents(opfPath, manifest)
        val paths = spineRefs.mapNotNull { idRef ->
            manifest[idRef]?.attr("href")?.takeIf { it.isNotBlank() }
        }.map { href -> resolveZipPath(opfPath, href) }
            .filterNot { path -> path.isWolneReaderFrontOrBackMatterPath() }
        val footnotesById = parseEpubFootnotes(opfPath, manifest)
        val readableChapters = book.chapters.filterNot { it.title.isSupplementalWolneAudioTitle() }
        val preferredReadableIndex = preferredChapterId
            ?.let { chapterId -> readableChapters.indexOfFirst { it.id == chapterId } }
            ?.takeIf { it >= 0 }
        val sourceChapters = staticReaderChapterSources(
            paths = paths,
            bookTitle = book.title,
            footnotesById = footnotesById,
            cssRules = cssRules,
        )
        val chaptersToParse = if (onlyPreferredChapter && preferredReadableIndex != null) {
            sourceChapters.getOrNull(preferredReadableIndex)?.let(::listOf) ?: sourceChapters
        } else {
            sourceChapters
        }
        val chapters = chaptersToParse.mapNotNull { sourceChapter ->
            val chapterIndex = sourceChapter.sourceIndex
            val appChapter = readableChapters.matchSourceChapter(sourceChapter.heading, chapterIndex)
                ?: AudioBookChapter(
                    id = "${sourceChapter.firstPath}:$chapterIndex",
                    title = sourceChapter.heading ?: book.title,
                    number = chapterIndex + 1,
                )
            buildReadAlongChapter(
                chapter = appChapter.copy(title = sourceChapter.heading ?: appChapter.title),
                displayIndex = chaptersDisplayIndex(chapterIndex, readableChapters, appChapter),
                segments = sourceChapter.segments.map { it.copy(chapterId = appChapter.id) },
            )
        }
        if (chapters.isNotEmpty()) {
            val isPartial = onlyPreferredChapter && chapters.size < sourceChapters.size
            return ReadAlongBook(
                bookId = book.id,
                title = book.title,
                chapters = chapters,
                hasTimedSync = false,
                tableOfContents = rawTableOfContents.resolveAgainst(
                    chapters = chapters,
                    includeSupplementalEntries = !isPartial,
                    retargetStartToFirstSegment = !isPartial,
                ),
                isPartial = isPartial,
            )
        }
        val textChunks = paths.mapNotNull { path -> xmlDocument(path).text().trim().takeIf { it.isNotBlank() } }
        return buildStaticReadAlongBook(book, textChunks)
    }

    private fun ZipFile.staticReaderChapterSources(
        paths: List<String>,
        bookTitle: String,
        footnotesById: Map<String, ReadAlongFootnote>,
        cssRules: List<EpubCssRule>,
    ): List<StaticReaderChapterSource> {
        val chapters = mutableListOf<StaticReaderChapterSource>()
        val pendingHeadingSegments = mutableListOf<ReadAlongSegment>()
        var pendingHeading: String? = null

        paths.forEach { path ->
            val document = xmlDocument(path)
            val elements = document.readerTextElements()
            val heading = elements
                .firstOrNull { it.isReaderHeading() }
                ?.cleanedReaderText()
                ?.takeIf { it.isNotBlank() }
            val hasBodyText = elements.any { element ->
                !element.isReaderHeading() &&
                    !element.cleanedReaderText().isWolneReaderBoilerplateText() &&
                    element.cleanedReaderText().isNotBlank()
            }
            val segments = elements.mapIndexedNotNull { elementIndex, element ->
                element.cleanedReaderText()
                    .takeIf { it.isNotBlank() }
                    ?.takeUnless { it.isWolneReaderBoilerplateText() }
                    ?.let { text ->
                        ReadAlongSegment(
                            id = "static:$path:$elementIndex",
                            chapterId = "",
                            text = text,
                            textRef = "$path#${element.id().ifBlank { elementIndex.toString() }}",
                            audioRef = null,
                            clipBeginMs = null,
                            clipEndMs = null,
                            footnotes = element.readerFootnotes(footnotesById),
                            role = element.readerSegmentRole(cssRules),
                            firstLineIndent = element.readerHasFirstLineIndent(cssRules),
                            sourceStyle = element.readerElementTextStyle(cssRules),
                            sourceSpans = element.readerInlineSpans(cssRules),
                            textBlockRef = element.readerTextBlockRef(path),
                        )
                    }
            }
            if (segments.isEmpty()) return@forEach

            if (!hasBodyText) {
                if (!heading.matchesReaderBookTitle(bookTitle)) {
                    pendingHeading = pendingHeading.combinedWithReaderHeading(heading)
                    pendingHeadingSegments += segments
                }
                return@forEach
            }

            val sourceIndex = chapters.size
            val contentSegments = segments.dropLeadingRepeatedBookTitleHeadings(
                bookTitle = bookTitle,
            )
            if (contentSegments.isEmpty()) return@forEach
            val sourceSegments = pendingHeadingSegments + contentSegments
            val contentHeading = contentSegments.firstReaderHeadingText()
            val sourceHeading = pendingHeading.combinedWithReaderHeading(contentHeading) ?: contentHeading
            chapters += StaticReaderChapterSource(
                sourceIndex = sourceIndex,
                firstPath = sourceSegments.firstOrNull()?.textRef?.substringBefore('#') ?: path,
                heading = sourceHeading,
                segments = sourceSegments,
            )
            pendingHeading = null
            pendingHeadingSegments.clear()
        }

        return chapters
    }

    private fun List<ReadAlongSegment>.dropLeadingRepeatedBookTitleHeadings(
        bookTitle: String,
    ): List<ReadAlongSegment> {
        return dropWhile { segment -> segment.isRepeatedBookTitleHeading(bookTitle) }
    }

    private fun List<ReadAlongSegment>.firstReaderHeadingText(): String? =
        firstOrNull { segment -> segment.role.isReaderHeadingRole() }
            ?.text
            ?.takeIf { it.isNotBlank() }

    private fun ReadAlongSegment.isRepeatedBookTitleHeading(bookTitle: String): Boolean =
        role.isReaderHeadingRole() && text.matchesReaderBookTitle(bookTitle)

    private fun ReadAlongSegmentRole.isReaderHeadingRole(): Boolean =
        when (this) {
            ReadAlongSegmentRole.Heading1,
            ReadAlongSegmentRole.Heading2,
            ReadAlongSegmentRole.Heading3 -> true
            ReadAlongSegmentRole.Paragraph,
            ReadAlongSegmentRole.Quote,
            ReadAlongSegmentRole.Verse -> false
        }

    private fun List<AudioBookChapter>.matchSourceChapter(
        sourceTitle: String?,
        sourceIndex: Int,
    ): AudioBookChapter? {
        val normalizedSourceTitle = sourceTitle?.normalizedReaderTitle().orEmpty()
        return firstOrNull { chapter ->
            normalizedSourceTitle.isNotBlank() &&
                chapter.title.normalizedReaderTitle().let { title ->
                    title == normalizedSourceTitle ||
                        title.contains(normalizedSourceTitle) ||
                        normalizedSourceTitle.contains(title)
                }
        } ?: getOrNull(sourceIndex)
    }

    private fun chaptersDisplayIndex(
        sourceIndex: Int,
        readableChapters: List<AudioBookChapter>,
        appChapter: AudioBookChapter,
    ): Int =
        readableChapters.indexOfFirst { it.id == appChapter.id }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: (sourceIndex + 1)

    private fun List<ReadAlongChapter>.preferredChapters(
        preferredChapterId: String?,
        onlyPreferredChapter: Boolean,
    ): List<ReadAlongChapter> {
        if (!onlyPreferredChapter) return this
        return listOfNotNull(
            firstOrNull { it.chapterId == preferredChapterId } ?: firstOrNull(),
        )
    }

    private fun ZipFile.parseEpubFootnotes(
        opfPath: String,
        manifest: Map<String, org.jsoup.nodes.Element>,
    ): Map<String, ReadAlongFootnote> {
        val annotationsPath = manifest.values
            .firstOrNull { item ->
                item.attr("href").substringAfterLast('/').equals("annotations.xhtml", ignoreCase = true)
            }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> resolveZipPath(opfPath, href) }
            ?: return emptyMap()
        return runCatching {
            xmlDocument(annotationsPath)
                .select("#footnotes .annotation, div.annotation")
                .mapNotNull { annotation ->
                    val id = annotation.id().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val number = annotation.selectFirst("a[href]")?.text()
                        ?.trim()
                        ?.trimEnd('.')
                        ?.takeIf { it.isNotBlank() }
                        ?: id.substringAfterLast('-')
                    val copy = annotation.clone()
                    copy.selectFirst("a[href]")?.remove()
                    val text = copy.text()
                        .trim()
                        .removePrefix(".")
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    id to ReadAlongFootnote(number = number, text = text)
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun ZipFile.parseEpubTableOfContents(
        opfPath: String,
        manifest: Map<String, org.jsoup.nodes.Element>,
    ): List<EpubTocItem> {
        val navPath = manifest.values
            .firstOrNull { item ->
                item.attr("properties")
                    .split(Regex("""\s+"""))
                    .any { property -> property.equals("nav", ignoreCase = true) }
            }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> resolveZipPath(opfPath, href) }
            ?: manifest.values
                .firstOrNull { item ->
                    item.attr("href").substringAfterLast('/').equals("nav.xhtml", ignoreCase = true)
                }
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { href -> resolveZipPath(opfPath, href) }
            ?: return emptyList()

        return runCatching {
            val navDocument = xmlDocument(navPath)
            val navElement = navDocument.select("nav")
                .firstOrNull { element ->
                    element.attr("epub:type").contains("toc", ignoreCase = true) ||
                        element.attr("role").equals("doc-toc", ignoreCase = true)
                }
                ?: navDocument.selectFirst("nav")
                ?: return@runCatching emptyList()
            val topList = navElement.children()
                .firstOrNull { it.tagName().equals("ol", ignoreCase = true) }
                ?: navElement.selectFirst("ol")
                ?: return@runCatching emptyList()
            topList.readTocList(navPath = navPath, level = 0)
        }.getOrDefault(emptyList())
    }

    private fun Element.readTocList(navPath: String, level: Int): List<EpubTocItem> =
        children()
            .filter { child -> child.tagName().equals("li", ignoreCase = true) }
            .flatMapIndexed { index, item ->
                val labelElement = item.children()
                    .firstOrNull { child ->
                        child.tagName().equals("a", ignoreCase = true) ||
                            child.tagName().equals("span", ignoreCase = true)
                    }
                val title = labelElement
                    ?.cleanedReaderText()
                    ?.takeIf { it.isNotBlank() }
                    ?: item.ownText().trim()
                val href = labelElement
                    ?.takeIf { it.tagName().equals("a", ignoreCase = true) }
                    ?.attr("href")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { href ->
                        val path = href.substringBefore('#')
                        val anchor = href.substringAfter('#', missingDelimiterValue = "")
                        val resolvedPath = if (path.isBlank()) navPath else resolveZipPath(navPath, path)
                        if (anchor.isBlank()) resolvedPath else "$resolvedPath#$anchor"
                    }
                val childList = item.children()
                    .firstOrNull { child -> child.tagName().equals("ol", ignoreCase = true) }
                val childItems = childList?.readTocList(navPath = navPath, level = level + 1).orEmpty()
                val collapsedBookDivision = title
                    .takeIf { it.isWolneBookDivisionTocTitle() && childItems.isNotEmpty() }
                    ?.let { parentTitle ->
                        val combinedTitle = parentTitle.combinedWithTocChildren(childItems)
                        EpubTocItem(
                            id = "toc:$level:$index:$combinedTitle",
                            title = combinedTitle,
                            level = level,
                            href = childItems.firstOrNull()?.href ?: href,
                        )
                    }
                buildList {
                    if (collapsedBookDivision != null) {
                        add(collapsedBookDivision)
                    } else if (title.isNotBlank()) {
                        add(
                            EpubTocItem(
                                id = "toc:$level:$index:$title",
                                title = title,
                                level = level,
                                href = href,
                            ),
                        )
                    }
                    if (childItems.isNotEmpty() && collapsedBookDivision == null) {
                        addAll(childItems)
                    }
                }
            }

    private fun List<EpubTocItem>.resolveAgainst(
        chapters: List<ReadAlongChapter>,
        includeSupplementalEntries: Boolean,
        retargetStartToFirstSegment: Boolean,
    ): List<ReadAlongTocItem> {
        if (isEmpty() || chapters.isEmpty()) return emptyList()
        val chaptersByPath = chapters
            .flatMap { chapter ->
                chapter.segments.map { segment -> segment.textRef.substringBefore('#') to chapter }
            }
            .distinctBy { (path, _) -> path }
            .toMap()
        val segmentsByRef = chapters
            .flatMap { chapter -> chapter.segments }
            .associateBy { segment -> segment.textRef }
        val firstSegmentByPath = chapters
            .flatMap { chapter -> chapter.segments }
            .groupBy { segment -> segment.textRef.substringBefore('#') }
            .mapValues { (_, segments) -> segments.firstOrNull() }
        val firstReadableSegment = chapters.firstOrNull()?.segments?.firstOrNull()

        return mapNotNull { item ->
            val isSupplemental = item.title.isWolneReaderTocSupplementalTitle()
            if (isSupplemental && !includeSupplementalEntries) {
                return@mapNotNull null
            }
            val href = item.href ?: return@mapNotNull null
            val path = href.substringBefore('#')
            val targetSegment = segmentsByRef[href]
                ?: firstSegmentByPath[path]
                ?: if (retargetStartToFirstSegment && item.title.isWolneReaderTocStartTitle()) {
                    firstReadableSegment
                } else {
                    null
                }
            val targetChapter = targetSegment?.chapterId
                ?.let { chapterId -> chapters.firstOrNull { chapter -> chapter.chapterId == chapterId } }
                ?: chaptersByPath[path]
                ?: chapters.chapterMatchingTocTitle(item.title)
            if (targetChapter == null && !includeSupplementalEntries) {
                return@mapNotNull null
            }
            ReadAlongTocItem(
                id = item.id,
                title = item.title,
                level = item.level,
                href = href,
                chapterId = targetChapter?.chapterId,
                segmentId = targetSegment?.id ?: targetChapter?.segments?.firstOrNull()?.id,
            )
        }.distinctBy { item -> item.href ?: item.segmentId ?: item.chapterId ?: item.title }
    }

    private fun List<ReadAlongChapter>.chapterMatchingTocTitle(title: String): ReadAlongChapter? {
        val tocTitle = title.normalizedReaderTitle()
        if (tocTitle.isBlank()) return null
        return firstOrNull { chapter -> chapter.title.normalizedReaderTitle() == tocTitle }
            ?: firstOrNull { chapter ->
                val chapterTitle = chapter.title.normalizedReaderTitle()
                tocTitle.length >= 5 &&
                    chapterTitle.length >= 5 &&
                    (chapterTitle.contains(tocTitle) || tocTitle.contains(chapterTitle))
            }
    }

    private fun ZipFile.parseDaisyStaticText(): List<String> =
        entries().asSequence()
            .map { it.name }
            .filter { path ->
                path.endsWith(".html", ignoreCase = true) ||
                    path.endsWith(".xhtml", ignoreCase = true)
            }
            .filterNot { path -> path.substringAfterLast('/').equals("ncc.html", ignoreCase = true) }
            .flatMap { path ->
                xmlDocument(path)
                    .readerTextElements(includeSentenceSpans = true)
                    .mapNotNull { element ->
                        element.cleanedReaderText()
                            .takeIf { it.isNotBlank() }
                            ?.takeUnless { it.isWolneReaderBoilerplateText() }
                    }
                    .asSequence()
            }
            .toList()

    private fun ZipFile.opfPathOrNull(): String? {
        val container = getEntry("META-INF/container.xml")?.let { entry ->
            getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val rootfile = container
            ?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
            ?.selectFirst("rootfile")
            ?.attr("full-path")
            ?.takeIf { it.isNotBlank() }
        if (rootfile != null) return rootfile
        return entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun ZipFile.xmlDocument(path: String): Document {
        val raw = rawEntryText(path)
        return Jsoup.parse(raw, "", Parser.xmlParser())
    }

    private fun ZipFile.rawEntryText(path: String): String {
        val entry = getEntry(path) ?: error("Read-along archive is missing $path.")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun Document.textForAnchor(anchor: String): String {
        val element = if (anchor.isBlank()) {
            body()
        } else {
            elementForAnchor(anchor)
        } ?: return ""
        return element.cleanedReaderText()
    }

    private fun Document.elementForAnchor(anchor: String): Element? =
        getElementById(anchor)
            ?: getElementsByAttributeValue("name", anchor).firstOrNull()
            ?: anchor.toIntOrNull()?.let { numericAnchor ->
                getElementById("t${numericAnchor + 1}") ?: getElementById("t$numericAnchor")
            }

    private fun Document.readerTextElements(includeSentenceSpans: Boolean = false): List<Element> {
        if (includeSentenceSpans) {
            val sentenceSpans = select("body span.sentence")
            if (sentenceSpans.isNotEmpty()) return sentenceSpans
        }
        val selector = buildString {
            append("body h1, body h2, body h3, body h4, body p, body blockquote, body div.stanza")
        }
        return select(selector)
    }

    private fun ZipFile.parseEpubTextStyleRules(
        opfPath: String,
        manifest: Map<String, org.jsoup.nodes.Element>,
    ): List<EpubCssRule> =
        manifest.values
            .filter { item ->
                item.attr("media-type").equals("text/css", ignoreCase = true) ||
                    item.attr("href").endsWith(".css", ignoreCase = true)
            }
            .mapNotNull { item -> item.attr("href").takeIf { it.isNotBlank() } }
            .map { href -> resolveZipPath(opfPath, href) }
            .distinct()
            .flatMap { path ->
                runCatching { rawEntryText(path).parseEpubCssRules() }.getOrDefault(emptyList())
            }

    private fun org.jsoup.nodes.Element.cleanedReaderText(): String {
        val copy = clone()
        copy.select("sup, script, style").remove()
        return copy.wholeText().cleanReaderRawText()
    }

    private fun Element.readerFootnotes(footnotesById: Map<String, ReadAlongFootnote>): List<ReadAlongFootnote> =
        select("a.anchor[href], a[href*=annotations.xhtml#], a[href*=#annotation-]")
            .mapNotNull { anchor ->
                val annotationId = anchor.attr("href")
                    .substringAfter('#', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
                annotationId?.let { id ->
                    footnotesById[id]?.copy(textOffset = readerTextOffsetBefore(anchor))
                }
            }
            .distinct()

    private fun Element.readerTextOffsetBefore(anchor: Element): Int {
        val anchorHtml = anchor.outerHtml()
        val beforeAnchor = html()
            .indexOf(anchorHtml)
            .takeIf { it >= 0 }
            ?.let { html().take(it) }
            ?: return cleanedReaderText().length
        val beforeText = Jsoup.parseBodyFragment(beforeAnchor)
            .body()
            .cleanedReaderText()
        return beforeText.length.coerceIn(0, cleanedReaderText().length)
    }

    private fun Element.isReaderHeading(): Boolean =
        tagName().lowercase().matches(Regex("""h[1-6]"""))

    private fun Element.closestReaderTextElement(): Element? {
        var current: Element? = this
        while (current != null) {
            if (current.isReaderTextElement()) return current
            current = current.parent()
        }
        return null
    }

    private fun Element.isReaderTextElement(): Boolean {
        val tag = tagName().lowercase()
        return when {
            tag.matches(Regex("""h[1-6]""")) -> true
            tag == "p" -> true
            tag == "blockquote" -> true
            tag == "div" && hasClass("stanza") -> true
            else -> false
        }
    }

    private fun Element.readerSegmentRole(cssRules: List<EpubCssRule>): ReadAlongSegmentRole {
        val tag = tagName().lowercase()
        val style = readerElementTextStyle(cssRules)
        return when {
            hasClass("h2") || hasClass("title") || style.textSizeMultiplier.atLeast(1.9f) ->
                ReadAlongSegmentRole.Heading1
            tag == "h1" -> ReadAlongSegmentRole.Heading1
            hasClass("h3") || hasClass("author") || hasClass("intitle") || hasClass("subtitle") ||
                style.textSizeMultiplier.atLeast(1.35f) -> ReadAlongSegmentRole.Heading2
            tag == "h2" -> ReadAlongSegmentRole.Heading2
            hasClass("h4") || tag.matches(Regex("""h[3-6]""")) -> ReadAlongSegmentRole.Heading3
            tag == "blockquote" -> ReadAlongSegmentRole.Quote
            tag == "div" && hasClass("stanza") -> ReadAlongSegmentRole.Verse
            else -> ReadAlongSegmentRole.Paragraph
        }
    }

    private fun Element.readerHasFirstLineIndent(
        cssRules: List<EpubCssRule>,
        anchorElement: Element? = null,
    ): Boolean {
        if (previousElementSibling()?.isReaderHeading() == true) {
            return false
        }
        val anchor = anchorElement?.takeUnless { it === this }
        if (anchor != null && !anchor.startsReaderTextBlock(this)) {
            return false
        }
        return readerElementTextStyle(cssRules).firstLineIndent
            ?: (
                tagName().equals("p", ignoreCase = true) &&
                    !hasClass("no-indent") &&
                    !hasClass("verse")
                )
    }

    private fun Element.readerTextBlockRef(path: String): String =
        "$path#${id().ifBlank { elementSiblingIndex().toString() }}"

    private fun Element.startsReaderTextBlock(readerElement: Element): Boolean {
        val anchorText = cleanedReaderText()
        if (anchorText.isBlank()) return false
        val readerText = readerElement.cleanedReaderText()
        return readerText.startsWith(anchorText)
    }

    private fun Element.readerElementTextStyle(cssRules: List<EpubCssRule>): ReadAlongTextStyle {
        var style = defaultElementTextStyle()
        cssRules.forEach { rule ->
            if (rule.matches(this)) {
                style = style.merge(rule.style)
            }
        }
        attr("style")
            .takeIf { it.isNotBlank() }
            ?.parseCssDeclarations()
            ?.let { inlineStyle -> style = style.merge(inlineStyle) }
        return style
    }

    private fun Element.defaultElementTextStyle(): ReadAlongTextStyle {
        val tag = tagName().lowercase()
        return when {
            tag == "b" || tag == "strong" ->
                ReadAlongTextStyle(fontWeight = ReadAlongFontWeight.Bold)
            tag == "i" || tag == "em" || tag == "cite" ->
                ReadAlongTextStyle(fontStyle = ReadAlongFontStyle.Italic)
            tag.matches(Regex("""h[1-6]""")) ->
                ReadAlongTextStyle(fontWeight = ReadAlongFontWeight.Bold)
            else -> ReadAlongTextStyle()
        }
    }

    private fun Element.readerInlineSpans(cssRules: List<EpubCssRule>): List<ReadAlongTextSpan> {
        val sourceText = cleanedReaderText()
        if (sourceText.isBlank()) return emptyList()
        val spans = mutableListOf<ReadAlongTextSpan>()

        fun visit(node: Node, searchStart: Int): Int {
            var cursor = searchStart.coerceIn(0, sourceText.length)
            when (node) {
                is TextNode -> {
                    val text = node.wholeText.cleanReaderRawText()
                    if (text.isNotBlank()) {
                        val index = sourceText.indexOf(text, startIndex = cursor)
                            .takeIf { it >= 0 }
                            ?: sourceText.indexOf(text)
                        if (index >= 0) cursor = (index + text.length).coerceAtMost(sourceText.length)
                    }
                }
                is Element -> {
                    if (node.tagName().equals("sup", ignoreCase = true) ||
                        node.tagName().equals("script", ignoreCase = true) ||
                        node.tagName().equals("style", ignoreCase = true)
                    ) {
                        return cursor
                    }
                    val elementText = node.cleanedReaderText()
                    val elementStart = elementText
                        .takeIf { it.isNotBlank() }
                        ?.let { text ->
                            sourceText.indexOf(text, startIndex = cursor)
                                .takeIf { it >= 0 }
                                ?: sourceText.indexOf(text)
                        }
                        ?: -1
                    val elementEnd = if (elementStart >= 0) {
                        (elementStart + elementText.length).coerceAtMost(sourceText.length)
                    } else {
                        cursor
                    }
                    val inlineStyle = node.readerElementTextStyle(cssRules).inlineOnly()
                    if (elementStart >= 0 && elementStart < elementEnd && inlineStyle.hasInlineTextStyle()) {
                        spans += ReadAlongTextSpan(
                            start = elementStart,
                            end = elementEnd,
                            style = inlineStyle,
                        )
                    }
                    var childCursor = if (elementStart >= 0) elementStart else cursor
                    node.childNodes().forEach { child ->
                        childCursor = visit(child, childCursor)
                    }
                    cursor = if (elementStart >= 0) maxOf(childCursor, elementEnd) else childCursor
                }
            }
            return cursor.coerceIn(0, sourceText.length)
        }

        var cursor = 0
        childNodes().forEach { child ->
            cursor = visit(child, cursor)
        }
        return spans
            .filter { span -> span.start < span.end && span.style.hasInlineTextStyle() }
            .distinct()
    }

    private fun Element.attrAny(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> attr(name).takeIf { it.isNotBlank() } }

    private fun resolveZipPath(basePath: String, href: String): String {
        val baseDirectory = basePath.substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { "$it/" }
            .orEmpty()
        val raw = if (href.startsWith("/")) href.dropWhile { it == '/' } else baseDirectory + href
        val parts = ArrayDeque<String>()
        raw.split('/')
            .filter { it.isNotBlank() && it != "." }
            .forEach { part ->
                when (part) {
                    ".." -> if (parts.isNotEmpty()) parts.removeLast()
                    else -> parts.addLast(part)
                }
            }
        return parts.joinToString("/")
            .replace("%20", " ")
    }

    private fun String.isWolneReaderFrontOrBackMatterPath(): Boolean {
        val name = substringAfterLast('/').lowercase()
        return name == "cover.xhtml" ||
            name == "title.xhtml" ||
            name == "nav.xhtml" ||
            name == "annotations.xhtml" ||
            name == "support.xhtml" ||
            name == "last.xhtml" ||
            Regex("""^fund\d+\.xhtml$""").matches(name)
    }

    private fun String.isWolneReaderBoilerplateText(): Boolean {
        val compact = replace(Regex("""\s+"""), " ").trim().lowercase()
        return compact.contains("ta lektura") && compact.contains("wolnelektury.pl") ||
            compact.startsWith("utwór opracowany") ||
            compact.startsWith("utwor opracowany") ||
            compact.startsWith("isbn-") ||
            compact == "wolnelektury.pl" ||
            compact.contains("wolnelektury.pl/pomagam") ||
            compact.contains("wesprzyj wolne lektury") ||
            compact.contains("wolne lektury nie mają stałego finansowania") ||
            compact.contains("wolne lektury nie maja stalego finansowania") ||
            compact.contains("aby spokojnie działać w 2026 roku") ||
            compact.contains("aby spokojnie dzialac w 2026 roku") ||
            compact.contains("książka, którą czytasz, pochodzi z biblioteki") ||
            compact.contains("ksiazka, ktora czytasz, pochodzi z biblioteki") ||
            compact.contains("przekaż 1,5% podatku na wolne lektury") ||
            compact.contains("przekaz 1,5% podatku na wolne lektury")
    }

    private fun String.isWolneReaderTocSupplementalTitle(): Boolean {
        val compact = replace(Regex("""\s+"""), " ").trim().lowercase()
        return compact == "strona tytułowa" ||
            compact == "strona tytulowa" ||
            compact == "spis treści" ||
            compact == "spis tresci" ||
            compact == "początek utworu" ||
            compact == "poczatek utworu" ||
            compact == "przypisy" ||
            compact == "wesprzyj wolne lektury" ||
            compact == "strona redakcyjna"
    }

    private fun String.isWolneReaderTocStartTitle(): Boolean {
        val compact = replace(Regex("""\s+"""), " ").trim().lowercase()
        return compact == "początek utworu" ||
            compact == "poczatek utworu"
    }

    private fun String.isWolneBookDivisionTocTitle(): Boolean {
        val compact = replace(Regex("""\s+"""), " ").trim()
        return Regex("""(?i)^ksi[eę]ga\s+\S+""").containsMatchIn(compact)
    }

    private fun String.combinedWithTocChildren(children: List<EpubTocItem>): String {
        val parent = replace(Regex("""\s+"""), " ").trim().trimEnd('.', ';', ':')
        val childTitles = children
            .filter { child -> child.level > 0 }
            .map { child -> child.title.replace(Regex("""\s+"""), " ").trim().trimEnd('.', ';', ':') }
            .filter { childTitle ->
                childTitle.isNotBlank() &&
                    !parent.normalizedReaderTitle().contains(childTitle.normalizedReaderTitle())
            }
        if (childTitles.isEmpty()) return parent
        return "$parent. ${childTitles.joinToString("; ")}"
    }

    private fun String.isSupplementalWolneAudioTitle(): Boolean {
        val compact = replace(Regex("""\s+"""), " ").trim().lowercase()
        return compact == "stopka" ||
            compact == "strona redakcyjna" ||
            compact == "nota redakcyjna"
    }

    private fun String?.matchesReaderTitle(title: String): Boolean =
        this?.normalizedReaderTitle() == title.normalizedReaderTitle()

    private fun String?.matchesReaderBookTitle(title: String): Boolean {
        val source = this?.normalizedReaderTitle().orEmpty()
        val bookTitle = title.normalizedReaderTitle()
        return source.isNotBlank() && (
            source == bookTitle ||
                bookTitle.contains(source)
            )
    }

    private fun String?.combinedWithReaderHeading(next: String?): String? {
        val first = this?.replace(Regex("""\s+"""), " ")?.trim()?.trimEnd('.', ';', ':')
        val second = next?.replace(Regex("""\s+"""), " ")?.trim()?.trimEnd('.', ';', ':')
        if (first.isNullOrBlank()) return second
        if (second.isNullOrBlank()) return first
        val firstKey = first.normalizedReaderTitle()
        val secondKey = second.normalizedReaderTitle()
        return when {
            firstKey.contains(secondKey) -> first
            secondKey.contains(firstKey) -> second
            else -> "$first. $second"
        }
    }

    private fun String.normalizedReaderTitle(): String =
        replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

    private fun String.timedMatchKey(): String =
        normalizedForReaderSearch()
            .replace(Regex("""[^a-z0-9ąćęłńóśźż]+"""), "")

    private data class EpubCssRule(
        val selector: EpubCssSelector,
        val style: ReadAlongTextStyle,
    ) {
        fun matches(element: Element): Boolean =
            selector.matches(element)
    }

    private data class EpubCssSelector(
        val tagName: String?,
        val requiredClasses: Set<String>,
        val requiredAncestorClasses: Set<String>,
    ) {
        fun matches(element: Element): Boolean {
            if (tagName != null && element.tagName().lowercase() != tagName) {
                return false
            }
            if (!element.classNames().containsAll(requiredClasses)) {
                return false
            }
            if (requiredAncestorClasses.isEmpty()) {
                return true
            }
            val ancestorClasses = element.parents()
                .flatMap { parent -> parent.classNames() }
                .toSet()
            return ancestorClasses.containsAll(requiredAncestorClasses)
        }
    }

    private fun String.parseEpubCssRules(): List<EpubCssRule> {
        val withoutComments = replace(CssCommentRegex, "")
        return CssBlockRegex.findAll(withoutComments).flatMap { match ->
            val selectors = match.groupValues[1]
                .split(',')
                .mapNotNull { selector -> selector.toEpubCssSelector() }
            val style = match.groupValues[2].parseCssDeclarations()
            if (!style.hasAnyTextStyle()) {
                emptySequence()
            } else {
                selectors.asSequence().map { selector -> EpubCssRule(selector, style) }
            }
        }.toList()
    }

    private fun String.toEpubCssSelector(): EpubCssSelector? {
        val selector = trim()
        if (selector.isBlank() || selector.any { it == '+' || it == '>' || it == '~' || it == '[' || it == '#' }) {
            return null
        }
        val cleaned = selector.replace(CssPseudoSelectorRegex, "")
        val parts = cleaned.split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        val target = parts.lastOrNull()?.toEpubCssSelectorPart() ?: return null
        val ancestorClasses = parts
            .dropLast(1)
            .flatMap { part -> CssClassRegex.findAll(part).map { it.groupValues[1] } }
            .toSet()
        if (target.tagName == null && target.requiredClasses.isEmpty()) return null
        return target.copy(requiredAncestorClasses = ancestorClasses)
    }

    private fun String.toEpubCssSelectorPart(): EpubCssSelector? {
        val tagName = CssTagRegex.find(this)?.groupValues?.getOrNull(1)?.lowercase()
        val classes = CssClassRegex.findAll(this)
            .map { it.groupValues[1] }
            .toSet()
        return EpubCssSelector(
            tagName = tagName,
            requiredClasses = classes,
            requiredAncestorClasses = emptySet(),
        )
    }

    private fun String.parseCssDeclarations(): ReadAlongTextStyle {
        var style = ReadAlongTextStyle()
        CssDeclarationRegex.findAll(this).forEach { match ->
            val name = match.groupValues[1].trim().lowercase()
            val value = match.groupValues[2].trim().lowercase()
            style = when (name) {
                "font-weight" -> style.merge(ReadAlongTextStyle(fontWeight = value.toReadAlongFontWeight()))
                "font-style" -> style.merge(ReadAlongTextStyle(fontStyle = value.toReadAlongFontStyle()))
                "font-size" -> style.merge(ReadAlongTextStyle(textSizeMultiplier = value.toFontSizeMultiplier()))
                "text-align" -> style.merge(ReadAlongTextStyle(textAlign = value.toReadAlongTextAlign()))
                "text-indent" -> style.merge(ReadAlongTextStyle(firstLineIndent = value.toFirstLineIndent()))
                else -> style
            }
        }
        return style
    }

    private fun String.toReadAlongFontWeight(): ReadAlongFontWeight? =
        when {
            contains("bold") -> ReadAlongFontWeight.Bold
            contains("normal") -> ReadAlongFontWeight.Normal
            toIntOrNull()?.let { it >= 600 } == true -> ReadAlongFontWeight.Bold
            toIntOrNull()?.let { it <= 500 } == true -> ReadAlongFontWeight.Normal
            else -> null
        }

    private fun String.toReadAlongFontStyle(): ReadAlongFontStyle? =
        when {
            contains("italic") || contains("oblique") -> ReadAlongFontStyle.Italic
            contains("normal") -> ReadAlongFontStyle.Normal
            else -> null
        }

    private fun String.toFontSizeMultiplier(): Float? {
        val number = CssNumberRegex.find(this)?.value?.toFloatOrNull() ?: return null
        return when {
            contains("%") -> (number / 100f).takeIf { it > 0f }
            contains("em") || contains("rem") -> number.takeIf { it > 0f }
            else -> number.takeIf { it > 0f && it <= 4f }
        }
    }

    private fun String.toReadAlongTextAlign(): ReadAlongTextAlign? =
        when {
            contains("center") -> ReadAlongTextAlign.Center
            contains("right") || contains("end") -> ReadAlongTextAlign.End
            contains("justify") -> ReadAlongTextAlign.Justify
            contains("left") || contains("start") -> ReadAlongTextAlign.Start
            else -> null
        }

    private fun String.toFirstLineIndent(): Boolean? {
        val number = CssNumberRegex.find(this)?.value?.toFloatOrNull() ?: return null
        return when {
            number <= 0f -> false
            else -> true
        }
    }

    private fun ReadAlongTextStyle.hasAnyTextStyle(): Boolean =
        hasInlineTextStyle() || textAlign != null || firstLineIndent != null

    private fun ReadAlongTextStyle.inlineOnly(): ReadAlongTextStyle =
        ReadAlongTextStyle(
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textSizeMultiplier = textSizeMultiplier,
        )

    private fun String.cleanReaderRawText(): String =
        replace(Regex("[\\t\\x0B\\f\\r]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("[ ]{2,}"), " ")
            .trim()

    private fun Float?.atLeast(value: Float): Boolean =
        this != null && this >= value

    private data class EpubTocItem(
        val id: String,
        val title: String,
        val level: Int,
        val href: String?,
    )

    private data class StaticReaderChapterSource(
        val sourceIndex: Int,
        val firstPath: String,
        val heading: String?,
        val segments: List<ReadAlongSegment>,
    )
}

private val CssCommentRegex = Regex("""(?s)/\*.*?\*/""")
private val CssBlockRegex = Regex("""(?s)([^{}]+)\{([^{}]*)\}""")
private val CssDeclarationRegex = Regex("""(?m)([-A-Za-z]+)\s*:\s*([^;]+)""")
private val CssClassRegex = Regex("""\.([A-Za-z0-9_-]+)""")
private val CssTagRegex = Regex("""^([A-Za-z][A-Za-z0-9_-]*)""")
private val CssPseudoSelectorRegex = Regex(""":[A-Za-z-]+(?:\([^)]*\))?""")
private val CssNumberRegex = Regex("""-?\d+(?:\.\d+)?""")
