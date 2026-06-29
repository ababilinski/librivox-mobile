package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogLanguageFiltersTest {
    @Test
    fun catalogLanguageRequestFilters_allowsWolneOnlyPolishSelection() {
        val filters = setOf(BookLanguagePreference.Polish)
            .catalogLanguageRequestFilters(BookSourcePreference.WolneLekturyOnly)

        assertEquals(listOf(""), filters)
    }

    @Test
    fun catalogLanguageRequestFilters_keepsPolishSpecificForMixedSources() {
        val filters = setOf(BookLanguagePreference.Polish)
            .catalogLanguageRequestFilters(BookSourcePreference.AllWithGutendex)

        assertEquals(listOf("Polish"), filters)
    }

    @Test
    fun catalogLanguageRequestFilters_keepsNonPolishSpecificForMixedSources() {
        val filters = setOf(BookLanguagePreference.English)
            .catalogLanguageRequestFilters(BookSourcePreference.AllWithGutendex)

        assertEquals(listOf("English"), filters)
    }
}
