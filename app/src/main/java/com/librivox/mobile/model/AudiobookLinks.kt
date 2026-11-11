package com.librivox.mobile.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ExternalAudiobookLinkTarget(
    val bookId: String? = null,
    val chapterId: String? = null,
    val chapterSlug: String? = null,
    val lookupUrl: String? = null,
    val searchQuery: String? = null,
)

enum class ShareLinkMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    SourceWebsite(
        preferenceValue = "source_website",
        label = "Source website",
        description = "Share the original audiobook source page.",
    ),
    AppLink(
        preferenceValue = "app_link",
        label = "Open in LibriVox",
        description = "Open the book in LibriVox, with a web page for people without the app.",
    );

    companion object {
        val Default: ShareLinkMode = AppLink

        fun fromPreference(value: String?): ShareLinkMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

const val AUDIOBOOK_APP_LINK_HOST = "ababilinski.github.io"
const val AUDIOBOOK_APP_LINK_PATH = "/librivox-mobile/open/"
const val AUDIOBOOK_APP_LINK_BASE_URL = "https://ababilinski.github.io/librivox-mobile"
private const val AUDIOBOOK_APP_SCHEME = "audiobook"
private const val INTENT_SCHEME = "intent"
private const val APP_LINK_PATH = "open"
private const val QUERY_BOOK_ID = "bookId"
private const val QUERY_CHAPTER_ID = "chapterId"
private const val QUERY_SOURCE_URL = "source"

fun AudioBook.publicWebUrl(): String? =
    when (source) {
        BookSource.LibriVox -> librivoxUrl ?: lit2goUrl ?: wolneLekturyUrl ?: gutenbergUrl
        BookSource.Lit2Go -> lit2goUrl ?: librivoxUrl ?: wolneLekturyUrl ?: gutenbergUrl
        BookSource.WolneLektury -> wolneLekturyUrl ?: librivoxUrl ?: lit2goUrl ?: gutenbergUrl
        BookSource.Gutendex -> gutenbergUrl ?: librivoxUrl ?: lit2goUrl ?: wolneLekturyUrl
        BookSource.LocalAsset,
        BookSource.CustomLocal -> librivoxUrl ?: lit2goUrl ?: wolneLekturyUrl ?: gutenbergUrl
    }?.takeIf { it.isNotBlank() }

fun AudioBook.allPublicWebUrls(): List<String> =
    listOfNotNull(librivoxUrl, lit2goUrl, wolneLekturyUrl, gutenbergUrl)
        .filter { it.isNotBlank() }

fun AudioBook.matchesPublicWebUrl(url: String): Boolean {
    val normalized = normalizedWebUrl(url) ?: return false
    return allPublicWebUrls().any { normalizedWebUrl(it) == normalized }
}

fun AudioBookChapter.publicWebUrl(book: AudioBook): String? {
    val bookUrl = book.publicWebUrl()
    if (book.source != BookSource.WolneLektury) return bookUrl
    val parentSlug = book.wolneLekturyUrl?.wolneLekturySlugFromUrl() ?: return bookUrl
    val childSlug = wolneLekturyChapterSlug()
    if (childSlug.isNullOrBlank() || childSlug == parentSlug) return bookUrl
    return "https://wolnelektury.pl/katalog/lektura/$childSlug/"
}

fun AudioBook.appLinkUrl(): String? =
    publicWebUrl()?.let { sourceUrl ->
        audiobookAppLinkUrl(
            bookId = id,
            chapterId = null,
            sourceUrl = sourceUrl,
        )
    }

fun AudioBookChapter.appLinkUrl(book: AudioBook): String? =
    publicWebUrl(book)?.let { sourceUrl ->
        audiobookAppLinkUrl(
            bookId = book.id,
            chapterId = id,
            sourceUrl = sourceUrl,
        )
    }

fun AudioBook.shareUrl(mode: ShareLinkMode = ShareLinkMode.Default): String? =
    when (mode) {
        ShareLinkMode.SourceWebsite -> publicWebUrl()
        ShareLinkMode.AppLink -> appLinkUrl() ?: publicWebUrl()
    }

fun AudioBookChapter.shareUrl(
    book: AudioBook,
    mode: ShareLinkMode = ShareLinkMode.Default,
): String? =
    when (mode) {
        ShareLinkMode.SourceWebsite -> publicWebUrl(book)
        ShareLinkMode.AppLink -> appLinkUrl(book) ?: publicWebUrl(book)
    }

fun AudioBook.shareSubject(): String = title

fun AudioBook.authorSearchQuery(): String =
    authorTags.firstOrNull()?.name?.takeIf { it.isNotBlank() }
        ?: author

fun AudioBook.shareText(mode: ShareLinkMode = ShareLinkMode.Default): String =
    buildString {
        append(title)
        author.takeIf { it.isNotBlank() }?.let { append(" by ").append(it) }
        shareUrl(mode)?.let { append('\n').append(it) }
    }

fun AudioBookChapter.shareSubject(book: AudioBook): String = "${book.title}: ${cleanTitle()}"

fun AudioBookChapter.shareText(
    book: AudioBook,
    mode: ShareLinkMode = ShareLinkMode.Default,
): String =
    buildString {
        append(numberedTitle())
        append('\n')
        append(book.title)
        book.author.takeIf { it.isNotBlank() }?.let { append(" by ").append(it) }
        shareUrl(book, mode)?.let { append('\n').append(it) }
    }

fun AudioBook.chapterIdForSourceSlug(slug: String?): String? {
    val normalizedSlug = slug?.trim('/')?.takeIf { it.isNotBlank() } ?: return null
    if (source != BookSource.WolneLektury) return null
    return chapters.firstOrNull { chapter ->
        chapter.wolneLekturyChapterSlug() == normalizedSlug
    }?.id
}

fun externalAudiobookLinkTarget(url: String): ExternalAudiobookLinkTarget? {
    if (url.length > 8_192) return null
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    val segments = uri.path
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()

    return when {
        scheme == AUDIOBOOK_APP_SCHEME && host == APP_LINK_PATH -> {
            appLinkTarget(uri, url)
        }
        scheme == INTENT_SCHEME && host == APP_LINK_PATH -> {
            appLinkTarget(uri, url)
        }
        scheme in setOf("https", "http") && host == AUDIOBOOK_APP_LINK_HOST &&
            uri.userInfo == null && uri.port == -1 &&
            uri.path.trimEnd('/') == AUDIOBOOK_APP_LINK_PATH.trimEnd('/') -> {
            appLinkTarget(uri, url)
        }
        scheme in setOf("https", "http") && host == "wolnelektury.pl" && segments.size == 3 &&
            segments[0] == "katalog" && segments[1] == "lektura" -> {
            val slug = segments[2].removeSuffix(".html")
            if (!slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) return null
            ExternalAudiobookLinkTarget(
                bookId = "wolnelektury-$slug",
                chapterSlug = slug,
            )
        }
        scheme in setOf("https", "http") && host == "etc.usf.edu" &&
            segments.size >= 3 && segments[0] == "lit2go" -> {
            val numericId = segments[1]
            val slug = segments[2]
            ExternalAudiobookLinkTarget(bookId = "lit2go-${numericId}__$slug")
        }
        scheme in setOf("https", "http") && host == "librivox.org" && segments.isNotEmpty() -> {
            val slug = segments.first()
            ExternalAudiobookLinkTarget(
                lookupUrl = normalizedWebUrl(url),
                searchQuery = slug
                    .replace('-', ' ')
                    .replace(Regex("""\bby\b.*$"""), "")
                    .trim()
                    .takeIf { it.isNotBlank() },
            )
        }
        else -> null
    }
}

internal fun normalizedWebUrl(url: String?): String? {
    val uri = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "https"
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    val path = uri.path.orEmpty().trimEnd('/').ifBlank { "/" }
    return "$scheme://$host$path"
}

private fun audiobookAppLinkUrl(
    bookId: String,
    chapterId: String?,
    sourceUrl: String,
): String =
    buildString {
        append(AUDIOBOOK_APP_LINK_BASE_URL)
        append("/open/")
        append('?')
        append(QUERY_BOOK_ID)
        append('=')
        append(bookId.urlEncoded())
        chapterId?.takeIf { it.isNotBlank() }?.let {
            append('&')
            append(QUERY_CHAPTER_ID)
            append('=')
            append(it.urlEncoded())
        }
        append('&')
        append(QUERY_SOURCE_URL)
        append('=')
        append(sourceUrl.urlEncoded())
    }

private fun appLinkTarget(uri: URI, originalUrl: String): ExternalAudiobookLinkTarget? {
    val bookId = uri.queryParameter(QUERY_BOOK_ID)
    val chapterId = uri.queryParameter(QUERY_CHAPTER_ID)
    val sourceUrl = uri.queryParameter(QUERY_SOURCE_URL)
    val sourceTarget = sourceUrl
        ?.takeIf { it.isNotBlank() && it != originalUrl }
        // Only publisher pages can be a fallback. Never recursively unwrap app links.
        ?.takeIf {
            val source = runCatching { URI(it) }.getOrNull()
            source?.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                source?.host?.lowercase(Locale.ROOT)?.removePrefix("www.") in
                setOf("wolnelektury.pl", "librivox.org", "etc.usf.edu")
        }
        ?.let { externalAudiobookLinkTarget(it) }

    return ExternalAudiobookLinkTarget(
        bookId = bookId ?: sourceTarget?.bookId,
        chapterId = chapterId,
        chapterSlug = sourceTarget?.chapterSlug,
        lookupUrl = sourceTarget?.lookupUrl,
        searchQuery = sourceTarget?.searchQuery,
    ).takeIf {
        it.bookId != null ||
            it.chapterId != null ||
            it.chapterSlug != null ||
            it.lookupUrl != null ||
            it.searchQuery != null
    }
}

private fun URI.queryParameter(name: String): String? =
    rawQuery
        ?.split('&')
        ?.asSequence()
        ?.map { parameter ->
            parameter.substringBefore('=') to parameter.substringAfter('=', missingDelimiterValue = "")
        }
        ?.firstOrNull { (key, _) -> key.urlDecoded() == name }
        ?.second
        ?.urlDecoded()
        ?.takeIf { it.isNotBlank() }

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.urlDecoded(): String? =
    runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrNull()

private fun String.wolneLekturySlugFromUrl(): String? =
    substringAfter("/katalog/lektura/", missingDelimiterValue = "")
        .trim('/')
        .takeIf { it.isNotBlank() }

private fun AudioBookChapter.wolneLekturyChapterSlug(): String? {
    val suffix = "-$number"
    return when {
        number > 0 && id.endsWith(suffix) -> id.removeSuffix(suffix)
        else -> id.replace(Regex("""-\d+$"""), "")
    }.takeIf { it.isNotBlank() }
}
