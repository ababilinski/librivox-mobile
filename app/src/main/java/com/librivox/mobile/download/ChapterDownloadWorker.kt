package com.librivox.mobile.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryRepository
import java.io.File

class ChapterDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val chapterId = inputData.getString(KEY_CHAPTER_ID) ?: return Result.failure()
        val listenUrl = inputData.getString(KEY_LISTEN_URL) ?: return Result.failure()
        val targetFileName = inputData.getString(KEY_TARGET_FILE) ?: return Result.failure()
        val coverUrl = inputData.getString(KEY_COVER_URL)
        val coverTargetFileName = inputData.getString(KEY_COVER_TARGET_FILE)
        val repository = LibraryRepository(applicationContext)

        return runCatching {
            repository.updateChapterDownload(bookId, chapterId, DownloadState.Downloading)
            val target = File(applicationContext.filesDir, targetFileName)
            val temp = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()
            downloadHttpToFile(listenUrl, temp)
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            downloadCoverIfNeeded(
                coverUrl = coverUrl,
                targetFileName = coverTargetFileName,
                repository = repository,
                bookId = bookId,
            )
            repository.updateChapterDownload(
                bookId = bookId,
                chapterId = chapterId,
                downloadState = DownloadState.Downloaded,
                localFileName = targetFileName,
            )
            Result.success()
        }.getOrElse {
            File(applicationContext.filesDir, "$targetFileName.part").delete()
            repository.updateChapterDownload(bookId, chapterId, DownloadState.Failed)
            Result.failure()
        }
    }

    private suspend fun downloadCoverIfNeeded(
        coverUrl: String?,
        targetFileName: String?,
        repository: LibraryRepository,
        bookId: String,
    ) {
        if (coverUrl.isNullOrBlank() || targetFileName.isNullOrBlank()) return
        val target = File(applicationContext.filesDir, targetFileName)
        val cachedBook = repository.snapshot().bookById(bookId)
        val cachedCoverMatches = cachedBook?.localCoverFileName == targetFileName &&
            cachedBook.localCoverSourceUrl == coverUrl
        if (target.isFile && target.length() > 0L && cachedCoverMatches) {
            repository.updateBookLocalCover(
                bookId = bookId,
                localCoverFileName = targetFileName,
                localCoverSourceUrl = coverUrl,
            )
            return
        }

        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.part")
        runCatching {
            target.parentFile?.mkdirs()
            downloadHttpToFile(coverUrl, temp)
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            repository.updateBookLocalCover(
                bookId = bookId,
                localCoverFileName = targetFileName,
                localCoverSourceUrl = coverUrl,
            )
        }.onFailure {
            temp.delete()
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_CHAPTER_ID = "chapter_id"
        const val KEY_LISTEN_URL = "listen_url"
        const val KEY_TARGET_FILE = "target_file"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_COVER_TARGET_FILE = "cover_target_file"
    }
}
