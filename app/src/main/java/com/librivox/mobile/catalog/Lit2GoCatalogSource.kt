package com.librivox.mobile.catalog

import android.content.Context
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.FallbackCover
import java.io.File
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
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val LIT2GO_ID_PREFIX = "lit2go-"
private const val LIT2GO_SLUG_SEPARATOR = "__"

class Lit2GoCatalogSource internal constructor(
    private val httpClient: OkHttpClient,
) : CatalogSource {
    private val appleArtworkCache = ConcurrentHashMap<String, String>()

    constructor(context: Context) : this(buildSharedClient(context))

    internal constructor() : this(OkHttpClient())

    override suspend fun featuredBooks(): List<AudioBook> =
        browse(limit = FEATURED_CANDIDATE_LIMIT, offset = 0, language = "")

    override suspend fun fetchByIds(vararg ids: String): List<AudioBook> =
        coroutineScope {
            ids.map { rawId ->
                async(Dispatchers.IO) {
                    val (numericId, slug) = decodeId(rawId) ?: return@async null
                    runCatching { fetchBookDetail(numericId, slug) }.getOrNull()
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
        if (!languageIsEnglishOrAny(language)) return emptyList()
        if (
            field == CatalogSearchField.Chapter ||
            field == CatalogSearchField.Reader ||
            field == CatalogSearchField.Epoch ||
            field == CatalogSearchField.Kind
        ) {
            return emptyList()
        }
        val q = query.trim()
        if (q.isBlank()) {
            return browse(limit = limit, offset = offset, language = language)
        }
        val source = browseStubs()
        val filtered = source.filter { stub -> stub.matches(q, field) }
        return filtered
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
            .hydrate()
    }

    override suspend fun browse(
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> {
        if (!languageIsEnglishOrAny(language)) return emptyList()
        return browseStubs()
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(1))
            .hydrate()
    }

    override suspend fun recent(sinceEpochSeconds: Long, limit: Int): List<AudioBook> =
        browse(limit = limit, offset = 0, language = "")

    override suspend fun byGenre(genre: String, limit: Int, language: String): List<AudioBook> =
        search(genre, CatalogSearchField.Genre, limit = limit, offset = 0, language = language)

    override suspend fun byAuthor(author: String, limit: Int, language: String): List<AudioBook> =
        search(author, CatalogSearchField.Author, limit = limit, offset = 0, language = language)

    internal fun parseLetterIndex(html: String): List<BookStub> {
        val doc = Jsoup.parse(html, BASE_URL)
        val stubs = mutableListOf<BookStub>()
        doc.select("a.book_icon").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val match = BookDetailPathRegex.find(href) ?: return@forEach
            val id = match.groupValues[1]
            val slug = match.groupValues[2]
            val img = anchor.selectFirst("img")
            val title = img?.attr("alt")?.trim().orEmpty().ifBlank { slug.titleized() }
            val cover = img?.let { node ->
                node.attr("data-src").ifBlank { node.attr("src") }
            }?.takeIf { it.isNotBlank() && !it.contains("place-holder") }
                ?: "$BASE_URL/lit2go/static/thumbnails/books/$id.png"
            val author = anchor.findFollowingAuthor()
            stubs += BookStub(
                id = id,
                slug = slug,
                title = title,
                coverUrl = cover,
                author = author,
            )
        }
        return stubs.distinctBy { it.id }
    }

    internal fun parseBookDetail(numericId: String, slug: String, html: String): AudioBook {
        val doc = Jsoup.parse(html, "$BASE_URL/lit2go/$numericId/$slug/")
        val title = doc.selectFirst("#column_primary h2")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: slug.titleized()
        val author = doc.parseAuthor()
        val description = doc.parseDescription()
        val genres = doc.parseGenres()
        val coverUrl = doc.parseThumbnailCoverUrl(numericId)
        val fullCoverUrl = doc.parseFullCoverUrl()
        val chapterRefs = doc.parseChapterRefs(numericId, slug)
        val chapters = chapterRefs.mapIndexed { index, ref ->
            AudioBookChapter(
                id = ref.id,
                title = ref.title,
                number = index + 1,
                listenUrl = chapterAudioUrl(slug, index + 1, ref.slug, ref.numericId),
                mimeType = "audio/mpeg",
            )
        }
        return AudioBook(
            id = encodeId(numericId, slug),
            title = title,
            author = author,
            description = description,
            source = BookSource.Lit2Go,
            coverImageUrl = coverUrl,
            fullCoverImageUrl = fullCoverUrl,
            fallbackCover = FallbackCover.Generated,
            chapters = chapters,
            totalDurationSeconds = chapters.sumOf { it.durationSeconds },
            lit2goUrl = "$BASE_URL/lit2go/$numericId/$slug/",
            language = "English",
            genres = genres,
        )
    }

    private suspend fun browseStubs(): List<BookStub> = withContext(Dispatchers.IO) {
        coroutineScope {
            BrowseLetters.map { letter ->
                async {
                    runCatching {
                        parseLetterIndex(getOrEmpty("$BASE_URL/lit2go/books/index/$letter/"))
                    }.getOrNull().orEmpty()
                }
            }.flatMap { it.await() }
                .distinctBy { it.id }
                .sortedBy { it.title.lowercase(Locale.ROOT) }
        }
    }

    private suspend fun List<BookStub>.hydrate(): List<AudioBook> = coroutineScope {
        map { stub ->
            async(Dispatchers.IO) {
                runCatching { fetchBookDetail(stub.id, stub.slug) }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun fetchBookDetail(numericId: String, slug: String): AudioBook =
        withContext(Dispatchers.IO) {
            val html = getOrEmpty("$BASE_URL/lit2go/$numericId/$slug/")
            parseBookDetail(numericId, slug, html).withAppleBookArtworkFallback()
        }

    private fun getOrEmpty(url: String, accept: String = "text/html"): String = runCatching {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .header("Accept", accept)
            .header("User-Agent", "AudioPlayerPrototype/1.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching ""
            response.body?.string().orEmpty()
        }
    }.getOrDefault("")

    private fun AudioBook.withAppleBookArtworkFallback(): AudioBook {
        val appleCoverUrl = runCatching { fetchAppleBookArtworkUrl(title, author) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return this
        return copy(
            coverImageUrl = appleCoverUrl,
            fullCoverImageUrl = appleCoverUrl,
        )
    }

    private fun fetchAppleBookArtworkUrl(title: String, author: String): String? {
        val query = appleArtworkSearchQuery(title, author)
        if (query.isBlank()) return null
        val cacheKey = query.lowercase(Locale.ROOT)
        appleArtworkCache[cacheKey]?.let { cached ->
            return cached.takeUnless { it == NO_APPLE_ARTWORK }
        }
        val url = ITUNES_SEARCH_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("term", query)
            .addQueryParameter("country", "US")
            .addQueryParameter("media", "ebook")
            .addQueryParameter("entity", "ebook")
            .addQueryParameter("limit", "8")
            .build()
        val coverUrl = parseAppleArtworkUrl(
            rawJson = getOrEmpty(url.toString(), accept = "application/json"),
            title = title,
            author = author,
        )
        appleArtworkCache[cacheKey] = coverUrl ?: NO_APPLE_ARTWORK
        return coverUrl
    }

    internal fun parseAppleArtworkUrl(rawJson: String, title: String, author: String): String? {
        val root = runCatching { json.parseToJsonElement(rawJson) as? JsonObject }.getOrNull()
            ?: return null
        val results = root["results"] as? JsonArray ?: return null
        return results
            .mapNotNull { element ->
                val result = element as? JsonObject ?: return@mapNotNull null
                val artworkUrl = result.string("artworkUrl100")
                    ?: result.string("artworkUrl60")
                    ?: return@mapNotNull null
                val candidateTitle = result.string("trackName")
                    ?: result.string("collectionName")
                    ?: return@mapNotNull null
                val authorScore = author.artworkAuthorMatchScore(result.string("artistName").orEmpty())
                val titleScore = title.artworkTitleMatchScore(
                    candidate = candidateTitle,
                    allowLooseMatch = authorScore > 0 || author.isUnknownArtworkAuthor(),
                )
                if (titleScore <= 0) return@mapNotNull null
                AppleArtworkCandidate(
                    url = artworkUrl.fullSizeAppleArtworkUrl(),
                    score = titleScore * 10 + authorScore,
                )
            }
            .maxByOrNull { it.score }
            ?.url
            ?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    }

    private fun Document.parseAuthor(): String {
        val authorLink = selectFirst("#column_primary h3 a[href*=/authors/]")
            ?: selectFirst("h3 a[href*=/authors/]")
            ?: selectFirst("a[href*=/lit2go/authors/]")
        val explicit = authorLink?.text()?.trim().orEmpty()
        if (explicit.isNotBlank()) return explicit
        return select("meta[name=author]").attr("content").trim()
            .ifBlank { "Unknown author" }
    }

    private fun Document.parseDescription(): String {
        val ogDescription = select("meta[property=og:description]").attr("content").trim()
        if (ogDescription.isNotBlank()) return ogDescription
        val metaDescription = select("meta[name=description]").attr("content").trim()
        if (metaDescription.isNotBlank()) return metaDescription
        val primary = selectFirst("#column_primary") ?: return ""
        for (paragraph in primary.select("p")) {
            val text = paragraph.text().trim()
            if (text.isBlank()) continue
            if (text.startsWith("Source:", ignoreCase = true)) continue
            return text
        }
        return ""
    }

    private fun Document.parseGenres(): List<String> =
        select("a[href*=/lit2go/genres/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Document.parseThumbnailCoverUrl(id: String): String? {
        val img = selectFirst("#page_thumbnail img")?.attr("src")?.trim().orEmpty()
        if (img.isNotBlank()) return absoluteUrl(img)
        return "$BASE_URL/lit2go/static/thumbnails/books/$id.png"
    }

    private fun Document.parseFullCoverUrl(): String? {
        val og = select("meta[property=og:image]").attr("content").trim()
        return og.takeIf { it.isNotBlank() }?.let(::absoluteUrl)
    }

    private fun Document.parseChapterRefs(bookId: String, bookSlug: String): List<ChapterRef> {
        val pattern = Regex("""/lit2go/$bookId/$bookSlug/(\d+)/([a-z0-9-]+)/?$""")
        val seen = mutableSetOf<String>()
        val refs = mutableListOf<ChapterRef>()
        select("a[href]").forEach { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val match = pattern.find(href) ?: return@forEach
            val chapterNumericId = match.groupValues[1]
            val chapterSlug = match.groupValues[2]
            if (!seen.add(chapterNumericId)) return@forEach
            val title = anchor.text().trim().ifBlank { chapterSlug.titleized() }
            refs += ChapterRef(
                id = "$bookId-$chapterNumericId",
                numericId = chapterNumericId,
                slug = chapterSlug,
                title = title,
            )
        }
        return refs
    }

    private fun chapterAudioUrl(
        bookSlug: String,
        oneBasedIndex: Int,
        chapterSlug: String,
        chapterNumericId: String,
    ): String {
        val padded = oneBasedIndex.toString().padStart(3, '0')
        return "$BASE_URL/lit2go/audio/mp3/$bookSlug-$padded-$chapterSlug.$chapterNumericId.mp3"
    }

    private fun org.jsoup.nodes.Element.findFollowingAuthor(): String? {
        var sibling = nextElementSibling()
        while (sibling != null) {
            val authorAnchor = sibling.selectFirst("a[href*=/lit2go/authors/]")
            if (authorAnchor != null) return authorAnchor.text().trim().ifBlank { null }
            if (sibling.hasClass("book_icon")) break
            sibling = sibling.nextElementSibling()
        }
        val parent = parent()
        if (parent != null) {
            val anchor = parent.selectFirst("figcaption.author a, .author a")
            if (anchor != null) return anchor.text().trim().ifBlank { null }
        }
        return null
    }

    private fun BookStub.matches(query: String, field: CatalogSearchField): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return when (field) {
            CatalogSearchField.All -> title.lowercase(Locale.ROOT).contains(normalized) ||
                author?.lowercase(Locale.ROOT)?.contains(normalized) == true
            CatalogSearchField.Title -> title.lowercase(Locale.ROOT).contains(normalized)
            CatalogSearchField.Chapter -> false
            CatalogSearchField.Author -> author?.lowercase(Locale.ROOT)?.contains(normalized) == true
            CatalogSearchField.Reader,
            CatalogSearchField.Genre -> title.lowercase(Locale.ROOT).contains(normalized)
            CatalogSearchField.Epoch,
            CatalogSearchField.Kind -> false
        }
    }

    private fun absoluteUrl(value: String): String =
        when {
            value.startsWith("http") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$BASE_URL$value"
            else -> "$BASE_URL/$value"
        }

    private fun encodeId(numericId: String, slug: String): String =
        "$LIT2GO_ID_PREFIX$numericId$LIT2GO_SLUG_SEPARATOR$slug"

    private fun decodeId(rawId: String): Pair<String, String>? {
        if (!rawId.startsWith(LIT2GO_ID_PREFIX)) return null
        val withoutPrefix = rawId.removePrefix(LIT2GO_ID_PREFIX)
        val parts = withoutPrefix.split(LIT2GO_SLUG_SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val numericId = parts[0]
        val slug = parts[1]
        if (numericId.isBlank() || slug.isBlank()) return null
        return numericId to slug
    }

    private fun String.titleized(): String =
        split('-').joinToString(" ") { word ->
            word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString() }
        }

    private fun languageIsEnglishOrAny(language: String): Boolean =
        language.isBlank() || language.equals("English", ignoreCase = true)

    private fun appleArtworkSearchQuery(title: String, author: String): String =
        listOf(
            title.trim(),
            author.trim().takeUnless { it.equals("Unknown author", ignoreCase = true) }.orEmpty(),
        )
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun String.artworkTitleMatchScore(candidate: String, allowLooseMatch: Boolean): Int {
        val requested = normalizedForArtworkMatch()
        val actual = candidate.normalizedForArtworkMatch()
        if (requested.isBlank() || actual.isBlank()) return 0
        return when {
            actual == requested -> 4
            allowLooseMatch && actual.contains(requested) -> 3
            allowLooseMatch && requested.contains(actual) && actual.length >= 6 -> 2
            else -> 0
        }
    }

    private fun String.artworkAuthorMatchScore(candidate: String): Int {
        val requested = normalizedForArtworkMatch()
        val actual = candidate.normalizedForArtworkMatch()
        if (requested.isBlank() || requested == "unknown author" || actual.isBlank()) return 0
        val requestedParts = requested.split(" ")
        val actualParts = actual.split(" ")
        val requestedLastName = requestedParts.lastOrNull().orEmpty()
        return when {
            actual == requested -> 4
            actual.contains(requested) || requested.contains(actual) -> 3
            requestedLastName.length >= 3 && requestedLastName in actualParts -> 2
            requestedParts.any { it.length >= 4 && it in actualParts } -> 1
            else -> 0
        }
    }

    private fun String.normalizedForArtworkMatch(): String =
        lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")

    private fun String.isUnknownArtworkAuthor(): Boolean =
        normalizedForArtworkMatch().let { it.isBlank() || it == "unknown author" }

    private fun String.fullSizeAppleArtworkUrl(): String =
        replace(AppleArtworkSizeRegex) { match ->
            "/1000x1000${match.groupValues[1]}.${match.groupValues[2]}"
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)
            ?.content
            ?.takeIf { it.isNotBlank() && it != "null" }

    internal data class BookStub(
        val id: String,
        val slug: String,
        val title: String,
        val coverUrl: String? = null,
        val author: String? = null,
    )

    private data class ChapterRef(
        val id: String,
        val numericId: String,
        val slug: String,
        val title: String,
    )

    private data class AppleArtworkCandidate(
        val url: String,
        val score: Int,
    )

    internal companion object {
        const val BASE_URL = "https://etc.usf.edu"
        const val ID_PREFIX = LIT2GO_ID_PREFIX

        private const val ITUNES_SEARCH_URL = "https://itunes.apple.com/search"
        private const val NO_APPLE_ARTWORK = "__NO_APPLE_ARTWORK__"
        private val BrowseLetters = ('a'..'z').map { it.toString() }
        private const val FEATURED_CANDIDATE_LIMIT = 50
        private val BookDetailPathRegex = Regex("""/lit2go/(\d+)/([a-z0-9-]+)/?$""")
        private val AppleArtworkSizeRegex = Regex(
            pattern = """/\d+x\d+(bb|sr|cc)\.(jpg|jpeg|png|webp)(?=$|\?)""",
            option = RegexOption.IGNORE_CASE,
        )
        private val json = Json { ignoreUnknownKeys = true }

        private fun buildSharedClient(context: Context): OkHttpClient {
            val cacheDir = File(context.cacheDir, "lit2go-http").apply { mkdirs() }
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
