package com.librivox.mobile.readalong

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadAlongArchiveDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val transferred = AtomicLong()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun rangeDownloadKeepsSourceTimingAndTextWithoutEmbeddedAudio() {
        val archive = archive()
        serve(archive)
        val target = folder.newFile("reader.epub")

        downloadReadAlongArchive(server.url("/book.audio.epub").toString(), target)

        ZipFile(target).use { zip ->
            assertEquals("mimetype", zip.entries().nextElement().name)
            assertEquals(ZipEntry.STORED, zip.getEntry("mimetype").method)
            TEXT_ENTRIES.forEach { (name, text) ->
                assertEquals(text, zip.getInputStream(zip.getEntry(name)).bufferedReader().use { it.readText() })
            }
            assertNotNull(zip.getEntry("EPUB/styles.css"))
            assertFalse(zip.entries().asSequence().any { it.name.endsWith(".mp3") || it.name.endsWith(".png") })
        }
        val parsed = WolneAudioEpubParser().parse(
            AudioBook(
                id = "wolnelektury-range-test",
                title = "Range test",
                author = "Author",
                source = BookSource.WolneLektury,
                chapters = listOf(AudioBookChapter("chapter-1", "Chapter 1", 1, listenUrl = "https://example.com/chapter.mp3")),
            ),
            target,
        )
        assertTrue(parsed.hasTimedSync)
        val segment = parsed.chapters.single().segments.single()
        assertEquals("Source text with Polish letters: żółć.", segment.text)
        assertEquals(1_000L, segment.clipBeginMs)
        assertEquals(3_000L, segment.clipEndMs)
        assertEquals("EPUB/audio/chapter.mp3", segment.audioRef)
        assertTrue(target.length() < 16_384L)
        assertTrue("Transferred ${transferred.get()} of ${archive.size} bytes", transferred.get() < 100_000L)
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.getHeader("Range") != null && it.getHeader("Accept-Encoding") == "identity" })
        assertEquals("\"archive-v1\"", requests.last().getHeader("If-Range"))
    }

    @Test fun maximumZipCommentStillFindsDirectoryOutsideTailRange() {
        serve(archive(comment = "x".repeat(65_535)))
        val target = folder.newFile()

        downloadReadAlongArchive(server.url("/book.zip").toString(), target)

        ZipFile(target).use { assertNotNull(it.getEntry("EPUB/overlay.smil")) }
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.getHeader("Range") != null })
    }

    @Test fun serverIgnoringRangeFallsBackToFullDownload() {
        val archive = archive()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return MockResponse().setBody(Buffer().write(archive))
            }
        }
        val target = folder.newFile()

        downloadReadAlongArchive(server.url("/book.epub").toString(), target)

        assertArrayEquals(archive, target.readBytes())
        assertEquals(2, requests.size)
        assertNotNull(requests.first().getHeader("Range"))
        assertEquals(null, requests.last().getHeader("Range"))
    }

    @Test fun incorrectContentRangeFallsBackRatherThanReadingWrongZipOffsets() {
        val archive = archive()
        serve(archive) { _, response -> response.setHeader("Content-Range", "bytes 0-1/${archive.size}") }
        assertFallback(archive)
    }

    @Test fun truncatedRangeFallsBackWithoutAcceptingPartialArchive() {
        val archive = archive()
        serve(archive) { _, response -> response.setBody("short") }
        assertFallback(archive)
    }

    @Test fun changedEntityBetweenRangesFallsBackWithoutMixingVersions() {
        val archive = archive()
        serve(archive) { number, response ->
            if (number > 1) response.setHeader("ETag", "\"archive-v2\"") else response
        }
        assertFallback(archive)
        assertEquals(3, requests.size)
    }

    @Test fun corruptedMetadataCrcFallsBackInsteadOfCachingDamagedText() {
        val archive = archive()
        val central = findCentralEntry(archive, "EPUB/chapter.xhtml")
        archive[central + 16] = (archive[central + 16].toInt() xor 1).toByte()
        serve(archive)
        assertFallback(archive)
    }

    @Test fun oversizedDeclaredTextIsRejectedBeforeInflating() {
        val archive = archive()
        val central = findCentralEntry(archive, "EPUB/chapter.xhtml")
        val declaredSize = 17 * 1024 * 1024
        repeat(4) { archive[central + 24 + it] = (declaredSize ushr (it * 8)).toByte() }
        serve(archive)
        assertFallback(archive)
        assertEquals(2, requests.size)
    }

    @Test fun nonZipResponseUsesExistingFullDownloader() {
        val invalid = "This is not a ZIP archive".toByteArray()
        serve(invalid)
        assertFallback(invalid)
    }

    private fun assertFallback(expected: ByteArray) {
        val target = folder.newFile()
        downloadReadAlongArchive(server.url("/book.epub").toString(), target)
        assertArrayEquals(expected, target.readBytes())
        assertEquals(null, requests.last().getHeader("Range"))
    }

    private fun serve(
        archive: ByteArray,
        mutateRange: (Int, MockResponse) -> MockResponse = { _, response -> response },
    ) {
        var rangeNumber = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val range = request.getHeader("Range")
                if (range == null) return MockResponse().setBody(Buffer().write(archive))
                val parts = range.removePrefix("bytes=").split('-')
                val start = if (parts[0].isEmpty()) (archive.size - parts[1].toInt()).coerceAtLeast(0) else parts[0].toInt()
                val end = if (parts[0].isEmpty()) archive.lastIndex else parts[1].toInt()
                if (start !in archive.indices || end !in start until archive.size) return MockResponse().setResponseCode(416)
                val body = archive.copyOfRange(start, end + 1)
                transferred.addAndGet(body.size.toLong())
                return mutateRange(
                    ++rangeNumber,
                    MockResponse().setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${archive.size}")
                        .setHeader("ETag", "\"archive-v1\"")
                        .setBody(Buffer().write(body)),
                )
            }
        }
    }

    private fun archive(comment: String = ""): ByteArray = ByteArrayOutputStream().apply {
        ZipOutputStream(this).use { zip ->
            TEXT_ENTRIES.forEach { (name, text) ->
                val bytes = text.toByteArray()
                zip.putNextEntry(ZipEntry(name).apply {
                    if (name == "mimetype") {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                })
                zip.write(bytes)
                zip.closeEntry()
            }
            val audio = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
            zip.putNextEntry(ZipEntry("EPUB/audio/chapter.mp3").apply {
                method = ZipEntry.STORED
                size = audio.size.toLong()
                crc = CRC32().apply { update(audio) }.value
            })
            zip.write(audio)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("EPUB/cover.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.setComment(comment)
        }
    }.toByteArray()

    private fun findCentralEntry(archive: ByteArray, name: String): Int {
        for (index in 0 until archive.size - 46) {
            if (archive[index] != 0x50.toByte() || archive[index + 1] != 0x4b.toByte() ||
                archive[index + 2] != 1.toByte() || archive[index + 3] != 2.toByte()
            ) continue
            val length = (archive[index + 28].toInt() and 255) or ((archive[index + 29].toInt() and 255) shl 8)
            if (index + 46 + length <= archive.size && archive.copyOfRange(index + 46, index + 46 + length).toString(Charsets.UTF_8) == name) return index
        }
        error("Missing central entry $name")
    }

    private companion object {
        val TEXT_ENTRIES = linkedMapOf(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="EPUB/content.opf"/></rootfiles></container>""",
            "EPUB/content.opf" to """<package><manifest><item id="text" href="chapter.xhtml" media-type="application/xhtml+xml" media-overlay="timing"/><item id="timing" href="overlay.smil" media-type="application/smil+xml"/><item id="style" href="styles.css" media-type="text/css"/><item id="audio" href="audio/chapter.mp3" media-type="audio/mpeg"/></manifest><spine><itemref idref="text"/></spine></package>""",
            "EPUB/chapter.xhtml" to """<html><body><p id="p1">Source text with Polish letters: żółć.</p></body></html>""",
            "EPUB/styles.css" to "p { text-indent: 1em; }",
            "EPUB/overlay.smil" to """<smil><body><seq><par><text src="chapter.xhtml#p1"/><audio src="audio/chapter.mp3" clipBegin="1s" clipEnd="3s"/></par></seq></body></smil>""",
        )
    }
}
