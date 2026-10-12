import AVFoundation
import LibriVoxCore
import MediaPlayer
import UIKit
import XCTest
@testable import LibriVoxMobile

final class PlaybackControllerTests: XCTestCase {
    @MainActor
    func testControllerReleasesWhileDurationIsLoading() async throws {
        var controller: PlaybackController? = PlaybackController()
        controller?.configureAudioSession(activateSession: {})
        weak let releasedController = controller
        controller?.play(book: makeBook(), startAt: 0, autoplay: false)
        controller = nil
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertNil(releasedController)
    }

    @MainActor
    func testAudioSessionFailureDoesNotTerminateTheApp() {
        let controller = PlaybackController()
        controller.configureAudioSession {
            throw NSError(domain: "AudioSessionRegression", code: 1)
        }
        XCTAssertFalse(controller.isPlaying)
        XCTAssertNil(controller.currentBook)
    }

    func testCoordinatorIdentifiersSupportConcurrentReadsAndWrites() {
        let delegate = AudiobookPlaybackCoordinatorDelegate()
        let player = AVQueuePlayer()
        let items = (0..<8).map { AVPlayerItem(url: URL(fileURLWithPath: "/identifier-\($0).wav")) }
        DispatchQueue.concurrentPerform(iterations: items.count) { lane in
            let item = items[lane]
            let identifier = "book::chapter-\(lane)"
            for _ in 0..<1_000 {
                delegate.setIdentifier(identifier, for: item)
                XCTAssertEqual(delegate.playbackCoordinator(player.playbackCoordinator, identifierFor: item), identifier)
            }
        }
    }

    @MainActor
    func testThirtyNaturalChapterTransitionsPreserveOrderAndStopAtTheEnd() async throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("playback-\(UUID()).caf")
        defer { try? FileManager.default.removeItem(at: url) }
        let format = try XCTUnwrap(AVAudioFormat(standardFormatWithSampleRate: 8_000, channels: 1))
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 800))
        buffer.frameLength = 800
        buffer.floatChannelData?[0].update(repeating: 0, count: 800)
        // Close the writer before AVPlayer reads the finalized CAF header.
        do {
            let file = try AVAudioFile(forWriting: url, settings: format.settings)
            try file.write(from: buffer)
        }

        let chapters = (1...30).map { number in
            AudioBookChapter(id: "local-\(number)", title: "Local \(number)", number: number, listenURL: url.absoluteString)
        }
        let book = AudioBook(id: "local-transitions", title: "Local transitions", author: "Test", chapters: chapters)
        let player = AVQueuePlayer()
        let controller = PlaybackController(player: player)
        controller.configureAudioSession()
        var ended: [String] = []
        controller.onChapterEnded = { _, chapter in
            ended.append(chapter.id)
            return false
        }
        controller.play(book: book, startAt: 0)
        let deadline = Date().addingTimeInterval(20)
        while ended.count < chapters.count, Date() < deadline {
            try await Task.sleep(for: .milliseconds(50))
        }
        XCTAssertEqual(ended, chapters.map(\.id), "Item error: \(String(describing: player.currentItem?.error)), player error: \(String(describing: player.error)), item count: \(player.items().count), rate: \(player.rate), state: \(player.timeControlStatus.rawValue), waiting: \(String(describing: player.reasonForWaitingToPlay))")
        XCTAssertEqual(controller.currentChapter?.id, chapters.last?.id)
        XCTAssertFalse(controller.isPlaying)
        XCTAssertTrue(controller.currentTimeSeconds.isFinite)
    }

    @MainActor
    func testAnUnrelatedEndedItemDoesNotAdvanceTheAudiobook() async throws {
        let player = AVQueuePlayer()
        let controller = PlaybackController(player: player)
        controller.configureAudioSession()
        let book = makeBook()
        controller.play(book: book, chapter: book.chapters[0], startAt: 0, autoplay: false)

        NotificationCenter.default.post(
            name: .AVPlayerItemDidPlayToEndTime,
            object: AVPlayerItem(url: URL(fileURLWithPath: "/unrelated.wav"))
        )
        try await Task.sleep(for: .milliseconds(100))

        XCTAssertEqual(controller.currentChapter?.id, book.chapters[0].id)
        XCTAssertFalse(controller.isPlaying)
    }

    @MainActor
    func testDelayedEndFromReplacedItemDoesNotAdvanceTheNewSelection() async throws {
        let player = AVQueuePlayer()
        let controller = PlaybackController(player: player)
        controller.configureAudioSession()
        let book = makeBook()
        controller.play(book: book, chapter: book.chapters[0], startAt: 0, autoplay: false)
        let oldItem = try XCTUnwrap(player.currentItem)

        // Delivery is queued on the main actor. Replace the item before it is handled.
        NotificationCenter.default.post(name: .AVPlayerItemDidPlayToEndTime, object: oldItem)
        controller.play(book: book, chapter: book.chapters[1], startAt: 12, autoplay: false)
        try await Task.sleep(for: .milliseconds(100))

        XCTAssertEqual(controller.currentChapter?.id, book.chapters[1].id)
        XCTAssertFalse(controller.isPlaying)
        XCTAssertEqual(controller.currentTimeSeconds, 12, accuracy: 0.1)
    }

    @MainActor
    func testRapidChapterChangesKeepTheLastBookAndFiniteProgress() async throws {
        let player = AVQueuePlayer()
        let controller = PlaybackController(player: player)
        controller.configureAudioSession()
        let book = makeBook()
        for index in 0..<100 {
            controller.play(
                book: book,
                chapter: book.chapters[index % book.chapters.count],
                startAt: Double(index),
                autoplay: false
            )
            controller.seek(to: index.isMultiple(of: 2) ? .nan : .infinity)
        }
        controller.play(book: book, chapter: book.chapters[1], startAt: 23, autoplay: false)
        try await Task.sleep(for: .milliseconds(300))

        XCTAssertEqual(controller.currentBook?.id, book.id)
        XCTAssertEqual(controller.currentChapter?.id, book.chapters[1].id)
        XCTAssertTrue(controller.currentTimeSeconds.isFinite)
        XCTAssertTrue(controller.durationSeconds.isFinite)
        XCTAssertFalse(controller.isPlaying)
    }

    private func makeBook() -> AudioBook {
        AudioBook(
            id: "playback-regression",
            title: "Playback regression",
            author: "Test reader",
            chapters: (1...3).map { number in
                AudioBookChapter(
                    id: "chapter-\(number)", title: "Chapter \(number)", number: number,
                    durationSeconds: 60, listenURL: "file:///missing-test-audio-\(number).wav"
                )
            }
        )
    }
}

final class ArtworkMetadataTests: XCTestCase {
    @MainActor
    func testCoverLoadingCanRetryAfterLeavingDetails() async throws {
        let gate = SuspendedFirstArtworkRequest(firstData: artworkPNG(width: 2), nextData: artworkPNG(width: 3))
        let loader = CoverImageLoader(dataLoader: { urls in await gate.load(urls) })
        let urls = [URL(string: "https://example.com/\(UUID()).png")!]
        let pending = Task { await loader.load(urls: urls) }
        try await waitForRequest(gate)
        XCTAssertTrue(loader.isLoading)
        loader.cancel()
        XCTAssertFalse(loader.isLoading)
        await loader.load(urls: urls)
        XCTAssertEqual(loader.image?.size.width, 3)
        await gate.finish()
        await pending.value
        XCTAssertEqual(loader.image?.size.width, 3, "The cancelled request must not overwrite a successful retry")
    }

    @MainActor
    func testAnOlderCoverRequestCannotReplaceTheNewBookCover() async throws {
        let gate = SuspendedFirstArtworkRequest(firstData: artworkPNG(width: 2), nextData: artworkPNG(width: 3))
        let loader = CoverImageLoader(dataLoader: { urls in await gate.load(urls) })
        let old = Task { await loader.load(urls: [URL(string: "https://example.com/old-\(UUID()).png")!]) }
        try await waitForRequest(gate)
        await loader.load(urls: [URL(string: "https://example.com/new-\(UUID()).png")!])
        XCTAssertEqual(loader.image?.size.width, 3)
        await gate.finish()
        await old.value
        XCTAssertEqual(loader.image?.size.width, 3)
    }

    @MainActor
    func testLockScreenMetadataContainsWolneArtworkAndCleanChapterTitle() async throws {
        let data = artworkPNG(width: 8)
        let controller = PlaybackController(artworkDataLoader: { _ in data })
        var book = wolneBook(id: "lalka", cover: "https://example.com/lalka.jpg")
        book.chapters.append(AudioBookChapter(id: "second", title: "Tom I, Rozdział II", number: 2))
        controller.play(book: book, startAt: 120, autoplay: false)
        try await waitForArtwork()
        let info = try XCTUnwrap(MPNowPlayingInfoCenter.default().nowPlayingInfo)
        XCTAssertEqual(info[MPMediaItemPropertyTitle] as? String, "Tom I, Rozdział I")
        XCTAssertEqual(info[MPMediaItemPropertyAlbumTitle] as? String, "Lalka")
        XCTAssertEqual(info[MPMediaItemPropertyArtist] as? String, "Bolesław Prus")
        let artwork = try XCTUnwrap(info[MPMediaItemPropertyArtwork] as? MPMediaItemArtwork)
        XCTAssertNotNil(artwork.image(at: CGSize(width: 4, height: 4)))
        let sharedArtwork = ImmutableTestArtwork(artwork: artwork)
        let backgroundSize = await Task.detached {
            sharedArtwork.artwork.image(at: CGSize(width: 4, height: 4))?.size
        }.value
        XCTAssertNotNil(backgroundSize, "MediaPlayer must be able to request artwork off MainActor")
        controller.seek(to: 150)
        XCTAssertNotNil(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork])
        controller.skipChapter(by: 1)
        XCTAssertNotNil(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork])
        XCTAssertEqual(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyTitle] as? String, "Tom I, Rozdział II")
    }

    @MainActor
    func testLateArtworkFromThePreviousBookCannotReplaceCurrentMetadata() async throws {
        let gate = SuspendedFirstArtworkRequest(firstData: artworkPNG(width: 2), nextData: artworkPNG(width: 3))
        let controller = PlaybackController(artworkDataLoader: { urls in await gate.load(urls) })
        controller.play(book: wolneBook(id: "old", cover: "https://example.com/old.jpg"), startAt: 0, autoplay: false)
        try await waitForRequest(gate)
        controller.play(book: wolneBook(id: "new", cover: "https://example.com/new.jpg"), startAt: 0, autoplay: false)
        try await waitForArtwork()
        await gate.finish()
        try await Task.sleep(for: .milliseconds(40))
        let artwork = try XCTUnwrap(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork] as? MPMediaItemArtwork)
        XCTAssertEqual(artwork.bounds.size.width, 3)
        XCTAssertEqual(controller.currentBook?.id, "new")
    }

    @MainActor
    func testHydratedMetadataUpdatesTheCurrentChapterAndLockScreenArtwork() async throws {
        let data = artworkPNG(width: 8)
        let controller = PlaybackController(artworkDataLoader: { _ in data })
        var book = wolneBook(id: "hydrate", cover: nil)
        controller.play(book: book, startAt: 120, autoplay: false)
        XCTAssertNil(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork])
        book.fullCoverImageURL = "https://example.com/hydrated.jpg"
        book.chapters[0].title = "Tom I, Rozdział I — corrected"
        controller.updateBookMetadata(book)
        try await waitForArtwork()
        XCTAssertEqual(controller.currentChapter?.title, "Tom I, Rozdział I — corrected")
        XCTAssertEqual(controller.currentTimeSeconds, 120)
        XCTAssertEqual(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyTitle] as? String, "Tom I, Rozdział I — corrected")
    }

    @MainActor
    private func wolneBook(id: String, cover: String?) -> AudioBook {
        AudioBook(id: id, title: "Lalka", author: "Bolesław Prus", source: .wolneLektury,
                  fullCoverImageURL: cover, chapters: [AudioBookChapter(
                    id: "first", title: "01. Bolesław Prus, Lalka, Lalka, tom pierwszy, Tom I, Rozdział I",
                    number: 1, durationSeconds: 600
                  )])
    }

    @MainActor
    private func artworkPNG(width: CGFloat) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: width), format: format).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: width))
        }.pngData()!
    }

    @MainActor
    private func waitForRequest(_ gate: SuspendedFirstArtworkRequest) async throws {
        for _ in 0..<100 {
            if await gate.isWaiting { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTFail("Artwork request did not start")
    }

    @MainActor
    private func waitForArtwork() async throws {
        for _ in 0..<100 {
            if MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyArtwork] != nil { return }
            try await Task.sleep(for: .milliseconds(10))
        }
        XCTFail("Now Playing artwork was not published")
    }
}

private actor SuspendedFirstArtworkRequest {
    let firstData: Data
    let nextData: Data
    private var requestCount = 0
    private var continuation: CheckedContinuation<Data?, Never>?
    var isWaiting: Bool { continuation != nil }

    init(firstData: Data, nextData: Data) {
        self.firstData = firstData
        self.nextData = nextData
    }

    func load(_ urls: [URL]) async -> Data? {
        requestCount += 1
        if requestCount == 1 {
            return await withCheckedContinuation { continuation = $0 }
        }
        return nextData
    }

    func finish() {
        continuation?.resume(returning: firstData)
        continuation = nil
    }
}

private struct ImmutableTestArtwork: @unchecked Sendable {
    let artwork: MPMediaItemArtwork
}
