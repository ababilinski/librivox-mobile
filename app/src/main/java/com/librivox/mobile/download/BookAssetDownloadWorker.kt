package com.librivox.mobile.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryRepository
import java.io.File

class BookAssetDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.failure()
        val assetUrl = inputData.getString(KEY_ASSET_URL) ?: return Result.failure()
        val targetFileName = inputData.getString(KEY_TARGET_FILE) ?: return Result.failure()
        val updateLibraryState = inputData.getBoolean(KEY_UPDATE_LIBRARY_STATE, true)
        val repository = LibraryRepository(applicationContext)
        var temp: File? = null

        return runCatching {
            if (updateLibraryState) {
                repository.updateBookAudioEpubDownload(bookId, DownloadState.Downloading)
            }
            val target = File(applicationContext.filesDir, targetFileName)
            val tempFile = File(target.parentFile, "${target.name}.part")
            temp = tempFile
            target.parentFile?.mkdirs()
            downloadHttpToFile(assetUrl, tempFile)
            if (target.exists()) {
                target.delete()
            }
            if (!tempFile.renameTo(target)) {
                tempFile.copyTo(target, overwrite = true)
                tempFile.delete()
            }
            if (updateLibraryState) {
                repository.updateBookAudioEpubDownload(
                    bookId = bookId,
                    downloadState = DownloadState.Downloaded,
                    localFileName = targetFileName,
                    sourceUrl = assetUrl,
                )
            }
            Result.success()
        }.getOrElse {
            temp?.delete()
            if (updateLibraryState) {
                repository.updateBookAudioEpubDownload(bookId, DownloadState.Failed)
            }
            Result.failure()
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_ASSET_URL = "asset_url"
        const val KEY_TARGET_FILE = "target_file"
        const val KEY_UPDATE_LIBRARY_STATE = "update_library_state"
    }
}
