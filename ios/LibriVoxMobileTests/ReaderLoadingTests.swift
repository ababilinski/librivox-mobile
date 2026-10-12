import LibriVoxCore
import XCTest
@testable import LibriVoxMobile

final class ReaderLoadingTests: XCTestCase {
    @MainActor
    func testAnOlderReaderLoadCannotReplaceTheNewBook() async {
        let loader = SuspendedReaderLoader()
        let app = AppModel(catalogClients: [], readAlongLoader: { await loader.load($0) })
        let first = AudioBook(id: "reader-first", title: "First", author: "Author", source: .wolneLektury)
        let second = AudioBook(id: "reader-second", title: "Second", author: "Author", source: .wolneLektury)
        let firstLoad = Task { await app.loadReadAlong(for: first) }
        await loader.waitUntilStarted(first.id)
        let secondLoad = Task { await app.loadReadAlong(for: second) }
        await loader.waitUntilStarted(second.id)

        await loader.finish(second)
        await secondLoad.value
        XCTAssertEqual(app.readAlongBook?.bookID, second.id)
        XCTAssertFalse(app.isReaderLoading)
        await loader.finish(first)
        await firstLoad.value
        XCTAssertEqual(app.readAlongBook?.bookID, second.id)
    }

    @MainActor
    func testLeavingReaderDiscardsCancelledLoadingResult() async {
        let loader = SuspendedReaderLoader()
        let app = AppModel(catalogClients: [], readAlongLoader: { await loader.load($0) })
        let book = AudioBook(id: "reader-cancelled", title: "Cancelled", author: "Author", source: .wolneLektury)
        let load = Task { await app.loadReadAlong(for: book) }
        await loader.waitUntilStarted(book.id)
        XCTAssertTrue(app.isReaderLoading)
        load.cancel()
        await loader.finish(book)
        await load.value
        XCTAssertNil(app.readAlongBook)
        XCTAssertFalse(app.isReaderLoading)
    }
}

private actor SuspendedReaderLoader {
    private var pending: [String: CheckedContinuation<ReadAlongBook, Never>] = [:]
    private var startWaiters: [String: CheckedContinuation<Void, Never>] = [:]

    func load(_ book: AudioBook) async -> ReadAlongBook {
        await withCheckedContinuation { continuation in
            pending[book.id] = continuation
            startWaiters.removeValue(forKey: book.id)?.resume()
        }
    }

    func waitUntilStarted(_ bookID: String) async {
        if pending[bookID] != nil { return }
        await withCheckedContinuation { startWaiters[bookID] = $0 }
    }

    func finish(_ book: AudioBook) {
        pending.removeValue(forKey: book.id)?.resume(returning: .fallback(for: book))
    }
}
