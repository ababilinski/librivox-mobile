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

private val Context.searchHistoryDataStore by preferencesDataStore(name = "audiobook_search_history")
private const val INCREMENTAL_QUERY_WINDOW_MS = 2L * 60 * 1000

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val searchedAtMillis: Long,
)

@Serializable
private data class SearchHistoryState(val entries: List<SearchHistoryEntry> = emptyList())

class SearchHistoryRepository(private val context: Context) {

    val entries: Flow<List<SearchHistoryEntry>> =
        context.searchHistoryDataStore.data
            .map { decode(it[STATE_JSON]).entries.withoutIncrementalPrefixes() }
            .catch { emit(emptyList()) }

    suspend fun snapshot(): List<SearchHistoryEntry> = entries.first()

    suspend fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        update { state ->
            val now = System.currentTimeMillis()
            val deduped = state.entries.filterNot {
                it.query.equals(trimmed, ignoreCase = true)
            }
            val next = (listOf(SearchHistoryEntry(trimmed, now)) + deduped)
                .withoutIncrementalPrefixes()
            state.copy(entries = next.take(MAX_ENTRIES))
        }
    }

    suspend fun remove(entry: SearchHistoryEntry) {
        update { state ->
            state.copy(
                entries = state.entries.filterNot {
                    it.query == entry.query
                },
            )
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(STATE_JSON) }
    }

    private suspend fun update(transform: (SearchHistoryState) -> SearchHistoryState) {
        context.searchHistoryDataStore.edit { prefs ->
            val current = decode(prefs[STATE_JSON])
            prefs[STATE_JSON] = json.encodeToString(transform(current))
        }
    }

    private fun decode(raw: String?): SearchHistoryState =
        raw?.let { runCatching { json.decodeFromString<SearchHistoryState>(it) }.getOrNull() }
            ?: SearchHistoryState()

    private companion object {
        const val MAX_ENTRIES = 10
        val STATE_JSON = stringPreferencesKey("history_json")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

private fun List<SearchHistoryEntry>.withoutIncrementalPrefixes(): List<SearchHistoryEntry> {
    val kept = mutableListOf<SearchHistoryEntry>()
    sortedByDescending { it.searchedAtMillis }.forEach { candidate ->
        val candidateQuery = candidate.query.trim()
        val isTypingPrefix = kept.any { newer ->
            val newerQuery = newer.query.trim()
            candidateQuery.length < newerQuery.length &&
                newerQuery.startsWith(candidateQuery, ignoreCase = true) &&
                newer.searchedAtMillis - candidate.searchedAtMillis in 0..INCREMENTAL_QUERY_WINDOW_MS
        }
        if (!isTypingPrefix) kept += candidate
    }
    return kept
}
