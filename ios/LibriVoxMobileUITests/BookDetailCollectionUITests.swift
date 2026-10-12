import XCTest

final class BookDetailCollectionUITests: XCTestCase {
    private let redTitle = "The Scarlet Garden: An Unusually Long Journey Through the Seasons and the Stories We Remember"

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    @MainActor
    func testFirstBookKeepsCentralPagingButAllowsLeadingEdgeBack() {
        let app = launch()
        openLibraryBook("appearance-red", in: app)
        expectSelection(app, title: redTitle, position: "1 of 2")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)

        swipeToPrevious(in: app)
        expectSelection(app, title: redTitle, position: "1 of 2")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        save(app, "collection-central-first-book-boundary")

        let window = app.windows.firstMatch
        let frame = window.frame
        let origin = window.coordinate(withNormalizedOffset: .zero)
        origin.withOffset(CGVector(dx: 2, dy: frame.height * 0.44)).press(
            forDuration: 0.05,
            thenDragTo: origin.withOffset(CGVector(dx: frame.width * 0.75, dy: frame.height * 0.44))
        )
        XCTAssertTrue(app.descendants(matching: .any)["book-detail-collection"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.buttons["library-book-appearance-red"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        save(app, "collection-leading-edge-back-to-library")
    }

    @MainActor
    func testHomeSelectionStartsAtTappedBookAndPagingPreservesPlayback() {
        let app = launch()
        let mini = app.buttons["mini-player-open"]
        XCTAssertTrue(mini.waitForExistence(timeout: 5))
        let shelf = app.scrollViews["home-shelf-Featured Audiobooks"]
        let goldenBook = shelf.buttons["home-shelf-book-appearance-gold"]
        for _ in 0..<5 where !goldenBook.isHittable { scrollUp(in: app) }
        XCTAssertTrue(goldenBook.isHittable)
        goldenBook.tap()
        expectSelection(app, title: "The Golden Orchard", position: "2 of 5")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable, "The selected book must be the visible page")
        expectCard(app, bookID: "appearance-gold", state: "Compact")
        XCTAssertTrue(mini.isHittable)

        app.buttons["book-detail-cover"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["cover-artwork-image"].waitForExistence(timeout: 5))
        let closeArtwork = app.buttons["cover-artwork-close"]
        XCTAssertTrue(closeArtwork.isHittable)
        closeArtwork.tap()
        XCTAssertTrue(closeArtwork.waitForNonExistence(timeout: 4))
        expectSelection(app, title: "The Golden Orchard", position: "2 of 5")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)

        mini.tap()
        let playerTransport = app.buttons["now-playing-play-pause"]
        XCTAssertTrue(playerTransport.waitForExistence(timeout: 5))
        if playerTransport.label == "Play" { playerTransport.tap() }
        XCTAssertEqual(playerTransport.label, "Pause")
        let playingChapter = app.staticTexts["now-playing-chapter-title"].label
        app.buttons["now-playing-dismiss-grabber"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["now-playing-surface"].waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable, "Returning from Now Playing must keep the selected page visible")
        XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Pause")
        save(app, "collection-home-middle-selection")

        swipeToNext(in: app)
        expectSelection(app, title: "A Quiet Morning", position: "3 of 5")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        swipeToPrevious(in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 5")
        swipeToPrevious(in: app)
        expectSelection(app, title: redTitle, position: "1 of 5")
        XCTAssertFalse(app.buttons["book-detail-collection-previous"].isEnabled)
        XCTAssertTrue(app.buttons["book-detail-collection-next"].isEnabled)
        XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Pause")
        save(app, "collection-home-first-book-playing-mini-player")

        mini.tap()
        XCTAssertTrue(playerTransport.waitForExistence(timeout: 4))
        XCTAssertEqual(playerTransport.label, "Pause")
        XCTAssertEqual(app.staticTexts["now-playing-chapter-title"].label, playingChapter)
        XCTAssertEqual(app.buttons["now-playing-book-title"].label, redTitle)
        playerTransport.tap()
        app.buttons["now-playing-dismiss-grabber"].tap()
        expectSelection(app, title: redTitle, position: "1 of 5")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)

        // The native zoom's full-content back recognizer must not consume a
        // central paging gesture at the beginning of the selection.
        swipeToPrevious(in: app)
        expectSelection(app, title: redTitle, position: "1 of 5")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Play")
        save(app, "collection-normal-motion-first-book-boundary")

        let back = app.navigationBars.buttons.matching(
            NSPredicate(format: "label == 'Back' OR label == 'LibriVox'")
        ).firstMatch
        XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(app.descendants(matching: .any)["book-detail-collection"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(goldenBook.isHittable, "Back must return to the original shelf and preserve its scroll position")
        XCTAssertTrue(mini.isHittable)
        save(app, "collection-returned-to-original-featured-shelf")
    }

    @MainActor
    func testVerticalScrollContinuouslyScalesArtworkAndReversesBeforeTheTop() {
        let app = launch()
        openLibraryBook("appearance-gold", in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        let cover = app.buttons["book-detail-cover"]
        XCTAssertTrue(cover.isHittable)
        let compactCoverFrame = cover.frame
        let compactWidth = compactCoverFrame.width
        let miniPlayerFrame = app.buttons["mini-player-open"].frame
        save(app, "collection-zoom-start")

        // Stop each small drag before releasing it so inertia cannot skip the
        // intermediate positions. A binary compact/expanded animation cannot
        // satisfy both distinct intermediate widths and reverse at this point.
        scrollPrecisely(in: app, by: -50)
        let firstWidth = cover.frame.width
        XCTAssertGreaterThan(firstWidth, compactWidth + 0.5)
        save(app, "collection-zoom-first-intermediate")
        scrollPrecisely(in: app, by: -50)
        let secondWidth = cover.frame.width
        XCTAssertGreaterThan(secondWidth, firstWidth + 0.5)
        save(app, "collection-zoom-second-intermediate")
        scrollPrecisely(in: app, by: 24)
        let reversedWidth = cover.frame.width
        XCTAssertLessThan(reversedWidth, secondWidth - 0.5)
        XCTAssertGreaterThan(reversedWidth, compactWidth + 0.5)
        XCTAssertEqual(app.buttons["mini-player-open"].frame.minY, miniPlayerFrame.minY, accuracy: 1)
        XCTAssertTrue(app.tabBars.buttons["Library"].isHittable)
        save(app, "collection-zoom-reversed-intermediate")

        // The usual flick must still complete the expansion, and a downward
        // flick from the content must recover the collection card and cover.
        scrollUp(in: app)
        expectCard(app, bookID: "appearance-gold", state: "Expanded")
        for _ in 0..<6 where abs(cover.frame.width - compactWidth) > 1
            || abs(cover.frame.minY - compactCoverFrame.minY) > 2 {
            scrollDown(in: app)
        }
        expectCard(app, bookID: "appearance-gold", state: "Compact")
        XCTAssertTrue(cover.isHittable)
        XCTAssertEqual(cover.frame.width, compactWidth, accuracy: 1)
        XCTAssertEqual(cover.frame.minY, compactCoverFrame.minY, accuracy: 2)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        XCTAssertEqual(app.buttons["mini-player-open"].frame.minY, miniPlayerFrame.minY, accuracy: 1)
        XCTAssertTrue(app.buttons["book-detail-collection-previous"].isHittable)
        let back = app.navigationBars.buttons.matching(
            NSPredicate(format: "label == 'Back' OR label == 'Library'")
        ).firstMatch
        XCTAssertTrue(back.isHittable)
        save(app, "collection-zoom-after-inertial-return")

        cover.tap()
        let closeArtwork = app.buttons["cover-artwork-close"]
        XCTAssertTrue(closeArtwork.waitForExistence(timeout: 5))
        closeArtwork.tap()
        XCTAssertTrue(closeArtwork.waitForNonExistence(timeout: 4))
        XCTAssertEqual(cover.frame.minY, compactCoverFrame.minY, accuracy: 2)
        XCTAssertTrue(back.isHittable)
        app.buttons["mini-player-open"].tap()
        let dismissPlayer = app.buttons["now-playing-dismiss-grabber"]
        XCTAssertTrue(dismissPlayer.waitForExistence(timeout: 5))
        dismissPlayer.tap()
        XCTAssertTrue(app.descendants(matching: .any)["now-playing-surface"].waitForNonExistence(timeout: 4))
        XCTAssertEqual(cover.frame.minY, compactCoverFrame.minY, accuracy: 2)
        XCTAssertTrue(back.isHittable)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        save(app, "collection-zoom-after-cover-and-player-roundtrips")

        // Hold a deliberate top pull long enough for the simulator recording
        // to inspect the source heading behind the card. XCTest itself waits
        // for release and the native spring to settle before it can snapshot.
        scrollPrecisely(in: app, by: 160, hold: 1.2)
        XCTAssertEqual(cover.frame.minY, compactCoverFrame.minY, accuracy: 2)
        XCTAssertEqual(cover.frame.width, compactWidth, accuracy: 1)
        XCTAssertTrue(back.isHittable)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        save(app, "collection-zoom-settled-after-held-top-pull")
    }

    @MainActor
    func testVerticalExpansionRetainsEachBooksScrollPosition() {
        let app = launch()
        openLibraryBook("appearance-gold", in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        expectCard(app, bookID: "appearance-gold", state: "Compact")
        let firstChapter = app.buttons["book-detail-chapter-gold-1"]
        scrollUp(in: app)
        expectCard(app, bookID: "appearance-gold", state: "Expanded")
        for _ in 0..<4 where !firstChapter.isHittable { scrollUp(in: app) }
        XCTAssertTrue(firstChapter.isHittable)
        let chapterY = firstChapter.frame.minY
        save(app, "collection-expanded-chapters")

        // The author shelf has its own horizontal scrolling. Page the book at
        // the visible chapter row, outside that nested horizontal surface.
        swipeToPrevious(in: app, atScreenY: firstChapter.frame.midY)
        expectSelection(app, title: redTitle, position: "1 of 2")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        expectCard(app, bookID: "appearance-red", state: "Compact")
        swipeToNext(in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        expectCard(app, bookID: "appearance-gold", state: "Expanded")
        XCTAssertTrue(firstChapter.isHittable, "Paging away and back must preserve the book's vertical position")
        XCTAssertEqual(firstChapter.frame.minY, chapterY, accuracy: 8)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)

        for _ in 0..<6 where card(app, bookID: "appearance-gold").value as? String != "Compact" {
            scrollDown(in: app)
        }
        expectCard(app, bookID: "appearance-gold", state: "Compact")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        XCTAssertTrue(app.tabBars.buttons["Library"].isHittable)
        save(app, "collection-returned-to-compact-card")
    }

    @MainActor
    func testReducedMotionControlsRespectCollectionBoundariesAndRotation() {
        let app = launch(extra: ["-animationSpeed", "reduced"])
        openLibraryBook("appearance-gold", in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        XCTAssertTrue(app.buttons["book-detail-cover"].isHittable)
        let previous = app.buttons["book-detail-collection-previous"]
        let next = app.buttons["book-detail-collection-next"]
        XCTAssertTrue(previous.isEnabled)
        XCTAssertFalse(next.isEnabled)

        swipeToNext(in: app)
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        previous.tap()
        expectSelection(app, title: redTitle, position: "1 of 2")
        XCTAssertFalse(previous.isEnabled)
        XCTAssertTrue(next.isEnabled)
        swipeToPrevious(in: app)
        expectSelection(app, title: redTitle, position: "1 of 2")

        let expand = app.buttons["book-detail-collection-expand"]
        XCTAssertTrue(expand.isHittable)
        expand.tap()
        expectCard(app, bookID: "appearance-red", state: "Expanded")
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        expectSelection(app, title: redTitle, position: "1 of 2")
        XCTAssertTrue(next.isHittable)
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        save(app, "collection-reduced-motion-landscape")
        next.tap()
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        XCTAssertFalse(next.isEnabled)
        XCUIDevice.shared.orientation = .portrait
        expectSelection(app, title: "The Golden Orchard", position: "2 of 2")
        save(app, "collection-reduced-motion-last-book")

        let back = app.navigationBars.buttons.matching(
            NSPredicate(format: "label == 'Back' OR label == 'Library'")
        ).firstMatch
        XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(app.buttons["library-book-appearance-gold"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        XCTAssertFalse(app.descendants(matching: .any)["book-detail-collection"].exists)
    }

    @MainActor
    private func launch(extra: [String] = []) -> XCUIApplication {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = [
            "-hasCompletedOnboarding", "YES", "-selectedTab", "home",
            "-catalogFixture", "appearance", "-catalogQuery", "",
            "-themeMode", "dark", "-artworkColors", "YES",
            "-appearanceLongBook", "NO",
        ] + extra
        app.launch()
        return app
    }

    @MainActor
    private func openLibraryBook(_ id: String, in app: XCUIApplication) {
        let library = app.tabBars.buttons["Library"]
        XCTAssertTrue(library.waitForExistence(timeout: 5))
        library.tap()
        let book = app.buttons["library-book-\(id)"]
        XCTAssertTrue(book.waitForExistence(timeout: 5))
        XCTAssertTrue(book.isHittable)
        book.tap()
    }

    @MainActor
    private func expectSelection(_ app: XCUIApplication, title: String, position: String) {
        let collection = app.descendants(matching: .any)["book-detail-collection"].firstMatch
        XCTAssertTrue(collection.waitForExistence(timeout: 5))
        expectValue(collection, position)
        let selectedTitle = app.staticTexts["book-detail-title"]
        XCTAssertTrue(selectedTitle.waitForExistence(timeout: 4))
        XCTAssertEqual(selectedTitle.label, title)
    }

    @MainActor
    private func card(_ app: XCUIApplication, bookID: String) -> XCUIElement {
        app.descendants(matching: .any)["book-detail-collection-card-\(bookID)"].firstMatch
    }

    @MainActor
    private func expectCard(_ app: XCUIApplication, bookID: String, state: String) {
        let selectedCard = card(app, bookID: bookID)
        XCTAssertTrue(selectedCard.waitForExistence(timeout: 4))
        expectValue(selectedCard, state)
    }

    @MainActor
    private func expectValue(_ element: XCUIElement, _ value: String) {
        let expected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", value), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [expected], timeout: 4), .completed)
    }

    @MainActor
    private func swipeToNext(in app: XCUIApplication) {
        drag(in: app, from: CGVector(dx: 0.80, dy: 0.44), to: CGVector(dx: 0.20, dy: 0.44))
    }

    @MainActor
    private func swipeToPrevious(in app: XCUIApplication, atScreenY: CGFloat? = nil) {
        drag(in: app, from: CGVector(dx: 0.20, dy: 0.44), to: CGVector(dx: 0.80, dy: 0.44), atScreenY: atScreenY)
    }

    @MainActor
    private func scrollUp(in app: XCUIApplication) {
        drag(in: app, from: CGVector(dx: 0.52, dy: 0.73), to: CGVector(dx: 0.52, dy: 0.30))
    }

    @MainActor
    private func scrollDown(in app: XCUIApplication) {
        drag(in: app, from: CGVector(dx: 0.52, dy: 0.31), to: CGVector(dx: 0.52, dy: 0.74))
    }

    @MainActor
    private func scrollPrecisely(in app: XCUIApplication, by distance: CGFloat, hold: TimeInterval = 0.3) {
        let window = app.windows.firstMatch
        let frame = window.frame
        let origin = window.coordinate(withNormalizedOffset: .zero)
        let start = CGVector(dx: frame.width * 0.50, dy: frame.height * 0.48)
        origin.withOffset(start).press(
            forDuration: 0.05,
            thenDragTo: origin.withOffset(CGVector(dx: start.dx, dy: start.dy + distance)),
            withVelocity: .slow,
            thenHoldForDuration: hold
        )
    }

    @MainActor
    private func drag(in app: XCUIApplication, from: CGVector, to: CGVector, atScreenY: CGFloat? = nil) {
        let collection = app.descendants(matching: .any)["book-detail-collection"].firstMatch
        let window = app.windows.firstMatch
        let windowFrame = window.frame
        let collectionFrame = collection.exists ? collection.frame : windowFrame
        // A lazy horizontal stack's accessibility frame can include offscreen pages.
        // Keep both gesture points inside the viewport seen by the user.
        let viewport = collectionFrame.intersection(windowFrame)
        XCTAssertFalse(viewport.isNull)
        XCTAssertGreaterThan(viewport.width, 0)
        XCTAssertGreaterThan(viewport.height, 0)
        if let atScreenY {
            XCTAssertGreaterThan(atScreenY, viewport.minY)
            XCTAssertLessThan(atScreenY, viewport.maxY)
        }
        let start = CGVector(
            dx: viewport.minX - windowFrame.minX + viewport.width * from.dx,
            dy: (atScreenY ?? (viewport.minY + viewport.height * from.dy)) - windowFrame.minY
        )
        let finish = CGVector(
            dx: viewport.minX - windowFrame.minX + viewport.width * to.dx,
            dy: (atScreenY ?? (viewport.minY + viewport.height * to.dy)) - windowFrame.minY
        )
        let diagnostic = "Collection drag: collection=\(collectionFrame), window=\(windowFrame), viewport=\(viewport), start=\(start), finish=\(finish)"
        print(diagnostic)
        let attachment = XCTAttachment(string: diagnostic)
        attachment.name = "Collection gesture viewport"
        attachment.lifetime = .keepAlways
        add(attachment)
        let origin = window.coordinate(withNormalizedOffset: .zero)
        origin.withOffset(start).press(
            forDuration: 0.05,
            thenDragTo: origin.withOffset(finish)
        )
    }

    @MainActor
    private func save(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
