package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.CatalogTag
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogSearchRankingTest {
    @Test
    fun rankForCatalogSearch_dropsUnmatchedBooks() {
        val gatsby = AudioBook(
            id = "gatsby",
            title = "Great Gatsby (Version 3)",
            author = "F. Scott Fitzgerald",
        )
        val crucible = AudioBook(
            id = "crucible",
            title = "The Crucible",
            author = "Arthur Miller",
        )

        val ranked = listOf(gatsby, crucible)
            .rankForCatalogSearch("the crucible", CatalogSearchField.All)

        assertEquals(listOf("crucible"), ranked.map { it.id })
    }

    @Test
    fun rankForCatalogSearch_prefersPlainTitleBeforeVersionedTitle() {
        val versioned = AudioBook(
            id = "gatsby-version-3",
            title = "Great Gatsby (Version 3)",
            author = "F. Scott Fitzgerald",
        )
        val plain = AudioBook(
            id = "gatsby",
            title = "Great Gatsby",
            author = "F. Scott Fitzgerald",
        )

        val ranked = listOf(versioned, plain)
            .rankForCatalogSearch("gatsby", CatalogSearchField.Title)

        assertEquals(listOf("gatsby", "gatsby-version-3"), ranked.map { it.id })
    }

    @Test
    fun rankForCatalogSearch_usesPopularityRankAfterMatchQuality() {
        val lessPopular = AudioBook(
            id = "less-popular",
            title = "Hamlet Alpha",
            author = "First Author",
            sourcePopularityRank = 20,
        )
        val morePopular = AudioBook(
            id = "more-popular",
            title = "Hamlet Beta",
            author = "Second Author",
            sourcePopularityRank = 3,
        )

        val ranked = listOf(lessPopular, morePopular)
            .rankForCatalogSearch("hamlet", CatalogSearchField.Title)

        assertEquals(listOf("more-popular", "less-popular"), ranked.map { it.id })
    }

    @Test
    fun rankForCatalogSearch_allIncludesMetadataAfterCoreMatches() {
        val titleMatch = AudioBook(
            id = "title",
            title = "River Stories",
            author = "Author",
        )
        val chapterMatch = AudioBook(
            id = "chapter",
            title = "Collected Works",
            author = "Author",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "River Crossing",
                    number = 1,
                ),
            ),
        )
        val authorMatch = AudioBook(
            id = "author",
            title = "Unrelated",
            author = "River Person",
        )
        val readerMatch = AudioBook(
            id = "reader",
            title = "Another Book",
            author = "Author",
            chapters = listOf(
                AudioBookChapter(
                    id = "reader-1",
                    title = "Opening",
                    number = 1,
                    reader = "River Voice",
                ),
            ),
        )
        val genreMatch = AudioBook(
            id = "genre",
            title = "Genre Book",
            author = "Author",
            genres = listOf("River fiction"),
        )
        val epochMatch = AudioBook(
            id = "epoch",
            title = "Epoch Book",
            author = "Author",
            literaryEpochs = listOf(CatalogTag("River era", "river-era")),
        )

        val ranked = listOf(epochMatch, genreMatch, readerMatch, authorMatch, chapterMatch, titleMatch)
            .rankForCatalogSearch("river", CatalogSearchField.All)

        assertEquals(
            listOf("title", "chapter", "author", "reader", "genre", "epoch"),
            ranked.map { it.id },
        )
    }
}
