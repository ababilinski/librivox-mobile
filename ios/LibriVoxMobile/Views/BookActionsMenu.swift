import LibriVoxCore
import SwiftUI

enum BookActionsContext {
    case home
    case continueListening
    case library
    case detail
    case nowPlaying
}

struct BookActionsMenuButton<LabelContent: View>: View {
    @Environment(AppModel.self) private var app

    var book: AudioBook
    var context: BookActionsContext
    var currentBookmarkExists = false
    var canBookmarkCurrent = false
    var onShowDetails: (() -> Void)?
    var onRead: (() -> Void)?
    var onShowBookmarks: (() -> Void)?
    var onToggleCurrentBookmark: (() -> Void)?
    @ViewBuilder var label: () -> LabelContent

    @State private var showingDeleteLibraryConfirmation = false

    private var displayedBook: AudioBook {
        (app.libraryBooks + app.featuredBooks + app.searchResults + [app.playback.currentBook].compactMap { $0 })
            .first { $0.id == book.id } ?? book
    }

    private var sourceURL: URL? {
        [
            displayedBook.libriVoxURL,
            displayedBook.lit2GoURL,
            displayedBook.wolneLekturyURL,
            displayedBook.gutenbergURL,
        ]
        .compactMap { value in
            value?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        .filter { !$0.isEmpty }
        .compactMap(URL.init(string:))
        .first
    }

    private var isInLibrary: Bool {
        app.isInLibrary(displayedBook)
    }

    private var hasDownload: Bool {
        app.hasDownload(for: displayedBook)
    }

    private var hasActiveDownload: Bool {
        app.hasActiveDownload(for: displayedBook)
    }

    var body: some View {
        Menu {
            quickActions
            navigationActions
            playbackActions
            downloadActions
            libraryActions
        } label: {
            label()
        }
        .menuIndicator(.hidden)
        .confirmationDialog(
            "Delete from Library?",
            isPresented: $showingDeleteLibraryConfirmation,
            titleVisibility: .visible
        ) {
            if hasDownload {
                Button(hasActiveDownload ? "Cancel Download" : "Remove Download") {
                    Task {
                        if hasActiveDownload {
                            await app.cancelDownload(bookID: displayedBook.id)
                        } else {
                            await app.deleteDownload(bookID: displayedBook.id)
                        }
                    }
                }
                Button("Delete from Library", role: .destructive) {
                    Task {
                        await app.removeFromLibrary(displayedBook)
                        await app.deleteDownload(bookID: displayedBook.id)
                    }
                }
            } else {
                Button("Delete from Library", role: .destructive) {
                    Task {
                        await app.removeFromLibrary(displayedBook)
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            if hasDownload {
                Text("Do you want to remove this book from your downloads or delete it from your library?")
            } else {
                Text("This removes the book from your library.")
            }
        }
    }

    @ViewBuilder
    private var quickActions: some View {
        ControlGroup {
            if !isInLibrary {
                Button("Add", systemImage: "plus.circle") {
                    Task { await app.saveToLibrary(displayedBook) }
                }
            }

            Button(
                displayedBook.isFavorite ? "Undo Favorite" : "Favorite",
                systemImage: displayedBook.isFavorite ? "star.fill" : "star"
            ) {
                Task { await app.toggleFavorite(displayedBook) }
            }

            if let sourceURL {
                ShareLink(item: sourceURL) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
            }
        }
    }

    @ViewBuilder
    private var navigationActions: some View {
        Section {
            if let onShowDetails, context != .detail {
                Button("Book Details", systemImage: "info.circle") {
                    onShowDetails()
                }
            }

            if displayedBook.canReadSourceText, let onRead {
                Button("Read", systemImage: "text.book.closed") {
                    onRead()
                }
            }
        }
    }

    @ViewBuilder
    private var playbackActions: some View {
        if context == .nowPlaying {
            Section {
                if let onShowBookmarks {
                    Button("Bookmarks", systemImage: "bookmark.square") {
                        onShowBookmarks()
                    }
                }
                if let onToggleCurrentBookmark {
                    Button(
                        currentBookmarkExists ? "Remove Bookmark" : "Add Bookmark",
                        systemImage: currentBookmarkExists ? "bookmark.fill" : "bookmark"
                    ) {
                        onToggleCurrentBookmark()
                    }
                    .disabled(!canBookmarkCurrent)
                }
            }
        }
    }

    @ViewBuilder
    private var downloadActions: some View {
        Section {
            Button("Add to Queue", systemImage: "text.line.last.and.arrowtriangle.forward") {
                app.enqueue(displayedBook)
            }

            if hasActiveDownload {
                Button("Cancel Download", systemImage: "xmark.circle") {
                    Task { await app.cancelDownload(bookID: displayedBook.id) }
                }
            } else if hasDownload {
                Button("Remove Download", systemImage: "xmark.circle") {
                    Task { await app.deleteDownload(bookID: displayedBook.id) }
                }
            } else {
                Button("Download", systemImage: "arrow.down.circle") {
                    Task { await app.enqueueDownload(displayedBook) }
                }
            }
        }
    }

    @ViewBuilder
    private var libraryActions: some View {
        if isInLibrary {
            Section {
                Button("Delete from Library", systemImage: "trash", role: .destructive) {
                    showingDeleteLibraryConfirmation = true
                }
            }
        }
    }
}

struct AppleMusicBookActionsOverlay: View {
    @Environment(AppModel.self) private var app
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.libriVoxAccent) private var accent

    var book: AudioBook
    var context: BookActionsContext
    var anchorPoint: CGPoint? = nil
    var currentBookmarkExists = false
    var canBookmarkCurrent = false
    var onDismiss: () -> Void
    var onShowDetails: (() -> Void)?
    var onRead: (() -> Void)?
    var onShowBookmarks: (() -> Void)?
    var onToggleCurrentBookmark: (() -> Void)?

    @State private var showingDeleteLibraryConfirmation = false
    @State private var favoriteToast: FavoriteToast?
    @State private var favoriteToastTask: Task<Void, Never>?

    private var displayedBook: AudioBook {
        (app.libraryBooks + app.featuredBooks + app.searchResults + [app.playback.currentBook].compactMap { $0 })
            .first { $0.id == book.id } ?? book
    }

    private var sourceURL: URL? {
        [
            displayedBook.libriVoxURL,
            displayedBook.lit2GoURL,
            displayedBook.wolneLekturyURL,
            displayedBook.gutenbergURL,
        ]
        .compactMap { value in
            value?.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        .filter { !$0.isEmpty }
        .compactMap(URL.init(string:))
        .first
    }

    private var isInLibrary: Bool {
        app.isInLibrary(displayedBook)
    }

    private var hasDownload: Bool {
        app.hasDownload(for: displayedBook)
    }

    private var hasActiveDownload: Bool {
        app.hasActiveDownload(for: displayedBook)
    }

    var body: some View {
        ZStack {
            Color.black
                .opacity(colorScheme == .dark ? 0.08 : 0.05)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            GeometryReader { proxy in
                let menuWidth = max(228, min(proxy.size.width - 136, 244))
                let maxHeight = max(300, proxy.size.height - proxy.safeAreaInsets.top - proxy.safeAreaInsets.bottom - 116)
                let localAnchor = localMenuAnchor(anchor: anchorPoint, proxy: proxy)
                let position = menuPosition(
                    anchor: localAnchor,
                    menuWidth: menuWidth,
                    proxySize: proxy.size,
                    safeAreaInsets: proxy.safeAreaInsets
                )
                let transitionAnchor = menuTransitionAnchor(
                    anchor: localAnchor,
                    menuPosition: position,
                    menuWidth: menuWidth
                )

                menuPanel(maxHeight: maxHeight)
                    .frame(width: menuWidth)
                    .position(position)
                    .transition(
                        .asymmetric(
                            insertion: .opacity.combined(with: .scale(scale: 0.16, anchor: transitionAnchor)),
                            removal: .opacity.combined(with: .scale(scale: 0.88, anchor: transitionAnchor))
                        )
                    )
            }

            if let favoriteToast {
                FavoriteToastView(toast: favoriteToast)
                    .padding(.bottom, 132)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .transition(.opacity.combined(with: .scale(scale: 0.94)))
                    .allowsHitTesting(false)
            }
        }
        .transition(.opacity)
        .zIndex(50)
        .onDisappear {
            favoriteToastTask?.cancel()
            favoriteToastTask = nil
        }
        .confirmationDialog(
            "Delete from Library?",
            isPresented: $showingDeleteLibraryConfirmation,
            titleVisibility: .visible
        ) {
            if hasDownload {
                Button(hasActiveDownload ? "Cancel Download" : "Remove Download") {
                    Task {
                        if hasActiveDownload {
                            await app.cancelDownload(bookID: displayedBook.id)
                        } else {
                            await app.deleteDownload(bookID: displayedBook.id)
                        }
                        onDismiss()
                    }
                }
                Button("Delete from Library", role: .destructive) {
                    Task {
                        await app.removeFromLibrary(displayedBook)
                        await app.deleteDownload(bookID: displayedBook.id)
                        onDismiss()
                    }
                }
            } else {
                Button("Delete from Library", role: .destructive) {
                    Task {
                        await app.removeFromLibrary(displayedBook)
                        onDismiss()
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            if hasDownload {
                Text("Do you want to remove this book from your downloads or delete it from your library?")
            } else {
                Text("This removes the book from your library.")
            }
        }
    }

    private func localMenuAnchor(anchor: CGPoint?, proxy: GeometryProxy) -> CGPoint? {
        guard let anchor else { return nil }
        let frame = proxy.frame(in: .global)
        return CGPoint(x: anchor.x - frame.minX, y: anchor.y - frame.minY)
    }

    private var estimatedMenuHeight: CGFloat {
        var height: CGFloat = 78
        if context != .detail, onShowDetails != nil {
            height += 48
        }
        if displayedBook.canReadSourceText, onRead != nil {
            height += 56
        }
        if context == .nowPlaying {
            height += 10
            if onShowBookmarks != nil {
                height += 40
            }
            if onToggleCurrentBookmark != nil {
                height += 40
            }
        }
        height += 96
        if isInLibrary {
            height += 50
        }
        return min(height, 420)
    }

    private func menuPosition(
        anchor: CGPoint?,
        menuWidth: CGFloat,
        proxySize: CGSize,
        safeAreaInsets: EdgeInsets
    ) -> CGPoint {
        let horizontalMargin: CGFloat = 14
        let verticalMargin: CGFloat = 14
        let bottomClearance = safeAreaInsets.bottom + 96
        let height = estimatedMenuHeight
        let minX = horizontalMargin + menuWidth / 2
        let maxX = max(minX, proxySize.width - horizontalMargin - menuWidth / 2)
        let fallbackMinY = safeAreaInsets.top + verticalMargin + height / 2
        let maxY = max(fallbackMinY, proxySize.height - bottomClearance - height / 2)

        guard let anchor else {
            return CGPoint(
                x: max(minX, min(maxX, proxySize.width - horizontalMargin - menuWidth / 2)),
                y: min(max(max(92, safeAreaInsets.top + 58) + height / 2, fallbackMinY), maxY)
            )
        }

        let horizontalOffset: CGFloat = 10
        let rawX: CGFloat
        if proxySize.width - anchor.x >= menuWidth + horizontalMargin + horizontalOffset {
            rawX = anchor.x + menuWidth / 2 - 24
        } else {
            rawX = anchor.x - menuWidth / 2 + 24
        }

        let topEdgeOffset: CGFloat = -22
        let bottomEdgeOffset: CGFloat = 22
        let availableBelow = proxySize.height - bottomClearance - anchor.y
        let rawY: CGFloat
        if availableBelow >= height + topEdgeOffset + verticalMargin {
            rawY = anchor.y + height / 2 + topEdgeOffset
        } else {
            rawY = anchor.y - height / 2 + bottomEdgeOffset
        }
        let anchoredMinY = anchor.y < fallbackMinY
            ? verticalMargin + height / 2
            : fallbackMinY

        return CGPoint(
            x: min(max(rawX, minX), maxX),
            y: min(max(rawY, anchoredMinY), maxY)
        )
    }

    private func menuTransitionAnchor(anchor: CGPoint?, menuPosition: CGPoint, menuWidth: CGFloat) -> UnitPoint {
        guard let anchor else {
            return .topTrailing
        }
        let height = estimatedMenuHeight
        let minX = menuPosition.x - menuWidth / 2
        let minY = menuPosition.y - height / 2
        return UnitPoint(
            x: min(max((anchor.x - minX) / menuWidth, 0), 1),
            y: min(max((anchor.y - minY) / height, 0), 1)
        )
    }

    private func menuPanel(maxHeight: CGFloat) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                quickActions
                    .padding(.horizontal, 10)
                    .padding(.top, 10)
                    .padding(.bottom, 8)

                BookActionDivider()

                VStack(spacing: 0) {
                    if let onShowDetails, context != .detail {
                        BookActionRow(
                            systemImage: "info.circle",
                            title: "Book Details",
                            subtitle: displayedBook.author
                        ) {
                            onDismiss()
                            onShowDetails()
                        }
                    }

                    if displayedBook.canReadSourceText, let onRead {
                        BookActionRow(
                            systemImage: "text.book.closed",
                            title: "Read",
                            subtitle: displayedBook.source.label
                        ) {
                            onDismiss()
                            onRead()
                        }
                    }
                }

                if context == .nowPlaying {
                    BookActionDivider()
                    VStack(spacing: 0) {
                        if let onShowBookmarks {
                            BookActionRow(systemImage: "bookmark.square", title: "Bookmarks") {
                                onDismiss()
                                onShowBookmarks()
                            }
                        }
                        if let onToggleCurrentBookmark {
                            BookActionRow(
                                systemImage: currentBookmarkExists ? "bookmark.fill" : "bookmark",
                                title: currentBookmarkExists ? "Remove Bookmark" : "Add Bookmark",
                                isEnabled: canBookmarkCurrent
                            ) {
                                onDismiss()
                                onToggleCurrentBookmark()
                            }
                        }
                    }
                }

                BookActionDivider()

                VStack(spacing: 0) {
                    BookActionRow(systemImage: "text.line.last.and.arrowtriangle.forward", title: "Add to Queue") {
                        app.enqueue(displayedBook)
                        onDismiss()
                    }

                    if hasActiveDownload {
                        BookActionRow(systemImage: "xmark.circle", title: "Cancel Download") {
                            Task {
                                await app.cancelDownload(bookID: displayedBook.id)
                                onDismiss()
                            }
                        }
                    } else if hasDownload {
                        BookActionRow(systemImage: "xmark.circle", title: "Remove Download") {
                            Task {
                                await app.deleteDownload(bookID: displayedBook.id)
                                onDismiss()
                            }
                        }
                    } else {
                        BookActionRow(systemImage: "arrow.down.circle", title: "Download") {
                            Task {
                                await app.enqueueDownload(displayedBook)
                                onDismiss()
                            }
                        }
                    }
                }

                if isInLibrary {
                    BookActionDivider()

                    BookActionRow(systemImage: "trash", title: "Delete from Library", isDestructive: true) {
                        showingDeleteLibraryConfirmation = true
                    }
                }
            }
            .padding(.bottom, 10)
        }
        .scrollIndicators(.hidden)
        .frame(maxHeight: min(maxHeight, 420))
        .background {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(.ultraThinMaterial)
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Color.black.opacity(colorScheme == .dark ? 0.28 : 0.2))
        }
        .overlay {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .stroke(.white.opacity(colorScheme == .dark ? 0.12 : 0.18), lineWidth: 0.8)
        }
        .shadow(color: .black.opacity(0.22), radius: 18, x: 0, y: 12)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .foregroundStyle(.white)
    }

    private var quickActions: some View {
        HStack(spacing: 2) {
            if !isInLibrary {
                BookActionTile(systemImage: "plus.circle.fill", title: "Add") {
                    Task {
                        await app.saveToLibrary(displayedBook)
                        onDismiss()
                    }
                }
            }

            BookActionTile(
                systemImage: displayedBook.isFavorite ? "star.fill" : "star",
                title: displayedBook.isFavorite ? "Undo Favorite" : "Favorite"
            ) {
                Task {
                    let willFavorite = !displayedBook.isFavorite
                    await app.toggleFavorite(displayedBook)
                    showFavoriteToast(isFavorite: willFavorite)
                }
            }

            if let sourceURL {
                ShareLink(item: sourceURL) {
                    BookActionTileLabel(systemImage: "square.and.arrow.up", title: "Share")
                }
                .buttonStyle(.plain)
            } else {
                BookActionTile(systemImage: "square.and.arrow.up", title: "Share", isEnabled: false) {}
            }
        }
    }

    private func showFavoriteToast(isFavorite: Bool) {
        favoriteToastTask?.cancel()
        withAnimation(.snappy(duration: 0.2)) {
            favoriteToast = FavoriteToast(
                systemImage: isFavorite ? "star.fill" : "star.slash.fill",
                message: isFavorite ? "Favorited" : "Favorite Removed"
            )
        }
        favoriteToastTask = Task {
            try? await Task.sleep(for: .seconds(1.6))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                withAnimation(.snappy(duration: 0.2)) {
                    favoriteToast = nil
                }
            }
        }
    }
}

private struct FavoriteToast: Equatable {
    var systemImage: String
    var message: String
}

private struct FavoriteToastView: View {
    var toast: FavoriteToast

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.systemImage)
                .font(.system(size: 20, weight: .semibold))
            Text(toast.message)
                .font(.headline.weight(.semibold))
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(.ultraThinMaterial)
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.black.opacity(0.42))
        }
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.22), radius: 18, y: 10)
    }
}

private struct BookActionTile: View {
    var systemImage: String
    var title: String
    var isEnabled = true
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            BookActionTileLabel(systemImage: systemImage, title: title)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private struct BookActionTileLabel: View {
    var systemImage: String
    var title: String

    var body: some View {
        VStack(spacing: 5) {
            Image(systemName: systemImage)
                .font(.system(size: 18, weight: .semibold))
                .frame(height: 20)
            Text(title)
                .font(.caption2.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, minHeight: 44)
        .contentShape(Rectangle())
    }
}

private struct BookActionRow: View {
    var systemImage: String
    var title: String
    var subtitle: String?
    var isDestructive = false
    var isEnabled = true
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .medium))
                    .frame(width: 22)

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline.weight(.regular))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.white.opacity(0.56))
                            .lineLimit(2)
                            .minimumScaleFactor(0.82)
                    }
                }

                Spacer(minLength: 0)
            }
            .foregroundStyle(isDestructive ? Color.red : .white)
            .opacity(isEnabled ? 1 : 0.52)
            .padding(.horizontal, 12)
            .padding(.vertical, subtitle == nil ? 7 : 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
    }
}

private struct BookActionDivider: View {
    var body: some View {
        Divider()
            .overlay(.white.opacity(0.12))
            .padding(.leading, 46)
            .padding(.trailing, 12)
            .padding(.vertical, 3)
    }
}

struct InteractionAnchorReader: View {
    @Binding var anchor: CGPoint?

    var body: some View {
        GeometryReader { proxy in
            Color.clear.preference(
                key: InteractionAnchorPreferenceKey.self,
                value: CGPoint(x: proxy.frame(in: .global).midX, y: proxy.frame(in: .global).midY)
            )
        }
        .allowsHitTesting(false)
        .onPreferenceChange(InteractionAnchorPreferenceKey.self) { point in
            anchor = point
        }
    }
}

/// Each visible book owns a source namespace, including repeated books in different shelves.
struct BookDetailsNavigationLink<Label: View>: View {
    @Environment(\.bookDetailCollection) private var collection
    @Namespace private var transition
    @State private var zoomSource = BookDetailZoomSource()
    var book: AudioBook
    @ViewBuilder var label: () -> Label

    var body: some View {
        NavigationLink {
            BookDetailBrowser(book: book, collection: collection)
                .environment(\.bookDetailZoomSource, zoomSource)
                .modifier(BookNavigationTransition(sourceID: book.id, namespace: transition))
        } label: {
            label()
                .background(BookDetailZoomAnchor(source: zoomSource))
                .matchedTransitionSource(id: book.id, in: transition)
        }
    }
}

private struct BookNavigationTransition: ViewModifier {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var sourceID: String
    var namespace: Namespace.ID

    @ViewBuilder
    func body(content: Content) -> some View {
        if reduceMotion || app.settings.animationSpeed == .reduced {
            content.navigationTransition(.automatic)
        } else {
            content.navigationTransition(.zoom(sourceID: sourceID, in: namespace))
        }
    }
}

struct AnchoredBookNavigationLink<Destination: View, Label: View>: View {
    @Namespace private var transition
    @State private var zoomSource = BookDetailZoomSource()
    @State private var isShowingDestination = false
    var book: AudioBook
    var onActions: (AudioBook, CGPoint?) -> Void
    @ViewBuilder var destination: () -> Destination
    @ViewBuilder var label: () -> Label

    var body: some View {
        Button {
            isShowingDestination = true
        } label: {
            label()
                .background(BookDetailZoomAnchor(source: zoomSource))
                .contentShape(Rectangle())
                .matchedTransitionSource(id: book.id, in: transition)
        }
        .buttonStyle(.plain)
        .bookActionsContextMenu(
            book: book,
            context: .home,
            previewStyle: .row,
            onShowDetails: { isShowingDestination = true }
        )
        .navigationDestination(isPresented: $isShowingDestination) {
            destination()
                .environment(\.bookDetailZoomSource, zoomSource)
                .modifier(BookNavigationTransition(sourceID: book.id, namespace: transition))
        }
    }
}

private struct InteractionAnchorPreferenceKey: PreferenceKey {
    static let defaultValue: CGPoint? = nil

    static func reduce(value: inout CGPoint?, nextValue: () -> CGPoint?) {
        value = nextValue() ?? value
    }
}

enum BookContextMenuPreviewStyle {
    case cover
    case row
}

extension View {
    func bookActionsContextMenu(
        book: AudioBook,
        context: BookActionsContext,
        previewStyle: BookContextMenuPreviewStyle,
        onShowDetails: (() -> Void)? = nil,
        onRead: (() -> Void)? = nil
    ) -> some View {
        modifier(
            NativeBookActionsContextMenuModifier(
                book: book,
                context: context,
                previewStyle: previewStyle,
                onShowDetails: onShowDetails,
                onRead: onRead
            )
        )
    }
}

private struct NativeBookActionsContextMenuModifier: ViewModifier {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    var context: BookActionsContext
    var previewStyle: BookContextMenuPreviewStyle
    var onShowDetails: (() -> Void)?
    var onRead: (() -> Void)?

    @State private var showingDeleteLibraryConfirmation = false

    func body(content: Content) -> some View {
        content
            .contextMenu {
                NativeBookActionsMenuContent(
                    book: book,
                    context: context,
                    onShowDetails: onShowDetails,
                    onRead: onRead,
                    onRequestLibraryDeletion: { showingDeleteLibraryConfirmation = true }
                )
                .environment(app)
            } preview: {
                NativeBookContextMenuPreview(book: book, style: previewStyle)
                    .environment(app)
            }
            .nativeBookDeletionConfirmation(
                book: book,
                isPresented: $showingDeleteLibraryConfirmation
            )
    }
}

private struct NativeBookActionsMenuContent: View {
    @Environment(AppModel.self) private var app

    var book: AudioBook
    var context: BookActionsContext
    var onShowDetails: (() -> Void)?
    var onRead: (() -> Void)?
    var onRequestLibraryDeletion: () -> Void

    private var displayedBook: AudioBook { app.latestKnownVersion(of: book) }
    private var isInLibrary: Bool { app.isInLibrary(displayedBook) }
    private var hasDownload: Bool { app.hasDownload(for: displayedBook) }
    private var hasActiveDownload: Bool { app.hasActiveDownload(for: displayedBook) }

    private var sourceURL: URL? {
        [
            displayedBook.libriVoxURL,
            displayedBook.lit2GoURL,
            displayedBook.wolneLekturyURL,
            displayedBook.gutenbergURL,
        ]
        .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
        .compactMap(URL.init(string:))
        .first
    }

    var body: some View {
        ControlGroup {
            if !isInLibrary {
                Button("Add", systemImage: "plus.circle") {
                    Task { await app.saveToLibrary(displayedBook) }
                }
            }

            Button(
                displayedBook.isFavorite ? "Undo Favorite" : "Favorite",
                systemImage: displayedBook.isFavorite ? "star.fill" : "star"
            ) {
                Task { await app.toggleFavorite(displayedBook) }
            }

            if let sourceURL {
                ShareLink(item: sourceURL) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
            }
        }

        Section {
            if let onShowDetails, context != .detail {
                Button("Book Details", systemImage: "info.circle", action: onShowDetails)
            }

            if displayedBook.canReadSourceText, let onRead {
                Button("Read", systemImage: "text.book.closed", action: onRead)
            }
        }

        Section {
            Button("Add to Queue", systemImage: "text.line.last.and.arrowtriangle.forward") {
                app.enqueue(displayedBook)
            }

            if hasActiveDownload {
                Button("Cancel Download", systemImage: "xmark.circle") {
                    Task { await app.cancelDownload(bookID: displayedBook.id) }
                }
            } else if hasDownload {
                Button("Remove Download", systemImage: "xmark.circle") {
                    Task { await app.deleteDownload(bookID: displayedBook.id) }
                }
            } else {
                Button("Download", systemImage: "arrow.down.circle") {
                    Task { await app.enqueueDownload(displayedBook) }
                }
            }
        }

        if context == .continueListening {
            Section {
                Button("Remove from Continue Listening", systemImage: "minus.circle") {
                    app.removeFromContinueListening(displayedBook)
                }
                .accessibilityIdentifier("remove-from-continue-listening")
            }
        }

        if isInLibrary {
            Section {
                Button("Delete from Library", systemImage: "trash", role: .destructive) {
                    onRequestLibraryDeletion()
                }
            }
        }
    }
}

private struct NativeBookDeletionConfirmationModifier: ViewModifier {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    @Binding var isPresented: Bool

    private var displayedBook: AudioBook { app.latestKnownVersion(of: book) }
    private var hasDownload: Bool { app.hasDownload(for: displayedBook) }
    private var hasActiveDownload: Bool { app.hasActiveDownload(for: displayedBook) }

    func body(content: Content) -> some View {
        content.confirmationDialog(
            "Delete from Library?",
            isPresented: $isPresented,
            titleVisibility: .visible
        ) {
            if hasDownload {
                Button(hasActiveDownload ? "Cancel Download" : "Remove Download") {
                    Task {
                        if hasActiveDownload {
                            await app.cancelDownload(bookID: displayedBook.id)
                        } else {
                            await app.deleteDownload(bookID: displayedBook.id)
                        }
                    }
                }
            }
            Button("Delete from Library", role: .destructive) {
                Task {
                    await app.removeFromLibrary(displayedBook)
                    if hasDownload {
                        await app.deleteDownload(bookID: displayedBook.id)
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(
                hasDownload
                    ? "Do you want to remove this book from your downloads or delete it from your library?"
                    : "This removes the book from your library."
            )
        }
    }
}

private extension View {
    func nativeBookDeletionConfirmation(book: AudioBook, isPresented: Binding<Bool>) -> some View {
        modifier(NativeBookDeletionConfirmationModifier(book: book, isPresented: isPresented))
    }
}

private struct NativeBookContextMenuPreview: View {
    var book: AudioBook
    var style: BookContextMenuPreviewStyle

    var body: some View {
        Group {
            switch style {
            case .cover:
                VStack(alignment: .leading, spacing: 12) {
                    BookCoverView(
                        book: book,
                        width: 244,
                        height: 244,
                        cornerRadius: 20,
                        preferFullImage: true,
                        displayModeOverride: .fit
                    )
                    previewLabels
                        .frame(width: 244, alignment: .leading)
                }
                .padding(14)
            case .row:
                HStack(spacing: 13) {
                    BookCoverView(
                        book: book,
                        width: 66,
                        height: 88,
                        cornerRadius: 10,
                        preferFullImage: true
                    )
                    previewLabels
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(12)
                .frame(width: 330, alignment: .leading)
            }
        }
        .background(
            Color(uiColor: .secondarySystemBackground),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
        .accessibilityHidden(true)
    }

    private var previewLabels: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(book.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(2)
            Text(book.author)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Text(book.source.label)
                .font(.caption)
                .foregroundStyle(.tertiary)
                .lineLimit(1)
        }
    }
}
