package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.playback.BookSourcePreference

enum class DiscoverSortOrder {
    Alphabetical,
    MostPopular,
}

data class DiscoverCacheState(
    val searchField: CatalogSearchField = CatalogSearchField.All,
    val query: String = "",
    val sortOrder: DiscoverSortOrder = DiscoverSortOrder.MostPopular,
    val results: List<AudioBook> = emptyList(),
    val nextOffset: Int = 0,
    val canLoadMore: Boolean = true,
    val loadedAtMillis: Long = 0L,
    val isDefaultBrowse: Boolean = true,
    val language: String = "",
    val bookSource: BookSourcePreference = BookSourcePreference.Default,
    val isLoading: Boolean = false,
    val loadingReset: Boolean = false,
    val errorMessage: String? = null,
) {
    fun describes(
        query: String,
        field: CatalogSearchField,
        language: String,
        bookSource: BookSourcePreference,
        sortOrder: DiscoverSortOrder,
    ): Boolean =
        this.query == query &&
            searchField == field &&
            this.sortOrder == sortOrder &&
            isDefaultBrowse == query.isBlank() &&
            this.language == language &&
            this.bookSource == bookSource

    fun matches(
        query: String,
        field: CatalogSearchField,
        language: String,
        bookSource: BookSourcePreference,
        sortOrder: DiscoverSortOrder,
    ): Boolean =
        loadedAtMillis > 0L &&
            describes(
                query = query,
                field = field,
                language = language,
                bookSource = bookSource,
                sortOrder = sortOrder,
            )
}

data class DiscoverPrefill(
    val query: String,
    val field: CatalogSearchField,
    val language: String = "",
)
