import LibriVoxCore
import SwiftUI
import UIKit

struct ReaderView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var book: AudioBook
    @State private var presentationID = UUID()
    @State private var activeSegment: ReadAlongSegment?
    @State private var searchText = ""
    @State private var isSearchPresented = false
    @State private var selectedSearchHitIndex = 0
    @State private var showingSearchResults = false
    @State private var showingReaderOptions = false
    @State private var lastAutoScrolledSegmentID: String?
    @State private var scrollMetrics = ReaderScrollMetrics.zero
    @State private var showingSpeedSheet = false
    @State private var showingSleepTimerSheet = false

    private var readAlongBook: ReadAlongBook? {
        guard app.readAlongBook?.bookID == book.id else { return nil }
        return app.readAlongBook
    }

    private var canSyncPlaybackText: Bool {
        readAlongBook?.supportsPlaybackSync == true
    }

    private var followSegment: ReadAlongSegment? {
        guard app.settings.readerSettings.followPlayback,
              canSyncPlaybackText
        else { return nil }
        return activeSegment
    }

    private var highlightedSegment: ReadAlongSegment? {
        guard app.settings.readerSettings.highlightCurrentText,
              canSyncPlaybackText
        else { return nil }
        return activeSegment
    }

    private var searchHits: [ReadAlongSearchHit] {
        readAlongBook?.search(searchText) ?? []
    }

    private var selectedSearchHit: ReadAlongSearchHit? {
        guard searchHits.indices.contains(selectedSearchHitIndex) else { return nil }
        return searchHits[selectedSearchHitIndex]
    }

    private var isReaderPlaybackActive: Bool {
        app.playback.currentBook?.id == book.id
    }

    private var readerPlaybackSpeedLabel: String {
        PlaybackSpeedOptions.label(for: Double(app.playback.playbackSpeed))
    }

    private var readerSleepTimerLabel: String {
        switch app.sleepTimerState {
        case .off:
            return "Sleep"
        case .expired:
            return "Expired"
        case .endOfChapter:
            return "Chapter"
        case .running:
            guard let remainingSeconds = app.sleepTimerRemainingSeconds() else {
                return "Sleep"
            }
            if remainingSeconds < 60 {
                return "<1 min"
            }
            let minutes = Int((Double(remainingSeconds) / 60).rounded(.up))
            return minutes >= 60 ? "1 hr" : "\(minutes) min"
        }
    }

    var body: some View {
        @Bindable var app = app

        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 24) {
                    header

                    if app.isReaderLoading, readAlongBook == nil {
                        loadingView
                    } else if let readAlongBook, readAlongBook.hasSourceText {
                        ForEach(readAlongBook.chapters) { chapter in
                            chapterSection(chapter, proxy: proxy)
                        }
                    } else {
                        unavailableView
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                .padding(.bottom, 36)
                .textSelection(.enabled)
            }
            .readerPaging(app.settings.readerSettings.layoutMode == .paged)
            .scrollEdgeEffectStyle(.soft, for: .top)
            .onScrollGeometryChange(for: ReaderScrollMetrics.self) { geometry in
                ReaderScrollMetrics(
                    offset: max(0, geometry.contentOffset.y + geometry.contentInsets.top),
                    contentHeight: geometry.contentSize.height,
                    viewportHeight: geometry.containerSize.height
                )
            } action: { _, metrics in
                scrollMetrics = metrics
            }
            .searchable(
                text: $searchText,
                isPresented: $isSearchPresented,
                placement: .toolbar,
                prompt: "Find in book"
            )
            .glassPageBackground()
            .navigationTitle("Read")
            .navigationBarTitleDisplayMode(.inline)
            .readerTabBarCompatibility()
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("Find in Book", systemImage: "magnifyingglass") {
                        isSearchPresented = true
                    }
                    .accessibilityIdentifier("Reader search")

                    if let readAlongBook, readAlongBook.chapters.count > 1 {
                        Menu("Contents", systemImage: "list.bullet") {
                            ForEach(readAlongBook.chapters) { chapter in
                                Button(chapter.title) {
                                    scrollTo(chapter.id, anchor: .top, proxy: proxy, animated: true)
                                }
                            }
                        }
                        .accessibilityIdentifier("Reader contents")
                    }

                    Button("Reader Options", systemImage: "textformat.size") {
                        showingReaderOptions = true
                    }
                    .accessibilityIdentifier("Reader options")
                }
            }
            .background {
                ReaderPlaybackTrackingView(book: book, activeSegment: $activeSegment)
            }
            .onAppear {
                app.visibleReaderIDs.insert(presentationID)
                updateEstimatedTiming()
                scrollToPlayback(proxy: proxy, animated: false, force: true, delay: true)
            }
            .onDisappear { app.visibleReaderIDs.remove(presentationID) }
            .onChange(of: app.playback.durationSeconds) { _, _ in updateEstimatedTiming() }
            .onChange(of: app.playback.currentChapter?.id) { _, _ in updateEstimatedTiming() }
            .task(id: book.id) {
                await app.loadReadAlong(for: book)
                updateEstimatedTiming()
                scrollToPlayback(proxy: proxy, animated: false, force: true, delay: true)
            }
            .refreshable {
                app.readAlongBook = nil
                await app.loadReadAlong(for: book)
                updateEstimatedTiming()
                scrollToPlayback(proxy: proxy, animated: false, force: true, delay: true)
            }
            .onChange(of: readAlongBook?.id) { _, _ in
                scrollToPlayback(proxy: proxy, animated: false, force: true, delay: true)
            }
            .onChange(of: followSegment?.id) { _, _ in
                scrollToPlayback(proxy: proxy, animated: true)
            }
            .onChange(of: app.settings.readerSettings.followPlayback) { _, isEnabled in
                guard isEnabled else { return }
                scrollToPlayback(proxy: proxy, animated: true, force: true, delay: true)
            }
            .onChange(of: app.settings.readerSettings.highlightMode) { _, _ in
                lastAutoScrolledSegmentID = nil
                scrollToPlayback(proxy: proxy, animated: true, force: true, delay: true)
            }
            .onChange(of: searchText) { _, newValue in
                selectedSearchHitIndex = 0
                guard !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                      let first = searchHits.first
                else { return }
                scrollTo(first.segmentID, anchor: .center, proxy: proxy, animated: true)
            }
            .safeAreaBar(edge: .bottom) {
                readerControls(app: app, proxy: proxy)
            }
            .sheet(isPresented: $showingSearchResults) {
                ReaderSearchResultsView(
                    query: searchText,
                    hits: searchHits,
                    selectedIndex: selectedSearchHitIndex
                ) { index in
                    selectSearchHit(index, proxy: proxy)
                    showingSearchResults = false
                }
            }
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
        .sheet(isPresented: $showingReaderOptions) {
            ReaderOptionsSheet(
                settings: $app.settings.readerSettings,
                canSyncPlaybackText: canSyncPlaybackText,
                syncQuality: readAlongBook?.syncQuality,
                isPlaybackActive: isReaderPlaybackActive,
                speedLabel: readerPlaybackSpeedLabel,
                sleepLabel: readerSleepTimerLabel,
                onShowSpeed: {
                    showingReaderOptions = false
                    Task { @MainActor in
                        await Task.yield()
                        showingSpeedSheet = true
                    }
                },
                onShowSleepTimer: {
                    showingReaderOptions = false
                    Task { @MainActor in
                        await Task.yield()
                        showingSleepTimerSheet = true
                    }
                }
            )
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            CoverArtworkButton(book: book, accessibilityIdentifier: "reader-cover") {
                BookCoverView(book: book, width: 70, height: 92, cornerRadius: 14, preferFullImage: true)
            }
            .shadow(color: .black.opacity(0.18), radius: 12, y: 8)

            VStack(alignment: .leading, spacing: 6) {
                Text(book.title)
                    .font(.title2.bold())
                    .lineLimit(3)
                Text(book.author)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                if let readAlongBook {
                    Label(readerSyncLabel(readAlongBook), systemImage: "text.book.closed")
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(readerSyncLabel(readAlongBook))
                        .accessibilityIdentifier("reader-sync-quality")
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.bottom, 8)
    }

    private var loadingView: some View {
        HStack(spacing: 12) {
            ProgressView()
            Text("Loading reader text")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private var unavailableView: some View {
        ContentUnavailableView(
            "Reader Text Unavailable",
            systemImage: "text.book.closed",
            description: Text("This source has not published reader text for this audiobook yet.")
        )
        .padding(.top, 40)
    }

    @ViewBuilder
    private func chapterSection(_ chapter: ReadAlongChapter, proxy: ScrollViewProxy) -> some View {
        let isCurrentChapter = isReaderPlaybackActive && app.playback.currentChapter?.id == chapter.id

        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 4) {
                Text("CHAPTER \(chapter.number)")
                    .font(.caption.smallCaps().weight(.semibold))
                    .tracking(1.2)
                    .foregroundStyle(.secondary)
                Text(chapter.title)
                    .font(.title2.bold())
            }
            Spacer(minLength: 12)
            Button {
                if let sourceChapter = book.chapters.first(where: { $0.id == chapter.id }) {
                    let shouldScrollToChapterStart = app.playback.currentBook?.id != book.id ||
                        app.playback.currentChapter?.id != sourceChapter.id
                    playInReader(chapter: sourceChapter, proxy: proxy)
                    if shouldScrollToChapterStart,
                       let firstSegmentID = chapter.segments.first?.id {
                        scrollTo(firstSegmentID, anchor: .top, proxy: proxy, animated: true)
                    }
                }
            } label: {
                Image(systemName: isCurrentChapter && app.playback.isPlaying ? "pause.fill" : "play.fill")
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .accessibilityLabel(isCurrentChapter && app.playback.isPlaying ? "Pause \(chapter.title)" : "Play \(chapter.title)")
        }
        .padding(.top, 8)
        .id(chapter.id)

        ForEach(chapter.segments) { segment in
            segmentText(segment, proxy: proxy)
                .background {
                    if isHighlighted(segment, in: chapter) {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(accent.opacity(0.18))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(accent.opacity(0.22), lineWidth: 1)
                            }
                    }
                }
                .id(segment.id)
        }

        Divider()
            .padding(.vertical, 10)
    }

    private func segmentText(_ segment: ReadAlongSegment, proxy: ScrollViewProxy) -> some View {
        ReaderSegmentText(
            segment: segment,
            isCurrent: highlightedSegment?.id == segment.id,
            searchRange: selectedSearchHit?.segmentID == segment.id ? selectedSearchHit?.matchRange?.nsRange : nil
        ) { selectedRange in
            readFromSegment(segment, selectedRange: selectedRange, proxy: proxy)
        }
    }

    private func updateEstimatedTiming() {
        guard isReaderPlaybackActive, let document = readAlongBook else { return }
        let updated = document.applyingPlaybackDuration(
            app.playback.durationSeconds, chapterID: app.playback.currentChapter?.id
        )
        if updated != document { app.readAlongBook = updated }
    }

    private func readerSyncLabel(_ document: ReadAlongBook) -> String {
        guard document.hasSourceText else { return "Text unavailable" }
        switch document.syncQuality {
        case .timed: return "Source sync"
        case .estimated: return "Estimated sync"
        case .staticText: return "Source text"
        }
    }

    private func playInReader(
        chapter: AudioBookChapter? = nil,
        proxy: ScrollViewProxy,
        togglesActivePlayback: Bool = true
    ) {
        if isReaderPlaybackActive {
            if let chapter, app.playback.currentChapter?.id != chapter.id {
                app.play(book, chapter: chapter, presentNowPlaying: false)
            } else if togglesActivePlayback, app.playback.isPlaying {
                app.playback.pause()
            } else {
                app.playback.resume()
            }
        } else {
            app.play(book, chapter: chapter, presentNowPlaying: false)
        }
        app.showingNowPlaying = false
        scrollToPlayback(proxy: proxy, animated: true, force: true, delay: true)
    }

    private func readFromSegment(_ segment: ReadAlongSegment, selectedRange: NSRange, proxy: ScrollViewProxy) {
        guard let chapter = book.chapters.first(where: { $0.id == segment.chapterID }),
              let startMilliseconds = startMilliseconds(for: segment, selectedRange: selectedRange)
        else { return }

        lastAutoScrolledSegmentID = nil
        if isReaderPlaybackActive, app.playback.currentChapter?.id == chapter.id {
            app.playback.seek(to: Double(startMilliseconds) / 1000.0)
            if !app.playback.isPlaying {
                app.playback.resume()
            }
        } else {
            app.play(
                book,
                chapter: chapter,
                startAt: Double(startMilliseconds) / 1000.0,
                presentNowPlaying: false
            )
        }
        app.showingNowPlaying = false
        scrollTo(segment.id, anchor: .center, proxy: proxy, animated: true)
    }

    private func startMilliseconds(for segment: ReadAlongSegment, selectedRange: NSRange) -> Int? {
        guard let segmentStart = segment.startMilliseconds else { return nil }
        guard let segmentEnd = segment.endMilliseconds,
              segmentEnd > segmentStart,
              selectedRange.location != NSNotFound,
              selectedRange.length > 0
        else { return segmentStart }

        let textLength = max(1, (segment.text as NSString).length)
        let clampedLocation = min(max(0, selectedRange.location), textLength)
        let clampedLength = min(max(0, selectedRange.length), max(0, textLength - clampedLocation))
        let clampedRange = ReadAlongTextRange(location: clampedLocation, length: clampedLength)
        if let sentenceRange = segment.proportionalSentenceRanges().first(where: { $0.textRange.intersects(clampedRange) }) {
            return sentenceRange.clipBeginMilliseconds
        }

        let fraction = Double(clampedRange.location) / Double(textLength)
        return segmentStart + Int((Double(segmentEnd - segmentStart) * fraction).rounded())
    }

    private func scrollToPlayback(
        proxy: ScrollViewProxy,
        animated: Bool,
        force: Bool = false,
        delay: Bool = false
    ) {
        guard app.settings.readerSettings.followPlayback,
              let segmentID = followSegment?.id
        else { return }
        guard force || lastAutoScrolledSegmentID != segmentID else { return }
        lastAutoScrolledSegmentID = segmentID

        if delay {
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 120_000_000)
                guard app.settings.readerSettings.followPlayback,
                      followSegment?.id == segmentID
                else { return }
                scrollTo(segmentID, anchor: .center, proxy: proxy, animated: animated, duration: 0.38)
            }
        } else {
            scrollTo(segmentID, anchor: .center, proxy: proxy, animated: animated, duration: 0.38)
        }
    }

    private func scrollTo<ID: Hashable>(
        _ id: ID,
        anchor: UnitPoint,
        proxy: ScrollViewProxy,
        animated: Bool,
        duration: Double = 0.32
    ) {
        if animated, !reduceMotion {
            withAnimation(.smooth(duration: duration)) {
                proxy.scrollTo(id, anchor: anchor)
            }
        } else {
            proxy.scrollTo(id, anchor: anchor)
        }
    }

    private func readerControls(app: AppModel, proxy: ScrollViewProxy) -> some View {
        VStack(spacing: 8) {
            if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                readerSearchControls(proxy: proxy)
            }
            HStack(spacing: 10) {
                Button {
                    showingReaderOptions = true
                } label: {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(scrollMetrics.pageLabel)
                            .font(.subheadline.monospacedDigit().weight(.semibold))
                        Text(app.settings.readerSettings.layoutMode == .paged ? "Paged" : "Scrolling")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(scrollMetrics.pageLabel), reader options")

                Spacer(minLength: 4)

                readerTransportButton(
                    systemImage: "gobackward.\(Int(PlaybackController.seekIntervalSeconds))",
                    label: "Back \(Int(PlaybackController.seekIntervalSeconds)) seconds"
                ) {
                    if !isReaderPlaybackActive { app.play(book, presentNowPlaying: false) }
                    app.playback.seek(by: -PlaybackController.seekIntervalSeconds)
                }

                Button {
                    playInReader(proxy: proxy)
                } label: {
                    Group {
                        if app.playback.isBuffering, isReaderPlaybackActive {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: isReaderPlaybackActive && app.playback.isPlaying ? "pause.fill" : "play.fill")
                                .font(.headline.weight(.bold))
                        }
                    }
                    .frame(width: 44, height: 44)
                }
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .foregroundStyle(.primary)
                .accessibilityLabel(isReaderPlaybackActive && app.playback.isPlaying ? "Pause audio" : "Play audio")
                .accessibilityIdentifier("reader-play-pause")

                readerTransportButton(
                    systemImage: "goforward.\(Int(PlaybackController.seekIntervalSeconds))",
                    label: "Forward \(Int(PlaybackController.seekIntervalSeconds)) seconds"
                ) {
                    if !isReaderPlaybackActive { app.play(book, presentNowPlaying: false) }
                    app.playback.seek(by: PlaybackController.seekIntervalSeconds)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(.thinMaterial, in: Capsule())
        }
        .padding(.horizontal)
        .padding(.bottom, 6)
    }

    private func readerTransportButton(
        systemImage: String,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.headline.weight(.semibold))
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.glass)
        .buttonBorderShape(.circle)
        .accessibilityLabel(label)
    }

    private func readerSearchControls(proxy: ScrollViewProxy) -> some View {
        HStack(spacing: 10) {
            Button {
                showingSearchResults = true
            } label: {
                Text(searchHits.isEmpty ? "No matches" : "\(selectedSearchHitIndex + 1) of \(searchHits.count)")
                    .font(.subheadline.monospacedDigit().weight(.semibold))
                    .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .disabled(searchHits.isEmpty)

            Spacer(minLength: 6)

            Button("Previous Match", systemImage: "chevron.up") {
                moveSearchHit(by: -1, proxy: proxy)
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .disabled(searchHits.isEmpty)

            Button("Next Match", systemImage: "chevron.down") {
                moveSearchHit(by: 1, proxy: proxy)
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .disabled(searchHits.isEmpty)

            Button("Show All Matches", systemImage: "list.bullet") {
                showingSearchResults = true
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .disabled(searchHits.isEmpty)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.thinMaterial, in: Capsule())
    }

    private func moveSearchHit(by offset: Int, proxy: ScrollViewProxy) {
        guard !searchHits.isEmpty else { return }
        let count = searchHits.count
        selectSearchHit((selectedSearchHitIndex + offset + count) % count, proxy: proxy)
    }

    private func selectSearchHit(_ index: Int, proxy: ScrollViewProxy) {
        guard searchHits.indices.contains(index) else { return }
        selectedSearchHitIndex = index
        scrollTo(searchHits[index].segmentID, anchor: .center, proxy: proxy, animated: true)
    }

    private func isHighlighted(_ segment: ReadAlongSegment, in chapter: ReadAlongChapter) -> Bool {
        guard let highlightedSegment,
              app.settings.readerSettings.highlightCurrentText
        else { return false }
        switch app.settings.readerSettings.highlightMode {
        case .chapter:
            return highlightedSegment.chapterID == chapter.id
        case .paragraph:
            return highlightedSegment.id == segment.id
        case .sentence:
            return false
        }
    }
}

// Observe the playback clock in a leaf so the full book is rebuilt only when
// narration reaches another paragraph, not at every progress tick.
private struct ReaderPlaybackTrackingView: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    @Binding var activeSegment: ReadAlongSegment?

    var body: some View {
        let current = app.activeReadAlongSegment(for: book)
        Color.clear
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)
            .onChange(of: current, initial: true) { _, current in activeSegment = current }
    }
}

private struct ReaderSegmentText: View {
    @Environment(AppModel.self) private var app
    var segment: ReadAlongSegment
    var isCurrent: Bool
    var searchRange: NSRange?
    var onReadFromHere: (NSRange) -> Void
    @State private var sentenceRanges: [ReadAlongTimedTextRange] = []

    var body: some View {
        let style = ReaderTextStyle(text: segment.text)
        SelectableReaderText(
            text: segment.text,
            style: style,
            textScale: app.settings.readerSettings.textScale,
            highlightRange: highlightRange,
            highlightColor: searchRange == nil ? LibriVoxBrand.accentUIColor.withAlphaComponent(0.18) : UIColor.systemYellow.withAlphaComponent(0.38),
            canReadFromHere: segment.startMilliseconds != nil,
            accessibilityIdentifier: "reader-segment-\(segment.id)",
            accessibilityStatus: isCurrent ? "Current text" : "Text",
            onReadFromHere: onReadFromHere
        )
        .padding(.horizontal, style.horizontalPadding)
        .padding(.vertical, style.verticalPadding)
        .frame(maxWidth: .infinity, alignment: style.frameAlignment)
        .task(id: segment) { sentenceRanges = segment.proportionalSentenceRanges() }
    }

    private var highlightRange: NSRange? {
        if let searchRange { return searchRange }
        guard isCurrent, app.settings.readerSettings.highlightMode == .sentence else { return nil }
        let position = Int((app.playback.currentTimeSeconds * 1_000).rounded())
        return sentenceRanges.first {
            position >= $0.clipBeginMilliseconds && position < $0.clipEndMilliseconds
        }?.textRange.nsRange
    }
}

private struct ReaderScrollMetrics: Equatable {
    var offset: CGFloat
    var contentHeight: CGFloat
    var viewportHeight: CGFloat

    static let zero = ReaderScrollMetrics(offset: 0, contentHeight: 1, viewportHeight: 1)

    var pageCount: Int {
        max(1, Int(ceil(contentHeight / max(viewportHeight, 1))))
    }

    var page: Int {
        min(pageCount, max(1, Int(floor(offset / max(viewportHeight, 1))) + 1))
    }

    var pageLabel: String {
        "Page \(page) of \(pageCount)"
    }
}

private extension View {
    @ViewBuilder
    func readerTabBarCompatibility() -> some View {
        if #available(iOS 26.1, *) {
            self
        } else {
            // iOS 26.0 retains an empty accessory capsule when its content is hidden.
            // Hide the tab bar while reading; navigation Back remains available.
            toolbar(.hidden, for: .tabBar)
        }
    }

    @ViewBuilder
    func readerPaging(_ isEnabled: Bool) -> some View {
        if isEnabled {
            scrollTargetBehavior(.paging)
        } else {
            self
        }
    }
}

private struct ReaderSearchResultsView: View {
    @Environment(\.dismiss) private var dismiss
    var query: String
    var hits: [ReadAlongSearchHit]
    var selectedIndex: Int
    var onSelect: (Int) -> Void

    var body: some View {
        NavigationStack {
            Group {
                if hits.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    List(Array(hits.enumerated()), id: \.element.id) { index, hit in
                        Button {
                            onSelect(index)
                        } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                HStack {
                                    Text(hit.chapterTitle)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    if index == selectedIndex {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(.tint)
                                    }
                                }
                                Text(hit.excerpt)
                                    .font(.body)
                                    .foregroundStyle(.primary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle(hits.isEmpty ? "Find in Book" : "\(hits.count) Matches")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

private struct ReaderOptionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var settings: ReaderSettings
    var canSyncPlaybackText: Bool
    var syncQuality: ReadAlongSyncQuality?
    var isPlaybackActive: Bool
    var speedLabel: String
    var sleepLabel: String
    var onShowSpeed: () -> Void
    var onShowSleepTimer: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Reading Layout") {
                    Picker("Page style", selection: $settings.layoutMode) {
                        ForEach(ReaderLayoutMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("reader-page-style")

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: "textformat.size.smaller")
                            Slider(value: $settings.textScale, in: 0.9...1.3, step: 0.05)
                            Image(systemName: "textformat.size.larger")
                        }
                        Text("Text size: \(Int(settings.textScale * 100))%")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Read Along") {
                    Toggle("Follow narration", isOn: $settings.followPlayback)
                    Toggle("Narration highlight", isOn: $settings.highlightCurrentText)
                    Picker("Highlight range", selection: $settings.highlightMode) {
                        ForEach(ReaderHighlightMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }

                    if syncQuality == .estimated {
                        Label("Following is estimated from the text length and audio duration. The publisher has not supplied exact narration timing for this edition.", systemImage: "info.circle")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if !canSyncPlaybackText {
                        Label(
                            "This title has readable text, but its publisher did not include narration timing. Your preferences will apply automatically to books with synchronized text.",
                            systemImage: "info.circle"
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }

                Section("Narration") {
                    Button("Speed: \(speedLabel)", systemImage: "speedometer", action: onShowSpeed)
                    Button("Sleep timer: \(sleepLabel)", systemImage: "moon.zzz", action: onShowSleepTimer)
                        .disabled(!isPlaybackActive)
                }
            }
            .navigationTitle("Reader Options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

private struct SelectableReaderText: UIViewRepresentable {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var text: String
    var style: ReaderTextStyle
    var textScale: Double
    var highlightRange: NSRange?
    var highlightColor: UIColor
    var canReadFromHere: Bool
    var accessibilityIdentifier: String
    var accessibilityStatus: String
    var onReadFromHere: (NSRange) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(canReadFromHere: canReadFromHere, onReadFromHere: onReadFromHere)
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.backgroundColor = .clear
        textView.isAccessibilityElement = true
        textView.isEditable = false
        textView.isSelectable = true
        textView.isScrollEnabled = false
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.adjustsFontForContentSizeCategory = true
        textView.delegate = context.coordinator
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.setContentHuggingPriority(.defaultLow, for: .vertical)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        // Set these on the UIKit element itself so the selected passage remains
        // readable to VoiceOver and container modifiers cannot replace its identity.
        textView.accessibilityIdentifier = accessibilityIdentifier
        textView.accessibilityLabel = text
        textView.accessibilityValue = accessibilityStatus
        context.coordinator.canReadFromHere = canReadFromHere
        context.coordinator.onReadFromHere = onReadFromHere
        let highlightSignature = highlightRange.map { "\($0.location):\($0.length)" } ?? "none"
        let signature = "\(text.hashValue)-\(style.signature)-\(textScale)-\(dynamicTypeSize)-\(highlightSignature)-\(highlightColorSignature)"
        guard context.coordinator.signature != signature else { return }

        let selectedRange = textView.selectedRange
        let nextAttributedText = attributedText
        if textView.isFirstResponder, selectedRange.length > 0 {
            context.coordinator.pendingAttributedText = nextAttributedText
            context.coordinator.pendingSignature = signature
            return
        }
        context.coordinator.apply(
            nextAttributedText,
            signature: signature,
            to: textView,
            preserving: selectedRange
        )
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        let width = proposal.width ?? uiView.bounds.width
        guard width > 0 else { return nil }
        let size = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(size.height))
    }

    private var highlightColorSignature: String {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard highlightColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return highlightColor.description
        }
        return "\(red)-\(green)-\(blue)-\(alpha)"
    }

    private var attributedText: NSAttributedString {
        let paragraphStyle = NSMutableParagraphStyle()
        paragraphStyle.alignment = style.nsTextAlignment
        paragraphStyle.lineSpacing = style.lineSpacing
        paragraphStyle.paragraphSpacing = style.lineSpacing * 0.6

        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: style.uiFont(scale: textScale),
                .foregroundColor: style.uiColor,
                .paragraphStyle: paragraphStyle
            ]
        )
        if let highlightRange,
           highlightRange.location != NSNotFound,
           highlightRange.length > 0,
           NSMaxRange(highlightRange) <= attributed.length {
            attributed.addAttributes(
                [
                    .backgroundColor: highlightColor,
                    .foregroundColor: UIColor.label
                ],
                range: highlightRange
            )
        }
        return attributed
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        var signature = ""
        var pendingSignature: String?
        var pendingAttributedText: NSAttributedString?
        var canReadFromHere: Bool
        var onReadFromHere: (NSRange) -> Void

        init(canReadFromHere: Bool, onReadFromHere: @escaping (NSRange) -> Void) {
            self.canReadFromHere = canReadFromHere
            self.onReadFromHere = onReadFromHere
        }

        func apply(
            _ attributedText: NSAttributedString,
            signature: String,
            to textView: UITextView,
            preserving selectedRange: NSRange
        ) {
            textView.attributedText = attributedText
            if selectedRange.location != NSNotFound,
               NSMaxRange(selectedRange) <= attributedText.length {
                textView.selectedRange = selectedRange
            }
            self.signature = signature
            pendingAttributedText = nil
            pendingSignature = nil
        }

        func textViewDidChangeSelection(_ textView: UITextView) {
            guard textView.selectedRange.length == 0,
                  let pendingAttributedText,
                  let pendingSignature
            else { return }
            apply(
                pendingAttributedText,
                signature: pendingSignature,
                to: textView,
                preserving: textView.selectedRange
            )
        }

        func textView(
            _ textView: UITextView,
            editMenuForTextIn range: NSRange,
            suggestedActions: [UIMenuElement]
        ) -> UIMenu? {
            guard canReadFromHere,
                  range.location != NSNotFound,
                  range.length > 0
            else { return nil }

            let readAction = UIAction(
                title: "Read from Here",
                image: UIImage(systemName: "play.fill")
            ) { [weak self] _ in
                self?.onReadFromHere(range)
            }
            return UIMenu(children: [readAction] + suggestedActions)
        }
    }
}

private struct ReaderTextStyle {
    var text: String

    private var trimmed: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var kind: ReaderTextKind {
        if trimmed.hasPrefix("[") && trimmed.hasSuffix("]") {
            return .caption
        }
        if trimmed.isReaderChapterHeading {
            return .heading
        }
        return .body
    }

    func font(scale: Double) -> Font {
        switch kind {
        case .heading:
            .system(size: CGFloat(22 * scale), weight: .bold)
        case .caption:
            .system(size: CGFloat(15 * scale), weight: .medium)
        case .body:
            .system(size: CGFloat(17 * scale))
        }
    }

    var isItalic: Bool {
        kind == .caption
    }

    var foregroundStyle: Color {
        switch kind {
        case .heading:
            .primary
        case .caption:
            .secondary
        case .body:
            .primary
        }
    }

    var alignment: TextAlignment {
        switch kind {
        case .heading, .caption:
            .center
        case .body:
            .leading
        }
    }

    var frameAlignment: Alignment {
        switch kind {
        case .heading, .caption:
            .center
        case .body:
            .leading
        }
    }

    var lineSpacing: CGFloat {
        kind == .body ? 7 : 4
    }

    var horizontalPadding: CGFloat {
        kind == .body ? 10 : 14
    }

    var verticalPadding: CGFloat {
        kind == .body ? 7 : 10
    }

    var nsTextAlignment: NSTextAlignment {
        switch kind {
        case .heading, .caption:
            .center
        case .body:
            .natural
        }
    }

    var uiColor: UIColor {
        switch kind {
        case .heading, .body:
            .label
        case .caption:
            .secondaryLabel
        }
    }

    var signature: String {
        "\(kind)-\(lineSpacing)-\(alignment)-\(isItalic)"
    }

    func uiFont(scale: Double) -> UIFont {
        let baseSize: CGFloat
        let weight: UIFont.Weight
        let textStyle: UIFont.TextStyle

        switch kind {
        case .heading:
            baseSize = 22
            weight = .bold
            textStyle = .title3
        case .caption:
            baseSize = 15
            weight = .medium
            textStyle = .subheadline
        case .body:
            baseSize = 17
            weight = .regular
            textStyle = .body
        }

        var font = UIFont.systemFont(ofSize: baseSize * CGFloat(scale), weight: weight)
        if isItalic,
           let descriptor = font.fontDescriptor.withSymbolicTraits(.traitItalic) {
            font = UIFont(descriptor: descriptor, size: font.pointSize)
        }
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: font)
    }
}

private enum ReaderTextKind {
    case heading
    case caption
    case body
}

private extension String {
    var isReaderChapterHeading: Bool {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 120 else { return false }
        return trimmed.range(
            of: #"(?i)^chapter\s+(?:[ivxlcdm]+|\d+)(?:[.: -].*)?$"#,
            options: .regularExpression
        ) != nil
    }
}
