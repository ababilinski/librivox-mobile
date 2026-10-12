package com.librivox.mobile.catalog

import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryState
import com.librivox.mobile.model.chapterIndexOrZero
import com.librivox.mobile.model.mergedWithIncoming
import com.librivox.mobile.model.sanitized
import org.junit.Assert.assertEquals
import org.junit.Test

class WolneVolumeOrderTest {
    private val source = WolneLekturyCatalogSource()

    // Wolne Lektury API responses captured 2026-10-11: children arrive II, I.
    private fun lalka() = source.parseBookDetail(
        slug = "lalka",
        rawJson = fixture("lalka"),
        childDetailJsons = listOf(fixture("lalka-tom-drugi"), fixture("lalka-tom-pierwszy")),
    )

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/wolnelektury/$name.json")).readText()

    @Test fun realLalkaResponseKeepsAllRecordingsInReadingOrder() {
        val book = lalka()
        val expectedIds = (1..20).map { "lalka-tom-pierwszy-$it" } +
            (1..19).map { "lalka-tom-drugi-$it" }
        assertEquals(expectedIds, book.chapters.map { it.id })
        assertEquals((1..39).toList(), book.chapters.map { it.number })
        assertEquals("Tom I, Rozdział I", book.chapters.first().title)
        assertEquals("Tom I, Rozdział XX", book.chapters[19].title)
        assertEquals("Tom II, Rozdział I", book.chapters[20].title)
        assertEquals("Tom II, Uzupełnienie", book.chapters.last().title)
    }

    @Test fun savedLalkaRepairsOfflineWithoutChangingChapterIdentityOrDownloadState() {
        val fresh = lalka()
        val old = fresh.copy(chapters = (fresh.chapters.drop(20) + fresh.chapters.take(20))
            .mapIndexed { index, chapter -> chapter.copy(
                number = index + 1,
                title = "${chapter.title} — Written heading",
                durationSeconds = 1234L + index,
                localFileName = "offline/${chapter.id}.mp3",
                downloadState = DownloadState.Downloaded,
            ) }, writtenChapterTitlesChecked = true)
        val state = LibraryState(books = listOf(old)).sanitized()
        val repaired = checkNotNull(state.bookById(old.id))
        assertEquals(fresh.chapters.map { it.id }, repaired.chapters.map { it.id })
        assertEquals((1..39).toList(), repaired.chapters.map { it.number })
        repaired.chapters.forEach { chapter ->
            val original = old.chapters.single { it.id == chapter.id }
            assertEquals(original, chapter.copy(number = original.number))
        }
        // Resume follows the saved ID to its new slot, rather than resuming another recording.
        assertEquals(20, repaired.chapterIndexOrZero("lalka-tom-drugi-1"))
        assertEquals(state, state.sanitized())
        val refreshed = listOf(old).mergedWithIncoming(listOf(fresh)).single()
        assertEquals(fresh.chapters.map { it.id }, refreshed.chapters.map { it.id })
        assertEquals(old.chapters.associate { it.id to it.localFileName },
            refreshed.chapters.associate { it.id to it.localFileName })
    }

    @Test fun volumeNumbersSortNumericallyAndPreserveOrderInsideEachVolume() {
        val titles = listOf("Tom X, Supplement", "Tom II, B", "Tom IX, A", "Tom II, A")
        assertEquals(listOf(titles[1], titles[3], titles[2], titles[0]),
            titles.orderedByWolneVolume { it })
        assertEquals(listOf("Tom 2", "Tom 9", "Tom 10"),
            listOf("Tom 10", "Tom 2", "Tom 9").orderedByWolneVolume { it })
        assertEquals(listOf("Tom pierwszy", "Tom drugi", "Tom ósmy"),
            listOf("Tom ósmy", "Tom drugi", "Tom pierwszy").orderedByWolneVolume { it })
    }

    @Test fun unnumberedCollectionsAndOtherCatalogsKeepTheirOrder() {
        val titles = listOf("Tom II", "Introduction", "Tom I")
        assertEquals(titles, titles.orderedByWolneVolume { it })
        val poems = listOf("Zima", "Jesień", "Lato")
        assertEquals(poems, poems.orderedByWolneVolume { it })
        val reversed = lalka().let { it.copy(chapters = it.chapters.reversed(), source = BookSource.LibriVox) }
        assertEquals(reversed, reversed.withWolneVolumeOrder())
    }

    @Test fun missingChildSlugsRetainOriginalFallbackIdsWhenVolumesMove() {
        val book = source.parseBookDetail(
            slug = "collection", rawJson = """{"title":"Collection"}""",
            childDetailJsons = listOf(
                """{"title":"Tom II","media":[{"type":"mp3","url":"https://example.com/2.mp3"}]}""",
                """{"title":"Tom I","media":[{"type":"mp3","url":"https://example.com/1.mp3"}]}""",
            ),
        )
        assertEquals(listOf("Tom I", "Tom II"), book.chapters.map { it.title })
        assertEquals(listOf("collection-2-1", "collection-1-1"), book.chapters.map { it.id })
        assertEquals(listOf(1, 2), book.chapters.map { it.number })
    }
}
