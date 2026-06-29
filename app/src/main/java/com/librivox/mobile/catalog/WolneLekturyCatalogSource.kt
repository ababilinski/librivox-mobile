package com.librivox.mobile.catalog

import android.content.Context
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.CatalogTag
import com.librivox.mobile.model.FallbackCover
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val WOLNE_LEKTURY_ID_PREFIX = "wolnelektury-"

class WolneLekturyCatalogSource internal constructor(
    private val httpClient: OkHttpClient,
) : CatalogSource {

    constructor(context: Context) : this(buildSharedClient(context))

    internal constructor() : this(OkHttpClient())

    @Volatile
    private var cachedStubs: List<BookStub>? = null
    @Volatile
    private var cachedFeaturedStubs: List<BookStub>? = null
    @Volatile
    private var cachedPopularityRanks: Map<String, Int>? = null
    private val cachedDetails = ConcurrentHashMap<String, AudioBook>()

    override suspend fun featuredBooks(): List<AudioBook> =
        featuredCollectionStubs()
            .sortedByPopularity()
            .take(FEATURED_CANDIDATE_LIMIT)
            .map { it.toAudioBook() }

    override suspend fun fetchByIds(vararg ids: String): List<AudioBook> =
        coroutineScope {
            ids.map { rawId ->
                async(Dispatchers.IO) {
                    val slug = decodeId(rawId) ?: return@async null
                    runCatching { fetchBookDetail(slug, includePageDescription = true) }.getOrNull()
                }
            }.mapNotNull { it.await() }
        }

    override suspend fun search(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> {
        if (!languageIsPolishOrAny(language)) return emptyList()
        val q = query.trim()
        if (q.isBlank()) {
            return browse(limit = limit, offset = offset, language = language)
        }
        if (field == CatalogSearchField.All) {
            return searchAllFields(q, limit = limit, offset = offset)
        }
        if (field == CatalogSearchField.Chapter || field == CatalogSearchField.Reader) {
            return searchCachedDetailBooks(q, field = field, limit = limit, offset = offset)
        }
        val taggedStubs = runCatching {
            fetchTaggedStubs(q, field, limit = limit, offset = offset)
        }.getOrNull()
        if (taggedStubs != null) {
            return taggedStubs.map { it.toAudioBook() }
        }
        val matchingStubs = audiobookStubs()
            .filter { stub -> stub.matches(q, field) }
        val titleSlugFallback = if (
            field == CatalogSearchField.Title &&
            offset <= 0 &&
            matchingStubs.isEmpty()
        ) {
            fetchTitleSlugFallback(q)
        } else {
            null
        }
        return (listOfNotNull(titleSlugFallback) + matchingStubs.map { it.toAudioBook() })
            .distinctBy { it.id }
            .rankForCatalogSearch(q, field)
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
    }

    override suspend fun browse(limit: Int, offset: Int, language: String): List<AudioBook> {
        if (!languageIsPolishOrAny(language)) return emptyList()
        return audiobookStubs()
            .sortedByPopularity()
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
            .map { it.toAudioBook() }
    }

    override suspend fun recent(sinceEpochSeconds: Long, limit: Int): List<AudioBook> =
        browse(limit = limit, offset = 0, language = "")

    override suspend fun byGenre(genre: String, limit: Int, language: String): List<AudioBook> =
        search(genre, CatalogSearchField.Genre, limit = limit, offset = 0, language = language)

    override suspend fun byAuthor(author: String, limit: Int, language: String): List<AudioBook> =
        search(author, CatalogSearchField.Author, limit = limit, offset = 0, language = language)

    internal fun parseAudiobookIndexJson(rawJson: String): List<BookStub> {
        val root = json.parseToJsonElement(rawJson)
        val books = when (root) {
            is JsonArray -> root
            is JsonObject -> root["books"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return books.mapNotNull { element ->
            val book = element as? JsonObject ?: return@mapNotNull null
            val slug = book.string("slug") ?: return@mapNotNull null
            val title = book.string("title") ?: slug.titleized()
            val author = book.string("author").orUnknownAuthor()
            val hasAudio = book.boolean("has_audio") ||
                book.arrayObjects("children").any { it.boolean("has_audio") }
            if (!hasAudio) return@mapNotNull null
            BookStub(
                slug = slug,
                title = title,
                author = author,
                coverUrl = book.string("simple_thumb")?.let(::absoluteUrl)
                    ?: book.string("cover_thumb")?.let(::absoluteUrl)
                    ?: book.string("cover")?.let(::absoluteUrl),
                siteUrl = book.string("url")?.let(::absoluteUrl) ?: "$SITE_BASE/katalog/lektura/$slug/",
                authorTags = listOf(CatalogTag(author, author.catalogSlug())),
                literaryGenres = book.string("genre").splitGenreNames().map { it.toCatalogTag() },
                literaryKinds = book.string("kind").singleNameList().map { it.toCatalogTag() },
                literaryEpochs = book.string("epoch").singleNameList().map { it.toCatalogTag() },
            )
        }.distinctBy { it.slug }
    }

    internal fun parsePopularityCatalogPage(rawHtml: String, firstRank: Int = 1): PopularityCatalogPage {
        val document = Jsoup.parse(rawHtml)
        val ranks = document
            .select("article.l-books__item")
            .mapNotNull { article ->
                val href = article.selectFirst("h2 a[href]")?.attr("href").orEmpty()
                href.catalogBookSlugFromHref()
            }
            .distinct()
            .mapIndexed { index, slug -> slug to firstRank + index }
            .toMap()
        val pageCount = document
            .select("#paginator a[href], .l-pagination a[href]")
            .mapNotNull { link -> link.attr("href").pageQueryValue()?.toIntOrNull() }
            .maxOrNull()
            ?: 1
        return PopularityCatalogPage(ranks = ranks, pageCount = pageCount)
    }

    internal fun parseBookDetail(
        slug: String,
        rawJson: String,
        pageHtml: String? = null,
        childDetailJsons: List<String> = emptyList(),
    ): AudioBook {
        val book = json.parseToJsonElement(rawJson) as? JsonObject
            ?: error("Wolne Lektury detail response was not an object")
        val title = book.string("title") ?: slug.titleized()
        val authorTags = book.namedTags("authors")
        val authors = authorTags
            .map { it.name }
            .ifEmpty { listOf("Unknown author") }
        val author = authors.joinToString(", ")
        val literaryGenres = book.namedTags("genres")
        val literaryKinds = book.namedTags("kinds")
        val literaryEpochs = book.namedTags("epochs")
        val translators = book.personNames("translators")
        val originalLanguage = book.originalLanguageLabel()
        val genres = (literaryGenres + literaryKinds + literaryEpochs)
            .map { it.name }
            .distinct()
        val totalDurationSeconds = parseDurationSeconds(book.string("audio_length"))
        val mediaEntries = book.arrayObjects("media")
        val epubUrl = book.string("epub")?.let(::absoluteUrl)
        val audioEpubUrl = mediaEntries.firstMediaUrl("audio.epub")
        val daisyUrl = mediaEntries.firstMediaUrl("daisy")
        val mp3Media = mediaEntries
            .filter { media ->
                media.string("type").equals("mp3", ignoreCase = true) &&
                    !media.string("url").isNullOrBlank()
            }
            .withoutSupplementalAudio(title, authors)
        val childDetails = childDetailJsons.mapNotNull { childRawJson ->
            json.parseToJsonElement(childRawJson) as? JsonObject
        }
        val directChapters = mp3Media.mapIndexed { index, media ->
            val listenUrl = absoluteUrl(media.string("url").orEmpty())
            AudioBookChapter(
                id = "$slug-${index + 1}",
                title = chapterTitle(
                    mediaName = media.string("name"),
                    bookTitle = title,
                    authors = authors,
                    index = index,
                    chapterCount = mp3Media.size,
                ),
                number = index + 1,
                reader = media.string("artist"),
                director = media.string("director"),
                durationSeconds = if (mp3Media.size == 1) totalDurationSeconds else 0L,
                listenUrl = listenUrl,
                mimeType = "audio/mpeg",
            )
        }
        val chapters = directChapters.ifEmpty {
            childDetails.toCollectionChapters(parentTitle = title, authors = authors)
        }
        val effectiveTotalDurationSeconds = totalDurationSeconds.takeIf { it > 0L }
            ?: chapters.sumOf { it.durationSeconds }
        val fullCoverUrl = book.string("cover")?.let(::absoluteUrl)
        val thumbnailCoverUrl = book.string("simple_thumb")?.let(::absoluteUrl)
            ?: book.string("cover_thumb")?.let(::absoluteUrl)
            ?: fullCoverUrl
        return AudioBook(
            id = encodeId(slug),
            title = title,
            author = author,
            description = pageHtml?.let(::parseBookPageDescription)
                .orEmpty()
                .ifBlank { book.fragmentDescription() },
            source = BookSource.WolneLektury,
            coverImageUrl = thumbnailCoverUrl,
            fullCoverImageUrl = fullCoverUrl,
            epubUrl = epubUrl,
            audioEpubUrl = audioEpubUrl,
            daisyUrl = daisyUrl,
            fallbackCover = FallbackCover.Generated,
            chapters = chapters,
            totalDurationSeconds = effectiveTotalDurationSeconds,
            wolneLekturyUrl = book.string("url")?.let(::absoluteUrl) ?: "$SITE_BASE/katalog/lektura/$slug/",
            language = languageLabel(book.string("language")),
            originalLanguage = originalLanguage,
            translators = translators,
            translationMetadataChecked = true,
            genres = genres,
            authorTags = authorTags.ifEmpty { listOf(CatalogTag(author, author.catalogSlug())) },
            literaryEpochs = literaryEpochs,
            literaryKinds = literaryKinds,
            literaryGenres = literaryGenres,
        )
    }

    internal fun parseDurationSeconds(value: String?): Long {
        val parts = value
            ?.split(':')
            ?.mapNotNull { it.trim().toLongOrNull() }
            .orEmpty()
        return when (parts.size) {
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            2 -> parts[0] * 60L + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private suspend fun audiobookStubs(): List<BookStub> =
        cachedStubs ?: withContext(Dispatchers.IO) {
            cachedStubs ?: parseAudiobookIndexJson(get(AUDIOBOOKS_URL))
                .withPopularityRanks()
                .also { cachedStubs = it }
        }

    private suspend fun featuredCollectionStubs(): List<BookStub> =
        cachedFeaturedStubs ?: withContext(Dispatchers.IO) {
            cachedFeaturedStubs ?: parseAudiobookIndexJson(get(ZAKAZANE_KSIAZKI_COLLECTION_URL))
                .withPopularityRanks()
                .also { cachedFeaturedStubs = it }
        }

    internal fun parseBookPageDescription(rawHtml: String): String {
        val abstract = Jsoup.parse(rawHtml).selectFirst(".l-article__overlay.abstract")
            ?: return ""
        return abstract.select("p")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private suspend fun fetchBookDetail(slug: String, includePageDescription: Boolean): AudioBook =
        withContext(Dispatchers.IO) {
            if (!includePageDescription) {
                cachedDetails[slug]?.let { return@withContext it }
            }
            val rawJson = get("$API_BASE/books/$slug/?format=json")
            val book = json.parseToJsonElement(rawJson) as? JsonObject
                ?: error("Wolne Lektury detail response was not an object")
            val parentSlug = book.parentSlug()
            if (parentSlug != null && parentSlug != slug) {
                val parentBook = fetchBookDetail(parentSlug, includePageDescription)
                cachedDetails[slug] = parentBook
                return@withContext parentBook
            }
            val pageHtml = if (includePageDescription) {
                val siteUrl = book
                    .string("url")
                    ?.let(::absoluteUrl)
                    ?: "$SITE_BASE/katalog/lektura/$slug/"
                runCatching { get(siteUrl, accept = "text/html") }.getOrNull()
            } else {
                null
            }
            val childDetailJsons = if (book.hasPlayableMp3Media()) {
                emptyList()
            } else {
                fetchAudioChildDetailJsons(book)
            }
            parseBookDetail(slug, rawJson, pageHtml, childDetailJsons).also { cachedDetails[slug] = it }
        }

    private fun List<JsonObject>.firstMediaUrl(type: String): String? =
        firstOrNull { media ->
            media.string("type").equals(type, ignoreCase = true) &&
                !media.string("url").isNullOrBlank()
        }?.string("url")?.let(::absoluteUrl)

    private suspend fun fetchTaggedStubs(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
    ): List<BookStub>? {
        val count = (offset.coerceAtLeast(0) + limit.coerceAtLeast(1)).coerceAtLeast(1)
        val urls = taggedAudiobooksUrls(query, field, count)
        if (urls.isEmpty()) return null
        val stubs = withContext(Dispatchers.IO) {
            urls.flatMap { url -> parseAudiobookIndexJson(get(url)) }
        }
        return stubs
            .withPopularityRanks()
            .sortedByPopularity()
            .distinctBy { it.slug }
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
    }

    private suspend fun searchAllFields(
        query: String,
        limit: Int,
        offset: Int,
    ): List<AudioBook> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        val matchingStubs = audiobookStubs()
            .filter { stub -> stub.matches(query, CatalogSearchField.All) }
            .map { it.toAudioBook() }
        val cachedDetailMatches = cachedDetails.values
            .filter { it.catalogSearchMatch(query, CatalogSearchField.All) != null }
        val titleSlugFallback = if (safeOffset == 0 && matchingStubs.isEmpty() && cachedDetailMatches.isEmpty()) {
            fetchTitleSlugFallback(query)
        } else {
            null
        }
        return (listOfNotNull(titleSlugFallback) + cachedDetailMatches + matchingStubs)
            .rankForCatalogSearch(query, CatalogSearchField.All)
            .drop(safeOffset)
            .take(safeLimit)
    }

    private suspend fun fetchTitleSlugFallback(query: String): AudioBook? {
        val slug = query.catalogSlug().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            fetchBookDetail(slug, includePageDescription = false)
                .takeIf { it.chapters.isNotEmpty() }
        }.getOrNull()
    }

    internal fun taggedAudiobooksUrl(query: String, field: CatalogSearchField, count: Int): String? =
        taggedAudiobooksUrls(query, field, count).firstOrNull()

    private fun taggedAudiobooksUrls(query: String, field: CatalogSearchField, count: Int): List<String> {
        val category = when (field) {
            CatalogSearchField.Author -> "authors"
            CatalogSearchField.Genre -> "genres"
            CatalogSearchField.Epoch -> "epochs"
            CatalogSearchField.Kind -> "kinds"
            CatalogSearchField.All,
            CatalogSearchField.Title,
            CatalogSearchField.Chapter,
            CatalogSearchField.Reader -> return emptyList()
        }
        val queryVariants = if (field == CatalogSearchField.Author) {
            listOf(query)
        } else {
            CatalogGenreTranslations.queryVariants(query)
        }
        return queryVariants
            .mapNotNull { variant ->
                variant.catalogSlug()
                    .takeIf { it.isNotBlank() }
                    ?.let { slug ->
                        "$API_BASE/$category/$slug/parent_books/count/${count.coerceAtLeast(1)}/?format=json"
                    }
            }
            .distinct()
    }

    private suspend fun fetchAudioChildDetailJsons(book: JsonObject): List<String> {
        val childSlugs = book.arrayObjects("children")
            .filter { it.boolean("has_audio") }
            .mapNotNull { it.string("slug") }
            .distinct()
        if (childSlugs.isEmpty()) return emptyList()
        return coroutineScope {
            childSlugs.chunked(ChildDetailBatchSize).flatMap { batch ->
                batch.map { childSlug ->
                    async(Dispatchers.IO) {
                        runCatching { get("$API_BASE/books/$childSlug/?format=json") }.getOrNull()
                    }
                }.mapNotNull { it.await() }
            }
        }
    }

    private fun searchCachedDetailBooks(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
    ): List<AudioBook> =
        cachedDetails.values
            .filter { it.catalogSearchMatch(query, field) != null }
            .rankForCatalogSearch(query, field)
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))

    private suspend fun List<BookStub>.withPopularityRanks(): List<BookStub> {
        if (isEmpty()) return this
        val ranks = popularityRanks()
        if (ranks.isEmpty()) return this
        return map { stub -> stub.copy(sourcePopularityRank = ranks[stub.slug]) }
    }

    private fun List<BookStub>.sortedByPopularity(): List<BookStub> =
        if (any { it.sourcePopularityRank != null }) {
            mapIndexed { index, stub -> IndexedValue(index, stub) }
                .sortedWith(
                    compareBy<IndexedValue<BookStub>> { it.value.sourcePopularityRank ?: Int.MAX_VALUE }
                        .thenBy { it.index },
                )
                .map { it.value }
        } else {
            this
        }

    private suspend fun popularityRanks(): Map<String, Int> =
        cachedPopularityRanks ?: withContext(Dispatchers.IO) {
            cachedPopularityRanks ?: fetchPopularityRanks().also { cachedPopularityRanks = it }
        }

    private fun fetchPopularityRanks(): Map<String, Int> {
        val firstPage = runCatching {
            parsePopularityCatalogPage(get(popularAudiobooksUrl(page = 1), accept = "text/html"))
        }.getOrNull() ?: return emptyMap()
        val ranks = linkedMapOf<String, Int>()
        ranks.putAll(firstPage.ranks)
        var nextRank = ranks.size + 1
        for (page in 2..firstPage.pageCount) {
            val pageRanks = runCatching {
                parsePopularityCatalogPage(
                    get(popularAudiobooksUrl(page = page), accept = "text/html"),
                    firstRank = nextRank,
                )
            }.getOrNull()?.ranks.orEmpty()
            pageRanks.forEach { (slug, rank) -> ranks.putIfAbsent(slug, rank) }
            nextRank = ranks.size + 1
        }
        return ranks
    }

    private fun get(url: String, accept: String = "application/json"): String {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Accept", accept)
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Wolne Lektury responded ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    private fun BookStub.matches(query: String, field: CatalogSearchField): Boolean =
        when (field) {
            CatalogSearchField.All -> title.matchesCatalogText(query) ||
                author.matchesCatalogText(query) ||
                literaryGenres.any { it.matchesCatalogText(query) } ||
                literaryKinds.any { it.matchesCatalogText(query) } ||
                literaryEpochs.any { it.matchesCatalogText(query) }
            CatalogSearchField.Title -> title.matchesCatalogText(query)
            CatalogSearchField.Chapter -> false
            CatalogSearchField.Author -> author.matchesCatalogText(query)
            CatalogSearchField.Reader -> false
            CatalogSearchField.Genre -> literaryGenres.any { it.matchesCatalogText(query) }
            CatalogSearchField.Epoch -> literaryEpochs.any { it.matchesCatalogText(query) }
            CatalogSearchField.Kind -> literaryKinds.any { it.matchesCatalogText(query) }
        }

    private fun BookStub.toAudioBook(): AudioBook =
        AudioBook(
            id = encodeId(slug),
            title = title,
            author = author,
            source = BookSource.WolneLektury,
            coverImageUrl = coverUrl,
            epubUrl = "$SITE_BASE/media/book/epub/$slug.epub",
            fallbackCover = FallbackCover.Generated,
            wolneLekturyUrl = siteUrl,
            language = "Polish",
            genres = (literaryGenres + literaryKinds + literaryEpochs).map { it.name }.distinct(),
            authorTags = authorTags,
            literaryEpochs = literaryEpochs,
            literaryKinds = literaryKinds,
            literaryGenres = literaryGenres,
            sourcePopularityRank = sourcePopularityRank,
        )

    private fun JsonObject.fragmentDescription(): String {
        val html = (this["fragment_data"] as? JsonObject)?.string("html")
        if (!html.isNullOrBlank()) return Jsoup.parse(html).text()
        return ""
    }

    private fun JsonObject.hasPlayableMp3Media(): Boolean =
        arrayObjects("media").any { media ->
            media.string("type").equals("mp3", ignoreCase = true) &&
                !media.string("url").isNullOrBlank()
        }

    private fun JsonObject.parentSlug(): String? =
        (this["parent"] as? JsonObject)?.string("slug")

    private fun List<JsonObject>.toCollectionChapters(
        parentTitle: String,
        authors: List<String>,
    ): List<AudioBookChapter> {
        var nextNumber = 1
        return flatMapIndexed { childIndex, child ->
            val childTitle = child.string("title")
                ?: child.slugFromUrl()
                    ?.titleized()
                ?: "Chapter ${childIndex + 1}"
            val childSlug = child.slugFromUrl() ?: "${parentTitle.catalogSlug()}-${childIndex + 1}"
            val childDurationSeconds = parseDurationSeconds(child.string("audio_length"))
            val mp3Media = child.arrayObjects("media")
                .filter { media ->
                    media.string("type").equals("mp3", ignoreCase = true) &&
                        !media.string("url").isNullOrBlank()
                }
                .withoutSupplementalAudio(childTitle, authors + parentTitle)
            mp3Media.mapIndexed { mediaIndex, media ->
                val number = nextNumber++
                AudioBookChapter(
                    id = "$childSlug-${mediaIndex + 1}",
                    title = if (mp3Media.size == 1) {
                        childTitle
                    } else {
                        chapterTitle(
                            mediaName = media.string("name"),
                            bookTitle = childTitle,
                            authors = authors + parentTitle,
                            index = mediaIndex,
                            chapterCount = mp3Media.size,
                        )
                    },
                    number = number,
                    reader = media.string("artist"),
                    director = media.string("director"),
                    durationSeconds = if (mp3Media.size == 1) childDurationSeconds else 0L,
                    listenUrl = absoluteUrl(media.string("url").orEmpty()),
                    mimeType = "audio/mpeg",
                )
            }
        }
    }

    private fun JsonObject.slugFromUrl(): String? =
        string("slug")
            ?: string("url")
                ?.substringAfter("/katalog/lektura/", missingDelimiterValue = "")
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }

    private fun chapterTitle(
        mediaName: String?,
        bookTitle: String,
        authors: List<String>,
        index: Int,
        chapterCount: Int,
    ): String {
        if (chapterCount == 1) return bookTitle
        var title = mediaName.orEmpty()
            .replace(Regex("""^\s*\d+\.\s*"""), "")
            .trim()
        authors.forEach { author ->
            title = title.removePrefix("$author,").trim()
        }
        title = title.removePrefix("$bookTitle,").trim()
        return title.ifBlank { "Chapter ${index + 1}" }
    }

    private fun List<JsonObject>.withoutSupplementalAudio(
        bookTitle: String,
        authors: List<String>,
    ): List<JsonObject> =
        filterIndexed { index, media ->
            !media.isSupplementalAudioMedia(
                bookTitle = bookTitle,
                authors = authors,
                index = index,
                chapterCount = size,
            )
        }.ifEmpty { this }

    private fun JsonObject.isSupplementalAudioMedia(
        bookTitle: String,
        authors: List<String>,
        index: Int,
        chapterCount: Int,
    ): Boolean {
        val url = string("url").orEmpty()
        val title = chapterTitle(
            mediaName = string("name"),
            bookTitle = bookTitle,
            authors = authors,
            index = index,
            chapterCount = chapterCount,
        ).lowercase()
        return url.contains("stopka", ignoreCase = true) ||
            title == "stopka" ||
            title == "strona redakcyjna" ||
            title == "nota redakcyjna"
    }

    internal data class BookStub(
        val slug: String,
        val title: String,
        val author: String,
        val coverUrl: String?,
        val siteUrl: String,
        val authorTags: List<CatalogTag>,
        val literaryGenres: List<CatalogTag>,
        val literaryKinds: List<CatalogTag>,
        val literaryEpochs: List<CatalogTag>,
        val sourcePopularityRank: Int? = null,
    )

    internal data class PopularityCatalogPage(
        val ranks: Map<String, Int>,
        val pageCount: Int,
    )

    internal companion object {
        const val ID_PREFIX = WOLNE_LEKTURY_ID_PREFIX
        private const val SITE_BASE = "https://wolnelektury.pl"
        private const val API_BASE = "$SITE_BASE/api"
        private const val AUDIOBOOKS_URL = "$API_BASE/parent_books/?format=json"
        private const val ZAKAZANE_KSIAZKI_COLLECTION_URL =
            "$API_BASE/collections/zakazane-ksiazki/?format=json"
        private const val ChildDetailBatchSize = 8

        private val json = Json { ignoreUnknownKeys = true }

        private fun buildSharedClient(context: Context): OkHttpClient {
            val cacheDir = File(context.cacheDir, "wolne-lektury-http").apply { mkdirs() }
            return OkHttpClient.Builder()
                .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        }

        private const val CACHE_SIZE_BYTES: Long = 4L * 1024 * 1024
        private const val FEATURED_CANDIDATE_LIMIT = 50
        private const val CONNECT_TIMEOUT_SECONDS: Long = 12

        private fun popularAudiobooksUrl(page: Int): String =
            "$SITE_BASE/katalog/audiobooki/?page=${page.coerceAtLeast(1)}&order=pop&search="
    }
}

private fun encodeId(slug: String): String =
    "$WOLNE_LEKTURY_ID_PREFIX$slug"

private fun decodeId(rawId: String): String? =
    rawId.takeIf { it.startsWith(WOLNE_LEKTURY_ID_PREFIX) }
        ?.removePrefix(WOLNE_LEKTURY_ID_PREFIX)
        ?.takeIf { it.isNotBlank() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)
        ?.content
        ?.takeIf { it.isNotBlank() && it != "null" }

private fun JsonObject.boolean(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.jsonPrimitive?.booleanOrNull == true

private fun JsonObject.arrayObjects(key: String): List<JsonObject> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun JsonObject.namedTags(key: String): List<CatalogTag> =
    arrayObjects(key)
        .mapNotNull { item ->
            val name = item.string("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogTag(
                name = CatalogGenreTranslations.displayName(name),
                slug = item.string("slug")?.takeIf { it.isNotBlank() } ?: name.catalogSlug(),
            )
        }
        .distinctBy { it.slug.ifBlank { it.name } }

private fun JsonObject.personNames(key: String): List<String> =
    arrayObjects(key)
        .mapNotNull { item -> item.string("name")?.trim()?.takeIf { it.isNotBlank() } }
        .distinct()

private fun JsonObject.originalLanguageLabel(): String? {
    val code = string("original_language")
        ?: string("originalLanguage")
        ?: string("language_original")
        ?: string("source_language")
        ?: string("sourceLanguage")
    return code?.let(::languageLabel)
        ?.takeUnless { it.equals(languageLabel(string("language")), ignoreCase = true) }
}

private fun String?.orUnknownAuthor(): String =
    this?.takeIf { it.isNotBlank() } ?: "Unknown author"

private fun String?.splitGenreNames(): List<String> =
    this?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

private fun String?.singleNameList(): List<String> =
    this?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::listOf)
        .orEmpty()

private fun String.toCatalogTag(): CatalogTag =
    CatalogTag(name = CatalogGenreTranslations.displayName(this), slug = catalogSlug())

private fun CatalogTag.matchesCatalogText(query: String): Boolean =
    (CatalogGenreTranslations.searchTermsForName(name) + slug)
        .any { it.matchesCatalogText(query) }

private fun String.matchesCatalogText(query: String): Boolean {
    val normalizedSelf = normalizedCatalogText()
    val normalizedQuery = query.normalizedCatalogText()
    return normalizedSelf.isNotBlank() &&
        normalizedQuery.isNotBlank() &&
        normalizedSelf.contains(normalizedQuery)
}

private fun String.normalizedCatalogText(): String {
    val stripped = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("ł", "l")
        .replace("Ł", "L")
        .replace(Regex("\\p{Mn}+"), "")
    return stripped.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

private fun String.catalogSlug(): String =
    normalizedCatalogText()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private fun String.titleized(): String =
    split('-').joinToString(" ") { word ->
        word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString() }
    }

private fun absoluteUrl(value: String): String =
    when {
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://wolnelektury.pl$value"
        else -> "https://wolnelektury.pl/$value"
    }

private fun String.catalogBookSlugFromHref(): String? =
    substringBefore('?')
        .substringBefore('#')
        .substringAfter("/katalog/lektura/", missingDelimiterValue = "")
        .trim('/')
        .takeIf { it.isNotBlank() }

private fun String.pageQueryValue(): String? =
    substringAfter("page=", missingDelimiterValue = "")
        .takeWhile { it.isDigit() }
        .takeIf { it.isNotBlank() }

private fun languageLabel(code: String?): String =
    when (code?.lowercase(Locale.ROOT)) {
        null,
        "",
        "pol",
        "pl" -> "Polish"
        "eng",
        "en" -> "English"
        "fre",
        "fra",
        "fr" -> "French"
        "ger",
        "deu",
        "de" -> "German"
        "ita",
        "it" -> "Italian"
        "spa",
        "es" -> "Spanish"
        "rus",
        "ru" -> "Russian"
        "ukr",
        "uk" -> "Ukrainian"
        "lit",
        "lt" -> "Lithuanian"
        else -> code
    }

private fun languageIsPolishOrAny(language: String): Boolean =
    language.isBlank() ||
        language.equals("Polish", ignoreCase = true) ||
        language.equals("pol", ignoreCase = true) ||
        language.equals("pl", ignoreCase = true)
