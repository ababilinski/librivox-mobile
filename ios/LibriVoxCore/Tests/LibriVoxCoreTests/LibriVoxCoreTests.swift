import Testing
@testable import LibriVoxCore

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
    #expect(settings.automaticSearchCachingEnabled)
    #expect(settings.themeMode == .system)
    #expect(settings.coverArtDisplayMode == .fit)
    #expect(settings.defaultPlaybackSpeed == 1.0)
}
