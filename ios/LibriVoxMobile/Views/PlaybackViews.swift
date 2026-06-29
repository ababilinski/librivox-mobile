import LibriVoxCore
import SwiftUI

struct MiniPlayerView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        if let book = app.defaultListeningBook {
            LiquidGlassGroup {
                Button {
                    app.showingNowPlaying = true
                } label: {
                    VStack(spacing: 8) {
                        HStack(spacing: 12) {
                            BookCoverView(book: book, width: 44, height: 56, cornerRadius: 9)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(book.title)
                                    .font(.subheadline.bold())
                                    .lineLimit(1)
                                Text(app.playback.currentChapter?.title ?? "Ready to listen")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer()
                            if app.settings.showMiniPlayerRouteButton {
                                AirPlayButton()
                                    .frame(width: 34, height: 34)
                            }
                            Button {
                                if app.playback.currentBook == nil {
                                    app.playDefaultBook()
                                } else {
                                    app.playback.toggle()
                                }
                            } label: {
                                Image(systemName: app.playback.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.title3)
                            }
                            .buttonStyle(.glass)
                        }
                        if app.settings.showMiniPlayerProgress {
                            ProgressView(value: app.playback.currentTimeSeconds, total: max(app.playback.durationSeconds, 1))
                                .tint(.primary)
                        }
                    }
                    .padding(10)
                    .liquidGlassPanel(cornerRadius: 24)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct NowPlayingView: View {
    @Environment(AppModel.self) private var app

    var progress: Binding<Double> {
        Binding(
            get: { app.playback.currentTimeSeconds },
            set: { app.playback.seek(to: $0) }
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    if let book = app.defaultListeningBook {
                        BookCoverView(book: book, width: 220, height: 300, cornerRadius: 24, preferFullImage: true)
                            .shadow(radius: 24)
                    }

                    VStack(spacing: 6) {
                        Text(app.defaultListeningBook?.title ?? "Nothing playing")
                            .font(.title.bold())
                            .multilineTextAlignment(.center)
                        Text(app.playback.currentChapter?.title ?? "Ready to listen")
                            .foregroundStyle(.secondary)
                    }

                    VStack(spacing: 6) {
                        Slider(value: progress, in: 0...max(app.playback.durationSeconds, 1))
                            .tint(.primary)
                        HStack {
                            Text(formatDuration(seconds: Int(app.playback.currentTimeSeconds)))
                            Spacer()
                            Text(formatDuration(seconds: Int(app.playback.durationSeconds)))
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }

                    LiquidGlassGroup {
                        HStack(spacing: 16) {
                            transportButton("backward.end.fill") {
                                app.playback.skipChapter(by: -1)
                            }
                            transportButton("gobackward.30") {
                                app.playback.seek(by: -30)
                            }
                            Button {
                                if app.playback.currentBook == nil {
                                    app.playDefaultBook()
                                } else {
                                    app.playback.toggle()
                                }
                            } label: {
                                Image(systemName: app.playback.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.largeTitle)
                            }
                            .buttonStyle(.glassProminent)
                            transportButton("goforward.15") {
                                app.playback.seek(by: 15)
                            }
                            transportButton("forward.end.fill") {
                                app.playback.skipChapter(by: 1)
                            }
                        }
                        .font(.title2)
                        .buttonStyle(.glass)
                    }

                    LiquidGlassGroup {
                        HStack(spacing: 12) {
                            Picker("Speed", selection: Binding(
                                get: { app.settings.defaultPlaybackSpeed },
                                set: { app.setPlaybackSpeed($0) }
                            )) {
                                ForEach([0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                                    Text("\(speed.formatted(.number.precision(.fractionLength(2))))x").tag(speed)
                                }
                            }
                            .pickerStyle(.menu)

                            Button {
                                Task { await app.addBookmark() }
                            } label: {
                                Label("Bookmark", systemImage: "bookmark")
                            }
                            .buttonStyle(.glass)

                            AirPlayButton()
                                .frame(width: 38, height: 38)
                        }
                    }

                    if let book = app.defaultListeningBook {
                        PlayerSection(title: "Chapters") {
                            ForEach(book.chapters) { chapter in
                                Button {
                                    app.playback.play(book: book, chapter: chapter)
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
                                .buttonStyle(.plain)
                                Divider()
                            }
                        }
                    }

                    PlayerSection(title: "Bookmarks") {
                        if app.bookmarks.isEmpty {
                            Text("Bookmarks you add from playback or the reader will appear here.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(app.bookmarks) { bookmark in
                                Button {
                                    app.playback.seek(to: Double(bookmark.positionMilliseconds) / 1000)
                                } label: {
                                    HStack {
                                        Text(formatDuration(seconds: bookmark.positionMilliseconds / 1000))
                                        Spacer()
                                        Text(bookmark.note.isEmpty ? "Bookmark" : bookmark.note)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    }

                    PlayerSection(title: "Up Next") {
                        if app.queue.isEmpty {
                            Text("Books added to your queue will appear here.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(app.queue) { entry in
                                Text(entry.bookID)
                            }
                        }
                    }
                }
                .padding(24)
            }
            .navigationTitle("Now Playing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        app.showingNowPlaying = false
                    }
                }
            }
        }
    }

    private func transportButton(_ systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
        }
    }
}

private struct PlayerSection<Content: View>: View {
    var title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
