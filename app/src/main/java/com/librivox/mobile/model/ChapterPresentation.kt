package com.librivox.mobile.model

/** Display-only chapter identity. Stored catalog titles and playback identifiers stay untouched. */
data class ChapterPresentation(
    val title: String,
    val context: String?,
    val author: String?,
    val reader: String?,
)

private val wolneVolumeChapterHeading = Regex(
    "^(Tom [IVXLCDM]+, Rozdział [IVXLCDM]+) — (.+)$",
)

fun AudioBook.chapterPresentation(chapter: AudioBookChapter): ChapterPresentation {
    val rawTitle = chapter.title.trim()
    val heading = if (source == BookSource.WolneLektury) {
        wolneVolumeChapterHeading.matchEntire(rawTitle)
    } else null
    return ChapterPresentation(
        title = heading?.groupValues?.get(2) ?: rawTitle.ifBlank { "Chapter ${chapter.number}" },
        context = heading?.groupValues?.get(1),
        author = author.trim().takeIf(String::isNotEmpty)
            ?: authorTags.map { it.name.trim() }.filter(String::isNotEmpty).distinct()
                .joinToString(", ").takeIf(String::isNotEmpty),
        reader = chapter.reader?.trim()?.takeIf(String::isNotEmpty)
            ?: narrators.map(String::trim).filter(String::isNotEmpty).distinct().singleOrNull(),
    )
}
