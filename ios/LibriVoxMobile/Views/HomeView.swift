import ImageIO
import LibriVoxCore
import SwiftUI
import UIKit

private let homeContentMargin: CGFloat = 16

private struct HomeBookDetailSelection: Identifiable {
    let id = UUID()
    let book: AudioBook
    let collection: BookDetailCollection
}

struct HomeView: View {
    @Environment(AppModel.self) private var app
    @State private var detailSelection: HomeBookDetailSelection?
    @State private var readerBook: AudioBook?

    private var usesRecentlyAddedCatalogTitle: Bool {
        !app.settings.enabledSources.isEmpty &&
            app.settings.enabledSources.isSubset(of: [.libriVox, .wolneLektury])
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    ContinueListeningSection()

                    if !app.isOnline || app.settings.downloadsOnlyModeEnabled {
                        Label(
                            app.settings.downloadsOnlyModeEnabled ? "Downloads only mode is on" : "You are offline",
                            systemImage: app.settings.downloadsOnlyModeEnabled ? "arrow.down.circle" : "wifi.slash"
                        )
                        .foregroundStyle(.secondary)
                        .padding()
                        .liquidGlassPanel(cornerRadius: 18, interactive: false)
                        .padding(.horizontal, homeContentMargin)
                    }

                    if app.libraryBooks.isEmpty {
                        WelcomeBanner()
                            .padding(.horizontal, homeContentMargin)
                    } else {
                        BookShelfView(
                            title: "Your Library",
                            books: Array(app.libraryBooks.prefix(12)),
                            coverWidth: 132,
                            coverHeight: 180,
                            onShowDetails: { book, collection in
                                detailSelection = HomeBookDetailSelection(book: book, collection: collection)
                            },
                            onRead: { readerBook = $0 }
                        )
                    }

                    BookShelfView(
                        title: usesRecentlyAddedCatalogTitle ? "Recently added" : "Featured Audiobooks",
                        subtitle: usesRecentlyAddedCatalogTitle
                            ? "The latest books from your selected catalogs"
                            : "Fresh public-domain recordings",
                        books: app.featuredBooks,
                        coverWidth: 156,
                        coverHeight: 206,
                        loadingStatus: app.isFeaturedBooksLoading
                            ? (usesRecentlyAddedCatalogTitle
                                ? "Loading recently added audiobooks..."
                                : "Loading featured audiobooks...")
                            : nil,
                        crossfadesBookChanges: true,
                        editorial: true,
                        onShowDetails: { book, collection in
                            detailSelection = HomeBookDetailSelection(book: book, collection: collection)
                        },
                        onRead: { readerBook = $0 }
                    )
                }
                .frame(width: geometry.size.width, alignment: .leading)
                .padding(.top, 16)
                .padding(.bottom, 220)
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .top)
        }
        .background {
            GlassArtworkBackground(book: nil)
        }
        .scrollEdgeEffectStyle(.soft, for: .top)
        .navigationTitle("LibriVox")
        .toolbarBackground(.hidden, for: .navigationBar)
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
        .sheet(item: $detailSelection) { selection in
            NavigationStack {
                BookDetailBrowser(book: selection.book, collection: selection.collection)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button {
                                detailSelection = nil
                            } label: {
                                Label("Back", systemImage: "chevron.backward")
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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton()
            }
        }
    }

}

private struct WelcomeBanner: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Welcome to LibriVox")
                .font(.title2.bold())
            Text("Browse public-domain audiobooks and keep your favorites in your library.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .liquidGlassPanel(cornerRadius: 26, interactive: false)
    }
}

private struct ContinueListeningSection: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        let books = app.continueListeningBooks
        if !books.isEmpty {
            ContinueListeningCarousel(books: books)
        }
    }
}

private struct ContinueListeningCarousel: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var books: [AudioBook]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HomeSectionHeader(title: "Continue Listening")
                .padding(.horizontal, homeContentMargin)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 16) {
                    ForEach(books) { book in
                        ContinueListeningCard(book: book)
                            .transition(.opacity)
                    }
                }
                .padding(.vertical, 2)
                .animation(
                    .easeInOut(duration: reduceMotion ? 0.12 : 0.28),
                    value: books.map(\.id)
                )
            }
            .contentMargins(.horizontal, homeContentMargin, for: .scrollContent)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("home-continue-listening-carousel")
        }
        .bookDetailCollection(books, title: "Continue Listening")
        .accessibilityIdentifier("home-continue-listening-section")
    }
}

private struct ContinueListeningCard: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook

    private let coverWidth: CGFloat = 156
    private let coverHeight: CGFloat = 204

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            ZStack(alignment: .bottomTrailing) {
                BookDetailsNavigationLink(book: book) {
                    BookCoverView(
                        book: book,
                        width: coverWidth,
                        height: coverHeight,
                        cornerRadius: 18,
                        preferFullImage: true
                    )
                    .contentShape(Rectangle())
                    .bookActionsContextMenu(
                        book: book,
                        context: .continueListening,
                        previewStyle: .cover
                    )
                    .accessibilityHint("Touch and hold for book actions")
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens book details")
                .accessibilityIdentifier("continue-listening-book-\(book.id)")

                playButton
                    .padding(8)
            }

            BookDetailsNavigationLink(book: book) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(book.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(2, reservesSpace: true)
                    Text(listeningSummary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2, reservesSpace: true)
                    BookShelfProgressView(book: book)
                }
                .frame(width: coverWidth, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .bookActionsContextMenu(book: book, context: .continueListening, previewStyle: .cover)
            .accessibilityHint("Opens book details. Touch and hold for book actions")
            .accessibilityIdentifier("continue-listening-summary-\(book.id)")
        }
        .frame(width: coverWidth, alignment: .leading)
    }

    private var listeningSummary: String {
        if app.playback.currentBook?.id == book.id {
            return app.playback.currentChapter?.displayTitle ?? book.author
        }
        return app.progressSummary(for: book) ?? book.author
    }

    private var playButton: some View {
        Button {
            if isCurrentBook {
                app.playback.toggle()
            } else {
                app.play(book, presentNowPlaying: false)
            }
        } label: {
            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(.black)
                .frame(width: 44, height: 44)
                .background(.white, in: Circle())
                .shadow(color: .black.opacity(0.24), radius: 10, y: 4)
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isPlaying ? "Pause \(book.title)" : "Resume \(book.title)")
        .accessibilityValue(isPlaying ? "Playing" : (isCurrentBook ? "Paused" : "Not playing"))
        .accessibilityIdentifier("continue-listening-playback-\(book.id)")
    }

    private var isCurrentBook: Bool {
        app.playback.currentBook?.id == book.id
    }

    private var isPlaying: Bool {
        isCurrentBook && app.playback.isPlaying
    }
}

private struct BookShelfView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var title: String
    var subtitle: String?
    var books: [AudioBook]
    var coverWidth: CGFloat
    var coverHeight: CGFloat
    var loadingStatus: String? = nil
    var crossfadesBookChanges = false
    var editorial = false
    var onShowDetails: (AudioBook, BookDetailCollection) -> Void
    var onRead: (AudioBook) -> Void

    private var contentIdentity: String {
        guard !books.isEmpty else { return "loading" }
        return crossfadesBookChanges ? books.map(\.id).joined(separator: "|") : "content"
    }

    var body: some View {
        if !books.isEmpty || loadingStatus != nil {
            VStack(alignment: .leading, spacing: 12) {
                HomeSectionHeader(title: title, subtitle: subtitle)
                    .padding(.horizontal, homeContentMargin)
                if let loadingStatus {
                    FeaturedLoadingStatus(text: loadingStatus)
                        .padding(.horizontal, homeContentMargin)
                }
                ZStack(alignment: .topLeading) {
                    if books.isEmpty {
                        BookShelfLoadingPlaceholder(
                            coverWidth: coverWidth,
                            coverHeight: coverHeight
                        )
                        .transition(.opacity)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(alignment: .top, spacing: 16) {
                                ForEach(books) { book in
                                    BookShelfItem(
                                        book: book,
                                        coverWidth: coverWidth,
                                        coverHeight: coverHeight,
                                        editorial: editorial,
                                        onShowDetails: { book in
                                            onShowDetails(book, BookDetailCollection(title: title, books: books))
                                        },
                                        onRead: onRead
                                    )
                                }
                            }
                            .padding(.vertical, 2)
                        }
                        .contentMargins(.horizontal, homeContentMargin, for: .scrollContent)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .bookDetailCollection(books, title: title)
                        .accessibilityIdentifier("home-shelf-\(title)")
                        .id(contentIdentity)
                        .transition(.opacity)
                    }
                }
                .animation(
                    .easeInOut(duration: reduceMotion ? 0.12 : 0.28),
                    value: contentIdentity
                )
            }
        }
    }
}

private struct BookShelfLoadingPlaceholder: View {
    var coverWidth: CGFloat
    var coverHeight: CGFloat

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 16) {
                ForEach(0..<3, id: \.self) { index in
                    VStack(alignment: .leading, spacing: 9) {
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .fill(.secondary.opacity(0.14))
                            .frame(width: coverWidth, height: coverHeight)
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(.secondary.opacity(0.14))
                            .frame(width: coverWidth * 0.82, height: 14)
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(.secondary.opacity(0.1))
                            .frame(width: coverWidth * 0.58, height: 11)
                    }
                    .accessibilityHidden(true)
                    .accessibilityIdentifier("home-featured-placeholder-\(index)")
                }
            }
            .padding(.vertical, 2)
        }
        .contentMargins(.horizontal, homeContentMargin, for: .scrollContent)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct BookShelfItem: View {
    var book: AudioBook
    var coverWidth: CGFloat
    var coverHeight: CGFloat
    var editorial = false
    var onShowDetails: (AudioBook) -> Void
    var onRead: (AudioBook) -> Void

    var body: some View {
        ZStack(alignment: .topTrailing) {
            BookDetailsNavigationLink(book: book) {
                CarouselBookCard(
                    book: book,
                    coverWidth: coverWidth,
                    coverHeight: coverHeight,
                    editorial: editorial
                )
                .contentShape(Rectangle())
                .bookActionsContextMenu(
                    book: book,
                    context: .home,
                    previewStyle: .cover,
                    onShowDetails: { onShowDetails(book) },
                    onRead: book.canReadSourceText ? { onRead(book) } : nil
                )
                .accessibilityHint("Touch and hold for book actions")
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens book details")
            .accessibilityIdentifier("home-shelf-book-\(book.id)")

            BookActionsMenuButton(
                book: book,
                context: .home,
                onShowDetails: { onShowDetails(book) },
                onRead: book.canReadSourceText ? { onRead(book) } : nil
            ) {
                CompactCarouselActionsLabel()
            }
            .accessibilityLabel("Actions for \(book.title)")
            .accessibilityIdentifier("carousel-book-actions-\(book.id)")
        }
    }
}

private struct CompactCarouselActionsLabel: View {
    var body: some View {
        GlassCircleIcon(
            systemImage: "ellipsis",
            size: 30,
            symbolSize: 12,
            material: .clear
        )
        .frame(width: 44, height: 44)
        .contentShape(Circle())
    }
}

private struct FeaturedLoadingStatus: View {
    @Environment(\.libriVoxAccent) private var accent
    var text: String

    var body: some View {
        HStack(spacing: 8) {
            ProgressView()
                .controlSize(.small)
                .tint(accent)
                .frame(width: 16, height: 16)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(text)
    }
}

private struct HomeSectionHeader: View {
    var title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(.title2, design: .serif, weight: .bold))
            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct CarouselBookCard: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    var coverWidth: CGFloat
    var coverHeight: CGFloat
    var editorial = false

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            BookCoverView(
                book: book,
                width: coverWidth,
                height: coverHeight,
                cornerRadius: 18,
                preferFullImage: true
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(book.title)
                    .font(editorial ? .system(.headline, design: .serif, weight: .semibold) : .subheadline.weight(.semibold))
                    // Keep shelf cards aligned while allowing Dynamic Type to size the text.
                    .lineLimit(editorial ? 3 : 2, reservesSpace: true)
                Text(book.author)
                    .font(.caption)
                    .foregroundStyle(editorial ? Color.primary.opacity(0.86) : Color.secondary)
                    .lineLimit(1, reservesSpace: true)
                Text(book.source.label)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(editorial ? AnyShapeStyle(Color.primary.opacity(0.86)) : AnyShapeStyle(.tertiary))
                    .lineLimit(1, reservesSpace: true)
                BookShelfProgressView(book: book)
            }
            .frame(width: coverWidth, alignment: .leading)
        }
        .frame(width: coverWidth, alignment: .leading)
        .padding(editorial ? 16 : 0)
        .background {
            if editorial {
                ArtworkGradient(book: book, style: .card, enabled: app.settings.bookDetailUseArtworkColorScheme)
                    .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            }
        }
    }
}

struct BookShelfProgressView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    var book: AudioBook

    var body: some View {
        let progress = app.progress(for: book)
        let hasStarted = progress?.hasStarted == true
        ProgressView(value: progress?.positionSeconds ?? 0, total: max(progress?.durationSeconds ?? 0, 1))
            .tint(accent)
            // Starting a book must not change its card's height.
            .opacity(hasStarted ? 1 : 0)
            .accessibilityHidden(!hasStarted)
    }
}

struct BookRow: View {
    var book: AudioBook
    var subtitle: String

    var body: some View {
        HStack(spacing: 14) {
            BookCoverView(book: book, width: 56, height: 76, cornerRadius: 10)
            VStack(alignment: .leading, spacing: 4) {
                Text(book.title)
                    .font(.headline)
                    .lineLimit(2)
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Text("\(book.source.label) · \(formatDuration(seconds: book.totalDurationSeconds))")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

struct BookCoverView: View {
    @Environment(AppModel.self) private var app
    @StateObject private var imageLoader = CoverImageLoader()
    var book: AudioBook
    var width: CGFloat
    var height: CGFloat
    var cornerRadius: CGFloat
    var preferFullImage = false
    var displayModeOverride: CoverArtDisplayMode? = nil
    var showsDownloadBadge = true
    var downloadBadgeAlignment: Alignment = .bottomLeading
    var downloadBadgeSize: CGFloat = 16

    private var coverURLs: [URL] {
        let urlStrings = preferFullImage
            ? [book.fullCoverImageURL, book.coverImageURL]
            : [book.coverImageURL, book.fullCoverImageURL]
        var seenURLStrings: Set<String> = []

        return urlStrings.compactMap { urlString in
            guard let urlString, let url = Self.normalizedImageURL(urlString) else { return nil }
            guard seenURLStrings.insert(url.absoluteString).inserted else { return nil }
            return url
        }
    }

    private var coverURLSignature: String {
        coverURLs.map(\.absoluteString).joined(separator: "|")
    }

    private static func normalizedImageURL(_ value: String) -> URL? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard var components = URLComponents(string: trimmed) else { return URL(string: trimmed) }
        if components.host == "www.archive.org" {
            components.host = "archive.org"
        }
        return components.url ?? URL(string: trimmed)
    }

    var body: some View {
        let candidateCoverURLs = coverURLs

        ZStack {
            if let loadedImage = imageLoader.image {
                let image = Image(uiImage: loadedImage)
                coverBackground(for: image)
                    .overlay {
                        coverImage(image, sourceSize: loadedImage.size)
                    }
            } else {
                fallbackCover
                    .overlay {
                        if !candidateCoverURLs.isEmpty, imageLoader.isLoading {
                            ProgressView()
                                .controlSize(.small)
                                .tint(.white)
                                .padding(8)
                                .background(.black.opacity(0.22), in: Circle())
                                .accessibilityLabel("Loading cover art")
                        }
                    }
            }
        }
        .task(id: coverURLSignature) {
            await imageLoader.load(urls: candidateCoverURLs)
        }
        .onDisappear {
            imageLoader.cancel()
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            if effectiveDisplayMode != .fit {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(.white.opacity(0.16), lineWidth: 1)
            }
        }
        .overlay(alignment: downloadBadgeAlignment) {
            if showsDownloadBadge, let state = app.downloadBadgeState(for: book) {
                DownloadCoverBadge(state: state, size: downloadBadgeSize)
                    .padding(max(4, min(width, height) * 0.045))
            }
        }
        .accessibilityLabel(book.title)
        .accessibilityValue(imageLoader.image != nil ? "Cover art loaded" : (imageLoader.isLoading ? "Loading cover art" : "Cover art unavailable"))
    }

    private var coverContentMode: ContentMode {
        effectiveDisplayMode == .fill ? .fill : .fit
    }

    private var coverContentPadding: CGFloat {
        effectiveDisplayMode == .artworkBackdrop ? max(2, min(width, height) * 0.025) : 0
    }

    private var effectiveDisplayMode: CoverArtDisplayMode {
        displayModeOverride ?? app.settings.coverArtDisplayMode
    }

    private var coverBackground: some View {
        LinearGradient(
            colors: [
                Color(uiColor: .secondarySystemBackground),
                Color(uiColor: .tertiarySystemFill),
                Color(uiColor: .systemBackground),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .frame(width: width, height: height)
    }

    @ViewBuilder
    private func coverBackground(for image: Image) -> some View {
        switch effectiveDisplayMode {
        case .fit:
            Color.clear
                .frame(width: width, height: height)
        case .fill:
            coverBackground
        case .artworkBackdrop:
            image
                .resizable()
                .scaledToFill()
                .frame(width: width, height: height)
                .scaleEffect(1.12)
                .blur(radius: max(16, min(width, height) * 0.12))
                .saturation(1.14)
                .overlay {
                    coverBackground.opacity(0.18)
                }
                .clipped()
        }
    }

    @ViewBuilder
    private func coverImage(_ image: Image, sourceSize: CGSize) -> some View {
        let contentWidth = max(1, width - (coverContentPadding * 2))
        let contentHeight = max(1, height - (coverContentPadding * 2))

        if effectiveDisplayMode == .fit {
            let fittedSize = fittedCoverSize(sourceSize: sourceSize)
            let fittedCornerRadius = min(cornerRadius, min(fittedSize.width, fittedSize.height) * 0.12)
            image
                .resizable()
                .scaledToFit()
                .frame(width: fittedSize.width, height: fittedSize.height)
                .clipShape(RoundedRectangle(cornerRadius: fittedCornerRadius, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: fittedCornerRadius, style: .continuous)
                        .stroke(.white.opacity(0.16), lineWidth: 1)
                }
                .frame(width: width, height: height)
        } else {
            image
                .resizable()
                .aspectRatio(contentMode: coverContentMode)
                .frame(width: contentWidth, height: contentHeight)
                .frame(width: width, height: height)
                .clipped()
        }
    }

    private func fittedCoverSize(sourceSize: CGSize) -> CGSize {
        guard sourceSize.width > 0, sourceSize.height > 0 else {
            return CGSize(width: width, height: height)
        }
        let scale = min(width / sourceSize.width, height / sourceSize.height)
        return CGSize(
            width: max(1, sourceSize.width * scale),
            height: max(1, sourceSize.height * scale)
        )
    }

    private var fallbackCover: some View {
        LinearGradient(
            colors: [
                Color.primary.opacity(0.18),
                Color.primary.opacity(0.08),
                Color(uiColor: .secondarySystemBackground),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .frame(width: width, height: height)
        .overlay(alignment: .bottomLeading) {
            Text(book.title)
                .font(.caption.bold())
                .foregroundStyle(.primary)
                .lineLimit(3)
                .padding(8)
        }
    }
}

private struct DownloadCoverBadge: View {
    @Environment(\.libriVoxAccent) private var accent
    @Environment(\.colorScheme) private var colorScheme
    var state: DownloadState
    var size: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: max(6, size * 0.32), style: .continuous)
                .fill(backgroundStyle)
                .frame(width: size, height: size)
                .shadow(color: .black.opacity(0.18), radius: max(3, size * 0.25), y: 2)

            badgeIcon
        }
        .accessibilityLabel(accessibilityLabel)
    }

    @ViewBuilder
    private var badgeIcon: some View {
        switch state {
        case .downloaded:
            Image(systemName: "checkmark")
                .font(.system(size: size * 0.5, weight: .bold))
                .foregroundStyle(downloadedForeground)
        case .queued, .downloading:
            ProgressView()
                .controlSize(.small)
                .tint(accent)
                .scaleEffect(max(0.55, size / 34))
        case .failed:
            Image(systemName: "exclamationmark")
                .font(.system(size: size * 0.5, weight: .bold))
                .foregroundStyle(.white)
        case .notDownloaded:
            EmptyView()
        }
    }

    private var backgroundStyle: Color {
        switch state {
        case .downloaded:
            return accent
        case .failed:
            return .orange
        case .queued, .downloading, .notDownloaded:
            return Color(.systemBackground).opacity(0.88)
        }
    }

    private var downloadedForeground: Color {
        colorScheme == .dark ? .black : .white
    }

    private var accessibilityLabel: String {
        switch state {
        case .downloaded:
            return "Fully downloaded"
        case .queued:
            return "Download queued"
        case .downloading:
            return "Download in progress"
        case .failed:
            return "Download failed"
        case .notDownloaded:
            return "Not downloaded"
        }
    }
}

struct ReadBookCoverShortcut: View {
    var foreground: Color = .primary

    var body: some View {
        GlassCircleIcon(
            systemImage: "text.book.closed",
            size: 52,
            symbolSize: 19,
            foreground: foreground
        )
        .accessibilityLabel("Read book")
    }
}


@MainActor
final class CoverImageLoader: ObservableObject {
    @Published private(set) var image: UIImage?
    @Published private(set) var isLoading = false

    private static let imageCache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 48 * 1_024 * 1_024
        cache.countLimit = 64
        return cache
    }()
    private let maxPixelSize: Int
    private let dataLoader: @Sendable ([URL]) async -> Data?
    private var decodingTask: Task<UIImage?, Never>?
    private var activeSignature = ""
    private var generation = 0

    init(maxPixelSize: Int = 1_024, dataLoader: @escaping @Sendable ([URL]) async -> Data? = { urls in
        await CoverArtworkCache.shared.data(for: urls)
    }) {
        self.maxPixelSize = max(256, min(maxPixelSize, 4_096))
        self.dataLoader = dataLoader
    }

    func load(urls: [URL]) async {
        let signature = urls.map(\.absoluteString).joined(separator: "|")
        guard signature != activeSignature || (!isLoading && image == nil) else { return }
        decodingTask?.cancel()
        decodingTask = nil
        generation += 1
        let requestGeneration = generation
        activeSignature = signature
        image = nil
        isLoading = !urls.isEmpty
        defer {
            if requestGeneration == generation {
                isLoading = false
                decodingTask = nil
            }
        }
        guard !urls.isEmpty, !Task.isCancelled else { return }
        let cacheKey = "\(maxPixelSize)|\(signature)" as NSString
        if let cached = Self.imageCache.object(forKey: cacheKey) {
            image = cached
            return
        }
        let data = await dataLoader(urls)
        guard !Task.isCancelled, requestGeneration == generation, let data else { return }

        let pixelLimit = maxPixelSize
        let request = Task.detached(priority: .utility) {
            Self.decodeCover(data, maxPixelSize: pixelLimit)
        }
        decodingTask = request
        let loadedImage = await withTaskCancellationHandler {
            await request.value
        } onCancel: {
            request.cancel()
        }
        guard !Task.isCancelled, requestGeneration == generation, let loadedImage
        else { return }
        let cost = loadedImage.cgImage.map { $0.bytesPerRow * $0.height } ?? 0
        Self.imageCache.setObject(loadedImage, forKey: cacheKey, cost: cost)
        image = loadedImage
    }

    nonisolated private static func decodeCover(_ data: Data, maxPixelSize: Int) -> UIImage? {
        // Bound both decoding work and texture memory before the cover reaches a transition.
        guard !Task.isCancelled,
              let source = CGImageSourceCreateWithData(data as CFData, [
                kCGImageSourceShouldCache: false,
              ] as CFDictionary),
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
                kCGImageSourceShouldCacheImmediately: true,
              ] as CFDictionary),
              !Task.isCancelled
        else { return nil }
        let thumbnailImage = UIImage(cgImage: thumbnail)
        let preparedImage = thumbnailImage.preparingForDisplay() ?? thumbnailImage
        return Task.isCancelled ? nil : preparedImage
    }

    func cancel() {
        // SwiftUI cancels the structured task. Reset loading state so returning to
        // book details can retry, and prevent an earlier result from being published.
        generation += 1
        decodingTask?.cancel()
        decodingTask = nil
        isLoading = false
    }
}
