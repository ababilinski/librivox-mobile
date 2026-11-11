package com.librivox.mobile.readalong

import android.content.Context
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookReadAlongSource
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.downloadableAudioEpubUrl
import com.librivox.mobile.model.downloadableBookEpubUrl
import com.librivox.mobile.model.downloadableDaisyUrl
import com.librivox.mobile.model.localAudioEpubFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadAlongRepository(
    private val context: Context,
    private val parser: WolneAudioEpubParser = WolneAudioEpubParser(),
    private val resolver: ReadAlongAssetResolver = ReadAlongAssetResolver(
        cacheDir = context.cacheDir,
        filesDir = context.filesDir,
    ),
) {
    suspend fun load(book: AudioBook): Result<ReadAlongBook> =
        withContext(Dispatchers.IO) {
            runCatching {
                loadReadAlongBook(book, parser, resolver)
            }
        }

    suspend fun loadPreferredChapter(
        book: AudioBook,
        preferredChapterId: String?,
    ): Result<ReadAlongBook> =
        withContext(Dispatchers.IO) {
            runCatching {
                loadReadAlongBook(
                    book = book,
                    parser = parser,
                    resolver = resolver,
                    preferredChapterId = preferredChapterId,
                    onlyPreferredChapter = true,
                )
            }
        }
}

internal fun loadReadAlongBook(
    book: AudioBook,
    parser: WolneAudioEpubParser,
    resolver: ReadAlongAssetResolver,
    preferredChapterId: String? = null,
    onlyPreferredChapter: Boolean = false,
): ReadAlongBook {
    if (book.readAlongSources.isNotEmpty()) {
        return loadCollectionReadAlongBook(book, parser, resolver, preferredChapterId, onlyPreferredChapter)
    }
    return loadSingleReadAlongBook(book, parser, resolver, preferredChapterId, onlyPreferredChapter)
}

private fun loadSingleReadAlongBook(
    book: AudioBook,
    parser: WolneAudioEpubParser,
    resolver: ReadAlongAssetResolver,
    preferredChapterId: String?,
    onlyPreferredChapter: Boolean,
): ReadAlongBook {
    var staticFallback: ReadAlongBook? = null
    var timedReadAlong: ReadAlongBook? = null
    val deferredStaticAssets = linkedSetOf<File>()
    fun parseAsset(file: File, deferStatic: Boolean): Result<ReadAlongBook> {
        if (deferStatic) deferredStaticAssets += file
        return runCatching {
            parser.parse(
                book = book,
                readAlongFile = file,
                preferredChapterId = preferredChapterId,
                onlyPreferredChapter = onlyPreferredChapter,
                allowStaticFallback = !deferStatic,
            )
        }
    }
    resolver.localDownloadedAsset(book)?.let { local ->
        parseAsset(local, deferStatic = onlyPreferredChapter).getOrNull()?.let { parsed ->
            if (parsed.hasTimedSync) {
                timedReadAlong = parsed
            } else {
                staticFallback = staticFallback.betterStaticFallback(parsed)
            }
        }
    }
    val candidates = resolver.candidates(book)
    val failures = mutableListOf<String>()
    for (candidate in candidates) {
        val hasCompleteTimedSource = timedReadAlong?.hasRequiredTimedCoverage(book, preferredChapterId, onlyPreferredChapter) == true
        // Show the requested timed chapter first; the full background load enriches
        // it with static EPUB footnotes and styles after the reader is usable.
        if (onlyPreferredChapter && hasCompleteTimedSource) return checkNotNull(timedReadAlong)
        if (hasCompleteTimedSource && staticFallback != null) break
        // Audio EPUB and DAISY contain the same recording. Once one supplies timings,
        // only ordinary EPUB is still needed for footnotes and source formatting.
        if (hasCompleteTimedSource && candidate.fileName != "book.epub") continue
        val parsed = runCatching {
            parseAsset(
                file = resolver.resolve(book, candidate),
                deferStatic = onlyPreferredChapter && candidate.fileName != "book.epub",
            ).getOrThrow()
        }
        parsed.getOrNull()?.let { readAlong ->
            if (readAlong.hasTimedSync) {
                timedReadAlong = timedReadAlong.betterTimedReadAlong(readAlong)
                return@let
            }
            staticFallback = staticFallback.betterStaticFallback(readAlong)
            return@let
        }
        failures += "${candidate.fileName}: ${parsed.exceptionOrNull()?.message ?: "Could not open file."}"
    }
    timedReadAlong?.let { return it.withFootnotesFrom(staticFallback) }
    if (staticFallback == null) {
        for (file in deferredStaticAssets) {
            val parsed = parseAsset(file, deferStatic = false).getOrNull() ?: continue
            staticFallback = staticFallback.betterStaticFallback(parsed)
        }
    }
    staticFallback?.let { return it }
    if (candidates.isEmpty()) error("This book does not include source-provided read-along text.")
    error("Could not open read-along text. ${failures.joinToString("; ")}")
}

private fun loadCollectionReadAlongBook(
    book: AudioBook,
    parser: WolneAudioEpubParser,
    resolver: ReadAlongAssetResolver,
    preferredChapterId: String?,
    onlyPreferredChapter: Boolean,
): ReadAlongBook {
    val sources = book.readAlongSources.sortedBy { source ->
        book.chapters.indexOfFirst { it.id in source.chapterIds }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }.let { ordered ->
        if (onlyPreferredChapter && preferredChapterId != null) {
            ordered.filter { preferredChapterId in it.chapterIds }
        } else if (onlyPreferredChapter) {
            ordered.take(1)
        } else {
            ordered
        }
    }
    val loaded = sources.mapNotNull { source ->
        val chapterIds = source.chapterIds.toSet()
        val volumeChapters = book.chapters.filter { it.id in chapterIds }
            .mapIndexed { index, chapter -> chapter.copy(number = index + 1) }
        if (volumeChapters.isEmpty()) return@mapNotNull null
        val volumeBook = book.copy(
            id = source.id,
            title = source.title,
            chapters = volumeChapters,
            epubUrl = source.epubUrl,
            audioEpubUrl = source.audioEpubUrl,
            daisyUrl = source.daisyUrl,
            readAlongSources = emptyList(),
            localAudioEpubFileName = null,
            localAudioEpubSourceUrl = null,
            audioEpubDownloadState = DownloadState.NotDownloaded,
        )
        runCatching {
            loadSingleReadAlongBook(volumeBook, parser, resolver, preferredChapterId, onlyPreferredChapter)
                .withSourceNamespace(source)
        }.getOrNull()
    }
    val loadedChapterIds = loaded.flatMap { it.chapters }.map { it.chapterId }.toSet()
    val needsFallback = loaded.isEmpty() || (!onlyPreferredChapter && book.chapters.any { it.id !in loadedChapterIds })
    val fallback = if (needsFallback) {
        runCatching {
            loadSingleReadAlongBook(book, parser, resolver, preferredChapterId, onlyPreferredChapter)
        }.getOrNull()
    } else {
        null
    }
    if (loaded.isEmpty()) return fallback ?: error("Could not open this book's source volumes.")

    val chapterById = loaded.flatMap { it.chapters }.associateBy { it.chapterId }
    val fallbackById = fallback?.chapters?.associateBy { it.chapterId }.orEmpty()
    val chapters = book.chapters.mapIndexedNotNull { index, chapter ->
        (chapterById[chapter.id] ?: fallbackById[chapter.id])?.copy(displayIndex = index + 1)
    }
    val tableOfContents = loaded.flatMap { it.tableOfContents } +
        fallback?.tableOfContents.orEmpty().filter { it.chapterId != null && it.chapterId !in loadedChapterIds }
    val order = chapters.mapIndexed { index, chapter -> chapter.chapterId to index }.toMap()
    return ReadAlongBook(
        bookId = book.id,
        title = book.title,
        chapters = chapters,
        hasTimedSync = chapters.any { chapter -> chapter.segments.any { it.clipBeginMs != null } },
        tableOfContents = tableOfContents.sortedBy { order[it.chapterId] ?: -1 },
        isPartial = onlyPreferredChapter || chapters.size < book.chapters.size,
    )
}

/** ZIP paths repeat between volumes; namespace them so navigation never jumps to another volume. */
private fun ReadAlongBook.withSourceNamespace(source: AudioBookReadAlongSource): ReadAlongBook {
    fun String.namespaced(): String = "${source.id}/$this"
    return copy(
        chapters = chapters.map { chapter ->
            chapter.copy(segments = chapter.segments.map { segment ->
                segment.copy(
                    id = segment.id.namespaced(),
                    textRef = segment.textRef.namespaced(),
                    audioRef = segment.audioRef?.namespaced(),
                    textBlockRef = segment.textBlockRef?.namespaced(),
                )
            })
        },
        tableOfContents = tableOfContents.map { item ->
            item.copy(
                id = item.id.namespaced(),
                href = item.href?.namespaced(),
                segmentId = item.segmentId?.namespaced(),
            )
        },
    )
}

data class ReadAlongAssetCandidate(
    val url: String,
    val fileName: String,
)

class ReadAlongAssetResolver(
    private val cacheDir: File,
    private val filesDir: File,
    private val downloader: (String, File) -> Unit = ::downloadReadAlongArchive,
) {
    fun localDownloadedAsset(book: AudioBook): File? =
        book.localAudioEpubFile(filesDir)

    fun candidates(book: AudioBook): List<ReadAlongAssetCandidate> =
        buildList {
            book.downloadableAudioEpubUrl()?.let { add(ReadAlongAssetCandidate(it, "book.audio.epub")) }
            book.downloadableDaisyUrl()?.let { add(ReadAlongAssetCandidate(it, "book.daisy.zip")) }
            book.downloadableBookEpubUrl()?.let { add(ReadAlongAssetCandidate(it, "book.epub")) }
        }

    fun resolve(book: AudioBook, candidate: ReadAlongAssetCandidate): File {
        val target = File(cacheDir, "readalong/${book.id.safeCacheSegment()}/${candidate.fileName}")
        // Catalog hydration can restart a reader while its previous IO load finishes.
        // Serialize only the shared cache publication; parsing remains independent.
        return synchronized(assetLocks.getOrPut(target.absolutePath) { Any() }) {
            resolveLocked(target, candidate)
        }
    }

    private fun resolveLocked(target: File, candidate: ReadAlongAssetCandidate): File {
        if (target.isFile && target.length() > 0L) {
            return target
        }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        if (temp.exists()) {
            temp.delete()
        }
        downloader(candidate.url, temp)
        check(temp.isFile && temp.length() > 0L) {
            "Download did not create ${temp.name}."
        }
        if (target.exists()) {
            target.delete()
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        check(target.isFile && target.length() > 0L) {
            "Download did not create ${target.name}."
        }
        return target
    }

    private companion object {
        val assetLocks = ConcurrentHashMap<String, Any>()
    }
}

private fun String.safeCacheSegment(): String =
    replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
        .trim('_')
        .ifBlank { "book" }

private fun ReadAlongBook?.betterStaticFallback(candidate: ReadAlongBook): ReadAlongBook {
    val current = this ?: return candidate
    return when {
        candidate.tableOfContents.isNotEmpty() && current.tableOfContents.isEmpty() -> candidate
        candidate.chapters.size > current.chapters.size && candidate.tableOfContents.size >= current.tableOfContents.size -> candidate
        candidate.chapters.sumOf { it.segments.size } > current.chapters.sumOf { it.segments.size } &&
            candidate.tableOfContents.size >= current.tableOfContents.size -> candidate
        else -> current
    }
}

private fun ReadAlongBook?.betterTimedReadAlong(candidate: ReadAlongBook): ReadAlongBook {
    val current = this ?: return candidate
    val candidateTextPathCount = candidate.uniqueTextPathCount()
    val currentTextPathCount = current.uniqueTextPathCount()
    return when {
        candidate.tableOfContents.isNotEmpty() && current.tableOfContents.isEmpty() -> candidate
        candidate.chapters.size > current.chapters.size -> candidate
        candidateTextPathCount > currentTextPathCount && candidate.chapters.size >= current.chapters.size -> candidate
        candidate.chapters.sumOf { it.segments.size } > current.chapters.sumOf { it.segments.size } &&
            candidateTextPathCount >= currentTextPathCount -> candidate
        else -> current
    }
}

private fun ReadAlongBook.uniqueTextPathCount(): Int =
    chapters
        .flatMap { chapter -> chapter.segments }
        .map { segment -> segment.textRef.substringBefore('#') }
        .distinct()
        .size

private fun ReadAlongBook.hasRequiredTimedCoverage(
    book: AudioBook,
    preferredChapterId: String?,
    onlyPreferredChapter: Boolean,
): Boolean {
    val readableChapters = book.chapters.filterNot { it.title.isSupplementalWolneAudioTitle() }
    val expectedIds = if (onlyPreferredChapter) {
        setOfNotNull(preferredChapterId ?: readableChapters.firstOrNull()?.id)
    } else {
        readableChapters.map { it.id }.toSet()
    }
    val timedChapters = chapters.filter { chapter -> chapter.segments.any { it.clipBeginMs != null } }
    return expectedIds.isNotEmpty() &&
        timedChapters.map { it.chapterId }.toSet().containsAll(expectedIds) &&
        (onlyPreferredChapter || uniqueTextPathCount() >= expectedIds.size)
}
