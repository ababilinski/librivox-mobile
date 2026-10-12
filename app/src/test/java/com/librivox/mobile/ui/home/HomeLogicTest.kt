package com.librivox.mobile.ui.home

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookChapter
import com.librivox.mobile.model.BookProgressSnapshot
import com.librivox.mobile.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    @Test
    fun continueShelf_dismissalOverridesCurrentMediaAndSavedProgressBeforeLimit() {
        val current = book("current", "Current")
        val saved = book("saved", "Saved")
        val visible = book("visible", "Visible")
        val progress = listOf(current, saved, visible).associate {
            it.id to BookProgressSnapshot(positionMs = 4_000L, lastActivityMillis = 100L)
        }

        val shelf = continueShelfBooks(
            books = listOf(current, saved, visible),
            progressByBookId = progress,
            currentBookId = current.id,
            hasCurrentMedia = true,
            limit = 1,
            dismissedBookIds = setOf(current.id, saved.id),
        )

        assertEquals(listOf(visible), shelf)
        assertEquals(
            listOf(current),
            continueShelfBooks(
                books = listOf(current, saved, visible),
                progressByBookId = progress,
                currentBookId = current.id,
                hasCurrentMedia = true,
                limit = 1,
                dismissedBookIds = setOf(saved.id),
            ),
        )
    }

    @Test
    fun continueShelf_prefersRecentReaderActivityOverOlderLargePlaybackOffset() {
        val anielka = book("wolnelektury-prus-anielka", "Anielka")
        val olderLongBook = book("older-book", "Older book")

        val sorted = continueShelfBooks(
            books = listOf(olderLongBook, anielka),
            progressByBookId = mapOf(
                olderLongBook.id to BookProgressSnapshot(
                    positionMs = 900_000L,
                    lastActivityMillis = 1_000L,
                ),
                anielka.id to BookProgressSnapshot(
                    positionMs = 0L,
                    lastActivityMillis = 2_000L,
                ),
            ),
            currentBookId = null,
            hasCurrentMedia = false,
        )

        assertEquals(listOf(anielka, olderLongBook), sorted)
    }

    @Test
    fun continueShelf_pinsCurrentBookWithMediaBeforeRecentActivity() {
        val current = book("current-book", "Current")
        val recent = book("recent-book", "Recent")

        val sorted = continueShelfBooks(
            books = listOf(recent, current),
            progressByBookId = mapOf(
                recent.id to BookProgressSnapshot(lastActivityMillis = 10_000L),
            ),
            currentBookId = current.id,
            hasCurrentMedia = true,
        )

        assertEquals(listOf(current, recent), sorted)
    }

    @Test
    fun continueShelf_skipsUntouchedBooks() {
        val untouched = book("untouched-book", "Untouched")

        val sorted = continueShelfBooks(
            books = listOf(untouched),
            progressByBookId = emptyMap(),
            currentBookId = null,
            hasCurrentMedia = false,
        )

        assertFalse(untouched in sorted)
    }

    @Test
    fun downloadedShelf_includesOnlyBooksWithDownloadedAssets() {
        val savedOnly = book("saved-only", "Saved only")
        val olderDownload = downloadedBook("older-download", "Older", addedAtMillis = 1_000L)
        val newerDownload = downloadedBook("newer-download", "Newer", addedAtMillis = 2_000L)

        val shelf = downloadedShelfBooks(
            books = listOf(savedOnly, olderDownload, newerDownload, olderDownload),
        )

        assertEquals(listOf(newerDownload, olderDownload), shelf)
    }

    @Test
    fun shouldDeferHomeContent_waitsOnlyForColdHomeContent() {
        assertEquals(
            true,
            shouldDeferHomeContent(
                lazyLoadRequest = 2,
                completedLazyLoadRequest = 1,
                hasWarmContent = false,
                deferWarmContent = false,
            ),
        )

        assertEquals(
            false,
            shouldDeferHomeContent(
                lazyLoadRequest = 2,
                completedLazyLoadRequest = 1,
                hasWarmContent = true,
                deferWarmContent = false,
            ),
        )
    }

    @Test
    fun shouldDeferHomeContent_canWaitForWarmContentDuringTabTransition() {
        assertTrue(
            shouldDeferHomeContent(
                lazyLoadRequest = 2,
                completedLazyLoadRequest = 1,
                hasWarmContent = true,
                deferWarmContent = true,
            ),
        )
    }

    @Test
    fun shouldDeferHomeContent_doesNotRepeatCompletedRequest() {
        assertEquals(
            false,
            shouldDeferHomeContent(
                lazyLoadRequest = 2,
                completedLazyLoadRequest = 2,
                hasWarmContent = false,
                deferWarmContent = true,
            ),
        )
    }

    @Test
    fun shouldShowHomeInitialSkeleton_showsWhileContentIsDeferred() {
        assertEquals(
            true,
            shouldShowHomeInitialSkeleton(
                deferHomeContent = true,
                initialRevealComplete = false,
                hasWarmContent = true,
                isHomeSharedTransition = false,
            ),
        )

        assertEquals(
            false,
            shouldShowHomeInitialSkeleton(
                deferHomeContent = false,
                initialRevealComplete = false,
                hasWarmContent = true,
                isHomeSharedTransition = false,
            ),
        )
    }

    @Test
    fun featuredLoadingIndicator_showsDuringInitialFeaturedLoad() {
        assertTrue(
            shouldShowFeaturedLoadingIndicator(
                deferHomeContent = false,
                hasFeaturedSource = true,
                noSourcesSelected = false,
                downloadsOnlyActive = false,
                featuredCacheIsLoading = true,
                featuredCacheMatches = false,
                featuredCacheLoadedAtMillis = 0L,
            ),
        )
    }

    @Test
    fun featuredLoadingIndicator_showsDuringStaleFeaturedRefresh() {
        assertTrue(
            shouldShowFeaturedLoadingIndicator(
                deferHomeContent = false,
                hasFeaturedSource = true,
                noSourcesSelected = false,
                downloadsOnlyActive = false,
                featuredCacheIsLoading = true,
                featuredCacheMatches = true,
                featuredCacheLoadedAtMillis = 42L,
            ),
        )
    }

    @Test
    fun featuredLoadingIndicator_hidesWhenRecommendationsArePaused() {
        assertFalse(
            shouldShowFeaturedLoadingIndicator(
                deferHomeContent = false,
                hasFeaturedSource = true,
                noSourcesSelected = false,
                downloadsOnlyActive = true,
                featuredCacheIsLoading = true,
                featuredCacheMatches = false,
                featuredCacheLoadedAtMillis = 0L,
            ),
        )
        assertFalse(
            shouldShowFeaturedLoadingIndicator(
                deferHomeContent = false,
                hasFeaturedSource = false,
                noSourcesSelected = true,
                downloadsOnlyActive = false,
                featuredCacheIsLoading = true,
                featuredCacheMatches = false,
                featuredCacheLoadedAtMillis = 0L,
            ),
        )
    }

    private fun book(id: String, title: String): AudioBook =
        AudioBook(id = id, title = title, author = "Author")

    private fun downloadedBook(id: String, title: String, addedAtMillis: Long): AudioBook =
        book(id, title).copy(
            addedAtMillis = addedAtMillis,
            chapters = listOf(
                AudioBookChapter(
                    id = "$id-chapter",
                    title = "Chapter 1",
                    number = 1,
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )
}
