import Foundation

public enum CatalogSearchField: String, Codable, CaseIterable, Identifiable, Sendable {
    case all
    case title
    case author
    case chapter
    case reader

    public var id: String { rawValue }
}

public enum BookLanguagePreference: String, Codable, CaseIterable, Identifiable, Sendable {
    case all
    case english
    case french
    case german
    case italian
    case spanish
    case polish

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .all: "All available"
        case .english: "English"
        case .french: "French"
        case .german: "German"
        case .italian: "Italian"
        case .spanish: "Spanish"
        case .polish: "Polish"
        }
    }
}

public enum AutoDownloadMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case off
    case currentChapter
    case currentBook

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .off: "Off"
        case .currentChapter: "Current chapter"
        case .currentBook: "Current book"
        }
    }
}

public enum DownloadNetworkPolicy: String, Codable, CaseIterable, Identifiable, Sendable {
    case wifiOnly
    case anyNetwork

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .wifiOnly: "Wi-Fi only"
        case .anyNetwork: "Any network"
        }
    }
}

public enum AppThemeMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case system
    case light
    case dark

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}

public enum AnimationSpeed: String, Codable, CaseIterable, Identifiable, Sendable {
    case reduced
    case standard
    case expressive

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .reduced: "Reduced"
        case .standard: "Standard"
        case .expressive: "Expressive"
        }
    }
}

public enum CoverArtDisplayMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case fit
    case fill
    case artworkBackdrop

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .fit: "Fit"
        case .fill: "Fill"
        case .artworkBackdrop: "Backdrop"
        }
    }
}

public enum FeedbackControlScope: String, Codable, CaseIterable, Identifiable, Sendable {
    case hidden
    case book
    case chapter

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .hidden: "Hidden"
        case .book: "Book"
        case .chapter: "Chapter"
        }
    }
}

public enum ReaderHighlightMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case sentence
    case paragraph
    case chapter

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .sentence: "Sentence"
        case .paragraph: "Paragraph"
        case .chapter: "Chapter"
        }
    }
}

public struct ReaderSettings: Codable, Hashable, Sendable {
    public var followPlayback: Bool
    public var highlightCurrentText: Bool
    public var highlightMode: ReaderHighlightMode
    public var textScale: Double

    public init(
        followPlayback: Bool = true,
        highlightCurrentText: Bool = true,
        highlightMode: ReaderHighlightMode = .sentence,
        textScale: Double = 1.0
    ) {
        self.followPlayback = followPlayback
        self.highlightCurrentText = highlightCurrentText
        self.highlightMode = highlightMode
        self.textScale = textScale
    }
}

public struct UserSettings: Codable, Hashable, Sendable {
    public var themeMode: AppThemeMode
    public var animationSpeed: AnimationSpeed
    public var enabledSources: Set<BookSource>
    public var preferredLanguages: Set<BookLanguagePreference>
    public var automaticSearchCachingEnabled: Bool
    public var autoDownloadMode: AutoDownloadMode
    public var downloadNetworkPolicy: DownloadNetworkPolicy
    public var downloadsOnlyModeEnabled: Bool
    public var coverArtDisplayMode: CoverArtDisplayMode
    public var bookDetailUseCoverBackdrop: Bool
    public var bookDetailUseArtworkColorScheme: Bool
    public var showMiniPlayerProgress: Bool
    public var showMiniPlayerRouteButton: Bool
    public var feedbackControlScope: FeedbackControlScope
    public var defaultPlaybackSpeed: Double
    public var readerSettings: ReaderSettings

    public init(
        themeMode: AppThemeMode = .system,
        animationSpeed: AnimationSpeed = .standard,
        enabledSources: Set<BookSource> = [.libriVox],
        preferredLanguages: Set<BookLanguagePreference> = [.english],
        automaticSearchCachingEnabled: Bool = true,
        autoDownloadMode: AutoDownloadMode = .off,
        downloadNetworkPolicy: DownloadNetworkPolicy = .wifiOnly,
        downloadsOnlyModeEnabled: Bool = false,
        coverArtDisplayMode: CoverArtDisplayMode = .fit,
        bookDetailUseCoverBackdrop: Bool = false,
        bookDetailUseArtworkColorScheme: Bool = false,
        showMiniPlayerProgress: Bool = true,
        showMiniPlayerRouteButton: Bool = true,
        feedbackControlScope: FeedbackControlScope = .book,
        defaultPlaybackSpeed: Double = 1.0,
        readerSettings: ReaderSettings = ReaderSettings()
    ) {
        self.themeMode = themeMode
        self.animationSpeed = animationSpeed
        self.enabledSources = enabledSources
        self.preferredLanguages = preferredLanguages
        self.automaticSearchCachingEnabled = automaticSearchCachingEnabled
        self.autoDownloadMode = autoDownloadMode
        self.downloadNetworkPolicy = downloadNetworkPolicy
        self.downloadsOnlyModeEnabled = downloadsOnlyModeEnabled
        self.coverArtDisplayMode = coverArtDisplayMode
        self.bookDetailUseCoverBackdrop = bookDetailUseCoverBackdrop
        self.bookDetailUseArtworkColorScheme = bookDetailUseArtworkColorScheme
        self.showMiniPlayerProgress = showMiniPlayerProgress
        self.showMiniPlayerRouteButton = showMiniPlayerRouteButton
        self.feedbackControlScope = feedbackControlScope
        self.defaultPlaybackSpeed = defaultPlaybackSpeed
        self.readerSettings = readerSettings
    }
}
