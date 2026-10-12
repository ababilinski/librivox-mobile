import Foundation

public struct AuthorProfile: Identifiable, Hashable, Sendable {
    public var id: String
    public var name: String
    public var alternateNames: [String]
    public var biography: String?
    public var birthDate: String?
    public var deathDate: String?
    public var topWork: String?
    public var topSubjects: [String]
    public var workCount: Int?
    public var portraitURL: URL
    public var fallbackPortraitURL: URL?
    public var sourceURL: URL

    public init(
        id: String,
        name: String,
        alternateNames: [String] = [],
        biography: String? = nil,
        birthDate: String? = nil,
        deathDate: String? = nil,
        topWork: String? = nil,
        topSubjects: [String] = [],
        workCount: Int? = nil,
        portraitURL: URL,
        fallbackPortraitURL: URL? = nil,
        sourceURL: URL
    ) {
        self.id = id
        self.name = name
        self.alternateNames = alternateNames
        self.biography = biography
        self.birthDate = birthDate
        self.deathDate = deathDate
        self.topWork = topWork
        self.topSubjects = topSubjects
        self.workCount = workCount
        self.portraitURL = portraitURL
        self.fallbackPortraitURL = fallbackPortraitURL
        self.sourceURL = sourceURL
    }
}

public struct AuthorWorkMetric: Identifiable, Hashable, Sendable {
    public var title: String
    public var firstPublishYear: Int?
    public var popularityCount: Int?

    public var id: String { Self.normalizedTitle(title) }

    public init(title: String, firstPublishYear: Int? = nil, popularityCount: Int? = nil) {
        self.title = title
        self.firstPublishYear = firstPublishYear
        self.popularityCount = popularityCount
    }

    static func normalizedTitle(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .replacingOccurrences(of: "ł", with: "l")
            .replacingOccurrences(of: "Ł", with: "l")
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}

public enum AuthorBookSort: String, CaseIterable, Identifiable, Sendable {
    case alphabetical
    case mostRecent
    case mostPopular

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .alphabetical: "Alphabetical"
        case .mostRecent: "Most Recent"
        case .mostPopular: "Most Popular"
        }
    }

    public var systemImage: String {
        switch self {
        case .alphabetical: "textformat.abc"
        case .mostRecent: "calendar"
        case .mostPopular: "chart.bar.fill"
        }
    }
}

public enum AuthorBookSorter {
    public static func availableSorts(
        for books: [AudioBook],
        metrics: [AuthorWorkMetric]
    ) -> [AuthorBookSort] {
        var sorts: [AuthorBookSort] = [.alphabetical]
        if books.contains(where: { publicationYear(for: $0, metrics: metrics) != nil }) {
            sorts.append(.mostRecent)
        }
        if metrics.contains(where: { $0.popularityCount != nil }) {
            sorts.append(.mostPopular)
        }
        return sorts
    }

    public static func sorted(
        _ books: [AudioBook],
        by sort: AuthorBookSort,
        metrics: [AuthorWorkMetric]
    ) -> [AudioBook] {
        books.sorted { lhs, rhs in
            switch sort {
            case .alphabetical:
                return alphabeticallyPrecedes(lhs, rhs)
            case .mostRecent:
                let lhsYear = publicationYear(for: lhs, metrics: metrics)
                let rhsYear = publicationYear(for: rhs, metrics: metrics)
                if lhsYear != rhsYear {
                    return (lhsYear ?? Int.min) > (rhsYear ?? Int.min)
                }
                return alphabeticallyPrecedes(lhs, rhs)
            case .mostPopular:
                let lhsPopularity = metric(for: lhs, in: metrics)?.popularityCount
                let rhsPopularity = metric(for: rhs, in: metrics)?.popularityCount
                if lhsPopularity != rhsPopularity {
                    return (lhsPopularity ?? Int.min) > (rhsPopularity ?? Int.min)
                }
                return alphabeticallyPrecedes(lhs, rhs)
            }
        }
    }

    private static func metric(for book: AudioBook, in metrics: [AuthorWorkMetric]) -> AuthorWorkMetric? {
        let title = AuthorWorkMetric.normalizedTitle(book.title)
        return metrics.first { $0.id == title }
    }

    private static func publicationYear(for book: AudioBook, metrics: [AuthorWorkMetric]) -> Int? {
        if let year = metric(for: book, in: metrics)?.firstPublishYear {
            return year
        }
        guard let releaseDate = book.releaseDate else { return nil }
        return releaseDate.firstMatch(of: /\b(1[0-9]{3}|20[0-9]{2}|2100)\b/).flatMap { Int($0.output.1) }
    }

    private static func alphabeticallyPrecedes(_ lhs: AudioBook, _ rhs: AudioBook) -> Bool {
        let titleOrder = lhs.title.localizedStandardCompare(rhs.title)
        if titleOrder != .orderedSame { return titleOrder == .orderedAscending }
        return lhs.id.localizedStandardCompare(rhs.id) == .orderedAscending
    }
}

public actor OpenLibraryAuthorClient {
    public static let shared = OpenLibraryAuthorClient()

    private let transport: any CatalogHTTPTransport
    private var profileCache: [String: AuthorProfile] = [:]
    private var workMetricCache: [String: [AuthorWorkMetric]] = [:]

    public init(transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport()) {
        self.transport = transport
    }

    public func profile(for author: String) async -> AuthorProfile? {
        let cacheKey = Self.normalizedName(author)
        guard !cacheKey.isEmpty else { return nil }
        if let cached = profileCache[cacheKey] { return cached }

        guard let document = try? await searchDocument(for: author) else { return nil }
        let detail = try? await authorDetail(key: document.key)
        let fallbackPortraitURL = await wikipediaPortraitURL(for: document.name)
        guard
            let portraitURL = URL(string: "https://covers.openlibrary.org/a/olid/\(document.key)-L.jpg?default=false"),
            let sourceURL = URL(string: "https://openlibrary.org/authors/\(document.key)")
        else { return nil }

        let profile = AuthorProfile(
            id: document.key,
            name: document.name,
            alternateNames: document.alternateNames ?? [],
            biography: detail?.biography?.value,
            birthDate: detail?.birthDate ?? document.birthDate,
            deathDate: detail?.deathDate ?? document.deathDate,
            topWork: document.topWork,
            topSubjects: document.topSubjects ?? [],
            workCount: document.workCount,
            portraitURL: portraitURL,
            fallbackPortraitURL: fallbackPortraitURL,
            sourceURL: sourceURL
        )
        profileCache[cacheKey] = profile
        return profile
    }

    public func workMetrics(for author: String) async -> [AuthorWorkMetric] {
        let cacheKey = Self.normalizedName(author)
        guard !cacheKey.isEmpty else { return [] }
        if let cached = workMetricCache[cacheKey] { return cached }

        guard var components = URLComponents(string: "https://openlibrary.org/search.json") else { return [] }
        components.queryItems = [
            URLQueryItem(name: "author", value: author),
            URLQueryItem(
                name: "fields",
                value: "title,first_publish_year,ratings_count,want_to_read_count,already_read_count,currently_reading_count"
            ),
            URLQueryItem(name: "limit", value: "100"),
        ]
        guard let url = components.url,
              let data = try? await transport.catalogData(from: url, accept: "application/json")
        else { return [] }

        let response = try? JSONDecoder().decode(OpenLibraryWorkSearchResponse.self, from: data)
        var bestByTitle: [String: AuthorWorkMetric] = [:]
        for document in response?.docs ?? [] {
            let metric = AuthorWorkMetric(
                title: document.title,
                firstPublishYear: document.firstPublishYear,
                popularityCount: document.popularityCount
            )
            let previous = bestByTitle[metric.id]
            if previous == nil || (metric.popularityCount ?? -1) > (previous?.popularityCount ?? -1) {
                bestByTitle[metric.id] = metric
            }
        }
        let metrics = Array(bestByTitle.values)
        workMetricCache[cacheKey] = metrics
        return metrics
    }

    private func searchDocument(for author: String) async throws -> OpenLibraryAuthorSearchResponse.Document {
        guard var components = URLComponents(string: "https://openlibrary.org/search/authors.json") else {
            throw URLError(.badURL)
        }
        components.queryItems = [URLQueryItem(name: "q", value: author)]
        guard let url = components.url else { throw URLError(.badURL) }
        let data = try await transport.catalogData(from: url, accept: "application/json")
        let response = try JSONDecoder().decode(OpenLibraryAuthorSearchResponse.self, from: data)
        let expected = Self.normalizedName(author)
        guard let document = response.docs.first(where: { document in
            Self.normalizedName(document.name) == expected ||
                (document.alternateNames ?? []).contains { Self.normalizedName($0) == expected }
        }) else {
            throw URLError(.cannotParseResponse)
        }
        return document
    }

    private func authorDetail(key: String) async throws -> OpenLibraryAuthorDetail {
        guard let url = URL(string: "https://openlibrary.org/authors/\(key).json") else {
            throw URLError(.badURL)
        }
        let data = try await transport.catalogData(from: url, accept: "application/json")
        return try JSONDecoder().decode(OpenLibraryAuthorDetail.self, from: data)
    }

    private func wikipediaPortraitURL(for author: String) async -> URL? {
        guard var components = URLComponents(string: "https://en.wikipedia.org/w/api.php") else { return nil }
        components.queryItems = [
            URLQueryItem(name: "action", value: "query"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "formatversion", value: "2"),
            URLQueryItem(name: "generator", value: "search"),
            URLQueryItem(name: "gsrsearch", value: author),
            URLQueryItem(name: "gsrnamespace", value: "0"),
            URLQueryItem(name: "gsrlimit", value: "5"),
            URLQueryItem(name: "prop", value: "pageimages|pageterms"),
            URLQueryItem(name: "piprop", value: "thumbnail"),
            URLQueryItem(name: "pithumbsize", value: "600"),
            URLQueryItem(name: "pilimit", value: "5"),
            URLQueryItem(name: "wbptterms", value: "description"),
            URLQueryItem(name: "redirects", value: "1"),
        ]
        guard let url = components.url,
              let data = try? await transport.catalogData(from: url, accept: "application/json"),
              let response = try? JSONDecoder().decode(WikipediaPageImageResponse.self, from: data)
        else { return nil }

        let expected = Self.normalizedName(author)
        let pages = response.query?.pages ?? []
        let exactPage = pages.first { Self.normalizedName($0.title) == expected }
        let describedAuthor = pages.first { page in
            let normalizedTitle = Self.normalizedName(page.title)
            return (normalizedTitle.hasPrefix(expected) || expected.hasPrefix(normalizedTitle)) &&
                page.description.map(Self.isAuthorDescription) == true
        }
        return (exactPage ?? describedAuthor)?.thumbnail.flatMap { URL(string: $0.source) }
    }

    private static func isAuthorDescription(_ value: String) -> Bool {
        let normalized = value.lowercased()
        return ["author", "writer", "novelist", "poet", "playwright", "essayist", "journalist"]
            .contains { normalized.contains($0) }
    }

    private static func normalizedName(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .replacingOccurrences(of: "ł", with: "l")
            .replacingOccurrences(of: "Ł", with: "l")
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }
}

private struct OpenLibraryAuthorSearchResponse: Decodable {
    struct Document: Decodable {
        var key: String
        var name: String
        var alternateNames: [String]?
        var birthDate: String?
        var deathDate: String?
        var topWork: String?
        var topSubjects: [String]?
        var workCount: Int?

        enum CodingKeys: String, CodingKey {
            case key
            case name
            case alternateNames = "alternate_names"
            case birthDate = "birth_date"
            case deathDate = "death_date"
            case topWork = "top_work"
            case topSubjects = "top_subjects"
            case workCount = "work_count"
        }
    }

    var docs: [Document]
}

private struct OpenLibraryAuthorDetail: Decodable {
    struct Biography: Decodable {
        var value: String

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if let value = try? container.decode(String.self) {
                self.value = value
            } else {
                self.value = try container.decode([String: String].self)["value"] ?? ""
            }
        }
    }

    var biography: Biography?
    var birthDate: String?
    var deathDate: String?

    enum CodingKeys: String, CodingKey {
        case biography = "bio"
        case birthDate = "birth_date"
        case deathDate = "death_date"
    }
}

private struct OpenLibraryWorkSearchResponse: Decodable {
    struct Document: Decodable {
        var title: String
        var firstPublishYear: Int?
        var ratingsCount: Int?
        var wantToReadCount: Int?
        var alreadyReadCount: Int?
        var currentlyReadingCount: Int?

        var popularityCount: Int? {
            let values = [ratingsCount, wantToReadCount, alreadyReadCount, currentlyReadingCount]
                .compactMap { $0 }
            return values.isEmpty ? nil : values.reduce(0, +)
        }

        enum CodingKeys: String, CodingKey {
            case title
            case firstPublishYear = "first_publish_year"
            case ratingsCount = "ratings_count"
            case wantToReadCount = "want_to_read_count"
            case alreadyReadCount = "already_read_count"
            case currentlyReadingCount = "currently_reading_count"
        }
    }

    var docs: [Document]
}

private struct WikipediaPageImageResponse: Decodable {
    struct Query: Decodable {
        var pages: [Page]
    }

    struct Page: Decodable {
        struct Thumbnail: Decodable {
            var source: String
        }

        struct Terms: Decodable {
            var description: [String]?
        }

        var title: String
        var thumbnail: Thumbnail?
        var terms: Terms?

        var description: String? { terms?.description?.first }
    }

    var query: Query?
}
