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
import org.junit.Test

class WolneAudioEpubParserTest {
    private val parser = WolneAudioEpubParser()

    @Test
    fun parseClipTimeMillis_supportsSmilUnits() {
        assertEquals(4_800L, parser.parseClipTimeMillis("4.800s"))
        assertEquals(4_800L, parser.parseClipTimeMillis("npt=4.800s"))
        assertEquals(500L, parser.parseClipTimeMillis("500ms"))
        assertEquals(62_500L, parser.parseClipTimeMillis("00:01:02.500"))
    }

    @Test
    fun parse_mapsSmilTextAnchorsToAppChapters() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="p1" href="part1.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p2" href="part2.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="overlay" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="p1"/>
                        <itemref idref="p2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
		                      <nav epub:type="toc" role="doc-toc">
		                        <ol>
		                          <li><a href="part1.xhtml#s1">Początek utworu</a></li>
		                          <li>
		                            <a href="part1.xhtml#s1">Księga pierwsza</a>
		                            <ol>
		                              <li><a href="part1.xhtml#s2">Gospodarstwo</a></li>
		                            </ol>
		                          </li>
		                          <li>
		                            <a href="part2.xhtml#s1">Księga druga</a>
		                            <ol>
		                              <li><a href="part2.xhtml#s1">Zamek</a></li>
		                            </ol>
		                          </li>
		                        </ol>
		                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part1.xhtml#s1"/>
                          <audio src="book0.mp3" clipBegin="0.000s" clipEnd="1.500s"/>
                        </par>
                        <par>
                          <text src="part1.xhtml#s2"/>
                          <audio src="book0.mp3" clipBegin="1.500s" clipEnd="3.000s"/>
                        </par>
                        <par>
                          <text src="part2.xhtml#s1"/>
                          <audio src="book1.mp3" clipBegin="0.000s" clipEnd="2.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/part1.xhtml" to """
                    <html><body>
                      <p id="s1">Litwo! Ojczyzno moja.</p>
                      <p id="s2">Ty jesteś jak zdrowie.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/part2.xhtml" to """
                    <html><body>
                      <p id="s1">Ile cię trzeba cenić.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/pan.audio.epub",
            chapters = listOf(
                AudioBookChapter(id = "pan-1", title = "Book one", number = 1),
                AudioBookChapter(id = "pan-2", title = "Book two", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(2, readAlong.chapters.size)
        assertEquals("pan-1", readAlong.chapters[0].chapterId)
        assertEquals("Litwo! Ojczyzno moja.\n\nTy jesteś jak zdrowie.", readAlong.chapters[0].fullText)
        assertEquals(1_500L, readAlong.chapters[0].segments[1].clipBeginMs)
        assertEquals("pan-2", readAlong.chapters[1].chapterId)
        assertEquals("Ile cię trzeba cenić.", readAlong.chapters[1].fullText)
        assertEquals(
            listOf("Początek utworu", "Księga pierwsza. Gospodarstwo", "Księga druga. Zamek"),
            readAlong.tableOfContents.map { it.title },
        )
        assertEquals(listOf("pan-1", "pan-1", "pan-2"), readAlong.tableOfContents.map { it.chapterId })
    }

    @Test
    fun parse_audioEpubKeepsWolneSourceTocWhenAudioUsesMultipleChunks() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="part1" href="part1.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="support" href="support.xhtml" media-type="application/xhtml+xml"/>
                        <item id="last" href="last.xhtml" media-type="application/xhtml+xml"/>
                        <item id="overlay" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="part1"/>
                        <itemref idref="support"/>
                        <itemref idref="last"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol>
                          <li><a href="title.xhtml">Strona tytułowa</a></li>
                          <li><a href="nav.xhtml">Spis treści</a></li>
                          <li><a href="part1.xhtml">Początek utworu</a></li>
                          <li><a href="support.xhtml">Wesprzyj Wolne Lektury</a></li>
                          <li><a href="last.xhtml">Strona redakcyjna</a></li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part1.xhtml#sec1"/>
                          <audio src="book0.mp3" clipBegin="0.000s" clipEnd="1.500s"/>
                        </par>
                        <par>
                          <text src="part1.xhtml#sec2"/>
                          <audio src="book1.mp3" clipBegin="0.000s" clipEnd="2.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/title.xhtml" to "<html><body><p>Ta lektura jest dostępna na wolnelektury.pl.</p></body></html>",
                "EPUB/part1.xhtml" to """
                    <html><body>
                      <p id="sec1">Pierwsza część tekstu.</p>
                      <p id="sec2">Druga część tekstu.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/support.xhtml" to "<html><body><p>Wesprzyj Wolne Lektury.</p></body></html>",
                "EPUB/last.xhtml" to "<html><body><p>Strona redakcyjna.</p></body></html>",
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-ojciec-goriot",
            title = "Ojciec Goriot",
            author = "Honoré de Balzac",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/ojciec-goriot.audio.epub",
            chapters = listOf(
                AudioBookChapter(id = "goriot-1", title = "Część 1", number = 1),
                AudioBookChapter(id = "goriot-2", title = "Część 2", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(
            listOf(
                "Strona tytułowa",
                "Spis treści",
                "Początek utworu",
                "Wesprzyj Wolne Lektury",
                "Strona redakcyjna",
            ),
            readAlong.tableOfContents.map { it.title },
        )
        assertEquals(listOf(null, null, "goriot-1", null, null), readAlong.tableOfContents.map { it.chapterId })
        assertEquals(
            listOf("EPUB/title.xhtml", "EPUB/nav.xhtml", "EPUB/part1.xhtml", "EPUB/support.xhtml", "EPUB/last.xhtml"),
            readAlong.tableOfContents.map { it.href },
        )
    }

    @Test
    fun parse_resolvesAudioEpubTocByChapterTitleWhenTimedHrefIsMissing() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="p3" href="part3.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p4" href="part4.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p5" href="part5.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="overlay" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="p3"/>
                        <itemref idref="p4"/>
                        <itemref idref="p5"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol>
                          <li>
                            <a href="part2.xhtml#title">Księga pierwsza</a>
                            <ol><li><a href="part3.xhtml#s1">Gospodarstwo</a></li></ol>
                          </li>
                          <li>
                            <a href="part4.xhtml#title">Księga druga</a>
                            <ol><li><a href="part5.xhtml#s1">Zamek</a></li></ol>
                          </li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part3.xhtml#s1"/>
                          <audio src="book0.mp3" clipBegin="0.000s" clipEnd="1.500s"/>
                        </par>
                        <par>
                          <text src="part4.xhtml#s1"/>
                          <audio src="book1.mp3" clipBegin="0.000s" clipEnd="2.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/part3.xhtml" to """
                    <html><body><p id="s1">Litwo! Ojczyzno moja.</p></body></html>
                """.trimIndent(),
                "EPUB/part4.xhtml" to """
                    <html><body><p id="s1">Kto z nas tych lat nie pomni.</p></body></html>
                """.trimIndent(),
                "EPUB/part5.xhtml" to """
                    <html><body><p id="s1">Static-only second chapter body.</p></body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/pan.audio.epub",
            chapters = listOf(
                AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1),
                AudioBookChapter(id = "pan-2", title = "Księga druga. Zamek", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val chapterToc = readAlong.tableOfContents.filter { it.title.startsWith("Księga") }

        assertTrue(readAlong.hasTimedSync)
        assertEquals(
            listOf("Księga pierwsza. Gospodarstwo", "Księga druga. Zamek"),
            chapterToc.map { it.title },
        )
        assertEquals(listOf("pan-1", "pan-2"), chapterToc.map { it.chapterId })
        assertEquals(readAlong.chapters[1].segments.first().id, chapterToc[1].segmentId)
    }

    @Test
    fun parse_collapsesMultiChildWolneBookTocEntryToSingleVisibleChapter() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="p24" href="part24.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p25" href="part25.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p26" href="part26.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="overlay" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="p24"/>
                        <itemref idref="p25"/>
                        <itemref idref="p26"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol>
                          <li>
                            <a href="part24.xhtml#title">Księga dwunasta</a>
                            <ol>
                              <li><a href="part25.xhtml#s1">Kochajmy się</a></li>
                              <li><a href="part26.xhtml#s1">Epilog</a></li>
                            </ol>
                          </li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part25.xhtml#s1"/>
                          <audio src="book11.mp3" clipBegin="0.000s" clipEnd="1.500s"/>
                        </par>
                        <par>
                          <text src="part26.xhtml#s1"/>
                          <audio src="book11.mp3" clipBegin="1.500s" clipEnd="3.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/part24.xhtml" to """
                    <html><body><h2 id="title">Księga dwunasta</h2></body></html>
                """.trimIndent(),
                "EPUB/part25.xhtml" to """
                    <html><body><p id="s1">Kochajmy się!</p></body></html>
                """.trimIndent(),
                "EPUB/part26.xhtml" to """
                    <html><body><p id="s1">O tem-że dumać na paryskim bruku.</p></body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/pan.audio.epub",
            chapters = listOf(
                AudioBookChapter(id = "pan-12", title = "Księga dwunasta. Kochajmy się; Epilog", number = 12),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(
            listOf("Księga dwunasta. Kochajmy się; Epilog"),
            readAlong.tableOfContents.map { it.title },
        )
        assertEquals(listOf("pan-12"), readAlong.tableOfContents.map { it.chapterId })
        assertEquals(listOf("EPUB/part25.xhtml#s1"), readAlong.tableOfContents.map { it.href })
    }

    @Test
    fun parse_preferredTimedChapterOnlyParsesMatchingAudioFirst() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="p1" href="part1.xhtml" media-type="application/xhtml+xml" media-overlay="overlay1"/>
                        <item id="p2" href="part2.xhtml" media-type="application/xhtml+xml" media-overlay="overlay2"/>
                        <item id="overlay1" href="audio1.smil" media-type="application/smil+xml"/>
                        <item id="overlay2" href="audio2.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="p1"/>
                        <itemref idref="p2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/audio1.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part1.xhtml#s1"/>
                          <audio src="book0.mp3" clipBegin="0.000s" clipEnd="1.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/audio2.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="part2.xhtml#s1"/>
                          <audio src="book1.mp3" clipBegin="0.000s" clipEnd="2.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/part1.xhtml" to """
                    <html><body>
                      <p id="s1">First timed sentence.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/part2.xhtml" to """
                    <html><body>
                      <p id="s1">Second timed sentence.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-timed",
            title = "Timed",
            author = "Author",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/timed.audio.epub",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter one",
                    number = 1,
                    listenUrl = "https://example.com/book0.mp3",
                ),
                AudioBookChapter(
                    id = "chapter-2",
                    title = "Chapter two",
                    number = 2,
                    listenUrl = "https://example.com/book1.mp3",
                ),
            ),
        )

        val readAlong = parser.parse(
            book = book,
            readAlongFile = epub,
            preferredChapterId = "chapter-2",
            onlyPreferredChapter = true,
        )

        assertTrue(readAlong.hasTimedSync)
        assertTrue(readAlong.isPartial)
        assertEquals(listOf("chapter-2"), readAlong.chapters.map { it.chapterId })
        assertEquals("Second timed sentence.", readAlong.chapters.single().fullText)
        assertFalse(readAlong.chapters.single().fullText.contains("First timed sentence"))
    }

    @Test
    fun parse_timedEpubDropsRepeatedBookTitleHeadings() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="p1" href="chapter1.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="p2" href="chapter2.xhtml" media-type="application/xhtml+xml" media-overlay="overlay"/>
                        <item id="overlay" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="p1"/>
                        <itemref idref="p2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="chapter1.xhtml#c1-title"/>
                          <audio src="book0.mp3" clipBegin="0.000s" clipEnd="1.000s"/>
                        </par>
                        <par>
                          <text src="chapter1.xhtml#c1-heading"/>
                          <audio src="book0.mp3" clipBegin="1.000s" clipEnd="2.000s"/>
                        </par>
                        <par>
                          <text src="chapter1.xhtml#c1-body"/>
                          <audio src="book0.mp3" clipBegin="2.000s" clipEnd="3.000s"/>
                        </par>
                        <par>
                          <text src="chapter2.xhtml#c2-title"/>
                          <audio src="book1.mp3" clipBegin="0.000s" clipEnd="1.000s"/>
                        </par>
                        <par>
                          <text src="chapter2.xhtml#c2-heading"/>
                          <audio src="book1.mp3" clipBegin="1.000s" clipEnd="2.000s"/>
                        </par>
                        <par>
                          <text src="chapter2.xhtml#c2-body"/>
                          <audio src="book1.mp3" clipBegin="2.000s" clipEnd="3.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
                "EPUB/chapter1.xhtml" to """
                    <html><body>
                      <h1 id="c1-title">Anielka</h1>
                      <h2 id="c1-heading">Rozdział pierwszy</h2>
                      <p id="c1-body">Pierwszy akapit.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/chapter2.xhtml" to """
                    <html><body>
                      <h1 id="c2-title">Anielka</h1>
                      <h2 id="c2-heading">Rozdział drugi</h2>
                      <p id="c2-body">Drugi akapit.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-anielka",
            title = "Anielka",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://example.com/anielka.audio.epub",
            chapters = listOf(
                AudioBookChapter(
                    id = "anielka-1",
                    title = "Rozdział pierwszy",
                    number = 1,
                    listenUrl = "https://example.com/book0.mp3",
                ),
                AudioBookChapter(
                    id = "anielka-2",
                    title = "Rozdział drugi",
                    number = 2,
                    listenUrl = "https://example.com/book1.mp3",
                ),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(
            "Rozdział pierwszy\n\nPierwszy akapit.",
            readAlong.chapters[0].fullText,
        )
        assertEquals("Rozdział drugi\n\nDrugi akapit.", readAlong.chapters[1].fullText)
        assertEquals(1_000L, readAlong.chapters[1].segments[0].clipBeginMs)
    }

    @Test
    fun parse_mapsDaisySmilTextAnchorsToSingleAppChapter() {
        val daisy = testZip(
            suffix = ".zip",
            entries = mapOf(
                "Daisy_202_audioFullText/antek_1.html" to """
                    <html><body>
                      <p><span class="sentence" id="s1">Bolesław Prus Antek</span></p>
                      <p><span class="sentence" id="s2">Antek urodził się we wsi nad Wisłą.</span></p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/wyrx0001.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="antek_1.html#s1"/>
                          <seq>
                            <audio src="boleslaw-prus-antek.mp3" clip-begin="npt=0.000s" clip-end="npt=3.421s"/>
                          </seq>
                        </par>
                        <par>
                          <text src="antek_1.html#s2"/>
                          <seq>
                            <audio src="boleslaw-prus-antek.mp3" clip-begin="npt=3.421s" clip-end="npt=108.841s"/>
                          </seq>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            daisyUrl = "https://example.com/antek.daisy.zip",
            chapters = listOf(AudioBookChapter(id = "antek-1", title = "Antek", number = 1)),
        )

        val readAlong = parser.parse(book, daisy)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(1, readAlong.chapters.size)
        assertEquals("antek-1", readAlong.chapters.single().chapterId)
        assertEquals("Bolesław Prus Antek\n\nAntek urodził się we wsi nad Wisłą.", readAlong.chapters.single().fullText)
        assertEquals(3_421L, readAlong.chapters.single().segments[1].clipBeginMs)
        assertEquals(108_841L, readAlong.chapters.single().segments[1].clipEndMs)
    }

    @Test
    fun parse_mapsWolneDaisyNumericAnchorsAndSkipsStopkaAudio() {
        val daisy = testZip(
            suffix = ".zip",
            entries = mapOf(
                "prus-anielka/book.html" to """
                    <html><body>
                      <div id="book-text">
                        <h1><span id="t2">Bolesław Prus</span><span id="t3">Anielka</span></h1>
                        <h3 id="t5">Rozdział pierwszy. Autor dokonywa przeglądu osób</h3>
                        <p id="t6">Anielka jest piękną dziewczyną.</p>
                        <p id="t7">A wieś jest najstosowniejszym miejscem pobytu dla dzieci.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "prus-anielka/content.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="book.html#1"/>
                          <audio src="book1.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                        <par>
                          <text src="book.html#4"/>
                          <audio src="book2.mp3" clip-begin="npt=0.000s" clip-end="npt=4.000s"/>
                        </par>
                        <par>
                          <text src="book.html#5"/>
                          <audio src="book2.mp3" clip-begin="npt=4.000s" clip-end="npt=8.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-prus-anielka",
            title = "Anielka",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            daisyUrl = "https://example.com/prus-anielka.daisy.zip",
            chapters = listOf(
                AudioBookChapter(id = "anielka-1", title = "Stopka", number = 1),
                AudioBookChapter(
                    id = "anielka-2",
                    title = "Rozdział pierwszy. Autor dokonywa przeglądu osób",
                    number = 2,
                ),
            ),
        )

        val readAlong = parser.parse(book, daisy)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(1, readAlong.chapters.size)
        assertEquals("anielka-2", readAlong.chapters.single().chapterId)
        assertEquals(
            "Rozdział pierwszy. Autor dokonywa przeglądu osób\n\nAnielka jest piękną dziewczyną.",
            readAlong.chapters.single().fullText,
        )
        assertFalse(readAlong.chapters.single().fullText.contains("Bolesław Prus"))
    }

    @Test
    fun parse_mapsOutOfOrderDaisySmilsByTextChapterInsteadOfZipOrder() {
        val daisy = testZip(
            suffix = ".zip",
            entries = linkedMapOf(
                "Daisy_202_audioFullText/ksiegapierwsza.html" to """
                    <html><body>
                      <h2 id="c1-title">Księga pierwsza</h2>
                      <p id="c1-p1">Litwo! Ojczyzno moja!</p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/ksiegatrzecia.html" to """
                    <html><body>
                      <h2 id="c3-title">Księga trzecia</h2>
                      <p id="c3-p1">Hrabia wracał do siebie.</p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/ksiegadruga.html" to """
                    <html><body>
                      <h2 id="c2-title">Księga druga</h2>
                      <p id="c2-p1">Kto z nas tych lat nie pomni.</p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/third.smil" to """
                    <smil><body>
                      <par><text src="ksiegatrzecia.html#c3-title"/><audio src="pan-tadeusz-ksiega-03.mp3" clip-begin="npt=0.000s" clip-end="npt=2.000s"/></par>
                      <par><text src="ksiegatrzecia.html#c3-p1"/><audio src="pan-tadeusz-ksiega-03.mp3" clip-begin="npt=2.000s" clip-end="npt=8.000s"/></par>
                    </body></smil>
                """.trimIndent(),
                "Daisy_202_audioFullText/first.smil" to """
                    <smil><body>
                      <par><text src="ksiegapierwsza.html#c1-title"/><audio src="pan-tadeusz-ksieg000b.mp3" clip-begin="npt=0.000s" clip-end="npt=2.000s"/></par>
                      <par><text src="ksiegapierwsza.html#c1-p1"/><audio src="pan-tadeusz-ksieg000b.mp3" clip-begin="npt=2.000s" clip-end="npt=8.000s"/></par>
                    </body></smil>
                """.trimIndent(),
                "Daisy_202_audioFullText/second.smil" to """
                    <smil><body>
                      <par><text src="ksiegadruga.html#c2-title"/><audio src="pan-tadeusz-ksiega-02.mp3" clip-begin="npt=0.000s" clip-end="npt=2.000s"/></par>
                      <par><text src="ksiegadruga.html#c2-p1"/><audio src="pan-tadeusz-ksiega-02.mp3" clip-begin="npt=2.000s" clip-end="npt=8.000s"/></par>
                    </body></smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz, czyli ostatni zajazd na Litwie",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            daisyUrl = "https://example.com/pan-tadeusz.daisy.zip",
            chapters = listOf(
                AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1),
                AudioBookChapter(id = "pan-2", title = "Księga druga. Zamek", number = 2),
                AudioBookChapter(id = "pan-3", title = "Księga trzecia. Umizgi", number = 3),
            ),
        )

        val readAlong = parser.parse(book, daisy)

        assertTrue(readAlong.hasTimedSync)
        assertEquals(listOf("pan-1", "pan-2", "pan-3"), readAlong.chapters.map { it.chapterId })
        assertTrue(readAlong.chapters[0].fullText.contains("Litwo! Ojczyzno moja"))
        assertTrue(readAlong.chapters[1].fullText.contains("Kto z nas tych lat nie pomni"))
        assertTrue(readAlong.chapters[2].fullText.contains("Hrabia wracał do siebie"))
    }

    @Test
    fun parse_daisyChapterTenDoesNotMatchChapterOneByDigitPrefix() {
        val daisy = testZip(
            suffix = ".zip",
            entries = linkedMapOf(
                "Daisy_202_audioFullText/ksiegadziesiata.html" to """
                    <html><body>
                      <h2 id="c10-title">Księga dziesiąta</h2>
                      <p id="c10-p1">Emigracja. Jacek.</p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/ksiegapierwsza.html" to """
                    <html><body>
                      <h2 id="c1-title">Księga pierwsza</h2>
                      <p id="c1-p1">Litwo! Ojczyzno moja!</p>
                    </body></html>
                """.trimIndent(),
                "Daisy_202_audioFullText/tenth.smil" to """
                    <smil><body>
                      <par><text src="ksiegadziesiata.html#c10-title"/><audio src="pan-tadeusz-ksiega-10.mp3" clip-begin="npt=0.000s" clip-end="npt=2.000s"/></par>
                      <par><text src="ksiegadziesiata.html#c10-p1"/><audio src="pan-tadeusz-ksiega-10.mp3" clip-begin="npt=2.000s" clip-end="npt=8.000s"/></par>
                    </body></smil>
                """.trimIndent(),
                "Daisy_202_audioFullText/first.smil" to """
                    <smil><body>
                      <par><text src="ksiegapierwsza.html#c1-title"/><audio src="pan-tadeusz-ksieg000b.mp3" clip-begin="npt=0.000s" clip-end="npt=2.000s"/></par>
                      <par><text src="ksiegapierwsza.html#c1-p1"/><audio src="pan-tadeusz-ksieg000b.mp3" clip-begin="npt=2.000s" clip-end="npt=8.000s"/></par>
                    </body></smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz, czyli ostatni zajazd na Litwie",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            daisyUrl = "https://example.com/pan-tadeusz.daisy.zip",
            chapters = listOf(
                AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1),
                AudioBookChapter(id = "pan-10", title = "Księga dziesiąta. Emigracja. Jacek.", number = 10),
            ),
        )

        val readAlong = parser.parse(book, daisy)

        assertEquals("pan-1", readAlong.chapters[0].chapterId)
        assertTrue(readAlong.chapters[0].fullText.contains("Litwo! Ojczyzno moja"))
        assertEquals("pan-10", readAlong.chapters[1].chapterId)
        assertTrue(readAlong.chapters[1].fullText.contains("Emigracja. Jacek."))
    }

    @Test
    fun parse_opensOrdinaryEpubAsStaticTextWhenSmilIsMissing() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/chapter.xhtml" to """
                    <html><body>
                      <p>Antek urodził się we wsi nad Wisłą.</p>
                      <p>Wieś leżała w niewielkiej dolinie.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/antek.epub",
            chapters = listOf(AudioBookChapter(id = "antek-1", title = "Antek", number = 1)),
        )

        val readAlong = parser.parse(book, epub)

        assertFalse(readAlong.hasTimedSync)
        assertEquals(1, readAlong.chapters.size)
        assertTrue(readAlong.chapters.single().fullText.contains("Antek urodził się"))
    }

    @Test
    fun parse_staticEpubDropsRepeatedBookTitleHeadings() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/chapter1.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h1>Anielka</h1>
                        <h2>Rozdział pierwszy</h2>
                        <p>Pierwszy akapit.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/chapter2.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h1>Anielka</h1>
                        <h2>Rozdział drugi</h2>
                        <p>Drugi akapit.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-anielka",
            title = "Anielka",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/anielka.epub",
            chapters = listOf(
                AudioBookChapter(id = "anielka-1", title = "Rozdział pierwszy", number = 1),
                AudioBookChapter(id = "anielka-2", title = "Rozdział drugi", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertFalse(readAlong.hasTimedSync)
        assertEquals("Rozdział pierwszy", readAlong.chapters[0].segments[0].text)
        assertEquals("Rozdział drugi", readAlong.chapters[1].segments[0].text)
        assertEquals(
            "Rozdział pierwszy\n\nPierwszy akapit.",
            readAlong.chapters[0].fullText,
        )
        assertEquals("Rozdział drugi\n\nDrugi akapit.", readAlong.chapters[1].fullText)
    }

    @Test
    fun parse_staticEpubTargetsAnielkaTocAtReadableChapters() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="part1" href="part1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="part2" href="part2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="part3" href="part3.xhtml" media-type="application/xhtml+xml"/>
                        <item id="part4" href="part4.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="part1"/>
                        <itemref idref="part2"/>
                        <itemref idref="part3"/>
                        <itemref idref="part4"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol>
                          <li><a href="title.xhtml">Strona tytułowa</a></li>
                          <li><a href="nav.xhtml">Spis treści</a></li>
                          <li><a href="part1.xhtml">Początek utworu</a></li>
                          <li><a href="part2.xhtml">Rozdział pierwszy. Autor dokonywa przeglądu osób</a></li>
                          <li><a href="part3.xhtml">Rozdział drugi. Czytelnik bliżej poznaje bohaterkę</a></li>
                          <li><a href="part4.xhtml">Rozdział trzeci. W którym jest mowa o medycynie</a></li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/title.xhtml" to "<html><body><h1>Anielka</h1><p>Ta lektura jest dostępna na wolnelektury.pl.</p></body></html>",
                "EPUB/part1.xhtml" to "<html><body><div id=\"book-text\"><h2 class=\"intitle\">Anielka</h2></div></body></html>",
                "EPUB/part2.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Rozdział pierwszy. Autor dokonywa przeglądu osób</h2>
                        <p>Anielka jest piękną dziewczyną.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/part3.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Rozdział drugi. Czytelnik bliżej poznaje bohaterkę</h2>
                        <p>Ktokolwiek spożywał papierowe owoce.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/part4.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Rozdział trzeci. W którym jest mowa o medycynie</h2>
                        <p>Minąwszy dwa pokoje.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-anielka",
            title = "Anielka",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/prus-anielka.epub",
            chapters = listOf(
                AudioBookChapter(id = "anielka-1", title = "Rozdział pierwszy. Autor dokonywa przeglądu osób", number = 1),
                AudioBookChapter(id = "anielka-2", title = "Rozdział drugi. Czytelnik bliżej poznaje bohaterkę", number = 2),
                AudioBookChapter(id = "anielka-3", title = "Rozdział trzeci. W którym jest mowa o medycynie", number = 3),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val tocByTitle = readAlong.tableOfContents.associateBy { it.title }

        assertFalse(readAlong.hasTimedSync)
        assertEquals(3, readAlong.chapters.size)
        assertTrue(readAlong.chapters.none { chapter -> chapter.fullText.startsWith("Anielka\n\n") })
        assertEquals("anielka-1", tocByTitle["Początek utworu"]?.chapterId)
        assertEquals(readAlong.chapters[0].segments.first().id, tocByTitle["Początek utworu"]?.segmentId)
        assertEquals("anielka-1", tocByTitle["Rozdział pierwszy. Autor dokonywa przeglądu osób"]?.chapterId)
        assertEquals("anielka-2", tocByTitle["Rozdział drugi. Czytelnik bliżej poznaje bohaterkę"]?.chapterId)
        assertEquals("anielka-3", tocByTitle["Rozdział trzeci. W którym jest mowa o medycynie"]?.chapterId)

        val partial = parser.parse(
            book = book,
            readAlongFile = epub,
            preferredChapterId = "anielka-2",
            onlyPreferredChapter = true,
        )

        assertTrue(partial.isPartial)
        assertEquals(listOf("Rozdział drugi. Czytelnik bliżej poznaje bohaterkę"), partial.tableOfContents.map { it.title })
        assertEquals(listOf("anielka-2"), partial.tableOfContents.map { it.chapterId })
    }

    @Test
    fun parse_preferredStaticChapterOnlyParsesMatchingSpineItemFirst() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter1"/>
                        <itemref idref="chapter2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/chapter1.xhtml" to """
                    <html><body>
                      <h2>Chapter one</h2>
                      <p>First static sentence.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/chapter2.xhtml" to """
                    <html><body>
                      <h2>Chapter two</h2>
                      <p>Second static sentence.</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-static",
            title = "Static",
            author = "Author",
            source = BookSource.WolneLektury,
            epubUrl = "https://example.com/static.epub",
            chapters = listOf(
                AudioBookChapter(id = "chapter-1", title = "Chapter one", number = 1),
                AudioBookChapter(id = "chapter-2", title = "Chapter two", number = 2),
            ),
        )

        val readAlong = parser.parse(
            book = book,
            readAlongFile = epub,
            preferredChapterId = "chapter-2",
            onlyPreferredChapter = true,
        )

        assertFalse(readAlong.hasTimedSync)
        assertTrue(readAlong.isPartial)
        assertEquals(listOf("chapter-2"), readAlong.chapters.map { it.chapterId })
        assertTrue(readAlong.chapters.single().fullText.contains("Second static sentence"))
        assertFalse(readAlong.chapters.single().fullText.contains("First static sentence"))
    }

    @Test
    fun parse_staticEpubSkipsWolneFrontMatterAndContainerDuplicates() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/title.xhtml" to """
                    <html><body>
                      <div class="title-page">
                        <p>Ta lektura, podobnie jak tysiące innych, jest dostępna on-line na stronie wolnelektury.pl.</p>
                        <p>ISBN-978-83-288-3719-5</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body><nav><ol><li>Spis treści</li></ol></nav></body></html>
                """.trimIndent(),
                "EPUB/chapter.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2>Rozdział pierwszy</h2>
                        <p>Antek urodził się we wsi nad Wisłą.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/antek.epub",
            chapters = listOf(AudioBookChapter(id = "antek-1", title = "Antek", number = 1)),
        )

        val readAlong = parser.parse(book, epub)
        val text = readAlong.chapters.single().fullText

        assertFalse(readAlong.hasTimedSync)
        assertFalse(text.contains("Ta lektura"))
        assertFalse(text.contains("ISBN"))
        assertEquals(1, text.occurrencesOf("Antek urodził się"))
        assertTrue(text.contains("Rozdział pierwszy"))
    }

    @Test
    fun parse_staticEpubKeepsInteractiveTocAndReaderFormatting() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="style" href="style.css" media-type="text/css"/>
                        <item id="start" href="part1.xhtml" media-type="application/xhtml+xml"/>
	                        <item id="chapter1" href="part2.xhtml" media-type="application/xhtml+xml"/>
	                        <item id="fund" href="fund1.xhtml" media-type="application/xhtml+xml"/>
	                        <item id="chapter2" href="part3.xhtml" media-type="application/xhtml+xml"/>
	                        <item id="annotations" href="annotations.xhtml" media-type="application/xhtml+xml"/>
	                        <item id="last" href="last.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="start"/>
                        <itemref idref="chapter1"/>
                        <itemref idref="fund"/>
                        <itemref idref="chapter2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/style.css" to """
                    .h3 {
                      font-size: 1.5em;
                      font-weight: normal;
                    }
                    .paragraph {
                      text-align: justify;
                      text-indent: 1.2em;
                    }
                    em.book-title {
                      font-style: italic;
                    }
                """.trimIndent(),
                "EPUB/title.xhtml" to """
                    <html><body>
                      <p>Ta lektura, podobnie jak tysiące innych, jest dostępna on-line na stronie wolnelektury.pl.</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <h2>Jaszczur</h2>
                        <ol>
                          <li><a href="title.xhtml">Strona tytułowa</a></li>
                          <li><a href="nav.xhtml">Spis treści</a></li>
                          <li><a href="part1.xhtml">Początek utworu</a></li>
	                          <li><a href="part2.xhtml">Od tłumacza</a></li>
	                          <li><a href="part3.xhtml">I. Talizman</a></li>
	                          <li><a href="annotations.xhtml">Przypisy</a></li>
	                          <li><a href="fund1.xhtml">Wesprzyj Wolne Lektury</a></li>
	                          <li><a href="last.xhtml">Strona redakcyjna</a></li>
	                        </ol>
	                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/part1.xhtml" to """
                    <html><body><div id="book-text"><h2 class="intitle">Jaszczur</h2></div></body></html>
                """.trimIndent(),
                "EPUB/part2.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Od tłumacza</h2>
                        <p class="paragraph"><em class="book-title">Jaszczur</em> (La peau de Chagrin), pisany w latach 1830-1831, ukazał się w całości w sierpniu 1831. Powieść ta należy tedy do pierwszej epoki twórczości <strong>Balzaca</strong>, pisał ją tuż po sukcesach odniesionych Fizjologią małżeństwa i Szuanami.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/fund1.xhtml" to """
                    <html><body>
                      <p>Wolne Lektury nie mają stałego finansowania — działamy dzięki społeczności.</p>
                      <p>wolnelektury.pl/pomagam/</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/part3.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">I. Talizman</h2>
                        <p class="paragraph">Młody człowiek wszedł do Palais-Royal.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-jaszczur",
            title = "Jaszczur",
            author = "Honoré de Balzac",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/balzac-komedia-ludzka-jaszczur.epub",
            chapters = listOf(
                AudioBookChapter(id = "translator", title = "Od tłumacza", number = 1),
                AudioBookChapter(id = "talizman", title = "I. Talizman", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val allText = readAlong.chapters.joinToString("\n") { it.fullText }

        assertFalse(readAlong.hasTimedSync)
        assertEquals(
            listOf(
                "Strona tytułowa",
                "Spis treści",
                "Początek utworu",
                "Od tłumacza",
                "I. Talizman",
                "Przypisy",
                "Wesprzyj Wolne Lektury",
                "Strona redakcyjna",
            ),
            readAlong.tableOfContents.map { it.title },
        )
        assertEquals(
            listOf(null, null, "translator", "translator", "talizman", null, null, null),
            readAlong.tableOfContents.map { it.chapterId },
        )
        assertFalse(allText.contains("wolnelektury.pl/pomagam"))
        assertFalse(allText.contains("Wolne Lektury nie mają stałego finansowania"))
        assertFalse(readAlong.chapters.any { it.fullText.trim() == "Jaszczur" })
        assertEquals(ReadAlongSegmentRole.Heading2, readAlong.chapters.first().segments.first().role)
        assertEquals("Od tłumacza", readAlong.chapters.first().segments[0].text)
        assertEquals(ReadAlongFontWeight.Normal, readAlong.chapters.first().segments[0].sourceStyle.fontWeight)
        assertEquals(1.5f, readAlong.chapters.first().segments[0].sourceStyle.textSizeMultiplier)
        assertEquals(
            "Od tłumacza\n\nJaszczur (La peau de Chagrin), pisany w latach 1830-1831, ukazał się w całości w sierpniu 1831. Powieść ta należy tedy do pierwszej epoki twórczości Balzaca, pisał ją tuż po sukcesach odniesionych Fizjologią małżeństwa i Szuanami.",
            readAlong.chapters.first().fullText,
        )
        val paragraph = readAlong.chapters.first().segments[1]
        assertFalse(paragraph.firstLineIndent)
        assertEquals(ReadAlongTextAlign.Justify, paragraph.sourceStyle.textAlign)
        assertEquals("Jaszczur", paragraph.text.substring(paragraph.sourceSpans[0].start, paragraph.sourceSpans[0].end))
        assertEquals(ReadAlongFontStyle.Italic, paragraph.sourceSpans[0].style.fontStyle)
        assertEquals("Balzaca", paragraph.text.substring(paragraph.sourceSpans[1].start, paragraph.sourceSpans[1].end))
        assertEquals(ReadAlongFontWeight.Bold, paragraph.sourceSpans[1].style.fontWeight)
    }

    @Test
    fun parse_audioEpubDoesNotIndentEveryTimedSentenceInParagraph() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="style" href="style.css" media-type="text/css"/>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                        <item id="smil" href="chapter.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/style.css" to """
                    .paragraph {
                      text-indent: 1.2em;
                    }
                """.trimIndent(),
                "EPUB/chapter.xhtml" to """
                    <html><body>
                      <p id="p1" class="paragraph"><span class="sentence" id="s1">Pierwsze zdanie.</span> <span class="sentence" id="s2">Drugie zdanie.</span></p>
                    </body></html>
                """.trimIndent(),
                "EPUB/chapter.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="chapter.xhtml#s1"/>
                          <audio src="chapter.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                        <par>
                          <text src="chapter.xhtml#s2"/>
                          <audio src="chapter.mp3" clip-begin="npt=1.000s" clip-end="npt=2.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-timed-paragraph",
            title = "Timed paragraph",
            author = "Author",
            source = BookSource.WolneLektury,
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter",
                    number = 1,
                    listenUrl = "https://example.com/chapter.mp3",
                ),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val segments = readAlong.chapters.single().segments

        assertEquals(listOf("Pierwsze zdanie.", "Drugie zdanie."), segments.map { it.text })
        assertTrue(segments[0].firstLineIndent)
        assertFalse(segments[1].firstLineIndent)
        assertEquals(segments[0].textBlockRef, segments[1].textBlockRef)
    }

    @Test
    fun parse_audioEpubKeepsTimedHeadingStyles() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="style" href="style.css" media-type="text/css"/>
                        <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                        <item id="smil" href="audio.smil" media-type="application/smil+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="chapter"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/style.css" to """
                    .h2 {
                      font-size: 2em;
                      font-weight: bold;
                    }
                    .h3 {
                      font-size: 1.5em;
                      font-weight: normal;
                    }
                    .paragraph {
                      text-indent: 1.2em;
                    }
                """.trimIndent(),
                "EPUB/chapter.xhtml" to """
                    <html><body>
                      <h2 class="h2" id="sec1">Księga pierwsza</h2>
                      <h2 class="h3" id="sec2">Gospodarstwo</h2>
                      <p class="paragraph" id="sec3">Litwo! Ojczyzno moja!</p>
                    </body></html>
                """.trimIndent(),
                "EPUB/audio.smil" to """
                    <smil>
                      <body>
                        <par>
                          <text src="chapter.xhtml#sec1"/>
                          <audio src="book0.mp3" clip-begin="npt=0.000s" clip-end="npt=1.000s"/>
                        </par>
                        <par>
                          <text src="chapter.xhtml#sec2"/>
                          <audio src="book0.mp3" clip-begin="npt=1.000s" clip-end="npt=2.000s"/>
                        </par>
                        <par>
                          <text src="chapter.xhtml#sec3"/>
                          <audio src="book0.mp3" clip-begin="npt=2.000s" clip-end="npt=3.000s"/>
                        </par>
                      </body>
                    </smil>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-timed-heading",
            title = "Timed heading",
            author = "Author",
            source = BookSource.WolneLektury,
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Księga pierwsza. Gospodarstwo",
                    number = 1,
                    listenUrl = "https://example.com/book0.mp3",
                ),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val segments = readAlong.chapters.single().segments

        assertEquals(ReadAlongSegmentRole.Heading1, segments[0].role)
        assertEquals(ReadAlongFontWeight.Bold, segments[0].sourceStyle.fontWeight)
        assertEquals(2f, segments[0].sourceStyle.textSizeMultiplier)
        assertEquals(ReadAlongSegmentRole.Heading2, segments[1].role)
        assertEquals(ReadAlongFontWeight.Normal, segments[1].sourceStyle.fontWeight)
        assertEquals(1.5f, segments[1].sourceStyle.textSizeMultiplier)
        assertEquals(ReadAlongSegmentRole.Paragraph, segments[2].role)
    }

    @Test
    fun parse_staticEpubMergesHeadingOnlyBookDivisionsWithBodyChaptersForTocTargets() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="start" href="part1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="book1" href="part2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="body1" href="part3.xhtml" media-type="application/xhtml+xml"/>
                        <item id="book2" href="part4.xhtml" media-type="application/xhtml+xml"/>
                        <item id="body2" href="part5.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="start"/>
                        <itemref idref="book1"/>
                        <itemref idref="body1"/>
                        <itemref idref="book2"/>
                        <itemref idref="body2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/title.xhtml" to """
                    <html><body><p>Ta lektura jest dostępna na wolnelektury.pl.</p></body></html>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <ol>
                          <li><a href="title.xhtml">Strona tytułowa</a></li>
                          <li><a href="nav.xhtml">Spis treści</a></li>
                          <li><a href="part1.xhtml">Początek utworu</a></li>
                          <li>
                            <a href="part2.xhtml">Księga pierwsza</a>
                            <ol><li><a href="part3.xhtml">Gospodarstwo</a></li></ol>
                          </li>
                          <li>
                            <a href="part4.xhtml">Księga druga</a>
                            <ol><li><a href="part5.xhtml">Zamek</a></li></ol>
                          </li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/part1.xhtml" to """
                    <html><body><div id="book-text"><h2 class="intitle">Pan Tadeusz</h2></div></body></html>
                """.trimIndent(),
                "EPUB/part2.xhtml" to """
                    <html><body><div id="book-text"><h2 class="h2">Księga pierwsza</h2></div></body></html>
                """.trimIndent(),
                "EPUB/part3.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Gospodarstwo</h2>
                        <p>Litwo! Ojczyzno moja!</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/part4.xhtml" to """
                    <html><body><div id="book-text"><h2 class="h2">Księga druga</h2></div></body></html>
                """.trimIndent(),
                "EPUB/part5.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2 class="h3">Zamek</h2>
                        <p>Kto z nas tych lat nie pomni?</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-pan-tadeusz",
            title = "Pan Tadeusz, czyli ostatni zajazd na Litwie",
            author = "Adam Mickiewicz",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/pan-tadeusz.epub",
            chapters = listOf(
                AudioBookChapter(id = "pan-1", title = "Księga pierwsza. Gospodarstwo", number = 1),
                AudioBookChapter(id = "pan-2", title = "Księga druga. Zamek", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)
        val chapterToc = readAlong.tableOfContents.filter { it.title.startsWith("Księga") }

        assertFalse(readAlong.hasTimedSync)
        assertEquals(listOf("pan-1", "pan-2"), readAlong.chapters.map { it.chapterId })
        assertEquals("Księga pierwsza. Gospodarstwo", readAlong.chapters[0].title)
        assertTrue(readAlong.chapters[0].fullText.startsWith("Księga pierwsza\n\nGospodarstwo"))
        assertEquals(ReadAlongSegmentRole.Heading1, readAlong.chapters[0].segments[0].role)
        assertEquals(ReadAlongSegmentRole.Heading2, readAlong.chapters[0].segments[1].role)
        assertEquals(
            listOf("Księga pierwsza. Gospodarstwo", "Księga druga. Zamek"),
            chapterToc.map { it.title },
        )
        assertEquals(listOf("pan-1", "pan-2"), chapterToc.map { it.chapterId })
        assertTrue(chapterToc.all { it.segmentId != null })
        assertFalse(chapterToc[0].segmentId == chapterToc[1].segmentId)
    }

    @Test
    fun parse_staticEpubKeepsAdjacentSourceTocEntriesWhenTheyMatchSameAudioChapter() {
        val epub = testEpub(
            mapOf(
                "META-INF/container.xml" to """
                    <container>
                      <rootfiles>
                        <rootfile full-path="EPUB/content.opf"/>
                      </rootfiles>
                    </container>
                """.trimIndent(),
                "EPUB/content.opf" to """
                    <package>
                      <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="translator" href="part2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="talizman" href="part3.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter2" href="part4.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="nav"/>
                        <itemref idref="translator"/>
                        <itemref idref="talizman"/>
                        <itemref idref="chapter2"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "EPUB/nav.xhtml" to """
                    <html><body>
                      <nav epub:type="toc" role="doc-toc">
                        <h2>Jaszczur</h2>
                        <ol>
                          <li><a href="part2.xhtml">Od tłumacza</a></li>
                          <li><a href="part3.xhtml">I. Talizman</a></li>
                          <li><a href="part4.xhtml">II. Kobieta bez serca</a></li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/part2.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2>Od tłumacza</h2>
                        <p>Przedmowa tłumacza.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/part3.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2>I. Talizman</h2>
                        <p>Młody człowiek wszedł do Palais-Royal.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
                "EPUB/part4.xhtml" to """
                    <html><body>
                      <div id="book-text">
                        <h2>II. Kobieta bez serca</h2>
                        <p>Kolejny rozdział.</p>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-jaszczur",
            title = "Jaszczur",
            author = "Honoré de Balzac",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/epub/balzac-komedia-ludzka-jaszczur.epub",
            chapters = listOf(
                AudioBookChapter(id = "audio-1", title = "Od tłumacza; I. Talizman (część 1.)", number = 1),
                AudioBookChapter(id = "audio-2", title = "II. Kobieta bez serca", number = 2),
            ),
        )

        val readAlong = parser.parse(book, epub)

        assertFalse(readAlong.hasTimedSync)
        assertEquals(
            listOf("Od tłumacza", "I. Talizman", "II. Kobieta bez serca"),
            readAlong.tableOfContents.map { it.title },
        )
        assertEquals(
            listOf("audio-1", "audio-1", "audio-2"),
            readAlong.tableOfContents.map { it.chapterId },
        )
        assertEquals(
            listOf("EPUB/part2.xhtml", "EPUB/part3.xhtml", "EPUB/part4.xhtml"),
            readAlong.tableOfContents.map { it.href },
        )
        assertEquals(3, readAlong.tableOfContents.mapNotNull { it.segmentId }.distinct().size)
    }

    @Test
    fun search_findsAccentInsensitiveContextAndSelectionSegment() {
        val chapter = buildReadAlongChapter(
            AudioBookChapter(id = "c1", title = "Chapter", number = 1),
            displayIndex = 1,
            segments = listOf(
                ReadAlongSegment(
                    id = "s1",
                    chapterId = "c1",
                    text = "Zażółć gęślą jaźń.",
                    textRef = "part.xhtml#s1",
                    audioRef = "book0.mp3",
                    clipBeginMs = 2_000L,
                    clipEndMs = 4_000L,
                ),
            ),
        )
        val book = ReadAlongBook("book", "Book", listOf(chapter), hasTimedSync = true)

        val hits = book.search("gesla")

        assertEquals(1, hits.size)
        assertEquals(2_000L, hits.single().timestampMs)
        assertTrue(hits.single().sentence.contains("gęślą"))
        assertFalse(chapter.segmentForSelection(0, 5) == null)
    }

    private fun testEpub(entries: Map<String, String>): File {
        return testZip(suffix = ".epub", entries = entries)
    }

    private fun testZip(suffix: String, entries: Map<String, String>): File {
        val file = File.createTempFile("read-along", suffix)
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun String.occurrencesOf(value: String): Int =
        split(value).size - 1
}
