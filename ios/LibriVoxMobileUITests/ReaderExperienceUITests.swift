import XCTest

final class ReaderExperienceUITests: XCTestCase {
    @MainActor
    func testLiveWolneLalkaLoadsProseWithOneReaderTransport() {
        let app = XCUIApplication()
        app.launchArguments = [
            "-hasCompletedOnboarding", "YES", "-selectedTab", "search",
            "-catalogFixture", "wolne-metadata", "-catalogQuery", "Lalka",
            "-themeMode", "dark"
        ]
        app.launch()

        let result = app.buttons["search-result-wolneLektury:wolnelektury-lalka"]
        XCTAssertTrue(result.waitForExistence(timeout: 8))
        result.tap()
        let cover = app.descendants(matching: .any)["book-detail-cover"].firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 6))
        let hydrated = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == 'Cover art loaded'"), object: cover
        )
        XCTAssertEqual(XCTWaiter.wait(for: [hydrated], timeout: 60), .completed)

        let read = app.buttons["book-detail-read"]
        for _ in 0..<8 where !read.isHittable { app.swipeUp() }
        XCTAssertTrue(read.isHittable)
        read.tap()
        XCTAssertTrue(app.buttons["reader-play-pause"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["mini-player-open"].exists)
        XCTAssertFalse(app.buttons["mini-player-play-pause"].exists)
        XCTAssertEqual(app.buttons.matching(identifier: "reader-play-pause").count, 1)
        assertNoEmptyAccessory(app)

        let syncQuality = app.descendants(matching: .any)["reader-sync-quality"].firstMatch
        let estimatedSync = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "label CONTAINS 'Estimated sync'"), object: syncQuality
        )
        XCTAssertEqual(XCTWaiter.wait(for: [estimatedSync], timeout: 60), .completed)

        // This paragraph comes from Lalka's EPUB, not its chapter headings or description.
        let prose = app.descendants(matching: .any).matching(NSPredicate(
            format: "identifier BEGINSWITH 'reader-segment-' AND (label CONTAINS %@ OR value CONTAINS %@)",
            "W początkach roku 1878", "W początkach roku 1878"
        )).firstMatch
        for _ in 0..<4 where !prose.isHittable { app.swipeUp() }
        XCTAssertTrue(prose.waitForExistence(timeout: 5))
        XCTAssertTrue(prose.isHittable)
        XCTAssertFalse(app.staticTexts["Text unavailable"].exists)
        XCTAssertFalse(app.buttons["mini-player-open"].exists)
        save(app, "live-lalka-reader-prose-estimated-sync-single-transport")

        app.navigationBars.buttons["BackButton"].tap()
        XCTAssertTrue(app.buttons["mini-player-open"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["reader-play-pause"].exists)
    }

    @MainActor
    func testReaderHasOneTransportFollowsPlaybackAndRestoresMiniPlayer() {
        let app = XCUIApplication()
        app.launchArguments = [
            "-hasCompletedOnboarding", "YES", "-catalogFixture", "appearance",
            "-catalogQuery", "Scarlet", "-themeMode", "dark", "-readerFixture", "YES"
        ]
        app.launch()
        let result = app.buttons["search-result-libriVox:appearance-red"]
        XCTAssertTrue(result.waitForExistence(timeout: 8)); result.tap()
        let read = app.buttons["book-detail-read"]
        for _ in 0..<8 where !read.isHittable { app.swipeUp() }
        XCTAssertTrue(read.isHittable); read.tap()

        let transport = app.buttons["reader-play-pause"]
        XCTAssertTrue(transport.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["mini-player-open"].exists)
        XCTAssertFalse(app.buttons["mini-player-play-pause"].exists)
        assertNoEmptyAccessory(app)
        XCTAssertTrue(app.descendants(matching: .any)["reader-sync-quality"].exists)
        transport.tap()
        expect(transport, key: "label", value: "Pause audio")

        let firstText = app.descendants(matching: .any)["reader-segment-red-1-reader-0"].firstMatch
        let secondText = app.descendants(matching: .any)["reader-segment-red-1-reader-1"].firstMatch
        let thirdText = app.descendants(matching: .any)["reader-segment-red-1-reader-2"].firstMatch
        let currentText = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'reader-segment-' AND value == 'Current text'")).firstMatch
        XCTAssertTrue(currentText.waitForExistence(timeout: 5))
        XCTAssertTrue(firstText.exists || secondText.exists)
        app.buttons["Forward 10 seconds"].tap()
        app.buttons["Forward 10 seconds"].tap()
        XCTAssertTrue(thirdText.waitForExistence(timeout: 5))
        expect(thirdText, key: "value", value: "Current text")
        transport.tap()
        expect(transport, key: "label", value: "Play audio")
        save(app, "reader-single-transport-following-text")

        app.navigationBars.buttons["BackButton"].tap()
        XCTAssertTrue(app.buttons["mini-player-open"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Play")
        XCTAssertFalse(app.buttons["reader-play-pause"].exists)
    }

    @MainActor
    private func assertNoEmptyAccessory(_ app: XCUIApplication) {
        let transport = app.buttons["reader-play-pause"]
        let home = app.tabBars.buttons["Home"]
        XCTAssertTrue(home.exists)
        XCTAssertLessThan(
            home.frame.minY - transport.frame.maxY, 45,
            "Reader controls should sit directly above tabs, with no empty mini-player capsule"
        )
    }

    @MainActor
    private func expect(_ element: XCUIElement, key: String, value: String) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate(format: "%K == %@", key, value), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: 5), .completed)
    }

    @MainActor
    private func save(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
