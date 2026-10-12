package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.util.Locale

data class CatalogSearchFacetCounts(
    val total: Int = 0,
    val sourceCounts: Map<CatalogSourcePreference, Int> = emptyMap(),
    val fieldCounts: Map<CatalogSearchField, Int> = emptyMap(),
    val languageCounts: Map<String, Int> = emptyMap(),
)

data class CatalogSearchOperation(
    val source: CatalogSourcePreference,
    val field: CatalogSearchField,
)

data class CatalogSearchProgressItem(
    val operation: CatalogSearchOperation,
    val outcome: CatalogOperationOutcome = CatalogOperationOutcome.Pending(),
) {
    val isComplete: Boolean
        get() = outcome !is CatalogOperationOutcome.Pending

    val didFail: Boolean
        get() = outcome is CatalogOperationOutcome.Failed

    val resultCount: Int
        get() = when (outcome) {
            is CatalogOperationOutcome.Pending -> outcome.interimCount
            is CatalogOperationOutcome.Success -> outcome.count
            CatalogOperationOutcome.AuthoritativeEmpty,
            is CatalogOperationOutcome.Skipped,
            is CatalogOperationOutcome.Failed -> 0
        }
}

internal fun resolveCatalogSearchSourcePreference(
    query: String,
    configured: BookSourcePreference,
    selectedSource: CatalogSourcePreference?,
): BookSourcePreference =
    if (sanitizeCatalogSearchQuery(query).isBlank() || selectedSource == null) {
        configured
    } else {
        BookSourcePreference.fromEnabledSources(setOf(selectedSource))
    }

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

internal fun List<AudioBook>.filterForCatalogSearch(
    query: String,
    field: CatalogSearchField,
    source: CatalogSourcePreference? = null,
    language: String? = null,
): List<AudioBook> =
    asSequence()
        .filter { book -> source == null || book.catalogSourcePreference() == source }
        .filter { book -> language.isNullOrBlank() || book.language.equals(language, ignoreCase = true) }
        .filter { book -> query.isBlank() || book.catalogSearchMatch(query, field) != null }
        .toList()
        .rankForCatalogSearch(query, field)

internal fun List<AudioBook>.catalogSearchFacetCounts(
    query: String,
    selectedField: CatalogSearchField,
    selectedSource: CatalogSourcePreference?,
    selectedLanguage: String?,
    availableSources: List<CatalogSourcePreference>,
    availableLanguages: List<String>,
): CatalogSearchFacetCounts {
    if (query.isBlank()) return CatalogSearchFacetCounts()

    fun matches(
        book: AudioBook,
        field: CatalogSearchField,
        source: CatalogSourcePreference?,
        language: String?,
    ): Boolean =
        (source == null || book.catalogSourcePreference() == source) &&
            (language.isNullOrBlank() || book.language.equals(language, ignoreCase = true)) &&
            book.catalogSearchMatch(query, field) != null

    val sourceCounts = availableSources.associateWith { source ->
        count { book -> matches(book, selectedField, source, selectedLanguage) }
    }
    val fieldCounts = CatalogSearchField.entries.associateWith { field ->
        count { book -> matches(book, field, selectedSource, selectedLanguage) }
    }
    val languageCounts = availableLanguages.distinct().associateWith { language ->
        count { book -> matches(book, selectedField, selectedSource, language) }
    }
    val total = count { book -> matches(book, selectedField, selectedSource, selectedLanguage) }
    return CatalogSearchFacetCounts(
        total = total,
        sourceCounts = sourceCounts,
        fieldCounts = fieldCounts,
        languageCounts = languageCounts,
    )
}

/**
 * Calculates facets from operation hits without inflating work-level counts when the same work
 * is returned by more than one catalog. Each work contributes at most once to total, field, and
 * language facets, while still contributing once to every source that returned a matching variant.
 */
internal fun List<CatalogSearchHit>.catalogSearchFacetCountsFromHits(
    selectedField: CatalogSearchField,
    selectedSource: CatalogSourcePreference?,
    selectedLanguage: String?,
    availableSources: List<CatalogSourcePreference>,
    availableLanguages: List<String>,
): CatalogSearchFacetCounts {
    fun CatalogSearchHit.matches(
        field: CatalogSearchField,
        source: CatalogSourcePreference?,
        language: String?,
    ): Boolean =
        (source == null || source in contributingSources) &&
            (language.isNullOrBlank() || book.language.equals(language, ignoreCase = true)) &&
            (field == CatalogSearchField.All || field in matchedFields)

    val workVariants = groupBy { hit -> BookMerger.matchKey(hit.book) }.values
    fun Collection<CatalogSearchHit>.matchesAny(
        field: CatalogSearchField,
        source: CatalogSourcePreference?,
        language: String?,
    ): Boolean = any { hit -> hit.matches(field, source, language) }

    return CatalogSearchFacetCounts(
        total = workVariants.count { variants ->
            variants.matchesAny(selectedField, selectedSource, selectedLanguage)
        },
        sourceCounts = availableSources.associateWith { source ->
            workVariants.count { variants ->
                variants.matchesAny(selectedField, source, selectedLanguage)
            }
        },
        fieldCounts = CatalogSearchField.entries.associateWith { field ->
            workVariants.count { variants ->
                variants.matchesAny(field, selectedSource, selectedLanguage)
            }
        },
        languageCounts = availableLanguages.distinct().associateWith { language ->
            workVariants.count { variants ->
                variants.matchesAny(selectedField, selectedSource, language)
            }
        },
    )
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

private fun AudioBook.catalogSourcePreference(): CatalogSourcePreference? =
    when (source) {
        BookSource.LibriVox -> CatalogSourcePreference.LibriVox
        BookSource.Lit2Go -> CatalogSourcePreference.Lit2Go
        BookSource.Gutendex -> CatalogSourcePreference.Gutendex
        BookSource.WolneLektury -> CatalogSourcePreference.WolneLektury
        BookSource.LocalAsset,
        BookSource.CustomLocal -> null
    }

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
