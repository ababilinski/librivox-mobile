package com.librivox.mobile.catalog

import android.content.Context
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.FallbackCover
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val GUTENDEX_ID_PREFIX = "gutendex-"

class GutendexCatalogSource internal constructor(
    private val httpClient: OkHttpClient,
) : CatalogSource {

    constructor(context: Context) : this(buildSharedClient(context))

    internal constructor() : this(OkHttpClient())

    override suspend fun featuredBooks(): List<AudioBook> =
        browse(limit = CatalogSource.DEFAULT_PAGE_SIZE, offset = 0, language = "")

    override suspend fun fetchByIds(vararg ids: String): List<AudioBook> =
        coroutineScope {
            ids.map { rawId ->
                async(Dispatchers.IO) {
                    val id = decodeId(rawId) ?: return@async null
                    runCatching { resolveAudioBook(rawId, id) }.getOrNull()
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
        val q = query.trim()
        if (q.isBlank()) {
            return browse(limit = limit, offset = offset, language = language)
        }
        if (field == CatalogSearchField.All) {
            return searchAllFields(q, limit = limit, offset = offset, language = language)
        }
        if (
            field == CatalogSearchField.Chapter ||
            field == CatalogSearchField.Reader ||
            field == CatalogSearchField.Epoch ||
            field == CatalogSearchField.Kind
        ) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val parameters = buildMap {
                putLanguage(language)
                when (field) {
                    CatalogSearchField.Genre -> put("topic", q)
                    CatalogSearchField.All,
                    CatalogSearchField.Title,
                    CatalogSearchField.Chapter,
                    CatalogSearchField.Author -> put("search", q)
                    CatalogSearchField.Reader,
                    CatalogSearchField.Epoch,
                    CatalogSearchField.Kind -> Unit
                }
            }
            fetchPagedBooks(parameters, limit, offset)
                .filter { it.matches(q, field) }
        }
    }

    private suspend fun searchAllFields(
        query: String,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> {
        val safeOffset = offset.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        val requested = safeOffset + safeLimit
        val searchMatches = withContext(Dispatchers.IO) {
            fetchPagedBooks(
                parameters = buildMap {
                    putLanguage(language)
                    put("search", query)
                },
                limit = requested,
                offset = 0,
            ).filter { it.matches(query, CatalogSearchField.All) }
        }
        return searchMatches
            .distinctBy { it.id }
            .drop(safeOffset)
            .take(safeLimit)
    }

    override suspend fun browse(
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        withContext(Dispatchers.IO) {
            fetchPagedBooks(
                parameters = buildMap {
                    putLanguage(language)
                    put("sort", "popular")
                },
                limit = limit,
                offset = offset,
            )
        }

    override suspend fun recent(sinceEpochSeconds: Long, limit: Int): List<AudioBook> =
        browse(limit = limit, offset = 0, language = "")

    override suspend fun byGenre(genre: String, limit: Int, language: String): List<AudioBook> =
        search(genre, CatalogSearchField.Genre, limit = limit, offset = 0, language = language)

    override suspend fun byAuthor(author: String, limit: Int, language: String): List<AudioBook> =
        search(author, CatalogSearchField.Author, limit = limit, offset = 0, language = language)

    internal fun parseBooksJson(rawJson: String): List<AudioBook> {
        val root = json.parseToJsonElement(rawJson) as? JsonObject ?: return emptyList()
        val books = (root["results"] as? JsonArray)
            ?: JsonArray(listOf(root))
        return books.mapNotNull { element ->
            val book = element as? JsonObject ?: return@mapNotNull null
            val gutenbergId = book.int("id") ?: return@mapNotNull null
            val title = book.string("title") ?: return@mapNotNull null
            val languages = book.stringArray("languages")
            val formats = book["formats"] as? JsonObject
            val audioFormat = formats?.preferredAudioFormat() ?: return@mapNotNull null
            AudioBook(
                id = encodeId(gutenbergId),
                title = title,
                author = book.displayCreator(),
                description = book.stringArray("summaries").firstOrNull().orEmpty(),
                source = BookSource.Gutendex,
                coverImageUrl = formats.imageUrl(),
                fallbackCover = FallbackCover.Generated,
                chapters = listOf(
                    AudioBookChapter(
                        id = "${encodeId(gutenbergId)}-audio",
                        title = "Complete audiobook",
                        number = 1,
                        listenUrl = audioFormat.url,
                        mimeType = audioFormat.mimeType,
                    ),
                ),
                gutenbergUrl = "$GUTENBERG_BASE/ebooks/$gutenbergId",
                language = languageLabel(languages.firstOrNull()),
                genres = (book.stringArray("bookshelves") + book.stringArray("subjects"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MaxGenres),
            )
        }
    }

    private fun resolveAudioBook(rawId: String, gutenbergId: Int): AudioBook? {
        val rawBook = getBook(gutenbergId)
        parseBooksJson(rawBook).firstOrNull()?.let {
            return it.withGutenbergIndexChapters(rawBook)
                .withAudiobookCoverFallback()
        }
        val lookup = parseLookup(rawBook) ?: return null
        val lookupKey = BookMerger.matchKey(lookup.asBook(rawId))
        return fetchPagedBooks(
            parameters = mapOf("search" to lookup.title),
            limit = StaleTextIdAudioLookupLimit,
            offset = 0,
        ).firstOrNull { candidate ->
            BookMerger.matchKey(candidate) == lookupKey
        }?.copy(id = rawId)
            ?.withAudiobookCoverFallback()
    }

    private fun AudioBook.withGutenbergIndexChapters(rawJson: String): AudioBook {
        val indexUrl = parseBookObject(rawJson)
            ?.let { book -> (book["formats"] as? JsonObject)?.gutenbergAudioIndexUrl() }
            ?: return this
        val indexHtml = runCatching { getHtml(indexUrl) }.getOrNull() ?: return this
        val index = parseGutenbergAudioIndex(id, indexUrl, indexHtml)
        if (index.chapters.isEmpty()) return this
        return copy(
            chapters = index.chapters,
            totalDurationSeconds = index.chapters.sumOf { it.durationSeconds },
            narrators = narrators.ifEmpty { index.narrators },
        )
    }

    private fun fetchPagedBooks(
        parameters: Map<String, String>,
        limit: Int,
        offset: Int,
    ): List<AudioBook> {
        val safeLimit = limit.coerceAtLeast(1)
        var remaining = safeLimit
        var page = (offset.coerceAtLeast(0) / GutendexPageSize) + 1
        var skip = offset.coerceAtLeast(0) % GutendexPageSize
        val results = mutableListOf<AudioBook>()
        while (remaining > 0) {
            val pageBooks = parseBooksJson(get(parameters + ("page" to page.toString())))
            if (pageBooks.isEmpty()) break
            val pageSlice = pageBooks.drop(skip).take(remaining)
            results += pageSlice
            remaining -= pageSlice.size
            if (pageBooks.size < GutendexPageSize) break
            page++
            skip = 0
        }
        return results
    }

    private fun get(parameters: Map<String, String>): String {
        val url = booksUrl().newBuilder().apply {
            (parameters + ("mime_type" to "audio")).forEach { (key, value) ->
                addQueryParameter(key, value)
            }
        }.build()
        return getUrl(url)
    }

    private fun getBook(id: Int): String =
        getUrl(booksUrl().newBuilder().addPathSegment(id.toString()).build())

    private fun getHtml(url: String): String {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Accept", "text/html")
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Gutenberg responded ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    private fun getUrl(url: HttpUrl, serviceName: String = "Gutendex"): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "$serviceName responded ${response.code}" }
            response.body?.string().orEmpty()
        }
    }

    private fun booksUrl(): HttpUrl =
        BASE_URL.toHttpUrl().newBuilder().addPathSegment("books").build()

    private fun MutableMap<String, String>.putLanguage(language: String) {
        languageCode(language)?.let { put("languages", it) }
    }

    private fun AudioBook.matches(query: String, field: CatalogSearchField): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return when (field) {
            CatalogSearchField.All -> catalogSearchMatch(query, CatalogSearchField.All) != null
            CatalogSearchField.Title -> title.lowercase(Locale.ROOT).contains(normalized)
            CatalogSearchField.Chapter -> false
            CatalogSearchField.Author -> author.lowercase(Locale.ROOT).contains(normalized)
            CatalogSearchField.Genre -> genres.any { it.lowercase(Locale.ROOT).contains(normalized) }
            CatalogSearchField.Reader,
            CatalogSearchField.Epoch,
            CatalogSearchField.Kind -> false
        }
    }

    private fun JsonObject.people(key: String): List<String> {
        val people = this[key] as? JsonArray ?: return emptyList()
        return people.mapNotNull { element ->
            val person = element as? JsonObject ?: return@mapNotNull null
            person.string("name")?.displayPersonName()
        }
    }

    private fun JsonObject.displayCreator(): String {
        val authors = people("authors")
        if (authors.isNotEmpty()) return authors.joinToString(", ")
        val translators = people("translators")
        if (translators.isNotEmpty()) return "Translated by ${translators.joinToString(", ")}"
        val editors = people("editors")
        if (editors.isNotEmpty()) return "Edited by ${editors.joinToString(", ")}"
        return UnknownAuthor
    }

    private fun JsonObject.stringArray(key: String): List<String> {
        val values = this[key] as? JsonArray ?: return emptyList()
        return values.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.imageUrl(): String? =
        entries.firstOrNull { (mimeType, _) ->
            mimeType.equals("image/jpeg", ignoreCase = true) || mimeType.startsWith("image/")
        }?.value?.jsonPrimitive?.content?.takeIf { it.startsWith("http") }

    private fun JsonObject.gutenbergAudioIndexUrl(): String? =
        entries.firstNotNullOfOrNull { (mimeType, element) ->
            val normalized = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
            if (normalized == "text/html") {
                element.jsonPrimitive.content.takeIf { it.startsWith("http") }
            } else {
                null
            }
        }

    private fun parseBookObject(rawJson: String): JsonObject? =
        json.parseToJsonElement(rawJson) as? JsonObject

    private fun parseLookup(rawJson: String): GutendexLookup? {
        val book = parseBookObject(rawJson) ?: return null
        val title = book.string("title") ?: return null
        return GutendexLookup(title = title, author = book.displayCreator())
    }

    private fun AudioBook.withAudiobookCoverFallback(): AudioBook {
        if (!coverImageUrl.isNullOrBlank()) return this
        val creator = author
            .removePrefix("Translated by ")
            .removePrefix("Edited by ")
            .takeUnless { it.equals(UnknownAuthor, ignoreCase = true) }
        val query = listOfNotNull(title, creator).joinToString(" ").trim()
        if (query.isBlank()) return this
        val coverUrl = runCatching { fetchAudiobookCoverUrl(query) }.getOrNull()
        return if (coverUrl.isNullOrBlank()) {
            this
        } else {
            copy(
                coverImageUrl = coverUrl,
                fullCoverImageUrl = coverUrl,
            )
        }
    }

    private fun fetchAudiobookCoverUrl(query: String): String? {
        val url = AUDIOBOOK_COVERS_BY_TEXT_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .build()
        val covers = json.parseToJsonElement(getUrl(url, serviceName = "AudiobookCovers")) as? JsonArray
            ?: return null
        return covers.firstNotNullOfOrNull { element ->
            val cover = element as? JsonObject ?: return@firstNotNullOfOrNull null
            val versions = cover["versions"] as? JsonObject ?: return@firstNotNullOfOrNull null
            val png = versions["png"] as? JsonObject ?: return@firstNotNullOfOrNull null
            png.string("original")?.takeIf { it.startsWith("http") }
        }
    }

    private fun JsonObject.preferredAudioFormat(): GutendexAudioFormat? =
        AudioMimeTypePreference
            .asSequence()
            .mapNotNull { preferredType ->
                entries.firstOrNull { (mimeType, _) ->
                    mimeType.equals(preferredType, ignoreCase = true)
                }?.toAudioFormat()
            }
            .firstOrNull()
            ?: entries
                .asSequence()
                .filter { (mimeType, _) -> mimeType.startsWith("audio/", ignoreCase = true) }
                .mapNotNull { it.toAudioFormat() }
                .firstOrNull()

    private fun Map.Entry<String, kotlinx.serialization.json.JsonElement>.toAudioFormat(): GutendexAudioFormat? {
        val url = value.jsonPrimitive.content.takeIf { it.startsWith("http") } ?: return null
        return GutendexAudioFormat(mimeType = key.substringBefore(';').trim(), url = url)
    }

    internal fun parseGutenbergAudioIndex(
        bookId: String,
        indexUrl: String,
        html: String,
    ): GutenbergAudioIndex {
        val doc = Jsoup.parse(html, indexUrl)
        val narrators = doc.gutenbergNarrators()
        val contentList = doc.gutenbergContentsList()
        val chapterItems = contentList?.children()?.filter { it.tagName().equals("li", ignoreCase = true) }
            ?: doc.select("body > ul > li, body > ol > li").toList()
        val chapters = chapterItems.mapIndexedNotNull { index, item ->
            val audioFormat = item.preferredGutenbergAudioLink() ?: return@mapIndexedNotNull null
            val (title, durationSeconds) = item.gutenbergChapterTitleAndDuration(index + 1)
            AudioBookChapter(
                id = "$bookId-${audioFormat.url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')}",
                title = title,
                number = index + 1,
                durationSeconds = durationSeconds,
                listenUrl = audioFormat.url,
                mimeType = audioFormat.mimeType,
            )
        }
        return GutenbergAudioIndex(
            chapters = chapters.distinctBy { it.id },
            narrators = narrators,
        )
    }

    private fun Document.gutenbergContentsList(): Element? {
        val contentsHeader = select("h1, h2, h3, h4, h5, h6").firstOrNull {
            it.text().trim().equals("Contents", ignoreCase = true)
        } ?: return null
        return contentsHeader.nextElementSiblings().firstOrNull { sibling ->
            sibling.tagName().equals("ul", ignoreCase = true) ||
                sibling.tagName().equals("ol", ignoreCase = true)
        }
    }

    private fun Document.gutenbergNarrators(): List<String> {
        val readerIntro = select("p").firstOrNull { paragraph ->
            paragraph.text().contains("read by", ignoreCase = true)
        } ?: return emptyList()
        val credits = readerIntro.nextElementSibling()
            ?.takeIf { it.tagName().equals("p", ignoreCase = true) }
            ?.text()
            .orEmpty()
        return credits.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun Element.preferredGutenbergAudioLink(): GutendexAudioFormat? {
        val audioFormats = select("a[href]").mapNotNull { anchor ->
            val url = anchor.absUrl("href").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            GutendexAudioFormat(
                mimeType = url.gutenbergAudioMimeType() ?: return@mapNotNull null,
                url = url,
            )
        }
        return AudioMimeTypePreference
            .asSequence()
            .mapNotNull { preferredType -> audioFormats.firstOrNull { it.mimeType == preferredType } }
            .firstOrNull()
            ?: audioFormats.firstOrNull()
    }

    private fun Element.gutenbergChapterTitleAndDuration(number: Int): Pair<String, Long> {
        val rawTitle = ownText().trim()
        val match = GutenbergChapterDurationRegex.matchEntire(rawTitle)
        val title = match?.groupValues?.get(1)?.trim().orEmpty()
            .ifBlank { rawTitle.ifBlank { "Chapter $number" } }
        val durationSeconds = match?.groupValues?.get(2)?.durationSeconds() ?: 0L
        return title to durationSeconds
    }

    private fun String.gutenbergAudioMimeType(): String? {
        val extension = substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "m4a",
            "m4b" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> null
        }
    }

    private fun String.durationSeconds(): Long {
        val parts = split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 60L * 60L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    private fun JsonObject.string(key: String): String? =
        primitive(key)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.int(key: String): Int? =
        primitive(key)?.intOrNull

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        this[key]?.jsonPrimitive

    private fun String.displayPersonName(): String =
        split(',', limit = 2)
            .takeIf { it.size == 2 }
            ?.let { parts -> "${parts[1].trim()} ${parts[0].trim()}".trim() }
            ?: this

    internal companion object {
        const val BASE_URL = "https://gutendex.com"
        const val GUTENBERG_BASE = "https://www.gutenberg.org"
        const val ID_PREFIX = GUTENDEX_ID_PREFIX
        private const val AUDIOBOOK_COVERS_BY_TEXT_URL = "https://audiobookcovers.com/cover/bytext"
        private const val UnknownAuthor = "Unknown Author"
        private const val GutendexPageSize = 32
        private const val MaxGenres = 12
        private const val StaleTextIdAudioLookupLimit = 12
        private val AudioMimeTypePreference = listOf("audio/mpeg", "audio/mp4", "audio/ogg")
        private val GutenbergChapterDurationRegex = Regex("""^(.*?)\s+-\s+((?:\d+:)?\d{2}:\d{2})$""")
        private val json = Json { ignoreUnknownKeys = true }

        internal fun encodeId(id: Int): String = "$GUTENDEX_ID_PREFIX$id"

        internal fun decodeId(rawId: String): Int? =
            rawId.removePrefix(GUTENDEX_ID_PREFIX)
                .takeIf { it != rawId && it.isNotBlank() }
                ?.toIntOrNull()

        internal fun languageCode(language: String): String? =
            when (language.trim().lowercase(Locale.ROOT)) {
                "" -> null
                "english", "en" -> "en"
                "french", "fr" -> "fr"
                "german", "de" -> "de"
                "spanish", "es" -> "es"
                "italian", "it" -> "it"
                "portuguese", "pt" -> "pt"
                "polish", "pl", "pol" -> "pl"
                else -> null
            }

        internal fun languageLabel(code: String?): String? =
            when (code?.lowercase(Locale.ROOT)) {
                null,
                "" -> null
                "en" -> "English"
                "fr" -> "French"
                "de" -> "German"
                "es" -> "Spanish"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "pl", "pol" -> "Polish"
                else -> code
            }

        private fun buildSharedClient(context: Context): OkHttpClient {
            val cacheDir = File(context.cacheDir, "gutendex-http").apply { mkdirs() }
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

private data class GutendexAudioFormat(
    val mimeType: String,
    val url: String,
)

internal data class GutenbergAudioIndex(
    val chapters: List<AudioBookChapter>,
    val narrators: List<String>,
)

private data class GutendexLookup(
    val title: String,
    val author: String,
) {
    fun asBook(id: String): AudioBook =
        AudioBook(
            id = id,
            title = title,
            author = author,
            source = BookSource.Gutendex,
        )
}
