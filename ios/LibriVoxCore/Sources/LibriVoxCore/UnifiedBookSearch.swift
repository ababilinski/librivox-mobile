import Foundation

public enum BookSearchScope: String, CaseIterable, Identifiable, Sendable {
    case everything
    case library
    case catalogs

    public var id: String { rawValue }
}

public struct UnifiedBookSearchResult: Identifiable, Equatable, Sendable {
    public var book: AudioBook
    public var isInLibrary: Bool

    public var id: String { Self.identity(for: book) }

    public init(book: AudioBook, isInLibrary: Bool) {
        self.book = book
        self.isInLibrary = isInLibrary
    }

    fileprivate static func identity(for book: AudioBook) -> String {
        "\(book.source.rawValue):\(book.id)"
    }
}

public enum UnifiedBookSearch {
    /// Combines ranked saved books with the current catalog request's results.
    /// Catalog hits may match server metadata absent from the displayed book;
    /// their membership and order are therefore retained without matching again.
    /// The caller supplies catalog hits for the requested field and language;
    /// local records are matched against those filters here.
    /// A nil source filter includes saved books from every origin, including
    /// imports and catalogs that are no longer enabled for online discovery.
    public static func results(
        libraryBooks: [AudioBook],
        catalogBooks: [AudioBook],
        query: String,
        field: CatalogSearchField,
        languages: Set<BookLanguagePreference>,
        sources: Set<BookSource>? = nil,
        scope: BookSearchScope = .everything
    ) -> [UnifiedBookSearchResult] {
        guard !query.normalizedCatalogSearchText.isEmpty else { return [] }

        let languageFilter: Set<BookLanguagePreference> = languages.isEmpty ? [.all] : languages
        let eligibleLibrary = libraryBooks.filter { book in
            (sources?.contains(book.source) ?? true) && book.matchesCatalogLanguage(languageFilter)
        }
        var localByID: [String: AudioBook] = [:]
        for book in eligibleLibrary {
            let id = UnifiedBookSearchResult.identity(for: book)
            if localByID[id] == nil { localByID[id] = book }
        }

        var results: [UnifiedBookSearchResult] = []
        var seen: Set<String> = []
        if scope != .catalogs {
            let localMatches = eligibleLibrary.rankedForCatalogSearch(
                query: query,
                field: field,
                deduplicate: false
            )
            for book in localMatches {
                let result = UnifiedBookSearchResult(book: book, isInLibrary: true)
                if seen.insert(result.id).inserted { results.append(result) }
            }
        }

        if scope != .library {
            for book in catalogBooks where sources?.contains(book.source) ?? true {
                let id = UnifiedBookSearchResult.identity(for: book)
                guard seen.insert(id).inserted else { continue }
                let local = localByID[id]
                results.append(UnifiedBookSearchResult(
                    book: local ?? book,
                    isInLibrary: local != nil
                ))
            }
        }
        return results
    }
}
