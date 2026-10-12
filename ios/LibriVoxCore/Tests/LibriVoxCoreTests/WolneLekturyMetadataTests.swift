import Foundation
import Testing
@testable import LibriVoxCore

@Test func wolneWrittenChapterNamesMatchVolumeAndOrdinalWithoutChangingPlaybackIdentity() {
    let book = AudioBook(id: "wolnelektury-lalka", title: "Lalka", author: "Bolesław Prus", source: .wolneLektury, chapters: [
        AudioBookChapter(id: "second", title: "Tom II, Rozdział I", number: 21, reader: "Albert Osik", listenURL: "https://example.com/second.mp3"),
        AudioBookChapter(id: "first", title: "Tom I, Rozdział II", number: 2),
        AudioBookChapter(id: "missing", title: "Tom I, Rozdział III", number: 3),
        AudioBookChapter(id: "named", title: "An existing chapter name", number: 4),
    ])
    let xml = """
    <utwor><powiesc>
    <naglowek_czesc>Tom I</naglowek_czesc>
    <naglowek_rozdzial>I. Jak wygląda firma J. Mincel?</naglowek_rozdzial>
    <naglowek_rozdzial>II. Rządy <wyroznienie>starego</wyroznienie> subiekta<pa>A footnote</pa></naglowek_rozdzial>
    <naglowek_czesc>Tom II</naglowek_czesc>
    <naglowek_rozdzial>I. Pamiętnik starego subiekta</naglowek_rozdzial>
    </powiesc></utwor>
    """
    let enriched = WolneLekturyMetadata.addingWrittenChapterNames(to: book, fromXML: xml)
    #expect(enriched.chapters.map(\.title) == [
        "Tom II, Rozdział I — Pamiętnik starego subiekta",
        "Tom I, Rozdział II — Rządy starego subiekta",
        "Tom I, Rozdział III", "An existing chapter name",
    ])
    #expect(enriched.chapters.map(\.id) == book.chapters.map(\.id))
    #expect(enriched.chapters.map(\.number) == book.chapters.map(\.number))
    #expect(enriched.chapters.map(\.listenURL) == book.chapters.map(\.listenURL))
    #expect(enriched.chapters.map(\.reader) == book.chapters.map(\.reader))
    #expect(enriched.normalizingWolneChapterTitles() == enriched)
    #expect(WolneLekturyMetadata.addingWrittenChapterNames(to: enriched, fromXML: xml) == enriched)
}

@Test func wolneWrittenChapterNamesRejectMalformedOrAmbiguousHeadings() {
    let book = AudioBook(id: "test", title: "Test", author: "Author", source: .wolneLektury, chapters: [
        AudioBookChapter(id: "1", title: "Rozdział I", number: 1),
    ])
    #expect(WolneLekturyMetadata.addingWrittenChapterNames(to: book, fromXML: "<utwor>") == book)
    let duplicate = "<utwor><naglowek_rozdzial>I. First</naglowek_rozdzial><naglowek_rozdzial>I. Another</naglowek_rozdzial></utwor>"
    #expect(WolneLekturyMetadata.addingWrittenChapterNames(to: book, fromXML: duplicate) == book)
}

@Test func wolneLalkaChapterNamesKeepOnlyVolumeAndChapter() throws {
    let book = try WolneLekturyCatalogClient.parseDetail(slug: "lalka-tom-pierwszy", data: wolneVolumeData(1))
    #expect(book.chapters.map(\.title) == ["Tom I, Rozdział I", "Tom I, Rozdział II"])
    #expect(book.chapters.first?.displayTitle == "01: Tom I, Rozdział I")
    #expect(book.chapters.map(\.id) == ["lalka-tom-pierwszy-1", "lalka-tom-pierwszy-2"])
    #expect(book.coverImageURL == "https://wolnelektury.pl/media/book/cover_api_thumb/lalka.jpg")
    #expect(book.fullCoverImageURL == "https://wolnelektury.pl/media/book/cover/lalka.jpg")
}

@Test func wolneLalkaParentOrdersVolumesAndKeepsChapterIdentities() throws {
    let data = Data(#"{"title":"Lalka","authors":[{"name":"Bolesław Prus"}],"children":[{"slug":"lalka-tom-drugi","title":"Lalka, tom drugi","has_audio":true},{"slug":"lalka-tom-pierwszy","title":"Lalka, tom pierwszy","has_audio":true}]}"#.utf8)
    #expect(WolneLekturyCatalogClient.audioChildSlugs(from: data) == ["lalka-tom-pierwszy", "lalka-tom-drugi"])
    let first = try WolneLekturyCatalogClient.parseDetail(slug: "lalka-tom-pierwszy", data: wolneVolumeData(1))
    let second = try WolneLekturyCatalogClient.parseDetail(slug: "lalka-tom-drugi", data: wolneVolumeData(2))
    let parent = try WolneLekturyCatalogClient.parseDetail(slug: "lalka", data: data, childBooks: [second, first])
    #expect(parent.chapters.map(\.number) == [1, 2, 3, 4])
    #expect(parent.chapters.map(\.title) == ["Tom I, Rozdział I", "Tom I, Rozdział II", "Tom II, Rozdział I", "Tom II, Rozdział II"])
    #expect(parent.chapters.map(\.id) == first.chapters.map(\.id) + second.chapters.map(\.id))
}

@Test func wolneSavedChapterNamesAreNormalizedWithoutChangingProgressIdentity() {
    let book = AudioBook(id: "wolnelektury-lalka", title: "Lalka", author: "Bolesław Prus", source: .wolneLektury, chapters: [
        AudioBookChapter(id: "lalka-tom-drugi-1", title: "01. Boleslaw Prus, Lalka, Lalka, tom drugi, Tom II, Rozdział I", number: 21),
        AudioBookChapter(id: "lalka-tom-drugi-2", title: "Tom II, Rozdział II", number: 22),
    ])
    let cleaned = book.normalizingWolneChapterTitles()
    #expect(cleaned.chapters.map(\.title) == ["Tom II, Rozdział I", "Tom II, Rozdział II"])
    #expect(cleaned.chapters.map(\.id) == book.chapters.map(\.id))
    #expect(cleaned.chapters.map(\.number) == [21, 22])
    #expect(cleaned.normalizingWolneChapterTitles() == cleaned)
}

@Test func wolneSingleRecordingUsesTheBookTitleAndKeepsLiteralChapterText() throws {
    let data = Data(#"{"title":"Antek","authors":[{"name":"Bolesław Prus"}],"media":[{"type":"mp3","name":"Bolesław Prus, Antek","url":"https://wolnelektury.pl/antek.mp3"}]}"#.utf8)
    #expect(try WolneLekturyCatalogClient.parseDetail(slug: "antek", data: data).chapters.first?.title == "Antek")
    #expect(WolneLekturyMetadata.chapterTitle("Powrót, w domu", bookTitle: "Lalka", authors: ["Bolesław Prus"], number: 1, chapterCount: 2) == "Powrót, w domu")
    #expect(AudioBook.theJungle.normalizingWolneChapterTitles() == AudioBook.theJungle)
}

@Test func wolneSupplementalAudioDoesNotRenumberSavedChapterIdentities() throws {
    let data = Data(#"{"title":"Antek","authors":[{"name":"Bolesław Prus"}],"media":[{"type":"mp3","name":"Stopka","url":"https://wolnelektury.pl/stopka.mp3"},{"type":"mp3","name":"Bolesław Prus, Antek","url":"https://wolnelektury.pl/antek.mp3"}]}"#.utf8)
    let book = try WolneLekturyCatalogClient.parseDetail(slug: "antek", data: data)
    #expect(book.chapters.map(\.id) == ["antek-2"])
    #expect(book.chapters.map(\.number) == [1])
    #expect(book.chapters.map(\.title) == ["Antek"])
}

@Test func coverArtworkCacheRejectsInvalidPrimaryAndUsesRealFallbackImage() async throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("cover-validation-\(UUID())")
    defer { try? FileManager.default.removeItem(at: directory) }
    let cache = CoverArtworkCache(directoryURL: directory)
    let primary = URL(string: "https://wolnelektury.pl/broken.jpg")!
    let fallback = URL(string: "https://wolnelektury.pl/thumbnail.jpg")!
    let png = Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==")!
    try await cache.store(png, for: fallback)
    let invalidURL = await cache.cacheFileURL(for: primary)
    try Data("<html>temporary error</html>".utf8).write(to: invalidURL)
    #expect(await cache.cachedData(for: [primary, fallback]) == png)
    try await cache.store(Data("bad data".utf8), for: URL(string: "https://wolnelektury.pl/new.jpg")!)
    #expect(await cache.cachedData(for: [URL(string: "https://wolnelektury.pl/new.jpg")!]) == nil)
}

private func wolneVolumeData(_ number: Int) throws -> Data {
    let ordinal = number == 1 ? "pierwszy" : "drugi"
    let roman = number == 1 ? "I" : "II"
    let media = ["I", "II", "stopka"].enumerated().map { index, chapter in
        ["type": "mp3", "name": "0\(index + 1). Bolesław Prus, Lalka, Lalka, tom \(ordinal), \(chapter == "stopka" ? chapter : "Tom \(roman), Rozdział \(chapter)")", "url": "https://wolnelektury.pl/lalka-\(roman)-\(chapter).mp3"]
    }
    return try JSONSerialization.data(withJSONObject: [
        "title": "Lalka, tom \(ordinal)", "authors": [["name": "Bolesław Prus"]],
        "parent": ["title": "Lalka"], "media": media,
        "cover": "https://wolnelektury.pl/media/book/cover/lalka.jpg",
        "simple_thumb": "https://wolnelektury.pl/media/book/cover_api_thumb/lalka.jpg",
    ])
}

@Test func liveWolneLalkaMetadataUsesCleanOrderedVolumesAndValidArtwork() async throws {
    guard ProcessInfo.processInfo.environment["RUN_LIVE_CATALOG_TESTS"] == "1" else { return }
    let stub = AudioBook(id: "wolnelektury-lalka", title: "Lalka", author: "Bolesław Prus", source: .wolneLektury)
    let book = try await WolneLekturyCatalogClient().hydrate(stub)
    #expect(book.chapters.first?.title == "Tom I, Rozdział I — Jak wygląda firma J. Mincel i S. Wokulski przez szkło butelek?")
    #expect(book.chapters.contains { $0.title == "Tom II, Rozdział I — Pamiętnik starego subiekta" })
    #expect(book.chapters.allSatisfy { !$0.title.contains("Bolesław Prus") && !$0.title.contains("Lalka") })
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("live-wolne-art-\(UUID())")
    defer { try? FileManager.default.removeItem(at: directory) }
    let cache = CoverArtworkCache(directoryURL: directory)
    let urls = [book.fullCoverImageURL, book.coverImageURL].compactMap { $0 }.compactMap(URL.init(string:))
    #expect(!urls.isEmpty)
    #expect(await cache.data(for: urls) != nil)
}
