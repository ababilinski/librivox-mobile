import Foundation
import ZIPFoundation

public enum ReadAlongSyncQuality: String, Codable, Sendable {
    case timed
    case estimated
    case staticText

    public var label: String {
        switch self {
        case .timed: "Timed"
        case .estimated: "Estimated"
        case .staticText: "Static"
        }
    }
}

public struct ReadAlongBook: Identifiable, Codable, Hashable, Sendable {
    public var id: String { bookID }
    public var bookID: String
    public var title: String
    public var author: String
    public var source: BookSource
    public var sourceURL: String?
    public var syncQuality: ReadAlongSyncQuality
    public var chapters: [ReadAlongChapter]

    public init(
        bookID: String,
        title: String,
        author: String,
        source: BookSource,
        sourceURL: String? = nil,
        syncQuality: ReadAlongSyncQuality = .staticText,
        chapters: [ReadAlongChapter]
    ) {
        self.bookID = bookID
        self.title = title
        self.author = author
        self.source = source
        self.sourceURL = sourceURL
        self.syncQuality = syncQuality
        self.chapters = chapters
    }

    public var hasSourceText: Bool {
        sourceURL != nil && chapters.contains { !$0.segments.isEmpty }
    }

    public var supportsPlaybackSync: Bool {
        syncQuality != .staticText &&
            chapters.contains { chapter in
                chapter.segments.contains { segment in
                    segment.startMilliseconds != nil && segment.endMilliseconds != nil
                }
            }
    }

    public func chapter(id: String?) -> ReadAlongChapter? {
        guard let id else { return chapters.first }
        return chapters.first { $0.id == id } ?? chapters.first
    }

    public func activeSegment(chapterID: String?, positionMilliseconds: Int) -> ReadAlongSegment? {
        guard supportsPlaybackSync,
              let chapter = chapter(id: chapterID),
              chapterID == nil || chapter.id == chapterID
        else { return nil }
        if let segment = chapter.segments.first(where: { segment in
            guard let start = segment.startMilliseconds,
                  let end = segment.endMilliseconds,
                  end > start
            else { return false }
            return positionMilliseconds >= start && positionMilliseconds < end
        }) {
            return segment
        }
        // An introductory silence belongs to the first passage, not the last page.
        return chapter.segments.last { ($0.startMilliseconds ?? Int.max) <= positionMilliseconds }
            ?? chapter.segments.first { $0.startMilliseconds != nil }
    }

    /// Publishers often omit per-track durations. Once the player knows a duration,
    /// distribute that chapter's prose by character weight without changing source timing.
    public func applyingPlaybackDuration(_ durationSeconds: Double, chapterID: String?) -> ReadAlongBook {
        guard syncQuality == .estimated, durationSeconds.isFinite, durationSeconds > 0,
              let index = chapters.firstIndex(where: { $0.id == chapterID })
        else { return self }
        let duration = Int((durationSeconds * 1_000).rounded())
        guard abs(chapters[index].durationMilliseconds - duration) > 1_000 ||
                chapters[index].segments.first?.startMilliseconds == nil
        else { return self }
        var result = self
        result.chapters[index] = chapters[index].estimatingTiming(durationMilliseconds: duration)
        return result
    }

    public func search(_ query: String) -> [ReadAlongSearchHit] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let options: String.CompareOptions = [.caseInsensitive, .diacriticInsensitive]
        return chapters.flatMap { chapter in
            chapter.segments.flatMap { segment in
                segment.text.matchRanges(of: trimmed, options: options).enumerated().map { index, range in
                    ReadAlongSearchHit(
                        id: "\(segment.id)-\(trimmed.localizedLowercase)-\(index)",
                        chapterID: chapter.id,
                        segmentID: segment.id,
                        chapterTitle: chapter.title,
                        excerpt: segment.text.excerpt(around: range),
                        matchRange: ReadAlongTextRange(nsRange: NSRange(range, in: segment.text))
                    )
                }
            }
        }
    }

    public static func sourceText(
        for book: AudioBook,
        sourceURL: String,
        text: String,
        isHTML: Bool = false
    ) -> ReadAlongBook {
        let cleanedText = ReadAlongTextParser.clean(text, isHTML: isHTML)
        let paragraphs = ReadAlongTextParser.paragraphs(from: cleanedText, source: book.source)
        guard !paragraphs.isEmpty else { return fallback(for: book) }

        let audioChapters = book.chapters.isEmpty
            ? [AudioBookChapter(id: "\(book.id)-read", title: book.title, number: 1, durationSeconds: book.totalDurationSeconds)]
            : book.chapters
        let paragraphsByChapter = ReadAlongTextParser.chapterParagraphs(from: paragraphs, for: audioChapters)
        return sourceText(for: book, sourceURL: sourceURL, audioChapters: audioChapters, paragraphsByChapter: paragraphsByChapter)
    }

    static func sourceText(
        for book: AudioBook,
        sourceURL: String,
        audioChapters: [AudioBookChapter],
        paragraphsByChapter: [[String]]
    ) -> ReadAlongBook {
        let chapters = audioChapters.enumerated().map { index, audioChapter in
            let textSegments = paragraphsByChapter[safe: index] ?? []
            let chapter = ReadAlongChapter(
                id: audioChapter.id,
                title: audioChapter.displayTitle,
                number: audioChapter.number,
                segments: textSegments.enumerated().map { segmentIndex, paragraph in
                    ReadAlongSegment(
                        id: "\(audioChapter.id)-segment-\(segmentIndex)",
                        chapterID: audioChapter.id,
                        index: segmentIndex,
                        text: paragraph
                    )
                }
            )
            return chapter.estimatingTiming(durationMilliseconds: max(0, audioChapter.durationSeconds * 1_000))
        }

        return ReadAlongBook(
            bookID: book.id,
            title: book.title,
            author: book.author,
            source: book.source,
            sourceURL: sourceURL,
            syncQuality: .estimated,
            chapters: chapters
        )
    }

    public static func fallback(for book: AudioBook) -> ReadAlongBook {
        let audioChapters = book.chapters.isEmpty
            ? [AudioBookChapter(id: "\(book.id)-read", title: book.title, number: 1, durationSeconds: book.totalDurationSeconds)]
            : book.chapters
        let fallbackText = book.description.trimmingCharacters(in: .whitespacesAndNewlines)
        let chapters = audioChapters.map { audioChapter in
            let body = fallbackText.isEmpty ? audioChapter.displayTitle : fallbackText
            let segment = ReadAlongSegment(
                id: "\(audioChapter.id)-segment-0",
                chapterID: audioChapter.id,
                index: 0,
                text: body,
                startMilliseconds: 0,
                endMilliseconds: max(0, audioChapter.durationSeconds * 1000)
            )
            return ReadAlongChapter(
                id: audioChapter.id,
                title: audioChapter.displayTitle,
                number: audioChapter.number,
                durationMilliseconds: max(0, audioChapter.durationSeconds * 1000),
                segments: [segment]
            )
        }
        return ReadAlongBook(
            bookID: book.id,
            title: book.title,
            author: book.author,
            source: book.source,
            syncQuality: .staticText,
            chapters: chapters
        )
    }
}

extension ReadAlongBook {
    var totalTextLength: Int {
        chapters.reduce(into: 0) { total, chapter in
            total += chapter.segments.reduce(0) { $0 + $1.text.count }
        }
    }

    var populatedChapterCount: Int {
        chapters.count { chapter in
            chapter.segments.contains { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        }
    }

    func applyingExactTiming(from timedBook: ReadAlongBook) -> ReadAlongBook {
        guard timedBook.supportsPlaybackSync else { return self }
        var merged = self
        let timedChapters = Dictionary(uniqueKeysWithValues: timedBook.chapters.map { ($0.id, $0) })
        merged.chapters = chapters.map { chapter in
            guard let timedChapter = timedChapters[chapter.id] else { return chapter }
            let timedByText = Dictionary(
                timedChapter.segments.map { ($0.text.readerTimingMatchKey, $0) },
                uniquingKeysWith: { first, _ in first }
            )
            var copy = chapter
            copy.segments = chapter.segments.map { segment in
                guard let timed = timedByText[segment.text.readerTimingMatchKey] else { return segment }
                var updated = segment
                updated.startMilliseconds = timed.startMilliseconds
                updated.endMilliseconds = timed.endMilliseconds
                return updated
            }
            return copy
        }
        return merged
    }
}

private extension Array where Element == ReadAlongBook {
    func bestReaderDocument(for book: AudioBook) -> ReadAlongBook {
        let expectedChapterCount = Swift.max(1, book.chapters.filter { !$0.title.isSupplementalWolneAudioTitle }.count)
        let best = self.max { lhs, rhs in
            let lhsCoverage = Swift.min(lhs.populatedChapterCount, expectedChapterCount)
            let rhsCoverage = Swift.min(rhs.populatedChapterCount, expectedChapterCount)
            if lhsCoverage != rhsCoverage { return lhsCoverage < rhsCoverage }
            if lhs.totalTextLength != rhs.totalTextLength { return lhs.totalTextLength < rhs.totalTextLength }
            return lhs.syncQuality.readerPreferenceRank < rhs.syncQuality.readerPreferenceRank
        } ?? ReadAlongBook.fallback(for: book)

        guard best.syncQuality != .timed,
              let timed = first(where: { $0.syncQuality == .timed })
        else { return best }
        return best.applyingExactTiming(from: timed)
    }
}

private extension ReadAlongSyncQuality {
    var readerPreferenceRank: Int {
        switch self {
        case .timed: 2
        case .estimated: 1
        case .staticText: 0
        }
    }
}

public struct ReadAlongChapter: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var title: String
    public var number: Int
    public var durationMilliseconds: Int
    public var segments: [ReadAlongSegment]

    public init(
        id: String,
        title: String,
        number: Int,
        durationMilliseconds: Int = 0,
        segments: [ReadAlongSegment]
    ) {
        self.id = id
        self.title = title
        self.number = number
        self.durationMilliseconds = durationMilliseconds
        self.segments = segments
    }
}

private extension ReadAlongChapter {
    func estimatingTiming(durationMilliseconds: Int) -> ReadAlongChapter {
        var result = self
        result.durationMilliseconds = durationMilliseconds
        guard durationMilliseconds > 0 else { return result }
        let weights = segments.map { max(1, $0.text.filter { !$0.isWhitespace }.count) }
        let total = max(1, weights.reduce(0, +))
        var elapsedWeight = 0
        result.segments = segments.enumerated().map { index, segment in
            var result = segment
            result.startMilliseconds = Int(Double(durationMilliseconds) * Double(elapsedWeight) / Double(total))
            elapsedWeight += weights[index]
            result.endMilliseconds = index == segments.count - 1 ? durationMilliseconds :
                Int(Double(durationMilliseconds) * Double(elapsedWeight) / Double(total))
            return result
        }
        return result
    }
}

public struct ReadAlongSegment: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var chapterID: String
    public var index: Int
    public var text: String
    public var startMilliseconds: Int?
    public var endMilliseconds: Int?

    public init(
        id: String,
        chapterID: String,
        index: Int,
        text: String,
        startMilliseconds: Int? = nil,
        endMilliseconds: Int? = nil
    ) {
        self.id = id
        self.chapterID = chapterID
        self.index = index
        self.text = text
        self.startMilliseconds = startMilliseconds
        self.endMilliseconds = endMilliseconds
    }
}

public struct ReadAlongSearchHit: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var chapterID: String
    public var segmentID: String
    public var chapterTitle: String
    public var excerpt: String
    public var matchRange: ReadAlongTextRange?

    public init(
        id: String,
        chapterID: String,
        segmentID: String,
        chapterTitle: String,
        excerpt: String,
        matchRange: ReadAlongTextRange? = nil
    ) {
        self.id = id
        self.chapterID = chapterID
        self.segmentID = segmentID
        self.chapterTitle = chapterTitle
        self.excerpt = excerpt
        self.matchRange = matchRange
    }
}

public struct ReadAlongTextRange: Codable, Equatable, Hashable, Sendable {
    public var location: Int
    public var length: Int

    public init(location: Int, length: Int) {
        self.location = location
        self.length = length
    }

    public init(nsRange: NSRange) {
        self.location = nsRange.location
        self.length = nsRange.length
    }

    public var nsRange: NSRange {
        NSRange(location: location, length: length)
    }

    public var upperBound: Int {
        location + length
    }

    public func intersects(_ other: ReadAlongTextRange) -> Bool {
        location < other.upperBound && other.location < upperBound
    }
}

public struct ReadAlongTimedTextRange: Equatable, Hashable, Sendable {
    public var textRange: ReadAlongTextRange
    public var clipBeginMilliseconds: Int
    public var clipEndMilliseconds: Int

    public init(
        textRange: ReadAlongTextRange,
        clipBeginMilliseconds: Int,
        clipEndMilliseconds: Int
    ) {
        self.textRange = textRange
        self.clipBeginMilliseconds = clipBeginMilliseconds
        self.clipEndMilliseconds = clipEndMilliseconds
    }
}

public extension ReadAlongSegment {
    func proportionalSentenceRanges(locale: Locale = Locale(identifier: "pl_PL")) -> [ReadAlongTimedTextRange] {
        guard let clipBegin = startMilliseconds,
              let clipEnd = endMilliseconds
        else { return [] }
        let duration = clipEnd - clipBegin
        guard duration > 0, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return [] }

        let slices = text.sentenceTextSlices(locale: locale)
        guard !slices.isEmpty else { return [] }
        if slices.count == 1 {
            return [
                ReadAlongTimedTextRange(
                    textRange: slices[0].textRange,
                    clipBeginMilliseconds: clipBegin,
                    clipEndMilliseconds: clipEnd
                )
            ]
        }

        let weights = slices.map { slice in
            max(1, text[slice.range].filter { !$0.isWhitespace }.count)
        }
        let totalWeight = max(1, weights.reduce(0, +))
        var sliceBegin = clipBegin
        var accumulatedWeight = 0

        return slices.enumerated().map { index, slice in
            accumulatedWeight += weights[index]
            let sliceEnd = if index == slices.count - 1 {
                clipEnd
            } else {
                min(
                    clipEnd,
                    max(
                        sliceBegin,
                        clipBegin + Int((Double(duration) * Double(accumulatedWeight) / Double(totalWeight)).rounded())
                    )
                )
            }
            let range = ReadAlongTimedTextRange(
                textRange: slice.textRange,
                clipBeginMilliseconds: sliceBegin,
                clipEndMilliseconds: sliceEnd
            )
            sliceBegin = sliceEnd
            return range
        }
    }

    func activeSentenceRange(
        positionMilliseconds: Int,
        locale: Locale = Locale(identifier: "pl_PL")
    ) -> ReadAlongTextRange? {
        proportionalSentenceRanges(locale: locale)
            .first { range in
                positionMilliseconds >= range.clipBeginMilliseconds &&
                    positionMilliseconds < range.clipEndMilliseconds
            }?
            .textRange
    }
}

public struct ReadAlongRepository: Sendable {
    public init() {}

    public func load(for book: AudioBook) async -> ReadAlongBook {
        async let timed = loadTimedReadAlong(for: book)
        async let source = loadSourceReadAlong(for: book)
        let primaryCandidates = await [timed, source]
        var candidates = primaryCandidates.compactMap { $0 }
        if primaryCandidates[1] == nil,
           let epub = await loadStaticEpubReadAlong(for: book) {
            candidates.append(epub)
        }
        guard !candidates.isEmpty else { return .fallback(for: book) }
        return candidates.bestReaderDocument(for: book)
    }

    private func loadSourceReadAlong(for book: AudioBook) async -> ReadAlongBook? {
        guard let endpoint = book.preferredReadAlongEndpoint,
              let url = URL(string: endpoint.url)
        else { return nil }

        do {
            var request = URLRequest(url: url)
            request.setValue(endpoint.isHTML ? "text/html" : "text/plain", forHTTPHeaderField: "Accept")
            request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode)
            else { throw URLError(.badServerResponse) }
            let text = String(data: data, encoding: .utf8)
                ?? String(data: data, encoding: .isoLatin1)
                ?? ""
            let document = ReadAlongBook.sourceText(
                for: book,
                sourceURL: endpoint.url,
                text: text,
                isHTML: endpoint.isHTML
            )
            return document.syncQuality != .staticText && document.totalTextLength > 0 ? document : nil
        } catch {
            return nil
        }
    }

    private func loadStaticEpubReadAlong(for book: AudioBook) async -> ReadAlongBook? {
        guard let urlString = book.epubURL?.nilIfBlank,
              let url = URL(string: urlString)
        else { return nil }

        do {
            let candidate = TimedReadAlongAssetCandidate(
                url: urlString,
                fileName: "book.epub",
                acceptHeader: "application/epub+zip, application/zip"
            )
            let fileURL = try await cachedTimedReadAlongAsset(
                bookID: book.id,
                candidate: candidate,
                url: url
            )
            return try WolneStaticEpubParser().parse(book: book, fileURL: fileURL, sourceURL: urlString)
        } catch {
            return nil
        }
    }

    private func loadTimedReadAlong(for book: AudioBook) async -> ReadAlongBook? {
        let candidates = book.timedReadAlongAssetCandidates
        guard !candidates.isEmpty else { return nil }

        let parser = WolneTimedReadAlongParser()
        for candidate in candidates {
            guard let url = URL(string: candidate.url) else { continue }
            do {
                let fileURL = try await cachedTimedReadAlongAsset(
                    bookID: book.id,
                    candidate: candidate,
                    url: url
                )
                if let readAlongBook = try parser.parse(book: book, fileURL: fileURL, sourceURL: candidate.url) {
                    return readAlongBook
                }
            } catch {
                continue
            }
        }
        return nil
    }

    private func cachedTimedReadAlongAsset(
        bookID: String,
        candidate: TimedReadAlongAssetCandidate,
        url: URL
    ) async throws -> URL {
        let directory = FileManager.default.readAlongCacheDirectory
            .appendingPathComponent(bookID.safeReadAlongPathComponent, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let fileURL = directory.appendingPathComponent(candidate.fileName)
        if let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
           let size = attributes[.size] as? NSNumber,
           size.intValue > 0 {
            return fileURL
        }

        var request = URLRequest(url: url)
        request.setValue(candidate.acceptHeader, forHTTPHeaderField: "Accept")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              !data.isEmpty
        else { throw URLError(.badServerResponse) }
        let temporaryURL = fileURL.appendingPathExtension("part")
        try? FileManager.default.removeItem(at: temporaryURL)
        try data.write(to: temporaryURL, options: .atomic)
        try? FileManager.default.removeItem(at: fileURL)
        try FileManager.default.moveItem(at: temporaryURL, to: fileURL)
        return fileURL
    }
}

private struct ReadAlongSentenceTextSlice {
    var range: Range<String.Index>
    var textRange: ReadAlongTextRange
}

private struct TimedReadAlongAssetCandidate {
    var url: String
    var fileName: String
    var acceptHeader: String
}

struct WolneTimedReadAlongParser {
    func parse(book: AudioBook, fileURL: URL, sourceURL: String) throws -> ReadAlongBook? {
        let archive = try Archive(url: fileURL, accessMode: .read)
        let reader = WolneArchiveReader(archive: archive)
        let segments: [WolneTimedSegment]
        if let opfPath = reader.opfPath() {
            segments = try audioEpubSegments(book: book, reader: reader, opfPath: opfPath)
        } else {
            segments = try daisySegments(book: book, reader: reader)
        }
        return timedBook(book: book, sourceURL: sourceURL, segments: segments)
    }

    private func audioEpubSegments(
        book: AudioBook,
        reader: WolneArchiveReader,
        opfPath: String
    ) throws -> [WolneTimedSegment] {
        let opf = try reader.text(path: opfPath)
        let smilPaths = opf.manifestItems()
            .filter { item in
                item.mediaType.localizedCaseInsensitiveCompare("application/smil+xml") == .orderedSame ||
                    item.href.localizedCaseInsensitiveContains(".smil")
            }
            .map { resolveZipPath(basePath: opfPath, href: $0.href) }
            .uniquePreservingOrder()
        return try smilPaths.flatMap { try smilSegments(book: book, reader: reader, smilPath: $0) }
    }

    private func daisySegments(book: AudioBook, reader: WolneArchiveReader) throws -> [WolneTimedSegment] {
        let smilPaths = reader.paths
            .filter { $0.localizedCaseInsensitiveContains(".smil") }
            .uniquePreservingOrder()
        return try smilPaths.flatMap { try smilSegments(book: book, reader: reader, smilPath: $0) }
    }

    private func smilSegments(
        book: AudioBook,
        reader: WolneArchiveReader,
        smilPath: String
    ) throws -> [WolneTimedSegment] {
        let smil = try reader.text(path: smilPath)
        var documentCache: [String: String] = [:]
        return smil.tags(named: "par").enumerated().compactMap { index, block in
            guard let textTag = block.raw.firstTag(named: "text"),
                  let textSource = textTag.attributes["src"]?.nilIfBlank,
                  let audioTag = block.raw.firstTag(named: "audio")
            else { return nil }

            let textPath = resolveZipPath(basePath: smilPath, href: textSource.substringBefore("#"))
            let textAnchor = textSource.substringAfter("#")
            let document: String
            do {
                if let cached = documentCache[textPath] {
                    document = cached
                } else {
                    document = try reader.text(path: textPath)
                    documentCache[textPath] = document
                }
            } catch {
                return nil
            }

            guard let text = document.readerText(anchor: textAnchor).nilIfBlank else { return nil }
            let audioSource = audioTag.attributes["src"]?.nilIfBlank
            let audioRef = audioSource.map { resolveZipPath(basePath: smilPath, href: $0) }
            return WolneTimedSegment(
                id: "\(book.id):\(smilPath):\(index)",
                text: text,
                textRef: "\(textPath)#\(textAnchor)",
                audioRef: audioRef,
                clipBeginMilliseconds: WolneClipTimeParser.milliseconds(
                    from: audioTag.attributes["clipBegin"] ?? audioTag.attributes["clip-begin"]
                ),
                clipEndMilliseconds: WolneClipTimeParser.milliseconds(
                    from: audioTag.attributes["clipEnd"] ?? audioTag.attributes["clip-end"]
                )
            )
        }
    }

    private func timedBook(
        book: AudioBook,
        sourceURL: String,
        segments: [WolneTimedSegment]
    ) -> ReadAlongBook? {
        let timedSegments = segments.filter { $0.clipBeginMilliseconds != nil && $0.audioRef != nil }
        guard !timedSegments.isEmpty else { return nil }
        let chapters = mapTimedSegmentsToChapters(book: book, segments: timedSegments)
        guard !chapters.isEmpty else { return nil }
        return ReadAlongBook(
            bookID: book.id,
            title: book.title,
            author: book.author,
            source: book.source,
            sourceURL: sourceURL,
            syncQuality: .timed,
            chapters: chapters
        )
    }

    private func mapTimedSegmentsToChapters(
        book: AudioBook,
        segments: [WolneTimedSegment]
    ) -> [ReadAlongChapter] {
        let readableChapters = book.chapters
            .filter { !$0.title.isSupplementalWolneAudioTitle }
        guard !readableChapters.isEmpty else { return [] }

        let groups = segments.groupedByAudioRef()
        let chapterGroups = audioGroupsMappedToChapters(groups: groups, chapters: readableChapters)
        return chapterGroups.compactMap { chapter, groupedSegments in
            let builtSegments = groupedSegments.enumerated().compactMap { index, source -> ReadAlongSegment? in
                guard let start = source.clipBeginMilliseconds else { return nil }
                let end = source.clipEndMilliseconds
                    ?? groupedSegments[safe: index + 1]?.clipBeginMilliseconds
                    ?? max(start, chapter.durationSeconds * 1000)
                let text = source.text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { return nil }
                return ReadAlongSegment(
                    id: "\(chapter.id)-timed-\(index)",
                    chapterID: chapter.id,
                    index: index,
                    text: text,
                    startMilliseconds: start,
                    endMilliseconds: max(start, end)
                )
            }
            guard !builtSegments.isEmpty else { return nil }
            return ReadAlongChapter(
                id: chapter.id,
                title: chapter.displayTitle,
                number: chapter.number,
                durationMilliseconds: max(0, chapter.durationSeconds * 1000),
                segments: builtSegments
            )
        }
    }

    private func audioGroupsMappedToChapters(
        groups: [(String, [WolneTimedSegment])],
        chapters: [AudioBookChapter]
    ) -> [(AudioBookChapter, [WolneTimedSegment])] {
        if chapters.count == 1 {
            return [(chapters[0], groups.flatMap(\.1))]
        }

        let directMatches = groups.compactMap { group -> (AudioBookChapter, [WolneTimedSegment])? in
            guard let chapter = chapters.first(where: { $0.matchesTimedAudioRef(group.0, segments: group.1) }) else {
                return nil
            }
            return (chapter, group.1)
        }
        if directMatches.map(\.0.id).uniquePreservingOrder().count == chapters.count {
            return chapters.compactMap { chapter in
                directMatches.first { $0.0.id == chapter.id }
            }
        }

        let alignedGroups: [(String, [WolneTimedSegment])]
        if groups.count == chapters.count {
            alignedGroups = groups
        } else if groups.count > chapters.count {
            alignedGroups = Array(groups.suffix(chapters.count))
        } else {
            return directMatches
        }

        return zip(chapters, alignedGroups).map { chapter, group in
            (chapter, group.1)
        }
    }
}

struct WolneStaticEpubParser {
    func parse(book: AudioBook, fileURL: URL, sourceURL: String) throws -> ReadAlongBook? {
        let archive = try Archive(url: fileURL, accessMode: .read)
        let reader = WolneArchiveReader(archive: archive)
        guard let opfPath = reader.opfPath() else { return nil }
        let opf = try reader.text(path: opfPath)
        let manifest = Dictionary(uniqueKeysWithValues: opf.manifestItems().map { ($0.id, $0) })
        let spinePaths = opf.tags(named: "itemref").compactMap { tag -> String? in
            guard let id = tag.attributes["idref"]?.nilIfBlank,
                  let item = manifest[id],
                  item.mediaType.localizedCaseInsensitiveContains("html")
            else { return nil }
            return resolveZipPath(basePath: opfPath, href: item.href)
        }
        let fallbackPaths = manifest.values
            .filter { $0.mediaType.localizedCaseInsensitiveContains("html") }
            .map { resolveZipPath(basePath: opfPath, href: $0.href) }
        let documentPaths = (spinePaths.isEmpty ? fallbackPaths : spinePaths)
            .uniquePreservingOrder()
            .filter { path in
                let fileName = path.substringAfterLast("/").lowercased()
                return !["nav.xhtml", "toc.xhtml", "annotations.xhtml", "cover.xhtml"].contains(fileName)
            }

        // Clean each spine entry independently. Joining title/legal pages with the novel
        // before boilerplate removal truncates all prose at the first license paragraph.
        let documents = documentPaths.compactMap { path -> [String]? in
            let fileName = path.substringAfterLast("/").lowercased()
            guard !["title.xhtml", "support.xhtml", "last.xhtml"].contains(fileName),
                  !fileName.hasPrefix("fund"),
                  let html = try? reader.text(path: path)
            else { return nil }
            let body = html.regexMatches(#"(?is)<body\b[^>]*>(.*?)</body>"#).first ?? html
            // Front-matter and volume-divider pages contain headings only.
            guard body.range(of: #"(?is)<(?:p|blockquote|li)\b|class=["'][^"']*(?:verse|paragraph|stanza)"#, options: .regularExpression) != nil
            else { return nil }
            let text = ReadAlongTextParser.clean(body, isHTML: true)
            let paragraphs = ReadAlongTextParser.paragraphs(from: text, source: book.source)
            guard !paragraphs.isEmpty else { return nil }
            return paragraphs
        }
        guard !documents.isEmpty else { return nil }
        let audioChapters = book.chapters.filter { !$0.title.isSupplementalWolneAudioTitle }
        if !audioChapters.isEmpty, documents.count == audioChapters.count {
            return ReadAlongBook.sourceText(
                for: book, sourceURL: sourceURL, audioChapters: audioChapters,
                paragraphsByChapter: documents
            )
        }
        let document = ReadAlongBook.sourceText(
            for: book,
            sourceURL: sourceURL,
            text: documents.flatMap { $0 }.joined(separator: "\n\n")
        )
        return document.hasSourceText && document.totalTextLength > 0 ? document : nil
    }
}

private struct WolneTimedSegment {
    var id: String
    var text: String
    var textRef: String
    var audioRef: String?
    var clipBeginMilliseconds: Int?
    var clipEndMilliseconds: Int?
}

internal enum WolneClipTimeParser {
    static func milliseconds(from value: String?) -> Int? {
        let raw = value?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "npt=", with: "")
            .nilIfBlank
        guard let raw else { return nil }
        if raw.localizedCaseInsensitiveHasSuffix("ms") {
            return Int((Double(raw.dropLast(2)) ?? 0).rounded())
        }
        if raw.localizedCaseInsensitiveHasSuffix("s") {
            return Int(((Double(raw.dropLast(1)) ?? 0) * 1000).rounded())
        }
        if raw.contains(":") {
            let parts = raw.split(separator: ":")
            guard let seconds = Double(parts.last ?? "") else { return nil }
            let minutes = parts.dropLast().last.flatMap { Double($0) } ?? 0
            let hours = parts.dropLast(2).last.flatMap { Double($0) } ?? 0
            return Int(((hours * 3600 + minutes * 60 + seconds) * 1000).rounded())
        }
        guard let seconds = Double(raw) else { return nil }
        return Int((seconds * 1000).rounded())
    }
}

private final class WolneArchiveReader {
    private let archive: Archive

    init(archive: Archive) {
        self.archive = archive
    }

    var paths: [String] {
        archive.map(\.path)
    }

    func text(path: String) throws -> String {
        let data = try data(path: path)
        return String(data: data, encoding: .utf8)
            ?? String(data: data, encoding: .isoLatin1)
            ?? ""
    }

    func opfPath() -> String? {
        if let container = try? text(path: "META-INF/container.xml"),
           let rootfile = container.firstTag(named: "rootfile")?.attributes["full-path"]?.nilIfBlank {
            return rootfile
        }
        return paths.first { $0.localizedCaseInsensitiveContains(".opf") }
    }

    private func data(path: String) throws -> Data {
        guard let entry = archive[path] else { throw URLError(.fileDoesNotExist) }
        var data = Data()
        _ = try archive.extract(entry) { chunk in
            data.append(chunk)
        }
        return data
    }
}

private struct ReadAlongEndpoint {
    var url: String
    var isHTML: Bool
}

private struct WolneManifestItem {
    var id: String
    var href: String
    var mediaType: String
}

private struct WolneXMLTag {
    var raw: String
    var attributes: [String: String]
}

private extension AudioBook {
    var timedReadAlongAssetCandidates: [TimedReadAlongAssetCandidate] {
        var candidates: [TimedReadAlongAssetCandidate] = []
        if let url = audioEpubURL?.nilIfBlank {
            candidates.append(
                TimedReadAlongAssetCandidate(
                    url: url,
                    fileName: "book.audio.epub",
                    acceptHeader: "application/epub+zip, application/zip"
                )
            )
        }
        if let url = daisyURL?.nilIfBlank {
            candidates.append(
                TimedReadAlongAssetCandidate(
                    url: url,
                    fileName: "book.daisy.zip",
                    acceptHeader: "application/zip"
                )
            )
        }
        return candidates
    }
}

private extension AudioBookChapter {
    func matchesTimedAudioRef(_ audioRef: String, segments: [WolneTimedSegment]) -> Bool {
        let audioKey = audioRef
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .timedMatchKey
        let chapterAudioKey = listenURL?
            .substringBefore("?")
            .substringAfterLast("/")
            .substringBeforeLast(".")
            .timedMatchKey ?? ""
        if audioKey.count >= 5,
           chapterAudioKey.count >= 5,
           (audioKey == chapterAudioKey || audioKey.contains(chapterAudioKey) || chapterAudioKey.contains(audioKey)) {
            return true
        }

        let titleKey = title.timedMatchKey
        let candidates = ([audioRef.substringAfterLast("/").substringBeforeLast(".")] + segments.map {
            $0.textRef.substringBefore("#").substringAfterLast("/").substringBeforeLast(".")
        })
            .map(\.timedMatchKey)
            .filter { $0.count >= 5 }
        if candidates.contains(where: { titleKey.contains($0) || $0.contains(titleKey) }) {
            return true
        }

        let numericPattern = "(^|[^0-9])0*\(max(number, 1))([^0-9]|$)"
        return candidates.contains { candidate in
            candidate.contains("ksiega") &&
                candidate.range(of: numericPattern, options: .regularExpression) != nil
        }
    }
}

private extension FileManager {
    var readAlongCacheDirectory: URL {
        urls(for: .cachesDirectory, in: .userDomainMask).first?
            .appendingPathComponent("LibriVoxMobileReadAlong", isDirectory: true)
            ?? temporaryDirectory.appendingPathComponent("LibriVoxMobileReadAlong", isDirectory: true)
    }
}

private extension Array where Element: Hashable {
    func uniquePreservingOrder() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

private extension Array where Element == WolneTimedSegment {
    func groupedByAudioRef() -> [(String, [WolneTimedSegment])] {
        var groups: [(String, [WolneTimedSegment])] = []
        for segment in self {
            guard let audioRef = segment.audioRef else { continue }
            if groups.last?.0 == audioRef {
                groups[groups.count - 1].1.append(segment)
            } else {
                groups.append((audioRef, [segment]))
            }
        }
        return groups
    }
}

private extension String {
    func sentenceTextSlices(locale: Locale) -> [ReadAlongSentenceTextSlice] {
        _ = locale
        var slices: [ReadAlongSentenceTextSlice] = []
        enumerateSubstrings(
            in: startIndex..<endIndex,
            options: [.bySentences, .substringNotRequired]
        ) { _, substringRange, _, _ in
            if let slice = trimmedTextSlice(in: substringRange) {
                slices.append(slice)
            }
        }

        if slices.isEmpty,
           let fallback = trimmedTextSlice(in: startIndex..<endIndex) {
            return [fallback]
        }
        return slices
    }

    func trimmedTextSlice(in range: Range<String.Index>) -> ReadAlongSentenceTextSlice? {
        var trimmedStart = range.lowerBound
        var trimmedEnd = range.upperBound
        while trimmedStart < trimmedEnd, self[trimmedStart].isWhitespace {
            formIndex(after: &trimmedStart)
        }
        while trimmedEnd > trimmedStart {
            let previous = index(before: trimmedEnd)
            guard self[previous].isWhitespace else { break }
            trimmedEnd = previous
        }
        guard trimmedStart < trimmedEnd else { return nil }
        let trimmedRange = trimmedStart..<trimmedEnd
        let nsRange = NSRange(trimmedRange, in: self)
        return ReadAlongSentenceTextSlice(
            range: trimmedRange,
            textRange: ReadAlongTextRange(location: nsRange.location, length: nsRange.length)
        )
    }

    func manifestItems() -> [WolneManifestItem] {
        tags(named: "item").compactMap { tag in
            guard let href = tag.attributes["href"]?.nilIfBlank else { return nil }
            return WolneManifestItem(
                id: tag.attributes["id"] ?? href,
                href: href,
                mediaType: tag.attributes["media-type"] ?? ""
            )
        }
    }

    func tags(named name: String) -> [WolneXMLTag] {
        let pattern = #"<\s*\#(name)\b[^>]*(?:/>|>.*?</\s*\#(name)\s*>)"#
        return regexMatches(pattern).map { raw in
            WolneXMLTag(raw: raw, attributes: raw.xmlAttributes)
        }
    }

    func firstTag(named name: String) -> WolneXMLTag? {
        let pattern = #"<\s*\#(name)\b[^>]*>"#
        return regexMatches(pattern).first.map { raw in
            WolneXMLTag(raw: raw, attributes: raw.xmlAttributes)
        }
    }

    func readerText(anchor: String) -> String {
        guard !anchor.isEmpty else { return htmlPlainTextForReadAlong.cleanReaderWhitespace }
        let escaped = NSRegularExpression.escapedPattern(for: anchor)
        let blockPattern = #"(?is)<(?:p|h[1-6]|li|blockquote|div)\b(?=[^>]*(?:id|xml:id|name)\s*=\s*["']\#(escaped)["'])[^>]*>.*?</(?:p|h[1-6]|li|blockquote|div)>"#
        if let text = regexMatches(blockPattern).first?.readerHTMLText.nilIfBlank {
            return text
        }

        let directPattern = #"(?is)<([A-Za-z][A-Za-z0-9:_-]*)\b(?=[^>]*(?:id|xml:id|name)\s*=\s*["']\#(escaped)["'])[^>]*>.*?</\1>"#
        if let text = regexMatches(directPattern).first?.readerHTMLText.nilIfBlank {
            return text
        }

        if let numericAnchor = Int(anchor) {
            return readerText(anchor: "t\(numericAnchor + 1)").nilIfBlank
                ?? readerText(anchor: "t\(numericAnchor)")
        }
        return ""
    }

    var xmlAttributes: [String: String] {
        let pattern = #"([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(['"])(.*?)\2"#
        let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators])
        let nsString = self as NSString
        let matches = regex?.matches(in: self, range: NSRange(location: 0, length: nsString.length)) ?? []
        var attributes: [String: String] = [:]
        for match in matches where match.numberOfRanges >= 4 {
            let key = nsString.substring(with: match.range(at: 1))
            let value = nsString.substring(with: match.range(at: 3)).decodingCommonHTMLEntities
            attributes[key] = value
        }
        return attributes
    }

    var readerHTMLText: String {
        replacingOccurrences(of: #"(?is)<(script|style|sup)[^>]*>.*?</\1>"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)<br\s*/?>"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
            .decodingCommonHTMLEntities
            .cleanReaderWhitespace
    }

    var cleanReaderWhitespace: String {
        replacingOccurrences(of: #"[ \t\n\r]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var safeReadAlongPathComponent: String {
        replacingOccurrences(of: #"[^A-Za-z0-9._-]+"#, with: "_", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "_"))
            .nilIfBlank ?? "book"
    }

    var timedMatchKey: String {
        folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "pl_PL"))
            .lowercased()
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: "", options: .regularExpression)
    }

    var readerTimingMatchKey: String {
        folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale.current)
            .lowercased()
            .replacingOccurrences(of: #"[^\p{L}\p{N}]+"#, with: "", options: .regularExpression)
    }

    var isSupplementalWolneAudioTitle: Bool {
        let compact = cleanReaderWhitespace.lowercased()
        return compact == "stopka" ||
            compact == "strona redakcyjna" ||
            compact == "nota redakcyjna"
    }

    func substringBefore(_ delimiter: String) -> String {
        components(separatedBy: delimiter).first ?? self
    }

    func substringAfter(_ delimiter: String) -> String {
        guard let range = range(of: delimiter) else { return "" }
        return String(self[range.upperBound...])
    }

    func substringBeforeLast(_ delimiter: String) -> String {
        guard let range = range(of: delimiter, options: .backwards) else { return self }
        return String(self[..<range.lowerBound])
    }

    func substringAfterLast(_ delimiter: String) -> String {
        guard let range = range(of: delimiter, options: .backwards) else { return self }
        return String(self[range.upperBound...])
    }

    func regexMatches(_ pattern: String) -> [String] {
        let options: NSRegularExpression.Options = [.caseInsensitive, .dotMatchesLineSeparators]
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else { return [] }
        let nsString = self as NSString
        let range = NSRange(location: 0, length: nsString.length)
        return regex.matches(in: self, range: range).map { nsString.substring(with: $0.range) }
    }

    func localizedCaseInsensitiveHasSuffix(_ suffix: String) -> Bool {
        lowercased().hasSuffix(suffix.lowercased())
    }
}

private func resolveZipPath(basePath: String, href: String) -> String {
    let cleanHref = (href.removingPercentEncoding ?? href)
        .substringBefore("?")
    if cleanHref.hasPrefix("/") {
        return String(cleanHref.drop(while: { $0 == "/" }))
    }
    let baseDirectory = basePath.substringBeforeLast("/")
    let raw = baseDirectory == basePath ? cleanHref : "\(baseDirectory)/\(cleanHref)"
    var parts: [String] = []
    for part in raw.components(separatedBy: "/") where !part.isEmpty && part != "." {
        if part == ".." {
            _ = parts.popLast()
        } else {
            parts.append(part)
        }
    }
    return parts.joined(separator: "/")
}

private enum ReadAlongTextParser {
    static func clean(_ text: String, isHTML: Bool) -> String {
        let stripped = isHTML ? text.htmlPlainTextForReadAlong : text
        return stripped
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .replacingOccurrences(of: #"[ \t]+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func paragraphs(from text: String, source: BookSource) -> [String] {
        let trimmed = removeSourceBoilerplate(from: text, source: source)
        let blocks = trimmed.components(separatedBy: "\n\n")
        return blocks
            .map { block in
                block
                    .components(separatedBy: .newlines)
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty }
                    .joined(separator: " ")
            }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count > 1 }
    }

    private static func removeSourceBoilerplate(from text: String, source: BookSource) -> String {
        let withoutGutenberg = removeProjectGutenbergBoilerplate(from: text)
        guard source == .wolneLektury else { return withoutGutenberg }
        return removeWolneLekturyBoilerplate(from: withoutGutenberg)
    }

    /// Wolne Lektury plain-text exports place a compact author/title/ISBN header before the
    /// work and a license/editorial footer after a line made of hyphens. Those fields are
    /// catalog metadata, not book prose, and must not appear in Follow Along when timed DAISY
    /// loading falls back to the TXT edition.
    private static func removeWolneLekturyBoilerplate(from text: String) -> String {
        var lines = text.components(separatedBy: .newlines)

        if let footerIndex = lines.firstIndex(where: isWolneLekturyFooterLine) {
            let footerLine = lines[footerIndex]
            if let separatorRange = footerLine.range(of: #"-{5,}"#, options: .regularExpression) {
                let prosePrefix = footerLine[..<separatorRange.lowerBound]
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if prosePrefix.isEmpty {
                    lines.removeSubrange(footerIndex...)
                } else {
                    lines[footerIndex] = prosePrefix
                    lines.removeSubrange((footerIndex + 1)...)
                }
            } else if footerIndex > lines.startIndex,
                      isHorizontalRule(lines[lines.index(before: footerIndex)]) {
                lines.removeSubrange(lines.index(before: footerIndex)...)
            } else {
                lines.removeSubrange(footerIndex...)
            }
        }

        let headerSearchLimit = min(lines.count, 24)
        if let isbnIndex = lines[..<headerSearchLimit].firstIndex(where: { line in
            line.trimmingCharacters(in: .whitespacesAndNewlines)
                .range(of: #"(?i)^ISBN(?:[- ]|$)"#, options: .regularExpression) != nil
        }) {
            lines.removeSubrange(...isbnIndex)
        }

        lines.removeAll(where: isHorizontalRule)

        return lines
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func isWolneLekturyFooterLine(_ line: String) -> Bool {
        let normalized = line.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
        return normalized.contains("ta lektura, podobnie jak") ||
            normalized.contains("ta lektura jest dostepna w wolnych lekturach") ||
            normalized.contains("utwor opracowany zostal w ramach projektu wolne lektury") ||
            normalized.contains("wszystkie zasoby wolnych lektur")
    }

    private static func isHorizontalRule(_ line: String) -> Bool {
        line.trimmingCharacters(in: .whitespacesAndNewlines)
            .range(of: #"^-{5,}$"#, options: .regularExpression) != nil
    }

    static func chapterParagraphs(
        from paragraphs: [String],
        for audioChapters: [AudioBookChapter]
    ) -> [[String]] {
        guard audioChapters.count > 1 else { return [paragraphs] }
        let headingIndices = paragraphs.indices.filter { paragraphs[$0].isChapterHeading }
        guard headingIndices.count == audioChapters.count else {
            return audioChapters.indices.map { index in
                paragraphs.chunk(index: index, totalChunks: audioChapters.count)
            }
        }

        return audioChapters.indices.map { chapterIndex in
            guard chapterIndex < headingIndices.count else { return [] }
            let start = headingIndices[chapterIndex]
            let end = chapterIndex + 1 < headingIndices.count ? headingIndices[chapterIndex + 1] : paragraphs.count
            var chapterParagraphs = Array(paragraphs[start..<end])
            if chapterParagraphs.first?.isChapterHeading == true {
                chapterParagraphs.removeFirst()
            }
            return chapterParagraphs.filter { !$0.isTableOfContentsBlock }
        }
    }

    private static func removeProjectGutenbergBoilerplate(from text: String) -> String {
        var result = text
        if let startRange = result.range(
            of: #"(?is)\*\*\* START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK .*?\*\*\*"#,
            options: .regularExpression
        ) {
            result = String(result[startRange.upperBound...])
        }
        if let endRange = result.range(
            of: #"(?is)\*\*\* END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK .*"#,
            options: .regularExpression
        ) {
            result = String(result[..<endRange.lowerBound])
        }
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private extension AudioBook {
    var preferredReadAlongEndpoint: ReadAlongEndpoint? {
        if let url = sourceTextURL?.nilIfBlank {
            return ReadAlongEndpoint(url: url, isHTML: false)
        }
        if let gutenbergTextURL = gutenbergURL?.gutenbergPlainTextURLString {
            return ReadAlongEndpoint(url: gutenbergTextURL, isHTML: false)
        }
        if let url = sourceHTMLURL?.nilIfBlank {
            return ReadAlongEndpoint(url: url, isHTML: true)
        }
        if let url = gutenbergURL?.nilIfBlank {
            return ReadAlongEndpoint(url: url, isHTML: true)
        }
        return nil
    }
}

private extension Array where Element == String {
    func chunk(index: Int, totalChunks: Int) -> [String] {
        guard totalChunks > 1, !isEmpty else { return self }
        let start = Swift.max(0, Swift.min(count, count * index / totalChunks))
        let end = Swift.max(start, Swift.min(count, count * (index + 1) / totalChunks))
        return Array(self[start..<end])
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension String {
    var isChapterHeading: Bool {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 120 else { return false }
        guard trimmed.regexMatchCount(#"(?i)\b(?:chapter|rozdzia[łl])\b"#) == 1 else { return false }
        return trimmed.range(
            of: #"(?i)^(?:chapter|rozdzia[łl])\s+(?:[ivxlcdm]+|\d+)(?:[.: -].*)?$"#,
            options: .regularExpression
        ) != nil
    }

    var isTableOfContentsBlock: Bool {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.localizedCaseInsensitiveContains("contents") ||
            trimmed.regexMatchCount(#"\b(?:CHAPTER|Chapter)\b"#) > 3
        else { return false }
        return trimmed.count < 2_000
    }

    func regexMatchCount(_ pattern: String) -> Int {
        var count = 0
        var searchRange = startIndex..<endIndex
        while let range = self.range(of: pattern, options: .regularExpression, range: searchRange) {
            count += 1
            guard range.upperBound < endIndex else { break }
            searchRange = range.upperBound..<endIndex
        }
        return count
    }

    var htmlPlainTextForReadAlong: String {
        replacingOccurrences(of: #"(?is)<(head|script|style|sup)[^>]*>.*?</\1>"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)</p>"#, with: "\n\n", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)<br\s*/?>"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"(?i)</h[1-6]>"#, with: "\n\n", options: .regularExpression)
            .replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
            .decodingCommonHTMLEntities
    }

    var decodingCommonHTMLEntities: String {
        replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
    }

    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var gutenbergPlainTextURLString: String? {
        guard let url = URL(string: self) else { return nil }
        let components = url.pathComponents
        guard let id = components.last(where: { $0.allSatisfy(\.isNumber) }) else { return nil }
        return "https://www.gutenberg.org/cache/epub/\(id)/pg\(id).txt"
    }

    func matchRanges(of query: String, options: String.CompareOptions) -> [Range<String.Index>] {
        var matches: [Range<String.Index>] = []
        var searchStart = startIndex
        while searchStart < endIndex,
              let range = range(of: query, options: options, range: searchStart..<endIndex) {
            matches.append(range)
            searchStart = range.upperBound
            if matches.count >= 500 { break }
        }
        return matches
    }

    func excerpt(around range: Range<String.Index>) -> String {
        let lower = index(range.lowerBound, offsetBy: -70, limitedBy: startIndex) ?? startIndex
        let upper = index(range.upperBound, offsetBy: 110, limitedBy: endIndex) ?? endIndex
        let prefix = lower == startIndex ? "" : "..."
        let suffix = upper == endIndex ? "" : "..."
        return prefix + self[lower..<upper].trimmingCharacters(in: .whitespacesAndNewlines) + suffix
    }
}
