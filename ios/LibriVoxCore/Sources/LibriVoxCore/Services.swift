import Foundation

public struct CatalogSearchRequest: Hashable, Sendable {
    public var query: String
    public var field: CatalogSearchField
    public var languages: Set<BookLanguagePreference>
    public var sources: Set<BookSource>

    public init(
        query: String,
        field: CatalogSearchField = .all,
        languages: Set<BookLanguagePreference> = [.english],
        sources: Set<BookSource> = [.libriVox]
    ) {
        self.query = query
        self.field = field
        self.languages = languages
        self.sources = sources
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
    func markDownloaded(bookID: String, chapterID: String?) async
}

@MainActor
public protocol PlaybackControlling: AnyObject {
    var currentBook: AudioBook? { get }
    var currentChapter: AudioBookChapter? { get }
    var isPlaying: Bool { get }
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

    public init(
        id: UUID = UUID(),
        bookID: String,
        chapterID: String? = nil,
        title: String,
        state: DownloadState = .queued,
        progress: Double = 0
    ) {
        self.id = id
        self.bookID = bookID
        self.chapterID = chapterID
        self.title = title
        self.state = state
        self.progress = progress
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

    public func markDownloaded(bookID: String, chapterID: String?) async {
        for index in items.indices where items[index].bookID == bookID && items[index].chapterID == chapterID {
            items[index].state = .downloaded
            items[index].progress = 1
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
        case .all:
            return contains(title) ||
                contains(author) ||
                chapters.contains { contains($0.title) || contains($0.reader) }
        }
    }
}
