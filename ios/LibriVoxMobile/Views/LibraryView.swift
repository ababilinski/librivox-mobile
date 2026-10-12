import Foundation
import LibriVoxCore
import SwiftUI

private struct LibraryBookDetailSelection: Identifiable {
    let id = UUID()
    let book: AudioBook
    let collection: BookDetailCollection
}

struct LibraryView: View {
    @Environment(AppModel.self) private var app
    @State private var searchText = ""
    @State private var filter: LibraryFilter = .all
    @State private var actionMenuBook: AudioBook?
    @State private var actionMenuAnchor: CGPoint?
    @State private var detailSelection: LibraryBookDetailSelection?
    @State private var readerBook: AudioBook?

    var visibleBooks: [AudioBook] {
        let base = baseBooks
        guard !searchText.isEmpty else { return base }
        return base.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
                $0.author.localizedCaseInsensitiveContains(searchText) ||
                $0.narrators.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }

    private var baseBooks: [AudioBook] {
        let books = app.settings.downloadsOnlyModeEnabled ? downloadedBooks : app.libraryBooks
        switch filter {
        case .all:
            return books
        case .liked:
            return books.filter(\.isFavorite)
        case .downloaded:
            return books.filter(isDownloaded)
        case .inProgress:
            return books.filter { app.progress(for: $0)?.hasStarted == true }
        }
    }

    private var downloadedBooks: [AudioBook] {
        app.libraryBooks.filter(isDownloaded)
    }

    var body: some View {
        GeometryReader { geometry in
            let books = visibleBooks
            ZStack(alignment: .topLeading) {
                GlassArtworkBackground(book: nil)

                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        LibraryFilterRow(selection: $filter)

                        if app.settings.downloadsOnlyModeEnabled {
                            Label("Downloads only mode is on", systemImage: "arrow.down.circle")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .liquidGlassPanel(cornerRadius: 18, interactive: false)
                        }

                        if books.isEmpty {
                            ContentUnavailableView(
                                emptyTitle,
                                systemImage: "books.vertical",
                                description: Text(emptyDescription)
                            )
                            .frame(maxWidth: .infinity, minHeight: 260)
                            .padding(.top, 22)
                        } else {
                            LazyVStack(spacing: 0) {
                                ForEach(Array(books.enumerated()), id: \.element.id) { index, book in
                                    AnchoredBookValueLink(
                                        book: book,
                                        onActions: showActions(for:anchor:),
                                        onShowDetails: {
                                            detailSelection = LibraryBookDetailSelection(
                                                book: $0,
                                                collection: BookDetailCollection(title: "Library", books: books)
                                            )
                                        }
                                    ) {
                                        LibraryBookRow(
                                            book: book,
                                            isDownloaded: isDownloaded(book),
                                            playbackProgress: app.progress(for: book),
                                            downloadItem: app.download(for: book)
                                        )
                                            .padding(.vertical, 8)
                                            .contentShape(Rectangle())
                                    }
                                    .accessibilityIdentifier("library-book-\(book.id)")

                                    if index < books.count - 1 {
                                        Divider()
                                            .padding(.leading, 88)
                                            .opacity(0.42)
                                    }
                                }
                            }
                            .bookDetailCollection(books, title: "Library")
                            .padding(.horizontal, 2)
                        }
                    }
                    .padding()
                    .padding(.bottom, 220)
                    .frame(width: geometry.size.width, alignment: .leading)
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .top)
                .scrollEdgeEffectStyle(.soft, for: .top)
            }
        }
        .overlay {
            if let actionMenuBook {
                AppleMusicBookActionsOverlay(
                    book: actionMenuBook,
                    context: .library,
                    anchorPoint: actionMenuAnchor,
                    onDismiss: {
                        withAnimation(.snappy(duration: 0.18)) {
                            self.actionMenuBook = nil
                            self.actionMenuAnchor = nil
                        }
                    },
                    onShowDetails: {
                        detailSelection = LibraryBookDetailSelection(
                            book: actionMenuBook,
                            collection: BookDetailCollection(title: "Library", books: visibleBooks)
                        )
                    },
                    onRead: actionMenuBook.canReadSourceText ? {
                        readerBook = actionMenuBook
                    } : nil
                )
            }
        }
        .navigationTitle("Library")
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search your library")
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink {
                    DownloadsView()
                } label: {
                    Image(systemName: "arrow.down.circle")
                        .foregroundStyle(.primary)
                }
                .tint(.primary)
                .accessibilityLabel("Manage Downloads")

                SettingsToolbarButton()
            }
        }
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
        .sheet(item: $detailSelection) { selection in
            NavigationStack {
                BookDetailBrowser(book: selection.book, collection: selection.collection)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                detailSelection = nil
                            }
                        }
                    }
            }
        }
        .sheet(item: $readerBook) { book in
            NavigationStack {
                ReaderView(book: book)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                readerBook = nil
                            }
                            .tint(.primary)
                        }
                    }
            }
        }
    }

    private var emptyTitle: String {
        if app.settings.downloadsOnlyModeEnabled { return "No Downloaded Books" }
        switch filter {
        case .all: return "No Books"
        case .liked: return "No Liked Books"
        case .downloaded: return "No Downloaded Books"
        case .inProgress: return "Nothing In Progress"
        }
    }

    private var emptyDescription: String {
        switch filter {
        case .all:
            return "Saved audiobooks will appear here."
        case .liked:
            return "Books you like will appear here."
        case .downloaded:
            return "Downloaded audiobooks will appear here."
        case .inProgress:
            return "Start listening to a saved book to see it here."
        }
    }

    private func isDownloaded(_ book: AudioBook) -> Bool {
        app.downloads.contains { item in
            item.bookID == book.id && item.state == .downloaded
        }
    }

    private func showActions(for book: AudioBook, anchor: CGPoint?) {
        withAnimation(.snappy(duration: 0.2)) {
            actionMenuBook = book
            actionMenuAnchor = anchor
        }
    }

    private func sourceURL(for book: AudioBook) -> URL? {
        [
            book.libriVoxURL,
            book.lit2GoURL,
            book.wolneLekturyURL,
            book.gutenbergURL,
        ]
        .compactMap { $0 }
        .compactMap(URL.init(string:))
        .first
    }
}

private struct AnchoredBookValueLink<Content: View>: View {
    @State private var anchor: CGPoint?
    var book: AudioBook
    var onActions: (AudioBook, CGPoint?) -> Void
    var onShowDetails: (AudioBook) -> Void
    var content: () -> Content

    var body: some View {
        BookDetailsNavigationLink(book: book) {
            content()
                .background(InteractionAnchorReader(anchor: $anchor))
        }
        .buttonStyle(.plain)
        .bookActionsContextMenu(
            book: book,
            context: .library,
            previewStyle: .row,
            onShowDetails: { onShowDetails(book) }
        )
    }
}

private enum LibraryFilter: String, CaseIterable, Identifiable {
    case all
    case liked
    case downloaded
    case inProgress

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: "All"
        case .liked: "Liked"
        case .downloaded: "Downloaded"
        case .inProgress: "In Progress"
        }
    }
}

private struct LibraryFilterRow: View {
    @Binding var selection: LibraryFilter

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(LibraryFilter.allCases) { filter in
                    Button {
                        selection = filter
                    } label: {
                        Text(filter.label)
                            .font(.subheadline.weight(selection == filter ? .bold : .medium))
                            .foregroundStyle(selection == filter ? .primary : .secondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(.ultraThinMaterial, in: Capsule())
                            .overlay {
                                Capsule()
                                    .stroke(.white.opacity(selection == filter ? 0.34 : 0.2), lineWidth: 0.8)
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 2)
        }
    }
}

private struct LibraryBookRow: View {
    @Environment(\.libriVoxAccent) private var accent
    var book: AudioBook
    var isDownloaded: Bool
    var playbackProgress: PlaybackProgress?
    var downloadItem: DownloadItem?

    var body: some View {
        HStack(spacing: 14) {
            BookCoverView(
                book: book,
                width: 60,
                height: 80,
                cornerRadius: 12,
                preferFullImage: true,
                downloadBadgeSize: 16
            )
            VStack(alignment: .leading, spacing: 4) {
                Text(book.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Text(book.author)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    if book.totalDurationSeconds > 0 {
                        Text(formatDuration(seconds: book.totalDurationSeconds))
                    }
                    Text(book.source.label)
                    if book.isFavorite {
                        Image(systemName: "star.fill")
                    }
                    if isDownloaded {
                        Image(systemName: "arrow.down.circle.fill")
                    }
                }
                .font(.caption)
                .foregroundStyle(.tertiary)
                .lineLimit(1)
                if let downloadItem, downloadItem.shouldShowInlineProgressStatus {
                    DownloadProgressStatusView(item: downloadItem, style: .inline)
                } else if let playbackProgress, playbackProgress.hasStarted {
                    HStack(spacing: 8) {
                        ProgressView(value: playbackProgress.positionSeconds, total: max(playbackProgress.durationSeconds, 1))
                            .tint(accent)
                        Text("\(Int(playbackProgress.fractionCompleted * 100))%")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(.tertiary)
                    }
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
    }
}

struct DownloadsView: View {
    @Environment(AppModel.self) private var app
    @State private var actionMenuBook: AudioBook?
    @State private var actionMenuAnchor: CGPoint?
    @State private var detailSelection: LibraryBookDetailSelection?
    @State private var actionMenuCollection: BookDetailCollection?
    @State private var readerBook: AudioBook?
    @State private var expandedDownloadBookIDs: Set<String> = []

    private var downloadGroups: [DownloadBookGroup] {
        Dictionary(grouping: app.downloads, by: \.bookID)
            .compactMap { bookID, items -> DownloadBookGroup? in
                guard let firstItem = items.first else { return nil }
                return DownloadBookGroup(
                    bookID: bookID,
                    book: app.book(for: firstItem),
                    items: items,
                    sourceLabel: app.downloadSourceLabel(for: firstItem),
                    storageBytes: app.storageBytes(for: firstItem),
                    chapterStorageBytesByID: app.chapterStorageBytes(for: bookID)
                )
            }
            .sorted { lhs, rhs in
                lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
            }
    }

    private var activeDownloadGroups: [DownloadBookGroup] {
        downloadGroups.filter(\.isActive)
    }

    private var downloadedGroups: [DownloadBookGroup] {
        downloadGroups.filter { group in
            !group.isActive && !group.hasFailedItems && group.hasDownloadedContent
        }
    }

    private var failedDownloadGroups: [DownloadBookGroup] {
        downloadGroups.filter { group in
            !group.isActive && group.hasFailedItems
        }
    }

    private var hasDownloads: Bool {
        !downloadGroups.isEmpty
    }

    private var totalStorageBytes: Int64 {
        downloadGroups.reduce(Int64(0)) { $0 + $1.effectiveStorageBytes }
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                GlassArtworkBackground(book: nil)

                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if totalStorageBytes > 0 {
                            DownloadStorageSummaryPill(bytes: totalStorageBytes)
                        }

                        if !hasDownloads {
                            ContentUnavailableView(
                                "No Downloads",
                                systemImage: "arrow.down.circle",
                                description: Text("Downloaded audiobook files will appear here with cover art and storage use.")
                            )
                            .frame(maxWidth: .infinity, minHeight: 300)
                            .padding(.top, 24)
                        } else {
                            if !activeDownloadGroups.isEmpty {
                                DownloadSectionHeader(text: "In Progress (\(activeDownloadGroups.count))")
                                downloadGroupRows(for: activeDownloadGroups, title: "In Progress")
                            }

                            if !downloadedGroups.isEmpty {
                                DownloadSectionHeader(text: "Downloaded Books (\(downloadedGroups.count))")
                                downloadGroupRows(for: downloadedGroups, title: "Downloaded Books")
                            }

                            if !failedDownloadGroups.isEmpty {
                                DownloadSectionHeader(text: "Needs Attention (\(failedDownloadGroups.count))")
                                downloadGroupRows(for: failedDownloadGroups, title: "Needs Attention")
                            }
                        }
                    }
                    .padding()
                    .padding(.bottom, 220)
                    .frame(width: geometry.size.width, alignment: .leading)
                }
                .frame(width: geometry.size.width, height: geometry.size.height, alignment: .top)
                .scrollEdgeEffectStyle(.soft, for: .top)
            }
        }
        .overlay {
            if let actionMenuBook {
                AppleMusicBookActionsOverlay(
                    book: actionMenuBook,
                    context: .library,
                    anchorPoint: actionMenuAnchor,
                    onDismiss: {
                        withAnimation(.snappy(duration: 0.18)) {
                            self.actionMenuBook = nil
                            self.actionMenuAnchor = nil
                        }
                    },
                    onShowDetails: {
                        detailSelection = LibraryBookDetailSelection(
                            book: actionMenuBook,
                            collection: actionMenuCollection ?? BookDetailCollection(title: "Downloads", books: [actionMenuBook])
                        )
                    },
                    onRead: actionMenuBook.canReadSourceText ? {
                        readerBook = actionMenuBook
                    } : nil
                )
            }
        }
        .navigationTitle("Downloads")
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton()
            }
        }
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
        .sheet(item: $detailSelection) { selection in
            NavigationStack {
                BookDetailBrowser(book: selection.book, collection: selection.collection)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                detailSelection = nil
                            }
                        }
                    }
            }
        }
        .sheet(item: $readerBook) { book in
            NavigationStack {
                ReaderView(book: book)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                readerBook = nil
                            }
                            .tint(.primary)
                        }
                    }
            }
        }
        .task {
            await app.reloadDownloads()
        }
    }

    private func showActions(for book: AudioBook, anchor: CGPoint?, collection: BookDetailCollection) {
        withAnimation(.snappy(duration: 0.2)) {
            actionMenuBook = book
            actionMenuAnchor = anchor
            actionMenuCollection = collection
        }
    }

    private func downloadGroupRows(for groups: [DownloadBookGroup], title: String) -> some View {
        let collection = BookDetailCollection(title: title, books: groups.compactMap(\.book))
        return LazyVStack(spacing: 0) {
            ForEach(Array(groups.enumerated()), id: \.element.id) { index, group in
                let isExpanded = expandedDownloadBookIDs.contains(group.id)

                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8) {
                        Button {
                            toggleDownloadGroup(group.id)
                        } label: {
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                                .rotationEffect(.degrees(isExpanded ? 90 : 0))
                                .frame(width: 30, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(isExpanded ? "Collapse chapters" : "Expand chapters")
                        .disabled(!group.canExpand)
                        .opacity(group.canExpand ? 1 : 0.18)

                        if let book = group.book {
                            AnchoredBookValueLink(
                                book: book,
                                onActions: { book, anchor in
                                    showActions(for: book, anchor: anchor, collection: collection)
                                },
                                onShowDetails: {
                                    detailSelection = LibraryBookDetailSelection(book: $0, collection: collection)
                                }
                            ) {
                                DownloadBookGroupSummary(group: group)
                            }
                        } else {
                            Button {
                                toggleDownloadGroup(group.id)
                            } label: {
                                DownloadBookGroupSummary(group: group)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 10)
                    .contextMenu {
                        downloadDeleteButton(for: group.primaryItem)
                    }

                    if isExpanded {
                        DownloadChapterBreakdownView(group: group)
                            .padding(.leading, 62)
                            .padding(.bottom, 12)
                            .transition(.opacity)
                    }
                }
                .animation(.snappy(duration: 0.18), value: isExpanded)

                if index < groups.count - 1 {
                    Divider()
                        .padding(.leading, 116)
                        .opacity(0.38)
                }
            }
        }
        .bookDetailCollection(collection.books, title: collection.title)
    }

    private func toggleDownloadGroup(_ bookID: String) {
        withAnimation(.snappy(duration: 0.2)) {
            if expandedDownloadBookIDs.contains(bookID) {
                expandedDownloadBookIDs.remove(bookID)
            } else {
                expandedDownloadBookIDs.insert(bookID)
            }
        }
    }

    private func downloadDeleteButton(for item: DownloadItem) -> some View {
        let title = item.isActiveDownload ? "Cancel Download" : "Remove Downloads"
        let systemImage = item.isActiveDownload ? "xmark.circle" : "trash"
        return Button(role: .destructive) {
            Task {
                if item.isActiveDownload {
                    await app.cancelDownload(bookID: item.bookID)
                } else {
                    await app.deleteDownload(bookID: item.bookID)
                }
            }
        } label: {
            Label(title, systemImage: systemImage)
        }
    }
}

private struct DownloadBookGroup: Identifiable {
    var id: String { bookID }
    var bookID: String
    var book: AudioBook?
    var items: [DownloadItem]
    var sourceLabel: String?
    var storageBytes: Int64
    var chapterStorageBytesByID: [String: Int64]
    var effectiveStorageBytes: Int64 {
        max(storageBytes, chapterStorageBytesByID.values.reduce(Int64(0), +))
    }

    var title: String {
        book?.title ?? aggregateItem?.title ?? items.first?.title ?? "Download"
    }

    var author: String? {
        guard let author = book?.author.trimmingCharacters(in: .whitespacesAndNewlines),
              !author.isEmpty
        else { return nil }
        return author
    }

    var primaryItem: DownloadItem {
        aggregateItem ?? items.first!
    }

    var aggregateItem: DownloadItem? {
        items.first { $0.chapterID == nil }
    }

    var chapterItems: [DownloadItem] {
        items
            .filter { $0.chapterID != nil }
            .sorted { lhs, rhs in
                chapterSortIndex(lhs.chapterID) < chapterSortIndex(rhs.chapterID)
            }
    }

    var isActive: Bool {
        items.contains { $0.isActiveDownload }
    }

    var hasFailedItems: Bool {
        items.contains { $0.state == .failed }
    }

    var hasDownloadedContent: Bool {
        isFullyDownloaded || downloadedChapterCount > 0 || items.contains { $0.state == .downloaded }
    }

    var isFullyDownloaded: Bool {
        if aggregateItem?.state == .downloaded {
            return true
        }
        guard totalChapterCount > 0 else {
            return false
        }
        return downloadedChapterCount >= totalChapterCount
    }

    var isPartiallyDownloaded: Bool {
        hasDownloadedContent && !isFullyDownloaded
    }

    var progress: Double {
        if let aggregateItem {
            return aggregateItem.clampedProgress
        }
        guard !items.isEmpty else { return 0 }
        let total = items.reduce(0) { $0 + $1.clampedProgress }
        return min(max(total / Double(items.count), 0), 1)
    }

    var statusState: DownloadState {
        if isFullyDownloaded { return .downloaded }
        if items.contains(where: { $0.state == .downloading }) { return .downloading }
        if items.contains(where: { $0.state == .queued }) { return .queued }
        if hasFailedItems { return .failed }
        if hasDownloadedContent { return .downloaded }
        return .notDownloaded
    }

    var totalChapterCount: Int {
        if !downloadableChapters.isEmpty {
            return downloadableChapters.count
        }
        return chapterItems.count
    }

    var downloadedChapterCount: Int {
        if aggregateItem?.state == .downloaded, totalChapterCount > 0 {
            return totalChapterCount
        }
        return chapterStatuses.filter { $0.state == .downloaded }.count
    }

    var canExpand: Bool {
        !chapterStatuses.isEmpty
    }

    var chapterStatuses: [DownloadChapterStatus] {
        if !downloadableChapters.isEmpty {
            return downloadableChapters.map { chapter in
                let item = item(for: chapter)
                return DownloadChapterStatus(
                    id: chapter.id,
                    title: chapter.displayTitle,
                    subtitle: chapter.reader,
                    state: item?.state ?? .notDownloaded,
                    progress: item?.clampedProgress ?? 0,
                    storageBytes: chapterStorageBytesByID[chapter.id] ?? 0,
                    item: item,
                    chapter: chapter
                )
            }
        }

        return chapterItems.map { item in
            DownloadChapterStatus(
                id: item.chapterID ?? item.id.uuidString,
                title: item.title,
                subtitle: nil,
                state: item.state,
                progress: item.clampedProgress,
                storageBytes: item.chapterID.flatMap { chapterStorageBytesByID[$0] } ?? 0,
                item: item,
                chapter: nil
            )
        }
    }

    private var downloadableChapters: [AudioBookChapter] {
        book?.chapters.filter { chapter in
            chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        } ?? []
    }

    private func item(for chapter: AudioBookChapter) -> DownloadItem? {
        if let exactItem = items.first(where: { $0.chapterID == chapter.id }) {
            return exactItem
        }

        guard let aggregateItem else { return nil }
        if aggregateItem.state == .downloaded {
            return DownloadItem(
                bookID: bookID,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: .downloaded,
                progress: 1
            )
        }

        guard aggregateItem.isActiveDownload,
              let index = downloadableChapters.firstIndex(where: { $0.id == chapter.id }),
              !downloadableChapters.isEmpty
        else { return nil }

        let rawProgress = aggregateItem.clampedProgress * Double(downloadableChapters.count)
        let completedCount = Int(rawProgress.rounded(.down))
        if index < completedCount {
            return DownloadItem(
                bookID: bookID,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: .downloaded,
                progress: 1
            )
        }
        if index == completedCount {
            return DownloadItem(
                bookID: bookID,
                chapterID: chapter.id,
                title: chapter.displayTitle,
                state: aggregateItem.state,
                progress: min(max(rawProgress - Double(completedCount), 0), 1)
            )
        }
        return DownloadItem(
            bookID: bookID,
            chapterID: chapter.id,
            title: chapter.displayTitle,
            state: .queued,
            progress: 0
        )
    }

    private func chapterSortIndex(_ chapterID: String?) -> Int {
        guard let chapterID,
              let chapters = book?.chapters,
              let index = chapters.firstIndex(where: { $0.id == chapterID })
        else { return Int.max }
        return index
    }
}

private struct DownloadChapterStatus: Identifiable {
    var id: String
    var title: String
    var subtitle: String?
    var state: DownloadState
    var progress: Double
    var storageBytes: Int64
    var item: DownloadItem?
    var chapter: AudioBookChapter?
}

private struct DownloadChapterBreakdownView: View {
    var group: DownloadBookGroup

    var body: some View {
        VStack(spacing: 0) {
            ForEach(group.chapterStatuses) { status in
                DownloadChapterStatusRow(group: group, status: status)
                    .padding(.vertical, 8)

                if status.id != group.chapterStatuses.last?.id {
                    Divider()
                        .padding(.leading, 82)
                        .opacity(0.3)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(.secondary.opacity(0.16))
                .frame(width: 1)
                .padding(.vertical, 12)
                .padding(.leading, 8)
                .accessibilityHidden(true)
        }
    }
}

private struct DownloadSectionHeader: View {
    @Environment(\.libriVoxAccent) private var accent
    var text: String

    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(accent)
            .padding(.top, 4)
    }
}

private struct DownloadStorageSummaryPill: View {
    var bytes: Int64

    var body: some View {
        Label("\(formatDownloadStorageBytes(bytes)) used", systemImage: "internaldrive")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .liquidGlassPanel(cornerRadius: 18, interactive: false)
    }
}

private struct DownloadBookGroupSummary: View {
    @Environment(\.libriVoxAccent) private var accent
    var group: DownloadBookGroup

    var body: some View {
        HStack(spacing: 14) {
            cover

            VStack(alignment: .leading, spacing: 5) {
                Text(group.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if let author = group.author {
                    Text(author)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Text(summaryText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)

                if group.statusState == .queued || group.statusState == .downloading || group.statusState == .failed {
                    ProgressView(value: group.progress)
                        .tint(statusColor)
                        .accessibilityHidden(true)
                }
            }

            Spacer(minLength: 8)
            trailingStatus
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    @ViewBuilder
    private var cover: some View {
        ZStack(alignment: .bottomTrailing) {
            if let book = group.book {
                BookCoverView(
                    book: book,
                    width: 62,
                    height: 82,
                    cornerRadius: 12,
                    preferFullImage: true,
                    showsDownloadBadge: false
                )
            } else {
                DownloadFallbackCover(title: group.title)
            }

            if let badgeState = completionBadgeState {
                DownloadGroupCompletionBadge(state: badgeState, size: 16)
                    .padding(3)
            }
        }
    }

    @ViewBuilder
    private var trailingStatus: some View {
        switch group.statusState {
        case .downloaded:
            Image(systemName: group.isFullyDownloaded ? "checkmark.circle.fill" : "checkmark.circle")
                .font(.title3)
                .foregroundStyle(accent)
        case .failed:
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title3)
                .foregroundStyle(.orange)
        case .queued, .downloading:
            Text("\(Int((group.progress * 100).rounded()))%")
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(.secondary)
        case .notDownloaded:
            EmptyView()
        }
    }

    private var completionBadgeState: DownloadGroupCompletionBadge.BadgeState? {
        if group.isFullyDownloaded { return .downloaded }
        if group.isPartiallyDownloaded { return .partial }
        if group.hasFailedItems { return .failed }
        if group.isActive { return .active }
        return nil
    }

    private var summaryText: String {
        var parts: [String] = []
        switch group.statusState {
        case .downloaded:
            parts.append(group.isFullyDownloaded ? "Downloaded" : "Partially downloaded")
        case .failed:
            parts.append("Needs attention")
        case .queued:
            parts.append("Queued")
        case .downloading:
            parts.append("Downloading")
        case .notDownloaded:
            parts.append("Not downloaded")
        }

        if group.totalChapterCount > 0 {
            parts.append("\(group.downloadedChapterCount) of \(group.totalChapterCount) chapters")
        }

        if let storageText {
            parts.append(storageText)
        }

        if let source = group.sourceLabel {
            parts.append(source)
        }

        return parts.joined(separator: " • ")
    }

    private var statusColor: Color {
        switch group.statusState {
        case .failed:
            .orange
        default:
            accent
        }
    }

    private var storageText: String? {
        if group.effectiveStorageBytes > 0 {
            return formatDownloadStorageBytes(group.effectiveStorageBytes)
        }
        if group.hasDownloadedContent {
            return "0\u{00A0}KB"
        }
        return nil
    }

    private var accessibilityLabel: String {
        "\(group.title), \(summaryText)"
    }
}

private struct DownloadChapterStatusRow: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    var group: DownloadBookGroup
    var status: DownloadChapterStatus

    var body: some View {
        HStack(spacing: 10) {
            statusIcon
                .frame(width: 22, height: 24)

            chapterCover

            VStack(alignment: .leading, spacing: 5) {
                Text(status.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if let subtitle = status.subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                if let detailText {
                    Text(detailText)
                        .font(.caption)
                        .foregroundStyle(statusColor)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)
            trailingControl
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .contextMenu {
            chapterContextMenu
        }
        .accessibilityHint("Touch and hold for chapter download actions")
    }

    @ViewBuilder
    private var chapterCover: some View {
        if let book = group.book {
            BookCoverView(
                book: book,
                width: 34,
                height: 46,
                cornerRadius: 7,
                preferFullImage: true,
                showsDownloadBadge: false
            )
        } else {
            DownloadFallbackCover(title: group.title, width: 34, height: 46, cornerRadius: 7)
        }
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch status.state {
        case .downloaded:
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(accent)
        case .failed:
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
        case .downloading:
            ProgressView()
                .controlSize(.small)
                .tint(accent)
        case .queued:
            Image(systemName: "clock")
                .foregroundStyle(.secondary)
        case .notDownloaded:
            Image(systemName: "arrow.down.circle")
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var trailingControl: some View {
        switch status.state {
        case .queued, .downloading:
            Text("\(Int((status.progress * 100).rounded()))%")
                .font(.caption2.monospacedDigit().weight(.semibold))
                .foregroundStyle(.secondary)
        case .failed, .notDownloaded:
            if let book = group.book, let chapter = status.chapter {
                Button {
                    Task {
                        await app.enqueueDownload(book, chapter: chapter)
                    }
                } label: {
                    Image(systemName: status.state == .failed ? "arrow.clockwise.circle" : "arrow.down.circle")
                        .font(.title3)
                }
                .buttonStyle(.plain)
                .foregroundStyle(accent)
                .accessibilityLabel(status.state == .failed ? "Retry chapter download" : "Download chapter")
            }
        case .downloaded:
            EmptyView()
        }
    }

    @ViewBuilder
    private var chapterContextMenu: some View {
        if let book = group.book, let chapter = status.chapter {
            switch status.state {
            case .notDownloaded:
                Button {
                    Task {
                        await app.enqueueDownload(book, chapter: chapter)
                    }
                } label: {
                    Label("Download Chapter", systemImage: "arrow.down.circle")
                }
            case .queued, .downloading:
                Button(role: .destructive) {
                    Task {
                        await app.deleteDownload(book: book, chapter: chapter)
                    }
                } label: {
                    Label("Cancel Download", systemImage: "xmark.circle")
                }
            case .downloaded, .failed:
                Button(role: .destructive) {
                    Task {
                        await app.deleteDownload(book: book, chapter: chapter)
                    }
                } label: {
                    Label("Remove Download", systemImage: "trash")
                }
            }
        }
    }

    private var statusText: String? {
        switch status.state {
        case .downloaded:
            "Downloaded"
        case .failed:
            "Download failed"
        case .downloading:
            status.progress <= 0 ? "Starting download" : "Downloading"
        case .queued:
            "Pending"
        case .notDownloaded:
            nil
        }
    }

    private var detailText: String? {
        var parts = statusText.map { [$0] } ?? []
        if status.storageBytes > 0 {
            parts.append(formatDownloadStorageBytes(status.storageBytes))
        }
        let text = parts.joined(separator: " • ")
        return text.isEmpty ? nil : text
    }

    private var accessibilityLabel: String {
        if let detailText {
            return "\(status.title), \(detailText)"
        }
        return status.title
    }

    private var statusColor: Color {
        switch status.state {
        case .failed:
            .orange
        case .notDownloaded, .queued:
            .secondary
        default:
            accent
        }
    }
}

private struct DownloadGroupCompletionBadge: View {
    enum BadgeState {
        case downloaded
        case partial
        case active
        case failed
    }

    @Environment(\.libriVoxAccent) private var accent
    var state: BadgeState
    var size: CGFloat

    var body: some View {
        Image(systemName: systemImage)
            .font(.system(size: size * 0.64, weight: .bold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .background(background, in: Circle())
            .overlay {
                Circle()
                    .stroke(.white.opacity(0.72), lineWidth: 1)
            }
            .shadow(color: .black.opacity(0.18), radius: 3, y: 1)
            .accessibilityLabel(accessibilityLabel)
    }

    private var systemImage: String {
        switch state {
        case .downloaded:
            "checkmark"
        case .partial:
            "checkmark.circle"
        case .active:
            "arrow.down.circle"
        case .failed:
            "exclamationmark.triangle.fill"
        }
    }

    private var foreground: Color {
        switch state {
        case .failed:
            .orange
        case .downloaded:
            .white
        case .partial, .active:
            accent
        }
    }

    private var background: Color {
        switch state {
        case .downloaded:
            accent
        case .failed:
            .white.opacity(0.9)
        case .partial, .active:
            .white.opacity(0.92)
        }
    }

    private var accessibilityLabel: String {
        switch state {
        case .downloaded:
            "Book fully downloaded"
        case .partial:
            "Book partially downloaded"
        case .active:
            "Book downloading"
        case .failed:
            "Book download failed"
        }
    }
}

private struct DownloadFallbackCover: View {
    var title: String
    var width: CGFloat = 62
    var height: CGFloat = 82
    var cornerRadius: CGFloat = 12

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(
                LinearGradient(
                    colors: [
                        Color.primary.opacity(0.10),
                        Color.secondary.opacity(0.08),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .frame(width: width, height: height)
            .overlay(alignment: .bottomLeading) {
                Text(title)
                    .font(width < 48 ? .system(size: 6, weight: .bold) : .caption2.bold())
                    .lineLimit(width < 48 ? 2 : 3)
                    .foregroundStyle(.primary)
                    .padding(width < 48 ? 4 : 7)
            }
    }
}

private func formatDownloadStorageBytes(_ bytes: Int64) -> String {
    guard bytes > 0 else { return "Size unavailable" }
    let formatter = ByteCountFormatter()
    formatter.allowedUnits = [.useKB, .useMB, .useGB]
    formatter.countStyle = .file
    formatter.includesUnit = true
    formatter.isAdaptive = true
    return formatter.string(fromByteCount: bytes).replacingOccurrences(of: " ", with: "\u{00A0}")
}

enum DownloadProgressStatusStyle {
    case panel
    case inline
}

struct DownloadProgressStatusView: View {
    @Environment(\.libriVoxAccent) private var accent
    var item: DownloadItem
    var style: DownloadProgressStatusStyle = .inline

    var body: some View {
        switch style {
        case .panel:
            content
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .liquidGlassPanel(cornerRadius: 18, interactive: false)
        case .inline:
            content
        }
    }

    private var content: some View {
        HStack(spacing: 10) {
            statusIcon
                .frame(width: 22, height: 22)

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(statusTitle)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(statusColor)
                        .lineLimit(1)
                    Spacer(minLength: 0)
                    if shouldShowPercentage {
                        Text("\(progressPercent)%")
                            .font(.caption2.monospacedDigit().weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }

                if shouldShowBar {
                    ProgressView(value: item.clampedProgress)
                        .tint(statusColor)
                        .accessibilityHidden(true)
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch item.state {
        case .queued:
            Image(systemName: "clock")
                .foregroundStyle(.secondary)
        case .downloading:
            ProgressView()
                .controlSize(.small)
                .tint(accent)
        case .downloaded:
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(accent)
        case .failed:
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
        case .notDownloaded:
            Image(systemName: "arrow.down.circle")
                .foregroundStyle(.secondary)
        }
    }

    private var statusTitle: String {
        switch item.state {
        case .queued:
            return item.chapterID == nil ? "Book queued" : "Chapter queued"
        case .downloading:
            if item.clampedProgress <= 0 {
                return "Starting download"
            }
            return item.chapterID == nil ? "Downloading book" : "Downloading chapter"
        case .downloaded:
            return "Downloaded"
        case .failed:
            return "Download failed"
        case .notDownloaded:
            return "Not downloaded"
        }
    }

    private var statusColor: Color {
        switch item.state {
        case .failed:
            return .orange
        case .notDownloaded:
            return .secondary
        default:
            return accent
        }
    }

    private var shouldShowBar: Bool {
        switch item.state {
        case .queued, .downloading, .failed:
            return true
        case .downloaded, .notDownloaded:
            return false
        }
    }

    private var shouldShowPercentage: Bool {
        switch item.state {
        case .queued, .downloading, .failed, .downloaded:
            return true
        case .notDownloaded:
            return false
        }
    }

    private var progressPercent: Int {
        Int((item.clampedProgress * 100).rounded())
    }

    private var accessibilityLabel: String {
        if shouldShowPercentage {
            return "\(statusTitle), \(progressPercent)%"
        }
        return statusTitle
    }
}

extension DownloadItem {
    var isActiveDownload: Bool {
        state == .queued || state == .downloading
    }

    var shouldShowInlineProgressStatus: Bool {
        isActiveDownload || state == .failed
    }

    var clampedProgress: Double {
        min(max(progress, 0), 1)
    }
}
