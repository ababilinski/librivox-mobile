package com.librivox.mobile.readalong

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadAlongRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolver_reportsMissingPartWithoutCopyToSourceFileMessage() {
        val resolver = ReadAlongAssetResolver(
            cacheDir = temporaryFolder.newFolder("cache"),
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { _, _ -> },
        )
        val book = antekBook()
        val candidate = ReadAlongAssetCandidate(
            url = AntekDaisyUrl,
            fileName = "book.daisy.zip",
        )

        val error = assertThrows(IllegalStateException::class.java) {
            resolver.resolve(book, candidate)
        }

        assertTrue(error.message.orEmpty().contains("Download did not create book.daisy.zip.part."))
        assertFalse(error.message.orEmpty().contains("source file doesn't exist"))
    }

    @Test
    fun loadReadAlongBook_fallsBackToOrdinaryEpubWhenDaisyDownloadDoesNotCreatePart() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = cacheDir,
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { url, target ->
                downloads += url
                if (url == AntekEpubUrl) {
                    writeStaticEpub(target)
                }
            },
        )

        val readAlong = loadReadAlongBook(
            book = antekBook(),
            parser = WolneAudioEpubParser(),
            resolver = resolver,
        )

        assertEquals(listOf(AntekDaisyUrl, AntekEpubUrl), downloads)
        assertFalse(readAlong.hasTimedSync)
        assertTrue(readAlong.chapters.single().fullText.contains("Antek urodził się"))
        assertTrue(File(cacheDir, "readalong/wolnelektury-antek/book.epub").isFile)
    }

    @Test
    fun loadReadAlongBook_prefersTimedDaisyWhenAudioEpubOnlyHasStaticText() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = cacheDir,
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { url, target ->
                downloads += url
                when (url) {
                    AntekAudioEpubUrl -> writeStaticEpub(target)
                    AntekDaisyUrl -> writeTimedDaisy(target)
                }
            },
        )

        val readAlong = loadReadAlongBook(
            book = antekBook().copy(audioEpubUrl = AntekAudioEpubUrl),
            parser = WolneAudioEpubParser(),
            resolver = resolver,
        )

        assertEquals(listOf(AntekAudioEpubUrl, AntekDaisyUrl), downloads)
        assertTrue(readAlong.hasTimedSync)
        assertEquals("Antek urodził się we wsi nad Wisłą.", readAlong.chapters.single().fullText)
        assertEquals("1", readAlong.chapters.single().segments.single().footnotes.single().number)
        assertTrue(readAlong.chapters.single().segments.single().footnotes.single().text.contains("wieś"))
        assertEquals("Antek urodził się we wsi".length, readAlong.chapters.single().segments.single().footnotes.single().textOffset)
    }

    @Test
    fun loadReadAlongBook_prefersOrdinaryEpubTocWhenDaisyOnlyHasStaticText() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = cacheDir,
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { url, target ->
                downloads += url
                when (url) {
                    AntekDaisyUrl -> writeStaticDaisy(target)
                    AntekEpubUrl -> writeStaticEpubWithToc(target)
                }
            },
        )

        val readAlong = loadReadAlongBook(
            book = antekBook(),
            parser = WolneAudioEpubParser(),
            resolver = resolver,
        )

        assertEquals(listOf(AntekDaisyUrl, AntekEpubUrl), downloads)
        assertFalse(readAlong.hasTimedSync)
        assertEquals(listOf("Antek"), readAlong.tableOfContents.map { it.title })
        assertTrue(readAlong.chapters.single().fullText.contains("EPUB paragraph"))
        assertFalse(readAlong.chapters.single().fullText.contains("DAISY paragraph"))
    }

    @Test
    fun loadReadAlongBook_mergesOrdinaryEpubTocIntoTimedReadAlong() {
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = temporaryFolder.newFolder("cache"),
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { url, target ->
                downloads += url
                when (url) {
                    AntekAudioEpubUrl -> writeTimedDaisy(target)
                    AntekEpubUrl -> writeStaticEpubWithToc(target)
                }
            },
        )

        val readAlong = loadReadAlongBook(
            book = antekBook().copy(audioEpubUrl = AntekAudioEpubUrl, daisyUrl = null),
            parser = WolneAudioEpubParser(),
            resolver = resolver,
        )

        assertEquals(listOf(AntekAudioEpubUrl, AntekEpubUrl), downloads)
        assertTrue(readAlong.hasTimedSync)
        assertEquals(listOf("Antek"), readAlong.tableOfContents.map { it.title })
        assertEquals("Antek urodził się we wsi nad Wisłą.", readAlong.chapters.single().fullText)
    }

    @Test
    fun withFootnotesFrom_prefersRicherStaticTocAndRetargetsRowsToTimedChapters() {
        val timed = ReadAlongBook(
            bookId = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            hasTimedSync = true,
            chapters = listOf(
                buildReadAlongChapter(
                    AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1),
                    displayIndex = 1,
                    segments = listOf(
                        ReadAlongSegment(
                            id = "timed-1",
                            chapterId = "pan-1",
                            text = "Litwo! Ojczyzno moja.",
                            textRef = "audio/part3.xhtml#s1",
                            audioRef = "book0.mp3",
                            clipBeginMs = 0L,
                            clipEndMs = 1_000L,
                        ),
                    ),
                ),
                buildReadAlongChapter(
                    AudioBookChapter(id = "pan-2", title = "Księga druga. Zamek", number = 2),
                    displayIndex = 2,
                    segments = listOf(
                        ReadAlongSegment(
                            id = "timed-2",
                            chapterId = "pan-2",
                            text = "Kto z nas tych lat nie pomni.",
                            textRef = "audio/part4.xhtml#s1",
                            audioRef = "book1.mp3",
                            clipBeginMs = 0L,
                            clipEndMs = 1_000L,
                        ),
                    ),
                ),
            ),
            tableOfContents = listOf(
                ReadAlongTocItem(
                    id = "toc:first",
                    title = "Księga pierwsza. Gospodarstwo",
                    level = 0,
                    href = "audio/part3.xhtml#s1",
                    chapterId = "pan-1",
                    segmentId = "timed-1",
                ),
            ),
        )
        val staticSource = ReadAlongBook(
            bookId = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            hasTimedSync = false,
            chapters = emptyList(),
            tableOfContents = listOf(
                ReadAlongTocItem(
                    id = "toc:title",
                    title = "Strona tytułowa",
                    level = 0,
                    href = "EPUB/title.xhtml",
                ),
                ReadAlongTocItem(
                    id = "toc:first",
                    title = "Księga pierwsza. Gospodarstwo",
                    level = 0,
                    href = "EPUB/part3.xhtml#s1",
                    chapterId = "pan-1",
                    segmentId = "static-1",
                ),
                ReadAlongTocItem(
                    id = "toc:second",
                    title = "Księga druga. Zamek",
                    level = 0,
                    href = "EPUB/part5.xhtml#s1",
                    chapterId = "pan-2",
                    segmentId = "static-2",
                ),
            ),
        )

        val merged = timed.withFootnotesFrom(staticSource)

        assertEquals(
            listOf("Strona tytułowa", "Księga pierwsza. Gospodarstwo", "Księga druga. Zamek"),
            merged.tableOfContents.map { it.title },
        )
        assertEquals(listOf(null, "pan-1", "pan-2"), merged.tableOfContents.map { it.chapterId })
        assertEquals(listOf(null, "timed-1", "timed-2"), merged.tableOfContents.map { it.segmentId })
    }

    @Test
    fun withFootnotesFrom_carriesSourceHeadingStylesIntoTimedSegments() {
        val chapter = AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1)
        val timed = ReadAlongBook(
            bookId = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            hasTimedSync = true,
            chapters = listOf(
                buildReadAlongChapter(
                    chapter = chapter,
                    displayIndex = 1,
                    segments = listOf(
                        ReadAlongSegment(
                            id = "timed-1",
                            chapterId = chapter.id,
                            text = "Księga pierwsza",
                            textRef = "daisy/ksiegapierwsza.html#s1",
                            audioRef = "book0.mp3",
                            clipBeginMs = 0L,
                            clipEndMs = 1_000L,
                        ),
                        ReadAlongSegment(
                            id = "timed-2",
                            chapterId = chapter.id,
                            text = "Gospodarstwo",
                            textRef = "daisy/ksiegapierwsza.html#s2",
                            audioRef = "book0.mp3",
                            clipBeginMs = 1_000L,
                            clipEndMs = 2_000L,
                        ),
                        ReadAlongSegment(
                            id = "timed-3",
                            chapterId = chapter.id,
                            text = "Litwo! Ojczyzno moja!",
                            textRef = "daisy/ksiegapierwsza.html#s3",
                            audioRef = "book0.mp3",
                            clipBeginMs = 2_000L,
                            clipEndMs = 3_000L,
                        ),
                    ),
                ),
            ),
        )
        val staticSource = ReadAlongBook(
            bookId = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            hasTimedSync = false,
            chapters = listOf(
                buildReadAlongChapter(
                    chapter = chapter,
                    displayIndex = 1,
                    segments = listOf(
                        ReadAlongSegment(
                            id = "static-1",
                            chapterId = chapter.id,
                            text = "Księga pierwsza",
                            textRef = "EPUB/part2.xhtml#sec5",
                            audioRef = null,
                            clipBeginMs = null,
                            clipEndMs = null,
                            role = ReadAlongSegmentRole.Heading1,
                            sourceStyle = ReadAlongTextStyle(
                                fontWeight = ReadAlongFontWeight.Bold,
                                textSizeMultiplier = 2f,
                            ),
                        ),
                        ReadAlongSegment(
                            id = "static-2",
                            chapterId = chapter.id,
                            text = "Gospodarstwo",
                            textRef = "EPUB/part3.xhtml#sec6",
                            audioRef = null,
                            clipBeginMs = null,
                            clipEndMs = null,
                            role = ReadAlongSegmentRole.Heading2,
                            sourceStyle = ReadAlongTextStyle(
                                fontWeight = ReadAlongFontWeight.Normal,
                                textSizeMultiplier = 1.5f,
                            ),
                        ),
                        ReadAlongSegment(
                            id = "static-3",
                            chapterId = chapter.id,
                            text = "Litwo! Ojczyzno moja!",
                            textRef = "EPUB/part3.xhtml#sec8",
                            audioRef = null,
                            clipBeginMs = null,
                            clipEndMs = null,
                            role = ReadAlongSegmentRole.Verse,
                        ),
                    ),
                ),
            ),
        )

        val merged = timed.withFootnotesFrom(staticSource)
        val mergedSegments = merged.chapters.single().segments

        assertTrue(merged.hasTimedSync)
        assertEquals(0L, mergedSegments[0].clipBeginMs)
        assertEquals(ReadAlongSegmentRole.Heading1, mergedSegments[0].role)
        assertEquals(ReadAlongFontWeight.Bold, mergedSegments[0].sourceStyle.fontWeight)
        assertEquals(2f, mergedSegments[0].sourceStyle.textSizeMultiplier)
        assertEquals(ReadAlongSegmentRole.Heading2, mergedSegments[1].role)
        assertEquals(ReadAlongFontWeight.Normal, mergedSegments[1].sourceStyle.fontWeight)
        assertEquals(1.5f, mergedSegments[1].sourceStyle.textSizeMultiplier)
        assertEquals(ReadAlongSegmentRole.Verse, mergedSegments[2].role)
    }

    @Test
    fun withFootnotesFrom_prefersSourceTocOverSynthesizedAudioChunks() {
        val timedChapters = (1..5).map { number ->
            val chapterId = "goriot-$number"
            buildReadAlongChapter(
                AudioBookChapter(id = chapterId, title = "Część $number", number = number),
                displayIndex = number,
                segments = listOf(
                    ReadAlongSegment(
                        id = "timed-$number",
                        chapterId = chapterId,
                        text = "Timed chapter $number text.",
                        textRef = "EPUB/part1.xhtml#sec$number",
                        audioRef = "book${number - 1}.mp3",
                        clipBeginMs = 0L,
                        clipEndMs = 1_000L,
                    ),
                ),
            )
        }
        val timed = ReadAlongBook(
            bookId = "wolnelektury-ojciec-goriot",
            title = "Ojciec Goriot",
            hasTimedSync = true,
            chapters = timedChapters,
            tableOfContents = timedChapters.readerChapterTableOfContents(),
        )
        val staticSource = ReadAlongBook(
            bookId = "wolnelektury-ojciec-goriot",
            title = "Ojciec Goriot",
            hasTimedSync = false,
            chapters = emptyList(),
            tableOfContents = listOf(
                ReadAlongTocItem(
                    id = "toc:title",
                    title = "Strona tytułowa",
                    level = 0,
                    href = "EPUB/title.xhtml",
                ),
                ReadAlongTocItem(
                    id = "toc:contents",
                    title = "Spis treści",
                    level = 0,
                    href = "EPUB/nav.xhtml",
                ),
                ReadAlongTocItem(
                    id = "toc:start",
                    title = "Początek utworu",
                    level = 0,
                    href = "EPUB/part1.xhtml",
                    chapterId = "goriot-1",
                    segmentId = "static-1",
                ),
                ReadAlongTocItem(
                    id = "toc:support",
                    title = "Wesprzyj Wolne Lektury",
                    level = 0,
                    href = "EPUB/support.xhtml",
                ),
                ReadAlongTocItem(
                    id = "toc:last",
                    title = "Strona redakcyjna",
                    level = 0,
                    href = "EPUB/last.xhtml",
                ),
            ),
        )

        val merged = timed.withFootnotesFrom(staticSource)

        assertEquals(
            listOf(
                "Strona tytułowa",
                "Spis treści",
                "Początek utworu",
                "Wesprzyj Wolne Lektury",
                "Strona redakcyjna",
            ),
            merged.tableOfContents.map { it.title },
        )
        assertEquals(
            listOf(null, null, "goriot-1", null, null),
            merged.tableOfContents.map { it.chapterId },
        )
        assertEquals(
            listOf(null, null, "timed-1", null, null),
            merged.tableOfContents.map { it.segmentId },
        )
    }

    @Test
    fun loadReadAlongBook_prefersTimedSourceWithBetterTextPathCoverage() {
        val downloads = mutableListOf<String>()
        val resolver = ReadAlongAssetResolver(
            cacheDir = temporaryFolder.newFolder("cache"),
            filesDir = temporaryFolder.newFolder("files"),
            downloader = { url, target ->
                downloads += url
                when (url) {
                    TwoChapterAudioEpubUrl -> writeLowCoverageTimedZip(target)
                    TwoChapterDaisyUrl -> writeBetterCoverageTimedZip(target)
                }
            },
        )

        val readAlong = loadReadAlongBook(
            book = twoChapterBook(),
            parser = WolneAudioEpubParser(),
            resolver = resolver,
        )

        assertEquals(listOf(TwoChapterAudioEpubUrl, TwoChapterDaisyUrl), downloads)
        assertTrue(readAlong.hasTimedSync)
        assertEquals(listOf("chapter-1", "chapter-2"), readAlong.chapters.map { it.chapterId })
        assertEquals("Better first chapter.", readAlong.chapters[0].fullText)
        assertEquals("Better second chapter.", readAlong.chapters[1].fullText)
    }

    private fun antekBook(): AudioBook =
        AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            daisyUrl = AntekDaisyUrl,
            epubUrl = AntekEpubUrl,
            chapters = listOf(AudioBookChapter(id = "antek-1", title = "Antek", number = 1)),
        )

    private fun twoChapterBook(): AudioBook =
        AudioBook(
            id = "wolnelektury-two-chapter",
            title = "Two chapter book",
            author = "Author",
            source = BookSource.WolneLektury,
            audioEpubUrl = TwoChapterAudioEpubUrl,
            daisyUrl = TwoChapterDaisyUrl,
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter one",
                    number = 1,
                    listenUrl = "https://example.com/audio0.mp3",
                ),
                AudioBookChapter(
                    id = "chapter-2",
                    title = "Chapter two",
                    number = 2,
                    listenUrl = "https://example.com/audio1.mp3",
                ),
            ),
        )

    private fun writeStaticEpub(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/content.opf",
                """
                    <package>
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                        <item id="annotations" href="annotations.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/chapter.xhtml",
                """
                    <html><body>
                      <p>Antek urodził się we wsi<a class="anchor" id="anchor-1" href="annotations.xhtml#annotation-1"><sup>1</sup></a> nad Wisłą.</p>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/annotations.xhtml",
                """
                    <html><body>
                      <div id="footnotes">
                        <div id="annotation-1" class="annotation"><a href="chapter.xhtml#anchor-1">1</a>. wieś — osada poza miastem. [przypis edytorski]</div>
                      </div>
                    </body></html>
                """.trimIndent(),
            )
        }
    }

    private fun writeTimedDaisy(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "book.html",
                """
                    <html><body>
                      <p id="s1">Antek urodził się we wsi nad Wisłą.</p>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "content.smil",
                """
                    <smil>
                      <body>
                        <par>
                          <text src="book.html#s1"/>
                          <audio src="antek.mp3" clip-begin="npt=0.000s" clip-end="npt=4.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            )
        }
    }

    private fun writeLowCoverageTimedZip(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "shared.html",
                """
                    <html><body>
                      <p id="s1">Weak first chapter.</p>
                      <p id="s2">Weak second chapter.</p>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "audio.smil",
                """
                    <smil>
                      <body>
                        <par>
                          <text src="shared.html#s1"/>
                          <audio src="audio0.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                        <par>
                          <text src="shared.html#s2"/>
                          <audio src="audio1.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            )
        }
    }

    private fun writeBetterCoverageTimedZip(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "chapter1.html",
                """
                    <html><body>
                      <p id="s1">Better first chapter.</p>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "chapter2.html",
                """
                    <html><body>
                      <p id="s1">Better second chapter.</p>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "audio.smil",
                """
                    <smil>
                      <body>
                        <par>
                          <text src="chapter1.html#s1"/>
                          <audio src="audio0.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                        <par>
                          <text src="chapter2.html#s1"/>
                          <audio src="audio1.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            )
        }
    }

    private fun writeStaticDaisy(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "book.html",
                """
                    <html><body>
                      <p>DAISY paragraph.</p>
                    </body></html>
                """.trimIndent(),
            )
        }
    }

    private fun writeStaticEpubWithToc(target: File) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.writeEntry(
                "META-INF/container.xml",
                """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/content.opf",
                """
                    <package>
                      <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="nav"/>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/nav.xhtml",
                """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol><li><a href="chapter.xhtml">Antek</a></li></ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
            )
            zip.writeEntry(
                "EPUB/chapter.xhtml",
                """
                    <html><body>
                      <h2>Antek</h2>
                      <p>EPUB paragraph.</p>
                    </body></html>
                """.trimIndent(),
            )
        }
    }

    private fun ZipOutputStream.writeEntry(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray())
        closeEntry()
    }

    private companion object {
        const val AntekAudioEpubUrl = "https://wolnelektury.pl/media/book/audio.epub/antek.epub"
        const val AntekDaisyUrl = "https://wolnelektury.pl/media/book/daisy.zip/boleslaw-prus-antek.daisy.zip"
        const val AntekEpubUrl = "https://wolnelektury.pl/media/book/epub/antek.epub"
        const val TwoChapterAudioEpubUrl = "https://example.com/audio.epub"
        const val TwoChapterDaisyUrl = "https://example.com/book.daisy.zip"
    }
}
