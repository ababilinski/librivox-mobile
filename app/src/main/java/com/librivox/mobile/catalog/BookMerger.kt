package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import java.text.Normalizer
import java.util.Locale

object BookMerger {

    fun matchKey(book: AudioBook): String =
        "${normalize(primaryTitle(book.title))}|${normalize(firstSurname(book.author))}"

    fun merge(librivox: List<AudioBook>, lit2go: List<AudioBook>): List<AudioBook> =
        mergeByPriority(lit2go, librivox)

    fun merge(
        librivox: List<AudioBook>,
        lit2go: List<AudioBook>,
        wolneLektury: List<AudioBook>,
    ): List<AudioBook> =
        merge(librivox, lit2go, wolneLektury, gutendex = emptyList())

    fun merge(
        librivox: List<AudioBook>,
        lit2go: List<AudioBook>,
        wolneLektury: List<AudioBook>,
        gutendex: List<AudioBook>,
    ): List<AudioBook> =
        mergeByPriority(lit2go, librivox, wolneLektury, gutendex)

    private fun mergeByPriority(vararg sourceBooks: List<AudioBook>): List<AudioBook> {
        val mergedByKey = linkedMapOf<String, AudioBook>()
        sourceBooks.forEach { books ->
            books.forEach { book ->
                val key = matchKey(book)
                val primary = mergedByKey[key]
                mergedByKey[key] = if (primary == null) {
                    book
                } else {
                    fillMissingFrom(primary, book)
                }
            }
        }
        return mergedByKey.values.toList()
    }

    private fun fillMissingFrom(primary: AudioBook, secondary: AudioBook): AudioBook =
        primary.copy(
            description = primary.description.ifBlank { secondary.description },
            coverImageUrl = primary.coverImageUrl ?: secondary.coverImageUrl,
            fullCoverImageUrl = primary.fullCoverImageUrl ?: secondary.fullCoverImageUrl,
            localCoverFileName = primary.localCoverFileName ?: secondary.localCoverFileName,
            localCoverSourceUrl = primary.localCoverSourceUrl ?: secondary.localCoverSourceUrl,
            language = primary.language ?: secondary.language,
            genres = (primary.genres + secondary.genres).distinct(),
            authorTags = primary.authorTags.ifEmpty { secondary.authorTags },
            literaryEpochs = primary.literaryEpochs.ifEmpty { secondary.literaryEpochs },
            literaryKinds = primary.literaryKinds.ifEmpty { secondary.literaryKinds },
            literaryGenres = primary.literaryGenres.ifEmpty { secondary.literaryGenres },
            totalDurationSeconds = if (primary.totalDurationSeconds > 0) {
                primary.totalDurationSeconds
            } else {
                secondary.totalDurationSeconds
            },
            chapters = primary.chapters.ifEmpty { secondary.chapters },
            librivoxUrl = primary.librivoxUrl ?: secondary.librivoxUrl,
            lit2goUrl = primary.lit2goUrl ?: secondary.lit2goUrl,
            wolneLekturyUrl = primary.wolneLekturyUrl ?: secondary.wolneLekturyUrl,
            gutenbergUrl = primary.gutenbergUrl ?: secondary.gutenbergUrl,
        )

    private fun firstSurname(author: String): String {
        val firstAuthor = author.split(',').firstOrNull()?.trim().orEmpty()
        val parts = firstAuthor.split(Regex("\\s+")).filter { it.isNotBlank() }
        return parts.lastOrNull().orEmpty()
    }

    private fun primaryTitle(title: String): String =
        title.substringBefore(';').substringBefore(':')

    private fun normalize(value: String): String {
        if (value.isBlank()) return ""
        val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val lowered = stripped.lowercase(Locale.ROOT)
        val withoutLeadingThe = if (lowered.startsWith("the ")) lowered.substring(4) else lowered
        return withoutLeadingThe
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
