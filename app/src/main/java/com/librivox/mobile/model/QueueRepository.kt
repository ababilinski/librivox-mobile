package com.librivox.mobile.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.queueDataStore by preferencesDataStore(name = "audiobook_queue")

@Serializable
private data class QueuePersistedState(val entries: List<QueueEntry> = emptyList())

class QueueRepository(private val context: Context) {

    val entries: Flow<List<QueueEntry>> =
        context.queueDataStore.data
            .map { decode(it[QUEUE_JSON]).entries }
            .catch { emit(emptyList()) }

    suspend fun snapshot(): List<QueueEntry> = entries.first()

    suspend fun playNext(bookId: String, chapterId: String? = null) {
        update { state ->
            val now = System.currentTimeMillis()
            val withoutSame = state.entries.filterNot { it.bookId == bookId && it.chapterId == chapterId }
            state.copy(entries = listOf(QueueEntry(bookId, chapterId, now)) + withoutSame)
        }
    }

    suspend fun addToQueue(bookId: String, chapterId: String? = null) {
        update { state ->
            val now = System.currentTimeMillis()
            if (state.entries.any { it.bookId == bookId && it.chapterId == chapterId }) {
                state
            } else {
                state.copy(entries = state.entries + QueueEntry(bookId, chapterId, now))
            }
        }
    }

    suspend fun remove(bookId: String, chapterId: String?) {
        update { state ->
            state.copy(entries = state.entries.filterNot { it.bookId == bookId && it.chapterId == chapterId })
        }
    }

    suspend fun reorder(fromIndex: Int, toIndex: Int) {
        update { state ->
            val entries = state.entries.toMutableList()
            if (fromIndex !in entries.indices || toIndex !in entries.indices) {
                state
            } else {
                val moved = entries.removeAt(fromIndex)
                entries.add(toIndex, moved)
                state.copy(entries = entries)
            }
        }
    }

    suspend fun clear() {
        context.queueDataStore.edit { it.remove(QUEUE_JSON) }
    }

    private suspend fun update(transform: (QueuePersistedState) -> QueuePersistedState) {
        context.queueDataStore.edit { preferences ->
            val current = decode(preferences[QUEUE_JSON])
            preferences[QUEUE_JSON] = json.encodeToString(transform(current))
        }
    }

    private fun decode(raw: String?): QueuePersistedState =
        raw?.let { runCatching { json.decodeFromString<QueuePersistedState>(it) }.getOrNull() }
            ?: QueuePersistedState()

    private companion object {
        val QUEUE_JSON = stringPreferencesKey("queue_json")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
