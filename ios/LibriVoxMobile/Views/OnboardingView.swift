import LibriVoxCore
import SwiftUI

private let publicSourceOptions: [BookSource] = [.libriVox, .lit2Go, .wolneLektury]

private enum OnboardingStep: Int, CaseIterable {
    case welcome, sources, languages, downloads, speakers

    var label: String {
        switch self {
        case .welcome: "Welcome"
        case .sources: "Book sources"
        case .languages: "Languages"
        case .downloads: "Offline listening"
        case .speakers: "Listen anywhere"
        }
    }
}

struct OnboardingView: View {
    @Environment(AppModel.self) private var app
    @Environment(\.libriVoxAccent) private var accent
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var step = OnboardingStep.welcome

    private var availableLanguages: [BookLanguagePreference] {
        if app.settings.enabledSources == [.wolneLektury] {
            return [.all, .polish]
        }
        var languages: [BookLanguagePreference] = [.all, .english, .french, .german, .italian, .spanish]
        if app.settings.enabledSources.contains(.wolneLektury) {
            languages.append(.polish)
        }
        return languages
    }

    private var canAdvance: Bool {
        switch step {
        case .sources: !app.settings.enabledSources.isEmpty
        case .languages: !app.settings.preferredLanguages.isEmpty
        default: true
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                progressHeader
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        stepContent
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 20)
                    .padding(.bottom, 28)
                    .frame(maxWidth: 620, alignment: .leading)
                    .frame(maxWidth: .infinity)
                }
                .id(step)
                .transition(.opacity)
                .accessibilityIdentifier("onboarding-content")
            }
            .background(LibriVoxBrand.surface(for: colorScheme))
            .safeAreaBar(edge: .bottom) {
                actionBar
            }
            .navigationTitle(step == .welcome ? "LibriVox Mobile" : "Set Up")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.hidden, for: .navigationBar)
        }
        .tint(accent)
    }

    private var progressHeader: some View {
        VStack(spacing: 10) {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 6) {
                    Text(step.label)
                        .font(.subheadline.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)
                    stepCount
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack {
                    Text(step.label)
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    stepCount
                }
            }
            ProgressView(value: Double(step.rawValue + 1), total: Double(OnboardingStep.allCases.count))
                .tint(accent)
        }
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .frame(maxWidth: 620)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Setup progress")
        .accessibilityValue("\(step.rawValue + 1) of \(OnboardingStep.allCases.count): \(step.label)")
    }

    private var stepCount: some View {
        Text("\(step.rawValue + 1) of \(OnboardingStep.allCases.count)")
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)
            .fixedSize()
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case .welcome: welcomeContent
        case .sources: sourcesContent
        case .languages: languagesContent
        case .downloads: downloadsContent
        case .speakers: speakersContent
        }
    }

    private var welcomeContent: some View {
        VStack(alignment: .leading, spacing: 24) {
            OnboardingHeading(
                title: "Great stories.\nReady when you are.",
                subtitle: "Discover free public-domain audiobooks, read by people who love them."
            )
            OnboardingBookshelf()
                .padding(.horizontal, -24)
            OnboardingCard {
                OnboardingFeatureRow(symbol: "books.vertical", title: "Find your next favorite", detail: "Classic novels, poetry, and more.")
                Divider()
                OnboardingFeatureRow(symbol: "arrow.down.circle", title: "Take a story with you", detail: "Stream now. Save chapters for later.")
                Divider()
                OnboardingFeatureRow(symbol: "headphones", title: "Listen your way", detail: "Bookmarks, speed, and a sleep timer.")
            }
        }
    }

    private var sourcesContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            OnboardingHeading(
                title: "Choose your\nbook sources.",
                subtitle: "Start with LibriVox or add more public audiobook collections."
            )
            ForEach(publicSourceOptions) { source in
                OnboardingSelectionRow(
                    title: source.label,
                    detail: source.onboardingDescription,
                    symbol: source.onboardingSymbol,
                    selected: app.settings.enabledSources.contains(source)
                ) {
                    let binding = sourceBinding(source)
                    binding.wrappedValue.toggle()
                }
                .accessibilityIdentifier("onboarding-source-\(source.rawValue)")
            }
            Label(
                app.settings.enabledSources.isEmpty
                    ? "Choose at least one source to continue."
                    : "You can change your sources later in Settings.",
                systemImage: app.settings.enabledSources.isEmpty ? "exclamationmark.circle" : "info.circle"
            )
            .font(.footnote)
            .foregroundStyle(.secondary)
        }
    }

    private var languagesContent: some View {
        VStack(alignment: .leading, spacing: 20) {
            OnboardingHeading(
                title: "Stories in\nyour language.",
                subtitle: "Choose what appears in Discover. You can select more than one language."
            )
            OnboardingSelectionRow(
                title: "All available languages",
                detail: "Explore everything your sources offer.",
                symbol: "globe",
                selected: app.settings.preferredLanguages.contains(.all)
            ) {
                app.settings.preferredLanguages = [.all]
            }
            .accessibilityIdentifier("onboarding-language-all")
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: dynamicTypeSize.isAccessibilitySize ? 1 : 2),
                spacing: 12
            ) {
                ForEach(availableLanguages.filter { $0 != .all }) { language in
                    OnboardingSelectionRow(
                        title: language.label,
                        detail: language.nativeName,
                        selected: app.settings.preferredLanguages.contains(language)
                    ) {
                        let binding = languageBinding(language)
                        binding.wrappedValue.toggle()
                    }
                    .accessibilityIdentifier("onboarding-language-\(language.rawValue)")
                }
            }
            if app.settings.enabledSources == [.wolneLektury] {
                Label("Wolne Lektury offers Polish audiobooks.", systemImage: "info.circle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var downloadsContent: some View {
        @Bindable var app = app
        return VStack(alignment: .leading, spacing: 24) {
            OnboardingHeading(
                title: "A good story.\nEven offline.",
                subtitle: "Choose how audio is saved when you listen. You can always download a book yourself."
            )
            OnboardingSection(title: "Save while listening") {
                ForEach(AutoDownloadMode.allCases) { mode in
                    OnboardingSelectionRow(
                        title: mode.label,
                        detail: mode.onboardingDescription,
                        symbol: mode == .off ? "arrow.down.circle" : "arrow.down.circle.fill",
                        selected: app.settings.autoDownloadMode == mode
                    ) {
                        app.settings.autoDownloadMode = mode
                    }
                    .accessibilityIdentifier("onboarding-download-\(mode.rawValue)")
                }
            }
            OnboardingSection(title: "Download network") {
                ForEach(DownloadNetworkPolicy.allCases) { policy in
                    OnboardingSelectionRow(
                        title: policy.label,
                        detail: policy == .wifiOnly ? "Save audio when you're connected to Wi-Fi." : "Downloads can also use cellular data.",
                        symbol: policy == .wifiOnly ? "wifi" : "antenna.radiowaves.left.and.right",
                        selected: app.settings.downloadNetworkPolicy == policy
                    ) {
                        app.settings.downloadNetworkPolicy = policy
                    }
                    .accessibilityIdentifier("onboarding-network-\(policy.rawValue)")
                }
            }
            OnboardingCard {
                Toggle(isOn: $app.settings.automaticSearchCachingEnabled) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Faster browsing").font(.headline)
                        Text("Keep recent search results on this device.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .tint(LibriVoxBrand.switchTint)
                .accessibilityIdentifier("onboarding-search-cache")
            }
        }
    }

    private var speakersContent: some View {
        VStack(alignment: .leading, spacing: 24) {
            OnboardingHeading(
                title: "Make yourself\nat home.",
                subtitle: "Listen on your phone, or bring a story to your speakers and TV."
            )
            OnboardingCard {
                OnboardingFeatureRow(
                    symbol: "hifispeaker.2",
                    title: "Connect with Google Cast",
                    detail: "Find a Chromecast or Google speaker on your Wi-Fi network."
                )
                GoogleCastRouteRow()
                Text("Casting is optional. iOS only asks for Local Network access when you choose to connect.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            OnboardingCard {
                HStack(spacing: 16) {
                    BookCoverView(book: AudioBook.theJungle, width: 64, height: 88, cornerRadius: 10, preferFullImage: true)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Your first book is ready").font(.headline)
                        Text("The Jungle").font(.subheadline)
                        Text("Upton Sinclair · In your library")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Label("All set. Your choices can be changed in Settings.", systemImage: "checkmark.circle")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var actionBar: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(spacing: 10) {
                    if step != .welcome {
                        backAction
                    }
                    primaryAction
                }
            } else {
                HStack(spacing: 14) {
                    if step != .welcome {
                        backAction
                    }
                    primaryAction
                }
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
        .frame(maxWidth: 620)
        .frame(maxWidth: .infinity)
    }

    private var backAction: some View {
        Button {
            if let previous = OnboardingStep(rawValue: step.rawValue - 1) {
                move(to: previous)
            }
        } label: {
            Label("Back", systemImage: "chevron.left")
                .padding(.vertical, 4)
                .frame(maxWidth: dynamicTypeSize.isAccessibilitySize ? .infinity : nil)
        }
        .buttonStyle(.glass)
        .controlSize(.large)
        .accessibilityIdentifier("onboarding-back")
    }

    private var primaryAction: some View {
        Button {
            if step == .speakers {
                app.completeOnboarding()
            } else if let next = OnboardingStep(rawValue: step.rawValue + 1) {
                move(to: next)
            }
        } label: {
            Group {
                if dynamicTypeSize.isAccessibilitySize {
                    Text(primaryActionTitle)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Label(
                        primaryActionTitle,
                        systemImage: step == .speakers ? "play.fill" : "arrow.right"
                    )
                }
            }
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 28)
            .padding(.vertical, 4)
            // A monochrome tint is light in Dark Mode; use its opposite
            // semantic surface color to keep the primary action legible.
            .foregroundStyle(LibriVoxBrand.surface(for: colorScheme))
        }
        .buttonStyle(.glassProminent)
        .controlSize(.large)
        .tint(accent)
        .disabled(!canAdvance)
        .accessibilityIdentifier("onboarding-next")
    }

    private var primaryActionTitle: String {
        switch step {
        case .welcome: "Get started"
        case .speakers: "Start Listening"
        default: "Continue"
        }
    }

    private func move(to next: OnboardingStep) {
        withAnimation(LibriVoxMotion.brief(reduceMotion: reduceMotion, preference: app.settings.animationSpeed)) {
            step = next
        }
    }

    private func sourceBinding(_ source: BookSource) -> Binding<Bool> {
        Binding {
            app.settings.enabledSources.contains(source)
        } set: { isEnabled in
            var settings = app.settings
            if isEnabled {
                settings.enabledSources.insert(source)
                if source == .wolneLektury && !settings.preferredLanguages.contains(.all) {
                    settings.preferredLanguages.insert(.polish)
                }
            } else {
                settings.enabledSources.remove(source)
            }
            settings.normalizeForAvailableSources()
            app.settings = settings
        }
    }

    private func languageBinding(_ language: BookLanguagePreference) -> Binding<Bool> {
        Binding {
            app.settings.preferredLanguages.contains(language)
        } set: { isEnabled in
            var settings = app.settings
            if isEnabled {
                settings.preferredLanguages.remove(.all)
                settings.preferredLanguages.insert(language)
            } else {
                settings.preferredLanguages.remove(language)
            }
            settings.normalizeForAvailableSources()
            app.settings = settings
        }
    }
}

private struct OnboardingHeading: View {
    var title: String
    var subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.largeTitle.bold())
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(subtitle)
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct OnboardingCard<Content: View>: View {
    @Environment(\.colorScheme) private var colorScheme
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 16, content: content)
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(LibriVoxBrand.surfaceVariant(for: colorScheme), in: RoundedRectangle(cornerRadius: 24))
    }
}

private struct OnboardingSection<Content: View>: View {
    var title: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.secondary)
                .accessibilityAddTraits(.isHeader)
            content()
        }
    }
}

private struct OnboardingSelectionRow: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.colorSchemeContrast) private var contrast
    var title: String
    var detail: String
    var symbol: String? = nil
    var selected: Bool
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                if let symbol {
                    Image(systemName: symbol)
                        .font(.title3)
                        .frame(width: 36, height: 40)
                        .accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: 5) {
                    Text(title).font(.headline)
                    Text(detail).font(.subheadline).foregroundStyle(.secondary)
                }
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Color.primary : Color.secondary)
                    .accessibilityHidden(true)
            }
            .padding(18)
            .frame(maxWidth: .infinity, minHeight: 68, alignment: .leading)
            .background(LibriVoxBrand.surfaceVariant(for: colorScheme), in: RoundedRectangle(cornerRadius: 24))
            .overlay {
                RoundedRectangle(cornerRadius: 24)
                    .strokeBorder(selected ? Color.primary.opacity(contrast == .increased ? 1 : 0.35) : Color.secondary.opacity(contrast == .increased ? 0.6 : 0.12), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 24))
        }
        .buttonStyle(.plain)
        .foregroundStyle(.primary)
        .accessibilityLabel(title)
        .accessibilityValue(selected ? "Selected" : "Not selected")
        .accessibilityHint(detail)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct OnboardingFeatureRow: View {
    var symbol: String
    var title: String
    var detail: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: symbol)
                .font(.title3)
                .frame(width: 28, height: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(detail).font(.subheadline).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct OnboardingBookshelf: View {
    @ScaledMetric(relativeTo: .body) private var coverWidth = 124.0

    private let books: [(image: String, title: String, author: String)] = [
        ("OnboardingAlice", "Alice's Adventures in Wonderland", "Lewis Carroll"),
        ("OnboardingPride", "Pride and Prejudice", "Jane Austen"),
        ("OnboardingSherlock", "The Adventures of Sherlock Holmes", "Arthur Conan Doyle"),
        ("OnboardingMoby", "Moby Dick", "Herman Melville"),
        ("OnboardingFrankenstein", "Frankenstein", "Mary Shelley"),
    ]

    var body: some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 16) {
                ForEach(books, id: \.image) { book in
                    VStack(alignment: .leading, spacing: 8) {
                        Image(book.image)
                            .resizable()
                            .scaledToFill()
                            .frame(width: min(coverWidth, 180), height: min(coverWidth, 180) * 1.35)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .accessibilityHidden(true)
                        Text(book.title).font(.caption.weight(.semibold))
                        Text(book.author).font(.caption2).foregroundStyle(.secondary)
                    }
                    .frame(width: min(coverWidth, 180), alignment: .leading)
                    .accessibilityElement(children: .combine)
                }
            }
            .scrollTargetLayout()
        }
        .contentMargins(.horizontal, 24, for: .scrollContent)
        .scrollTargetBehavior(.viewAligned)
        .scrollIndicators(.hidden)
        .accessibilityLabel("Classic audiobooks")
    }
}

private extension BookSource {
    var onboardingDescription: String {
        switch self {
        case .libriVox: "Classics, poetry, and nonfiction read by volunteers."
        case .lit2Go: "Stories and poems from the University of South Florida."
        case .gutendex: "Public-domain audiobook records from Project Gutenberg."
        case .wolneLektury: "Polish audiobooks from Wolne Lektury."
        default: label
        }
    }

    var onboardingSymbol: String {
        switch self {
        case .libriVox: "books.vertical"
        case .lit2Go: "graduationcap"
        case .gutendex: "book.closed"
        case .wolneLektury: "text.book.closed"
        default: "book"
        }
    }
}

private extension BookLanguagePreference {
    var nativeName: String {
        switch self {
        case .all: "Every language"
        case .english: "English"
        case .french: "Français"
        case .german: "Deutsch"
        case .italian: "Italiano"
        case .spanish: "Español"
        case .polish: "Polski"
        }
    }
}

private extension AutoDownloadMode {
    var onboardingDescription: String {
        switch self {
        case .off: "Only save audio when you choose Download."
        case .currentChapter: "Save the chapter you're listening to."
        case .currentBook: "Save the whole book when playback starts."
        }
    }
}

#Preview("Welcome · Light") {
    OnboardingView()
        .environment(AppModel())
        .preferredColorScheme(.light)
}

#Preview("Welcome · Dark") {
    OnboardingView()
        .environment(AppModel())
        .preferredColorScheme(.dark)
}
