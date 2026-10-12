import LibriVoxCore
import Observation
import SwiftUI
import UIKit

/// The ordered shelf or result set from which a book was opened.
struct BookDetailCollection: Sendable {
    var title: String
    var books: [AudioBook]

    func books(including selectedBook: AudioBook) -> [AudioBook] {
        var seen = Set<String>()
        var result = books.filter { seen.insert($0.id).inserted }
        if !seen.contains(selectedBook.id) {
            result.insert(selectedBook, at: 0)
        }
        return result
    }
}

private struct BookDetailCollectionKey: EnvironmentKey {
    static let defaultValue: BookDetailCollection? = nil
}

extension EnvironmentValues {
    var bookDetailCollection: BookDetailCollection? {
        get { self[BookDetailCollectionKey.self] }
        set { self[BookDetailCollectionKey.self] = newValue }
    }
}

extension View {
    func bookDetailCollection(_ books: [AudioBook], title: String) -> some View {
        environment(\.bookDetailCollection, BookDetailCollection(title: title, books: books))
    }
}

/// Native scroll geometry is observed only by the lightweight viewport and chrome.
@Observable
@MainActor
final class BookDetailScrollMotion {
    var offset: CGFloat = 0
}

/// Render-only geometry: the scroll view's bounds, content size and hero padding
/// remain invariant while the card grows and while UIKit rubber-bands its content.
struct BookDetailScrollTransform {
    static let expansionDistance: CGFloat = 112
    static let sourceHeadingTopSpacing: CGFloat = 4

    let expansion: CGFloat
    let scale: CGFloat
    let topOffset: CGFloat
    let contentOffset: CGFloat
    let cornerRadius: CGFloat
    let pullDistance: CGFloat
    let sourceHeadingReveal: CGFloat

    init(
        offset: CGFloat,
        viewportWidth: CGFloat,
        compactTopInset: CGFloat,
        forcedExpansion: CGFloat? = nil,
        reducesMotion: Bool = false
    ) {
        let progress = min(1, max(0, forcedExpansion ?? offset / Self.expansionDistance))
        expansion = reducesMotion ? (progress >= 1 ? 1 : 0) : progress
        let compactScale = max(0.85, (viewportWidth - 28) / max(viewportWidth, 1))
        pullDistance = reducesMotion || forcedExpansion != nil ? 0 : max(0, -offset)
        let additionalContraction = min(pullDistance / 120, 1) * 0.02
        scale = compactScale + (1 - compactScale) * expansion - additionalContraction
        let topInset = compactTopInset * (1 - expansion)
        topOffset = topInset + pullDistance
        // Native negative scroll offset has already moved the content downward.
        // Cancel that travel inside the clip before moving the whole card once.
        contentOffset = -topInset - pullDistance
        cornerRadius = 28 * (1 - expansion)
        sourceHeadingReveal = max(0, topOffset - compactTopInset - Self.sourceHeadingTopSpacing)
    }
}

/// Retained by the browser when its lazy stack releases an offscreen page.
@Observable
@MainActor
final class BookDetailPageState {
    var hydratedBook: AudioBook?
    var isLoading = false
    var scrollPosition = ScrollPosition(edge: .top)
    var verticalOffset: CGFloat = 0
    let motion = BookDetailScrollMotion()
}

enum BookDetailScrollRegion: Equatable {
    case topPull, top, reading

    init(offset: CGFloat) {
        if offset < 0 {
            self = .topPull
        } else if offset >= BookDetailScrollTransform.expansionDistance {
            self = .reading
        } else {
            self = .top
        }
    }
}

/// Keeps per-frame updates away from the chapter and artwork view bodies.
struct BookDetailScrollTracking: ViewModifier {
    @Bindable var state: BookDetailPageState
    var isActive: Bool
    var onRegionChange: ((BookDetailScrollRegion) -> Void)?

    func body(content: Content) -> some View {
        content
            .scrollPosition($state.scrollPosition)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                min(BookDetailScrollTransform.expansionDistance, geometry.contentOffset.y + geometry.contentInsets.top)
            } action: { oldOffset, offset in
                guard isActive else { return }
                // Geometry includes native deceleration and rubber-banding. Do not
                // start a new spring for each sample or put it in the detail model.
                var transaction = Transaction()
                transaction.disablesAnimations = true
                withTransaction(transaction) { state.motion.offset = offset }
                let region = BookDetailScrollRegion(offset: offset)
                if region != BookDetailScrollRegion(offset: oldOffset) {
                    onRegionChange?(region)
                }
            }
            .onScrollPhaseChange { _, phase, context in
                if phase == .idle, isActive {
                    state.verticalOffset = max(0, context.geometry.contentOffset.y + context.geometry.contentInsets.top)
                }
            }
            .onAppear {
                if state.verticalOffset > 0 {
                    state.scrollPosition.scrollTo(y: state.verticalOffset)
                }
            }
    }
}

/// Only this small modifier depends on the live vertical offset. The existing
/// detail subtree is passed through unchanged at every scroll sample.
private struct BookDetailCardMotion: ViewModifier {
    @Environment(\.colorScheme) private var colorScheme
    let motion: BookDetailScrollMotion
    let viewportWidth: CGFloat
    let compactTopInset: CGFloat
    let isSelected: Bool
    let isPaging: Bool
    let permitsMotion: Bool

    func body(content: Content) -> some View {
        let transform = BookDetailScrollTransform(
            offset: motion.offset,
            viewportWidth: viewportWidth,
            compactTopInset: compactTopInset,
            forcedExpansion: isPaging || !isSelected ? 0 : nil,
            reducesMotion: !permitsMotion
        )
        content
            .offset(y: transform.contentOffset)
            .background {
                Rectangle().fill(LibriVoxBrand.surface(for: colorScheme))
            }
            .clipShape(RoundedRectangle(cornerRadius: transform.cornerRadius, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: transform.cornerRadius, style: .continuous))
            .scaleEffect(transform.scale, anchor: .top)
            .offset(y: transform.topOffset)
            .accessibilityElement(children: .contain)
            .accessibilityValue(transform.expansion >= 1 ? "Expanded" : "Compact")
    }
}

private struct BookCollectionSourceHeading: View {
    let title: String
    let motion: BookDetailScrollMotion
    let viewportWidth: CGFloat
    let compactTopInset: CGFloat
    let permitsMotion: Bool

    var body: some View {
        let transform = BookDetailScrollTransform(
            offset: motion.offset,
            viewportWidth: viewportWidth,
            compactTopInset: compactTopInset,
            reducesMotion: !permitsMotion
        )
        Text(title)
            .font(.system(.largeTitle, design: .serif, weight: .bold))
            .lineLimit(1)
            .padding(.horizontal, 24)
            .padding(.top, compactTopInset + BookDetailScrollTransform.sourceHeadingTopSpacing)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            // Native soft scroll-edge compositing can expose background layers.
            // Reveal only the physical strip above the pulled card, so the
            // heading cannot bleed through its resting or expanded surface.
            .mask(alignment: .top) {
                Rectangle().frame(height: transform.topOffset)
            }
            .accessibilityIdentifier("book-detail-collection-source-title")
            .accessibilityHidden(true)
            .allowsHitTesting(false)
    }
}

struct BookDetailBrowser: View {
    @Environment(\.bookDetailCollection) private var inheritedCollection
    @Environment(\.bookDetailZoomSource) private var zoomSource
    var book: AudioBook
    var collection: BookDetailCollection? = nil

    var body: some View {
        BookDetailBrowserContent(book: book, collection: collection ?? inheritedCollection, zoomSource: zoomSource)
            // Nested routes supply their own collection and zoom source.
            .environment(\.bookDetailCollection, nil)
            .environment(\.bookDetailZoomSource, nil)
    }
}

private struct BookDetailBrowserContent: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.colorScheme) private var colorScheme
    @State private var books: [AudioBook]
    @State private var collectionTitle: String
    @State private var selectedID: String?
    @State private var isCompact: Bool
    @State private var isHorizontalPaging = false
    @State private var pageStates: [String: BookDetailPageState]
    @State private var visibilityID = UUID()
    @State private var initialBookID: String
    @State private var didPositionInitialBook = false
    @State private var zoomSource: BookDetailZoomSource?

    init(book: AudioBook, collection: BookDetailCollection?, zoomSource: BookDetailZoomSource?) {
        let books = collection?.books(including: book) ?? [book]
        _books = State(initialValue: books)
        _collectionTitle = State(initialValue: collection?.title ?? "Books")
        _zoomSource = State(initialValue: zoomSource)
        _selectedID = State(initialValue: book.id)
        _initialBookID = State(initialValue: book.id)
        _isCompact = State(initialValue: books.count > 1)
        _pageStates = State(initialValue: Dictionary(uniqueKeysWithValues: books.map { ($0.id, BookDetailPageState()) }))
    }

    private var selectedIndex: Int { books.firstIndex { $0.id == selectedID } ?? 0 }
    private var selectedBook: AudioBook { books[selectedIndex] }
    private var permitsMotion: Bool { !reduceMotion && !voiceOver && app.settings.animationSpeed != .reduced }
    private var positionLabel: String { "\(selectedIndex + 1) of \(books.count)" }
    private var initialScrollAnchor: UnitPoint {
        let index = books.firstIndex { $0.id == initialBookID } ?? 0
        // Uniform page strides and symmetric margins make this fraction the
        // exact initial offset, before the lazy layout's first rendered frame.
        return UnitPoint(x: CGFloat(index) / CGFloat(max(books.count - 1, 1)), y: 0)
    }

    var body: some View {
        if books.count < 2 {
            BookDetailView(book: selectedBook)
        } else {
            collectionBrowser
                .onAppear { app.visibleBookDetailBrowserIDs.insert(visibilityID) }
                .onDisappear { app.visibleBookDetailBrowserIDs.remove(visibilityID) }
        }
    }

    private var collectionBrowser: some View {
        GeometryReader { geometry in
            let topInset = geometry.safeAreaInsets.top
            // Extend underneath native tab/accessory chrome, including while contracted.
            let pageHeight = geometry.size.height + topInset + geometry.safeAreaInsets.bottom
            let pageWidth = max(1, geometry.size.width - 28)
            let compactTopInset = max(14, topInset - 44)
            ZStack(alignment: .topLeading) {
                if let motion = pageStates[selectedBook.id]?.motion {
                    BookCollectionSourceHeading(
                        title: collectionTitle,
                        motion: motion,
                        viewportWidth: geometry.size.width,
                        compactTopInset: compactTopInset,
                        permitsMotion: permitsMotion
                    )
                }
                ScrollViewReader { scrollProxy in
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 6) {
                            ForEach(books) { book in
                                page(
                                    for: book,
                                    width: geometry.size.width,
                                    height: pageHeight,
                                    topInset: topInset,
                                    compactTopInset: compactTopInset
                                )
                                .frame(width: pageWidth, height: pageHeight)
                                .id(book.id)
                            }
                        }
                        .scrollTargetLayout()
                    }
                    .contentMargins(.horizontal, 14, for: .scrollContent)
                    .scrollIndicators(.hidden)
                    .scrollTargetBehavior(.viewAligned(limitBehavior: .alwaysByOne, anchor: .center))
                    .scrollPosition(id: $selectedID, anchor: .center)
                    .defaultScrollAnchor(initialScrollAnchor, for: .initialOffset)
                    .scrollClipDisabled()
                    .onScrollPhaseChange { _, phase in
                        if phase == .interacting || phase == .animating {
                            setHorizontalPaging(true)
                        } else if phase == .idle {
                            setHorizontalPaging(false)
                        }
                    }
                    .task {
                        guard !didPositionInitialBook else { return }
                        // The ID binding tracks paging, but on first appearance the lazy
                        // layout must register its targets before positioning a middle book.
                        await Task.yield()
                        guard !Task.isCancelled else { return }
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            scrollProxy.scrollTo(initialBookID, anchor: .center)
                            selectedID = initialBookID
                        }
                        didPositionInitialBook = true
                    }
                }
            }
            .frame(height: pageHeight)
            .offset(y: -topInset)
        }
        .background {
            if let motion = pageStates[selectedBook.id]?.motion {
                BookCollectionNavigationMotion(
                    motion: motion,
                    source: zoomSource,
                    permitsMotion: permitsMotion,
                    isPaging: isHorizontalPaging
                )
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
            }
        }
        .background {
            LibriVoxBrand.surface(for: colorScheme).ignoresSafeArea()
            ArtworkGradient(book: selectedBook, style: .hero, enabled: app.settings.bookDetailUseArtworkColorScheme)
                .opacity(0.25)
                .ignoresSafeArea()
        }
        .toolbarBackground(.hidden, for: .navigationBar)
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                collectionControls
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("book-detail-collection")
        .accessibilityLabel(collectionTitle)
        .accessibilityValue(positionLabel)
        .accessibilityScrollAction { edge in
            if edge == .leading { selectBook(offset: -1) }
            if edge == .trailing { selectBook(offset: 1) }
        }
        .onChange(of: selectedID) { _, _ in
            // Swiping changes only the visible book, never playback or its queue.
            isCompact = isHorizontalPaging || (pageStates[selectedBook.id]?.verticalOffset ?? 0) < BookDetailScrollTransform.expansionDistance
        }
    }

    private func page(for book: AudioBook, width: CGFloat, height: CGFloat, topInset: CGFloat, compactTopInset: CGFloat) -> some View {
        let isSelected = book.id == selectedID
        let state = pageStates[book.id]!
        return BookDetailView(
            book: book,
            isActive: isSelected,
            pageState: state,
            browserTopInset: topInset,
            onScrollRegionChange: { region in
                guard isSelected, !isHorizontalPaging else { return }
                isCompact = region != .reading
            }
        )
        .frame(width: width, height: height)
        .modifier(BookDetailCardMotion(
            motion: state.motion,
            viewportWidth: width,
            compactTopInset: compactTopInset,
            isSelected: isSelected,
            isPaging: isHorizontalPaging,
            permitsMotion: permitsMotion
        ))
        .accessibilityIdentifier("book-detail-collection-card-\(book.id)")
        .accessibilityLabel(book.title)
        .accessibilityHidden(!isSelected)
        .allowsHitTesting(isSelected)
        .zIndex(isSelected ? 1 : 0)
    }

    private var collectionControls: some View {
        HStack(spacing: 0) {
            Button {
                selectBook(offset: -1)
            } label: {
                Image(systemName: "chevron.left")
                    .frame(width: 36, height: 44)
            }
            .disabled(selectedIndex == 0)
            .accessibilityLabel("Previous book")
            .accessibilityIdentifier("book-detail-collection-previous")

            Button {
                scrollSelectedBook(compact: !isCompact)
            } label: {
                Text(positionLabel)
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .frame(minWidth: 44, minHeight: 44)
            }
            .accessibilityLabel(isCompact ? "Expand book" : "Browse books")
            .accessibilityValue(positionLabel)
            .accessibilityIdentifier(isCompact ? "book-detail-collection-expand" : "book-detail-collection-collapse")

            Button {
                selectBook(offset: 1)
            } label: {
                Image(systemName: "chevron.right")
                    .frame(width: 36, height: 44)
            }
            .disabled(selectedIndex == books.count - 1)
            .accessibilityLabel("Next book")
            .accessibilityIdentifier("book-detail-collection-next")
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
    }

    private func scrollSelectedBook(compact: Bool) {
        guard let state = pageStates[selectedBook.id] else { return }
        withAnimation(permitsMotion ? .snappy(duration: 0.28, extraBounce: 0) : nil) {
            state.scrollPosition.scrollTo(y: compact ? 0 : BookDetailScrollTransform.expansionDistance)
        }
        isCompact = compact
    }

    private func setHorizontalPaging(_ paging: Bool) {
        guard paging != isHorizontalPaging else { return }
        withAnimation(permitsMotion ? .snappy(duration: 0.28, extraBounce: 0) : nil) {
            isHorizontalPaging = paging
        }
        isCompact = paging || (pageStates[selectedBook.id]?.verticalOffset ?? 0) < BookDetailScrollTransform.expansionDistance
    }

    private func selectBook(offset: Int) {
        let index = selectedIndex + offset
        guard books.indices.contains(index) else { return }
        withAnimation(permitsMotion ? .snappy(duration: 0.32, extraBounce: 0) : nil) {
            selectedID = books[index].id
        }
    }

}

/// A UIKit anchor preserves the native zoom source while giving the collection
/// an explicit public policy for interactive dismissal.
@MainActor
final class BookDetailZoomSource {
    weak var view: UIView?
}

private struct BookDetailZoomSourceKey: EnvironmentKey {
    static let defaultValue: BookDetailZoomSource? = nil
}

extension EnvironmentValues {
    var bookDetailZoomSource: BookDetailZoomSource? {
        get { self[BookDetailZoomSourceKey.self] }
        set { self[BookDetailZoomSourceKey.self] = newValue }
    }
}

struct BookDetailZoomAnchor: UIViewRepresentable {
    var source: BookDetailZoomSource

    func makeUIView(context: Context) -> UIView {
        let view = UIView(frame: .zero)
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        view.isAccessibilityElement = false
        view.accessibilityElementsHidden = true
        return view
    }

    func updateUIView(_ view: UIView, context: Context) { source.view = view }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UIView, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 0, height: proposal.height ?? 0)
    }
}

private struct BookCollectionNavigationMotion: View {
    let motion: BookDetailScrollMotion
    let source: BookDetailZoomSource?
    let permitsMotion: Bool
    let isPaging: Bool

    var body: some View {
        BookCollectionContentPopGuard(
            source: source,
            enablesZoom: permitsMotion,
            pullDistance: permitsMotion && !isPaging ? max(0, -motion.offset) : 0
        )
    }
}

/// Central horizontal swipes belong to the collection and downward pulls
/// contract its page. Leading-edge back and the native Back button still work.
private struct BookCollectionContentPopGuard: UIViewControllerRepresentable {
    var source: BookDetailZoomSource?
    var enablesZoom: Bool
    var pullDistance: CGFloat

    func makeUIViewController(context: Context) -> Controller { Controller() }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.configure(source: source, enablesZoom: enablesZoom, pullDistance: pullDistance)
    }

    static func dismantleUIViewController(_ controller: Controller, coordinator: ()) {
        controller.restore()
    }

    final class Controller: UIViewController {
        private weak var contentPopGesture: UIGestureRecognizer?
        private var originalIsEnabled: Bool?
        private weak var configuredHost: UIViewController?
        private var originalTransition: UIViewController.Transition?
        private var collectionTransition: UIViewController.Transition?
        private weak var source: BookDetailZoomSource?
        private var enablesZoom = false
        private var isVisible = false
        private var pullDistance: CGFloat = 0
        private weak var configuredNavigationBar: UINavigationBar?
        private var originalNavigationBarTransform: CGAffineTransform?
        private var appliedNavigationBarTransform: CGAffineTransform?

        override func loadView() {
            view = UIView(frame: .zero)
            view.isUserInteractionEnabled = false
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            isVisible = true
            refresh()
        }

        override func viewWillDisappear(_ animated: Bool) {
            isVisible = false
            restore()
            super.viewWillDisappear(animated)
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            refresh()
        }

        func configure(source: BookDetailZoomSource?, enablesZoom: Bool, pullDistance: CGFloat) {
            if self.source !== source || self.enablesZoom != enablesZoom {
                restore()
            }
            self.source = source
            self.enablesZoom = enablesZoom
            self.pullDistance = pullDistance
            refresh()
        }

        func refresh() {
            guard isVisible, isViewLoaded, view.window != nil,
                  let navigation = enclosingNavigationController(),
                  let host = navigation.topViewController,
                  view.isDescendant(of: host.view)
            else {
                restore()
                return
            }

            if configuredHost !== host {
                restore()
                configuredHost = host
                originalTransition = host.preferredTransition
            }

            let navigationBar = navigation.navigationBar
            if configuredNavigationBar !== navigationBar {
                configuredNavigationBar = navigationBar
                originalNavigationBarTransform = navigationBar.transform
            }
            if let originalNavigationBarTransform {
                let transform = originalNavigationBarTransform.translatedBy(x: 0, y: pullDistance)
                if navigationBar.transform != transform {
                    navigationBar.transform = transform
                }
                appliedNavigationBarTransform = transform
            }

            if let gesture = navigation.interactiveContentPopGestureRecognizer {
                if contentPopGesture !== gesture {
                    contentPopGesture = gesture
                    originalIsEnabled = gesture.isEnabled
                }
                gesture.isEnabled = false
            }

            // SwiftUI's zoom has a separate interactive dismissal policy from
            // UINavigationController's ordinary full-content pop recognizer.
            if enablesZoom, let source {
                if collectionTransition == nil {
                    let options = UIViewController.Transition.ZoomOptions()
                    options.interactiveDismissShouldBegin = { [weak host] context in
                        guard let host else { return false }
                        let isRTL = host.view.effectiveUserInterfaceLayoutDirection == .rightToLeft
                        let startsAtLeadingEdge = isRTL
                            ? context.location.x >= host.view.bounds.width - 44
                            : context.location.x <= 44
                        let movesBack = isRTL ? context.velocity.dx < 0 : context.velocity.dx > 0
                        return context.willBegin && startsAtLeadingEdge && movesBack
                            && abs(context.velocity.dx) > abs(context.velocity.dy)
                    }
                    collectionTransition = .zoom(options: options) { [weak source] _ in
                        guard let view = source?.view, view.window != nil, !view.bounds.isEmpty else { return nil }
                        return view
                    }
                }
                // SwiftUI may reapply its matched transition after a sheet closes.
                if host.preferredTransition !== collectionTransition {
                    host.preferredTransition = collectionTransition
                }
            }
        }

        private func enclosingNavigationController() -> UINavigationController? {
            if let navigationController { return navigationController }
            var responder: UIResponder? = view
            while let current = responder {
                if let navigation = current as? UINavigationController { return navigation }
                if let controller = current as? UIViewController,
                   let navigation = controller.navigationController { return navigation }
                responder = current.next
            }
            var ancestor = parent
            while let current = ancestor {
                if let navigation = current as? UINavigationController { return navigation }
                if let navigation = current.navigationController { return navigation }
                ancestor = current.parent
            }
            return nil
        }

        func restore() {
            if let configuredNavigationBar,
               let originalNavigationBarTransform,
               configuredNavigationBar.transform == appliedNavigationBarTransform {
                configuredNavigationBar.transform = originalNavigationBarTransform
            }
            configuredNavigationBar = nil
            originalNavigationBarTransform = nil
            appliedNavigationBarTransform = nil
            if let contentPopGesture, let originalIsEnabled {
                contentPopGesture.isEnabled = originalIsEnabled
            }
            if let configuredHost, configuredHost.preferredTransition === collectionTransition {
                configuredHost.preferredTransition = originalTransition
            }
            contentPopGesture = nil
            originalIsEnabled = nil
            configuredHost = nil
            originalTransition = nil
            collectionTransition = nil
        }
    }
}
