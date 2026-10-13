import Foundation

public struct CatalogSearchOperation: Hashable, Identifiable, Sendable {
    public var source: BookSource
    public var field: CatalogSearchField

    public var id: String {
        "\(source.rawValue)-\(field.rawValue)"
    }

    public init(source: BookSource, field: CatalogSearchField) {
        self.source = source
        self.field = field
    }
}

public struct CatalogSearchProgress: Sendable {
    public var operation: CatalogSearchOperation
    public var books: [AudioBook]
    public var didSucceed: Bool

    public init(operation: CatalogSearchOperation, books: [AudioBook], didSucceed: Bool = true) {
        self.operation = operation
        self.books = books
        self.didSucceed = didSucceed
    }
}

public struct AggregateCatalogClient: CatalogSourceClient {
    public let source: BookSource = .libriVox
    private let clients: [BookSource: any CatalogSourceClient]

    public init(clients: [any CatalogSourceClient] = [
        LibriVoxCatalogClient(),
        Lit2GoCatalogClient(),
        WolneLekturyCatalogClient(),
    ]) {
        self.clients = Dictionary(uniqueKeysWithValues: clients
            .filter { $0.source.isAvailableCatalogSource }
            .map { ($0.source, $0) })
    }

    public func featuredBooks(limit: Int) async throws -> [AudioBook] {
        try await featuredBooks(limit: limit, sourcePriority: BookSource.defaultPublicCatalogPriority)
    }

    public func featuredBooks(limit: Int, sourcePriority: [BookSource]) async throws -> [AudioBook] {
        let safeLimit = max(1, limit)
        let books = try await fanOut(sourceOrder: sourcePriority) { client, _ in
            try await client.featuredBooks(limit: safeLimit)
        }
        return Array(books.prefix(safeLimit))
    }

    public func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        var booksBySource: [BookSource: [AudioBook]] = [:]
        for await progress in searchProgress(request) {
            booksBySource[progress.operation.source, default: []].append(contentsOf: progress.books)
        }
        return mergedSearchResults(booksBySource, for: request)
    }

    public func searchOperations(for request: CatalogSearchRequest) -> [CatalogSearchOperation] {
        normalizedSourceOrder(request.orderedSources).flatMap { source in
            if source == .libriVox, request.field == .all {
                return [CatalogSearchField.title, .author, .reader, .chapter, .genre].map {
                    CatalogSearchOperation(source: source, field: $0)
                }
            }
            return [CatalogSearchOperation(source: source, field: request.field)]
        }
    }

    public func searchProgress(_ request: CatalogSearchRequest) -> AsyncStream<CatalogSearchProgress> {
        AsyncStream { continuation in
            let task = Task {
                await withTaskGroup(of: CatalogSearchProgress?.self) { group in
                    for operation in searchOperations(for: request) {
                        group.addTask {
                            guard !Task.isCancelled else { return nil }
                            let sourceRequest = CatalogSearchRequest(
                                query: request.query,
                                field: operation.field,
                                languages: request.languages,
                                sources: [operation.source],
                                sourcePriority: request.sourcePriority
                            )
                            guard let client = clients[operation.source] else {
                                return CatalogSearchProgress(
                                    operation: operation,
                                    books: [],
                                    didSucceed: false
                                )
                            }
                            do {
                                let books = try await client.search(sourceRequest)
                                guard !Task.isCancelled else { return nil }
                                return CatalogSearchProgress(operation: operation, books: books)
                            } catch is CancellationError {
                                return nil
                            } catch {
                                return CatalogSearchProgress(
                                    operation: operation,
                                    books: [],
                                    didSucceed: false
                                )
                            }
                        }
                    }

                    for await progress in group {
                        guard let progress, !Task.isCancelled else { continue }
                        continuation.yield(progress)
                    }
                }
                continuation.finish()
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    public func hydrate(_ book: AudioBook) async throws -> AudioBook {
        try Task.checkCancellation()
        guard let client = clients[book.source] else { return book }
        return try await client.hydrate(book).preservingUserState(from: book)
    }

    private func fanOut(
        sourceOrder: [BookSource],
        call: @escaping @Sendable (any CatalogSourceClient, BookSource) async throws -> [AudioBook]
    ) async throws -> [AudioBook] {
        let normalizedOrder = normalizedSourceOrder(sourceOrder)
        return await withTaskGroup(of: (BookSource, [AudioBook]).self, returning: [AudioBook].self) { group in
            for source in normalizedOrder {
                group.addTask {
                    (source, await sourceCall(source, call: call))
                }
            }

            var booksBySource: [BookSource: [AudioBook]] = [:]
            for await (source, books) in group {
                booksBySource[source] = books
            }

            return CatalogBookMerger.merge(
                normalizedOrder.map { booksBySource[$0] ?? [] }
            )
        }
    }

    private func normalizedSourceOrder(_ sourceOrder: [BookSource]) -> [BookSource] {
        var seen: Set<BookSource> = []
        return sourceOrder.filter { source in
            source.isAvailableCatalogSource &&
                clients[source] != nil &&
                seen.insert(source).inserted
        }
    }

    private func mergedSearchResults(
        _ booksBySource: [BookSource: [AudioBook]],
        for request: CatalogSearchRequest
    ) -> [AudioBook] {
        CatalogBookMerger.merge(
            normalizedSourceOrder(request.orderedSources).map { booksBySource[$0] ?? [] }
        )
        .rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
    }

    private func sourceCall(
        _ source: BookSource,
        call: @escaping @Sendable (any CatalogSourceClient, BookSource) async throws -> [AudioBook]
    ) async -> [AudioBook] {
        guard let client = clients[source] else { return [] }
        do {
            return try await call(client, source)
        } catch is CancellationError {
            return []
        } catch {
            return []
        }
    }
}

public extension AudioBook {
    func preservingUserState(from existing: AudioBook) -> AudioBook {
        var copy = self
        copy.libraryStatus = existing.libraryStatus
        copy.isFavorite = existing.isFavorite
        copy.addedAt = existing.addedAt
        return copy
    }
}
