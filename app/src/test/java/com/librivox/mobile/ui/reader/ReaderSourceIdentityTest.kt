package com.librivox.mobile.ui.reader

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.AudioBookReadAlongSource
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.DownloadState
import com.librivox.mobile.model.LibraryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderSourceIdentityTest {
    @Test fun playbackDurationAndLibraryMetadataUpdatesKeepLoadedText() {
        val initial = book()
        val updated = initial.copy(
            totalDurationSeconds = 600,
            isFavorite = true,
            libraryStatus = LibraryStatus.InLibrary,
            coverImageUrl = "https://example.com/cover.jpg",
            narrators = listOf("Narrator"),
            addedAtMillis = 1234,
            chapters = initial.chapters.map {
                it.copy(durationSeconds = 300, reader = "Narrator", downloadState = DownloadState.Downloading)
            },
            audioEpubDownloadState = DownloadState.Downloading,
        )

        assertEquals(initial.readerSourceIdentity(), updated.readerSourceIdentity())
    }

    @Test fun finishingAudioDownloadsDoesNotReloadSourceText() {
        val initial = book()
        val downloaded = initial.copy(chapters = initial.chapters.map {
            it.copy(localFileName = "offline/${it.id}.mp3", downloadState = DownloadState.Downloaded)
        })

        assertEquals(initial.readerSourceIdentity(), downloaded.readerSourceIdentity())
    }

    @Test fun hydratedVolumeSourcesTriggerLoadAndRetainTheirChapterMappings() {
        val initial = book()
        val hydrated = initial.copy(readAlongSources = listOf(volume()))
        val correctedMapping = hydrated.copy(readAlongSources = listOf(volume().copy(chapterIds = listOf("chapter-6"))))
        val changedArchive = hydrated.copy(readAlongSources = listOf(volume().copy(daisyUrl = "https://example.com/revised.daisy.zip")))

        assertNotEquals(initial.readerSourceIdentity(), hydrated.readerSourceIdentity())
        assertNotEquals(hydrated.readerSourceIdentity(), correctedMapping.readerSourceIdentity())
        assertNotEquals(hydrated.readerSourceIdentity(), changedArchive.readerSourceIdentity())
    }

    @Test fun sourceArchiveReplacementInvalidatesTheLoadedText() {
        val initial = book()
        val replacements = listOf(
            initial.copy(epubUrl = "https://example.com/revised.epub"),
            initial.copy(audioEpubUrl = "https://example.com/revised.audio.epub"),
            initial.copy(daisyUrl = "https://example.com/revised.daisy.zip"),
            initial.copy(localAudioEpubFileName = "offline/book.daisy.zip"),
            initial.copy(localAudioEpubSourceUrl = "https://example.com/local-source.daisy.zip"),
        )

        replacements.forEach { assertNotEquals(initial.readerSourceIdentity(), it.readerSourceIdentity()) }
    }

    @Test fun changedChapterMappingInvalidatesTheLoadedText() {
        val initial = book()
        val chapter = initial.chapters.first()
        val changedChapters = listOf(
            chapter.copy(id = "corrected-chapter-5"),
            chapter.copy(title = "Corrected source title"),
            chapter.copy(number = 4),
            chapter.copy(listenUrl = "https://example.com/corrected-chapter-5.mp3"),
        )
        changedChapters.forEach { changed ->
            val updated = initial.copy(chapters = listOf(changed, initial.chapters.last()))
            assertNotEquals(initial.readerSourceIdentity(), updated.readerSourceIdentity())
        }
        assertNotEquals(
            initial.readerSourceIdentity(),
            initial.copy(chapters = initial.chapters.reversed()).readerSourceIdentity(),
        )
    }

    @Test fun changingBooksOrSourceAvailabilityInvalidatesTheLoadedText() {
        val initial = book()
        val replacements = listOf(
            initial.copy(id = "another-book"),
            initial.copy(source = BookSource.LibriVox),
            initial.copy(wolneLekturyUrl = "https://example.com/corrected-source/"),
        )

        replacements.forEach { assertNotEquals(initial.readerSourceIdentity(), it.readerSourceIdentity()) }
    }

    private fun book() = AudioBook(
        id = "wolnelektury-lalka",
        title = "Lalka",
        author = "Bolesław Prus",
        source = BookSource.WolneLektury,
        epubUrl = "https://example.com/lalka.epub",
        chapters = (5..6).map { number ->
            AudioBookChapter(
                id = "chapter-$number",
                title = "Rozdział $number",
                number = number,
                listenUrl = "https://example.com/chapter-$number.mp3",
            )
        },
    )

    private fun volume() = AudioBookReadAlongSource(
        id = "wolnelektury-lalka-tom-pierwszy",
        title = "Lalka, tom pierwszy",
        chapterIds = listOf("chapter-5", "chapter-6"),
        epubUrl = "https://example.com/tom-pierwszy.epub",
        daisyUrl = "https://example.com/tom-pierwszy.daisy.zip",
    )
}
