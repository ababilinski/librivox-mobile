package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSearchFacetsTest {

    @Test
    fun facetCounts_includeEverySourceAndSearchField() {
        val books = listOf(
            book(id = "title", title = "River Stories", source = BookSource.LibriVox),
            book(id = "author", author = "River Writer", source = BookSource.Lit2Go),
            book(
                id = "chapter",
                source = BookSource.WolneLektury,
                language = "Polish",
                chapters = listOf(chapter(title = "River Crossing")),
            ),
            book(
                id = "reader",
                source = BookSource.LibriVox,
                chapters = listOf(chapter(title = "Opening", reader = "River Voice")),
            ),
        )

        val counts = books.catalogSearchFacetCounts(
            query = "River",
            selectedField = CatalogSearchField.All,
            selectedSource = null,
            selectedLanguage = null,
            availableSources = listOf(
                CatalogSourcePreference.LibriVox,
                CatalogSourcePreference.Lit2Go,
                CatalogSourcePreference.WolneLektury,
            ),
            availableLanguages = listOf("English", "Polish"),
        )

        assertEquals(4, counts.total)
        assertEquals(2, counts.sourceCounts[CatalogSourcePreference.LibriVox])
        assertEquals(1, counts.sourceCounts[CatalogSourcePreference.Lit2Go])
        assertEquals(1, counts.sourceCounts[CatalogSourcePreference.WolneLektury])
        assertEquals(1, counts.fieldCounts[CatalogSearchField.Title])
        assertEquals(1, counts.fieldCounts[CatalogSearchField.Author])
        assertEquals(1, counts.fieldCounts[CatalogSearchField.Chapter])
        assertEquals(1, counts.fieldCounts[CatalogSearchField.Reader])
        assertEquals(4, counts.fieldCounts[CatalogSearchField.All])
        assertEquals(3, counts.languageCounts["English"])
        assertEquals(1, counts.languageCounts["Polish"])
    }

    @Test
    fun hitFacetCounts_dedupeWorkWhilePreservingEveryContributingSource() {
        val hits = listOf(
            CatalogSearchHit(
                book = book(
                    id = "librivox-common-sense",
                    title = "Common Sense",
                    author = "Thomas Paine",
                    source = BookSource.LibriVox,
                ),
                matchedFields = setOf(CatalogSearchField.Title),
                contributingSources = setOf(CatalogSourcePreference.LibriVox),
                provenance = CatalogResultProvenance.Network,
            ),
            CatalogSearchHit(
                book = book(
                    id = "lit2go-common-sense",
                    title = "Common Sense",
                    author = "Thomas Paine",
                    source = BookSource.Lit2Go,
                ),
                matchedFields = setOf(CatalogSearchField.Title),
                contributingSources = setOf(CatalogSourcePreference.Lit2Go),
                provenance = CatalogResultProvenance.Network,
            ),
        )

        val counts = hits.catalogSearchFacetCountsFromHits(
            selectedField = CatalogSearchField.All,
            selectedSource = null,
            selectedLanguage = null,
            availableSources = listOf(
                CatalogSourcePreference.LibriVox,
                CatalogSourcePreference.Lit2Go,
            ),
            availableLanguages = listOf("English"),
        )

        assertEquals(1, counts.total)
        assertEquals(1, counts.fieldCounts[CatalogSearchField.All])
        assertEquals(1, counts.fieldCounts[CatalogSearchField.Title])
        assertEquals(1, counts.languageCounts["English"])
        assertEquals(1, counts.sourceCounts[CatalogSourcePreference.LibriVox])
        assertEquals(1, counts.sourceCounts[CatalogSourcePreference.Lit2Go])
        assertEquals(2, counts.sourceCounts.values.sum())
    }

    @Test
    fun filteredResults_applySourceFieldAndLanguageWithoutAnotherRemoteSearch() {
        val books = listOf(
            book(id = "librivox", author = "River Writer", source = BookSource.LibriVox),
            book(id = "lit2go", author = "River Writer", source = BookSource.Lit2Go),
            book(
                id = "polish",
                author = "River Writer",
                source = BookSource.WolneLektury,
                language = "Polish",
            ),
        )

        val results = books.filterForCatalogSearch(
            query = "River",
            field = CatalogSearchField.Author,
            source = CatalogSourcePreference.Lit2Go,
            language = "English",
        )

        assertEquals(listOf("lit2go"), results.map { it.id })
    }

    @Test
    fun sourceFilterNarrowsTheExecutedSearchPlanButNotEmptyBrowse() {
        val configured = BookSourcePreference.LibriVoxAndLit2Go

        assertEquals(
            BookSourcePreference.LibriVoxOnly,
            resolveCatalogSearchSourcePreference(
                query = "alice",
                configured = configured,
                selectedSource = CatalogSourcePreference.LibriVox,
            ),
        )
        assertEquals(
            configured,
            resolveCatalogSearchSourcePreference(
                query = "",
                configured = configured,
                selectedSource = CatalogSourcePreference.LibriVox,
            ),
        )
    }

    @Test
    fun allFieldSearch_expandsLibriVoxIntoIndependentOperations() {
        val operations = catalogSearchOperations(
            sourcePreference = BookSourcePreference.LibriVoxAndLit2Go,
            field = CatalogSearchField.All,
        )

        assertEquals(
            listOf(
                CatalogSearchField.Title,
                CatalogSearchField.Author,
                CatalogSearchField.Reader,
                CatalogSearchField.Chapter,
                CatalogSearchField.Genre,
            ),
            operations
                .filter { it.source == CatalogSourcePreference.LibriVox }
                .map { it.field },
        )
        assertEquals(
            listOf(
                CatalogSearchField.Title,
                CatalogSearchField.Author,
                CatalogSearchField.Reader,
                CatalogSearchField.Chapter,
                CatalogSearchField.Genre,
            ),
            operations
                .filter { it.source == CatalogSourcePreference.Lit2Go }
                .map { it.field },
        )
    }

    private fun book(
        id: String,
        title: String = "Other Book",
        author: String = "Someone Else",
        source: BookSource,
        language: String = "English",
        chapters: List<AudioBookChapter> = emptyList(),
    ): AudioBook =
        AudioBook(
            id = id,
            title = title,
            author = author,
            source = source,
            language = language,
            chapters = chapters,
        )

    private fun chapter(title: String, reader: String? = null): AudioBookChapter =
        AudioBookChapter(
            id = title,
            title = title,
            number = 1,
            reader = reader,
            listenUrl = "https://example.com/audio.mp3",
        )
}
