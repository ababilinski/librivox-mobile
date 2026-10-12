import Foundation

public actor CatalogSearchCoordinator {
    private struct OperationBooks: Sendable {
        var books: [AudioBook]
        var field: CatalogSearchField
        var provenance: CatalogResultProvenance
    }

    private struct NetworkResult: Sendable {
        var operation: CatalogSearchOperation
        var books: [AudioBook]?
        var failure: CatalogOperationFailure?
    }

    private let clients: [BookSource: any CatalogSourceClient]
    private let cache: any CatalogSearchCaching
    private let clock: any CatalogClock
    private let connectivity: any CatalogConnectivityProviding
    private var activeTask: Task<Void, Never>?
    private var lastRequest: CatalogSearchRequest?
    private var lastKnownOnline = true
    private var didRetryAfterReconnect = false
    private var cachingEnabled: Bool

    public init(
        clients: [any CatalogSourceClient] = [
            LibriVoxCatalogClient(),
            Lit2GoCatalogClient(),
            GutendexCatalogClient(),
            WolneLekturyCatalogClient(),
        ],
        cache: any CatalogSearchCaching = JSONCatalogSearchCache(),
        clock: any CatalogClock = SystemCatalogClock(),
        connectivity: any CatalogConnectivityProviding = MutableCatalogConnectivity(),
        cachingEnabled: Bool = true
    ) {
        self.clients = Dictionary(uniqueKeysWithValues: clients.map { ($0.source, $0) })
        self.cache = cache
        self.clock = clock
        self.connectivity = connectivity
        self.cachingEnabled = cachingEnabled
    }

    public func snapshots(for request: CatalogSearchRequest) -> AsyncStream<CatalogSearchSnapshot> {
        startSnapshots(for: request, recordsNewRequest: true, emitsInitialPendingSnapshot: true)
    }

    public func search(_ request: CatalogSearchRequest) async -> CatalogSearchSnapshot {
        var latest: CatalogSearchSnapshot?
        for await snapshot in snapshots(for: request) {
            latest = snapshot
        }
        if let latest { return latest }
        let now = clock.now()
        let plan = CatalogSearchPlan(request: request, availableSources: Set(clients.keys))
        return CatalogSearchSnapshot(
            normalizedQuery: plan.normalizedQuery,
            outcomes: plan.initialOutcomes,
            hits: [],
            startedAt: now,
            updatedAt: now
        )
    }

    /// Updates connectivity and returns an offline replay or the one automatic
    /// retry stream for the last plan when connectivity changes.
    public func connectivityChanged(to isOnline: Bool) async -> AsyncStream<CatalogSearchSnapshot>? {
        if let mutable = connectivity as? MutableCatalogConnectivity {
            await mutable.setOnline(isOnline)
        }
        let wasOnline = lastKnownOnline
        lastKnownOnline = isOnline
        if !isOnline {
            activeTask?.cancel()
            activeTask = nil
            guard wasOnline, let lastRequest else { return nil }
            didRetryAfterReconnect = false
            // Replay the same plan against the operation cache while offline.
            // Completed operations remain visible and uncached operations become
            // retryable failures instead of lingering as Pending. Each distinct
            // offline cycle receives one reconnect retry; this replay does not
            // consume it.
            return startSnapshots(for: lastRequest, recordsNewRequest: false, emitsInitialPendingSnapshot: false)
        }
        guard isOnline, !wasOnline, !didRetryAfterReconnect, let lastRequest else { return nil }
        didRetryAfterReconnect = true
        return startSnapshots(for: lastRequest, recordsNewRequest: false, emitsInitialPendingSnapshot: false)
    }

    /// Stops the active search. Forget the request when leaving catalog search
    /// so a later connectivity change cannot restart an abandoned query.
    public func cancel(clearLastRequest: Bool = false) {
        activeTask?.cancel()
        activeTask = nil
        if clearLastRequest {
            lastRequest = nil
            didRetryAfterReconnect = false
        }
    }

    public func clearCache() async {
        await cache.removeAll()
    }

    public func setCachingEnabled(_ enabled: Bool) async {
        guard cachingEnabled != enabled else { return }
        cachingEnabled = enabled
        if !enabled {
            await cache.removeAll()
        }
    }

    public func cacheEntryCount() async -> Int {
        await cache.entryCount()
    }

    private func startSnapshots(
        for request: CatalogSearchRequest,
        recordsNewRequest: Bool,
        emitsInitialPendingSnapshot: Bool
    ) -> AsyncStream<CatalogSearchSnapshot> {
        activeTask?.cancel()
        if recordsNewRequest {
            didRetryAfterReconnect = false
            lastRequest = request
        }

        let plan = CatalogSearchPlan(request: request, availableSources: Set(clients.keys))
        let taskAndStream = AsyncStream<CatalogSearchSnapshot>.makeStream()
        let task = Task { [weak self] in
            guard let self else {
                taskAndStream.continuation.finish()
                return
            }
            await self.execute(
                plan: plan,
                continuation: taskAndStream.continuation,
                emitsInitialPendingSnapshot: emitsInitialPendingSnapshot
            )
        }
        activeTask = task
        taskAndStream.continuation.onTermination = { _ in task.cancel() }
        return taskAndStream.stream
    }

    private func execute(
        plan: CatalogSearchPlan,
        continuation: AsyncStream<CatalogSearchSnapshot>.Continuation,
        emitsInitialPendingSnapshot: Bool
    ) async {
        defer { continuation.finish() }
        guard !Task.isCancelled else { return }
        let startedAt = clock.now()
        var outcomes = plan.initialOutcomes
        var operationBooks: [String: OperationBooks] = [:]
        let online = await connectivity.isOnline()
        guard !Task.isCancelled else { return }
        lastKnownOnline = online

        if emitsInitialPendingSnapshot {
            continuation.yield(snapshot(
                plan: plan,
                outcomes: outcomes,
                operationBooks: operationBooks,
                startedAt: startedAt
            ))
        }

        var networkOperations: [CatalogSearchOperation] = []
        for operation in plan.operations where outcomes[operation.id] == .pending {
            guard !Task.isCancelled else { return }
            let key = CatalogOperationCacheKey(
                query: plan.normalizedQuery,
                operation: operation,
                languages: plan.request.languages
            )
            let cachedLookup: CatalogCacheLookup = if cachingEnabled {
                await cache.lookup(key, at: clock.now())
            } else {
                .miss
            }
            guard !Task.isCancelled else { return }
            switch cachedLookup {
            case .fresh(let cached):
                outcomes[operation.id] = cached.outcome
                operationBooks[operation.id] = OperationBooks(
                    books: cached.books,
                    field: operation.field,
                    provenance: .freshCache
                )
            case .stale(let cached):
                operationBooks[operation.id] = OperationBooks(
                    books: cached.books,
                    field: operation.field,
                    provenance: .staleCache
                )
                if online {
                    networkOperations.append(operation)
                } else {
                    outcomes[operation.id] = cached.books.isEmpty
                        ? .authoritativeEmpty
                        : .success(count: cached.books.count)
                }
            case .miss:
                if online {
                    networkOperations.append(operation)
                } else {
                    outcomes[operation.id] = .failed(CatalogOperationFailure(
                        message: URLError(.notConnectedToInternet).localizedDescription,
                        isRetryable: true
                    ))
                }
            }
        }

        continuation.yield(snapshot(
            plan: plan,
            outcomes: outcomes,
            operationBooks: operationBooks,
            startedAt: startedAt
        ))

        await withTaskGroup(of: NetworkResult.self) { group in
            for operation in networkOperations {
                guard let client = clients[operation.source] else { continue }
                let request = CatalogSearchRequest(
                    query: plan.normalizedQuery,
                    field: operation.field,
                    languages: plan.request.languages,
                    sources: [operation.source],
                    sourcePriority: plan.request.sourcePriority
                )
                group.addTask {
                    do {
                        let books = try await client.search(request)
                        try Task.checkCancellation()
                        return NetworkResult(operation: operation, books: books, failure: nil)
                    } catch is CancellationError {
                        return NetworkResult(operation: operation, books: nil, failure: nil)
                    } catch {
                        return NetworkResult(
                            operation: operation,
                            books: nil,
                            failure: CatalogOperationFailure(
                                message: String(describing: error),
                                isRetryable: Self.isRetryable(error)
                            )
                        )
                    }
                }
            }

            for await result in group {
                guard !Task.isCancelled else { continue }
                if let books = result.books {
                    let outcome: CatalogOperationOutcome = books.isEmpty
                        ? .authoritativeEmpty
                        : .success(count: books.count)
                    outcomes[result.operation.id] = outcome
                    operationBooks[result.operation.id] = OperationBooks(
                        books: books,
                        field: result.operation.field,
                        provenance: .network
                    )
                    let now = clock.now()
                    if cachingEnabled {
                        await cache.store(CatalogCachedOperation(
                            key: CatalogOperationCacheKey(
                                query: plan.normalizedQuery,
                                operation: result.operation,
                                languages: plan.request.languages
                            ),
                            books: books,
                            outcome: outcome,
                            storedAt: now
                        ))
                    }
                } else if let failure = result.failure {
                    outcomes[result.operation.id] = .failed(failure)
                    // Keep stale books visible, but a failed revalidation remains failed.
                }
                guard !Task.isCancelled else { continue }
                continuation.yield(snapshot(
                    plan: plan,
                    outcomes: outcomes,
                    operationBooks: operationBooks,
                    startedAt: startedAt
                ))
            }
        }

        if !Task.isCancelled {
            continuation.yield(snapshot(
                plan: plan,
                outcomes: outcomes,
                operationBooks: operationBooks,
                startedAt: startedAt
            ))
        }
    }

    private func snapshot(
        plan: CatalogSearchPlan,
        outcomes: [String: CatalogOperationOutcome],
        operationBooks: [String: OperationBooks],
        startedAt: Date
    ) -> CatalogSearchSnapshot {
        let hits = Self.mergeHits(
            operationBooks,
            query: plan.normalizedQuery,
            sourcePriority: plan.request.sourcePriority
        )
        return CatalogSearchSnapshot(
            normalizedQuery: plan.normalizedQuery,
            outcomes: outcomes,
            hits: hits,
            startedAt: startedAt,
            updatedAt: clock.now()
        )
    }

    private static func mergeHits(
        _ operationBooks: [String: OperationBooks],
        query: String,
        sourcePriority: [BookSource]
    ) -> [CatalogSearchHit] {
        var grouped: [String: CatalogSearchHit] = [:]
        let priority = Dictionary(uniqueKeysWithValues: sourcePriority.enumerated().map { ($1, $0) })
        for result in operationBooks.values {
            for book in result.books {
                let title = book.title.normalizedCatalogSearchTitle
                let author = book.author.normalizedCatalogSearchText
                let workKey = title.isEmpty ? "\(book.source.rawValue):\(book.id)" : "\(title)|\(author)"
                if var hit = grouped[workKey] {
                    if !hit.variants.contains(where: { $0.id == book.id && $0.source == book.source }) {
                        hit.variants.append(book)
                    }
                    hit.matchedFields.insert(result.field)
                    hit.contributingSources.insert(book.source)
                    hit.provenance.insert(result.provenance)
                    if priority[book.source, default: .max] < priority[hit.book.source, default: .max] {
                        hit.book = book
                    }
                    grouped[workKey] = hit
                } else {
                    grouped[workKey] = CatalogSearchHit(
                        book: book,
                        matchedFields: [result.field],
                        provenance: [result.provenance]
                    )
                }
            }
        }

        let hits = Array(grouped.values)
        let rankedBooks = hits.map(\.book)
            .rankedForCatalogSearch(query: query, field: .all, keepUnmatched: true)
        let hitsByBook = Dictionary(uniqueKeysWithValues: hits.map { ("\($0.book.source.rawValue)|\($0.book.id)", $0) })
        return rankedBooks.compactMap { hitsByBook["\($0.source.rawValue)|\($0.id)"] }
    }

    private static func isRetryable(_ error: Error) -> Bool {
        guard let urlError = error as? URLError else { return true }
        switch urlError.code {
        case .badURL, .unsupportedURL, .userAuthenticationRequired, .noPermissionsToReadFile:
            return false
        default:
            return true
        }
    }
}
