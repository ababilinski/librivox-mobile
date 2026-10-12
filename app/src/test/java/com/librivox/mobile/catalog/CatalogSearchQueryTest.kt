package com.librivox.mobile.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchQueryTest {

    @Test
    fun sanitizeCatalogSearchQuery_usesMarkdownLabelForPersonLinks() {
        assertEquals(
            "Kate Chopin",
            sanitizeCatalogSearchQuery("[Kate Chopin](https://librivox.org/author/433)"),
        )
    }

    @Test
    fun sanitizeCatalogSearchQuery_keepsReadableCollectionForArchiveChapterLinks() {
        val query = sanitizeCatalogSearchQuery(
            "[The Story of an Hour](https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_storyofanhour_add.mp3)",
        )

        assertEquals("The Story of an Hour Short Story Collection Vol. 026", query)
    }

    @Test
    fun catalogSearchQueryVariants_normalizesCompactVolumeNotation() {
        val variants = catalogSearchQueryVariants("short story collection vol.026")

        assertTrue(variants.contains("short story collection Vol. 026"))
    }

    @Test
    fun catalogSearchQueryVariants_stripsLeadingArticlesForSourceTitleLookups() {
        val variants = catalogSearchQueryVariants("The Great Gatsby")

        assertTrue(variants.contains("Great Gatsby"))
    }

    @Test
    fun catalogSearchQueryVariants_extractsCollectionFromArchiveChapterMarkdown() {
        val variants = catalogSearchQueryVariants(
            "[The Story of an Hour](https://www.archive.org/download/short_story_026_0804_librivox/shortstory026_storyofanhour_add.mp3)",
        )

        assertTrue(variants.contains("Short Story Collection Vol. 026"))
    }

    @Test
    fun normalizedAutocompleteSearchText_handlesPolishCharacters() {
        assertEquals("boleslaw prus", normalizedAutocompleteSearchText("Bolesław Prus"))
    }

    @Test
    fun autocompleteFtsPrefixQuery_buildsBoundedPrefixTokens() {
        assertEquals(
            "short* story* collection* vol* 026*",
            autocompleteFtsPrefixQuery("short story collection vol 026"),
        )
    }
}
