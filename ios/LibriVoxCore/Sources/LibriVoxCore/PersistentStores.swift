import Foundation
import Security

public struct JSONStoreEnvelope<Payload: Codable & Sendable>: Codable, Sendable {
    public var schemaVersion: Int
    public var payload: Payload

    public init(schemaVersion: Int, payload: Payload) {
        self.schemaVersion = schemaVersion
        self.payload = payload
    }
}

public enum ApplicationSupportStoreDirectory {
    public static func baseURL(
        fileManager: FileManager = .default,
        appFolderName: String = "LibriVoxMobile"
    ) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return base
            .appendingPathComponent(appFolderName, isDirectory: true)
            .appendingPathComponent("Stores", isDirectory: true)
    }

    public static func fileURL(
        named fileName: String,
        fileManager: FileManager = .default,
        appFolderName: String = "LibriVoxMobile"
    ) -> URL {
        baseURL(fileManager: fileManager, appFolderName: appFolderName)
            .appendingPathComponent(fileName)
    }
}

public actor JSONStoreFile<Payload: Codable & Sendable> {
    public let fileURL: URL
    public let schemaVersion: Int
    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(
        fileURL: URL,
        schemaVersion: Int = 1,
        fileManager: FileManager = .default
    ) {
        self.fileURL = fileURL
        self.schemaVersion = schemaVersion
        self.fileManager = fileManager
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        self.encoder = encoder
        self.decoder = JSONDecoder()
    }

    public func load() -> Payload? {
        guard fileManager.fileExists(atPath: fileURL.path),
              let data = try? Data(contentsOf: fileURL)
        else { return nil }

        if let envelope = try? decoder.decode(JSONStoreEnvelope<Payload>.self, from: data) {
            return envelope.payload
        }

        return try? decoder.decode(Payload.self, from: data)
    }

    public func load(default defaultValue: @autoclosure () -> Payload) -> Payload {
        load() ?? defaultValue()
    }

    public func save(_ payload: Payload) throws {
        try fileManager.createDirectory(
            at: fileURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let envelope = JSONStoreEnvelope(schemaVersion: schemaVersion, payload: payload)
        let data = try encoder.encode(envelope)
        try data.write(to: fileURL, options: [.atomic])
    }

    public func delete() throws {
        guard fileManager.fileExists(atPath: fileURL.path) else { return }
        try fileManager.removeItem(at: fileURL)
    }
}

public struct KeychainLocalUserProfileStore: Sendable {
    private let service: String
    private let account: String

    public init(
        service: String = "com.ababilinski.librivoxmobile.local-user-profile",
        account: String = "local-user-profile"
    ) {
        self.service = service
        self.account = account
    }

    public func load() -> LocalUserProfile? {
        var query = baseQuery()
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecReturnData as String] = true

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(LocalUserProfile.self, from: data)
    }

    public func loadOrCreate() -> LocalUserProfile {
        if let profile = load() {
            return profile
        }

        let profile = LocalUserProfile()
        _ = save(profile)
        return profile
    }

    @discardableResult
    public func save(_ profile: LocalUserProfile) -> Bool {
        guard let data = try? JSONEncoder().encode(profile) else { return false }
        let updateAttributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]

        let updateStatus = SecItemUpdate(baseQuery() as CFDictionary, updateAttributes as CFDictionary)
        if updateStatus == errSecSuccess {
            return true
        }

        guard updateStatus == errSecItemNotFound else { return false }
        var addQuery = baseQuery()
        addQuery[kSecValueData as String] = data
        addQuery[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(addQuery as CFDictionary, nil) == errSecSuccess
    }

    @discardableResult
    public func delete() -> Bool {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

public actor PersistentLibraryStore: LibraryStoring {
    private let defaultsKey: String
    private let store: JSONStoreFile<[AudioBook]>

    public init(
        defaultsKey: String = "LibriVoxMobile.libraryBooks",
        store: JSONStoreFile<[AudioBook]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "library-books.json")
        )
    }

    public func snapshot() async -> [AudioBook] {
        var books = await load()
        if !books.contains(where: { $0.id == AudioBook.theJungle.id }) {
            books.append(AudioBook.theJungle)
            await saveAll(books)
        }
        return sorted(books)
    }

    public func save(_ book: AudioBook) async {
        var books = await load()
        var saved = book
        saved.libraryStatus = .inLibrary
        if saved.addedAt == nil {
            saved.addedAt = .now
        }
        books.removeAll { $0.id == saved.id }
        books.append(saved)
        await saveAll(books)
    }

    public func remove(bookID: String) async {
        var books = await load()
        books.removeAll { $0.id == bookID && bookID != AudioBook.theJungle.id }
        await saveAll(books)
    }

    private func load() async -> [AudioBook] {
        if let saved = await store.load() {
            return saved
        }

        let migrated = Self.loadLegacy(defaultsKey: defaultsKey) ?? AudioBook.seedLibrary
        try? await store.save(sorted(migrated))
        return migrated
    }

    private func saveAll(_ books: [AudioBook]) async {
        try? await store.save(sorted(books))
    }

    private static func loadLegacy(defaultsKey: String) -> [AudioBook]? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey)
        else { return nil }
        return try? JSONDecoder().decode([AudioBook].self, from: data)
    }

    private func sorted(_ books: [AudioBook]) -> [AudioBook] {
        books.uniqued { $0.id }.sorted { lhs, rhs in
            if lhs.id == AudioBook.theJungle.id { return true }
            if rhs.id == AudioBook.theJungle.id { return false }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
    }
}

public actor PersistentBookmarkStore: BookmarkStoring {
    private let defaultsKey: String
    private let store: JSONStoreFile<[Bookmark]>

    public init(
        defaultsKey: String = "LibriVoxMobile.bookmarks",
        store: JSONStoreFile<[Bookmark]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "bookmarks.json")
        )
    }

    public func bookmarks(for bookID: String) async -> [Bookmark] {
        await load()
            .filter { $0.bookID == bookID }
            .sorted { $0.positionMilliseconds < $1.positionMilliseconds }
    }

    public func save(_ bookmark: Bookmark) async {
        var bookmarks = await load()
        bookmarks.removeAll { $0.id == bookmark.id }
        bookmarks.append(bookmark)
        await saveAll(bookmarks)
    }

    public func remove(bookmarkID: UUID) async {
        var bookmarks = await load()
        bookmarks.removeAll { $0.id == bookmarkID }
        await saveAll(bookmarks)
    }

    private func load() async -> [Bookmark] {
        if let saved = await store.load() {
            return saved
        }

        let migrated = Self.loadLegacy(defaultsKey: defaultsKey) ?? []
        if !migrated.isEmpty {
            await saveAll(migrated)
        }
        return migrated
    }

    private func saveAll(_ bookmarks: [Bookmark]) async {
        try? await store.save(bookmarks)
    }

    private static func loadLegacy(defaultsKey: String) -> [Bookmark]? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey)
        else { return nil }
        return try? JSONDecoder().decode([Bookmark].self, from: data)
    }
}

public actor PersistentPlaybackProgressStore: PlaybackProgressStoring {
    private let defaultsKey: String
    private let store: JSONStoreFile<[PlaybackProgress]>

    public init(
        defaultsKey: String = "LibriVoxMobile.playbackProgress",
        store: JSONStoreFile<[PlaybackProgress]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "playback-progress.json")
        )
    }

    public func snapshot() async -> [String: PlaybackProgress] {
        Dictionary(uniqueKeysWithValues: await load().map { ($0.bookID, $0) })
    }

    public func progress(for bookID: String) async -> PlaybackProgress? {
        await load().first { $0.bookID == bookID }
    }

    public func save(_ progress: PlaybackProgress) async {
        var allProgress = await load()
        allProgress.removeAll { $0.bookID == progress.bookID }
        allProgress.append(progress)
        await saveAll(allProgress)
    }

    public func remove(bookID: String) async {
        var allProgress = await load()
        allProgress.removeAll { $0.bookID == bookID }
        await saveAll(allProgress)
    }

    private func load() async -> [PlaybackProgress] {
        if let saved = await store.load() {
            return saved
        }

        let migrated = Self.loadLegacy(defaultsKey: defaultsKey) ?? []
        if !migrated.isEmpty {
            await saveAll(migrated)
        }
        return migrated
    }

    private func saveAll(_ progress: [PlaybackProgress]) async {
        let sorted = progress.sorted { $0.updatedAt > $1.updatedAt }
        try? await store.save(sorted)
    }

    private static func loadLegacy(defaultsKey: String) -> [PlaybackProgress]? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey)
        else { return nil }
        return try? JSONDecoder().decode([PlaybackProgress].self, from: data)
    }
}

public actor PersistentPlaybackFeedbackStore: PlaybackFeedbackStoring {
    private let defaultsKey: String
    private let store: JSONStoreFile<[PlaybackFeedbackEntry]>

    public init(
        defaultsKey: String = "LibriVoxMobile.playbackFeedback",
        store: JSONStoreFile<[PlaybackFeedbackEntry]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "playback-feedback.json")
        )
    }

    public func snapshot() async -> [String: PlaybackFeedbackEntry] {
        Dictionary(uniqueKeysWithValues: await load().map { ($0.id, $0) })
    }

    public func feedback(bookID: String?, chapterID: String?, scope: FeedbackControlScope) async -> PlaybackFeedback {
        guard let key = PlaybackFeedbackEntry.key(bookID: bookID, chapterID: chapterID, scope: scope)
        else { return .none }
        return await load().first { $0.id == key }?.feedback ?? .none
    }

    public func toggleFeedback(
        bookID: String?,
        chapterID: String?,
        target: PlaybackFeedback,
        scope: FeedbackControlScope
    ) async -> PlaybackFeedback {
        guard let key = PlaybackFeedbackEntry.key(bookID: bookID, chapterID: chapterID, scope: scope),
              let bookID
        else { return .none }
        var entries = await load()
        let current = entries.first { $0.id == key }?.feedback ?? .none
        let next: PlaybackFeedback = current == target ? .none : target
        entries.removeAll { $0.id == key }
        if next != .none {
            entries.append(
                PlaybackFeedbackEntry(
                    id: key,
                    bookID: bookID,
                    chapterID: scope == .chapter ? chapterID : nil,
                    feedback: next
                )
            )
        }
        await saveAll(entries)
        return next
    }

    private func load() async -> [PlaybackFeedbackEntry] {
        if let saved = await store.load() {
            return saved
        }

        let migrated = Self.loadLegacy(defaultsKey: defaultsKey) ?? []
        if !migrated.isEmpty {
            await saveAll(migrated)
        }
        return migrated
    }

    private func saveAll(_ entries: [PlaybackFeedbackEntry]) async {
        let sorted = entries.sorted { $0.updatedAt > $1.updatedAt }
        try? await store.save(sorted)
    }

    private static func loadLegacy(defaultsKey: String) -> [PlaybackFeedbackEntry]? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey)
        else { return nil }
        return try? JSONDecoder().decode([PlaybackFeedbackEntry].self, from: data)
    }
}

public actor PersistentQueueStore {
    private let defaultsKey: String
    private let store: JSONStoreFile<[QueueEntry]>

    public init(
        defaultsKey: String = "LibriVoxMobile.queue",
        store: JSONStoreFile<[QueueEntry]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "playback-queue.json")
        )
    }

    public func snapshot() async -> [QueueEntry] {
        if let saved = await store.load() {
            return saved.sorted { $0.addedAt < $1.addedAt }
        }

        let migrated = Self.loadLegacy(defaultsKey: defaultsKey) ?? []
        if !migrated.isEmpty {
            await save(migrated)
        }
        return migrated.sorted { $0.addedAt < $1.addedAt }
    }

    public func save(_ queue: [QueueEntry]) async {
        let sorted = queue.sorted { $0.addedAt < $1.addedAt }
        try? await store.save(sorted)
    }

    private static func loadLegacy(defaultsKey: String) -> [QueueEntry]? {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey)
        else { return nil }
        return try? JSONDecoder().decode([QueueEntry].self, from: data)
    }
}

public actor PersistentSearchHistoryStore {
    private let defaultsKey: String
    private let store: JSONStoreFile<[String]>

    public init(
        defaultsKey: String = "LibriVoxMobile.searchHistory",
        store: JSONStoreFile<[String]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "search-history.json")
        )
    }

    public func snapshot() async -> [String] {
        if let saved = await store.load() {
            return saved
        }

        let migrated = UserDefaults.standard.stringArray(forKey: defaultsKey) ?? []
        if !migrated.isEmpty {
            await save(migrated)
        }
        return migrated
    }

    public func save(_ entries: [String]) async {
        try? await store.save(entries)
    }
}

public actor PersistentDownloadService: DownloadServicing {
    private let defaultsKey: String
    private let fileManager: FileManager
    private let downloadsDirectory: URL
    private let store: JSONStoreFile<[DownloadItem]>
    private var items: [DownloadItem]
    private var didLoadFileStore = false
    private var activeDownloads: Set<String> = []
    private var activeDownloadTasks: [String: Task<Void, Never>] = [:]

    public init(
        defaultsKey: String = "LibriVoxMobile.downloads",
        fileManager: FileManager = .default,
        downloadsDirectory: URL? = nil,
        store: JSONStoreFile<[DownloadItem]>? = nil
    ) {
        self.defaultsKey = defaultsKey
        self.fileManager = fileManager
        self.downloadsDirectory = downloadsDirectory ?? Self.defaultDownloadsDirectory(fileManager: fileManager)
        self.store = store ?? JSONStoreFile(
            fileURL: ApplicationSupportStoreDirectory.fileURL(named: "downloads.json")
        )
        self.items = Self.loadItems(defaultsKey: defaultsKey)
    }

    public func downloads() async -> [DownloadItem] {
        await ensureLoaded()
        return items.sorted { lhs, rhs in
            lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }
    }

    public func enqueue(book: AudioBook) async {
        await enqueue(book: book, chapter: nil)
    }

    public func enqueue(book: AudioBook, chapter requestedChapter: AudioBookChapter?) async {
        await ensureLoaded()
        if let chapter = requestedChapter {
            guard chapter.listenURL?.nilIfBlank != nil else { return }
            let taskKey = Self.taskKey(bookID: book.id, chapterID: chapter.id)
            upsert(
                bookID: book.id,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: .downloading,
                progress: 0,
                book: book,
                chapter: chapter
            )
            await persist()
            guard activeDownloads.insert(taskKey).inserted else { return }
            let task = Task {
                await self.download(book: book, chapters: [chapter], taskKey: taskKey, updatesBookAggregate: false)
            }
            activeDownloadTasks[taskKey] = task
            return
        }

        let playableChapters = book.chapters.filter { $0.listenURL?.nilIfBlank != nil }
        let taskKey = Self.taskKey(bookID: book.id, chapterID: nil)
        if let index = items.firstIndex(where: { $0.bookID == book.id && $0.chapterID == nil }) {
            switch items[index].state {
            case .queued, .downloading:
                items[index].applyDownloadMetadata(book: book, chapter: nil)
                seedQueuedChapterItems(book: book, chapters: playableChapters)
                await persist()
            case .downloaded:
                items[index].applyDownloadMetadata(book: book, chapter: nil)
                await persist()
                return
            case .notDownloaded, .failed:
                items[index].title = book.title
                items[index].state = .downloading
                items[index].progress = 0
                items[index].applyDownloadMetadata(book: book, chapter: nil)
            }
        } else {
            let item = DownloadItem(
                bookID: book.id,
                title: book.title,
                state: .downloading,
                progress: 0,
                bookTitle: book.title,
                bookAuthor: book.author,
                bookSource: book.source,
                coverImageURL: book.coverImageURL,
                fullCoverImageURL: book.fullCoverImageURL,
                localCoverFileName: book.localCoverFileName
            )
            items.append(item)
        }
        seedQueuedChapterItems(book: book, chapters: playableChapters)
        await persist()
        guard activeDownloads.insert(taskKey).inserted else { return }
        let task = Task {
            await self.download(book: book, chapters: playableChapters, taskKey: taskKey, updatesBookAggregate: true)
        }
        activeDownloadTasks[taskKey] = task
    }

    public func cancel(bookID: String) async {
        await ensureLoaded()
        let activeKeys = activeDownloadTasks.keys.filter { Self.taskKey($0, belongsTo: bookID) }
        for key in activeKeys {
            activeDownloadTasks[key]?.cancel()
            activeDownloadTasks[key] = nil
            activeDownloads.remove(key)
        }
        items.removeAll { item in
            item.bookID == bookID && Self.isActiveDownloadState(item.state)
        }
        await persist()
    }

    public func markDownloaded(bookID: String, chapterID: String?) async {
        await ensureLoaded()
        await update(bookID: bookID, chapterID: chapterID, state: .downloaded, progress: 1)
    }

    public func localFileURL(bookID: String, chapter: AudioBookChapter) async -> URL? {
        let bookDirectory = downloadsDirectory.appendingPathComponent(bookID.safeFileName, isDirectory: true)
        let candidateNames = [
            chapter.localFileName?.nilIfBlank,
            chapter.downloadFileName,
        ].compactMap { $0 }

        for candidateName in candidateNames {
            let fileURL = bookDirectory.appendingPathComponent(candidateName)
            if fileManager.fileExists(atPath: fileURL.path) {
                return fileURL
            }
        }
        return nil
    }

    public func storageBytes(bookID: String) async -> Int64 {
        let url = downloadsDirectory.appendingPathComponent(bookID.safeFileName, isDirectory: true)
        return Self.storageBytes(at: url, fileManager: fileManager)
    }

    public func storageBytes(bookID: String, chapter: AudioBookChapter) async -> Int64 {
        guard let url = await localFileURL(bookID: bookID, chapter: chapter) else { return 0 }
        return Self.fileStorageBytes(at: url, fileManager: fileManager)
    }

    public func delete(bookID: String) async {
        await ensureLoaded()
        let activeKeys = activeDownloadTasks.keys.filter { Self.taskKey($0, belongsTo: bookID) }
        for key in activeKeys {
            activeDownloadTasks[key]?.cancel()
            activeDownloadTasks[key] = nil
            activeDownloads.remove(key)
        }
        items.removeAll { $0.bookID == bookID }
        await persist()
        let url = downloadsDirectory.appendingPathComponent(bookID.safeFileName, isDirectory: true)
        try? fileManager.removeItem(at: url)
    }

    public func delete(book: AudioBook, chapter: AudioBookChapter) async {
        await ensureLoaded()
        let taskKey = Self.taskKey(bookID: book.id, chapterID: chapter.id)
        activeDownloadTasks[taskKey]?.cancel()
        activeDownloadTasks[taskKey] = nil
        activeDownloads.remove(taskKey)

        if let fileURL = await localFileURL(bookID: book.id, chapter: chapter) {
            try? fileManager.removeItem(at: fileURL)
        }

        items.removeAll { $0.bookID == book.id && ($0.chapterID == chapter.id || $0.chapterID == nil) }
        let playableChapters = book.chapters.filter { $0.listenURL?.nilIfBlank != nil }
        var downloadedCount = 0
        for remainingChapter in playableChapters where remainingChapter.id != chapter.id {
            if await localFileURL(bookID: book.id, chapter: remainingChapter) != nil {
                downloadedCount += 1
                upsert(
                    bookID: book.id,
                    chapterID: remainingChapter.id,
                    title: remainingChapter.displayTitle,
                    state: .downloaded,
                    progress: 1,
                    book: book,
                    chapter: remainingChapter
                )
            }
        }

        if downloadedCount == playableChapters.count, downloadedCount > 0 {
            upsert(
                bookID: book.id,
                chapterID: nil,
                title: book.title,
                state: .downloaded,
                progress: 1,
                book: book,
                chapter: nil
            )
        }
        await persist()

        if downloadedCount == 0 {
            let bookDirectory = downloadsDirectory.appendingPathComponent(book.id.safeFileName, isDirectory: true)
            if (try? fileManager.contentsOfDirectory(atPath: bookDirectory.path).isEmpty) == true {
                try? fileManager.removeItem(at: bookDirectory)
            }
        }
    }

    private func download(
        book: AudioBook,
        chapters playableChapters: [AudioBookChapter],
        taskKey: String,
        updatesBookAggregate: Bool
    ) async {
        await ensureLoaded()
        defer {
            activeDownloads.remove(taskKey)
            activeDownloadTasks[taskKey] = nil
        }
        guard !playableChapters.isEmpty else {
            if updatesBookAggregate {
                await update(
                    bookID: book.id,
                    chapterID: nil,
                    title: book.title,
                    state: .failed,
                    progress: 0,
                    book: book,
                    chapter: nil
                )
            }
            return
        }

        let bookDirectory = downloadsDirectory.appendingPathComponent(book.id.safeFileName, isDirectory: true)
        try? fileManager.createDirectory(at: bookDirectory, withIntermediateDirectories: true)

        for (index, chapter) in playableChapters.enumerated() {
            guard !Task.isCancelled else { return }
            guard let urlString = chapter.listenURL,
                  let url = URL(string: urlString)
            else { continue }
            do {
                if await localFileURL(bookID: book.id, chapter: chapter) != nil {
                    await update(
                        bookID: book.id,
                        chapterID: chapter.id,
                        title: chapter.displayTitle,
                        state: .downloaded,
                        progress: 1,
                        book: book,
                        chapter: chapter
                    )
                    if updatesBookAggregate {
                        let progress = Double(index + 1) / Double(playableChapters.count)
                        await update(
                            bookID: book.id,
                            chapterID: nil,
                            title: book.title,
                            state: progress >= 1 ? .downloaded : .downloading,
                            progress: progress,
                            book: book,
                            chapter: nil
                        )
                    }
                    continue
                }
                await update(
                    bookID: book.id,
                    chapterID: chapter.id,
                    title: chapter.displayTitle,
                    state: .downloading,
                    progress: 0,
                    book: book,
                    chapter: chapter
                )
                let (temporaryURL, _) = try await URLSession.shared.download(from: url)
                try Task.checkCancellation()
                let fileURL = bookDirectory.appendingPathComponent(chapter.downloadFileName)
                try? fileManager.removeItem(at: fileURL)
                try fileManager.moveItem(at: temporaryURL, to: fileURL)
                await update(
                    bookID: book.id,
                    chapterID: chapter.id,
                    title: chapter.displayTitle,
                    state: .downloaded,
                    progress: 1,
                    book: book,
                    chapter: chapter
                )
                let progress = Double(index + 1) / Double(playableChapters.count)
                if updatesBookAggregate {
                    await update(
                        bookID: book.id,
                        chapterID: nil,
                        title: book.title,
                        state: progress >= 1 ? .downloaded : .downloading,
                        progress: progress,
                        book: book,
                        chapter: nil
                    )
                }
            } catch {
                if Task.isCancelled { return }
                await update(
                    bookID: book.id,
                    chapterID: chapter.id,
                    title: chapter.displayTitle,
                    state: .failed,
                    progress: 0,
                    book: book,
                    chapter: chapter
                )
                if updatesBookAggregate {
                    await update(
                        bookID: book.id,
                        chapterID: nil,
                        title: book.title,
                        state: .failed,
                        progress: Double(index) / Double(playableChapters.count),
                        book: book,
                        chapter: nil
                    )
                }
                return
            }
        }
    }

    private func update(
        bookID: String,
        chapterID: String?,
        title: String? = nil,
        state: DownloadState,
        progress: Double,
        book: AudioBook? = nil,
        chapter: AudioBookChapter? = nil
    ) async {
        await ensureLoaded()
        upsert(
            bookID: bookID,
            chapterID: chapterID,
            title: title,
            state: state,
            progress: progress,
            book: book,
            chapter: chapter
        )
        await persist()
    }

    private func upsert(
        bookID: String,
        chapterID: String?,
        title: String?,
        state: DownloadState,
        progress: Double,
        book: AudioBook? = nil,
        chapter: AudioBookChapter? = nil
    ) {
        for index in items.indices where items[index].bookID == bookID && items[index].chapterID == chapterID {
            if let title {
                items[index].title = title
            }
            items[index].state = state
            items[index].progress = progress
            items[index].applyDownloadMetadata(book: book, chapter: chapter)
            return
        }
        var item = DownloadItem(
            bookID: bookID,
            chapterID: chapterID,
            title: title ?? "Download",
            state: state,
            progress: progress
        )
        item.applyDownloadMetadata(book: book, chapter: chapter)
        items.append(item)
    }

    private func seedQueuedChapterItems(book: AudioBook, chapters: [AudioBookChapter]) {
        for chapter in chapters {
            if let index = items.firstIndex(where: { $0.bookID == book.id && $0.chapterID == chapter.id }) {
                items[index].title = chapter.displayTitle
                items[index].applyDownloadMetadata(book: book, chapter: chapter)
                switch items[index].state {
                case .downloaded, .queued, .downloading:
                    break
                case .notDownloaded, .failed:
                    items[index].state = .queued
                    items[index].progress = 0
                }
            } else {
                items.append(
                    DownloadItem(
                        bookID: book.id,
                        chapterID: chapter.id,
                        title: chapter.displayTitle,
                        state: .queued,
                        progress: 0,
                        bookTitle: book.title,
                        bookAuthor: book.author,
                        bookSource: book.source,
                        coverImageURL: book.coverImageURL,
                        fullCoverImageURL: book.fullCoverImageURL,
                        localCoverFileName: book.localCoverFileName,
                        chapterNumber: chapter.number,
                        chapterReader: chapter.reader,
                        chapterDurationSeconds: chapter.durationSeconds,
                        chapterListenURL: chapter.listenURL,
                        chapterLocalFileName: chapter.localFileName,
                        chapterMimeType: chapter.mimeType
                    )
                )
            }
        }
    }

    private func ensureLoaded() async {
        guard !didLoadFileStore else { return }
        didLoadFileStore = true
        if let saved = await store.load() {
            items = saved
            return
        }
        if !items.isEmpty {
            await persist()
        }
    }

    private func persist() async {
        try? await store.save(items)
    }

    private static func loadItems(defaultsKey: String) -> [DownloadItem] {
        guard let data = UserDefaults.standard.data(forKey: defaultsKey),
              let decoded = try? JSONDecoder().decode([DownloadItem].self, from: data)
        else { return [] }
        return decoded
    }

    private static func defaultDownloadsDirectory(fileManager: FileManager) -> URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        return base
            .appendingPathComponent("LibriVoxMobile", isDirectory: true)
            .appendingPathComponent("Downloads", isDirectory: true)
    }

    private static func taskKey(bookID: String, chapterID: String?) -> String {
        "\(bookID)\u{1F}\(chapterID ?? "book")"
    }

    private static func taskKey(_ taskKey: String, belongsTo bookID: String) -> Bool {
        taskKey == Self.taskKey(bookID: bookID, chapterID: nil) || taskKey.hasPrefix("\(bookID)\u{1F}")
    }

    private static func isActiveDownloadState(_ state: DownloadState) -> Bool {
        state == .queued || state == .downloading
    }

    private static func storageBytes(at url: URL, fileManager: FileManager) -> Int64 {
        guard let enumerator = fileManager.enumerator(
            at: url,
            includingPropertiesForKeys: [.isRegularFileKey, .fileSizeKey],
            options: [.skipsHiddenFiles]
        ) else { return 0 }

        var total: Int64 = 0
        for case let fileURL as URL in enumerator {
            guard let values = try? fileURL.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
                  values.isRegularFile == true,
                  let size = values.fileSize
            else { continue }
            total += Int64(size)
        }
        return total
    }

    private static func fileStorageBytes(at url: URL, fileManager: FileManager) -> Int64 {
        guard fileManager.fileExists(atPath: url.path),
              let values = try? url.resourceValues(forKeys: [.isRegularFileKey, .fileSizeKey]),
              values.isRegularFile == true,
              let size = values.fileSize
        else { return 0 }
        return Int64(size)
    }
}

private extension Array {
    func uniqued<Key: Hashable>(by key: (Element) -> Key) -> [Element] {
        var seen: Set<Key> = []
        return filter { seen.insert(key($0)).inserted }
    }
}

private extension DownloadItem {
    mutating func applyDownloadMetadata(book: AudioBook?, chapter: AudioBookChapter?) {
        if let book {
            bookTitle = book.title
            bookAuthor = book.author
            bookSource = book.source
            coverImageURL = book.coverImageURL
            fullCoverImageURL = book.fullCoverImageURL
            localCoverFileName = book.localCoverFileName
        }

        if let chapter {
            chapterNumber = chapter.number
            chapterReader = chapter.reader
            chapterDurationSeconds = chapter.durationSeconds
            chapterListenURL = chapter.listenURL
            chapterLocalFileName = chapter.localFileName
            chapterMimeType = chapter.mimeType
        }
    }
}

private extension AudioBookChapter {
    var downloadFileName: String {
        let ext = URL(string: listenURL ?? "")?.pathExtension.nilIfBlank ?? "mp3"
        return "\(id.safeFileName).\(ext)"
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    var safeFileName: String {
        replacingOccurrences(of: #"[^a-zA-Z0-9._-]+"#, with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .nilIfBlank ?? UUID().uuidString
    }
}
