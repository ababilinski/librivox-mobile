package com.librivox.mobile.model

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BookProgressSnapshot(
    val positionMs: Long = 0L,
    val lastActivityMillis: Long = 0L,
)

data class ChapterProgressSnapshot(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
)

class ProgressStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("audiobook_progress", Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    val dismissedContinueListeningBookIds: Set<String>
        get() = preferences.getStringSet(KEY_DISMISSED_CONTINUE_LISTENING, emptySet())
            .orEmpty().toSet()

    fun dismissFromContinueListening(bookId: String) {
        if (bookId.isBlank()) return
        val current = dismissedContinueListeningBookIds
        if (bookId in current) return
        preferences.edit { putStringSet(KEY_DISMISSED_CONTINUE_LISTENING, current + bookId) }
        notifyProgressChanged()
    }

    fun restoreToContinueListening(bookId: String) {
        val current = dismissedContinueListeningBookIds
        if (bookId !in current) return
        preferences.edit { putStringSet(KEY_DISMISSED_CONTINUE_LISTENING, current - bookId) }
        notifyProgressChanged()
    }

    fun savedPositionMs(bookId: String): Long =
        savedPositionMs(bookId, lastChapterId(bookId).orEmpty())

    fun savedPositionMs(bookId: String, chapterId: String): Long =
        preferences.getLong(positionKey(bookId, chapterId), 0L).coerceAtLeast(0L)

    fun savedChapterProgress(bookId: String, chapterId: String): ChapterProgressSnapshot {
        val positionMs = savedPositionMs(bookId, chapterId)
        val durationMs = preferences.getLong(durationKey(bookId, chapterId), 0L)
            .coerceAtLeast(0L)
        val completionKey = completedKey(bookId, chapterId)
        // Older versions saved the final position without a completion flag. Treat only
        // the endpoint (including 1 ms metadata rounding) as finished. An explicit false
        // from a replay always wins; elapsed percentage or chapter order proves nothing.
        val isCompleted = if (preferences.contains(completionKey)) {
            preferences.getBoolean(completionKey, false)
        } else {
            durationMs > 0L && positionMs > 0L && positionMs >= durationMs - 1L
        }
        return ChapterProgressSnapshot(
            positionMs = positionMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
        )
    }

    fun savedPositionsMs(bookIds: Collection<String>): Map<String, Long> =
        bookIds
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { savedPositionMs(it) }

    fun savedProgressSnapshots(bookIds: Collection<String>): Map<String, BookProgressSnapshot> =
        bookIds
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { bookId ->
                BookProgressSnapshot(
                    positionMs = savedPositionMs(bookId),
                    lastActivityMillis = lastActivityMillis(bookId),
                )
            }

    fun savePosition(
        bookId: String,
        chapterId: String,
        chapterIndex: Int,
        positionMs: Long,
        durationMs: Long = 0L,
    ) {
        preferences.edit {
            putLong(positionKey(bookId, chapterId), positionMs.coerceAtLeast(0L))
            // A player can temporarily report an unknown duration while loading or casting.
            if (durationMs > 0L) putLong(durationKey(bookId, chapterId), durationMs)
            putString(KEY_LAST_BOOK_ID, bookId)
            putString(lastChapterKey(bookId), chapterId)
            putInt(lastChapterIndexKey(bookId), chapterIndex.coerceAtLeast(0))
            putLong(lastActivityKey(bookId), System.currentTimeMillis())
        }
        notifyProgressChanged()
    }

    /** Completion is explicit listening history, independent of the resume position. */
    fun markChapterCompleted(bookId: String, chapterId: String, durationMs: Long = 0L) {
        if (bookId.isBlank() || chapterId.isBlank()) return
        preferences.edit {
            putBoolean(completedKey(bookId, chapterId), true)
            if (durationMs > 0L) putLong(durationKey(bookId, chapterId), durationMs)
        }
        notifyProgressChanged()
    }

    /** Undo completion without changing playback or moving book activity. */
    fun markChapterUnfinished(bookId: String, chapterId: String) {
        if (bookId.isBlank() || chapterId.isBlank()) return
        val previous = savedChapterProgress(bookId, chapterId)
        preferences.edit {
            putBoolean(completedKey(bookId, chapterId), false)
            // Keep a partial listen, but make a previously finished endpoint playable again.
            if (previous.durationMs > 0L && previous.positionMs >= previous.durationMs - 1L) {
                putLong(positionKey(bookId, chapterId), 0L)
            }
        }
        notifyProgressChanged()
    }

    /** Start a new listen only after an explicit replay action, not a passive player reset. */
    fun startChapterReplay(bookId: String, chapterId: String) {
        if (bookId.isBlank() || chapterId.isBlank()) return
        preferences.edit {
            putBoolean(completedKey(bookId, chapterId), false)
            putLong(positionKey(bookId, chapterId), 0L)
        }
        notifyProgressChanged()
    }

    fun saveChapterDuration(bookId: String, chapterId: String, durationMs: Long) {
        if (bookId.isBlank() || chapterId.isBlank() || durationMs <= 0L) return
        if (preferences.getLong(durationKey(bookId, chapterId), 0L) == durationMs) return
        preferences.edit { putLong(durationKey(bookId, chapterId), durationMs) }
        notifyProgressChanged()
    }

    fun lastBookId(): String =
        preferences.getString(KEY_LAST_BOOK_ID, AudioBookLibrary.defaultBook.id)
            ?: AudioBookLibrary.defaultBook.id

    fun lastChapterId(bookId: String): String? =
        preferences.getString(lastChapterKey(bookId), null)

    fun lastChapterIndex(bookId: String): Int =
        preferences.getInt(lastChapterIndexKey(bookId), 0).coerceAtLeast(0)

    fun lastActivityMillis(bookId: String): Long =
        preferences.getLong(lastActivityKey(bookId), 0L).coerceAtLeast(0L)

    fun saveLastBook(
        bookId: String,
        chapterId: String? = null,
        chapterIndex: Int = 0,
    ) {
        preferences.edit {
            putString(KEY_LAST_BOOK_ID, bookId)
            if (chapterId != null) {
                putString(lastChapterKey(bookId), chapterId)
                putInt(lastChapterIndexKey(bookId), chapterIndex.coerceAtLeast(0))
            }
            putLong(lastActivityKey(bookId), System.currentTimeMillis())
        }
        notifyProgressChanged()
    }

    fun markBookActivity(
        bookId: String,
        chapterId: String? = null,
        chapterIndex: Int = 0,
    ) {
        if (bookId.isBlank()) return
        preferences.edit {
            putString(KEY_LAST_BOOK_ID, bookId)
            if (chapterId != null) {
                putString(lastChapterKey(bookId), chapterId)
                putInt(lastChapterIndexKey(bookId), chapterIndex.coerceAtLeast(0))
            }
            putLong(lastActivityKey(bookId), System.currentTimeMillis())
        }
        notifyProgressChanged()
    }

    private fun positionKey(bookId: String, chapterId: String): String =
        "position_${bookId}_$chapterId"

    private fun durationKey(bookId: String, chapterId: String): String =
        "duration_${bookId}_$chapterId"

    private fun completedKey(bookId: String, chapterId: String): String =
        "completed_${bookId}_$chapterId"

    private fun lastChapterKey(bookId: String): String = "last_chapter_$bookId"

    private fun lastChapterIndexKey(bookId: String): String = "last_chapter_index_$bookId"

    private fun lastActivityKey(bookId: String): String = "last_activity_$bookId"

    private fun notifyProgressChanged() {
        _revision.update { it + 1L }
    }

    private companion object {
        const val KEY_LAST_BOOK_ID = "last_book_id"
        const val KEY_DISMISSED_CONTINUE_LISTENING = "dismissed_continue_listening_book_ids"
    }
}
