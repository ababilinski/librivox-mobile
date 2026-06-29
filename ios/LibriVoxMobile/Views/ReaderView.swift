import LibriVoxCore
import SwiftUI

struct ReaderView: View {
    @Environment(AppModel.self) private var app
    var book: AudioBook
    @State private var searchText = ""

    var body: some View {
        @Bindable var app = app

        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(book.title)
                    .font(.largeTitle.bold())
                Text(book.author)
                    .font(.title3)
                    .foregroundStyle(.secondary)
                Text("Read Book")
                    .font(.caption.bold())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .liquidGlassPanel(cornerRadius: 12, interactive: false)

                Text(book.description.isEmpty ? "No source text is available for this book yet." : book.description)
                    .font(.body)
                    .lineSpacing(6)
                    .scaleEffect(app.settings.readerSettings.textScale, anchor: .topLeading)
                    .padding(.bottom, 10 * app.settings.readerSettings.textScale)

                ForEach(book.chapters) { chapter in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(chapter.title)
                            .font(.headline)
                        Text(formatDuration(seconds: chapter.durationSeconds))
                            .foregroundStyle(.secondary)
                        if app.settings.readerSettings.highlightCurrentText {
                            Text(app.settings.readerSettings.highlightMode.label)
                                .font(.caption.bold())
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .liquidGlassPanel(cornerRadius: 10, interactive: false)
                        }
                    }
                    .padding()
                    .liquidGlassPanel(cornerRadius: 18, interactive: false)
                }
            }
            .padding()
            .padding(.bottom, 88)
        }
        .navigationTitle("Reader")
        .searchable(text: $searchText, prompt: "Find in book")
        .safeAreaInset(edge: .bottom) {
            LiquidGlassGroup {
                HStack(spacing: 12) {
                    Toggle(isOn: $app.settings.readerSettings.followPlayback) {
                        Image(systemName: app.settings.readerSettings.followPlayback ? "text.line.first.and.arrowtriangle.forward" : "text.alignleft")
                    }
                    .labelsHidden()

                    Toggle(isOn: $app.settings.readerSettings.highlightCurrentText) {
                        Image(systemName: "highlighter")
                    }
                    .labelsHidden()

                    Picker("Highlight", selection: $app.settings.readerSettings.highlightMode) {
                        ForEach(ReaderHighlightMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.menu)

                    Button {
                        app.settings.readerSettings.textScale = min(app.settings.readerSettings.textScale + 0.05, 1.3)
                    } label: {
                        Image(systemName: "textformat.size.larger")
                    }
                    .buttonStyle(.glass)
                }
                .padding(10)
                .liquidGlassPanel(cornerRadius: 24)
            }
            .padding(.horizontal)
            .padding(.bottom, 8)
        }
    }
}
