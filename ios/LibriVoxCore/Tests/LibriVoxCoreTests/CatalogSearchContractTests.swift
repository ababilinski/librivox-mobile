import Foundation
import Testing
@testable import LibriVoxCore

@Test func catalogPlanNormalizesWhitespaceCaseAndDiacritics() {
    let plan = CatalogSearchPlan(request: CatalogSearchRequest(
        query: "  Bolesław   PRUS  ",
        field: .author,
        languages: [.all],
        sources: [.wolneLektury]
    ))

    #expect(plan.normalizedQuery == "boleslaw prus")
    #expect(plan.request.query == "boleslaw prus")
}

@Test func catalogPlanDoesNotCreateNetworkOperationsForWhitespaceOnlyQuery() {
    let plan = CatalogSearchPlan(request: CatalogSearchRequest(
        query: " \t\n ", field: .all, languages: [.all], sources: [.libriVox, .lit2Go]
    ))
    #expect(plan.normalizedQuery.isEmpty)
    #expect(plan.operations.isEmpty)
    #expect(plan.initialOutcomes.isEmpty)
}

@Test func catalogNormalizationMatchesSharedCompatibilityMap() {
    #expect("Æsop Œuvre Straße Þing Ørn Đorđe".normalizedCatalogSearchText == "aesop oeuvre strasse thing orn dorde")
}

@Test func catalogPlanExpandsAllFieldsAndMarksIncompatibleLanguageSkipped() {
    let plan = CatalogSearchPlan(request: CatalogSearchRequest(
        query: "alice ",
        field: .all,
        languages: [.polish],
        sources: [.libriVox, .wolneLektury],
        sourcePriority: [.libriVox, .wolneLektury]
    ))

    let libriVoxOperations = plan.operations.filter { $0.source == .libriVox }
    let wolneOperations = plan.operations.filter { $0.source == .wolneLektury }
    #expect(libriVoxOperations.count == 5)
    #expect(wolneOperations.count == 5)
    #expect(libriVoxOperations.allSatisfy {
        plan.initialOutcomes[$0.id] == .skipped(.incompatibleLanguage)
    })
    #expect(wolneOperations.filter { [.title, .author, .genre].contains($0.field) }.allSatisfy {
        plan.initialOutcomes[$0.id] == .pending
    })
    #expect(wolneOperations.filter { [.reader, .chapter].contains($0.field) }.allSatisfy {
        plan.initialOutcomes[$0.id] == .skipped(.unsupportedField)
    })
}

@Test func catalogPlanRetainsUnsupportedAllFieldOperationsAsSkipped() {
    let plan = CatalogSearchPlan(request: CatalogSearchRequest(
        query: "alice",
        field: .all,
        languages: [.english],
        sources: [.lit2Go]
    ))

    #expect(plan.operations.map(\.field) == [.title, .author, .reader, .chapter, .genre])
    #expect(plan.initialOutcomes[CatalogSearchOperation(source: .lit2Go, field: .title).id] == .pending)
    #expect(plan.initialOutcomes[CatalogSearchOperation(source: .lit2Go, field: .author).id] == .pending)
    for field in [CatalogSearchField.reader, .chapter, .genre] {
        let operation = CatalogSearchOperation(source: .lit2Go, field: field)
        #expect(plan.initialOutcomes[operation.id] == .skipped(.unsupportedField))
    }
}

@Test func catalogPlanMarksUnsupportedFieldInsteadOfEmptySuccess() {
    let plan = CatalogSearchPlan(request: CatalogSearchRequest(
        query: "alice",
        field: .reader,
        languages: [.polish],
        sources: [.wolneLektury]
    ))
    guard let operation = plan.operations.first else {
        Issue.record("expected a skipped Wolne reader operation")
        return
    }
    #expect(plan.initialOutcomes[operation.id] == .skipped(.unsupportedField))
}

@Test func coordinatorStreamsOperationsAndPreservesCrossSourceFacetsBeforeMerge() async throws {
    let aliceLibriVox = AudioBook(
        id: "200",
        title: "Alice's Adventures in Wonderland",
        author: "Lewis Carroll",
        source: .libriVox,
        language: "English"
    )
    let aliceLit2Go = AudioBook(
        id: "lit2go-1__alices-adventures-in-wonderland",
        title: "Alice's Adventures in Wonderland",
        author: "Lewis Carroll",
        source: .lit2Go,
        language: "English"
    )
    let cacheURL = temporaryCatalogCacheURL()
    let coordinator = CatalogSearchCoordinator(
        clients: [
            FixtureCatalogClient(source: .libriVox) { request in
                if request.field == .title { return [aliceLibriVox] }
                return []
            },
            FixtureCatalogClient(source: .lit2Go) { request in
                if request.field == .title { return [aliceLit2Go] }
                return []
            },
        ],
        cache: JSONCatalogSearchCache(fileURL: cacheURL),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let request = CatalogSearchRequest(
        query: "alice ",
        field: .all,
        languages: [.all],
        sources: [.libriVox, .lit2Go],
        sourcePriority: [.libriVox, .lit2Go]
    )

    var snapshots: [CatalogSearchSnapshot] = []
    for await snapshot in await coordinator.snapshots(for: request) { snapshots.append(snapshot) }
    let final = try #require(snapshots.last)
    #expect(snapshots.count > 2)
    #expect(final.isComplete)
    #expect(final.hits.count == 1)
    #expect(final.hits.first?.variants.count == 2)
    #expect(final.sourceCounts[.libriVox] == 1)
    #expect(final.sourceCounts[.lit2Go] == 1)
    #expect(final.fieldCounts[.title] == 1)
    try? FileManager.default.removeItem(at: cacheURL.deletingLastPathComponent())
}

@Test func jsonCatalogCacheUsesPositiveNegativeAndStaleTTLs() async {
    let url = temporaryCatalogCacheURL()
    let cache = JSONCatalogSearchCache(fileURL: url)
    let operation = CatalogSearchOperation(source: .libriVox, field: .title)
    let key = CatalogOperationCacheKey(query: "alice ", operation: operation, languages: [.all])
    let start = Date(timeIntervalSince1970: 1_700_000_000)
    let alice = AudioBook(id: "200", title: "Alice", author: "Lewis Carroll", source: .libriVox)
    await cache.store(CatalogCachedOperation(
        key: key,
        books: [alice],
        outcome: .success(count: 1),
        storedAt: start
    ))

    if case .fresh = await cache.lookup(key, at: start.addingTimeInterval(30 * 60)) {} else {
        Issue.record("positive entry should remain fresh for 30 minutes")
    }
    if case .stale = await cache.lookup(key, at: start.addingTimeInterval(30 * 60 + 1)) {} else {
        Issue.record("positive entry should become stale after 30 minutes")
    }
    if case .miss = await cache.lookup(key, at: start.addingTimeInterval(7 * 24 * 60 * 60 + 1)) {} else {
        Issue.record("entry should expire after seven days")
    }

    let emptyKey = CatalogOperationCacheKey(
        query: "does not exist",
        operation: operation,
        languages: [.all]
    )
    await cache.store(CatalogCachedOperation(
        key: emptyKey,
        books: [],
        outcome: .authoritativeEmpty,
        storedAt: start
    ))
    if case .fresh = await cache.lookup(emptyKey, at: start.addingTimeInterval(5 * 60)) {} else {
        Issue.record("authoritative empty should remain fresh for five minutes")
    }
    if case .stale = await cache.lookup(emptyKey, at: start.addingTimeInterval(5 * 60 + 1)) {} else {
        Issue.record("authoritative empty should revalidate after five minutes")
    }
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func jsonCatalogCacheNeverStoresPartialFailuresAndUsesDeterministicLRU() async {
    let url = temporaryCatalogCacheURL()
    let cache = JSONCatalogSearchCache(fileURL: url, maximumEntries: 2)
    let operation = CatalogSearchOperation(source: .libriVox, field: .title)
    let start = Date(timeIntervalSince1970: 1_700_000_000)

    for (offset, query) in ["one", "two", "three"].enumerated() {
        let key = CatalogOperationCacheKey(query: query, operation: operation, languages: [.all])
        await cache.store(CatalogCachedOperation(
            key: key,
            books: [AudioBook(id: query, title: query, author: "Author")],
            outcome: .success(count: 1),
            storedAt: start.addingTimeInterval(Double(offset))
        ))
    }
    let failedKey = CatalogOperationCacheKey(query: "failure", operation: operation, languages: [.all])
    await cache.store(CatalogCachedOperation(
        key: failedKey,
        books: [],
        outcome: .failed(CatalogOperationFailure(message: "timeout")),
        storedAt: start.addingTimeInterval(4)
    ))

    #expect(await cache.entryCount() == 2)
    let oldestKey = CatalogOperationCacheKey(query: "one", operation: operation, languages: [.all])
    if case .miss = await cache.lookup(oldestKey, at: start.addingTimeInterval(5)) {} else {
        Issue.record("oldest deterministic LRU entry should be evicted")
    }
    if case .miss = await cache.lookup(failedKey, at: start.addingTimeInterval(5)) {} else {
        Issue.record("partial failure must never be cached")
    }
    let completeKey = CatalogOperationCacheKey(query: "three", operation: operation, languages: [.all])
    await cache.store(CatalogCachedOperation(
        key: completeKey,
        books: [],
        outcome: .failed(CatalogOperationFailure(message: "revalidation failed")),
        storedAt: start.addingTimeInterval(6)
    ))
    if case .fresh(let retained) = await cache.lookup(completeKey, at: start.addingTimeInterval(7)) {
        #expect(retained.books.first?.id == "three")
    } else {
        Issue.record("a failed revalidation must not overwrite a complete cache entry")
    }
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func jsonCatalogCacheDiscardsCorruptionAndInvalidatesLegacyDefaults() async throws {
    let url = temporaryCatalogCacheURL()
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try Data("{broken".utf8).write(to: url)
    UserDefaults.standard.set(Data("legacy".utf8), forKey: "searchCache.v2")

    let cache = JSONCatalogSearchCache(fileURL: url)
    #expect(UserDefaults.standard.data(forKey: "searchCache.v2") == nil)
    #expect(await cache.entryCount() == 0)
    let operation = CatalogSearchOperation(source: .libriVox, field: .title)
    let key = CatalogOperationCacheKey(query: "alice", operation: operation, languages: [.all])
    await cache.store(CatalogCachedOperation(
        key: key,
        books: [AudioBook(id: "200", title: "Alice", author: "Lewis Carroll")],
        outcome: .success(count: 1),
        storedAt: .now
    ))
    #expect(await cache.entryCount() == 1)
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func coordinatorUsesStaleCacheOfflineAndRetriesReconnectOnlyOnce() async throws {
    let url = temporaryCatalogCacheURL()
    let cache = JSONCatalogSearchCache(fileURL: url)
    let connectivity = MutableCatalogConnectivity(isOnline: false)
    let operation = CatalogSearchOperation(source: .libriVox, field: .title)
    let key = CatalogOperationCacheKey(query: "alice", operation: operation, languages: [.all])
    let staleDate = Date().addingTimeInterval(-(31 * 60))
    let alice = AudioBook(id: "200", title: "Alice", author: "Lewis Carroll", source: .libriVox)
    await cache.store(CatalogCachedOperation(
        key: key,
        books: [alice],
        outcome: .success(count: 1),
        storedAt: staleDate
    ))
    let calls = CallRecorder()
    let coordinator = CatalogSearchCoordinator(
        clients: [FixtureCatalogClient(source: .libriVox) { _ in
            await calls.record()
            return [alice]
        }],
        cache: cache,
        connectivity: connectivity
    )
    let request = CatalogSearchRequest(
        query: "alice",
        field: .title,
        languages: [.all],
        sources: [.libriVox]
    )

    let offline = await coordinator.search(request)
    #expect(offline.hits.first?.provenance == [.staleCache])
    #expect(await calls.count == 0)

    let retry = try #require(await coordinator.connectivityChanged(to: true))
    var retryFinal: CatalogSearchSnapshot?
    for await snapshot in retry { retryFinal = snapshot }
    #expect(retryFinal?.hits.first?.provenance == [.network])
    #expect(await calls.count == 1)
    #expect(await coordinator.connectivityChanged(to: true) == nil)
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func coordinatorNetworkLossRetainsCompletedTitleAndFailsDelayedAuthorUntilReconnect() async throws {
    let url = temporaryCatalogCacheURL()
    let alice = AudioBook(
        id: "200",
        title: "Alice's Adventures in Wonderland",
        author: "Lewis Carroll",
        source: .libriVox,
        chapters: [AudioBookChapter(
            id: "200-1",
            title: "Down the Rabbit-Hole",
            number: 1,
            listenURL: "https://example.com/alice.mp3"
        )]
    )
    let client = FixtureCatalogClient(source: .libriVox) { request in
        if request.field == .title {
            try await Task.sleep(for: .milliseconds(10))
            return [alice]
        }
        try await Task.sleep(for: .seconds(30))
        return []
    }
    let coordinator = CatalogSearchCoordinator(
        clients: [client],
        cache: JSONCatalogSearchCache(fileURL: url),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let request = CatalogSearchRequest(
        query: "alice",
        field: .all,
        languages: [.english],
        sources: [.libriVox]
    )
    let titleID = CatalogSearchOperation(source: .libriVox, field: .title).id
    let authorID = CatalogSearchOperation(source: .libriVox, field: .author).id

    let liveStream = await coordinator.snapshots(for: request)
    var liveIterator = liveStream.makeAsyncIterator()
    var progressive: CatalogSearchSnapshot?
    while let snapshot = await liveIterator.next() {
        if snapshot.outcomes[titleID] == .success(count: 1),
           snapshot.outcomes[authorID] == .pending {
            progressive = snapshot
            break
        }
    }
    #expect(progressive?.hits.first?.book.id == "200")
    #expect(await coordinator.cacheEntryCount() == 1)

    let offlineStream = try #require(await coordinator.connectivityChanged(to: false))
    var offlineSnapshots: [CatalogSearchSnapshot] = []
    var offlineFinal: CatalogSearchSnapshot?
    for await snapshot in offlineStream {
        offlineSnapshots.append(snapshot)
        offlineFinal = snapshot
    }
    #expect(!offlineSnapshots.isEmpty)
    #expect(offlineSnapshots.allSatisfy { $0.hits.contains { $0.book.id == "200" } })
    let final = try #require(offlineFinal)
    #expect(final.outcomes[titleID] == .success(count: 1))
    guard case .failed(let authorFailure) = final.outcomes[authorID] else {
        Issue.record("unfinished author operation must become Failed after network loss")
        return
    }
    #expect(authorFailure.isRetryable)
    #expect(final.hits.first?.book.id == "200")
    #expect(final.hits.first?.provenance == [.freshCache])

    let reconnectStream = try #require(await coordinator.connectivityChanged(to: true))
    var reconnectIterator = reconnectStream.makeAsyncIterator()
    let reconnectFirst = await reconnectIterator.next()
    #expect(reconnectFirst?.hits.contains { $0.book.id == "200" } == true)
    await coordinator.cancel()
    await coordinator.clearCache()
    #expect(await coordinator.cacheEntryCount() == 0)
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func coordinatorCachingDisabledAlwaysUsesNetworkAndStoresNothing() async {
    let url = temporaryCatalogCacheURL()
    let calls = CallRecorder()
    let alice = AudioBook(
        id: "200", title: "Alice", author: "Lewis Carroll", source: .libriVox
    )
    let coordinator = CatalogSearchCoordinator(
        clients: [FixtureCatalogClient(source: .libriVox) { _ in
            await calls.record()
            return [alice]
        }],
        cache: JSONCatalogSearchCache(fileURL: url),
        connectivity: MutableCatalogConnectivity(isOnline: true),
        cachingEnabled: false
    )
    let request = CatalogSearchRequest(
        query: "alice", field: .title, languages: [.all], sources: [.libriVox]
    )

    _ = await coordinator.search(request)
    _ = await coordinator.search(request)

    #expect(await calls.count == 2)
    #expect(await coordinator.cacheEntryCount() == 0)
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func coordinatorCancelsPreviousPlanWhenSourcesOrQueryChangeMidflight() async throws {
    let url = temporaryCatalogCacheURL()
    let client = FixtureCatalogClient(source: .libriVox) { request in
        if request.query == "slow" {
            try await Task.sleep(for: .seconds(2))
        }
        return [AudioBook(
            id: request.query,
            title: request.query.capitalized,
            author: "Fixture Author",
            source: .libriVox
        )]
    }
    let coordinator = CatalogSearchCoordinator(
        clients: [client],
        cache: JSONCatalogSearchCache(fileURL: url),
        connectivity: MutableCatalogConnectivity(isOnline: true)
    )
    let slowRequest = CatalogSearchRequest(
        query: "slow", field: .title, languages: [.all], sources: [.libriVox]
    )
    let slowStream = await coordinator.snapshots(for: slowRequest)
    let slowCollector = Task { () -> [CatalogSearchSnapshot] in
        var snapshots: [CatalogSearchSnapshot] = []
        for await snapshot in slowStream { snapshots.append(snapshot) }
        return snapshots
    }
    try await Task.sleep(for: .milliseconds(30))

    let fast = await coordinator.search(CatalogSearchRequest(
        query: "fast", field: .title, languages: [.all], sources: [.libriVox]
    ))
    let slow = await slowCollector.value

    #expect(fast.hits.first?.book.id == "fast")
    #expect(slow.last?.hits.isEmpty == true)
    try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
}

@Test func libriVoxAdvancedParserBuildsStableSearchBooks() throws {
    let html = """
    <li class="catalog-result">
      <a href="https://librivox.org/alices-adventures-in-wonderland-by-lewis-carroll/" class="book-cover"><img src="https://archive.org/alice_thumb.jpg"></a>
      <div class="result-data">
        <h3><a href="https://librivox.org/alices-adventures-in-wonderland-by-lewis-carroll/">Alice's Adventures in Wonderland</a></h3>
        <p class="book-author"><a href="https://librivox.org/author/19">Lewis Carroll <span>(1832 - 1898)</span></a></p>
        <p class="book-meta">Complete | Solo | English</p>
      </div>
    </li>
    """

    let books = LibriVoxCatalogClient.parseAdvancedSearchResults(html, matchedField: .reader)
    let book = try #require(books.first)
    #expect(book.id == "librivox-web-alices-adventures-in-wonderland-by-lewis-carroll")
    #expect(book.title == "Alice's Adventures in Wonderland")
    #expect(book.author == "Lewis Carroll")
    #expect(book.language == "English")
}

@Test func libriVoxClientUsesAnchoredTitleFeedAndInjectedTransport() async throws {
    let transport = FixtureHTTPTransport { request in
        let components = URLComponents(url: try #require(request.url), resolvingAgainstBaseURL: false)
        #expect(components?.queryItems?.contains(URLQueryItem(name: "title", value: "^alice")) == true)
        return CatalogHTTPResponse(data: Data("""
        {"books":[{"id":"200","title":"Alice's Adventures in Wonderland","authors":[{"first_name":"Lewis","last_name":"Carroll"}],"language":"English"}]}
        """.utf8), statusCode: 200)
    }
    let client = LibriVoxCatalogClient(transport: transport)
    let books = try await client.search(CatalogSearchRequest(
        query: " Alice ",
        field: .title,
        languages: [.all],
        sources: [.libriVox]
    ))
    #expect(books.first?.id == "200")
}

@Test func libriVoxAdvancedSearchPrimesSessionAndRetainsCookie() async throws {
    let transport = FixtureHTTPTransport { request in
        let url = try #require(request.url)
        if url.path == "/search" {
            return CatalogHTTPResponse(
                data: Data("<html></html>".utf8),
                statusCode: 200,
                headers: ["set-cookie": "PHPSESSID=fixture-session; Path=/; Secure"]
            )
        }
        if url.path == "/api/feed/audiobooks" {
            return CatalogHTTPResponse(data: Data("""
            {"books":[{"id":"200","title":"Alice Fixture","authors":[{"first_name":"Alice","last_name":"Author"}],"language":"English","url_librivox":"https://librivox.org/alice-fixture/","sections":[{"id":"200-1","section_number":"1","title":"Chapter 1","listen_url":"https://example.com/alice-fixture.mp3"}]}]}
            """.utf8), statusCode: 200)
        }
        #expect(url.path == "/advanced_search")
        #expect(request.value(forHTTPHeaderField: "Cookie") == "PHPSESSID=fixture-session")
        let html = """
        <li class="catalog-result"><div class="result-data"><h3><a href="https://librivox.org/alice-fixture/">Alice Fixture</a></h3><p class="book-author"><a href="/author/1">Alice Author</a></p><p class="book-meta">Complete | Solo | English</p></div></li>
        """
        let data = try JSONSerialization.data(withJSONObject: [
            "status": "SUCCESS", "results": html, "pagination": "",
        ])
        return CatalogHTTPResponse(data: data, statusCode: 200)
    }
    let books = try await LibriVoxCatalogClient(transport: transport).search(CatalogSearchRequest(
        query: "alice", field: .author, languages: [.all], sources: [.libriVox]
    ))
    #expect(books.first?.id == "200")
    #expect(books.first?.title == "Alice Fixture")
    #expect(books.first?.chapters.first?.listenURL == "https://example.com/alice-fixture.mp3")
}

@Test func libriVoxAdvancedSearchSerializesSharedPHPSessionRequests() async throws {
    let concurrency = ConcurrencyRecorder()
    let transport = FixtureHTTPTransport { request in
        let url = try #require(request.url)
        switch url.path {
        case "/search":
            return CatalogHTTPResponse(
                data: Data("<html></html>".utf8),
                statusCode: 200,
                headers: ["set-cookie": "PHPSESSID=serialized-session; Path=/"]
            )
        case "/advanced_search":
            await concurrency.begin()
            try await Task.sleep(for: .milliseconds(15))
            await concurrency.end()
            let html = """
            <li class="catalog-result"><div class="result-data"><h3><a href="https://librivox.org/alice-fixture/">Alice Fixture</a></h3><p class="book-author"><a href="/author/1">Alice Author</a></p><p class="book-meta">Complete | Solo | English</p></div></li>
            """
            return CatalogHTTPResponse(data: try JSONSerialization.data(withJSONObject: [
                "status": "SUCCESS", "results": html, "pagination": "",
            ]), statusCode: 200)
        default:
            return CatalogHTTPResponse(data: Data("""
            {"books":[{"id":"200","title":"Alice Fixture","authors":[{"first_name":"Alice","last_name":"Author"}],"url_librivox":"https://librivox.org/alice-fixture/","sections":[{"id":"200-1","section_number":"1","title":"Chapter 1","listen_url":"https://example.com/alice.mp3"}]}]}
            """.utf8), statusCode: 200)
        }
    }
    let client = LibriVoxCatalogClient(transport: transport)
    await withTaskGroup(of: Void.self) { group in
        for field in [CatalogSearchField.author, .reader, .chapter] {
            group.addTask {
                _ = try? await client.search(CatalogSearchRequest(
                    query: "alice", field: field, languages: [.all], sources: [.libriVox]
                ))
            }
        }
    }
    #expect(await concurrency.maximumConcurrent == 1)
}

@Test func sourceAdaptersRejectMalformedBlankAndHTTPFailureAsUnavailable() async {
    let malformed = FixtureHTTPTransport { _ in
        CatalogHTTPResponse(data: Data("not-json".utf8), statusCode: 200)
    }
    let failed = FixtureHTTPTransport { _ in
        CatalogHTTPResponse(data: Data(), statusCode: 500)
    }
    let request = CatalogSearchRequest(
        query: "alice", field: .title, languages: [.all], sources: [.libriVox]
    )
    do {
        _ = try await LibriVoxCatalogClient(transport: malformed).search(request)
        Issue.record("malformed LibriVox JSON must not become authoritative empty")
    } catch {}
    do {
        _ = try await LibriVoxCatalogClient(transport: failed).search(request)
        Issue.record("LibriVox HTTP 500 must fail the operation")
    } catch {}

    do {
        _ = try await WolneLekturyCatalogClient(transport: malformed).search(CatalogSearchRequest(
            query: "Antek", field: .title, languages: [.polish], sources: [.wolneLektury]
        ))
        Issue.record("malformed Wolne JSON must not become authoritative empty")
    } catch {}
    do {
        _ = try await Lit2GoCatalogClient(transport: malformed).search(CatalogSearchRequest(
            query: "alice", field: .title, languages: [.english], sources: [.lit2Go]
        ))
        Issue.record("blank or malformed Lit2Go indexes must report unavailable")
    } catch {}
}

@Test func lit2GoRequiresAtLeastOnePlayableDetailAndKeepsPartialSuccess() async throws {
    let fixtures = repositoryRoot().appendingPathComponent("catalog-contract/fixtures/v1", isDirectory: true)
    let detail = try Data(contentsOf: fixtures.appendingPathComponent("lit2go/detail-alice.html"))
    let errorPage = try Data(contentsOf: fixtures.appendingPathComponent("failures/http-200-error-page.html"))
    let index = Data("""
    <html><body>
      <a class="book_icon" href="/lit2go/1/alices-adventures-in-wonderland/"><img alt="Alice's Adventures in Wonderland"></a>
      <a class="book_icon" href="/lit2go/2/alice-through-the-looking-glass/"><img alt="Alice Through the Looking-Glass"></a>
    </body></html>
    """.utf8)
    let request = CatalogSearchRequest(
        query: "alice", field: .title, languages: [.english], sources: [.lit2Go]
    )

    let allDetailsFail = FixtureHTTPTransport { request in
        let path = try #require(request.url?.path)
        if path.contains("/books/index/") {
            return CatalogHTTPResponse(data: index, statusCode: 200)
        }
        return CatalogHTTPResponse(data: Data(), statusCode: 404)
    }
    do {
        _ = try await Lit2GoCatalogClient(transport: allDetailsFail).search(request)
        Issue.record("all Lit2Go detail failures must make the operation unavailable")
    } catch {}

    let partialDetail = FixtureHTTPTransport { request in
        let path = try #require(request.url?.path)
        if path.contains("/books/index/") {
            return CatalogHTTPResponse(data: index, statusCode: 200)
        }
        if path.contains("/lit2go/1/alices-adventures-in-wonderland") {
            return CatalogHTTPResponse(data: detail, statusCode: 200)
        }
        return CatalogHTTPResponse(data: Data(), statusCode: 404)
    }
    let partialBooks = try await Lit2GoCatalogClient(transport: partialDetail).search(request)
    #expect(partialBooks.count == 1)
    #expect(partialBooks.first?.id == "lit2go-1__alices-adventures-in-wonderland")
    #expect(partialBooks.first?.chapters.first?.listenURL?.isEmpty == false)

    let outageHTML = FixtureHTTPTransport { request in
        let path = try #require(request.url?.path)
        if path.contains("/books/index/") {
            return CatalogHTTPResponse(data: index, statusCode: 200)
        }
        return CatalogHTTPResponse(data: errorPage, statusCode: 200)
    }
    do {
        _ = try await Lit2GoCatalogClient(transport: outageHTML).search(request)
        Issue.record("HTTP 200 outage HTML must not become authoritative Lit2Go success")
    } catch {}
}

@Test func sharedCatalogContractJSONFixturesAreReadableBySwiftTests() throws {
    let root = repositoryRoot().appendingPathComponent("catalog-contract", isDirectory: true)
    #expect(FileManager.default.fileExists(atPath: root.path))
    let enumerator = try #require(FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil))
    let jsonFiles = enumerator.compactMap { $0 as? URL }.filter { $0.pathExtension == "json" }
    #expect(!jsonFiles.isEmpty)
    for file in jsonFiles {
        let data = try Data(contentsOf: file)
        #expect((try? JSONSerialization.jsonObject(with: data)) != nil, "Invalid shared fixture: \(file.path)")
    }

    let scenariosURL = root.appendingPathComponent("scenarios/v1/catalog-search-scenarios.json")
    let scenariosRoot = try #require(
        JSONSerialization.jsonObject(with: Data(contentsOf: scenariosURL)) as? [String: Any]
    )
    #expect(scenariosRoot["contractVersion"] as? Int == CatalogContract.version)
    let scenarios = try #require(scenariosRoot["scenarios"] as? [[String: Any]])
    for scenario in scenarios where scenario["kind"] as? String == "normalization" {
        let request = try #require(scenario["request"] as? [String: Any])
        let expected = try #require(scenario["expected"] as? [String: Any])
        let query = try #require(request["query"] as? String)
        let normalized = try #require(expected["normalizedQuery"] as? String)
        #expect(query.normalizedCatalogSearchText == normalized, "Shared scenario failed: \(scenario["id"] ?? "unknown")")
    }

    let manifestURL = root.appendingPathComponent("fixtures/v1/manifest.json")
    let manifest = try #require(
        JSONSerialization.jsonObject(with: Data(contentsOf: manifestURL)) as? [String: Any]
    )
    let entries = try #require(manifest["entries"] as? [[String: Any]])
    for entry in entries {
        let path = try #require(entry["path"] as? String)
        let fixtureURL = root.appendingPathComponent(path)
        #expect(FileManager.default.fileExists(atPath: fixtureURL.path), "Missing shared fixture: \(path)")
        #expect((try Data(contentsOf: fixtureURL)).isEmpty == false, "Empty shared fixture: \(path)")
    }
}

@Test func sharedPlanningScenariosMatchSwiftPlanIncludingUnsupportedFields() throws {
    let scenariosURL = repositoryRoot()
        .appendingPathComponent("catalog-contract/scenarios/v1/catalog-search-scenarios.json")
    let root = try #require(
        JSONSerialization.jsonObject(with: Data(contentsOf: scenariosURL)) as? [String: Any]
    )
    let scenarios = try #require(root["scenarios"] as? [[String: Any]])

    for scenario in scenarios {
        guard let expected = scenario["expected"] as? [String: Any],
              let expectedKeys = expected["plannedOperationKeys"] as? [String],
              let request = scenario["request"] as? [String: Any],
              let query = request["query"] as? String,
              let sourceNames = request["sources"] as? [String],
              let fieldNames = request["fields"] as? [String],
              let languageNames = request["languages"] as? [String],
              let priorityNames = request["sourcePriority"] as? [String]
        else { continue }

        let field: CatalogSearchField
        if fieldNames.count == 5 {
            field = .all
        } else if fieldNames.count == 1 {
            field = try #require(sharedField(fieldNames.first))
        } else {
            // CatalogSearchRequest models either one field or the five-field All mode.
            continue
        }
        let languages = Set(languageNames.compactMap(sharedLanguage))
        let plan = CatalogSearchPlan(request: CatalogSearchRequest(
            query: query,
            field: field,
            languages: languages,
            sources: Set(sourceNames.compactMap(sharedSource)),
            sourcePriority: priorityNames.compactMap(sharedSource)
        ))
        let languageKey = languageNames.first ?? "any"
        let actualKeys = plan.operations.map {
            "\(sharedSourceName($0.source)):\($0.field.rawValue):\(languageKey)"
        }
        #expect(actualKeys == expectedKeys, "Shared planning scenario failed: \(scenario["id"] ?? "unknown")")

        if let skipReasons = expected["skipReasons"] as? [String: String] {
            for (operationKey, reason) in skipReasons {
                guard let index = actualKeys.firstIndex(of: operationKey) else {
                    Issue.record("Missing shared skipped operation \(operationKey)")
                    continue
                }
                let outcome = plan.initialOutcomes[plan.operations[index].id]
                switch reason {
                case "unsupported_field":
                    #expect(outcome == .skipped(.unsupportedField))
                case "incompatible_language":
                    #expect(outcome == .skipped(.incompatibleLanguage))
                default:
                    break
                }
            }
        }
    }
}

@Test func swiftSourceParsersConsumeSharedRecordedFixtures() async throws {
    let fixtures = repositoryRoot().appendingPathComponent("catalog-contract/fixtures/v1", isDirectory: true)

    let feedData = try Data(contentsOf: fixtures.appendingPathComponent("librivox/feed-title-alice.json"))
    let feedBooks = try await LibriVoxCatalogClient(transport: FixtureHTTPTransport { _ in
        CatalogHTTPResponse(data: feedData, statusCode: 200)
    }).search(CatalogSearchRequest(
        query: "alice ", field: .title, languages: [.all], sources: [.libriVox]
    ))
    #expect(feedBooks.contains { $0.id == "200" })

    for (name, field) in [
        ("advanced-author-alice.json", CatalogSearchField.author),
        ("advanced-reader-alice.json", CatalogSearchField.reader),
        ("advanced-chapter-advice.json", CatalogSearchField.chapter),
    ] {
        let data = try Data(contentsOf: fixtures.appendingPathComponent("librivox/\(name)"))
        let root = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let html = try #require(root["results"] as? String)
        let books = LibriVoxCatalogClient.parseAdvancedSearchResults(html, matchedField: field)
        #expect(!books.isEmpty)
        if field == .author {
            #expect(books.contains { $0.author.localizedCaseInsensitiveContains("Alice") })
        } else if field == .chapter {
            #expect(books.contains { book in
                book.chapters.contains { $0.title.localizedCaseInsensitiveContains("Advice From A Caterpillar") }
            })
        }
    }

    let litIndex = try String(
        contentsOf: fixtures.appendingPathComponent("lit2go/index-a.html"),
        encoding: .utf8
    )
    #expect(!Lit2GoCatalogClient.parseLetterIndexBooks(litIndex).isEmpty)
    let litDetail = try String(
        contentsOf: fixtures.appendingPathComponent("lit2go/detail-alice.html"),
        encoding: .utf8
    )
    #expect(Lit2GoCatalogClient.parseBookDetail(
        numericID: "1", slug: "alices-adventures-in-wonderland", html: litDetail
    ).title.localizedCaseInsensitiveContains("Alice"))

    let gutendex = LegacyGutendexBookParser.parseBooks(
        try Data(contentsOf: fixtures.appendingPathComponent("gutendex/search-alice.json"))
    )
    #expect(!gutendex.isEmpty)
    #expect(gutendex.allSatisfy { $0.chapters.contains { $0.listenURL?.isEmpty == false } })

    let wolne = try WolneLekturyCatalogClient.parseDetail(
        slug: "antek",
        data: Data(contentsOf: fixtures.appendingPathComponent("wolne/detail-antek.json"))
    )
    #expect(wolne.title.localizedCaseInsensitiveContains("Antek"))
    #expect(wolne.chapters.contains { $0.listenURL?.isEmpty == false })
}

@Test func liveCatalogCanariesWhenExplicitlyEnabled() async throws {
    guard ProcessInfo.processInfo.environment["RUN_LIVE_CATALOG_TESTS"] == "1" else { return }

    let libriVox = LibriVoxCatalogClient()
    let title = try await libriVox.search(CatalogSearchRequest(
        query: "alice ", field: .title, languages: [.all], sources: [.libriVox]
    ))
    #expect(title.contains { $0.id == "200" })
    for field in [CatalogSearchField.author, .reader, .chapter] {
        let books = try await libriVox.search(CatalogSearchRequest(
            query: "alice", field: field, languages: [.all], sources: [.libriVox]
        ))
        #expect(!books.isEmpty, "LibriVox \(field.rawValue) Alice canary returned no results")
    }

    let wolne = try await WolneLekturyCatalogClient().search(CatalogSearchRequest(
        query: "Antek", field: .all, languages: [.polish], sources: [.wolneLektury]
    ))
    #expect(wolne.contains { $0.title.localizedCaseInsensitiveContains("Antek") })

}

@Test func polishTitleAndAllSearchesPresentParentBooksWithoutWaitingForAudioHydration() async throws {
    let requests = CallRecorder()
    let index = Data("""
        [
          {"slug":"lalka","title":"Lalka","author":"Bolesław Prus","has_audio":true},
          {"slug":"antek","title":"Antek","author":"Bolesław Prus","has_audio":true}
        ]
        """.utf8)
    let transport = FixtureHTTPTransport { request in
        await requests.record()
        #expect(request.url?.path == "/api/parent_books")
        guard request.url?.path == "/api/parent_books" else { throw URLError(.timedOut) }
        return CatalogHTTPResponse(data: index, statusCode: 200)
    }
    let client = WolneLekturyCatalogClient(transport: transport)
    async let initialLalka = client.search(CatalogSearchRequest(
        query: "Lalka", field: .title, languages: [.polish], sources: [.wolneLektury]
    ))
    async let initialAntek = client.search(CatalogSearchRequest(
        query: "Antek", field: .all, languages: [.polish], sources: [.wolneLektury]
    ))
    #expect(try await initialLalka.first?.id == "wolnelektury-lalka")
    #expect(try await initialAntek.first?.id == "wolnelektury-antek")
    for field in [CatalogSearchField.all, .title] {
        for title in ["Lalka", "Antek"] {
            let books = try await client.search(CatalogSearchRequest(
                query: title, field: field, languages: [.polish], sources: [.wolneLektury]
            ))
            #expect(books.map(\.id) == ["wolnelektury-\(title.lowercased())"])
        }
    }
    let featured = try await client.featuredBooks(limit: 40)
    #expect(Set(featured.map(\.title)) == ["Lalka", "Antek"])
    #expect(await requests.count == 1)
}

@Test func switchingToPolishCancelsAnUnfinishedLibriVoxSearch() async throws {
    let started = CallRecorder()
    let cancelled = CallRecorder()
    let cacheURL = FileManager.default.temporaryDirectory.appendingPathComponent("polish-switch-\(UUID()).json")
    defer { try? FileManager.default.removeItem(at: cacheURL) }
    let coordinator = CatalogSearchCoordinator(clients: [
        FixtureCatalogClient(source: .libriVox) { _ in
            await started.record()
            do {
                try await Task.sleep(for: .seconds(60))
            } catch {
                await cancelled.record()
                throw error
            }
            return []
        },
        FixtureCatalogClient(source: .wolneLektury) { request in
            [AudioBook(id: "wolnelektury-\(request.query.lowercased())", title: request.query,
                       author: "Bolesław Prus", source: .wolneLektury, language: "Polish")]
        },
    ], cache: JSONCatalogSearchCache(fileURL: cacheURL))
    let previous = await coordinator.snapshots(for: CatalogSearchRequest(
        query: "Lalka", field: .title, languages: [.all], sources: [.libriVox]
    ))
    let consuming = Task { for await _ in previous {} }
    for _ in 0..<100 where await started.count == 0 {
        try await Task.sleep(for: .milliseconds(10))
    }
    #expect(await started.count == 1)
    for title in ["Lalka", "Antek"] {
        let snapshot = await coordinator.search(CatalogSearchRequest(
            query: title, field: .title, languages: [.polish], sources: [.wolneLektury]
        ))
        #expect(snapshot.hits.map(\.book.id) == ["wolnelektury-\(title.lowercased())"])
    }
    await consuming.value
    #expect(await cancelled.count == 1)
}

@Test func livePolishLalkaAndAntekSearchesResolveParentTitles() async throws {
    guard ProcessInfo.processInfo.environment["RUN_LIVE_CATALOG_TESTS"] == "1" else { return }
    let client = WolneLekturyCatalogClient()
    for field in [CatalogSearchField.all, .title] {
        for title in ["Lalka", "Antek"] {
            let books = try await client.search(CatalogSearchRequest(
                query: title, field: field, languages: [.polish], sources: [.wolneLektury]
            ))
            #expect(books.contains { $0.id == "wolnelektury-\(title.lowercased())" })
        }
    }
}

private struct FixtureCatalogClient: CatalogSourceClient {
    var source: BookSource
    var handler: @Sendable (CatalogSearchRequest) async throws -> [AudioBook]

    func featuredBooks(limit: Int) async throws -> [AudioBook] { [] }
    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] { try await handler(request) }
    func hydrate(_ book: AudioBook) async throws -> AudioBook { book }
}

private struct FixtureHTTPTransport: CatalogHTTPTransport {
    var handler: @Sendable (URLRequest) async throws -> CatalogHTTPResponse
    func response(for request: URLRequest) async throws -> CatalogHTTPResponse { try await handler(request) }
}

private actor CallRecorder {
    private(set) var count = 0
    func record() { count += 1 }
}

private actor ConcurrencyRecorder {
    private var active = 0
    private(set) var maximumConcurrent = 0

    func begin() {
        active += 1
        maximumConcurrent = max(maximumConcurrent, active)
    }

    func end() {
        active -= 1
    }
}

private func temporaryCatalogCacheURL() -> URL {
    FileManager.default.temporaryDirectory
        .appendingPathComponent("LibriVoxCatalogCacheTests-\(UUID().uuidString)", isDirectory: true)
        .appendingPathComponent("catalog-search.json")
}

private func repositoryRoot() -> URL {
    var url = URL(fileURLWithPath: #filePath)
    for _ in 0..<5 { url.deleteLastPathComponent() }
    return url
}

private func sharedSource(_ value: String?) -> BookSource? {
    switch value {
    case "librivox": .libriVox
    case "lit2go": .lit2Go
    case "gutendex": .gutendex
    case "wolne_lektury": .wolneLektury
    default: nil
    }
}

private func sharedSourceName(_ source: BookSource) -> String {
    switch source {
    case .libriVox: "librivox"
    case .lit2Go: "lit2go"
    case .gutendex: "gutendex"
    case .wolneLektury: "wolne_lektury"
    case .localAsset: "local_asset"
    case .customLocal: "custom_local"
    }
}

private func sharedField(_ value: String?) -> CatalogSearchField? {
    value.flatMap(CatalogSearchField.init(rawValue:))
}

private func sharedLanguage(_ value: String) -> BookLanguagePreference? {
    value == "any" ? .all : BookLanguagePreference(rawValue: value)
}
