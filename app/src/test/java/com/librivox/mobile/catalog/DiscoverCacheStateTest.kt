package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.playback.BookSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCacheStateTest {
    @Test
    fun defaultSortOrder_isMostPopular() {
        assertEquals(DiscoverSortOrder.MostPopular, DiscoverCacheState().sortOrder)
    }

    @Test
    fun defaultSearchField_isAllFields() {
        assertEquals(CatalogSearchField.All, DiscoverCacheState().searchField)
    }

    @Test
    fun matches_requiresSameSortOrder() {
        val cache = DiscoverCacheState(
            query = "baczynski",
            searchField = CatalogSearchField.Author,
            sortOrder = DiscoverSortOrder.MostPopular,
            loadedAtMillis = 1L,
            isDefaultBrowse = false,
            bookSource = BookSourcePreference.WolneLekturyOnly,
        )

        assertTrue(
            cache.matches(
                query = "baczynski",
                field = CatalogSearchField.Author,
                language = "",
                bookSource = BookSourcePreference.WolneLekturyOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
        assertFalse(
            cache.matches(
                query = "baczynski",
                field = CatalogSearchField.Author,
                language = "",
                bookSource = BookSourcePreference.WolneLekturyOnly,
                sortOrder = DiscoverSortOrder.Alphabetical,
            ),
        )
    }

    @Test
    fun matches_requiresSameBookSource() {
        val cache = DiscoverCacheState(
            query = "gatsby",
            searchField = CatalogSearchField.Title,
            loadedAtMillis = 1L,
            isDefaultBrowse = false,
            bookSource = BookSourcePreference.GutendexOnly,
        )

        assertTrue(
            cache.matches(
                query = "gatsby",
                field = CatalogSearchField.Title,
                language = "",
                bookSource = BookSourcePreference.GutendexOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
        assertFalse(
            cache.matches(
                query = "gatsby",
                field = CatalogSearchField.Title,
                language = "",
                bookSource = BookSourcePreference.LibriVoxOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
    }

    @Test
    fun matches_reusesCachedLibriVoxAuthorSearch() {
        val cache = DiscoverCacheState(
            query = "Charles Dickens",
            searchField = CatalogSearchField.Author,
            loadedAtMillis = 1L,
            isDefaultBrowse = false,
            bookSource = BookSourcePreference.LibriVoxOnly,
        )

        assertTrue(
            cache.matches(
                query = "Charles Dickens",
                field = CatalogSearchField.Author,
                language = "",
                bookSource = BookSourcePreference.LibriVoxOnly,
                sortOrder = DiscoverSortOrder.MostPopular,
            ),
        )
    }

    @Test
    fun inMemoryReuseRejectsPartialFailedAndExpiredSearches() {
        val request = DiscoverLoadRequest(
            query = "alice",
            field = CatalogSearchField.Title,
            languages = listOf(""),
            languageKey = "",
            sourcePreference = BookSourcePreference.LibriVoxOnly,
            sortOrder = DiscoverSortOrder.MostPopular,
            reset = true,
            offset = 0,
        )
        val complete = DiscoverCacheState(
            query = "alice",
            searchField = CatalogSearchField.Title,
            sortOrder = DiscoverSortOrder.MostPopular,
            results = listOf(
                AudioBook(
                    id = "200",
                    title = "Alice's Adventures in Wonderland",
                    author = "Lewis Carroll",
                    source = BookSource.LibriVox,
                ),
            ),
            loadedAtMillis = 1_000L,
            isDefaultBrowse = false,
            bookSource = BookSourcePreference.LibriVoxOnly,
            isComplete = true,
        )

        assertTrue(complete.canReuseInMemory(request, "alice", nowMillis = 1_001L))
        assertFalse(complete.copy(isComplete = false).canReuseInMemory(request, "alice", 1_001L))
        assertFalse(complete.copy(errorMessage = "timeout").canReuseInMemory(request, "alice", 1_001L))
        assertFalse(
            complete.canReuseInMemory(
                request,
                "alice",
                nowMillis = 1_001L + CatalogPositiveFreshMillis,
            ),
        )
    }

    @Test
    fun authoritativeEmptyInMemoryReuseUsesFiveMinuteTtl() {
        val request = DiscoverLoadRequest(
            query = "no match",
            field = CatalogSearchField.Title,
            languages = listOf(""),
            languageKey = "",
            sourcePreference = BookSourcePreference.LibriVoxOnly,
            sortOrder = DiscoverSortOrder.MostPopular,
            reset = true,
            offset = 0,
        )
        val empty = DiscoverCacheState(
            query = "no match",
            searchField = CatalogSearchField.Title,
            loadedAtMillis = 1_000L,
            isDefaultBrowse = false,
            bookSource = BookSourcePreference.LibriVoxOnly,
            isComplete = true,
        )

        assertTrue(empty.canReuseInMemory(request, "no match", 1_000L + CatalogEmptyFreshMillis))
        assertFalse(empty.canReuseInMemory(request, "no match", 1_001L + CatalogEmptyFreshMillis))
    }
}
