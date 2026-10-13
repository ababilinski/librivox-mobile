import AVFoundation
import Foundation
import LibriVoxCore
import MediaPlayer
import Network
import Observation
import OSLog
import UIKit

#if canImport(GroupActivities)
import GroupActivities
#endif

struct DiscoverSearchNavigationRequest: Identifiable, Equatable {
    var id = UUID()
    var query: String
    var field: CatalogSearchField
}

/// Chapter cleanup is needed for older saved books, but must not run on every
/// progress tick or animation frame. Keep metadata changes in the cache key by
/// comparing the complete input, including newly hydrated chapter headings.
struct WolneChapterTitleCache {
    private struct Entry {
        var input: AudioBook
        var normalized: AudioBook
    }

    private var entries: [String: Entry] = [:]
    private var order: [String] = []
    private let capacity: Int

    init(capacity: Int = 64) {
        self.capacity = max(1, capacity)
    }

    mutating func normalized(_ book: AudioBook) -> AudioBook {
        guard book.source == .wolneLektury else { return book }
        if let entry = entries[book.id], entry.input == book || entry.normalized == book {
            return entry.normalized
        }

        let normalized = book.normalizingWolneChapterTitles()
        if entries[book.id] == nil {
            order.append(book.id)
        }
        entries[book.id] = Entry(input: book, normalized: normalized)
        if order.count > capacity {
            entries.removeValue(forKey: order.removeFirst())
        }
        return normalized
    }
}

struct SearchProgressItem: Identifiable, Equatable {
    var operation: CatalogSearchOperation
    var isComplete = false
    var didFail = false
    var resultCount = 0
    var skipReason: CatalogOperationSkipReason?

    var id: String { operation.id }
}

private struct FeaturedCatalogScope: Equatable {
    var enabledSources: Set<BookSource>
    var sourcePriority: [BookSource]

    init(settings: UserSettings) {
        enabledSources = settings.enabledSources
        sourcePriority = settings.sourcePriority
    }
}

private struct RelatedCatalogScope: Equatable {
    var enabledSources: Set<BookSource>
    var preferredLanguages: Set<BookLanguagePreference>
    var sourcePriority: [BookSource]

    init(settings: UserSettings) {
        enabledSources = settings.enabledSources
        preferredLanguages = settings.preferredLanguages
        sourcePriority = settings.sourcePriority
    }
}

enum PlaybackSpeedOptions {
    static let presets: [Double] = [0.25, 0.5, 1.0, 1.25, 1.5, 2.0]

    static func label(for speed: Double) -> String {
        let rounded = (speed * 100).rounded() / 100
        var text = String(format: "%.2f", rounded)
        while text.contains(".") && text.last == "0" {
            text.removeLast()
        }
        if text.last == "." {
            text.removeLast()
        }
        return "\(text)x"
    }
}

struct PlaybackSleepTimerPreset: Identifiable, Equatable {
    var label: String
    var duration: TimeInterval

    var id: TimeInterval { duration }

    static let presets: [PlaybackSleepTimerPreset] = [
        PlaybackSleepTimerPreset(label: "5 min", duration: 5 * 60),
        PlaybackSleepTimerPreset(label: "10 min", duration: 10 * 60),
        PlaybackSleepTimerPreset(label: "15 min", duration: 15 * 60),
        PlaybackSleepTimerPreset(label: "30 min", duration: 30 * 60),
        PlaybackSleepTimerPreset(label: "45 min", duration: 45 * 60),
        PlaybackSleepTimerPreset(label: "1 hour", duration: 60 * 60),
    ]
}

enum PlaybackSleepTimerState: Equatable {
    case off
    case running(totalSeconds: TimeInterval, endsAt: Date)
    case endOfChapter
    case expired

    var isActive: Bool {
        switch self {
        case .running, .endOfChapter:
            true
        case .off, .expired:
            false
        }
    }

    var selectedDuration: TimeInterval? {
        guard case let .running(totalSeconds, _) = self else { return nil }
        return totalSeconds
    }

    func remainingSeconds(at date: Date = .now) -> Int? {
        guard case let .running(_, endsAt) = self else { return nil }
        return max(0, Int(endsAt.timeIntervalSince(date).rounded(.up)))
    }
}

@MainActor
@Observable
final class AppModel {
    var hasCompletedOnboarding: Bool
    var localUserProfile: LocalUserProfile
    var settings: UserSettings {
        didSet {
            guard !isNormalizingSettings else { return }
            let shouldRefreshCatalog = FeaturedCatalogScope(settings: oldValue) != FeaturedCatalogScope(settings: settings)
            let searchCachingChanged = oldValue.automaticSearchCachingEnabled != settings.automaticSearchCachingEnabled
            persistSettings()
            if settings.downloadsOnlyModeEnabled && !oldValue.downloadsOnlyModeEnabled {
                cancelSearch()
            }
            if searchCachingChanged {
                let isEnabled = settings.automaticSearchCachingEnabled
                Task {
                    await catalogSearchCoordinator.setCachingEnabled(isEnabled)
                    await refreshSearchCacheEntryCount()
                }
            }
            if shouldRefreshCatalog {
                refreshFeaturedBooksAfterSettingsChange()
            }
        }
    }
    var libraryBooks: [AudioBook] = AudioBook.seedLibrary
    var featuredBooks: [AudioBook] = AudioBook.seedLibrary
    var isFeaturedBooksLoading = true
    var searchResults: [AudioBook] = AudioBook.seedLibrary
    private(set) var searchResultsRequest: CatalogSearchRequest?
    var downloads: [DownloadItem] = []
    var downloadStorageByBookID: [String: Int64] = [:]
    var downloadStorageByChapterKey: [String: Int64] = [:]
    var downloadFallbackBooksByID: [String: AudioBook] = [:]
    var bookmarks: [Bookmark] = []
    var queue: [QueueEntry] = []
    var progressByBookID: [String: PlaybackProgress] = [:]
    private(set) var hiddenContinueListeningBookIDs: Set<String>
    var recentlyPlayedBooks: [AudioBook] = []
    var playbackFeedbackEntries: [String: PlaybackFeedbackEntry] = [:]
    var searchHistory: [String] = []
    var isOnline = true
    var isSearching = false
    var searchProgressItems: [SearchProgressItem] = []
    var searchFacetCounts: CatalogSearchFacetCounts = .empty
    var routeDiagnostics: [String] = []
    var showingNowPlaying = false
    var discoverSearchNavigationRequest: DiscoverSearchNavigationRequest?
    var readAlongBook: ReadAlongBook?
    var isReaderLoading = false
    var visibleReaderIDs: Set<UUID> = []
    var isReaderPresented: Bool { !visibleReaderIDs.isEmpty }
    var visibleBookDetailBrowserIDs: Set<UUID> = []
    var isBookDetailBrowserVisible: Bool { !visibleBookDetailBrowserIDs.isEmpty }
    var sleepTimerState: PlaybackSleepTimerState = .off
    var searchCacheEntryCount = 0

    let catalog: AggregateCatalogClient
    let catalogSearchCoordinator: CatalogSearchCoordinator
    let libraryStore = PersistentLibraryStore()
    let bookmarkStore = PersistentBookmarkStore()
    let progressStore = PersistentPlaybackProgressStore()
    let recentlyPlayedBookStore = JSONStoreFile<[AudioBook]>(
        fileURL: ApplicationSupportStoreDirectory.fileURL(named: "recently-played-books.json")
    )
    let playbackFeedbackStore = PersistentPlaybackFeedbackStore()
    let searchHistoryStore = PersistentSearchHistoryStore(defaultsKey: UserDefaultsKeys.searchHistory)
    let queueStore = PersistentQueueStore(defaultsKey: UserDefaultsKeys.queue)
    let localUserProfileStore: KeychainLocalUserProfileStore
    let downloadService = PersistentDownloadService()
    private let readAlongLoader: @Sendable (AudioBook) async -> ReadAlongBook
    private var readAlongLoadID: UUID?
    private var readAlongSourceBook: AudioBook?
    let sharePlay = SharePlayCoordinator()
    let playback = PlaybackController()
    private let networkMonitor = NWPathMonitor()
    private let networkQueue = DispatchQueue(label: "LibriVoxMobile.NetworkStatus")
    private var relatedAuthorBooksCache: [String: [AudioBook]]
    private var activeSearchID: UUID?
    private var searchRequestGeneration = UUID()
    private var catalogSearchCancellationTask: Task<Void, Never>?
    private var isNormalizingSettings = false
    private let usesCatalogFixtures: Bool
    private let usesUnifiedSearchFixtures: Bool
    private var lastProgressPersistAt: [String: Date] = [:]
    private var featuredBooksRefreshID: UUID?
    private var catalogSettingsRefreshTask: Task<Void, Never>?
    private var catalogSearchRefreshTask: Task<Void, Never>?
    private var lastCatalogSearchRequest: CatalogSearchRequest?
    private var lastSearchUsesSettingsSources = true
    private var downloadRefreshTask: Task<Void, Never>?
    @ObservationIgnored private var sleepTimerTask: Task<Void, Never>?
    @ObservationIgnored private var sleepTimerResetTask: Task<Void, Never>?
    @ObservationIgnored private var chapterTitleCache = WolneChapterTitleCache()
    private static let downloadRefreshIntervalNanoseconds: UInt64 = 750_000_000
    private static let downloadChapterStorageSeparator = "\u{1F}"
    private static let currentOnboardingVersion = LocalUserProfile.currentOnboardingVersion
    private static let maximumRecentlyPlayedBooks = 12
    private static let maximumContinueListeningBooks = 10

    init(
        catalogClients: [any CatalogSourceClient]? = nil,
        readAlongLoader: @escaping @Sendable (AudioBook) async -> ReadAlongBook = {
            await ReadAlongRepository().load(for: $0)
        }
    ) {
        self.readAlongLoader = readAlongLoader
        let fixtureMode = Self.launchArgument(named: "-catalogFixture")
        usesCatalogFixtures = fixtureMode != nil
        usesUnifiedSearchFixtures = fixtureMode?.hasPrefix("unified") == true
        if usesCatalogFixtures, Self.launchArgument(named: "-continueListeningFixture") == "reset" {
            UserDefaults.standard.removeObject(forKey: UserDefaultsKeys.hiddenContinueListeningBookIDs)
        }
        hiddenContinueListeningBookIDs = Set(
            UserDefaults.standard.stringArray(forKey: UserDefaultsKeys.hiddenContinueListeningBookIDs) ?? []
        )
        if let catalogClients {
            catalog = AggregateCatalogClient(clients: catalogClients)
            catalogSearchCoordinator = CatalogSearchCoordinator(clients: catalogClients, cachingEnabled: false)
        } else if let fixtureMode {
            let clients: [any CatalogSourceClient] = BookSource.defaultPublicCatalogPriority.map {
                CatalogUITestFixtureClient(source: $0, mode: fixtureMode)
            }
            let connectivity = MutableCatalogConnectivity(isOnline: !fixtureMode.contains("offline"))
            catalog = AggregateCatalogClient(clients: clients)
            catalogSearchCoordinator = CatalogSearchCoordinator(
                clients: clients,
                cache: JSONCatalogSearchCache(
                    fileURL: FileManager.default.temporaryDirectory
                        .appendingPathComponent("LibriVoxMobile-UITests", isDirectory: true)
                        .appendingPathComponent("catalog-search.json")
                ),
                connectivity: connectivity
            )
        } else {
            catalog = AggregateCatalogClient()
            catalogSearchCoordinator = CatalogSearchCoordinator()
        }
        let profileStore = KeychainLocalUserProfileStore()
        let storedProfile = profileStore.loadOrCreate()
        localUserProfileStore = profileStore
        localUserProfile = storedProfile
        let completedOnboardingVersion = UserDefaults.standard.integer(forKey: UserDefaultsKeys.onboardingVersion)
        hasCompletedOnboarding = usesCatalogFixtures || !storedProfile.requiresOnboarding(
            completedVersion: completedOnboardingVersion
        )
        var loadedSettings = Self.loadSettings()
        ArtworkAppearanceMigration.apply(to: &loadedSettings, defaults: .standard)
        if !UserDefaults.standard.bool(forKey: UserDefaultsKeys.didAdoptPanelsNowPlayingDefault) {
            loadedSettings.nowPlayingPresentationMode = .panels
            UserDefaults.standard.set(true, forKey: UserDefaultsKeys.didAdoptPanelsNowPlayingDefault)
            if let data = try? JSONEncoder().encode(loadedSettings) {
                UserDefaults.standard.set(data, forKey: UserDefaultsKeys.settings)
            }
        }
        if let themeMode = Self.launchArgument(named: "-themeMode").flatMap(AppThemeMode.init(rawValue:)) {
            loadedSettings.themeMode = themeMode
        }
        if usesCatalogFixtures {
            loadedSettings.enabledSources = Set(BookSource.defaultPublicCatalogPriority)
            loadedSettings.preferredLanguages = [.all]
            loadedSettings.showSearchBarOnDiscover = false
            loadedSettings.animationSpeed = .standard
            loadedSettings.nowPlayingPresentationMode = .panels
            loadedSettings.normalizeForAvailableSources()
        }
        if usesUnifiedSearchFixtures {
            loadedSettings.automaticSearchCachingEnabled = false
            loadedSettings.downloadsOnlyModeEnabled = Self.launchArgument(named: "-unifiedDownloadsOnly") == "YES"
            loadedSettings.showSearchBarOnDiscover = Self.launchArgument(named: "-unifiedSearchOnDiscover") == "YES"
        }
        if let animationSpeed = Self.launchArgument(named: "-animationSpeed").flatMap(AnimationSpeed.init(rawValue:)) {
            loadedSettings.animationSpeed = animationSpeed
        }
        #if DEBUG
        if let artworkColors = Self.launchArgument(named: "-artworkColors") {
            loadedSettings.bookDetailUseArtworkColorScheme = artworkColors == "YES"
        }
        if Self.launchArgument(named: "-readerFixture") == "YES" {
            loadedSettings.readerSettings = ReaderSettings(highlightMode: .paragraph, layoutMode: .scrolling)
        }
        #endif
        let initialFeaturedBooks = Self.fallbackFeaturedBooks(for: loadedSettings)
        settings = loadedSettings
        featuredBooks = initialFeaturedBooks
        searchResults = initialFeaturedBooks
        let storedSearchHistory = UserDefaults.standard.stringArray(forKey: UserDefaultsKeys.searchHistory) ?? []
        let cleanedSearchHistory = Self.cleanedSearchHistory(storedSearchHistory)
        searchHistory = cleanedSearchHistory
        relatedAuthorBooksCache = Self.loadRelatedAuthorBooksCache()
        queue = Self.loadQueue()
        if hasCompletedOnboarding && !storedProfile.hasCompletedOnboarding {
            persistLocalUserProfile { profile in
                profile.hasCompletedOnboarding = true
                profile.touch()
            }
        }
        if cleanedSearchHistory != storedSearchHistory {
            UserDefaults.standard.set(cleanedSearchHistory, forKey: UserDefaultsKeys.searchHistory)
        }
        playback.configureAudioSession()
        bindPlaybackProgress()
        playback.setSpeed(Float(settings.defaultPlaybackSpeed))
        sharePlay.configure(app: self)
        if !usesCatalogFixtures, catalogClients == nil {
            startNetworkMonitor()
        } else if fixtureMode?.contains("offline") == true {
            isOnline = false
        }
        if let fixtureQuery = Self.launchArgument(named: "-catalogQuery")?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !fixtureQuery.isEmpty {
            discoverSearchNavigationRequest = DiscoverSearchNavigationRequest(
                query: fixtureQuery,
                field: .all
            )
        }
        if usesCatalogFixtures,
           Self.launchArgument(named: "-showNowPlaying")?.uppercased() == "YES" {
            showingNowPlaying = true
        }
        if catalogClients == nil {
            Task { await refresh() }
        }
    }

    var defaultListeningBook: AudioBook? {
        playback.currentBook ??
            latestProgressBook ??
            libraryBooks.first { $0.id == AudioBook.theJungle.id } ??
            libraryBooks.first ??
            featuredBooks.first { $0.id == AudioBook.theJungle.id } ??
            featuredBooks.first
    }

    var continueListeningBooks: [AudioBook] {
        let activeBookID = playback.currentBook?.id
        let progressBookIDs = progressByBookID.values
            .filter(\.hasStarted)
            .sorted { $0.updatedAt > $1.updatedAt }
            .map(\.bookID)
        let orderedBookIDs = [activeBookID].compactMap { $0 } + progressBookIDs
        var seenBookIDs = Set<String>()
        var books: [AudioBook] = []
        for bookID in orderedBookIDs {
            guard !hiddenContinueListeningBookIDs.contains(bookID),
                  seenBookIDs.insert(bookID).inserted,
                  let book = latestVersion(bookID: bookID)
            else { continue }
            books.append(book)
            if books.count == Self.maximumContinueListeningBooks { break }
        }
        return books
    }

    func removeFromContinueListening(_ book: AudioBook) {
        hiddenContinueListeningBookIDs.insert(book.id)
        persistHiddenContinueListeningBooks()
    }

    private func persistHiddenContinueListeningBooks() {
        UserDefaults.standard.set(
            hiddenContinueListeningBookIDs.sorted(),
            forKey: UserDefaultsKeys.hiddenContinueListeningBookIDs
        )
    }

    func completeOnboarding() {
        markOnboardingComplete()
        persistSettings()
    }

    func refresh() async {
        if Self.launchArgument(named: "-catalogFixture") == "related-sources" {
            let books = CatalogUITestFixtureClient.relatedSourceBooks
            var saved = books.first { $0.id == "librivox-prus-volume-1" }!
            saved.libraryStatus = .inLibrary
            libraryBooks = [saved]
            recentlyPlayedBooks = [saved]
            featuredBooks = books
            isFeaturedBooksLoading = false
            return
        }
        #if DEBUG
        if Self.launchArgument(named: "-catalogFixture") == "appearance" {
            let books = ArtworkAppearanceFixtures.books
            libraryBooks = books.prefix(2).map { book in
                var saved = book
                saved.libraryStatus = .inLibrary
                return saved
            }
            featuredBooks = books
            recentlyPlayedBooks = Array(books.prefix(1))
            progressByBookID = [books[0].id: PlaybackProgress(bookID: books[0].id, chapterID: books[0].chapters[0].id, positionSeconds: 12, durationSeconds: 60)]
            isFeaturedBooksLoading = false
            return
        }
        #endif
        if usesCatalogFixtures, let fixture = Self.launchArgument(named: "-continueListeningFixture") {
            let books = [AudioBook.theJungle, CatalogUITestFixtureClient.books.first { $0.id == "wolne-antek" }!]
                .map { book in
                    var saved = book
                    saved.libraryStatus = .inLibrary
                    return saved
                }
            if fixture == "reset" {
                for (index, book) in books.enumerated() {
                    await progressStore.save(PlaybackProgress(
                        bookID: book.id,
                        chapterID: book.chapters[0].id,
                        positionSeconds: Double((index + 1) * 120),
                        durationSeconds: 1_200,
                        updatedAt: Date(timeIntervalSince1970: Double(2 - index))
                    ))
                }
            }
            libraryBooks = books
            featuredBooks = books
            recentlyPlayedBooks = books
            let bookIDs = Set(books.map(\.id))
            progressByBookID = await progressStore.snapshot().filter { bookIDs.contains($0.key) }
            isFeaturedBooksLoading = false
            return
        }
        if usesUnifiedSearchFixtures {
            libraryBooks = CatalogUITestFixtureClient.savedBooks
            featuredBooks = CatalogUITestFixtureClient.books
            isFeaturedBooksLoading = false
            return
        }
        // Publish the local library immediately. Catalog hydration enriches the
        // same stable rows afterward instead of withholding every saved book
        // until all network requests finish.
        let cachedLibraryBooks = await libraryStore.snapshot()
        libraryBooks = cachedLibraryBooks

        await catalogSearchCoordinator.setCachingEnabled(settings.automaticSearchCachingEnabled)
        await refreshSearchCacheEntryCount()
        let settingsSnapshot = settings
        recentlyPlayedBooks = await recentlyPlayedBookStore.load() ?? []
        progressByBookID = await progressStore.snapshot()
        playbackFeedbackEntries = await playbackFeedbackStore.snapshot()
        searchHistory = Self.cleanedSearchHistory(await searchHistoryStore.snapshot())
        await searchHistoryStore.save(searchHistory)
        queue = await queueStore.snapshot()
        _ = await hydrateLibrary(cachedLibraryBooks)
        libraryBooks = await libraryStore.snapshot()
        if FeaturedCatalogScope(settings: settingsSnapshot) == FeaturedCatalogScope(settings: settings) {
            await refreshFeaturedBooks(for: settingsSnapshot, mirrorToSearchResults: true)
        }
        let latestDownloads = await refreshDownloadSnapshot()
        await resumeActiveWholeBookDownloadsIfNeeded(from: latestDownloads)
        startDownloadRefreshLoopIfNeeded()
    }

    func search(
        query: String,
        field: CatalogSearchField,
        sources: Set<BookSource>? = nil,
        languages: Set<BookLanguagePreference>? = nil,
        recordHistory: Bool = false
    ) async {
        guard !Task.isCancelled, !settings.downloadsOnlyModeEnabled else { return }
        if recordHistory {
            saveSearchHistory(query)
        }
        let request = CatalogSearchRequest(
            query: query,
            field: field,
            languages: languages ?? settings.preferredLanguages,
            sources: sources ?? settings.enabledSources,
            sourcePriority: settings.sourcePriority
        )
        let searchID = beginSearch()
        defer { finishSearch(searchID) }
        await catalogSearchCancellationTask?.value
        guard searchIsCurrent(searchID) else { return }
        lastCatalogSearchRequest = request
        lastSearchUsesSettingsSources = sources == nil
        searchResultsRequest = request
        let plan = CatalogSearchPlan(request: request)
        setSearchLoading(searchID, operations: plan.operations)
        await catalogSearchCoordinator.setCachingEnabled(settings.automaticSearchCachingEnabled)
        guard searchIsCurrent(searchID) else { return }
        let snapshots = await catalogSearchCoordinator.snapshots(for: request)
        for await snapshot in snapshots {
            guard searchIsCurrent(searchID) else {
                await refreshSearchCacheEntryCount()
                return
            }
            applyCatalogSnapshot(snapshot, searchID: searchID, request: request)
        }
        await refreshSearchCacheEntryCount()
    }

    /// Invalidate visible results immediately, before a replacement query's debounce.
    func cancelSearch() {
        catalogSearchRefreshTask?.cancel()
        activeSearchID = nil
        searchRequestGeneration = UUID()
        lastCatalogSearchRequest = nil
        searchResultsRequest = nil
        searchResults = []
        searchProgressItems = []
        searchFacetCounts = .empty
        isSearching = false
        let previousCancellation = catalogSearchCancellationTask
        catalogSearchCancellationTask = Task {
            await previousCancellation?.value
            await catalogSearchCoordinator.cancel(clearLastRequest: true)
        }
    }

    var searchableLocalBooks: [AudioBook] {
        let downloadedBooks = Set(downloads.filter { $0.state == .downloaded }.map(\.bookID))
            .sorted()
            .compactMap { latestVersion(bookID: $0) }
        var seen: Set<String> = []
        return (libraryBooks + downloadedBooks)
            .filter { book in
                seen.insert("\(book.source.rawValue):\(book.id)").inserted &&
                    (!settings.downloadsOnlyModeEnabled || isAvailableOffline(book))
            }
    }

    func isAvailableOffline(_ book: AudioBook) -> Bool {
        book.chapters.contains { $0.localFileName != nil || $0.downloadState == .downloaded } ||
            downloads.contains { $0.bookID == book.id && $0.state == .downloaded }
    }

    func cachedRelatedBooksByAuthor(for book: AudioBook, limit: Int = 12) -> [AudioBook] {
        let latestBook = latestVersion(of: book)
        let authorKey = Self.normalizedRelatedBookKey(latestBook.author)
        guard !authorKey.isEmpty, limit > 0 else { return [] }

        return cachedRelatedAuthorMatches(
            for: latestBook,
            authorKey: authorKey,
            limit: limit
        )
    }

    func isEnabledRecommendation(_ book: AudioBook) -> Bool {
        book.source.isAvailableCatalogSource && settings.enabledSources.contains(book.source)
    }

    func relatedBooksByAuthor(for book: AudioBook, limit: Int = 12) async -> [AudioBook] {
        let settingsSnapshot = settings
        let scope = RelatedCatalogScope(settings: settingsSnapshot)
        let latestBook = latestVersion(of: book)
        let authorKey = Self.normalizedRelatedBookKey(latestBook.author)
        guard !Task.isCancelled, !authorKey.isEmpty, limit > 0 else { return [] }

        let cachedMatches = cachedRelatedAuthorMatches(
            for: latestBook,
            authorKey: authorKey,
            limit: limit
        )
        let fetchedMatches = await relatedAuthorCatalogMatches(
            for: latestBook,
            authorKey: authorKey,
            limit: limit,
            settingsSnapshot: settingsSnapshot
        )
        guard !Task.isCancelled, scope == RelatedCatalogScope(settings: settings) else { return [] }

        let merged = Self.uniqueRelatedAuthorBooks(
            (cachedMatches + fetchedMatches).filter(isEnabledRecommendation),
            currentBook: latestBook,
            limit: limit
        )
        storeRelatedAuthorBooks(merged, for: latestBook, settingsSnapshot: settingsSnapshot)
        return merged
    }

    func relatedAuthorRepresentatives(for book: AudioBook, limit: Int = 8) -> [AudioBook] {
        guard limit > 0 else { return [] }
        let current = latestKnownVersion(of: book)
        let currentAuthor = Self.normalizedRelatedBookKey(current.author)
        let currentGenres = Set(
            (current.genres + current.literaryGenres.map(\.name))
                .map(Self.normalizedRelatedBookKey)
                .filter { !$0.isEmpty }
        )

        var bestByAuthor: [String: (book: AudioBook, score: Int)] = [:]
        for candidate in knownBooks {
            let latest = latestKnownVersion(of: candidate)
            guard isEnabledRecommendation(latest) else { continue }
            let authorKey = Self.normalizedRelatedBookKey(latest.author)
            guard !authorKey.isEmpty, authorKey != currentAuthor else { continue }

            let candidateGenres = Set(
                (latest.genres + latest.literaryGenres.map(\.name))
                    .map(Self.normalizedRelatedBookKey)
                    .filter { !$0.isEmpty }
            )
            let genreOverlap = currentGenres.intersection(candidateGenres).count
            let score = (genreOverlap * 100) +
                (latest.source == current.source ? 10 : 0) +
                min(catalogDetailScore(latest), 50)

            if score > (bestByAuthor[authorKey]?.score ?? Int.min) {
                bestByAuthor[authorKey] = (latest, score)
            }
        }

        return bestByAuthor.values
            .sorted {
                if $0.score != $1.score { return $0.score > $1.score }
                return $0.book.author.localizedCaseInsensitiveCompare($1.book.author) == .orderedAscending
            }
            .prefix(limit)
            .map(\.book)
    }

    private func cachedRelatedAuthorMatches(
        for latestBook: AudioBook,
        authorKey: String,
        limit: Int
    ) -> [AudioBook] {
        let localMatches = knownBooks.filter {
            isEnabledRecommendation($0) &&
                Self.isRelatedAuthorBook($0, currentBook: latestBook, authorKey: authorKey)
        }

        let relatedCacheKey = Self.relatedAuthorCacheKey(for: latestBook, settings: settings)
        let cachedMatches = relatedAuthorBooksCache[relatedCacheKey] ?? []
        let preparedCachedMatches = mergeKnownArtwork(into: cachedMatches)
            .filter {
                isEnabledRecommendation($0) &&
                    Self.isRelatedAuthorBook($0, currentBook: latestBook, authorKey: authorKey)
            }

        return Self.uniqueRelatedAuthorBooks(
            localMatches + preparedCachedMatches,
            currentBook: latestBook,
            limit: limit
        )
    }

    private func relatedAuthorCatalogMatches(
        for book: AudioBook,
        authorKey: String,
        limit: Int,
        settingsSnapshot: UserSettings
    ) async -> [AudioBook] {
        var matches: [AudioBook] = []
        let scope = RelatedCatalogScope(settings: settingsSnapshot)

        for request in Self.relatedAuthorSearchRequests(for: book, settings: settingsSnapshot) {
            guard !Task.isCancelled, scope == RelatedCatalogScope(settings: settings) else { return [] }
            let fetched = (try? await catalog.search(request)) ?? []
            guard !Task.isCancelled, scope == RelatedCatalogScope(settings: settings) else { return [] }
            let filtered = fetched.filter {
                request.sources.contains($0.source) && isEnabledRecommendation($0) &&
                    Self.isRelatedAuthorBook($0, currentBook: book, authorKey: authorKey)
            }
            guard !filtered.isEmpty else { continue }

            let hydrated = await hydrateIncompleteBooks(in: mergeKnownArtwork(into: filtered))
                .filter {
                    isEnabledRecommendation($0) &&
                        Self.isRelatedAuthorBook($0, currentBook: book, authorKey: authorKey)
                }
            guard !Task.isCancelled, scope == RelatedCatalogScope(settings: settings) else { return [] }
            matches.append(contentsOf: hydrated)

            matches = Self.uniqueRelatedAuthorBooks(matches, currentBook: book, limit: limit)
            if matches.count >= limit { break }
        }

        return matches
    }

    func openDiscoverSearch(query: String, field: CatalogSearchField) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        showingNowPlaying = false
        discoverSearchNavigationRequest = DiscoverSearchNavigationRequest(query: trimmed, field: field)
    }

    func clearSearchCache() {
        relatedAuthorBooksCache.removeAll()
        UserDefaults.standard.removeObject(forKey: UserDefaultsKeys.relatedAuthorBooksCache)
        searchCacheEntryCount = 0
        Task {
            await catalogSearchCoordinator.clearCache()
            await refreshSearchCacheEntryCount()
        }
    }

    private func refreshFeaturedBooksAfterSettingsChange() {
        catalogSettingsRefreshTask?.cancel()
        let settingsSnapshot = settings
        pruneCatalogResultsForCurrentSources()
        catalogSettingsRefreshTask = Task { @MainActor in
            await refreshFeaturedBooks(for: settingsSnapshot, mirrorToSearchResults: false)
        }
        refreshActiveSearchAfterSettingsChange()
    }

    private func refreshActiveSearchAfterSettingsChange() {
        guard let previous = lastCatalogSearchRequest,
              !previous.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }
        catalogSearchRefreshTask?.cancel()
        let usesSettingsSources = lastSearchUsesSettingsSources
        let nextSources = usesSettingsSources
            ? settings.enabledSources
            : previous.sources.intersection(settings.enabledSources)
        catalogSearchRefreshTask = Task { @MainActor in
            await search(
                query: previous.query,
                field: previous.field,
                sources: nextSources,
                languages: previous.languages
            )
            if usesSettingsSources, self.lastCatalogSearchRequest?.query == previous.query {
                self.lastSearchUsesSettingsSources = true
            }
        }
    }

    private func pruneCatalogResultsForCurrentSources() {
        let enabledSources = settings.enabledSources
        featuredBooks = featuredBooks.filter { enabledSources.contains($0.source) }
        searchResults = searchResults.filter { enabledSources.contains($0.source) }
    }

    private func refreshFeaturedBooks(
        for settingsSnapshot: UserSettings,
        mirrorToSearchResults: Bool
    ) async {
        let refreshID = UUID()
        featuredBooksRefreshID = refreshID
        isFeaturedBooksLoading = true
        defer {
            if featuredBooksRefreshID == refreshID {
                isFeaturedBooksLoading = false
            }
        }

        let books = await featuredBooks(for: settingsSnapshot)
        guard !Task.isCancelled,
              featuredBooksRefreshID == refreshID,
              FeaturedCatalogScope(settings: settingsSnapshot) == FeaturedCatalogScope(settings: settings)
        else { return }

        featuredBooks = books
        if mirrorToSearchResults, lastCatalogSearchRequest == nil {
            searchResults = books
        }
    }

    private func featuredBooks(for settingsSnapshot: UserSettings) async -> [AudioBook] {
        let fallbackFeaturedBooks = Self.fallbackFeaturedBooks(for: settingsSnapshot)
        guard !settingsSnapshot.orderedEnabledSources.isEmpty else { return [] }
        let fetchedFeaturedBooks = try? await catalog.featuredBooks(
            limit: 12,
            sourcePriority: settingsSnapshot.orderedEnabledSources
        )
        return fetchedFeaturedBooks?.isEmpty == false ? fetchedFeaturedBooks ?? [] : fallbackFeaturedBooks
    }

    private static func fallbackFeaturedBooks(for settings: UserSettings) -> [AudioBook] {
        AudioBook.seedLibrary.filter { settings.enabledSources.contains($0.source) }
    }

    func saveToLibrary(_ book: AudioBook) async {
        var saved = latestVersion(of: book)
        let wasInLibrary = saved.libraryStatus == .inLibrary
        saved.libraryStatus = .inLibrary
        await libraryStore.save(saved)
        applyBookUpdate(saved)
        libraryBooks = await libraryStore.snapshot()
        if settings.shouldAutoDownloadLibraryAddition(wasInLibrary: wasInLibrary) {
            await enqueueDownload(saved)
        }
    }

    func removeFromLibrary(_ book: AudioBook) async {
        let latest = latestVersion(of: book)
        await libraryStore.remove(bookID: latest.id)
        var updated = latest
        updated.libraryStatus = .notInLibrary
        updated.isFavorite = false
        updated.addedAt = nil
        applyBookUpdate(updated)
        libraryBooks = await libraryStore.snapshot()
    }

    func isInLibrary(_ book: AudioBook) -> Bool {
        latestVersion(of: book).libraryStatus == .inLibrary
    }

    func enqueueDownload(_ book: AudioBook) async {
        await downloadService.enqueue(book: latestVersion(of: book))
        await refreshDownloadSnapshot()
        startDownloadRefreshLoopIfNeeded()
    }

    func enqueueDownload(_ book: AudioBook, chapter: AudioBookChapter) async {
        await downloadService.enqueue(book: latestVersion(of: book), chapter: chapter)
        await refreshDownloadSnapshot()
        startDownloadRefreshLoopIfNeeded()
    }

    func cancelDownload(bookID: String) async {
        await downloadService.cancel(bookID: bookID)
        await refreshDownloadSnapshot()
    }

    func deleteDownload(bookID: String) async {
        await downloadService.delete(bookID: bookID)
        await refreshDownloadSnapshot()
    }

    func deleteDownload(book: AudioBook, chapter: AudioBookChapter) async {
        await downloadService.delete(book: latestVersion(of: book), chapter: chapter)
        await refreshDownloadSnapshot()
    }

    func reloadDownloads() async {
        let latestDownloads = await refreshDownloadSnapshot()
        await resumeActiveWholeBookDownloadsIfNeeded(from: latestDownloads)
        startDownloadRefreshLoopIfNeeded()
    }

    func download(for book: AudioBook) -> DownloadItem? {
        downloads.first { item in
            item.bookID == book.id && item.chapterID == nil
        }
    }

    func downloads(for book: AudioBook) -> [DownloadItem] {
        downloads.filter { item in
            item.bookID == book.id
        }
    }

    func download(for book: AudioBook, chapter: AudioBookChapter) -> DownloadItem? {
        let bookDownloads = downloads(for: book)
        if let exactItem = bookDownloads.first(where: { $0.chapterID == chapter.id }) {
            return exactItem
        }
        guard let aggregateItem = bookDownloads.first(where: { $0.chapterID == nil }) else {
            return nil
        }
        if aggregateItem.state == .downloaded {
            return DownloadItem(
                bookID: book.id,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: .downloaded,
                progress: 1
            )
        }
        guard Self.isActiveDownloadState(aggregateItem.state) else {
            return nil
        }
        let playableChapters = book.chapters.filter { $0.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false }
        guard let index = playableChapters.firstIndex(where: { $0.id == chapter.id }),
              !playableChapters.isEmpty
        else { return nil }

        let rawProgress = min(max(aggregateItem.progress, 0), 1) * Double(playableChapters.count)
        let completedCount = Int(rawProgress.rounded(.down))
        if index < completedCount {
            return DownloadItem(
                bookID: book.id,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: .downloaded,
                progress: 1
            )
        }
        if index == completedCount {
            return DownloadItem(
                bookID: book.id,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: aggregateItem.state,
                progress: min(max(rawProgress - Double(completedCount), 0), 1)
            )
        }
        return DownloadItem(
            bookID: book.id,
            chapterID: chapter.id,
            title: chapter.displayTitle,
            state: .queued,
            progress: 0
        )
    }

    func hasActiveDownload(for book: AudioBook) -> Bool {
        downloads(for: book).contains { Self.isActiveDownloadState($0.state) }
    }

    func hasDownload(for book: AudioBook) -> Bool {
        downloadBadgeState(for: book) != nil
    }

    func storageBytes(for download: DownloadItem) -> Int64 {
        downloadStorageByBookID[download.bookID] ?? 0
    }

    func chapterStorageBytes(for bookID: String) -> [String: Int64] {
        var result: [String: Int64] = [:]
        for (key, bytes) in downloadStorageByChapterKey {
            let parts = key.components(separatedBy: Self.downloadChapterStorageSeparator)
            guard parts.count == 2, parts[0] == bookID else { continue }
            result[parts[1]] = bytes
        }
        return result
    }

    func book(for download: DownloadItem) -> AudioBook? {
        latestVersion(bookID: download.bookID)
    }

    func downloadSourceLabel(for download: DownloadItem) -> String? {
        knownBook(bookID: download.bookID)?.source.label ?? download.bookSource?.label
    }

    func downloadBadgeState(for book: AudioBook) -> DownloadState? {
        let bookDownloads = downloads(for: book)
        if bookDownloads.contains(where: { $0.chapterID == nil && $0.state == .downloaded }) {
            return .downloaded
        }
        if bookDownloads.contains(where: { $0.state == .queued }) {
            return .queued
        }
        if bookDownloads.contains(where: { $0.state == .downloading }) {
            return .downloading
        }
        if allDownloadableChaptersAreDownloaded(book, downloads: bookDownloads) {
            return .downloaded
        }
        if bookDownloads.contains(where: { $0.state == .failed }) {
            return .failed
        }
        return nil
    }

    func addBookmark(for book: AudioBook? = nil, positionSeconds: Double? = nil, note: String = "") async {
        guard let context = currentBookmarkContext(for: book, positionSeconds: positionSeconds) else { return }
        let bookmark = Bookmark(
            bookID: context.book.id,
            chapterID: context.chapter.id,
            positionMilliseconds: Int(context.positionSeconds * 1000),
            note: note
        )
        await bookmarkStore.save(bookmark)
        bookmarks = await bookmarkStore.bookmarks(for: context.book.id)
    }

    func removeBookmark(_ bookmark: Bookmark) async {
        await bookmarkStore.remove(bookmarkID: bookmark.id)
        bookmarks = await bookmarkStore.bookmarks(for: bookmark.bookID)
    }

    func currentPlayheadBookmark(
        for book: AudioBook? = nil,
        positionSeconds: Double? = nil,
        toleranceMilliseconds: Int = 1_500
    ) -> Bookmark? {
        guard let context = currentBookmarkContext(for: book, positionSeconds: positionSeconds) else { return nil }
        return playheadBookmark(
            bookID: context.book.id,
            chapterID: context.chapter.id,
            positionMilliseconds: Int(context.positionSeconds * 1000),
            bookmarks: bookmarks,
            toleranceMilliseconds: toleranceMilliseconds
        )
    }

    func canBookmarkCurrentPlayhead(for book: AudioBook? = nil) -> Bool {
        currentBookmarkContext(for: book) != nil
    }

    func toggleCurrentPlayheadBookmark(for book: AudioBook? = nil, positionSeconds: Double? = nil) async {
        guard let context = currentBookmarkContext(for: book, positionSeconds: positionSeconds) else { return }
        if let bookmark = currentPlayheadBookmark(for: context.book, positionSeconds: context.positionSeconds) {
            await removeBookmark(bookmark)
        } else {
            await addBookmark(for: context.book, positionSeconds: context.positionSeconds)
        }
        await reloadBookmarks(for: context.book.id)
    }

    func reloadBookmarks(for bookID: String? = nil) async {
        guard let bookID = bookID ?? playback.currentBook?.id else {
            bookmarks = []
            return
        }
        bookmarks = await bookmarkStore.bookmarks(for: bookID)
    }

    private func currentBookmarkContext(
        for book: AudioBook? = nil,
        positionSeconds: Double? = nil
    ) -> (book: AudioBook, chapter: AudioBookChapter, positionSeconds: Double)? {
        if let currentBook = playback.currentBook,
           book?.id == nil || book?.id == currentBook.id,
           let chapter = playback.currentChapter {
            return (
                currentBook,
                chapter,
                playback.isDurationKnown
                    ? min(max(positionSeconds ?? playback.currentTimeSeconds, 0), playback.durationSeconds)
                    : max(positionSeconds ?? playback.currentTimeSeconds, 0)
            )
        }

        guard let book = book ?? defaultListeningBook,
              let progress = progress(for: book),
              progress.hasStarted,
              let chapter = book.chapters.first(where: { $0.id == progress.chapterID })
        else { return nil }

        return (
            book,
            chapter,
            progress.durationSeconds > 0
                ? min(max(positionSeconds ?? progress.positionSeconds, 0), progress.durationSeconds)
                : max(positionSeconds ?? progress.positionSeconds, 0)
        )
    }

    func reloadPlaybackFeedback() async {
        playbackFeedbackEntries = await playbackFeedbackStore.snapshot()
    }

    func playbackFeedback(
        bookID: String?,
        chapterID: String?,
        scope: FeedbackControlScope? = nil
    ) -> PlaybackFeedback {
        let scope = scope ?? settings.feedbackControlScope
        guard let key = PlaybackFeedbackEntry.key(bookID: bookID, chapterID: chapterID, scope: scope)
        else { return .none }
        return playbackFeedbackEntries[key]?.feedback ?? .none
    }

    func togglePlaybackFeedback(_ target: PlaybackFeedback) async {
        guard target != .none else { return }
        let fallbackBook = defaultListeningBook
        let fallbackChapter = fallbackBook.flatMap { progressChapter(for: $0) }
        _ = await playbackFeedbackStore.toggleFeedback(
            bookID: playback.currentBook?.id ?? fallbackBook?.id,
            chapterID: playback.currentChapter?.id ?? fallbackChapter?.id,
            target: target,
            scope: settings.feedbackControlScope
        )
        await reloadPlaybackFeedback()
    }

    func toggleFavorite(_ book: AudioBook) async {
        var updated = latestVersion(of: book)
        let wasInLibrary = updated.libraryStatus == .inLibrary
        updated.isFavorite.toggle()
        updated.libraryStatus = .inLibrary
        await libraryStore.save(updated)
        applyBookUpdate(updated)
        libraryBooks = await libraryStore.snapshot()
        if settings.shouldAutoDownloadLibraryAddition(wasInLibrary: wasInLibrary) {
            await enqueueDownload(updated)
        }
    }

    func play(
        _ book: AudioBook,
        chapter: AudioBookChapter? = nil,
        startAt startOverride: Double? = nil,
        presentNowPlaying: Bool = true
    ) {
        let latest = latestVersion(of: book)
        if hiddenContinueListeningBookIDs.remove(latest.id) != nil {
            persistHiddenContinueListeningBooks()
        }
        recordRecentlyPlayedBook(latest)
        let savedProgress = progressByBookID[latest.id]
        let progressChapter = savedProgress.flatMap { progress in
            latest.chapters.first { $0.id == progress.chapterID }
        }
        let selectedChapter = chapter ?? progressChapter ?? latest.chapters.first
        let savedStart = selectedChapter?.id == savedProgress?.chapterID ? savedProgress?.positionSeconds ?? 0 : 0
        let startSeconds = startOverride ?? savedStart
        Task { @MainActor in
            let mediaURL: URL? = if let selectedChapter {
                await downloadService.localFileURL(bookID: latest.id, chapter: selectedChapter)
            } else {
                nil
            }

            playback.play(book: latest, chapter: selectedChapter, startAt: startSeconds, mediaURL: mediaURL)
            showingNowPlaying = presentNowPlaying
            await reloadBookmarks(for: latest.id)
            switch settings.autoDownloadMode {
            case .off:
                break
            case .currentChapter:
                if let chapter = selectedChapter {
                    await downloadService.markDownloaded(bookID: latest.id, chapterID: chapter.id)
                    downloads = await downloadService.downloads()
                }
            case .currentBook:
                await enqueueDownload(latest)
            }
        }
    }

    func playDefaultBook() {
        guard let book = defaultListeningBook else { return }
        play(book, chapter: playback.currentChapter)
    }

    func startSharePlayForCurrentBook() async -> String {
        await sharePlay.startSharePlay()
    }

    func leaveSharePlay() {
        sharePlay.leaveSharePlay()
    }

    func loadReadAlong(for book: AudioBook) async {
        let latest = latestVersion(of: book)
        if readAlongBook?.bookID == latest.id, readAlongSourceBook == latest { return }
        let loadID = UUID()
        readAlongLoadID = loadID
        isReaderLoading = true
        defer {
            if readAlongLoadID == loadID { isReaderLoading = false }
        }
        let loaded: ReadAlongBook
        #if DEBUG
        if Self.launchArgument(named: "-catalogFixture") == "appearance",
           Self.launchArgument(named: "-readerFixture") == "YES" {
            loaded = ArtworkAppearanceFixtures.readerDocument(for: latest)
        } else {
            loaded = await readAlongLoader(latest)
        }
        #else
        loaded = await readAlongLoader(latest)
        #endif
        guard !Task.isCancelled, readAlongLoadID == loadID else { return }
        readAlongBook = loaded
        readAlongSourceBook = latest
    }

    func activeReadAlongSegment(for book: AudioBook) -> ReadAlongSegment? {
        guard playback.currentBook?.id == book.id,
              let readAlongBook,
              readAlongBook.bookID == book.id
        else { return nil }
        return readAlongBook.activeSegment(
            chapterID: playback.currentChapter?.id,
            positionMilliseconds: Int(playback.currentTimeSeconds * 1000)
        )
    }

    func progress(for book: AudioBook) -> PlaybackProgress? {
        if playback.currentBook?.id == book.id,
           let chapter = playback.currentChapter {
            return PlaybackProgress(
                bookID: book.id,
                chapterID: chapter.id,
                positionSeconds: playback.currentTimeSeconds,
                durationSeconds: playback.durationSeconds,
                completedChapterIDs: progressByBookID[book.id]?.completedChapterIDs ?? [],
                updatedAt: progressByBookID[book.id]?.updatedAt ?? .now
            )
        }
        return progressByBookID[book.id]
    }

    func progressChapter(for book: AudioBook) -> AudioBookChapter? {
        guard let progress = progress(for: book) else { return nil }
        return book.chapters.first { $0.id == progress.chapterID }
    }

    func progressSummary(for book: AudioBook) -> String? {
        guard let progress = progress(for: book), progress.hasStarted else { return nil }
        let chapterTitle = progressChapter(for: book)?.displayTitle ?? "In progress"
        return "\(chapterTitle) · \(formatDuration(seconds: Int(progress.positionSeconds)))"
    }

    func handleOpenURL(_ url: URL) {
        guard url.scheme == "librivoxmobile" else { return }
        markOnboardingComplete()
        let candidateID = url.host == "book"
            ? url.pathComponents.dropFirst().first
            : url.host
        guard let candidateID,
              let book = (libraryBooks + featuredBooks).first(where: { $0.id == candidateID })
        else { return }
        play(book, chapter: book.chapters.first)
    }

    func enqueue(_ book: AudioBook, chapter: AudioBookChapter? = nil) {
        queue.append(QueueEntry(bookID: book.id, chapterID: chapter?.id))
        persistQueue()
    }

    func book(for entry: QueueEntry) -> AudioBook? {
        (libraryBooks + featuredBooks + searchResults + [playback.currentBook].compactMap { $0 })
            .first { $0.id == entry.bookID }
    }

    func setPlaybackSpeed(_ speed: Double) {
        settings.defaultPlaybackSpeed = speed
        playback.setSpeed(Float(speed))
        persistSettings()
    }

    func setAutoDownloadLibraryAdditions(_ isEnabled: Bool) {
        settings.autoDownloadLibraryAdditions = isEnabled
    }

    func setSleepTimer(duration: TimeInterval) {
        guard duration > 0 else {
            cancelSleepTimer()
            return
        }

        sleepTimerTask?.cancel()
        sleepTimerResetTask?.cancel()
        let endsAt = Date().addingTimeInterval(duration)
        sleepTimerState = .running(totalSeconds: duration, endsAt: endsAt)
        sleepTimerTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                self?.expireSleepTimer(pausingPlayback: true)
            }
        }
    }

    func setSleepTimerEndOfChapter() {
        sleepTimerTask?.cancel()
        sleepTimerResetTask?.cancel()
        sleepTimerTask = nil
        sleepTimerResetTask = nil
        sleepTimerState = .endOfChapter
    }

    func cancelSleepTimer() {
        sleepTimerTask?.cancel()
        sleepTimerResetTask?.cancel()
        sleepTimerTask = nil
        sleepTimerResetTask = nil
        sleepTimerState = .off
    }

    func sleepTimerRemainingSeconds(at date: Date = .now) -> Int? {
        sleepTimerState.remainingSeconds(at: date)
    }

    func persistSettings() {
        var normalized = settings
        normalized.normalizeForAvailableSources()
        if normalized != settings {
            isNormalizingSettings = true
            settings = normalized
            isNormalizingSettings = false
        }
        if let data = try? JSONEncoder().encode(normalized) {
            UserDefaults.standard.set(data, forKey: UserDefaultsKeys.settings)
        }
    }

    private static func loadSettings() -> UserSettings {
        guard let data = UserDefaults.standard.data(forKey: UserDefaultsKeys.settings),
              let decoded = try? JSONDecoder().decode(UserSettings.self, from: data)
        else {
            return UserSettings()
        }
        var settings = decoded
        settings.normalizeForAvailableSources()
        return settings
    }

    private var latestProgress: PlaybackProgress? {
        progressByBookID.values
            .filter(\.hasStarted)
            .sorted { $0.updatedAt > $1.updatedAt }
            .first
    }

    private var latestProgressBook: AudioBook? {
        guard let progress = latestProgress else { return nil }
        return latestVersion(bookID: progress.bookID)
    }

    private var knownBooks: [AudioBook] {
        libraryBooks + featuredBooks + searchResults + recentlyPlayedBooks + [playback.currentBook].compactMap { $0 }
    }

    func latestKnownVersion(of book: AudioBook) -> AudioBook {
        let candidates = knownBooks.filter { $0.id == book.id } + [book]
        var richest = candidates.max { catalogDetailScore($0) < catalogDetailScore($1) } ?? book
        if let libraryVersion = libraryBooks.first(where: { $0.id == book.id }) {
            richest.libraryStatus = libraryVersion.libraryStatus
            richest.isFavorite = libraryVersion.isFavorite
            richest.addedAt = libraryVersion.addedAt
        }
        return chapterTitleCache.normalized(richest)
    }

    func hydrateBookDetails(_ book: AudioBook) async -> AudioBook {
        let current = latestKnownVersion(of: book)
        do {
            let hydrated = try await catalog.hydrate(current)
                .preservingUserState(from: current)
            applyBookUpdate(hydrated)
            return latestKnownVersion(of: hydrated)
        } catch {
            return current
        }
    }

    private func catalogDetailScore(_ book: AudioBook) -> Int {
        (book.description.isEmpty ? 0 : min(book.description.count, 1_000)) +
            (book.chapters.count * 40) +
            (book.narrators.count * 20) +
            (book.translators.count * 20) +
            (book.genres.count * 5) +
            (book.fullCoverImageURL == nil ? 0 : 20) +
            (book.publisher == nil ? 0 : 10) +
            (book.releaseDate == nil ? 0 : 10)
    }

    private func knownBook(bookID: String) -> AudioBook? {
        guard let book = knownBooks.first(where: { $0.id == bookID }) else { return nil }
        return latestKnownVersion(of: book)
    }

    private func latestVersion(bookID: String) -> AudioBook? {
        knownBook(bookID: bookID) ?? downloadFallbackBooksByID[bookID]
    }

    func bookForSharePlay(bookID: String) -> AudioBook? {
        latestVersion(bookID: bookID)
    }

    func currentSharePlaySnapshot(reason: AudiobookSharePlayMessage.Reason = .snapshot) -> AudiobookSharePlayMessage? {
        guard let book = playback.currentBook ?? defaultListeningBook else { return nil }
        let chapter = playback.currentBook == nil ? progressChapter(for: book) : playback.currentChapter
        let position = playback.currentBook == nil ? progress(for: book)?.positionSeconds ?? 0 : playback.currentTimeSeconds
        return AudiobookSharePlayMessage(
            reason: reason,
            bookID: book.id,
            bookTitle: book.title,
            chapterID: chapter?.id,
            positionSeconds: position,
            isPlaying: playback.currentBook != nil && playback.isPlaying
        )
    }

    func applySharePlayMessage(_ message: AudiobookSharePlayMessage) async {
        guard let book = bookForSharePlay(bookID: message.bookID) else {
            recordRouteDiagnostic("SharePlay ignored unavailable book \(message.bookID)")
            return
        }
        let chapter = message.chapterID.flatMap { chapterID in
            book.chapters.first { $0.id == chapterID }
        } ?? book.chapters.first
        let resolvedDuration = PlaybackDurationPolicy.initialDuration(
            chapterDurationSeconds: chapter?.durationSeconds,
            bookTotalDurationSeconds: book.totalDurationSeconds,
            chapterCount: book.chapters.count
        )
        let startSeconds = PlaybackDurationPolicy.boundedPosition(
            message.positionSeconds,
            resolvedDuration: resolvedDuration
        )
        let mediaURL: URL? = if let chapter {
            await downloadService.localFileURL(bookID: book.id, chapter: chapter)
        } else {
            nil
        }
        if playback.currentBook?.id == book.id,
           playback.currentChapter?.id == chapter?.id,
           abs(playback.currentTimeSeconds - startSeconds) < 1,
           playback.isPlaying == message.isPlaying {
            showingNowPlaying = true
            await reloadBookmarks(for: book.id)
            return
        }
        recordRecentlyPlayedBook(book)
        playback.play(
            book: book,
            chapter: chapter,
            startAt: startSeconds,
            mediaURL: mediaURL,
            autoplay: message.isPlaying,
            broadcastSharePlay: false
        )
        showingNowPlaying = true
        await reloadBookmarks(for: book.id)
    }

    func recordRouteDiagnostic(_ message: String) {
        routeDiagnostics.insert("\(Date.now.formatted(date: .omitted, time: .standard)): \(message)", at: 0)
        if routeDiagnostics.count > 40 {
            routeDiagnostics.removeLast(routeDiagnostics.count - 40)
        }
    }

    private func markOnboardingComplete() {
        hasCompletedOnboarding = true
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.hasCompletedOnboarding)
        UserDefaults.standard.set(Self.currentOnboardingVersion, forKey: UserDefaultsKeys.onboardingVersion)
        persistLocalUserProfile { profile in
            profile.markOnboardingComplete()
        }
    }

    private func persistLocalUserProfile(_ update: (inout LocalUserProfile) -> Void) {
        var profile = localUserProfile
        update(&profile)
        localUserProfile = profile
        _ = localUserProfileStore.save(profile)
    }

    private func hydrateLibrary(_ books: [AudioBook]) async -> [AudioBook] {
        var hydrated: [AudioBook] = []
        for book in books {
            let sourceBook = (try? await catalog.hydrate(book)) ?? book
            hydrated.append(sourceBook)
            if sourceBook != book {
                await libraryStore.save(sourceBook)
            }
        }
        return hydrated.sorted { lhs, rhs in
            if lhs.id == AudioBook.theJungle.id { return true }
            if rhs.id == AudioBook.theJungle.id { return false }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
    }

    private func latestVersion(of book: AudioBook) -> AudioBook {
        latestVersion(bookID: book.id) ?? book
    }

    private func beginSearch() -> UUID {
        let searchID = UUID()
        activeSearchID = searchID
        searchRequestGeneration = searchID
        searchProgressItems = []
        return searchID
    }

    private func setSearchLoading(
        _ searchID: UUID,
        operations: [CatalogSearchOperation] = []
    ) {
        guard activeSearchID == searchID else { return }
        isSearching = true
        searchResults = []
        searchFacetCounts = .empty
        searchProgressItems = operations.map { SearchProgressItem(operation: $0) }
    }

    private func updateSearchProgress(
        _ searchID: UUID,
        operation: CatalogSearchOperation,
        resultCount: Int,
        didSucceed: Bool
    ) {
        guard activeSearchID == searchID else { return }
        if let index = searchProgressItems.firstIndex(where: { $0.operation == operation }) {
            searchProgressItems[index].isComplete = true
            searchProgressItems[index].didFail = !didSucceed
            searchProgressItems[index].resultCount = resultCount
        }
    }

    private func applyCatalogSnapshot(
        _ snapshot: CatalogSearchSnapshot,
        searchID: UUID,
        request: CatalogSearchRequest,
        preserveVisibleResultsWhilePending: Bool = false
    ) {
        guard activeSearchID == searchID else { return }
        for operation in CatalogSearchPlan(request: request).operations {
            guard let outcome = snapshot.outcomes[operation.id],
                  let index = searchProgressItems.firstIndex(where: { $0.operation == operation })
            else { continue }
            switch outcome {
            case .pending:
                searchProgressItems[index].isComplete = false
                searchProgressItems[index].didFail = false
                searchProgressItems[index].skipReason = nil
            case .success(let count):
                searchProgressItems[index].isComplete = true
                searchProgressItems[index].didFail = false
                searchProgressItems[index].resultCount = count
                searchProgressItems[index].skipReason = nil
            case .authoritativeEmpty:
                searchProgressItems[index].isComplete = true
                searchProgressItems[index].didFail = false
                searchProgressItems[index].resultCount = 0
                searchProgressItems[index].skipReason = nil
            case .skipped(let reason):
                searchProgressItems[index].isComplete = true
                searchProgressItems[index].didFail = false
                searchProgressItems[index].resultCount = 0
                searchProgressItems[index].skipReason = reason
            case .failed:
                searchProgressItems[index].isComplete = true
                searchProgressItems[index].didFail = true
                searchProgressItems[index].resultCount = 0
                searchProgressItems[index].skipReason = nil
            }
        }

        let hasResolvedRetrieval = snapshot.outcomes.values.contains { outcome in
            switch outcome {
            case .success, .authoritativeEmpty, .failed:
                true
            case .pending, .skipped:
                false
            }
        }
        if preserveVisibleResultsWhilePending,
           snapshot.hits.isEmpty,
           !hasResolvedRetrieval,
           !searchResults.isEmpty {
            return
        }

        let prepared = mergeKnownArtwork(into: snapshot.hits.map(\.book))
        searchResults = prepared
        let languageCounts = prepared.catalogSearchFacetCounts(for: request).languageCounts
        searchFacetCounts = CatalogSearchFacetCounts(
            total: prepared.count,
            sourceCounts: snapshot.sourceCounts,
            fieldCounts: snapshot.fieldCounts,
            languageCounts: languageCounts
        )
    }

    private func searchIsCurrent(_ searchID: UUID) -> Bool {
        activeSearchID == searchID && !Task.isCancelled
    }

    private func finishSearch(_ searchID: UUID) {
        guard activeSearchID == searchID else { return }
        activeSearchID = nil
        isSearching = false
    }

    private func mergeProgressiveSearchResults(
        _ booksBySource: [BookSource: [AudioBook]],
        for request: CatalogSearchRequest
    ) -> [AudioBook] {
        CatalogBookMerger.merge(
            request.orderedSources.map { booksBySource[$0] ?? [] }
        )
        .rankedForCatalogSearch(query: request.query, field: request.field, keepUnmatched: false)
    }

    private func baseSearchRequest(for request: CatalogSearchRequest) -> CatalogSearchRequest {
        CatalogSearchRequest(
            query: request.query,
            field: .all,
            languages: [.all],
            sources: settings.enabledSources,
            sourcePriority: settings.sourcePriority
        )
    }

    private func applySearchResults(
        _ baseResults: [AudioBook],
        for request: CatalogSearchRequest
    ) {
        searchResults = baseResults.filteredForCatalogSearch(request)
        searchFacetCounts = baseResults.catalogSearchFacetCounts(for: request)
    }

    private func mergeKnownArtwork(into books: [AudioBook]) -> [AudioBook] {
        books.map(mergeKnownArtwork(into:))
    }

    private func mergeKnownArtwork(into book: AudioBook) -> AudioBook {
        guard let known = (libraryBooks + featuredBooks + [playback.currentBook].compactMap { $0 } + searchResults)
            .first(where: { $0.id == book.id && $0.source == book.source })
        else { return book }

        var merged = book.preservingUserState(from: known)
        if merged.coverImageURL?.isEmpty != false {
            merged.coverImageURL = known.coverImageURL
        }
        if merged.fullCoverImageURL?.isEmpty != false {
            merged.fullCoverImageURL = known.fullCoverImageURL
        }
        return merged
    }

    private func hydrateIncompleteBooks(in books: [AudioBook]) async -> [AudioBook] {
        var hydrated: [AudioBook] = []
        for book in books {
            guard !Task.isCancelled else { return hydrated }
            guard book.needsCatalogHydration else {
                hydrated.append(book)
                continue
            }

            let detail = (try? await catalog.hydrate(book)) ?? book
            guard !Task.isCancelled else { return hydrated }
            hydrated.append(mergeKnownArtwork(into: detail.preservingUserState(from: book)))
        }
        return hydrated
    }

    private func applyBookUpdate(_ updated: AudioBook) {
        func replace(in books: inout [AudioBook]) {
            for index in books.indices where books[index].id == updated.id {
                books[index] = updated
            }
        }
        replace(in: &libraryBooks)
        replace(in: &featuredBooks)
        replace(in: &searchResults)
        replace(in: &recentlyPlayedBooks)
        if playback.currentBook?.id == updated.id {
            playback.updateBookMetadata(updated)
        }
    }

    @discardableResult
    private func refreshDownloadSnapshot() async -> [DownloadItem] {
        let latestDownloads = await downloadService.downloads()
        downloads = latestDownloads
        downloadFallbackBooksByID = fallbackDownloadBooks(from: latestDownloads)
        downloadStorageByBookID = await storageSnapshot(for: latestDownloads)
        downloadStorageByChapterKey = await chapterStorageSnapshot(for: latestDownloads)
        return latestDownloads
    }

    private func fallbackDownloadBooks(from downloads: [DownloadItem]) -> [String: AudioBook] {
        let knownBookIDs = Set(knownBooks.map(\.id))
        let groupedDownloads = Dictionary(grouping: downloads, by: \.bookID)
        return Dictionary(
            uniqueKeysWithValues: groupedDownloads.compactMap { bookID, items in
                guard !knownBookIDs.contains(bookID),
                      let recoveredBook = DownloadItem.recoveredAudioBook(bookID: bookID, from: items)
                else { return nil }
                return (bookID, recoveredBook)
            }
        )
    }

    private func storageSnapshot(for downloads: [DownloadItem]) async -> [String: Int64] {
        var snapshot: [String: Int64] = [:]
        for bookID in Set(downloads.map(\.bookID)) {
            snapshot[bookID] = await downloadService.storageBytes(bookID: bookID)
        }
        return snapshot
    }

    private func chapterStorageSnapshot(for downloads: [DownloadItem]) async -> [String: Int64] {
        var snapshot: [String: Int64] = [:]
        let aggregateDownloadedBookIDs = Set(downloads.compactMap { item in
            item.state == .downloaded && item.chapterID == nil ? item.bookID : nil
        })

        for bookID in aggregateDownloadedBookIDs {
            guard let book = latestVersion(bookID: bookID) else { continue }
            for chapter in downloadableChapters(in: book) {
                if let entry = await chapterStorageEntry(bookID: bookID, chapter: chapter) {
                    snapshot[entry.key] = entry.bytes
                }
            }
        }

        for item in downloads where item.state == .downloaded {
            guard let chapterID = item.chapterID,
                  let book = latestVersion(bookID: item.bookID),
                  let chapter = book.chapters.first(where: { $0.id == chapterID })
            else { continue }

            if let entry = await chapterStorageEntry(bookID: item.bookID, chapter: chapter) {
                snapshot[entry.key] = entry.bytes
            }
        }
        return snapshot
    }

    private func chapterStorageEntry(
        bookID: String,
        chapter: AudioBookChapter
    ) async -> (key: String, bytes: Int64)? {
        let bytes = await downloadService.storageBytes(bookID: bookID, chapter: chapter)
        guard bytes > 0 else { return nil }
        return (Self.downloadChapterStorageKey(bookID: bookID, chapterID: chapter.id), bytes)
    }

    private func downloadableChapters(in book: AudioBook) -> [AudioBookChapter] {
        book.chapters.filter { chapter in
            chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        }
    }

    private func resumeActiveWholeBookDownloadsIfNeeded(from downloads: [DownloadItem]) async {
        let activeBookIDs = Set(downloads.compactMap { item in
            item.chapterID == nil && Self.isActiveDownloadState(item.state) ? item.bookID : nil
        })
        guard !activeBookIDs.isEmpty else { return }

        for bookID in activeBookIDs {
            guard let book = latestVersion(bookID: bookID) else { continue }
            await downloadService.enqueue(book: book)
        }
        await refreshDownloadSnapshot()
    }

    private func allDownloadableChaptersAreDownloaded(_ book: AudioBook, downloads: [DownloadItem]) -> Bool {
        let downloadableChapterIDs = Set(book.chapters.compactMap { chapter in
            chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false ? chapter.id : nil
        })
        guard !downloadableChapterIDs.isEmpty else { return false }
        let downloadedChapterIDs = Set(downloads.compactMap { item in
            item.state == .downloaded ? item.chapterID : nil
        })
        return downloadableChapterIDs.isSubset(of: downloadedChapterIDs)
    }

    private func startDownloadRefreshLoopIfNeeded() {
        guard downloadRefreshTask == nil else { return }
        guard hasActiveDownloads(downloads) else { return }

        downloadRefreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: Self.downloadRefreshIntervalNanoseconds)
                guard let self else { return }

                let latestDownloads = await self.refreshDownloadSnapshot()
                guard self.hasActiveDownloads(latestDownloads) else { break }
            }
            self?.downloadRefreshTask = nil
        }
    }

    private func hasActiveDownloads(_ downloads: [DownloadItem]) -> Bool {
        downloads.contains { item in Self.isActiveDownloadState(item.state) }
    }

    private static func isActiveDownloadState(_ state: DownloadState) -> Bool {
        state == .queued || state == .downloading
    }

    private static func downloadChapterStorageKey(bookID: String, chapterID: String) -> String {
        "\(bookID)\(downloadChapterStorageSeparator)\(chapterID)"
    }

    private func bindPlaybackProgress() {
        playback.onProgressChanged = { [weak self] book, chapter, positionSeconds, durationSeconds, shouldPersist in
            self?.recordPlaybackProgress(
                book: book,
                chapter: chapter,
                positionSeconds: positionSeconds,
                durationSeconds: durationSeconds,
                forcePersist: shouldPersist
            )
        }
        playback.onSharePlayItemChanged = { [weak self] book, chapter, positionSeconds, isPlaying in
            guard let self else { return }
            Task { @MainActor in
                await self.sharePlay.send(
                    AudiobookSharePlayMessage(
                        reason: .itemChanged,
                        bookID: book.id,
                        bookTitle: book.title,
                        chapterID: chapter?.id,
                        positionSeconds: positionSeconds,
                        isPlaying: isPlaying
                    )
                )
            }
        }
        playback.onChapterEnded = { [weak self] _, _ in
            guard let self, self.sleepTimerState == .endOfChapter else { return false }
            self.expireSleepTimer(pausingPlayback: false)
            return true
        }
    }

    private func expireSleepTimer(pausingPlayback: Bool) {
        sleepTimerTask?.cancel()
        sleepTimerTask = nil
        if pausingPlayback {
            playback.pause()
        }
        sleepTimerState = .expired
        scheduleExpiredSleepTimerReset()
    }

    private func scheduleExpiredSleepTimerReset() {
        sleepTimerResetTask?.cancel()
        sleepTimerResetTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if self?.sleepTimerState == .expired {
                    self?.sleepTimerState = .off
                }
            }
        }
    }

    private func recordPlaybackProgress(
        book: AudioBook,
        chapter: AudioBookChapter,
        positionSeconds: Double,
        durationSeconds: Double,
        forcePersist: Bool
    ) {
        guard positionSeconds.isFinite, durationSeconds.isFinite else { return }
        let existing = progressByBookID[book.id]
        let effectiveDurationSeconds: Double
        if durationSeconds > 0 {
            effectiveDurationSeconds = durationSeconds
        } else if existing?.chapterID == chapter.id {
            effectiveDurationSeconds = existing?.durationSeconds ?? 0
        } else {
            effectiveDurationSeconds = 0
        }
        var completedChapterIDs = existing?.completedChapterIDs ?? []
        if effectiveDurationSeconds > 0 &&
            positionSeconds >= max(effectiveDurationSeconds * 0.98, effectiveDurationSeconds - 3) {
            completedChapterIDs.insert(chapter.id)
        }
        let progress = PlaybackProgress(
            bookID: book.id,
            chapterID: chapter.id,
            positionSeconds: positionSeconds,
            durationSeconds: effectiveDurationSeconds,
            completedChapterIDs: completedChapterIDs,
            updatedAt: .now
        )
        progressByBookID[book.id] = progress

        guard shouldPersist(progress, force: forcePersist) else { return }
        lastProgressPersistAt[book.id] = .now
        Task {
            await progressStore.save(progress)
        }
    }

    private func recordRecentlyPlayedBook(_ book: AudioBook) {
        recentlyPlayedBooks.removeAll { $0.id == book.id }
        recentlyPlayedBooks.insert(book, at: 0)
        if recentlyPlayedBooks.count > Self.maximumRecentlyPlayedBooks {
            recentlyPlayedBooks.removeLast(recentlyPlayedBooks.count - Self.maximumRecentlyPlayedBooks)
        }
        let snapshot = recentlyPlayedBooks
        Task {
            try? await recentlyPlayedBookStore.save(snapshot)
        }
    }

    private func shouldPersist(_ progress: PlaybackProgress, force: Bool) -> Bool {
        if force { return true }
        guard progress.hasStarted else { return false }
        guard let last = lastProgressPersistAt[progress.bookID] else { return true }
        return Date.now.timeIntervalSince(last) >= 5
    }

    func saveSearchHistory(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= Self.minimumSearchHistoryLength else { return }
        searchHistory = Self.cleanedSearchHistory([trimmed] + searchHistory)
        Task {
            await searchHistoryStore.save(searchHistory)
        }
    }

    private static let minimumSearchHistoryLength = 3
    private static let maximumSearchHistoryItems = 12

    private static func cleanedSearchHistory(_ entries: [String]) -> [String] {
        var cleaned: [String] = []
        for entry in entries {
            let trimmed = entry.trimmingCharacters(in: .whitespacesAndNewlines)
            guard trimmed.count >= minimumSearchHistoryLength else { continue }
            let normalized = normalizedSearchHistoryKey(trimmed)
            guard !normalized.isEmpty else { continue }

            if cleaned.contains(where: { normalizedSearchHistoryKey($0) == normalized }) {
                continue
            }

            if let prefixIndex = cleaned.firstIndex(where: { existing in
                let existingKey = normalizedSearchHistoryKey(existing)
                return existingKey.hasPrefix(normalized) || normalized.hasPrefix(existingKey)
            }) {
                if trimmed.count > cleaned[prefixIndex].count {
                    cleaned[prefixIndex] = trimmed
                }
                continue
            }

            cleaned.append(trimmed)
            if cleaned.count >= maximumSearchHistoryItems { break }
        }
        return cleaned
    }

    private static func normalizedSearchHistoryKey(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .lowercased()
            .replacingOccurrences(of: "\u{0142}", with: "l")
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func loadRelatedAuthorBooksCache() -> [String: [AudioBook]] {
        guard let data = UserDefaults.standard.data(forKey: UserDefaultsKeys.relatedAuthorBooksCache),
              let decoded = try? JSONDecoder().decode([String: [AudioBook]].self, from: data)
        else { return [:] }
        return decoded.filter { key, books in
            !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !books.isEmpty
        }
    }

    private func refreshSearchCacheEntryCount() async {
        let catalogEntries = await catalogSearchCoordinator.cacheEntryCount()
        searchCacheEntryCount = catalogEntries + relatedAuthorBooksCache.count
    }

    private func storeRelatedAuthorBooks(_ books: [AudioBook], for book: AudioBook, settingsSnapshot: UserSettings) {
        let cacheKey = Self.relatedAuthorCacheKey(for: book, settings: settingsSnapshot)
        guard !cacheKey.isEmpty, !books.isEmpty else { return }
        relatedAuthorBooksCache[cacheKey] = books
        persistRelatedAuthorBooksCache()
        Task { await refreshSearchCacheEntryCount() }
    }

    private func persistRelatedAuthorBooksCache() {
        if relatedAuthorBooksCache.count > 48 {
            relatedAuthorBooksCache = Dictionary(
                uniqueKeysWithValues: relatedAuthorBooksCache.prefix(48).map { ($0.key, $0.value) }
            )
        }
        guard let data = try? JSONEncoder().encode(relatedAuthorBooksCache) else { return }
        UserDefaults.standard.set(data, forKey: UserDefaultsKeys.relatedAuthorBooksCache)
    }

    private static func relatedAuthorCacheKey(for book: AudioBook, settings: UserSettings) -> String {
        let authorKey = normalizedRelatedBookKey(book.author)
        guard !authorKey.isEmpty else { return "" }
        let scopeKey = relatedAuthorSearchRequests(for: book, settings: settings)
            .map { SearchCacheKey(request: $0).storageKey }
            .sorted()
            .joined(separator: "||")
        guard !scopeKey.isEmpty else { return "" }
        return "\(authorKey)||\(scopeKey)"
    }

    private static func isRelatedAuthorBook(
        _ candidate: AudioBook,
        currentBook: AudioBook,
        authorKey: String
    ) -> Bool {
        guard !isSameRelatedBook(candidate, currentBook) else { return false }
        let candidateAuthorKey = normalizedRelatedBookKey(candidate.author)
        guard !candidateAuthorKey.isEmpty else { return false }
        return candidateAuthorKey == authorKey ||
            candidateAuthorKey.contains(authorKey) ||
            authorKey.contains(candidateAuthorKey)
    }

    private static func relatedAuthorSearchRequests(
        for book: AudioBook,
        settings: UserSettings
    ) -> [CatalogSearchRequest] {
        let query = book.author.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }

        let bookLanguages = relatedLanguagePreferences(for: book, settings: settings)
        var requests: [CatalogSearchRequest] = []
        var seenKeys = Set<String>()

        func appendRequest(
            languages: Set<BookLanguagePreference>,
            sources: Set<BookSource>,
            sourcePriority: [BookSource]
        ) {
            let publicSources = Set(sources.filter(\.isAvailableCatalogSource))
                .intersection(settings.enabledSources)
            guard !publicSources.isEmpty else { return }
            let safeLanguages = languages.isEmpty ? [.all] : languages
            let request = CatalogSearchRequest(
                query: query,
                field: .author,
                languages: safeLanguages,
                sources: publicSources,
                sourcePriority: sourcePriority
            )
            let key = [
                safeLanguages.map(\.rawValue).sorted().joined(separator: ","),
                publicSources.map(\.rawValue).sorted().joined(separator: ","),
                request.sourcePriority.map(\.rawValue).joined(separator: ","),
            ].joined(separator: "|")
            guard seenKeys.insert(key).inserted else { return }
            requests.append(request)
        }

        if book.source.isAvailableCatalogSource {
            appendRequest(
                languages: bookLanguages,
                sources: [book.source],
                sourcePriority: [book.source]
            )
        }

        if !settings.enabledSources.isEmpty {
            var settingsLanguages = settings.preferredLanguages
            settingsLanguages.formUnion(bookLanguages)
            appendRequest(
                languages: settingsLanguages,
                sources: settings.enabledSources,
                sourcePriority: settings.sourcePriority
            )
        }

        return requests
    }

    private static func relatedLanguagePreferences(
        for book: AudioBook,
        settings: UserSettings
    ) -> Set<BookLanguagePreference> {
        if let language = languagePreference(for: book.language) ?? languagePreference(for: book.originalLanguage) {
            return [language]
        }

        switch book.source {
        case .wolneLektury:
            return [.polish]
        case .lit2Go:
            return [.english]
        case .libriVox, .gutendex, .localAsset, .customLocal:
            return settings.preferredLanguages.isEmpty ? [.all] : settings.preferredLanguages
        }
    }

    private static func languagePreference(for value: String?) -> BookLanguagePreference? {
        guard let value else { return nil }
        let normalized = value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }

        if ["pl", "polish", "polski", "polska"].contains(normalized) ||
            normalized.contains("polish") ||
            normalized.contains("polski") {
            return .polish
        }
        if ["en", "eng", "english"].contains(normalized) || normalized.contains("english") {
            return .english
        }
        if ["fr", "fre", "fra", "french", "francais"].contains(normalized) || normalized.contains("french") {
            return .french
        }
        if ["de", "ger", "deu", "german", "deutsch"].contains(normalized) || normalized.contains("german") {
            return .german
        }
        if ["it", "ita", "italian", "italiano"].contains(normalized) || normalized.contains("italian") {
            return .italian
        }
        if ["es", "spa", "spanish", "espanol"].contains(normalized) || normalized.contains("spanish") {
            return .spanish
        }
        return nil
    }

    private static func uniqueRelatedAuthorBooks(
        _ books: [AudioBook],
        currentBook: AudioBook,
        limit: Int
    ) -> [AudioBook] {
        var seenIDs = Set<String>()
        var seenTitleAuthors = Set<String>()
        var unique: [AudioBook] = []

        for book in books {
            guard !isSameRelatedBook(book, currentBook) else { continue }
            let idKey = "\(book.source.rawValue)|\(book.id)"
            let titleAuthorKey = "\(normalizedRelatedBookKey(book.title))|\(normalizedRelatedBookKey(book.author))"
            guard seenIDs.insert(idKey).inserted,
                  seenTitleAuthors.insert(titleAuthorKey).inserted
            else { continue }

            unique.append(book)
            if unique.count >= limit { break }
        }

        return unique
    }

    private static func isSameRelatedBook(_ lhs: AudioBook, _ rhs: AudioBook) -> Bool {
        if lhs.id == rhs.id { return true }
        return normalizedRelatedBookKey(lhs.title) == normalizedRelatedBookKey(rhs.title) &&
            normalizedRelatedBookKey(lhs.author) == normalizedRelatedBookKey(rhs.author)
    }

    private static func normalizedRelatedBookKey(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .lowercased()
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func loadQueue() -> [QueueEntry] {
        guard let data = UserDefaults.standard.data(forKey: UserDefaultsKeys.queue),
              let decoded = try? JSONDecoder().decode([QueueEntry].self, from: data)
        else { return [] }
        return decoded
    }

    private func persistQueue() {
        Task {
            await queueStore.save(queue)
        }
    }

    private func startNetworkMonitor() {
        networkMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                await self?.handleConnectivityChange(isOnline: path.status == .satisfied)
            }
        }
        networkMonitor.start(queue: networkQueue)
    }

    private func handleConnectivityChange(isOnline newValue: Bool) async {
        isOnline = newValue
        let generation = searchRequestGeneration
        guard let retrySnapshots = await catalogSearchCoordinator.connectivityChanged(to: newValue),
              generation == searchRequestGeneration,
              let request = lastCatalogSearchRequest
        else { return }
        let searchID = beginSearch()
        isSearching = true
        searchProgressItems = CatalogSearchPlan(request: request).operations.map {
            SearchProgressItem(operation: $0)
        }
        for await snapshot in retrySnapshots {
            guard searchIsCurrent(searchID) else { return }
            applyCatalogSnapshot(
                snapshot,
                searchID: searchID,
                request: request,
                preserveVisibleResultsWhilePending: true
            )
        }
        finishSearch(searchID)
        await refreshSearchCacheEntryCount()
    }

    private static func launchArgument(named name: String) -> String? {
        let arguments = ProcessInfo.processInfo.arguments
        guard let index = arguments.firstIndex(of: name), arguments.indices.contains(index + 1) else {
            return nil
        }
        return arguments[index + 1]
    }
}

private struct CatalogUITestFixtureClient: CatalogSourceClient {
    var source: BookSource
    var mode: String

    private func fixtureBooks() async -> [AudioBook] {
        #if DEBUG
        if mode == "appearance" { return await ArtworkAppearanceFixtures.books }
        #endif
        if mode == "related-sources" { return Self.relatedSourceBooks }
        return Self.books
    }

    func featuredBooks(limit: Int) async throws -> [AudioBook] {
        Array(await fixtureBooks().filter { $0.source == source }.prefix(limit))
    }

    func search(_ request: CatalogSearchRequest) async throws -> [AudioBook] {
        guard request.sources.contains(source) else { return [] }
        if mode.contains("offline") { throw URLError(.notConnectedToInternet) }
        if mode == "unified-slow" {
            try await Task.sleep(for: .seconds(2))
        }
        if mode == "progressive" {
            let fieldOffset = CatalogSearchField.allCases.firstIndex(of: request.field) ?? 0
            let sourceOffset = BookSource.defaultPublicCatalogPriority.firstIndex(of: source) ?? 0
            try await Task.sleep(for: .milliseconds(35 + fieldOffset * 25 + sourceOffset * 20))
        }
        return await fixtureBooks()
            .filter { $0.source == source }
            .filteredForCatalogSearch(request)
    }

    func hydrate(_ book: AudioBook) async throws -> AudioBook {
        if mode == "wolne-metadata", book.id == "wolnelektury-lalka" {
            return try await WolneLekturyCatalogClient().hydrate(book)
        }
        return book
    }

    static var savedBooks: [AudioBook] {
        var alice = books.first { $0.id == "200" }!
        alice.libraryStatus = .inLibrary
        return [
            AudioBook(
                id: "unified-local",
                title: "Alice at Home",
                author: "Local Author",
                source: .customLocal,
                libraryStatus: .inLibrary,
                chapters: [AudioBookChapter(
                    id: "unified-chapter", title: "The Hidden Orchard", number: 1,
                    reader: "Local Narrator", localFileName: "unified-local.m4a"
                )],
                language: "English",
                genres: ["Memoir"]
            ),
            alice,
            AudioBook(
                id: "unified-polish", title: "Pamiętnik z podróży", author: "Maria Kowalska",
                source: .customLocal, libraryStatus: .inLibrary, language: "Polish"
            ),
        ]
    }

    static var relatedSourceBooks: [AudioBook] {
        books + [
            AudioBook(
                id: "librivox-prus-volume-1", title: "Lalka (tom 1)", author: "Bolesław Prus",
                source: .libriVox, language: "Polish", genres: ["Powieść", "Realizm"]
            ),
            AudioBook(
                id: "wolne-tadeusz", title: "Pan Tadeusz", author: "Adam Mickiewicz",
                source: .wolneLektury, language: "Polish", genres: ["Powieść"]
            ),
        ]
    }

    static let books: [AudioBook] = [
        AudioBook(
            id: "200",
            title: "Alice's Adventures in Wonderland",
            author: "Lewis Carroll",
            source: .libriVox,
            coverImageURL: "https://archive.org/services/img/alicesadventures1902",
            chapters: [
                AudioBookChapter(
                    id: "alice-caterpillar",
                    title: "Advice from a Caterpillar",
                    number: 5,
                    reader: "Alice Reader"
                ),
            ],
            language: "English",
            genres: ["Children's Fiction", "Fantasy"]
        ),
        AudioBook(
            id: "lit2go-1__alices-adventures-in-wonderland",
            title: "Alice's Adventures in Wonderland",
            author: "Lewis Carroll",
            source: .lit2Go,
            language: "English"
        ),
        AudioBook(
            id: "wolne-antek",
            title: "Antek",
            author: "Bolesław Prus",
            description: "Talent is not always a blessing. Antek is one of Bolesław Prus's most widely read stories: a stark portrait of a poor, isolated nineteenth-century village, made more vivid by the dreams and potential of a growing boy. Antek is bright and capable, but his talent becomes more obstacle than advantage. Quick hands and an eager mind are not enough to save his sister from terrifying folk remedies, yet they are too much for him to find a place at the blacksmith's forge. Does the boy have any chance at a happy life?",
            source: .wolneLektury,
            coverImageURL: "https://wolnelektury.pl/media/book/cover_api_thumb/antek_2MbAJ1y.jpg",
            fullCoverImageURL: "https://wolnelektury.pl/media/book/cover/antek.jpg",
            sourceTextURL: "https://wolnelektury.pl/media/book/txt/antek.txt",
            chapters: [
                AudioBookChapter(
                    id: "wolne-antek-1",
                    title: "Antek",
                    number: 1,
                    reader: "Wolne Lektury",
                    durationSeconds: 2_700
                ),
            ],
            language: "Polish",
            genres: ["Nowela"]
        ),
        AudioBook(
            id: "wolnelektury-lalka",
            title: "Lalka",
            author: "Bolesław Prus",
            source: .wolneLektury,
            language: "Polish",
            genres: ["Novel"]
        ),
        AudioBook(
            id: "wolne-katarynka",
            title: "Katarynka",
            author: "Bolesław Prus",
            description: "A Warsaw lawyer's encounter with a blind girl changes his view of street music and compassion.",
            source: .wolneLektury,
            coverImageURL: "https://wolnelektury.pl/media/book/cover_api_thumb/katarynka_VGXbgcs.jpg",
            fullCoverImageURL: "https://wolnelektury.pl/media/book/cover/katarynka.jpg",
            chapters: [AudioBookChapter(id: "wolne-katarynka-1", title: "Katarynka", number: 1, durationSeconds: 2_160)],
            totalDurationSeconds: 2_160,
            language: "Polish",
            genres: ["Nowela", "Realizm"]
        ),
        AudioBook(
            id: "wolne-lalka",
            title: "Lalka",
            author: "Bolesław Prus",
            description: "A panoramic realist novel of Warsaw society, ambition, class, and unrequited love.",
            source: .wolneLektury,
            coverImageURL: "https://wolnelektury.pl/media/book/cover_api_thumb/lalka_nCyTxKb.jpg",
            fullCoverImageURL: "https://wolnelektury.pl/media/book/cover/lalka.jpg",
            chapters: [AudioBookChapter(id: "wolne-lalka-1", title: "Tom pierwszy", number: 1, durationSeconds: 36_000)],
            totalDurationSeconds: 36_000,
            language: "Polish",
            genres: ["Powieść", "Realizm"]
        ),
    ]
}

private extension AudioBook {
    var needsCatalogHydration: Bool {
        needsArtworkHydration ||
            (source == .wolneLektury && (
                description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                    chapters.isEmpty
            ))
    }

    var needsArtworkHydration: Bool {
        coverImageURL?.isEmpty != false && fullCoverImageURL?.isEmpty != false
    }
}

private enum UserDefaultsKeys {
    static let hasCompletedOnboarding = "hasCompletedOnboarding"
    static let onboardingVersion = "onboardingVersion"
    static let settings = "settings"
    static let didAdoptPanelsNowPlayingDefault = "didAdoptPanelsNowPlayingDefault.v1"
    static let searchHistory = "searchHistory"
    static let relatedAuthorBooksCache = "relatedAuthorBooksCache"
    static let queue = "queue"
    static let hiddenContinueListeningBookIDs = "hiddenContinueListeningBookIDs"
}

private struct SearchCacheKey: Hashable {
    var query: String
    var field: CatalogSearchField
    var sources: [BookSource]
    var languages: [BookLanguagePreference]

    init(request: CatalogSearchRequest) {
        query = request.query.trimmingCharacters(in: .whitespacesAndNewlines).localizedLowercase
        field = request.field
        sources = request.orderedSources
        languages = request.languages.sorted { $0.rawValue < $1.rawValue }
    }

    var normalizedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines).localizedLowercase
    }

    var storageKey: String {
        [
            normalizedQuery,
            field.rawValue,
            sources.map(\.rawValue).joined(separator: ","),
            languages.map(\.rawValue).joined(separator: ","),
        ].joined(separator: "|")
    }
}

@MainActor
@Observable
final class PlaybackController: PlaybackControlling {
    static let seekIntervalSeconds: Double = 10
    static let previousChapterRestartThresholdSeconds: Double = 3

    var currentBook: AudioBook?
    var currentChapter: AudioBookChapter?
    var isPlaying = false
    var isBuffering = false
    var showsBufferingText = false
    var currentTimeSeconds: Double = 0
    var durationSeconds: Double = 0
    var isDurationKnown = false
    var playbackSpeed: Float = 1
    var onProgressChanged: (@MainActor (AudioBook, AudioBookChapter, Double, Double, Bool) -> Void)?
    var onSharePlayItemChanged: (@MainActor (AudioBook, AudioBookChapter?, Double, Bool) -> Void)?
    var onChapterEnded: (@MainActor (AudioBook, AudioBookChapter) -> Bool)?

    private let player: AVQueuePlayer
    private let playbackCoordinatorDelegate = AudiobookPlaybackCoordinatorDelegate()
    private var timeObserver: Any?
    private var itemEndObserver: NSObjectProtocol?
    private var timeControlStatusObservation: NSKeyValueObservation?
    private var currentItemObservations: [NSKeyValueObservation] = []
    @ObservationIgnored private var remoteCommandTargets: [(MPRemoteCommand, Any)] = []
    private var pendingSeekSeconds: Double?
    private var seekProtectionUntil: Date?
    private var seekGeneration = 0
    @ObservationIgnored private var bufferingTextTask: Task<Void, Never>?
    @ObservationIgnored private var durationLoadTask: Task<Void, Never>?
    private var currentItemGeneration = 0
    @ObservationIgnored private var artworkLoadTask: Task<Void, Never>?
    @ObservationIgnored private var chapterTitleCache = WolneChapterTitleCache()
    private let artworkDataLoader: @Sendable ([URL]) async -> Data?
    private var nowPlayingArtwork: MPMediaItemArtwork?
    private var artworkSignature = ""
    private var artworkGeneration = 0
    private static let bufferingTextDelayNanoseconds: UInt64 = 650_000_000

    init(
        player: AVQueuePlayer = AVQueuePlayer(),
        artworkDataLoader: @escaping @Sendable ([URL]) async -> Data? = { urls in
            await CoverArtworkCache.shared.data(for: urls)
        }
    ) {
        self.player = player
        self.artworkDataLoader = artworkDataLoader
        // The controller replaces chapters itself. Queue auto-advance can remove
        // the replacement while an end notification is being handled.
        player.actionAtItemEnd = .pause
    }

    isolated deinit {
        artworkLoadTask?.cancel()
        durationLoadTask?.cancel()
        bufferingTextTask?.cancel()
        if let timeObserver {
            player.removeTimeObserver(timeObserver)
        }
        if let itemEndObserver {
            NotificationCenter.default.removeObserver(itemEndObserver)
        }
        timeControlStatusObservation?.invalidate()
        currentItemObservations.forEach { $0.invalidate() }
        remoteCommandTargets.forEach { command, target in command.removeTarget(target) }
    }

    func configureAudioSession(activateSession: () throws -> Void = {
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playback, mode: .spokenAudio, policy: .longFormAudio)
        try audioSession.setActive(true)
    }) {
        do {
            try activateSession()
        } catch {
            Logger(subsystem: "com.ababilinski.librivoxmobile", category: "Playback")
                .error("Unable to configure playback audio session: \(error.localizedDescription, privacy: .public)")
        }
        player.playbackCoordinator.delegate = playbackCoordinatorDelegate
        configureRemoteCommands()
        observeTimeControlStatus()
        observeTime()
    }

    func play(book: AudioBook, chapter: AudioBookChapter? = nil) {
        play(book: book, chapter: chapter, startAt: 0)
    }

    func play(
        book: AudioBook,
        chapter: AudioBookChapter? = nil,
        startAt startSeconds: Double,
        mediaURL: URL? = nil,
        autoplay: Bool = true,
        broadcastSharePlay: Bool = true
    ) {
        let book = chapterTitleCache.normalized(book)
        currentBook = book
        currentChapter = chapter.flatMap { selected in book.chapters.first { $0.id == selected.id } ?? selected }
            ?? book.chapters.first
        loadNowPlayingArtwork(for: book)
        let metadataDuration = initialDuration(for: book, chapter: currentChapter)
        durationSeconds = metadataDuration ?? 0
        isDurationKnown = metadataDuration != nil
        currentTimeSeconds = boundedPosition(startSeconds)
        currentItemGeneration += 1
        let itemGeneration = currentItemGeneration
        durationLoadTask?.cancel()
        // A seek callback or time-protection window from the previous chapter must
        // not change the newly selected chapter's position.
        seekGeneration += 1
        pendingSeekSeconds = nil
        seekProtectionUntil = nil

        if let url = mediaURL ?? currentChapter?.remoteListenURL {
            let item = AVPlayerItem(url: url)
            playbackCoordinatorDelegate.setIdentifier(playbackItemIdentifier(book: book, chapter: currentChapter), for: item)
            player.replaceCurrentItem(with: item)
            observeItemEnd(for: item, generation: itemGeneration)
            observeBuffering(for: item)
            loadDuration(for: item, generation: itemGeneration)
            if currentTimeSeconds > 0 {
                player.seek(to: CMTime(seconds: currentTimeSeconds, preferredTimescale: 600))
            }
            if autoplay {
                setBuffering(true)
                player.rate = playbackSpeed
                player.play()
                isPlaying = true
            } else {
                player.pause()
                isPlaying = false
                setBuffering(false)
            }
            updateBufferingState()
        } else {
            player.replaceCurrentItem(with: nil)
            observeItemEnd(for: nil, generation: itemGeneration)
            observeBuffering(for: nil)
            isPlaying = false
            setBuffering(false)
        }
        updateNowPlaying()
        notifyProgressChanged(shouldPersist: true)
        if broadcastSharePlay {
            onSharePlayItemChanged?(book, currentChapter, currentTimeSeconds, isPlaying)
        }
    }

    func pause() {
        pause(broadcastSharePlay: true)
    }

    func pause(broadcastSharePlay: Bool) {
        player.pause()
        isPlaying = false
        setBuffering(false)
        updateNowPlaying()
        notifyProgressChanged(shouldPersist: true)
    }

    func toggle() {
        isPlaying ? pause() : resume()
    }

    func resume() {
        resume(broadcastSharePlay: true)
    }

    func resume(broadcastSharePlay: Bool) {
        player.rate = playbackSpeed
        player.play()
        isPlaying = true
        updateBufferingState()
        updateNowPlaying()
    }

    func setSpeed(_ speed: Float) {
        playbackSpeed = speed
        if isPlaying {
            player.rate = speed
            updateBufferingState()
        }
        updateNowPlaying()
    }

    func seek(by delta: Double, broadcastSharePlay: Bool = true) {
        let target = boundedPosition(currentTimeSeconds + delta)
        seek(to: target, broadcastSharePlay: broadcastSharePlay)
    }

    func seek(to target: Double, broadcastSharePlay: Bool = true) {
        let target = boundedPosition(target)
        seekGeneration += 1
        let generation = seekGeneration
        pendingSeekSeconds = target
        seekProtectionUntil = Date().addingTimeInterval(0.75)
        currentTimeSeconds = target
        if isPlaying {
            setBuffering(true)
        }
        updateNowPlaying()
        notifyProgressChanged(shouldPersist: true)
        player.seek(
            to: CMTime(seconds: target, preferredTimescale: 600),
            toleranceBefore: .zero,
            toleranceAfter: .zero
        ) { [weak self] finished in
            Task { @MainActor in
                guard let self, self.seekGeneration == generation else { return }
                if finished {
                    let actualSeconds = self.player.currentTime().seconds
                    self.currentTimeSeconds = actualSeconds.isFinite ? actualSeconds : target
                }
                self.pendingSeekSeconds = nil
                self.seekProtectionUntil = Date().addingTimeInterval(0.3)
                self.updateBufferingState()
                self.updateNowPlaying()
                self.notifyProgressChanged(shouldPersist: false)
            }
        }
    }

    func skipChapter(by delta: Int, broadcastSharePlay: Bool = true) {
        guard let book = currentBook,
              let currentChapter,
              let currentIndex = book.chapters.firstIndex(where: { $0.id == currentChapter.id })
        else { return }
        let nextIndex = min(max(currentIndex + delta, 0), book.chapters.count - 1)
        play(book: book, chapter: book.chapters[nextIndex], startAt: 0, broadcastSharePlay: broadcastSharePlay)
    }

    func restartOrSkipToPreviousChapter(broadcastSharePlay: Bool = true) {
        guard let book = currentBook,
              let currentChapter,
              let currentIndex = book.chapters.firstIndex(where: { $0.id == currentChapter.id })
        else { return }

        if currentTimeSeconds > Self.previousChapterRestartThresholdSeconds || currentIndex == 0 {
            seek(to: 0, broadcastSharePlay: broadcastSharePlay)
            return
        }

        play(
            book: book,
            chapter: book.chapters[currentIndex - 1],
            startAt: 0,
            autoplay: isPlaying,
            broadcastSharePlay: broadcastSharePlay
        )
    }

    #if canImport(GroupActivities)
    func coordinateWithSharePlaySession<Activity: GroupActivity>(_ session: GroupSession<Activity>) {
        player.playbackCoordinator.coordinateWithSession(session)
    }
    #endif

    private func configureRemoteCommands() {
        guard remoteCommandTargets.isEmpty else { return }
        let commandCenter = MPRemoteCommandCenter.shared()
        addRemoteCommandTarget(commandCenter.playCommand) { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }
        addRemoteCommandTarget(commandCenter.pauseCommand) { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
        addRemoteCommandTarget(commandCenter.togglePlayPauseCommand) { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        commandCenter.skipForwardCommand.preferredIntervals = [NSNumber(value: Self.seekIntervalSeconds)]
        addRemoteCommandTarget(commandCenter.skipForwardCommand) { [weak self] _ in
            Task { @MainActor in self?.seek(by: Self.seekIntervalSeconds) }
            return .success
        }
        commandCenter.skipBackwardCommand.preferredIntervals = [NSNumber(value: Self.seekIntervalSeconds)]
        addRemoteCommandTarget(commandCenter.skipBackwardCommand) { [weak self] _ in
            Task { @MainActor in self?.seek(by: -Self.seekIntervalSeconds) }
            return .success
        }
        addRemoteCommandTarget(commandCenter.nextTrackCommand) { [weak self] _ in
            Task { @MainActor in self?.skipChapter(by: 1) }
            return .success
        }
        addRemoteCommandTarget(commandCenter.previousTrackCommand) { [weak self] _ in
            Task { @MainActor in self?.restartOrSkipToPreviousChapter() }
            return .success
        }
    }

    private func addRemoteCommandTarget(
        _ command: MPRemoteCommand,
        handler: @escaping @Sendable (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus
    ) {
        remoteCommandTargets.append((command, command.addTarget(handler: handler)))
    }

    private func observeTime() {
        guard timeObserver == nil else { return }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 1, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            guard let self else { return }
            Task { @MainActor in
                let observedSeconds = time.seconds.isFinite ? time.seconds : 0
                guard !self.shouldIgnoreObservedTime(observedSeconds) else { return }
                self.currentTimeSeconds = observedSeconds
                self.resolveDurationIfAvailable(from: self.player.currentItem)
                if self.isBuffering {
                    self.updateBufferingState()
                }
                self.updateNowPlaying()
                self.notifyProgressChanged(shouldPersist: false)
            }
        }
    }

    private func observeTimeControlStatus() {
        guard timeControlStatusObservation == nil else { return }
        timeControlStatusObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] _, _ in
            Task { @MainActor in
                self?.updateBufferingState()
            }
        }
    }

    private func observeBuffering(for item: AVPlayerItem?) {
        currentItemObservations = []
        guard let item else {
            updateBufferingState()
            return
        }

        currentItemObservations = [
            item.observe(\.status, options: [.initial, .new]) { [weak self, weak item] _, _ in
                Task { @MainActor in
                    self?.resolveDurationIfAvailable(from: item)
                    self?.updateBufferingState()
                }
            },
            item.observe(\.duration, options: [.initial, .new]) { [weak self, weak item] _, _ in
                Task { @MainActor in
                    self?.resolveDurationIfAvailable(from: item)
                }
            },
            item.observe(\.isPlaybackBufferEmpty, options: [.initial, .new]) { [weak self] _, _ in
                Task { @MainActor in
                    self?.updateBufferingState()
                }
            },
            item.observe(\.isPlaybackLikelyToKeepUp, options: [.initial, .new]) { [weak self] _, _ in
                Task { @MainActor in
                    self?.updateBufferingState()
                }
            },
        ]
    }

    private func initialDuration(for book: AudioBook, chapter: AudioBookChapter?) -> Double? {
        PlaybackDurationPolicy.initialDuration(
            chapterDurationSeconds: chapter?.durationSeconds,
            bookTotalDurationSeconds: book.totalDurationSeconds,
            chapterCount: book.chapters.count
        )
    }

    private func boundedPosition(_ seconds: Double) -> Double {
        PlaybackDurationPolicy.boundedPosition(
            seconds,
            resolvedDuration: isDurationKnown ? durationSeconds : nil
        )
    }

    private func loadDuration(for item: AVPlayerItem, generation: Int) {
        durationLoadTask = Task { @MainActor [weak self, weak item] in
            guard let item else { return }
            do {
                let duration = try await item.asset.load(.duration)
                guard let self, !Task.isCancelled,
                      self.currentItemGeneration == generation,
                      self.player.currentItem === item
                else { return }
                self.resolveDuration(duration.seconds)
            } catch {
                // Streaming assets may expose duration later through AVPlayerItem KVO.
            }
        }
    }

    private func resolveDurationIfAvailable(from item: AVPlayerItem?) {
        guard let item, player.currentItem === item else { return }
        resolveDuration(item.duration.seconds)
    }

    private func resolveDuration(_ seconds: Double) {
        guard seconds.isFinite, seconds > 0 else { return }
        guard !isDurationKnown || abs(durationSeconds - seconds) > 0.5 else { return }
        durationSeconds = seconds
        isDurationKnown = true
        currentTimeSeconds = min(currentTimeSeconds, seconds)
        updateNowPlaying()
        notifyProgressChanged(shouldPersist: true)
    }

    private func updateBufferingState() {
        guard isPlaying, let item = player.currentItem, item.status != .failed else {
            setBuffering(false)
            return
        }

        let waitingForPlayer = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
        let waitingForItem = item.status == .unknown
        let bufferIsEmpty = item.isPlaybackBufferEmpty
        let notLikelyToKeepUp = !item.isPlaybackLikelyToKeepUp && player.timeControlStatus != .playing
        setBuffering(waitingForPlayer || waitingForItem || bufferIsEmpty || notLikelyToKeepUp)
    }

    private func setBuffering(_ buffering: Bool) {
        guard isBuffering != buffering else { return }
        isBuffering = buffering
        updateBufferingTextVisibility(buffering)
        updateNowPlaying()
    }

    private func updateBufferingTextVisibility(_ buffering: Bool) {
        bufferingTextTask?.cancel()
        guard buffering else {
            showsBufferingText = false
            return
        }

        showsBufferingText = false
        bufferingTextTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.bufferingTextDelayNanoseconds)
            guard let self, !Task.isCancelled, self.isBuffering else { return }
            self.showsBufferingText = true
        }
    }

    private func shouldIgnoreObservedTime(_ observedSeconds: Double) -> Bool {
        if let pendingSeekSeconds {
            if abs(observedSeconds - pendingSeekSeconds) <= 0.75 {
                self.pendingSeekSeconds = nil
                seekProtectionUntil = Date().addingTimeInterval(0.3)
                return false
            }
            return true
        }

        guard let seekProtectionUntil else { return false }
        if Date() >= seekProtectionUntil {
            self.seekProtectionUntil = nil
            return false
        }

        return abs(observedSeconds - currentTimeSeconds) > 1
    }

    private func observeItemEnd(for item: AVPlayerItem?, generation: Int) {
        if let itemEndObserver {
            NotificationCenter.default.removeObserver(itemEndObserver)
        }
        itemEndObserver = nil
        guard let item else { return }
        itemEndObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self, self.currentItemGeneration == generation else { return }
                self.advanceAfterCurrentItem(endedItem: item)
            }
        }
    }

    private func playbackItemIdentifier(book: AudioBook, chapter: AudioBookChapter?) -> String {
        [book.id, chapter?.id ?? "book"].joined(separator: "::")
    }

    private func advanceAfterCurrentItem(endedItem: AVPlayerItem) {
        // AVQueuePlayer may already have removed the finished item from its queue.
        resolveDuration(endedItem.duration.seconds)
        if !isDurationKnown {
            let endedAt = endedItem.currentTime().seconds
            resolveDuration(endedAt)
        }
        currentTimeSeconds = isDurationKnown ? durationSeconds : max(currentTimeSeconds, 0)
        notifyProgressChanged(shouldPersist: true)
        if let book = currentBook,
           let currentChapter,
           onChapterEnded?(book, currentChapter) == true {
            player.pause()
            isPlaying = false
            setBuffering(false)
            updateNowPlaying()
            return
        }
        guard let book = currentBook,
              let currentChapter,
              let currentIndex = book.chapters.firstIndex(where: { $0.id == currentChapter.id }),
              currentIndex < book.chapters.count - 1
        else {
            isPlaying = false
            setBuffering(false)
            updateNowPlaying()
            return
        }
        play(book: book, chapter: book.chapters[currentIndex + 1], startAt: 0)
    }

    private func notifyProgressChanged(shouldPersist: Bool) {
        guard let currentBook,
              let currentChapter
        else { return }
        onProgressChanged?(
            currentBook,
            currentChapter,
            currentTimeSeconds,
            isDurationKnown ? durationSeconds : 0,
            shouldPersist
        )
    }

    func updateBookMetadata(_ book: AudioBook) {
        guard currentBook?.id == book.id else { return }
        let updated = chapterTitleCache.normalized(book)
        currentBook = updated
        if let chapterID = currentChapter?.id,
           let chapter = updated.chapters.first(where: { $0.id == chapterID }) {
            currentChapter = chapter
        }
        loadNowPlayingArtwork(for: updated)
        updateNowPlaying()
    }

    private func loadNowPlayingArtwork(for book: AudioBook) {
        let urls = [book.fullCoverImageURL, book.coverImageURL]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .compactMap(URL.init(string:))
        let signature = ([book.id] + urls.map(\.absoluteString)).joined(separator: "|")
        guard signature != artworkSignature || (nowPlayingArtwork == nil && artworkLoadTask == nil) else { return }
        artworkLoadTask?.cancel()
        artworkGeneration += 1
        let generation = artworkGeneration
        artworkSignature = signature
        nowPlayingArtwork = nil
        guard !urls.isEmpty else {
            artworkLoadTask = nil
            return
        }
        let bookID = book.id
        artworkLoadTask = Task { [weak self, artworkDataLoader] in
            let data = await artworkDataLoader(urls)
            guard let self, generation == self.artworkGeneration, self.currentBook?.id == bookID else { return }
            self.artworkLoadTask = nil
            guard !Task.isCancelled, let data, let image = UIImage(data: data) else { return }
            self.nowPlayingArtwork = Self.makeNowPlayingArtwork(image)
            self.updateNowPlaying()
        }
    }

    // MediaPlayer invokes the synchronous request handler on its own access queue.
    // Construct it outside MainActor so Swift does not insert an executor assertion.
    private nonisolated static func makeNowPlayingArtwork(_ image: UIImage) -> MPMediaItemArtwork {
        MPMediaItemArtwork(boundsSize: image.size) { requestedSize in
            let scale = min(1, min(requestedSize.width / image.size.width, requestedSize.height / image.size.height))
            let size = CGSize(width: max(1, image.size.width * scale), height: max(1, image.size.height * scale))
            return UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        }
    }

    private func updateNowPlaying() {
        guard let book = currentBook else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: currentChapter?.title ?? book.title,
            MPMediaItemPropertyAlbumTitle: book.title,
            MPMediaItemPropertyArtist: book.author,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTimeSeconds,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying && !isBuffering ? playbackSpeed : 0,
            MPMediaItemPropertyMediaType: MPMediaType.anyAudio.rawValue,
        ]
        if isDurationKnown {
            info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
        }
        if let reader = currentChapter?.reader {
            info[MPMediaItemPropertyComments] = "Read by \(reader)"
        }
        if let artwork = nowPlayingArtwork {
            info[MPMediaItemPropertyArtwork] = artwork
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

}

final class AudiobookPlaybackCoordinatorDelegate: NSObject, AVPlayerPlaybackCoordinatorDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private let identifiers = NSMapTable<AVPlayerItem, NSString>(keyOptions: .weakMemory, valueOptions: .strongMemory)

    func setIdentifier(_ identifier: String, for item: AVPlayerItem) {
        lock.lock()
        defer { lock.unlock() }
        identifiers.setObject(identifier as NSString, forKey: item)
    }

    func playbackCoordinator(
        _ coordinator: AVPlayerPlaybackCoordinator,
        identifierFor playerItem: AVPlayerItem
    ) -> String {
        lock.lock()
        defer { lock.unlock() }
        return identifiers.object(forKey: playerItem) as String? ?? "unknown-audiobook-item"
    }
}

private extension AudioBookChapter {
    var remoteListenURL: URL? {
        guard let urlString = listenURL?.trimmingCharacters(in: .whitespacesAndNewlines),
              !urlString.isEmpty
        else { return nil }
        return URL(string: urlString)
    }
}
