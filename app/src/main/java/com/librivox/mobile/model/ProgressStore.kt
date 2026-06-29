package com.librivox.mobile.model

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BookProgressSnapshot(
    val positionMs: Long = 0L,
    val lastActivityMillis: Long = 0L,
)

class ProgressStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("audiobook_progress", Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun savedPositionMs(bookId: String): Long =
        savedPositionMs(bookId, lastChapterId(bookId).orEmpty())

    fun savedPositionMs(bookId: String, chapterId: String): Long =
        preferences.getLong(positionKey(bookId, chapterId), 0L).coerceAtLeast(0L)

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
    ) {
        preferences.edit {
            putLong(positionKey(bookId, chapterId), positionMs.coerceAtLeast(0L))
            putString(KEY_LAST_BOOK_ID, bookId)
            putString(lastChapterKey(bookId), chapterId)
            putInt(lastChapterIndexKey(bookId), chapterIndex.coerceAtLeast(0))
            putLong(lastActivityKey(bookId), System.currentTimeMillis())
        }
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

    private fun lastChapterKey(bookId: String): String = "last_chapter_$bookId"

    private fun lastChapterIndexKey(bookId: String): String = "last_chapter_index_$bookId"

    private fun lastActivityKey(bookId: String): String = "last_activity_$bookId"

    private fun notifyProgressChanged() {
        _revision.value = _revision.value + 1L
    }

    private companion object {
        const val KEY_LAST_BOOK_ID = "last_book_id"
    }
}
