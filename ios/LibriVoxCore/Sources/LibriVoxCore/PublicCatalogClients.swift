import Foundation

public struct Lit2GoCatalogClient: CatalogSourceClient {
    public let source: BookSource = .lit2Go
    private static let baseURL = URL(string: "https://etc.usf.edu")!
    private static let allLetters = Array("abcdefghijklmnopqrstuvwxyz").map(String.init)
    private let transport: any CatalogHTTPTransport

    public init(transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport()) {
        self.transport = transport
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        let stubs = try await browseStubs(letters: Array(Self.allLetters.prefix(4)))
        return try await hydrate(stubs: Array(stubs.prefix(max(1, limit))))
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(.lit2Go), request.allowsEnglish else { return [] }
        let trimmed = request.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return try await featuredBooks(limit: 40)
        }

        let stubs = try await browseStubs(letters: Self.allLetters)
        try Task.checkCancellation()
        let matches = stubs.filter { stub in
            Self.matches(stub: stub, query: trimmed, field: request.field)
        }
        return try await hydrate(stubs: Array(matches.rankedForLit2GoSearch(query: trimmed).prefix(24)))
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        guard let decoded = Self.decodeID(book.id) else { return book }
        return try await fetchBookDetail(numericID: decoded.numericID, slug: decoded.slug)
            .preservingUserState(from: book)
    }

    private func browseStubs(letters: [String]) async throws -> [Lit2GoStub] {
        let liveStubs = await withTaskGroup(of: [Lit2GoStub].self, returning: [Lit2GoStub].self) { group in
            for letter in letters {
                group.addTask {
                    guard !Task.isCancelled else { return [] }
                    let url = Self.baseURL.appendingPathComponent("lit2go/books/index/\(letter)/")
                    let html = (try? await transport.catalogText(from: url, timeoutInterval: 6)) ?? ""
                    return Self.parseLetterIndex(html)
                }
            }

            var collected: [Lit2GoStub] = []
            for await letterStubs in group {
                collected.append(contentsOf: letterStubs)
            }
            return collected
        }

        guard !liveStubs.isEmpty else { throw URLError(.cannotParseResponse) }
        let stubs = liveStubs
            .uniqued { $0.id }
            .sorted {
                $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
            }
        return stubs
    }

    private func hydrate(stubs: [Lit2GoStub]) async throws -> [AudioBook] {
        guard !stubs.isEmpty else { return [] }
        return try await withThrowingTaskGroup(of: (Int, AudioBook?).self) { group in
            for (index, stub) in stubs.enumerated() {
                group.addTask {
                    try Task.checkCancellation()
                    do {
                        let detail = try await fetchBookDetail(numericID: stub.id, slug: stub.slug)
                        return (index, detail)
                    } catch is CancellationError {
                        throw CancellationError()
                    } catch {
                        return (index, nil)
                    }
                }
            }

            var indexedBooks: [(Int, AudioBook)] = []
            for try await (index, book) in group {
                if let book { indexedBooks.append((index, book)) }
            }
            guard !indexedBooks.isEmpty else { throw URLError(.cannotParseResponse) }
            return indexedBooks
                .sorted { $0.0 < $1.0 }
                .map(\.1)
        }
    }

    private func fetchBookDetail(numericID: String, slug: String) async throws -> AudioBook {
        let url = Self.baseURL.appendingPathComponent("lit2go/\(numericID)/\(slug)/")
        let html = try await transport.catalogText(from: url, timeoutInterval: 6)
        guard !html.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              html.firstMatch(
                #"<h2[^>]*>(.*?)</h2>"#,
                options: [.caseInsensitive, .dotMatchesLineSeparators]
              )?[1].htmlPlainText.nilIfBlank != nil
        else { throw URLError(.cannotParseResponse) }
        let book = Self.parseBookDetail(numericID: numericID, slug: slug, html: html)
        guard !book.chapters.isEmpty,
              book.chapters.allSatisfy({ $0.listenURL?.nilIfBlank != nil })
        else { throw URLError(.cannotParseResponse) }
        return book
    }

    private static func parseLetterIndex(_ html: String) -> [Lit2GoStub] {
        let anchors = html.matches(
            #"<a([^>]*)>(.*?)</a>"#,
            options: [.caseInsensitive, .dotMatchesLineSeparators]
        )
        return anchors.compactMap { match in
            guard let attributes = match[safe: 1],
                  attributes.range(
                    of: #"class=["'][^"']*book_icon"#,
                    options: [.regularExpression, .caseInsensitive]
                  ) != nil,
                  let href = attributes.firstMatch(#"href=["']([^"']+)["']"#)?[1],
                  let pathMatch = href.firstMatch(#"/lit2go/(\d+)/([a-z0-9-]+)/?"#)
            else { return nil }
            let id = pathMatch[1]
            let slug = pathMatch[2]
            let anchorHTML = match[safe: 2] ?? ""
            let title = anchorHTML.firstMatch(#"<img[^>]*alt=["']([^"']+)["']"#)?[1]
                .htmlPlainText
                .nilIfBlank ?? slug.titleized
            let cover = anchorHTML.firstMatch(#"<img[^>]*(?:data-src|src)=["']([^"']+)["']"#)?[1]
                .absoluteURLString(base: Self.baseURL)
                ?? "https://etc.usf.edu/lit2go/static/thumbnails/books/\(id).png"
            let author = html.authorNearLit2GoBookAnchor(id: id, slug: slug) ?? "Unknown author"
            return Lit2GoStub(id: id, slug: slug, title: title, author: author, coverURL: cover)
        }
    }

    static func parseLetterIndexBooks(_ html: String) -> [AudioBook] {
        parseLetterIndex(html).map(\.audioBook)
    }

    static func parseBookDetail(numericID: String, slug: String, html: String) -> AudioBook {
        let title = html.firstMatch(#"<h2[^>]*>(.*?)</h2>"#, options: [.caseInsensitive, .dotMatchesLineSeparators])?[1]
            .htmlPlainText
            .nilIfBlank ?? slug.titleized
        let author = html.firstMatch(
            #"<a[^>]+href=["'][^"']*/lit2go/authors/[^"']+["'][^>]*>(.*?)</a>"#,
            options: [.caseInsensitive, .dotMatchesLineSeparators]
        )?[1].htmlPlainText.nilIfBlank ?? "Unknown author"
        let description = html.firstMatch(#"<p[^>]*>(.*?)</p>"#, options: [.caseInsensitive, .dotMatchesLineSeparators])?[1]
            .htmlPlainText
            .nilIfBlank ?? ""
        let cover = html.firstMatch(#"<img[^>]*(?:data-src|src)=["']([^"']*thumbnails/books/[^"']+)["']"#)?[1]
            .absoluteURLString(base: Self.baseURL)
            ?? "https://etc.usf.edu/lit2go/static/thumbnails/books/\(numericID).png"
        let chapters = parseChapters(numericID: numericID, slug: slug, html: html)
        return AudioBook(
            id: Self.encodeID(numericID: numericID, slug: slug),
            title: title,
            author: author,
            description: description,
            source: .lit2Go,
            coverImageURL: cover,
            fullCoverImageURL: cover,
            chapters: chapters,
            totalDurationSeconds: chapters.reduce(0) { $0 + $1.durationSeconds },
            lit2GoURL: "https://etc.usf.edu/lit2go/\(numericID)/\(slug)/",
            language: "English",
            publisher: "Lit2Go",
            sourcePopularityRank: Int(numericID)
        )
    }

    private static func parseChapters(numericID: String, slug: String, html: String) -> [AudioBookChapter] {
        let pattern = #"<a[^>]+href=["']([^"']*/lit2go/\#(numericID)/\#(slug)/(\d+)/([a-z0-9-]+)/?[^"']*)["'][^>]*>(.*?)</a>"#
        let refs = html.matches(pattern, options: [.caseInsensitive, .dotMatchesLineSeparators])
            .compactMap { match -> (id: String, slug: String, title: String)? in
                guard let chapterNumericID = match[safe: 2],
                      let chapterSlug = match[safe: 3],
                      Int(chapterNumericID) != nil
                else { return nil }
                let rawTitle = match[safe: 4]?.htmlPlainText.nilIfBlank ?? chapterSlug.titleized
                return (chapterNumericID, chapterSlug, rawTitle)
            }
            .uniqued { $0.id }

        return refs.enumerated()
            .map { offset, ref in
                let chapterNumber = offset + 1
                let audioURL = "https://etc.usf.edu/lit2go/audio/mp3/\(slug)-\(String(format: "%03d", chapterNumber))-\(ref.slug).\(ref.id).mp3"
                return AudioBookChapter(
                    id: "\(Self.encodeID(numericID: numericID, slug: slug))-\(ref.id)",
                    title: ref.title,
                    number: chapterNumber,
                    listenURL: audioURL
                )
            }
            .sorted { $0.number < $1.number }
    }

    private static func matches(stub: Lit2GoStub, query: String, field: CatalogSearchField) -> Bool {
        switch field {
        case .title:
            return stub.title.localizedCaseInsensitiveContains(query)
        case .author:
            return stub.author.localizedCaseInsensitiveContains(query)
        case .all:
            return stub.title.localizedCaseInsensitiveContains(query) ||
                stub.author.localizedCaseInsensitiveContains(query)
        case .chapter, .reader, .genre:
            return false
        }
    }

    private static let fallbackStubs: [Lit2GoStub] = [
        Lit2GoStub(id: "1", slug: "alices-adventures-in-wonderland", title: "Alice's Adventures in Wonderland", author: "Lewis Carroll", coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/1.png"),
        Lit2GoStub(id: "21", slug: "the-adventures-of-huckleberry-finn", title: "The Adventures of Huckleberry Finn", author: "Mark Twain", coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/21.png"),
        Lit2GoStub(id: "35", slug: "aesops-fables", title: "Aesop's Fables", author: "Aesop", coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/35.png"),
        Lit2GoStub(id: "100", slug: "pride-and-prejudice", title: "Pride and Prejudice", author: "Jane Austen", coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/100.png"),
        Lit2GoStub(id: "108", slug: "winesburg-ohio", title: "Winesburg, Ohio", author: "Sherwood Anderson", coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/108.png"),
        Lit2GoStub(
            id: "168",
            slug: "common-sense",
            title: "Common Sense",
            author: "Thomas Paine",
            coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/168.png",
            chapters: [
                AudioBookChapter(
                    id: "lit2go-168__common-sense-1378",
                    title: "Introduction",
                    number: 1,
                    listenURL: "https://etc.usf.edu/lit2go/audio/mp3/common-sense-001-introduction.1378.mp3"
                ),
            ]
        ),
        Lit2GoStub(
            id: "200",
            slug: "the-jungle",
            title: "The Jungle",
            author: "Upton Sinclair",
            coverURL: "https://etc.usf.edu/lit2go/static/thumbnails/books/200.png",
            chapters: [
                AudioBookChapter(
                    id: "lit2go-200__the-jungle-1",
                    title: "Chapter 1",
                    number: 1,
                    listenURL: "https://etc.usf.edu/lit2go/audio/mp3/the-jungle-001-chapter-1.1.mp3"
                ),
            ]
        ),
    ]

    private static func encodeID(numericID: String, slug: String) -> String {
        "lit2go-\(numericID)__\(slug)"
    }

    private static func decodeID(_ id: String) -> (numericID: String, slug: String)? {
        let raw = id.replacingOccurrences(of: "lit2go-", with: "")
        let parts = raw.components(separatedBy: "__")
        guard parts.count == 2, !parts[0].isEmpty, !parts[1].isEmpty else { return nil }
        return (parts[0], parts[1])
    }
}

public struct GutendexCatalogClient: CatalogSourceClient {
    public let source: BookSource = .gutendex
    private static let booksURL = URL(string: "https://gutendex.com/books/")!
    private let transport: any CatalogHTTPTransport

    public init(transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport()) {
        self.transport = transport
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        try await fetch(queryItems: [
            URLQueryItem(name: "sort", value: "popular"),
            URLQueryItem(name: "mime_type", value: "audio"),
        ], limit: limit)
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(.gutendex) else { return [] }
        if request.field == .chapter || request.field == .reader { return [] }
        var items = [
            URLQueryItem(name: "mime_type", value: "audio"),
        ]
        let trimmed = request.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            items.append(URLQueryItem(name: "search", value: trimmed))
        } else {
            items.append(URLQueryItem(name: "sort", value: "popular"))
        }
        if let code = request.primaryGutendexLanguageCode {
            items.append(URLQueryItem(name: "languages", value: code))
        }
        let books = try await fetch(queryItems: items, limit: 40)
        guard !trimmed.isEmpty else { return books }
        return books.filter { book in
            switch request.field {
            case .title:
                return book.title.localizedCaseInsensitiveContains(trimmed)
            case .author:
                return book.author.localizedCaseInsensitiveContains(trimmed)
            case .all:
                return book.title.localizedCaseInsensitiveContains(trimmed) ||
                    book.author.localizedCaseInsensitiveContains(trimmed) ||
                    book.genres.joined(separator: " ").localizedCaseInsensitiveContains(trimmed)
            case .chapter, .reader:
                return false
            case .genre:
                return book.genres.joined(separator: " ").localizedCaseInsensitiveContains(trimmed)
            }
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        guard book.source == .gutendex,
              book.id.hasPrefix("gutendex-"),
              let id = book.id.replacingOccurrences(of: "gutendex-", with: "").nilIfBlank
        else { return book }
        let url = Self.booksURL.appendingPathComponent(id)
        let data = try await transport.catalogData(from: url, accept: "application/json")
        return Self.parseBooks(data).first?.preservingUserState(from: book) ?? book
    }

    private func fetch(queryItems: [URLQueryItem], limit: Int) async throws -> [AudioBook] {
        var components = URLComponents(url: Self.booksURL, resolvingAgainstBaseURL: false)
        components?.queryItems = queryItems
        guard let url = components?.url else { throw URLError(.badURL) }
        let data = try await transport.catalogData(from: url, accept: "application/json")
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["results"] is [[String: Any]]
        else { throw URLError(.cannotParseResponse) }
        return Array(Self.parseBooks(data).prefix(max(1, limit)))
    }

    static func parseBooks(_ data: Data) -> [AudioBook] {
        guard let root = try? JSONSerialization.jsonObject(with: data) else { return [] }
        let objects: [[String: Any]]
        if let dict = root as? [String: Any], let results = dict["results"] as? [[String: Any]] {
            objects = results
        } else if let dict = root as? [String: Any] {
            objects = [dict]
        } else {
            objects = []
        }
        return objects.compactMap(Self.parseBook)
    }

    private static func parseBook(_ json: [String: Any]) -> AudioBook? {
        guard let gutenbergID = json.intValue("id"),
              let title = json.stringValue("title"),
              let formats = json["formats"] as? [String: Any],
              let audio = formats.preferredAudioFormat
        else { return nil }
        let author = (json["authors"] as? [[String: Any]])?
            .compactMap { $0.stringValue("name")?.displayPersonName }
            .joined(separator: ", ")
            .nilIfBlank ?? "Unknown author"
        let summaries = (json["summaries"] as? [String]) ?? []
        let languages = (json["languages"] as? [String]) ?? []
        let genres = ((json["bookshelves"] as? [String]) ?? []) + ((json["subjects"] as? [String]) ?? [])
        let id = "gutendex-\(gutenbergID)"
        return AudioBook(
            id: id,
            title: title,
            author: author,
            description: summaries.first ?? "",
            source: .gutendex,
            coverImageURL: formats.imageURL,
            fullCoverImageURL: formats.imageURL,
            epubURL: formats.epubURL,
            sourceTextURL: formats.plainTextURL,
            sourceHTMLURL: formats.htmlURL,
            chapters: [
                AudioBookChapter(
                    id: "\(id)-audio",
                    title: "Complete audiobook",
                    number: 1,
                    listenURL: audio.url,
                    mimeType: audio.mimeType
                ),
            ],
            gutenbergURL: "https://www.gutenberg.org/ebooks/\(gutenbergID)",
            language: Self.languageLabel(languages.first),
            publisher: "Project Gutenberg",
            genres: Array(genres.prefix(12))
        )
    }

    private static func languageLabel(_ code: String?) -> String? {
        switch code?.lowercased() {
        case "en": "English"
        case "fr": "French"
        case "de": "German"
        case "es": "Spanish"
        case "it": "Italian"
        case "pl": "Polish"
        case .some(let value): value
        case nil: nil
        }
    }
}

public struct WolneLekturyCatalogClient: CatalogSourceClient {
    public let source: BookSource = .wolneLektury
    private static let apiBase = URL(string: "https://wolnelektury.pl/api")!
    private static let siteBase = URL(string: "https://wolnelektury.pl")!
    private let transport: any CatalogHTTPTransport
    private let indexCache = WolneLekturyIndexCache()

    public init(transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport()) {
        self.transport = transport
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        let stubs = try await fetchStubs()
        return Array(stubs.prefix(max(1, limit))).map(\.audioBook)
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(.wolneLektury), request.allowsPolish else { return [] }
        let trimmed = request.query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return try await featuredBooks(limit: 40)
        }
        if request.field == .reader || request.field == .chapter { return [] }
        // Parent titles include multi-volume books such as Lalka. The audiobook endpoint also
        // contains individual volumes and poems, which must not replace their parent title.
        let stubs = try await fetchStubs()
        try Task.checkCancellation()
        let matches = stubs
            .filter { Self.matches(stub: $0, query: trimmed, field: request.field) }
        // Present matching metadata immediately; opening a book hydrates its audio chapters.
        let books = Array(matches.prefix(24)).map(\.audioBook)
        try Task.checkCancellation()
        if !books.isEmpty { return books }
        return try await Self.addingExactTitleFallback(to: books) {
            try await exactTitleFallback(for: trimmed, field: request.field)
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        guard book.source == .wolneLektury,
              let slug = Self.decodeID(book.id)
        else { return book }
        return try await fetchDetail(slug: slug, includeFullDescription: true)
            .preservingUserState(from: book)
    }

    private func fetchStubs() async throws -> [WolneStub] {
        let stubs = try await indexCache.load { [transport] in
            let url = Self.apiBase.appendingPathComponent("parent_books/")
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
            components?.queryItems = [URLQueryItem(name: "format", value: "json")]
            guard let requestURL = components?.url else { throw URLError(.badURL) }
            let data = try await transport.catalogData(from: requestURL, accept: "application/json", timeoutInterval: 20)
            return try Self.parseStubsStrict(data)
        }
        try Task.checkCancellation()
        return stubs
    }

    private func fetchDetail(slug: String, includeFullDescription: Bool = false) async throws -> AudioBook {
        let url = Self.apiBase.appendingPathComponent("books/\(slug)/")
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.queryItems = [URLQueryItem(name: "format", value: "json")]
        guard let requestURL = components?.url else { throw URLError(.badURL) }
        let data = try await transport.catalogData(from: requestURL, accept: "application/json")
        var book = try Self.parseDetail(slug: slug, data: data)

        if book.chapters.isEmpty {
            let childSlugs = Self.audioChildSlugs(from: data)
            if !childSlugs.isEmpty {
                var childBooks: [AudioBook] = []
                for childSlug in childSlugs {
                    try Task.checkCancellation()
                    do {
                        let childBook = try await fetchDetail(slug: childSlug)
                        guard childBook.hasPlayableAudio else { continue }
                        childBooks.append(childBook)
                    } catch is CancellationError {
                        throw CancellationError()
                    } catch {
                        continue
                    }
                }
                book = try Self.parseDetail(slug: slug, data: data, childBooks: childBooks)
            }
        }

        let needsChapterNames = book.chapters.contains {
            $0.title.range(of: #"Rozdział\s+[IVXLCDM0-9]+$"#, options: [.regularExpression, .caseInsensitive]) != nil
        }
        if includeFullDescription || needsChapterNames,
           let xmlURL = Self.wolneXMLURL(from: data) {
            do {
                let xml = try await transport.catalogText(
                    from: xmlURL,
                    accept: "application/xml",
                    timeoutInterval: 12
                )
                book = WolneLekturyMetadata.addingWrittenChapterNames(to: book, fromXML: xml)
                if includeFullDescription, let fullDescription = Self.parseWolneAbstract(fromXML: xml) {
                    book.description = fullDescription
                }
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                // The API fragment remains useful when the optional editorial abstract is unavailable.
            }
        }

        return book
    }

    private func exactTitleFallback(for query: String, field: CatalogSearchField) async throws -> AudioBook? {
        guard field == .all || field == .title,
              let slug = Self.wolneSlugCandidate(for: query)
        else { return nil }
        let book = try await fetchDetail(slug: slug)
        guard book.hasPlayableAudio,
              Self.matches(book: book, query: query, field: field)
        else { return nil }
        return book
    }

    static func addingExactTitleFallback(
        to fuzzyMatches: [AudioBook],
        fetch: @Sendable () async throws -> AudioBook?
    ) async throws -> [AudioBook] {
        var books = fuzzyMatches
        do {
            if let exactBook = try await fetch(),
               !books.contains(where: { $0.id == exactBook.id }) {
                books.insert(exactBook, at: 0)
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            // Partial queries rarely map to a real slug; fuzzy catalog matches remain valid.
        }
        return books.uniqued { $0.id }
    }

    static func wolneSlugCandidate(for query: String) -> String? {
        let folded = query
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "pl_PL"))
            .lowercased()
            .replacingOccurrences(of: "\u{0142}", with: "l")
        return folded
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .nilIfBlank
    }

    private static func matches(stub: WolneStub, query: String, field: CatalogSearchField) -> Bool {
        switch field {
        case .title:
            return stub.title.looseCatalogContains(query)
        case .author:
            return stub.author.looseCatalogContains(query)
        case .all:
            return stub.title.looseCatalogContains(query) ||
                stub.author.looseCatalogContains(query) ||
                stub.genres.joined(separator: " ").looseCatalogContains(query)
        case .chapter, .reader:
            return false
        case .genre:
            return stub.genres.joined(separator: " ").looseCatalogContains(query)
        }
    }

    private static func matches(book: AudioBook, query: String, field: CatalogSearchField) -> Bool {
        switch field {
        case .title:
            return book.title.looseCatalogContains(query)
        case .all:
            return book.title.looseCatalogContains(query) ||
                book.author.looseCatalogContains(query) ||
                book.genres.joined(separator: " ").looseCatalogContains(query)
        case .author:
            return book.author.looseCatalogContains(query)
        case .chapter, .reader:
            return false
        case .genre:
            return book.genres.joined(separator: " ").looseCatalogContains(query)
        }
    }

    private static func parseStubs(_ data: Data) -> [WolneStub] {
        guard let root = try? JSONSerialization.jsonObject(with: data) else { return [] }
        let objects: [[String: Any]]
        if let array = root as? [[String: Any]] {
            objects = array
        } else if let dict = root as? [String: Any], let books = dict["books"] as? [[String: Any]] {
            objects = books
        } else {
            objects = []
        }
        return objects.compactMap { json in
            guard json.boolValue("has_audio") ||
                    (json["children"] as? [[String: Any]])?.contains(where: { $0.boolValue("has_audio") }) == true,
                  let slug = json.stringValue("slug")
            else { return nil }
            let title = json.stringValue("title") ?? slug.titleized
            let author = json.stringValue("author") ?? "Unknown author"
            let cover = [
                json.stringValue("simple_thumb"),
                json.stringValue("cover_thumb"),
                json.stringValue("cover"),
            ].compactMap { $0?.absoluteURLString(base: Self.siteBase) }.first
            let genres = [json.stringValue("genre"), json.stringValue("kind"), json.stringValue("epoch")]
                .compactMap { $0 }
            return WolneStub(slug: slug, title: title, author: author, coverURL: cover, genres: genres)
        }
    }

    private static func parseStubsStrict(_ data: Data) throws -> [WolneStub] {
        let root = try JSONSerialization.jsonObject(with: data)
        guard root is [[String: Any]] || (root as? [String: Any])?["books"] is [[String: Any]] else {
            throw URLError(.cannotParseResponse)
        }
        return parseStubs(data)
    }

    static func parseDetail(slug: String, data: Data) throws -> AudioBook {
        try parseDetail(slug: slug, data: data, childBooks: [])
    }

    static func parseDetail(slug: String, data: Data, childBooks: [AudioBook]) throws -> AudioBook {
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw URLError(.cannotParseResponse)
        }
        let title = json.stringValue("title") ?? slug.titleized
        let author = (json["authors"] as? [[String: Any]])?
            .compactMap { $0.stringValue("name") }
            .joined(separator: ", ")
            .nilIfBlank ?? json.stringValue("author") ?? "Unknown author"
        let media = (json["media"] as? [[String: Any]]) ?? []
        let authors = (json["authors"] as? [[String: Any]])?.compactMap { $0.stringValue("name") } ?? [author]
        let parentTitle = (json["parent"] as? [String: Any])?.stringValue("title")
        let allMP3 = media.filter { $0.stringValue("type")?.localizedCaseInsensitiveCompare("mp3") == .orderedSame }
            .enumerated().map { (originalIndex: $0.offset, item: $0.element) }
        let playableMP3 = allMP3.filter { entry in
            let item = entry.item
            let label = WolneLekturyMetadata.chapterTitle(
                item.stringValue("name"), bookTitle: title, authors: authors,
                ancestorTitles: [parentTitle].compactMap { $0 }, number: 1, chapterCount: allMP3.count
            ).lowercased()
            return item.stringValue("url")?.localizedCaseInsensitiveContains("stopka") != true &&
                !["stopka", "strona redakcyjna", "nota redakcyjna"].contains(label)
        }
        let mp3 = playableMP3.isEmpty ? allMP3 : playableMP3
        let duration = (json.stringValue("audio_length") ?? "").durationSeconds
        let chapters = mp3.enumerated().compactMap { index, entry -> AudioBookChapter? in
            let item = entry.item
            guard let url = item.stringValue("url")?.absoluteURLString(base: Self.siteBase) else { return nil }
            return AudioBookChapter(
                id: "\(slug)-\(entry.originalIndex + 1)",
                title: WolneLekturyMetadata.chapterTitle(
                    item.stringValue("name"), bookTitle: title, authors: authors,
                    ancestorTitles: [parentTitle].compactMap { $0 }, number: index + 1, chapterCount: mp3.count
                ),
                number: index + 1,
                reader: item.stringValue("artist"),
                director: item.stringValue("director"),
                durationSeconds: mp3.count == 1 ? duration : 0,
                listenURL: url
            )
        }
        let cover = json.stringValue("cover")?.absoluteURLString(base: Self.siteBase)
        let thumbnail = json.stringValue("simple_thumb")?.absoluteURLString(base: Self.siteBase)
            ?? json.stringValue("cover_thumb")?.absoluteURLString(base: Self.siteBase)
            ?? cover
        let translators = (json["translators"] as? [[String: Any]])?.compactMap { $0.stringValue("name") } ?? []
        let genres = [
            json.tagNames("genres"),
            json.tagNames("kinds"),
            json.tagNames("epochs"),
        ].flatMap { $0 }
        let description = Self.wolneDescription(from: json)
        var book = AudioBook(
            id: Self.encodeID(slug),
            title: title,
            author: author,
            description: description,
            source: .wolneLektury,
            coverImageURL: thumbnail,
            fullCoverImageURL: cover ?? thumbnail,
            epubURL: json.stringValue("epub")?.absoluteURLString(base: Self.siteBase),
            audioEpubURL: media.firstMediaURL(type: "audio.epub", base: Self.siteBase),
            daisyURL: media.firstMediaURL(type: "daisy", base: Self.siteBase),
            sourceTextURL: json.stringValue("txt")?.absoluteURLString(base: Self.siteBase),
            sourceHTMLURL: json.stringValue("html")?.absoluteURLString(base: Self.siteBase),
            chapters: chapters,
            totalDurationSeconds: duration > 0 ? duration : chapters.reduce(0) { $0 + $1.durationSeconds },
            wolneLekturyURL: json.stringValue("url")?.absoluteURLString(base: Self.siteBase)
                ?? "https://wolnelektury.pl/katalog/lektura/\(slug)/",
            language: "Polish",
            originalLanguage: json.stringValue("language"),
            translators: translators,
            narrators: Array(Set(chapters.compactMap(\.reader))).sorted(),
            publisher: "Wolne Lektury",
            genres: genres
        )
        if book.chapters.isEmpty {
            book = Self.addingChildAudio(childBooks, to: book)
        }
        return book
    }

    static func audioChildSlugs(from data: Data) -> [String] {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
        var children = ((json["children"] as? [[String: Any]]) ?? []).filter { $0.boolValue("has_audio") }
        if children.allSatisfy({ WolneLekturyMetadata.volumeNumber(in: $0.stringValue("title") ?? "") != nil }) {
            children.sort {
                WolneLekturyMetadata.volumeNumber(in: $0.stringValue("title") ?? "")! <
                    WolneLekturyMetadata.volumeNumber(in: $1.stringValue("title") ?? "")!
            }
        }
        return children
            .compactMap { child -> String? in
                guard child.boolValue("has_audio") else { return nil }
                return child.stringValue("slug")?.nilIfBlank
            }
            .uniqued { $0 }
    }

    private static func addingChildAudio(_ childBooks: [AudioBook], to parent: AudioBook) -> AudioBook {
        var playableChildren = childBooks.filter(\.hasPlayableAudio)
        if playableChildren.allSatisfy({ WolneLekturyMetadata.volumeNumber(in: $0.title) != nil }) {
            playableChildren.sort { WolneLekturyMetadata.volumeNumber(in: $0.title)! < WolneLekturyMetadata.volumeNumber(in: $1.title)! }
        }
        guard !playableChildren.isEmpty else { return parent }

        var nextNumber = 1
        var mergedChapters: [AudioBookChapter] = []
        for child in playableChildren {
            for childChapter in child.chapters where childChapter.listenURL?.nilIfBlank != nil {
                var chapter = childChapter
                chapter.number = nextNumber
                mergedChapters.append(chapter)
                nextNumber += 1
            }
        }
        guard !mergedChapters.isEmpty else { return parent }

        var book = parent
        book.chapters = mergedChapters
        book.totalDurationSeconds = playableChildren.reduce(0) { total, child in
            total + max(child.totalDurationSeconds, child.chapters.reduce(0) { $0 + $1.durationSeconds })
        }
        if book.totalDurationSeconds == 0 {
            book.totalDurationSeconds = mergedChapters.reduce(0) { $0 + $1.durationSeconds }
        }
        book.narrators = Array(Set(
            playableChildren.flatMap(\.narrators) + mergedChapters.compactMap(\.reader)
        )).sorted()
        return book
    }

    private static func wolneDescription(from json: [String: Any]) -> String {
        let fragment = json["fragment_data"] as? [String: Any]
        let candidates = [
            json.stringValue("description"),
            json.stringValue("abstract"),
            json.stringValue("simple_desc"),
            fragment?.stringValue("html"),
            json.stringValue("fragment_data"),
        ]

        return candidates
            .compactMap { $0?.htmlPlainText.nilIfBlank }
            .first ?? ""
    }

    private static func wolneXMLURL(from data: Data) -> URL? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let value = json.stringValue("xml")?.absoluteURLString(base: Self.siteBase)
        else { return nil }
        return URL(string: value)
    }

    static func parseWolneAbstract(fromXML xml: String) -> String? {
        guard let rawAbstract = xml.firstMatch(
            #"<abstrakt(?:\s[^>]*)?>(.*?)</abstrakt>"#,
            options: [.caseInsensitive, .dotMatchesLineSeparators]
        )?[1] else { return nil }

        return rawAbstract
            .replacingOccurrences(
                of: #"</(?:akap|naglowek|podpis|lista|punkt)>"#,
                with: "\n\n",
                options: .regularExpression
            )
            .htmlPlainText
            .replacingOccurrences(of: #"[\t ]+"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\n[\t ]+"#, with: "\n", options: .regularExpression)
            .replacingOccurrences(of: #"\n\s*\n(?:\s*\n)*"#, with: "\n\n", options: .regularExpression)
            .nilIfBlank
    }

    private static func encodeID(_ slug: String) -> String { "wolnelektury-\(slug)" }

    private static func decodeID(_ id: String) -> String? {
        guard id.hasPrefix("wolnelektury-") else { return nil }
        return id.replacingOccurrences(of: "wolnelektury-", with: "").nilIfBlank
    }
}

private struct Lit2GoStub {
    var id: String
    var slug: String
    var title: String
    var author: String
    var coverURL: String?
    var chapters: [AudioBookChapter] = []

    var audioBook: AudioBook {
        AudioBook(
            id: "lit2go-\(id)__\(slug)",
            title: title,
            author: author,
            source: .lit2Go,
            coverImageURL: coverURL,
            fullCoverImageURL: coverURL,
            chapters: chapters,
            lit2GoURL: "https://etc.usf.edu/lit2go/\(id)/\(slug)/",
            language: "English",
            publisher: "Lit2Go",
            sourcePopularityRank: Int(id)
        )
    }
}

private extension Array where Element == Lit2GoStub {
    func rankedForLit2GoSearch(query: String) -> [Lit2GoStub] {
        let normalizedQuery = query.normalizedLit2GoSearchText
        guard !normalizedQuery.isEmpty else { return self }
        return enumerated()
            .sorted { lhs, rhs in
                let lhsQuality = lhs.element.title.searchQuality(forLit2GoQuery: normalizedQuery) ?? Int.max
                let rhsQuality = rhs.element.title.searchQuality(forLit2GoQuery: normalizedQuery) ?? Int.max
                if lhsQuality != rhsQuality { return lhsQuality < rhsQuality }
                if lhs.element.title.count != rhs.element.title.count {
                    return lhs.element.title.count < rhs.element.title.count
                }
                let titleCompare = lhs.element.title.localizedStandardCompare(rhs.element.title)
                if titleCompare != .orderedSame { return titleCompare == .orderedAscending }
                return lhs.offset < rhs.offset
            }
            .map(\.element)
    }
}

private actor WolneLekturyIndexCache {
    private var stubs: [WolneStub]?
    private var storedAt = Date.distantPast
    private var pending: Task<[WolneStub], Error>?

    func load(_ fetch: @Sendable @escaping () async throws -> [WolneStub]) async throws -> [WolneStub] {
        if Date().timeIntervalSince(storedAt) <= 30 * 60, let stubs { return stubs }
        if let pending { return try await pending.value }
        // All fields use one bounded index fetch. A changed query can reuse that same catalog.
        let task = Task { try await fetch() }
        pending = task
        defer { pending = nil }
        let loaded = try await task.value
        stubs = loaded
        storedAt = Date()
        return loaded
    }
}

private struct WolneStub: Sendable {
    var slug: String
    var title: String
    var author: String
    var coverURL: String?
    var genres: [String]

    var audioBook: AudioBook {
        AudioBook(
            id: "wolnelektury-\(slug)",
            title: title,
            author: author,
            source: .wolneLektury,
            coverImageURL: coverURL,
            fullCoverImageURL: coverURL,
            wolneLekturyURL: "https://wolnelektury.pl/katalog/lektura/\(slug)/",
            language: "Polish",
            publisher: "Wolne Lektury",
            genres: genres
        )
    }
}

private extension AudioBook {
    var hasPlayableAudio: Bool {
        chapters.contains { $0.listenURL?.nilIfBlank != nil }
    }
}

private enum HTTPClient {
    static func data(from url: URL, accept: String, timeoutInterval: TimeInterval = 12) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = timeoutInterval
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode)
        else { throw URLError(.badServerResponse) }
        return data
    }

    static func text(from url: URL, accept: String, timeoutInterval: TimeInterval = 12) async throws -> String {
        String(data: try await data(from: url, accept: accept, timeoutInterval: timeoutInterval), encoding: .utf8) ?? ""
    }
}

private enum AppleBookArtworkClient {
    static func coverURL(title: String, author: String) async throws -> String? {
        let query = [title, author].joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return nil }
        var components = URLComponents(string: "https://itunes.apple.com/search")
        components?.queryItems = [
            URLQueryItem(name: "term", value: query),
            URLQueryItem(name: "country", value: "US"),
            URLQueryItem(name: "media", value: "ebook"),
            URLQueryItem(name: "entity", value: "ebook"),
            URLQueryItem(name: "limit", value: "8"),
        ]
        guard let url = components?.url else { return nil }
        let data = try await HTTPClient.data(from: url, accept: "application/json", timeoutInterval: 6)
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let results = root["results"] as? [[String: Any]]
        else { return nil }
        return results
            .compactMap { $0.stringValue("artworkUrl100") ?? $0.stringValue("artworkUrl60") }
            .map { $0.fullSizeAppleArtworkURL }
            .first
    }
}

private struct AudioFormat {
    var mimeType: String
    var url: String
}

private extension CatalogSearchRequest {
    var allowsEnglish: Bool {
        languages.contains(.all) || languages.contains(.english)
    }

    var allowsPolish: Bool {
        languages.contains(.all) || languages.contains(.polish)
    }

    var primaryGutendexLanguageCode: String? {
        if languages.contains(.all) { return nil }
        if languages.contains(.english) { return "en" }
        if languages.contains(.french) { return "fr" }
        if languages.contains(.german) { return "de" }
        if languages.contains(.italian) { return "it" }
        if languages.contains(.spanish) { return "es" }
        if languages.contains(.polish) { return "pl" }
        return nil
    }
}

private extension Dictionary where Key == String, Value == Any {
    func stringValue(_ key: String) -> String? {
        if let string = self[key] as? String, !string.isEmpty, string != "null" { return string }
        if let number = self[key] as? NSNumber { return number.stringValue }
        return nil
    }

    func intValue(_ key: String) -> Int? {
        if let int = self[key] as? Int { return int }
        if let number = self[key] as? NSNumber { return number.intValue }
        return nil
    }

    func boolValue(_ key: String) -> Bool {
        if let bool = self[key] as? Bool { return bool }
        if let number = self[key] as? NSNumber { return number.boolValue }
        if let string = self[key] as? String { return string == "true" || string == "1" }
        return false
    }

    func tagNames(_ key: String) -> [String] {
        (self[key] as? [[String: Any]])?.compactMap { $0.stringValue("name") } ?? []
    }

    var imageURL: String? {
        first { key, _ in
            key.localizedCaseInsensitiveContains("image/")
        }?.value as? String
    }

    var preferredAudioFormat: AudioFormat? {
        let preferred = ["audio/mpeg", "audio/mp4", "audio/ogg"]
        for mimeType in preferred {
            if let entry = first(where: { $0.key.compare(mimeType, options: .caseInsensitive) == .orderedSame }),
               let url = entry.value as? String,
               url.hasPrefix("http") {
                return AudioFormat(mimeType: mimeType, url: url)
            }
        }
        return first { key, value in
            key.localizedCaseInsensitiveContains("audio/") && (value as? String)?.hasPrefix("http") == true
        }.flatMap { key, value in
            (value as? String).map { AudioFormat(mimeType: key.components(separatedBy: ";").first ?? key, url: $0) }
        }
    }

    var plainTextURL: String? {
        preferredURL(containing: "text/plain")
    }

    var htmlURL: String? {
        preferredURL(containing: "text/html")
    }

    var epubURL: String? {
        preferredURL(containing: "application/epub+zip")
    }

    private func preferredURL(containing mimeFragment: String) -> String? {
        first { key, value in
            key.localizedCaseInsensitiveContains(mimeFragment) &&
                (value as? String)?.hasPrefix("http") == true
        }?.value as? String
    }
}

private extension Array {
    func uniqued<Key: Hashable>(by key: (Element) -> Key) -> [Element] {
        var seen: Set<Key> = []
        return filter { seen.insert(key($0)).inserted }
    }
}

private extension Array where Element == [String: Any] {
    func firstMediaURL(type: String, base: URL) -> String? {
        first { item in
            item.stringValue("type")?.localizedCaseInsensitiveCompare(type) == .orderedSame
        }?
        .stringValue("url")?
        .absoluteURLString(base: base)
    }
}

private extension String {
    func matches(_ pattern: String, options: NSRegularExpression.Options = []) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else { return [] }
        let range = NSRange(startIndex..., in: self)
        return regex.matches(in: self, range: range).map { result in
            (0..<result.numberOfRanges).map { index in
                guard let range = Range(result.range(at: index), in: self) else { return "" }
                return String(self[range])
            }
        }
    }

    func firstMatch(_ pattern: String, options: NSRegularExpression.Options = []) -> [String]? {
        matches(pattern, options: options).first
    }

    var htmlPlainText: String {
        replacingOccurrences(of: "<br>", with: "\n")
            .replacingOccurrences(of: "<br/>", with: "\n")
            .replacingOccurrences(of: "<br />", with: "\n")
            .replacingOccurrences(of: #"<[^>]+>"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&apos;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var titleized: String {
        split(separator: "-")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }

    var displayPersonName: String {
        let parts = split(separator: ",", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        guard parts.count == 2 else { return self }
        return [parts[1], parts[0]].joined(separator: " ")
    }

    var fullSizeAppleArtworkURL: String {
        replacingOccurrences(of: #"(?i)\d+x\d+bb"#, with: "1200x1200bb", options: .regularExpression)
    }

    var durationSeconds: Int {
        let parts = split(separator: ":").compactMap { Int($0) }
        switch parts.count {
        case 3:
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        case 2:
            return parts[0] * 60 + parts[1]
        case 1:
            return parts[0]
        default:
            return 0
        }
    }

    func absoluteURLString(base: URL) -> String? {
        if hasPrefix("http") { return self }
        return URL(string: self, relativeTo: base)?.absoluteString
    }

    var normalizedLooseCatalogText: String {
        folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .localizedLowercase
            .replacingOccurrences(of: "\u{0142}", with: "l")
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    func looseCatalogContains(_ query: String) -> Bool {
        let normalizedQuery = query.normalizedLooseCatalogText
        guard !normalizedQuery.isEmpty else { return true }
        return normalizedLooseCatalogText.contains(normalizedQuery)
    }

    func authorNearLit2GoBookAnchor(id: String, slug: String) -> String? {
        guard let range = range(of: "/lit2go/\(id)/\(slug)/") else { return nil }
        let suffix = self[range.upperBound...].prefix(900)
        return String(suffix)
            .firstMatch(#"<a[^>]+href=["'][^"']*/lit2go/authors/[^"']+["'][^>]*>(.*?)</a>"#, options: [.caseInsensitive, .dotMatchesLineSeparators])?[1]
            .htmlPlainText
            .nilIfBlank
    }

    var normalizedLit2GoSearchText: String {
        folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .localizedLowercase
            .replacingOccurrences(of: "\u{0142}", with: "l")
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    func searchQuality(forLit2GoQuery normalizedQuery: String) -> Int? {
        let normalizedSelf = normalizedLit2GoSearchText
        guard !normalizedSelf.isEmpty else { return nil }
        if normalizedSelf == normalizedQuery { return 0 }
        if normalizedSelf.hasPrefix(normalizedQuery) { return 1 }
        if normalizedSelf.split(separator: " ").contains(Substring(normalizedQuery)) { return 2 }
        if normalizedSelf.contains(normalizedQuery) { return 3 }
        if normalizedQuery.count >= 4, normalizedQuery.contains(normalizedSelf) { return 4 }
        return nil
    }
}

private extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
