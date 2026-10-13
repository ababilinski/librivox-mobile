import XCTest

final class CatalogSearchUITests: XCTestCase {
    @MainActor
    func testUnifiedSearchLibraryKeepsPolishLanguageForImports() throws {
        let app = launch(fixture: "unified", query: "Pamietnik")
        let importedBook = app.buttons["search-result-customLocal:unified-polish"]
        XCTAssertTrue(importedBook.waitForExistence(timeout: 6))
        let scope = app.segmentedControls["unified-search-scope"]
        XCTAssertTrue(scope.waitForExistence(timeout: 4))
        scope.buttons["Library"].tap()
        XCTAssertTrue(importedBook.waitForExistence(timeout: 4))

        let languageMenu = app.buttons.matching(
            NSPredicate(format: "label == 'Any language'")
        ).firstMatch
        XCTAssertTrue(languageMenu.waitForExistence(timeout: 4))
        languageMenu.tap()
        let polish = app.buttons.matching(
            NSPredicate(format: "label == 'Polish' OR label BEGINSWITH 'Polish ('")
        ).firstMatch
        XCTAssertTrue(polish.waitForExistence(timeout: 3))
        polish.tap()

        XCTAssertTrue(importedBook.waitForExistence(timeout: 4))
        XCTAssertTrue(importedBook.label.contains("Pamiętnik z podróży"))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == 'Polish'")).firstMatch.exists)
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label == 'Any language'")).firstMatch.exists)
        saveUnifiedScreenshot(app, named: "Library search retains Polish filtering for an imported book")
    }

    @MainActor
    func testUnifiedSearchDownloadsOnlySkipsCatalogs() throws {
        let app = launch(fixture: "unified-slow", query: "Alice", extraArguments: [
            "-unifiedDownloadsOnly", "YES",
        ])
        let localBook = app.buttons["search-result-customLocal:unified-local"]
        XCTAssertTrue(localBook.waitForExistence(timeout: 6))
        XCTAssertTrue(app.staticTexts["Searching downloaded books"].waitForExistence(timeout: 4))
        XCTAssertTrue(localBook.label.contains("Downloaded"))

        let nonDownloadedSavedBook = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true"),
            object: app.buttons["search-result-libriVox:200"]
        )
        let catalogBook = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true"),
            object: app.buttons["search-result-lit2Go:lit2go-1__alices-adventures-in-wonderland"]
        )
        nonDownloadedSavedBook.isInverted = true
        catalogBook.isInverted = true
        wait(for: [nonDownloadedSavedBook, catalogBook], timeout: 3)
        XCTAssertTrue(localBook.exists)
        XCTAssertEqual(
            app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'search-result-'")).count,
            1
        )
        saveUnifiedScreenshot(app, named: "Downloads-only unified search excludes online and undownloaded books")
    }

    @MainActor
    func testUnifiedSearchTabBrowsesAndReturnsFromSearch() throws {
        let app = launchHome(fixture: "unified", extraArguments: [
            "-unifiedSearchOnDiscover", "YES",
        ])
        let searchTab = app.tabBars.buttons["Search"]
        XCTAssertTrue(searchTab.waitForExistence(timeout: 6))
        XCTAssertFalse(app.tabBars.buttons["Discover"].exists)
        XCTAssertEqual(app.tabBars.buttons.count, 3)
        searchTab.tap()
        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.keyboards.firstMatch.exists)
        XCTAssertTrue(app.staticTexts["Alice's Adventures in Wonderland"].firstMatch.waitForExistence(timeout: 5))
        saveUnifiedScreenshot(app, named: "One Search tab retains featured browsing without opening the keyboard")

        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 4))
        XCTAssertGreaterThan(
            search.frame.midY,
            app.windows.firstMatch.frame.midY,
            "The dedicated Search tab should expose the native field at the bottom on iPhone"
        )
        XCTAssertTrue(search.isHittable)
        if app.buttons["mini-player-open"].exists {
            XCTAssertLessThanOrEqual(
                app.buttons["mini-player-open"].frame.maxY,
                search.frame.minY + 1,
                "The mini-player must not cover the bottom search field"
            )
        }
        search.tap()
        search.typeText("Alice")

        XCTAssertTrue(app.buttons["search-result-customLocal:unified-local"].waitForExistence(timeout: 6))
        XCTAssertTrue(app.segmentedControls["unified-search-scope"].exists)
        XCTAssertTrue(
            app.buttons["search-result-lit2Go:lit2go-1__alices-adventures-in-wonderland"]
                .waitForExistence(timeout: 6)
        )
        let backToBrowse = app.buttons["search-back-to-browse"]
        XCTAssertTrue(backToBrowse.isHittable)
        saveUnifiedScreenshot(app, named: "Unified search provides a visible control to return to browsing")
        backToBrowse.tap()
        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.tabBars.buttons["Home"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-open"].exists)
        XCTAssertFalse(app.buttons["search-result-customLocal:unified-local"].exists)
        saveUnifiedScreenshot(app, named: "Returning to Browse clears the query and keeps the native bottom field")
        app.tabBars.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["LibriVox"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.tabBars.buttons["Library"].isHittable)
        XCTAssertTrue(app.tabBars.buttons["Search"].isHittable)
    }

    @MainActor
    func testSearchCanCancelAnEmptyQueryAndSwitchTabs() throws {
        let app = launchHome(fixture: "unified", extraArguments: [
            "-unifiedSearchOnDiscover", "NO",
        ])
        let searchTab = app.tabBars.buttons["Search"]
        XCTAssertTrue(searchTab.waitForExistence(timeout: 6))
        XCTAssertFalse(app.tabBars.buttons["Discover"].exists)
        searchTab.tap()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 4))
        search.tap()
        let back = app.buttons["search-back-to-browse"]
        XCTAssertTrue(back.waitForExistence(timeout: 4))
        XCTAssertTrue(back.isHittable)
        back.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["search-browse-heading"].exists)
        search.tap()
        search.typeText("Alice")
        let cancel = app.buttons.matching(NSPredicate(format: "label == 'Close' OR label == 'Cancel'")).firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 4))
        cancel.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 4))
        app.tabBars.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["LibriVox"].waitForExistence(timeout: 4))
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.navigationBars["Library"].waitForExistence(timeout: 4))
        app.tabBars.buttons["Home"].tap()
        XCTAssertTrue(app.navigationBars["LibriVox"].waitForExistence(timeout: 4))
        searchTab.tap()
        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 4))
        XCTAssertFalse(app.keyboards.firstMatch.exists, "Returning to the tab must not reopen search")
        XCTAssertTrue(app.searchFields.firstMatch.isHittable)
        XCTAssertTrue(app.tabBars.buttons["Home"].isHittable)
        saveUnifiedScreenshot(app, named: "Native search can be dismissed and Home restores the full tab bar")
    }

    @MainActor
    func testReturningToBrowseCancelsPendingCatalogSearch() throws {
        let app = launch(fixture: "unified-slow", query: "Alice")
        XCTAssertTrue(app.buttons["search-result-customLocal:unified-local"].waitForExistence(timeout: 6))
        app.buttons["search-back-to-browse"].tap()
        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 4))
        let staleResult = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true"),
            object: app.buttons["search-result-libriVox:200"]
        )
        staleResult.isInverted = true
        wait(for: [staleResult], timeout: 3)
        XCTAssertTrue(app.staticTexts["search-browse-heading"].exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'search-result-'")).count, 0)
    }

    @MainActor
    func testUnifiedSearchFindsDownloadedTitleAndChapterWhileOffline() throws {
        let app = launch(fixture: "unified-offline", query: "Alice at Home")
        let localBook = app.buttons["search-result-customLocal:unified-local"]
        XCTAssertTrue(localBook.waitForExistence(timeout: 6))
        XCTAssertTrue(localBook.label.contains("Downloaded"))
        XCTAssertFalse(app.buttons["search-result-lit2Go:lit2go-1__alices-adventures-in-wonderland"].exists)

        replaceUnifiedQuery(in: app, with: "The Hidden Orchard")

        XCTAssertTrue(localBook.waitForExistence(timeout: 5))
        XCTAssertTrue(localBook.label.contains("Alice at Home"))
        XCTAssertEqual(app.searchFields.firstMatch.value as? String, "The Hidden Orchard")
        saveUnifiedScreenshot(app, named: "Unified search finds a downloaded chapter while offline")
    }

    @MainActor
    func testUnifiedSearchCombinesLibraryAndCatalogsAndIsolatesScopes() throws {
        let app = launch(fixture: "unified", query: "Alice")
        let localBook = app.buttons["search-result-customLocal:unified-local"]
        let savedBook = app.buttons["search-result-libriVox:200"]
        let catalogBook = app.buttons["search-result-lit2Go:lit2go-1__alices-adventures-in-wonderland"]
        XCTAssertTrue(localBook.waitForExistence(timeout: 6))
        XCTAssertTrue(catalogBook.waitForExistence(timeout: 6))
        XCTAssertTrue(savedBook.exists)
        XCTAssertEqual(app.buttons.matching(identifier: "search-result-libriVox:200").count, 1)
        XCTAssertTrue(savedBook.label.contains("In Library"))

        let scope = app.segmentedControls["unified-search-scope"]
        XCTAssertTrue(scope.waitForExistence(timeout: 4))
        scope.buttons["Library"].tap()
        XCTAssertTrue(localBook.waitForExistence(timeout: 4))
        XCTAssertTrue(savedBook.exists)
        XCTAssertTrue(catalogBook.waitForNonExistence(timeout: 4))
        saveUnifiedScreenshot(app, named: "Unified search Library scope excludes unsaved catalog books")

        scope.buttons["Catalogs"].tap()
        XCTAssertTrue(catalogBook.waitForExistence(timeout: 6))
        XCTAssertTrue(localBook.waitForNonExistence(timeout: 4))
        XCTAssertTrue(savedBook.waitForNonExistence(timeout: 4), "Catalog variants use the preferred online source")

        scope.buttons["Everything"].tap()
        XCTAssertTrue(localBook.waitForExistence(timeout: 4))
        XCTAssertTrue(catalogBook.waitForExistence(timeout: 6))
        XCTAssertEqual(app.buttons.matching(identifier: "search-result-libriVox:200").count, 1)
        saveUnifiedScreenshot(app, named: "Unified search Everything merges saved and catalog results")

        let sourceMenu = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'All sources'")).firstMatch
        XCTAssertTrue(sourceMenu.waitForExistence(timeout: 4))
        sourceMenu.tap()
        let libriVox = app.buttons.matching(
            NSPredicate(format: "label == 'LibriVox' OR label BEGINSWITH 'LibriVox ('")
        ).firstMatch
        XCTAssertTrue(libriVox.waitForExistence(timeout: 3))
        libriVox.tap()

        // Observe the remote match before merging it back with the saved copy.
        scope.buttons["Catalogs"].tap()
        XCTAssertTrue(savedBook.waitForExistence(timeout: 6))
        scope.buttons["Everything"].tap()
        XCTAssertTrue(savedBook.waitForExistence(timeout: 6))
        XCTAssertEqual(app.buttons.matching(identifier: "search-result-libriVox:200").count, 1)
        XCTAssertTrue(savedBook.label.contains("In Library"))
        XCTAssertTrue(localBook.waitForNonExistence(timeout: 4))
        XCTAssertTrue(catalogBook.waitForNonExistence(timeout: 4))
        saveUnifiedScreenshot(app, named: "Exact saved catalog match appears once with its library badge")
    }

    @MainActor
    func testUnifiedSearchReplacingQueryDoesNotRestoreStaleResults() throws {
        let app = launch(fixture: "unified-slow", query: "Alice")
        let staleLocalBook = app.buttons["search-result-customLocal:unified-local"]
        XCTAssertTrue(staleLocalBook.waitForExistence(timeout: 6))

        replaceUnifiedQuery(in: app, with: "Antek")

        XCTAssertTrue(staleLocalBook.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.buttons["search-result-wolneLektury:wolne-antek"].waitForExistence(timeout: 8))
        XCTAssertEqual(app.searchFields.firstMatch.value as? String, "Antek")
        let staleResult = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true"),
            object: app.buttons["search-result-libriVox:200"]
        )
        staleResult.isInverted = true
        wait(for: [staleResult], timeout: 3)
        XCTAssertFalse(staleLocalBook.exists)
        saveUnifiedScreenshot(app, named: "Replacement query keeps only current unified search results")
    }

    @MainActor
    func testUnifiedSearchClearingQueryCancelsSlowCatalogResults() throws {
        let app = launch(fixture: "unified-slow", query: "Alice")
        XCTAssertTrue(app.buttons["search-result-customLocal:unified-local"].waitForExistence(timeout: 6))

        replaceUnifiedQuery(in: app, with: "")

        XCTAssertTrue(app.staticTexts["search-browse-heading"].waitForExistence(timeout: 4))
        let staleCatalogBook = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == true"),
            object: app.buttons["search-result-libriVox:200"]
        )
        staleCatalogBook.isInverted = true
        // The fixture completes each delayed catalog operation after two seconds.
        wait(for: [staleCatalogBook], timeout: 3)
        XCTAssertEqual(
            app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'search-result-'")).count,
            0
        )
        XCTAssertTrue(app.staticTexts["search-browse-heading"].exists)
        saveUnifiedScreenshot(app, named: "Cleared unified search returns to browsing after delayed catalog replies")
    }

    @MainActor
    func testUnifiedSearchOpensTheMatchingLocalBookDetails() throws {
        let app = launchHome(fixture: "unified-offline")
        let searchTab = app.tabBars.buttons["Search"]
        XCTAssertTrue(searchTab.waitForExistence(timeout: 6))
        searchTab.tap()
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 4))
        search.tap()
        search.typeText("Alice")
        let localBook = app.buttons["search-result-customLocal:unified-local"]
        XCTAssertTrue(localBook.waitForExistence(timeout: 6))
        localBook.tap()

        let detailTitle = app.staticTexts["book-detail-title"]
        XCTAssertTrue(detailTitle.waitForExistence(timeout: 5))
        XCTAssertEqual(detailTitle.label, "Alice at Home")
        XCTAssertTrue(app.buttons["book-detail-play"].exists)
        XCTAssertNotEqual(detailTitle.label, "Alice's Adventures in Wonderland")
        saveUnifiedScreenshot(app, named: "Unified local result opens Alice at Home details")
        app.navigationBars.firstMatch.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(localBook.waitForExistence(timeout: 5))
        XCTAssertEqual(app.searchFields.firstMatch.value as? String, "Alice")
        XCTAssertTrue(app.buttons["search-back-to-browse"].isHittable)
        saveUnifiedScreenshot(app, named: "Book details return to the same search results and query")
    }

    @MainActor
    func testPolishTitlesAfterSwitchingAwayFromLibriVox() throws {
        let app = launch(fixture: "progressive", query: "Lalka")
        let sourceMenu = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'All sources'")).firstMatch
        XCTAssertTrue(sourceMenu.waitForExistence(timeout: 4))
        sourceMenu.tap()
        let libriVox = app.buttons.matching(NSPredicate(format: "label == 'LibriVox' OR label BEGINSWITH 'LibriVox ('")).firstMatch
        XCTAssertTrue(libriVox.waitForExistence(timeout: 3))
        libriVox.tap()

        let selectedSource = app.buttons.matching(NSPredicate(format: "label == 'LibriVox'")).firstMatch
        XCTAssertTrue(selectedSource.waitForExistence(timeout: 3))
        selectedSource.tap()
        let polish = app.buttons.matching(NSPredicate(format: "label == 'Wolne Lektury' OR label BEGINSWITH 'Wolne Lektury ('")).firstMatch
        XCTAssertTrue(polish.waitForExistence(timeout: 3))
        polish.tap()
        XCTAssertTrue(app.staticTexts["Lalka"].waitForExistence(timeout: 5))

        let fieldMenu = app.buttons.matching(NSPredicate(format: "label == 'All'")).firstMatch
        XCTAssertTrue(fieldMenu.waitForExistence(timeout: 3))
        fieldMenu.tap()
        let titleOnly = app.buttons.matching(NSPredicate(format: "label == 'Title' OR label BEGINSWITH 'Title ('")).firstMatch
        XCTAssertTrue(titleOnly.waitForExistence(timeout: 3))
        titleOnly.tap()
        XCTAssertTrue(app.staticTexts["Lalka"].waitForExistence(timeout: 5))

        let search = app.searchFields.firstMatch
        search.tap()
        if let value = search.value as? String {
            search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
        }
        search.typeText("Antek")
        XCTAssertTrue(app.staticTexts["Antek"].waitForExistence(timeout: 5))
    }

    @MainActor
    func testLegacyOnboardingFlagDoesNotSkipUpdatedSetup() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-onboardingVersion", "2", "-themeMode", "light"]
        app.launch()
        let next = app.buttons["onboarding-next"]
        XCTAssertTrue(next.waitForExistence(timeout: 6))
        XCTAssertEqual(next.label, "Get started")
        saveUnifiedScreenshot(app, named: "Onboarding welcome with bundled audiobook covers")
        next.tap()

        XCTAssertFalse(app.buttons["onboarding-source-gutendex"].exists)
        let sourceIDs = ["libriVox", "lit2Go", "wolneLektury"]
        for sourceID in sourceIDs {
            let source = app.buttons["onboarding-source-\(sourceID)"]
            XCTAssertTrue(source.waitForExistence(timeout: 4))
            if source.value as? String == "Selected" {
                source.tap()
            }
        }
        XCTAssertFalse(next.isEnabled, "Setup requires at least one audiobook source")
        app.buttons["onboarding-source-wolneLektury"].tap()
        XCTAssertTrue(next.isEnabled)
        saveUnifiedScreenshot(app, named: "Onboarding source choices with only Wolne Lektury selected")
        next.tap()

        let polish = app.buttons["onboarding-language-polish"]
        XCTAssertTrue(polish.waitForExistence(timeout: 4))
        XCTAssertFalse(app.buttons["onboarding-language-english"].exists)
        app.buttons["onboarding-language-all"].tap()
        XCTAssertEqual(app.buttons["onboarding-language-all"].value as? String, "Selected")
        XCTAssertEqual(polish.value as? String, "Not selected")
        polish.tap()
        XCTAssertEqual(app.buttons["onboarding-language-all"].value as? String, "Not selected")
        app.buttons["onboarding-back"].tap()
        XCTAssertTrue(app.buttons["onboarding-source-wolneLektury"].waitForExistence(timeout: 4))
        XCTAssertEqual(app.buttons["onboarding-source-wolneLektury"].value as? String, "Selected")
        next.tap()
        XCTAssertTrue(polish.waitForExistence(timeout: 4))
        XCTAssertEqual(polish.value as? String, "Selected")
        saveUnifiedScreenshot(app, named: "Onboarding languages adapt to the selected Polish source")
        next.tap()

        let downloadsOff = app.buttons["onboarding-download-off"]
        XCTAssertTrue(downloadsOff.waitForExistence(timeout: 4))
        downloadsOff.tap()
        XCTAssertEqual(downloadsOff.value as? String, "Selected")
        saveUnifiedScreenshot(app, named: "Onboarding offline listening options")
        next.tap()
        XCTAssertTrue(app.staticTexts["Your first book is ready"].waitForExistence(timeout: 4))
        XCTAssertEqual(next.label, "Start Listening")
        saveUnifiedScreenshot(app, named: "Onboarding optional casting and themed Start Listening button")
        next.tap()
        XCTAssertTrue(app.navigationBars["Set Up"].waitForNonExistence(timeout: 5))
        XCTAssertTrue(app.tabBars.buttons["Home"].waitForExistence(timeout: 5))
        app.terminate()
        app.launchArguments = ["-hasCompletedOnboarding", "YES"]
        app.launch()
        XCTAssertTrue(app.tabBars.buttons["Home"].waitForExistence(timeout: 6))
        XCTAssertFalse(app.navigationBars["Set Up"].exists)

        // Revisit setup without clearing the profile to check that the choices
        // survive a cold launch as well as navigation within the flow.
        app.terminate()
        app.launchArguments = ["-onboardingVersion", "2", "-themeMode", "light"]
        app.launch()
        XCTAssertTrue(next.waitForExistence(timeout: 6))
        next.tap()
        XCTAssertTrue(app.buttons["onboarding-source-wolneLektury"].waitForExistence(timeout: 4))
        XCTAssertEqual(app.buttons["onboarding-source-wolneLektury"].value as? String, "Selected")
        XCTAssertEqual(app.buttons["onboarding-source-libriVox"].value as? String, "Not selected")
        next.tap()
        XCTAssertTrue(polish.waitForExistence(timeout: 4))
        XCTAssertEqual(polish.value as? String, "Selected")
        next.tap()
        XCTAssertTrue(downloadsOff.waitForExistence(timeout: 4))
        XCTAssertEqual(downloadsOff.value as? String, "Selected")
    }

    @MainActor
    func testOnboardingDarkAppearanceAndAccessibilityText() throws {
        let app = XCUIApplication()
        app.launchArguments = [
            "-onboardingVersion", "2", "-themeMode", "dark", "-animationSpeed", "reduced",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ]
        app.launch()
        let next = app.buttons["onboarding-next"]
        XCTAssertTrue(next.waitForExistence(timeout: 6))
        XCTAssertTrue(next.isHittable)
        saveUnifiedScreenshot(app, named: "Onboarding dark welcome with largest accessibility text")
        next.tap()
        XCTAssertTrue(app.buttons["onboarding-source-libriVox"].waitForExistence(timeout: 4))
        if !next.isEnabled {
            app.buttons["onboarding-source-libriVox"].tap()
        }
        next.tap()
        XCTAssertTrue(app.buttons["onboarding-language-all"].waitForExistence(timeout: 4))
        app.buttons["onboarding-language-all"].tap()
        saveUnifiedScreenshot(app, named: "Onboarding languages use one column with accessibility text")
        next.tap()
        XCTAssertTrue(app.buttons["onboarding-download-off"].waitForExistence(timeout: 4))
        XCTAssertTrue(next.isHittable, "The primary action stays reachable on long setup screens")
        next.tap()
        XCTAssertTrue(app.staticTexts["Your first book is ready"].waitForExistence(timeout: 4))
        XCTAssertTrue(next.isHittable)
        XCTAssertEqual(next.label, "Start Listening")
        let back = app.buttons["onboarding-back"]
        XCTAssertTrue(back.isHittable)
        XCTAssertGreaterThan(next.frame.width, app.frame.width * 0.7)
        XCTAssertLessThan(back.frame.maxY, next.frame.minY, "Large-text actions are stacked instead of squeezed side by side")
        saveUnifiedScreenshot(app, named: "Onboarding dark Start Listening with accessibility text")
    }

    @MainActor
    func testProgressiveAliceSearchShowsFixtureResults() throws {
        let app = launch(fixture: "progressive", query: "alice ")

        XCTAssertTrue(app.searchFields.firstMatch.waitForExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["Alice's Adventures in Wonderland"].waitForExistence(timeout: 6))
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'All sources'")).firstMatch.exists)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'Any language'")).firstMatch.exists)
    }

    @MainActor
    func testChangingSourceRerunsCurrentQuery() throws {
        let app = launch(fixture: "progressive", query: "alice")
        XCTAssertTrue(app.staticTexts["Alice's Adventures in Wonderland"].waitForExistence(timeout: 6))

        let sourceMenu = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'All sources'")).firstMatch
        XCTAssertTrue(sourceMenu.waitForExistence(timeout: 3))
        sourceMenu.tap()
        let libriVox = app.buttons.matching(NSPredicate(format: "label == 'LibriVox' OR label BEGINSWITH 'LibriVox ('")).firstMatch
        XCTAssertTrue(libriVox.waitForExistence(timeout: 2))
        libriVox.tap()

        XCTAssertTrue(app.staticTexts["Alice's Adventures in Wonderland"].waitForExistence(timeout: 4))
    }

    @MainActor
    func testOfflineFixtureKeepsQueryAndExposesStatus() throws {
        let app = launch(fixture: "offline", query: "alice")

        let offlineStatus = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Offline'")).firstMatch
        XCTAssertTrue(offlineStatus.waitForExistence(timeout: 4))
        XCTAssertTrue(app.searchFields.firstMatch.waitForExistence(timeout: 4))
        XCTAssertEqual(app.searchFields.firstMatch.value as? String, "alice")
    }

    @MainActor
    func testAuthorScreenSupportsSortingAndPressHoldActions() throws {
        let app = launch(fixture: "progressive", query: "alice")
        let alice = app.staticTexts["Alice's Adventures in Wonderland"].firstMatch
        XCTAssertTrue(alice.waitForExistence(timeout: 6))
        alice.tap()

        let authorLink = app.buttons["book-detail-author-profile"]
        XCTAssertTrue(authorLink.waitForExistence(timeout: 4))
        authorLink.tap()

        XCTAssertTrue(app.staticTexts["author-profile-name"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["author-sort-menu"].waitForExistence(timeout: 6))

        let authorBook = app.buttons
            .matching(NSPredicate(format: "identifier BEGINSWITH 'author-book-'"))
            .firstMatch
        XCTAssertTrue(authorBook.waitForExistence(timeout: 4))
        authorBook.press(forDuration: 0.6)
        let bookDetails = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH 'Book Details'"))
            .firstMatch
        XCTAssertTrue(bookDetails.waitForExistence(timeout: 3))

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.96, dy: 0.45)).tap()
        let relatedAuthor = app.buttons
            .matching(NSPredicate(format: "identifier BEGINSWITH 'related-author-'"))
            .firstMatch
        if !relatedAuthor.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(relatedAuthor.waitForExistence(timeout: 4))
        relatedAuthor.press(forDuration: 0.7)
        XCTAssertTrue(app.buttons["View Author Details"].waitForExistence(timeout: 3))
    }

    @MainActor
    func testRelatedBooksAndAuthorsRespectChangedCatalogSources() throws {
        let app = launch(fixture: "related-sources", query: "Antek")
        let antek = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'Antek' AND label CONTAINS[c] 'Wolne Lektury'")
        ).firstMatch
        XCTAssertTrue(antek.waitForExistence(timeout: 6))
        antek.tap()

        let authorLink = app.buttons["book-detail-author-profile"]
        XCTAssertTrue(authorLink.waitForExistence(timeout: 5))
        let otherEdition = app.buttons["more-by-author-librivox-prus-volume-1"]
        for _ in 0..<10 where !otherEdition.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(otherEdition.waitForExistence(timeout: 5), "Warm the related-book cache with all catalogs enabled")
        for _ in 0..<10 where !authorLink.isHittable {
            app.swipeDown()
        }
        XCTAssertTrue(authorLink.isHittable)
        authorLink.tap()

        let authorEdition = app.buttons["author-book-librivox-prus-volume-1"]
        for _ in 0..<10 where !authorEdition.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(authorEdition.waitForExistence(timeout: 5))
        let lewisCarroll = app.buttons["related-author-lewis-carroll"]
        let adamMickiewicz = app.buttons["related-author-adam-mickiewicz"]
        for _ in 0..<10 where !adamMickiewicz.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(adamMickiewicz.waitForExistence(timeout: 4))
        XCTAssertTrue(lewisCarroll.waitForExistence(timeout: 4), "Warm related authors before changing the enabled catalogs")

        // Revisit these books after changing sources to exercise the warmed caches.
        app.tabBars.buttons["Home"].tap()
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        let catalog = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH 'Catalog,'")
        ).firstMatch
        XCTAssertTrue(catalog.waitForExistence(timeout: 4))
        catalog.tap()
        for source in ["LibriVox", "Lit2Go"] {
            let toggle = app.switches.matching(
                NSPredicate(format: "label CONTAINS %@", source)
            ).firstMatch
            XCTAssertTrue(toggle.waitForExistence(timeout: 4))
            XCTAssertEqual(toggle.value as? String, "1")
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
            let disabled = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "value == '0'"),
                object: toggle
            )
            wait(for: [disabled], timeout: 4)
        }
        XCTAssertEqual(
            app.switches.matching(NSPredicate(format: "label CONTAINS 'Wolne Lektury'")).firstMatch.value as? String,
            "1"
        )

        app.tabBars.buttons["Search"].tap()
        if !antek.waitForExistence(timeout: 5) {
            replaceUnifiedQuery(in: app, with: "Antek")
        }
        XCTAssertTrue(antek.waitForExistence(timeout: 6))
        antek.tap()
        XCTAssertTrue(authorLink.waitForExistence(timeout: 5))
        authorLink.tap()
        XCTAssertTrue(app.staticTexts["author-profile-name"].waitForExistence(timeout: 5))
        XCTAssertTrue(authorEdition.waitForNonExistence(timeout: 5))
        XCTAssertTrue(lewisCarroll.waitForNonExistence(timeout: 5))
        for _ in 0..<10 where !adamMickiewicz.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(adamMickiewicz.isHittable, "Recommendations from the enabled Polish catalog remain available")
        saveUnifiedScreenshot(app, named: "Related authors update to the enabled Polish catalog")

        let katarynka = app.buttons["author-book-wolne-katarynka"]
        for _ in 0..<10 where !katarynka.isHittable {
            app.swipeDown()
            XCTAssertFalse(authorEdition.exists, "The saved LibriVox edition must not leak back into author recommendations")
        }
        XCTAssertTrue(katarynka.isHittable)
        XCTAssertFalse(authorEdition.exists)
        saveUnifiedScreenshot(app, named: "Author books exclude disabled catalogs while retaining Wolne Lektury")

        app.buttons["BackButton"].tap()
        XCTAssertTrue(authorLink.waitForExistence(timeout: 4))
        let relatedKatarynka = app.buttons["more-by-author-wolne-katarynka"]
        for _ in 0..<10 where !relatedKatarynka.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(relatedKatarynka.isHittable)
        XCTAssertTrue(otherEdition.waitForNonExistence(timeout: 4), "Reopening the warmed book shelf must not restore disabled sources")
        saveUnifiedScreenshot(app, named: "More by this author respects the updated catalog selection")
    }

    @MainActor
    func testBookDescriptionUsesNativeSelectableTextMenu() throws {
        let app = launchHome(fixture: "progressive")
        let jungle = app.buttons
            .matching(NSPredicate(format: "label CONTAINS 'Upton Sinclair' AND label CONTAINS 'LibriVox'"))
            .firstMatch
        XCTAssertTrue(jungle.waitForExistence(timeout: 6))
        jungle.tap()

        let description = app.descendants(matching: .any)
            .matching(identifier: "book-detail-description")
            .firstMatch
        for _ in 0..<4 where !description.exists {
            app.swipeUp()
        }
        XCTAssertTrue(description.waitForExistence(timeout: 5))
        description.press(forDuration: 1.0)

        let copy = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == 'Copy'"))
            .firstMatch
        XCTAssertTrue(copy.waitForExistence(timeout: 3))
        XCTAssertTrue(app.descendants(matching: .any)["Look Up"].exists)
    }

    @MainActor
    func testBottomPlayerWithLargeTextAndDarkAppearance() throws {
        let app = launchHome(fixture: "progressive", extraArguments: [
            "-themeMode", "dark",
            "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL",
        ])
        XCTAssertTrue(app.buttons["mini-player-play-pause"].waitForExistence(timeout: 6))
        app.buttons["mini-player-play-pause"].tap()
        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 5))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
            .press(forDuration: 0.05, thenDragTo:
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72)))
        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))
        app.swipeUp()
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-play-pause"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-route"].isHittable)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Bottom player with largest accessibility text and dark appearance"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testStartingFromScrolledCoverAfterRelaunchExpandsBottomPlayer() throws {
        let app = launchHome(fixture: "progressive")
        XCTAssertTrue(app.buttons["mini-player-play-pause"].waitForExistence(timeout: 6))
        app.buttons["mini-player-play-pause"].tap()
        XCTAssertTrue(app.descendants(matching: .any)["now-playing-surface"].waitForExistence(timeout: 5))
        // At position zero the book is not yet a Continue Listening entry.
        // Persist a started position before testing a process-dead relaunch.
        let forward = app.buttons["Forward 10 seconds"]
        XCTAssertTrue(forward.waitForExistence(timeout: 5))
        forward.tap()
        app.terminate()
        app.launch()

        let cover = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'continue-listening-playback-'")
        ).firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 6))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.52))
            .press(forDuration: 0.05, thenDragTo:
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.46)))
        XCTAssertTrue(cover.isHittable)
        cover.tap()

        XCTAssertTrue(app.buttons["mini-player-open"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["mini-player-open"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-play-pause"].isHittable)
        XCTAssertTrue(app.buttons["mini-player-route"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["mini-player-route"].isHittable)
        XCTAssertTrue(app.tabBars.buttons["Library"].isHittable)
        XCTAssertFalse(app.descendants(matching: .any)["now-playing-surface"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Expanded bottom player after first play from a scrolled cover"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testScrolledCoverPlaybackKeepsBottomPlayerAccessibleAfterBackgrounding() throws {
        let app = launchHome(fixture: "progressive")
        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 6))
        app.buttons["mini-player-play-pause"].tap()
        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 5))
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
            .press(forDuration: 0.05, thenDragTo:
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72)))
        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))

        let cover = app.buttons.matching(
            NSPredicate(format: "identifier BEGINSWITH 'continue-listening-playback-'")
        ).firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 5))
        // Scroll enough to trigger tab minimization while retaining the cover button.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.52))
            .press(forDuration: 0.05, thenDragTo:
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.46)))
        for _ in 0..<6 {
            XCTAssertTrue(cover.isHittable)
            cover.tap()
            XCTAssertTrue(miniPlayer.waitForExistence(timeout: 3))
            XCTAssertTrue(miniPlayer.isHittable)
            XCTAssertTrue(app.buttons["mini-player-play-pause"].isHittable)
            XCTAssertFalse(nowPlaying.exists)
        }

        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 5))
        XCTAssertTrue(miniPlayer.isHittable)
        // Loaded media keeps the full accessory, including its output control.
        XCTAssertTrue(app.buttons["mini-player-route"].isHittable)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Bottom player after scrolling, cover playback, and background resume"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testLiveWolneLalkaArtworkAndChapterTitleSurviveReturningFromPlayer() throws {
        let app = launch(fixture: "wolne-metadata", query: "Lalka")
        let result = app.buttons["search-result-wolneLektury:wolnelektury-lalka"]
        XCTAssertTrue(result.waitForExistence(timeout: 8))
        result.tap()
        let cover = app.descendants(matching: .any)["book-detail-cover"].firstMatch
        XCTAssertTrue(cover.waitForExistence(timeout: 6))
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == 'Cover art loaded'"), object: cover)
        wait(for: [loaded], timeout: 60)
        saveUnifiedScreenshot(app, named: "Live Wolne Lektury Lalka details show the downloaded cover")
        let donate = app.buttons["book-detail-donate"]
        for _ in 0..<5 where !donate.isHittable { app.swipeUp() }
        XCTAssertTrue(donate.isHittable)
        XCTAssertEqual(donate.label, "Donate to Wolne Lektury")
        XCTAssertTrue(app.buttons["book-detail-read"].isHittable)
        saveUnifiedScreenshot(app, named: "Lalka description includes Read and Donate together")
        let detailChapter = app.buttons["book-detail-chapter-lalka-tom-pierwszy-1"]
        for _ in 0..<4 where !detailChapter.isHittable { app.swipeUp() }
        XCTAssertTrue(detailChapter.waitForExistence(timeout: 5))
        XCTAssertTrue(detailChapter.label.contains("Jak wygląda firma J. Mincel"))
        XCTAssertTrue(detailChapter.label.contains("Bolesław Prus"))
        XCTAssertTrue(detailChapter.label.contains("Read by Albert Osik"))
        detailChapter.tap()
        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 6))
        XCTAssertTrue(app.staticTexts["01: Tom I, Rozdział I — Jak wygląda firma J. Mincel i S. Wokulski przez szkło butelek?"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH '01. Bolesław Prus'")).firstMatch.exists)
        saveUnifiedScreenshot(app, named: "Live Lalka player shows volume I first and a clean chapter title")
        app.buttons["Chapters"].tap()
        let playerChapter = app.buttons["now-playing-chapter-lalka-tom-pierwszy-1"]
        XCTAssertTrue(playerChapter.waitForExistence(timeout: 5))
        XCTAssertTrue(playerChapter.label.contains("Bolesław Prus"))
        XCTAssertTrue(playerChapter.label.contains("Read by Albert Osik"))
        saveUnifiedScreenshot(app, named: "Lalka player chapters distinguish the author and narrator")
        app.buttons["Done"].tap()
        let dismissStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let dismissEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        dismissStart.press(forDuration: 0.05, thenDragTo: dismissEnd)
        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))
        let returnedCover = app.descendants(matching: .any)["book-detail-cover"].firstMatch
        XCTAssertTrue(returnedCover.waitForExistence(timeout: 5))
        XCTAssertEqual(returnedCover.value as? String, "Cover art loaded")
        saveUnifiedScreenshot(app, named: "Lalka book page retains cover art after returning from playback")
    }

    @MainActor
    func testContinueListeningRemovalPersistsAndResumingPreservesProgress() throws {
        let app = launchHome(fixture: "progressive", extraArguments: ["-continueListeningFixture", "reset"])
        defer { resetContinueListeningFixture(app) }
        let jungle = app.buttons["continue-listening-book-3269"]
        XCTAssertTrue(jungle.waitForExistence(timeout: 6))
        XCTAssertTrue(app.buttons["continue-listening-summary-3269"].label.contains("2:00"))
        jungle.press(forDuration: 1)
        let remove = app.buttons["Remove from Continue Listening"]
        XCTAssertTrue(remove.waitForExistence(timeout: 4))
        saveUnifiedScreenshot(app, named: "Continue Listening long-press menu offers removal")
        remove.tap()
        XCTAssertTrue(jungle.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.buttons["continue-listening-book-wolne-antek"].exists)
        XCTAssertTrue(app.staticTexts["Your Library"].exists)

        app.terminate()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-catalogFixture", "progressive", "-continueListeningFixture", "preserve"]
        app.launch()
        XCTAssertTrue(app.buttons["continue-listening-book-wolne-antek"].waitForExistence(timeout: 6))
        XCTAssertFalse(jungle.exists, "Removed books must remain hidden after relaunch")
        saveUnifiedScreenshot(app, named: "Removed book stays hidden after relaunch and remains in the library")

        let libraryJungle = app.buttons.matching(
            NSPredicate(format: "label CONTAINS 'Upton Sinclair' AND label CONTAINS 'LibriVox'")
        ).firstMatch
        XCTAssertTrue(libraryJungle.waitForExistence(timeout: 4))
        libraryJungle.tap()
        let resume = app.buttons["book-detail-play"]
        XCTAssertTrue(resume.waitForExistence(timeout: 4))
        XCTAssertTrue(resume.label.contains("Resume"), "Removal must keep saved listening progress")
        resume.tap()
        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["2:00"].waitForExistence(timeout: 4), "Resume must use the saved two-minute position")
        let dismissStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let dismissEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        dismissStart.press(forDuration: 0.05, thenDragTo: dismissEnd)
        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))
        app.navigationBars.firstMatch.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(jungle.waitForExistence(timeout: 5), "Playing the book again restores Continue Listening")
        saveUnifiedScreenshot(app, named: "Resuming a removed book restores Continue Listening")
    }

    @MainActor
    func testContinueListeningRemovalDoesNotInterruptCurrentPlayback() throws {
        let app = launchHome(fixture: "progressive", extraArguments: ["-continueListeningFixture", "reset"])
        defer { resetContinueListeningFixture(app) }
        let play = app.buttons["continue-listening-playback-3269"]
        XCTAssertTrue(play.waitForExistence(timeout: 6))
        play.tap()
        XCTAssertTrue(app.buttons["Pause The Jungle"].waitForExistence(timeout: 5))
        let jungle = app.buttons["continue-listening-book-3269"]
        jungle.press(forDuration: 1)
        let remove = app.buttons["Remove from Continue Listening"]
        XCTAssertTrue(remove.waitForExistence(timeout: 4))
        remove.tap()
        XCTAssertTrue(jungle.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.buttons["mini-player-open"].exists)
        XCTAssertTrue(["Pause", "Buffering"].contains(app.buttons["mini-player-play-pause"].label))
        let resurfacedCard = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true"), object: jungle)
        resurfacedCard.isInverted = true
        wait(for: [resurfacedCard], timeout: 3)
        XCTAssertFalse(app.descendants(matching: .any)["now-playing-surface"].exists)
        saveUnifiedScreenshot(app, named: "Removing the current book preserves its mini-player and playback")
    }

    @MainActor
    func testContinueListeningSummaryLongPressCanRemoveTheLastBook() throws {
        let app = launchHome(fixture: "progressive", extraArguments: ["-continueListeningFixture", "reset"])
        defer { resetContinueListeningFixture(app) }
        let antekSummary = app.buttons["continue-listening-summary-wolne-antek"]
        XCTAssertTrue(antekSummary.waitForExistence(timeout: 6))
        antekSummary.press(forDuration: 1)
        let remove = app.buttons["Remove from Continue Listening"]
        XCTAssertTrue(remove.waitForExistence(timeout: 4))
        remove.tap()
        XCTAssertTrue(antekSummary.waitForNonExistence(timeout: 4))
        let jungle = app.buttons["continue-listening-book-3269"]
        jungle.press(forDuration: 1)
        XCTAssertTrue(remove.waitForExistence(timeout: 4))
        remove.tap()
        XCTAssertTrue(app.staticTexts["Continue Listening"].waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["Your Library"].exists)
        XCTAssertTrue(app.staticTexts["The Jungle"].firstMatch.exists)
        XCTAssertTrue(app.staticTexts["Antek"].firstMatch.exists)
        XCTAssertTrue(app.tabBars.buttons["Search"].isHittable)
        saveUnifiedScreenshot(app, named: "Removing the last Continue Listening book keeps the library and tabs")
    }

    @MainActor
    func testContinueListeningCoverControlTogglesCurrentBookWithoutPresentingNowPlaying() throws {
        let app = launchHome(fixture: "progressive")
        let jungle = app.buttons
            .matching(NSPredicate(format: "label CONTAINS 'Upton Sinclair' AND label CONTAINS 'LibriVox'"))
            .firstMatch
        XCTAssertTrue(jungle.waitForExistence(timeout: 6))
        jungle.tap()

        let detailPlay = app.buttons["book-detail-play"]
        XCTAssertTrue(detailPlay.waitForExistence(timeout: 5))
        detailPlay.tap()

        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 5))
        let dismissStart = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let dismissEnd = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        dismissStart.press(forDuration: 0.05, thenDragTo: dismissEnd)
        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 5))

        let detailNavigationBar = app.navigationBars.firstMatch
        XCTAssertTrue(app.staticTexts["book-detail-title"].waitForExistence(timeout: 4))
        let backButton = detailNavigationBar.buttons.element(boundBy: 0)
        XCTAssertTrue(backButton.exists)
        backButton.tap()

        XCTAssertTrue(app.staticTexts["Continue Listening"].waitForExistence(timeout: 5))
        let coverTransport = app.buttons
            .matching(identifier: "continue-listening-playback-3269")
            .matching(NSPredicate(format: "label == 'Pause The Jungle'"))
            .firstMatch
        XCTAssertTrue(coverTransport.waitForExistence(timeout: 5))
        XCTAssertEqual(coverTransport.value as? String, "Playing")
        XCTAssertFalse(nowPlaying.exists)

        coverTransport.tap()

        let pausedTransport = app.buttons
            .matching(identifier: "continue-listening-playback-3269")
            .matching(NSPredicate(format: "label == 'Resume The Jungle'"))
            .firstMatch
        XCTAssertTrue(pausedTransport.waitForExistence(timeout: 4))
        XCTAssertEqual(pausedTransport.value as? String, "Paused")
        XCTAssertTrue(app.staticTexts["Continue Listening"].exists)
        XCTAssertFalse(nowPlaying.exists)
    }

    @MainActor
    func testNowPlayingSwipeDownDismissesToMiniPlayer() throws {
        let app = launchHome(fixture: "progressive")
        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 6))
        miniPlayer.tap()

        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 4))
        add(XCTAttachment(screenshot: app.screenshot(), quality: .medium))

        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        start.press(forDuration: 0.05, thenDragTo: end)

        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 4))
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 4))
        add(XCTAttachment(screenshot: app.screenshot(), quality: .medium))
    }

    @MainActor
    func testNowPlayingDismissalOverBookDetailsPreservesUnderlyingNavigation() throws {
        let app = launchHome(fixture: "progressive")
        let jungle = app.buttons
            .matching(NSPredicate(format: "label CONTAINS 'Upton Sinclair' AND label CONTAINS 'LibriVox'"))
            .firstMatch
        XCTAssertTrue(jungle.waitForExistence(timeout: 6))
        jungle.tap()

        let detailTitle = app.staticTexts["book-detail-title"]
        XCTAssertTrue(detailTitle.waitForExistence(timeout: 5))
        XCTAssertEqual(detailTitle.label, "The Jungle")

        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 4))
        miniPlayer.tap()

        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 4))
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.13))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.72))
        start.press(forDuration: 0.05, thenDragTo: end)

        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 4))
        XCTAssertTrue(detailTitle.waitForExistence(timeout: 4))
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 4))

        let attachment = XCTAttachment(screenshot: app.screenshot(), quality: .medium)
        attachment.name = "Now Playing returns to the existing Book Details navigation"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testAudioOutputPickerRemainsReachableWithoutExternalRoute() throws {
        let app = launchHome(fixture: "progressive")
        let routeButton = app.buttons["mini-player-route"]
        XCTAssertTrue(routeButton.waitForExistence(timeout: 6))
        routeButton.tap()

        XCTAssertTrue(app.navigationBars["Audio Output"].waitForExistence(timeout: 4))
        let outputSheet = app.descendants(matching: .any)["audio-output-sheet"]
        XCTAssertTrue(outputSheet.waitForExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["Playing"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["All Devices"].waitForExistence(timeout: 4))
        XCTAssertTrue(
            app.descendants(matching: .any)["current-output-row"]
                .waitForExistence(timeout: 4)
        )
        XCTAssertTrue(
            app.descendants(matching: .any)["audio-output-volume"]
                .waitForExistence(timeout: 4)
        )
        XCTAssertTrue(
            app.descendants(matching: .any)["airplay-bluetooth-route-row"]
                .waitForExistence(timeout: 4)
        )

        outputSheet.swipeUp()

        XCTAssertTrue(app.staticTexts["Listen Together"].waitForExistence(timeout: 4))
        let googleCast = app.descendants(matching: .any)["google-cast-route-row"]
        XCTAssertTrue(googleCast.waitForExistence(timeout: 4))
        let sharePlay = app.buttons["shareplay-route-row"]
        XCTAssertTrue(sharePlay.waitForExistence(timeout: 4))
        XCTAssertTrue(sharePlay.isEnabled)
        XCTAssertFalse(app.navigationBars["Other Outputs"].exists)
    }

    @MainActor
    func testNowPlayingContentSwipeDoesNotOpenBookDetails() throws {
        let app = launchHome(fixture: "progressive")
        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 6))
        miniPlayer.tap()

        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 4))
        XCTAssertGreaterThanOrEqual(
            nowPlaying.frame.maxY,
            app.windows.firstMatch.frame.maxY - 1,
            "The Now Playing canvas must cover the bottom safe area"
        )

        let cover = app.buttons["now-playing-book-title"]
        XCTAssertTrue(cover.waitForExistence(timeout: 4))
        XCTAssertFalse(cover.label.isEmpty)

        let coverCenter = cover.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let shortDragEnd = coverCenter.withOffset(CGVector(dx: 0, dy: 72))
        coverCenter.press(
            forDuration: 0.2,
            thenDragTo: shortDragEnd,
            withVelocity: .slow,
            thenHoldForDuration: 0.25
        )

        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 2))
        XCTAssertFalse(
            app.staticTexts["book-detail-title"].exists,
            "Releasing a dismiss drag on the moving title must not open Book Details"
        )

        let start = cover.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.78))
        start.press(forDuration: 0.05, thenDragTo: end)

        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 4))
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 4))
        XCTAssertFalse(app.staticTexts["book-detail-title"].exists)
    }

    @MainActor
    func testNowPlayingDismissalPreservesAppearanceNavigation() throws {
        let app = launchHome(fixture: "progressive")
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 6))
        settings.tap()

        let appearance = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH 'Appearance,'"))
            .firstMatch
        XCTAssertTrue(appearance.waitForExistence(timeout: 4))
        appearance.tap()
        XCTAssertTrue(app.navigationBars["Appearance"].waitForExistence(timeout: 4))

        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 4))
        miniPlayer.tap()

        let nowPlaying = app.descendants(matching: .any)["now-playing-surface"]
        XCTAssertTrue(nowPlaying.waitForExistence(timeout: 4))
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.38))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.78))
        start.press(forDuration: 0.05, thenDragTo: end)

        XCTAssertTrue(nowPlaying.waitForNonExistence(timeout: 4))
        XCTAssertTrue(app.navigationBars["Appearance"].waitForExistence(timeout: 4))
        XCTAssertFalse(app.staticTexts["Book Knowledge"].exists)
        add(XCTAttachment(screenshot: app.screenshot(), quality: .medium))
    }

    @MainActor
    func testNowPlayingRestoresAudiobookTransportAndCanonicalDetailsNavigation() throws {
        let app = launchHome(fixture: "progressive")
        let miniPlayer = app.buttons["mini-player-open"]
        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 6))
        miniPlayer.tap()

        XCTAssertTrue(app.descendants(matching: .any)["now-playing-surface"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["Restart or previous chapter"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["Rewind 10 seconds"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["Play"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["Forward 10 seconds"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["Next chapter"].waitForExistence(timeout: 4))

        let cover = app.buttons["now-playing-book-title"]
        XCTAssertTrue(cover.waitForExistence(timeout: 4))
        let expectedBookTitle = cover.label
        cover.tap()
        let detailTitle = app.staticTexts["book-detail-title"]
        XCTAssertTrue(detailTitle.waitForExistence(timeout: 5))
        XCTAssertEqual(detailTitle.label, expectedBookTitle)

        XCTAssertTrue(miniPlayer.waitForExistence(timeout: 3))
        miniPlayer.tap()
        XCTAssertTrue(cover.waitForExistence(timeout: 5))

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Restored white audiobook transport controls"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testAppearanceOwnsLayoutSettingsAndDownloadsExposeLibraryAutomation() throws {
        let app = launchHome(fixture: "progressive")
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()

        XCTAssertFalse(
            app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Interface'"))
                .firstMatch.exists
        )
        let appearance = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH 'Appearance'")
        ).firstMatch
        XCTAssertTrue(appearance.waitForExistence(timeout: 4))
        appearance.tap()
        XCTAssertTrue(app.segmentedControls["Bottom navigation style"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.segmentedControls["Now Playing style"].waitForExistence(timeout: 4))

        app.buttons["BackButton"].tap()
        let downloads = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH 'Download Settings'")
        ).firstMatch
        XCTAssertTrue(downloads.waitForExistence(timeout: 4))
        downloads.tap()
        XCTAssertTrue(app.switches["Automatically download added books"].waitForExistence(timeout: 4))
    }

    @MainActor
    func testReaderRestoresFindContentsPageAndOptionsControls() throws {
        let app = launch(fixture: "progressive", query: "Antek")
        let antek = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'Antek' AND label CONTAINS[c] 'Wolne Lektury'")
        ).firstMatch
        XCTAssertTrue(antek.waitForExistence(timeout: 6))
        antek.tap()

        let readBook = app.buttons["Read book"]
        XCTAssertTrue(readBook.waitForExistence(timeout: 5))
        readBook.tap()

        XCTAssertTrue(app.buttons["Reader search"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Reader options"].waitForExistence(timeout: 5))
        XCTAssertTrue(
            app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'Page '")).firstMatch
                .waitForExistence(timeout: 5)
        )

        app.buttons["Reader options"].tap()
        XCTAssertTrue(app.navigationBars["Reader Options"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.descendants(matching: .any)["reader-page-style"].exists)
    }

    @MainActor
    private func launch(fixture: String, query: String, extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            "-hasCompletedOnboarding", "YES",
            "-selectedTab", "search",
            "-catalogFixture", fixture,
            "-catalogQuery", query,
        ]
        app.launchArguments += extraArguments
        app.launch()
        return app
    }

    @MainActor
    private func replaceUnifiedQuery(in app: XCUIApplication, with query: String) {
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 4))
        search.tap()
        let clear = search.buttons["Clear text"]
        if clear.exists {
            clear.tap()
        } else if let currentQuery = search.value as? String, !currentQuery.isEmpty {
            search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentQuery.count))
        }
        if !query.isEmpty {
            search.typeText(query)
        }
    }

    @MainActor
    private func saveUnifiedScreenshot(_ app: XCUIApplication, named name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    private func resetContinueListeningFixture(_ app: XCUIApplication) {
        app.terminate()
        app.launchArguments = ["-hasCompletedOnboarding", "YES", "-catalogFixture", "progressive", "-continueListeningFixture", "reset"]
        app.launch()
        XCTAssertTrue(app.buttons["continue-listening-book-3269"].waitForExistence(timeout: 6))
        app.terminate()
    }

    @MainActor
    private func launchHome(fixture: String, extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments += [
            "-hasCompletedOnboarding", "YES",
            "-catalogFixture", fixture,
        ]
        app.launchArguments += extraArguments
        app.launch()
        return app
    }
}
