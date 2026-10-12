import Foundation

public enum CatalogSearchField: String, Codable, CaseIterable, Identifiable, Sendable {
    case all
    case title
    case author
    case chapter
    case reader
    case genre

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

public enum NowPlayingPresentationMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case scrollable
    case panels

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .scrollable: "Scrollable"
        case .panels: "Panels"
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

public enum ReaderLayoutMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case paged
    case scrolling

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .paged: "Pages"
        case .scrolling: "Scrolling"
        }
    }
}

public struct ReaderSettings: Codable, Hashable, Sendable {
    public var followPlayback: Bool
    public var highlightCurrentText: Bool
    public var highlightMode: ReaderHighlightMode
    public var textScale: Double
    public var layoutMode: ReaderLayoutMode

    public init(
        followPlayback: Bool = true,
        highlightCurrentText: Bool = true,
        highlightMode: ReaderHighlightMode = .sentence,
        textScale: Double = 1.0,
        layoutMode: ReaderLayoutMode = .paged
    ) {
        self.followPlayback = followPlayback
        self.highlightCurrentText = highlightCurrentText
        self.highlightMode = highlightMode
        self.textScale = textScale
        self.layoutMode = layoutMode
    }

    private enum CodingKeys: String, CodingKey {
        case followPlayback
        case highlightCurrentText
        case highlightMode
        case textScale
        case layoutMode
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            followPlayback: try container.decodeIfPresent(Bool.self, forKey: .followPlayback) ?? true,
            highlightCurrentText: try container.decodeIfPresent(Bool.self, forKey: .highlightCurrentText) ?? true,
            highlightMode: try container.decodeIfPresent(ReaderHighlightMode.self, forKey: .highlightMode) ?? .sentence,
            textScale: try container.decodeIfPresent(Double.self, forKey: .textScale) ?? 1.0,
            layoutMode: try container.decodeIfPresent(ReaderLayoutMode.self, forKey: .layoutMode) ?? .paged
        )
    }
}

public struct UserSettings: Codable, Hashable, Sendable {
    public var themeMode: AppThemeMode
    public var animationSpeed: AnimationSpeed
    public var enabledSources: Set<BookSource>
    public var sourcePriority: [BookSource]
    public var preferredLanguages: Set<BookLanguagePreference>
    public var showSearchBarOnDiscover: Bool
    public var automaticSearchCachingEnabled: Bool
    public var autoDownloadMode: AutoDownloadMode
    public var autoDownloadLibraryAdditions: Bool
    public var downloadNetworkPolicy: DownloadNetworkPolicy
    public var downloadsOnlyModeEnabled: Bool
    public var coverArtDisplayMode: CoverArtDisplayMode
    public var bookDetailUseCoverBackdrop: Bool
    public var bookDetailUseArtworkColorScheme: Bool
    public var showMiniPlayerProgress: Bool
    public var showMiniPlayerRouteButton: Bool
    public var nowPlayingPresentationMode: NowPlayingPresentationMode
    public var feedbackControlScope: FeedbackControlScope
    public var defaultPlaybackSpeed: Double
    public var readerSettings: ReaderSettings

    public init(
        themeMode: AppThemeMode = .system,
        animationSpeed: AnimationSpeed = .standard,
        enabledSources: Set<BookSource> = [.libriVox],
        sourcePriority: [BookSource] = BookSource.defaultPublicCatalogPriority,
        preferredLanguages: Set<BookLanguagePreference> = [.english],
        showSearchBarOnDiscover: Bool = false,
        automaticSearchCachingEnabled: Bool = true,
        autoDownloadMode: AutoDownloadMode = .currentChapter,
        autoDownloadLibraryAdditions: Bool = false,
        downloadNetworkPolicy: DownloadNetworkPolicy = .wifiOnly,
        downloadsOnlyModeEnabled: Bool = false,
        coverArtDisplayMode: CoverArtDisplayMode = .fit,
        bookDetailUseCoverBackdrop: Bool = false,
        bookDetailUseArtworkColorScheme: Bool = true,
        showMiniPlayerProgress: Bool = true,
        showMiniPlayerRouteButton: Bool = true,
        nowPlayingPresentationMode: NowPlayingPresentationMode = .panels,
        feedbackControlScope: FeedbackControlScope = .book,
        defaultPlaybackSpeed: Double = 1.0,
        readerSettings: ReaderSettings = ReaderSettings()
    ) {
        self.themeMode = themeMode
        self.animationSpeed = animationSpeed
        self.enabledSources = enabledSources
        self.sourcePriority = BookSource.normalizedPublicCatalogPriority(sourcePriority)
        self.preferredLanguages = preferredLanguages
        self.showSearchBarOnDiscover = showSearchBarOnDiscover
        self.automaticSearchCachingEnabled = automaticSearchCachingEnabled
        self.autoDownloadMode = autoDownloadMode
        self.autoDownloadLibraryAdditions = autoDownloadLibraryAdditions
        self.downloadNetworkPolicy = downloadNetworkPolicy
        self.downloadsOnlyModeEnabled = downloadsOnlyModeEnabled
        self.coverArtDisplayMode = coverArtDisplayMode
        self.bookDetailUseCoverBackdrop = bookDetailUseCoverBackdrop
        self.bookDetailUseArtworkColorScheme = bookDetailUseArtworkColorScheme
        self.showMiniPlayerProgress = showMiniPlayerProgress
        self.showMiniPlayerRouteButton = showMiniPlayerRouteButton
        self.nowPlayingPresentationMode = nowPlayingPresentationMode
        self.feedbackControlScope = feedbackControlScope
        self.defaultPlaybackSpeed = defaultPlaybackSpeed
        self.readerSettings = readerSettings
    }

    private enum CodingKeys: String, CodingKey {
        case themeMode
        case animationSpeed
        case enabledSources
        case sourcePriority
        case preferredLanguages
        case showSearchBarOnDiscover
        case automaticSearchCachingEnabled
        case autoDownloadMode
        case autoDownloadLibraryAdditions
        case downloadNetworkPolicy
        case downloadsOnlyModeEnabled
        case coverArtDisplayMode
        case bookDetailUseCoverBackdrop
        case bookDetailUseArtworkColorScheme
        case showMiniPlayerProgress
        case showMiniPlayerRouteButton
        case nowPlayingPresentationMode
        case feedbackControlScope
        case defaultPlaybackSpeed
        case readerSettings
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            themeMode: try container.decodeIfPresent(AppThemeMode.self, forKey: .themeMode) ?? .system,
            animationSpeed: try container.decodeIfPresent(AnimationSpeed.self, forKey: .animationSpeed) ?? .standard,
            enabledSources: try container.decodeIfPresent(Set<BookSource>.self, forKey: .enabledSources) ?? [.libriVox],
            sourcePriority: try container.decodeIfPresent(
                [BookSource].self,
                forKey: .sourcePriority
            ) ?? BookSource.defaultPublicCatalogPriority,
            preferredLanguages: try container.decodeIfPresent(
                Set<BookLanguagePreference>.self,
                forKey: .preferredLanguages
            ) ?? [.english],
            showSearchBarOnDiscover: try container.decodeIfPresent(
                Bool.self,
                forKey: .showSearchBarOnDiscover
            ) ?? false,
            automaticSearchCachingEnabled: try container.decodeIfPresent(
                Bool.self,
                forKey: .automaticSearchCachingEnabled
            ) ?? true,
            autoDownloadMode: try container.decodeIfPresent(
                AutoDownloadMode.self,
                forKey: .autoDownloadMode
            ) ?? .currentChapter,
            autoDownloadLibraryAdditions: try container.decodeIfPresent(
                Bool.self,
                forKey: .autoDownloadLibraryAdditions
            ) ?? false,
            downloadNetworkPolicy: try container.decodeIfPresent(
                DownloadNetworkPolicy.self,
                forKey: .downloadNetworkPolicy
            ) ?? .wifiOnly,
            downloadsOnlyModeEnabled: try container.decodeIfPresent(
                Bool.self,
                forKey: .downloadsOnlyModeEnabled
            ) ?? false,
            coverArtDisplayMode: try container.decodeIfPresent(
                CoverArtDisplayMode.self,
                forKey: .coverArtDisplayMode
            ) ?? .fit,
            bookDetailUseCoverBackdrop: try container.decodeIfPresent(
                Bool.self,
                forKey: .bookDetailUseCoverBackdrop
            ) ?? false,
            bookDetailUseArtworkColorScheme: try container.decodeIfPresent(
                Bool.self,
                forKey: .bookDetailUseArtworkColorScheme
            ) ?? true,
            showMiniPlayerProgress: try container.decodeIfPresent(
                Bool.self,
                forKey: .showMiniPlayerProgress
            ) ?? true,
            showMiniPlayerRouteButton: try container.decodeIfPresent(
                Bool.self,
                forKey: .showMiniPlayerRouteButton
            ) ?? true,
            nowPlayingPresentationMode: try container.decodeIfPresent(
                NowPlayingPresentationMode.self,
                forKey: .nowPlayingPresentationMode
            ) ?? .panels,
            feedbackControlScope: try container.decodeIfPresent(
                FeedbackControlScope.self,
                forKey: .feedbackControlScope
            ) ?? .book,
            defaultPlaybackSpeed: try container.decodeIfPresent(Double.self, forKey: .defaultPlaybackSpeed) ?? 1.0,
            readerSettings: try container.decodeIfPresent(ReaderSettings.self, forKey: .readerSettings) ?? ReaderSettings()
        )
    }

    public mutating func normalizeForAvailableSources() {
        enabledSources = Set(enabledSources.filter(\.isPublicAudiobookSource))
        sourcePriority = BookSource.normalizedPublicCatalogPriority(sourcePriority)

        if !enabledSources.contains(.wolneLektury) {
            preferredLanguages.remove(.polish)
        }
        if enabledSources == [.wolneLektury] {
            preferredLanguages = preferredLanguages.filter { $0 == .all || $0 == .polish }
            if preferredLanguages.isEmpty {
                preferredLanguages.insert(.polish)
            }
            return
        }
        if preferredLanguages.isEmpty {
            preferredLanguages.insert(.english)
        }
    }

    public var orderedEnabledSources: [BookSource] {
        sourcePriority.filter { enabledSources.contains($0) }
    }

    public func shouldAutoDownloadLibraryAddition(wasInLibrary: Bool) -> Bool {
        autoDownloadLibraryAdditions && !wasInLibrary
    }
}
