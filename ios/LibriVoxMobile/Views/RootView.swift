import LibriVoxCore
import SwiftUI
import UIKit

enum AppTab: String, CaseIterable, Identifiable {
    case home
    case library
    case search

    var id: String { rawValue }
}

private enum PlayerBrowsingRoute: Hashable {
    case book(AudioBook)
    case author(AudioBook)
}

struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if !app.hasCompletedOnboarding {
                OnboardingView()
            } else {
                MainTabView()
            }
        }
        .appMotionPolicy(
            reduceMotion: reduceMotion,
            preference: app.settings.animationSpeed
        )
    }
}

struct MainTabView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var accessibilityReduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab = AppTab.home
    @State private var homePath = NavigationPath()
    @State private var libraryPath = NavigationPath()
    @State private var searchPath = NavigationPath()
    @State private var homeStackID = UUID()
    @State private var libraryStackID = UUID()
    @State private var searchStackID = UUID()
    @State private var searchQuery = ""
    @State private var searchPresented = false
    @State private var searchSubmissionID = UUID()
    @State private var playerTransitionSources = PlayerTransitionSources()
    @State private var playerTransitionSource = "mini-player"
    @State private var isPlayerPresented = false

    var body: some View {
        TabView(selection: $selectedTab) {
            Tab("Home", systemImage: "house.fill", value: AppTab.home) {
                NavigationStack(path: $homePath) {
                    HomeView()
                        .navigationDestination(for: PlayerBrowsingRoute.self, destination: playerBrowsingDestination)
                }
                .id(homeStackID)
            }

            Tab("Library", systemImage: "books.vertical.fill", value: AppTab.library) {
                NavigationStack(path: $libraryPath) {
                    LibraryView()
                        .navigationDestination(for: PlayerBrowsingRoute.self, destination: playerBrowsingDestination)
                }
                .id(libraryStackID)
            }

            Tab("Search", systemImage: "magnifyingglass", value: AppTab.search, role: .search) {
                NavigationStack(path: $searchPath) {
                    DiscoverView(
                        query: $searchQuery,
                        nativeSearchPresented: $searchPresented,
                        searchSubmissionID: searchSubmissionID
                    )
                        .navigationDestination(for: PlayerBrowsingRoute.self, destination: playerBrowsingDestination)
                }
                .id(searchStackID)
            }
        }
        .onSubmit(of: .search) {
            guard selectedTab == .search, !app.isReaderPresented else { return }
            searchSubmissionID = UUID()
        }
        .searchable(
            text: $searchQuery,
            isPresented: $searchPresented,
            prompt: "Search your library and catalogs"
        )
        .searchPresentationToolbarBehavior(.avoidHidingContent)
        .adaptiveTabStyle(horizontalSizeClass: horizontalSizeClass)
        // Keep every destination available while browsing or leaving search,
        // and keep loaded playback controls expanded on the other tabs.
        .tabBarMinimizeBehavior(
            selectedTab == .search || app.playback.currentBook != nil || app.isBookDetailBrowserVisible
                ? .never : .onScrollDown
        )
        .tint(accent)
        .playbackBottomAccessory(isEnabled: app.defaultListeningBook != nil && !app.isReaderPresented) {
            if app.defaultListeningBook != nil {
                MiniPlayerView(transitionSources: playerTransitionSources) {
                    playerTransitionSource = "mini-player"
                    isPlayerPresented = true
                }
            }
        }
        .background {
            NativeZoomPresentation(
                isPresented: $isPlayerPresented,
                sourceID: playerTransitionSource,
                sources: playerTransitionSources,
                reduceMotion: reducesNowPlayingMotion
            ) {
                NowPlayingView(
                    onShowBookDetails: showBookDetailsFromNowPlaying,
                    onShowAuthorDetails: showAuthorDetailsFromNowPlaying
                )
                .ignoresSafeArea()
                .environment(app)
                .environment(\.libriVoxAccent, accent)
                .environment(\.colorScheme, .dark)
                .environment(\.scenePhase, scenePhase)
                .tint(accent)
                .appMotionPolicy(reduceMotion: accessibilityReduceMotion, preference: app.settings.animationSpeed)
            }
            .frame(width: 0, height: 0)
            .accessibilityHidden(true)
        }
        .environment(\.playerTransitionContext, PlayerTransitionContext(sources: playerTransitionSources) {
            playerTransitionSource = $0
        })
        .onChange(of: app.showingNowPlaying, initial: true) { _, isShowing in
            isPlayerPresented = isShowing
        }
        .onChange(of: isPlayerPresented) { _, isShowing in
            app.showingNowPlaying = isShowing
        }
        .onChange(of: app.settings) { _, _ in
            app.persistSettings()
        }
        .onChange(of: selectedTab) { previousTab, _ in
            resetNavigation(for: previousTab)
        }
        .onChange(of: app.discoverSearchNavigationRequest?.id) { _, requestID in
            guard requestID != nil else { return }
            openSearch()
        }
        .task {
            if app.discoverSearchNavigationRequest != nil {
                openSearch()
            }
        }
    }

    private var reducesNowPlayingMotion: Bool {
        accessibilityReduceMotion || app.settings.animationSpeed == .reduced
    }

    private func openSearch() {
        app.showingNowPlaying = false
        searchPath = NavigationPath()
        selectedTab = .search
    }

    private func resetNavigation(for tab: AppTab) {
        switch tab {
        case .home:
            homePath = NavigationPath()
            homeStackID = UUID()
        case .library:
            libraryPath = NavigationPath()
            libraryStackID = UUID()
        case .search:
            searchPresented = false
            searchQuery = ""
            searchPath = NavigationPath()
            searchStackID = UUID()
        }
    }

    private func showBookDetailsFromNowPlaying(_ book: AudioBook) {
        browseFromNowPlaying(.book(book))
    }

    private func showAuthorDetailsFromNowPlaying(_ book: AudioBook) {
        browseFromNowPlaying(.author(book))
    }

    private func browseFromNowPlaying(_ route: PlayerBrowsingRoute) {
        // Browse in the active tab so its native mini-player remains available.
        // A second stack over Now Playing hides the tab accessory and safe areas.
        // Prepare the destination underneath the system's reverse zoom. A second
        // push animation at the same time makes the book appear to slide sideways.
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            switch selectedTab {
            case .home: homePath.append(route)
            case .library: libraryPath.append(route)
            case .search: searchPath.append(route)
            }
        }
        app.showingNowPlaying = false
    }

    @ViewBuilder
    private func playerBrowsingDestination(_ route: PlayerBrowsingRoute) -> some View {
        switch route {
        case let .book(book):
            BookDetailView(book: book)
        case let .author(book):
            AuthorDetailView(seedBook: book)
        }
    }
}

private extension View {
    @ViewBuilder
    func playbackBottomAccessory<Content: View>(isEnabled: Bool, @ViewBuilder content: () -> Content) -> some View {
        if #available(iOS 26.1, *) {
            // Empty accessory content still leaves a glass capsule. Disable the system
            // accessory itself while Reader supplies its own transport controls.
            self.tabViewBottomAccessory(isEnabled: isEnabled, content: content)
        } else {
            // Reader hides the tab bar on iOS 26.0, where dynamic accessory visibility
            // is unavailable. Keep the TabView's identity and navigation stacks stable.
            self.tabViewBottomAccessory(content: content)
        }
    }

    @ViewBuilder
    func adaptiveTabStyle(horizontalSizeClass: UserInterfaceSizeClass?) -> some View {
        if horizontalSizeClass == .regular {
            self.tabViewStyle(.sidebarAdaptable)
        } else {
            self
        }
    }
}

struct SettingsToolbarButton: View {
    var body: some View {
        NavigationLink {
            SettingsView()
        } label: {
            Image(systemName: "gearshape")
                .foregroundStyle(.primary)
        }
        .tint(.primary)
        .accessibilityLabel("Settings")
    }
}

/// Explicit UIKit anchors survive the tab accessory's separate SwiftUI host.
/// Each opening action selects the source that the user touched.
struct PlayerTransitionContext: Sendable {
    var sources: PlayerTransitionSources?
    var selectSource: @MainActor @Sendable (String) -> Void = { _ in }
}

@MainActor
final class PlayerTransitionSources {
    private final class Entry {
        weak var view: UIView?
        init(_ view: UIView) { self.view = view }
    }

    private var entries: [String: [ObjectIdentifier: Entry]] = [:]
    private var observers: [UUID: () -> Void] = [:]

    fileprivate func register(_ view: UIView, id: String) {
        entries[id, default: [:]][ObjectIdentifier(view)] = Entry(view)
        for observer in Array(observers.values) { observer() }
    }

    fileprivate func observeRegistration(_ observer: @escaping () -> Void) -> UUID {
        let id = UUID()
        observers[id] = observer
        return id
    }

    fileprivate func removeObserver(_ id: UUID) { observers[id] = nil }

    fileprivate func unregister(_ view: UIView, id: String) {
        entries[id]?[ObjectIdentifier(view)] = nil
        if entries[id]?.isEmpty == true { entries[id] = nil }
    }

    fileprivate func view(for id: String) -> UIView? {
        for candidate in [id, "mini-player"] {
            let visible = (entries[candidate] ?? [:]).values.compactMap(\.view).filter { view in
                guard let window = view.window, !view.bounds.isEmpty,
                      view.convert(view.bounds, to: window).intersects(window.bounds)
                else { return false }
                var ancestor: UIView? = view
                while let current = ancestor {
                    guard !current.isHidden, current.alpha > 0.01 else { return false }
                    if current.clipsToBounds, !view.convert(view.bounds, to: current).intersects(current.bounds) {
                        return false
                    }
                    ancestor = current.superview
                }
                return true
            }
            // The tab accessory can keep both expanded and inline hosts alive.
            if let view = visible.max(by: { $0.bounds.width * $0.bounds.height < $1.bounds.width * $1.bounds.height }) {
                return view
            }
        }
        return nil
    }
}

/// A real view gives UIKit a stable source rectangle across SwiftUI hosting boundaries.
struct PlayerTransitionAnchor: UIViewRepresentable {
    var id: String
    var sources: PlayerTransitionSources

    func makeUIView(context: Context) -> AnchorView { AnchorView() }

    func updateUIView(_ view: AnchorView, context: Context) {
        view.configure(id: id, sources: sources)
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: AnchorView, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 0, height: proposal.height ?? 0)
    }

    static func dismantleUIView(_ view: AnchorView, coordinator: ()) {
        view.unregister()
    }

    final class AnchorView: UIView {
        private weak var sources: PlayerTransitionSources?
        private var sourceID = ""

        init() {
            super.init(frame: .zero)
            backgroundColor = .clear
            isUserInteractionEnabled = false
            isAccessibilityElement = false
            accessibilityElementsHidden = true
        }

        required init?(coder: NSCoder) { nil }

        func configure(id: String, sources: PlayerTransitionSources) {
            if sourceID != id || self.sources !== sources { unregister() }
            sourceID = id
            self.sources = sources
            if window != nil { sources.register(self, id: id) }
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window != nil { sources?.register(self, id: sourceID) }
            else { unregister() }
        }

        func unregister() { sources?.unregister(self, id: sourceID) }
    }
}

/// Uses the system zoom with an explicit source view rather than SwiftUI's
/// source lookup, which can lose views hosted by the native tab accessory.
struct NativeZoomPresentation<Content: View>: UIViewControllerRepresentable {
    @Binding var isPresented: Bool
    var sourceID: String
    var sources: PlayerTransitionSources
    var reduceMotion: Bool
    var detents: [UISheetPresentationController.Detent]? = nil
    @ViewBuilder var content: () -> Content

    func makeUIViewController(context: Context) -> PresenterController {
        PresenterController()
    }

    func updateUIViewController(_ controller: PresenterController, context: Context) {
        let presentation = $isPresented
        controller.configure(
            requested: isPresented, sourceID: sourceID, sources: sources,
            reduceMotion: reduceMotion, detents: detents, content: content()
        ) { presentation.wrappedValue = $0 }
    }

    static func dismantleUIViewController(_ controller: PresenterController, coordinator: ()) {
        controller.tearDown()
    }

    final class PresenterController: UIViewController, UIAdaptivePresentationControllerDelegate {
        private var requested = false
        private var sourceID = ""
        private var sources: PlayerTransitionSources?
        private var reduceMotion = false
        private var detents: [UISheetPresentationController.Detent]?
        private var content: Content?
        private var onPresentationChange: ((Bool) -> Void)?
        private var hostingController: PlayerHostingController?
        private var isPresenting = false
        private var isDismissing = false
        private var isTornDown = false
        private var sourceObserver: UUID?
        private var sourceWaitID: UUID?
        private var sourceDeadline: DispatchWorkItem?
        private var sourceRetryScheduled = false

        override func loadView() {
            let view = UIView()
            view.backgroundColor = .clear
            view.isUserInteractionEnabled = false
            view.isAccessibilityElement = false
            self.view = view
        }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            reconcilePresentation()
        }

        func configure(
            requested: Bool, sourceID: String, sources: PlayerTransitionSources,
            reduceMotion: Bool, detents: [UISheetPresentationController.Detent]?,
            content: Content, onPresentationChange: @escaping (Bool) -> Void
        ) {
            if self.sources !== sources { cancelSourceWait() }
            self.requested = requested
            self.sourceID = sourceID
            self.sources = sources
            self.reduceMotion = reduceMotion
            self.detents = detents
            self.onPresentationChange = onPresentationChange
            if requested {
                self.content = content
                if !isDismissing { hostingController?.rootView = content }
            }
            reconcilePresentation()
        }

        private func reconcilePresentation() {
            guard !isTornDown, isViewLoaded, view.window != nil else { return }
            if requested {
                beginPresentation()
            } else {
                cancelSourceWait()
                guard let host = hostingController, !isDismissing, !isPresenting else { return }
                isDismissing = true
                (host.presentingViewController ?? host).dismiss(animated: !reduceMotion) { [weak self, weak host] in
                    guard let host else { return }
                    self?.didDismiss(host)
                }
            }
        }

        private func beginPresentation(allowMissingSource: Bool = false) {
            guard requested, !isTornDown, hostingController == nil,
                  isViewLoaded, view.window != nil, let content, let sources
            else { return }
            let selectedSource = sourceID
            let hasSource = sources.view(for: selectedSource) != nil
            if !reduceMotion, !hasSource, !allowMissingSource {
                waitForSource(sources)
                return
            }
            cancelSourceWait()
            let host = PlayerHostingController(rootView: content)
            host.overrideUserInterfaceStyle = .dark
            host.view.accessibilityViewIsModal = true
            host.modalPresentationCapturesStatusBarAppearance = true
            // Keep the original source mounted while the player is open.
            host.modalPresentationStyle = detents == nil ? .overFullScreen : .pageSheet
            if let detents, let sheet = host.sheetPresentationController {
                sheet.detents = detents
                sheet.prefersGrabberVisible = true
            }
            if !reduceMotion, hasSource {
                host.preferredTransition = .zoom { [weak sources] _ in
                    sources?.view(for: selectedSource)
                }
            } else {
                host.preferredTransition = .crossDissolve
            }
            host.onDismissalCompleted = { [weak self, weak host] in
                guard let host else { return }
                self?.didDismiss(host)
            }
            hostingController = host
            isPresenting = true
            present(host, animated: !reduceMotion) { [weak self, weak host] in
                guard let self, let host, self.hostingController === host else { return }
                self.isPresenting = false
                self.reconcilePresentation()
            }
            host.presentationController?.delegate = self
        }

        private func waitForSource(_ sources: PlayerTransitionSources) {
            guard sourceWaitID == nil else { return }
            let waitID = UUID()
            sourceWaitID = waitID
            // SwiftUI briefly replaces the accessory's host when presentation
            // changes. Resume when its new anchor is mounted, after that update.
            sourceObserver = sources.observeRegistration { [weak self] in
                guard let self, self.sourceWaitID == waitID, !self.sourceRetryScheduled else { return }
                self.sourceRetryScheduled = true
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.sourceWaitID == waitID else { return }
                    self.sourceRetryScheduled = false
                    self.beginPresentation()
                }
            }
            let deadline = DispatchWorkItem { [weak self] in
                guard let self, self.sourceWaitID == waitID else { return }
                self.beginPresentation(allowMissingSource: true)
            }
            sourceDeadline = deadline
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2, execute: deadline)
        }

        private func cancelSourceWait() {
            if let sourceObserver { sources?.removeObserver(sourceObserver) }
            sourceObserver = nil
            sourceWaitID = nil
            sourceRetryScheduled = false
            sourceDeadline?.cancel()
            sourceDeadline = nil
        }

        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
            guard let host = presentationController.presentedViewController as? PlayerHostingController else { return }
            didDismiss(host)
        }

        private func didDismiss(_ host: PlayerHostingController) {
            guard hostingController === host else { return }
            host.onDismissalCompleted = nil
            hostingController = nil
            isPresenting = false
            let shouldReopen = isDismissing && requested
            isDismissing = false
            if shouldReopen {
                DispatchQueue.main.async { [weak self] in self?.reconcilePresentation() }
                return
            }
            requested = false
            onPresentationChange?(false)
        }

        func tearDown() {
            isTornDown = true
            cancelSourceWait()
            onPresentationChange = nil
            hostingController?.onDismissalCompleted = nil
            if let host = hostingController {
                (host.presentingViewController ?? host).dismiss(animated: false)
            }
            hostingController = nil
        }
    }

    final class PlayerHostingController: UIHostingController<Content> {
        var onDismissalCompleted: (() -> Void)?

        override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            guard isBeingDismissed, let coordinator = transitionCoordinator else { return }
            coordinator.animate(alongsideTransition: nil) { [weak self] context in
                // An interrupted native gesture must leave the player presented.
                if !context.isCancelled { self?.onDismissalCompleted?() }
            }
        }

        override func viewDidDisappear(_ animated: Bool) {
            super.viewDidDisappear(animated)
            if presentingViewController == nil { onDismissalCompleted?() }
        }
    }
}

private struct PlayerTransitionContextKey: EnvironmentKey {
    static let defaultValue = PlayerTransitionContext()
}

extension EnvironmentValues {
    var playerTransitionContext: PlayerTransitionContext {
        get { self[PlayerTransitionContextKey.self] }
        set { self[PlayerTransitionContextKey.self] = newValue }
    }
}

private struct PlayerTransitionSourceModifier: ViewModifier {
    @Environment(\.playerTransitionContext) private var context
    var id: String

    @ViewBuilder
    func body(content: Content) -> some View {
        content.background {
            if let sources = context.sources {
                PlayerTransitionAnchor(id: id, sources: sources)
            }
        }
    }
}

extension View {
    func nowPlayingTransitionSource(_ id: String) -> some View {
        modifier(PlayerTransitionSourceModifier(id: id))
    }
}
