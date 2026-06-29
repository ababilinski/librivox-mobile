import LibriVoxCore
import SwiftUI

struct LibraryView: View {
    @Environment(AppModel.self) private var app
    @State private var searchText = ""

    var visibleBooks: [AudioBook] {
        let base = app.settings.downloadsOnlyModeEnabled
            ? app.libraryBooks.filter { book in app.downloads.contains { $0.bookID == book.id } }
            : app.libraryBooks
        guard !searchText.isEmpty else { return base }
        return base.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
                $0.author.localizedCaseInsensitiveContains(searchText) ||
                $0.narrators.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        List {
            if app.settings.downloadsOnlyModeEnabled {
                Section {
                    Label("Downloads only mode is on", systemImage: "arrow.down.circle")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Saved Books") {
                if visibleBooks.isEmpty {
                    ContentUnavailableView(
                        app.settings.downloadsOnlyModeEnabled ? "No Downloaded Books" : "No Books",
                        systemImage: "books.vertical",
                        description: Text("Saved audiobooks will appear here.")
                    )
                } else {
                    ForEach(visibleBooks) { book in
                        NavigationLink(value: book) {
                            BookRow(book: book, subtitle: book.author)
                        }
                        .contextMenu {
                            Button {
                                app.playback.play(book: book, chapter: book.chapters.first)
                                app.showingNowPlaying = true
                            } label: {
                                Label("Play", systemImage: "play.fill")
                            }
                            NavigationLink("Details", value: book)
                            NavigationLink("Read", destination: ReaderView(book: book))
                            Button {
                                app.enqueue(book)
                            } label: {
                                Label("Add To Queue", systemImage: "text.line.last.and.arrowtriangle.forward")
                            }
                            Button {
                                Task { await app.enqueueDownload(book) }
                            } label: {
                                Label("Download", systemImage: "arrow.down.circle")
                            }
                            if let url = sourceURL(for: book) {
                                ShareLink(item: url) {
                                    Label("Share", systemImage: "square.and.arrow.up")
                                }
                            }
                        }
                    }
                }
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Library")
        .searchable(text: $searchText, prompt: "Search library")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    DownloadsView()
                } label: {
                    Image(systemName: "arrow.down.circle")
                }
            }
        }
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
    }

    private func sourceURL(for book: AudioBook) -> URL? {
        [
            book.libriVoxURL,
            book.lit2GoURL,
            book.wolneLekturyURL,
            book.gutenbergURL,
        ]
        .compactMap { $0 }
        .compactMap(URL.init(string:))
        .first
    }
}

struct DownloadsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List {
            if app.downloads.isEmpty {
                ContentUnavailableView(
                    "No Downloads",
                    systemImage: "arrow.down.circle",
                    description: Text("Downloaded audiobook files will appear here.")
                )
            } else {
                ForEach(app.downloads) { item in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.title)
                                Text(item.state.rawValue.capitalized)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(Int(item.progress * 100))%")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: item.progress)
                    }
                    .contextMenu {
                        Button(role: .destructive) {} label: {
                            Label("Delete Download", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Downloads")
    }
}
