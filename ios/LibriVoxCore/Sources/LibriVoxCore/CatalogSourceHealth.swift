import Foundation

/// The versioned stable-root health contract. Root health is intentionally
/// independent from field-level catalog search operation outcomes.
public enum CatalogSourceHealthContract {
    public static let id = "org.librivox-mobile.catalog-source-health"
    public static let version = 1
    public static let timeoutInterval: TimeInterval = 45
    public static let availableFreshness: TimeInterval = 5 * 60
    public static let brokenFreshness: TimeInterval = 60
    public static let unknownFreshness: TimeInterval = 60
    public static let maximumCacheEntries = 4

    public static let providerURLs: [BookSource: URL] = [
        .libriVox: URL(string: "https://librivox.org/")!,
        .lit2Go: URL(string: "https://etc.usf.edu/lit2go/")!,
        .gutendex: URL(string: "https://gutendex.com/")!,
        .wolneLektury: URL(string: "https://wolnelektury.pl/")!,
    ]

    public static let recognizableTitleMarkers: [BookSource: [String]] = [
        .libriVox: ["librivox"],
        .lit2Go: ["lit2go"],
        .gutendex: ["gutendex"],
        .wolneLektury: ["wolnelektury", "wolne lektury"],
    ]

    public static let outageContentMarkers: [String] = [
        "bad gateway",
        "gateway timeout",
        "internal server error",
        "service unavailable",
        "temporarily unavailable",
        "website is down",
        "origin is unreachable",
        "error 500",
        "error 502",
        "error 503",
        "error 504",
    ]
}

public enum CatalogSourceHealthState: String, Codable, CaseIterable, Sendable {
    case unknown
    case checking
    case available
    case broken
    case offline
}

public enum CatalogSourceHealthFailureKind: String, Codable, CaseIterable, Sendable {
    case offline
    case httpStatus = "http_status"
    case invalidContent = "invalid_content"
    case timeout
    case network
}

public struct CatalogSourceHealthFailure: Codable, Equatable, Sendable {
    public var kind: CatalogSourceHealthFailureKind
    public var message: String
    public var statusCode: Int?

    public init(kind: CatalogSourceHealthFailureKind, message: String, statusCode: Int? = nil) {
        self.kind = kind
        self.message = message
        self.statusCode = statusCode
    }
}

public struct CatalogSourceHealthStatus: Codable, Equatable, Identifiable, Sendable {
    public var contractVersion: Int
    public var source: BookSource
    public var state: CatalogSourceHealthState
    public var checkedAt: Date?
    public var expiresAt: Date?
    public var statusCode: Int?
    public var message: String?
    public var failure: CatalogSourceHealthFailure?
    public var isCached: Bool

    public var id: BookSource { source }
    public var isTerminal: Bool {
        switch state {
        case .available, .broken, .offline: true
        case .unknown: checkedAt != nil
        case .checking: false
        }
    }

    public init(
        contractVersion: Int = CatalogSourceHealthContract.version,
        source: BookSource,
        state: CatalogSourceHealthState = .unknown,
        checkedAt: Date? = nil,
        expiresAt: Date? = nil,
        statusCode: Int? = nil,
        message: String? = nil,
        failure: CatalogSourceHealthFailure? = nil,
        isCached: Bool = false
    ) {
        self.contractVersion = contractVersion
        self.source = source
        self.state = state
        self.checkedAt = checkedAt
        self.expiresAt = expiresAt
        self.statusCode = statusCode
        self.message = message
        self.failure = failure
        self.isCached = isCached
    }
}

public struct CatalogSourceHealthSnapshot: Codable, Equatable, Sendable {
    public var contractVersion: Int
    public var statuses: [BookSource: CatalogSourceHealthStatus]
    public var startedAt: Date
    public var updatedAt: Date

    public var isComplete: Bool { statuses.values.allSatisfy(\.isTerminal) }

    public init(
        contractVersion: Int = CatalogSourceHealthContract.version,
        statuses: [BookSource: CatalogSourceHealthStatus],
        startedAt: Date,
        updatedAt: Date
    ) {
        self.contractVersion = contractVersion
        self.statuses = statuses
        self.startedAt = startedAt
        self.updatedAt = updatedAt
    }
}

/// Probes public catalog providers at stable root URLs. The actor owns only a
/// short-lived, bounded health cache; it does not read or mutate search caches,
/// plans, operation outcomes, or result snapshots.
public actor CatalogSourceHealthProbe {
    private struct CacheEntry: Sendable {
        var status: CatalogSourceHealthStatus
        var lastAccessedAt: Date
    }

    private struct ProbeResult: Sendable {
        var source: BookSource
        var state: CatalogSourceHealthState
        var statusCode: Int?
        var message: String?
        var failureKind: CatalogSourceHealthFailureKind?
    }

    private let transport: any CatalogHTTPTransport
    private let clock: any CatalogClock
    private let providerURLs: [BookSource: URL]
    private let recognizableTitleMarkers: [BookSource: [String]]
    private let outageContentMarkers: [String]
    private let timeoutInterval: TimeInterval
    private let availableFreshness: TimeInterval
    private let brokenFreshness: TimeInterval
    private let unknownFreshness: TimeInterval
    private let maximumEntries: Int
    private var cache: [BookSource: CacheEntry] = [:]

    public init(
        transport: any CatalogHTTPTransport = URLSessionCatalogHTTPTransport(),
        clock: any CatalogClock = SystemCatalogClock(),
        providerURLs: [BookSource: URL] = CatalogSourceHealthContract.providerURLs,
        recognizableTitleMarkers: [BookSource: [String]] = CatalogSourceHealthContract.recognizableTitleMarkers,
        outageContentMarkers: [String] = CatalogSourceHealthContract.outageContentMarkers,
        timeoutInterval: TimeInterval = CatalogSourceHealthContract.timeoutInterval,
        availableFreshness: TimeInterval = CatalogSourceHealthContract.availableFreshness,
        brokenFreshness: TimeInterval = CatalogSourceHealthContract.brokenFreshness,
        unknownFreshness: TimeInterval = CatalogSourceHealthContract.unknownFreshness,
        maximumEntries: Int = CatalogSourceHealthContract.maximumCacheEntries
    ) {
        self.transport = transport
        self.clock = clock
        self.providerURLs = providerURLs
        self.recognizableTitleMarkers = recognizableTitleMarkers
        self.outageContentMarkers = outageContentMarkers
        self.timeoutInterval = max(0.1, timeoutInterval)
        self.availableFreshness = max(0, availableFreshness)
        self.brokenFreshness = max(0, brokenFreshness)
        self.unknownFreshness = max(0, unknownFreshness)
        self.maximumEntries = max(1, maximumEntries)
    }

    public func snapshots(
        for sources: [BookSource] = BookSource.defaultPublicCatalogPriority,
        forceRefresh: Bool = false
    ) -> AsyncStream<CatalogSourceHealthSnapshot> {
        let pair = AsyncStream<CatalogSourceHealthSnapshot>.makeStream()
        let worker = Task { [weak self] in
            guard let self else {
                pair.continuation.finish()
                return
            }
            await self.run(
                sources: sources,
                forceRefresh: forceRefresh,
                continuation: pair.continuation
            )
        }
        pair.continuation.onTermination = { _ in worker.cancel() }
        return pair.stream
    }

    public func check(
        sources: [BookSource] = BookSource.defaultPublicCatalogPriority,
        forceRefresh: Bool = false
    ) async -> CatalogSourceHealthSnapshot {
        var latest: CatalogSourceHealthSnapshot?
        for await snapshot in snapshots(for: sources, forceRefresh: forceRefresh) {
            latest = snapshot
        }
        if let latest { return latest }
        let now = clock.now()
        return CatalogSourceHealthSnapshot(statuses: [:], startedAt: now, updatedAt: now)
    }

    public func removeAllCachedHealth() {
        cache.removeAll()
    }

    public func cachedHealthCount() -> Int {
        cache.count
    }

    /// Returns fresh cached health when it exists; otherwise the source is
    /// unchecked. `checking` is reserved for streams with an active probe.
    public func currentStatus(for source: BookSource) -> CatalogSourceHealthStatus {
        guard source.isPublicAudiobookSource else {
            return CatalogSourceHealthStatus(source: source)
        }
        return freshCachedStatus(for: source, at: clock.now())
            ?? CatalogSourceHealthStatus(source: source)
    }

    private func run(
        sources: [BookSource],
        forceRefresh: Bool,
        continuation: AsyncStream<CatalogSourceHealthSnapshot>.Continuation
    ) async {
        let orderedSources = normalizedPublicSources(sources)
        let startedAt = clock.now()
        var statuses: [BookSource: CatalogSourceHealthStatus] = [:]
        var pending: [(source: BookSource, url: URL)] = []

        for source in orderedSources {
            let now = clock.now()
            if !forceRefresh, let cached = freshCachedStatus(for: source, at: now) {
                statuses[source] = cached
            } else if let url = providerURLs[source] {
                statuses[source] = CatalogSourceHealthStatus(source: source, state: .checking)
                pending.append((source, url))
            } else {
                let status = terminalStatus(
                    source: source,
                    result: ProbeResult(
                        source: source,
                        state: .broken,
                        message: "No stable root URL is configured.",
                        failureKind: .network
                    ),
                    checkedAt: now
                )
                statuses[source] = status
                storeIfCacheable(status, at: now)
            }
        }

        let transport = self.transport
        let timeoutInterval = self.timeoutInterval
        let recognizableTitleMarkers = self.recognizableTitleMarkers
        let outageContentMarkers = self.outageContentMarkers
        await withTaskGroup(of: ProbeResult?.self) { group in
            for item in pending {
                group.addTask {
                    await Self.probe(
                        source: item.source,
                        url: item.url,
                        recognizableTitleMarkers: recognizableTitleMarkers[item.source] ?? [],
                        outageContentMarkers: outageContentMarkers,
                        transport: transport,
                        timeoutInterval: timeoutInterval
                    )
                }
            }

            // Uncached sources become checking only after their probe tasks are
            // active. Outside this stream their default state remains unknown.
            continuation.yield(CatalogSourceHealthSnapshot(
                statuses: statuses,
                startedAt: startedAt,
                updatedAt: clock.now()
            ))

            for await result in group {
                guard !Task.isCancelled, let result else { continue }
                let checkedAt = clock.now()
                let status = terminalStatus(
                    source: result.source,
                    result: result,
                    checkedAt: checkedAt
                )
                statuses[result.source] = status
                storeIfCacheable(status, at: checkedAt)
                continuation.yield(CatalogSourceHealthSnapshot(
                    statuses: statuses,
                    startedAt: startedAt,
                    updatedAt: clock.now()
                ))
            }
        }

        continuation.finish()
    }

    private func normalizedPublicSources(_ sources: [BookSource]) -> [BookSource] {
        var seen: Set<BookSource> = []
        return sources.filter { source in
            source.isPublicAudiobookSource && seen.insert(source).inserted
        }
    }

    private func freshCachedStatus(
        for source: BookSource,
        at date: Date
    ) -> CatalogSourceHealthStatus? {
        guard var entry = cache[source],
              let expiresAt = entry.status.expiresAt,
              expiresAt >= date
        else {
            cache.removeValue(forKey: source)
            return nil
        }
        entry.lastAccessedAt = date
        cache[source] = entry
        var status = entry.status
        status.isCached = true
        return status
    }

    private func terminalStatus(
        source: BookSource,
        result: ProbeResult,
        checkedAt: Date
    ) -> CatalogSourceHealthStatus {
        let freshness: TimeInterval? = switch result.state {
        case .available: availableFreshness
        case .broken: brokenFreshness
        case .unknown: unknownFreshness
        case .checking, .offline: nil
        }
        let failure = result.failureKind.map { kind in
            CatalogSourceHealthFailure(
                kind: kind,
                message: result.message ?? "Source root health check failed.",
                statusCode: result.statusCode
            )
        }
        return CatalogSourceHealthStatus(
            source: source,
            state: result.state,
            checkedAt: checkedAt,
            expiresAt: freshness.map { checkedAt.addingTimeInterval($0) },
            statusCode: result.statusCode,
            message: result.message,
            failure: failure
        )
    }

    private func storeIfCacheable(_ status: CatalogSourceHealthStatus, at date: Date) {
        guard status.state == .available ||
                status.state == .broken ||
                (status.state == .unknown && status.checkedAt != nil)
        else { return }
        cache[status.source] = CacheEntry(status: status, lastAccessedAt: date)
        pruneCache()
    }

    private func pruneCache() {
        guard cache.count > maximumEntries else { return }
        let retained = cache.sorted { lhs, rhs in
            if lhs.value.lastAccessedAt != rhs.value.lastAccessedAt {
                return lhs.value.lastAccessedAt > rhs.value.lastAccessedAt
            }
            let lhsCheckedAt = lhs.value.status.checkedAt ?? .distantPast
            let rhsCheckedAt = rhs.value.status.checkedAt ?? .distantPast
            if lhsCheckedAt != rhsCheckedAt { return lhsCheckedAt > rhsCheckedAt }
            return lhs.key.rawValue < rhs.key.rawValue
        }.prefix(maximumEntries)
        cache = Dictionary(uniqueKeysWithValues: retained.map { ($0.key, $0.value) })
    }

    private static func probe(
        source: BookSource,
        url: URL,
        recognizableTitleMarkers: [String],
        outageContentMarkers: [String],
        transport: any CatalogHTTPTransport,
        timeoutInterval: TimeInterval
    ) async -> ProbeResult? {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = timeoutInterval
        request.setValue("text/html, application/json;q=0.9, */*;q=0.8", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue("LibriVoxMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")

        do {
            let response = try await transport.response(for: request)
            try Task.checkCancellation()
            if (200..<300).contains(response.statusCode) {
                let responseText = String(decoding: response.data, as: UTF8.self)
                    .lowercased(with: Locale(identifier: "en_US_POSIX"))
                let hasOutageMarker = outageContentMarkers.contains { marker in
                    responseText.contains(marker.lowercased(with: Locale(identifier: "en_US_POSIX")))
                }
                let title = documentTitle(in: responseText)
                let isRecognizable = title.map { title in
                    recognizableTitleMarkers.contains { marker in
                        title.contains(marker.lowercased(with: Locale(identifier: "en_US_POSIX")))
                    }
                } ?? false
                guard !hasOutageMarker, isRecognizable else {
                    return ProbeResult(
                        source: source,
                        state: .broken,
                        statusCode: response.statusCode,
                        message: "Root page returned unexpected content.",
                        failureKind: .invalidContent
                    )
                }
                return ProbeResult(
                    source: source,
                    state: .available,
                    statusCode: response.statusCode
                )
            }
            return ProbeResult(
                source: source,
                state: .broken,
                statusCode: response.statusCode,
                message: "Root page returned HTTP \(response.statusCode).",
                failureKind: .httpStatus
            )
        } catch is CancellationError {
            return nil
        } catch let error as URLError where error.code == .cancelled {
            return nil
        } catch let error as URLError where error.code == .timedOut {
            return ProbeResult(
                source: source,
                state: .unknown,
                message: "Root page is responding slowly.",
                failureKind: .timeout
            )
        } catch {
            let offline = isOffline(error)
            return ProbeResult(
                source: source,
                state: offline ? .offline : .broken,
                message: error.localizedDescription,
                failureKind: offline ? .offline : .network
            )
        }
    }

    private static func documentTitle(in lowercaseHTML: String) -> String? {
        guard let open = lowercaseHTML.range(of: "<title", options: .caseInsensitive),
              let openingEnd = lowercaseHTML.range(
                of: ">",
                range: open.lowerBound..<lowercaseHTML.endIndex
              ),
              let close = lowercaseHTML.range(
                of: "</title>",
                options: .caseInsensitive,
                range: openingEnd.upperBound..<lowercaseHTML.endIndex
              )
        else { return nil }
        let title = lowercaseHTML[openingEnd.upperBound..<close.lowerBound]
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return title.isEmpty ? nil : title
    }

    private static func isOffline(_ error: Error) -> Bool {
        guard let urlError = error as? URLError else { return false }
        return switch urlError.code {
        case .notConnectedToInternet,
             .networkConnectionLost,
             .dataNotAllowed,
             .internationalRoamingOff,
             .callIsActive:
            true
        default:
            false
        }
    }
}
