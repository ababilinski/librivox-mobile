import Foundation
import Testing
@testable import LibriVoxCore

@Test func retiredCatalogSettingsKeepOtherChoicesAndNeverEnableReplacementSources() throws {
    let legacy = Data(#"""
    {"enabledSources":["gutendex","wolneLektury","lit2Go"],
     "sourcePriority":["gutendex","wolneLektury","lit2Go","libriVox"],
     "preferredLanguages":["polish"],"themeMode":"dark"}
    """#.utf8)
    var settings = try JSONDecoder().decode(UserSettings.self, from: legacy)
    settings.normalizeForAvailableSources()
    #expect(settings.enabledSources == [.wolneLektury, .lit2Go])
    #expect(settings.sourcePriority == [.wolneLektury, .lit2Go, .libriVox])
    #expect(settings.orderedEnabledSources == [.wolneLektury, .lit2Go])
    #expect(settings.preferredLanguages == [.polish])
    #expect(settings.themeMode == .dark)
    let persisted = try JSONEncoder().encode(settings)
    #expect(!String(decoding: persisted, as: UTF8.self).contains("gutendex"))

    for sources in [#"["gutendex"]"#, "[]"] {
        var empty = try JSONDecoder().decode(
            UserSettings.self,
            from: Data("{\"enabledSources\":\(sources)}".utf8)
        )
        empty.normalizeForAvailableSources()
        #expect(empty.enabledSources.isEmpty)
        #expect(empty.orderedEnabledSources.isEmpty)
    }
    #expect(UserSettings().enabledSources == [.libriVox])
}

@Test func retiredCatalogCannotRunEvenWhenExplicitlyRequestedOrInjected() async throws {
    let calls = RetiredCatalogCallRecorder()
    let clients: [any CatalogSourceClient] = [
        RecordingCatalogClient(source: .gutendex, calls: calls),
        RecordingCatalogClient(source: .lit2Go, calls: calls),
    ]
    let catalog = AggregateCatalogClient(clients: clients)
    let featured = try await catalog.featuredBooks(limit: 10, sourcePriority: [.gutendex, .lit2Go])
    #expect(featured.map(\.source) == [.lit2Go])
    var request = CatalogSearchRequest(query: "Alice", field: .title, languages: [.all])
    // Public request fields can be restored or changed after initialization.
    request.sources = [.gutendex, .lit2Go]
    request.sourcePriority = [.gutendex, .lit2Go]
    let results = try await catalog.search(request)
    #expect(results.map(\.source) == [.lit2Go])
    let legacyBook = savedGutendexBook()
    #expect(try await catalog.hydrate(legacyBook) == legacyBook)

    let directory = retiredCatalogTemporaryDirectory()
    defer { try? FileManager.default.removeItem(at: directory) }
    let coordinator = CatalogSearchCoordinator(
        clients: clients,
        cache: JSONCatalogSearchCache(fileURL: directory.appendingPathComponent("search.json"))
    )
    let snapshot = await coordinator.search(request)
    #expect(snapshot.hits.map(\.book.source) == [.lit2Go])
    #expect(!snapshot.outcomes.keys.contains { $0.hasPrefix("gutendex-") })
    #expect(await calls.entries == ["lit2Go:featured", "lit2Go:search", "lit2Go:search"])
    #expect(CatalogSourceCapabilities.capabilities(for: .gutendex) == nil)

    let retiredOnly = CatalogSearchPlan(request: CatalogSearchRequest(
        query: "Alice", field: .all, languages: [.all], sources: [.gutendex]
    ))
    #expect(retiredOnly.operations.isEmpty)
    #expect(retiredOnly.request.sources.isEmpty)
}

@Test func retiredCatalogHealthNeverRequestsItsOldEndpoint() async {
    let calls = RetiredCatalogCallRecorder()
    let probe = CatalogSourceHealthProbe(
        transport: RetiredCatalogHealthTransport(calls: calls),
        providerURLs: [.gutendex: URL(string: "https://gutendex.com/")!]
    )
    let snapshot = await probe.check(sources: [.gutendex], forceRefresh: true)
    #expect(snapshot.statuses.isEmpty)
    #expect(await calls.entries.isEmpty)
    #expect(CatalogSourceHealthContract.providerURLs[.gutendex] == nil)
    #expect(CatalogSourceHealthContract.recognizableTitleMarkers[.gutendex] == nil)
    #expect(!BookSource.defaultPublicCatalogPriority.contains(.gutendex))
}

@Test func savedGutendexLibraryRecordsAndPlaybackReferencesRemainReadable() async throws {
    let directory = retiredCatalogTemporaryDirectory()
    defer { try? FileManager.default.removeItem(at: directory) }
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let book = savedGutendexBook()
    let fileURL = directory.appendingPathComponent("library-books.json")
    let data = try JSONEncoder().encode(JSONStoreEnvelope(schemaVersion: 1, payload: [book]))
    #expect(String(decoding: data, as: UTF8.self).contains("\"source\":\"gutendex\""))
    try data.write(to: fileURL)
    let library = PersistentLibraryStore(
        defaultsKey: "RetiredCatalogCompatibility-\(UUID().uuidString)",
        store: JSONStoreFile<[AudioBook]>(fileURL: fileURL)
    )
    let loaded = try #require(await library.snapshot().first { $0.id == book.id })
    #expect(loaded == book)
    #expect(loaded.chapters.first?.localFileName == "alice-complete.mp3")
    #expect(loaded.chapters.first?.downloadState == .downloaded)
    #expect(loaded.canReadSourceText)
    #expect(try await AggregateCatalogClient().hydrate(loaded) == loaded)
    let localResults = UnifiedBookSearch.results(
        libraryBooks: [loaded], catalogBooks: [], query: "Alice", field: .title,
        languages: [.all], sources: [.gutendex], scope: .library
    )
    #expect(localResults.map(\.book) == [book])
    #expect(localResults.first?.isInLibrary == true)

    let chapterID = try #require(book.chapters.first?.id)
    let queue = [QueueEntry(bookID: book.id, chapterID: chapterID)]
    let progress = [PlaybackProgress(
        bookID: book.id, chapterID: chapterID, positionSeconds: 123,
        durationSeconds: 600, completedChapterIDs: ["gutendex-19573-previous"]
    )]
    let queueStore = JSONStoreFile<[QueueEntry]>(fileURL: directory.appendingPathComponent("queue.json"))
    let progressStore = JSONStoreFile<[PlaybackProgress]>(fileURL: directory.appendingPathComponent("progress.json"))
    try await queueStore.save(queue)
    try await progressStore.save(progress)
    #expect(await queueStore.load() == queue)
    #expect(await progressStore.load() == progress)
}

private func savedGutendexBook() -> AudioBook {
    AudioBook(
        id: "gutendex-19573", title: "Alice's Adventures in Wonderland", author: "Lewis Carroll",
        source: .gutendex, libraryStatus: .inLibrary, isFavorite: true,
        chapters: [AudioBookChapter(
            id: "gutendex-19573-audio", title: "Complete audiobook", number: 1,
            durationSeconds: 600, listenURL: "https://www.gutenberg.org/files/19573/audio.mp3",
            localFileName: "alice-complete.mp3", downloadState: .downloaded
        )],
        gutenbergURL: "https://www.gutenberg.org/ebooks/19573", language: "English"
    )
}

private func retiredCatalogTemporaryDirectory() -> URL {
    FileManager.default.temporaryDirectory.appendingPathComponent(
        "LibriVoxRetiredCatalogTests-\(UUID().uuidString)", isDirectory: true
    )
}

private actor RetiredCatalogCallRecorder {
    private(set) var entries: [String] = []
    func record(_ entry: String) { entries.append(entry) }
}

private struct RecordingCatalogClient: CatalogSourceClient {
    var source: BookSource
    var calls: RetiredCatalogCallRecorder
    func featuredBooks(limit: Int) async throws -> [AudioBook] {
        await calls.record("\(source.rawValue):featured")
        return [AudioBook(id: source.rawValue, title: "Alice", author: "Lewis Carroll", source: source)]
    }
    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        await calls.record("\(source.rawValue):search")
        return [AudioBook(id: source.rawValue, title: "Alice", author: "Lewis Carroll", source: source)]
    }
    func hydrate(_ book: AudioBook) async throws -> AudioBook {
        await calls.record("\(source.rawValue):hydrate")
        return book
    }
}

private struct RetiredCatalogHealthTransport: CatalogHTTPTransport {
    var calls: RetiredCatalogCallRecorder
    func response(for request: URLRequest) async throws -> CatalogHTTPResponse {
        await calls.record(request.url?.absoluteString ?? "missing URL")
        throw URLError(.cannotConnectToHost)
    }
}
