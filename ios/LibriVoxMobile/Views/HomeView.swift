import LibriVoxCore
import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var app

    private var enabledSourceShelves: [(BookSource, [AudioBook])] {
        [.libriVox, .lit2Go, .gutendex, .wolneLektury]
            .filter { app.settings.enabledSources.contains($0) }
            .map { source in
                (source, app.featuredBooks.filter { $0.source == source })
            }
            .filter { !$0.1.isEmpty }
    }

    var body: some View {
        ZStack {
            GlassArtworkBackground(book: app.defaultListeningBook)

            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    if !app.isOnline || app.settings.downloadsOnlyModeEnabled {
                        Label(
                            app.settings.downloadsOnlyModeEnabled ? "Downloads only mode is on" : "You are offline",
                            systemImage: app.settings.downloadsOnlyModeEnabled ? "arrow.down.circle" : "wifi.slash"
                        )
                        .foregroundStyle(.secondary)
                        .padding()
                        .liquidGlassPanel(cornerRadius: 18, interactive: false)
                    }

                    if let book = app.defaultListeningBook {
                        ContinueListeningView(book: book)
                    }

                    BookShelfView(title: "Your Library", books: app.libraryBooks)

                    ForEach(enabledSourceShelves, id: \.0) { source, books in
                        BookShelfView(title: source.label, books: books)
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Featured")
                            .font(.title2.bold())
                        ForEach(app.featuredBooks) { book in
                            NavigationLink(value: book) {
                                BookRow(book: book, subtitle: book.author)
                                    .padding(10)
                                    .liquidGlassPanel(cornerRadius: 20, interactive: false)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding()
                .padding(.bottom, 144)
            }
        }
        .navigationTitle("Home")
        .toolbarBackground(.hidden, for: .navigationBar)
        .navigationDestination(for: AudioBook.self) { book in
            BookDetailView(book: book)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    app.showingNowPlaying = true
                } label: {
                    Image(systemName: "music.note.list")
                }
                .disabled(app.defaultListeningBook == nil)
            }
        }
    }
}

private struct ContinueListeningView: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(app.playback.currentBook == nil ? "Start Listening" : "Continue Listening")
                .font(.title2.bold())
            NavigationLink(value: book) {
                HStack(spacing: 16) {
                    BookCoverView(book: book, width: 98, height: 132, cornerRadius: 16, preferFullImage: true)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(book.title)
                            .font(.title3.bold())
                            .lineLimit(2)
                        Text(app.playback.currentChapter?.title ?? book.author)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                        Button {
                            app.playDefaultBook()
                            app.showingNowPlaying = true
                        } label: {
                            Label(app.playback.currentBook == nil ? "Play" : "Resume", systemImage: "play.fill")
                        }
                        .buttonStyle(.glassProminent)
                    }
                    Spacer()
                }
                .padding(14)
                .liquidGlassPanel(cornerRadius: 24)
            }
            .buttonStyle(.plain)
        }
    }
}

private struct BookShelfView: View {
    var title: String
    var books: [AudioBook]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.title2.bold())
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 16) {
                    ForEach(books) { book in
                        NavigationLink(value: book) {
                            VStack(alignment: .leading, spacing: 8) {
                                BookCoverView(book: book, width: 132, height: 180, cornerRadius: 16, preferFullImage: true)
                                Text(book.title)
                                    .font(.headline)
                                    .lineLimit(2)
                                    .frame(width: 132, alignment: .leading)
                                Text(book.author)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                    .frame(width: 132, alignment: .leading)
                            }
                            .padding(10)
                            .frame(width: 152, alignment: .leading)
                            .liquidGlassPanel(cornerRadius: 22, interactive: false)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
        }
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
    var book: AudioBook
    var width: CGFloat
    var height: CGFloat
    var cornerRadius: CGFloat
    var preferFullImage = false

    private var coverURL: URL? {
        let urlString = preferFullImage
            ? (book.fullCoverImageURL ?? book.coverImageURL)
            : (book.coverImageURL ?? book.fullCoverImageURL)
        return urlString.flatMap(URL.init(string:))
    }

    var body: some View {
        ZStack {
            if let coverURL {
                AsyncImage(url: coverURL) { phase in
                    switch phase {
                    case .empty:
                        coverBackground
                            .overlay { ProgressView() }
                    case .success(let image):
                        coverBackground
                            .overlay {
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: app.settings.coverArtDisplayMode == .fill ? .fill : .fit)
                                    .padding(app.settings.coverArtDisplayMode == .fill ? 0 : 2)
                            }
                    case .failure:
                        fallbackCover
                    @unknown default:
                        fallbackCover
                    }
                }
            } else {
                fallbackCover
            }
        }
        .frame(width: width, height: height)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(.white.opacity(0.16), lineWidth: 1)
        }
        .accessibilityLabel(book.title)
    }

    private var coverBackground: some View {
        LinearGradient(
            colors: [.green.opacity(0.22), .teal.opacity(0.24), .blue.opacity(0.2)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var fallbackCover: some View {
        LinearGradient(
            colors: [.green.opacity(0.75), .teal.opacity(0.82), .blue.opacity(0.72)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .overlay(alignment: .bottomLeading) {
            Text(book.title)
                .font(.caption.bold())
                .foregroundStyle(.white)
                .lineLimit(3)
                .padding(8)
        }
    }
}
