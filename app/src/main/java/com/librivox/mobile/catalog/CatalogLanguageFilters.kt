package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.isAllCatalogLanguagesSelected
import com.librivox.mobile.playback.normalizeForCatalogSource
import java.util.Locale

fun Set<BookLanguagePreference>.catalogLanguageRequestFilters(
    source: BookSourcePreference,
): List<String> {
    if (isAllCatalogLanguagesSelected(source)) return listOf("")
    return normalizeForCatalogSource(source)
        .map { it.apiValue }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("") }
}

fun List<String>.catalogLanguageCacheKey(): String =
    if (size == 1 && first().isBlank()) {
        ""
    } else {
        joinToString("|") { it.lowercase(Locale.ROOT) }
    }
