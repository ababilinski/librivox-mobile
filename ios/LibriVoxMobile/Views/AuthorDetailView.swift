import LibriVoxCore
import SwiftUI
import UIKit

struct AuthorDetailView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent

    var seedBook: AudioBook

    @State private var profile: AuthorProfile?
    @State private var workMetrics: [AuthorWorkMetric] = []
    @State private var authorBooks: [AudioBook] = []
    @State private var selectedSort: AuthorBookSort = .alphabetical
    @State private var isLoading = true
    @State private var actionMenuBook: AudioBook?
    @State private var actionMenuAnchor: CGPoint?
    @State private var detailBook: AudioBook?
    @State private var detailCollection: BookDetailCollection?
    @State private var readerBook: AudioBook?
    @State private var selectedRelatedAuthor: AudioBook?

    private var displayedSeedBook: AudioBook {
        app.latestKnownVersion(of: seedBook)
    }

    private var authorName: String {
        displayedSeedBook.author.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var enabledAuthorBooks: [AudioBook] {
        authorBooks.filter(app.isEnabledRecommendation)
    }

    private var sortedBooks: [AudioBook] {
        AuthorBookSorter.sorted(enabledAuthorBooks, by: selectedSort, metrics: workMetrics)
    }

    private var availableSorts: [AuthorBookSort] {
        AuthorBookSorter.availableSorts(for: enabledAuthorBooks, metrics: workMetrics)
    }

    private var relatedAuthors: [AudioBook] {
        app.relatedAuthorRepresentatives(for: displayedSeedBook)
            .filter(app.isEnabledRecommendation)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                authorHeader
                authorBooksSection
                if !relatedAuthors.isEmpty {
                    relatedAuthorsSection
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 140)
        }
        .scrollEdgeEffectStyle(.soft, for: .top)
        .background {
            GlassArtworkBackground(book: displayedSeedBook)
        }
        .navigationTitle(authorName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .overlay {
            if let actionMenuBook {
                AppleMusicBookActionsOverlay(
                    book: actionMenuBook,
                    context: .home,
                    anchorPoint: actionMenuAnchor,
                    onDismiss: dismissBookActions,
                    onShowDetails: {
                        detailCollection = BookDetailCollection(title: "Books by \(authorName)", books: sortedBooks)
                        detailBook = actionMenuBook
                    },
                    onRead: actionMenuBook.canReadSourceText ? {
                        readerBook = actionMenuBook
                    } : nil
                )
            }
        }
        .navigationDestination(item: $detailBook) { book in
            BookDetailBrowser(book: book, collection: detailCollection)
        }
        .navigationDestination(item: $selectedRelatedAuthor) { book in
            AuthorDetailView(seedBook: book)
        }
        .sheet(item: $readerBook) { book in
            NavigationStack {
                ReaderView(book: book)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") { readerBook = nil }
                        }
                    }
            }
        }
        .task(id: authorTaskID) {
            await loadAuthor()
        }
    }

    private var authorTaskID: String {
        [
            displayedSeedBook.id,
            authorName,
            app.settings.enabledSources.map(\.rawValue).sorted().joined(separator: ","),
            app.settings.preferredLanguages.map(\.rawValue).sorted().joined(separator: ","),
            app.settings.sourcePriority.map(\.rawValue).joined(separator: ","),
        ].joined(separator: "|")
    }

    private var authorHeader: some View {
        VStack(spacing: 16) {
            AuthorPortraitView(
                authorName: authorName,
                profile: profile,
                width: 184,
                height: 220,
                cornerRadius: 28
            )
            .shadow(color: .black.opacity(0.2), radius: 22, y: 12)

            VStack(spacing: 10) {
                Text(authorName)
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("author-profile-name")

                if let profile {
                    if let lifeDates = lifeDates(for: profile) {
                        Text(lifeDates)
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(.secondary)
                    }

                    if let biography = profile.biography?.trimmingCharacters(in: .whitespacesAndNewlines),
                       !biography.isEmpty {
                        NativeSelectableText(
                            text: biography,
                            textStyle: .body,
                            color: .secondaryLabel,
                            alignment: .center,
                            accessibilityIdentifier: "author-biography"
                        )
                    } else if let topWork = profile.topWork {
                        NativeSelectableText(
                            text: "Known for \(topWork).",
                            textStyle: .body,
                            color: .secondaryLabel,
                            alignment: .center,
                            accessibilityIdentifier: "author-biography"
                        )
                    }

                    Link(destination: profile.sourceURL) {
                        Label("Author record on Open Library", systemImage: "arrow.up.right.square")
                            .font(.subheadline.weight(.semibold))
                    }
                    .foregroundStyle(accent)
                } else if isLoading {
                    HStack(spacing: 8) {
                        ProgressView()
                            .controlSize(.small)
                        Text("Finding author details…")
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                } else {
                    Text("A verified biography or portrait was not available. Books from the enabled catalogs are shown below.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
    }

    private var authorBooksSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                Text("Books by \(authorName)")
                    .font(.title2.bold())
                    .accessibilityIdentifier("author-books-heading")

                Spacer(minLength: 0)

                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Menu {
                        Picker("Sort books", selection: $selectedSort) {
                            ForEach(availableSorts) { sort in
                                Label(sort.label, systemImage: sort.systemImage)
                                    .tag(sort)
                            }
                        }
                    } label: {
                        Label(selectedSort.label, systemImage: "arrow.up.arrow.down")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                    .accessibilityIdentifier("author-sort-menu")
                }
            }

            if sortedBooks.isEmpty, !isLoading {
                ContentUnavailableView(
                    "No Books Found",
                    systemImage: "books.vertical",
                    description: Text("Try searching the enabled catalogs for this author.")
                )
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(Array(sortedBooks.enumerated()), id: \.element.id) { index, book in
                        AnchoredBookNavigationLink(
                            book: book,
                            onActions: showBookActions(for:anchor:),
                            destination: {
                                BookDetailBrowser(
                                    book: book,
                                    collection: BookDetailCollection(title: "Books by \(authorName)", books: sortedBooks)
                                )
                            },
                            label: {
                                AuthorBookRow(
                                    book: book,
                                    metric: metric(for: book)
                                )
                            }
                        )
                        .accessibilityIdentifier("author-book-\(book.id)")
                        .accessibilityHint("Touch and hold for book actions")

                        if index < sortedBooks.count - 1 {
                            Divider()
                                .padding(.leading, 84)
                                .opacity(0.38)
                        }
                    }
                }
                .bookDetailCollection(sortedBooks, title: "Books by \(authorName)")
            }

            Button {
                app.openDiscoverSearch(query: authorName, field: .author)
            } label: {
                Label("Search all enabled catalogs", systemImage: "magnifyingglass")
                    .font(.subheadline.weight(.semibold))
            }
            .buttonStyle(.plain)
            .foregroundStyle(accent)
            .frame(minHeight: 44)
        }
    }

    private var relatedAuthorsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Related Authors")
                .font(.title2.bold())

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(relatedAuthors) { book in
                        Button {
                            selectedRelatedAuthor = book
                        } label: {
                            RelatedAuthorCard(book: book)
                                .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                selectedRelatedAuthor = book
                            } label: {
                                Label("View Author Details", systemImage: "person.text.rectangle")
                            }
                            Button {
                                app.openDiscoverSearch(query: book.author, field: .author)
                            } label: {
                                Label("Search Books", systemImage: "magnifyingglass")
                            }
                            Button {
                                UIPasteboard.general.string = book.author
                            } label: {
                                Label("Copy Author Name", systemImage: "doc.on.doc")
                            }
                        } preview: {
                            AuthorContextPreview(book: book)
                        }
                        .accessibilityIdentifier("related-author-\(authorIdentifier(book.author))")
                        .accessibilityHint("Touch and hold for author details")
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    @MainActor
    private func loadAuthor() async {
        let requestID = authorTaskID
        let book = displayedSeedBook
        let name = authorName
        authorBooks = mergedAuthorBooks(
            [book] + app.cachedRelatedBooksByAuthor(for: book, limit: 50)
        )
        isLoading = true

        async let profileResult = OpenLibraryAuthorClient.shared.profile(for: name)
        async let metricsResult = OpenLibraryAuthorClient.shared.workMetrics(for: name)
        let fetchedBooks = await app.relatedBooksByAuthor(for: book, limit: 50)
        guard !Task.isCancelled, authorTaskID == requestID else { return }

        authorBooks = mergedAuthorBooks([book] + fetchedBooks)
        isLoading = false

        let (loadedProfile, loadedMetrics) = await (profileResult, metricsResult)
        guard !Task.isCancelled, authorTaskID == requestID else { return }
        profile = loadedProfile
        workMetrics = loadedMetrics
        if !availableSorts.contains(selectedSort) {
            selectedSort = .alphabetical
        }
    }

    private func mergedAuthorBooks(_ books: [AudioBook]) -> [AudioBook] {
        var seen = Set<String>()
        return books
            .map(app.latestKnownVersion(of:))
            .filter(app.isEnabledRecommendation)
            .filter { seen.insert($0.id).inserted }
    }

    private func metric(for book: AudioBook) -> AuthorWorkMetric? {
        let normalized = normalizedTitle(book.title)
        return workMetrics.first { normalizedTitle($0.title) == normalized }
    }

    private func normalizedTitle(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    private func lifeDates(for profile: AuthorProfile) -> String? {
        let values = [profile.birthDate, profile.deathDate]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return values.isEmpty ? nil : values.joined(separator: " – ")
    }

    private func authorIdentifier(_ value: String) -> String {
        value
            .folding(options: [.caseInsensitive, .diacriticInsensitive], locale: .current)
            .replacingOccurrences(of: " ", with: "-")
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

private struct AuthorBookRow: View {
    var book: AudioBook
    var metric: AuthorWorkMetric?

    private var readers: [String] {
        Array(Set(book.narrators + book.chapters.compactMap(\.reader))).sorted()
    }

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            BookCoverView(
                book: book,
                width: 68,
                height: 92,
                cornerRadius: 12,
                preferFullImage: true,
                showsDownloadBadge: false
            )

            VStack(alignment: .leading, spacing: 5) {
                Text(book.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                HStack(spacing: 7) {
                    if let year = metric?.firstPublishYear {
                        Label(String(year), systemImage: "calendar")
                    } else if let releaseDate = book.releaseDate?.trimmingCharacters(in: .whitespacesAndNewlines),
                              !releaseDate.isEmpty {
                        Label(releaseDate, systemImage: "calendar")
                    }
                    if book.totalDurationSeconds > 0 {
                        Label(formatDuration(seconds: book.totalDurationSeconds), systemImage: "clock")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                if !readers.isEmpty {
                    Text("Read by \(readers.joined(separator: ", "))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                Text(book.source.label)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.tertiary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: "chevron.forward")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
                .padding(.top, 8)
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

private struct RelatedAuthorCard: View {
    var book: AudioBook

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            AuthorPortraitView(
                authorName: book.author,
                width: 58,
                height: 58,
                cornerRadius: 29
            )
            Text(book.author)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2)
            Text(book.genres.first ?? book.source.label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(width: 150, height: 126, alignment: .topLeading)
        .padding(14)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(.white.opacity(0.2), lineWidth: 0.7)
        }
    }
}

private struct AuthorContextPreview: View {
    var book: AudioBook
    @State private var profile: AuthorProfile?

    var body: some View {
        VStack(spacing: 12) {
            AuthorPortraitView(
                authorName: book.author,
                profile: profile,
                width: 132,
                height: 156,
                cornerRadius: 22
            )
            Text(book.author)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            if let profile, let topWork = profile.topWork {
                Text("Known for \(topWork)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
            } else {
                Text(book.genres.first ?? book.source.label)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(20)
        .frame(width: 280)
        .task(id: book.author) {
            profile = await OpenLibraryAuthorClient.shared.profile(for: book.author)
        }
    }
}

private struct AuthorPortraitView: View {
    var authorName: String
    var profile: AuthorProfile? = nil
    var width: CGFloat
    var height: CGFloat
    var cornerRadius: CGFloat

    @State private var loadedProfile: AuthorProfile?

    private var displayedProfile: AuthorProfile? {
        profile ?? loadedProfile
    }

    var body: some View {
        Group {
            if let portraitURL = displayedProfile?.portraitURL {
                AsyncImage(url: portraitURL) { phase in
                    switch phase {
                    case let .success(image):
                        image
                            .resizable()
                            .scaledToFill()
                    case .empty:
                        ZStack {
                            placeholder
                            ProgressView()
                                .controlSize(.small)
                        }
                    case .failure:
                        fallbackPortrait
                    @unknown default:
                        fallbackPortrait
                    }
                }
            } else {
                fallbackPortrait
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .accessibilityLabel("Portrait of \(authorName)")
        .task(id: authorName) {
            if profile == nil {
                loadedProfile = await OpenLibraryAuthorClient.shared.profile(for: authorName)
            }
        }
    }

    @ViewBuilder
    private var fallbackPortrait: some View {
        if let fallbackURL = displayedProfile?.fallbackPortraitURL {
            AsyncImage(url: fallbackURL) { phase in
                switch phase {
                case let .success(image):
                    image
                        .resizable()
                        .scaledToFill()
                case .empty:
                    ZStack {
                        placeholder
                        ProgressView()
                            .controlSize(.small)
                    }
                case .failure:
                    placeholder
                @unknown default:
                    placeholder
                }
            }
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        ZStack {
            LinearGradient(
                colors: [Color.primary.opacity(0.16), Color.primary.opacity(0.07)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Text(initials)
                .font(.system(size: min(width, height) * 0.3, weight: .semibold, design: .rounded))
                .foregroundStyle(.secondary)
        }
    }

    private var initials: String {
        let words = authorName.split(separator: " ").filter { !$0.isEmpty }
        return words.prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
    }
}
