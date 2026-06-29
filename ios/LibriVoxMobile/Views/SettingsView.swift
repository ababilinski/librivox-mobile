import LibriVoxCore
import SwiftUI

private let settingsSourceOptions: [BookSource] = [
    .lit2Go,
    .gutendex,
    .wolneLektury,
]

struct SettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List {
            Section("Library") {
                NavigationLink {
                    DownloadsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Downloads",
                        subtitle: downloadSummary,
                        systemImage: "arrow.down.circle"
                    )
                }
            }

            Section("Preferences") {
                NavigationLink {
                    AppearanceSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Appearance",
                        subtitle: "\(app.settings.themeMode.label), \(app.settings.coverArtDisplayMode.label.lowercased()) covers",
                        systemImage: "sparkles"
                    )
                }
                NavigationLink {
                    CatalogSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Catalog",
                        subtitle: catalogSummary,
                        systemImage: "books.vertical"
                    )
                }
                NavigationLink {
                    DownloadSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Download Settings",
                        subtitle: downloadSummary,
                        systemImage: "icloud.and.arrow.down"
                    )
                }
                NavigationLink {
                    PlaybackSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Playback",
                        subtitle: "\(app.settings.defaultPlaybackSpeed.formatted(.number.precision(.fractionLength(2))))x, mini-player controls",
                        systemImage: "waveform"
                    )
                }
                NavigationLink {
                    ReadBookSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "Read Book",
                        subtitle: app.settings.readerSettings.highlightMode.label,
                        systemImage: "text.book.closed"
                    )
                }
                NavigationLink {
                    RouteDiagnosticsSettingsView()
                } label: {
                    SettingsNavigationLabel(
                        title: "AirPlay And Diagnostics",
                        subtitle: app.isOnline ? "Online" : "Offline",
                        systemImage: "airplayaudio"
                    )
                }
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Settings")
    }

    private var catalogSummary: String {
        app.settings.enabledSources
            .filter(\.isPublicAudiobookSource)
            .map(\.label)
            .sorted()
            .joined(separator: ", ")
    }

    private var downloadSummary: String {
        [
            app.settings.autoDownloadMode.label,
            app.settings.downloadNetworkPolicy.label,
            app.settings.downloadsOnlyModeEnabled ? "Downloads only" : nil,
        ]
        .compactMap { $0 }
        .joined(separator: ", ")
    }
}

private struct SettingsNavigationLabel: View {
    var title: String
    var subtitle: String
    var systemImage: String

    var body: some View {
        Label {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        } icon: {
            Image(systemName: systemImage)
        }
    }
}

private struct AppearanceSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Theme") {
                Picker("Mode", selection: $app.settings.themeMode) {
                    ForEach(AppThemeMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                Picker("Motion", selection: $app.settings.animationSpeed) {
                    ForEach(AnimationSpeed.allCases) { speed in
                        Text(speed.label).tag(speed)
                    }
                }
            }

            Section("Artwork") {
                Picker("Cover display", selection: $app.settings.coverArtDisplayMode) {
                    ForEach(CoverArtDisplayMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                Toggle("Artwork color scheme", isOn: $app.settings.bookDetailUseArtworkColorScheme)
                Toggle("Cover backdrop", isOn: $app.settings.bookDetailUseCoverBackdrop)
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Appearance")
    }
}

private struct CatalogSettingsView: View {
    @Environment(AppModel.self) private var app

    private var availableLanguages: [BookLanguagePreference] {
        var languages: [BookLanguagePreference] = [.all, .english, .french, .german, .italian, .spanish]
        if app.settings.enabledSources.contains(.wolneLektury) {
            languages.append(.polish)
        }
        return languages
    }

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Primary Audiobook Source") {
                HStack {
                    Label("LibriVox", systemImage: "checkmark.circle.fill")
                    Spacer()
                    Text("On")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Other Public Audiobook Sources") {
                ForEach(settingsSourceOptions) { source in
                    Toggle(isOn: sourceBinding(source)) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(source.label)
                            if source == .wolneLektury {
                                Text("Polish audiobooks")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }

            Section("Languages") {
                ForEach(availableLanguages) { language in
                    Toggle(language.label, isOn: languageBinding(language))
                }
            }

            Section("Search Cache") {
                Toggle("Automatic search caching", isOn: $app.settings.automaticSearchCachingEnabled)
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Catalog")
    }

    private func sourceBinding(_ source: BookSource) -> Binding<Bool> {
        Binding {
            app.settings.enabledSources.contains(source)
        } set: { isEnabled in
            if isEnabled {
                app.settings.enabledSources.insert(source)
            } else {
                app.settings.enabledSources.remove(source)
            }
            app.settings.enabledSources.insert(.libriVox)
            if !app.settings.enabledSources.contains(.wolneLektury) {
                app.settings.preferredLanguages.remove(.polish)
            }
        }
    }

    private func languageBinding(_ language: BookLanguagePreference) -> Binding<Bool> {
        Binding {
            app.settings.preferredLanguages.contains(language)
        } set: { isEnabled in
            if isEnabled {
                app.settings.preferredLanguages.insert(language)
            } else {
                app.settings.preferredLanguages.remove(language)
            }
            if app.settings.preferredLanguages.isEmpty {
                app.settings.preferredLanguages.insert(.english)
            }
        }
    }
}

private struct DownloadSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Offline Mode") {
                Toggle("Downloads only", isOn: $app.settings.downloadsOnlyModeEnabled)
            }

            Section("Auto-download While Listening") {
                Picker("Mode", selection: $app.settings.autoDownloadMode) {
                    ForEach(AutoDownloadMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section("Manual Downloads") {
                Picker("Network", selection: $app.settings.downloadNetworkPolicy) {
                    ForEach(DownloadNetworkPolicy.allCases) { policy in
                        Text(policy.label).tag(policy)
                    }
                }
                NavigationLink("Open Download Library") {
                    DownloadsView()
                }
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Downloads")
    }
}

private struct PlaybackSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Audio") {
                LabeledContent("Audio session", value: "Spoken audio")
                Picker("Default speed", selection: Binding(
                    get: { app.settings.defaultPlaybackSpeed },
                    set: { app.setPlaybackSpeed($0) }
                )) {
                    ForEach([0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                        Text("\(speed.formatted(.number.precision(.fractionLength(2))))x").tag(speed)
                    }
                }
            }

            Section("Mini Player") {
                Toggle("Show progress", isOn: $app.settings.showMiniPlayerProgress)
                Toggle("Show route button", isOn: $app.settings.showMiniPlayerRouteButton)
            }

            Section("Like And Dislike") {
                Picker("Feedback controls", selection: $app.settings.feedbackControlScope) {
                    ForEach(FeedbackControlScope.allCases) { scope in
                        Text(scope.label).tag(scope)
                    }
                }
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Playback")
    }
}

private struct ReadBookSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Read Along") {
                Toggle("Follow playback", isOn: $app.settings.readerSettings.followPlayback)
                Toggle("Highlight current text", isOn: $app.settings.readerSettings.highlightCurrentText)
            }

            Section("Highlight Range") {
                Picker("Mode", selection: $app.settings.readerSettings.highlightMode) {
                    ForEach(ReaderHighlightMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section("Text Size") {
                Slider(value: $app.settings.readerSettings.textScale, in: 0.9...1.3, step: 0.05) {
                    Text("Text scale")
                }
                LabeledContent("Current", value: "\(Int(app.settings.readerSettings.textScale * 100))%")
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("Read Book")
    }
}

private struct RouteDiagnosticsSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        List {
            Section("Route") {
                HStack {
                    Text("AirPlay")
                    Spacer()
                    AirPlayButton()
                        .frame(width: 38, height: 38)
                }
            }

            Section("Diagnostics") {
                LabeledContent("Network", value: app.isOnline ? "Online" : "Offline")
                LabeledContent("Route events", value: "\(app.routeDiagnostics.count)")
            }
        }
        .glassPageBackground(book: app.defaultListeningBook)
        .navigationTitle("AirPlay")
    }
}
