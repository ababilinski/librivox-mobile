package com.librivox.mobile.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DISCOVER = "discover"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val SETTINGS_CATALOG = "$SETTINGS/catalog"

    const val BOOK_DETAIL_PREFIX = "book"
    const val BOOK_DETAIL_ARG = "bookId"
    const val BOOK_DETAIL_CHAPTER_ARG = "chapterId"
    const val BOOK_DETAIL = "$BOOK_DETAIL_PREFIX/{$BOOK_DETAIL_ARG}?$BOOK_DETAIL_CHAPTER_ARG={$BOOK_DETAIL_CHAPTER_ARG}"
    const val BOOK_READER_PREFIX = "reader"
    const val BOOK_READER = "$BOOK_READER_PREFIX/{$BOOK_DETAIL_ARG}?$BOOK_DETAIL_CHAPTER_ARG={$BOOK_DETAIL_CHAPTER_ARG}"

    fun bookDetail(bookId: String, chapterId: String? = null): String =
        buildString {
            append("$BOOK_DETAIL_PREFIX/$bookId")
            if (!chapterId.isNullOrBlank()) {
                append("?$BOOK_DETAIL_CHAPTER_ARG=$chapterId")
            }
        }

    fun bookReader(bookId: String, chapterId: String? = null): String =
        buildString {
            append("$BOOK_READER_PREFIX/$bookId")
            if (!chapterId.isNullOrBlank()) {
                append("?$BOOK_DETAIL_CHAPTER_ARG=$chapterId")
            }
        }
}

/**
 * Bottom-nav tabs. Downloads is intentionally NOT here — it's reachable from
 * the Library top-app-bar action so it doesn't crowd the primary navigation.
 */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(Routes.HOME, "Home", Icons.Filled.Home),
    Library(Routes.LIBRARY, "Library", Icons.AutoMirrored.Filled.LibraryBooks),
    Discover(Routes.DISCOVER, "Discover", Icons.Filled.Explore),
    Settings(Routes.SETTINGS, "Settings", Icons.Filled.Settings);

    companion object {
        fun fromRoute(route: String?): TopDestination? =
            entries.firstOrNull { destination ->
                route == destination.route || route?.startsWith("${destination.route}/") == true
            }
    }
}
