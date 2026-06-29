package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.CatalogTag
import java.util.Locale

internal enum class CatalogSearchBucket(val priority: Int) {
    Title(0),
    Chapter(1),
    Author(2),
    Reader(3),
    Genre(4),
    Metadata(5),
}

internal data class CatalogSearchMatch(
    val bucket: CatalogSearchBucket,
    val quality: Int,
    val text: String,
)

internal fun AudioBook.catalogSearchMatch(
    query: String,
    field: CatalogSearchField,
): CatalogSearchMatch? {
    val normalizedQuery = normalizedCatalogSearchText(query)
    if (normalizedQuery.isBlank()) return null

    fun best(
        bucket: CatalogSearchBucket,
        values: Iterable<String>,
    ): CatalogSearchMatch? =
        values.asSequence()
            .mapNotNull { value ->
                value.searchQuality(normalizedQuery)?.let { quality ->
                    CatalogSearchMatch(bucket = bucket, quality = quality, text = value)
                }
            }
            .minWithOrNull(
                compareBy<CatalogSearchMatch> { it.quality }
                    .thenBy { it.text.length }
                    .thenBy { it.text.lowercase(Locale.ROOT) },
            )

    val titleMatch = { best(CatalogSearchBucket.Title, listOf(title)) }
    val chapterMatch = {
        best(CatalogSearchBucket.Chapter, chapters.map { it.title })
    }
    val authorMatch = {
        best(
            CatalogSearchBucket.Author,
            listOf(author) + authorTags.flatMap { it.searchTerms() },
        )
    }
    val readerMatch = {
        best(
            CatalogSearchBucket.Reader,
            narrators + chapters.flatMap { chapter ->
                listOf(chapter.reader.orEmpty(), chapter.director.orEmpty())
            },
        )
    }
    val genreMatch = {
        best(
            CatalogSearchBucket.Genre,
            genres.flatMap { CatalogGenreTranslations.searchTermsForName(it) } +
                literaryGenres.flatMap { it.searchTerms() },
        )
    }
    val epochMatch = {
        best(CatalogSearchBucket.Metadata, literaryEpochs.flatMap { it.searchTerms() })
    }
    val kindMatch = {
        best(CatalogSearchBucket.Metadata, literaryKinds.flatMap { it.searchTerms() })
    }

    return when (field) {
        CatalogSearchField.All -> listOfNotNull(
            titleMatch(),
            chapterMatch(),
            authorMatch(),
            readerMatch(),
            genreMatch(),
            epochMatch(),
            kindMatch(),
        ).minByOrNull { it.bucket.priority }
        CatalogSearchField.Title -> titleMatch()
        CatalogSearchField.Chapter -> chapterMatch()
        CatalogSearchField.Author -> authorMatch()
        CatalogSearchField.Reader -> readerMatch()
        CatalogSearchField.Genre -> genreMatch()
        CatalogSearchField.Epoch -> epochMatch()
        CatalogSearchField.Kind -> kindMatch()
    }
}

internal fun List<AudioBook>.rankForCatalogSearch(
    query: String,
    field: CatalogSearchField,
    keepUnmatched: Boolean = false,
): List<AudioBook> {
    val normalizedQuery = normalizedCatalogSearchText(query)
    if (normalizedQuery.isBlank()) return dedupeCatalogBooks()
    val ranked = mapIndexedNotNull { index, book ->
        val match = book.catalogSearchMatch(normalizedQuery, field) ?: return@mapIndexedNotNull null
        RankedBook(book = book, match = match, sourceIndex = index)
    }.sortedWith(
        compareBy<RankedBook> { it.match.bucket.priority }
            .thenBy { it.match.quality }
            .thenBy { it.book.sourcePopularityRank ?: Int.MAX_VALUE }
            .thenBy { it.book.title.normalizedCatalogSearchTitle() }
            .thenBy { it.book.author.normalizedCatalogSearchTitle() }
            .thenBy { it.sourceIndex },
    ).map { it.book }
    val unmatched = if (keepUnmatched) {
        filter { book -> book.catalogSearchMatch(normalizedQuery, field) == null }
    } else {
        emptyList()
    }
    return (ranked + unmatched).dedupeCatalogBooks()
}

internal fun Iterable<AudioBook>.dedupeCatalogBooks(): List<AudioBook> {
    val seenIds = linkedSetOf<String>()
    val seenBooks = linkedSetOf<String>()
    return filter { book ->
        seenIds.add(book.id) && seenBooks.add(BookMerger.matchKey(book))
    }
}

private data class RankedBook(
    val book: AudioBook,
    val match: CatalogSearchMatch,
    val sourceIndex: Int,
)

private fun CatalogTag.searchTerms(): List<String> =
    CatalogGenreTranslations.searchTermsForName(name) + listOf(slug)

private fun String.searchQuality(normalizedQuery: String): Int? {
    val normalizedSelf = normalizedCatalogSearchText(this)
    if (normalizedSelf.isBlank()) return null
    return when {
        normalizedSelf == normalizedQuery -> 0
        normalizedSelf.startsWith(normalizedQuery) -> 1
        normalizedSelf.split(' ').any { it == normalizedQuery } -> 2
        normalizedSelf.contains(normalizedQuery) -> 3
        normalizedQuery.length >= 4 && normalizedQuery.contains(normalizedSelf) -> 4
        else -> null
    }
}

private fun String.normalizedCatalogSearchTitle(): String =
    normalizedCatalogSearchText(this)
        .removePrefix("the ")
        .removePrefix("a ")
        .removePrefix("an ")
