package com.librivox.mobile.ui.detail

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailTranslationMetadataTest {
    @Test
    fun wolneTranslationMetadataShowsPillAndTranslatedByCredit() {
        val book = AudioBook(
            id = "wolnelektury-studnia-i-wahadlo",
            title = "Studnia i wahadło",
            author = "Edgar Allan Poe",
            source = BookSource.WolneLektury,
            translators = listOf("Bolesław Leśmian"),
            translationMetadataChecked = true,
        )

        assertTrue(book.showTranslatedMetadataPill())
        assertEquals("Translated by Bolesław Leśmian", book.translatedByLabel())
    }

    @Test
    fun wolneTranslationMetadataShowsPillWhenOnlyOriginalLanguageIsKnown() {
        val book = AudioBook(
            id = "wolnelektury-hamlet",
            title = "Hamlet",
            author = "William Shakespeare",
            source = BookSource.WolneLektury,
            originalLanguage = "English",
            translationMetadataChecked = true,
        )

        assertTrue(book.showTranslatedMetadataPill())
        assertNull(book.translatedByLabel())
    }

    @Test
    fun translationMetadataPillIsHiddenWhenNoTranslationDataExists() {
        val book = AudioBook(
            id = "wolnelektury-zemsta",
            title = "Zemsta",
            author = "Aleksander Fredro",
            source = BookSource.WolneLektury,
            translationMetadataChecked = true,
        )

        assertFalse(book.showTranslatedMetadataPill())
        assertNull(book.translatedByLabel())
    }

    @Test
    fun translationMetadataPillStaysScopedToWolneLekturyMetadata() {
        val book = AudioBook(
            id = "gutenberg-123",
            title = "Hamlet",
            author = "William Shakespeare",
            source = BookSource.Gutendex,
            translators = listOf("Józef Paszkowski"),
        )

        assertFalse(book.showTranslatedMetadataPill())
        assertEquals("Translated by Józef Paszkowski", book.translatedByLabel())
    }
}
