import Foundation
import Testing
@testable import LibriVoxCore

@Test func forgottenCatalogSearchDoesNotReplayOnConnectivityChanges() async {
    let calls = CancellationSearchCalls()
    let coordinator = CatalogSearchCoordinator(
        clients: [CancellationCatalogClient { _ in
            await calls.record()
            return [cancellationBook]
        }],
        cache: CancellationSearchCache(),
        connectivity: MutableCatalogConnectivity(isOnline: false)
    )
    _ = await coordinator.search(cancellationRequest)

    await coordinator.cancel(clearLastRequest: true)

    #expect(await coordinator.connectivityChanged(to: true) == nil)
    #expect(await coordinator.connectivityChanged(to: false) == nil)
    #expect(await coordinator.connectivityChanged(to: true) == nil)
    #expect(await calls.count == 0)
}

@Test func ordinaryCatalogCancellationPreservesReconnectReplay() async throws {
    let calls = CancellationSearchCalls()
    let coordinator = CatalogSearchCoordinator(
        clients: [CancellationCatalogClient { _ in
            await calls.record()
            return [cancellationBook]
        }],
        cache: CancellationSearchCache(),
        connectivity: MutableCatalogConnectivity(isOnline: false)
    )
    _ = await coordinator.search(cancellationRequest)
    await coordinator.cancel()

    let retry = try #require(await coordinator.connectivityChanged(to: true))
    let snapshots = await collectCancellationSnapshots(retry).value

    #expect(snapshots.last?.hits.first?.book.id == cancellationBook.id)
    #expect(await calls.count == 1)
    #expect(await coordinator.connectivityChanged(to: true) == nil)
}

@Test func canceledCatalogSearchCannotEmitAfterSuspendedConnectivityCheck() async {
    let gate = CancellationSuspensionGate()
    let coordinator = CatalogSearchCoordinator(
        clients: [CancellationCatalogClient { _ in [cancellationBook] }],
        cache: CancellationSearchCache(),
        connectivity: SuspendedCancellationConnectivity(gate: gate)
    )
    let collected = collectCancellationSnapshots(await coordinator.snapshots(for: cancellationRequest))
    await gate.waitUntilEntered()

    await coordinator.cancel(clearLastRequest: true)
    await gate.release()

    #expect(await collected.value.isEmpty)
    #expect(await coordinator.connectivityChanged(to: false) == nil)
    #expect(await coordinator.connectivityChanged(to: true) == nil)
}

@Test func canceledCatalogSearchCannotEmitAfterSuspendedCacheLookup() async {
    let gate = CancellationSuspensionGate()
    let calls = CancellationSearchCalls()
    let coordinator = CatalogSearchCoordinator(
        clients: [CancellationCatalogClient { _ in
            await calls.record()
            return [cancellationBook]
        }],
        cache: CancellationSearchCache(lookupGate: gate, cachedBook: cancellationBook),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let collected = collectCancellationSnapshots(await coordinator.snapshots(for: cancellationRequest))
    await gate.waitUntilEntered()

    await coordinator.cancel(clearLastRequest: true)
    await gate.release()

    let snapshots = await collected.value
    #expect(snapshots.count == 1)
    #expect(snapshots.allSatisfy { $0.hits.isEmpty })
    #expect(await calls.count == 0)
}

@Test func canceledCatalogSearchCannotEmitAfterSuspendedCacheWrite() async {
    let gate = CancellationSuspensionGate()
    let coordinator = CatalogSearchCoordinator(
        clients: [CancellationCatalogClient { _ in [cancellationBook] }],
        cache: CancellationSearchCache(storeGate: gate),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let collected = collectCancellationSnapshots(await coordinator.snapshots(for: cancellationRequest))
    await gate.waitUntilEntered()

    await coordinator.cancel(clearLastRequest: true)
    await gate.release()

    let snapshots = await collected.value
    #expect(snapshots.count == 2)
    #expect(snapshots.allSatisfy { $0.hits.isEmpty })
    #expect(snapshots.allSatisfy { !$0.isComplete })
}

private let cancellationBook = AudioBook(
    id: "cancel-alice", title: "Alice", author: "Lewis Carroll", source: .libriVox
)

private let cancellationRequest = CatalogSearchRequest(
    query: "alice", field: .title, languages: [.all], sources: [.libriVox]
)

private func collectCancellationSnapshots(
    _ stream: AsyncStream<CatalogSearchSnapshot>
) -> Task<[CatalogSearchSnapshot], Never> {
    Task {
        var snapshots: [CatalogSearchSnapshot] = []
        for await snapshot in stream { snapshots.append(snapshot) }
        return snapshots
    }
}

private struct CancellationCatalogClient: CatalogSourceClient {
    let source: BookSource = .libriVox
    var handler: @Sendable (CatalogSearchRequest) async throws -> [AudioBook]

    func featuredBooks(limit: Int) async throws -> [AudioBook] { [] }
    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] { try await handler(request) }
    func hydrate(_ book: AudioBook) async throws -> AudioBook { book }
}

private actor CancellationSearchCalls {
    private(set) var count = 0
    func record() { count += 1 }
}

/// Explicit suspension keeps these race regressions independent of task timing.
private actor CancellationSuspensionGate {
    private var entered = false
    private var released = false
    private var suspended: CheckedContinuation<Void, Never>?
    private var entryWaiters: [CheckedContinuation<Void, Never>] = []

    func pause() async {
        entered = true
        entryWaiters.forEach { $0.resume() }
        entryWaiters.removeAll()
        if !released {
            await withCheckedContinuation { suspended = $0 }
        }
    }

    func waitUntilEntered() async {
        if !entered {
            await withCheckedContinuation { entryWaiters.append($0) }
        }
    }

    func release() {
        released = true
        suspended?.resume()
        suspended = nil
    }
}

private struct SuspendedCancellationConnectivity: CatalogConnectivityProviding {
    let gate: CancellationSuspensionGate
    func isOnline() async -> Bool {
        await gate.pause()
        return true
    }
}

private actor CancellationSearchCache: CatalogSearchCaching {
    let lookupGate: CancellationSuspensionGate?
    let storeGate: CancellationSuspensionGate?
    let cachedBook: AudioBook?
    private var entries: [CatalogOperationCacheKey: CatalogCachedOperation] = [:]

    init(
        lookupGate: CancellationSuspensionGate? = nil,
        storeGate: CancellationSuspensionGate? = nil,
        cachedBook: AudioBook? = nil
    ) {
        self.lookupGate = lookupGate
        self.storeGate = storeGate
        self.cachedBook = cachedBook
    }

    func lookup(_ key: CatalogOperationCacheKey, at date: Date) async -> CatalogCacheLookup {
        await lookupGate?.pause()
        if let cachedBook {
            return .fresh(CatalogCachedOperation(
                key: key, books: [cachedBook], outcome: .success(count: 1), storedAt: date
            ))
        }
        return entries[key].map(CatalogCacheLookup.fresh) ?? .miss
    }

    func store(_ operation: CatalogCachedOperation) async {
        await storeGate?.pause()
        entries[operation.key] = operation
    }

    func removeAll() { entries.removeAll() }
    func entryCount() -> Int { entries.count }
}
