import AVFoundation
import LibriVoxCore
import SwiftUI
import UIKit

struct MiniPlayerView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.tabViewBottomAccessoryPlacement) private var placement
    @Environment(\.playerTransitionContext) private var playerTransition
    @State private var showingRoutes = false
    var transitionSources: PlayerTransitionSources
    var onOpenNowPlaying: (() -> Void)?

    private var isInline: Bool {
        placement == .inline
    }

    var body: some View {
        if let book = app.defaultListeningBook {
            HStack(spacing: 12) {
                Button {
                    openNowPlaying()
                } label: {
                    HStack(spacing: 12) {
                        BookCoverView(
                            book: book,
                            width: isInline ? 28 : 36,
                            height: isInline ? 34 : 44,
                            cornerRadius: isInline ? 7 : 9,
                            showsDownloadBadge: false
                        )
                            .overlay {
                                if app.playback.showsBufferingText {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                                            .fill(.black.opacity(0.34))
                                        ProgressView()
                                            .controlSize(.small)
                                            .tint(.white)
                                    }
                                    .accessibilityHidden(true)
                                }
                            }
                        VStack(alignment: .leading, spacing: 1) {
                            MarqueeText(
                                text: app.playback.currentChapter?.displayTitle ?? book.title,
                                font: .subheadline.bold(), motion: app.settings.animationSpeed,
                                isActive: !app.showingNowPlaying && !showingRoutes
                            )
                                .foregroundStyle(.primary)
                                .accessibilityIdentifier("mini-player-title")
                            if !isInline {
                                Text(miniPlayerSubtitle(for: book))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("mini-player-open")
                .accessibilityLabel(miniPlayerAccessibilityLabel(for: book))
                .accessibilityHint("Opens Now Playing")

                if !isInline, app.settings.showMiniPlayerRouteButton {
                    Button {
                        showingRoutes = true
                    } label: {
                        Image(systemName: "airplayaudio")
                            .font(.title3)
                            .foregroundStyle(.primary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Routes")
                    .accessibilityIdentifier("mini-player-route")
                }

                Button {
                    if app.playback.currentBook == nil {
                        app.playDefaultBook()
                    } else {
                        app.playback.toggle()
                    }
                } label: {
                    MiniPlayerTransportLabel(
                        isPlaying: app.playback.isPlaying
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(app.playback.isPlaying ? "Pause" : "Play")
                .accessibilityValue(app.playback.showsBufferingText ? "Buffering" : "")
                .accessibilityIdentifier("mini-player-play-pause")
            }
            .padding(.horizontal, isInline ? 8 : 10)
            .padding(.vertical, isInline ? 2 : 4)
            // The system accessory has a fixed height. Larger accessibility text
            // remains available in Now Playing, which this compact control opens.
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            .overlay(alignment: .bottom) {
                if app.settings.showMiniPlayerProgress {
                    let progress = app.progress(for: book)
                    let duration = max(progress?.durationSeconds ?? 1, 1)
                    ProgressView(value: min(max(progress?.positionSeconds ?? 0, 0), duration), total: duration)
                        .tint(.primary)
                        .frame(height: 2)
                        .offset(y: 2)
                }
            }
            .background {
                PlayerTransitionAnchor(id: "mini-player", sources: transitionSources)
            }
            .sheet(isPresented: $showingRoutes) {
                RoutePickerSheet(book: app.defaultListeningBook)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
            .simultaneousGesture(openNowPlayingSwipeGesture)
        }
    }

    private func miniPlayerAccessibilityLabel(for book: AudioBook) -> String {
        let chapter = app.playback.currentChapter?.displayTitle ?? app.progressChapter(for: book)?.displayTitle
        return [book.title, chapter, book.author].compactMap { $0 }.joined(separator: ", ")
    }

    private func miniPlayerSubtitle(for book: AudioBook) -> String {
        if app.playback.showsBufferingText {
            return "Buffering..."
        }
        return app.playback.currentBook?.title ?? app.progressSummary(for: book) ?? "Ready to listen"
    }

    private var openNowPlayingSwipeGesture: some Gesture {
        DragGesture(minimumDistance: 18, coordinateSpace: .global)
            .onEnded { value in
                guard shouldOpenNowPlaying(from: value) else { return }
                openNowPlaying()
            }
    }

    private func openNowPlaying() {
        playerTransition.selectSource("mini-player")
        if let onOpenNowPlaying { onOpenNowPlaying() }
        else { app.showingNowPlaying = true }
    }

    private func shouldOpenNowPlaying(from value: DragGesture.Value) -> Bool {
        let upwardDrag = -value.translation.height
        let predictedUpwardDrag = -value.predictedEndTranslation.height
        let horizontalDrag = abs(value.translation.width)
        guard upwardDrag > horizontalDrag * 1.2 else { return false }
        return upwardDrag > 34 || predictedUpwardDrag > 72
    }
}

private struct MiniPlayerTransportLabel: View {
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorSchemeContrast) private var colorSchemeContrast

    var isPlaying: Bool

    var body: some View {
        adaptiveSurface
            .overlay {
                if colorSchemeContrast == .increased {
                    Circle()
                        .stroke(.white, lineWidth: 1)
                        .allowsHitTesting(false)
                }
            }
            .contentShape(Circle())
    }

    @ViewBuilder
    private var adaptiveSurface: some View {
        if reduceTransparency {
            transportContent
                .background(.black.opacity(0.64), in: Circle())
        } else {
            transportContent
                .glassEffect(.regular.tint(.black.opacity(0.16)).interactive(), in: Circle())
        }
    }

    private var transportContent: some View {
        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
            .font(.title3.bold())
            .foregroundStyle(.white)
            .contentTransition(.symbolEffect(.replace))
            .frame(width: 44, height: 44)
    }
}

private enum NowPlayingPanel: String, Identifiable {
    case details
    case bookmarks
    case chapters
    case queue

    var id: String { rawValue }

    var title: String {
        switch self {
        case .details: "Book Details"
        case .bookmarks: "Bookmarks"
        case .chapters: "Chapters"
        case .queue: "Up Next"
        }
    }
}

struct NowPlayingView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var playerAccent
    @Environment(\.accessibilityReduceMotion) private var accessibilityReduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @StateObject private var routeMonitor = AudioOutputRouteMonitor()
    @Environment(\.scenePhase) private var scenePhase
    @State private var panelTransitionSources = PlayerTransitionSources()
    var onShowBookDetails: ((AudioBook) -> Void)?
    var onShowAuthorDetails: ((AudioBook) -> Void)?
    @State private var detailBook: AudioBook?
    @State private var readerBook: AudioBook?
    @State private var showingCoverArtwork = false
    @State private var showingBookmarks = false
    @State private var showingRoutes = false
    @State private var showingSpeedSheet = false
    @State private var showingSleepTimerSheet = false
    @State private var isBookDetailsExpanded = false
    @State private var activePanel: NowPlayingPanel?
    @State private var scrubPreviewSeconds: Double?
    @State private var voiceOver = UIAccessibility.isVoiceOverRunning

    private var nowPlayingPrimaryText: Color {
        .white
    }

    private var nowPlayingSecondaryText: Color {
        .white.opacity(0.9)
    }

    private var nowPlayingTertiaryText: Color {
        .white.opacity(0.86)
    }

    private var titleMotionIsActive: Bool {
        activePanel == nil && detailBook == nil && readerBook == nil && !showingCoverArtwork && !showingBookmarks &&
            !showingRoutes && !showingSpeedSheet && !showingSleepTimerSheet
    }

    var body: some View {
        GeometryReader { proxy in
            let windowInsets = Self.currentWindowSafeAreaInsets
            let topInset = max(proxy.safeAreaInsets.top, windowInsets.top)
            let bottomInset = max(proxy.safeAreaInsets.bottom, windowInsets.bottom)
            let leadingInset = max(proxy.safeAreaInsets.leading, windowInsets.left)
            let trailingInset = max(proxy.safeAreaInsets.trailing, windowInsets.right)
            let safeWidth = max(0, proxy.size.width - leadingInset - trailingInset)
            let contentWidth = max(280, min(safeWidth - 32, 430))
            let coverSize = min(contentWidth * 0.72, min(280, proxy.size.height * 0.28))

            ZStack(alignment: .top) {
                if app.settings.nowPlayingPresentationMode == .panels,
                   !dynamicTypeSize.isAccessibilitySize,
                   !accessibilityReduceMotion, app.settings.animationSpeed != .reduced, !voiceOver {
                    fixedNowPlayingContent(
                        topInset: topInset,
                        bottomInset: bottomInset,
                        contentWidth: contentWidth,
                        coverSize: min(contentWidth * 0.68, min(260, proxy.size.height * 0.26))
                    )
                } else {
                    scrollableNowPlayingContent(
                        topInset: topInset,
                        bottomInset: bottomInset,
                        contentWidth: contentWidth,
                        coverSize: coverSize
                    )
                }

                dismissGrabber(topInset: topInset)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .background {
                ArtworkGradient(book: app.defaultListeningBook, style: .player, enabled: app.settings.bookDetailUseArtworkColorScheme)
            }
            .ignoresSafeArea(.container, edges: .all)
            .clipShape(
                UnevenRoundedRectangle(
                    cornerRadii: .init(
                        topLeading: 34,
                        bottomLeading: 0,
                        bottomTrailing: 0,
                        topTrailing: 34
                    ),
                    style: .continuous
                )
            )
            .foregroundStyle(.primary)
            .tint(playerAccent)
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Now Playing")
            .accessibilityIdentifier("now-playing-surface")
            .accessibilityAction(.escape) {
                dismissNowPlaying()
            }
        }
        .sheet(item: $detailBook) { book in
            NavigationStack {
                BookDetailView(book: book)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") {
                                detailBook = nil
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
        .sheet(isPresented: $showingBookmarks) {
            NowPlayingBookmarksDetailView(book: app.defaultListeningBook)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showingRoutes) {
            RoutePickerSheet(book: app.defaultListeningBook)
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .background {
            NativeZoomPresentation(
                isPresented: Binding(
                    get: { activePanel != nil },
                    set: { if !$0 { activePanel = nil } }
                ),
                sourceID: activePanel?.rawValue ?? NowPlayingPanel.chapters.rawValue,
                sources: panelTransitionSources,
                reduceMotion: accessibilityReduceMotion || app.settings.animationSpeed == .reduced,
                detents: [.medium(), .large()]
            ) {
                if let panel = activePanel {
                    nowPlayingPanelSheet(panel)
                        .environment(app)
                        .environment(\.libriVoxAccent, playerAccent)
                        .environment(\.colorScheme, .dark)
                        .environment(\.scenePhase, scenePhase)
                        .tint(playerAccent)
                }
            }
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)
        }
        .sheet(isPresented: $showingSpeedSheet) {
            PlaybackSpeedSheet()
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showingSleepTimerSheet) {
            PlaybackSleepTimerSheet()
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
        .task(id: activeBookmarkBookID) {
            await app.reloadBookmarks(for: activeBookmarkBookID)
            routeMonitor.refresh()
        }
        .task(id: displayedNowPlayingBook?.id) {
            guard let book = displayedNowPlayingBook else { return }
            _ = await app.hydrateBookDetails(book)
        }
        .onReceive(NotificationCenter.default.publisher(for: AVAudioSession.routeChangeNotification)) { _ in
            routeMonitor.refresh()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
            voiceOver = UIAccessibility.isVoiceOverRunning
        }
    }

    private func nowPlayingPanelSheet(_ panel: NowPlayingPanel) -> some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    nowPlayingPanelContent(panel)
                }
                .frame(maxWidth: .infinity)
                .padding(16)
            }
            .scrollBounceBehavior(.basedOnSize)
            .glassPageBackground()
            .navigationTitle(panel.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        activePanel = nil
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func nowPlayingPanelContent(_ panel: NowPlayingPanel) -> some View {
        switch panel {
        case .details:
            bookDetailsSection
        case .bookmarks:
            bookmarksSection
        case .chapters:
            chapterListSection
        case .queue:
            queueSection
        }
    }

    private static var currentWindowSafeAreaInsets: UIEdgeInsets {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .safeAreaInsets ?? .zero
    }

    private func nowPlayingCover(size: CGFloat) -> some View {
        Group {
            if let book = app.defaultListeningBook {
                ZStack(alignment: .bottomTrailing) {
                    CoverArtworkButton(
                        book: app.latestKnownVersion(of: book),
                        accessibilityIdentifier: "now-playing-cover",
                        onPresentationChange: { showingCoverArtwork = $0 }
                    ) {
                        BookCoverView(
                            book: book,
                            width: size,
                            height: size,
                            cornerRadius: 26,
                            preferFullImage: true,
                            downloadBadgeAlignment: .bottomLeading
                        )
                        .contentShape(Rectangle())
                    }

                    if book.canReadSourceText {
                        Button {
                            readerBook = book
                        } label: {
                            ReadBookCoverShortcut(foreground: nowPlayingPrimaryText)
                        }
                        .buttonStyle(.plain)
                        .offset(x: 10, y: 8)
                    }
                }
                .shadow(color: .black.opacity(0.38), radius: 34, x: 0, y: 22)
            }
        }
    }

    private func scrollableNowPlayingContent(
        topInset: CGFloat,
        bottomInset: CGFloat,
        contentWidth: CGFloat,
        coverSize: CGFloat
    ) -> some View {
        ScrollViewReader { scrollProxy in
            ScrollView {
                LazyVStack(spacing: 24) {
                    Color.clear
                        .frame(height: max(78, topInset + 74))
                        .id(NowPlayingSectionID.top)

                    nowPlayingCover(size: coverSize)

                    trackInfo
                        .frame(width: contentWidth)

                    compactActionRow(
                        width: contentWidth,
                        openDetails: {
                            scrollProxy.scrollTo(NowPlayingSectionID.details, anchor: .top)
                        },
                        openBookmarks: {
                            scrollProxy.scrollTo(NowPlayingSectionID.bookmarks, anchor: .top)
                        },
                        openChapters: {
                            scrollProxy.scrollTo(NowPlayingSectionID.chapters, anchor: .top)
                        }
                    )

                    NowPlayingProgressStrip(scrubPreviewSeconds: $scrubPreviewSeconds)
                        .frame(width: contentWidth)

                    NowPlayingTransportControls(width: contentWidth)

                    playerUtilityRow(width: contentWidth)

                    AudioOutputRoutePill(route: displayedRoute) {
                        showingRoutes = true
                    }
                    .frame(width: contentWidth)

                    bookDetailsSection
                        .frame(width: contentWidth)
                        .id(NowPlayingSectionID.details)

                    bookmarksSection
                        .frame(width: contentWidth)
                        .id(NowPlayingSectionID.bookmarks)

                    chapterListSection
                        .frame(width: contentWidth)
                        .id(NowPlayingSectionID.chapters)

                    queueSection
                        .frame(width: contentWidth)
                        .id(NowPlayingSectionID.queue)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, max(18, bottomInset + 10))
            }
            .scrollBounceBehavior(.basedOnSize)

        }
    }

    private func fixedNowPlayingContent(
        topInset: CGFloat,
        bottomInset: CGFloat,
        contentWidth: CGFloat,
        coverSize: CGFloat
    ) -> some View {
        VStack(spacing: 18) {
            Color.clear
                .frame(height: max(60, topInset + 54))

            nowPlayingCover(size: coverSize)

            trackInfo
                .frame(width: contentWidth)

            compactActionRow(
                width: contentWidth,
                openDetails: { activePanel = .details },
                openBookmarks: { activePanel = .bookmarks },
                openChapters: { activePanel = .chapters }
            )

            NowPlayingProgressStrip(scrubPreviewSeconds: $scrubPreviewSeconds)
                .frame(width: contentWidth)

            NowPlayingTransportControls(width: contentWidth)

            playerUtilityRow(width: contentWidth)

            AudioOutputRoutePill(route: displayedRoute) {
                showingRoutes = true
            }
            .frame(width: contentWidth)

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding(.bottom, max(18, bottomInset + 10))
        .contentShape(Rectangle())
    }

    private func dismissGrabber(topInset: CGFloat) -> some View {
        Button(action: dismissNowPlaying) {
            Capsule()
                .fill(.primary.opacity(0.24))
                .frame(width: 46, height: 5)
                .frame(width: 180, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(.top, max(36, topInset + 28))
        .zIndex(3)
        .accessibilityLabel("Dismiss Now Playing")
        .accessibilityHint("Returns to the mini player")
        .accessibilityIdentifier("now-playing-dismiss-grabber")
    }

    private var trackInfo: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                MarqueeText(
                    text: app.playback.currentChapter?.displayTitle ?? app.defaultListeningBook.flatMap(app.progressChapter(for:))?.displayTitle ?? "Ready to listen",
                    font: .title2.bold(), motion: app.settings.animationSpeed, color: nowPlayingPrimaryText, staticLineLimit: nil,
                    isActive: titleMotionIsActive
                )
                    .foregroundStyle(nowPlayingPrimaryText)
                    .accessibilityIdentifier("now-playing-chapter-title")
                if app.playback.showsBufferingText {
                    HStack(spacing: 7) {
                        ProgressView()
                            .controlSize(.small)
                            .tint(nowPlayingPrimaryText)
                        Text(bufferingStatusText)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(nowPlayingPrimaryText)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(bufferingStatusText)
                }
                if let book = app.defaultListeningBook {
                    Button {
                        showBookDetails(book)
                    } label: {
                        MarqueeText(text: book.title, font: .headline, motion: app.settings.animationSpeed, color: nowPlayingSecondaryText, staticLineLimit: nil, isActive: titleMotionIsActive)
                            .foregroundStyle(nowPlayingSecondaryText)
                            .accessibilityIdentifier("now-playing-book-title")
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens book details")

                    Button {
                        showAuthorDetails(app.latestKnownVersion(of: book))
                    } label: {
                        Text(book.author)
                            .foregroundStyle(nowPlayingTertiaryText)
                            .multilineTextAlignment(.leading)
                            .lineLimit(1)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens the author profile")
                    .accessibilityIdentifier("now-playing-author")
                } else {
                    Text("Nothing playing")
                        .font(.headline)
                        .foregroundStyle(nowPlayingSecondaryText)
                        .multilineTextAlignment(.leading)
                        .lineLimit(1)
                }
            }
            .shadow(color: .black.opacity(0.34), radius: 7, y: 2)

            Spacer(minLength: 0)

            if let book = displayedNowPlayingBook {
                NowPlayingBookmarkControl(scrubPreviewSeconds: $scrubPreviewSeconds) { isBookmarked, canBookmark in
                    BookActionsMenuButton(
                        book: book,
                        context: .nowPlaying,
                        currentBookmarkExists: isBookmarked,
                        canBookmarkCurrent: canBookmark,
                        onShowDetails: {
                            showBookDetails(book)
                        },
                        onRead: book.canReadSourceText ? {
                            readerBook = book
                        } : nil,
                        onShowBookmarks: {
                            showBookmarks()
                        },
                        onToggleCurrentBookmark: {
                            Task {
                                await app.toggleCurrentPlayheadBookmark(
                                    for: app.defaultListeningBook,
                                    positionSeconds: scrubPreviewSeconds
                                )
                            }
                        }
                    ) {
                        GlassCircleIcon(
                            systemImage: "ellipsis",
                            size: 44,
                            symbolSize: 18,
                            material: .clear,
                            foreground: nowPlayingPrimaryText
                        )
                    }
                    .accessibilityLabel("Now playing actions")
                }
            }
        }
    }

    private var displayedNowPlayingBook: AudioBook? {
        guard let book = app.defaultListeningBook else { return nil }
        return app.latestKnownVersion(of: book)
    }

    private func showBookDetails(_ book: AudioBook) {
        if let onShowBookDetails {
            onShowBookDetails(book)
        } else {
            activePanel = nil
            detailBook = book
        }
    }

    private func showAuthorDetails(_ book: AudioBook) {
        if let onShowAuthorDetails {
            onShowAuthorDetails(book)
        } else {
            app.openDiscoverSearch(query: book.author, field: .author)
        }
    }

    private func showBookmarks() {
        if app.settings.nowPlayingPresentationMode == .panels {
            activePanel = .bookmarks
        } else {
            showingBookmarks = true
        }
    }

    private var bufferingStatusText: String {
        if let chapter = app.playback.currentChapter {
            return "Buffering \(chapter.displayTitle)"
        }
        if let book = app.playback.currentBook ?? app.defaultListeningBook {
            return "Buffering \(book.title)"
        }
        return "Buffering"
    }

    private var bookBookmarks: [Bookmark] {
        guard let bookID = activeBookmarkBookID else { return [] }
        return app.bookmarks
            .filter { $0.bookID == bookID }
            .sorted { lhs, rhs in
                if lhs.chapterID != rhs.chapterID {
                    return chapterNumber(for: lhs) < chapterNumber(for: rhs)
                }
                return lhs.positionMilliseconds < rhs.positionMilliseconds
            }
    }

    private var visibleBookBookmarks: [Bookmark] {
        Array(bookBookmarks.prefix(4))
    }

    private var playbackSpeedLabel: String {
        PlaybackSpeedOptions.label(for: Double(app.playback.playbackSpeed))
    }

    private func sleepTimerLabel(at date: Date = .now) -> String {
        switch app.sleepTimerState {
        case .off:
            return "Sleep"
        case .expired:
            return "Expired"
        case .endOfChapter:
            return "Chapter"
        case .running:
            guard let remainingSeconds = app.sleepTimerRemainingSeconds(at: date) else {
                return "Sleep"
            }
            if remainingSeconds < 60 {
                return "<1 min"
            }
            let minutes = Int((Double(remainingSeconds) / 60).rounded(.up))
            return minutes >= 60 ? "1 hr" : "\(minutes) min"
        }
    }

    private var sleepTimerIcon: String {
        switch app.sleepTimerState {
        case .off:
            return "moon.zzz"
        case .running:
            return "timer"
        case .endOfChapter:
            return "book.pages"
        case .expired:
            return "checkmark"
        }
    }

    private var activeBookmarkBookID: String? {
        app.playback.currentBook?.id ?? app.defaultListeningBook?.id
    }

    private var displayedRoute: AudioOutputRoute {
        if GoogleCastBridge.isConnected {
            return AudioOutputRoute(
                name: "Google Cast",
                systemImage: "dot.radiowaves.left.and.right",
                isExternal: true
            )
        }

        if app.sharePlay.isActive {
            return AudioOutputRoute(
                name: "SharePlay",
                systemImage: "shareplay",
                isExternal: true
            )
        }

        return routeMonitor.route
    }

    private func dismissNowPlaying() {
        app.showingNowPlaying = false
    }

    private func sourceURL(for book: AudioBook) -> URL? {
        [
            book.libriVoxURL,
            book.lit2GoURL,
            book.wolneLekturyURL,
            book.gutenbergURL,
        ]
        .compactMap { value in
            value?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        .filter { !$0.isEmpty }
        .compactMap(URL.init(string:))
        .first
    }

    private func playerUtilityRow(width: CGFloat) -> some View {
        let usesPanels = app.settings.nowPlayingPresentationMode == .panels

        return LiquidGlassGroup {
            HStack(spacing: usesPanels ? 10 : 24) {
                playerUtilityButton(
                    label: "Speed",
                    accessibilityLabel: "Narration speed"
                ) {
                    Text(playbackSpeedLabel)
                        .font(.subheadline.monospacedDigit().weight(.bold))
                } action: {
                    showingSpeedSheet = true
                }

                Spacer(minLength: 0)

                TimelineView(.periodic(from: .now, by: 30)) { timeline in
                    playerUtilityButton(
                        label: sleepTimerLabel(at: timeline.date),
                        accessibilityLabel: "Sleep timer",
                        isSelected: app.sleepTimerState.isActive
                    ) {
                        Image(systemName: sleepTimerIcon)
                            .font(.headline.weight(.semibold))
                    } action: {
                        showingSleepTimerSheet = true
                    }
                }

                Spacer(minLength: 0)

                playerUtilityButton(
                    label: "Output",
                    accessibilityLabel: "Audio output and Cast"
                ) {
                    Image(systemName: "airplayaudio")
                        .font(.headline.weight(.semibold))
                } action: {
                    showingRoutes = true
                }

                if usesPanels {
                    Spacer(minLength: 0)

                    playerUtilityButton(
                        label: "Chapters",
                        accessibilityLabel: "Chapters"
                    ) {
                        Image(systemName: "list.bullet")
                            .font(.headline.weight(.semibold))
                    } action: {
                        activePanel = .chapters
                    }
                    .background { PlayerTransitionAnchor(id: NowPlayingPanel.chapters.rawValue, sources: panelTransitionSources) }
                }
            }
            .frame(width: width)
        }
    }

    private func playerUtilityButton<Icon: View>(
        label: String,
        accessibilityLabel: String,
        isSelected: Bool = false,
        showsLabel: Bool = false,
        @ViewBuilder icon: () -> Icon,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: showsLabel ? 6 : 0) {
                icon()
                    .foregroundStyle(isSelected ? nowPlayingPrimaryText : nowPlayingPrimaryText.opacity(0.88))
                    .frame(width: 48, height: 48)
                    .glassEffect(
                        isSelected
                            ? .clear.tint(nowPlayingPrimaryText.opacity(0.22)).interactive()
                            : .clear.interactive(),
                        in: Circle()
                    )

                if showsLabel {
                    Text(label)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(nowPlayingSecondaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
            }
            .frame(width: 64, height: showsLabel ? nil : 54)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }

    private func compactActionRow(
        width: CGFloat,
        openDetails: @escaping () -> Void,
        openBookmarks: @escaping () -> Void,
        openChapters: @escaping () -> Void
    ) -> some View {
        let usesPanels = app.settings.nowPlayingPresentationMode == .panels

        return LiquidGlassGroup {
            HStack(spacing: 8) {
                Spacer(minLength: 0)

                compactCircleButton("info.circle", label: "Details", action: openDetails)
                    .background { PlayerTransitionAnchor(id: NowPlayingPanel.details.rawValue, sources: panelTransitionSources) }
                    .disabled(app.defaultListeningBook == nil)

                NowPlayingBookmarkControl(scrubPreviewSeconds: $scrubPreviewSeconds) { isBookmarked, canBookmark in
                    compactCircleButton(
                        !isBookmarked ? "bookmark" : "bookmark.fill",
                        label: !isBookmarked ? "Bookmark current position" : "Remove current bookmark",
                        isSelected: isBookmarked
                    ) {
                        Task {
                            await app.toggleCurrentPlayheadBookmark(
                                for: app.defaultListeningBook,
                                positionSeconds: scrubPreviewSeconds
                            )
                        }
                    }
                    .disabled(!canBookmark)
                }

                compactCircleButton("bookmark.square", label: "Show bookmarks", action: openBookmarks)
                    .background { PlayerTransitionAnchor(id: NowPlayingPanel.bookmarks.rawValue, sources: panelTransitionSources) }
                    .disabled(activeBookmarkBookID == nil)

                if !usesPanels {
                    compactCircleButton("list.bullet", label: "Show chapters", action: openChapters)
                        .disabled(app.defaultListeningBook?.chapters.isEmpty ?? true)
                }
            }
            .frame(width: width)
        }
    }

    private var chapterListSection: some View {
        PlayerSection(title: "Chapters") {
            if let book = app.defaultListeningBook {
                LazyVStack(spacing: 12) {
                    ForEach(book.chapters) { chapter in
                        NowPlayingChapterRow(book: book, chapter: chapter)
                        Divider()
                    }
                }
            } else {
                Text("Chapters will appear here when audio is ready.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var bookDetailsSection: some View {
        PlayerSection(title: "Book Details") {
            if let book = displayedNowPlayingBook {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top, spacing: 12) {
                        Button {
                            showBookDetails(book)
                        } label: {
                            BookCoverView(
                                book: book,
                                width: 56,
                                height: 76,
                                cornerRadius: 10,
                                preferFullImage: true,
                                showsDownloadBadge: false
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Open \(book.title) details")

                        VStack(alignment: .leading, spacing: 4) {
                            Text(book.title)
                                .font(.headline)
                                .lineLimit(2)
                            Button {
                                showAuthorDetails(book)
                            } label: {
                                Text(book.author)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                            .buttonStyle(.plain)
                            .accessibilityHint("Opens the author profile")
                            Text(book.source.label)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }

                    bookDetailsMetadata(for: book)

                    let readers = nowPlayingReaderNames(for: book)
                    if !readers.isEmpty {
                        Label("Read by \(readers.joined(separator: ", "))", systemImage: "person.wave.2")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                            .textSelection(.enabled)
                            .accessibilityIdentifier("Now playing book readers")
                    }

                    let description = book.description.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !description.isEmpty {
                        Text(description)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(isBookDetailsExpanded ? nil : 5)
                            .fixedSize(horizontal: false, vertical: true)
                            .textSelection(.enabled)
                            .accessibilityIdentifier("Now playing book description")

                        if description.count > 180 {
                            bookDetailsExpansionButton
                        }
                    }

                    showFullBookDetailsButton(for: book)
                }
            } else {
                Text("Book details will appear here when audio is ready.")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var bookDetailsExpansionButton: some View {
        Button {
            withAnimation(.snappy(duration: 0.28)) {
                isBookDetailsExpanded.toggle()
            }
        } label: {
            Label(
                isBookDetailsExpanded ? "Show Less" : "Show More",
                systemImage: isBookDetailsExpanded ? "chevron.up" : "chevron.down"
            )
            .font(.subheadline.weight(.semibold))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isBookDetailsExpanded ? "Collapse book description" : "Expand book description")
        .accessibilityIdentifier("Now playing description toggle")
    }

    private func showFullBookDetailsButton(for book: AudioBook) -> some View {
        Button {
            showBookDetails(book)
        } label: {
            Label("See Full Book Details", systemImage: "arrow.right")
                .font(.subheadline.weight(.semibold))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Show full book details")
        .accessibilityHint("Opens the complete book details screen")
        .accessibilityIdentifier("Show full book details")
    }

    private func bookDetailsMetadata(for book: AudioBook) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                bookDetailsMetadataPills(for: book)
            }
        }
    }

    @ViewBuilder
    private func bookDetailsMetadataPills(for book: AudioBook) -> some View {
        detailMetadataPill(text: book.source.label, systemImage: "music.note.list")
        detailMetadataPill(text: "\(book.chapters.count) chapters", systemImage: "list.bullet")
        if book.totalDurationSeconds > 0 {
            detailMetadataPill(text: formatDuration(seconds: book.totalDurationSeconds), systemImage: "clock")
        }
        if let language = book.language {
            detailMetadataPill(text: language, systemImage: "globe")
        }
        if let releaseDate = book.releaseDate?.trimmingCharacters(in: .whitespacesAndNewlines),
           !releaseDate.isEmpty {
            detailMetadataPill(text: releaseDate, systemImage: "calendar")
        }
    }

    private func nowPlayingReaderNames(for book: AudioBook) -> [String] {
        Array(Set(book.narrators + book.chapters.compactMap(\.reader))).sorted()
    }

    private func detailMetadataPill(text: String, systemImage: String) -> some View {
        Label(text, systemImage: systemImage)
            .font(.caption.weight(.medium))
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.thinMaterial, in: Capsule())
    }

    private var bookmarksSection: some View {
        PlayerSection(title: "Bookmarks") {
            VStack(alignment: .leading, spacing: 12) {
                if bookBookmarks.isEmpty {
                    Label("No bookmarks yet", systemImage: "bookmark")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 0) {
                        ForEach(visibleBookBookmarks) { bookmark in
                            Button {
                                openBookmark(bookmark)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "bookmark.fill")
                                        .font(.body)
                                        .foregroundStyle(playerAccent)
                                        .frame(width: 22)

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(chapterTitle(for: bookmark))
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                        Text(bookmark.note.isEmpty ? bookmark.createdAt.formatted(date: .abbreviated, time: .shortened) : bookmark.note)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }

                                    Spacer(minLength: 8)

                                    Text(formatDuration(seconds: bookmark.positionMilliseconds / 1000))
                                        .font(.caption.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                }
                                .contentShape(Rectangle())
                                .padding(.vertical, 8)
                            }
                            .buttonStyle(.plain)

                            if bookmark.id != visibleBookBookmarks.last?.id {
                                Divider()
                            }
                        }
                    }
                }

                HStack(spacing: 10) {
                    NowPlayingBookmarkControl(scrubPreviewSeconds: $scrubPreviewSeconds) { isBookmarked, canBookmark in
                        if canBookmark {
                            Button {
                                Task {
                                    await app.toggleCurrentPlayheadBookmark(
                                        for: app.defaultListeningBook,
                                        positionSeconds: scrubPreviewSeconds
                                    )
                                }
                            } label: {
                                Label(
                                    !isBookmarked ? "Add Bookmark" : "Remove Bookmark",
                                    systemImage: !isBookmarked ? "bookmark" : "bookmark.fill"
                                )
                            }
                        }
                    }

                    Spacer(minLength: 0)

                    Button {
                        showBookmarks()
                    } label: {
                        Label(bookBookmarks.count > visibleBookBookmarks.count ? "View All" : "Manage", systemImage: "bookmark.square")
                    }
                    .disabled(activeBookmarkBookID == nil)
                }
                .font(.subheadline.weight(.semibold))
            }
        }
    }

    private func chapterTitle(for bookmark: Bookmark) -> String {
        app.defaultListeningBook?.chapters.first { $0.id == bookmark.chapterID }?.displayTitle ?? "Bookmark"
    }

    private func chapterNumber(for bookmark: Bookmark) -> Int {
        app.defaultListeningBook?.chapters.first { $0.id == bookmark.chapterID }?.number ?? Int.max
    }

    private func openBookmark(_ bookmark: Bookmark) {
        guard let book = app.defaultListeningBook,
              let chapter = book.chapters.first(where: { $0.id == bookmark.chapterID })
        else { return }
        app.play(book, chapter: chapter, startAt: Double(bookmark.positionMilliseconds) / 1000)
    }

    private func compactCircleButton(
        _ systemImage: String,
        label: String,
        isSelected: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            GlassCircleIcon(
                systemImage: systemImage,
                size: 44,
                symbolSize: 17,
                material: .clear,
                foreground: nowPlayingPrimaryText,
                isSelected: isSelected,
                selectedTint: nowPlayingPrimaryText
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var queueSection: some View {
        PlayerSection(title: "Up Next") {
            if app.queue.isEmpty {
                Text("Books added to your queue will appear here.")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(app.queue) { entry in
                    if let book = app.book(for: entry) {
                        Button {
                            app.play(book, chapter: book.chapters.first { $0.id == entry.chapterID } ?? book.chapters.first)
                        } label: {
                            HStack {
                                Text(book.title)
                                    .lineLimit(1)
                                Spacer()
                                Text(book.author)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                    } else {
                        Label("Queued book unavailable", systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

}

private struct NowPlayingChapterRow: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    var book: AudioBook
    var chapter: AudioBookChapter

    private var isCurrent: Bool {
        app.playback.currentBook?.id == book.id && app.playback.currentChapter?.id == chapter.id
    }

    private var isPlaying: Bool {
        isCurrent && app.playback.isPlaying
    }

    var body: some View {
        Button {
            if isCurrent {
                app.playback.toggle()
            } else {
                app.play(book, chapter: chapter)
            }
        } label: {
            HStack(spacing: 14) {
                Label(isPlaying ? "Pause" : "Play", systemImage: isPlaying ? "pause.fill" : "play.fill")
                    .labelStyle(.iconOnly)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isCurrent ? accent : .primary)
                    .frame(width: 42, height: 42)
                    .background(isCurrent ? accent.opacity(0.22) : Color.primary.opacity(0.08), in: Circle())
                VStack(alignment: .leading) {
                    Text(chapter.displayTitle)
                        .lineLimit(2)
                    Text(book.author)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    if let reader = chapter.reader, !reader.isEmpty {
                        Text("Read by \(reader)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                if chapter.durationSeconds > 0 {
                    Text(formatDuration(seconds: chapter.durationSeconds))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(isCurrent ? (isPlaying ? "Playing" : "Paused") : "Not playing")
        .accessibilityIdentifier("now-playing-chapter-\(chapter.id)")
    }
}

// Keep the one-second playback clock in the controls that actually display it.
// Cover art, titles, chapter lists and presentation geometry stay outside it.
private struct NowPlayingProgressStrip: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var playerAccent
    @Binding var scrubPreviewSeconds: Double?

    var progress: Binding<Double> {
        Binding(
            get: {
                if app.playback.currentBook == nil,
                   let book = app.defaultListeningBook,
                   let progress = app.progress(for: book) {
                    return progress.positionSeconds
                }
                return app.playback.currentTimeSeconds
            },
            set: {
                if app.playback.currentBook == nil {
                    app.playDefaultBook()
                }
                app.playback.seek(to: $0)
            }
        )
    }

    var body: some View {
        let displayedSeconds = scrubPreviewSeconds ?? progress.wrappedValue
        let chapterRemainingSeconds = max(0, Int(displayedDurationSeconds - displayedSeconds))
        let bookRemainingText = bookRemainingText(displayedChapterSeconds: displayedSeconds)

        return VStack(spacing: 8) {
            if displayedDurationIsKnown {
                BookmarkProgressSlider(
                    value: progress,
                    duration: displayedDurationSeconds,
                    bookmarks: currentChapterBookmarks,
                    accent: playerAccent,
                    onPreviewChange: { scrubPreviewSeconds = $0 }
                )
            } else {
                UnknownDurationProgressTrack(accent: playerAccent)
            }
            HStack {
                Text(formatDuration(seconds: Int(displayedSeconds)))
                    .frame(maxWidth: .infinity, alignment: .leading)

                if let bookRemainingText {
                    Text(bookRemainingText)
                        .font(.caption.weight(.medium))
                        .foregroundStyle(.white.opacity(0.9))
                        .lineLimit(1)
                        .minimumScaleFactor(0.76)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .accessibilityLabel(bookRemainingText)
                } else {
                    Spacer()
                        .frame(maxWidth: .infinity)
                }

                if displayedDurationIsKnown {
                    Text("-\(formatDuration(seconds: chapterRemainingSeconds))")
                        .frame(maxWidth: .infinity, alignment: .trailing)
                } else {
                    Text("--:--")
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .accessibilityLabel("Time remaining is being calculated")
                }
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(.white.opacity(0.86))
        }
        .padding(.top, 2)
    }

    private var currentChapterBookmarks: [Bookmark] {
        guard let bookID = app.playback.currentBook?.id,
              let chapterID = app.playback.currentChapter?.id
        else { return [] }
        return app.bookmarks
            .filter { $0.bookID == bookID && $0.chapterID == chapterID }
            .sorted { $0.positionMilliseconds < $1.positionMilliseconds }
    }

    private var displayedDurationSeconds: Double {
        if app.playback.currentBook == nil,
           let book = app.defaultListeningBook,
           let progress = app.progress(for: book) {
            return max(progress.durationSeconds, 0)
        }
        return max(app.playback.durationSeconds, 0)
    }

    private var displayedDurationIsKnown: Bool {
        if app.playback.currentBook == nil,
           let book = app.defaultListeningBook,
           let progress = app.progress(for: book) {
            return progress.durationSeconds > 0
        }
        return app.playback.isDurationKnown
    }

    private func bookRemainingText(displayedChapterSeconds: Double) -> String? {
        guard displayedDurationIsKnown else { return nil }
        guard let book = app.playback.currentBook ?? app.defaultListeningBook else { return nil }
        guard let chapter = currentProgressChapter(in: book) else { return nil }
        guard let currentIndex = book.chapters.firstIndex(where: { $0.id == chapter.id }) else { return nil }

        let currentChapterRemaining = max(0, displayedDurationSeconds - displayedChapterSeconds)
        let laterChapters = book.chapters.dropFirst(currentIndex + 1)
        guard laterChapters.allSatisfy({ $0.durationSeconds > 0 }) else { return nil }
        let laterChaptersRemaining = laterChapters.reduce(0) { $0 + $1.durationSeconds }
        let remainingSeconds = Int((currentChapterRemaining + Double(laterChaptersRemaining)).rounded())

        return formatBookRemaining(seconds: remainingSeconds)
    }

    private func currentProgressChapter(in book: AudioBook) -> AudioBookChapter? {
        if let chapter = app.playback.currentChapter {
            return chapter
        }
        if let progress = app.progress(for: book) {
            return book.chapters.first { $0.id == progress.chapterID }
        }
        return book.chapters.first
    }

    private func formatBookRemaining(seconds: Int) -> String {
        let clampedSeconds = max(seconds, 0)
        guard clampedSeconds >= 60 else {
            return clampedSeconds == 0 ? "0 minutes left" : "Less than 1 minute left"
        }

        let totalMinutes = Int((Double(clampedSeconds) / 60).rounded())
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            let hourText = hours == 1 ? "1 hour" : "\(hours) hours"
            if minutes == 0 {
                return "\(hourText) left"
            }
            let minuteText = minutes == 1 ? "1 minute" : "\(minutes) minutes"
            return "\(hourText), \(minuteText) left"
        }

        return totalMinutes == 1 ? "1 minute left" : "\(totalMinutes) minutes left"
    }

}

private struct NowPlayingTransportControls: View {
    @Environment(AppModel.self) private var app
    var width: CGFloat

    var body: some View {
        HStack(spacing: 8) {
            chapterSkipButton(
                systemImage: "backward.end.fill",
                label: "Restart or previous chapter",
                delta: -1
            )

            immersiveTransportButton(
                "gobackward.10",
                symbolSize: 32,
                hitSize: 54,
                label: "Rewind 10 seconds"
            ) {
                app.playback.seek(by: -PlaybackController.seekIntervalSeconds)
            }

            Button {
                if app.playback.currentBook == nil {
                    app.playDefaultBook()
                } else {
                    app.playback.toggle()
                }
            } label: {
                // Seeking can buffer without changing the user's playback intent.
                // Keep this control stable; the track status reports lasting buffering.
                Image(systemName: app.playback.isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: 48, weight: .regular))
                .contentTransition(.symbolEffect(.replace))
                .foregroundStyle(.white)
                .frame(width: 76, height: 76)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(app.playback.isPlaying ? "Pause" : "Play")
            .accessibilityValue(app.playback.showsBufferingText ? "Buffering" : "")
            .accessibilityIdentifier("now-playing-play-pause")

            immersiveTransportButton(
                "goforward.10",
                symbolSize: 32,
                hitSize: 54,
                label: "Forward 10 seconds"
            ) {
                app.playback.seek(by: PlaybackController.seekIntervalSeconds)
            }

            chapterSkipButton(
                systemImage: "forward.end.fill",
                label: "Next chapter",
                delta: 1
            )
        }
        .frame(width: width)
    }

    private func chapterSkipButton(
        systemImage: String,
        label: String,
        delta: Int
    ) -> some View {
        let isEnabled = canSkipChapter(by: delta)

        return Button {
            if delta < 0 {
                app.playback.restartOrSkipToPreviousChapter()
            } else {
                app.playback.skipChapter(by: delta)
            }
        } label: {
            Image(systemName: systemImage)
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.3)
        .accessibilityLabel(label)
    }

    private func canSkipChapter(by delta: Int) -> Bool {
        guard let book = app.playback.currentBook,
              let chapter = app.playback.currentChapter,
              let currentIndex = book.chapters.firstIndex(where: { $0.id == chapter.id })
        else { return false }

        if delta < 0 {
            return app.playback.currentTimeSeconds > 0.25 || currentIndex > 0
        }

        return book.chapters.indices.contains(currentIndex + delta)
    }

    private func immersiveTransportButton(
        _ systemImage: String,
        symbolSize: CGFloat,
        hitSize: CGFloat,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: symbolSize, weight: .regular))
                .foregroundStyle(.white)
                .frame(width: hitSize, height: hitSize)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

private struct NowPlayingBookmarkControl<Content: View>: View {
    @Environment(AppModel.self) private var app
    @Binding var scrubPreviewSeconds: Double?
    @ViewBuilder var content: (_ isBookmarked: Bool, _ canBookmark: Bool) -> Content

    var body: some View {
        let book = app.defaultListeningBook
        let isBookmarked = app.currentPlayheadBookmark(for: book, positionSeconds: scrubPreviewSeconds) != nil
        let canBookmark = app.canBookmarkCurrentPlayhead(for: book)
        content(isBookmarked, canBookmark)
    }
}

struct PlaybackSpeedSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Narration Speed") {
                    ForEach(PlaybackSpeedOptions.presets, id: \.self) { speed in
                        Button {
                            app.setPlaybackSpeed(speed)
                            dismiss()
                        } label: {
                            SelectablePlaybackOptionRow(
                                title: PlaybackSpeedOptions.label(for: speed),
                                subtitle: speed == 1.0 ? "Normal speed" : "Adjust narration pace",
                                systemImage: "speedometer",
                                isSelected: abs(Double(app.playback.playbackSpeed) - speed) < 0.001
                            )
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
            .glassPageBackground()
            .navigationTitle("Narration Speed")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct PlaybackSleepTimerSheet: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Sleep Timer") {
                    ForEach(PlaybackSleepTimerPreset.presets) { preset in
                        Button {
                            app.setSleepTimer(duration: preset.duration)
                            dismiss()
                        } label: {
                            SelectablePlaybackOptionRow(
                                title: preset.label,
                                subtitle: "Pause after \(preset.label.lowercased())",
                                systemImage: "timer",
                                isSelected: app.sleepTimerState.selectedDuration == preset.duration
                            )
                        }
                        .foregroundStyle(.primary)
                    }

                    Button {
                        app.setSleepTimerEndOfChapter()
                        dismiss()
                    } label: {
                        SelectablePlaybackOptionRow(
                            title: "End of chapter",
                            subtitle: "Pause when this chapter finishes",
                            systemImage: "book.pages",
                            isSelected: app.sleepTimerState == .endOfChapter
                        )
                    }
                    .foregroundStyle(.primary)

                    Button {
                        app.cancelSleepTimer()
                        dismiss()
                    } label: {
                        SelectablePlaybackOptionRow(
                            title: "Off",
                            subtitle: "Keep playing until you stop it",
                            systemImage: "xmark.circle",
                            isSelected: !app.sleepTimerState.isActive
                        )
                    }
                    .foregroundStyle(.primary)
                }
            }
            .glassPageBackground()
            .navigationTitle("Sleep Timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

private struct SelectablePlaybackOptionRow: View {
    var title: String
    var subtitle: String
    var systemImage: String
    var isSelected: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .frame(width: 24)
                .foregroundStyle(.secondary)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.body.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer(minLength: 8)

            if isSelected {
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.primary)
            }
        }
        .contentShape(Rectangle())
        .padding(.vertical, 4)
    }
}

struct NowPlayingBookmarksDetailView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dismiss) private var dismiss
    var book: AudioBook?

    private var bookmarks: [Bookmark] {
        guard let bookID = book?.id else { return [] }
        return app.bookmarks
            .filter { $0.bookID == bookID }
            .sorted { $0.positionMilliseconds < $1.positionMilliseconds }
    }

    var body: some View {
        NavigationStack {
            List {
                if let book {
                    Section {
                        HStack(spacing: 14) {
                            BookCoverView(book: book, width: 56, height: 76, cornerRadius: 10, preferFullImage: true)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(book.title)
                                    .font(.headline)
                                    .lineLimit(2)
                                Text(book.author)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    if bookmarks.isEmpty {
                        ContentUnavailableView("No Bookmarks", systemImage: "bookmark")
                    } else {
                        ForEach(bookmarks) { bookmark in
                            Button {
                                open(bookmark)
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "bookmark.fill")
                                        .foregroundStyle(.tint)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(chapterTitle(for: bookmark))
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                        Text(bookmark.note.isEmpty ? bookmark.createdAt.formatted(date: .abbreviated, time: .shortened) : bookmark.note)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Text(formatDuration(seconds: bookmark.positionMilliseconds / 1000))
                                        .font(.caption.monospacedDigit())
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .glassPageBackground(book: book)
            .navigationTitle("Bookmarks")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .task(id: book?.id) {
            await app.reloadBookmarks(for: book?.id)
        }
    }

    private func chapterTitle(for bookmark: Bookmark) -> String {
        book?.chapters.first { $0.id == bookmark.chapterID }?.displayTitle ?? "Bookmark"
    }

    private func open(_ bookmark: Bookmark) {
        if let book, let chapter = book.chapters.first(where: { $0.id == bookmark.chapterID }) {
            app.play(book, chapter: chapter, startAt: Double(bookmark.positionMilliseconds) / 1000)
        }
        dismiss()
    }

}

private enum NowPlayingSectionID: Hashable {
    case top
    case details
    case bookmarks
    case chapters
    case queue
}

private struct UnknownDurationProgressTrack: View {
    var accent: Color

    var body: some View {
        ProgressView()
            .progressViewStyle(.linear)
            .tint(accent)
            .frame(height: 34)
            .accessibilityLabel("Playback progress")
            .accessibilityValue("Duration is being calculated")
            .accessibilityHint("Seeking becomes available when the chapter duration is known")
    }
}

private struct BookmarkProgressSlider: View {
    @Binding var value: Double
    var duration: Double
    var bookmarks: [Bookmark]
    var accent: Color
    var onPreviewChange: (Double?) -> Void = { _ in }

    private let trackHeight: CGFloat = 5
    private let tickFraction = 0.05

    @State private var isScrubbing = false
    @State private var scrubValue = 0.0
    @State private var preScrubValue: Double?
    @State private var lastSnapTarget: ScrubSnapTarget?
    @State private var lastTickFraction: Double?
    @State private var hoveredBookmarkID: Bookmark.ID?

    var body: some View {
        GeometryReader { geometry in
            let width = max(1, geometry.size.width)
            let centerY = geometry.size.height / 2
            let displayedValue = isScrubbing ? scrubValue : value
            let progress = CGFloat(normalized(displayedValue / max(duration, 1)))

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(.primary.opacity(0.16))
                    .frame(width: width, height: trackHeight)
                    .position(x: width / 2, y: centerY)

                Capsule()
                    .fill(.primary.opacity(isScrubbing ? 0.68 : 0.52))
                    .frame(width: max(trackHeight, width * progress), height: trackHeight)
                    .position(x: max(trackHeight, width * progress) / 2, y: centerY)

                ForEach(bookmarks) { bookmark in
                    let bookmarkSeconds = Double(bookmark.positionMilliseconds) / 1000
                    let tickProgress = CGFloat(normalized(bookmarkSeconds / max(duration, 1)))
                    let isActiveBookmark = hoveredBookmarkID == bookmark.id || abs(bookmarkSeconds - displayedValue) < 0.5
                    Capsule()
                        .fill(isActiveBookmark ? accent.opacity(0.95) : .primary.opacity(0.58))
                        .frame(width: isActiveBookmark ? 3 : 2, height: isActiveBookmark ? 13 : 9)
                        .shadow(color: .black.opacity(isActiveBookmark ? 0.14 : 0.08), radius: isActiveBookmark ? 3 : 1, y: 1)
                        .position(x: width * tickProgress, y: centerY)
                        .onHover { isHovering in
                            guard !isScrubbing else { return }
                            if isHovering {
                                hoveredBookmarkID = bookmark.id
                                onPreviewChange(bookmarkSeconds)
                            } else if hoveredBookmarkID == bookmark.id {
                                hoveredBookmarkID = nil
                                onPreviewChange(nil)
                            }
                        }
                        .accessibilityHidden(true)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        updateScrub(locationX: gesture.location.x, width: width)
                    }
                    .onEnded { _ in
                        commitScrub()
                    }
            )
        }
        .frame(height: 34)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Playback progress")
        .accessibilityValue("\(formatDuration(seconds: Int(isScrubbing ? scrubValue : value))) of \(formatDuration(seconds: Int(duration)))")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment:
                value = min(duration, value + PlaybackController.seekIntervalSeconds)
                playTickHaptic()
            case .decrement:
                value = max(0, value - PlaybackController.seekIntervalSeconds)
                playTickHaptic()
            @unknown default:
                break
            }
        }
    }

    private func updateScrub(locationX: CGFloat, width: CGFloat) {
        if !isScrubbing {
            isScrubbing = true
            scrubValue = value
            preScrubValue = value
            lastSnapTarget = nil
            lastTickFraction = normalized(value / max(duration, 1))
            hoveredBookmarkID = nil
        }

        let position = min(max(0, locationX), width)
        let rawValue = Double(position / width) * max(duration, 1)
        let result = snapScrubPosition(rawValue)
        scrubValue = result.position
        onPreviewChange(result.position)

        if let target = result.target, target != lastSnapTarget {
            playSnapHaptic()
            lastTickFraction = normalized(result.position / max(duration, 1))
        } else if result.target == nil {
            playQuantizedTickIfNeeded(position: result.position)
        }
        lastSnapTarget = result.target
    }

    private func commitScrub() {
        guard isScrubbing else { return }
        value = scrubValue
        isScrubbing = false
        preScrubValue = nil
        lastSnapTarget = nil
        lastTickFraction = nil
        hoveredBookmarkID = nil
        onPreviewChange(nil)
    }

    private func snapScrubPosition(_ rawValue: Double) -> ScrubSnapResult {
        let safeDuration = max(duration, 0)
        let clamped = min(max(rawValue, 0), safeDuration)
        guard safeDuration > 0 else {
            return ScrubSnapResult(position: 0, target: nil)
        }

        var targets = bookmarkPositions.map {
            ScrubSnapTarget(position: min(max($0, 0), safeDuration), kind: .bookmark)
        }
        if let preScrubValue {
            targets.append(
                ScrubSnapTarget(
                    position: min(max(preScrubValue, 0), safeDuration),
                    kind: .preScrubPosition
                )
            )
        }
        guard !targets.isEmpty else {
            return ScrubSnapResult(position: clamped, target: nil)
        }

        let threshold = snapThreshold(duration: safeDuration)
        let nearest = targets.min { lhs, rhs in
            let lhsDistance = abs(lhs.position - clamped)
            let rhsDistance = abs(rhs.position - clamped)
            if lhsDistance != rhsDistance {
                return lhsDistance < rhsDistance
            }
            if lhs.kind != rhs.kind {
                return lhs.kind == .bookmark
            }
            return lhs.position < rhs.position
        }

        if let nearest, abs(nearest.position - clamped) <= threshold {
            return ScrubSnapResult(position: nearest.position, target: nearest)
        }
        return ScrubSnapResult(position: clamped, target: nil)
    }

    private var bookmarkPositions: [Double] {
        Array(
            Set(
                bookmarks.map { Double($0.positionMilliseconds) / 1000 }
            )
        )
        .sorted()
    }

    private func snapThreshold(duration: Double) -> Double {
        min(max(duration * 0.02, 3), 12)
    }

    private func playQuantizedTickIfNeeded(position: Double) {
        let fraction = normalized(position / max(duration, 1))
        guard let lastTickFraction else {
            self.lastTickFraction = fraction
            return
        }
        if abs(fraction - lastTickFraction) >= tickFraction {
            self.lastTickFraction = fraction
            playTickHaptic()
        }
    }

    private func playTickHaptic() {
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func playSnapHaptic() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.55)
    }

    private func normalized(_ value: Double) -> Double {
        min(max(value, 0), 1)
    }

    private struct ScrubSnapResult {
        var position: Double
        var target: ScrubSnapTarget?
    }

    private struct ScrubSnapTarget: Equatable {
        var position: Double
        var kind: ScrubSnapTargetKind
    }

    private enum ScrubSnapTargetKind {
        case bookmark
        case preScrubPosition
    }
}

private struct PlayerSection<Content: View>: View {
    var title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .contentMaterialPanel(cornerRadius: 22)
    }
}

private struct BufferingCircleIcon: View {
    @Environment(\.colorScheme) private var colorScheme

    var size: CGFloat
    var tint: Color

    var body: some View {
        ProgressView()
            .controlSize(.regular)
            .tint(LibriVoxBrand.surface(for: colorScheme))
            .frame(width: size, height: size)
            .background {
                Circle()
                    .fill(tint)
            }
            .overlay {
                Circle()
                    .stroke(.white.opacity(0.22), lineWidth: 0.8)
            }
            .shadow(color: .black.opacity(0.24), radius: 14, y: 8)
            .contentShape(Circle())
    }
}
