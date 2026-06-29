import LibriVoxCore
import SwiftUI

struct BookDetailView: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook

    private var sourceURL: URL? {
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

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top, spacing: 18) {
                        BookCoverView(book: book, width: 96, height: 132, cornerRadius: 16, preferFullImage: true)
                        VStack(alignment: .leading, spacing: 8) {
                            Text(book.title)
                                .font(.title.bold())
                            Text(book.author)
                                .font(.title3)
                                .foregroundStyle(.secondary)
                            Text(book.source.label)
                                .font(.caption.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .liquidGlassPanel(cornerRadius: 12, interactive: false)
                            if book.originalLanguage != nil || !book.translators.isEmpty {
                                Text("Translated work")
                                    .font(.caption.bold())
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .liquidGlassPanel(cornerRadius: 12, interactive: false)
                            }
                        }
                    }
                    .padding(app.settings.bookDetailUseCoverBackdrop ? 12 : 0)
                    .background {
                        if app.settings.bookDetailUseCoverBackdrop {
                            BookCoverView(book: book, width: 360, height: 190, cornerRadius: 24, preferFullImage: true)
                                .blur(radius: 18)
                                .opacity(0.28)
                        }
                    }

                    Text(book.description)
                        .foregroundStyle(.secondary)

                    LiquidGlassGroup {
                        HStack {
                            Button {
                                app.playback.play(book: book, chapter: book.chapters.first)
                                app.showingNowPlaying = true
                            } label: {
                                Label("Play", systemImage: "play.fill")
                            }
                            .buttonStyle(.glassProminent)

                            Menu {
                                Button {
                                    Task { await app.saveToLibrary(book) }
                                } label: {
                                    Label("Save", systemImage: "bookmark")
                                }
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
                                if let sourceURL {
                                    ShareLink(item: sourceURL) {
                                        Label("Share", systemImage: "square.and.arrow.up")
                                    }
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                            }
                            .buttonStyle(.glass)
                        }
                    }
                }
            }

            Section("Chapters") {
                ForEach(book.chapters) { chapter in
                    Button {
                        app.playback.play(book: book, chapter: chapter)
                        app.showingNowPlaying = true
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(chapter.title)
                                if let reader = chapter.reader {
                                    Text(reader)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Text(formatDuration(seconds: chapter.durationSeconds))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section("Source") {
                if let urlString = book.libriVoxURL, let url = URL(string: urlString) {
                    Link("Open LibriVox page", destination: url)
                }
                if let urlString = book.lit2GoURL, let url = URL(string: urlString) {
                    Link("Open Lit2Go page", destination: url)
                }
                if let urlString = book.wolneLekturyURL, let url = URL(string: urlString) {
                    Link("Open Wolne Lektury page", destination: url)
                }
                if let urlString = book.gutenbergURL, let url = URL(string: urlString) {
                    Link("Open Project Gutenberg page", destination: url)
                }
                NavigationLink("Read source text") {
                    ReaderView(book: book)
                }
            }
        }
        .navigationTitle(book.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
