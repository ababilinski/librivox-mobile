package com.librivox.mobile.ui.detail

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.DownloadState
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

    @Test
    fun canDeleteDownloadsAfterLibraryRemovalTracksDownloadCleanupEligibility() {
        val remoteDownloaded = AudioBook(
            id = "remote-downloaded",
            title = "Remote Downloaded",
            author = "Reader",
            source = BookSource.LibriVox,
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter 1",
                    number = 1,
                    localFileName = "offline/remote-downloaded/chapter-1.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )
        val remoteActive = remoteDownloaded.copy(
            id = "remote-active",
            chapters = listOf(
                remoteDownloaded.chapters.single().copy(
                    localFileName = null,
                    downloadState = DownloadState.Downloading,
                ),
            ),
        )
        val localAsset = remoteDownloaded.copy(
            id = "local-asset",
            source = BookSource.LocalAsset,
            chapters = listOf(
                remoteDownloaded.chapters.single().copy(
                    assetFileName = "cathedral.mp3",
                    localFileName = null,
                ),
            ),
        )

        assertTrue(remoteDownloaded.canDeleteDownloadsAfterLibraryRemoval())
        assertTrue(remoteActive.canDeleteDownloadsAfterLibraryRemoval())
        assertFalse(localAsset.canDeleteDownloadsAfterLibraryRemoval())
    }
}
