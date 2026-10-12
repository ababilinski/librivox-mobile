import LibriVoxCore
import SwiftUI
import UIKit

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
                        subtitle: appearanceSummary,
                        systemImage: "rectangle.3.group"
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
                        title: "Routes",
                        subtitle: "AirPlay, SharePlay, Google Cast",
                        systemImage: "airplayaudio"
                    )
                }
            }
        }
        .glassPageBackground()
        .navigationTitle("Settings")
    }

    private var catalogSummary: String {
        app.settings.orderedEnabledSources
            .map(\.label)
            .joined(separator: ", ")
    }

    private var appearanceSummary: String {
        [
            app.settings.themeMode.label,
            app.settings.nowPlayingPresentationMode.label,
        ]
        .joined(separator: ", ")
    }

    private var downloadSummary: String {
        [
            app.settings.autoDownloadLibraryAdditions ? "Auto-download library additions" : nil,
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

            Section("Layout") {
                Picker("Now Playing", selection: $app.settings.nowPlayingPresentationMode) {
                    ForEach(NowPlayingPresentationMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("Now Playing style")
            }

            Section("Artwork") {
                Picker("Cover display", selection: $app.settings.coverArtDisplayMode) {
                    ForEach(CoverArtDisplayMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                Toggle("Artwork color scheme", isOn: $app.settings.bookDetailUseArtworkColorScheme)
                    .tint(LibriVoxBrand.switchTint)
                Toggle("Cover backdrop", isOn: $app.settings.bookDetailUseCoverBackdrop)
                    .tint(LibriVoxBrand.switchTint)
            }
        }
        .glassPageBackground()
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

    private var orderedSources: [BookSource] {
        BookSource.normalizedPublicCatalogPriority(app.settings.sourcePriority)
    }

    var body: some View {
        @Bindable var app = app

        Form {
            Section {
                ForEach(orderedSources) { source in
                    Toggle(isOn: sourceBinding(source)) {
                        SourcePriorityLabel(
                            source: source,
                            priority: orderedSources.firstIndex(of: source).map { $0 + 1 }
                        )
                    }
                    .tint(LibriVoxBrand.switchTint)
                }
                .onMove(perform: moveSources)
            } header: {
                Text("Audiobook Sources")
            } footer: {
                Text("Enabled sources are checked in this order. When the same book appears in more than one source, the higher source is kept and details from lower sources are merged when useful.")
            }

            Section("Languages") {
                ForEach(availableLanguages) { language in
                    Toggle(language.label, isOn: languageBinding(language))
                        .tint(LibriVoxBrand.switchTint)
                }
            }

            Section("Search") {
                Toggle("Automatic search caching", isOn: $app.settings.automaticSearchCachingEnabled)
                    .tint(LibriVoxBrand.switchTint)
                LabeledContent("Cached searches", value: "\(app.searchCacheEntryCount)")
                Button(role: .destructive) {
                    app.clearSearchCache()
                } label: {
                    Label("Clear Search Cache", systemImage: "trash")
                }
                .disabled(app.searchCacheEntryCount == 0)
            }
        }
        .glassPageBackground()
        .navigationTitle("Catalog")
        .toolbar {
            EditButton()
        }
    }

    private func sourceBinding(_ source: BookSource) -> Binding<Bool> {
        Binding {
            app.settings.enabledSources.contains(source)
        } set: { isEnabled in
            var settings = app.settings
            if isEnabled {
                settings.enabledSources.insert(source)
                if source == .wolneLektury {
                    settings.preferredLanguages.insert(.polish)
                }
            } else {
                settings.enabledSources.remove(source)
            }
            settings.normalizeForAvailableSources()
            app.settings = settings
        }
    }

    private func moveSources(from source: IndexSet, to destination: Int) {
        var settings = app.settings
        var priority = orderedSources
        priority.move(fromOffsets: source, toOffset: destination)
        settings.sourcePriority = priority
        settings.normalizeForAvailableSources()
        app.settings = settings
    }

    private func languageBinding(_ language: BookLanguagePreference) -> Binding<Bool> {
        Binding {
            app.settings.preferredLanguages.contains(language)
        } set: { isEnabled in
            var settings = app.settings
            if isEnabled {
                settings.preferredLanguages.insert(language)
            } else {
                settings.preferredLanguages.remove(language)
            }
            settings.normalizeForAvailableSources()
            app.settings = settings
        }
    }
}

private struct SourcePriorityLabel: View {
    var source: BookSource
    var priority: Int?

    var body: some View {
        HStack(spacing: 12) {
            if let priority {
                Text("\(priority)")
                    .font(.caption.monospacedDigit().weight(.bold))
                    .foregroundStyle(.secondary)
                    .frame(width: 24, height: 24)
                    .background(.thinMaterial, in: Circle())
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(source.label)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var subtitle: String? {
        switch source {
        case .lit2Go:
            "Fast literature catalog"
        case .libriVox:
            "Large audiobook catalog"
        case .wolneLektury:
            "Polish audiobooks only"
        case .gutendex:
            "Gutenberg metadata"
        case .localAsset, .customLocal:
            nil
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
                    .tint(LibriVoxBrand.switchTint)
            }

            Section("Auto-download While Listening") {
                Picker("Mode", selection: $app.settings.autoDownloadMode) {
                    ForEach(AutoDownloadMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
            }

            Section {
                Toggle(
                    "Automatically download books added to Library",
                    isOn: Binding(
                        get: { app.settings.autoDownloadLibraryAdditions },
                        set: { app.setAutoDownloadLibraryAdditions($0) }
                    )
                )
                .tint(LibriVoxBrand.switchTint)
                .accessibilityIdentifier("Automatically download added books")
            } header: {
                Text("Library Additions")
            } footer: {
                Text("When enabled, adding a book to Your Library starts downloading its available audio for offline listening. Turning this off does not remove existing downloads.")
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
        .glassPageBackground()
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
                    ForEach(PlaybackSpeedOptions.presets, id: \.self) { speed in
                        Text(PlaybackSpeedOptions.label(for: speed)).tag(speed)
                    }
                }
            }

            Section("Mini Player") {
                Toggle("Show progress", isOn: $app.settings.showMiniPlayerProgress)
                    .tint(LibriVoxBrand.switchTint)
                Toggle("Show route button", isOn: $app.settings.showMiniPlayerRouteButton)
                    .tint(LibriVoxBrand.switchTint)
            }

        }
        .glassPageBackground()
        .navigationTitle("Playback")
    }
}

private struct ReadBookSettingsView: View {
    @Environment(AppModel.self) private var app

    var body: some View {
        @Bindable var app = app

        Form {
            Section("Reading Layout") {
                Picker("Page style", selection: $app.settings.readerSettings.layoutMode) {
                    ForEach(ReaderLayoutMode.allCases) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                Text("Pages snap one screen at a time. Scrolling keeps the document continuous.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Read Along") {
                Toggle("Follow playback", isOn: $app.settings.readerSettings.followPlayback)
                    .tint(LibriVoxBrand.switchTint)
                Toggle("Highlight current text", isOn: $app.settings.readerSettings.highlightCurrentText)
                    .tint(LibriVoxBrand.switchTint)
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
        .glassPageBackground()
        .navigationTitle("Read Book")
    }
}

private struct RouteDiagnosticsSettingsView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.openURL) private var openURL

    var body: some View {
        List {
            Section("Apple Routes And SharePlay") {
                HStack {
                    Text("AirPlay")
                    Spacer()
                    AirPlayButton()
                        .frame(width: 38, height: 38)
                }
                SharePlayRouteRow(book: app.defaultListeningBook)
            }

            Section("Google Cast") {
                GoogleCastRouteRow()
                Text("Your iPhone and Cast device must be on the same Wi-Fi network. If discovery was denied, allow Local Network access in iOS Settings.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        openURL(url)
                    }
                } label: {
                    Label("Open iOS Settings", systemImage: "gear")
                }
            }

            Section("Diagnostics") {
                LabeledContent("Network", value: app.isOnline ? "Online" : "Offline")
                LabeledContent("Route events", value: "\(app.routeDiagnostics.count)")
                LabeledContent(
                    "Google Cast",
                    value: GoogleCastBridge.isAvailable ? "Ready" : "Unavailable"
                )
            }
        }
        .glassPageBackground()
        .navigationTitle("Routes")
    }
}
