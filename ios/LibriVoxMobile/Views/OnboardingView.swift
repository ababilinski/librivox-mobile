import LibriVoxCore
import SwiftUI

private let publicSourceOptions: [BookSource] = [
    .lit2Go,
    .gutendex,
    .wolneLektury,
]

struct OnboardingView: View {
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

        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 16) {
                        BookCoverView(book: AudioBook.theJungle, width: 82, height: 112, cornerRadius: 14, preferFullImage: true)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("LibriVox Mobile")
                                .font(.title.bold())
                            Text("The Jungle is ready in your library.")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 8)
                }

                Section("Primary Audiobook Source") {
                    HStack {
                        Label("LibriVox", systemImage: "checkmark.circle.fill")
                        Spacer()
                        Text("On")
                            .foregroundStyle(.secondary)
                    }
                }

                Section("Other Public Audiobook Sources") {
                    ForEach(publicSourceOptions) { source in
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

                Section("Downloads And Search") {
                    Picker("Auto-download", selection: $app.settings.autoDownloadMode) {
                        ForEach(AutoDownloadMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    Picker("Download network", selection: $app.settings.downloadNetworkPolicy) {
                        ForEach(DownloadNetworkPolicy.allCases) { policy in
                            Text(policy.label).tag(policy)
                        }
                    }
                    Toggle("Automatic search caching", isOn: $app.settings.automaticSearchCachingEnabled)
                }
            }
            .contentMargins(.bottom, 104, for: .scrollContent)
            .safeAreaInset(edge: .bottom) {
                LiquidGlassGroup {
                    Button {
                        app.completeOnboarding()
                    } label: {
                        Label("Start Listening", systemImage: "play.fill")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.glassProminent)
                    .padding(.horizontal)
                    .padding(.bottom, 8)
                }
            }
            .navigationTitle("Set Up")
        }
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
