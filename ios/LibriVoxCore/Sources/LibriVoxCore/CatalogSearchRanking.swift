import Foundation

enum CatalogSearchBucket: Int, Comparable {
    case title = 0
    case chapter = 1
    case author = 2
    case reader = 3
    case genre = 4
    case metadata = 5

    static func < (lhs: CatalogSearchBucket, rhs: CatalogSearchBucket) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}

struct CatalogSearchMatch {
    var bucket: CatalogSearchBucket
    var quality: Int
    var text: String
}

public struct CatalogSearchFacetCounts: Equatable, Sendable {
    public var total: Int
    public var sourceCounts: [BookSource: Int]
    public var fieldCounts: [CatalogSearchField: Int]
    public var languageCounts: [BookLanguagePreference: Int]

    public init(
        total: Int = 0,
        sourceCounts: [BookSource: Int] = [:],
        fieldCounts: [CatalogSearchField: Int] = [:],
        languageCounts: [BookLanguagePreference: Int] = [:]
    ) {
        self.total = total
        self.sourceCounts = sourceCounts
        self.fieldCounts = fieldCounts
        self.languageCounts = languageCounts
    }

    public static let empty = CatalogSearchFacetCounts()
}

public enum CatalogBookMerger {
    public static func merge(
        libriVox: [AudioBook],
        lit2Go: [AudioBook],
        wolneLektury: [AudioBook],
        gutendex: [AudioBook]
    ) -> [AudioBook] {
        merge([lit2Go, libriVox, wolneLektury, gutendex])
    }

    public static func merge(_ sourceBooksByPriority: [[AudioBook]]) -> [AudioBook] {
        mergeByPriority(sourceBooksByPriority)
    }

    public static func matchKey(_ book: AudioBook) -> String {
        [
            book.title.primaryCatalogTitle.normalizedCatalogMergeText,
            book.author.firstCatalogSurname.normalizedCatalogMergeText,
        ]
        .joined(separator: "|")
    }

    private static func mergeByPriority(_ sourceBooksByPriority: [[AudioBook]]) -> [AudioBook] {
        var mergedByKey: [String: AudioBook] = [:]
        var keys: [String] = []

        for books in sourceBooksByPriority {
            for book in books {
                let key = matchKey(book)
                if let primary = mergedByKey[key] {
                    mergedByKey[key] = primary.fillingMissingCatalogFields(from: book)
                } else {
                    mergedByKey[key] = book
                    keys.append(key)
                }
            }
        }

        return keys.compactMap { mergedByKey[$0] }
    }
}

public extension Array where Element == AudioBook {
    func filteredForCatalogSearch(_ request: CatalogSearchRequest) -> [AudioBook] {
        filter { book in
            request.sources.contains(book.source) && book.matchesCatalogLanguage(request.languages)
        }
        .rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
    }

    func catalogSearchFacetCounts(for request: CatalogSearchRequest) -> CatalogSearchFacetCounts {
        let sourceCounts = Dictionary(uniqueKeysWithValues: request.sourcePriority
            .filter(\.isPublicAudiobookSource)
            .map { source in
                let count = filter { book in
                    book.source == source && book.matchesCatalogLanguage(request.languages)
                }
                .rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
                .count
                return (source, count)
            })

        let fieldCounts = Dictionary(uniqueKeysWithValues: CatalogSearchField.allCases.map { field in
            let count = filter { book in
                request.sources.contains(book.source) && book.matchesCatalogLanguage(request.languages)
            }
            .rankedForCatalogSearch(query: request.query, field: field, keepUnmatched: false)
            .count
            return (field, count)
        })

        let languageCounts = Dictionary(uniqueKeysWithValues: BookLanguagePreference.allCases.map { language in
            let languageSet: Set<BookLanguagePreference> = [language]
            let count = filter { book in
                request.sources.contains(book.source) && book.matchesCatalogLanguage(languageSet)
            }
            .rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
            .count
            return (language, count)
        })

        return CatalogSearchFacetCounts(
            total: filteredForCatalogSearch(request).count,
            sourceCounts: sourceCounts,
            fieldCounts: fieldCounts,
            languageCounts: languageCounts
        )
    }

    func rankedForCatalogSearch(
        query: String,
        field: CatalogSearchField,
        keepUnmatched: Bool = false,
        deduplicate: Bool = true
    ) -> [AudioBook] {
        let normalizedQuery = query.normalizedCatalogSearchText
        guard !normalizedQuery.isEmpty else { return deduplicate ? deduplicatedCatalogBooks() : self }

        let ranked = enumerated()
            .compactMap { index, book -> RankedCatalogBook? in
                guard let match = book.catalogSearchMatch(query: normalizedQuery, field: field) else { return nil }
                return RankedCatalogBook(book: book, match: match, sourceIndex: index)
            }
            .sorted { lhs, rhs in
                if lhs.match.bucket != rhs.match.bucket { return lhs.match.bucket < rhs.match.bucket }
                if lhs.match.quality != rhs.match.quality { return lhs.match.quality < rhs.match.quality }
                let lhsPopularity = lhs.book.sourcePopularityRank ?? Int.max
                let rhsPopularity = rhs.book.sourcePopularityRank ?? Int.max
                if lhsPopularity != rhsPopularity { return lhsPopularity < rhsPopularity }
                let titleCompare = lhs.book.title.normalizedCatalogSearchTitle
                    .localizedStandardCompare(rhs.book.title.normalizedCatalogSearchTitle)
                if titleCompare != .orderedSame { return titleCompare == .orderedAscending }
                let authorCompare = lhs.book.author.normalizedCatalogSearchTitle
                    .localizedStandardCompare(rhs.book.author.normalizedCatalogSearchTitle)
                if authorCompare != .orderedSame { return authorCompare == .orderedAscending }
                return lhs.sourceIndex < rhs.sourceIndex
            }
            .map(\.book)

        let unmatched = keepUnmatched
            ? filter { $0.catalogSearchMatch(query: normalizedQuery, field: field) == nil }
            : []
        let results = ranked + unmatched
        return deduplicate ? results.deduplicatedCatalogBooks() : results
    }

    func deduplicatedCatalogBooks() -> [AudioBook] {
        var seenIDs: Set<String> = []
        var seenBooks: Set<String> = []
        return filter { book in
            seenIDs.insert(book.id).inserted &&
                seenBooks.insert(CatalogBookMerger.matchKey(book)).inserted
        }
    }
}

private struct RankedCatalogBook {
    var book: AudioBook
    var match: CatalogSearchMatch
    var sourceIndex: Int
}

extension AudioBook {
    func matchesCatalogLanguage(_ languages: Set<BookLanguagePreference>) -> Bool {
        if languages.contains(.all) { return true }
        let normalizedValues = [language, originalLanguage]
            .compactMap { $0?.normalizedCatalogSearchText }
            .filter { !$0.isEmpty }
        guard !normalizedValues.isEmpty else { return false }

        return languages.contains { preference in
            switch preference {
            case .all:
                return true
            case .english:
                return normalizedValues.contains { $0.contains("english") || $0 == "en" }
            case .french:
                return normalizedValues.contains { $0.contains("french") || $0 == "fr" }
            case .german:
                return normalizedValues.contains { $0.contains("german") || $0 == "de" }
            case .italian:
                return normalizedValues.contains { $0.contains("italian") || $0 == "it" }
            case .spanish:
                return normalizedValues.contains { $0.contains("spanish") || $0 == "es" }
            case .polish:
                return normalizedValues.contains { $0.contains("polish") || $0 == "pl" }
            }
        }
    }
}

private extension AudioBook {
    func catalogSearchMatch(query normalizedQuery: String, field: CatalogSearchField) -> CatalogSearchMatch? {
        guard !normalizedQuery.isEmpty else { return nil }

        func best(_ bucket: CatalogSearchBucket, values: [String]) -> CatalogSearchMatch? {
            values
                .compactMap { value -> CatalogSearchMatch? in
                    guard let quality = value.searchQuality(for: normalizedQuery) else { return nil }
                    return CatalogSearchMatch(bucket: bucket, quality: quality, text: value)
                }
                .min { lhs, rhs in
                    if lhs.quality != rhs.quality { return lhs.quality < rhs.quality }
                    if lhs.text.count != rhs.text.count { return lhs.text.count < rhs.text.count }
                    return lhs.text.localizedStandardCompare(rhs.text) == .orderedAscending
                }
        }

        let titleMatch = { best(.title, values: [title]) }
        let chapterMatch = { best(.chapter, values: chapters.map(\.title)) }
        let authorMatch = { best(.author, values: [author] + authorTags.flatMap(\.catalogSearchTerms)) }
        let readerMatch = {
            best(
                .reader,
                values: narrators + chapters.flatMap { [$0.reader, $0.director].compactMap { $0 } }
            )
        }
        let genreMatch = {
            best(
                .genre,
                values: genres + literaryGenres.flatMap(\.catalogSearchTerms)
            )
        }
        let epochMatch = { best(.metadata, values: literaryEpochs.flatMap(\.catalogSearchTerms)) }
        let kindMatch = { best(.metadata, values: literaryKinds.flatMap(\.catalogSearchTerms)) }

        switch field {
        case .all:
            return [
                titleMatch(),
                chapterMatch(),
                authorMatch(),
                readerMatch(),
                genreMatch(),
                epochMatch(),
                kindMatch(),
            ]
            .compactMap { $0 }
            .min { $0.bucket < $1.bucket }
        case .title:
            return titleMatch()
        case .author:
            return authorMatch()
        case .chapter:
            return chapterMatch()
        case .reader:
            return readerMatch()
        case .genre:
            return genreMatch()
        }
    }

    func fillingMissingCatalogFields(from secondary: AudioBook) -> AudioBook {
        var copy = self
        if copy.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            copy.description = secondary.description
        }
        copy.coverImageURL = copy.coverImageURL ?? secondary.coverImageURL
        copy.fullCoverImageURL = copy.fullCoverImageURL ?? secondary.fullCoverImageURL
        copy.localCoverFileName = copy.localCoverFileName ?? secondary.localCoverFileName
        copy.epubURL = copy.epubURL ?? secondary.epubURL
        copy.audioEpubURL = copy.audioEpubURL ?? secondary.audioEpubURL
        copy.daisyURL = copy.daisyURL ?? secondary.daisyURL
        copy.sourceTextURL = copy.sourceTextURL ?? secondary.sourceTextURL
        copy.sourceHTMLURL = copy.sourceHTMLURL ?? secondary.sourceHTMLURL
        if copy.chapters.isEmpty { copy.chapters = secondary.chapters }
        if copy.totalDurationSeconds <= 0 { copy.totalDurationSeconds = secondary.totalDurationSeconds }
        copy.libriVoxURL = copy.libriVoxURL ?? secondary.libriVoxURL
        copy.lit2GoURL = copy.lit2GoURL ?? secondary.lit2GoURL
        copy.wolneLekturyURL = copy.wolneLekturyURL ?? secondary.wolneLekturyURL
        copy.gutenbergURL = copy.gutenbergURL ?? secondary.gutenbergURL
        copy.language = copy.language ?? secondary.language
        copy.originalLanguage = copy.originalLanguage ?? secondary.originalLanguage
        if copy.translators.isEmpty { copy.translators = secondary.translators }
        if copy.narrators.isEmpty { copy.narrators = secondary.narrators }
        copy.publisher = copy.publisher ?? secondary.publisher
        copy.releaseDate = copy.releaseDate ?? secondary.releaseDate
        copy.genres = (copy.genres + secondary.genres).stableUnique()
        if copy.authorTags.isEmpty { copy.authorTags = secondary.authorTags }
        if copy.literaryEpochs.isEmpty { copy.literaryEpochs = secondary.literaryEpochs }
        if copy.literaryKinds.isEmpty { copy.literaryKinds = secondary.literaryKinds }
        if copy.literaryGenres.isEmpty { copy.literaryGenres = secondary.literaryGenres }
        copy.sourcePopularityRank = copy.sourcePopularityRank ?? secondary.sourcePopularityRank
        return copy
    }
}

private extension CatalogTag {
    var catalogSearchTerms: [String] {
        [name, slug].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}

private extension Array where Element == String {
    func stableUnique() -> [String] {
        var seen: Set<String> = []
        return filter { seen.insert($0).inserted }
    }
}

extension String {
    var primaryCatalogTitle: String {
        components(separatedBy: ";").first?
            .components(separatedBy: ":").first?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? self
    }

    var firstCatalogSurname: String {
        let firstAuthor = components(separatedBy: ",").first ?? self
        return firstAuthor
            .split(whereSeparator: { $0.isWhitespace })
            .last
            .map(String.init) ?? ""
    }

    var normalizedCatalogMergeText: String {
        let stripped = catalogCompatibilityFolded
        let withoutLeadingThe = stripped.hasPrefix("the ") ? String(stripped.dropFirst(4)) : stripped
        return withoutLeadingThe
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedCatalogSearchText: String {
        catalogCompatibilityFolded
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    private var catalogCompatibilityFolded: String {
        decomposedStringWithCompatibilityMapping
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()
            .replacingOccurrences(of: "ł", with: "l")
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "ð", with: "d")
            .replacingOccurrences(of: "þ", with: "th")
            .replacingOccurrences(of: "æ", with: "ae")
            .replacingOccurrences(of: "œ", with: "oe")
            .replacingOccurrences(of: "ø", with: "o")
            .replacingOccurrences(of: "ß", with: "ss")
    }

    var normalizedCatalogSearchTitle: String {
        normalizedCatalogSearchText
            .removingPrefix("the ")
            .removingPrefix("a ")
            .removingPrefix("an ")
    }

    func searchQuality(for normalizedQuery: String) -> Int? {
        let normalizedSelf = normalizedCatalogSearchText
        guard !normalizedSelf.isEmpty else { return nil }
        if normalizedSelf == normalizedQuery { return 0 }
        if normalizedSelf.hasPrefix(normalizedQuery) { return 1 }
        if normalizedSelf.split(separator: " ").contains(Substring(normalizedQuery)) { return 2 }
        if normalizedSelf.contains(normalizedQuery) { return 3 }
        if normalizedQuery.count >= 4, normalizedQuery.contains(normalizedSelf) { return 4 }
        return nil
    }

    func removingPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}
