import Foundation

public enum CatalogContract {
    public static let version = 1
}

public struct CatalogSourceCapabilities: Codable, Equatable, Sendable {
    public var source: BookSource
    public var supportedFields: Set<CatalogSearchField>
    public var supportedLanguages: Set<BookLanguagePreference>

    public init(
        source: BookSource,
        supportedFields: Set<CatalogSearchField>,
        supportedLanguages: Set<BookLanguagePreference>
    ) {
        self.source = source
        self.supportedFields = supportedFields
        self.supportedLanguages = supportedLanguages
    }

    public func supports(languages: Set<BookLanguagePreference>) -> Bool {
        languages.contains(.all) || !supportedLanguages.isDisjoint(with: languages)
    }

    public static func capabilities(for source: BookSource) -> CatalogSourceCapabilities? {
        switch source {
        case .libriVox:
            // The public LibriVox search surface is not a Polish catalog. Treating a
            // Polish-only plan as an empty result previously poisoned the cache.
            return CatalogSourceCapabilities(
                source: source,
                supportedFields: [.title, .author, .reader, .chapter, .genre],
                supportedLanguages: [.english, .french, .german, .italian, .spanish]
            )
        case .lit2Go:
            return CatalogSourceCapabilities(
                source: source,
                supportedFields: [.title, .author],
                supportedLanguages: [.english]
            )
        case .wolneLektury:
            return CatalogSourceCapabilities(
                source: source,
                supportedFields: [.title, .author, .genre],
                supportedLanguages: [.polish]
            )
        case .gutendex, .localAsset, .customLocal:
            return nil
        }
    }
}

public enum CatalogOperationSkipReason: String, Codable, Equatable, Sendable {
    case incompatibleLanguage
    case unsupportedField
    case unavailableSource
}

public struct CatalogOperationFailure: Codable, Equatable, Sendable {
    public var message: String
    public var isRetryable: Bool
    public var attempt: Int
    public var retryAfter: Date?

    public init(message: String, isRetryable: Bool = true, attempt: Int = 1, retryAfter: Date? = nil) {
        self.message = message
        self.isRetryable = isRetryable
        self.attempt = attempt
        self.retryAfter = retryAfter
    }
}

public enum CatalogOperationOutcome: Codable, Equatable, Sendable {
    case pending
    case success(count: Int)
    case authoritativeEmpty
    case skipped(CatalogOperationSkipReason)
    case failed(CatalogOperationFailure)

    private enum Kind: String, Codable { case pending, success, authoritativeEmpty, skipped, failed }
    private enum CodingKeys: String, CodingKey { case kind, count, skipReason, failure }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        switch try values.decode(Kind.self, forKey: .kind) {
        case .pending: self = .pending
        case .success: self = .success(count: try values.decode(Int.self, forKey: .count))
        case .authoritativeEmpty: self = .authoritativeEmpty
        case .skipped: self = .skipped(try values.decode(CatalogOperationSkipReason.self, forKey: .skipReason))
        case .failed: self = .failed(try values.decode(CatalogOperationFailure.self, forKey: .failure))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var values = encoder.container(keyedBy: CodingKeys.self)
        switch self {
        case .pending:
            try values.encode(Kind.pending, forKey: .kind)
        case .success(let count):
            try values.encode(Kind.success, forKey: .kind)
            try values.encode(count, forKey: .count)
        case .authoritativeEmpty:
            try values.encode(Kind.authoritativeEmpty, forKey: .kind)
        case .skipped(let reason):
            try values.encode(Kind.skipped, forKey: .kind)
            try values.encode(reason, forKey: .skipReason)
        case .failed(let failure):
            try values.encode(Kind.failed, forKey: .kind)
            try values.encode(failure, forKey: .failure)
        }
    }

    public var isTerminal: Bool {
        if case .pending = self { return false }
        return true
    }
}

public struct CatalogSearchPlan: Equatable, Sendable {
    public var normalizedQuery: String
    public var request: CatalogSearchRequest
    public var operations: [CatalogSearchOperation]
    public var initialOutcomes: [String: CatalogOperationOutcome]

    public init(request: CatalogSearchRequest, availableSources: Set<BookSource>? = nil) {
        let normalizedQuery = request.query.normalizedCatalogSearchText
        let normalizedRequest = CatalogSearchRequest(
            query: normalizedQuery,
            field: request.field,
            languages: request.languages.isEmpty ? [.all] : request.languages,
            sources: request.sources,
            sourcePriority: request.sourcePriority
        )
        self.normalizedQuery = normalizedQuery
        self.request = normalizedRequest

        guard !normalizedQuery.isEmpty else {
            self.operations = []
            self.initialOutcomes = [:]
            return
        }

        var operations: [CatalogSearchOperation] = []
        var outcomes: [String: CatalogOperationOutcome] = [:]
        for source in normalizedRequest.orderedSources {
            let requestedFields: [CatalogSearchField]
            if normalizedRequest.field == .all {
                requestedFields = [.title, .author, .reader, .chapter, .genre]
            } else {
                requestedFields = [normalizedRequest.field]
            }

            guard availableSources?.contains(source) != false,
                  let capabilities = CatalogSourceCapabilities.capabilities(for: source)
            else {
                for field in requestedFields {
                    let operation = CatalogSearchOperation(source: source, field: field)
                    operations.append(operation)
                    outcomes[operation.id] = .skipped(.unavailableSource)
                }
                continue
            }

            for field in requestedFields {
                let operation = CatalogSearchOperation(source: source, field: field)
                operations.append(operation)
                if !capabilities.supportedFields.contains(field) {
                    outcomes[operation.id] = .skipped(.unsupportedField)
                } else if !capabilities.supports(languages: normalizedRequest.languages) {
                    outcomes[operation.id] = .skipped(.incompatibleLanguage)
                } else {
                    outcomes[operation.id] = .pending
                }
            }
        }
        self.operations = operations
        self.initialOutcomes = outcomes
    }
}

public enum CatalogResultProvenance: String, Codable, Hashable, Sendable {
    case network
    case freshCache
    case staleCache
    case bundledFallback
}

public struct CatalogSearchHit: Codable, Hashable, Identifiable, Sendable {
    public var book: AudioBook
    public var variants: [AudioBook]
    public var matchedFields: Set<CatalogSearchField>
    public var contributingSources: Set<BookSource>
    public var provenance: Set<CatalogResultProvenance>

    public var id: String {
        let work = book.title.normalizedCatalogSearchTitle
        let author = book.author.normalizedCatalogSearchText
        return work.isEmpty ? "\(book.source.rawValue):\(book.id)" : "\(work)|\(author)"
    }

    public init(
        book: AudioBook,
        variants: [AudioBook]? = nil,
        matchedFields: Set<CatalogSearchField>,
        contributingSources: Set<BookSource>? = nil,
        provenance: Set<CatalogResultProvenance>
    ) {
        self.book = book
        self.variants = variants ?? [book]
        self.matchedFields = matchedFields
        self.contributingSources = contributingSources ?? [book.source]
        self.provenance = provenance
    }
}

public struct CatalogPagination: Codable, Equatable, Sendable {
    public var returnedCount: Int
    public var hasMore: Bool

    public init(returnedCount: Int = 0, hasMore: Bool = false) {
        self.returnedCount = returnedCount
        self.hasMore = hasMore
    }
}

public struct CatalogSearchSnapshot: Codable, Equatable, Sendable {
    public var contractVersion: Int
    public var normalizedQuery: String
    public var outcomes: [String: CatalogOperationOutcome]
    public var hits: [CatalogSearchHit]
    public var startedAt: Date
    public var updatedAt: Date
    public var pagination: CatalogPagination

    public init(
        contractVersion: Int = CatalogContract.version,
        normalizedQuery: String,
        outcomes: [String: CatalogOperationOutcome],
        hits: [CatalogSearchHit],
        startedAt: Date,
        updatedAt: Date,
        pagination: CatalogPagination? = nil
    ) {
        self.contractVersion = contractVersion
        self.normalizedQuery = normalizedQuery
        self.outcomes = outcomes
        self.hits = hits
        self.startedAt = startedAt
        self.updatedAt = updatedAt
        self.pagination = pagination ?? CatalogPagination(returnedCount: hits.count)
    }

    public var completedOperationCount: Int { outcomes.values.filter(\.isTerminal).count }
    public var operationCount: Int { outcomes.count }
    public var isComplete: Bool {
        outcomes.values.allSatisfy { outcome in
            switch outcome {
            case .success, .authoritativeEmpty, .skipped:
                true
            case .pending, .failed:
                false
            }
        }
    }

    public var sourceCounts: [BookSource: Int] {
        var counts: [BookSource: Int] = [:]
        for hit in hits {
            for source in hit.contributingSources { counts[source, default: 0] += 1 }
        }
        return counts
    }

    public var fieldCounts: [CatalogSearchField: Int] {
        var counts: [CatalogSearchField: Int] = [:]
        for hit in hits {
            for field in hit.matchedFields { counts[field, default: 0] += 1 }
        }
        return counts
    }
}

public protocol CatalogClock: Sendable {
    func now() -> Date
}

public struct SystemCatalogClock: CatalogClock {
    public init() {}
    public func now() -> Date { .now }
}

public protocol CatalogConnectivityProviding: Sendable {
    func isOnline() async -> Bool
}

public actor MutableCatalogConnectivity: CatalogConnectivityProviding {
    private var online: Bool

    public init(isOnline: Bool = true) { online = isOnline }
    public func isOnline() -> Bool { online }
    public func setOnline(_ value: Bool) { online = value }
}

public struct CatalogOperationCacheKey: Codable, Hashable, Sendable {
    public var contractVersion: Int
    public var normalizedQuery: String
    public var source: BookSource
    public var field: CatalogSearchField
    public var languages: [BookLanguagePreference]

    public init(query: String, operation: CatalogSearchOperation, languages: Set<BookLanguagePreference>) {
        contractVersion = CatalogContract.version
        normalizedQuery = query.normalizedCatalogSearchText
        source = operation.source
        field = operation.field
        self.languages = languages.sorted { $0.rawValue < $1.rawValue }
    }

    public var storageKey: String {
        [
            String(contractVersion), normalizedQuery, source.rawValue, field.rawValue,
            languages.map(\.rawValue).joined(separator: ","),
        ].joined(separator: "|")
    }
}

public struct CatalogCachedOperation: Codable, Equatable, Sendable {
    public var key: CatalogOperationCacheKey
    public var books: [AudioBook]
    public var outcome: CatalogOperationOutcome
    public var storedAt: Date
    public var lastAccessedAt: Date

    public init(
        key: CatalogOperationCacheKey,
        books: [AudioBook],
        outcome: CatalogOperationOutcome,
        storedAt: Date,
        lastAccessedAt: Date? = nil
    ) {
        self.key = key
        self.books = books
        self.outcome = outcome
        self.storedAt = storedAt
        self.lastAccessedAt = lastAccessedAt ?? storedAt
    }
}

public enum CatalogCacheLookup: Equatable, Sendable {
    case miss
    case fresh(CatalogCachedOperation)
    case stale(CatalogCachedOperation)
}

public protocol CatalogSearchCaching: Sendable {
    func lookup(_ key: CatalogOperationCacheKey, at date: Date) async -> CatalogCacheLookup
    func store(_ operation: CatalogCachedOperation) async
    func removeAll() async
    func entryCount() async -> Int
}

public actor JSONCatalogSearchCache: CatalogSearchCaching {
    public static let positiveFreshness: TimeInterval = 30 * 60
    public static let emptyFreshness: TimeInterval = 5 * 60
    public static let staleLifetime: TimeInterval = 7 * 24 * 60 * 60

    private struct Envelope: Codable, Sendable {
        var schemaVersion: Int
        var contractVersion: Int
        var entries: [String: CatalogCachedOperation]
    }

    private let fileURL: URL
    private let maximumEntries: Int
    private var entries: [String: CatalogCachedOperation]?

    public init(
        fileURL: URL = ApplicationSupportStoreDirectory.fileURL(named: "catalog-search-v3.json"),
        maximumEntries: Int = 100
    ) {
        self.fileURL = fileURL
        self.maximumEntries = max(1, maximumEntries)
        // v2 stored merged, potentially partial snapshots without coverage metadata.
        UserDefaults.standard.removeObject(forKey: "searchCache.v2")
        UserDefaults.standard.removeObject(forKey: "searchCache")
    }

    public func lookup(_ key: CatalogOperationCacheKey, at date: Date) -> CatalogCacheLookup {
        loadIfNeeded()
        guard var entry = entries?[key.storageKey], entry.key.contractVersion == CatalogContract.version else {
            return .miss
        }
        let age = max(0, date.timeIntervalSince(entry.storedAt))
        guard age <= Self.staleLifetime else {
            entries?.removeValue(forKey: key.storageKey)
            persist()
            return .miss
        }
        entry.lastAccessedAt = date
        entries?[key.storageKey] = entry
        persist()
        let freshness: TimeInterval
        if case .authoritativeEmpty = entry.outcome {
            freshness = Self.emptyFreshness
        } else {
            freshness = Self.positiveFreshness
        }
        return age <= freshness ? .fresh(entry) : .stale(entry)
    }

    public func store(_ operation: CatalogCachedOperation) {
        guard operation.key.contractVersion == CatalogContract.version else { return }
        switch operation.outcome {
        case .success, .authoritativeEmpty:
            break
        case .pending, .skipped, .failed:
            return
        }
        loadIfNeeded()
        entries?[operation.key.storageKey] = operation
        prune()
        persist()
    }

    public func removeAll() {
        entries = [:]
        try? FileManager.default.removeItem(at: fileURL)
    }

    public func entryCount() -> Int {
        loadIfNeeded()
        return entries?.count ?? 0
    }

    private func loadIfNeeded() {
        guard entries == nil else { return }
        guard let data = try? Data(contentsOf: fileURL),
              let envelope = try? JSONDecoder().decode(Envelope.self, from: data),
              envelope.schemaVersion == 1,
              envelope.contractVersion == CatalogContract.version
        else {
            entries = [:]
            return
        }
        entries = envelope.entries.filter { $0.value.key.contractVersion == CatalogContract.version }
        prune()
    }

    private func prune() {
        guard let current = entries, current.count > maximumEntries else { return }
        let retained = current.sorted { lhs, rhs in
            if lhs.value.lastAccessedAt != rhs.value.lastAccessedAt {
                return lhs.value.lastAccessedAt > rhs.value.lastAccessedAt
            }
            if lhs.value.storedAt != rhs.value.storedAt {
                return lhs.value.storedAt > rhs.value.storedAt
            }
            return lhs.key < rhs.key
        }.prefix(maximumEntries)
        entries = Dictionary(uniqueKeysWithValues: retained.map { ($0.key, $0.value) })
    }

    private func persist() {
        guard let entries else { return }
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let envelope = Envelope(schemaVersion: 1, contractVersion: CatalogContract.version, entries: entries)
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.sortedKeys]
            try encoder.encode(envelope).write(to: fileURL, options: [.atomic])
        } catch {
            // Cache persistence must never make search unavailable.
        }
    }
}
