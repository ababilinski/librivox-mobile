package com.librivox.mobile.ui.reader

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.readalong.WolneAudioEpubParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChapterNavigationTest {
    @Test
    fun anielkaChapterSheetButtonsJumpToMatchingChapterTextBlocks() {
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
                        <item id="part5" href="part5.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="title"/>
                        <itemref idref="nav"/>
                        <itemref idref="part1"/>
                        <itemref idref="part2"/>
                        <itemref idref="part3"/>
                        <itemref idref="part4"/>
                        <itemref idref="part5"/>
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
                          <li><a href="part5.xhtml">Rozdział czwarty. Dziedzic odbywa naradę</a></li>
                        </ol>
                      </nav>
                    </body></html>
                """.trimIndent(),
                "EPUB/title.xhtml" to "<html><body><h1>Anielka</h1></body></html>",
                "EPUB/part1.xhtml" to "<html><body><div id=\"book-text\"><h2 class=\"intitle\">Anielka</h2></div></body></html>",
                "EPUB/part2.xhtml" to chapterXhtml(
                    title = "Rozdział pierwszy. Autor dokonywa przeglądu osób",
                    body = "Anielka jest piękną dziewczyną.",
                ),
                "EPUB/part3.xhtml" to chapterXhtml(
                    title = "Rozdział drugi. Czytelnik bliżej poznaje bohaterkę",
                    body = "Ktokolwiek spożywał papierowe owoce.",
                ),
                "EPUB/part4.xhtml" to chapterXhtml(
                    title = "Rozdział trzeci. W którym jest mowa o medycynie",
                    body = "Minąwszy dwa pokoje.",
                ),
                "EPUB/part5.xhtml" to chapterXhtml(
                    title = "Rozdział czwarty. Dziedzic odbywa naradę",
                    body = "W półtorej godziny później przyjechał do domu dziedzic.",
                ),
            ),
        )
        val book = AudioBook(
            id = "wolnelektury-prus-anielka",
            title = "Anielka",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book/audio.epub/prus-anielka.epub",
            chapters = listOf(
                AudioBookChapter(id = "anielka-1", title = "Rozdział pierwszy. Autor dokonywa przeglądu osób", number = 1),
                AudioBookChapter(id = "anielka-2", title = "Rozdział drugi. Czytelnik bliżej poznaje bohaterkę", number = 2),
                AudioBookChapter(id = "anielka-3", title = "Rozdział trzeci. W którym jest mowa o medycynie", number = 3),
                AudioBookChapter(id = "anielka-4", title = "Rozdział czwarty. Dziedzic odbywa naradę", number = 4),
            ),
        )

        val readAlong = WolneAudioEpubParser().parse(book, epub)
        val pickerItems = readAlong.readerChapterPickerItems()
        val readerItems = readAlong.readerItems()
        val jumpIndexes = readerItems.readerTocJumpIndexes(leadingReaderItemCount = 1)
        val firstChapterButtonIndex = pickerItems[0].readerTocJumpListIndex(jumpIndexes)

        assertEquals(
            listOf(
                "Rozdział pierwszy. Autor dokonywa przeglądu osób",
                "Rozdział drugi. Czytelnik bliżej poznaje bohaterkę",
                "Rozdział trzeci. W którym jest mowa o medycynie",
                "Rozdział czwarty. Dziedzic odbywa naradę",
            ),
            pickerItems.take(4).map { it.title },
        )
        assertTrue(readAlong.chapters.none { chapter -> chapter.fullText.startsWith("Anielka\n\n") })
        pickerItems.take(4).forEachIndexed { index, item ->
            val chapterId = "anielka-${index + 1}"
            val expectedIndex = readerItems.firstTextBlockListIndexForChapter(
                chapterId = chapterId,
                leadingReaderItemCount = 1,
            )

            assertEquals(chapterId, item.chapterId)
            assertEquals(expectedIndex, item.readerTocJumpListIndex(jumpIndexes))
            if (index > 0) {
                assertNotEquals(firstChapterButtonIndex, item.readerTocJumpListIndex(jumpIndexes))
            }
        }
        assertFalse(pickerItems.take(4).any { it.title == "Początek utworu" })
    }

    private fun List<ReaderItem>.firstTextBlockListIndexForChapter(
        chapterId: String,
        leadingReaderItemCount: Int,
    ): Int {
        val itemIndex = indexOfFirst { item ->
            item is ReaderItem.TextBlock && item.chapter.chapterId == chapterId
        }
        assertTrue("Missing text block for $chapterId", itemIndex >= 0)
        return readerLazyListIndex(itemIndex, leadingReaderItemCount)
    }

    private fun chapterXhtml(title: String, body: String): String =
        """
            <html><body>
              <div id="book-text">
                <h2 class="h3">$title</h2>
                <p>$body</p>
              </div>
            </body></html>
        """.trimIndent()

    private fun testEpub(entries: Map<String, String>): File {
        val file = File.createTempFile("reader-nav-test", ".epub")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
