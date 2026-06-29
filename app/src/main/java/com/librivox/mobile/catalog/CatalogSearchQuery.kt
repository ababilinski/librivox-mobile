package com.librivox.mobile.catalog

import java.net.URI
import java.text.Normalizer
import java.util.Locale

private val MarkdownLinkPattern = Regex("""\[([^]]+)]\(([^)]+)\)""")
private val UrlPattern = Regex("""https?://\S+""")
private val CombiningMarks = Regex("\\p{Mn}+")
private val NonSearchCharacters = Regex("[^\\p{L}\\p{Nd}]+")
private val Whitespace = Regex("\\s+")
private val LeadingEnglishArticle = Regex("""(?i)^(the|a|an)\s+""")
private val VolumeNumberPattern = Regex("""(?i)\bvol\.?\s*0*(\d{1,3})\b""")
private val LibriVoxPersonPattern = Regex("""https?://(?:www\.)?librivox\.org/(author|reader)/(\d+)""")

internal data class LibriVoxPersonPointer(
    val category: String,
    val id: String,
)

fun sanitizeCatalogSearchQuery(raw: String): String {
    if (raw.isBlank()) return ""
    val withMarkdownLabels = MarkdownLinkPattern.replace(raw.trim()) { match ->
        val label = match.groupValues[1]
        val url = match.groupValues[2]
        val urlText = readableCatalogTextFromUrl(url)
            ?.takeUnless { librivoxPersonPointers(url).isNotEmpty() }
        listOf(label, urlText)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { label }
    }
    val withReadableUrls = UrlPattern.replace(withMarkdownLabels) { match ->
        readableCatalogTextFromUrl(match.value).orEmpty()
    }
    return withReadableUrls
        .replace(Whitespace, " ")
        .trim()
}

internal fun catalogSearchQueryVariants(raw: String): List<String> {
    val candidates = mutableListOf<String>()
    candidates += sanitizeCatalogSearchQuery(raw)
    MarkdownLinkPattern.findAll(raw).forEach { match ->
        candidates += match.groupValues[1]
        candidates += readableCatalogTextFromUrl(match.groupValues[2]).orEmpty()
    }
    UrlPattern.findAll(raw).forEach { match ->
        candidates += readableCatalogTextFromUrl(match.value).orEmpty()
    }

    return candidates
        .flatMap { candidate -> catalogTitleVariants(candidate) }
        .map { it.replace(Whitespace, " ").trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun normalizedCatalogSearchText(value: String): String {
    if (value.isBlank()) return ""
    return Normalizer.normalize(sanitizeCatalogSearchQuery(value), Normalizer.Form.NFD)
        .replace("ł", "l")
        .replace("Ł", "L")
        .replace(CombiningMarks, "")
        .lowercase(Locale.ROOT)
        .replace(NonSearchCharacters, " ")
        .trim()
}

internal fun librivoxPersonPointers(raw: String): List<LibriVoxPersonPointer> =
    LibriVoxPersonPattern.findAll(raw)
        .map { match ->
            LibriVoxPersonPointer(
                category = match.groupValues[1],
                id = match.groupValues[2],
            )
        }
        .distinct()
        .toList()

private fun catalogTitleVariants(candidate: String): List<String> {
    val trimmed = candidate.trim()
    if (trimmed.isBlank()) return emptyList()
    val variants = mutableListOf<String>()
    val articleStripped = trimmed.replace(LeadingEnglishArticle, "").trim()
    if (articleStripped.isNotBlank() && articleStripped != trimmed) {
        variants += articleStripped
    }
    variants += trimmed
    val volumeVariant = VolumeNumberPattern.replace(trimmed) { match ->
        "Vol. ${match.groupValues[1].padStart(3, '0')}"
    }
    variants += volumeVariant
    variants += trimmed.replace(Regex("""[._-]+"""), " ")
    return variants.distinct()
}

private fun readableCatalogTextFromUrl(rawUrl: String): String? {
    val cleaned = rawUrl
        .trim()
        .trimEnd('.', ',', ';', ':', '!', '?', ']', '}')
    val uri = runCatching { URI(cleaned) }.getOrNull() ?: return null
    val host = uri.host.orEmpty().lowercase(Locale.ROOT)
    val segments = uri.path
        .orEmpty()
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty()) return null

    if ("archive.org" in host) {
        val identifier = archiveIdentifierFromSegments(segments)
        collectionTitleFromArchiveIdentifier(identifier)?.let { return it }
        return identifier?.replace(Regex("""[_-]+"""), " ")
    }

    if ("librivox.org" in host) {
        val first = segments.firstOrNull().orEmpty()
        if (first == "author" || first == "reader") {
            return listOf(first, segments.getOrNull(1).orEmpty())
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
        }
        return first
            .removeSuffix(".html")
            .replace(Regex("""[_-]+"""), " ")
            .replace(VolumeNumberPattern) { match ->
                "Vol. ${match.groupValues[1].padStart(3, '0')}"
            }
    }

    return null
}

private fun archiveIdentifierFromSegments(segments: List<String>): String? {
    val downloadIndex = segments.indexOf("download")
    if (downloadIndex >= 0) {
        return segments.getOrNull(downloadIndex + 1)
    }
    val detailsIndex = segments.indexOf("details")
    if (detailsIndex >= 0) {
        return segments.getOrNull(detailsIndex + 1)
    }
    return null
}

private fun collectionTitleFromArchiveIdentifier(identifier: String?): String? {
    if (identifier.isNullOrBlank()) return null
    Regex("""(?i)(?:^|_)short_story_(\d{1,3})(?:_|$)""").find(identifier)?.let { match ->
        return "Short Story Collection Vol. ${match.groupValues[1].padStart(3, '0')}"
    }
    Regex("""(?i)(?:^|_)ssc_?(\d{1,3})(?:_|$)""").find(identifier)?.let { match ->
        return "Short Story Collection Vol. ${match.groupValues[1].padStart(3, '0')}"
    }
    Regex("""(?i)(?:^|_)short_?poetry_(\d{1,3})(?:_|$)""").find(identifier)?.let { match ->
        return "Short Poetry Collection ${match.groupValues[1].padStart(3, '0')}"
    }
    return null
}
