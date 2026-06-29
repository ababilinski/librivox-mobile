package com.librivox.mobile.download

import android.content.Context
import androidx.work.Data
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSourceAssetDownload
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.downloadableReadAlongAssets
import com.librivox.mobile.model.downloadableCoverImageUrl
import com.librivox.mobile.model.downloadedAudioEpubFileName
import com.librivox.mobile.model.downloadedCoverFileName
import com.librivox.mobile.model.downloadedFileName
import com.librivox.mobile.model.localCoverFile
import com.librivox.mobile.playback.PlaybackSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class DownloadUndoToken internal constructor(
    internal val items: List<StagedDownloadDelete>,
)

class StagedDownloadDelete internal constructor(
    val bookId: String,
    val chapterId: String,
    val localFileName: String?,
    internal val stagedFile: File?,
)

/**
 * Thin façade around [ChapterDownloadWorker] so the UI never enqueues
 * WorkManager requests directly. Keeps download-policy decisions (constraints,
 * unique work naming, state stamping) in one place.
 */
class DownloadManager(
    private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val playbackSettingsStore: PlaybackSettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun downloadChapter(book: AudioBook, chapter: AudioBookChapter) {
        if (chapter.listenUrl.isNullOrBlank()) return
        libraryRepository.upsertCatalogBooks(listOf(book))
        enqueueCoverRefreshIfNeeded(book)
        libraryRepository.updateChapterDownload(book.id, chapter.id, DownloadState.Queued)
        enqueue(book, chapter)
    }

    suspend fun downloadAll(book: AudioBook) {
        val missing = book.chapters.filter {
            it.localFileName == null &&
                it.assetFileName == null &&
                !it.listenUrl.isNullOrBlank()
        }
        val readAlongAssets = book.downloadableReadAlongAssets()
        val missingReadAlongAssets = readAlongAssets.filter { asset ->
            !File(context.filesDir, asset.targetFileName).isFile &&
                (
                    !asset.primary ||
                        (
                            book.localAudioEpubFileName == null &&
                                book.audioEpubDownloadState != DownloadState.Queued &&
                                book.audioEpubDownloadState != DownloadState.Downloading
                        )
                )
        }
        libraryRepository.upsertCatalogBooks(listOf(book))
        enqueueCoverRefreshIfNeeded(book)
        if (missing.isEmpty() && missingReadAlongAssets.isEmpty()) return
        if (missing.isNotEmpty()) {
            libraryRepository.markMissingChaptersQueued(book.id, missing.mapTo(mutableSetOf()) { it.id })
        }
        if (missingReadAlongAssets.any { it.primary }) {
            libraryRepository.updateBookAudioEpubDownload(book.id, DownloadState.Queued)
        }
        missingReadAlongAssets.forEach { asset ->
            enqueueReadAlongAsset(book, asset)
        }
        missing.forEach { enqueue(book, it) }
    }

    suspend fun downloadCover(book: AudioBook) {
        libraryRepository.upsertCatalogBooks(listOf(book))
        enqueueCoverRefreshIfNeeded(book)
    }

    fun cancelDownload(book: AudioBook, chapter: AudioBookChapter) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(book.id, chapter.id))
        scope.launch {
            libraryRepository.updateChapterDownload(book.id, chapter.id, DownloadState.NotDownloaded)
        }
    }

    suspend fun deleteDownload(book: AudioBook, chapter: AudioBookChapter) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(book.id, chapter.id))
        chapter.localFileName?.let { fileName ->
            val file = java.io.File(context.filesDir, fileName)
            if (file.exists()) file.delete()
        }
        libraryRepository.updateChapterDownload(
            book.id,
            chapter.id,
            DownloadState.NotDownloaded,
            clearLocalFileName = true,
        )
    }

    suspend fun deleteDownloads(book: AudioBook) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(coverWorkName(book.id))
        workManager.cancelUniqueWork(audioEpubWorkName(book.id))
        ReadAlongAssetFileNames.forEach { fileName ->
            workManager.cancelUniqueWork(readAlongAssetWorkName(book.id, fileName))
        }
        book.chapters.forEach { chapter ->
            workManager.cancelUniqueWork(workName(book.id, chapter.id))
            chapter.localFileName?.let { fileName ->
                val file = File(context.filesDir, fileName)
                if (file.exists()) file.delete()
            }
        }
        book.localCoverFile(context.filesDir)?.delete()
        File(context.filesDir, book.downloadedCoverFileName())
            .parentFile
            ?.listFiles { file ->
                file.isFile &&
                    (file.name.startsWith("cover.") || file.name.startsWith("cover-"))
            }
            ?.forEach { it.delete() }
        book.localAudioEpubFileName?.let { fileName ->
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
        }
        File(context.filesDir, book.downloadedAudioEpubFileName()).takeIf { it.exists() }?.delete()
        val offlineDirectory = File(context.filesDir, File(book.downloadedAudioEpubFileName()).parent.orEmpty())
        ReadAlongAssetFileNames.forEach { fileName ->
            File(offlineDirectory, fileName).takeIf { it.exists() }?.delete()
        }
        libraryRepository.clearBookDownloads(book.id)
    }

    suspend fun stageDeleteDownload(book: AudioBook, chapter: AudioBookChapter): DownloadUndoToken? =
        stageDeleteDownloads(book, listOf(chapter)).takeIf { it.items.isNotEmpty() }

    suspend fun stageDeleteDownloads(
        book: AudioBook,
        chapters: List<AudioBookChapter>,
    ): DownloadUndoToken {
        val workManager = WorkManager.getInstance(context)
        val staged = chapters.mapNotNull { chapter ->
            workManager.cancelUniqueWork(workName(book.id, chapter.id))
            val localFileName = chapter.localFileName
            val stagedFile = localFileName?.let { fileName ->
                stageFile(File(context.filesDir, fileName))
            }
            if (localFileName == null && stagedFile == null && chapter.downloadState == DownloadState.NotDownloaded) {
                null
            } else {
                libraryRepository.updateChapterDownload(
                    book.id,
                    chapter.id,
                    DownloadState.NotDownloaded,
                    clearLocalFileName = true,
                )
                StagedDownloadDelete(
                    bookId = book.id,
                    chapterId = chapter.id,
                    localFileName = localFileName,
                    stagedFile = stagedFile,
                )
            }
        }
        return DownloadUndoToken(staged)
    }

    suspend fun restoreStagedDownloads(token: DownloadUndoToken) {
        token.items.forEach { item ->
            val localFileName = item.localFileName
            val stagedFile = item.stagedFile
            if (localFileName != null && stagedFile?.isFile == true) {
                val targetFile = File(context.filesDir, localFileName)
                targetFile.parentFile?.mkdirs()
                if (!stagedFile.renameTo(targetFile)) {
                    stagedFile.copyTo(targetFile, overwrite = true)
                    stagedFile.delete()
                }
                libraryRepository.updateChapterDownload(
                    item.bookId,
                    item.chapterId,
                    DownloadState.Downloaded,
                    localFileName = localFileName,
                )
            }
        }
    }

    fun discardStagedDownloads(token: DownloadUndoToken) {
        token.items.forEach { item ->
            item.stagedFile?.takeIf { it.exists() }?.delete()
        }
    }

    private fun enqueue(book: AudioBook, chapter: AudioBookChapter) {
        val listenUrl = chapter.listenUrl ?: return
        val targetFile = chapter.downloadedFileName(book.id)
        val request = OneTimeWorkRequestBuilder<ChapterDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(playbackSettingsStore.downloadNetworkPolicy.audioWorkNetworkType)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(ChapterDownloadWorker.KEY_BOOK_ID, book.id)
                    .putString(ChapterDownloadWorker.KEY_CHAPTER_ID, chapter.id)
                    .putString(ChapterDownloadWorker.KEY_LISTEN_URL, listenUrl)
                    .putString(ChapterDownloadWorker.KEY_TARGET_FILE, targetFile)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(book.id, chapter.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun enqueueCoverRefreshIfNeeded(book: AudioBook) {
        val coverUrl = book.downloadableCoverImageUrl() ?: return
        val targetFile = book.downloadedCoverFileName()
        val localCoverFile = book.localCoverFile(context.filesDir)
        if (localCoverFile?.isFile == true && localCoverFile.length() > 0L) {
            val detectedCoverFileName = localCoverFile
                .relativeTo(context.filesDir)
                .invariantSeparatorsPath
            if (book.localCoverSourceUrl == coverUrl && detectedCoverFileName == targetFile) {
                if (book.localCoverFileName != detectedCoverFileName) {
                    libraryRepository.updateBookLocalCover(
                        book.id,
                        detectedCoverFileName,
                        coverUrl,
                    )
                }
                return
            }
        }
        val request = OneTimeWorkRequestBuilder<BookCoverDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(playbackSettingsStore.downloadNetworkPolicy.bookInfoWorkNetworkType)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(BookCoverDownloadWorker.KEY_BOOK_ID, book.id)
                    .putString(BookCoverDownloadWorker.KEY_COVER_URL, coverUrl)
                    .putString(BookCoverDownloadWorker.KEY_COVER_TARGET_FILE, targetFile)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            coverWorkName(book.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun enqueueReadAlongAsset(book: AudioBook, asset: BookSourceAssetDownload) {
        val request = OneTimeWorkRequestBuilder<BookAssetDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(playbackSettingsStore.downloadNetworkPolicy.bookInfoWorkNetworkType)
                    .build(),
            )
            .setInputData(
                Data.Builder()
                    .putString(BookAssetDownloadWorker.KEY_BOOK_ID, book.id)
                    .putString(BookAssetDownloadWorker.KEY_ASSET_URL, asset.url)
                    .putString(BookAssetDownloadWorker.KEY_TARGET_FILE, asset.targetFileName)
                    .putBoolean(BookAssetDownloadWorker.KEY_UPDATE_LIBRARY_STATE, asset.primary)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            readAlongAssetWorkName(book.id, asset.targetFileName),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun workName(bookId: String, chapterId: String): String =
        "download::$bookId::$chapterId"

    private fun coverWorkName(bookId: String): String =
        "download-cover::$bookId"

    private fun audioEpubWorkName(bookId: String): String =
        "download-audio-epub::$bookId"

    private fun readAlongAssetWorkName(bookId: String, targetFileName: String): String =
        "download-book-asset::$bookId::${targetFileName.substringAfterLast('/')}"

    private fun stageFile(source: File): File? {
        if (!source.isFile) return null
        val stagedDir = File(context.cacheDir, "download-delete-undo").apply { mkdirs() }
        val stagedFile = File(stagedDir, "${System.nanoTime()}-${source.name}")
        return runCatching {
            if (!source.renameTo(stagedFile)) {
                source.copyTo(stagedFile, overwrite = true)
                source.delete()
            }
            stagedFile
        }.getOrNull()
    }

    private companion object {
        val ReadAlongAssetFileNames = listOf(
            "book.audio.epub",
            "book.daisy.zip",
            "book.epub",
        )
    }
}
