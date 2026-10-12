package com.librivox.mobile.ui.onboarding

import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOnboardingScreenTest {
    @Test
    fun normalizeOnboardingSources_allowsEmptySourceSelection() {
        assertEquals(
            emptySet<CatalogSourcePreference>(),
            normalizeOnboardingSources(emptySet()),
        )
    }

    @Test
    fun normalizeOnboardingSources_keepsSelectablePublicSources() {
        assertEquals(
            setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury),
            normalizeOnboardingSources(
                setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury),
            ),
        )
    }

    @Test
    fun normalizeOnboardingLanguages_keepsPolishWhenWolneIsSelected() {
        val normalized = normalizeOnboardingLanguages(
            languages = setOf(BookLanguagePreference.English, BookLanguagePreference.Polish),
            sources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury),
        )

        assertEquals(setOf(BookLanguagePreference.English, BookLanguagePreference.Polish), normalized)
    }

    @Test
    fun isLanguageAvailableForOnboarding_tracksSelectedSources() {
        val librivoxSources = setOf(CatalogSourcePreference.LibriVox)
        val wolneSources = setOf(CatalogSourcePreference.WolneLektury)
        val mixedSources = setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.WolneLektury)

        assertTrue(isLanguageAvailableForOnboarding(BookLanguagePreference.English, librivoxSources))
        assertFalse(isLanguageAvailableForOnboarding(BookLanguagePreference.Polish, librivoxSources))
        assertFalse(isLanguageAvailableForOnboarding(BookLanguagePreference.English, wolneSources))
        assertTrue(isLanguageAvailableForOnboarding(BookLanguagePreference.Polish, wolneSources))
        assertTrue(isLanguageAvailableForOnboarding(BookLanguagePreference.English, mixedSources))
        assertTrue(isLanguageAvailableForOnboarding(BookLanguagePreference.Polish, mixedSources))
        assertFalse(isLanguageAvailableForOnboarding(BookLanguagePreference.All, mixedSources))
    }

    @Test
    fun normalizeOnboardingLanguages_allowsEmptyLanguageSelection() {
        val normalized = normalizeOnboardingLanguages(
            languages = emptySet(),
            sources = setOf(CatalogSourcePreference.LibriVox),
        )

        assertTrue(normalized.isEmpty())
    }

    @Test
    fun availableOnboardingLanguages_excludesPolish() {
        val languages = availableOnboardingLanguages(
            setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.Lit2Go),
        )

        assertTrue(BookLanguagePreference.English in languages)
        assertFalse(BookLanguagePreference.Polish in languages)
    }

    @Test
    fun availableOnboardingLanguages_includesPolishForWolneSourceSelection() {
        val languages = availableOnboardingLanguages(setOf(CatalogSourcePreference.WolneLektury))

        assertEquals(listOf(BookLanguagePreference.Polish), languages)
    }

    @Test
    fun availableOnboardingLanguages_includesAllLanguageTypesForMixedSources() {
        val languages = availableOnboardingLanguages(
            setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.WolneLektury),
        )

        assertTrue(BookLanguagePreference.English in languages)
        assertTrue(BookLanguagePreference.Polish in languages)
    }

    @Test
    fun onboardingCarouselTitles_areBundledAndUnique() {
        assertTrue(onboardingCarouselTitles.size >= 6)
        assertEquals(
            onboardingCarouselTitles.size,
            onboardingCarouselTitles.map { it.title }.distinct().size,
        )
        assertTrue(onboardingCarouselTitles.all { it.coverRes != 0 })
    }

    @Test
    fun onboardingCarouselTitles_includePopularClassics() {
        val titles = onboardingCarouselTitles.map { it.title }

        assertTrue("Pride and Prejudice" in titles)
        assertTrue("Moby Dick" in titles)
        assertTrue("The Adventures of Sherlock Holmes" in titles)
    }
}
