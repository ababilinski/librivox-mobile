package com.librivox.mobile.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.librivox.mobile.model.LibraryRepository
import java.io.File

class BookCoverDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val coverUrl = inputData.getString(KEY_COVER_URL) ?: return Result.failure()
        val targetFileName = inputData.getString(KEY_COVER_TARGET_FILE) ?: return Result.failure()
        val repository = LibraryRepository(applicationContext)
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
            return Result.success()
        }

        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.part")
        return runCatching {
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
            Result.success()
        }.getOrElse {
            temp.delete()
            Result.failure()
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_COVER_TARGET_FILE = "cover_target_file"
    }
}
