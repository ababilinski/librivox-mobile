import Foundation
import Testing
import ZIPFoundation
@testable import LibriVoxCore

@Test func existingProfileMustCompleteUpdatedSourceOnboardingOnce() {
    var profile = LocalUserProfile(displayName: "Adrian", hasCompletedOnboarding: true)
    let originalID = profile.id
    #expect(profile.requiresOnboarding(completedVersion: 0))
    #expect(profile.requiresOnboarding(completedVersion: 2))
    profile.markOnboardingComplete()
    #expect(!profile.requiresOnboarding(completedVersion: LocalUserProfile.currentOnboardingVersion))
    #expect(profile.id == originalID)
    #expect(profile.displayName == "Adrian")
    let fresh = LocalUserProfile()
    #expect(fresh.requiresOnboarding(completedVersion: LocalUserProfile.currentOnboardingVersion))
}

@Test func seedLibraryStartsWithTheJungle() async throws {
    let store = InMemoryLibraryStore()
    let books = await store.snapshot()
    #expect(books.contains { $0.id == AudioBook.theJungle.id })
    #expect(AudioBook.theJungle.libraryStatus == .inLibrary)
}

@Test func seedCatalogSearchesByAuthorAndReader() async throws {
    let catalog = SeedCatalogClient()
    let authorResults = try await catalog.search(
        CatalogSearchRequest(query: "Upton Sinclair", field: .author)
    )
    let readerResults = try await catalog.search(
        CatalogSearchRequest(query: "Tom Weiss", field: .reader)
    )
    #expect(authorResults.map(\.id).contains(AudioBook.theJungle.id))
    #expect(readerResults.map(\.id).contains(AudioBook.theJungle.id))
}

@Test func durationFormatterHandlesBooksAndChapters() {
    #expect(formatDuration(seconds: 75) == "1:15")
    #expect(formatDuration(seconds: 3_724) == "1:02:04")
}

@Test func chapterDisplayTitleDoesNotDoubleNumberExistingChapterNames() {
    let alreadyNumbered = AudioBookChapter(id: "one", title: "Chapter 1", number: 1)
    let bareTitle = AudioBookChapter(id: "two", title: "The Stockyards", number: 2)
    let romanTitle = AudioBookChapter(id: "three", title: "II. A Visit", number: 3)

    #expect(alreadyNumbered.displayTitle == "Chapter 1")
    #expect(bareTitle.displayTitle == "02: The Stockyards")
    #expect(romanTitle.displayTitle == "II. A Visit")
}

@Test func playbackProgressClampsFractionAndStartState() {
    let progress = PlaybackProgress(
        bookID: "book",
        chapterID: "chapter",
        positionSeconds: 90,
        durationSeconds: 60
    )

    #expect(progress.fractionCompleted == 1)
    #expect(progress.hasStarted)
}

@Test func playbackDurationPolicyKeepsUnknownChapterDurationUnresolved() {
    let multiChapterDuration = PlaybackDurationPolicy.initialDuration(
        chapterDurationSeconds: 0,
        bookTotalDurationSeconds: 7_200,
        chapterCount: 8
    )
    let singleChapterDuration = PlaybackDurationPolicy.initialDuration(
        chapterDurationSeconds: 0,
        bookTotalDurationSeconds: 1_341,
        chapterCount: 1
    )

    #expect(multiChapterDuration == nil)
    #expect(singleChapterDuration == 1_341)
    #expect(PlaybackDurationPolicy.boundedPosition(164, resolvedDuration: nil) == 164)
    #expect(PlaybackDurationPolicy.boundedPosition(164, resolvedDuration: 120) == 120)
}

@Test func localUserProfilePreservesStableIdentityAndOnboardingState() throws {
    let createdAt = Date(timeIntervalSince1970: 1_700_000_000)
    let updatedAt = Date(timeIntervalSince1970: 1_700_000_100)
    var profile = LocalUserProfile(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000111")!,
        displayName: "Local Listener",
        createdAt: createdAt,
        updatedAt: updatedAt
    )

    profile.markOnboardingComplete(at: Date(timeIntervalSince1970: 1_700_000_200))

    let data = try JSONEncoder().encode(profile)
    let decoded = try JSONDecoder().decode(LocalUserProfile.self, from: data)

    #expect(decoded.id == profile.id)
    #expect(decoded.displayName == "Local Listener")
    #expect(decoded.hasCompletedOnboarding)
    #expect(decoded.createdAt == createdAt)
    #expect(decoded.updatedAt == Date(timeIntervalSince1970: 1_700_000_200))
}

@Test func jsonStoreFilePersistsVersionedEnvelope() async throws {
    let url = temporaryStoreURL(named: "strings.json")
    let store = JSONStoreFile<[String]>(fileURL: url, schemaVersion: 4)

    try await store.save(["The Jungle", "Antek"])

    let data = try Data(contentsOf: url)
    let envelope = try JSONDecoder().decode(JSONStoreEnvelope<[String]>.self, from: data)
    #expect(envelope.schemaVersion == 4)
    #expect(envelope.payload == ["The Jungle", "Antek"])
    #expect(await store.load(default: []) == ["The Jungle", "Antek"])

    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func coverArtworkCachePersistsArtworkByURL() async throws {
    let directoryURL = temporaryStoreURL(named: "cover-artwork").deletingPathExtension()
    let cache = CoverArtworkCache(directoryURL: directoryURL)
    let coverURL = try #require(URL(string: "https://archive.org/services/img/jungle_tw_0908_librivox"))
    let imageData = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")!

    try await cache.store(imageData, for: coverURL)

    #expect(await cache.cachedData(for: [coverURL]) == imageData)
    #expect(FileManager.default.fileExists(atPath: await cache.cacheFileURL(for: coverURL).path))

    let secondCacheInstance = CoverArtworkCache(directoryURL: directoryURL)
    #expect(await secondCacheInstance.cachedData(for: [coverURL]) == imageData)

    try? await cache.removeAll()
}

@Test func coverArtworkCacheUsesCachedFallbackCandidate() async throws {
    let directoryURL = temporaryStoreURL(named: "cover-fallback-artwork").deletingPathExtension()
    let cache = CoverArtworkCache(directoryURL: directoryURL)
    let unavailableURL = try #require(URL(string: "https://archive.org/services/img/unavailable-cover"))
    let cachedFallbackURL = try #require(URL(string: "https://archive.org/download/book/cover.jpg"))
    let imageData = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")!

    try await cache.store(imageData, for: cachedFallbackURL)

    #expect(await cache.cachedData(for: [unavailableURL, cachedFallbackURL]) == imageData)

    try? await cache.removeAll()
}

@Test func persistentLibraryStoreMigratesLegacyDefaultsToJSONStoreFile() async throws {
    let key = "LibriVoxMobile.tests.library.migration.\(UUID().uuidString)"
    let storeURL = temporaryStoreURL(named: "library-books.json")
    let fileStore = JSONStoreFile<[AudioBook]>(fileURL: storeURL)
    let legacyBook = AudioBook(
        id: "legacy-book",
        title: "Legacy Book",
        author: "Legacy Author",
        libraryStatus: .inLibrary
    )
    UserDefaults.standard.set(try JSONEncoder().encode([legacyBook]), forKey: key)

    let store = PersistentLibraryStore(defaultsKey: key, store: fileStore)
    let books = await store.snapshot()

    #expect(books.contains { $0.id == "legacy-book" })
    #expect(books.contains { $0.id == AudioBook.theJungle.id })
    #expect(await fileStore.load(default: []).contains { $0.id == "legacy-book" })

    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func persistentPlaybackProgressStoreSavesAndRemovesProgress() async {
    let key = "LibriVoxMobile.tests.progress.\(UUID().uuidString)"
    UserDefaults.standard.removeObject(forKey: key)
    let storeURL = temporaryStoreURL(named: "playback-progress.json")
    let fileStore = JSONStoreFile<[PlaybackProgress]>(fileURL: storeURL)
    let store = PersistentPlaybackProgressStore(defaultsKey: key, store: fileStore)
    let progress = PlaybackProgress(
        bookID: "book",
        chapterID: "chapter-2",
        positionSeconds: 42,
        durationSeconds: 120,
        completedChapterIDs: ["chapter-1"]
    )

    await store.save(progress)
    let saved = await store.progress(for: "book")
    #expect(saved?.chapterID == "chapter-2")
    #expect(saved?.completedChapterIDs.contains("chapter-1") == true)
    #expect((await store.snapshot())["book"] == saved)

    await store.remove(bookID: "book")
    #expect(await store.progress(for: "book") == nil)
    #expect(await fileStore.load(default: []).isEmpty)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func audioBookCanReadSourceTextDetectsReadableAssets() {
    let plain = AudioBook(id: "plain", title: "Plain", author: "Author")
    let gutenberg = AudioBook(
        id: "gutenberg",
        title: "Gutenberg",
        author: "Author",
        gutenbergURL: "https://www.gutenberg.org/ebooks/1"
    )
    let wolne = AudioBook(
        id: "wolne",
        title: "Wolne",
        author: "Author",
        sourceTextURL: "https://wolnelektury.pl/media/book/txt/example.txt",
        sourceHTMLURL: " "
    )
    let packaged = AudioBook(
        id: "packaged",
        title: "Packaged",
        author: "Author",
        audioEpubURL: "https://example.test/book.audio.epub"
    )

    #expect(!plain.canReadSourceText)
    #expect(gutenberg.canReadSourceText)
    #expect(wolne.canReadSourceText)
    #expect(packaged.canReadSourceText)
}

@Test func wolneTimedParserBuildsSegmentsFromAudioEpubSmil() throws {
    let workingDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxCoreTimedReadAlong-\(UUID().uuidString)", isDirectory: true)
    let sourceDirectory = workingDirectory.appendingPathComponent("source", isDirectory: true)
    let metaDirectory = sourceDirectory.appendingPathComponent("META-INF", isDirectory: true)
    let epubDirectory = sourceDirectory.appendingPathComponent("EPUB", isDirectory: true)
    let audioDirectory = epubDirectory.appendingPathComponent("audio", isDirectory: true)
    try FileManager.default.createDirectory(at: metaDirectory, withIntermediateDirectories: true)
    try FileManager.default.createDirectory(at: audioDirectory, withIntermediateDirectories: true)

    try """
    <?xml version="1.0" encoding="UTF-8"?>
    <container>
        <rootfiles>
            <rootfile full-path="EPUB/package.opf" />
        </rootfiles>
    </container>
    """.write(to: metaDirectory.appendingPathComponent("container.xml"), atomically: true, encoding: .utf8)
    try """
    <?xml version="1.0" encoding="UTF-8"?>
    <package>
        <manifest>
            <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml" />
            <item id="sync" href="chapter.smil" media-type="application/smil+xml" />
        </manifest>
    </package>
    """.write(to: epubDirectory.appendingPathComponent("package.opf"), atomically: true, encoding: .utf8)
    try """
    <?xml version="1.0" encoding="UTF-8"?>
    <html>
        <body>
            <p id="p1">Pierwszy akapit Antka.</p>
            <p id="p2">Drugi akapit Antka.</p>
        </body>
    </html>
    """.write(to: epubDirectory.appendingPathComponent("chapter.xhtml"), atomically: true, encoding: .utf8)
    try """
    <?xml version="1.0" encoding="UTF-8"?>
    <smil>
        <body>
            <seq>
                <par>
                    <text src="chapter.xhtml#p1" />
                    <audio src="audio/chapter.mp3" clipBegin="npt=1.25s" clipEnd="npt=3.5s" />
                </par>
                <par>
                    <text src="chapter.xhtml#p2" />
                    <audio src="audio/chapter.mp3" clip-begin="00:00:03.500" clip-end="00:00:05.000" />
                </par>
            </seq>
        </body>
    </smil>
    """.write(to: epubDirectory.appendingPathComponent("chapter.smil"), atomically: true, encoding: .utf8)
    try Data().write(to: audioDirectory.appendingPathComponent("chapter.mp3"))

    let archiveURL = workingDirectory.appendingPathComponent("book.audio.epub")
    try FileManager.default.zipItem(
        at: sourceDirectory,
        to: archiveURL,
        shouldKeepParent: false,
        compressionMethod: .deflate
    )

    let book = AudioBook(
        id: "wolnelektury-antek",
        title: "Antek",
        author: "Boleslaw Prus",
        source: .wolneLektury,
        chapters: [
            AudioBookChapter(
                id: "antek-1",
                title: "Antek",
                number: 1,
                durationSeconds: 10,
                listenURL: "https://wolnelektury.pl/media/book/mp3/chapter.mp3"
            )
        ]
    )

    let parsed = try WolneTimedReadAlongParser().parse(
        book: book,
        fileURL: archiveURL,
        sourceURL: "https://example.test/book.audio.epub"
    )

    #expect(parsed?.syncQuality == .timed)
    #expect(parsed?.supportsPlaybackSync == true)
    #expect(parsed?.chapters.first?.segments.map(\.text) == ["Pierwszy akapit Antka.", "Drugi akapit Antka."])
    #expect(parsed?.chapters.first?.segments.first?.startMilliseconds == 1_250)
    #expect(parsed?.chapters.first?.segments.first?.endMilliseconds == 3_500)
    #expect(parsed?.chapters.first?.segments.last?.startMilliseconds == 3_500)
    try? FileManager.default.removeItem(at: workingDirectory)
}

@Test func readAlongSegmentBuildsProportionalSentenceRanges() {
    let segment = ReadAlongSegment(
        id: "segment-1",
        chapterID: "chapter-1",
        index: 0,
        text: "Ala. Bbbb.",
        startMilliseconds: 1_000,
        endMilliseconds: 10_000
    )

    let ranges = segment.proportionalSentenceRanges()

    #expect(ranges == [
        ReadAlongTimedTextRange(
            textRange: ReadAlongTextRange(location: 0, length: 4),
            clipBeginMilliseconds: 1_000,
            clipEndMilliseconds: 5_000
        ),
        ReadAlongTimedTextRange(
            textRange: ReadAlongTextRange(location: 5, length: 5),
            clipBeginMilliseconds: 5_000,
            clipEndMilliseconds: 10_000
        )
    ])
    #expect(segment.activeSentenceRange(positionMilliseconds: 7_500) == ReadAlongTextRange(location: 5, length: 5))
}

@Test func persistentPlaybackFeedbackStoreTogglesBookAndChapterFeedback() async {
    let key = "LibriVoxMobile.tests.feedback.\(UUID().uuidString)"
    UserDefaults.standard.removeObject(forKey: key)
    let storeURL = temporaryStoreURL(named: "playback-feedback.json")
    let fileStore = JSONStoreFile<[PlaybackFeedbackEntry]>(fileURL: storeURL)
    let store = PersistentPlaybackFeedbackStore(defaultsKey: key, store: fileStore)

    #expect(await store.feedback(bookID: "book", chapterID: nil, scope: .book) == .none)
    #expect(await store.toggleFeedback(bookID: "book", chapterID: nil, target: .liked, scope: .book) == .liked)
    #expect(await store.feedback(bookID: "book", chapterID: nil, scope: .book) == .liked)
    #expect(await store.toggleFeedback(bookID: "book", chapterID: nil, target: .liked, scope: .book) == .none)
    #expect(await store.feedback(bookID: "book", chapterID: nil, scope: .book) == .none)

    #expect(await store.toggleFeedback(bookID: "book", chapterID: "chapter", target: .disliked, scope: .chapter) == .disliked)
    #expect(await store.feedback(bookID: "book", chapterID: "chapter", scope: .chapter) == .disliked)
    #expect(await store.feedback(bookID: "book", chapterID: "other", scope: .chapter) == .none)
    #expect(await store.toggleFeedback(bookID: "book", chapterID: "chapter", target: .liked, scope: .hidden) == .none)

    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func queueAndSearchHistoryStoresMigrateLegacyDefaults() async throws {
    let queueKey = "LibriVoxMobile.tests.queue.\(UUID().uuidString)"
    let searchKey = "LibriVoxMobile.tests.searchHistory.\(UUID().uuidString)"
    let queueURL = temporaryStoreURL(named: "queue.json")
    let searchURL = temporaryStoreURL(named: "search-history.json")
    let queueFileStore = JSONStoreFile<[QueueEntry]>(fileURL: queueURL)
    let searchFileStore = JSONStoreFile<[String]>(fileURL: searchURL)
    let legacyQueue = [
        QueueEntry(
            bookID: "legacy-book",
            chapterID: "legacy-chapter",
            addedAt: Date(timeIntervalSince1970: 10)
        ),
    ]
    UserDefaults.standard.set(try JSONEncoder().encode(legacyQueue), forKey: queueKey)
    UserDefaults.standard.set([" Antek ", "The Jungle"], forKey: searchKey)

    let queueStore = PersistentQueueStore(defaultsKey: queueKey, store: queueFileStore)
    let searchStore = PersistentSearchHistoryStore(defaultsKey: searchKey, store: searchFileStore)

    #expect(await queueStore.snapshot().map(\.bookID) == ["legacy-book"])
    #expect(await searchStore.snapshot() == [" Antek ", "The Jungle"])
    #expect(await queueFileStore.load(default: []).map(\.chapterID) == ["legacy-chapter"])
    #expect(await searchFileStore.load(default: []) == [" Antek ", "The Jungle"])

    await queueStore.save([QueueEntry(bookID: "new-book")])
    await searchStore.save(["Katarynka"])
    #expect(await queueFileStore.load(default: []).map(\.bookID) == ["new-book"])
    #expect(await searchFileStore.load(default: []) == ["Katarynka"])

    UserDefaults.standard.removeObject(forKey: queueKey)
    UserDefaults.standard.removeObject(forKey: searchKey)
    try? FileManager.default.removeItem(at: queueURL.deletingLastPathComponent())
    try? FileManager.default.removeItem(at: searchURL.deletingLastPathComponent())
}

@Test func playheadBookmarkFindsNearestBookmarkWithinTolerance() {
    let oldNear = Bookmark(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
        bookID: "book",
        chapterID: "chapter",
        positionMilliseconds: 10_400,
        createdAt: Date(timeIntervalSince1970: 1)
    )
    let newNear = Bookmark(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
        bookID: "book",
        chapterID: "chapter",
        positionMilliseconds: 10_300,
        createdAt: Date(timeIntervalSince1970: 2)
    )
    let wrongChapter = Bookmark(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000003")!,
        bookID: "book",
        chapterID: "other",
        positionMilliseconds: 10_000
    )
    let far = Bookmark(
        id: UUID(uuidString: "00000000-0000-0000-0000-000000000004")!,
        bookID: "book",
        chapterID: "chapter",
        positionMilliseconds: 20_000
    )

    let found = playheadBookmark(
        bookID: "book",
        chapterID: "chapter",
        positionMilliseconds: 10_000,
        bookmarks: [oldNear, newNear, wrongChapter, far],
        toleranceMilliseconds: 500
    )

    #expect(found == newNear)
    #expect(playheadBookmark(
        bookID: "book",
        chapterID: "chapter",
        positionMilliseconds: 10_000,
        bookmarks: [oldNear, newNear],
        toleranceMilliseconds: 100
    ) == nil)
}

@Test func persistentDownloadServiceResolvesExistingChapterFile() async throws {
    let key = "LibriVoxMobile.tests.downloads.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    let store = PersistentDownloadService(defaultsKey: key, downloadsDirectory: downloadsDirectory, store: fileStore)
    let bookID = "book one"
    let chapter = AudioBookChapter(
        id: "chapter one",
        title: "Chapter One",
        number: 1,
        listenURL: "https://example.test/audio/chapter-one.m4a"
    )
    let bookDirectory = downloadsDirectory.appendingPathComponent("book-one", isDirectory: true)
    try FileManager.default.createDirectory(at: bookDirectory, withIntermediateDirectories: true)
    let fileURL = bookDirectory.appendingPathComponent("chapter-one.m4a")
    try Data([1, 2, 3]).write(to: fileURL)

    let resolvedURL = await store.localFileURL(bookID: bookID, chapter: chapter)
    #expect(resolvedURL == fileURL)
    #expect(await store.storageBytes(bookID: bookID) == 3)
    #expect(await store.storageBytes(bookID: bookID, chapter: chapter) == 3)

    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func persistentDownloadServiceDeletesSingleChapterDownload() async throws {
    let key = "LibriVoxMobile.tests.downloads.deleteChapter.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    let firstChapter = AudioBookChapter(
        id: "chapter one",
        title: "Chapter One",
        number: 1,
        listenURL: "https://example.test/audio/chapter-one.mp3"
    )
    let secondChapter = AudioBookChapter(
        id: "chapter two",
        title: "Chapter Two",
        number: 2,
        listenURL: "https://example.test/audio/chapter-two.mp3"
    )
    let book = AudioBook(
        id: "book one",
        title: "Book One",
        author: "Author",
        chapters: [firstChapter, secondChapter]
    )
    let bookDirectory = downloadsDirectory.appendingPathComponent("book-one", isDirectory: true)
    try FileManager.default.createDirectory(at: bookDirectory, withIntermediateDirectories: true)
    let firstFileURL = bookDirectory.appendingPathComponent("chapter-one.mp3")
    let secondFileURL = bookDirectory.appendingPathComponent("chapter-two.mp3")
    try Data([1, 2, 3]).write(to: firstFileURL)
    try Data([4, 5, 6, 7]).write(to: secondFileURL)
    try await fileStore.save([
        DownloadItem(bookID: book.id, title: book.title, state: .downloaded, progress: 1),
        DownloadItem(bookID: book.id, chapterID: firstChapter.id, title: firstChapter.displayTitle, state: .downloaded, progress: 1),
        DownloadItem(bookID: book.id, chapterID: secondChapter.id, title: secondChapter.displayTitle, state: .downloaded, progress: 1),
    ])
    let service = PersistentDownloadService(defaultsKey: key, downloadsDirectory: downloadsDirectory, store: fileStore)

    await service.delete(book: book, chapter: firstChapter)

    let downloads = await service.downloads()
    #expect(await service.localFileURL(bookID: book.id, chapter: firstChapter) == nil)
    #expect(await service.localFileURL(bookID: book.id, chapter: secondChapter) == secondFileURL)
    #expect(await service.storageBytes(bookID: book.id) == 4)
    #expect(!downloads.contains { $0.bookID == book.id && $0.chapterID == nil })
    #expect(!downloads.contains { $0.bookID == book.id && $0.chapterID == firstChapter.id })
    #expect(downloads.contains { $0.bookID == book.id && $0.chapterID == secondChapter.id && $0.state == .downloaded })

    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func persistentDownloadServiceMigratesLegacyDownloadItemsToJSONStoreFile() async throws {
    let key = "LibriVoxMobile.tests.downloads.migration.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    let legacyItems = [
        DownloadItem(bookID: "legacy-book", title: "Legacy Book", state: .downloaded, progress: 1),
    ]
    UserDefaults.standard.set(try JSONEncoder().encode(legacyItems), forKey: key)

    let service = PersistentDownloadService(
        defaultsKey: key,
        downloadsDirectory: downloadsDirectory,
        store: fileStore
    )

    let downloads = await service.downloads()
    #expect(downloads.map(\.bookID) == ["legacy-book"])
    #expect(downloads.first?.state == .downloaded)
    #expect(await fileStore.load(default: []).map(\.bookID) == ["legacy-book"])

    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func downloadItemDecodesLegacyRowsAndRecoversBook() throws {
    let data = Data(
        """
        [{
          "id": "00000000-0000-0000-0000-000000000001",
          "bookID": "wolnelektury-antek",
          "chapterID": null,
          "title": "Antek",
          "state": "downloaded",
          "progress": 1
        }]
        """.utf8
    )

    let items = try JSONDecoder().decode([DownloadItem].self, from: data)
    let recoveredBook = DownloadItem.recoveredAudioBook(bookID: "wolnelektury-antek", from: items)

    #expect(items.first?.bookSource == nil)
    #expect(recoveredBook?.id == "wolnelektury-antek")
    #expect(recoveredBook?.title == "Antek")
    #expect(recoveredBook?.chapters.isEmpty == true)
}

@Test func downloadItemRecoveryUsesStoredBookAndChapterMetadata() throws {
    let items = [
        DownloadItem(
            bookID: "wolnelektury-antek",
            title: "Antek",
            state: .downloaded,
            progress: 1,
            bookTitle: "Antek",
            bookAuthor: "Boleslaw Prus",
            bookSource: .wolneLektury,
            coverImageURL: "https://example.test/antek.jpg"
        ),
        DownloadItem(
            bookID: "wolnelektury-antek",
            chapterID: "chapter-one",
            title: "01: Boleslaw Prus, Antek",
            state: .downloaded,
            progress: 1,
            bookTitle: "Antek",
            bookAuthor: "Boleslaw Prus",
            bookSource: .wolneLektury,
            coverImageURL: "https://example.test/antek.jpg",
            chapterNumber: 1,
            chapterReader: "Jan Peszek",
            chapterDurationSeconds: 615,
            chapterListenURL: "https://example.test/antek.mp3"
        ),
    ]

    let recoveredBook = try #require(DownloadItem.recoveredAudioBook(bookID: "wolnelektury-antek", from: items))

    #expect(recoveredBook.title == "Antek")
    #expect(recoveredBook.author == "Boleslaw Prus")
    #expect(recoveredBook.source == .wolneLektury)
    #expect(recoveredBook.coverImageURL == "https://example.test/antek.jpg")
    #expect(recoveredBook.chapters.first?.id == "chapter-one")
    #expect(recoveredBook.chapters.first?.reader == "Jan Peszek")
    #expect(recoveredBook.chapters.first?.durationSeconds == 615)
    #expect(recoveredBook.chapters.first?.listenURL == "https://example.test/antek.mp3")
}

@Test func persistentDownloadServiceMarkDownloadedUpsertsChapterItems() async throws {
    let key = "LibriVoxMobile.tests.downloads.chapter.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    let service = PersistentDownloadService(
        defaultsKey: key,
        downloadsDirectory: downloadsDirectory,
        store: fileStore
    )

    await service.markDownloaded(bookID: "book", chapterID: "chapter-one")

    let downloads = await service.downloads()
    #expect(downloads.count == 1)
    #expect(downloads.first?.bookID == "book")
    #expect(downloads.first?.chapterID == "chapter-one")
    #expect(downloads.first?.state == .downloaded)
    #expect(downloads.first?.progress == 1)

    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func persistentDownloadServiceCancelRemovesActiveItemsAndKeepsCompletedChapters() async throws {
    let key = "LibriVoxMobile.tests.downloads.cancel.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    try await fileStore.save([
        DownloadItem(bookID: "book", title: "Book", state: .downloading, progress: 0.42),
        DownloadItem(bookID: "book", chapterID: "chapter-one", title: "Chapter One", state: .downloaded, progress: 1),
        DownloadItem(bookID: "book", chapterID: "chapter-two", title: "Chapter Two", state: .queued, progress: 0),
        DownloadItem(bookID: "other-book", title: "Other Book", state: .downloading, progress: 0.2),
    ])
    let service = PersistentDownloadService(
        defaultsKey: key,
        downloadsDirectory: downloadsDirectory,
        store: fileStore
    )

    await service.cancel(bookID: "book")

    let downloads = await service.downloads()
    #expect(downloads.count == 2)
    #expect(downloads.contains { $0.bookID == "book" && $0.chapterID == "chapter-one" && $0.state == .downloaded })
    #expect(downloads.contains { $0.bookID == "other-book" && $0.state == .downloading })
    #expect(!downloads.contains { $0.bookID == "book" && ($0.state == .queued || $0.state == .downloading) })

    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func persistentDownloadServiceEnqueueWholeBookCreatesChapterItems() async throws {
    let key = "LibriVoxMobile.tests.downloads.whole-book.\(UUID().uuidString)"
    let downloadsDirectory = FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxMobileTests-\(UUID().uuidString)", isDirectory: true)
    let storeURL = temporaryStoreURL(named: "downloads.json")
    let fileStore = JSONStoreFile<[DownloadItem]>(fileURL: storeURL)
    let service = PersistentDownloadService(
        defaultsKey: key,
        downloadsDirectory: downloadsDirectory,
        store: fileStore
    )
    let book = AudioBook(
        id: "book",
        title: "Book",
        author: "Author",
        chapters: [
            AudioBookChapter(id: "chapter-one", title: "One", number: 1, listenURL: "http://127.0.0.1:1/one.mp3"),
            AudioBookChapter(id: "chapter-two", title: "Two", number: 2, listenURL: "http://127.0.0.1:1/two.mp3"),
        ]
    )

    await service.enqueue(book: book)

    let downloads = await service.downloads()
    #expect(downloads.contains { $0.bookID == "book" && $0.chapterID == nil })
    #expect(downloads.contains { $0.bookID == "book" && $0.chapterID == "chapter-one" })
    #expect(downloads.contains { $0.bookID == "book" && $0.chapterID == "chapter-two" })
    #expect(downloads.first { $0.bookID == "book" && $0.chapterID == nil }?.bookAuthor == "Author")
    #expect(downloads.first { $0.bookID == "book" && $0.chapterID == "chapter-one" }?.chapterNumber == 1)
    #expect(downloads.first { $0.bookID == "book" && $0.chapterID == "chapter-one" }?.chapterListenURL == "http://127.0.0.1:1/one.mp3")

    await service.cancel(bookID: "book")
    try? FileManager.default.removeItem(at: downloadsDirectory)
    UserDefaults.standard.removeObject(forKey: key)
    try? FileManager.default.removeItem(at: storeURL.deletingLastPathComponent())
}

@Test func seedCatalogRespectsSourceAndLanguageFilters() async throws {
    let catalog = SeedCatalogClient()
    let noResults = try await catalog.search(
        CatalogSearchRequest(
            query: "The Jungle",
            field: .title,
            languages: [.english],
            sources: [.lit2Go]
        )
    )
    let englishLibriVoxResults = try await catalog.search(
        CatalogSearchRequest(
            query: "The Jungle",
            field: .title,
            languages: [.english],
            sources: [.libriVox]
        )
    )
    #expect(noResults.isEmpty)
    #expect(englishLibriVoxResults.map(\.id).contains(AudioBook.theJungle.id))
}

@Test func settingsDefaultsMirrorAudiobookProductDefaults() {
    let settings = UserSettings()
    #expect(settings.enabledSources == [.libriVox])
    #expect(settings.sourcePriority == BookSource.defaultPublicCatalogPriority)
    #expect(settings.orderedEnabledSources == [.libriVox])
    #expect(!settings.showSearchBarOnDiscover)
    #expect(settings.automaticSearchCachingEnabled)
    #expect(settings.autoDownloadMode == .currentChapter)
    #expect(settings.themeMode == .system)
    #expect(settings.coverArtDisplayMode == .fit)
    #expect(settings.defaultPlaybackSpeed == 1.0)
    #expect(settings.nowPlayingPresentationMode == .panels)
    #expect(settings.readerSettings.layoutMode == .paged)
    #expect(!settings.autoDownloadLibraryAdditions)
}

@Test func settingsDecodeOlderPayloadIgnoresLegacyAccentColor() throws {
    let data = Data(#"{"themeMode":"dark","accentColor":"iconPurple","enabledSources":["lit2Go"],"preferredLanguages":["english"]}"#.utf8)
    let settings = try JSONDecoder().decode(UserSettings.self, from: data)

    #expect(settings.themeMode == .dark)
    #expect(settings.enabledSources == [.lit2Go])
    #expect(settings.sourcePriority == BookSource.defaultPublicCatalogPriority)
    #expect(settings.orderedEnabledSources == [.lit2Go])
    #expect(!settings.showSearchBarOnDiscover)
    #expect(settings.autoDownloadMode == .currentChapter)
    #expect(settings.nowPlayingPresentationMode == .panels)
    #expect(settings.readerSettings.layoutMode == .paged)
    #expect(!settings.autoDownloadLibraryAdditions)
}

@Test func autoDownloadLibraryAdditionsRoundTripsAndOlderSettingsDefaultOff() throws {
    let olderSettings = try JSONDecoder().decode(UserSettings.self, from: Data("{}".utf8))
    #expect(!olderSettings.autoDownloadLibraryAdditions)

    var enabledSettings = UserSettings()
    enabledSettings.autoDownloadLibraryAdditions = true
    let decoded = try JSONDecoder().decode(
        UserSettings.self,
        from: JSONEncoder().encode(enabledSettings)
    )
    #expect(decoded.autoDownloadLibraryAdditions)
}

@Test func settingsDecodeSearchBarOnDiscoverPreference() throws {
    let data = Data(#"{"showSearchBarOnDiscover":true}"#.utf8)
    let settings = try JSONDecoder().decode(UserSettings.self, from: data)

    #expect(settings.showSearchBarOnDiscover)
}

@Test func settingsNormalizationAllowsDisablingLibriVoxAndKeepsValidFallbacks() {
    var settings = UserSettings(
        enabledSources: [.lit2Go],
        sourcePriority: [.gutendex, .lit2Go, .lit2Go, .customLocal],
        preferredLanguages: [.polish]
    )
    settings.normalizeForAvailableSources()
    #expect(settings.enabledSources == [.lit2Go])
    #expect(settings.sourcePriority == [.lit2Go, .libriVox, .wolneLektury])
    #expect(settings.orderedEnabledSources == [.lit2Go])
    #expect(settings.preferredLanguages == [.english])

    settings = UserSettings(
        enabledSources: [.wolneLektury],
        preferredLanguages: [.english]
    )
    settings.normalizeForAvailableSources()
    #expect(settings.enabledSources == [.wolneLektury])
    #expect(settings.preferredLanguages == [.polish])

    settings.enabledSources.removeAll()
    settings.normalizeForAvailableSources()
    #expect(settings.enabledSources.isEmpty)
    #expect(settings.orderedEnabledSources.isEmpty)
}

@Test func aggregateCatalogSearchAllowsNoEnabledSources() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(source: .libriVox, books: [.theJungle]),
    ])

    let results = try await catalog.search(
        CatalogSearchRequest(query: "Jungle", field: .title, sources: [])
    )

    #expect(results.isEmpty)
}

@Test func aggregateCatalogFeaturedBooksAllowsNoEnabledSources() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(source: .libriVox, books: [.theJungle]),
    ])

    let results = try await catalog.featuredBooks(limit: 12, sourcePriority: [])

    #expect(results.isEmpty)
}

@Test func aggregateCatalogSearchOnlyUsesRequestedEnabledSources() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(source: .libriVox, books: [.theJungle]),
        TestCatalogClient(
            source: .lit2Go,
            books: [
                AudioBook(
                    id: "lit2go-test",
                    title: "A Lit2Go Test",
                    author: "Public Domain",
                    source: .lit2Go,
                    language: "English"
                ),
            ]
        ),
    ])
    let results = try await catalog.search(
        CatalogSearchRequest(query: "test", field: .title, sources: [.lit2Go])
    )
    #expect(results.map(\.source) == [.lit2Go])
    #expect(results.map(\.id) == ["lit2go-test"])
}

@Test func aggregateCatalogSearchUsesAllRequestedEnabledSources() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(
            source: .libriVox,
            books: [
                AudioBook(
                    id: "librivox-shared",
                    title: "Shared Source LibriVox",
                    author: "Public Domain",
                    source: .libriVox,
                    language: "English"
                ),
            ]
        ),
        TestCatalogClient(
            source: .lit2Go,
            books: [
                AudioBook(
                    id: "lit2go-shared",
                    title: "Shared Source Lit2Go",
                    author: "Public Domain",
                    source: .lit2Go,
                    language: "English"
                ),
            ]
        ),
    ])
    let results = try await catalog.search(
        CatalogSearchRequest(query: "shared", field: .title, sources: [.libriVox, .lit2Go])
    )
    #expect(results.map(\.source) == [.libriVox, .lit2Go])
    #expect(results.map(\.id) == ["librivox-shared", "lit2go-shared"])
}

@Test func aggregateCatalogExpandsLibriVoxAllSearchIntoFieldOperations() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(source: .libriVox, books: []),
        TestCatalogClient(source: .lit2Go, books: []),
    ])

    let operations = catalog.searchOperations(
        for: CatalogSearchRequest(
            query: "Ant",
            field: .all,
            sources: [.libriVox, .lit2Go],
            sourcePriority: [.libriVox, .lit2Go]
        )
    )

    #expect(operations == [
        CatalogSearchOperation(source: .libriVox, field: .title),
        CatalogSearchOperation(source: .libriVox, field: .author),
        CatalogSearchOperation(source: .libriVox, field: .reader),
        CatalogSearchOperation(source: .libriVox, field: .chapter),
        CatalogSearchOperation(source: .libriVox, field: .genre),
        CatalogSearchOperation(source: .lit2Go, field: .all),
    ])
}

@Test func aggregateCatalogProgressiveSearchYieldsFastSourceBeforeSlowLibriVoxFields() async throws {
    let catalog = AggregateCatalogClient(clients: [
        DelayedTestCatalogClient(
            source: .libriVox,
            delayNanoseconds: 120_000_000,
            books: [
                AudioBook(
                    id: "librivox-ant",
                    title: "Ant LibriVox",
                    author: "Slow Source",
                    source: .libriVox,
                    language: "English"
                ),
            ]
        ),
        DelayedTestCatalogClient(
            source: .lit2Go,
            delayNanoseconds: 1_000_000,
            books: [
                AudioBook(
                    id: "lit2go-ant",
                    title: "Ant Lit2Go",
                    author: "Fast Source",
                    source: .lit2Go,
                    language: "English"
                ),
            ]
        ),
    ])
    let request = CatalogSearchRequest(
        query: "Ant",
        field: .all,
        sources: [.libriVox, .lit2Go],
        sourcePriority: [.libriVox, .lit2Go]
    )

    var progressEvents: [CatalogSearchProgress] = []
    for await progress in catalog.searchProgress(request) {
        progressEvents.append(progress)
    }

    #expect(progressEvents.first?.operation.source == .lit2Go)
    #expect(progressEvents.map(\.operation).contains(CatalogSearchOperation(source: .libriVox, field: .title)))
    #expect(progressEvents.map(\.operation).contains(CatalogSearchOperation(source: .libriVox, field: .author)))
    #expect(progressEvents.map(\.operation).contains(CatalogSearchOperation(source: .libriVox, field: .reader)))
    #expect(progressEvents.map(\.operation).contains(CatalogSearchOperation(source: .libriVox, field: .chapter)))
    #expect(progressEvents.map(\.operation).contains(CatalogSearchOperation(source: .libriVox, field: .genre)))

    let results = try await catalog.search(request)
    #expect(Set(results.map(\.id)) == ["librivox-ant", "lit2go-ant"])
}

@Test func aggregateCatalogProgressReportsFailedSources() async throws {
    let catalog = AggregateCatalogClient(clients: [
        FailingTestCatalogClient(source: .wolneLektury),
    ])
    let request = CatalogSearchRequest(
        query: "Ant",
        field: .all,
        languages: [.all],
        sources: [.wolneLektury],
        sourcePriority: [.wolneLektury]
    )

    var events: [CatalogSearchProgress] = []
    for await event in catalog.searchProgress(request) {
        events.append(event)
    }

    #expect(events.count == 1)
    #expect(events.first?.operation.source == .wolneLektury)
    #expect(events.first?.books.isEmpty == true)
    #expect(events.first?.didSucceed == false)
}

@Test func aggregateCatalogRanksExactTitleMatchesBeforeSourceOrder() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(
            source: .libriVox,
            books: [
                AudioBook(id: "librivox-prefix", title: "Common Sense Essays", author: "Someone", source: .libriVox, language: "English"),
            ]
        ),
        TestCatalogClient(
            source: .lit2Go,
            books: [
                AudioBook(id: "lit2go-exact", title: "Common Sense", author: "Thomas Paine", source: .lit2Go, language: "English"),
            ]
        ),
    ])

    let results = try await catalog.search(
        CatalogSearchRequest(query: "Common Sense", field: .title, sources: [.libriVox, .lit2Go])
    )

    #expect(results.map(\.id).prefix(2) == ["lit2go-exact", "librivox-prefix"])
}

@Test func catalogSearchNormalizesPolishSlashLInAuthorNames() {
    let books = [
        AudioBook(
            id: "wolne-katarynka",
            title: "Katarynka",
            author: "Bolesław Prus",
            source: .wolneLektury,
            language: "Polish"
        ),
    ]

    let results = books.rankedForCatalogSearch(query: "Boleslaw Prus", field: .author)

    #expect(results.map(\.id) == ["wolne-katarynka"])
}

@Test func catalogSearchFiltersCachedResultsByRequestScope() {
    let books = [
        AudioBook(id: "librivox-ant", title: "Ant Stories", author: "English Author", source: .libriVox, language: "English"),
        AudioBook(id: "lit2go-ant", title: "Ant Tales", author: "English Author", source: .lit2Go, language: "English"),
        AudioBook(id: "wolne-ant", title: "Antek", author: "Bolesław Prus", source: .wolneLektury, language: "Polish"),
    ]
    let request = CatalogSearchRequest(
        query: "An",
        field: .title,
        languages: [.english],
        sources: [.lit2Go],
        sourcePriority: [.libriVox, .lit2Go, .wolneLektury]
    )

    let results = books.filteredForCatalogSearch(request)

    #expect(results.map(\.id) == ["lit2go-ant"])
}

@Test func catalogSearchFacetCountsUseCachedResultsAndCurrentFilterScope() {
    let books = [
        AudioBook(
            id: "librivox-title",
            title: "Ant Stories",
            author: "Ann English",
            source: .libriVox,
            language: "English"
        ),
        AudioBook(
            id: "lit2go-title",
            title: "Ant Tales",
            author: "Ann English",
            source: .lit2Go,
            language: "English"
        ),
        AudioBook(
            id: "wolne-title",
            title: "Antek",
            author: "Bolesław Prus",
            source: .wolneLektury,
            language: "Polish"
        ),
        AudioBook(
            id: "librivox-author",
            title: "Plain Book",
            author: "Ann Author",
            source: .libriVox,
            language: "English"
        ),
        AudioBook(
            id: "librivox-reader",
            title: "Reader Match",
            author: "Someone",
            source: .libriVox,
            language: "English",
            narrators: ["Ann Reader"]
        ),
    ]
    let request = CatalogSearchRequest(
        query: "An",
        field: .all,
        languages: [.english],
        sources: [.libriVox, .lit2Go, .wolneLektury],
        sourcePriority: [.libriVox, .lit2Go, .wolneLektury]
    )

    let counts = books.catalogSearchFacetCounts(for: request)

    #expect(counts.total == 4)
    #expect(counts.sourceCounts[.libriVox] == 3)
    #expect(counts.sourceCounts[.lit2Go] == 1)
    #expect(counts.sourceCounts[.wolneLektury] == 0)
    #expect(counts.fieldCounts[.all] == 4)
    #expect(counts.fieldCounts[.title] == 2)
    #expect(counts.fieldCounts[.author] == 3)
    #expect(counts.fieldCounts[.reader] == 1)
    #expect(counts.languageCounts[.all] == 5)
    #expect(counts.languageCounts[.english] == 4)
    #expect(counts.languageCounts[.polish] == 1)

    let lit2GoRequest = CatalogSearchRequest(
        query: "An",
        field: .all,
        languages: [.english],
        sources: [.lit2Go],
        sourcePriority: [.libriVox, .lit2Go, .wolneLektury]
    )
    let lit2GoCounts = books.catalogSearchFacetCounts(for: lit2GoRequest)
    #expect(lit2GoCounts.total == 1)
    #expect(lit2GoCounts.sourceCounts[.libriVox] == 3)
    #expect(lit2GoCounts.sourceCounts[.lit2Go] == 1)
    #expect(lit2GoCounts.sourceCounts[.wolneLektury] == 0)
}

@Test func aggregateCatalogMergesDuplicateBooksWithLit2GoPriority() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(
            source: .libriVox,
            books: [
                AudioBook(
                    id: "librivox-common-sense",
                    title: "Common Sense",
                    author: "Thomas Paine",
                    description: "LibriVox metadata",
                    source: .libriVox,
                    libriVoxURL: "https://librivox.org/common-sense/",
                    language: "English"
                ),
            ]
        ),
        TestCatalogClient(
            source: .lit2Go,
            books: [
                AudioBook(
                    id: "lit2go-168__common-sense",
                    title: "Common Sense",
                    author: "Thomas Paine",
                    source: .lit2Go,
                    lit2GoURL: "https://etc.usf.edu/lit2go/168/common-sense/",
                    language: "English"
                ),
            ]
        ),
    ])

    let results = try await catalog.search(
        CatalogSearchRequest(query: "Common Sense", field: .title, sources: [.libriVox, .lit2Go])
    )

    #expect(results.count == 1)
    #expect(results.first?.id == "lit2go-168__common-sense")
    #expect(results.first?.source == .lit2Go)
    #expect(results.first?.libriVoxURL == "https://librivox.org/common-sense/")
    #expect(results.first?.lit2GoURL == "https://etc.usf.edu/lit2go/168/common-sense/")
}

@Test func aggregateCatalogMergesDuplicateBooksWithRequestedSourcePriority() async throws {
    let catalog = AggregateCatalogClient(clients: [
        TestCatalogClient(
            source: .libriVox,
            books: [
                AudioBook(
                    id: "librivox-common-sense",
                    title: "Common Sense",
                    author: "Thomas Paine",
                    description: "LibriVox metadata",
                    source: .libriVox,
                    libriVoxURL: "https://librivox.org/common-sense/",
                    language: "English"
                ),
            ]
        ),
        TestCatalogClient(
            source: .lit2Go,
            books: [
                AudioBook(
                    id: "lit2go-168__common-sense",
                    title: "Common Sense",
                    author: "Thomas Paine",
                    source: .lit2Go,
                    lit2GoURL: "https://etc.usf.edu/lit2go/168/common-sense/",
                    language: "English"
                ),
            ]
        ),
    ])

    let results = try await catalog.search(
        CatalogSearchRequest(
            query: "Common Sense",
            field: .title,
            sources: [.libriVox, .lit2Go],
            sourcePriority: [.libriVox, .lit2Go]
        )
    )

    #expect(results.count == 1)
    #expect(results.first?.id == "librivox-common-sense")
    #expect(results.first?.source == .libriVox)
    #expect(results.first?.libriVoxURL == "https://librivox.org/common-sense/")
    #expect(results.first?.lit2GoURL == "https://etc.usf.edu/lit2go/168/common-sense/")
}

@Test func lit2GoRecordedCommonSenseFixtureParsesWithoutBundledSearchFallback() {
    let html = """
    <html><body>
      <h2>Common Sense</h2>
      <a href="/lit2go/authors/thomas-paine/">Thomas Paine</a>
      <a href="/lit2go/168/common-sense/1378/introduction/">Introduction</a>
    </body></html>
    """
    let book = Lit2GoCatalogClient.parseBookDetail(numericID: "168", slug: "common-sense", html: html)
    #expect(book.id == "lit2go-168__common-sense")
    #expect(book.title == "Common Sense")
    #expect(book.author == "Thomas Paine")
    #expect(book.chapters.first?.listenURL == "https://etc.usf.edu/lit2go/audio/mp3/common-sense-001-introduction.1378.mp3")
}

@Test func lit2GoDetailParserComputesAudioURLsUsingPositionalIndex() {
    let html = """
    <html><body>
      <div id="column_primary">
        <h2>Winesburg, Ohio</h2>
        <h3>by <a href="https://etc.usf.edu/lit2go/authors/14/sherwood-anderson/">Sherwood Anderson</a></h3>
        <dl>
          <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1915/introduction-by-irving-howe/">Introduction by Irving Howe</a></dt>
          <dt><a href="https://etc.usf.edu/lit2go/108/winesburg-ohio/1916/the-book-of-the-grotesque/">The Book of the Grotesque</a></dt>
        </dl>
      </div>
    </body></html>
    """

    let book = Lit2GoCatalogClient.parseBookDetail(numericID: "108", slug: "winesburg-ohio", html: html)

    #expect(book.chapters.map(\.number) == [1, 2])
    #expect(book.chapters[0].listenURL == "https://etc.usf.edu/lit2go/audio/mp3/winesburg-ohio-001-introduction-by-irving-howe.1915.mp3")
    #expect(book.chapters[1].listenURL == "https://etc.usf.edu/lit2go/audio/mp3/winesburg-ohio-002-the-book-of-the-grotesque.1916.mp3")
}

@Test func wolneLekturySlugCandidateHandlesPolishTitleSearches() {
    #expect(WolneLekturyCatalogClient.wolneSlugCandidate(for: "Katarynka") == "katarynka")
    #expect(WolneLekturyCatalogClient.wolneSlugCandidate(for: " Zażółć gęślą jaźń ") == "zazolc-gesla-jazn")
}

@Test func wolneLekturyPartialTitleKeepsFuzzyMatchesWhenSlugLookupFails() async throws {
    let antek = AudioBook(
        id: "wolnelektury-antek",
        title: "Antek",
        author: "Bolesław Prus",
        source: .wolneLektury,
        language: "Polish"
    )

    let results = try await WolneLekturyCatalogClient.addingExactTitleFallback(to: [antek]) {
        throw URLError(.badServerResponse)
    }

    #expect(results.map(\.id) == [antek.id])
}

@Test func wolneLekturyDetailParserBuildsPlayableKatarynkaAudiobook() throws {
    let data = """
    {
      "title": "Katarynka",
      "authors": [{"name": "Boleslaw Prus"}],
      "audio_length": "26:55",
      "media": [
        {"type": "audio.epub", "name": "Boleslaw Prus, Katarynka", "url": "/media/book/audio.epub/katarynka.audio.epub"},
        {"type": "daisy", "name": "Boleslaw Prus, Katarynka", "url": "/media/book/daisy.zip/katarynka.daisy.zip"},
        {"type": "mp3", "name": "Boleslaw Prus, Katarynka", "url": "/media/book/mp3/katarynka.mp3"}
      ],
      "txt": "/media/book/txt/katarynka.txt",
      "html": "/media/book/html/katarynka.html",
      "cover": "/media/book/cover/katarynka.jpg",
      "simple_thumb": "/media/book/cover_thumb/katarynka.jpg",
      "fragment_data": {
        "title": "Boleslaw Prus, Katarynka",
        "html": "<p class=\\"paragraph\\">I tak zawsze byl poblazliwy dla niedoskonalosci ludzkiej.</p>\\n"
      },
      "url": "https://wolnelektury.pl/katalog/lektura/katarynka/"
    }
    """.data(using: .utf8)!

    let book = try WolneLekturyCatalogClient.parseDetail(slug: "katarynka", data: data)

    #expect(book.id == "wolnelektury-katarynka")
    #expect(book.title == "Katarynka")
    #expect(book.author == "Boleslaw Prus")
    #expect(book.source == .wolneLektury)
    #expect(book.totalDurationSeconds == 1_615)
    #expect(book.chapters.first?.listenURL == "https://wolnelektury.pl/media/book/mp3/katarynka.mp3")
    #expect(book.audioEpubURL == "https://wolnelektury.pl/media/book/audio.epub/katarynka.audio.epub")
    #expect(book.daisyURL == "https://wolnelektury.pl/media/book/daisy.zip/katarynka.daisy.zip")
    #expect(book.sourceTextURL == "https://wolnelektury.pl/media/book/txt/katarynka.txt")
    #expect(book.sourceHTMLURL == "https://wolnelektury.pl/media/book/html/katarynka.html")
    #expect(book.description == "I tak zawsze byl poblazliwy dla niedoskonalosci ludzkiej.")
}

@Test func wolneLekturyParentDetailBuildsChaptersFromAudioChildren() throws {
    let data = """
    {
      "title": "Trzej muszkieterowie",
      "authors": [{"name": "Aleksander Dumas (ojciec)"}],
      "media": [],
      "children": [
        {"title": "Trzej muszkieterowie, tom pierwszy", "slug": "trzej-muszkieterowie-tom-i", "has_audio": true},
        {"title": "Trzej muszkieterowie, tom drugi", "slug": "aleksander-dumas-trzej-muszkieterowie-tom-2", "has_audio": true},
        {"title": "Text only child", "slug": "text-only-child", "has_audio": false}
      ],
      "cover": "https://wolnelektury.pl/media/book/cover/aleksander-dumas-trzej-muszkieterowie.jpg",
      "simple_thumb": "https://wolnelektury.pl/media/book/cover_api_thumb/aleksander-dumas-trzej-muszkieterowie.jpg",
      "url": "https://wolnelektury.pl/katalog/lektura/aleksander-dumas-trzej-muszkieterowie/"
    }
    """.data(using: .utf8)!
    let firstVolume = AudioBook(
        id: "wolnelektury-trzej-muszkieterowie-tom-i",
        title: "Trzej muszkieterowie, tom pierwszy",
        author: "Aleksander Dumas (ojciec)",
        source: .wolneLektury,
        chapters: [
            AudioBookChapter(
                id: "trzej-muszkieterowie-tom-i-1",
                title: "01. Tom I, Rozdzial I",
                number: 1,
                reader: "Aleksander Bauman",
                listenURL: "https://wolnelektury.pl/media/book/mp3/tom-i-001.mp3"
            ),
            AudioBookChapter(
                id: "trzej-muszkieterowie-tom-i-2",
                title: "02. Tom I, Rozdzial II",
                number: 2,
                reader: "Aleksander Bauman",
                listenURL: "https://wolnelektury.pl/media/book/mp3/tom-i-002.mp3"
            ),
        ],
        totalDurationSeconds: 1_200,
        language: "Polish",
        narrators: ["Aleksander Bauman"],
        publisher: "Wolne Lektury"
    )
    let secondVolume = AudioBook(
        id: "wolnelektury-aleksander-dumas-trzej-muszkieterowie-tom-2",
        title: "Trzej muszkieterowie, tom drugi",
        author: "Aleksander Dumas (ojciec)",
        source: .wolneLektury,
        chapters: [
            AudioBookChapter(
                id: "aleksander-dumas-trzej-muszkieterowie-tom-2-1",
                title: "01. Tom II, Rozdzial I",
                number: 1,
                reader: "Aleksander Bauman",
                listenURL: "https://wolnelektury.pl/media/book/mp3/tom-ii-001.mp3"
            ),
        ],
        totalDurationSeconds: 1_800,
        language: "Polish",
        narrators: ["Aleksander Bauman"],
        publisher: "Wolne Lektury"
    )

    #expect(WolneLekturyCatalogClient.audioChildSlugs(from: data) == [
        "trzej-muszkieterowie-tom-i",
        "aleksander-dumas-trzej-muszkieterowie-tom-2",
    ])

    let book = try WolneLekturyCatalogClient.parseDetail(
        slug: "aleksander-dumas-trzej-muszkieterowie",
        data: data,
        childBooks: [firstVolume, secondVolume]
    )

    #expect(book.id == "wolnelektury-aleksander-dumas-trzej-muszkieterowie")
    #expect(book.title == "Trzej muszkieterowie")
    #expect(book.chapters.count == 3)
    #expect(book.chapters.map(\.number) == [1, 2, 3])
    #expect(book.chapters.map(\.listenURL) == [
        "https://wolnelektury.pl/media/book/mp3/tom-i-001.mp3",
        "https://wolnelektury.pl/media/book/mp3/tom-i-002.mp3",
        "https://wolnelektury.pl/media/book/mp3/tom-ii-001.mp3",
    ])
    #expect(book.totalDurationSeconds == 3_000)
    #expect(book.narrators == ["Aleksander Bauman"])
}

@Test func persistentLibraryStoreAlwaysSeedsTheJungle() async throws {
    let key = "LibriVoxMobile.tests.library.\(UUID().uuidString)"
    UserDefaults.standard.removeObject(forKey: key)
    let store = PersistentLibraryStore(defaultsKey: key)
    let books = await store.snapshot()
    #expect(books.first?.id == AudioBook.theJungle.id)
    UserDefaults.standard.removeObject(forKey: key)
}

@Test func readAlongSourceTextBuildsSearchableEstimatedSegments() {
    let book = AudioBook(
        id: "read-test",
        title: "Read Test",
        author: "Author",
        chapters: [
            AudioBookChapter(id: "read-test-1", title: "Opening", number: 1, durationSeconds: 120),
            AudioBookChapter(id: "read-test-2", title: "Closing", number: 2, durationSeconds: 60),
        ]
    )
    let readAlong = ReadAlongBook.sourceText(
        for: book,
        sourceURL: "https://example.test/read.txt",
        text: """
        First paragraph about factories.

        Second paragraph about Packingtown.

        Third paragraph about a family.

        Fourth paragraph at the end.
        """
    )

    #expect(readAlong.syncQuality == .estimated)
    #expect(readAlong.supportsPlaybackSync)
    #expect(readAlong.chapters.count == 2)
    #expect(readAlong.search("Packingtown").count == 1)
    #expect(readAlong.activeSegment(chapterID: "read-test-1", positionMilliseconds: 1_000)?.text.contains("First") == true)
}

@Test func readAlongSearchReturnsEveryMatchWithSelectableRanges() {
    let book = AudioBook(
        id: "read-search-ranges",
        title: "Search Ranges",
        author: "Author",
        chapters: [
            AudioBookChapter(id: "read-search-ranges-1", title: "Chapter", number: 1, durationSeconds: 60),
        ]
    )
    let readAlong = ReadAlongBook.sourceText(
        for: book,
        sourceURL: "https://example.test/read.txt",
        text: "Antek asked about Antek, and then ANTEK answered."
    )

    let hits = readAlong.search("antek")
    #expect(hits.count == 3)
    #expect(hits.allSatisfy { ($0.matchRange?.length ?? 0) > 0 })
}

@Test func readAlongSourceTextRemovesWolneHeaderAndFooter() {
    let book = AudioBook(
        id: "wolnelektury-kamizelka",
        title: "Kamizelka",
        author: "Bolesław Prus",
        source: .wolneLektury,
        chapters: [
            AudioBookChapter(id: "kamizelka-1", title: "Kamizelka", number: 1, durationSeconds: 1_341),
        ]
    )
    let readAlong = ReadAlongBook.sourceText(
        for: book,
        sourceURL: "https://wolnelektury.pl/media/book/txt/kamizelka.txt",
        text: "Bolesław Prus\r\nKamizelka\r\nISBN 978-83-288-2670-0\r\n\r\nNiektórzy ludzie mają pociąg do zbierania osobliwości.\r\n\r\n-----\r\n\r\nWewnętrzny podział nie kończy opowiadania.\r\n\r\nCzy znowu zejdą się kiedy oboje?\r\n\r\n----- Ta lektura, podobnie jak tysiące innych, dostępna jest na stronie wolnelektury.pl.\r\nISBN 978-83-288-1715-9"
    )

    let readerText = readAlong.chapters.flatMap(\.segments).map(\.text).joined(separator: " ")
    #expect(readerText.contains("Niektórzy ludzie"))
    #expect(readerText.contains("Wewnętrzny podział"))
    #expect(readerText.contains("Czy znowu zejdą się"))
    #expect(readerText.localizedCaseInsensitiveContains("ISBN") == false)
    #expect(readerText.contains("Ta lektura jest dostępna") == false)
}

@Test func readAlongSourceTextUsesChapterHeadingsAndSkipsRawContents() {
    let book = AudioBook(
        id: "chaptered-read-test",
        title: "Chaptered Read Test",
        author: "Author",
        chapters: [
            AudioBookChapter(id: "chaptered-read-test-1", title: "Opening", number: 1, durationSeconds: 120),
            AudioBookChapter(id: "chaptered-read-test-2", title: "Closing", number: 2, durationSeconds: 60),
        ]
    )
    let readAlong = ReadAlongBook.sourceText(
        for: book,
        sourceURL: "https://example.test/read.txt",
        text: """
        Title Page

        Contents

        CHAPTER I CHAPTER II

        CHAPTER I

        First chapter opening.

        CHAPTER II

        Second chapter opening.
        """
    )

    let firstChapterText = readAlong.chapters[0].segments.map(\.text).joined(separator: " ")
    let secondChapterText = readAlong.chapters[1].segments.map(\.text).joined(separator: " ")
    #expect(firstChapterText.contains("First chapter opening."))
    #expect(firstChapterText.contains("Contents") == false)
    #expect(firstChapterText.contains("CHAPTER II") == false)
    #expect(secondChapterText.contains("Second chapter opening."))
}

@Test func readAlongSourceTextFallsBackWhenSourceHeadingCountDoesNotMatchAudioChapters() {
    let book = AudioBook(
        id: "partial-heading-read-test",
        title: "Partial Heading Read Test",
        author: "Author",
        chapters: [
            AudioBookChapter(id: "partial-heading-read-test-1", title: "Opening", number: 1, durationSeconds: 60),
            AudioBookChapter(id: "partial-heading-read-test-2", title: "Middle", number: 2, durationSeconds: 60),
            AudioBookChapter(id: "partial-heading-read-test-3", title: "Closing", number: 3, durationSeconds: 60),
        ]
    )
    let readAlong = ReadAlongBook.sourceText(
        for: book,
        sourceURL: "https://example.test/read.txt",
        text: """
        CHAPTER I

        First source block.

        Second source block.

        Third source block.
        """
    )

    #expect(readAlong.chapters.count == 3)
    #expect(readAlong.chapters.allSatisfy { $0.segments.isEmpty == false })
    #expect(readAlong.chapters.map { $0.segments.map(\.text).joined(separator: " ") }
        .joined(separator: " ")
        .contains("Third source block."))
}

@Test func readAlongFallbackUsesBookDescriptionWhenNoSourceTextExists() {
    let book = AudioBook(
        id: "fallback-test",
        title: "Fallback Test",
        author: "Author",
        description: "A short public-domain description.",
        chapters: [
            AudioBookChapter(id: "fallback-test-1", title: "Only Chapter", number: 1, durationSeconds: 30),
        ]
    )
    let readAlong = ReadAlongBook.fallback(for: book)

    #expect(readAlong.syncQuality == .staticText)
    #expect(!readAlong.supportsPlaybackSync)
    #expect(readAlong.chapters.first?.segments.first?.text == "A short public-domain description.")
}

@Test func openLibraryAuthorClientResolvesAlternateNameAndBiography() async throws {
    let transport = AuthorFixtureTransport(responses: [
        "/search/authors.json": #"""
        {"docs":[
          {"key":"OL404A","name":"Someone Else"},
          {"key":"OL123A","name":"Bolesław Prus","alternate_names":["Aleksander Głowacki"],"birth_date":"20 August 1847","death_date":"19 May 1912","top_work":"The Doll","top_subjects":["Polish fiction"],"work_count":42}
        ]}
        """#,
        "/authors/OL123A.json": #"""
        {"bio":{"value":"A Polish novelist and leading figure in the history of Polish literature."}}
        """#,
        "/w/api.php": #"""
        {"query":{"pages":[
          {"pageid":123,"title":"Bolesław Prus","thumbnail":{"source":"https://upload.wikimedia.org/prus.jpg","width":600,"height":800},"terms":{"description":["Polish novelist and journalist"]}}
        ]}}
        """#,
    ])
    let client = OpenLibraryAuthorClient(transport: transport)

    let profile = await client.profile(for: "Aleksander Glowacki")

    #expect(profile?.id == "OL123A")
    #expect(profile?.name == "Bolesław Prus")
    #expect(profile?.biography?.contains("Polish novelist") == true)
    #expect(profile?.portraitURL.absoluteString == "https://covers.openlibrary.org/a/olid/OL123A-L.jpg?default=false")
    #expect(profile?.fallbackPortraitURL?.absoluteString == "https://upload.wikimedia.org/prus.jpg")
    #expect(profile?.sourceURL.absoluteString == "https://openlibrary.org/authors/OL123A")
}

@Test func openLibraryAuthorClientRejectsAmbiguousNonExactResult() async {
    let transport = AuthorFixtureTransport(responses: [
        "/search/authors.json": #"{"docs":[{"key":"OL404A","name":"Prus Something"}]}"#,
    ])
    let client = OpenLibraryAuthorClient(transport: transport)

    #expect(await client.profile(for: "Bolesław Prus") == nil)
}

@Test func authorBookSorterUsesComparablePublicationAndPopularityMetadata() {
    let older = AudioBook(id: "older", title: "Alpha", author: "Author")
    let newer = AudioBook(id: "newer", title: "Zulu", author: "Author")
    let unknown = AudioBook(id: "unknown", title: "Middle", author: "Author")
    let metrics = [
        AuthorWorkMetric(title: "Alpha", firstPublishYear: 1901, popularityCount: 900),
        AuthorWorkMetric(title: "Zulu", firstPublishYear: 1922, popularityCount: 300),
    ]

    #expect(AuthorBookSorter.availableSorts(for: [newer, unknown, older], metrics: metrics) == [
        .alphabetical,
        .mostRecent,
        .mostPopular,
    ])
    #expect(AuthorBookSorter.sorted([older, unknown, newer], by: .alphabetical, metrics: metrics).map(\.id) == [
        "older", "unknown", "newer",
    ])
    #expect(AuthorBookSorter.sorted([older, unknown, newer], by: .mostRecent, metrics: metrics).map(\.id) == [
        "newer", "older", "unknown",
    ])
    #expect(AuthorBookSorter.sorted([older, unknown, newer], by: .mostPopular, metrics: metrics).map(\.id) == [
        "older", "newer", "unknown",
    ])
}

@Test func openLibraryWorkMetricsDecodeCountsUsedForConditionalSorts() async {
    let transport = AuthorFixtureTransport(responses: [
        "/search.json": #"""
        {"docs":[
          {"title":"Antek","first_publish_year":1880,"ratings_count":8,"want_to_read_count":20,"already_read_count":12,"currently_reading_count":2}
        ]}
        """#,
    ])
    let client = OpenLibraryAuthorClient(transport: transport)

    let metrics = await client.workMetrics(for: "Bolesław Prus")

    #expect(metrics.count == 1)
    #expect(metrics.first?.title == "Antek")
    #expect(metrics.first?.firstPublishYear == 1880)
    #expect(metrics.first?.popularityCount == 42)
}

private struct TestCatalogClient: CatalogSourceClient {
    var source: BookSource
    var books: [AudioBook]

    func featuredBooks(limit: Int) async throws -> [AudioBook] {
        Array(books.prefix(limit))
    }

    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(source) else { return [] }
        return books.filter { book in
            book.title.localizedCaseInsensitiveContains(request.query) ||
                book.author.localizedCaseInsensitiveContains(request.query)
        }
    }

    func hydrate(_ book: AudioBook) async throws -> AudioBook {
        book
    }
}

private struct AuthorFixtureTransport: CatalogHTTPTransport {
    var responses: [String: String]

    func response(for request: URLRequest) async throws -> CatalogHTTPResponse {
        guard let path = request.url?.path,
              let body = responses[path],
              let data = body.data(using: .utf8)
        else { return CatalogHTTPResponse(data: Data(), statusCode: 404) }
        return CatalogHTTPResponse(data: data, statusCode: 200, headers: ["content-type": "application/json"])
    }
}

private struct DelayedTestCatalogClient: CatalogSourceClient {
    var source: BookSource
    var delayNanoseconds: UInt64
    var books: [AudioBook]

    func featuredBooks(limit: Int) async throws -> [AudioBook] {
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return Array(books.prefix(limit))
    }

    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(source) else { return [] }
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return books.rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
    }

    func hydrate(_ book: AudioBook) async throws -> AudioBook {
        book
    }
}

private struct FailingTestCatalogClient: CatalogSourceClient {
    var source: BookSource

    func featuredBooks(limit: Int) async throws -> [AudioBook] {
        throw URLError(.timedOut)
    }

    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        throw URLError(.timedOut)
    }

    func hydrate(_ book: AudioBook) async throws -> AudioBook {
        throw URLError(.timedOut)
    }
}

private func temporaryStoreURL(named fileName: String) -> URL {
    FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxJSONStoreTests-\(UUID().uuidString)", isDirectory: true)
        .appendingPathComponent(fileName)
}
