package com.librivox.mobile.readalong

import com.librivox.mobile.catalog.WolneLekturyCatalogSource
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.AudioBookReadAlongSource
import com.librivox.mobile.model.BookSource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadAlongCollectionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val parser = WolneAudioEpubParser()

    @Test fun realLalkaChapterSixFallsBackFromMalformedAudioEpubToCorrectDaisyText() {
        val catalogBook = WolneLekturyCatalogSource().parseBookDetail(
            slug = "lalka",
            rawJson = fixtureText("lalka.json"),
            childDetailJsons = listOf(fixtureText("lalka-tom-pierwszy.json")),
        )
        val book = catalogBook.copy(
            // This test isolates the source timing fallback; ordinary EPUB only adds notes/style.
            epubUrl = null,
            readAlongSources = catalogBook.readAlongSources.map { it.copy(epubUrl = null) },
        )
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = temporaryFolder.newFolder(),
            filesDir = temporaryFolder.newFolder(),
            downloader = { url, target ->
                downloads += url
                val fixture = when {
                    url.endsWith(".audio.epub") -> "lalka-malformed-audio.epub"
                    url.endsWith(".daisy.zip") -> "lalka-valid-daisy.zip"
                    else -> error("Unexpected archive request: $url")
                }
                checkNotNull(javaClass.getResourceAsStream("/wolnelektury/$fixture"))
                    .use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            },
        )

        val partial = loadReadAlongBook(book, parser, resolver, "lalka-tom-pierwszy-6", true)
        val chapter = partial.chapters.single()
        assertTrue(partial.hasTimedSync)
        assertEquals("lalka-tom-pierwszy-6", chapter.chapterId)
        assertEquals(6, chapter.displayIndex)
        assertTrue(chapter.fullText.replace('\u00a0', ' ').startsWith("VI. W jaki sposób nowi ludzie"))
        assertTrue(chapter.activeSegment(15_000L)!!.text.startsWith("Początek kwietnia"))
        assertEquals(10_360L, chapter.activeSegment(15_000L)!!.clipBeginMs)
        assertTrue(chapter.segments.all { it.textRef.contains("/book5.html#") })
        assertEquals(2, downloads.size)
        assertTrue(downloads[0].endsWith(".audio.epub"))
        assertTrue(downloads[1].endsWith(".daisy.zip"))

        val full = loadReadAlongBook(book, parser, resolver)
        assertEquals(book.chapters.map { it.id }, full.chapters.map { it.chapterId })
        assertEquals(chapter.fullText, full.chapter(chapter.chapterId)!!.fullText)
        assertEquals(2, downloads.size)
    }

    private fun fixtureText(name: String): String =
        checkNotNull(javaClass.getResource("/wolnelektury/$name")).readText()

    @Test fun preferredChapterSixLoadsOnlyItsVolumeAndKeepsSourceTiming() {
        val downloads = mutableListOf<String>()
        val readAlong = loadReadAlongBook(
            book = collection(),
            parser = parser,
            resolver = resolver(downloads),
            preferredChapterId = "volume-1-6",
            onlyPreferredChapter = true,
        )

        assertEquals(listOf(VolumeOneUrl), downloads)
        assertTrue(readAlong.hasTimedSync)
        assertTrue(readAlong.isPartial)
        assertEquals("parent", readAlong.bookId)
        assertEquals("volume-1-6", readAlong.chapters.single().chapterId)
        assertEquals(6, readAlong.chapters.single().displayIndex)
        assertEquals("Volume 1 paragraph 6.", readAlong.chapters.single().fullText)
        assertEquals(10_000L, readAlong.chapters.single().segments.single().clipBeginMs)
        assertEquals("Volume 1 paragraph 6.", readAlong.chapter("volume-1-6")?.activeSegment(15_000L)?.text)
        assertNull(readAlong.chapter("volume-2-1"))
    }

    @Test fun fullCollectionReusesPreferredVolumeAndKeepsOtherVolumePathsDistinct() {
        val downloads = mutableListOf<String>()
        val resolver = resolver(downloads)
        loadReadAlongBook(collection(), parser, resolver, "volume-1-6", onlyPreferredChapter = true)
        val full = loadReadAlongBook(collection(), parser, resolver)

        assertEquals(listOf(VolumeOneUrl, VolumeTwoUrl), downloads)
        assertEquals(collection().chapters.map { it.id }, full.chapters.map { it.chapterId })
        assertEquals((1..8).toList(), full.chapters.map { it.displayIndex })
        assertTrue(full.hasTimedSync)
        assertEquals(false, full.isPartial)
        val first = full.chapter("volume-1-1")!!.segments.single()
        val second = full.chapter("volume-2-1")!!.segments.single()
        assertNotEquals(first.textRef, second.textRef)
        assertNotEquals(first.id, second.id)
        assertEquals("Volume 2 paragraph 1.", full.chapter("volume-2-1")?.activeSegment(15_000L)?.text)
        assertEquals(full.tableOfContents.size, full.tableOfContents.map { it.id }.distinct().size)
        assertNull(full.chapter("missing-chapter"))
    }

    @Test fun preferredSecondVolumeCannotBeMappedOntoFirstVolumeChapterNumbers() {
        val downloads = mutableListOf<String>()
        val result = loadReadAlongBook(collection(), parser, resolver(downloads), "volume-2-1", true)

        assertEquals(listOf(VolumeTwoUrl), downloads)
        assertEquals("volume-2-1", result.chapters.single().chapterId)
        assertEquals(7, result.chapters.single().displayIndex)
        assertEquals("Volume 2 paragraph 1.", result.chapters.single().fullText)
        assertNull(result.chapter("volume-1-1"))
    }

    @Test fun volumeMetadataSurvivesSavedLibrarySerialization() {
        val original = collection()
        val saved = Json.encodeToString(original)
        val reopened = Json.decodeFromString<AudioBook>(saved)

        assertEquals(original.readAlongSources, reopened.readAlongSources)
        assertEquals(original.chapters, reopened.chapters)
    }

    private fun resolver(downloads: MutableList<String>) = ReadAlongAssetResolver(
        cacheDir = temporaryFolder.newFolder(),
        filesDir = temporaryFolder.newFolder(),
        downloader = { url, target ->
            downloads += url
            when (url) {
                VolumeOneUrl -> writeTimedVolume(target, 1, 6)
                VolumeTwoUrl -> writeTimedVolume(target, 2, 2)
                else -> error("Unexpected archive request: $url")
            }
        },
    )

    private fun collection(): AudioBook {
        val first = (1..6).map { chapter(1, it, it) }
        val second = (1..2).map { chapter(2, it, it + 6) }
        return AudioBook(
            id = "parent",
            title = "Collection",
            author = "Author",
            source = BookSource.WolneLektury,
            chapters = first + second,
            // Deliberately reversed, as the real source's children can arrive out of order.
            readAlongSources = listOf(
                AudioBookReadAlongSource("volume-2", "Volume 2", second.map { it.id }, audioEpubUrl = VolumeTwoUrl),
                AudioBookReadAlongSource("volume-1", "Volume 1", first.map { it.id }, audioEpubUrl = VolumeOneUrl),
            ),
        )
    }

    private fun chapter(volume: Int, number: Int, parentNumber: Int) = AudioBookChapter(
        id = "volume-$volume-$number",
        title = "Volume $volume chapter $number",
        number = parentNumber,
        listenUrl = "https://example.com/volume-${volume}_$number.mp3",
    )

    private fun writeTimedVolume(target: File, volume: Int, chapterCount: Int) {
        ZipOutputStream(target.outputStream()).use { zip ->
            val text = (1..chapterCount).joinToString("") {
                "<p id='s$it'>Volume $volume paragraph $it.</p>"
            }
            val timing = (1..chapterCount).joinToString("") {
                "<par><text src='book.html#s$it'/><audio src='volume-${volume}_$it.mp3' clipBegin='10s' clipEnd='30s'/></par>"
            }
            for ((path, content) in mapOf(
                "book.html" to "<html><body>$text</body></html>",
                "content.smil" to "<smil><body>$timing</body></smil>",
            )) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val VolumeOneUrl = "https://example.com/volume-1.audio.epub"
        const val VolumeTwoUrl = "https://example.com/volume-2.audio.epub"
    }
}
