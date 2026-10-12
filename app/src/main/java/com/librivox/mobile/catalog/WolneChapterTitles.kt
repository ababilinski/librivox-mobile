package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import java.text.Normalizer
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

private val recordedChapter = Regex("(?i)^Rozdział\\s+([IVXLCDM]+|[0-9]+)$")
private val volumeLabel = Regex("(?i)\\btom\\s+([a-z0-9]+)\\b")
private val ordinalWords = listOf(
    "pierwszy", "drugi", "trzeci", "czwarty", "piaty", "szosty", "siodmy", "osmy", "dziewiaty", "dziesiaty",
)

internal fun AudioBook.needsWolneChapterNames(): Boolean =
    source == BookSource.WolneLektury && !writtenChapterTitlesChecked &&
        chapters.any { recordedChapter.matches(it.title.substringAfterLast(',').trim()) }

/** The API's child list is not reading order. Only reorder explicitly numbered volumes. */
internal fun <T> List<T>.orderedByWolneVolume(title: (T) -> String?): List<T> {
    val numbered = map { item ->
        val volume = title(item)?.substringBefore(" — ")?.let(::volumeNumber) ?: return this
        item to volume
    }
    if (numbered.map { it.second }.distinct().size < 2) return this
    // Stable sorting retains the recording order within each volume, including supplements.
    return numbered.sortedBy { it.second }.map { it.first }
}

/** Also repairs saved books offline; chapter IDs, files and ID-keyed progress stay intact. */
internal fun AudioBook.withWolneVolumeOrder(): AudioBook {
    if (source != BookSource.WolneLektury) return this
    val ordered = chapters.orderedByWolneVolume { it.title }
    if (ordered == chapters) return this
    return copy(chapters = ordered.mapIndexed { index, chapter -> chapter.copy(number = index + 1) })
}

/** Written headings are matched by volume and ordinal, never by recording/list order. */
internal fun AudioBook.withWrittenChapterNames(xmlDocuments: List<String>): AudioBook {
    val headings = mutableMapOf<Pair<Int?, Int>, String>()
    xmlDocuments.forEach { xml ->
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        var volume: Int? = null
        document.select("naglowek_czesc, naglowek_rozdzial").forEach { element ->
            val heading = element.clone().also { it.select("pa").remove() }.text().trim()
            if (element.tagName() == "naglowek_czesc") {
                volume = volumeNumber(heading)
            } else {
                val ordinal = ordinalNumber(heading.substringBefore('.').trim())
                val name = heading.substringAfter('.', "").trim()
                if (ordinal != null && name.isNotBlank()) {
                    val key = volume to ordinal
                    val previous = headings[key]
                    headings[key] = if (previous == null || previous == name) name else ""
                }
            }
        }
    }
    return copy(chapters = chapters.map { chapter ->
        val title = cleanWolneRecordingTitle(chapter.title, this)
        val ordinal = recordedChapter.matchEntire(title.substringAfterLast(',').trim())
            ?.groupValues?.get(1)?.let(::ordinalNumber)
        val heading = ordinal?.let { headings[volumeNumber(title) to it] }?.takeIf { it.isNotBlank() }
        chapter.copy(title = if (heading != null) "$title — $heading" else title)
    })
}

private fun cleanWolneRecordingTitle(raw: String, book: AudioBook): String {
    var title = raw.replace(Regex("^\\s*\\d+[.:]\\s*"), "").trim()
    val prefixes = (book.author.split(',') + book.author + book.title + book.title.substringBefore(','))
        .filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    var removed = false
    while (true) {
        val prefix = prefixes.firstOrNull { normalized(title.take(it.length + 1)) == normalized("$it,") }
            ?: break
        title = title.drop(prefix.length + 1).trim()
        removed = true
    }
    if (removed) {
        title = title.replace(
            Regex("(?i)^tom\\s+(?:pierwszy|drugi|trzeci|czwarty|piąty|szósty|siódmy|ósmy|dziewiąty|dziesiąty)\\s*,\\s*"),
            "",
        )
    }
    return title.ifBlank { raw }
}

private fun normalized(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('ł', 'l')

private fun volumeNumber(title: String): Int? {
    val value = volumeLabel.find(normalized(title))?.groupValues?.get(1) ?: return null
    val index = ordinalWords.indexOf(value)
    return if (index >= 0) index + 1 else ordinalNumber(value)
}

private fun ordinalNumber(value: String): Int? {
    value.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    val roman = value.uppercase(Locale.ROOT)
    if (roman.isEmpty() || roman.any { it !in values }) return null
    return roman.indices.sumOf { index ->
        val current = values.getValue(roman[index])
        if (current < (roman.getOrNull(index + 1)?.let(values::getValue) ?: 0)) -current else current
    }
}
