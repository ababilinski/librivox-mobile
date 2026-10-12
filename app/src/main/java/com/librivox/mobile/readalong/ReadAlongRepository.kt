package com.librivox.mobile.readalong

import android.content.Context
import com.librivox.mobile.download.downloadHttpToFile
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.downloadableAudioEpubUrl
import com.librivox.mobile.model.downloadableBookEpubUrl
import com.librivox.mobile.model.downloadableDaisyUrl
import com.librivox.mobile.model.localAudioEpubFile
import java.io.File
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
    var staticFallback: ReadAlongBook? = null
    var timedReadAlong: ReadAlongBook? = null
    resolver.localDownloadedAsset(book)?.let { local ->
        runCatching {
            parser.parse(
                book = book,
                readAlongFile = local,
                preferredChapterId = preferredChapterId,
                onlyPreferredChapter = onlyPreferredChapter,
            )
        }.getOrNull()?.let { parsed ->
            if (parsed.hasTimedSync) {
                timedReadAlong = parsed
            } else {
                staticFallback = staticFallback.betterStaticFallback(parsed)
            }
        }
    }
    val candidates = resolver.candidates(book)
    if (candidates.isEmpty()) {
        timedReadAlong?.let { return it.withFootnotesFrom(staticFallback) }
        staticFallback?.let { return it }
        error("This book does not include source-provided read-along text.")
    }
    val failures = mutableListOf<String>()
    for (candidate in candidates) {
        if (timedReadAlong != null && staticFallback != null) break
        val parsed = runCatching {
            parser.parse(
                book = book,
                readAlongFile = resolver.resolve(book, candidate),
                preferredChapterId = preferredChapterId,
                onlyPreferredChapter = onlyPreferredChapter,
            )
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
    staticFallback?.let { return it }
    error("Could not open read-along text. ${failures.joinToString("; ")}")
}

data class ReadAlongAssetCandidate(
    val url: String,
    val fileName: String,
)

class ReadAlongAssetResolver(
    private val cacheDir: File,
    private val filesDir: File,
    private val downloader: (String, File) -> Unit = ::downloadHttpToFile,
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
