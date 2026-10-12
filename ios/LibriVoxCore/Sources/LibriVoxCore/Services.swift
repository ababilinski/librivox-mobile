import Foundation

public struct CatalogSearchRequest: Hashable, Sendable {
    public var query: String
    public var field: CatalogSearchField
    public var languages: Set<BookLanguagePreference>
    public var sources: Set<BookSource>
    public var sourcePriority: [BookSource]

    public init(
        query: String,
        field: CatalogSearchField = .all,
        languages: Set<BookLanguagePreference> = [.english],
        sources: Set<BookSource> = [.libriVox],
        sourcePriority: [BookSource] = BookSource.defaultPublicCatalogPriority
    ) {
        self.query = query
        self.field = field
        self.languages = languages
        self.sources = sources
        self.sourcePriority = BookSource.normalizedPublicCatalogPriority(sourcePriority)
    }

    public var orderedSources: [BookSource] {
        sourcePriority.filter { sources.contains($0) }
    }
}

public protocol CatalogSourceClient: Sendable {
    var source: BookSource { get }
    func featuredBooks(limit: Int) async throws -> [AudioBook]
    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook]
    func hydrate(_ book: AudioBook) async throws -> AudioBook
}

public protocol LibraryStoring: Sendable {
    func snapshot() async -> [AudioBook]
    func save(_ book: AudioBook) async
    func remove(bookID: String) async
}

public protocol BookmarkStoring: Sendable {
    func bookmarks(for bookID: String) async -> [Bookmark]
    func save(_ bookmark: Bookmark) async
    func remove(bookmarkID: UUID) async
}

public protocol DownloadServicing: Sendable {
    func downloads() async -> [DownloadItem]
    func enqueue(book: AudioBook) async
    func enqueue(book: AudioBook, chapter: AudioBookChapter?) async
    func cancel(bookID: String) async
    func markDownloaded(bookID: String, chapterID: String?) async
    func localFileURL(bookID: String, chapter: AudioBookChapter) async -> URL?
    func storageBytes(bookID: String) async -> Int64
    func storageBytes(bookID: String, chapter: AudioBookChapter) async -> Int64
    func delete(bookID: String) async
    func delete(book: AudioBook, chapter: AudioBookChapter) async
}

public extension DownloadServicing {
    func enqueue(book: AudioBook, chapter: AudioBookChapter?) async {
        await enqueue(book: book)
    }
}

public protocol PlaybackProgressStoring: Sendable {
    func snapshot() async -> [String: PlaybackProgress]
    func progress(for bookID: String) async -> PlaybackProgress?
    func save(_ progress: PlaybackProgress) async
    func remove(bookID: String) async
}

public protocol PlaybackFeedbackStoring: Sendable {
    func snapshot() async -> [String: PlaybackFeedbackEntry]
    func feedback(bookID: String?, chapterID: String?, scope: FeedbackControlScope) async -> PlaybackFeedback
    func toggleFeedback(
        bookID: String?,
        chapterID: String?,
        target: PlaybackFeedback,
        scope: FeedbackControlScope
    ) async -> PlaybackFeedback
}

@MainActor
public protocol PlaybackControlling: AnyObject {
    var currentBook: AudioBook? { get }
    var currentChapter: AudioBookChapter? { get }
    var isPlaying: Bool { get }
    var isBuffering: Bool { get }
    func play(book: AudioBook, chapter: AudioBookChapter?)
    func pause()
}

public struct DownloadItem: Identifiable, Codable, Hashable, Sendable {
    public var id: UUID
    public var bookID: String
    public var chapterID: String?
    public var title: String
    public var state: DownloadState
    public var progress: Double
    public var bookTitle: String?
    public var bookAuthor: String?
    public var bookSource: BookSource?
    public var coverImageURL: String?
    public var fullCoverImageURL: String?
    public var localCoverFileName: String?
    public var chapterNumber: Int?
    public var chapterReader: String?
    public var chapterDurationSeconds: Int?
    public var chapterListenURL: String?
    public var chapterLocalFileName: String?
    public var chapterMimeType: String?

    public init(
        id: UUID = UUID(),
        bookID: String,
        chapterID: String? = nil,
        title: String,
        state: DownloadState = .queued,
        progress: Double = 0,
        bookTitle: String? = nil,
        bookAuthor: String? = nil,
        bookSource: BookSource? = nil,
        coverImageURL: String? = nil,
        fullCoverImageURL: String? = nil,
        localCoverFileName: String? = nil,
        chapterNumber: Int? = nil,
        chapterReader: String? = nil,
        chapterDurationSeconds: Int? = nil,
        chapterListenURL: String? = nil,
        chapterLocalFileName: String? = nil,
        chapterMimeType: String? = nil
    ) {
        self.id = id
        self.bookID = bookID
        self.chapterID = chapterID
        self.title = title
        self.state = state
        self.progress = progress
        self.bookTitle = bookTitle
        self.bookAuthor = bookAuthor
        self.bookSource = bookSource
        self.coverImageURL = coverImageURL
        self.fullCoverImageURL = fullCoverImageURL
        self.localCoverFileName = localCoverFileName
        self.chapterNumber = chapterNumber
        self.chapterReader = chapterReader
        self.chapterDurationSeconds = chapterDurationSeconds
        self.chapterListenURL = chapterListenURL
        self.chapterLocalFileName = chapterLocalFileName
        self.chapterMimeType = chapterMimeType
    }
}

public extension DownloadItem {
    static func recoveredAudioBook(bookID: String, from items: [DownloadItem]) -> AudioBook? {
        guard let metadataItem = items.first(where: { $0.chapterID == nil }) ?? items.first else {
            return nil
        }

        let aggregateTitle = items.first(where: { $0.chapterID == nil })?.title.nonBlankDownloadMetadata
        let title = metadataItem.bookTitle.nonBlankDownloadMetadata ??
            aggregateTitle ??
            metadataItem.title.nonBlankDownloadMetadata ??
            "Downloaded Book"
        let author = items.compactMap { $0.bookAuthor.nonBlankDownloadMetadata }.first ?? ""
        let source = items.compactMap(\.bookSource).first ?? .libriVox
        let chapters = recoveredChapters(from: items)

        return AudioBook(
            id: bookID,
            title: title,
            author: author,
            source: source,
            coverImageURL: items.compactMap { $0.coverImageURL.nonBlankDownloadMetadata }.first,
            fullCoverImageURL: items.compactMap { $0.fullCoverImageURL.nonBlankDownloadMetadata }.first,
            localCoverFileName: items.compactMap { $0.localCoverFileName.nonBlankDownloadMetadata }.first,
            chapters: chapters,
            totalDurationSeconds: chapters.reduce(0) { $0 + $1.durationSeconds }
        )
    }

    private static func recoveredChapters(from items: [DownloadItem]) -> [AudioBookChapter] {
        let chapterItems = items
            .filter { $0.chapterID != nil }
            .sorted { lhs, rhs in
                let lhsNumber = lhs.chapterNumber ?? Int.max
                let rhsNumber = rhs.chapterNumber ?? Int.max
                if lhsNumber != rhsNumber { return lhsNumber < rhsNumber }
                return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
            }

        return chapterItems.enumerated().compactMap { offset, item in
            guard let chapterID = item.chapterID else { return nil }
            return AudioBookChapter(
                id: chapterID,
                title: item.title,
                number: item.chapterNumber ?? offset + 1,
                reader: item.chapterReader,
                durationSeconds: item.chapterDurationSeconds ?? 0,
                listenURL: item.chapterListenURL,
                localFileName: item.chapterLocalFileName,
                mimeType: item.chapterMimeType ?? "audio/mpeg",
                downloadState: item.state
            )
        }
    }
}

private extension Optional where Wrapped == String {
    var nonBlankDownloadMetadata: String? {
        switch self {
        case .some(let value):
            return value.nonBlankDownloadMetadata
        case .none:
            return nil
        }
    }
}

private extension String {
    var nonBlankDownloadMetadata: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

public actor InMemoryLibraryStore: LibraryStoring {
    private var books: [String: AudioBook]

    public init(seedBooks: [AudioBook] = AudioBook.seedLibrary) {
        self.books = Dictionary(uniqueKeysWithValues: seedBooks.map { ($0.id, $0) })
    }

    public func snapshot() async -> [AudioBook] {
        books.values.sorted { lhs, rhs in
            if lhs.id == AudioBook.theJungle.id { return true }
            if rhs.id == AudioBook.theJungle.id { return false }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
    }

    public func save(_ book: AudioBook) async {
        var saved = book
        saved.libraryStatus = .inLibrary
        if saved.addedAt == nil {
            saved.addedAt = .now
        }
        books[saved.id] = saved
    }

    public func remove(bookID: String) async {
        books.removeValue(forKey: bookID)
    }
}

public actor InMemoryBookmarkStore: BookmarkStoring {
    private var bookmarks: [UUID: Bookmark] = [:]

    public init() {}

    public func bookmarks(for bookID: String) async -> [Bookmark] {
        bookmarks.values
            .filter { $0.bookID == bookID }
            .sorted { $0.positionMilliseconds < $1.positionMilliseconds }
    }

    public func save(_ bookmark: Bookmark) async {
        bookmarks[bookmark.id] = bookmark
    }

    public func remove(bookmarkID: UUID) async {
        bookmarks.removeValue(forKey: bookmarkID)
    }
}

public actor InMemoryPlaybackFeedbackStore: PlaybackFeedbackStoring {
    private var entries: [String: PlaybackFeedbackEntry] = [:]

    public init() {}

    public func snapshot() async -> [String: PlaybackFeedbackEntry] {
        entries
    }

    public func feedback(bookID: String?, chapterID: String?, scope: FeedbackControlScope) async -> PlaybackFeedback {
        guard let key = PlaybackFeedbackEntry.key(bookID: bookID, chapterID: chapterID, scope: scope)
        else { return .none }
        return entries[key]?.feedback ?? .none
    }

    public func toggleFeedback(
        bookID: String?,
        chapterID: String?,
        target: PlaybackFeedback,
        scope: FeedbackControlScope
    ) async -> PlaybackFeedback {
        guard let key = PlaybackFeedbackEntry.key(bookID: bookID, chapterID: chapterID, scope: scope),
              let bookID
        else { return .none }
        let next: PlaybackFeedback = entries[key]?.feedback == target ? .none : target
        if next == .none {
            entries.removeValue(forKey: key)
        } else {
            entries[key] = PlaybackFeedbackEntry(
                id: key,
                bookID: bookID,
                chapterID: scope == .chapter ? chapterID : nil,
                feedback: next
            )
        }
        return next
    }
}

public actor InMemoryDownloadService: DownloadServicing {
    private var items: [DownloadItem] = []

    public init() {}

    public func downloads() async -> [DownloadItem] {
        items
    }

    public func enqueue(book: AudioBook) async {
        let existingIDs = Set(items.map(\.bookID))
        guard !existingIDs.contains(book.id) else { return }
        items.append(
            DownloadItem(
                bookID: book.id,
                title: book.title,
                state: .queued,
                progress: 0
            )
        )
    }

    public func enqueue(book: AudioBook, chapter: AudioBookChapter?) async {
        if let chapter {
            guard items.contains(where: { $0.bookID == book.id && $0.chapterID == chapter.id }) == false else { return }
            items.append(
                DownloadItem(
                    bookID: book.id,
                    chapterID: chapter.id,
                    title: chapter.displayTitle,
                    state: .queued,
                    progress: 0
                )
            )
        } else {
            await enqueue(book: book)
        }
    }

    public func cancel(bookID: String) async {
        items.removeAll { item in
            item.bookID == bookID && (item.state == .queued || item.state == .downloading)
        }
    }

    public func markDownloaded(bookID: String, chapterID: String?) async {
        for index in items.indices where items[index].bookID == bookID && items[index].chapterID == chapterID {
            items[index].state = .downloaded
            items[index].progress = 1
            return
        }
        items.append(
            DownloadItem(
                bookID: bookID,
                chapterID: chapterID,
                title: "Download",
                state: .downloaded,
                progress: 1
            )
        )
    }

    public func localFileURL(bookID: String, chapter: AudioBookChapter) async -> URL? {
        nil
    }

    public func storageBytes(bookID: String) async -> Int64 {
        0
    }

    public func storageBytes(bookID: String, chapter: AudioBookChapter) async -> Int64 {
        0
    }

    public func delete(bookID: String) async {
        items.removeAll { $0.bookID == bookID }
    }

    public func delete(book: AudioBook, chapter: AudioBookChapter) async {
        items.removeAll { $0.bookID == book.id && $0.chapterID == chapter.id }
        if let index = items.firstIndex(where: { $0.bookID == book.id && $0.chapterID == nil && $0.state == .downloaded }) {
            items[index].state = .notDownloaded
            items[index].progress = 0
        }
    }
}

public struct SeedCatalogClient: CatalogSourceClient {
    public let source: BookSource = .libriVox
    private let books: [AudioBook]

    public init(books: [AudioBook] = AudioBook.seedLibrary) {
        self.books = books
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        Array(books.prefix(limit))
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        let trimmed = request.query.trimmingCharacters(in: .whitespacesAndNewlines)
        let sourceFiltered = books.filter { book in
            request.sources.contains(book.source)
        }
        let languageFiltered = sourceFiltered.filter { book in
            request.languages.contains(.all) || book.matchesLanguage(request.languages)
        }
        guard !trimmed.isEmpty else { return languageFiltered }
        return languageFiltered.filter { book in
            book.matches(trimmed, field: request.field)
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        book
    }
}

private extension AudioBook {
    func matchesLanguage(_ languages: Set<BookLanguagePreference>) -> Bool {
        let normalized = (language ?? "").localizedLowercase
        return languages.contains { preference in
            switch preference {
            case .all:
                return true
            case .english:
                return normalized.contains("english")
            case .french:
                return normalized.contains("french")
            case .german:
                return normalized.contains("german")
            case .italian:
                return normalized.contains("italian")
            case .spanish:
                return normalized.contains("spanish")
            case .polish:
                return normalized.contains("polish")
            }
        }
    }

    func matches(_ query: String, field: CatalogSearchField) -> Bool {
        let normalized = query.localizedLowercase
        func contains(_ value: String?) -> Bool {
            value?.localizedLowercase.contains(normalized) == true
        }

        switch field {
        case .title:
            return contains(title)
        case .author:
            return contains(author)
        case .chapter:
            return chapters.contains { contains($0.title) }
        case .reader:
            return chapters.contains { contains($0.reader) }
        case .genre:
            return genres.contains { contains($0) } || literaryGenres.contains { contains($0.name) }
        case .all:
            return contains(title) ||
                contains(author) ||
                chapters.contains { contains($0.title) || contains($0.reader) }
        }
    }
}
