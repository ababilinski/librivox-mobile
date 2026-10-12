import LibriVoxCore
import SwiftUI
import UIKit
import XCTest
@testable import LibriVoxMobile

final class ArtworkAppearanceTests: XCTestCase {
    func testBookDetailZoomHasDistinctIntermediateSizesAndNoThresholdJump() {
        func transform(_ offset: CGFloat) -> BookDetailScrollTransform {
            BookDetailScrollTransform(offset: offset, viewportWidth: 402, compactTopInset: 72)
        }
        let offsets: [CGFloat] = [0, 14, 28, 42, 56, 70, 84, 98, 112]
        let samples = offsets.map(transform)
        XCTAssertLessThan(samples[0].scale, 1)
        XCTAssertGreaterThan(samples[0].cornerRadius, 0)
        for (earlier, later) in zip(samples, samples.dropFirst()) {
            XCTAssertGreaterThan(later.scale, earlier.scale)
            XCTAssertLessThan(later.topOffset, earlier.topOffset)
            XCTAssertLessThan(later.cornerRadius, earlier.cornerRadius)
        }
        let expanded = transform(112)
        XCTAssertEqual(expanded.scale, 1, accuracy: 0.0001)
        XCTAssertEqual(expanded.topOffset, 0, accuracy: 0.0001)
        XCTAssertEqual(expanded.cornerRadius, 0, accuracy: 0.0001)
        XCTAssertEqual(transform(400).scale, expanded.scale)
        XCTAssertEqual(transform(400).topOffset, expanded.topOffset)

        // The previous implementation switched at 28 points. A tiny movement
        // around that location must produce a tiny, reversible geometry change.
        XCTAssertLessThan(abs(transform(28.01).scale - transform(27.99).scale), 0.0001)
        XCTAssertLessThan(abs(transform(28.01).topOffset - transform(27.99).topOffset), 0.1)
        XCTAssertLessThan(abs(transform(0.01).scale - transform(-0.01).scale), 0.0001)
        XCTAssertLessThan(abs(transform(112.01).scale - transform(111.99).scale), 0.0001)
    }

    func testBookDetailTopPullRevealsSourceHeadingWithoutDoublingNativeBounce() {
        let compactTopInset: CGFloat = 72
        func transform(_ offset: CGFloat) -> BookDetailScrollTransform {
            BookDetailScrollTransform(offset: offset, viewportWidth: 402, compactTopInset: compactTopInset)
        }
        let resting = transform(0)
        XCTAssertEqual(resting.sourceHeadingReveal, 0)
        XCTAssertEqual(transform(56).sourceHeadingReveal, 0)
        XCTAssertEqual(transform(400).sourceHeadingReveal, 0)

        let modestPull = transform(-20)
        let largerPull = transform(-60)
        XCTAssertGreaterThan(modestPull.sourceHeadingReveal, 0)
        XCTAssertGreaterThan(largerPull.sourceHeadingReveal, modestPull.sourceHeadingReveal)
        XCTAssertGreaterThan(largerPull.sourceHeadingReveal, 44, "A normal top pull must uncover a full standard-size source heading")
        XCTAssertGreaterThan(largerPull.topOffset, resting.topOffset)
        XCTAssertLessThan(largerPull.scale, resting.scale)
        XCTAssertGreaterThan(largerPull.cornerRadius, 0)

        // UIKit already moves the inner scroll content by the native pull.
        // That movement is compensated inside the card, leaving only the
        // outer card's translation to expose the collection behind it.
        for pull: CGFloat in [10, 30, 60, 120] {
            let pulled = transform(-pull)
            XCTAssertEqual(pulled.topOffset - resting.topOffset, pull, accuracy: 0.001)
            XCTAssertEqual(pulled.contentOffset + pull, resting.contentOffset, accuracy: 0.001)
        }

        // A deceleration or spring-back supplies these same decreasing native
        // offsets; geometry must follow them all the way back without latching.
        let returning = [-60.0, -30.0, -10.0, 0.0].map { transform(CGFloat($0)) }
        for (earlier, later) in zip(returning, returning.dropFirst()) {
            XCTAssertLessThan(later.topOffset, earlier.topOffset)
            XCTAssertLessThan(later.sourceHeadingReveal, earlier.sourceHeadingReveal)
        }
        XCTAssertEqual(returning.last?.topOffset, resting.topOffset)
        XCTAssertEqual(returning.last?.scale, resting.scale)
    }

    func testBookDetailMotionReductionAndPagingDoNotCarryVerticalBounce() {
        let compact = BookDetailScrollTransform(offset: 0, viewportWidth: 402, compactTopInset: 72)
        let reducedPull = BookDetailScrollTransform(offset: -80, viewportWidth: 402, compactTopInset: 72, reducesMotion: true)
        XCTAssertEqual(reducedPull.scale, compact.scale)
        XCTAssertEqual(reducedPull.topOffset, compact.topOffset)
        XCTAssertEqual(reducedPull.pullDistance, 0)
        XCTAssertEqual(reducedPull.sourceHeadingReveal, 0)

        let paging = BookDetailScrollTransform(offset: -80, viewportWidth: 402, compactTopInset: 72, forcedExpansion: 0)
        XCTAssertEqual(paging.scale, compact.scale)
        XCTAssertEqual(paging.topOffset, compact.topOffset)
        XCTAssertEqual(paging.pullDistance, 0)
        XCTAssertEqual(paging.sourceHeadingReveal, 0)

        for offset: CGFloat in [14, 28, 56, 84, 100] {
            let reduced = BookDetailScrollTransform(offset: offset, viewportWidth: 402, compactTopInset: 72, reducesMotion: true)
            XCTAssertTrue(reduced.expansion == 0 || reduced.expansion == 1, "Reduced motion must avoid proportional zoom")
        }
    }

    func testHistogramFindsTheLargeColoredRegionAndIsDeterministic() throws {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 100)).image { context in
            UIColor(red: 0.8, green: 0.1, blue: 0.05, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 100, height: 100))
            UIColor(red: 0.85, green: 0.65, blue: 0.1, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 100))
        }
        let data = try XCTUnwrap(image.pngData())
        let palette = try XCTUnwrap(ArtworkPalette.extract(from: data))
        XCTAssertGreaterThan(palette.dominant.red, palette.dominant.green * 3)
        XCTAssertGreaterThan(palette.secondary.green, palette.dominant.green)
        XCTAssertEqual(palette, ArtworkPalette.extract(from: data))
    }

    func testInvalidTransparentAndMonochromeImagesUseTheFallback() throws {
        XCTAssertNil(ArtworkPalette.extract(from: Data("invalid".utf8)))
        for color in [UIColor.clear, UIColor.white, UIColor.black, UIColor(white: 0.7, alpha: 1)] {
            let image = UIGraphicsImageRenderer(size: CGSize(width: 32, height: 32)).image { context in
                color.setFill(); context.fill(CGRect(x: 0, y: 0, width: 32, height: 32))
            }
            XCTAssertNil(ArtworkPalette.extract(from: try XCTUnwrap(image.pngData())))
        }
    }

    func testPaletteVariantsMaintainTextContrastAcrossAppearanceModes() {
        for dominant in [ArtworkRGB(red: 1, green: 1, blue: 0), ArtworkRGB(red: 1, green: 0, blue: 0), ArtworkRGB(red: 0, green: 1, blue: 0), ArtworkRGB(red: 0.1, green: 0.2, blue: 0.9)] {
            let palette = ArtworkPalette(dominant: dominant, secondary: dominant)
            for dark in [true, false] {
                for highContrast in [true, false] {
                    for background in palette.backgroundColors(dark: dark, increasedContrast: highContrast) {
                        XCTAssertGreaterThanOrEqual(background.contrast(with: dark ? .white : .black), 4.5)
                        XCTAssertGreaterThanOrEqual(background.contrast(with: background.mixed(with: dark ? .white : .black, amount: 0.86)), 4.5)
                    }
                }
            }
        }
    }

    func testAppearanceMigrationEnablesColorOnceAndPreservesLaterChoices() throws {
        let suite = "artwork-migration-\(UUID())"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        var settings = UserSettings(themeMode: .dark, downloadsOnlyModeEnabled: true, bookDetailUseCoverBackdrop: true, bookDetailUseArtworkColorScheme: false)
        ArtworkAppearanceMigration.apply(to: &settings, defaults: defaults)
        XCTAssertTrue(settings.bookDetailUseArtworkColorScheme)
        XCTAssertTrue(settings.bookDetailUseCoverBackdrop)
        XCTAssertTrue(settings.downloadsOnlyModeEnabled)
        XCTAssertEqual(settings.themeMode, .dark)
        let stored = try JSONDecoder().decode(UserSettings.self, from: XCTUnwrap(defaults.data(forKey: "settings")))
        XCTAssertEqual(stored, settings)
        settings.bookDetailUseArtworkColorScheme = false
        ArtworkAppearanceMigration.apply(to: &settings, defaults: defaults)
        XCTAssertFalse(settings.bookDetailUseArtworkColorScheme)
        XCTAssertTrue(UserSettings().bookDetailUseArtworkColorScheme)
        XCTAssertTrue(try JSONDecoder().decode(UserSettings.self, from: Data("{}".utf8)).bookDetailUseArtworkColorScheme)
    }

    func testMarqueeRunsOnlyForOverflowWhileVisibleAndMotionIsAllowed() {
        XCTAssertTrue(MarqueeMetrics.shouldScroll(textWidth: 500, availableWidth: 200, active: true, reduceMotion: false, voiceOver: false))
        XCTAssertFalse(MarqueeMetrics.shouldScroll(textWidth: 100, availableWidth: 200, active: true, reduceMotion: false, voiceOver: false))
        XCTAssertFalse(MarqueeMetrics.shouldScroll(textWidth: 500, availableWidth: 200, active: false, reduceMotion: false, voiceOver: false))
        XCTAssertFalse(MarqueeMetrics.shouldScroll(textWidth: 500, availableWidth: 200, active: true, reduceMotion: true, voiceOver: false))
        XCTAssertFalse(MarqueeMetrics.shouldScroll(textWidth: 500, availableWidth: 200, active: true, reduceMotion: false, voiceOver: true))
        XCTAssertEqual(MarqueeMetrics.duration(textWidth: 576), 15, accuracy: 0.001)
    }

    @MainActor
    func testMarqueeKeepsItsCycleDuringPlaybackUpdatesAndStopsWhenHidden() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 320, height: 640)
        let controller = UIViewController()
        window.rootViewController = controller
        window.isHidden = false
        defer { window.isHidden = true }
        let marquee = MarqueeLabelView(frame: CGRect(x: 0, y: 0, width: 160, height: 30))
        controller.view.addSubview(marquee)
        let title = "A very long chapter title that keeps scrolling during playback updates"
        marquee.configure(text: title, font: .headline, color: .label, active: true)
        marquee.layoutIfNeeded()
        let strip = try XCTUnwrap(marquee.subviews.first)
        let animation = try XCTUnwrap(strip.layer.animation(forKey: "title-marquee"))
        XCTAssertGreaterThan(animation.beginTime, 0)

        // Playback invalidates surrounding views every second. Updating the same
        // inputs must not reset the ticker to its initial pause.
        marquee.configure(text: title, font: .headline, color: .white, active: true)
        marquee.layoutIfNeeded()
        XCTAssertEqual(strip.layer.animation(forKey: "title-marquee")?.beginTime, animation.beginTime)

        marquee.configure(text: title, font: .headline, color: .label, active: false)
        marquee.layoutIfNeeded()
        XCTAssertNil(strip.layer.animation(forKey: "title-marquee"))
        marquee.configure(text: title, font: .headline, color: .label, active: true)
        marquee.layoutIfNeeded()
        XCTAssertNotNil(strip.layer.animation(forKey: "title-marquee"))
        marquee.removeFromSuperview()
        XCTAssertNil(strip.layer.animation(forKey: "title-marquee"))
    }

    @MainActor
    func testMarqueeReevaluatesChangedTitlesAndWidths() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 320, height: 640)
        let controller = UIViewController()
        window.rootViewController = controller
        window.isHidden = false
        defer { window.isHidden = true }
        let marquee = MarqueeLabelView(frame: CGRect(x: 0, y: 0, width: 150, height: 30))
        controller.view.addSubview(marquee)
        marquee.configure(text: "A long title that must pan", font: .headline, color: .label, active: true)
        marquee.layoutIfNeeded()
        let strip = try XCTUnwrap(marquee.subviews.first)
        XCTAssertNotNil(strip.layer.animation(forKey: "title-marquee"))
        marquee.configure(text: "Short", font: .headline, color: .label, active: true)
        marquee.layoutIfNeeded()
        XCTAssertNil(strip.layer.animation(forKey: "title-marquee"))
        marquee.configure(text: "A long title that must pan", font: .headline, color: .label, active: true)
        marquee.layoutIfNeeded()
        XCTAssertNotNil(strip.layer.animation(forKey: "title-marquee"))
        marquee.frame.size.width = 1000
        marquee.layoutIfNeeded()
        XCTAssertNil(strip.layer.animation(forKey: "title-marquee"))
    }


    @MainActor
    func testMarqueeUsesSwiftUIDynamicTypeCapInsteadOfLargerWindowTraits() throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 320, height: 640)
        window.traitOverrides.preferredContentSizeCategory = .accessibilityExtraExtraExtraLarge
        let controller = UIViewController()
        window.rootViewController = controller
        window.isHidden = false
        defer { window.isHidden = true }
        let marquee = MarqueeLabelView(frame: CGRect(x: 0, y: 0, width: 160, height: 30))
        controller.view.addSubview(marquee)
        // Trait overrides propagate at UIKit's next update; flush the attached
        // hierarchy so this really exercises a larger surrounding environment.
        window.updateTraitsIfNeeded()
        controller.updateTraitsIfNeeded()
        marquee.updateTraitsIfNeeded()
        XCTAssertEqual(window.traitCollection.preferredContentSizeCategory, .accessibilityExtraExtraExtraLarge)
        XCTAssertEqual(marquee.traitCollection.preferredContentSizeCategory, .accessibilityExtraExtraExtraLarge)
        let cappedCategory = MarqueeMetrics.contentSizeCategory(for: .xxxLarge)
        marquee.configure(text: "Mini player title", font: .subheadline.bold(), color: .label, active: false, contentSizeCategory: cappedCategory)
        marquee.layoutIfNeeded()
        let label = try XCTUnwrap(marquee.subviews.first?.subviews.first as? UILabel)
        let cappedFont = MarqueeFont.subheadline.bold().uiFont(compatibleWith: UITraitCollection(preferredContentSizeCategory: .extraExtraExtraLarge))
        XCTAssertEqual(label.font.pointSize, cappedFont.pointSize)
        XCTAssertEqual(marquee.traitCollection.preferredContentSizeCategory, .accessibilityExtraExtraExtraLarge)

        marquee.configure(text: "Full player title", font: .subheadline.bold(), color: .label, active: false, contentSizeCategory: MarqueeMetrics.contentSizeCategory(for: .accessibility5))
        XCTAssertGreaterThan(label.font.pointSize, cappedFont.pointSize)
        marquee.configure(text: "Mini player title", font: .subheadline.bold(), color: .label, active: false, contentSizeCategory: cappedCategory)
        XCTAssertEqual(label.font.pointSize, cappedFont.pointSize)
    }

    func testMarqueeMapsAllDynamicTypeSizes() {
        let sizes: [DynamicTypeSize] = [.xSmall, .small, .medium, .large, .xLarge, .xxLarge, .xxxLarge, .accessibility1, .accessibility2, .accessibility3, .accessibility4, .accessibility5]
        let expected: [UIContentSizeCategory] = [.extraSmall, .small, .medium, .large, .extraLarge, .extraExtraLarge, .extraExtraExtraLarge, .accessibilityMedium, .accessibilityLarge, .accessibilityExtraLarge, .accessibilityExtraExtraLarge, .accessibilityExtraExtraExtraLarge]
        XCTAssertEqual(sizes.map { MarqueeMetrics.contentSizeCategory(for: $0) }, expected)
    }


    func testChapterTitleCacheAcceptsLegacyAndAlreadyNormalizedBooks() {
        var cache = WolneChapterTitleCache()
        let original = legacyWolneBook()
        let cleaned = cache.normalized(original)
        XCTAssertEqual(cleaned.chapters.map(\.title), ["Tom II, Rozdział I", "Tom II, Rozdział II"])
        XCTAssertEqual(cleaned.chapters.map(\.id), original.chapters.map(\.id))
        XCTAssertEqual(cleaned.chapters.map(\.number), [21, 22])
        XCTAssertEqual(cleaned.chapters.map(\.listenURL), original.chapters.map(\.listenURL))
        XCTAssertEqual(cache.normalized(original), cleaned)
        XCTAssertEqual(cache.normalized(cleaned), cleaned)
    }

    func testChapterTitleCacheRefreshesHydratedMetadataForTheSameBookID() {
        var cache = WolneChapterTitleCache()
        var hydrated = cache.normalized(legacyWolneBook())
        hydrated.chapters[0].title = "Tom II, Rozdział I — Pamiętnik starego subiekta"
        hydrated.author = "Bolesław Prus (Aleksander Głowacki)"
        hydrated.coverImageURL = "https://example.com/new-cover.jpg"
        hydrated.fullCoverImageURL = "https://example.com/new-full-cover.jpg"
        hydrated.libraryStatus = .inLibrary
        hydrated.isFavorite = true
        hydrated.chapters[0].downloadState = .downloaded
        hydrated.chapters[0].localFileName = "downloaded-first.mp3"
        XCTAssertEqual(cache.normalized(hydrated), hydrated)
        XCTAssertEqual(cache.normalized(hydrated), hydrated)
    }

    func testChapterTitleCachePreservesOtherSourcesAndEvictedBooks() {
        var cache = WolneChapterTitleCache(capacity: 1)
        let original = legacyWolneBook()
        let cleaned = cache.normalized(original)
        var another = original
        another.id = "another-book"
        _ = cache.normalized(another)
        XCTAssertEqual(cache.normalized(original), cleaned)
        var libriVoxBook = original
        libriVoxBook.source = .libriVox
        XCTAssertEqual(cache.normalized(libriVoxBook), libriVoxBook)
    }

    private func legacyWolneBook() -> AudioBook {
        AudioBook(id: "wolnelektury-lalka", title: "Lalka", author: "Bolesław Prus", source: .wolneLektury, chapters: [
            AudioBookChapter(id: "lalka-21", title: "01. Boleslaw Prus, Lalka, Lalka, tom drugi, Tom II, Rozdział I", number: 21, reader: "Albert Osik", listenURL: "https://example.com/chapter-21.mp3"),
            AudioBookChapter(id: "lalka-22", title: "Tom II, Rozdział II", number: 22),
        ])
    }

}

final class RecommendationSourceScopeTests: XCTestCase {
    @MainActor
    func testCachedAndSimilarAuthorRecommendationsExcludeDisabledSavedBooks() {
        let restoreDefaults = isolateRecommendationDefaults()
        defer { restoreDefaults() }
        let model = AppModel(catalogClients: [])
        setSources([.wolneLektury], on: model)
        let author = "Source scope author \(UUID())"
        let otherAuthor = "Similar author \(UUID())"
        let seed = makeBook(title: "Seed", author: author, source: .wolneLektury)
        let enabled = makeBook(title: "Enabled work", author: author, source: .wolneLektury)
        let disabled = makeBook(title: "Disabled saved work", author: author, source: .libriVox)
        let disabledRecent = makeBook(title: "Disabled recent work", author: author, source: .lit2Go)
        let enabledRepresentative = makeBook(title: "Enabled similar author", author: otherAuthor, source: .wolneLektury)
        let disabledRepresentative = makeBook(title: "Disabled similar author", author: otherAuthor, source: .libriVox)
        let imported = makeBook(title: "Imported work", author: author, source: .customLocal)
        let library = [seed, enabled, disabled, disabledRepresentative, enabledRepresentative, imported]
        model.libraryBooks = library
        model.recentlyPlayedBooks = [disabledRecent]
        model.featuredBooks = []
        model.searchResults = []

        XCTAssertEqual(model.cachedRelatedBooksByAuthor(for: seed).map(\.id), [enabled.id])
        XCTAssertEqual(model.relatedAuthorRepresentatives(for: seed).map(\.id), [enabledRepresentative.id])
        XCTAssertEqual(model.libraryBooks, library, "Catalog preferences must not remove saved books")
        XCTAssertEqual(model.recentlyPlayedBooks, [disabledRecent])
    }

    @MainActor
    func testDisabledSeedSourceDoesNotGetQueriedForRelatedBooks() async {
        let restoreDefaults = isolateRecommendationDefaults()
        defer { restoreDefaults() }
        let author = "Disabled seed author \(UUID())"
        let seed = makeBook(title: "Saved seed", author: author, source: .libriVox)
        let enabled = makeBook(title: "Polish catalog work", author: author, source: .wolneLektury)
        let disabled = makeBook(title: "Other catalog work", author: author, source: .libriVox)
        let polish = RecommendationCatalogFixture(source: .wolneLektury, books: [enabled])
        let libriVox = RecommendationCatalogFixture(source: .libriVox, books: [disabled])
        let model = AppModel(catalogClients: [polish, libriVox])
        setSources([.wolneLektury], on: model)
        model.libraryBooks = [seed]
        model.recentlyPlayedBooks = []
        model.featuredBooks = []
        model.searchResults = []

        let books = await model.relatedBooksByAuthor(for: seed)
        let polishRequests = await polish.recordedRequests()
        let libriVoxRequests = await libriVox.recordedRequests()
        XCTAssertEqual(books.map(\.id), [enabled.id])
        XCTAssertFalse(polishRequests.isEmpty)
        XCTAssertTrue(polishRequests.allSatisfy { $0.sources == [.wolneLektury] && $0.field == .author })
        XCTAssertTrue(libriVoxRequests.isEmpty, "Opening a saved book must not re-enable its catalog")
        XCTAssertEqual(model.libraryBooks.map(\.id), [seed.id])
    }

    @MainActor
    func testDelayedResultFromPreviousSourceScopeCannotReplaceOrPoisonNewResults() async throws {
        let restoreDefaults = isolateRecommendationDefaults()
        defer { restoreDefaults() }
        let author = "Delayed catalog author \(UUID())"
        let seed = makeBook(title: "Saved seed", author: author, source: .libriVox)
        let oldBook = makeBook(title: "Old catalog work", author: author, source: .libriVox)
        let newBook = makeBook(title: "New catalog work", author: author, source: .wolneLektury)
        let started = expectation(description: "Old catalog request started")
        let completed = expectation(description: "Old catalog request completed")
        let libriVox = RecommendationCatalogFixture(
            source: .libriVox,
            books: [oldBook],
            pauseSearches: true,
            searchStarted: { started.fulfill() }
        )
        let polish = RecommendationCatalogFixture(source: .wolneLektury, books: [newBook])
        let model = AppModel(catalogClients: [libriVox, polish])
        setSources([.libriVox], on: model)
        model.libraryBooks = [seed]
        model.recentlyPlayedBooks = []
        model.featuredBooks = []
        model.searchResults = []
        var oldResults: [AudioBook]?
        let oldRequest = Task { @MainActor in
            oldResults = await model.relatedBooksByAuthor(for: seed)
            completed.fulfill()
        }
        defer { oldRequest.cancel() }
        await fulfillment(of: [started], timeout: 3)

        setSources([.wolneLektury], on: model)
        let newResults = await model.relatedBooksByAuthor(for: seed)
        XCTAssertEqual(newResults.map(\.id), [newBook.id])
        await libriVox.releaseSearches()
        await fulfillment(of: [completed], timeout: 3)

        XCTAssertEqual(try XCTUnwrap(oldResults), [])
        XCTAssertEqual(model.cachedRelatedBooksByAuthor(for: seed).map(\.id), [newBook.id])
        let persisted = try JSONDecoder().decode(
            [String: [AudioBook]].self,
            from: XCTUnwrap(UserDefaults.standard.data(forKey: "relatedAuthorBooksCache"))
        )
        XCTAssertFalse(persisted.values.joined().contains { $0.id == oldBook.id })
        XCTAssertTrue(persisted.values.joined().contains { $0.id == newBook.id })
    }

    @MainActor
    func testReenablingCatalogRestoresItsRecommendationsAndEmptySelectionReturnsNone() async {
        let restoreDefaults = isolateRecommendationDefaults()
        defer { restoreDefaults() }
        let author = "Reenabled catalog author \(UUID())"
        let seed = makeBook(title: "Saved seed", author: author, source: .libriVox)
        let libriVoxBook = makeBook(title: "LibriVox work", author: author, source: .libriVox)
        let polishBook = makeBook(title: "Wolne work", author: author, source: .wolneLektury)
        let libriVox = RecommendationCatalogFixture(source: .libriVox, books: [libriVoxBook])
        let polish = RecommendationCatalogFixture(source: .wolneLektury, books: [polishBook])
        let model = AppModel(catalogClients: [libriVox, polish])
        model.libraryBooks = [seed, libriVoxBook, polishBook]
        model.recentlyPlayedBooks = []
        model.featuredBooks = []
        model.searchResults = []
        setSources([.libriVox, .wolneLektury], on: model)
        let both = await model.relatedBooksByAuthor(for: seed)
        XCTAssertEqual(Set(both.map(\.id)), [libriVoxBook.id, polishBook.id])

        setSources([.wolneLektury], on: model)
        XCTAssertEqual(model.cachedRelatedBooksByAuthor(for: seed).map(\.id), [polishBook.id])
        setSources([.libriVox, .wolneLektury], on: model)
        let restored = await model.relatedBooksByAuthor(for: seed)
        XCTAssertEqual(Set(restored.map(\.id)), [libriVoxBook.id, polishBook.id])

        model.recentlyPlayedBooks = [makeBook(
            title: "Similar author book", author: "A different author \(UUID())", source: .wolneLektury
        )]
        XCTAssertFalse(model.relatedAuthorRepresentatives(for: seed).isEmpty)
        let libriVoxRequestsBefore = await libriVox.recordedRequests().count
        let polishRequestsBefore = await polish.recordedRequests().count
        setSources([], on: model)
        XCTAssertTrue(model.cachedRelatedBooksByAuthor(for: seed).isEmpty)
        XCTAssertTrue(model.relatedAuthorRepresentatives(for: seed).isEmpty)
        let empty = await model.relatedBooksByAuthor(for: seed)
        let libriVoxRequestsAfter = await libriVox.recordedRequests().count
        let polishRequestsAfter = await polish.recordedRequests().count
        XCTAssertTrue(empty.isEmpty)
        XCTAssertEqual(libriVoxRequestsAfter, libriVoxRequestsBefore)
        XCTAssertEqual(polishRequestsAfter, polishRequestsBefore)
        XCTAssertEqual(model.libraryBooks.count, 3)
    }

    private func makeBook(title: String, author: String, source: BookSource) -> AudioBook {
        AudioBook(
            id: "recommendation-test-\(UUID())",
            title: title,
            author: author,
            description: "A complete local test record.",
            source: source,
            chapters: [AudioBookChapter(id: "chapter-1", title: "Chapter one", number: 1)],
            language: "Polish",
            genres: ["Fiction"]
        )
    }

    @MainActor
    private func setSources(_ sources: Set<BookSource>, on model: AppModel) {
        var settings = model.settings
        settings.enabledSources = sources
        settings.preferredLanguages = [.all]
        model.settings = settings
    }

    @MainActor
    private func isolateRecommendationDefaults() -> @MainActor () -> Void {
        let defaults = UserDefaults.standard
        let keys = ["settings", "relatedAuthorBooksCache"]
        let saved = Dictionary(uniqueKeysWithValues: keys.compactMap { key in
            defaults.object(forKey: key).map { (key, $0) }
        })
        defaults.removeObject(forKey: "relatedAuthorBooksCache")
        return {
            for key in keys {
                if let value = saved[key] {
                    defaults.set(value, forKey: key)
                } else {
                    defaults.removeObject(forKey: key)
                }
            }
        }
    }
}

private actor RecommendationCatalogFixture: CatalogSourceClient {
    nonisolated let source: BookSource
    private let books: [AudioBook]
    private let searchStarted: (@Sendable () -> Void)?
    private var pauseSearches: Bool
    private var requests: [CatalogSearchRequest] = []
    private var continuations: [CheckedContinuation<Void, Never>] = []

    init(
        source: BookSource,
        books: [AudioBook],
        pauseSearches: Bool = false,
        searchStarted: (@Sendable () -> Void)? = nil
    ) {
        self.source = source
        self.books = books
        self.pauseSearches = pauseSearches
        self.searchStarted = searchStarted
    }

    func featuredBooks(limit: Int) async throws -> [AudioBook] { [] }

    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        requests.append(request)
        if requests.count == 1 { searchStarted?() }
        if pauseSearches {
            await withCheckedContinuation { continuations.append($0) }
        }
        return books
    }

    func hydrate(_ book: AudioBook) async throws -> AudioBook { book }

    func recordedRequests() -> [CatalogSearchRequest] { requests }

    func releaseSearches() {
        pauseSearches = false
        let waiting = continuations
        continuations.removeAll()
        waiting.forEach { $0.resume() }
    }
}
