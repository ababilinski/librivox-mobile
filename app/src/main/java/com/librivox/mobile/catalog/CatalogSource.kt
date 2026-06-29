package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook

interface CatalogSource {
    suspend fun featuredBooks(): List<AudioBook>

    suspend fun fetchByIds(vararg ids: String): List<AudioBook>

    suspend fun search(
        query: String,
        field: CatalogSearchField = CatalogSearchField.All,
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        language: String = "",
    ): List<AudioBook>

    suspend fun browse(
        limit: Int = DEFAULT_PAGE_SIZE,
        offset: Int = 0,
        language: String = "",
    ): List<AudioBook>

    suspend fun recent(sinceEpochSeconds: Long, limit: Int = 20): List<AudioBook>

    suspend fun byGenre(genre: String, limit: Int = 20, language: String = ""): List<AudioBook>

    suspend fun byAuthor(author: String, limit: Int = 20, language: String = ""): List<AudioBook>

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
    }
}
