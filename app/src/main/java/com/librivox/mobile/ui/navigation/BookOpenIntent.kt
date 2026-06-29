package com.librivox.mobile.ui.navigation

import android.content.Intent
import android.net.Uri
import com.librivox.mobile.catalog.CatalogSearchField
import java.util.Locale

const val ACTION_OPEN_BOOK = "com.librivox.mobile.action.OPEN_BOOK"
const val EXTRA_OPEN_BOOK_QUERY = "query"
const val EXTRA_OPEN_BOOK_ID = "book_id"
const val EXTRA_OPEN_BOOK_CHAPTER_ID = "chapter_id"
const val EXTRA_OPEN_BOOK_DESTINATION = "destination"
const val EXTRA_OPEN_BOOK_FIELD = "field"
const val EXTRA_OPEN_BOOK_LANGUAGE = "language"
const val EXTRA_OPEN_BOOK_SOURCE_URL = "source_url"
const val EXTRA_OPEN_BOOK_TIMEOUT_MS = "timeout_ms"

private const val DefaultOpenBookTimeoutMillis = 90_000L
private const val MinOpenBookTimeoutMillis = 5_000L
private const val MaxOpenBookTimeoutMillis = 120_000L

enum class BookOpenIntentDestination {
    Detail,
    Reader,
}

data class BookOpenIntentRequest(
    val requestId: Long,
    val query: String? = null,
    val bookId: String? = null,
    val sourceUrl: String? = null,
    val chapterId: String? = null,
    val field: CatalogSearchField = CatalogSearchField.Title,
    val language: String = "",
    val destination: BookOpenIntentDestination = BookOpenIntentDestination.Detail,
    val timeoutMillis: Long = DefaultOpenBookTimeoutMillis,
) {
    val targetLabel: String
        get() = query ?: bookId ?: sourceUrl ?: "book"
}

fun Intent.toBookOpenIntentRequest(requestId: Long = System.nanoTime()): BookOpenIntentRequest? {
    val uri = data
    val isOpenBookAction = action == ACTION_OPEN_BOOK
    val isOpenBookUri = uri?.isOpenBookRequestUri() == true
    if (!isOpenBookAction && !isOpenBookUri) return null

    val query = openBookValue(uri, EXTRA_OPEN_BOOK_QUERY, "book_query", Intent.EXTRA_TEXT)
    val bookId = openBookValue(uri, EXTRA_OPEN_BOOK_ID, "bookId", "book")
    val chapterId = openBookValue(uri, EXTRA_OPEN_BOOK_CHAPTER_ID, "chapterId", "chapter")
    val sourceUrl = openBookValue(uri, EXTRA_OPEN_BOOK_SOURCE_URL, "url")
        ?: uri?.toString()?.takeIf { isOpenBookAction && !isOpenBookUri }
    if (query == null && bookId == null && sourceUrl == null) return null

    val field = openBookValue(uri, EXTRA_OPEN_BOOK_FIELD, "search_field")
        ?.toCatalogSearchField()
        ?: CatalogSearchField.Title
    val destination = openBookValue(uri, EXTRA_OPEN_BOOK_DESTINATION, "open")
        ?.toOpenBookDestination()
        ?: BookOpenIntentDestination.Detail
    val language = openBookValue(uri, EXTRA_OPEN_BOOK_LANGUAGE, "lang").orEmpty()
    val timeoutMillis = openBookValue(uri, EXTRA_OPEN_BOOK_TIMEOUT_MS, "timeout")
        ?.toLongOrNull()
        ?.coerceIn(MinOpenBookTimeoutMillis, MaxOpenBookTimeoutMillis)
        ?: DefaultOpenBookTimeoutMillis

    return BookOpenIntentRequest(
        requestId = requestId,
        query = query,
        bookId = bookId,
        sourceUrl = sourceUrl,
        chapterId = chapterId,
        field = field,
        language = language,
        destination = destination,
        timeoutMillis = timeoutMillis,
    )
}

private fun Uri.isOpenBookRequestUri(): Boolean {
    val scheme = scheme?.lowercase(Locale.ROOT)
    val host = host?.lowercase(Locale.ROOT)
    return scheme == "audiobook" &&
        host == "open" &&
        (
            getQueryParameter(EXTRA_OPEN_BOOK_QUERY) != null ||
                getQueryParameter("book_query") != null ||
                getQueryParameter(EXTRA_OPEN_BOOK_ID) != null ||
                getQueryParameter("bookId") != null ||
                getQueryParameter(EXTRA_OPEN_BOOK_SOURCE_URL) != null ||
                getQueryParameter("url") != null
            )
}

private fun Intent.openBookValue(uri: Uri?, vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        getStringExtra(key)?.cleanOpenBookValue()
            ?: uri?.getQueryParameter(key)?.cleanOpenBookValue()
    }

private fun String.cleanOpenBookValue(): String? =
    trim().takeIf { it.isNotBlank() }

private fun String.toCatalogSearchField(): CatalogSearchField? =
    CatalogSearchField.entries.firstOrNull { field ->
        field.name.equals(this, ignoreCase = true)
    }

private fun String.toOpenBookDestination(): BookOpenIntentDestination? =
    when (lowercase(Locale.ROOT).replace("-", "_")) {
        "reader", "read", "read_along", "readalong", "text" -> BookOpenIntentDestination.Reader
        "detail", "details", "book" -> BookOpenIntentDestination.Detail
        else -> null
    }
