import LibriVoxCore
import SwiftUI

private enum DiscoverSortMode: String, CaseIterable, Identifiable {
    case relevance
    case title
    case author

    var id: String { rawValue }

    var label: String {
        switch self {
        case .relevance: "Relevance"
        case .title: "Title"
        case .author: "Author"
        }
    }
}

private enum DiscoverSourceFilter: Hashable, Identifiable {
    case all
    case source(BookSource)

    var id: String {
        switch self {
        case .all:
            "all"
        case .source(let source):
            source.id
        }
    }

    var label: String {
        switch self {
        case .all:
            "All sources"
        case .source(let source):
            source.label
        }
    }

    var pillLabel: String {
        switch self {
        case .all:
            "All"
        case .source(let source):
            source.label
        }
    }

    func sources(from availableSources: [BookSource]) -> Set<BookSource> {
        switch self {
        case .all:
            Set(availableSources)
        case .source(let source):
            [source]
        }
    }
}

struct DiscoverView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Binding var query: String
    @Binding var nativeSearchPresented: Bool
    let searchSubmissionID: UUID
    @State private var field: CatalogSearchField = .all
    @State private var sourceFilter: DiscoverSourceFilter = .all
    @State private var language: BookLanguagePreference = .all
    @State private var sortMode: DiscoverSortMode = .relevance
    @State private var scope: BookSearchScope = .everything
    @State private var searchTask: Task<Void, Never>?
    @State private var pendingSearchID: UUID?

    private var availableSources: [BookSource] {
        let catalogs = effectiveScope == .library ? [] : app.settings.orderedEnabledSources
        let localSources = effectiveScope == .catalogs ? [] : app.searchableLocalBooks.map(\.source)
        let order = app.settings.sourcePriority + BookSource.allCases.filter { !app.settings.sourcePriority.contains($0) }
        return order.filter { catalogs.contains($0) || localSources.contains($0) }
    }

    private var effectiveScope: BookSearchScope {
        app.settings.downloadsOnlyModeEnabled ? .library : scope
    }

    private var catalogSources: Set<BookSource> {
        sourceFilter.sources(from: availableSources).intersection(app.settings.enabledSources)
    }

    private var unifiedResults: [UnifiedBookSearchResult] {
        guard !queryIsEmpty else { return [] }
        let selectedSources: Set<BookSource>?
        switch sourceFilter {
        case .all: selectedSources = nil
        case .source(let source): selectedSources = [source]
        }
        let remoteRequest = CatalogSearchRequest(
            query: query, field: field, languages: [language], sources: catalogSources,
            sourcePriority: app.settings.sourcePriority
        )
        let remote = app.searchResultsRequest == remoteRequest ? app.searchResults : []
        return UnifiedBookSearch.results(
            libraryBooks: app.searchableLocalBooks,
            catalogBooks: remote,
            query: query, field: field, languages: [language], sources: selectedSources,
            scope: effectiveScope
        )
    }

    private var availableSourceFilters: [DiscoverSourceFilter] {
        [.all] + availableSources.map { .source($0) }
    }

    private var availableLanguages: [BookLanguagePreference] {
        if sourceFilter == .source(.wolneLektury) {
            return [.all, .polish]
        }
        if sourceFilter == .source(.lit2Go) {
            return [.all, .english]
        }
        var languages: [BookLanguagePreference] = [.all, .english, .french, .german, .italian, .spanish]
        if effectiveScope != .catalogs || availableSources.contains(.wolneLektury) {
            languages.append(.polish)
        }
        return languages
    }

    private var visibleResults: [AudioBook] {
        let books = queryIsEmpty ? app.featuredBooks : visibleSearchResults.map(\.book)
        switch sortMode {
        case .relevance:
            return books
        case .title:
            return books.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .author:
            return books.sorted { $0.author.localizedCaseInsensitiveCompare($1.author) == .orderedAscending }
        }
    }

    private var visibleSearchResults: [UnifiedBookSearchResult] {
        let matches = unifiedResults
        switch sortMode {
        case .relevance: return matches
        case .title: return matches.sorted { $0.book.title.localizedCaseInsensitiveCompare($1.book.title) == .orderedAscending }
        case .author: return matches.sorted { $0.book.author.localizedCaseInsensitiveCompare($1.book.author) == .orderedAscending }
        }
    }

    private var gridColumns: [GridItem] {
        [GridItem(.adaptive(minimum: 150), spacing: 18, alignment: .top)]
    }

    private var searchIsActive: Bool {
        nativeSearchPresented || !queryIsEmpty
    }

    private var queryIsEmpty: Bool {
        query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var recentSearchesAreVisible: Bool {
        searchIsActive && queryIsEmpty && !app.searchHistory.isEmpty
    }

    private var sourceFilterPillsAreVisible: Bool {
        availableSources.count > 1
    }

    private var searchIsLoading: Bool {
        if queryIsEmpty {
            return app.isFeaturedBooksLoading
        }
        return effectiveScope != .library && (pendingSearchID != nil || app.isSearching)
    }

    private var searchProgressIsVisible: Bool {
        !queryIsEmpty && effectiveScope != .library && !app.searchProgressItems.isEmpty
    }

    private var resultsSubtitle: String {
        if queryIsEmpty {
            return app.isFeaturedBooksLoading ? "Refreshing catalog..." : "Fresh public-domain recordings"
        }
        if searchIsLoading {
            let completed = app.searchProgressItems.filter(\.isComplete).count
            let total = app.searchProgressItems.count
            if !visibleResults.isEmpty, total > 0 {
                return "\(visibleResults.count) result\(visibleResults.count == 1 ? "" : "s") so far · \(completed) of \(total) searches complete"
            }
            return refreshingStatusText
        }
        return "\(visibleResults.count) matching audiobook\(visibleResults.count == 1 ? "" : "s")"
    }

    private var usesListResults: Bool {
        dynamicTypeSize.isAccessibilitySize || !queryIsEmpty
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            DiscoverSurfaceBackground()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if searchIsActive {
                        searchScopePicker
                        searchControlStack
                    }

                    if !app.isOnline {
                        Label(
                            queryIsEmpty ? "Offline" : "Offline · showing saved results when available",
                            systemImage: "wifi.slash"
                        )
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .accessibilityIdentifier("OfflineCatalogStatus")
                    }

                    if recentSearchesAreVisible {
                        recentSearches
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    resultsSection
                }
                .padding()
                .padding(.bottom, 220)
                .frame(maxWidth: .infinity, alignment: .leading)
                .animation(.snappy, value: recentSearchesAreVisible)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.large)
        .onChange(of: searchSubmissionID) { _, _ in
            submitCurrentSearch()
        }
        .toolbar {
            if searchIsActive {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Browse", systemImage: "chevron.left") {
                        returnToBrowse()
                    }
                    .accessibilityLabel("Back to browse")
                    .accessibilityIdentifier("search-back-to-browse")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                SettingsToolbarButton()
            }
        }
        .onAppear {
            normalizeFilters()
            if !handlePendingNavigationSearch() {
                scheduleSearch(immediate: true)
            }
        }
        .onChange(of: query) { _, _ in scheduleSearch() }
        .onChange(of: field) { _, _ in scheduleSearch(immediate: true) }
        .onChange(of: scope) { _, _ in
            normalizeFilters()
            scheduleSearch(immediate: true)
        }
        .onChange(of: app.settings.downloadsOnlyModeEnabled) { _, _ in
            normalizeFilters()
            scheduleSearch(immediate: true)
        }
        .onChange(of: sourceFilter) { _, _ in
            normalizeFilters()
            scheduleSearch(immediate: true)
        }
        .onChange(of: language) { _, _ in scheduleSearch(immediate: true) }
        .onChange(of: app.settings.enabledSources) { _, _ in
            normalizeFilters()
            scheduleSearch(immediate: true)
        }
        .onChange(of: app.settings.sourcePriority) { _, _ in
            normalizeFilters()
            scheduleSearch(immediate: true)
        }
        .onChange(of: app.settings.preferredLanguages) { _, _ in
            normalizeFilters(preferSettingsLanguage: true)
            scheduleSearch(immediate: true)
        }
        .onChange(of: app.discoverSearchNavigationRequest?.id) { _, _ in
            handlePendingNavigationSearch()
        }
        .onDisappear {
            searchTask?.cancel()
            pendingSearchID = nil
            app.cancelSearch()
        }
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
    }

    @ViewBuilder
    private var searchScopePicker: some View {
        if app.settings.downloadsOnlyModeEnabled {
            Label("Searching downloaded books", systemImage: "arrow.down.circle")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        } else {
            Picker("Search scope", selection: $scope) {
                Text("Everything").tag(BookSearchScope.everything)
                Text("Library").tag(BookSearchScope.library)
                Text("Catalogs").tag(BookSearchScope.catalogs)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("unified-search-scope")
        }
    }

    private var searchControlStack: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                DiscoverMenuChip(title: field.discoverLabel, systemImage: "text.magnifyingglass") {
                    Picker("Search In", selection: $field) {
                        ForEach(CatalogSearchField.allCases) { option in
                            Text(searchFieldLabel(option)).tag(option)
                        }
                    }
                }

                if sourceFilterPillsAreVisible {
                    DiscoverMenuChip(title: sourceFilter.label, systemImage: "music.note.list") {
                        Picker("Source", selection: $sourceFilter) {
                            ForEach(availableSourceFilters) { option in
                                Text(sourceFilterLabel(option)).tag(option)
                            }
                        }
                    }
                }

                DiscoverMenuChip(title: language.discoverLabel, systemImage: "globe") {
                    Picker("Language", selection: $language) {
                        ForEach(availableLanguages) { language in
                            Text(languageFilterLabel(language)).tag(language)
                        }
                    }
                }

                DiscoverMenuChip(title: sortMode.label, systemImage: "arrow.up.arrow.down") {
                    Picker("Sort", selection: $sortMode) {
                        ForEach(DiscoverSortMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
        .padding(.top, 8)
    }

    private var recentSearches: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Recent Searches")
                .font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(app.searchHistory, id: \.self) { search in
                        Button {
                            query = search
                            submitCurrentSearch(queryOverride: search)
                        } label: {
                            Label(search, systemImage: "clock.arrow.circlepath")
                                .font(.subheadline)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .discoverPill()
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var resultsSection: some View {
        let searchMatches = queryIsEmpty
            ? visibleResults.map { UnifiedBookSearchResult(book: $0, isInLibrary: false) }
            : visibleSearchResults
        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(query.isEmpty ? "Browse" : "Results")
                        .font(.title2.bold())
                        .accessibilityIdentifier(queryIsEmpty ? "search-browse-heading" : "search-results-heading")
                    Text(resultsSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)
                }
                Spacer()
            }

            if searchProgressIsVisible {
                SearchProgressStrip(items: app.searchProgressItems)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }

            if searchIsLoading && !visibleResults.isEmpty {
                SearchRefreshStatus(text: resultsSubtitle)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }

            if searchIsLoading && visibleResults.isEmpty {
                ProgressView(refreshingStatusText)
                    .frame(maxWidth: .infinity, minHeight: 220)
                    .padding()
                    .liquidGlassPanel(cornerRadius: 28, interactive: false)
            } else if visibleResults.isEmpty {
                ContentUnavailableView(
                    "No Results",
                    systemImage: "magnifyingglass",
                    description: Text("Try another query, source, or language.")
                )
                .frame(maxWidth: .infinity, minHeight: 240)
                .padding()
                .liquidGlassPanel(cornerRadius: 28, interactive: false)
            } else if usesListResults {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(searchMatches.enumerated()), id: \.element.id) { index, match in
                        SearchResultRow(
                            book: match.book,
                            isInLibrary: match.isInLibrary
                        )

                        if index < searchMatches.count - 1 {
                            Divider()
                                .padding(.leading, 74)
                        }
                    }
                }
                .bookDetailCollection(searchMatches.map(\.book), title: queryIsEmpty ? "Browse" : "Search Results")
            } else {
                LazyVGrid(columns: gridColumns, alignment: .leading, spacing: 16) {
                    ForEach(visibleResults) { book in
                        BookDetailsNavigationLink(book: book) {
                            DiscoverBookCard(book: book)
                        }
                        .buttonStyle(.plain)
                        .bookActionsContextMenu(
                            book: book,
                            context: .home,
                            previewStyle: .cover
                        )
                    }
                }
                .bookDetailCollection(visibleResults, title: queryIsEmpty ? "Browse" : "Search Results")
            }
        }
    }

    @MainActor
    private func scheduleSearch(immediate: Bool = false, recordHistory: Bool = false) {
        runSearch(
            query: query,
            field: field,
            immediate: immediate,
            recordHistory: recordHistory
        )
    }

    @MainActor
    private func returnToBrowse() {
        searchTask?.cancel()
        searchTask = nil
        pendingSearchID = nil
        app.cancelSearch()
        query = ""
        nativeSearchPresented = false
    }

    @MainActor
    @discardableResult
    private func handlePendingNavigationSearch() -> Bool {
        guard let request = app.discoverSearchNavigationRequest else { return false }
        searchTask?.cancel()
        scope = .everything
        sourceFilter = .all
        field = request.field
        query = request.query
        normalizeFilters(preferSettingsLanguage: true)
        if availableLanguages.contains(.all) {
            language = .all
        }
        app.discoverSearchNavigationRequest = nil
        submitCurrentSearch(queryOverride: request.query, fieldOverride: request.field)
        return true
    }

    @MainActor
    private func submitCurrentSearch(
        queryOverride: String? = nil,
        fieldOverride: CatalogSearchField? = nil
    ) {
        runSearch(
            query: queryOverride ?? query,
            field: fieldOverride ?? field,
            immediate: true,
            recordHistory: true
        )
    }

    @MainActor
    private func runSearch(
        query currentQuery: String,
        field currentField: CatalogSearchField,
        immediate: Bool,
        recordHistory: Bool
    ) {
        searchTask?.cancel()
        app.cancelSearch()
        pendingSearchID = nil
        if recordHistory {
            app.saveSearchHistory(currentQuery)
        }
        guard !currentQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        guard effectiveScope != .library else { return }
        let currentSources = catalogSources
        guard !currentSources.isEmpty else { return }
        let currentLanguage = language
        let searchID = UUID()
        pendingSearchID = searchID

        searchTask = Task { @MainActor in
            defer {
                if pendingSearchID == searchID {
                    pendingSearchID = nil
                }
            }

            if !immediate {
                do {
                    try await Task.sleep(for: .milliseconds(350))
                } catch {
                    return
                }
            }
            guard !Task.isCancelled else { return }
            await app.search(
                query: currentQuery,
                field: currentField,
                sources: currentSources,
                languages: [currentLanguage],
                recordHistory: false
            )
        }
    }

    private func normalizeFilters(preferSettingsLanguage: Bool = false) {
        if case .source(let source) = sourceFilter, !availableSources.contains(source) {
            sourceFilter = .all
        }
        if preferSettingsLanguage {
            language = settingsPreferredLanguageFilter
        } else if !availableLanguages.contains(language) {
            language = settingsPreferredLanguageFilter
        }
    }

    private var settingsPreferredLanguageFilter: BookLanguagePreference {
        switch sourceFilter {
        case .source(.wolneLektury):
            return .polish
        case .source(.lit2Go):
            return .english
        case .all, .source:
            break
        }

        let supportedPreferences = app.settings.preferredLanguages
            .filter { availableLanguages.contains($0) }
        if supportedPreferences.contains(.all) {
            return .all
        }
        if supportedPreferences.count > 1 {
            return .all
        }
        if let preferred = supportedPreferences.first {
            return preferred
        }
        return availableSources.contains(.wolneLektury) ? .all : .english
    }

    private var refreshingStatusText: String {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            return "Refreshing catalog..."
        }
        return "Searching \(sourceFilter.label) for \"\(trimmed)\"..."
    }

    private func searchFieldLabel(_ option: CatalogSearchField) -> String {
        label(option.discoverLabel, count: effectiveScope == .catalogs ? app.searchFacetCounts.fieldCounts[option] : nil)
    }

    private func sourceFilterLabel(_ option: DiscoverSourceFilter) -> String {
        guard effectiveScope == .catalogs else { return option.label }
        let count: Int?
        switch option {
        case .all:
            count = app.searchFacetCounts.total
        case .source(let source):
            count = app.searchFacetCounts.sourceCounts[source]
        }
        return label(option.label, count: count)
    }

    private func languageFilterLabel(_ option: BookLanguagePreference) -> String {
        label(option.discoverLabel, count: effectiveScope == .catalogs ? app.searchFacetCounts.languageCounts[option] : nil)
    }

    private func label(_ title: String, count: Int?) -> String {
        guard !queryIsEmpty, let count else { return title }
        return "\(title) (\(count))"
    }
}

private struct DiscoverSurfaceBackground: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        LibriVoxBrand.surface(for: colorScheme)
            .ignoresSafeArea()
    }
}

private struct DiscoverMenuChip<Content: View>: View {
    var title: String
    var systemImage: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        Menu {
            content()
        } label: {
            Label(title, systemImage: systemImage)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .discoverPill()
        }
        .buttonStyle(.plain)
    }
}

private struct SearchRefreshStatus: View {
    var text: String

    var body: some View {
        HStack(spacing: 8) {
            ProgressView()
                .controlSize(.small)
            Text(text)
                .font(.footnote.weight(.medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
    }
}

private struct SearchProgressStrip: View {
    var items: [SearchProgressItem]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items) { item in
                    SearchProgressPill(item: item)
                }
            }
            .padding(.vertical, 1)
        }
    }
}

private struct SearchProgressPill: View {
    var item: SearchProgressItem

    var body: some View {
        HStack(spacing: 7) {
            if item.skipReason != nil {
                Image(systemName: "minus.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            } else if item.didFail {
                Image(systemName: "exclamationmark.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            } else if item.isComplete {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            } else {
                ProgressView()
                    .controlSize(.small)
            }

            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)

            if item.isComplete, item.resultCount > 0 {
                Text("\(item.resultCount)")
                    .font(.caption2.monospacedDigit().weight(.semibold))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(.primary.opacity(0.08), in: Capsule())
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .discoverPill()
    }

    private var title: String {
        let source = item.operation.source.label
        let field = item.operation.field.discoverLabel.lowercased()
        if let skipReason = item.skipReason {
            switch skipReason {
            case .incompatibleLanguage:
                return "\(source) · incompatible language"
            case .unsupportedField:
                return "\(source) · field unavailable"
            case .unavailableSource:
                return "\(source) · unavailable"
            }
        }
        if item.operation.field == .all {
            return source
        }
        return "\(source) \(field)"
    }
}

private struct DiscoverBookCard: View {
    var book: AudioBook

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            BookCoverView(book: book, width: 148, height: 196, cornerRadius: 18, preferFullImage: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .shadow(color: .black.opacity(0.18), radius: 12, x: 0, y: 8)
            Text(book.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2, reservesSpace: true)
            Text(book.author)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1, reservesSpace: true)
            HStack(spacing: 6) {
                Text(book.source.label)
                if book.totalDurationSeconds > 0 {
                    Text("·")
                    Text(formatDuration(seconds: book.totalDurationSeconds))
                }
            }
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.tertiary)
            .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

private struct SearchResultRow: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    var isInLibrary = false

    var body: some View {
        HStack(spacing: 12) {
            BookDetailsNavigationLink(book: book) {
                HStack(spacing: 12) {
                    BookCoverView(
                        book: book,
                        width: 58,
                        height: 78,
                        cornerRadius: 10,
                        preferFullImage: true
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

                        if isInLibrary {
                            Label(
                                app.isAvailableOffline(book) ? "Downloaded" : "In Library",
                                systemImage: app.isAvailableOffline(book) ? "arrow.down.circle.fill" : "books.vertical"
                            )
                            .font(.caption.weight(.medium))
                            .foregroundStyle(.secondary)
                        }

                        HStack(spacing: 6) {
                            Text(book.source.label)
                            if book.totalDurationSeconds > 0 {
                                Text("·")
                                Text(formatDuration(seconds: book.totalDurationSeconds))
                            }
                        }
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("search-result-\(book.source.rawValue):\(book.id)")

            Spacer(minLength: 8)

            BookActionsMenuButton(book: book, context: .home) {
                Image(systemName: "ellipsis")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .accessibilityLabel("Book actions")
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .bookActionsContextMenu(
            book: book,
            context: .home,
            previewStyle: .row
        )
    }
}

private extension CatalogSearchField {
    var discoverLabel: String {
        switch self {
        case .all: "All"
        case .title: "Title"
        case .author: "Author"
        case .chapter: "Chapter"
        case .reader: "Reader"
        case .genre: "Genre"
        }
    }
}

private extension BookLanguagePreference {
    var discoverLabel: String {
        switch self {
        case .all:
            "Any language"
        default:
            label
        }
    }
}

private extension View {
    func discoverPill(isSelected: Bool = false) -> some View {
        self
            .background(.ultraThinMaterial, in: Capsule())
            .background {
                if isSelected {
                    Capsule().fill(.primary.opacity(0.05))
                }
            }
            .overlay {
                Capsule()
                    .stroke(.white.opacity(isSelected ? 0.34 : 0.2), lineWidth: 0.8)
            }
    }
}
