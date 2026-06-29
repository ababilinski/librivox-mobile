package com.librivox.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourcePreferenceTest {
    @Test
    fun fromPreference_normalizesStoredSourceSelectionsToEnabledSources() {
        assertEquals(BookSourcePreference.LibriVoxOnly, BookSourcePreference.fromPreference(null))
        assertEquals(BookSourcePreference.All, BookSourcePreference.fromPreference("both"))
        assertEquals(BookSourcePreference.All, BookSourcePreference.fromPreference("all"))
        assertEquals(BookSourcePreference.LibriVoxOnly, BookSourcePreference.fromPreference("librivox"))
        assertEquals(BookSourcePreference.WolneLekturyOnly, BookSourcePreference.fromPreference("wolne_lektury"))
        assertEquals(BookSourcePreference.Lit2GoOnly, BookSourcePreference.fromPreference("lit2go"))
        assertEquals(BookSourcePreference.GutendexOnly, BookSourcePreference.fromPreference("gutendex"))
        assertEquals(BookSourcePreference.LibriVoxOnly, BookSourcePreference.fromPreference("unknown_legacy_source"))
        assertEquals(BookSourcePreference.None, BookSourcePreference.fromPreference("none"))
    }

    @Test
    fun fromEnabledSources_preservesSelectablePublicSources() {
        assertEquals(
            BookSourcePreference.LibriVoxAndWolneLektury,
            BookSourcePreference.fromEnabledSources(
                setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.WolneLektury),
            ),
        )
        assertEquals(
            BookSourcePreference.Lit2GoAndWolneLektury,
            BookSourcePreference.fromEnabledSources(
                setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury),
            ),
        )
        assertEquals(
            BookSourcePreference.LibriVoxAndLit2GoAndGutendex,
            BookSourcePreference.fromEnabledSources(
                setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.LibriVox, CatalogSourcePreference.Gutendex),
            ),
        )
        assertEquals(
            BookSourcePreference.AllWithGutendex,
            BookSourcePreference.fromEnabledSources(
                setOf(
                    CatalogSourcePreference.Lit2Go,
                    CatalogSourcePreference.LibriVox,
                    CatalogSourcePreference.WolneLektury,
                    CatalogSourcePreference.Gutendex,
                ),
            ),
        )
        assertEquals(
            BookSourcePreference.GutendexOnly,
            BookSourcePreference.fromEnabledSources(setOf(CatalogSourcePreference.Gutendex)),
        )
    }

    @Test
    fun selectableSources_includeLibriVoxAndOtherPublicSources() {
        assertEquals(
            listOf(
                CatalogSourcePreference.LibriVox,
                CatalogSourcePreference.Lit2Go,
                CatalogSourcePreference.Gutendex,
                CatalogSourcePreference.WolneLektury,
            ),
            BookSourcePreference.SelectableSources,
        )
    }

    @Test
    fun fromEnabledSources_allowsNoSourceSelection() {
        assertEquals(
            BookSourcePreference.None,
            BookSourcePreference.fromEnabledSources(emptySet()),
        )
    }

    @Test
    fun supportsPolish_tracksWolneLekturySelection() {
        assertFalse(BookSourcePreference.LibriVoxOnly.supportsPolish)
        assertFalse(BookSourcePreference.LibriVoxAndLit2Go.supportsPolish)
        assertTrue(BookSourcePreference.WolneLekturyOnly.supportsPolish)
        assertTrue(BookSourcePreference.All.supportsPolish)
    }

    @Test
    fun languageSelection_removesPolishForNonPolishSources() {
        val normalized = setOf(
            BookLanguagePreference.English,
            BookLanguagePreference.Polish,
        ).normalizeForCatalogSource(BookSourcePreference.LibriVoxOnly)

        assertEquals(setOf(BookLanguagePreference.English), normalized)
    }

    @Test
    fun languageSelection_keepsPolishWhenWolneLekturyIsSelected() {
        val normalized = setOf(BookLanguagePreference.Polish)
            .normalizeForCatalogSource(BookSourcePreference.WolneLekturyOnly)

        assertEquals(setOf(BookLanguagePreference.Polish), normalized)
    }

    @Test
    fun selectableLanguages_tracksSelectedSourceCapabilities() {
        assertEquals(
            listOf(BookLanguagePreference.Polish),
            BookSourcePreference.WolneLekturyOnly.selectableLanguages(),
        )
        assertFalse(BookLanguagePreference.Polish in BookSourcePreference.LibriVoxOnly.selectableLanguages())
        assertTrue(BookLanguagePreference.Polish in BookSourcePreference.AllWithGutendex.selectableLanguages())
        assertTrue(BookLanguagePreference.English in BookSourcePreference.AllWithGutendex.selectableLanguages())
    }

    @Test
    fun languageSelection_allMeansAllRemainingSupportedLanguages() {
        val normalized = setOf(BookLanguagePreference.All)
            .normalizeForCatalogSource(BookSourcePreference.AllWithGutendex)

        assertEquals(
            BookLanguagePreference.ConcreteLanguages.toSet(),
            normalized,
        )
        assertTrue(BookLanguagePreference.Polish in normalized)
        assertTrue(
            BookSourcePreference.AllWithGutendex
                .selectableLanguages()
                .any { it == BookLanguagePreference.Polish },
        )
    }

    @Test
    fun languagePreference_parsesAndSerializesWithPolish() {
        assertEquals(
            setOf(BookLanguagePreference.English, BookLanguagePreference.Polish),
            BookLanguagePreference.fromPreferences("english,polish"),
        )
        assertEquals(
            setOf(BookLanguagePreference.Polish),
            BookLanguagePreference.fromPreferences("polish"),
        )
        assertEquals(
            setOf(BookLanguagePreference.Polish),
            BookLanguagePreference.normalizeStored(setOf(BookLanguagePreference.Polish)),
        )

        val languages = setOf(BookLanguagePreference.English, BookLanguagePreference.Polish)
        val value = BookLanguagePreference.toPreferenceValue(languages)

        assertEquals(languages, BookLanguagePreference.fromPreferences(value))
    }
}
