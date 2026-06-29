package com.librivox.mobile.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioBookChapterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun numberedTitle_prefixesChapterNumber() {
        val chapter = AudioBookChapter(
            id = "owl",
            title = "The Long-Eared Owl",
            number = 1,
        )

        assertEquals("01: The Long-Eared Owl", chapter.numberedTitle())
    }

    @Test
    fun numberedTitle_normalizesExistingPrefix() {
        val chapter = AudioBookChapter(
            id = "owl",
            title = "1: The Long-Eared Owl",
            number = 1,
        )

        assertEquals("01: The Long-Eared Owl", chapter.numberedTitle())
    }

    @Test
    fun numberedTitle_removesLeadingChapterPhrases() {
        val chapter = AudioBookChapter(
            id = "anielka-1",
            title = "Rozdział pierwszy. Autor dokonywa przeglądu osób",
            number = 1,
        )

        assertEquals("01: Autor dokonywa przeglądu osób", chapter.numberedTitle())
    }

    @Test
    fun numberedTitle_removesColonChapterPhrases() {
        assertEquals(
            "01: Autor dokonywa przeglądu osób",
            AudioBookChapter(
                id = "anielka-1",
                title = "Rozdział pierwszy: Autor dokonywa przeglądu osób",
                number = 1,
            ).numberedTitle(),
        )
        assertEquals(
            "01: Autor dokonywa przeglądu osób",
            AudioBookChapter(
                id = "anielka-1",
                title = "Rozdział: Autor dokonywa przeglądu osób",
                number = 1,
            ).numberedTitle(),
        )
        assertEquals(
            "01: The Letter",
            AudioBookChapter(
                id = "letter-1",
                title = "Chapter: The Letter",
                number = 1,
            ).numberedTitle(),
        )
    }

    @Test
    fun numberedTitle_doesNotPrefixTitlesWithOwnOrdinal() {
        assertEquals(
            "12 Angry Men",
            AudioBookChapter(id = "numbered", title = "12 Angry Men", number = 1).numberedTitle(),
        )
        assertEquals(
            "IV. A Roman Start",
            AudioBookChapter(id = "roman", title = "IV. A Roman Start", number = 4).numberedTitle(),
        )
        assertEquals(
            "Part One",
            AudioBookChapter(id = "part", title = "Part One", number = 1).numberedTitle(),
        )
        assertEquals(
            "Udział pierwszy",
            AudioBookChapter(id = "udzial", title = "Udział pierwszy", number = 1).numberedTitle(),
        )
    }

    @Test
    fun seedDefaults_migratesLegacyCathedralStarterToTheJungle() {
        val legacyCathedral = AudioBook(
            id = AudioBookLibrary.LEGACY_CATHEDRAL_ID,
            title = "Cathedral",
            author = "Raymond Carver",
            source = BookSource.LocalAsset,
            libraryStatus = LibraryStatus.InLibrary,
            chapters = listOf(
                AudioBookChapter(
                    id = "cathedral-1",
                    title = "Cathedral",
                    number = 1,
                    assetFileName = "Cathedral by Raymond Carver.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )
        val oldJunglePlaceholder = AudioBookLibrary.jungle.withLibraryStatus(LibraryStatus.NotInLibrary)

        val state = LibraryState(books = listOf(legacyCathedral, oldJunglePlaceholder)).withSeedDefaults()
        val jungle = state.books.single { it.id == AudioBookLibrary.THE_JUNGLE_ID }

        assertFalse(state.books.any { it.id == AudioBookLibrary.LEGACY_CATHEDRAL_ID })
        assertEquals(BookSource.LibriVox, jungle.source)
        assertEquals(LibraryStatus.InLibrary, jungle.libraryStatus)
        assertEquals("https://librivox.org/the-jungle-by-upton-sinclair/", jungle.librivoxUrl)
        assertTrue(jungle.chapters.all { it.listenUrl?.startsWith("https://www.archive.org/") == true })
        assertTrue(jungle.chapters.none { it.assetFileName != null })
    }

    @Test
    fun seededJungleBook_usesCastableLibrivoxStreams() {
        val jungle = AudioBookLibrary.jungle

        assertEquals(LibraryStatus.InLibrary, jungle.libraryStatus)
        assertEquals(31, jungle.chapters.size)
        assertEquals("https://librivox.org/the-jungle-by-upton-sinclair/", jungle.librivoxUrl)
        assertTrue(jungle.chapters.all { it.listenUrl?.startsWith("https://www.archive.org/") == true })
        assertTrue(jungle.chapters.all { it.isCastable() })
        assertNull(jungle.chapters.first().assetFileName)
    }

    @Test
    fun localAsset_isCastableOnlyWhenLocalNetworkIsAvailable() {
        val chapter = AudioBookChapter(
            id = "local-1",
            title = "Local sample",
            number = 1,
            assetFileName = "sample.mp3",
            downloadState = DownloadState.Downloaded,
        )

        assertTrue(chapter.isLocalCastCandidate())
        assertFalse(chapter.isCastable())
        assertTrue(chapter.isCastable(localNetworkAvailable = true))
    }

    @Test
    fun seededPrideBook_usesCatalogBackedLibriVoxProject() {
        val pride = AudioBookLibrary.featuredPlaceholders.single {
            it.id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID
        }

        assertEquals(BookSource.LibriVox, pride.source)
        assertEquals(LibraryStatus.NotInLibrary, pride.libraryStatus)
        assertEquals("https://librivox.org/pride-and-prejudice-by-jane-austen/", pride.librivoxUrl)
        assertTrue(pride.chapters.isEmpty())
    }

    @Test
    fun seededLibrary_doesNotExposeEmptyPridePlaceholder() {
        val library = LibraryState()

        assertFalse(library.libraryBooks.any { it.id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID })
        assertFalse(library.featuredBooks.any { it.id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID })
    }

    @Test
    fun mergeSeededBooks_removesStalePridePreviewAsset() {
        val stalePride = AudioBook(
            id = AudioBookLibrary.PRIDE_AND_PREJUDICE_ID,
            title = "Pride and Prejudice",
            author = "Jane Austen",
            source = BookSource.LibriVox,
            libraryStatus = LibraryStatus.InLibrary,
            chapters = listOf(
                AudioBookChapter(
                    id = "253-preview-1",
                    title = "Chapters 1-3 preview asset",
                    number = 1,
                    assetFileName = "pride_and_prejudice_01.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )

        val merged = listOf(stalePride).mergedWithIncoming(AudioBookLibrary.seededBooks)
        val pride = merged.single { it.id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID }

        assertEquals(LibraryStatus.NotInLibrary, pride.libraryStatus)
        assertTrue(pride.chapters.isEmpty())
    }

    @Test
    fun bookDownloadStateHelpers_reportPartialAndActiveProgress() {
        val book = AudioBook(
            id = "download-test",
            title = "Download Test",
            author = "Tester",
            chapters = listOf(
                AudioBookChapter(
                    id = "one",
                    title = "One",
                    number = 1,
                    listenUrl = "https://example.com/one.mp3",
                    localFileName = "one.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
                AudioBookChapter(
                    id = "two",
                    title = "Two",
                    number = 2,
                    listenUrl = "https://example.com/two.mp3",
                    downloadState = DownloadState.Downloading,
                ),
            ),
        )

        assertEquals(2, book.downloadableChapterCount())
        assertEquals(1, book.downloadedChapterCount())
        assertEquals(1, book.activeDownloadChapterCount())
        assertTrue(book.hasDownloadedChapters())
        assertFalse(book.isFullyDownloaded())
        assertEquals(0.5f, book.downloadProgressFraction(), 0.0001f)
    }

    @Test
    fun wolneBookDownloadHelpers_countAudioEpubAsBookAsset() {
        val book = AudioBook(
            id = "wolne-download-test",
            title = "Wolne Download Test",
            author = "Tester",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://wolnelektury.pl/media/book-audio.epub",
            chapters = listOf(
                AudioBookChapter(
                    id = "one",
                    title = "One",
                    number = 1,
                    listenUrl = "https://example.com/one.mp3",
                    localFileName = "offline/wolne-download-test/one.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
                AudioBookChapter(
                    id = "two",
                    title = "Two",
                    number = 2,
                    listenUrl = "https://example.com/two.mp3",
                ),
            ),
        )

        assertEquals(3, book.downloadableBookAssetCount())
        assertEquals(1, book.downloadedBookAssetCount())
        assertEquals(2, book.missingDownloadAssetCount())
        assertTrue(book.hasDownloadedBookAssets())
        assertFalse(book.isFullyDownloaded())
        assertEquals(1f / 3f, book.downloadProgressFraction(), 0.0001f)
    }

    @Test
    fun wolneBookDownloadHelpers_countDaisyAsReadAlongAsset() {
        val book = AudioBook(
            id = "wolne-daisy-download-test",
            title = "Wolne Daisy Download Test",
            author = "Tester",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book.epub",
            daisyUrl = "https://wolnelektury.pl/media/book.daisy.zip",
            chapters = listOf(
                AudioBookChapter(
                    id = "one",
                    title = "One",
                    number = 1,
                    listenUrl = "https://example.com/one.mp3",
                ),
            ),
        )

        assertTrue(book.canReadSourceText())
        assertEquals("https://wolnelektury.pl/media/book.daisy.zip", book.downloadableReadAlongAssetUrl())
        assertEquals("offline/wolne-daisy-download-test/book.daisy.zip", book.downloadedReadAlongAssetFileName())
        assertEquals(
            listOf(
                "offline/wolne-daisy-download-test/book.daisy.zip",
                "offline/wolne-daisy-download-test/book.epub",
            ),
            book.downloadableReadAlongAssets().map { it.targetFileName },
        )
        assertEquals(
            listOf(true, false),
            book.downloadableReadAlongAssets().map { it.primary },
        )
        assertEquals(2, book.downloadableBookAssetCount())
    }

    @Test
    fun wolneCatalogStub_canOpenReaderWithoutInflatingDownloadAssets() {
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            wolneLekturyUrl = "https://wolnelektury.pl/katalog/lektura/antek/",
            chapters = emptyList(),
        )

        assertTrue(book.canReadSourceText())
        assertNull(book.downloadableReadAlongAssetUrl())
        assertEquals(0, book.downloadableBookAssetCount())
    }

    @Test
    fun wolneBookDownloadHelpers_requireAudioEpubForFullDownload() {
        val book = AudioBook(
            id = "wolne-full-download-test",
            title = "Wolne Full Download Test",
            author = "Tester",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://wolnelektury.pl/media/book-audio.epub",
            localAudioEpubFileName = "offline/wolne-full-download-test/book.audio.epub",
            audioEpubDownloadState = DownloadState.Downloaded,
            chapters = listOf(
                AudioBookChapter(
                    id = "one",
                    title = "One",
                    number = 1,
                    listenUrl = "https://example.com/one.mp3",
                    localFileName = "offline/wolne-full-download-test/one.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
                AudioBookChapter(
                    id = "two",
                    title = "Two",
                    number = 2,
                    listenUrl = "https://example.com/two.mp3",
                    localFileName = "offline/wolne-full-download-test/two.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )

        assertEquals(3, book.downloadableBookAssetCount())
        assertEquals(3, book.downloadedBookAssetCount())
        assertEquals(0, book.missingDownloadAssetCount())
        assertTrue(book.hasDownloadedAudioEpub())
        assertTrue(book.isFullyDownloaded())
        assertEquals(1f, book.downloadProgressFraction(), 0.0001f)
    }

    @Test
    fun oneChapterBook_isFullyDownloadedWhenChapterDownloads() {
        val book = AudioBook(
            id = "single-download-test",
            title = "Single Download Test",
            author = "Tester",
            chapters = listOf(
                AudioBookChapter(
                    id = "only",
                    title = "Only",
                    number = 1,
                    listenUrl = "https://example.com/only.mp3",
                    localFileName = "offline/single/only.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )

        assertEquals(1, book.downloadedChapterCount())
        assertTrue(book.hasDownloadedChapters())
        assertTrue(book.isFullyDownloaded())
        assertEquals(1f, book.downloadProgressFraction(), 0.0001f)
    }

    @Test
    fun wolneOneChapterBook_isAudioFullyDownloadedWhenOnlyChapterDownloads() {
        val book = AudioBook(
            id = "wolne-single-download-test",
            title = "Wolne Single Download Test",
            author = "Tester",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://wolnelektury.pl/media/book-audio.epub",
            chapters = listOf(
                AudioBookChapter(
                    id = "only",
                    title = "Only",
                    number = 1,
                    listenUrl = "https://example.com/only.mp3",
                    localFileName = "offline/wolne-single/only.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )

        assertEquals(1, book.downloadableChapterCount())
        assertEquals(1, book.downloadedChapterCount())
        assertEquals(2, book.downloadableBookAssetCount())
        assertEquals(1, book.downloadedBookAssetCount())
        assertEquals(0, book.missingAudioDownloadCount())
        assertEquals(1, book.missingDownloadAssetCount())
        assertTrue(book.isAudioFullyDownloaded())
        assertFalse(book.isFullyDownloaded())
    }

    @Test
    fun detectsDownloadedFileForOneChapterCatalogBook() {
        val book = AudioBook(
            id = "antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            chapters = listOf(
                AudioBookChapter(
                    id = "antek",
                    title = "Antek",
                    number = 1,
                    listenUrl = "https://audio.wolnelektury.pl/antek.mp3",
                ),
            ),
        )
        val expectedFileName = book.chapters.single().downloadedFileName(book.id)
        val downloadedFile = File(temporaryFolder.root, expectedFileName)
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("audio")

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals(expectedFileName, repaired.chapters.single().localFileName)
        assertEquals(DownloadState.Downloaded, repaired.chapters.single().downloadState)
        assertTrue(repaired.isFullyDownloaded())
    }

    @Test
    fun detectsDownloadedAudioEpubForWolneBook() {
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            audioEpubUrl = "https://wolnelektury.pl/media/book-audio.epub",
            chapters = emptyList(),
        )
        val expectedFileName = book.downloadedAudioEpubFileName()
        val downloadedFile = File(temporaryFolder.root, expectedFileName)
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("epub")

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals(expectedFileName, repaired.localAudioEpubFileName)
        assertEquals(DownloadState.Downloaded, repaired.audioEpubDownloadState)
        assertTrue(repaired.hasDownloadedAudioEpub())
        assertTrue(repaired.isFullyDownloaded())
    }

    @Test
    fun localReadAlongAsset_prefersSyncCapableAssetOverStoredPlainEpub() {
        val book = AudioBook(
            id = "wolnelektury-sync-and-epub",
            title = "Sync and EPUB",
            author = "Tester",
            source = BookSource.WolneLektury,
            epubUrl = "https://wolnelektury.pl/media/book.epub",
            daisyUrl = "https://wolnelektury.pl/media/book.daisy.zip",
            localAudioEpubFileName = "offline/wolnelektury-sync-and-epub/book.epub",
            chapters = emptyList(),
        )
        val offlineDir = File(temporaryFolder.root, "offline/wolnelektury-sync-and-epub")
        val plainEpub = File(offlineDir, "book.epub")
        val daisy = File(offlineDir, "book.daisy.zip")
        offlineDir.mkdirs()
        plainEpub.writeText("plain")
        daisy.writeText("sync")

        assertEquals(daisy.absolutePath, book.localAudioEpubFile(temporaryFolder.root)?.absolutePath)

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals("offline/wolnelektury-sync-and-epub/book.daisy.zip", repaired.localAudioEpubFileName)
        assertEquals(DownloadState.Downloaded, repaired.audioEpubDownloadState)
    }

    @Test
    fun downloadedCoverFileName_usesOfflineBookDirectoryAndImageExtension() {
        val book = AudioBook(
            id = "Book: One",
            title = "Book One",
            author = "Tester",
            coverImageUrl = "https://example.com/thumb.jpg",
            fullCoverImageUrl = "https://example.com/full-cover.png?size=large",
        )

        val coverFileName = book.downloadedCoverFileName()
        assertTrue(coverFileName.startsWith("offline/Book__One/cover-"))
        assertTrue(coverFileName.endsWith(".png"))
        assertEquals("https://example.com/full-cover.png?size=large", book.downloadableCoverImageUrl())
    }

    @Test
    fun withDetectedLocalDownloads_detectsDownloadedCoverFile() {
        val book = AudioBook(
            id = "cover book",
            title = "Cover Book",
            author = "Tester",
            coverImageUrl = "https://example.com/full-cover.webp",
        )
        val coverFile = File(temporaryFolder.root, book.downloadedCoverFileName())
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(byteArrayOf(1, 2, 3))

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals(book.downloadedCoverFileName(), repaired.localCoverFileName)
        assertEquals(coverFile, repaired.localCoverFile(temporaryFolder.root))
    }

    @Test
    fun withDetectedLocalDownloads_detectsLegacyDownloadedCoverFile() {
        val book = AudioBook(
            id = "legacy cover book",
            title = "Legacy Cover Book",
            author = "Tester",
            coverImageUrl = "https://example.com/full-cover.webp",
        )
        val coverFile = File(temporaryFolder.root, "offline/legacy_cover_book/cover.webp")
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(byteArrayOf(1, 2, 3))

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals("offline/legacy_cover_book/cover.webp", repaired.localCoverFileName)
        assertEquals(coverFile, repaired.localCoverFile(temporaryFolder.root))
    }

    @Test
    fun mergedWithIncoming_preservesDownloadedCoverFile() {
        val existing = AudioBook(
            id = "cover-book",
            title = "Old",
            author = "Tester",
            coverImageUrl = "https://example.com/old-thumb.jpg",
            localCoverFileName = "offline/cover-book/cover.jpg",
            localCoverSourceUrl = "https://example.com/old-thumb.jpg",
        )
        val incoming = AudioBook(
            id = "cover-book",
            title = "New",
            author = "Tester",
            coverImageUrl = "https://example.com/new-thumb.jpg",
            fullCoverImageUrl = "https://example.com/full-cover.jpg",
            originalLanguage = "French",
            translators = listOf("Translator One"),
            translationMetadataChecked = true,
        )

        val merged = listOf(existing).mergedWithIncoming(listOf(incoming)).single()

        assertEquals("https://example.com/new-thumb.jpg", merged.coverImageUrl)
        assertEquals("https://example.com/full-cover.jpg", merged.fullCoverImageUrl)
        assertEquals("offline/cover-book/cover.jpg", merged.localCoverFileName)
        assertEquals("https://example.com/old-thumb.jpg", merged.localCoverSourceUrl)
        assertEquals("French", merged.originalLanguage)
        assertEquals(listOf("Translator One"), merged.translators)
        assertTrue(merged.translationMetadataChecked)
    }

    @Test
    fun mergedWithIncoming_dedupesExistingBooksByIdAndPreservesLocalState() {
        val stale = AudioBook(
            id = "duplicate-book",
            title = "Duplicate Book",
            author = "Tester",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter 1",
                    number = 1,
                    listenUrl = "https://example.com/chapter-1.mp3",
                ),
            ),
        )
        val downloaded = stale.copy(
            title = "Duplicate Book Updated",
            libraryStatus = LibraryStatus.InLibrary,
            isFavorite = true,
            localCoverFileName = "offline/duplicate-book/cover.jpg",
            localCoverSourceUrl = "https://example.com/cover.jpg",
            addedAtMillis = 42L,
            chapters = listOf(
                stale.chapters.single().copy(
                    localFileName = "offline/duplicate-book/chapter-1.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )

        val merged = listOf(stale, downloaded).mergedWithIncoming(emptyList())

        assertEquals(1, merged.count { it.id == "duplicate-book" })
        val repaired = merged.single { it.id == "duplicate-book" }
        assertEquals("Duplicate Book Updated", repaired.title)
        assertEquals(LibraryStatus.InLibrary, repaired.libraryStatus)
        assertTrue(repaired.isFavorite)
        assertEquals(42L, repaired.addedAtMillis)
        assertEquals("offline/duplicate-book/cover.jpg", repaired.localCoverFileName)
        assertEquals("offline/duplicate-book/chapter-1.mp3", repaired.chapters.single().localFileName)
        assertEquals(DownloadState.Downloaded, repaired.chapters.single().downloadState)
    }

    @Test
    fun mergedWithIncoming_dedupesIncomingBooksBeforeMergingLibraryState() {
        val existing = AudioBook(
            id = "catalog-duplicate",
            title = "Library Copy",
            author = "Tester",
            libraryStatus = LibraryStatus.InLibrary,
            isFavorite = true,
            localCoverFileName = "offline/catalog-duplicate/cover.jpg",
            addedAtMillis = 100L,
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter 1",
                    number = 1,
                    localFileName = "offline/catalog-duplicate/chapter-1.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )
        val firstIncoming = AudioBook(
            id = "catalog-duplicate",
            title = "Catalog Copy",
            author = "Tester",
            coverImageUrl = "https://example.com/thumb.jpg",
            chapters = existing.chapters.map {
                it.copy(localFileName = null, downloadState = DownloadState.NotDownloaded)
            },
        )
        val richerIncoming = firstIncoming.copy(
            title = "Catalog Copy Refreshed",
            fullCoverImageUrl = "https://example.com/full.jpg",
            addedAtMillis = 50L,
        )

        val merged = listOf(existing).mergedWithIncoming(listOf(firstIncoming, richerIncoming))

        assertEquals(1, merged.count { it.id == "catalog-duplicate" })
        val repaired = merged.single { it.id == "catalog-duplicate" }
        assertEquals("Catalog Copy Refreshed", repaired.title)
        assertEquals(LibraryStatus.InLibrary, repaired.libraryStatus)
        assertTrue(repaired.isFavorite)
        assertEquals(100L, repaired.addedAtMillis)
        assertEquals("https://example.com/thumb.jpg", repaired.coverImageUrl)
        assertEquals("https://example.com/full.jpg", repaired.fullCoverImageUrl)
        assertEquals("offline/catalog-duplicate/cover.jpg", repaired.localCoverFileName)
        assertEquals("offline/catalog-duplicate/chapter-1.mp3", repaired.chapters.single().localFileName)
    }

    @Test
    fun libraryStateSanitized_dedupesBooksAndPreservesNewestLibraryState() {
        val first = AudioBook(
            id = "persisted-duplicate",
            title = "Persisted Copy",
            author = "Tester",
            localCoverFileName = "offline/persisted-duplicate/cover.jpg",
            chapters = listOf(
                AudioBookChapter(
                    id = "chapter-1",
                    title = "Chapter 1",
                    number = 1,
                    localFileName = "offline/persisted-duplicate/chapter-1.mp3",
                    downloadState = DownloadState.Downloaded,
                ),
            ),
        )
        val second = first.copy(
            title = "Persisted Copy Later",
            libraryStatus = LibraryStatus.InLibrary,
            isFavorite = true,
            addedAtMillis = 200L,
            localCoverFileName = null,
        )

        val sanitized = LibraryState(books = listOf(first, second)).sanitized()

        assertEquals(1, sanitized.books.count { it.id == "persisted-duplicate" })
        val repaired = sanitized.books.single { it.id == "persisted-duplicate" }
        assertEquals("Persisted Copy Later", repaired.title)
        assertEquals(LibraryStatus.InLibrary, repaired.libraryStatus)
        assertTrue(repaired.isFavorite)
        assertEquals(200L, repaired.addedAtMillis)
        assertEquals("offline/persisted-duplicate/cover.jpg", repaired.localCoverFileName)
        assertEquals(DownloadState.Downloaded, repaired.chapters.single().downloadState)
    }

    @Test
    fun rebuildsEmptyCatalogChaptersFromExistingDownloadedFiles() {
        val book = AudioBook(
            id = "wolnelektury-antek",
            title = "Antek",
            author = "Bolesław Prus",
            source = BookSource.WolneLektury,
            chapters = emptyList(),
        )
        val downloadedFile = File(
            temporaryFolder.root,
            "offline/wolnelektury-antek/antek-1.mp3",
        )
        downloadedFile.parentFile?.mkdirs()
        downloadedFile.writeText("audio")

        val repaired = book.withDetectedLocalDownloads(temporaryFolder.root)

        assertEquals(1, repaired.chapters.size)
        assertEquals("antek-1", repaired.chapters.single().id)
        assertEquals("offline/wolnelektury-antek/antek-1.mp3", repaired.chapters.single().localFileName)
        assertEquals(DownloadState.Downloaded, repaired.chapters.single().downloadState)
        assertTrue(repaired.isFullyDownloaded())
    }
}
