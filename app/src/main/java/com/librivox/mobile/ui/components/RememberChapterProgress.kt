package com.librivox.mobile.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.ChapterProgressSnapshot
import com.librivox.mobile.model.ProgressStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Both chapter surfaces can be composed at once. Limit metadata decoding across them.
private val chapterDurationReadMutex = Mutex()

@Composable
fun rememberChapterProgress(
    book: AudioBook,
    progressStore: ProgressStore,
): Map<String, ChapterProgressSnapshot> {
    val context = LocalContext.current.applicationContext
    val revision by progressStore.revision.collectAsState()
    val snapshots = remember(book.id, book.chapters, progressStore, revision) {
        book.chapters.associate { chapter ->
            chapter.id to progressStore.savedChapterProgress(book.id, chapter.id)
        }
    }

    // Older catalog entries can have no lengths even though their audio is downloaded.
    // Read local metadata off the main thread; never open a network stream for a row.
    LaunchedEffect(book.id, book.chapters, progressStore) {
        withContext(Dispatchers.IO) {
            for (chapter in book.chapters) {
                ensureActive()
                if (chapter.durationSeconds > 0L) continue
                if (chapter.localFileName == null && chapter.assetFileName == null) continue
                chapterDurationReadMutex.withLock {
                    if (progressStore.savedChapterProgress(book.id, chapter.id).durationMs <= 0L) {
                        localChapterDurationMs(context, chapter)?.let { durationMs ->
                            progressStore.saveChapterDuration(book.id, chapter.id, durationMs)
                        }
                    }
                }
            }
        }
    }
    return snapshots
}

internal fun localChapterDurationMs(context: Context, chapter: AudioBookChapter): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        when {
            chapter.localFileName != null -> {
                val file = File(context.filesDir, chapter.localFileName)
                if (!file.isFile) return null
                retriever.setDataSource(file.absolutePath)
            }
            chapter.assetFileName != null -> {
                context.assets.openFd(chapter.assetFileName).use { descriptor ->
                    retriever.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length,
                    )
                }
            }
            else -> return null
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}
