import LibriVoxCore
import SwiftUI

private enum DiscoverSortMode: String, CaseIterable, Identifiable {
    case relevance
    case title
    case author

    var id: String { rawValue }
    var label: String { rawValue.capitalized }
}

struct DiscoverView: View {
    @Environment(AppModel.self) private var app
    @State private var query = ""
    @State private var field: CatalogSearchField = .all
    @State private var source: BookSource = .libriVox
    @State private var language: BookLanguagePreference = .english
    @State private var sortMode: DiscoverSortMode = .relevance

    private var availableSources: [BookSource] {
        [.libriVox, .lit2Go, .gutendex, .wolneLektury].filter { app.settings.enabledSources.contains($0) }
    }

    private var availableLanguages: [BookLanguagePreference] {
        var languages: [BookLanguagePreference] = [.all, .english, .french, .german, .italian, .spanish]
        if app.settings.enabledSources.contains(.wolneLektury) {
            languages.append(.polish)
        }
        return languages
    }

    private var visibleResults: [AudioBook] {
        switch sortMode {
        case .relevance:
            app.searchResults
        case .title:
            app.searchResults.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .author:
            app.searchResults.sorted { $0.author.localizedCaseInsensitiveCompare($1.author) == .orderedAscending }
        }
    }

    var body: some View {
        List {
            if !app.isOnline {
                Section {
                    Label("Showing available cached catalog data", systemImage: "wifi.slash")
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Picker("Search field", selection: $field) {
                    ForEach(CatalogSearchField.allCases) { field in
                        Text(field.rawValue.capitalized).tag(field)
                    }
                }
                .pickerStyle(.segmented)

                Picker("Source", selection: $source) {
                    ForEach(availableSources) { source in
                        Text(source.label).tag(source)
                    }
                }

                Picker("Language", selection: $language) {
                    ForEach(availableLanguages) { language in
                        Text(language.label).tag(language)
                    }
                }

                Picker("Sort", selection: $sortMode) {
                    ForEach(DiscoverSortMode.allCases) { sortMode in
                        Text(sortMode.label).tag(sortMode)
                    }
                }
            }

            if query.isEmpty && !app.searchHistory.isEmpty {
                Section("Recent Searches") {
                    ForEach(app.searchHistory, id: \.self) { search in
                        Button {
                            query = search
                            Task { await runSearch() }
                        } label: {
                            Label(search, systemImage: "clock.arrow.circlepath")
                        }
                    }
                }
            }

            Section(query.isEmpty ? "Browse" : "Results") {
                if visibleResults.isEmpty {
                    ContentUnavailableView(
                        "No Results",
                        systemImage: "magnifyingglass",
                        description: Text("Try another query, source, or language.")
                    )
                } else {
                    ForEach(visibleResults) { book in
                        NavigationLink(value: book) {
                            BookRow(book: book, subtitle: book.author)
                        }
                    }
                }
            }
        }
        .navigationTitle("Discover")
        .searchable(text: $query, prompt: "Title, author, chapter, or reader")
        .onAppear {
            normalizeFilters()
            Task { await runSearch() }
        }
        .onChange(of: query) { _, _ in
            Task { await runSearch() }
        }
        .onChange(of: field) { _, _ in
            Task { await runSearch() }
        }
        .onChange(of: source) { _, _ in
            Task { await runSearch() }
        }
        .onChange(of: language) { _, _ in
            Task { await runSearch() }
        }
        .onChange(of: app.settings.enabledSources) { _, _ in
            normalizeFilters()
            Task { await runSearch() }
        }
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
    }

    @MainActor
    private func runSearch() async {
        await app.search(query: query, field: field, sources: [source], languages: [language])
    }

    private func normalizeFilters() {
        if !availableSources.contains(source) {
            source = availableSources.first ?? .libriVox
        }
        if !availableLanguages.contains(language) {
            language = .english
        }
    }
}
