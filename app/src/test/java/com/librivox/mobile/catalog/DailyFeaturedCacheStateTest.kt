package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DailyFeaturedCacheStateTest {

    @Test
    fun selection_isStableForSameDayAndSource() {
        val candidates = candidates()
        val first = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.LibriVoxOnly,
            dayKey = 20_000L,
            limit = 8,
        )
        val second = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.LibriVoxOnly,
            dayKey = 20_000L,
            limit = 8,
        )

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun selection_changesWhenDayChanges() {
        val candidates = candidates()
        val today = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.LibriVoxOnly,
            dayKey = 20_000L,
            limit = 8,
        )
        val tomorrow = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.LibriVoxOnly,
            dayKey = 20_001L,
            limit = 8,
        )

        assertFalse(today.map { it.id } == tomorrow.map { it.id })
    }

    @Test
    fun selection_changesWhenSourceSelectionChanges() {
        val candidates = candidates()
        val librivox = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.LibriVoxOnly,
            dayKey = 20_000L,
            limit = 8,
        )
        val wolneLektury = selectDailyFeaturedAudiobooks(
            candidates = candidates,
            bookSource = BookSourcePreference.WolneLekturyOnly,
            dayKey = 20_000L,
            limit = 8,
        )

        assertFalse(librivox.map { it.id } == wolneLektury.map { it.id })
    }

    @Test
    fun selection_canPreserveSourceOrderForCuratedShelves() {
        val selected = selectDailyFeaturedAudiobooks(
            candidates = candidates(),
            bookSource = BookSourcePreference.WolneLekturyOnly,
            dayKey = 20_000L,
            limit = 5,
            preserveSourceOrder = true,
        )

        assertEquals(
            listOf("book-1", "book-2", "book-3", "book-4", "book-5"),
            selected.map { it.id },
        )
    }

    @Test
    fun currentDayKey_usesLocalCalendarDay() {
        val millis = 1_704_067_199_000L

        assertEquals(
            19_722L,
            currentDailyFeaturedDayKey(
                nowMillis = millis,
                zoneId = ZoneId.of("America/Chicago"),
            ),
        )
    }

    @Test
    fun featuredCatalogSource_usesPreferredSelectedSource() {
        assertEquals(null, BookSourcePreference.None.featuredCatalogSource())
        assertEquals(CatalogSourcePreference.Lit2Go, BookSourcePreference.LibriVoxAndLit2Go.featuredCatalogSource())
        assertEquals(CatalogSourcePreference.Lit2Go, BookSourcePreference.All.featuredCatalogSource())
        assertEquals(CatalogSourcePreference.LibriVox, BookSourcePreference.LibriVoxOnly.featuredCatalogSource())
        assertEquals(CatalogSourcePreference.Gutendex, BookSourcePreference.GutendexOnly.featuredCatalogSource())
        assertEquals(CatalogSourcePreference.WolneLektury, BookSourcePreference.WolneLekturyOnly.featuredCatalogSource())
    }

    @Test
    fun catalogSourcePreference_asSingleSourcePreferenceUsesSelectableSources() {
        assertEquals(BookSourcePreference.LibriVoxOnly, CatalogSourcePreference.LibriVox.asSingleSourcePreference())
        assertEquals(BookSourcePreference.Lit2GoOnly, CatalogSourcePreference.Lit2Go.asSingleSourcePreference())
        assertEquals(BookSourcePreference.WolneLekturyOnly, CatalogSourcePreference.WolneLektury.asSingleSourcePreference())
        assertEquals(BookSourcePreference.GutendexOnly, CatalogSourcePreference.Gutendex.asSingleSourcePreference())
    }

    private fun candidates(): List<AudioBook> =
        (1..20).map { index ->
            AudioBook(
                id = "book-$index",
                title = "Book $index",
                author = "Author",
            )
        }
}
