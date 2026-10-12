package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WolneChapterTitlesTest {
    @Test fun detailHydrationLoadsWrittenNamesFromEachChildVolume() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            synchronized(requestedPaths) { requestedPaths.add(path) }
            val response = when (path) {
                "/api/books/lalka/" -> """{"title":"Lalka","authors":[{"name":"Bolesław Prus"}],"children":[{"slug":"lalka-tom-drugi","has_audio":true},{"slug":"lalka-tom-pierwszy","has_audio":true}]}"""
                "/api/books/lalka-tom-drugi/" -> volumeJson("drugi", "II")
                "/api/books/lalka-tom-pierwszy/" -> volumeJson("pierwszy", "I")
                "/media/II.xml" -> "<utwor><naglowek_czesc>Tom II</naglowek_czesc><naglowek_rozdzial>I. Pamiętnik starego subiekta</naglowek_rozdzial></utwor>"
                "/media/I.xml" -> "<utwor><naglowek_czesc>Tom I</naglowek_czesc><naglowek_rozdzial>I. Pierwszy rozdział</naglowek_rozdzial></utwor>"
                else -> "<html/>"
            }
            Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(response.toResponseBody()).build()
        }.build()
        val detail = WolneLekturyCatalogSource(client).fetchByIds("wolnelektury-lalka").single()
        assertEquals("Tom I, Rozdział I — Pierwszy rozdział", detail.chapters[0].title)
        assertEquals("lalka-tom-pierwszy-1", detail.chapters[0].id)
        assertEquals("Tom II, Rozdział I — Pamiętnik starego subiekta", detail.chapters[2].title)
        assertTrue(detail.writtenChapterTitlesChecked)
        assertTrue(requestedPaths.containsAll(listOf("/media/II.xml", "/media/I.xml")))
    }

    private fun volumeJson(word: String, roman: String) = """
        {"title":"Lalka, tom $word","url":"https://wolnelektury.pl/katalog/lektura/lalka-tom-$word/","xml":"https://wolnelektury.pl/media/$roman.xml",
        "media":[
        {"type":"mp3","name":"01. Bolesław Prus, Lalka, Lalka, tom $word, Tom $roman, Rozdział I","url":"https://example.com/$roman-1.mp3"},
        {"type":"mp3","name":"02. Bolesław Prus, Lalka, Lalka, tom $word, Tom $roman, Rozdział II","url":"https://example.com/$roman-2.mp3"}]}
    """.trimIndent()

    private fun book(vararg titles: String) = AudioBook(
        id = "wolnelektury-lalka", title = "Lalka", author = "Bolesław Prus",
        source = BookSource.WolneLektury,
        chapters = titles.mapIndexed { index, title ->
            AudioBookChapter(id = "recording-$index", title = title, number = index + 1,
                listenUrl = "https://example.com/recording-$index.mp3", durationSeconds = 100)
        },
    )

    @Test fun matchesVolumeAndOrdinalRatherThanListPosition() {
        val original = book("Tom II, Rozdział I", "Tom I, Rozdział I", "Tom II, Rozdział II")
        val updated = original.withWrittenChapterNames(listOf("""
            <utwor><naglowek_czesc>Tom I</naglowek_czesc>
            <naglowek_rozdzial>I. Jak wygląda firma J. Mincel i S. Wokulski przez szkło butelek?</naglowek_rozdzial>
            <naglowek_czesc>Tom II</naglowek_czesc>
            <naglowek_rozdzial>I. Pamiętnik starego subiekta</naglowek_rozdzial>
            <naglowek_rozdzial>II. Szare dnie i krwawe godziny</naglowek_rozdzial></utwor>
        """.trimIndent()))
        assertEquals("Tom II, Rozdział I — Pamiętnik starego subiekta", updated.chapters[0].title)
        assertTrue(updated.chapters[1].title.endsWith("przez szkło butelek?"))
        assertEquals("Tom II, Rozdział II — Szare dnie i krwawe godziny", updated.chapters[2].title)
        assertEquals(original.chapters, updated.chapters.mapIndexed { i, c -> c.copy(title = original.chapters[i].title) })
    }

    @Test fun combinesSeparateVolumeDocumentsAndCleansRecordingMetadata() {
        val original = book("Lalka, Lalka, tom drugi, Tom II, Rozdział I", "Tom I, Rozdział I")
        val updated = original.withWrittenChapterNames(listOf(
            "<utwor><naglowek_czesc>Tom II</naglowek_czesc><naglowek_rozdzial>I. Pamiętnik starego subiekta</naglowek_rozdzial></utwor>",
            "<utwor><naglowek_czesc>Tom I</naglowek_czesc><naglowek_rozdzial>I. Pierwszy rozdział</naglowek_rozdzial></utwor>",
        ))
        assertEquals("Tom II, Rozdział I — Pamiętnik starego subiekta", updated.chapters[0].title)
        assertEquals("Tom I, Rozdział I — Pierwszy rozdział", updated.chapters[1].title)
    }

    @Test fun preservesLabelsWhenHeadingIsMissingOrAmbiguous() {
        val original = book("Tom II, Rozdział I", "Tom II, Rozdział II")
        val updated = original.withWrittenChapterNames(listOf("""
            <utwor><naglowek_czesc>Tom II</naglowek_czesc>
            <naglowek_rozdzial>I. First heading</naglowek_rozdzial>
            <naglowek_rozdzial>I. Conflicting heading</naglowek_rozdzial></utwor>
        """.trimIndent()))
        assertEquals(original, updated)
        assertEquals(original, original.withWrittenChapterNames(listOf("<utwor/>")))
    }

    @Test fun excludesFootnotesAndDoesNotAppendNamesTwice() {
        val original = book("Tom II, Rozdział I")
        val xml = "<utwor><naglowek_czesc>Tom II</naglowek_czesc><naglowek_rozdzial>I. Pamiętnik <wyroznienie>starego</wyroznienie> subiekta<pa>Footnote</pa></naglowek_rozdzial></utwor>"
        val updated = original.withWrittenChapterNames(listOf(xml))
        assertEquals("Tom II, Rozdział I — Pamiętnik starego subiekta", updated.chapters.single().title)
        assertEquals(updated, updated.withWrittenChapterNames(listOf(xml)))
    }

    @Test fun oldSavedLabelsNeedHydrationButCheckedAndOtherCatalogBooksDoNot() {
        val original = book("Tom II, Rozdział I")
        assertTrue(original.needsWolneChapterNames())
        assertFalse(original.copy(writtenChapterTitlesChecked = true).needsWolneChapterNames())
        assertFalse(original.copy(source = BookSource.LibriVox).needsWolneChapterNames())
        assertFalse(book("Tom II, Rozdział I — Pamiętnik starego subiekta").needsWolneChapterNames())
    }
}
