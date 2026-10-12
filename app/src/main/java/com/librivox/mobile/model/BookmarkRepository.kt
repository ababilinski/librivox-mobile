package com.librivox.mobile.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.bookmarkDataStore by preferencesDataStore(name = "audiobook_bookmarks")

@Serializable
private data class BookmarkPersistedState(val bookmarks: List<Bookmark> = emptyList())

class BookmarkRepository(private val context: Context) {

    val bookmarks: Flow<List<Bookmark>> =
        context.bookmarkDataStore.data
            .map { decode(it[BOOKMARKS_JSON]).bookmarks }
            .catch { emit(emptyList()) }

    suspend fun snapshot(): List<Bookmark> = bookmarks.first()

    fun forBook(bookId: String): Flow<List<Bookmark>> =
        kotlinx.coroutines.flow.flow {
            bookmarks.collect { all ->
                emit(all.filter { it.bookId == bookId }.sortedByDescending { it.createdAtMillis })
            }
        }

    suspend fun add(
        bookId: String,
        chapterId: String,
        positionMs: Long,
        note: String,
    ): Bookmark {
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterId = chapterId,
            positionMs = positionMs.coerceAtLeast(0L),
            note = note.trim(),
            createdAtMillis = System.currentTimeMillis(),
        )
        update { state -> state.copy(bookmarks = state.bookmarks + bookmark) }
        return bookmark
    }

    suspend fun updateNote(bookmarkId: String, note: String) {
        update { state ->
            state.copy(
                bookmarks = state.bookmarks.map {
                    if (it.id == bookmarkId) it.copy(note = note.trim()) else it
                },
            )
        }
    }

    suspend fun restore(bookmark: Bookmark) {
        update { state ->
            state.copy(
                bookmarks = state.bookmarks
                    .filterNot { it.id == bookmark.id }
                    .plus(bookmark),
            )
        }
    }

    suspend fun remove(bookmarkId: String) {
        update { state ->
            state.copy(bookmarks = state.bookmarks.filterNot { it.id == bookmarkId })
        }
    }

    suspend fun removeForBook(bookId: String) {
        update { state ->
            state.copy(bookmarks = state.bookmarks.filterNot { it.bookId == bookId })
        }
    }

    suspend fun clearAll() {
        context.bookmarkDataStore.edit { it.remove(BOOKMARKS_JSON) }
    }

    private suspend fun update(transform: (BookmarkPersistedState) -> BookmarkPersistedState) {
        context.bookmarkDataStore.edit { preferences ->
            val current = decode(preferences[BOOKMARKS_JSON])
            preferences[BOOKMARKS_JSON] = json.encodeToString(transform(current))
        }
    }

    private fun decode(raw: String?): BookmarkPersistedState =
        raw?.let {
            runCatching { json.decodeFromString<BookmarkPersistedState>(it) }.getOrNull()
        } ?: BookmarkPersistedState()

    private companion object {
        val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
