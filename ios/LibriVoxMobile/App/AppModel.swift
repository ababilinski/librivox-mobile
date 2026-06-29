import AVFoundation
import Foundation
import LibriVoxCore
import MediaPlayer
import Network
import Observation

@MainActor
@Observable
final class AppModel {
    var hasCompletedOnboarding: Bool
    var settings: UserSettings
    var libraryBooks: [AudioBook] = AudioBook.seedLibrary
    var featuredBooks: [AudioBook] = AudioBook.seedLibrary
    var searchResults: [AudioBook] = AudioBook.seedLibrary
    var downloads: [DownloadItem] = []
    var bookmarks: [Bookmark] = []
    var queue: [QueueEntry] = []
    var searchHistory: [String] = []
    var isOnline = true
    var routeDiagnostics: [String] = []
    var showingNowPlaying = false

    let catalog = SeedCatalogClient()
    let libraryStore = InMemoryLibraryStore()
    let bookmarkStore = InMemoryBookmarkStore()
    let downloadService = InMemoryDownloadService()
    let playback = PlaybackController()
    private let networkMonitor = NWPathMonitor()
    private let networkQueue = DispatchQueue(label: "LibriVoxMobile.NetworkStatus")

    init() {
        hasCompletedOnboarding = UserDefaults.standard.bool(forKey: UserDefaultsKeys.hasCompletedOnboarding)
        settings = Self.loadSettings()
        searchHistory = UserDefaults.standard.stringArray(forKey: UserDefaultsKeys.searchHistory) ?? []
        playback.configureAudioSession()
        playback.setSpeed(Float(settings.defaultPlaybackSpeed))
        startNetworkMonitor()
        Task { await refresh() }
    }

    var defaultListeningBook: AudioBook? {
        playback.currentBook ??
            libraryBooks.first { $0.id == AudioBook.theJungle.id } ??
            libraryBooks.first ??
            featuredBooks.first { $0.id == AudioBook.theJungle.id } ??
            featuredBooks.first
    }

    func completeOnboarding() {
        hasCompletedOnboarding = true
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.hasCompletedOnboarding)
        persistSettings()
    }

    func refresh() async {
        libraryBooks = await libraryStore.snapshot()
        featuredBooks = (try? await catalog.featuredBooks(limit: 12)) ?? AudioBook.seedLibrary
        searchResults = featuredBooks
        downloads = await downloadService.downloads()
    }

    func search(
        query: String,
        field: CatalogSearchField,
        sources: Set<BookSource>? = nil,
        languages: Set<BookLanguagePreference>? = nil
    ) async {
        saveSearchHistory(query)
        searchResults = (try? await catalog.search(
            CatalogSearchRequest(
                query: query,
                field: field,
                languages: languages ?? settings.preferredLanguages,
                sources: sources ?? settings.enabledSources
            )
        )) ?? []
    }

    func saveToLibrary(_ book: AudioBook) async {
        await libraryStore.save(book)
        await refresh()
    }

    func enqueueDownload(_ book: AudioBook) async {
        await downloadService.enqueue(book: book)
        await refresh()
    }

    func addBookmark(note: String = "") async {
        guard let book = playback.currentBook,
              let chapter = playback.currentChapter
        else { return }
        let bookmark = Bookmark(
            bookID: book.id,
            chapterID: chapter.id,
            positionMilliseconds: Int(playback.currentTimeSeconds * 1000),
            note: note
        )
        await bookmarkStore.save(bookmark)
        bookmarks = await bookmarkStore.bookmarks(for: book.id)
    }

    func playDefaultBook() {
        guard let book = defaultListeningBook else { return }
        playback.play(book: book, chapter: playback.currentChapter ?? book.chapters.first)
    }

    func handleOpenURL(_ url: URL) {
        guard url.scheme == "librivoxmobile" else { return }
        hasCompletedOnboarding = true
        UserDefaults.standard.set(true, forKey: UserDefaultsKeys.hasCompletedOnboarding)
        let candidateID = url.host == "book"
            ? url.pathComponents.dropFirst().first
            : url.host
        guard let candidateID,
              let book = (libraryBooks + featuredBooks).first(where: { $0.id == candidateID })
        else { return }
        playback.play(book: book, chapter: book.chapters.first)
        showingNowPlaying = true
    }

    func enqueue(_ book: AudioBook, chapter: AudioBookChapter? = nil) {
        queue.append(QueueEntry(bookID: book.id, chapterID: chapter?.id))
    }

    func setPlaybackSpeed(_ speed: Double) {
        settings.defaultPlaybackSpeed = speed
        playback.setSpeed(Float(speed))
        persistSettings()
    }

    func persistSettings() {
        settings.enabledSources.insert(.libriVox)
        if !settings.enabledSources.contains(.wolneLektury) {
            settings.preferredLanguages.remove(.polish)
        }
        if settings.preferredLanguages.isEmpty {
            settings.preferredLanguages.insert(.english)
        }
        if let data = try? JSONEncoder().encode(settings) {
            UserDefaults.standard.set(data, forKey: UserDefaultsKeys.settings)
        }
    }

    private static func loadSettings() -> UserSettings {
        guard let data = UserDefaults.standard.data(forKey: UserDefaultsKeys.settings),
              let decoded = try? JSONDecoder().decode(UserSettings.self, from: data)
        else { return UserSettings() }
        var settings = decoded
        settings.enabledSources.insert(.libriVox)
        if !settings.enabledSources.contains(.wolneLektury) {
            settings.preferredLanguages.remove(.polish)
        }
        if settings.preferredLanguages.isEmpty {
            settings.preferredLanguages.insert(.english)
        }
        return settings
    }

    private func saveSearchHistory(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > 1 else { return }
        searchHistory.removeAll { $0.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }
        searchHistory.insert(trimmed, at: 0)
        searchHistory = Array(searchHistory.prefix(12))
        UserDefaults.standard.set(searchHistory, forKey: UserDefaultsKeys.searchHistory)
    }

    private func startNetworkMonitor() {
        networkMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isOnline = path.status == .satisfied
            }
        }
        networkMonitor.start(queue: networkQueue)
    }
}

private enum UserDefaultsKeys {
    static let hasCompletedOnboarding = "hasCompletedOnboarding"
    static let settings = "settings"
    static let searchHistory = "searchHistory"
}

@MainActor
@Observable
final class PlaybackController: PlaybackControlling {
    var currentBook: AudioBook?
    var currentChapter: AudioBookChapter?
    var isPlaying = false
    var currentTimeSeconds: Double = 0
    var durationSeconds: Double = 1
    var playbackSpeed: Float = 1

    private let player = AVQueuePlayer()
    private var timeObserver: Any?

    func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            assertionFailure("Unable to configure playback audio session: \(error)")
        }
        configureRemoteCommands()
        observeTime()
    }

    func play(book: AudioBook, chapter: AudioBookChapter? = nil) {
        currentBook = book
        currentChapter = chapter ?? book.chapters.first
        durationSeconds = Double(currentChapter?.durationSeconds ?? book.totalDurationSeconds).clamped(to: 1...)

        if let urlString = currentChapter?.listenURL,
           let url = URL(string: urlString) {
            player.replaceCurrentItem(with: AVPlayerItem(url: url))
            player.rate = playbackSpeed
            player.play()
        }
        isPlaying = true
        updateNowPlaying()
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func toggle() {
        isPlaying ? pause() : resume()
    }

    func resume() {
        player.rate = playbackSpeed
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func setSpeed(_ speed: Float) {
        playbackSpeed = speed
        if isPlaying {
            player.rate = speed
        }
        updateNowPlaying()
    }

    func seek(by delta: Double) {
        let target = max(0, min(currentTimeSeconds + delta, durationSeconds))
        seek(to: target)
    }

    func seek(to target: Double) {
        let target = max(0, min(target, durationSeconds))
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
        currentTimeSeconds = target
        updateNowPlaying()
    }

    func skipChapter(by delta: Int) {
        guard let book = currentBook,
              let currentChapter,
              let currentIndex = book.chapters.firstIndex(where: { $0.id == currentChapter.id })
        else { return }
        let nextIndex = min(max(currentIndex + delta, 0), book.chapters.count - 1)
        play(book: book, chapter: book.chapters[nextIndex])
    }

    private func configureRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()
        commandCenter.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resume() }
            return .success
        }
        commandCenter.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.pause() }
            return .success
        }
        commandCenter.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.toggle() }
            return .success
        }
        commandCenter.skipForwardCommand.preferredIntervals = [15]
        commandCenter.skipForwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.seek(by: 15) }
            return .success
        }
        commandCenter.skipBackwardCommand.preferredIntervals = [30]
        commandCenter.skipBackwardCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.seek(by: -30) }
            return .success
        }
        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.skipChapter(by: 1) }
            return .success
        }
        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.skipChapter(by: -1) }
            return .success
        }
    }

    private func observeTime() {
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 1, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            guard let self else { return }
            Task { @MainActor in
                self.currentTimeSeconds = time.seconds.isFinite ? time.seconds : 0
                self.updateNowPlaying()
            }
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
            MPMediaItemPropertyPlaybackDuration: durationSeconds,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? playbackSpeed : 0,
            MPMediaItemPropertyMediaType: MPMediaType.anyAudio.rawValue,
        ]
        if let reader = currentChapter?.reader {
            info[MPMediaItemPropertyComments] = "Read by \(reader)"
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}

private extension Comparable {
    func clamped(to limits: PartialRangeFrom<Self>) -> Self {
        max(self, limits.lowerBound)
    }
}
