import XCTest

final class ArtworkAppearanceUITests: XCTestCase {
    @MainActor
    func testFullScreenCoverZoomsRotatesAndReturnsToDetailsAndPlayer() {
        let app = launch(query: "Scarlet", theme: "dark")
        let result = app.buttons["search-result-libriVox:appearance-red"]
        XCTAssertTrue(result.waitForExistence(timeout: 6)); result.tap()
        let detailCover = app.buttons["book-detail-cover"]
        XCTAssertTrue(detailCover.waitForExistence(timeout: 5)); detailCover.tap()
        let artwork = app.descendants(matching: .any)["cover-artwork-image"]
        let close = app.buttons["cover-artwork-close"]
        XCTAssertTrue(artwork.waitForExistence(timeout: 5))
        XCTAssertEqual(artwork.value as? String, "Zoom 100 percent")
        artwork.doubleTap()
        let zoomed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value != 'Zoom 100 percent'"), object: artwork)
        XCTAssertEqual(XCTWaiter.wait(for: [zoomed], timeout: 3), .completed)
        artwork.pinch(withScale: 1.3, velocity: 1)
        save(app, "full-screen-cover-zoomed")
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(close.isHittable)
        XCTAssertTrue(artwork.exists)
        XCUIDevice.shared.orientation = .portrait
        artwork.doubleTap()
        let fitted = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == 'Zoom 100 percent'"), object: artwork)
        XCTAssertEqual(XCTWaiter.wait(for: [fitted], timeout: 3), .completed)
        save(app, "full-screen-cover-fitted")
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))
        XCTAssertTrue(detailCover.isHittable)

        app.buttons["book-detail-play"].tap()
        let playerCover = app.buttons["now-playing-cover"]
        XCTAssertTrue(playerCover.waitForExistence(timeout: 5))
        let chapterTitle = app.staticTexts["now-playing-chapter-title"].label
        playerCover.tap()
        XCTAssertTrue(artwork.waitForExistence(timeout: 5))
        XCTAssertEqual(artwork.value as? String, "Zoom 100 percent")
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))
        XCTAssertTrue(playerCover.isHittable)
        XCTAssertEqual(app.staticTexts["now-playing-chapter-title"].label, chapterTitle)
        XCTAssertEqual(app.buttons["now-playing-play-pause"].label, "Pause")
    }

    @MainActor
    func testMissingFullScreenCoverRemainsDismissibleWithReducedMotion() {
        let app = launch(query: "Without Artwork", theme: "light", extra: ["-animationSpeed", "reduced"])
        let result = app.buttons["search-result-libriVox:appearance-missing"]
        XCTAssertTrue(result.waitForExistence(timeout: 6)); result.tap()
        let cover = app.buttons["book-detail-cover"]
        XCTAssertTrue(cover.waitForExistence(timeout: 5)); cover.tap()
        XCTAssertTrue(app.staticTexts["Cover unavailable"].waitForExistence(timeout: 5))
        save(app, "full-screen-cover-unavailable")
        app.buttons["cover-artwork-close"].tap()
        XCTAssertTrue(cover.waitForExistence(timeout: 5))
        XCTAssertTrue(cover.isHittable)
    }

    @MainActor
    func testChapterControlsTrackPlaybackAcrossDetailsMiniPlayerAndChapterPanel() {
        let app = launch(query: "Scarlet", theme: "dark")
        let result = app.buttons["search-result-libriVox:appearance-red"]
        XCTAssertTrue(result.waitForExistence(timeout: 6)); result.tap()
        let first = app.buttons["book-detail-chapter-play-pause-red-1"]
        let second = app.buttons["book-detail-chapter-play-pause-red-2"]
        let surface = app.descendants(matching: .any)["now-playing-surface"]
        let miniTransport = app.buttons["mini-player-play-pause"]
        let playerTransport = app.buttons["now-playing-play-pause"]

        func expectState(_ button: XCUIElement, _ state: String, action: String) {
            XCTAssertTrue(button.waitForExistence(timeout: 3))
            let updated = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "value == %@", state), object: button
            )
            XCTAssertEqual(XCTWaiter.wait(for: [updated], timeout: 3), .completed)
            XCTAssertTrue(
                button.label.hasPrefix(action + " ") || button.label.hasPrefix(action + ", "),
                button.label
            )
        }

        for _ in 0..<8 where !second.isHittable { app.swipeUp(velocity: .fast) }
        XCTAssertTrue(first.isHittable)
        XCTAssertTrue(second.isHittable)
        // The fixture has saved progress, but selecting it first creates the live player item.
        first.tap()
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        XCTAssertEqual(playerTransport.label, "Pause")
        playerTransport.tap()
        app.buttons["now-playing-dismiss-grabber"].tap()
        XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
        expectState(first, "Paused", action: "Play")
        expectState(second, "Not playing", action: "Play")

        first.tap()
        expectState(first, "Playing", action: "Pause")
        XCTAssertFalse(surface.exists, "An active chapter's control must toggle in place")
        XCTAssertEqual(miniTransport.label, "Pause")
        save(app, "chapter-detail-active-pause-control")

        miniTransport.tap()
        expectState(first, "Paused", action: "Play")
        XCTAssertEqual(miniTransport.label, "Play")
        save(app, "chapter-detail-paused-play-control")
        first.tap()
        expectState(first, "Playing", action: "Pause")
        first.tap()
        expectState(first, "Paused", action: "Play")
        XCTAssertFalse(surface.exists)
        XCTAssertEqual(miniTransport.label, "Play")

        // Opening the active chapter by its title must preserve its paused state.
        app.buttons["book-detail-chapter-red-1"].tap()
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        XCTAssertEqual(playerTransport.label, "Play")
        app.buttons["now-playing-dismiss-grabber"].tap()
        XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
        expectState(first, "Paused", action: "Play")

        // Selecting a different chapter keeps the native transition and moves the active marker.
        second.tap()
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["now-playing-chapter-title"].label, "02: The next chapter")
        app.buttons["now-playing-dismiss-grabber"].tap()
        XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
        expectState(first, "Not playing", action: "Play")
        expectState(second, "Playing", action: "Pause")
        miniTransport.tap()
        expectState(second, "Paused", action: "Play")
        second.tap()
        expectState(second, "Playing", action: "Pause")
        XCTAssertFalse(surface.exists)

        app.buttons["mini-player-open"].tap()
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        app.buttons["Chapters"].tap()
        let panelFirst = app.buttons["now-playing-chapter-red-1"]
        let panelSecond = app.buttons["now-playing-chapter-red-2"]
        expectState(panelFirst, "Not playing", action: "Play")
        expectState(panelSecond, "Playing", action: "Pause")
        panelSecond.tap()
        expectState(panelSecond, "Paused", action: "Play")
        XCTAssertTrue(app.buttons["Done"].isHittable)
        panelSecond.tap()
        expectState(panelSecond, "Playing", action: "Pause")
        panelFirst.tap()
        expectState(panelFirst, "Playing", action: "Pause")
        expectState(panelSecond, "Not playing", action: "Play")
        app.buttons["Done"].tap()
        XCTAssertTrue(app.staticTexts["now-playing-chapter-title"].label.hasPrefix("01:"))
        XCTAssertEqual(playerTransport.label, "Pause")
        playerTransport.tap()
        app.buttons["Chapters"].tap()
        expectState(panelFirst, "Paused", action: "Play")
    }

    @MainActor
    func testNativePlayerAndChapterTransitionsRemainResponsive() {
        let app = launch(query: "Scarlet", theme: "dark")
        let result = app.buttons["search-result-libriVox:appearance-red"]
        XCTAssertTrue(result.waitForExistence(timeout: 6)); result.tap()
        let secondChapter = app.buttons["book-detail-chapter-red-2"]
        for _ in 0..<8 where !secondChapter.isHittable { app.swipeUp(velocity: .fast) }
        XCTAssertTrue(secondChapter.isHittable); secondChapter.tap()
        let surface = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["now-playing-chapter-title"].label, "02: The next chapter")
        app.buttons["now-playing-dismiss-grabber"].tap()
        XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
        XCTAssertTrue(secondChapter.isHittable)

        let mini = app.buttons["mini-player-open"]
        let options = XCTMeasureOptions()
        options.iterationCount = 3
        measure(metrics: [XCTCPUMetric(application: app)], options: options) {
            mini.tap()
            XCTAssertTrue(surface.waitForExistence(timeout: 3))
            app.buttons["Chapters"].tap()
            XCTAssertTrue(app.buttons["now-playing-chapter-red-2"].waitForExistence(timeout: 3))
            app.buttons["Done"].tap()
            app.buttons["now-playing-dismiss-grabber"].tap()
            XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
            XCTAssertTrue(mini.isHittable)
        }
        mini.tap()
        XCTAssertTrue(surface.waitForExistence(timeout: 3))
        // The native zoom's cancellation must restore the same player and controls.
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.10))
        top.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.14)))
        XCTAssertTrue(surface.exists)
        XCTAssertTrue(app.buttons["now-playing-play-pause"].isHittable)
        save(app, "native-player-after-cancelled-dismissal")

        // Leaving from a nested panel must dismiss the entire player presentation.
        app.buttons["Details"].tap()
        let fullDetails = app.buttons["Show full book details"]
        XCTAssertTrue(fullDetails.waitForExistence(timeout: 3))
        for _ in 0..<5 where !fullDetails.isHittable {
            let bottom = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
            bottom.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)))
        }
        XCTAssertTrue(fullDetails.isHittable); fullDetails.tap()
        XCTAssertTrue(surface.waitForNonExistence(timeout: 3))
        XCTAssertTrue(app.staticTexts["book-detail-title"].isHittable)
        XCTAssertTrue(mini.isHittable)
    }

    @MainActor
    func testTenSecondSeekingKeepsTransportStateWhilePlayingAndPaused() {
        let app = launch(query: "", theme: "dark", tab: "home")
        let mini = app.buttons["mini-player-open"]
        XCTAssertTrue(mini.waitForExistence(timeout: 5)); mini.tap()
        let transport = app.buttons["now-playing-play-pause"]
        XCTAssertTrue(transport.waitForExistence(timeout: 5))
        XCTAssertEqual(transport.label, "Play")
        transport.tap()
        let chapter = app.staticTexts["now-playing-chapter-title"].label

        for expected in ["Pause", "Play"] {
            XCTAssertEqual(transport.label, expected)
            for _ in 0..<3 {
                app.buttons["Forward 10 seconds"].tap()
                XCTAssertEqual(transport.label, expected)
                XCTAssertTrue(transport.isEnabled)
                app.buttons["Rewind 10 seconds"].tap()
                XCTAssertEqual(transport.label, expected)
                XCTAssertEqual(app.staticTexts["now-playing-chapter-title"].label, chapter)
            }
            save(app, "ten-second-seeking-\(expected.lowercased())-control")
            app.buttons["now-playing-dismiss-grabber"].tap()
            XCTAssertTrue(mini.waitForExistence(timeout: 5))
            XCTAssertEqual(app.buttons["mini-player-play-pause"].label, expected)
            mini.tap()
            XCTAssertEqual(transport.label, expected)
            if expected == "Pause" { transport.tap() }
        }
    }

    @MainActor
    func testPlayerLinksKeepMiniPlayerAndLiveTransportInEveryTab() {
        for tab in ["Home", "Library", "Search"] {
            let app = launch(query: "Golden", theme: tab == "Library" ? "light" : "dark", tab: "home")
            // A seeded query opens native Search, which collapses the other tabs.
            // Its Home control first restores the bar; select the destination after that.
            let libraryTab = app.tabBars.buttons["Library"]
            if !libraryTab.isHittable {
                let restoreTabs = app.tabBars.buttons["Home"]
                XCTAssertTrue(restoreTabs.waitForExistence(timeout: 5))
                restoreTabs.tap()
                let expanded = XCTNSPredicateExpectation(
                    predicate: NSPredicate(format: "isHittable == true"), object: libraryTab
                )
                XCTAssertEqual(XCTWaiter.wait(for: [expanded], timeout: 3), .completed)
            }
            let destinationTab = app.tabBars.buttons[tab]
            XCTAssertTrue(destinationTab.isHittable)
            destinationTab.tap()
            let mini = app.buttons["mini-player-open"]
            XCTAssertTrue(mini.waitForExistence(timeout: 5)); mini.tap()
            let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
            let transport = nowPlaying.buttons["Play"]
            XCTAssertTrue(transport.waitForExistence(timeout: 5)); transport.tap()
            XCTAssertTrue(nowPlaying.buttons["Pause"].waitForExistence(timeout: 5))
            nowPlaying.buttons["Next chapter"].tap()
            let chapterTitle = app.staticTexts["now-playing-chapter-title"]
            XCTAssertEqual(chapterTitle.label, "02: The next chapter")

            for shortcut in ["now-playing-book-title"] {
                let link = app.buttons[shortcut]
                XCTAssertTrue(link.waitForExistence(timeout: 5)); link.tap()
                XCTAssertTrue(app.staticTexts["book-detail-title"].waitForExistence(timeout: 5))
                XCTAssertTrue(mini.isHittable)
                let detailTransport = app.buttons["book-detail-play"]
                for _ in 0..<4 where !detailTransport.isHittable { app.swipeUp() }
                XCTAssertEqual(detailTransport.label, "Pause")
                detailTransport.tap()
                XCTAssertEqual(detailTransport.label, "Resume")
                XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Play")
                detailTransport.tap()
                XCTAssertEqual(detailTransport.label, "Pause")
                XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Pause")
                save(app, "\(tab.lowercased())-player-link-book-with-mini-player")
                mini.tap()
                XCTAssertEqual(chapterTitle.label, "02: The next chapter")
            }

            let author = app.buttons["now-playing-author"]
            XCTAssertTrue(author.waitForExistence(timeout: 5)); author.tap()
            XCTAssertTrue(app.staticTexts["author-profile-name"].waitForExistence(timeout: 5))
            XCTAssertTrue(mini.isHittable)
            XCTAssertEqual(app.buttons["mini-player-play-pause"].label, "Pause")
            save(app, "\(tab.lowercased())-player-link-author-with-mini-player")
            mini.tap()
            XCTAssertTrue(nowPlaying.buttons["Pause"].waitForExistence(timeout: 5))
            app.buttons["now-playing-dismiss-grabber"].tap()
            XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))
            XCTAssertTrue(app.staticTexts["author-profile-name"].waitForExistence(timeout: 5))
            app.navigationBars.firstMatch.buttons.firstMatch.tap()
            XCTAssertTrue(app.staticTexts["book-detail-title"].waitForExistence(timeout: 5))
            app.terminate()
        }
    }

    @MainActor
    func testLongBookDetailScrollingDuringPlayback() {
        let app = launch(query: "Golden", theme: "dark", extra: ["-appearanceLongBook", "YES"])
        let result = app.buttons["search-result-wolneLektury:appearance-gold"]
        XCTAssertTrue(result.waitForExistence(timeout: 8)); result.tap()
        let play = app.buttons["book-detail-play"]
        XCTAssertTrue(play.waitForExistence(timeout: 5)); play.tap()
        let dismiss = app.buttons["now-playing-dismiss-grabber"]
        XCTAssertTrue(dismiss.waitForExistence(timeout: 5)); dismiss.tap()
        XCTAssertTrue(app.descendants(matching: .any)["now-playing-surface"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["book-detail-title"].waitForExistence(timeout: 5))
        save(app, "long-book-detail-at-top")
        let options = XCTMeasureOptions()
        options.iterationCount = 3
        measure(metrics: [XCTCPUMetric(application: app), XCTOSSignpostMetric.scrollingAndDecelerationMetric], options: options) {
            app.swipeUp(velocity: .fast)
            app.swipeUp(velocity: .fast)
            app.swipeDown(velocity: .fast)
            app.swipeDown(velocity: .fast)
        }
        for _ in 0..<4 { app.swipeUp() }
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'book-detail-chapter-gold-'")).firstMatch.exists)
        save(app, "long-book-detail-scrolled-to-chapters")
        app.navigationBars.firstMatch.buttons.firstMatch.tap()
        XCTAssertTrue(result.waitForExistence(timeout: 5))
    }

    @MainActor
    func testProductionHomeRetainsNativeNavigation() {
        // No fixtures or preference overrides: safe to run against the connected phone's library.
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["Home"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.tabBars.buttons["Search"].exists)
        save(app, "production-home-native-navigation")
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.navigationBars["Library"].waitForExistence(timeout: 5))
        save(app, "production-library-native-navigation")
        let miniPlayer = app.buttons["mini-player-open"]
        if miniPlayer.exists {
            miniPlayer.tap()
            XCTAssertTrue(app.staticTexts["now-playing-chapter-title"].waitForExistence(timeout: 5))
            save(app, "production-cover-colored-player")
            app.buttons["now-playing-book-title"].tap()
            XCTAssertTrue(app.descendants(matching: .any)["book-detail-cover"].waitForExistence(timeout: 5))
            save(app, "production-cover-colored-book-details")
            miniPlayer.tap()
            app.buttons["now-playing-dismiss-grabber"].tap()
        }
        app.tabBars.buttons["Home"].tap()
    }

    @MainActor
    func testDetailHeroesAcrossCoverAndAppearanceVariants() {
        let samples = [("red", "Scarlet"), ("gold", "Golden"), ("pale", "Quiet"), ("dark", "Midnight"), ("missing", "Without Artwork")]
        for theme in ["light", "dark"] {
            for (key, query) in samples {
                let app = launch(query: query, theme: theme)
                let result = app.buttons["search-result-libriVox:appearance-\(key)"]
                XCTAssertTrue(result.waitForExistence(timeout: 6))
                result.tap()
                let cover = app.descendants(matching: .any)["book-detail-cover"]
                XCTAssertTrue(cover.waitForExistence(timeout: 5))
                if key != "missing" {
                    let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == 'Cover art loaded'"), object: cover)
                    wait(for: [loaded], timeout: 5)
                }
                XCTAssertTrue(app.buttons["book-detail-play"].exists)
                save(app, "\(theme)-\(key)-cover-hero")
                app.terminate()
            }
        }
    }

    @MainActor
    func testPlayerTitlesSurviveChapterChangesBackgroundingAndDismissal() {
        let app = launch(query: "Scarlet", theme: "dark")
        let result = app.buttons["search-result-libriVox:appearance-red"]
        XCTAssertTrue(result.waitForExistence(timeout: 6)); result.tap()
        let play = app.buttons["book-detail-play"]
        for _ in 0..<3 where !play.isHittable { app.swipeUp() }
        XCTAssertTrue(play.waitForExistence(timeout: 5)); play.tap()
        let chapter = app.staticTexts["now-playing-chapter-title"]
        XCTAssertTrue(chapter.waitForExistence(timeout: 5))
        XCTAssertTrue(chapter.label.contains("The opening chapter with a wonderfully long descriptive title"))
        XCTAssertTrue(app.buttons["now-playing-book-title"].exists || app.staticTexts["now-playing-book-title"].exists)
        save(app, "dark-red-player-long-titles")
        app.buttons["Chapters"].tap()
        let next = app.buttons["now-playing-chapter-red-2"]
        XCTAssertTrue(next.waitForExistence(timeout: 4)); next.tap()
        app.buttons["Done"].tap()
        XCTAssertTrue(chapter.waitForExistence(timeout: 4))
        XCTAssertEqual(chapter.label, "02: The next chapter")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(chapter.waitForExistence(timeout: 5))
        XCTAssertEqual(chapter.label, "02: The next chapter")
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.buttons["Chapters"].exists)
        XCUIDevice.shared.orientation = .portrait
        save(app, "player-after-chapter-change-and-resume")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        start.press(forDuration: 0.1, thenDragTo: end)
        XCTAssertTrue(app.buttons["mini-player-open"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["book-detail-play"].exists)
    }

    @MainActor
    func testArtworkAppearanceWithAccessibilityAndReducedMotion() {
        let app = launch(query: "", theme: "dark", tab: "home", extra: [
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
            "-animationSpeed", "reduced",
        ])
        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 6)); miniPlayer.tap()
        let coverShortcut = app.buttons["now-playing-book-title"]
        XCTAssertTrue(coverShortcut.waitForExistence(timeout: 5)); coverShortcut.tap()
        XCTAssertTrue(app.descendants(matching: .any)["book-detail-cover"].waitForExistence(timeout: 5))
        save(app, "accessible-cover-hero")
        let play = app.buttons["book-detail-play"]
        for _ in 0..<20 where !play.isHittable { app.swipeUp() }
        save(app, "accessible-cover-hero-actions")
        XCTAssertTrue(play.isHittable); play.tap()
        if !app.staticTexts["now-playing-chapter-title"].exists { miniPlayer.tap() }
        let chapter = app.staticTexts["now-playing-chapter-title"]
        XCTAssertTrue(chapter.waitForExistence(timeout: 5))
        XCTAssertTrue(chapter.label.contains("stories we remember"))
        save(app, "accessible-player-static-full-title")
        for _ in 0..<20 where !app.buttons["Chapters"].isHittable { app.swipeUp() }
        XCTAssertTrue(app.buttons["Chapters"].isHittable)
        save(app, "accessible-player-transport-and-chapters")
    }

    @MainActor
    func testArtworkSettingRemainsDisabledAfterRelaunch() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-catalogFixture", "appearance"]
        app.launch()
        openAppearance(app)
        let colors = app.switches["Artwork color scheme"]
        XCTAssertTrue(colors.waitForExistence(timeout: 4))
        if colors.value as? String == "1" {
            colors.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        }
        XCTAssertEqual(colors.value as? String, "0")
        app.terminate(); app.launch()
        openAppearance(app)
        let restored = app.switches["Artwork color scheme"]
        XCTAssertTrue(restored.waitForExistence(timeout: 4))
        XCTAssertEqual(restored.value as? String, "0")
        restored.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
        XCTAssertEqual(restored.value as? String, "1")
    }

    @MainActor
    func testHomeEditorialCardsKeepLibraryAndContinueListening() {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-catalogFixture", "appearance", "-themeMode", "light", "-artworkColors", "YES"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Continue Listening"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["continue-listening-book-appearance-red"].exists)
        app.swipeUp(); app.swipeUp()
        XCTAssertTrue(app.staticTexts["Featured Audiobooks"].exists)
        save(app, "home-cover-led-editorial-cards")
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.navigationBars["Library"].waitForExistence(timeout: 5))
        save(app, "neutral-library-with-colored-covers")
    }

    @MainActor
    private func openAppearance(_ app: XCUIApplication) {
        app.buttons["Settings"].tap()
        let appearance = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Appearance'")).firstMatch
        XCTAssertTrue(appearance.waitForExistence(timeout: 4)); appearance.tap()
    }

    @MainActor
    private func launch(query: String, theme: String, tab: String = "search", extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-selectedTab", tab, "-catalogFixture", "appearance", "-catalogQuery", query, "-themeMode", theme, "-artworkColors", "YES"] + extra
        app.launch()
        return app
    }

    @MainActor
    private func save(_ app: XCUIApplication, _ name: String) {
        // Wait for the native navigation and palette fade to finish before comparing pixels.
        Thread.sleep(forTimeInterval: 0.6)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
