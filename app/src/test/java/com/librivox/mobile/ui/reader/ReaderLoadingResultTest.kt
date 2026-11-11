package com.librivox.mobile.ui.reader

import com.librivox.mobile.readalong.ReadAlongBook
import com.librivox.mobile.readalong.ReadAlongChapter
import com.librivox.mobile.readalong.ReadAlongSegment
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLoadingResultTest {
    @Test fun fullStaticTextCannotRemoveChapterSixTimingAlreadyInUse() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = true)), partial = true)
        val fullStatic = book(chapters = (1..6).map { chapter("chapter-$it", timed = false) })

        val result = resolveReaderLoadResult(Result.success(preferred), Result.success(fullStatic))

        assertSame(preferred, result.getOrThrow())
        assertTrue(result.getOrThrow().isPartial)
    }

    @Test fun fullResultMustKeepTimingForThePreferredChapterEvenIfAnotherChapterIsTimed() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = true)), partial = true)
        val incompleteFull = book(
            chapters = listOf(chapter("chapter-1", timed = true), chapter("chapter-6", timed = false)),
        )

        assertSame(
            preferred,
            resolveReaderLoadResult(Result.success(preferred), Result.success(incompleteFull)).getOrThrow(),
        )
    }

    @Test fun successfulFullSyncReplacesPreferredChapterWithTheCompleteBook() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = true)), partial = true)
        val full = book(chapters = (1..6).map { chapter("chapter-$it", timed = true) })

        assertSame(full, resolveReaderLoadResult(Result.success(preferred), Result.success(full)).getOrThrow())
    }

    @Test fun failedFullLoadLeavesReadablePreferredChapterAvailable() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = true)), partial = true)

        assertSame(
            preferred,
            resolveReaderLoadResult(Result.success(preferred), Result.failure(IllegalStateException("Offline")))
                .getOrThrow(),
        )
    }

    @Test fun fullStaticTextCanExpandAStaticPreferredChapter() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = false)), partial = true)
        val full = book(chapters = (1..6).map { chapter("chapter-$it", timed = false) })

        assertSame(full, resolveReaderLoadResult(Result.success(preferred), Result.success(full)).getOrThrow())
    }

    @Test fun partialFullLoadOfAnotherVolumeCannotRemoveReadableStaticPreferredChapter() {
        val preferred = book(chapters = listOf(chapter("chapter-6", timed = false)), partial = true)
        val otherVolume = book(chapters = listOf(chapter("chapter-21", timed = true)), partial = true)

        assertSame(
            preferred,
            resolveReaderLoadResult(Result.success(preferred), Result.success(otherVolume)).getOrThrow(),
        )
    }

    @Test fun fullFailureIsReportedWhenNoReadablePreferredResultExists() {
        val error = IllegalStateException("Could not load book text")
        assertSame(error, resolveReaderLoadResult(null, Result.failure(error)).exceptionOrNull())
    }

    private fun book(chapters: List<ReadAlongChapter>, partial: Boolean = false) = ReadAlongBook(
        bookId = "wolnelektury-lalka",
        title = "Lalka",
        chapters = chapters,
        hasTimedSync = chapters.any { chapter -> chapter.segments.any { it.clipBeginMs != null } },
        isPartial = partial,
    )

    private fun chapter(id: String, timed: Boolean) = ReadAlongChapter(
        chapterId = id,
        title = id,
        displayIndex = 6,
        fullText = "Tekst rozdziału.",
        segments = listOf(
            ReadAlongSegment(
                id = "$id:segment-1",
                chapterId = id,
                text = "Tekst rozdziału.",
                textRef = "$id.xhtml#p1",
                audioRef = if (timed) "$id.mp3" else null,
                clipBeginMs = if (timed) 12_000L else null,
                clipEndMs = if (timed) 20_000L else null,
            ),
        ),
    )
}
