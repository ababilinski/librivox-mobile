package com.librivox.mobile.ui.player.bookmarks

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.Bookmark
import com.librivox.mobile.model.numberedTitle

internal data class BookmarkChapterSection(
    val chapterId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val bookmarks: List<Bookmark>,
    val isCurrentChapter: Boolean,
)

internal data class BookmarkPanelSections(
    val currentChapter: BookmarkChapterSection?,
    val remainingChapters: List<BookmarkChapterSection>,
) {
    val isEmpty: Boolean = currentChapter == null && remainingChapters.isEmpty()
    val showDivider: Boolean = currentChapter != null && remainingChapters.isNotEmpty()
}

internal fun buildBookmarkPanelSections(
    book: AudioBook,
    bookmarks: List<Bookmark>,
    activeBookId: String?,
    activeChapterId: String?,
): BookmarkPanelSections {
    val bookBookmarks = bookmarks
        .filter { it.bookId == book.id }
        .groupBy { it.chapterId }
    if (bookBookmarks.isEmpty()) {
        return BookmarkPanelSections(currentChapter = null, remainingChapters = emptyList())
    }

    val chapterIndexById = book.chapters
        .mapIndexed { index, chapter -> chapter.id to index }
        .toMap()
    val chapterById = book.chapters.associateBy { it.id }
    val knownChapterIds = chapterIndexById.keys

    val orderedSections = buildList {
        book.chapters.forEachIndexed { index, chapter ->
            val chapterBookmarks = bookBookmarks[chapter.id].orEmpty()
            if (chapterBookmarks.isNotEmpty()) {
                add(
                    BookmarkChapterSection(
                        chapterId = chapter.id,
                        chapterIndex = index,
                        chapterTitle = chapter.numberedTitle(),
                        bookmarks = chapterBookmarks.sortedBy { it.positionMs },
                        isCurrentChapter = activeBookId == book.id && activeChapterId == chapter.id,
                    ),
                )
            }
        }

        bookBookmarks
            .filterKeys { it !in knownChapterIds }
            .toSortedMap()
            .forEach { (chapterId, chapterBookmarks) ->
                add(
                    BookmarkChapterSection(
                        chapterId = chapterId,
                        chapterIndex = Int.MAX_VALUE,
                        chapterTitle = chapterById[chapterId]?.numberedTitle() ?: "Unknown chapter",
                        bookmarks = chapterBookmarks.sortedBy { it.positionMs },
                        isCurrentChapter = activeBookId == book.id && activeChapterId == chapterId,
                    ),
                )
            }
    }

    val currentSection = orderedSections.firstOrNull { it.isCurrentChapter }
    return BookmarkPanelSections(
        currentChapter = currentSection,
        remainingChapters = orderedSections.filterNot { it === currentSection },
    )
}
