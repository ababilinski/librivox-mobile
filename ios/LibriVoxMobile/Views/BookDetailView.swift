import Foundation
import LibriVoxCore
import SwiftUI

struct BookDetailView: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    var isActive = true
    var pageState: BookDetailPageState? = nil
    var browserTopInset: CGFloat? = nil
    var onScrollRegionChange: ((BookDetailScrollRegion) -> Void)? = nil
    @State private var standaloneState = BookDetailPageState()

    private var state: BookDetailPageState { pageState ?? standaloneState }
    private var hydratedVersion: AudioBook {
        state.hydratedBook.flatMap { $0.id == book.id ? $0 : nil } ?? book
    }

    var body: some View {
        // Resolve and normalize once when catalog/library metadata changes. Playback
        // ticks belong to the transport control, not every section and chapter row.
        BookDetailContent(
            displayedBook: app.latestKnownVersion(of: hydratedVersion),
            isLoadingBookDetails: state.isLoading,
            isActive: isActive,
            pageState: state,
            browserTopInset: browserTopInset,
            onScrollRegionChange: onScrollRegionChange
        )
        .task(id: "\(book.id)|\(isActive)") {
            guard isActive else {
                state.isLoading = false
                return
            }
            guard state.hydratedBook?.id != book.id else { return }
            state.isLoading = true
            let detail = await app.hydrateBookDetails(book)
            guard !Task.isCancelled else { return }
            state.hydratedBook = detail
            state.isLoading = false
        }
    }
}

private struct BookDetailContent: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    @Environment(\.playerTransitionContext) private var playerTransition
    @Environment(\.colorScheme) private var colorScheme
    var displayedBook: AudioBook
    var isLoadingBookDetails: Bool
    var isActive: Bool
    var pageState: BookDetailPageState
    var browserTopInset: CGFloat?
    var onScrollRegionChange: ((BookDetailScrollRegion) -> Void)?
    @State private var loadedRelatedAuthorTaskID: String?
    @State private var expandedDescription = false
    @State private var readerBook: AudioBook?
    @State private var relatedAuthorBooks: [AudioBook] = []
    @State private var isLoadingRelatedAuthorBooks = false
    @State private var pendingWholeBookDownloadAction: WholeBookDownloadAction?
    @State private var actionMenuBook: AudioBook?
    @State private var actionMenuAnchor: CGPoint?
    @State private var contextMenuDetailBook: AudioBook?
    @State private var contextMenuDetailCollection: BookDetailCollection?

    private var isInLibrary: Bool {
        app.isInLibrary(displayedBook)
    }

    private var sourceURL: URL? {
        [
            displayedBook.libriVoxURL,
            displayedBook.lit2GoURL,
            displayedBook.wolneLekturyURL,
            displayedBook.gutenbergURL,
        ]
        .compactMap { $0 }
        .compactMap(URL.init(string:))
        .first
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(spacing: 20) {
                        heroHeader(coverSize: min(320, max(180, geometry.size.width - 96)))
                        metadataPills
                        actionRow
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, (browserTopInset ?? geometry.safeAreaInsets.top) + 24)
                    .padding(.bottom, 40)
                    .frame(maxWidth: .infinity)
                    .background {
                        ArtworkGradient(book: displayedBook, style: .hero, enabled: app.settings.bookDetailUseArtworkColorScheme)
                    }
                    if let downloadItem = app.download(for: displayedBook),
                       downloadItem.shouldShowInlineProgressStatus {
                        downloadStatusPanel(downloadItem)
                            .padding(.horizontal)
                    }
                    if !displayedBook.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        descriptionBlock
                            .padding(.horizontal)
                    }
                    sourceLinks
                        .padding(.horizontal)
                    bookInformation
                        .padding(.horizontal)
                    if shouldShowRelatedAuthorBooksSection {
                        relatedAuthorBooksSection
                    }
                    chaptersBlock
                }
                .padding(.bottom, 140)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            // Let the hero fill the navigation safe area behind the system's floating controls.
            .ignoresSafeArea(.container, edges: browserTopInset == nil ? .top : [])
            .scrollEdgeEffectStyle(.soft, for: .top)
            .modifier(BookDetailScrollTracking(
                state: pageState,
                isActive: isActive,
                onRegionChange: onScrollRegionChange
            ))
        }
        .background {
            if app.settings.bookDetailUseCoverBackdrop {
                GlassArtworkBackground(book: displayedBook)
            } else {
                LibriVoxBrand.surface(for: colorScheme)
                    .ignoresSafeArea()
            }
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar(.visible, for: .navigationBar)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if isActive {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if let sourceURL {
                        ShareLink(item: sourceURL) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel("Share")
                    }

                    BookActionsMenuButton(
                        book: displayedBook,
                        context: .detail,
                        onRead: displayedBook.canReadSourceText ? {
                            readerBook = displayedBook
                        } : nil
                    ) {
                        Image(systemName: "ellipsis")
                    }
                    .accessibilityLabel("More actions")
                }
            }
        }
        .overlay {
            if let actionMenuBook {
                AppleMusicBookActionsOverlay(
                    book: actionMenuBook,
                    context: .home,
                    anchorPoint: actionMenuAnchor,
                    onDismiss: dismissBookActions,
                    onShowDetails: {
                        contextMenuDetailCollection = relatedAuthorCollection
                        contextMenuDetailBook = actionMenuBook
                    },
                    onRead: actionMenuBook.canReadSourceText ? {
                        readerBook = actionMenuBook
                    } : nil
                )
            }
        }
        .navigationDestination(item: $contextMenuDetailBook) { book in
            BookDetailBrowser(book: book, collection: contextMenuDetailCollection)
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
        .task(id: "\(relatedAuthorBooksTaskID)|\(isActive)") {
            guard isActive, loadedRelatedAuthorTaskID != relatedAuthorBooksTaskID else { return }
            await loadRelatedAuthorBooks()
            if !Task.isCancelled { loadedRelatedAuthorTaskID = relatedAuthorBooksTaskID }
        }
        .confirmationDialog(
            wholeBookDownloadDialogTitle,
            isPresented: Binding(
                get: { pendingWholeBookDownloadAction != nil },
                set: { isPresented in
                    if !isPresented {
                        pendingWholeBookDownloadAction = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            if let action = pendingWholeBookDownloadAction {
                Button(action.buttonTitle, role: action.buttonRole) {
                    performWholeBookDownloadAction(action)
                    pendingWholeBookDownloadAction = nil
                }
            }
            Button("Cancel", role: .cancel) {
                pendingWholeBookDownloadAction = nil
            }
        } message: {
            Text(wholeBookDownloadDialogMessage)
        }
    }

    private var relatedAuthorBooksTaskID: String {
        [
            displayedBook.id,
            displayedBook.author,
            app.settings.enabledSources.map(\.rawValue).sorted().joined(separator: ","),
            app.settings.preferredLanguages.map(\.rawValue).sorted().joined(separator: ","),
            app.settings.sourcePriority.map(\.rawValue).joined(separator: ","),
        ].joined(separator: "|")
    }

    private var shouldShowRelatedAuthorBooksSection: Bool {
        !displayedBook.author.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func heroHeader(coverSize: CGFloat) -> some View {
        VStack(spacing: 16) {
            CoverArtworkButton(book: displayedBook, accessibilityIdentifier: "book-detail-cover") {
                BookCoverView(
                    book: displayedBook,
                    width: coverSize,
                    height: coverSize,
                    cornerRadius: 24,
                    preferFullImage: true,
                    displayModeOverride: .fit,
                    downloadBadgeAlignment: .bottomLeading
                )
            }
            .overlay(alignment: .bottomTrailing) {
                if displayedBook.canReadSourceText {
                    NavigationLink {
                        ReaderView(book: displayedBook)
                    } label: {
                        ReadBookCoverShortcut()
                    }
                    .buttonStyle(.plain)
                    .offset(x: 10, y: 8)
                }
            }
            .shadow(color: .black.opacity(0.26), radius: 28, x: 0, y: 18)

            VStack(spacing: 7) {
                Text(displayedBook.title)
                    .font(.system(.title, design: .serif, weight: .bold))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("book-detail-title")

                HStack(alignment: .center, spacing: 4) {
                    NavigationLink {
                        AuthorDetailView(seedBook: displayedBook)
                    } label: {
                        Text(displayedBook.author)
                            .font(.title3)
                            .foregroundStyle(.primary.opacity(0.86))
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("View \(displayedBook.author)")
                    .accessibilityHint("Opens the author profile")
                    .accessibilityIdentifier("book-detail-author-profile")

                    Button {
                        app.openDiscoverSearch(query: displayedBook.author, field: .author)
                    } label: {
                        Image(systemName: "magnifyingglass")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(accent)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Search for books by \(displayedBook.author)")
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding(.horizontal, 4)
        }
        .frame(maxWidth: .infinity)
    }

    private func downloadStatusPanel(_ item: DownloadItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                DownloadProgressStatusView(item: item, style: .inline)
                if item.isActiveDownload {
                    Button {
                        Task { await app.cancelDownload(bookID: displayedBook.id) }
                    } label: {
                        Label("Cancel", systemImage: "xmark.circle")
                            .font(.caption.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .controlSize(.small)
                    .accessibilityLabel("Cancel download")
                }
            }

            if let chapterDownloadSummary {
                Text(chapterDownloadSummary)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .liquidGlassPanel(cornerRadius: 18, interactive: false)
    }

    private var chapterDownloadSummary: String? {
        let downloadableChapters = displayedBook.chapters.filter { chapter in
            chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        }
        guard !downloadableChapters.isEmpty else { return nil }

        let chapterItems = downloadableChapters.compactMap { chapter in
            app.download(for: displayedBook, chapter: chapter)
        }
        guard !chapterItems.isEmpty else { return nil }

        let downloaded = chapterItems.filter { $0.state == .downloaded }.count
        let downloading = chapterItems.filter { $0.state == .downloading }.count
        let pending = chapterItems.filter { $0.state == .queued }.count
        let failed = chapterItems.filter { $0.state == .failed }.count
        var parts = ["\(downloaded) of \(downloadableChapters.count) chapters downloaded"]
        if downloading > 0 {
            parts.append("\(downloading) downloading")
        }
        if pending > 0 {
            parts.append("\(pending) pending")
        }
        if failed > 0 {
            parts.append("\(failed) failed")
        }
        return parts.joined(separator: " • ")
    }

    private var metadataPills: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                BookDetailMetadataPill(text: displayedBook.source.label, systemImage: "music.note.list")
                BookDetailMetadataPill(text: "\(displayedBook.chapters.count) chapters", systemImage: "list.bullet")
                if displayedBook.totalDurationSeconds > 0 {
                    BookDetailMetadataPill(text: formatDuration(seconds: displayedBook.totalDurationSeconds), systemImage: "clock")
                }
                if let genre = informationGenres.first {
                    BookDetailMetadataPill(text: genre, systemImage: "tag")
                }
                if let language = displayedBook.language {
                    BookDetailMetadataPill(text: language, systemImage: "globe")
                }
                if isInLibrary {
                    BookDetailMetadataPill(text: "In Library", systemImage: "checkmark.circle.fill")
                }
                if displayedBook.isFavorite {
                    BookDetailMetadataPill(text: "Liked", systemImage: "star.fill")
                }
            }
            .padding(.vertical, 2)
        }
    }

    private var actionRow: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                BookDetailPlaybackButton(book: displayedBook)

                if !downloadableChapters.isEmpty {
                    BookDetailIconButton(
                        systemImage: wholeBookDownloadSystemImage,
                        accessibilityLabel: wholeBookDownloadAccessibilityLabel,
                        isSelected: app.downloadBadgeState(for: displayedBook) == .downloaded
                    ) {
                        presentWholeBookDownloadAction()
                    }
                    .accessibilityIdentifier("book-download-button")
                }

                Spacer(minLength: 0)

                BookDetailIconButton(
                    systemImage: displayedBook.isFavorite ? "star.fill" : "star",
                    accessibilityLabel: displayedBook.isFavorite ? "Liked" : "Like",
                    isSelected: displayedBook.isFavorite
                ) {
                    Task { await app.toggleFavorite(displayedBook) }
                }
            }

            HStack {
                Spacer(minLength: 0)
                libraryMembershipControl
            }
        }
    }

    @ViewBuilder
    private var libraryMembershipControl: some View {
        if isInLibrary {
            Menu {
                Button("Remove from Library", systemImage: "trash", role: .destructive) {
                    Task { await app.removeFromLibrary(displayedBook) }
                }
            } label: {
                Label("In Library", systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.capsule)
            .controlSize(.regular)
            .accessibilityLabel("In Library. More library options")
            .accessibilityIdentifier("book-library-membership")
        } else {
            Button {
                Task { await app.saveToLibrary(displayedBook) }
            } label: {
                Label("Add to Library", systemImage: "plus")
                    .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.capsule)
            .controlSize(.regular)
            .accessibilityIdentifier("book-library-membership")
        }
    }

    private var downloadableChapters: [AudioBookChapter] {
        displayedBook.chapters.filter { chapter in
            chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
        }
    }

    private var wholeBookDownloadSystemImage: String {
        switch app.downloadBadgeState(for: displayedBook) {
        case .downloaded:
            "checkmark.circle.fill"
        case .queued, .downloading:
            "xmark.circle"
        case .failed:
            "arrow.clockwise.circle"
        case .notDownloaded, nil:
            "arrow.down.circle"
        }
    }

    private var wholeBookDownloadAccessibilityLabel: String {
        switch app.downloadBadgeState(for: displayedBook) {
        case .downloaded:
            "Downloaded. Delete downloaded book"
        case .queued, .downloading:
            "Downloading. Cancel book download"
        case .failed:
            "Retry book download"
        case .notDownloaded, nil:
            "Download book"
        }
    }

    private func presentWholeBookDownloadAction() {
        pendingWholeBookDownloadAction = switch app.downloadBadgeState(for: displayedBook) {
        case .downloaded:
            .delete
        case .queued, .downloading:
            .cancel
        case .failed, .notDownloaded, nil:
            .download
        }
    }

    private var wholeBookDownloadDialogTitle: String {
        guard let action = pendingWholeBookDownloadAction else { return "Download Book" }
        return switch action {
        case .download:
            "Download \(displayedBook.title)?"
        case .cancel:
            "Cancel Download?"
        case .delete:
            "\(displayedBook.title) is downloaded"
        }
    }

    private var wholeBookDownloadDialogMessage: String {
        guard let action = pendingWholeBookDownloadAction else { return "" }
        return switch action {
        case .download:
            "Download all \(downloadableChapters.count) chapters for offline listening?"
        case .cancel:
            "Stop downloading the remaining chapters of this book? Chapters already downloaded will stay available."
        case .delete:
            "Delete the entire downloaded book from this device? Your library and listening progress will be kept."
        }
    }

    private func performWholeBookDownloadAction(_ action: WholeBookDownloadAction) {
        Task {
            switch action {
            case .download:
                await app.enqueueDownload(displayedBook)
            case .cancel:
                await app.cancelDownload(bookID: displayedBook.id)
            case .delete:
                await app.deleteDownload(bookID: displayedBook.id)
            }
        }
    }

    private var descriptionBlock: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("About")
                .font(.headline)
            NativeSelectableText(
                text: displayedBook.description,
                textStyle: .body,
                color: .secondaryLabel,
                maximumNumberOfLines: expandedDescription ? 0 : 5,
                accessibilityIdentifier: "book-detail-description"
            )
            if shouldShowDescriptionToggle {
                Button {
                    withAnimation(.snappy(duration: 0.3)) {
                        expandedDescription.toggle()
                    }
                } label: {
                    Label(
                        expandedDescription ? "Show Less" : "Show More",
                        systemImage: expandedDescription ? "chevron.up" : "chevron.down"
                    )
                        .font(.subheadline.weight(.semibold))
                        .frame(minHeight: 32)
                }
                .buttonStyle(.plain)
                .foregroundStyle(accent)
                .contentShape(Rectangle())
                .accessibilityIdentifier("book-description-toggle")
                .accessibilityValue(expandedDescription ? "Expanded" : "Collapsed")
            } else if isLoadingBookDetails,
                      displayedBook.source == .wolneLektury,
                      displayedBook.description.trimmingCharacters(in: .whitespacesAndNewlines).hasSuffix("...") {
                HStack(spacing: 7) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Loading full description…")
                        .font(.footnote)
                }
                .foregroundStyle(.secondary)
                .accessibilityElement(children: .combine)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var shouldShowDescriptionToggle: Bool {
        displayedBook.description.count > 180
    }

    private var readerNames: [String] {
        uniqueInformationValues(displayedBook.narrators + displayedBook.chapters.compactMap(\.reader))
    }

    private var directorNames: [String] {
        uniqueInformationValues(displayedBook.chapters.compactMap(\.director))
    }

    private var informationGenres: [String] {
        uniqueInformationValues(displayedBook.genres + displayedBook.literaryGenres.map(\.name))
    }

    private var bookInformationItems: [BookInformationItem] {
        var items = [
            BookInformationItem(label: "Author", value: displayedBook.author, searchField: .author),
        ]

        if !readerNames.isEmpty {
            items.append(
                BookInformationItem(
                    label: "Read by",
                    value: readerNames.joined(separator: ", "),
                    searchField: .reader
                )
            )
        }
        if !displayedBook.translators.isEmpty {
            items.append(
                BookInformationItem(
                    label: "Translated by",
                    value: uniqueInformationValues(displayedBook.translators).joined(separator: ", ")
                )
            )
        }
        if !directorNames.isEmpty {
            items.append(BookInformationItem(label: "Production", value: directorNames.joined(separator: ", ")))
        }

        items.append(BookInformationItem(label: "Chapters", value: "\(displayedBook.chapters.count)"))
        if displayedBook.totalDurationSeconds > 0 {
            items.append(
                BookInformationItem(
                    label: "Duration",
                    value: formatDuration(seconds: displayedBook.totalDurationSeconds)
                )
            )
        }
        if let language = displayedBook.language?.trimmingCharacters(in: .whitespacesAndNewlines),
           !language.isEmpty {
            items.append(BookInformationItem(label: "Language", value: language))
        }
        if let originalLanguage = displayedBook.originalLanguage?.trimmingCharacters(in: .whitespacesAndNewlines),
           !originalLanguage.isEmpty,
           originalLanguage.localizedCaseInsensitiveCompare(displayedBook.language ?? "") != .orderedSame {
            items.append(BookInformationItem(label: "Original language", value: originalLanguage))
        }
        if let publisher = displayedBook.publisher?.trimmingCharacters(in: .whitespacesAndNewlines),
           !publisher.isEmpty {
            items.append(BookInformationItem(label: "Publisher", value: publisher))
        }
        if let releaseDate = displayedBook.releaseDate?.trimmingCharacters(in: .whitespacesAndNewlines),
           !releaseDate.isEmpty {
            items.append(BookInformationItem(label: "Published", value: releaseDate))
        }
        if !informationGenres.isEmpty {
            items.append(BookInformationItem(label: "Genres", value: informationGenres.joined(separator: ", ")))
        }
        items.append(BookInformationItem(label: "Source", value: displayedBook.source.label))
        return items
    }

    private var bookInformation: some View {
        let items = bookInformationItems
        return VStack(alignment: .leading, spacing: 0) {
            Text("Book Information")
                .font(.headline)
                .padding(.bottom, 8)

            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                BookInformationRow(
                    label: item.label,
                    value: item.value,
                    showsDivider: index < items.count - 1,
                    action: item.searchField.map { field in
                        { app.openDiscoverSearch(query: item.value, field: field) }
                    }
                )
            }
        }
        .padding(16)
        .liquidGlassPanel(cornerRadius: 22, interactive: false)
    }

    private func uniqueInformationValues(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.compactMap { value in
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return nil }
            let key = trimmed.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            return seen.insert(key).inserted ? trimmed : nil
        }
    }

    private var sourceLinks: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) { sourceLinkActions }
            VStack(alignment: .leading, spacing: 12) { sourceLinkActions }
        }
    }

    private var sourceLinkActions: some View {
        Group {
            if let sourceURL {
                Link(destination: sourceURL) {
                    Label(sourceLinkTitle, systemImage: "arrow.up.right.square")
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(accent)
                .underline(true, color: accent.opacity(0.55))
            }

            NavigationLink {
                ReaderView(book: displayedBook)
            } label: {
                Label("Read", systemImage: "text.book.closed")
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(accent)
            .underline(true, color: accent.opacity(0.55))
            .accessibilityIdentifier("book-detail-read")

            if let donationURL = displayedBook.source.donationURL {
                Link(destination: donationURL) {
                    Label("Donate", systemImage: "heart")
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(accent)
                .underline(true, color: accent.opacity(0.55))
                .accessibilityLabel("Donate to \(displayedBook.source.label)")
                .accessibilityIdentifier("book-detail-donate")
            }
        }
    }

    private var enabledRelatedAuthorBooks: [AudioBook] {
        relatedAuthorBooks.filter(app.isEnabledRecommendation)
    }

    private var relatedAuthorBooksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("More books by \(displayedBook.author)")
                    .font(.title3.weight(.semibold))
                    .lineLimit(2)
                    .textSelection(.enabled)
                Spacer(minLength: 0)
                if isLoadingRelatedAuthorBooks {
                    ProgressView()
                        .controlSize(.small)
                        .tint(accent)
                        .accessibilityLabel("Loading more books by this author")
                }
            }
            .padding(.horizontal)

            if enabledRelatedAuthorBooks.isEmpty, isLoadingRelatedAuthorBooks {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                        .tint(accent)
                    Text("Finding more books...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Finding more books by this author")
            } else if enabledRelatedAuthorBooks.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("No other books found yet.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button {
                        app.openDiscoverSearch(query: displayedBook.author, field: .author)
                    } label: {
                        Label("Search this author", systemImage: "magnifyingglass")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(accent)
                    .accessibilityHint("Shows all available search results for this author")
                }
                .padding(.horizontal)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: 14) {
                        ForEach(enabledRelatedAuthorBooks) { relatedBook in
                            AnchoredBookNavigationLink(
                                book: relatedBook,
                                onActions: showBookActions(for:anchor:),
                                destination: {
                                    BookDetailBrowser(book: relatedBook, collection: relatedAuthorCollection)
                                },
                                label: {
                                BookDetailRelatedBookCard(book: relatedBook)
                                }
                            )
                            .accessibilityIdentifier("more-by-author-\(relatedBook.id)")
                        }
                    }
                    .padding(.vertical, 2)
                }
                .contentMargins(.horizontal, 16, for: .scrollContent)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var chaptersBlock: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text("Chapters")
                    .font(.title3.weight(.semibold))
                Spacer()
                Text("\(displayedBook.chapters.count)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal)

            LazyVStack(spacing: 0) {
                ForEach(Array(displayedBook.chapters.enumerated()), id: \.element.id) { index, chapter in
                    BookDetailChapterRow(
                        bookID: displayedBook.id,
                        chapter: chapter,
                        author: displayedBook.author,
                        downloadItem: app.download(for: displayedBook, chapter: chapter),
                        onDownload: {
                            Task { await app.enqueueDownload(displayedBook, chapter: chapter) }
                        },
                        onCancelDownload: {
                            Task { await app.deleteDownload(book: displayedBook, chapter: chapter) }
                        },
                        onRemoveDownload: {
                            Task { await app.deleteDownload(book: displayedBook, chapter: chapter) }
                        },
                        onAddToQueue: {
                            app.enqueue(displayedBook, chapter: chapter)
                        }
                    ) {
                        playerTransition.selectSource("chapter:\(displayedBook.id):\(chapter.id)")
                        if app.playback.currentBook?.id == displayedBook.id,
                           app.playback.currentChapter?.id == chapter.id {
                            app.showingNowPlaying = true
                        } else {
                            app.play(displayedBook, chapter: chapter)
                        }
                    }
                    .nowPlayingTransitionSource("chapter:\(displayedBook.id):\(chapter.id)")
                    .padding(.horizontal)

                    if index < displayedBook.chapters.count - 1 {
                        Divider()
                            .padding(.leading, 76)
                            .padding(.horizontal)
                            .opacity(0.34)
                    }
                }
            }
        }
    }

    private var sourceLinkTitle: String {
        switch displayedBook.source {
        case .libriVox:
            "Open LibriVox"
        case .lit2Go:
            "Open Lit2Go"
        case .wolneLektury:
            "Open Wolne Lektury"
        case .gutendex:
            "Open Project Gutenberg"
        case .localAsset, .customLocal:
            "Open Source"
        }
    }

    private var relatedAuthorCollection: BookDetailCollection {
        BookDetailCollection(title: "More by \(displayedBook.author)", books: enabledRelatedAuthorBooks)
    }

    private func loadRelatedAuthorBooks() async {
        let requestID = relatedAuthorBooksTaskID
        let book = displayedBook
        let trimmedAuthor = displayedBook.author.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedAuthor.isEmpty else {
            relatedAuthorBooks = []
            isLoadingRelatedAuthorBooks = false
            return
        }

        let cachedBooks = app.cachedRelatedBooksByAuthor(for: book)
        if relatedAuthorBooks != cachedBooks {
            relatedAuthorBooks = cachedBooks
        }
        isLoadingRelatedAuthorBooks = true
        let books = await app.relatedBooksByAuthor(for: book)
        guard !Task.isCancelled, relatedAuthorBooksTaskID == requestID else { return }

        relatedAuthorBooks = books
        isLoadingRelatedAuthorBooks = false
    }

    private func showBookActions(for book: AudioBook, anchor: CGPoint?) {
        withAnimation(.snappy(duration: 0.2)) {
            actionMenuBook = book
            actionMenuAnchor = anchor
        }
    }

    private func dismissBookActions() {
        withAnimation(.snappy(duration: 0.18)) {
            actionMenuBook = nil
            actionMenuAnchor = nil
        }
    }
}

private struct BookDetailPlaybackButton: View {
    @Environment(AppModel.self) private var app
    @Environment(\.playerTransitionContext) private var playerTransition
    @Environment(\.colorScheme) private var colorScheme
    var book: AudioBook

    var body: some View {
        let progress = app.progress(for: book)
        let isCurrent = app.playback.currentBook?.id == book.id
        let isPlaying = isCurrent && app.playback.isPlaying
        let foreground = LibriVoxBrand.surface(for: colorScheme)
        let title = isPlaying ? "Pause" : (isCurrent || progress?.hasStarted == true ? "Resume" : "Play")
        Button {
            if isCurrent {
                app.playback.toggle()
            } else {
                playerTransition.selectSource("book-play:\(book.id)")
                app.play(book)
            }
        } label: {
            VStack(spacing: 5) {
                Label(title, systemImage: isPlaying ? "pause.fill" : "play.fill")
                    .font(.headline)
                if let progress, progress.hasStarted {
                    ProgressView(value: min(max(progress.fractionCompleted, 0), 1))
                        .progressViewStyle(.linear)
                        .tint(foreground.opacity(0.78))
                        .frame(width: 106)
                }
            }
            .frame(minWidth: 112)
        }
        .buttonStyle(.glassProminent)
        .buttonBorderShape(.capsule)
        .controlSize(.regular)
        .foregroundStyle(foreground)
        .disabled(book.chapters.isEmpty)
        .accessibilityLabel(title)
        .accessibilityValue(progress.map { $0.fractionCompleted.formatted(.percent.precision(.fractionLength(0))) } ?? "")
        .accessibilityIdentifier("book-detail-play")
        .nowPlayingTransitionSource("book-play:\(book.id)")
    }
}

private struct BookInformationItem {
    var label: String
    var value: String
    var searchField: CatalogSearchField?

    init(label: String, value: String, searchField: CatalogSearchField? = nil) {
        self.label = label
        self.value = value
        self.searchField = searchField
    }
}

private struct BookInformationRow: View {
    @Environment(\.libriVoxAccent) private var accent
    var label: String
    var value: String
    var showsDivider = true
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                Text(label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .frame(width: 104, alignment: .leading)
                    .textSelection(.enabled)

                HStack(alignment: .top, spacing: 4) {
                    NativeSelectableText(
                        text: value,
                        textStyle: .subheadline,
                        color: .label,
                        maximumNumberOfLines: 0,
                        accessibilityIdentifier: "book-information-\(label)"
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if let action {
                        Button(action: action) {
                            Image(systemName: "magnifyingglass")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(accent)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Search \(label.lowercased()): \(value)")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 10)

            if showsDivider {
                Divider()
                    .padding(.leading, 118)
                    .opacity(0.34)
            }
        }
    }
}

private enum WholeBookDownloadAction {
    case download
    case cancel
    case delete

    var buttonTitle: String {
        switch self {
        case .download: "Download All Chapters"
        case .cancel: "Stop Downloading"
        case .delete: "Delete Downloaded Book"
        }
    }

    var buttonRole: ButtonRole? {
        switch self {
        case .download: nil
        case .cancel, .delete: .destructive
        }
    }
}

private struct BookDetailMetadataPill: View {
    var text: String
    var systemImage: String

    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.caption.weight(.medium))
            .lineLimit(1)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(.thinMaterial, in: Capsule())
            .overlay {
                Capsule()
                    .stroke(.white.opacity(0.22), lineWidth: 0.6)
            }
    }
}

private struct BookDetailIconButton: View {
    @Environment(\.libriVoxAccent) private var accent
    var systemImage: String
    var accessibilityLabel: String
    var isSelected = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            GlassCircleIcon(
                systemImage: systemImage,
                size: 42,
                symbolSize: 18,
                isSelected: isSelected,
                selectedTint: accent
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }
}

private struct BookDetailRelatedBookCard: View {
    var book: AudioBook

    private let coverWidth: CGFloat = 118
    private let coverHeight: CGFloat = 158

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            BookCoverView(
                book: book,
                width: coverWidth,
                height: coverHeight,
                cornerRadius: 16,
                preferFullImage: true
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(book.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2, reservesSpace: true)
                Text(book.source.label)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1, reservesSpace: true)
                BookShelfProgressView(book: book)
            }
            .frame(width: coverWidth, alignment: .leading)
        }
        .frame(width: coverWidth, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(book.title), \(book.source.label)")
    }
}

private struct BookDetailChapterRow: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    var bookID: String
    var chapter: AudioBookChapter
    var author: String
    var downloadItem: DownloadItem?
    var onDownload: () -> Void
    var onCancelDownload: () -> Void
    var onRemoveDownload: () -> Void
    var onAddToQueue: () -> Void
    var onPlay: () -> Void

    private var isCurrent: Bool {
        app.playback.currentBook?.id == bookID && app.playback.currentChapter?.id == chapter.id
    }

    private var isPlaying: Bool {
        isCurrent && app.playback.isPlaying
    }

    private var playbackState: String {
        isCurrent ? (isPlaying ? "Playing" : "Paused") : "Not playing"
    }

    private func togglePlayback() {
        if isCurrent {
            // Preserve the current player item and position when pausing or resuming.
            app.playback.toggle()
        } else {
            onPlay()
        }
    }

    private var isDownloadableChapter: Bool {
        chapter.listenURL?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
    }

    var body: some View {
        HStack(spacing: 14) {
            Button(action: togglePlayback) {
                ZStack {
                    Circle()
                        .fill(isCurrent ? accent.opacity(0.22) : Color.primary.opacity(0.08))
                        .frame(width: 42, height: 42)
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(isCurrent ? accent : .primary)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(isPlaying ? "Pause" : "Play") \(chapter.displayTitle)")
            .accessibilityValue(playbackState)
            .accessibilityIdentifier("book-detail-chapter-play-pause-\(chapter.id)")

            Button(action: onPlay) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(chapter.displayTitle)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    HStack(spacing: 6) {
                        Text(author)
                            .lineLimit(1)
                        if chapter.durationSeconds > 0 {
                            Text("·")
                            Text(formatDuration(seconds: chapter.durationSeconds))
                                .font(.caption.monospacedDigit())
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)

                    if let reader = chapter.reader, !reader.isEmpty {
                        Text("Read by \(reader)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    if let chapterDownloadStatusText {
                        Text(chapterDownloadStatusText)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(chapterDownloadStatusColor)
                            .lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(playbackState)
            .accessibilityHint("Opens Now Playing")
            .accessibilityIdentifier("book-detail-chapter-\(chapter.id)")

            chapterDownloadControl
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .contextMenu {
            chapterContextMenu
        }
        .accessibilityHint("Touch and hold for chapter actions")
    }

    @ViewBuilder
    private var chapterContextMenu: some View {
        Button(action: togglePlayback) {
            Label(isPlaying ? "Pause Chapter" : "Play Chapter", systemImage: isPlaying ? "pause.fill" : "play.fill")
        }

        Button(action: onAddToQueue) {
            Label("Add to Queue", systemImage: "text.line.last.and.arrowtriangle.forward")
        }

        downloadContextMenuAction
    }

    @ViewBuilder
    private var downloadContextMenuAction: some View {
        if let downloadItem {
            switch downloadItem.state {
            case .downloaded:
                Button(role: .destructive, action: onRemoveDownload) {
                    Label("Remove Download", systemImage: "trash")
                }
            case .queued, .downloading:
                Button(role: .destructive, action: onCancelDownload) {
                    Label("Cancel Download", systemImage: "xmark.circle")
                }
            case .failed:
                Button(action: onDownload) {
                    Label("Retry Download", systemImage: "arrow.clockwise")
                }
            case .notDownloaded:
                if isDownloadableChapter {
                    Button(action: onDownload) {
                        Label("Download Chapter", systemImage: "arrow.down.circle")
                    }
                }
            }
        } else if isDownloadableChapter {
            Button(action: onDownload) {
                Label("Download Chapter", systemImage: "arrow.down.circle")
            }
        }
    }

    @ViewBuilder
    private var chapterDownloadControl: some View {
        if let downloadItem {
            switch downloadItem.state {
            case .downloaded:
                Image(systemName: "checkmark.circle.fill")
                    .font(.title3)
                    .foregroundStyle(accent)
                    .frame(width: 34, height: 34)
                    .accessibilityLabel("Downloaded")
            case .queued:
                Image(systemName: "clock")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .frame(width: 34, height: 34)
                    .accessibilityLabel("Pending download")
            case .downloading:
                Button(action: onCancelDownload) {
                    ZStack {
                        ProgressView(value: downloadItem.clampedProgress)
                            .tint(accent)
                        Image(systemName: "xmark")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(accent)
                    }
                    .frame(width: 34, height: 34)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Cancel download")
            case .failed:
                Button(action: onDownload) {
                    Label("Retry", systemImage: "arrow.clockwise")
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(.orange.opacity(0.12), in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Retry download")
            case .notDownloaded:
                downloadButton
            }
        } else if isDownloadableChapter {
            downloadButton
        }
    }

    private var downloadButton: some View {
        Button(action: onDownload) {
            Label("Download", systemImage: "arrow.down.circle.fill")
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .foregroundStyle(accent)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(accent.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Download chapter")
    }

    private var chapterDownloadStatusText: String? {
        guard let downloadItem else {
            return nil
        }
        switch downloadItem.state {
        case .downloaded:
            return "Downloaded"
        case .queued:
            return "Pending"
        case .downloading:
            let percent = Int((downloadItem.clampedProgress * 100).rounded())
            return percent > 0 ? "Downloading \(percent)%" : "Downloading"
        case .failed:
            return "Download failed"
        case .notDownloaded:
            return nil
        }
    }

    private var chapterDownloadStatusColor: Color {
        guard let downloadItem else { return .secondary }
        switch downloadItem.state {
        case .downloaded, .downloading:
            return accent
        case .queued, .notDownloaded:
            return .secondary
        case .failed:
            return .orange
        }
    }
}
