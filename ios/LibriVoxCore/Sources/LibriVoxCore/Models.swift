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
        self.addedAt = addedAt
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
