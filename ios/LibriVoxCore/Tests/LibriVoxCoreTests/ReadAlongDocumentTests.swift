import Foundation
import Testing
import ZIPFoundation
@testable import LibriVoxCore

@Test func readerEpubPreservesNovelAfterTitlePageAndKeepsChapterBoundaries() throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: directory) }
    let source = directory.appendingPathComponent("source")
    let files = [
        "META-INF/container.xml": "<container><rootfiles><rootfile full-path=\"EPUB/content.opf\"/></rootfiles></container>",
        "EPUB/content.opf": """
        <package><manifest>
          <item id="title" href="title.xhtml" media-type="application/xhtml+xml"/>
          <item id="divider" href="part1.xhtml" media-type="application/xhtml+xml"/>
          <item id="first" href="part2.xhtml" media-type="application/xhtml+xml"/>
          <item id="fund" href="fund0.xhtml" media-type="application/xhtml+xml"/>
          <item id="second" href="part3.xhtml" media-type="application/xhtml+xml"/>
        </manifest><spine><itemref idref="title"/><itemref idref="divider"/>
          <itemref idref="first"/><itemref idref="fund"/><itemref idref="second"/>
        </spine></package>
        """,
        "EPUB/title.xhtml": "<html><body><h1>Lalka</h1><p>Ta lektura, podobnie jak tysiące innych, jest dostępna na stronie wolnelektury.pl.</p><p>ISBN 123</p></body></html>",
        "EPUB/part1.xhtml": "<html><body><h2>Tom I</h2></body></html>",
        "EPUB/part2.xhtml": "<html><head><title>WolneLektury.pl</title></head><body><h2>I. Początek</h2><p>Pierwszy akapit powieści.<sup>12</sup></p><p>Drugi akapit powieści.</p></body></html>",
        "EPUB/fund0.xhtml": "<html><body><p>Wesprzyj Wolne Lektury</p></body></html>",
        "EPUB/part3.xhtml": "<html><body><h2>II. Dalej</h2><p>Cały drugi rozdział jest tutaj.</p></body></html>",
    ]
    for (path, text) in files {
        let url = source.appendingPathComponent(path)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try text.write(to: url, atomically: true, encoding: .utf8)
    }
    let archive = directory.appendingPathComponent("book.epub")
    try FileManager.default.zipItem(at: source, to: archive, shouldKeepParent: false)
    let book = readerBook(durations: [0, 0])
    let parsed = try #require(try WolneStaticEpubParser().parse(book: book, fileURL: archive, sourceURL: "https://example.test/book.epub"))
    #expect(parsed.hasSourceText)
    #expect(parsed.chapters.count == 2)
    #expect(parsed.chapters[0].segments.map(\.text).contains("Pierwszy akapit powieści."))
    #expect(parsed.chapters[1].segments.map(\.text).contains("Cały drugi rozdział jest tutaj."))
    let text = parsed.chapters.flatMap(\.segments).map(\.text).joined(separator: " ")
    #expect(!text.contains("ISBN"))
    #expect(!text.contains("Wesprzyj"))
    #expect(!text.contains("WolneLektury.pl"))
    #expect(!parsed.supportsPlaybackSync)
}

@Test func readerUsesLiveDurationForMissingMetadataAndWeightsParagraphs() {
    let book = readerBook(durations: [0, 0])
    let document = ReadAlongBook.sourceText(for: book, sourceURL: "https://example.test/book.txt", text: """
    CHAPTER I

    One.

    A considerably longer paragraph with many more spoken words.

    CHAPTER II

    This is the other chapter.
    """)
    #expect(!document.supportsPlaybackSync)
    let updated = document.applyingPlaybackDuration(60, chapterID: "chapter-1")
    #expect(updated.syncQuality == .estimated)
    #expect(updated.supportsPlaybackSync)
    #expect(updated.chapters[0].segments.first?.startMilliseconds == 0)
    #expect(updated.chapters[0].segments.first!.endMilliseconds! < 10_000)
    #expect(updated.chapters[0].segments.last?.endMilliseconds == 60_000)
    #expect(updated.chapters[1].segments.first?.startMilliseconds == nil)
    #expect(updated.activeSegment(chapterID: "chapter-1", positionMilliseconds: 10_000)?.text.hasPrefix("A considerably") == true)
    #expect(updated.activeSegment(chapterID: "chapter-2", positionMilliseconds: 0) == nil)
    #expect(updated.activeSegment(chapterID: "unknown", positionMilliseconds: 0) == nil)
}

@Test func readerDoesNotReplaceSourceTimingOrInventTextForEmptyChapters() {
    let book = readerBook(durations: [60, 60])
    let sparse = ReadAlongBook.sourceText(for: book, sourceURL: "https://example.test/book.txt", text: "The only source paragraph.")
    #expect(sparse.chapters.flatMap(\.segments).map(\.text) == ["The only source paragraph."])
    #expect(sparse.chapters.contains { $0.segments.isEmpty })
    var timed = sparse
    timed.syncQuality = .timed
    #expect(timed.applyingPlaybackDuration(120, chapterID: "chapter-2") == timed)
    #expect(sparse.applyingPlaybackDuration(.infinity, chapterID: "chapter-2") == sparse)
    #expect(sparse.applyingPlaybackDuration(0, chapterID: "chapter-2") == sparse)
}

@Test func readerIntroductorySilenceHighlightsFirstPassage() {
    let first = ReadAlongSegment(id: "first", chapterID: "chapter-1", index: 0, text: "First passage.", startMilliseconds: 5_000, endMilliseconds: 10_000)
    let last = ReadAlongSegment(id: "last", chapterID: "chapter-1", index: 1, text: "Last passage.", startMilliseconds: 10_000, endMilliseconds: 20_000)
    let document = ReadAlongBook(bookID: "book", title: "Book", author: "Author", source: .wolneLektury, syncQuality: .timed, chapters: [ReadAlongChapter(id: "chapter-1", title: "Chapter", number: 1, segments: [first, last])])
    #expect(document.activeSegment(chapterID: "chapter-1", positionMilliseconds: 0)?.id == "first")
    #expect(document.activeSegment(chapterID: "chapter-1", positionMilliseconds: 15_000)?.id == "last")
}

private func readerBook(durations: [Int]) -> AudioBook {
    AudioBook(id: "reader-test", title: "Lalka", author: "Bolesław Prus", source: .wolneLektury, chapters: durations.enumerated().map { index, duration in
        AudioBookChapter(id: "chapter-\(index + 1)", title: "Chapter \(index + 1)", number: index + 1, durationSeconds: duration)
    })
}
