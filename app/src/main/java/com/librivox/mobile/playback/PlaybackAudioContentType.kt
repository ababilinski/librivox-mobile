package com.librivox.mobile.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.work.NetworkType
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.ShareLinkMode

enum class AppThemeMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    Auto(
        preferenceValue = "auto",
        label = "Auto",
        description = "Follow the device theme.",
    ),
    Light(
        preferenceValue = "light",
        label = "Light",
        description = "Keep the app in light theme.",
    ),
    Dark(
        preferenceValue = "dark",
        label = "Dark",
        description = "Keep the app in dark theme.",
    );

    companion object {
        val Default: AppThemeMode = Auto

        fun fromPreference(value: String?): AppThemeMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class BookLanguagePreference(
    val preferenceValue: String,
    val label: String,
    val apiValue: String,
) {
    All(
        preferenceValue = "all",
        label = "All languages",
        apiValue = "",
    ),
    English(
        preferenceValue = "english",
        label = "English",
        apiValue = "English",
    ),
    French(
        preferenceValue = "french",
        label = "French",
        apiValue = "French",
    ),
    German(
        preferenceValue = "german",
        label = "German",
        apiValue = "German",
    ),
    Spanish(
        preferenceValue = "spanish",
        label = "Spanish",
        apiValue = "Spanish",
    ),
    Italian(
        preferenceValue = "italian",
        label = "Italian",
        apiValue = "Italian",
    ),
    Portuguese(
        preferenceValue = "portuguese",
        label = "Portuguese",
        apiValue = "Portuguese",
    ),
    Polish(
        preferenceValue = "polish",
        label = "Polish",
        apiValue = "Polish",
    );

    companion object {
        val Default: BookLanguagePreference = All
        val DefaultSelection: Set<BookLanguagePreference> = setOf(All)
        val ConcreteLanguages: List<BookLanguagePreference> =
            entries.filterNot { it == All }

        fun fromPreference(value: String?): BookLanguagePreference =
            entries
                .firstOrNull { it.preferenceValue == value }
                ?: Default

        fun fromPreferences(value: String?): Set<BookLanguagePreference> =
            value
                ?.split(',')
                ?.mapNotNull { token ->
                    entries.firstOrNull { it.preferenceValue == token.trim() }
                }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: DefaultSelection

        fun toPreferenceValue(languages: Set<BookLanguagePreference>): String =
            normalizeStored(languages).joinToString(",") { it.preferenceValue }

        fun normalizeStored(languages: Set<BookLanguagePreference>): Set<BookLanguagePreference> =
            when {
                languages.isEmpty() || All in languages -> DefaultSelection
                else -> (languages - All).ifEmpty { DefaultSelection }
            }
    }
}

enum class DownloadNetworkPolicy(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val audioWorkNetworkType: NetworkType,
    val autoDownloadsEnabled: Boolean,
) {
    WifiOnly(
        preferenceValue = "wifi_only",
        label = "Wi-Fi only",
        description = "Save audio only when Wi-Fi is available.",
        audioWorkNetworkType = NetworkType.UNMETERED,
        autoDownloadsEnabled = true,
    ),
    WifiAndData(
        preferenceValue = "wifi_and_data",
        label = "Cellular + Wi-Fi",
        description = "Save audio on any network connection.",
        audioWorkNetworkType = NetworkType.CONNECTED,
        autoDownloadsEnabled = true,
    ),
    Manual(
        preferenceValue = "manual",
        label = "Manual",
        description = "Legacy setting. Explicit downloads use any network connection.",
        audioWorkNetworkType = NetworkType.CONNECTED,
        autoDownloadsEnabled = false,
    );

    val bookInfoWorkNetworkType: NetworkType
        get() = NetworkType.CONNECTED

    companion object {
        val Default: DownloadNetworkPolicy = WifiOnly

        fun fromPreference(value: String?): DownloadNetworkPolicy =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class AutoDownloadMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val lookAheadCount: Int,
) {
    Off(
        preferenceValue = "off",
        label = "Off",
        description = "Only save audio after you choose Download.",
        lookAheadCount = 0,
    ),
    CurrentChapter(
        preferenceValue = "current_chapter",
        label = "Current chapter",
        description = "When playback starts, save the chapter you are listening to.",
        lookAheadCount = 1,
    ),
    CurrentAndNextFew(
        preferenceValue = "current_and_next_few",
        label = "Current + next 3",
        description = "When playback starts, save the current chapter and the next three.",
        lookAheadCount = 4,
    );

    val enabled: Boolean
        get() = lookAheadCount > 0

    companion object {
        val Default: AutoDownloadMode = Off

        fun fromPreference(value: String?): AutoDownloadMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class AnimationSpeed(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val motionScale: Float,
    val sharedTransitionGuardMillis: Long,
) {
    Fast(
        preferenceValue = "fast",
        label = "Fast",
        description = "Use tighter book detail transitions.",
        motionScale = 1.05f,
        sharedTransitionGuardMillis = 360L,
    ),
    Standard(
        preferenceValue = "standard",
        label = "Standard",
        description = "Use a readable Material book detail transition.",
        motionScale = 1.8f,
        sharedTransitionGuardMillis = 620L,
    ),
    Slow(
        preferenceValue = "slow",
        label = "Slow",
        description = "Slow source-aware transitions so the movement is easier to see.",
        motionScale = 2.9f,
        sharedTransitionGuardMillis = 1_000L,
    );

    companion object {
        val Default: AnimationSpeed = Standard

        fun fromPreference(value: String?): AnimationSpeed =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class SearchCacheSizeLimit(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val maxBytes: Long,
) {
    FiveMb(
        preferenceValue = "5mb",
        label = "5 MB",
        description = "Keep a small metadata index for the selected search sources.",
        maxBytes = 5L * 1024L * 1024L,
    ),
    TwentyMb(
        preferenceValue = "20mb",
        label = "20 MB",
        description = "Balanced cache size for faster search across a few selected sources.",
        maxBytes = 20L * 1024L * 1024L,
    ),
    FiftyMb(
        preferenceValue = "50mb",
        label = "50 MB",
        description = "Keep more title, chapter, author, and reader metadata offline for search.",
        maxBytes = 50L * 1024L * 1024L,
    ),
    OneHundredMb(
        preferenceValue = "100mb",
        label = "100 MB",
        description = "Use a larger local metadata index for broad source searches.",
        maxBytes = 100L * 1024L * 1024L,
    );

    companion object {
        val Default: SearchCacheSizeLimit = TwentyMb

        fun fromPreference(value: String?): SearchCacheSizeLimit =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class PlaybackAudioContentType(
    val preferenceValue: String,
    val label: String,
    val description: String,
    @param:C.AudioContentType val media3ContentType: Int,
) {
    Speech(
        preferenceValue = "speech",
        label = "Speech",
        description = "Best for audiobooks. Android avoids automatic ducking for speech so narration stays clear.",
        media3ContentType = C.AUDIO_CONTENT_TYPE_SPEECH,
    ),
    Music(
        preferenceValue = "music",
        label = "Music",
        description = "Treats playback like music, allowing normal system ducking and fade behavior.",
        media3ContentType = C.AUDIO_CONTENT_TYPE_MUSIC,
    );

    companion object {
        val Default: PlaybackAudioContentType = Speech

        @Suppress("UNUSED_PARAMETER")
        fun fromPreference(value: String?): PlaybackAudioContentType =
            Default
    }
}

enum class BookSourcePreference(
    val preferenceValue: String,
    val label: String,
    val description: String,
    val enabledSources: Set<CatalogSourcePreference>,
) {
    None(
        preferenceValue = "none",
        label = "No sources selected",
        description = "Discovery is paused until at least one catalog source is selected.",
        enabledSources = emptySet(),
    ),
    AllWithGutendex(
        preferenceValue = "all_with_gutendex",
        label = "All sources + Gutendex",
        description = "Browse audiobook catalogs and use Project Gutenberg metadata and ebook links from Gutendex.",
        enabledSources = setOf(
            CatalogSourcePreference.Lit2Go,
            CatalogSourcePreference.LibriVox,
            CatalogSourcePreference.WolneLektury,
            CatalogSourcePreference.Gutendex,
        ),
    ),
    All(
        preferenceValue = "all",
        label = "All audiobook sources",
        description = "Browse LibriVox, Lit2Go, and Polish-only Wolne Lektury together. When an English book appears in both LibriVox and Lit2Go, the Lit2Go recording is used and details are combined.",
        enabledSources = setOf(
            CatalogSourcePreference.Lit2Go,
            CatalogSourcePreference.LibriVox,
            CatalogSourcePreference.WolneLektury,
        ),
    ),
    LibriVoxAndLit2GoAndGutendex(
        preferenceValue = "librivox_lit2go_gutendex",
        label = "LibriVox + Lit2Go + Gutendex",
        description = "Browse the English audiobook catalogs and enrich matches with Project Gutenberg metadata.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.LibriVox, CatalogSourcePreference.Gutendex),
    ),
    LibriVoxAndLit2Go(
        preferenceValue = "librivox_lit2go",
        label = "LibriVox + Lit2Go",
        description = "Browse the English-language public audiobook catalogs together.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.LibriVox),
    ),
    LibriVoxAndWolneLekturyAndGutendex(
        preferenceValue = "librivox_wolne_lektury_gutendex",
        label = "LibriVox + Wolne Lektury + Gutendex",
        description = "Browse LibriVox, Polish-only Wolne Lektury, and Project Gutenberg metadata together.",
        enabledSources = setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.WolneLektury, CatalogSourcePreference.Gutendex),
    ),
    LibriVoxAndWolneLektury(
        preferenceValue = "librivox_wolne_lektury",
        label = "LibriVox + Wolne Lektury",
        description = "Browse LibriVox and the Polish-only Wolne Lektury catalog.",
        enabledSources = setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.WolneLektury),
    ),
    Lit2GoAndWolneLekturyAndGutendex(
        preferenceValue = "lit2go_wolne_lektury_gutendex",
        label = "Lit2Go + Wolne Lektury + Gutendex",
        description = "Browse Lit2Go, Polish-only Wolne Lektury, and Project Gutenberg metadata together.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury, CatalogSourcePreference.Gutendex),
    ),
    Lit2GoAndWolneLektury(
        preferenceValue = "lit2go_wolne_lektury",
        label = "Lit2Go + Wolne Lektury",
        description = "Browse Lit2Go and the Polish-only Wolne Lektury catalog.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.WolneLektury),
    ),
    WolneLekturyAndGutendex(
        preferenceValue = "wolne_lektury_gutendex",
        label = "Wolne Lektury + Gutendex",
        description = "Browse Polish-only Wolne Lektury and Project Gutenberg metadata together.",
        enabledSources = setOf(CatalogSourcePreference.WolneLektury, CatalogSourcePreference.Gutendex),
    ),
    LibriVoxAndGutendex(
        preferenceValue = "librivox_gutendex",
        label = "LibriVox + Gutendex",
        description = "Browse LibriVox and enrich matches with Project Gutenberg metadata.",
        enabledSources = setOf(CatalogSourcePreference.LibriVox, CatalogSourcePreference.Gutendex),
    ),
    Lit2GoAndGutendex(
        preferenceValue = "lit2go_gutendex",
        label = "Lit2Go + Gutendex",
        description = "Browse Lit2Go and enrich matches with Project Gutenberg metadata.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go, CatalogSourcePreference.Gutendex),
    ),
    LibriVoxOnly(
        preferenceValue = "librivox_only",
        label = "LibriVox only",
        description = "Volunteer-recorded public-domain audiobooks.",
        enabledSources = setOf(CatalogSourcePreference.LibriVox),
    ),
    Lit2GoOnly(
        preferenceValue = "lit2go_only",
        label = "Lit2Go only",
        description = "Free educational audiobooks from the University of South Florida.",
        enabledSources = setOf(CatalogSourcePreference.Lit2Go),
    ),
    WolneLekturyOnly(
        preferenceValue = "wolne_lektury_only",
        label = "Wolne Lektury only",
        description = "Free Polish public-domain audiobooks from WolneLektury.pl.",
        enabledSources = setOf(CatalogSourcePreference.WolneLektury),
    ),
    GutendexOnly(
        preferenceValue = "gutendex_only",
        label = "Gutendex only",
        description = "Project Gutenberg audiobook records and metadata through the Gutendex API.",
        enabledSources = setOf(CatalogSourcePreference.Gutendex),
    );

    val supportsPolish: Boolean
        get() = CatalogSourcePreference.WolneLektury in enabledSources

    companion object {
        val SelectableSources: List<CatalogSourcePreference> = listOf(
            CatalogSourcePreference.LibriVox,
            CatalogSourcePreference.Lit2Go,
            CatalogSourcePreference.Gutendex,
            CatalogSourcePreference.WolneLektury,
        )
        val SelectableSourceSet: Set<CatalogSourcePreference> = SelectableSources.toSet()
        val PrimarySelectableSources: List<CatalogSourcePreference> = listOf(CatalogSourcePreference.LibriVox)
        val OtherSelectableSources: List<CatalogSourcePreference> =
            SelectableSources.filterNot { it in PrimarySelectableSources }
        val Default: BookSourcePreference = LibriVoxOnly

        fun fromPreference(value: String?): BookSourcePreference {
            val stored = when (value) {
                "both" -> All
                "librivox" -> LibriVoxOnly
                "lit2go" -> Lit2GoOnly
                "wolne_lektury" -> WolneLekturyOnly
                "gutendex" -> GutendexOnly
                else -> entries.firstOrNull { it.preferenceValue == value } ?: Default
            }
            return if (stored == None) {
                None
            } else {
                fromEnabledSources(stored.enabledSources)
            }
        }

        fun fromEnabledSources(sources: Set<CatalogSourcePreference>): BookSourcePreference {
            if (sources.isEmpty()) return None
            val normalized = sources.intersect(SelectableSourceSet)
            if (normalized.isEmpty()) return Default
            return entries.firstOrNull { it.enabledSources == normalized }
                ?: Default
        }
    }
}

fun BookSourcePreference.selectableLanguages(): List<BookLanguagePreference> {
    val supportsNonPolish = enabledSources.any { it != CatalogSourcePreference.WolneLektury }
    return BookLanguagePreference.ConcreteLanguages.filter { language ->
        when (language) {
            BookLanguagePreference.Polish -> supportsPolish
            else -> supportsNonPolish
        }
    }
}

fun Set<BookLanguagePreference>.normalizeForCatalogSource(
    source: BookSourcePreference,
): Set<BookLanguagePreference> {
    val selectable = source.selectableLanguages().toSet()
    val concrete = when {
        isEmpty() || BookLanguagePreference.All in this -> selectable
        else -> (this - BookLanguagePreference.All).intersect(selectable)
    }.toMutableSet()
    return concrete.ifEmpty { selectable }
}

fun Set<BookLanguagePreference>.isAllCatalogLanguagesSelected(source: BookSourcePreference): Boolean {
    val selectable = source.selectableLanguages().toSet()
    return BookLanguagePreference.All in this ||
        selectable.isNotEmpty() && normalizeForCatalogSource(source).containsAll(selectable)
}

enum class CatalogSourcePreference(
    val label: String,
    val description: String,
) {
    Lit2Go(
        label = "Lit2Go",
        description = "Educational public-domain recordings from the University of South Florida.",
    ),
    LibriVox(
        label = "LibriVox",
        description = "Volunteer-recorded public-domain audiobooks.",
    ),
    Gutendex(
        label = "Gutendex",
        description = "A JSON API for Project Gutenberg metadata, including public-domain audiobook records.",
    ),
    WolneLektury(
        label = "Wolne Lektury",
        description = "Polish-only public-domain audiobooks and literary metadata.",
    ),
}

enum class CastUiMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    Custom(
        preferenceValue = "custom",
        label = "Custom in-app player",
        description = "Use the audiobook prototype's own Cast picker, mini controller, and expanded sheet.",
    ),
    GoogleDefault(
        preferenceValue = "google_default",
        label = "Google default Cast UI",
        description = "Use the Cast SDK's MediaRouteButton, introductory overlay, and ExpandedControllerActivity.",
    );

    companion object {
        val Default: CastUiMode = Custom

        fun fromPreference(value: String?): CastUiMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class CastPreloadTime(
    val seconds: Int,
    val label: String,
    val preferenceValue: String,
) {
    Short(15, "15 seconds", "15"),
    Medium(20, "20 seconds", "20"),
    Long(30, "30 seconds", "30");

    companion object {
        val Default: CastPreloadTime = Medium

        fun fromPreference(value: String?): CastPreloadTime =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class NotificationControlsMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    None(
        preferenceValue = "none",
        label = "None",
        description = "Keep the media notification to transport controls only.",
    ),
    Like(
        preferenceValue = "like",
        label = "Like",
        description = "Show one thumbs-up action in the media notification.",
    ),
    Speed(
        preferenceValue = "speed",
        label = "Speed",
        description = "Show one speed action that cycles 0.25x, 0.5x, 1x, 1.25x, 1.5x, and 2x.",
    ),
    Both(
        preferenceValue = "both",
        label = "Both",
        description = "Show the thumbs-up action and the speed cycle action.",
    );

    companion object {
        val Default: NotificationControlsMode = Like

        fun fromPreference(value: String?): NotificationControlsMode =
            when (value) {
                "feedback" -> Like
                else -> entries.firstOrNull { it.preferenceValue == value } ?: Default
            }
    }
}

enum class MiniPlayerSkipMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    Chapter(
        preferenceValue = "chapter",
        label = "Chapter",
        description = "Show previous and next chapter buttons in the mini player.",
    ),
    TenSeconds(
        preferenceValue = "ten_seconds",
        label = "10 seconds",
        description = "Show 10-second rewind and forward buttons in the mini player.",
    );

    companion object {
        val Default: MiniPlayerSkipMode = Chapter

        fun fromPreference(value: String?): MiniPlayerSkipMode =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class FeedbackControlScope(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    Chapter(
        preferenceValue = "chapter",
        label = "Chapters",
        description = "Like or dislike individual chapters from Now Playing and chapter menus.",
    ),
    Book(
        preferenceValue = "book",
        label = "Whole book",
        description = "Like or dislike only the current book from Now Playing.",
    ),
    Hidden(
        preferenceValue = "hidden",
        label = "Hidden",
        description = "Hide like and dislike buttons in the app and notification.",
    );

    companion object {
        val Default: FeedbackControlScope = Chapter

        fun fromPreference(value: String?): FeedbackControlScope =
            entries.firstOrNull { it.preferenceValue == value } ?: Default
    }
}

enum class ReaderHighlightMode(
    val preferenceValue: String,
    val label: String,
    val description: String,
) {
    SourceSegment(
        preferenceValue = "source_segment",
        label = "Source paragraph",
        description = "Use the Wolne source-sync text block.",
    ),
    BreakIteratorProportional(
        preferenceValue = "break_iterator_proportional",
        label = "BreakIterator sentence",
        description = "Split each timed paragraph into sentence ranges with proportional timing.",
    );

    companion object {
        val Default: ReaderHighlightMode = BreakIteratorProportional

        fun fromPreference(value: String?): ReaderHighlightMode =
            when (value) {
                "sentence", "few_sentences" -> BreakIteratorProportional
                "segment" -> SourceSegment
                else -> entries.firstOrNull { it.preferenceValue == value } ?: Default
            }
    }
}

data class ReaderSettings(
    val followPlayback: Boolean = true,
    val highlightCurrentText: Boolean = true,
    val highlightMode: ReaderHighlightMode = ReaderHighlightMode.Default,
    val textScale: Float = DefaultTextScale,
) {
    companion object {
        const val MinTextScale: Float = 0.90f
        const val DefaultTextScale: Float = 1.00f
        const val MaxTextScale: Float = 1.30f

        fun clampTextScale(value: Float): Float =
            value.coerceIn(MinTextScale, MaxTextScale)
    }
}

class PlaybackSettingsStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val audioContentType: PlaybackAudioContentType
        get() = PlaybackAudioContentType.fromPreference(
            preferences.getString(KEY_AUDIO_CONTENT_TYPE, PlaybackAudioContentType.Default.preferenceValue),
        )

    val themeMode: AppThemeMode
        get() = AppThemeMode.fromPreference(
            preferences.getString(KEY_THEME_MODE, AppThemeMode.Default.preferenceValue),
        )

    val animationSpeed: AnimationSpeed
        get() = AnimationSpeed.fromPreference(
            preferences.getString(KEY_ANIMATION_SPEED, AnimationSpeed.Default.preferenceValue),
        )

    val bookDetailUseArtworkColorScheme: Boolean
        get() = preferences.getBoolean(KEY_BOOK_DETAIL_USE_ARTWORK_COLOR_SCHEME, false)

    val bookDetailUseCoverBackdrop: Boolean
        get() = preferences.getBoolean(KEY_BOOK_DETAIL_USE_COVER_BACKDROP, false)

    val coverArtDisplayMode: CoverArtDisplayMode
        get() = CoverArtDisplayMode.fromPreference(
            preferences.getString(KEY_COVER_ART_DISPLAY_MODE, CoverArtDisplayMode.Default.preferenceValue),
        )

    val preferredLanguage: BookLanguagePreference
        get() = preferredLanguages.singleOrNull() ?: BookLanguagePreference.All

    val preferredLanguages: Set<BookLanguagePreference>
        get() {
            preferences.getString(KEY_BOOK_LANGUAGES, null)?.let { saved ->
                return BookLanguagePreference.fromPreferences(saved)
            }
            return setOf(
                BookLanguagePreference.fromPreference(
                    preferences.getString(KEY_BOOK_LANGUAGE, BookLanguagePreference.Default.preferenceValue),
                ),
            )
        }

    val bookSourcePreference: BookSourcePreference
        get() = BookSourcePreference.fromPreference(
            preferences.getString(KEY_BOOK_SOURCE, BookSourcePreference.Default.preferenceValue),
        )

    val searchCacheSizeLimit: SearchCacheSizeLimit
        get() = SearchCacheSizeLimit.fromPreference(
            preferences.getString(KEY_SEARCH_CACHE_SIZE_LIMIT, SearchCacheSizeLimit.Default.preferenceValue),
        )

    val automaticSearchCachingEnabled: Boolean
        get() = preferences.getBoolean(
            KEY_AUTOMATIC_SEARCH_CACHING_ENABLED,
            DEFAULT_AUTOMATIC_SEARCH_CACHING_ENABLED,
        )

    val downloadsOnlyModeEnabled: Boolean
        get() = preferences.getBoolean(KEY_DOWNLOADS_ONLY_MODE_ENABLED, false)

    val autoDownloadWhenListening: Boolean
        get() = autoDownloadMode.enabled

    val autoDownloadMode: AutoDownloadMode
        get() {
            preferences.getString(KEY_AUTO_DOWNLOAD_MODE, null)?.let { saved ->
                return AutoDownloadMode.fromPreference(saved)
            }
            preferences.getString(KEY_LISTENING_DOWNLOAD_POLICY, null)?.let { legacyPolicy ->
                return if (DownloadNetworkPolicy.fromPreference(legacyPolicy) == DownloadNetworkPolicy.Manual) {
                    AutoDownloadMode.Off
                } else {
                    AutoDownloadMode.CurrentChapter
                }
            }
            return if (preferences.getBoolean(KEY_AUTO_DOWNLOAD_WHEN_LISTENING, false)) {
                AutoDownloadMode.CurrentChapter
            } else {
                AutoDownloadMode.Default
            }
        }

    val downloadNetworkPolicy: DownloadNetworkPolicy
        get() {
            preferences.getString(KEY_DOWNLOAD_NETWORK_POLICY, null)?.let { saved ->
                return DownloadNetworkPolicy.fromPreference(saved)
                    .takeUnless { it == DownloadNetworkPolicy.Manual }
                    ?: DownloadNetworkPolicy.Default
            }
            preferences.getString(KEY_LISTENING_DOWNLOAD_POLICY, null)?.let { legacyPolicy ->
                return DownloadNetworkPolicy.fromPreference(legacyPolicy)
                    .takeUnless { it == DownloadNetworkPolicy.Manual }
                    ?: DownloadNetworkPolicy.Default
            }
            return DownloadNetworkPolicy.Default
        }

    val notificationControlsMode: NotificationControlsMode
        get() = NotificationControlsMode.fromPreference(
            preferences.getString(
                KEY_NOTIFICATION_CONTROLS_MODE,
                NotificationControlsMode.Default.preferenceValue,
            ),
        )

    val miniPlayerSkipMode: MiniPlayerSkipMode
        get() = MiniPlayerSkipMode.fromPreference(
            preferences.getString(KEY_MINI_PLAYER_SKIP_MODE, MiniPlayerSkipMode.Default.preferenceValue),
        )

    val showMiniPlayerSecondaryControls: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MINI_PLAYER_SECONDARY_CONTROLS, true)

    val showMiniPlayerCastButton: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MINI_PLAYER_CAST_BUTTON, true)

    val showMiniPlayerProgressBar: Boolean
        get() = preferences.getBoolean(KEY_SHOW_MINI_PLAYER_PROGRESS_BAR, true)

    val feedbackControlScope: FeedbackControlScope
        get() = FeedbackControlScope.fromPreference(
            preferences.getString(KEY_FEEDBACK_CONTROL_SCOPE, FeedbackControlScope.Default.preferenceValue),
        )

    val shareLinkMode: ShareLinkMode
        get() = ShareLinkMode.fromPreference(
            preferences.getString(KEY_SHARE_LINK_MODE, ShareLinkMode.Default.preferenceValue),
        )

    val castUiMode: CastUiMode
        get() = CastUiMode.fromPreference(
            preferences.getString(KEY_CAST_UI_MODE, CastUiMode.Default.preferenceValue),
        )

    val castIntroDismissed: Boolean
        get() = preferences.getBoolean(KEY_CAST_INTRO_DISMISSED, false)

    val castUseArtworkColorScheme: Boolean
        get() = preferences.getBoolean(KEY_CAST_USE_ARTWORK_COLOR_SCHEME, true)

    val castResumeSession: Boolean
        get() = preferences.getBoolean(KEY_CAST_RESUME_SESSION, false)

    val castStopReceiverOnDisconnect: Boolean
        get() = preferences.getBoolean(KEY_CAST_STOP_RECEIVER, true)

    val castPreloadTime: CastPreloadTime
        get() = CastPreloadTime.fromPreference(
            preferences.getString(KEY_CAST_PRELOAD_SECONDS, CastPreloadTime.Default.preferenceValue),
        )

    val castReceiverAppId: String
        get() = preferences.getString(KEY_CAST_RECEIVER_APP_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CAST_RECEIVER_APP_ID

    val castForceLocalHttp: Boolean
        get() = preferences.getBoolean(KEY_CAST_FORCE_LOCAL_HTTP, false)

    val castVerboseLogging: Boolean
        get() = preferences.getBoolean(KEY_CAST_VERBOSE, false)

    val castDiagnosticsEnabled: Boolean
        get() = preferences.getBoolean(KEY_CAST_DIAGNOSTICS_ENABLED, false)

    val castDiagnosticsOverlay: Boolean
        get() = preferences.getBoolean(KEY_CAST_DIAGNOSTICS_OVERLAY, false)

    val readerSettings: ReaderSettings
        get() = ReaderSettings(
            followPlayback = preferences.getBoolean(KEY_READER_FOLLOW_PLAYBACK, true),
            highlightCurrentText = preferences.getBoolean(KEY_READER_HIGHLIGHT_CURRENT_TEXT, true),
            highlightMode = ReaderHighlightMode.fromPreference(
                preferences.getString(KEY_READER_HIGHLIGHT_MODE, ReaderHighlightMode.Default.preferenceValue),
            ),
            textScale = ReaderSettings.clampTextScale(
                preferences.getFloat(KEY_READER_TEXT_SCALE, ReaderSettings.DefaultTextScale),
            ),
        )

    @Suppress("UNUSED_PARAMETER")
    fun saveAudioContentType(contentType: PlaybackAudioContentType) {
        preferences.edit()
            .putString(KEY_AUDIO_CONTENT_TYPE, PlaybackAudioContentType.Default.preferenceValue)
            .apply()
    }

    fun saveThemeMode(mode: AppThemeMode) {
        preferences.edit()
            .putString(KEY_THEME_MODE, mode.preferenceValue)
            .apply()
    }

    fun saveAnimationSpeed(speed: AnimationSpeed) {
        preferences.edit()
            .putString(KEY_ANIMATION_SPEED, speed.preferenceValue)
            .apply()
    }

    fun saveBookDetailUseArtworkColorScheme(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BOOK_DETAIL_USE_ARTWORK_COLOR_SCHEME, enabled)
            .apply()
    }

    fun saveBookDetailUseCoverBackdrop(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_BOOK_DETAIL_USE_COVER_BACKDROP, enabled)
            .apply()
    }

    fun saveCoverArtDisplayMode(mode: CoverArtDisplayMode) {
        preferences.edit()
            .putString(KEY_COVER_ART_DISPLAY_MODE, mode.preferenceValue)
            .apply()
    }

    fun savePreferredLanguage(language: BookLanguagePreference) {
        savePreferredLanguages(setOf(language))
    }

    fun savePreferredLanguages(languages: Set<BookLanguagePreference>) {
        val normalized = BookLanguagePreference.normalizeStored(languages)
        preferences.edit()
            .putString(KEY_BOOK_LANGUAGES, BookLanguagePreference.toPreferenceValue(normalized))
            .putString(KEY_BOOK_LANGUAGE, preferredLegacyLanguage(normalized).preferenceValue)
            .apply()
    }

    fun saveBookSourcePreference(preference: BookSourcePreference) {
        val normalized = BookSourcePreference.fromPreference(preference.preferenceValue)
        preferences.edit()
            .putString(KEY_BOOK_SOURCE, normalized.preferenceValue)
            .apply()
    }

    fun saveSearchCacheSizeLimit(limit: SearchCacheSizeLimit) {
        preferences.edit()
            .putString(KEY_SEARCH_CACHE_SIZE_LIMIT, limit.preferenceValue)
            .apply()
    }

    fun saveAutomaticSearchCachingEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTOMATIC_SEARCH_CACHING_ENABLED, enabled)
            .apply()
    }

    fun saveDownloadsOnlyModeEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_DOWNLOADS_ONLY_MODE_ENABLED, enabled)
            .apply()
    }

    fun saveAutoDownloadWhenListening(enabled: Boolean) {
        val nextMode = if (enabled) {
            autoDownloadMode.takeIf { it.enabled } ?: AutoDownloadMode.CurrentChapter
        } else {
            AutoDownloadMode.Off
        }
        saveAutoDownloadMode(nextMode)
    }

    fun saveAutoDownloadMode(mode: AutoDownloadMode) {
        preferences.edit()
            .putString(KEY_AUTO_DOWNLOAD_MODE, mode.preferenceValue)
            .putBoolean(KEY_AUTO_DOWNLOAD_WHEN_LISTENING, mode.enabled)
            .putString(
                KEY_LISTENING_DOWNLOAD_POLICY,
                if (mode.enabled) downloadNetworkPolicy.preferenceValue else DownloadNetworkPolicy.Manual.preferenceValue,
            )
            .apply()
    }

    fun saveDownloadNetworkPolicy(policy: DownloadNetworkPolicy) {
        val normalized = policy.takeUnless { it == DownloadNetworkPolicy.Manual } ?: DownloadNetworkPolicy.Default
        preferences.edit()
            .putString(KEY_LISTENING_DOWNLOAD_POLICY, if (autoDownloadMode.enabled) normalized.preferenceValue else DownloadNetworkPolicy.Manual.preferenceValue)
            .putString(KEY_DOWNLOAD_NETWORK_POLICY, normalized.preferenceValue)
            .apply()
    }

    fun saveNotificationControlsMode(mode: NotificationControlsMode) {
        preferences.edit()
            .putString(KEY_NOTIFICATION_CONTROLS_MODE, mode.preferenceValue)
            .apply()
    }

    fun saveMiniPlayerSkipMode(mode: MiniPlayerSkipMode) {
        preferences.edit()
            .putString(KEY_MINI_PLAYER_SKIP_MODE, mode.preferenceValue)
            .apply()
    }

    fun saveShowMiniPlayerSecondaryControls(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SHOW_MINI_PLAYER_SECONDARY_CONTROLS, enabled)
            .apply()
    }

    fun saveShowMiniPlayerCastButton(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SHOW_MINI_PLAYER_CAST_BUTTON, enabled)
            .apply()
    }

    fun saveShowMiniPlayerProgressBar(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SHOW_MINI_PLAYER_PROGRESS_BAR, enabled)
            .apply()
    }

    fun saveFeedbackControlScope(scope: FeedbackControlScope) {
        preferences.edit()
            .putString(KEY_FEEDBACK_CONTROL_SCOPE, scope.preferenceValue)
            .apply()
    }

    fun saveShareLinkMode(mode: ShareLinkMode) {
        preferences.edit()
            .putString(KEY_SHARE_LINK_MODE, mode.preferenceValue)
            .apply()
    }

    fun saveCastUiMode(mode: CastUiMode) {
        preferences.edit().putString(KEY_CAST_UI_MODE, mode.preferenceValue).apply()
    }

    fun saveCastIntroDismissed(dismissed: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_INTRO_DISMISSED, dismissed).apply()
    }

    fun saveCastUseArtworkColorScheme(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_USE_ARTWORK_COLOR_SCHEME, enabled).apply()
    }

    fun saveCastResumeSession(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_RESUME_SESSION, enabled).apply()
    }

    fun saveCastStopReceiverOnDisconnect(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_STOP_RECEIVER, enabled).apply()
    }

    fun saveCastPreloadTime(time: CastPreloadTime) {
        preferences.edit().putString(KEY_CAST_PRELOAD_SECONDS, time.preferenceValue).apply()
    }

    fun saveCastReceiverAppId(value: String?) {
        val normalized = value?.trim().orEmpty()
        preferences.edit().apply {
            if (normalized.isBlank()) {
                remove(KEY_CAST_RECEIVER_APP_ID)
            } else {
                putString(KEY_CAST_RECEIVER_APP_ID, normalized)
            }
        }.apply()
    }

    fun saveCastForceLocalHttp(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_FORCE_LOCAL_HTTP, enabled).apply()
    }

    fun saveCastVerboseLogging(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_VERBOSE, enabled).apply()
    }

    fun saveCastDiagnosticsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_DIAGNOSTICS_ENABLED, enabled).apply()
    }

    fun saveCastDiagnosticsOverlay(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAST_DIAGNOSTICS_OVERLAY, enabled).apply()
    }

    fun saveReaderSettings(settings: ReaderSettings) {
        preferences.edit()
            .putBoolean(KEY_READER_FOLLOW_PLAYBACK, settings.followPlayback)
            .putBoolean(KEY_READER_HIGHLIGHT_CURRENT_TEXT, settings.highlightCurrentText)
            .putString(KEY_READER_HIGHLIGHT_MODE, settings.highlightMode.preferenceValue)
            .putFloat(KEY_READER_TEXT_SCALE, ReaderSettings.clampTextScale(settings.textScale))
            .apply()
    }

    fun saveReaderFollowPlayback(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_READER_FOLLOW_PLAYBACK, enabled).apply()
    }

    fun saveReaderHighlightCurrentText(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_READER_HIGHLIGHT_CURRENT_TEXT, enabled).apply()
    }

    fun saveReaderHighlightMode(mode: ReaderHighlightMode) {
        preferences.edit().putString(KEY_READER_HIGHLIGHT_MODE, mode.preferenceValue).apply()
    }

    fun saveReaderTextScale(textScale: Float) {
        preferences.edit()
            .putFloat(KEY_READER_TEXT_SCALE, ReaderSettings.clampTextScale(textScale))
            .apply()
    }

    fun resetCastPreferences() {
        preferences.edit()
            .remove(KEY_CAST_UI_MODE)
            .remove(KEY_CAST_INTRO_DISMISSED)
            .remove(KEY_CAST_USE_ARTWORK_COLOR_SCHEME)
            .remove(KEY_CAST_RESUME_SESSION)
            .remove(KEY_CAST_STOP_RECEIVER)
            .remove(KEY_CAST_PRELOAD_SECONDS)
            .remove(KEY_CAST_RECEIVER_APP_ID)
            .remove(KEY_CAST_FORCE_LOCAL_HTTP)
            .remove(KEY_CAST_VERBOSE)
            .remove(KEY_CAST_DIAGNOSTICS_ENABLED)
            .remove(KEY_CAST_DIAGNOSTICS_OVERLAY)
            .apply()
    }

    fun speedFor(bookId: String): Float {
        val perBook = preferences.getFloat(speedKey(bookId), Float.NaN)
        if (!perBook.isNaN()) return PlaybackSpeed.clamp(perBook).value
        return PlaybackSpeed.clamp(preferences.getFloat(KEY_DEFAULT_SPEED, 1f)).value
    }

    fun saveSpeed(bookId: String?, speed: Float) {
        preferences.edit().apply {
            putFloat(KEY_DEFAULT_SPEED, speed)
            if (!bookId.isNullOrBlank()) {
                putFloat(speedKey(bookId), speed)
            }
        }.apply()
    }

    fun registerAudioContentTypeListener(
        onChanged: (PlaybackAudioContentType) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_AUDIO_CONTENT_TYPE) {
                    onChanged(audioContentType)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerThemeModeListener(
        onChanged: (AppThemeMode) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_THEME_MODE) {
                    onChanged(themeMode)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerAnimationSpeedListener(
        onChanged: (AnimationSpeed) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_ANIMATION_SPEED) {
                    onChanged(animationSpeed)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerBookDetailColorSchemeListener(
        onChanged: (Boolean) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_BOOK_DETAIL_USE_ARTWORK_COLOR_SCHEME) {
                    onChanged(bookDetailUseArtworkColorScheme)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerBookDetailCoverBackdropListener(
        onChanged: (Boolean) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_BOOK_DETAIL_USE_COVER_BACKDROP) {
                    onChanged(bookDetailUseCoverBackdrop)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerCoverArtDisplayModeListener(
        onChanged: (CoverArtDisplayMode) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_COVER_ART_DISPLAY_MODE) {
                    onChanged(coverArtDisplayMode)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerBookLanguageListener(
        onChanged: (Set<BookLanguagePreference>) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_BOOK_LANGUAGE || key == KEY_BOOK_LANGUAGES) {
                    onChanged(preferredLanguages)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerBookSourceListener(
        onChanged: (BookSourcePreference) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_BOOK_SOURCE) {
                    onChanged(bookSourcePreference)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerSearchCacheSettingsListener(
        onChanged: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_SEARCH_CACHE_SIZE_LIMIT || key == KEY_AUTOMATIC_SEARCH_CACHING_ENABLED) {
                    onChanged()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerDownloadSettingsListener(
        onChanged: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (
                    key == KEY_AUTO_DOWNLOAD_WHEN_LISTENING ||
                    key == KEY_AUTO_DOWNLOAD_MODE ||
                    key == KEY_DOWNLOAD_NETWORK_POLICY ||
                    key == KEY_LISTENING_DOWNLOAD_POLICY ||
                    key == KEY_DOWNLOADS_ONLY_MODE_ENABLED
                ) {
                    onChanged()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerNotificationControlsListener(
        onChanged: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_NOTIFICATION_CONTROLS_MODE || key == KEY_FEEDBACK_CONTROL_SCOPE) {
                    onChanged()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerPlaybackUiSettingsListener(
        onChanged: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && key in PLAYBACK_UI_PREFERENCE_KEYS) {
                    onChanged()
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerCastSettingsListener(
        onChanged: (String?) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && key in CAST_PREFERENCE_KEYS) {
                    onChanged(key)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun registerReaderSettingsListener(
        onChanged: (ReaderSettings) -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && key in READER_PREFERENCE_KEYS) {
                    onChanged(readerSettings)
                }
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun speedKey(bookId: String): String = "speed_$bookId"

    private fun preferredLegacyLanguage(languages: Set<BookLanguagePreference>): BookLanguagePreference =
        languages.singleOrNull() ?: BookLanguagePreference.All

    companion object {
        const val DEFAULT_CAST_RECEIVER_APP_ID = "CC1AD845"
        internal const val PREFERENCES_NAME = "playback_settings"
        internal const val KEY_AUDIO_CONTENT_TYPE = "audio_content_type"
        internal const val KEY_DEFAULT_SPEED = "default_speed"
        internal const val KEY_THEME_MODE = "theme_mode"
        internal const val KEY_ANIMATION_SPEED = "animation_speed"
        internal const val KEY_BOOK_DETAIL_USE_ARTWORK_COLOR_SCHEME = "book_detail_use_artwork_color_scheme"
        internal const val KEY_BOOK_DETAIL_USE_COVER_BACKDROP = "book_detail_use_cover_backdrop"
        internal const val KEY_COVER_ART_DISPLAY_MODE = "cover_art_display_mode"
        internal const val KEY_BOOK_LANGUAGE = "book_language"
        internal const val KEY_BOOK_LANGUAGES = "book_languages"
        internal const val KEY_BOOK_SOURCE = "book_source_preference"
        internal const val KEY_SEARCH_CACHE_SIZE_LIMIT = "search_cache_size_limit"
        internal const val KEY_AUTOMATIC_SEARCH_CACHING_ENABLED = "automatic_search_caching_enabled"
        internal const val DEFAULT_AUTOMATIC_SEARCH_CACHING_ENABLED = true
        internal const val KEY_DOWNLOADS_ONLY_MODE_ENABLED = "downloads_only_mode_enabled"
        internal const val KEY_AUTO_DOWNLOAD_WHEN_LISTENING = "auto_download_when_listening"
        internal const val KEY_AUTO_DOWNLOAD_MODE = "auto_download_mode"
        internal const val KEY_LISTENING_DOWNLOAD_POLICY = "listening_download_policy"
        internal const val KEY_NOTIFICATION_CONTROLS_MODE = "notification_controls_mode"
        internal const val KEY_MINI_PLAYER_SKIP_MODE = "mini_player_skip_mode"
        internal const val KEY_SHOW_MINI_PLAYER_SECONDARY_CONTROLS = "show_mini_player_secondary_controls"
        internal const val KEY_SHOW_MINI_PLAYER_CAST_BUTTON = "show_mini_player_cast_button"
        internal const val KEY_SHOW_MINI_PLAYER_PROGRESS_BAR = "show_mini_player_progress_bar"
        internal const val KEY_FEEDBACK_CONTROL_SCOPE = "feedback_control_scope"
        internal const val KEY_SHARE_LINK_MODE = "share_link_mode"
        internal const val KEY_DOWNLOAD_NETWORK_POLICY = "download_network_policy"
        const val KEY_CAST_UI_MODE = "cast_ui_mode"
        const val KEY_CAST_INTRO_DISMISSED = "cast_intro_dismissed"
        const val KEY_CAST_USE_ARTWORK_COLOR_SCHEME = "cast_use_artwork_color_scheme"
        const val KEY_CAST_RESUME_SESSION = "cast_resume_session"
        const val KEY_CAST_STOP_RECEIVER = "cast_stop_receiver_on_disconnect"
        const val KEY_CAST_PRELOAD_SECONDS = "cast_preload_seconds"
        const val KEY_CAST_RECEIVER_APP_ID = "cast_receiver_app_id"
        const val KEY_CAST_FORCE_LOCAL_HTTP = "cast_force_local_http"
        const val KEY_CAST_VERBOSE = "cast_verbose_logging"
        const val KEY_CAST_DIAGNOSTICS_ENABLED = "cast_diagnostics_enabled"
        const val KEY_CAST_DIAGNOSTICS_OVERLAY = "cast_diagnostics_overlay"
        internal const val KEY_READER_FOLLOW_PLAYBACK = "reader_follow_playback"
        internal const val KEY_READER_HIGHLIGHT_CURRENT_TEXT = "reader_highlight_current_text"
        internal const val KEY_READER_HIGHLIGHT_MODE = "reader_highlight_mode"
        internal const val KEY_READER_TEXT_SCALE = "reader_text_scale"

        private val PLAYBACK_UI_PREFERENCE_KEYS = setOf(
            KEY_NOTIFICATION_CONTROLS_MODE,
            KEY_MINI_PLAYER_SKIP_MODE,
            KEY_SHOW_MINI_PLAYER_SECONDARY_CONTROLS,
            KEY_SHOW_MINI_PLAYER_CAST_BUTTON,
            KEY_SHOW_MINI_PLAYER_PROGRESS_BAR,
            KEY_FEEDBACK_CONTROL_SCOPE,
            KEY_COVER_ART_DISPLAY_MODE,
        )

        private val CAST_PREFERENCE_KEYS = setOf(
            KEY_CAST_UI_MODE,
            KEY_CAST_INTRO_DISMISSED,
            KEY_CAST_USE_ARTWORK_COLOR_SCHEME,
            KEY_CAST_RESUME_SESSION,
            KEY_CAST_STOP_RECEIVER,
            KEY_CAST_PRELOAD_SECONDS,
            KEY_CAST_RECEIVER_APP_ID,
            KEY_CAST_FORCE_LOCAL_HTTP,
            KEY_CAST_VERBOSE,
            KEY_CAST_DIAGNOSTICS_ENABLED,
            KEY_CAST_DIAGNOSTICS_OVERLAY,
        )

        private val READER_PREFERENCE_KEYS = setOf(
            KEY_READER_FOLLOW_PLAYBACK,
            KEY_READER_HIGHLIGHT_CURRENT_TEXT,
            KEY_READER_HIGHLIGHT_MODE,
            KEY_READER_TEXT_SCALE,
        )

        val RESTART_REQUIRED_CAST_KEYS: Set<String> = setOf(
            KEY_CAST_UI_MODE,
            KEY_CAST_RESUME_SESSION,
            KEY_CAST_STOP_RECEIVER,
            KEY_CAST_RECEIVER_APP_ID,
            KEY_CAST_VERBOSE,
        )
    }
}
