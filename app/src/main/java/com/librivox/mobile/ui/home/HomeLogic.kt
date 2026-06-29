package com.librivox.mobile.ui.home

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookProgressSnapshot

internal fun continueShelfBooks(
    books: List<AudioBook>,
    progressByBookId: Map<String, BookProgressSnapshot>,
    currentBookId: String?,
    hasCurrentMedia: Boolean,
    limit: Int = 10,
): List<AudioBook> =
    books
        .distinctBy { it.id }
        .map { book -> book to progressByBookId[book.id].orEmpty() }
        .filter { (book, progress) ->
            progress.positionMs > 0L ||
                progress.lastActivityMillis > 0L ||
                (hasCurrentMedia && book.id == currentBookId)
        }
        .sortedWith(
            compareByDescending<Pair<AudioBook, BookProgressSnapshot>> {
                hasCurrentMedia && it.first.id == currentBookId
            }
                .thenByDescending { it.second.lastActivityMillis }
                .thenByDescending { it.second.positionMs },
        )
        .map { it.first }
        .take(limit.coerceAtLeast(0))

private fun BookProgressSnapshot?.orEmpty(): BookProgressSnapshot =
    this ?: BookProgressSnapshot()

internal fun hasWarmHomeContent(
    hasRenderedContent: Boolean,
    hasLibraryBooks: Boolean,
    hasStagedBookDetails: Boolean,
    hasFeaturedCacheBooks: Boolean,
    hasRetainedFeaturedBooks: Boolean,
): Boolean =
    hasRenderedContent ||
        hasLibraryBooks ||
        hasStagedBookDetails ||
        hasFeaturedCacheBooks ||
        hasRetainedFeaturedBooks

internal fun shouldDeferHomeContent(
    lazyLoadRequest: Int,
    completedLazyLoadRequest: Int,
    hasWarmContent: Boolean,
    deferWarmContent: Boolean,
): Boolean =
    lazyLoadRequest > 0 &&
        lazyLoadRequest != completedLazyLoadRequest &&
        (!hasWarmContent || deferWarmContent)

internal fun shouldShowHomeInitialSkeleton(
    deferHomeContent: Boolean,
    initialRevealComplete: Boolean,
    hasWarmContent: Boolean,
    isHomeSharedTransition: Boolean,
): Boolean =
    !isHomeSharedTransition &&
        (deferHomeContent || (!hasWarmContent && !initialRevealComplete))

internal fun shouldShowFeaturedLoadingIndicator(
    deferHomeContent: Boolean,
    hasFeaturedSource: Boolean,
    noSourcesSelected: Boolean,
    downloadsOnlyActive: Boolean,
    featuredCacheIsLoading: Boolean,
    featuredCacheMatches: Boolean,
    featuredCacheLoadedAtMillis: Long,
): Boolean =
    !deferHomeContent &&
        hasFeaturedSource &&
        !noSourcesSelected &&
        !downloadsOnlyActive &&
        (
            featuredCacheIsLoading ||
                (!featuredCacheMatches && featuredCacheLoadedAtMillis == 0L)
            )
