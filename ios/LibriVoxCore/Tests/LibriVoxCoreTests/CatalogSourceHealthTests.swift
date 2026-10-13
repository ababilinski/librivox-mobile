import Foundation
import Testing
@testable import LibriVoxCore

@Test func catalogSourceHealthSwiftDefaultsMatchVersionedSharedContract() throws {
    let data = try Data(contentsOf: sourceHealthRepositoryRoot()
        .appendingPathComponent("catalog-contract/contract/v1/catalog-source-health-contract.json"))
    let root = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    #expect(root["contractVersion"] as? Int == CatalogSourceHealthContract.version)
    #expect(Set(root["states"] as? [String] ?? []) == Set(CatalogSourceHealthState.allCases.map(\.rawValue)))
    #expect(Set(root["failureKinds"] as? [String] ?? []) == Set(CatalogSourceHealthFailureKind.allCases.map(\.rawValue)))

    let providers = try #require(root["providers"] as? [String: [String: Any]])
    let sourceNames: [String: BookSource] = [
        "librivox": .libriVox,
        "lit2go": .lit2Go,
        "wolne_lektury": .wolneLektury,
    ]
    #expect(Set(providers.keys) == Set(sourceNames.keys))
    for (name, source) in sourceNames {
        let provider = try #require(providers[name])
        #expect(provider["probeURL"] as? String == CatalogSourceHealthContract.providerURLs[source]?.absoluteString)
        #expect(
            provider["recognizableTitleMarkers"] as? [String] ==
                CatalogSourceHealthContract.recognizableTitleMarkers[source]
        )
    }

    let probePolicy = try #require(root["probePolicy"] as? [String: Any])
    #expect(probePolicy["timeoutSeconds"] as? Int == Int(CatalogSourceHealthContract.timeoutInterval))
    #expect(probePolicy["outageContentMarkers"] as? [String] == CatalogSourceHealthContract.outageContentMarkers)
    let cachePolicy = try #require(root["cachePolicy"] as? [String: Any])
    #expect(cachePolicy["availableFreshSeconds"] as? Int == Int(CatalogSourceHealthContract.availableFreshness))
    #expect(cachePolicy["brokenFreshSeconds"] as? Int == Int(CatalogSourceHealthContract.brokenFreshness))
    #expect(cachePolicy["unknownFreshSeconds"] as? Int == Int(CatalogSourceHealthContract.unknownFreshness))
    #expect(cachePolicy["maximumEntries"] as? Int == CatalogSourceHealthContract.maximumCacheEntries)
}

@Test func catalogSourceHealthStartsUnknownThenClassifiesIndependentRootProbes() async throws {
    let recorder = SourceHealthRequestRecorder()
    let outagePage = try Data(contentsOf: sourceHealthRepositoryRoot()
        .appendingPathComponent("catalog-contract/fixtures/v1/failures/http-200-error-page.html"))
    let transport = SourceHealthFixtureTransport { request in
        await recorder.record(request)
        switch request.url?.host {
        case "librivox.org":
            return CatalogHTTPResponse(
                data: Data("<title>LibriVox | free public domain audiobooks</title>".utf8),
                statusCode: 200
            )
        case "etc.usf.edu":
            return CatalogHTTPResponse(data: outagePage, statusCode: 200)
        case "wolnelektury.pl":
            throw URLError(.cannotConnectToHost)
        default:
            throw URLError(.badURL)
        }
    }
    let probe = CatalogSourceHealthProbe(transport: transport)

    #expect(await probe.currentStatus(for: .libriVox).state == .unknown)
    var snapshots: [CatalogSourceHealthSnapshot] = []
    for await snapshot in await probe.snapshots(for: [.libriVox, .lit2Go, .wolneLektury]) {
        snapshots.append(snapshot)
    }

    let first = try #require(snapshots.first)
    #expect(!first.isComplete)
    #expect(first.statuses.values.allSatisfy { $0.state == .checking })
    let final = try #require(snapshots.last)
    #expect(final.isComplete)
    #expect(final.statuses[.libriVox]?.state == .available)
    #expect(final.statuses[.lit2Go]?.state == .broken)
    #expect(final.statuses[.wolneLektury]?.state == .broken)
    #expect(final.statuses[.wolneLektury]?.failure?.kind == .network)
    #expect(final.statuses[.lit2Go]?.statusCode == 200)
    #expect(final.statuses[.lit2Go]?.failure?.kind == .invalidContent)

    let requests = await recorder.requests
    #expect(requests.count == 3)
    #expect(requests.allSatisfy { $0.method == "GET" })
    #expect(requests.allSatisfy { $0.timeout == 45 })
}

@Test func catalogSourceHealthRejectsBrandedHTTP200OutagePage() async {
    let transport = SourceHealthFixtureTransport { _ in
        CatalogHTTPResponse(
            data: Data("<title>LibriVox</title><h1>Service unavailable</h1>".utf8),
            statusCode: 200
        )
    }
    let probe = CatalogSourceHealthProbe(transport: transport)

    let health = await probe.check(sources: [.libriVox])
    #expect(health.statuses[.libriVox]?.state == .broken)
    #expect(health.statuses[.libriVox]?.failure?.kind == .invalidContent)
    #expect(health.statuses[.libriVox]?.statusCode == 200)
}

@Test func catalogSourceHealthCacheUsesStateSpecificBoundariesAndNeverCachesOffline() async throws {
    let start = Date(timeIntervalSince1970: 1_700_000_000)
    let clock = MutableCatalogHealthClock(start)
    let recorder = SourceHealthRequestRecorder()
    let transport = SourceHealthFixtureTransport { request in
        await recorder.record(request)
        switch request.url?.host {
        case "librivox.org":
            return CatalogHTTPResponse(
                data: Data("<title>LibriVox | free public domain audiobooks</title>".utf8),
                statusCode: 200
            )
        case "etc.usf.edu":
            return CatalogHTTPResponse(data: Data("Welcome to Lit2Go".utf8), statusCode: 500)
        case "wolnelektury.pl":
            throw URLError(.notConnectedToInternet)
        default:
            throw URLError(.badURL)
        }
    }
    let probe = CatalogSourceHealthProbe(transport: transport, clock: clock)

    #expect(await probe.check(sources: [.libriVox]).statuses[.libriVox]?.state == .available)
    clock.advance(by: 5 * 60)
    let availableBoundary = await probe.check(sources: [.libriVox])
    #expect(availableBoundary.statuses[.libriVox]?.isCached == true)
    #expect(await recorder.count(for: "librivox.org") == 1)
    clock.advance(by: 0.001)
    _ = await probe.check(sources: [.libriVox])
    #expect(await recorder.count(for: "librivox.org") == 2)

    #expect(await probe.check(sources: [.lit2Go]).statuses[.lit2Go]?.state == .broken)
    clock.advance(by: 60)
    let brokenBoundary = await probe.check(sources: [.lit2Go])
    #expect(brokenBoundary.statuses[.lit2Go]?.isCached == true)
    #expect(await recorder.count(for: "etc.usf.edu") == 1)
    clock.advance(by: 0.001)
    _ = await probe.check(sources: [.lit2Go])
    #expect(await recorder.count(for: "etc.usf.edu") == 2)

    #expect(await probe.check(sources: [.wolneLektury]).statuses[.wolneLektury]?.state == .offline)
    #expect(await probe.check(sources: [.wolneLektury]).statuses[.wolneLektury]?.state == .offline)
    #expect(await recorder.count(for: "wolnelektury.pl") == 2)
    #expect(await probe.cachedHealthCount() == 2)
}

@Test func catalogSourceHealthTimeoutIsCheckedUnknownAndCachedForOneMinute() async {
    let clock = MutableCatalogHealthClock(Date(timeIntervalSince1970: 1_700_000_000))
    let recorder = SourceHealthRequestRecorder()
    let transport = SourceHealthFixtureTransport { request in
        await recorder.record(request)
        throw URLError(.timedOut)
    }
    let probe = CatalogSourceHealthProbe(transport: transport, clock: clock)

    let first = await probe.check(sources: [.libriVox])
    #expect(first.isComplete)
    #expect(first.statuses[.libriVox]?.state == .unknown)
    #expect(first.statuses[.libriVox]?.failure?.kind == .timeout)
    #expect(first.statuses[.libriVox]?.isCached == false)
    clock.advance(by: 60)
    let boundary = await probe.check(sources: [.libriVox])
    #expect(boundary.statuses[.libriVox]?.state == .unknown)
    #expect(boundary.statuses[.libriVox]?.isCached == true)
    #expect(await recorder.count(for: "librivox.org") == 1)
    clock.advance(by: 0.001)
    _ = await probe.check(sources: [.libriVox])
    #expect(await recorder.count(for: "librivox.org") == 2)
    #expect(await probe.cachedHealthCount() == 1)
}

@Test func catalogSourceHealthCacheHasDeterministicLRUBound() async {
    let clock = MutableCatalogHealthClock(Date(timeIntervalSince1970: 1_700_000_000))
    let recorder = SourceHealthRequestRecorder()
    let transport = SourceHealthFixtureTransport { request in
        await recorder.record(request)
        let body: String = switch request.url?.host {
        case "librivox.org": "<title>LibriVox | free public domain audiobooks</title>"
        case "etc.usf.edu": "<title>Welcome to Lit2Go ETC</title>"
        case "wolnelektury.pl": "<title>Wolne Lektury</title>"
        default: "<title>WolneLektury.pl</title>"
        }
        return CatalogHTTPResponse(data: Data(body.utf8), statusCode: 200)
    }
    let probe = CatalogSourceHealthProbe(
        transport: transport,
        clock: clock,
        maximumEntries: 2
    )

    _ = await probe.check(sources: [.libriVox])
    clock.advance(by: 1)
    _ = await probe.check(sources: [.lit2Go])
    clock.advance(by: 1)
    #expect(await probe.check(sources: [.libriVox]).statuses[.libriVox]?.isCached == true)
    clock.advance(by: 1)
    _ = await probe.check(sources: [.wolneLektury])

    #expect(await probe.cachedHealthCount() == 2)
    #expect(await recorder.count(for: "librivox.org") == 1)
    _ = await probe.check(sources: [.lit2Go])
    #expect(await recorder.count(for: "etc.usf.edu") == 2)
    #expect(await probe.cachedHealthCount() == 2)
}

@Test func catalogSourceHealthAndFieldOperationOutcomesDoNotRewriteEachOther() async throws {
    let healthTransport = SourceHealthFixtureTransport { request in
        switch request.url?.host {
        case "librivox.org":
            return CatalogHTTPResponse(data: Data("maintenance".utf8), statusCode: 503)
        case "etc.usf.edu":
            return CatalogHTTPResponse(data: Data("<title>Welcome to Lit2Go ETC</title>".utf8), statusCode: 200)
        default:
            throw URLError(.badURL)
        }
    }
    let healthProbe = CatalogSourceHealthProbe(transport: healthTransport)
    let health = await healthProbe.check(sources: [.libriVox, .lit2Go])

    let alice = AudioBook(
        id: "200",
        title: "Alice's Adventures in Wonderland",
        author: "Lewis Carroll",
        source: .libriVox
    )
    let cacheURL = sourceHealthTemporaryCacheURL()
    let coordinator = CatalogSearchCoordinator(
        clients: [
            SourceHealthCatalogClient(source: .libriVox) { _ in [alice] },
            SourceHealthCatalogClient(source: .lit2Go) { _ in throw URLError(.cannotParseResponse) },
        ],
        cache: JSONCatalogSearchCache(fileURL: cacheURL),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let search = await coordinator.search(CatalogSearchRequest(
        query: "alice",
        field: .title,
        languages: [.all],
        sources: [.libriVox, .lit2Go],
        sourcePriority: [.libriVox, .lit2Go]
    ))

    #expect(health.statuses[.libriVox]?.state == .broken)
    #expect(search.outcomes[CatalogSearchOperation(source: .libriVox, field: .title).id] == .success(count: 1))
    #expect(health.statuses[.lit2Go]?.state == .available)
    if case .failed = search.outcomes[CatalogSearchOperation(source: .lit2Go, field: .title).id] {
        // Expected: root health does not convert a field failure to success.
    } else {
        Issue.record("expected the independent Lit2Go title operation to remain failed")
    }
    try? FileManager.default.removeItem(at: cacheURL.deletingLastPathComponent())
}

private struct SourceHealthFixtureTransport: CatalogHTTPTransport {
    var handler: @Sendable (URLRequest) async throws -> CatalogHTTPResponse

    func response(for request: URLRequest) async throws -> CatalogHTTPResponse {
        try await handler(request)
    }
}

private struct SourceHealthCatalogClient: CatalogSourceClient {
    var source: BookSource
    var handler: @Sendable (CatalogSearchRequest) async throws -> [AudioBook]

    func featuredBooks(limit: Int) async throws -> [AudioBook] { [] }
    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] { try await handler(request) }
    func hydrate(_ book: AudioBook) async throws -> AudioBook { book }
}

private struct SourceHealthRecordedRequest: Sendable {
    var host: String
    var method: String
    var timeout: TimeInterval
}

private actor SourceHealthRequestRecorder {
    private(set) var requests: [SourceHealthRecordedRequest] = []

    func record(_ request: URLRequest) {
        requests.append(SourceHealthRecordedRequest(
            host: request.url?.host ?? "",
            method: request.httpMethod ?? "",
            timeout: request.timeoutInterval
        ))
    }

    func count(for host: String) -> Int {
        requests.count { $0.host == host }
    }
}

private final class MutableCatalogHealthClock: CatalogClock, @unchecked Sendable {
    private let lock = NSLock()
    private var value: Date

    init(_ value: Date) {
        self.value = value
    }

    func now() -> Date {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func advance(by interval: TimeInterval) {
        lock.lock()
        value = value.addingTimeInterval(interval)
        lock.unlock()
    }
}

private func sourceHealthRepositoryRoot() -> URL {
    var url = URL(fileURLWithPath: #filePath)
    for _ in 0..<5 { url.deleteLastPathComponent() }
    return url
}

private func sourceHealthTemporaryCacheURL() -> URL {
    FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxSourceHealthTests-\(UUID().uuidString)", isDirectory: true)
        .appendingPathComponent("catalog-search.json")
}
