import Foundation

public enum BookSource: String, Codable, CaseIterable, Identifiable, Sendable {
    case libriVox
    case lit2Go
    case wolneLektury
    case gutendex
    case localAsset
    case customLocal

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .libriVox: "LibriVox"
        case .lit2Go: "Lit2Go"
        case .wolneLektury: "Wolne Lektury"
        case .gutendex: "Gutendex"
        case .localAsset: "Local asset"
        case .customLocal: "Custom local"
        }
    }

    public var isPublicAudiobookSource: Bool {
        switch self {
        case .libriVox, .lit2Go, .gutendex, .wolneLektury:
            true
        case .localAsset, .customLocal:
            false
        }
    }
}

public extension BookSource {
    var donationURL: URL? {
        switch self {
        case .libriVox: URL(string: "https://librivox.org/pages/donate-to-librivox/")
        case .lit2Go: URL(string: "https://etc.usf.edu/lit2go/giving/")
        case .wolneLektury: URL(string: "https://wolnelektury.pl/pomagam/")
        case .gutendex, .localAsset, .customLocal: nil
        }
    }

    static var defaultPublicCatalogPriority: [BookSource] {
        [.lit2Go, .libriVox, .wolneLektury, .gutendex]
    }

    static func normalizedPublicCatalogPriority(_ sources: [BookSource]) -> [BookSource] {
        var seen: Set<BookSource> = []
        var priority: [BookSource] = []

        for source in sources where source.isPublicAudiobookSource && seen.insert(source).inserted {
            priority.append(source)
        }
        for source in defaultPublicCatalogPriority where seen.insert(source).inserted {
            priority.append(source)
        }
        return priority
    }
}

public enum LibraryStatus: String, Codable, Sendable {
    case notInLibrary
    case inLibrary
}

public enum DownloadState: String, Codable, Sendable {
    case notDownloaded
    case queued
    case downloading
    case downloaded
    case failed
}

public struct CatalogTag: Codable, Hashable, Sendable {
    public var name: String
    public var slug: String

    public init(name: String, slug: String = "") {
        self.name = name
        self.slug = slug
    }
}

public struct AudioBookChapter: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var title: String
    public var number: Int
    public var reader: String?
    public var director: String?
    public var durationSeconds: Int
    public var listenURL: String?
    public var localFileName: String?
    public var mimeType: String
    public var downloadState: DownloadState

    public init(
        id: String,
        title: String,
        number: Int,
        reader: String? = nil,
        director: String? = nil,
        durationSeconds: Int = 0,
        listenURL: String? = nil,
        localFileName: String? = nil,
        mimeType: String = "audio/mpeg",
        downloadState: DownloadState = .notDownloaded
    ) {
        self.id = id
        self.title = title
        self.number = number
        self.reader = reader
        self.director = director
        self.durationSeconds = durationSeconds
        self.listenURL = listenURL
        self.localFileName = localFileName
        self.mimeType = mimeType
        self.downloadState = downloadState
    }
}

public extension AudioBookChapter {
    var displayTitle: String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let base = trimmed.isEmpty ? "Chapter \(max(1, number))" : trimmed
        guard number > 0, !base.hasLeadingChapterOrdinal(number: number) else { return base }
        return String(format: "%02d: %@", number, base)
    }
}

private extension String {
    func hasLeadingChapterOrdinal(number: Int) -> Bool {
        let value = lowercased()
        if value.hasPrefix("chapter") ||
            value.hasPrefix("part") ||
            value.hasPrefix("section") ||
            value.hasPrefix("book ") ||
            value.hasPrefix("preface") ||
            value.hasPrefix("préface") {
            return true
        }
        if range(of: #"^\s*\d+\b"#, options: .regularExpression) != nil { return true }
        if range(
            of: #"^\s*[ivxlcdm]+\b[\s\.\-:]"#,
            options: [.regularExpression, .caseInsensitive]
        ) != nil {
            return true
        }
        return value.hasPrefix("\(number)")
    }
}

public struct AudioBook: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var title: String
    public var author: String
    public var description: String
    public var source: BookSource
    public var libraryStatus: LibraryStatus
    public var isFavorite: Bool
    public var coverImageURL: String?
    public var fullCoverImageURL: String?
    public var localCoverFileName: String?
    public var epubURL: String?
    public var audioEpubURL: String?
    public var daisyURL: String?
    public var sourceTextURL: String?
    public var sourceHTMLURL: String?
    public var chapters: [AudioBookChapter]
    public var totalDurationSeconds: Int
    public var libriVoxURL: String?
    public var lit2GoURL: String?
    public var wolneLekturyURL: String?
    public var gutenbergURL: String?
    public var language: String?
    public var originalLanguage: String?
    public var translators: [String]
    public var narrators: [String]
    public var publisher: String?
    public var releaseDate: String?
    public var genres: [String]
    public var authorTags: [CatalogTag]
    public var literaryEpochs: [CatalogTag]
    public var literaryKinds: [CatalogTag]
    public var literaryGenres: [CatalogTag]
    public var sourcePopularityRank: Int?
    public var addedAt: Date?

    public init(
        id: String,
        title: String,
        author: String,
        description: String = "",
        source: BookSource = .libriVox,
        libraryStatus: LibraryStatus = .notInLibrary,
        isFavorite: Bool = false,
        coverImageURL: String? = nil,
        fullCoverImageURL: String? = nil,
        localCoverFileName: String? = nil,
        epubURL: String? = nil,
        audioEpubURL: String? = nil,
        daisyURL: String? = nil,
        sourceTextURL: String? = nil,
        sourceHTMLURL: String? = nil,
        chapters: [AudioBookChapter] = [],
        totalDurationSeconds: Int? = nil,
        libriVoxURL: String? = nil,
        lit2GoURL: String? = nil,
        wolneLekturyURL: String? = nil,
        gutenbergURL: String? = nil,
        language: String? = nil,
        originalLanguage: String? = nil,
        translators: [String] = [],
        narrators: [String] = [],
        publisher: String? = nil,
        releaseDate: String? = nil,
        genres: [String] = [],
        authorTags: [CatalogTag] = [],
        literaryEpochs: [CatalogTag] = [],
        literaryKinds: [CatalogTag] = [],
        literaryGenres: [CatalogTag] = [],
        sourcePopularityRank: Int? = nil,
        addedAt: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.description = description
        self.source = source
        self.libraryStatus = libraryStatus
        self.isFavorite = isFavorite
        self.coverImageURL = coverImageURL
        self.fullCoverImageURL = fullCoverImageURL
        self.localCoverFileName = localCoverFileName
        self.epubURL = epubURL
        self.audioEpubURL = audioEpubURL
        self.daisyURL = daisyURL
        self.sourceTextURL = sourceTextURL
        self.sourceHTMLURL = sourceHTMLURL
        self.chapters = chapters
        self.totalDurationSeconds = totalDurationSeconds ?? chapters.reduce(0) { $0 + $1.durationSeconds }
        self.libriVoxURL = libriVoxURL
        self.lit2GoURL = lit2GoURL
        self.wolneLekturyURL = wolneLekturyURL
        self.gutenbergURL = gutenbergURL
        self.language = language
        self.originalLanguage = originalLanguage
        self.translators = translators
        self.narrators = narrators
        self.publisher = publisher
        self.releaseDate = releaseDate
        self.genres = genres
        self.authorTags = authorTags
        self.literaryEpochs = literaryEpochs
        self.literaryKinds = literaryKinds
        self.literaryGenres = literaryGenres
        self.sourcePopularityRank = sourcePopularityRank
        self.addedAt = addedAt
    }
}

public extension AudioBook {
    var canReadSourceText: Bool {
        [
            sourceTextURL,
            sourceHTMLURL,
            gutenbergURL,
            audioEpubURL,
            daisyURL,
            epubURL,
        ]
        .contains { value in
            value?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        }
    }
}

public struct Bookmark: Identifiable, Codable, Hashable, Sendable {
    public var id: UUID
    public var bookID: String
    public var chapterID: String
    public var positionMilliseconds: Int
    public var note: String
    public var selectedText: String?
    public var createdAt: Date

    public init(
        id: UUID = UUID(),
        bookID: String,
        chapterID: String,
        positionMilliseconds: Int,
        note: String = "",
        selectedText: String? = nil,
        createdAt: Date = .now
    ) {
        self.id = id
        self.bookID = bookID
        self.chapterID = chapterID
        self.positionMilliseconds = positionMilliseconds
        self.note = note
        self.selectedText = selectedText
        self.createdAt = createdAt
    }
}

public struct LocalUserProfile: Identifiable, Codable, Hashable, Sendable {
    public static let currentOnboardingVersion = 3
    public var id: UUID
    public var displayName: String
    public var hasCompletedOnboarding: Bool
    public var createdAt: Date
    public var updatedAt: Date

    public init(
        id: UUID = UUID(),
        displayName: String = "Listener",
        hasCompletedOnboarding: Bool = false,
        createdAt: Date = .now,
        updatedAt: Date = .now
    ) {
        self.id = id
        self.displayName = displayName
        self.hasCompletedOnboarding = hasCompletedOnboarding
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    public mutating func markOnboardingComplete(at date: Date = .now) {
        hasCompletedOnboarding = true
        updatedAt = date
    }

    public func requiresOnboarding(completedVersion: Int) -> Bool {
        !hasCompletedOnboarding || completedVersion < Self.currentOnboardingVersion
    }

    public mutating func touch(at date: Date = .now) {
        updatedAt = date
    }
}

public func playheadBookmark(
    bookID: String?,
    chapterID: String?,
    positionMilliseconds: Int,
    bookmarks: [Bookmark],
    toleranceMilliseconds: Int = 1_500
) -> Bookmark? {
    guard let bookID, let chapterID else { return nil }
    return bookmarks
        .filter { bookmark in
            bookmark.bookID == bookID &&
                bookmark.chapterID == chapterID &&
                abs(bookmark.positionMilliseconds - positionMilliseconds) <= toleranceMilliseconds
        }
        .min { lhs, rhs in
            let lhsDistance = abs(lhs.positionMilliseconds - positionMilliseconds)
            let rhsDistance = abs(rhs.positionMilliseconds - positionMilliseconds)
            if lhsDistance != rhsDistance {
                return lhsDistance < rhsDistance
            }
            return lhs.createdAt > rhs.createdAt
        }
}

public enum PlaybackFeedback: String, Codable, CaseIterable, Identifiable, Sendable {
    case none
    case liked
    case disliked

    public var id: String { rawValue }
}

public struct PlaybackFeedbackEntry: Identifiable, Codable, Hashable, Sendable {
    public var id: String
    public var bookID: String
    public var chapterID: String?
    public var feedback: PlaybackFeedback
    public var updatedAt: Date

    public init(
        id: String,
        bookID: String,
        chapterID: String? = nil,
        feedback: PlaybackFeedback,
        updatedAt: Date = .now
    ) {
        self.id = id
        self.bookID = bookID
        self.chapterID = chapterID
        self.feedback = feedback
        self.updatedAt = updatedAt
    }

    public static func key(bookID: String?, chapterID: String?, scope: FeedbackControlScope) -> String? {
        guard let bookID else { return nil }
        switch scope {
        case .hidden:
            return nil
        case .book:
            return "feedback-book::\(bookID)"
        case .chapter:
            guard let chapterID else { return nil }
            return "feedback-chapter::\(bookID)::\(chapterID)"
        }
    }
}

public struct QueueEntry: Identifiable, Codable, Hashable, Sendable {
    public var id: UUID
    public var bookID: String
    public var chapterID: String?
    public var addedAt: Date

    public init(id: UUID = UUID(), bookID: String, chapterID: String? = nil, addedAt: Date = .now) {
        self.id = id
        self.bookID = bookID
        self.chapterID = chapterID
        self.addedAt = addedAt
    }
}

public struct PlaybackProgress: Identifiable, Codable, Hashable, Sendable {
    public var id: String { bookID }
    public var bookID: String
    public var chapterID: String
    public var positionSeconds: Double
    public var durationSeconds: Double
    public var completedChapterIDs: Set<String>
    public var updatedAt: Date

    public init(
        bookID: String,
        chapterID: String,
        positionSeconds: Double = 0,
        durationSeconds: Double = 0,
        completedChapterIDs: Set<String> = [],
        updatedAt: Date = .now
    ) {
        self.bookID = bookID
        self.chapterID = chapterID
        self.positionSeconds = max(0, positionSeconds)
        self.durationSeconds = max(0, durationSeconds)
        self.completedChapterIDs = completedChapterIDs
        self.updatedAt = updatedAt
    }

    public var fractionCompleted: Double {
        guard durationSeconds > 0 else { return completedChapterIDs.isEmpty ? 0 : 1 }
        return min(max(positionSeconds / durationSeconds, 0), 1)
    }

    public var hasStarted: Bool {
        positionSeconds >= 5 || !completedChapterIDs.isEmpty
    }
}

public enum PlaybackDurationPolicy {
    /// Returns chapter duration metadata only when it can describe the currently playing item.
    /// A whole-book total is a valid fallback for a single-chapter book, but not for one unknown
    /// chapter inside a multi-chapter book.
    public static func initialDuration(
        chapterDurationSeconds: Int?,
        bookTotalDurationSeconds: Int,
        chapterCount: Int
    ) -> Double? {
        if let chapterDurationSeconds, chapterDurationSeconds > 0 {
            return Double(chapterDurationSeconds)
        }
        if chapterCount <= 1, bookTotalDurationSeconds > 0 {
            return Double(bookTotalDurationSeconds)
        }
        return nil
    }

    public static func boundedPosition(
        _ seconds: Double,
        resolvedDuration: Double?
    ) -> Double {
        let nonnegative = max(seconds.isFinite ? seconds : 0, 0)
        guard let resolvedDuration, resolvedDuration.isFinite, resolvedDuration > 0 else {
            return nonnegative
        }
        return min(nonnegative, resolvedDuration)
    }
}
