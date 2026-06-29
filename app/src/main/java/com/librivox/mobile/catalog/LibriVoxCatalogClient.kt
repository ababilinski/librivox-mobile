package com.librivox.mobile.catalog

import android.content.Context
import android.util.Log
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.FallbackCover
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

enum class CatalogSearchField { All, Title, Chapter, Author, Reader, Genre, Epoch, Kind }

class LibriVoxCatalogSource internal constructor(
    private val httpClient: OkHttpClient,
) : CatalogSource {

    constructor(context: Context) : this(buildSharedClient(context))

    internal constructor() : this(OkHttpClient())

    override suspend fun featuredBooks(): List<AudioBook> =
        browse(limit = FEATURED_CANDIDATE_LIMIT, offset = 0, language = "")

    override suspend fun fetchByIds(vararg ids: String): List<AudioBook> =
        withContext(Dispatchers.IO) {
            ids.mapNotNull { id ->
                runCatching {
                    parseBooksJson(
                        get(
                            buildMap {
                                put("id", id)
                                putExtendedBookFields()
                            },
                        ),
                    ).firstOrNull()
                }.getOrNull()
            }
        }

    override suspend fun search(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> {
        val q = sanitizeCatalogSearchQuery(query)
        if (q.isBlank()) {
            return browse(limit = limit, offset = offset, language = language)
        }
        if (field == CatalogSearchField.All) {
            return searchAllFields(query, limit = limit, offset = offset, language = language)
        }
        return searchSingleField(query, field = field, limit = limit, offset = offset, language = language)
    }

    private suspend fun searchAllFields(
        query: String,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> {
        val requested = offset.coerceAtLeast(0) + limit.coerceAtLeast(1)
        val combined = mutableListOf<AudioBook>()
        for (field in listOf(
            CatalogSearchField.Title,
            CatalogSearchField.Chapter,
            CatalogSearchField.Author,
            CatalogSearchField.Reader,
            CatalogSearchField.Genre,
        )) {
            val fieldResults = searchSingleField(query, field = field, limit = requested, offset = 0, language = language)
            combined += fieldResults
            if (fieldResults.isNotEmpty()) {
                val ranked = combined.rankForCatalogSearch(query, CatalogSearchField.All)
                val validResults = ranked.ifEmpty {
                    if (field == CatalogSearchField.Author || field == CatalogSearchField.Reader) {
                        fieldResults
                    } else {
                        emptyList()
                    }
                }
                return validResults
                    .drop(offset.coerceAtLeast(0))
                    .take(limit.coerceAtLeast(1))
            }
            if (combined.dedupeCatalogBooks().size >= requested) {
                break
            }
        }
        return combined
            .rankForCatalogSearch(query, CatalogSearchField.All)
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
    }

    private suspend fun searchSingleField(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        withContext(Dispatchers.IO) {
            if (field == CatalogSearchField.Epoch || field == CatalogSearchField.Kind) {
                return@withContext emptyList()
            }
            if (offset == 0) {
                val sectionResults = searchPersonSectionResults(query, field, limit, language)
                if (sectionResults.isNotEmpty()) {
                    return@withContext sectionResults
                }
            }
            val results = mutableListOf<AudioBook>()
            for ((key, value) in searchRequestValues(query, field)) {
                val raw = runCatching {
                    get(
                        buildMap {
                            put(key, value)
                            putLanguage(language)
                            putExtendedBookFields()
                            putPaging(limit, offset)
                        },
                    )
                }.getOrElse { throwable ->
                    logUnexpectedCatalogFailure("LibriVox $field search request failed for $key", throwable)
                    continue
                }
                val books = parseBooksJson(
                    raw,
                ).filterByLanguage(language)
                    .filterForSearch(query, field)
                results += books
                if (results.isNotEmpty()) {
                    return@withContext results.distinctBy { it.id }
                }
            }
            if (field == CatalogSearchField.Title) {
                val requested = offset.coerceAtLeast(0) + limit.coerceAtLeast(1)
                val archiveResults = searchArchiveTitleResults(
                    query = query,
                    limit = requested,
                    language = language,
                )
                if (archiveResults.isNotEmpty()) {
                    return@withContext archiveResults
                        .drop(offset.coerceAtLeast(0))
                        .take(limit.coerceAtLeast(1))
                }
            }
            results.distinctBy { it.id }
        }

    override suspend fun browse(
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        withContext(Dispatchers.IO) {
            parseBooksJson(
                get(
                    buildMap {
                        putLanguage(language)
                        putExtendedBookFields()
                        putPaging(limit, offset)
                    },
                ),
            ).filterByLanguage(language)
        }

    override suspend fun recent(sinceEpochSeconds: Long, limit: Int): List<AudioBook> =
        withContext(Dispatchers.IO) {
            parseBooksJson(
                get(
                    buildMap {
                        put("since", sinceEpochSeconds.toString())
                        putExtendedBookFields()
                        put("limit", limit.toString())
                    },
                ),
            )
        }

    override suspend fun byGenre(genre: String, limit: Int, language: String): List<AudioBook> =
        search(genre, CatalogSearchField.Genre, limit = limit, language = language)

    override suspend fun byAuthor(author: String, limit: Int, language: String): List<AudioBook> =
        search(author, CatalogSearchField.Author, limit = limit, language = language)

    fun parseBooksJson(rawJson: String): List<AudioBook> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val books = root["books"] as? JsonArray ?: return emptyList()
        return books.mapNotNull { element ->
            val book = element.jsonObject
            val id = book.string("id") ?: return@mapNotNull null
            val title = book.string("title").orEmpty().normalizedTitle(id)
            val sections = (book["sections"] as? JsonArray).orEmpty()
            val urlIArchive = book.string("url_iarchive")
            val fullCoverUrl = book.string("coverart_jpg")
                ?: archiveCoverUrl(urlIArchive)
            val coverImageUrl = book.string("coverart_thumbnail")
                ?: fullCoverUrl
            AudioBook(
                id = id,
                title = title,
                author = book.authors(),
                description = book.string("description").orEmpty(),
                source = BookSource.LibriVox,
                coverImageUrl = coverImageUrl,
                fullCoverImageUrl = fullCoverUrl,
                fallbackCover = fallbackCoverFor(id),
                chapters = sections.mapNotNull { sectionElement ->
                    val section = sectionElement.jsonObject
                    val sectionId = section.string("id")
                        ?: "$id-section-${section.string("section_number").orEmpty()}"
                    val number = section.string("section_number")?.toIntOrNull() ?: 0
                    val listenUrl = section.string("listen_url")
                    if (listenUrl.isNullOrBlank()) {
                        return@mapNotNull null
                    }
                    AudioBookChapter(
                        id = sectionId,
                        title = section.string("title").orEmpty().ifBlank { "Chapter $number" },
                        number = number,
                        reader = section.readers(),
                        durationSeconds = section.string("playtime")?.toLongOrNull() ?: 0L,
                        listenUrl = listenUrl,
                    )
                }.sortedBy { it.number },
                totalDurationSeconds = book.long("totaltimesecs"),
                librivoxUrl = book.string("url_librivox"),
                language = book.string("language"),
                genres = book.genres(),
            )
        }
    }

    private fun get(parameters: Map<String, String>): String =
        getFrom(BASE_URL, parameters)

    private fun getFrom(baseUrl: String, parameters: Map<String, String>): String {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "LibriVox responded ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    private fun getWebResults(category: String, id: String, page: Int): String {
        val url = "${WEB_BASE_URL}${category}/get_results".toHttpUrl().newBuilder()
            .addQueryParameter("primary_key", id)
            .addQueryParameter("search_category", category)
            .addQueryParameter("sub_category", "")
            .addQueryParameter("search_page", page.toString())
            .addQueryParameter("search_order", "catalog_date")
            .addQueryParameter("project_type", "either")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "LibriVox responded ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    private fun searchPersonSectionResults(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        language: String,
    ): List<AudioBook> {
        val pointers = personPointersForQuery(query, field)
        if (pointers.isEmpty()) return emptyList()

        val titleCandidates = linkedSetOf<String>()
        pointers.forEach { pointer ->
            for (page in 1..PERSON_RESULT_MAX_PAGES) {
                val rawResults = runCatching {
                    parsePersonResultsHtml(getWebResults(pointer.category, pointer.id, page))
                }.getOrDefault("")
                if (rawResults.isBlank()) break
                titleCandidates += personResultTitleCandidates(rawResults)
                if (titleCandidates.size >= PERSON_RESULT_TITLE_LIMIT) break
            }
        }
        if (titleCandidates.isEmpty()) return emptyList()
        return fetchTitleCandidates(
            titles = titleCandidates.toList(),
            limit = limit,
            language = language,
        )
    }

    private fun personPointersForQuery(
        query: String,
        field: CatalogSearchField,
    ): List<LibriVoxPersonPointer> {
        val pointers = librivoxPersonPointers(query).filter { it.matchesField(field) }
        if (pointers.isNotEmpty()) return pointers
        val sanitized = sanitizeCatalogSearchQuery(query)
        if (sanitized.isBlank()) return emptyList()
        return when (field) {
            CatalogSearchField.All,
            CatalogSearchField.Author -> resolveAuthorPointers(sanitized)
            CatalogSearchField.Reader -> resolveReaderPointers(sanitized)
            CatalogSearchField.Title,
            CatalogSearchField.Chapter,
            CatalogSearchField.Genre,
            CatalogSearchField.Epoch,
            CatalogSearchField.Kind -> emptyList()
        }
    }

    private fun resolveAuthorPointers(query: String): List<LibriVoxPersonPointer> {
        val parts = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return emptyList()
        val raw = runCatching {
            getFrom(
                AUTHORS_BASE_URL,
                buildMap {
                    put("format", "json")
                    put("first_name", parts.dropLast(1).joinToString(" "))
                    put("last_name", parts.last())
                },
            )
        }.getOrNull() ?: return emptyList()
        val authors = runCatching {
            json.parseToJsonElement(raw).jsonObject["authors"] as? JsonArray
        }.getOrNull().orEmpty()
        return authors.mapNotNull { element ->
            val author = element.jsonObject
            val id = author.string("id") ?: return@mapNotNull null
            val name = listOfNotNull(author.string("first_name"), author.string("last_name"))
                .joinToString(" ")
                .trim()
            LibriVoxPersonPointer("author", id).takeIf { name.matchesCatalogQuery(query) }
        }.take(PERSON_POINTER_LIMIT)
    }

    private fun resolveReaderPointers(query: String): List<LibriVoxPersonPointer> {
        val raw = runCatching {
            get(
                buildMap {
                    put("reader", query)
                    putExtendedBookFields()
                    put("limit", "1")
                },
            )
        }.getOrNull() ?: return emptyList()
        val books = runCatching {
            json.parseToJsonElement(raw).jsonObject["books"] as? JsonArray
        }.getOrNull().orEmpty()
        return books
            .flatMap { book ->
                (book.jsonObject["sections"] as? JsonArray).orEmpty().flatMap { section ->
                    (section.jsonObject["readers"] as? JsonArray).orEmpty().mapNotNull { reader ->
                        val readerObject = reader.jsonObject
                        val id = readerObject.string("reader_id") ?: return@mapNotNull null
                        val name = readerObject.string("display_name").orEmpty()
                        LibriVoxPersonPointer("reader", id).takeIf { name.matchesCatalogQuery(query) }
                    }
                }
            }
            .distinct()
            .take(PERSON_POINTER_LIMIT)
    }

    private fun fetchTitleCandidates(
        titles: List<String>,
        limit: Int,
        language: String,
    ): List<AudioBook> {
        val books = mutableListOf<AudioBook>()
        for (title in titles.distinct().take(PERSON_RESULT_TITLE_LIMIT)) {
            for ((key, value) in searchRequestValues(title, CatalogSearchField.Title)) {
                val raw = runCatching {
                    get(
                        buildMap {
                            put(key, value)
                            putLanguage(language)
                            putExtendedBookFields()
                            putPaging(limit, 0)
                        },
                    )
                }.getOrElse { throwable ->
                    logUnexpectedCatalogFailure("LibriVox title candidate request failed for $key", throwable)
                    continue
                }
                val parsed = parseBooksJson(
                    raw,
                ).filterByLanguage(language)
                    .filterForSearch(title, CatalogSearchField.Title)
                if (parsed.isNotEmpty()) {
                    books += parsed
                    break
                }
            }
            if (books.distinctBy { it.id }.size >= limit.coerceAtLeast(1)) break
        }
        return books.distinctBy { it.id }.take(limit.coerceAtLeast(1))
    }

    private fun searchArchiveTitleResults(
        query: String,
        limit: Int,
        language: String,
    ): List<AudioBook> {
        val titles = archiveTitleCandidates(query, limit)
        if (titles.isEmpty()) return emptyList()
        return fetchTitleCandidates(titles, limit, language)
            .filterForSearch(query, CatalogSearchField.Title)
    }

    private fun archiveTitleCandidates(query: String, limit: Int): List<String> {
        val searchExpression = archiveTitleSearchExpression(query) ?: return emptyList()
        val raw = runCatching {
            getFrom(
                ARCHIVE_SEARCH_BASE_URL,
                buildMap {
                    put("q", searchExpression)
                    put("fl[]", "title")
                    put("rows", limit.coerceIn(1, ARCHIVE_TITLE_CANDIDATE_LIMIT).toString())
                    put("page", "1")
                    put("output", "json")
                },
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Archive title fallback failed", throwable)
        }.getOrNull() ?: return emptyList()
        return parseArchiveTitleCandidates(raw)
    }

    private fun parseArchiveTitleCandidates(rawJson: String): List<String> {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }.getOrNull() ?: return emptyList()
        val response = root["response"] as? JsonObject ?: return emptyList()
        val docs = response["docs"] as? JsonArray ?: return emptyList()
        return docs
            .mapNotNull { (it as? JsonObject)?.string("title")?.stripCatalogHtml() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun archiveTitleSearchExpression(query: String): String? {
        val normalized = normalizedCatalogSearchText(query)
        if (normalized.isBlank()) return null
        val titleClause = if (' ' in normalized) {
            "title:($normalized)"
        } else {
            "title:$normalized"
        }
        return "collection:librivoxaudio AND $titleClause"
    }

    private fun logUnexpectedCatalogFailure(message: String, throwable: Throwable) {
        if (!throwable.isHttpNotFound()) {
            Log.w(TAG, message, throwable)
        }
    }

    private fun MutableMap<String, String>.putExtendedBookFields() {
        put("format", "json")
        put("extended", "1")
        put("coverart", "1")
    }

    private fun MutableMap<String, String>.putPaging(limit: Int, offset: Int) {
        put("limit", limit.coerceIn(1, MAX_PAGE_SIZE).toString())
        put("offset", offset.coerceAtLeast(0).toString())
    }

    private fun MutableMap<String, String>.putLanguage(language: String) {
        if (language.isNotBlank()) {
            put("language", language)
        }
    }

    private fun JsonObject.authors(): String {
        val authors = this["authors"] as? JsonArray ?: return "Unknown author"
        return authors.mapNotNull { element ->
            val author = element.jsonObject
            listOfNotNull(author.string("first_name"), author.string("last_name"))
                .joinToString(" ")
                .trim()
                .ifBlank { null }
        }.joinToString(", ").ifBlank { "Unknown author" }
    }

    private fun JsonObject.readers(): String? {
        val readers = this["readers"] as? JsonArray ?: return null
        return readers.mapNotNull { element ->
            element.jsonObject.string("display_name")
        }.joinToString(", ").ifBlank { null }
    }

    private fun JsonObject.genres(): List<String> {
        val genres = this["genres"] as? JsonArray ?: return emptyList()
        return genres.mapNotNull { it.jsonObject.string("name") }
    }

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.long(key: String): Long =
        primitive(key)?.content?.toLongOrNull() ?: 0L

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        this[key]?.jsonPrimitive

    private fun String.normalizedTitle(id: String): String =
        if (id == AudioBookLibrary.THE_JUNGLE_ID && equals("Jungle", ignoreCase = true)) {
            "The Jungle"
        } else {
            this
        }

    private fun fallbackCoverFor(id: String): FallbackCover =
        when (id) {
            AudioBookLibrary.THE_JUNGLE_ID -> FallbackCover.Jungle
            AudioBookLibrary.PRIDE_AND_PREJUDICE_ID -> FallbackCover.Pride
            else -> FallbackCover.Generated
        }

    internal companion object {
        const val BASE_URL = "https://librivox.org/api/feed/audiobooks/"
        private const val TAG = "LibriVoxCatalog"
        private const val AUTHORS_BASE_URL = "https://librivox.org/api/feed/authors/"
        private const val ARCHIVE_SEARCH_BASE_URL = "https://archive.org/advancedsearch.php"
        private const val WEB_BASE_URL = "https://librivox.org/"
        const val DEFAULT_PAGE_SIZE = 50
        private const val FEATURED_CANDIDATE_LIMIT = 80
        private const val MAX_PAGE_SIZE = 100
        private const val ARCHIVE_TITLE_CANDIDATE_LIMIT = 24
        private const val PERSON_POINTER_LIMIT = 2
        private const val PERSON_RESULT_MAX_PAGES = 3
        private const val PERSON_RESULT_TITLE_LIMIT = 60
        val json = Json { ignoreUnknownKeys = true }

        internal fun searchRequestValues(
            query: String,
            field: CatalogSearchField,
        ): List<Pair<String, String>> {
            val key = when (field) {
                CatalogSearchField.All,
                CatalogSearchField.Chapter,
                CatalogSearchField.Title -> "title"
                CatalogSearchField.Author -> "author"
                CatalogSearchField.Reader -> "reader"
                CatalogSearchField.Genre -> "genre"
                CatalogSearchField.Epoch,
                CatalogSearchField.Kind -> "genre"
            }
            val queryVariants = catalogSearchQueryVariants(query)
            if (queryVariants.isEmpty()) return emptyList()

            val values = queryVariants.flatMap { trimmed ->
                val anchored = if (trimmed.startsWith("^")) trimmed else "^$trimmed"
                when (field) {
                    CatalogSearchField.All,
                    CatalogSearchField.Chapter,
                    CatalogSearchField.Title -> listOf(anchored)
                    CatalogSearchField.Author,
                    CatalogSearchField.Reader,
                    CatalogSearchField.Genre,
                    CatalogSearchField.Epoch,
                    CatalogSearchField.Kind -> listOf(trimmed, anchored)
                }
            } + when (field) {
                CatalogSearchField.All,
                CatalogSearchField.Chapter,
                CatalogSearchField.Title -> queryVariants
                CatalogSearchField.Author,
                CatalogSearchField.Reader,
                CatalogSearchField.Genre,
                CatalogSearchField.Epoch,
                CatalogSearchField.Kind -> emptyList()
            }
            return values.distinct().map { key to it }
        }

        fun archiveCoverUrl(urlIArchive: String?): String? {
            if (urlIArchive.isNullOrBlank()) return null
            val identifier = urlIArchive
                .substringAfter("archive.org/details/", missingDelimiterValue = "")
                .substringBefore('/')
                .substringBefore('?')
                .trim()
            return identifier.takeIf { it.isNotBlank() }
                ?.let { "https://archive.org/services/img/$it" }
        }

        private fun buildSharedClient(context: Context): OkHttpClient {
            val cacheDir = File(context.cacheDir, "librivox-http").apply { mkdirs() }
            return OkHttpClient.Builder()
                .cache(Cache(cacheDir, CACHE_SIZE_BYTES))
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
        }

        private const val CACHE_SIZE_BYTES: Long = 4L * 1024 * 1024
        private const val CONNECT_TIMEOUT_SECONDS: Long = 12
    }
}

private fun Throwable.isHttpNotFound(): Boolean =
    message?.contains("responded 404") == true

private fun LibriVoxPersonPointer.matchesField(field: CatalogSearchField): Boolean =
    field == CatalogSearchField.All ||
        (category == "author" && field == CatalogSearchField.Author) ||
        (category == "reader" && field == CatalogSearchField.Reader)

private fun parsePersonResultsHtml(rawJson: String): String {
    val root = LibriVoxCatalogSource.json.parseToJsonElement(rawJson).jsonObject
    return root["results"]?.jsonPrimitive?.content.orEmpty()
}

private fun personResultTitleCandidates(resultsHtml: String): List<String> =
    PersonResultTitleRegex.findAll(resultsHtml)
        .map { match -> match.groupValues[1].stripCatalogHtml() }
        .mapNotNull { title ->
            val collectionTitle = title.substringAfter("(in", missingDelimiterValue = "")
                .substringBefore(")")
                .trim()
            collectionTitle.ifBlank { title }.takeIf { it.isNotBlank() }
        }
        .distinct()
        .toList()

private fun String.stripCatalogHtml(): String =
    replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&#039;", "'")
        .replace("&quot;", "\"")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private val PersonResultTitleRegex = Regex(
    """<h3>\s*<a href="https://librivox\.org/[^"]+/">(.+?)</a>\s*</h3>""",
    RegexOption.DOT_MATCHES_ALL,
)

private fun JsonElement?.orEmpty(): JsonArray =
    this as? JsonArray ?: JsonArray(emptyList())

private fun List<AudioBook>.filterByLanguage(language: String): List<AudioBook> {
    if (language.isBlank()) return this
    return filter { book ->
        book.language.equals(language, ignoreCase = true)
    }
}

private fun List<AudioBook>.filterForSearch(
    query: String,
    field: CatalogSearchField,
): List<AudioBook> =
    when (field) {
        CatalogSearchField.All -> filter { it.catalogSearchMatch(query, CatalogSearchField.All) != null }
        CatalogSearchField.Title -> filter { book ->
            book.catalogSearchMatch(query, CatalogSearchField.Title) != null
        }
        CatalogSearchField.Chapter -> filter { book ->
            book.catalogSearchMatch(query, CatalogSearchField.Chapter) != null
        }
        CatalogSearchField.Author -> filter { it.author.matchesCatalogQuery(query) }
        CatalogSearchField.Reader -> filter { book ->
            book.chapters.any { it.reader.orEmpty().matchesCatalogQuery(query) }
        }
        CatalogSearchField.Genre -> filter { book ->
            book.genres.any { it.matchesCatalogQuery(query) }
        }
        CatalogSearchField.Epoch,
        CatalogSearchField.Kind -> emptyList()
    }

private fun String.matchesCatalogQuery(query: String): Boolean {
    val normalizedSelf = normalizedCatalogSearchText(this)
    val normalizedQuery = normalizedCatalogSearchText(query)
    if (normalizedSelf.isBlank() || normalizedQuery.isBlank()) return false
    return normalizedSelf == normalizedQuery ||
        normalizedSelf.contains(normalizedQuery) ||
        normalizedQuery.contains(normalizedSelf)
}
