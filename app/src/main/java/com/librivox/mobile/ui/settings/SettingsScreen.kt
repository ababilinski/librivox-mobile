package com.librivox.mobile.ui.settings

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.librivox.mobile.catalog.CatalogAutocompleteCacheSnapshot
import com.librivox.mobile.catalog.CatalogAutocompleteRefreshWorker
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.ShareLinkMode
import com.librivox.mobile.model.SourceDonationLink
import com.librivox.mobile.model.sourceDonationLinks
import com.librivox.mobile.playback.AnimationSpeed
import com.librivox.mobile.playback.AppThemeMode
import com.librivox.mobile.playback.AutoDownloadMode
import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.playback.DownloadNetworkPolicy
import com.librivox.mobile.playback.FeedbackControlScope
import com.librivox.mobile.playback.MiniPlayerSkipMode
import com.librivox.mobile.playback.NotificationControlsMode
import com.librivox.mobile.playback.ReaderHighlightMode
import com.librivox.mobile.playback.ReaderSettings
import com.librivox.mobile.playback.isAllCatalogLanguagesSelected
import com.librivox.mobile.playback.normalizeForCatalogSource
import com.librivox.mobile.playback.selectableLanguages
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SettingsReaderTextScales = listOf(0.90f, 1.00f, 1.15f, 1.30f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialOpenCatalog: Boolean = false,
    mainListState: LazyListState = rememberLazyListState(),
    onOpenDownloads: () -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val store = graph.app.playbackSettingsStore
    val context = LocalContext.current
    var showAppearanceSettings by rememberSaveable { mutableStateOf(false) }
    var showCatalogSettings by rememberSaveable { mutableStateOf(initialOpenCatalog) }
    var showDownloadSettings by rememberSaveable { mutableStateOf(false) }
    var showPlaybackSettings by rememberSaveable { mutableStateOf(false) }
    var showReadBookSettings by rememberSaveable { mutableStateOf(false) }
    var showCastSettings by rememberSaveable { mutableStateOf(false) }
    var showCastDiagnostics by rememberSaveable { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(store.themeMode) }
    var bookDetailUseArtworkColors by remember { mutableStateOf(store.bookDetailUseArtworkColorScheme) }
    var bookDetailUseCoverBackdrop by remember { mutableStateOf(store.bookDetailUseCoverBackdrop) }
    var coverArtDisplayMode by remember { mutableStateOf(store.coverArtDisplayMode) }
    var animationSpeed by remember { mutableStateOf(store.animationSpeed) }
    var preferredLanguages by remember { mutableStateOf(store.preferredLanguages) }
    var bookSource by remember { mutableStateOf(store.bookSourcePreference) }
    var autoDownloadMode by remember { mutableStateOf(store.autoDownloadMode) }
    var notificationControlsMode by remember { mutableStateOf(store.notificationControlsMode) }
    var miniPlayerSkipMode by remember { mutableStateOf(store.miniPlayerSkipMode) }
    var showMiniPlayerSecondaryControls by remember { mutableStateOf(store.showMiniPlayerSecondaryControls) }
    var showMiniPlayerCastButton by remember { mutableStateOf(store.showMiniPlayerCastButton) }
    var showMiniPlayerProgressBar by remember { mutableStateOf(store.showMiniPlayerProgressBar) }
    var feedbackControlScope by remember { mutableStateOf(store.feedbackControlScope) }
    var shareLinkMode by remember { mutableStateOf(store.shareLinkMode) }
    var networkPolicy by remember { mutableStateOf(store.downloadNetworkPolicy) }
    var downloadsOnlyModeEnabled by remember { mutableStateOf(store.downloadsOnlyModeEnabled) }
    var readerSettings by remember { mutableStateOf(store.readerSettings) }
    var automaticSearchCachingEnabled by remember { mutableStateOf(store.automaticSearchCachingEnabled) }

    if (showAppearanceSettings) {
        AppearanceSettingsScreen(
            selectedThemeMode = themeMode,
            selectedBookDetailUseArtworkColors = bookDetailUseArtworkColors,
            selectedBookDetailUseCoverBackdrop = bookDetailUseCoverBackdrop,
            selectedCoverArtDisplayMode = coverArtDisplayMode,
            selectedAnimationSpeed = animationSpeed,
            onThemeModeSelected = {
                themeMode = it
                store.saveThemeMode(it)
            },
            onBookDetailUseArtworkColorsChange = {
                bookDetailUseArtworkColors = it
                store.saveBookDetailUseArtworkColorScheme(it)
            },
            onBookDetailUseCoverBackdropChange = {
                bookDetailUseCoverBackdrop = it
                store.saveBookDetailUseCoverBackdrop(it)
            },
            onCoverArtDisplayModeSelected = {
                coverArtDisplayMode = it
                store.saveCoverArtDisplayMode(it)
            },
            onAnimationSpeedSelected = {
                animationSpeed = it
                store.saveAnimationSpeed(it)
            },
            onBack = { showAppearanceSettings = false },
        )
    } else if (showCatalogSettings) {
        CatalogSettingsScreen(
            selectedBookSource = bookSource,
            selectedLanguages = preferredLanguages,
            automaticSearchCachingEnabled = automaticSearchCachingEnabled,
            onBookSourceSelected = {
                bookSource = it
                store.saveBookSourcePreference(it)
                val normalizedLanguages = preferredLanguages.normalizeForCatalogSource(it)
                preferredLanguages = normalizedLanguages
                store.savePreferredLanguages(normalizedLanguages)
                if (automaticSearchCachingEnabled && it.enabledSources.isNotEmpty()) {
                    CatalogAutocompleteRefreshWorker.enqueueManualRefresh(context)
                }
            },
            onLanguageSelected = {
                preferredLanguages = it
                store.savePreferredLanguages(it)
            },
            onAutomaticSearchCachingChange = { enabled ->
                automaticSearchCachingEnabled = enabled
                store.saveAutomaticSearchCachingEnabled(enabled)
                CatalogAutocompleteRefreshWorker.setAutomaticRefreshEnabled(context, enabled)
            },
            onBack = { showCatalogSettings = false },
        )
    } else if (showDownloadSettings) {
        DownloadSettingsScreen(
            autoDownloadMode = autoDownloadMode,
            networkPolicy = networkPolicy,
            downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
            onAutoDownloadModeChange = {
                autoDownloadMode = it
                store.saveAutoDownloadMode(it)
            },
            onNetworkPolicyChange = {
                networkPolicy = it
                store.saveDownloadNetworkPolicy(it)
            },
            onDownloadsOnlyModeChange = {
                downloadsOnlyModeEnabled = it
                store.saveDownloadsOnlyModeEnabled(it)
            },
            onOpenDownloads = onOpenDownloads,
            onBack = { showDownloadSettings = false },
        )
    } else if (showPlaybackSettings) {
        PlaybackSettingsScreen(
            notificationControlsMode = notificationControlsMode,
            miniPlayerSkipMode = miniPlayerSkipMode,
            showMiniPlayerSecondaryControls = showMiniPlayerSecondaryControls,
            showMiniPlayerCastButton = showMiniPlayerCastButton,
            showMiniPlayerProgressBar = showMiniPlayerProgressBar,
            feedbackControlScope = feedbackControlScope,
            onNotificationControlsModeChange = {
                notificationControlsMode = it
                store.saveNotificationControlsMode(it)
            },
            onMiniPlayerSkipModeChange = {
                miniPlayerSkipMode = it
                store.saveMiniPlayerSkipMode(it)
            },
            onShowMiniPlayerSecondaryControlsChange = {
                showMiniPlayerSecondaryControls = it
                store.saveShowMiniPlayerSecondaryControls(it)
            },
            onShowMiniPlayerCastButtonChange = {
                showMiniPlayerCastButton = it
                store.saveShowMiniPlayerCastButton(it)
            },
            onShowMiniPlayerProgressBarChange = {
                showMiniPlayerProgressBar = it
                store.saveShowMiniPlayerProgressBar(it)
            },
            onFeedbackControlScopeChange = {
                feedbackControlScope = it
                store.saveFeedbackControlScope(it)
            },
            onBack = { showPlaybackSettings = false },
        )
    } else if (showReadBookSettings) {
        ReadBookSettingsScreen(
            settings = readerSettings,
            onSettingsChange = {
                readerSettings = it
                store.saveReaderSettings(it)
            },
            onBack = { showReadBookSettings = false },
        )
    } else if (showCastDiagnostics) {
        CastDiagnosticsScreen(onBack = { showCastDiagnostics = false })
    } else if (showCastSettings) {
        CastSettingsScreen(
            store = store,
            onBack = { showCastSettings = false },
            onOpenDiagnostics = { showCastDiagnostics = true },
        )
    } else {
        SettingsMainScreen(
            listState = mainListState,
            themeMode = themeMode,
            bookDetailUseArtworkColors = bookDetailUseArtworkColors,
            bookDetailUseCoverBackdrop = bookDetailUseCoverBackdrop,
            coverArtDisplayMode = coverArtDisplayMode,
            animationSpeed = animationSpeed,
            preferredLanguages = preferredLanguages,
            bookSource = bookSource,
            autoDownloadMode = autoDownloadMode,
            notificationControlsMode = notificationControlsMode,
            miniPlayerSkipMode = miniPlayerSkipMode,
            showMiniPlayerSecondaryControls = showMiniPlayerSecondaryControls,
            showMiniPlayerCastButton = showMiniPlayerCastButton,
            showMiniPlayerProgressBar = showMiniPlayerProgressBar,
            feedbackControlScope = feedbackControlScope,
            shareLinkMode = shareLinkMode,
            networkPolicy = networkPolicy,
            downloadsOnlyModeEnabled = downloadsOnlyModeEnabled,
            readerSettings = readerSettings,
            automaticSearchCachingEnabled = automaticSearchCachingEnabled,
            onShareLinkModeChange = {
                shareLinkMode = it
                store.saveShareLinkMode(it)
            },
            onOpenAppearanceSettings = { showAppearanceSettings = true },
            onOpenCatalogSettings = { showCatalogSettings = true },
            onOpenDownloadSettings = { showDownloadSettings = true },
            onOpenPlaybackSettings = { showPlaybackSettings = true },
            onOpenReadBookSettings = { showReadBookSettings = true },
            onOpenCastSettings = { showCastSettings = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    listState: LazyListState,
    themeMode: AppThemeMode,
    bookDetailUseArtworkColors: Boolean,
    bookDetailUseCoverBackdrop: Boolean,
    coverArtDisplayMode: CoverArtDisplayMode,
    animationSpeed: AnimationSpeed,
    preferredLanguages: Set<BookLanguagePreference>,
    bookSource: BookSourcePreference,
    autoDownloadMode: AutoDownloadMode,
    notificationControlsMode: NotificationControlsMode,
    miniPlayerSkipMode: MiniPlayerSkipMode,
    showMiniPlayerSecondaryControls: Boolean,
    showMiniPlayerCastButton: Boolean,
    showMiniPlayerProgressBar: Boolean,
    feedbackControlScope: FeedbackControlScope,
    shareLinkMode: ShareLinkMode,
    networkPolicy: DownloadNetworkPolicy,
    downloadsOnlyModeEnabled: Boolean,
    readerSettings: ReaderSettings,
    automaticSearchCachingEnabled: Boolean,
    onShareLinkModeChange: (ShareLinkMode) -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenCatalogSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    onOpenReadBookSettings: () -> Unit,
    onOpenCastSettings: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Appearance") {
                    SettingsNavigationRow(
                        headline = "Appearance",
                        supporting = appearanceSummary(
                            themeMode = themeMode,
                            bookDetailUseArtworkColors = bookDetailUseArtworkColors,
                            bookDetailUseCoverBackdrop = bookDetailUseCoverBackdrop,
                            coverArtDisplayMode = coverArtDisplayMode,
                            animationSpeed = animationSpeed,
                        ),
                        icon = Icons.Filled.DarkMode,
                        onClick = onOpenAppearanceSettings,
                    )
                }
            }

            item {
                SettingsSection(title = "Catalog") {
                    SettingsNavigationRow(
                        headline = "Catalog",
                        supporting = "${catalogSummary(bookSource, preferredLanguages)} • Search cache ${if (automaticSearchCachingEnabled) "on" else "off"}",
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        onClick = onOpenCatalogSettings,
                    )
                }
            }

            item {
                SettingsSection(title = "Downloads") {
                    SettingsNavigationRow(
                        headline = "Downloads",
                        supporting = downloadSettingsSummary(autoDownloadMode, networkPolicy, downloadsOnlyModeEnabled),
                        icon = Icons.Filled.Download,
                        onClick = onOpenDownloadSettings,
                    )
                }
            }

            item {
                SettingsSection(title = "Playback") {
                    SettingsNavigationRow(
                        headline = "Playback settings",
                        supporting = playbackSettingsSummary(
                            notificationControlsMode = notificationControlsMode,
                            miniPlayerSkipMode = miniPlayerSkipMode,
                            showSecondaryControls = showMiniPlayerSecondaryControls,
                            showCast = showMiniPlayerCastButton,
                            showProgress = showMiniPlayerProgressBar,
                            feedbackScope = feedbackControlScope,
                        ),
                        icon = Icons.Filled.RecordVoiceOver,
                        onClick = onOpenPlaybackSettings,
                    )
                    SectionDivider()
                    SettingsNavigationRow(
                        headline = "Read Book",
                        supporting = readBookSettingsSummary(readerSettings),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        onClick = onOpenReadBookSettings,
                    )
                }
            }

            item {
                SettingsSection(title = "Cast") {
                    SettingsNavigationRow(
                        headline = "Cast",
                        supporting = "Picker UI, queueing, diagnostics",
                        icon = Icons.Filled.Cast,
                        onClick = onOpenCastSettings,
                    )
                }
            }

            item {
                SettingsSection(title = "Sharing") {
                    ShareLinkMode.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = shareLinkMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Share,
                            onClick = { onShareLinkModeChange(option) },
                        )
                        if (index != ShareLinkMode.entries.lastIndex) SectionDivider()
                    }
                }
            }

            item {
                SettingsSection(title = "Support audiobook sources") {
                    sourceDonationLinks.forEachIndexed { index, link ->
                        DonationRow(
                            link = link,
                            onClick = { openExternalUrl(context, link.url) },
                        )
                        if (index != sourceDonationLinks.lastIndex) SectionDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSettingsScreen(
    autoDownloadMode: AutoDownloadMode,
    networkPolicy: DownloadNetworkPolicy,
    downloadsOnlyModeEnabled: Boolean,
    onAutoDownloadModeChange: (AutoDownloadMode) -> Unit,
    onNetworkPolicyChange: (DownloadNetworkPolicy) -> Unit,
    onDownloadsOnlyModeChange: (Boolean) -> Unit,
    onOpenDownloads: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val networkChoices = remember {
        DownloadNetworkPolicy.entries.filterNot { it == DownloadNetworkPolicy.Manual }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Offline mode") {
                    SettingsSwitchRow(
                        headline = "Downloads only mode",
                        supporting = if (downloadsOnlyModeEnabled) {
                            "Only show books saved on this device until you leave offline mode."
                        } else {
                            "Enter Offline Mode when you want the app to behave as downloaded-only."
                        },
                        icon = Icons.Filled.Download,
                        checked = downloadsOnlyModeEnabled,
                        onCheckedChange = onDownloadsOnlyModeChange,
                    )
                }
            }
            item {
                SettingsSection(title = "Auto-download while listening") {
                    AutoDownloadMode.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = autoDownloadMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Download,
                            onClick = { onAutoDownloadModeChange(option) },
                        )
                        if (index != AutoDownloadMode.entries.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Manual downloads") {
                    networkChoices.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = networkPolicy == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Download,
                            onClick = { onNetworkPolicyChange(option) },
                        )
                        if (index != networkChoices.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Download library") {
                    SettingsNavigationRow(
                        headline = "Download library",
                        supporting = "Review queued downloads, saved audio, and storage.",
                        icon = Icons.Filled.Download,
                        onClick = onOpenDownloads,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsScreen(
    notificationControlsMode: NotificationControlsMode,
    miniPlayerSkipMode: MiniPlayerSkipMode,
    showMiniPlayerSecondaryControls: Boolean,
    showMiniPlayerCastButton: Boolean,
    showMiniPlayerProgressBar: Boolean,
    feedbackControlScope: FeedbackControlScope,
    onNotificationControlsModeChange: (NotificationControlsMode) -> Unit,
    onMiniPlayerSkipModeChange: (MiniPlayerSkipMode) -> Unit,
    onShowMiniPlayerSecondaryControlsChange: (Boolean) -> Unit,
    onShowMiniPlayerCastButtonChange: (Boolean) -> Unit,
    onShowMiniPlayerProgressBarChange: (Boolean) -> Unit,
    onFeedbackControlScopeChange: (FeedbackControlScope) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val secondaryControlsHidden = !showMiniPlayerSecondaryControls
    val secondaryControlsUseChapters =
        showMiniPlayerSecondaryControls && miniPlayerSkipMode == MiniPlayerSkipMode.Chapter
    val secondaryControlsUseTenSeconds =
        showMiniPlayerSecondaryControls && miniPlayerSkipMode == MiniPlayerSkipMode.TenSeconds

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Playback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Mini player controls") {
                    ChoiceRow(
                        selected = secondaryControlsHidden,
                        headline = "Hide secondary controls",
                        supporting = "Only show play/pause, plus Cast if enabled.",
                        icon = Icons.Filled.RecordVoiceOver,
                        onClick = { onShowMiniPlayerSecondaryControlsChange(false) },
                    )
                    SectionDivider()
                    ChoiceRow(
                        selected = secondaryControlsUseChapters,
                        headline = "Previous and next",
                        supporting = "Show chapter previous and next buttons beside play/pause.",
                        icon = Icons.Filled.RecordVoiceOver,
                        onClick = {
                            onShowMiniPlayerSecondaryControlsChange(true)
                            onMiniPlayerSkipModeChange(MiniPlayerSkipMode.Chapter)
                        },
                    )
                    SectionDivider()
                    ChoiceRow(
                        selected = secondaryControlsUseTenSeconds,
                        headline = "10-second jumps",
                        supporting = "Show 10-second back and forward buttons beside play/pause.",
                        icon = Icons.Filled.Speed,
                        onClick = {
                            onShowMiniPlayerSecondaryControlsChange(true)
                            onMiniPlayerSkipModeChange(MiniPlayerSkipMode.TenSeconds)
                        },
                    )
                }
            }
            item {
                SettingsSection(title = "Mini player display") {
                    SettingsSwitchRow(
                        headline = "Cast button",
                        supporting = if (showMiniPlayerCastButton) {
                            "Show Cast in the mini player when audio can be cast."
                        } else {
                            "Keep Cast available from Now Playing and menus."
                        },
                        icon = Icons.Filled.Cast,
                        checked = showMiniPlayerCastButton,
                        onCheckedChange = onShowMiniPlayerCastButtonChange,
                    )
                    SectionDivider()
                    SettingsSwitchRow(
                        headline = "Progress bar",
                        supporting = if (showMiniPlayerProgressBar) {
                            "Show the thin scrub bar under the mini player."
                        } else {
                            "Hide mini-player progress for a simpler bar."
                        },
                        icon = Icons.Filled.Speed,
                        checked = showMiniPlayerProgressBar,
                        onCheckedChange = onShowMiniPlayerProgressBarChange,
                    )
                }
            }
            item {
                SettingsSection(title = "Like and dislike") {
                    FeedbackControlScope.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = feedbackControlScope == option,
                            headline = option.label,
                            supporting = option.description,
                            onClick = { onFeedbackControlScopeChange(option) },
                        )
                        if (index != FeedbackControlScope.entries.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Notification controls") {
                    NotificationControlsPicker(
                        selected = notificationControlsMode,
                        onSelected = onNotificationControlsModeChange,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadBookSettingsScreen(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Read Book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Read along") {
                    SettingsSwitchRow(
                        headline = "Source sync",
                        supporting = "Keep the spoken text in view while audio plays.",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        checked = settings.followPlayback,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(followPlayback = checked))
                        },
                    )
                    SectionDivider()
                    SettingsSwitchRow(
                        headline = "Highlight current text",
                        supporting = "Show the source text currently being read.",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        checked = settings.highlightCurrentText,
                        onCheckedChange = { checked ->
                            onSettingsChange(settings.copy(highlightCurrentText = checked))
                        },
                    )
                }
            }
            item {
                SettingsSection(title = "Highlight range") {
                    ReaderHighlightMode.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = settings.highlightMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            onClick = { onSettingsChange(settings.copy(highlightMode = option)) },
                        )
                        if (index != ReaderHighlightMode.entries.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Text size") {
                    SettingsReaderTextScales.forEachIndexed { index, scale ->
                        ChoiceRow(
                            selected = settings.textScale.isSameReaderTextScale(scale),
                            headline = readerTextScaleLabel(scale),
                            supporting = readerTextScaleDescription(scale),
                            icon = Icons.Filled.FormatSize,
                            onClick = { onSettingsChange(settings.copy(textScale = scale)) },
                        )
                        if (index != SettingsReaderTextScales.lastIndex) SectionDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettingsScreen(
    selectedThemeMode: AppThemeMode,
    selectedBookDetailUseArtworkColors: Boolean,
    selectedBookDetailUseCoverBackdrop: Boolean,
    selectedCoverArtDisplayMode: CoverArtDisplayMode,
    selectedAnimationSpeed: AnimationSpeed,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onBookDetailUseArtworkColorsChange: (Boolean) -> Unit,
    onBookDetailUseCoverBackdropChange: (Boolean) -> Unit,
    onCoverArtDisplayModeSelected: (CoverArtDisplayMode) -> Unit,
    onAnimationSpeedSelected: (AnimationSpeed) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(title = "Theme") {
                    AppThemeMode.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = selectedThemeMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.DarkMode,
                            onClick = { onThemeModeSelected(option) },
                        )
                        if (index != AppThemeMode.entries.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Artwork colors") {
                    SettingsSwitchRow(
                        headline = "Book detail cover colors",
                        supporting = if (selectedBookDetailUseArtworkColors) {
                            "Book detail pages use colors from their cover art. Player surfaces always use cover colors."
                        } else {
                            "Only the mini player, Now Playing, and playback panels use cover colors."
                        },
                        icon = Icons.Filled.Palette,
                        checked = selectedBookDetailUseArtworkColors,
                        onCheckedChange = onBookDetailUseArtworkColorsChange,
                    )
                    SectionDivider()
                    SettingsSwitchRow(
                        headline = "Cover backdrop fade",
                        supporting = if (selectedBookDetailUseCoverBackdrop) {
                            "Stretch and blur the cover behind book details, then fade into the page."
                        } else {
                            "Use the simpler tinted book detail background."
                        },
                        icon = Icons.Filled.Palette,
                        checked = selectedBookDetailUseCoverBackdrop,
                        onCheckedChange = onBookDetailUseCoverBackdropChange,
                    )
                }
            }
            item {
                SettingsSection(title = "Cover art") {
                    CoverArtDisplayMode.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = selectedCoverArtDisplayMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Palette,
                            onClick = { onCoverArtDisplayModeSelected(option) },
                        )
                        if (index != CoverArtDisplayMode.entries.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Motion") {
                    AnimationSpeed.entries.forEachIndexed { index, option ->
                        ChoiceRow(
                            selected = selectedAnimationSpeed == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Speed,
                            onClick = { onAnimationSpeedSelected(option) },
                        )
                        if (index != AnimationSpeed.entries.lastIndex) SectionDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSettingsScreen(
    selectedBookSource: BookSourcePreference,
    selectedLanguages: Set<BookLanguagePreference>,
    automaticSearchCachingEnabled: Boolean,
    onBookSourceSelected: (BookSourcePreference) -> Unit,
    onLanguageSelected: (Set<BookLanguagePreference>) -> Unit,
    onAutomaticSearchCachingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val graph = LocalAppGraph.current
    val tonal = LocalTonalSurfaces.current
    val context = LocalContext.current
    val cacheRepository = graph.app.catalogAutocompleteRepository
    val cacheSnapshots = remember(cacheRepository) { cacheRepository.cacheSnapshots() }
    val cacheSnapshot by cacheSnapshots.collectAsState(initial = CatalogAutocompleteCacheSnapshot())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sourceInfo by remember { mutableStateOf<CatalogSourceInfo?>(null) }
    var cacheSourceToDelete by rememberSaveable { mutableStateOf<BookSource?>(null) }
    var confirmNoSources by remember { mutableStateOf(false) }
    var languageAction by remember { mutableStateOf<BookLanguagePreference?>(null) }
    val normalizedLanguages = selectedLanguages.normalizeForCatalogSource(selectedBookSource)
    val cacheBuildInProgress = cacheSnapshot.lastStartedAtMillis > cacheSnapshot.lastCompletedAtMillis &&
        cacheSnapshot.failureCount == 0 &&
        automaticSearchCachingEnabled

    fun selectLanguages(next: Set<BookLanguagePreference>) {
        onLanguageSelected(next.normalizeForCatalogSource(selectedBookSource))
    }

    LaunchedEffect(cacheSnapshot.lastCompletedAtMillis) {
        if (cacheSnapshot.lastCompletedAtMillis > 0L) {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    sourceInfo?.let { info ->
        CatalogSourceInfoSheet(
            info = info,
            onDismiss = { sourceInfo = null },
            onOpenWebsite = { url -> openExternalUrl(context, url) },
        )
    }
    languageAction?.let { language ->
        LanguageOnlyActionSheet(
            language = language,
            onDismiss = { languageAction = null },
            onSelectOnly = {
                val next = if (language == BookLanguagePreference.All) {
                    setOf(BookLanguagePreference.All)
                } else {
                    setOf(language)
                }
                selectLanguages(next)
                languageAction = null
            },
        )
    }
    if (confirmNoSources) {
        AlertDialog(
            onDismissRequest = { confirmNoSources = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                )
            },
            title = { Text("Turn off all catalog sources?") },
            text = {
                Text("Discover will pause and show a prompt until you choose at least one audiobook source.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmNoSources = false
                        onBookSourceSelected(BookSourcePreference.None)
                    },
                ) {
                    Text("Turn off sources")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNoSources = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    cacheSourceToDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { cacheSourceToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Delete ${source.cacheLabel()} cache?") },
            text = {
                Text(
                    "This clears only ${source.cacheLabel()} metadata used for faster catalog search. Audio downloads, library items, bookmarks, and progress stay saved.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        cacheSourceToDelete = null
                        coroutineScope.launch {
                            cacheRepository.clearCache(source)
                            snackbarHostState.showSnackbar("${source.cacheLabel()} search cache deleted.")
                        }
                    },
                ) {
                    Text("Delete cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { cacheSourceToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Catalog") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tonal.screenBackground,
                    scrolledContainerColor = tonal.screenBackground,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection(
                    title = "Sources",
                    titleAction = {
                        IconButton(onClick = { sourceInfo = catalogSourcesOverviewInfo() }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "About catalog sources",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                ) {
                    PrimaryCatalogSources.forEach { option ->
                        val selectedSources = selectedBookSource.enabledSources
                        SourceChoiceRow(
                            selected = option in selectedSources,
                            enabled = true,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                            onClick = {
                                val nextSources = if (option in selectedSources) {
                                    selectedSources - option
                                } else {
                                    selectedSources + option
                                }
                                if (nextSources.isEmpty()) {
                                    confirmNoSources = true
                                } else {
                                    onBookSourceSelected(BookSourcePreference.fromEnabledSources(nextSources))
                                }
                            },
                            onLongClick = { sourceInfo = option.catalogSourceInfo() },
                        )
                    }
                    SectionDivider()
                    SettingsSubsectionHeader("Other public audiobook sources")
                    OtherPublicCatalogSources.forEachIndexed { index, option ->
                        val selectedSources = selectedBookSource.enabledSources
                        SourceChoiceRow(
                            selected = option in selectedSources,
                            enabled = true,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.AutoMirrored.Filled.LibraryBooks,
                            onClick = {
                                val nextSources = if (option in selectedSources) {
                                    selectedSources - option
                                } else {
                                    selectedSources + option
                                }
                                if (nextSources.isEmpty()) {
                                    confirmNoSources = true
                                } else {
                                    onBookSourceSelected(BookSourcePreference.fromEnabledSources(nextSources))
                                }
                            },
                            onLongClick = { sourceInfo = option.catalogSourceInfo() },
                        )
                        if (index != OtherPublicCatalogSources.lastIndex) SectionDivider()
                    }
                }
            }
            if (selectedBookSource.enabledSources.isEmpty()) {
                item {
                    CatalogWarningBanner(
                        title = "No catalog sources selected",
                        message = "Discover is paused until you choose at least one audiobook source.",
                    )
                }
            }
            item {
                SettingsSection(title = "Language") {
                    val languageOptions = listOf(BookLanguagePreference.All) + selectedBookSource.selectableLanguages()
                    val allSelected = selectedLanguages.isAllCatalogLanguagesSelected(selectedBookSource)
                    languageOptions.forEachIndexed { index, option ->
                        val selected = if (option == BookLanguagePreference.All) {
                            allSelected
                        } else {
                            option in normalizedLanguages
                        }
                        LanguageChoiceRow(
                            selected = selected,
                            headline = option.label,
                            supporting = languageChoiceDescription(
                                language = option,
                            ),
                            icon = Icons.Filled.Language,
                            toggleEnabled = true,
                            onClick = {
                                when {
                                    option == BookLanguagePreference.All -> selectLanguages(setOf(BookLanguagePreference.All))
                                    option in normalizedLanguages -> {
                                        val next = normalizedLanguages - option
                                        if (next.isNotEmpty()) {
                                            selectLanguages(next)
                                        }
                                    }
                                    else -> selectLanguages(normalizedLanguages + option)
                                }
                            },
                            onLongClick = {
                                languageAction = option
                            },
                        )
                        if (index != languageOptions.lastIndex) SectionDivider()
                    }
                }
            }
            item {
                SettingsSection(title = "Search cache") {
                    SettingsSwitchRow(
                        headline = "Automatic search caching",
                        supporting = if (automaticSearchCachingEnabled) {
                            "Build and reuse local metadata for faster suggestions, search, and featured shelves."
                        } else {
                            "Skip local metadata cache reads and writes. Searches may load slower, but book selection and playback avoid cache work."
                        },
                        icon = Icons.Filled.Storage,
                        checked = automaticSearchCachingEnabled,
                        onCheckedChange = onAutomaticSearchCachingChange,
                    )
                    SectionDivider()
                    CatalogCacheStatusRow(
                        snapshot = cacheSnapshot,
                        sizeLabel = Formatter.formatFileSize(context, cacheSnapshot.sizeBytes),
                        automaticCachingEnabled = automaticSearchCachingEnabled,
                    )
                    SectionDivider()
                    SettingsActionRow(
                        headline = if (cacheBuildInProgress) {
                            "Building selected source cache"
                        } else {
                            "Build selected source cache"
                        },
                        supporting = if (automaticSearchCachingEnabled) {
                            searchCacheBuildDescription(selectedBookSource, selectedLanguages)
                        } else {
                            "Turn on automatic search caching to build the local metadata database."
                        },
                        icon = Icons.Filled.Refresh,
                        enabled = automaticSearchCachingEnabled && selectedBookSource.enabledSources.isNotEmpty(),
                        showProgress = cacheBuildInProgress,
                        onClick = {
                            CatalogAutocompleteRefreshWorker.enqueueManualRefresh(context)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Building search metadata for selected catalog sources.",
                                )
                            }
                        },
                    )
                    SectionDivider()
                    BookSourcePreference.SelectableSources.forEachIndexed { index, option ->
                        val source = option.bookSource()
                        CatalogSourceCacheRow(
                            source = source,
                            recordCount = cacheSnapshot.sourceCounts[source] ?: 0,
                            sizeLabel = Formatter.formatFileSize(
                                context,
                                cacheSnapshot.cacheSizeForSource(source),
                            ),
                            onDelete = { cacheSourceToDelete = source },
                        )
                        if (index != BookSourcePreference.SelectableSources.lastIndex) SectionDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val tint = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        },
        headlineContent = {
            Text(
                text = headline,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = if (enabled) {
                    { _: Boolean -> onClick() }
                } else {
                    null
                },
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun SettingsSubsectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LanguageChoiceRow(
    selected: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    toggleEnabled: Boolean,
    visuallyDisabled: Boolean = false,
    emphasizeSupporting: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
    val tint = when {
        visuallyDisabled -> disabledColor
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        },
        headlineContent = {
            Text(
                text = headline,
                color = if (visuallyDisabled) disabledColor else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (visuallyDisabled) disabledColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (emphasizeSupporting) FontWeight.Bold else null,
            )
        },
        trailingContent = {
            Checkbox(
                checked = selected,
                onCheckedChange = if (toggleEnabled) {
                    { _: Boolean -> onClick() }
                } else {
                    null
                },
                enabled = toggleEnabled,
            )
        },
    )
}

@Composable
private fun CatalogWarningBanner(
    title: String,
    message: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageOnlyActionSheet(
    language: BookLanguagePreference,
    onDismiss: () -> Unit,
    onSelectOnly: () -> Unit,
) {
    val title = if (language == BookLanguagePreference.All) {
        "All languages"
    } else {
        language.label
    }
    val actionLabel = if (language == BookLanguagePreference.All) {
        "Select all languages"
    } else {
        "Only $title"
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            ListItem(
                modifier = Modifier.clickable { onSelectOnly() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = { Text(actionLabel) },
                supportingContent = {
                    Text(
                        text = "Replace the current language selection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSourceInfoSheet(
    info: CatalogSourceInfo,
    onDismiss: () -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
            Text(
                text = info.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = info.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            info.bestFor?.let { bestFor ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Best for",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = bestFor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            info.websiteUrl?.let { url ->
                ListItem(
                    modifier = Modifier.clickable { onOpenWebsite(url) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = info.websiteLabel ?: "Open source project",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "Open the source project page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    titleAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title, action = titleAction)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = tonal.listItem,
            tonalElevation = 1.dp,
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun CatalogCacheStatusRow(
    snapshot: CatalogAutocompleteCacheSnapshot,
    sizeLabel: String,
    automaticCachingEnabled: Boolean,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = "Search metadata cache",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${snapshot.bookCount} indexed metadata records • $sizeLabel total",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (automaticCachingEnabled) {
                        "Speeds up source search. Audio files and book downloads are stored separately."
                    } else {
                        "Stored metadata is kept until deleted, but it is not used while automatic caching is off."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = catalogCacheRefreshSummary(snapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SettingsSwitchRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        headlineContent = {
            Text(
                text = headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun CatalogSourceCacheRow(
    source: BookSource,
    recordCount: Int,
    sizeLabel: String,
    onDelete: () -> Unit,
) {
    val hasCache = recordCount > 0
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                tint = if (hasCache) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        headlineContent = {
            Text(
                text = source.cacheLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = if (hasCache) {
                    "$recordCount records • about $sizeLabel"
                } else {
                    "No cached metadata"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            TextButton(
                enabled = hasCache,
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun SettingsActionRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    enabled: Boolean = true,
    tintError: Boolean = false,
    showProgress: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        tintError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
        },
        headlineContent = {
            Text(
                text = headline,
                color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (showProgress) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun NotificationControlsPicker(
    selected: NotificationControlsMode,
    onSelected: (NotificationControlsMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Notification actions",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = selected.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            NotificationControlsMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = NotificationControlsMode.entries.size,
                    ),
                    label = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val selectedTint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = icon?.let { imageVector ->
            {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = selectedTint,
                )
            }
        },
        headlineContent = {
            Text(
                text = headline,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
    )
}

@Composable
private fun DonationRow(
    link: SourceDonationLink,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(
                text = link.actionLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = link.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun SettingsNavigationRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(
                text = headline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun languageSummary(
    languages: Set<BookLanguagePreference>,
    source: BookSourcePreference,
): String {
    if (languages.isAllCatalogLanguagesSelected(source)) return "All languages"
    val labels = languages.normalizeForCatalogSource(source)
        .map { it.label }
    return when {
        labels.isEmpty() -> "No languages"
        labels.size <= 3 -> labels.joinToString(", ")
        else -> "${labels.take(2).joinToString(", ")} + ${labels.size - 2} more"
    }
}

private fun languageChoiceDescription(
    language: BookLanguagePreference,
): String =
    when {
        language == BookLanguagePreference.All -> "Search and browse every language available for selected sources."
        else -> "Include ${language.label} titles in Discover and search."
    }

private fun catalogSummary(
    source: BookSourcePreference,
    languages: Set<BookLanguagePreference>,
): String =
    "${source.label} • ${languageSummary(languages, source)}"

private fun searchCacheBuildDescription(
    source: BookSourcePreference,
    languages: Set<BookLanguagePreference>,
): String =
    if (source.enabledSources.isEmpty()) {
        "Choose at least one source before building the search metadata cache."
    } else {
        "Index ${catalogSummary(source, languages)} by source for title, chapter, author, and reader search."
    }

private fun catalogCacheRefreshSummary(snapshot: CatalogAutocompleteCacheSnapshot): String =
    when {
        snapshot.lastStartedAtMillis > snapshot.lastCompletedAtMillis && snapshot.failureCount == 0 ->
            "Building now • started ${formatCacheDate(snapshot.lastStartedAtMillis)}"
        snapshot.failureCount > 0 && snapshot.lastCompletedAtMillis == 0L ->
            "Last build did not complete • ${snapshot.failureCount} failed attempts"
        snapshot.lastCompletedAtMillis > 0L ->
            "Last built ${formatCacheDate(snapshot.lastCompletedAtMillis)}"
        else -> "Not built yet"
    }

private fun CatalogAutocompleteCacheSnapshot.cacheSizeForSource(source: BookSource): Long =
    if ((sourceCounts[source] ?: 0) > 0) {
        sourceSizeBytes[source] ?: 0L
    } else {
        0L
    }

private fun CatalogSourcePreference.bookSource(): BookSource =
    when (this) {
        CatalogSourcePreference.LibriVox -> BookSource.LibriVox
        CatalogSourcePreference.Lit2Go -> BookSource.Lit2Go
        CatalogSourcePreference.WolneLektury -> BookSource.WolneLektury
        CatalogSourcePreference.Gutendex -> BookSource.Gutendex
    }

private fun BookSource.cacheLabel(): String =
    when (this) {
        BookSource.LibriVox -> "LibriVox"
        BookSource.Lit2Go -> "Lit2Go"
        BookSource.WolneLektury -> "Wolne Lektury"
        BookSource.Gutendex -> "Gutendex"
        BookSource.LocalAsset -> "Local assets"
        BookSource.CustomLocal -> "Custom local"
    }

private fun formatCacheDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

private data class CatalogSourceInfo(
    val title: String,
    val summary: String,
    val details: String,
    val bestFor: String?,
    val websiteLabel: String? = null,
    val websiteUrl: String? = null,
)

private fun catalogSourcesOverviewInfo(): CatalogSourceInfo =
    CatalogSourceInfo(
        title = "Catalog sources",
        summary = "Choose the libraries Discover should search and browse.",
        details = "New installs start with LibriVox only. You can opt into other public audiobook sources from the Catalog screen whenever you want broader discovery.",
        bestFor = "Use LibriVox for broad volunteer recordings. Lit2Go adds English educational classics, Gutendex adds Project Gutenberg audiobook metadata, and Wolne Lektury is Polish-only.",
    )

private fun CatalogSourcePreference.catalogSourceInfo(): CatalogSourceInfo =
    when (this) {
        CatalogSourcePreference.Lit2Go -> CatalogSourceInfo(
            title = "Lit2Go",
            summary = "A free online collection of stories and poems in MP3 audiobook format.",
            details = "Lit2Go is published by the Florida Center for Instructional Technology at the University of South Florida. Its passages include classroom-friendly metadata such as abstracts, citations, playing time, word count, and downloadable read-along PDFs.",
            bestFor = "Good for English classics, short works, and chapter-level educational listening.",
            websiteLabel = "Open Lit2Go",
            websiteUrl = "https://etc.usf.edu/lit2go/",
        )
        CatalogSourcePreference.LibriVox -> CatalogSourceInfo(
            title = "LibriVox",
            summary = "Volunteer-recorded audiobooks of public-domain books.",
            details = "LibriVox volunteers record chapters of public-domain works and release the audio back to the web for free. It is broad and multilingual, with reader information available for many titles.",
            bestFor = "Good for a large selection of classic audiobooks and volunteer narrator recordings.",
            websiteLabel = "Open LibriVox",
            websiteUrl = "https://librivox.org/pages/about-librivox/",
        )
        CatalogSourcePreference.Gutendex -> CatalogSourceInfo(
            title = "Gutendex",
            summary = "A JSON web API for Project Gutenberg book metadata.",
            details = "Gutendex exposes Project Gutenberg catalog records in an app-friendly API. The app filters it to audiobook records, expands Gutenberg audio index pages into chapters when possible, and uses AudiobookCovers.com as a best-effort cover fallback when Gutenberg has no image.",
            bestFor = "Good for Project Gutenberg public-domain audio records and metadata from the Gutenberg catalog.",
            websiteLabel = "Open Gutendex",
            websiteUrl = "https://gutendex.com/",
        )
        CatalogSourcePreference.WolneLektury -> CatalogSourceInfo(
            title = "Wolne Lektury",
            summary = "A free Polish online library with ebooks, audiobooks, and literary metadata.",
            details = "Wolne Lektury only supports Polish in this app. It publishes Polish public-domain literature and open-license classics with metadata such as authors, genres, kinds, and literary epochs.",
            bestFor = "Good for Polish literature, school readings, and Polish audiobook metadata.",
            websiteLabel = "Open Wolne Lektury",
            websiteUrl = "https://fundacja.wolnelektury.pl/about-us/",
        )
    }

private fun downloadSettingsSummary(
    autoDownloadMode: AutoDownloadMode,
    networkPolicy: DownloadNetworkPolicy,
    downloadsOnlyModeEnabled: Boolean,
): String =
    if (downloadsOnlyModeEnabled) {
        "Downloads only mode on"
    } else {
        "${autoDownloadMode.label} auto • ${networkPolicy.label} audio"
    }

private fun playbackSettingsSummary(
    notificationControlsMode: NotificationControlsMode,
    miniPlayerSkipMode: MiniPlayerSkipMode,
    showSecondaryControls: Boolean,
    showCast: Boolean,
    showProgress: Boolean,
    feedbackScope: FeedbackControlScope,
): String {
    val miniSummary = when {
        !showSecondaryControls -> "play/pause mini"
        miniPlayerSkipMode == MiniPlayerSkipMode.Chapter -> "previous/next mini"
        else -> "10-second mini"
    }
    return listOf(
        miniSummary,
        if (showCast) "Cast shown" else "Cast hidden",
        if (showProgress) "progress shown" else "progress hidden",
        "${feedbackScope.label} feedback",
        "${notificationControlsMode.label} notification",
    ).joinToString(" • ")
}

private fun readBookSettingsSummary(settings: ReaderSettings): String =
    listOf(
        if (settings.followPlayback) "Source sync on" else "Source sync off",
        if (settings.highlightCurrentText) settings.highlightMode.label else "Highlight off",
        "${(ReaderSettings.clampTextScale(settings.textScale) * 100).roundToInt()}% text",
    ).joinToString(" • ")

private fun Float.isSameReaderTextScale(other: Float): Boolean =
    kotlin.math.abs(this - other) < 0.01f

private fun readerTextScaleLabel(scale: Float): String =
    "${(scale * 100).roundToInt()}%"

private fun readerTextScaleDescription(scale: Float): String =
    when {
        scale < 1f -> "Compact book text."
        scale.isSameReaderTextScale(1f) -> "Default reader size."
        scale < 1.2f -> "Larger book text."
        else -> "Largest book text."
    }

private val PrimaryCatalogSources = BookSourcePreference.PrimarySelectableSources

private val OtherPublicCatalogSources = BookSourcePreference.OtherSelectableSources

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

private fun appearanceSummary(
    themeMode: AppThemeMode,
    bookDetailUseArtworkColors: Boolean,
    bookDetailUseCoverBackdrop: Boolean,
    coverArtDisplayMode: CoverArtDisplayMode,
    animationSpeed: AnimationSpeed,
): String =
    listOf(
        "${themeMode.label} theme",
        coverArtDisplayMode.label,
        if (bookDetailUseCoverBackdrop) "cover backdrop fade" else "simple detail background",
        if (bookDetailUseArtworkColors) "book details use cover colors" else "player cover colors only",
        "${animationSpeed.label} motion",
    ).joinToString(" • ")

@Composable
private fun SectionTitle(
    text: String,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
        action?.invoke()
    }
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}
