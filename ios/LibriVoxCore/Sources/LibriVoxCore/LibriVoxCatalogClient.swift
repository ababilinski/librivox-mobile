import Foundation

public struct LibriVoxBackedCatalogClient: CatalogSourceClient {
    public let source: BookSource = .libriVox
    private let live: LibriVoxCatalogClient
    private let fallback: SeedCatalogClient

    public init(
        live: LibriVoxCatalogClient = LibriVoxCatalogClient(),
        fallback: SeedCatalogClient = SeedCatalogClient()
    ) {
        self.live = live
        self.fallback = fallback
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        do {
            return try await live.featuredBooks(limit: limit)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            return try await fallback.featuredBooks(limit: limit)
        }
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(.libriVox) else { return [] }
        do {
            return try await live.search(request)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            return try await fallback.search(request)
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        guard book.source == .libriVox else { return book }
        do {
            return try await live.hydrate(book).preservingLibraryState(from: book)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            return try await fallback.hydrate(book)
        }
    }
}

public struct LibriVoxCatalogClient: CatalogSourceClient {
    public let source: BookSource = .libriVox

    private static let feedURL = URL(string: "https://librivox.org/api/feed/audiobooks/")!
    private static let searchPageURL = URL(string: "https://librivox.org/search")!
    private static let advancedSearchURL = URL(string: "https://librivox.org/advanced_search")!
    private let transport: any CatalogHTTPTransport
    private let advancedSession: LibriVoxAdvancedSearchSession

    public init(transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport()) {
        self.transport = transport
        self.advancedSession = LibriVoxAdvancedSearchSession()
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        let data = try await request(extra: [
            URLQueryItem(name: "limit", value: String(limit)),
            URLQueryItem(name: "offset", value: "0"),
        ])
        return try parseBooks(from: data)
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(.libriVox) else { return [] }
        let trimmed = request.query.normalizedCatalogSearchText
        guard !trimmed.isEmpty else {
            return try await browse(request)
        }

        switch request.field {
        case .all:
            return try await withThrowingTaskGroup(of: [AudioBook].self) { group in
                for field in [CatalogSearchField.title, .author, .reader, .chapter, .genre] {
                    group.addTask { try await search(trimmed, field: field, request: request) }
                }
                var books: [AudioBook] = []
                for try await fieldBooks in group { books.append(contentsOf: fieldBooks) }
                return books.uniquedByCatalogIdentity()
            }
        default:
            return try await search(trimmed, field: request.field, request: request)
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        guard !book.id.hasPrefix("librivox-web-") else { return book }
        let data = try await request(extra: [
            URLQueryItem(name: "id", value: book.id),
        ])
        return try parseBooks(from: data).first ?? book
    }

    private func browse(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        var query = [
            URLQueryItem(name: "limit", value: "40"),
            URLQueryItem(name: "offset", value: "0"),
        ]
        if let languageCode = request.primaryLibriVoxLanguageCode {
            query.append(URLQueryItem(name: "language", value: languageCode))
        }
        return try parseBooks(from: await self.request(extra: query))
    }

    private func search(
        _ text: String,
        field: CatalogSearchField,
        request: CatalogSearchRequest
    ) async throws -> [AudioBook] {
        switch field {
        case .title, .all:
            var query = [
                URLQueryItem(name: "title", value: "^" + text),
                URLQueryItem(name: "limit", value: "40"),
                URLQueryItem(name: "offset", value: "0"),
            ]
            if let languageCode = request.primaryLibriVoxLanguageCode {
                query.append(URLQueryItem(name: "language", value: languageCode))
            }
            let feedBooks = try parseBooks(from: await self.request(extra: query))
            if !feedBooks.isEmpty { return feedBooks }
            return try await advancedSearch(text, field: field, request: request)
        case .author, .reader, .chapter:
            return try await advancedSearch(text, field: field, request: request)
        case .genre:
            var query = [
                URLQueryItem(name: "genre", value: "^" + text),
                URLQueryItem(name: "limit", value: "40"),
                URLQueryItem(name: "offset", value: "0"),
            ]
            if let languageCode = request.primaryLibriVoxLanguageCode {
                query.append(URLQueryItem(name: "language", value: languageCode))
            }
            return try parseBooks(from: await self.request(extra: query))
        }
    }

    private func request(extra: [URLQueryItem]) async throws -> Data {
        var components = URLComponents(url: Self.feedURL, resolvingAgainstBaseURL: false)
        components?.queryItems = [
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "extended", value: "1"),
            URLQueryItem(name: "coverart", value: "1"),
        ] + extra
        guard let url = components?.url else { throw URLError(.badURL) }

        var request = URLRequest(url: url)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")

        return try await transport.successfulData(for: request)
    }

    private func parseBooks(from data: Data) throws -> [AudioBook] {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let books = root["books"] as? [[String: Any]]
        else { throw URLError(.cannotParseResponse) }
        return books.compactMap(AudioBook.init(libriVoxJSON:))
    }

    private func advancedSearch(
        _ text: String,
        field: CatalogSearchField,
        request: CatalogSearchRequest
    ) async throws -> [AudioBook] {
        var components = URLComponents(url: Self.advancedSearchURL, resolvingAgainstBaseURL: false)
        let fieldName: String
        switch field {
        case .author: fieldName = "author"
        case .reader: fieldName = "reader"
        case .genre: fieldName = "keywords"
        case .chapter, .title, .all: fieldName = "title"
        }
        var values = [
            "title": "", "author": "", "reader": "", "keywords": "",
            "genre_id": "0", "status": "all", "project_type": "either",
            "recorded_language": request.primaryLibriVoxAdvancedLanguageID ?? "",
            "sort_order": "alpha", "search_page": "1", "search_form": "advanced",
            "q": "",
        ]
        values[fieldName] = text
        components?.queryItems = values.sorted { $0.key < $1.key }.map {
            URLQueryItem(name: $0.key, value: $0.value)
        }
        guard let url = components?.url else { throw URLError(.badURL) }
        var urlRequest = URLRequest(url: url)
        urlRequest.timeoutInterval = 60
        urlRequest.setValue("application/json, text/javascript, */*; q=0.01", forHTTPHeaderField: "Accept")
        urlRequest.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        urlRequest.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        urlRequest.setValue(Self.searchPageURL.absoluteString, forHTTPHeaderField: "Referer")
        urlRequest.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        let data = try await advancedSession.response(
            for: urlRequest,
            transport: transport,
            searchPageURL: Self.searchPageURL
        )
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              (root["status"] as? String)?.uppercased() == "SUCCESS",
              let html = root["results"] as? String
        else { throw URLError(.cannotParseResponse) }
        let stubs = Self.parseAdvancedSearchResults(html, matchedField: field)
        guard !stubs.isEmpty else { return [] }
        let resolved = await withTaskGroup(of: (Int, AudioBook?).self, returning: [AudioBook].self) { group in
            for (index, stub) in stubs.enumerated() {
                group.addTask {
                    let book = try? await resolveAdvancedBook(stub, request: request)
                    return (index, book)
                }
            }
            var books: [(Int, AudioBook)] = []
            for await (index, book) in group {
                if let book { books.append((index, book)) }
            }
            return books.sorted { $0.0 < $1.0 }.map(\.1)
        }
        guard !resolved.isEmpty else { throw URLError(.cannotParseResponse) }
        return resolved.uniquedByCatalogIdentity()
    }

    private func resolveAdvancedBook(
        _ stub: AudioBook,
        request: CatalogSearchRequest
    ) async throws -> AudioBook? {
        var query = [
            URLQueryItem(name: "title", value: "^" + stub.title.normalizedCatalogSearchText),
            URLQueryItem(name: "limit", value: "40"),
            URLQueryItem(name: "offset", value: "0"),
        ]
        if let languageCode = request.primaryLibriVoxLanguageCode {
            query.append(URLQueryItem(name: "language", value: languageCode))
        }
        let candidates = try parseBooks(from: await self.request(extra: query))
        let stubURL = stub.libriVoxURL?.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let normalizedTitle = stub.title.normalizedCatalogSearchText
        let normalizedAuthor = stub.author.normalizedCatalogSearchText
        let canonical = candidates.first {
            $0.libriVoxURL?.trimmingCharacters(in: CharacterSet(charactersIn: "/")) == stubURL
        } ?? candidates.first {
            $0.title.normalizedCatalogSearchText == normalizedTitle &&
                $0.author.normalizedCatalogSearchText == normalizedAuthor
        } ?? candidates.first {
            $0.title.normalizedCatalogSearchText == normalizedTitle
        }
        guard let canonical,
              !canonical.id.hasPrefix("librivox-web-"),
              canonical.chapters.contains(where: { $0.listenURL?.isEmpty == false })
        else { return nil }
        return canonical
    }

    static func parseAdvancedSearchResults(
        _ html: String,
        matchedField: CatalogSearchField
    ) -> [AudioBook] {
        html.catalogRegexMatches(#"<li[^>]*class=[\"'][^\"']*catalog-result[^\"']*[\"'][^>]*>(.*?)</li>"#)
            .compactMap { match -> AudioBook? in
                guard let body = match[safe: 1],
                      let headingHTML = body.catalogFirstMatch(#"<h3[^>]*>(.*?)</h3>"#)?[safe: 1],
                      let rawTitle = headingHTML.catalogHTMLPlainText.nilIfCatalogBlank
                else { return nil }
                let headingLinks = headingHTML.catalogRegexMatches(
                    #"<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"#
                )
                let href = headingLinks.last?[safe: 1]?.catalogHTMLDecoded.nilIfCatalogBlank
                    ?? body.catalogFirstMatch(
                        #"<a[^>]*href=[\"'](https://librivox\.org/[^\"']+/)[\"'][^>]*class=[\"'][^\"']*book-cover"#
                    )?[safe: 1]?.catalogHTMLDecoded.nilIfCatalogBlank
                guard let href else { return nil }
                let bookURL: String
                let displayTitle: String
                if let parent = rawTitle.catalogFirstMatch(#"^(.*?)\s*\(in\s+(.*?)\)\s*$"#) {
                    displayTitle = parent[safe: 2]?.catalogHTMLPlainText.nilIfCatalogBlank ?? rawTitle
                    bookURL = body.catalogRegexMatches(#"<a[^>]*href=[\"'](https://librivox\.org/[^\"']+/)[\"']"#)
                        .compactMap { $0[safe: 1] }
                        .last ?? href
                } else {
                    displayTitle = rawTitle
                    bookURL = href
                }
                let authorHTML = body.catalogFirstMatch(
                    #"<p[^>]*class=[\"'][^\"']*book-author[^\"']*[\"'][^>]*>(.*?)</p>"#
                )?[safe: 1] ?? ""
                let author = authorHTML.catalogRegexMatches(#"<a[^>]*>(.*?)</a>"#)
                    .compactMap { $0[safe: 1]?.catalogHTMLPlainText }
                    .map {
                        $0.replacingOccurrences(
                            of: #"\s*\([^)]*\)\s*$"#,
                            with: "",
                            options: .regularExpression
                        )
                    }
                    .filter { !$0.isEmpty }
                    .joined(separator: ", ")
                    .nilIfCatalogBlank ?? "Various"
                let cover = body.catalogFirstMatch(#"<img[^>]*(?:data-src|src)=[\"']([^\"']+)[\"']"#)?[safe: 1]?
                    .catalogHTMLDecoded.nilIfCatalogBlank
                let metadata = body.catalogFirstMatch(
                    #"<p[^>]*class=[\"'][^\"']*book-meta[^\"']*[\"'][^>]*>(.*?)</p>"#
                )?[safe: 1]?.catalogHTMLPlainText ?? ""
                let language = metadata.split(separator: "|").last.map(String.init)?
                    .trimmingCharacters(in: .whitespacesAndNewlines).nilIfCatalogBlank
                let slug = URL(string: bookURL)?.lastPathComponent.nilIfCatalogBlank
                    ?? bookURL.normalizedCatalogSearchText.replacingOccurrences(of: " ", with: "-")
                let chapter = matchedField == .chapter
                    ? AudioBookChapter(
                        id: "librivox-web-\(slug)-section",
                        title: rawTitle,
                        number: 1
                    )
                    : nil
                return AudioBook(
                    id: "librivox-web-\(slug)",
                    title: displayTitle,
                    author: author,
                    source: .libriVox,
                    coverImageURL: cover,
                    fullCoverImageURL: cover,
                    chapters: chapter.map { [$0] } ?? [],
                    libriVoxURL: bookURL,
                    language: language,
                    publisher: "LibriVox"
                )
            }
            .uniquedByCatalogIdentity()
    }
}

private actor LibriVoxAdvancedSearchSession {
    private var didPrime = false
    private var sessionCookie: String?
    private var requestInFlight = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func response(
        for request: URLRequest,
        transport: any CatalogHTTPTransport,
        searchPageURL: URL
    ) async throws -> Data {
        await acquire()
        defer { release() }
        try Task.checkCancellation()
        var request = request
        if let cookie = try await cookie(transport: transport, searchPageURL: searchPageURL) {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }
        return try await transport.successfulData(for: request)
    }

    private func cookie(
        transport: any CatalogHTTPTransport,
        searchPageURL: URL
    ) async throws -> String? {
        if didPrime { return sessionCookie }
        var request = URLRequest(url: searchPageURL)
        request.timeoutInterval = 60
        request.setValue("text/html", forHTTPHeaderField: "Accept")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        let response = try await transport.response(for: request)
        guard (200..<300).contains(response.statusCode) else { throw URLError(.badServerResponse) }
        if let setCookie = response.headers["set-cookie"],
           let match = setCookie.catalogFirstMatch(#"(?:^|[;,]\s*)(PHPSESSID=[^;,\s]+)"#)?[safe: 1] {
            sessionCookie = match
        }
        didPrime = true
        return sessionCookie
    }

    private func acquire() async {
        if !requestInFlight {
            requestInFlight = true
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    private func release() {
        if waiters.isEmpty {
            requestInFlight = false
        } else {
            waiters.removeFirst().resume()
        }
    }
}

private extension CatalogSearchRequest {
    var primaryLibriVoxLanguageCode: String? {
        if languages.contains(.all) { return nil }
        if languages.contains(.english) { return "English" }
        if languages.contains(.french) { return "French" }
        if languages.contains(.german) { return "German" }
        if languages.contains(.italian) { return "Italian" }
        if languages.contains(.spanish) { return "Spanish" }
        if languages.contains(.polish) { return "Polish" }
        return nil
    }

    var primaryLibriVoxAdvancedLanguageID: String? {
        if languages.contains(.all) { return nil }
        // LibriVox's advanced-search form uses database IDs rather than feed labels.
        if languages.contains(.english) { return "1" }
        if languages.contains(.polish) { return "51" }
        return nil
    }
}

private extension String {
    func catalogRegexMatches(_ pattern: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(
            pattern: pattern,
            options: [.caseInsensitive, .dotMatchesLineSeparators]
        ) else { return [] }
        let range = NSRange(startIndex..<endIndex, in: self)
        return regex.matches(in: self, range: range).map { match in
            (0..<match.numberOfRanges).map { index in
                let capture = match.range(at: index)
                guard capture.location != NSNotFound,
                      let swiftRange = Range(capture, in: self)
                else { return "" }
                return String(self[swiftRange])
            }
        }
    }

    func catalogFirstMatch(_ pattern: String) -> [String]? {
        catalogRegexMatches(pattern).first
    }

    var catalogHTMLPlainText: String {
        replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
            .catalogHTMLDecoded
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var catalogHTMLDecoded: String {
        replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&#8217;", with: "’")
    }

    var nilIfCatalogBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

private extension Array where Element == AudioBook {
    func uniquedByCatalogIdentity() -> [AudioBook] {
        var seen: Set<String> = []
        return filter { seen.insert("\($0.source.rawValue)|\($0.id)").inserted }
    }
}

private extension Array where Element == String {
    subscript(safe index: Int) -> String? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension AudioBook {
    init?(libriVoxJSON json: [String: Any]) {
        guard let id = json.stringValue("id") else { return nil }
        let rawTitle = json.stringValue("title") ?? "Untitled"
        let title = id == "3269" && rawTitle == "Jungle" ? "The Jungle" : rawTitle
        let archiveURL = json.stringValue("url_iarchive")
        let fullCover = Self.normalizedArchiveMediaURL(json.stringValue("coverart_jpg") ?? Self.archiveCoverURL(archiveURL))
        let thumbnail = Self.normalizedArchiveMediaURL(json.stringValue("coverart_thumbnail") ?? fullCover)
        let textSourceURL = json.stringValue("url_text_source")

        let authorNames = (json["authors"] as? [[String: Any]])?
            .compactMap { author -> String? in
                let first = author.stringValue("first_name") ?? ""
                let last = author.stringValue("last_name") ?? ""
                let joined = [first, last].joined(separator: " ").trimmingCharacters(in: .whitespaces)
                return joined.isEmpty ? nil : joined
            } ?? []

        let genreNames = (json["genres"] as? [[String: Any]])?
            .compactMap { $0.stringValue("name") } ?? []

        let chapters = ((json["sections"] as? [[String: Any]]) ?? [])
            .compactMap { section -> AudioBookChapter? in
                guard let listenURL = section.stringValue("listen_url") else { return nil }
                let number = Int(section.stringValue("section_number") ?? "") ?? 0
                let chapterID = section.stringValue("id") ?? "\(id)-section-\(number)"
                let readers = (section["readers"] as? [[String: Any]])?
                    .compactMap { $0.stringValue("display_name") }
                    .joined(separator: ", ")
                return AudioBookChapter(
                    id: chapterID,
                    title: section.stringValue("title") ?? "Chapter \(max(number, 1))",
                    number: number,
                    reader: readers?.isEmpty == false ? readers : nil,
                    durationSeconds: Int(section.stringValue("playtime") ?? "") ?? 0,
                    listenURL: listenURL
                )
            }
            .sorted { $0.number < $1.number }

        self.init(
            id: id,
            title: title,
            author: authorNames.isEmpty ? "Unknown author" : authorNames.joined(separator: ", "),
            description: (json.stringValue("description") ?? "").plainTextFromLibriVoxHTML(),
            source: .libriVox,
            coverImageURL: thumbnail,
            fullCoverImageURL: fullCover,
            sourceTextURL: textSourceURL?.gutenbergPlainTextURLString,
            sourceHTMLURL: textSourceURL,
            chapters: chapters,
            totalDurationSeconds: Int(json.stringValue("totaltimesecs") ?? "") ?? chapters.reduce(0) { $0 + $1.durationSeconds },
            libriVoxURL: json.stringValue("url_librivox"),
            gutenbergURL: textSourceURL,
            language: json.stringValue("language"),
            narrators: Array(Set(chapters.compactMap(\.reader))).sorted(),
            publisher: "LibriVox",
            genres: genreNames
        )
    }

    func preservingLibraryState(from existing: AudioBook) -> AudioBook {
        var copy = self
        copy.libraryStatus = existing.libraryStatus
        copy.isFavorite = existing.isFavorite
        copy.addedAt = existing.addedAt
        return copy
    }

    static func archiveCoverURL(_ archiveURL: String?) -> String? {
        guard let archiveURL,
              let range = archiveURL.range(of: "archive.org/details/") else { return nil }
        let identifier = archiveURL[range.upperBound...]
            .split(separator: "/")
            .first
            .map(String.init)?
            .split(separator: "?")
            .first
            .map(String.init)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let identifier, !identifier.isEmpty else { return nil }
        return "https://archive.org/services/img/\(identifier)"
    }

    static func normalizedArchiveMediaURL(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard var components = URLComponents(string: trimmed) else { return trimmed }
        if components.host == "www.archive.org" {
            components.host = "archive.org"
        }
        return components.url?.absoluteString ?? trimmed
    }
}

private extension Dictionary where Key == String, Value == Any {
    func stringValue(_ key: String) -> String? {
        if let string = self[key] as? String, !string.isEmpty, string != "null" { return string }
        if let number = self[key] as? NSNumber { return number.stringValue }
        return nil
    }
}

private extension String {
    func plainTextFromLibriVoxHTML() -> String {
        let withLineBreaks = replacingOccurrences(of: "<br>", with: "\n")
            .replacingOccurrences(of: "<br/>", with: "\n")
            .replacingOccurrences(of: "<br />", with: "\n")
        let stripped = withLineBreaks.replacingOccurrences(
            of: #"<[^>]+>"#,
            with: "",
            options: .regularExpression
        )
        return stripped
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var gutenbergPlainTextURLString: String? {
        guard let url = URL(string: self) else { return nil }
        let components = url.pathComponents
        guard let id = components.last(where: { $0.allSatisfy(\.isNumber) }) else { return nil }
        return "https://www.gutenberg.org/cache/epub/\(id)/pg\(id).txt"
    }
}
