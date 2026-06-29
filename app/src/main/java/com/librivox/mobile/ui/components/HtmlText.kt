package com.librivox.mobile.ui.components

import androidx.core.text.HtmlCompat

/**
 * Strips HTML tags from catalog descriptions and collapses whitespace to a
 * single space.
 */
fun stripHtml(html: String): String {
    if (html.isBlank()) return ""
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()
}
