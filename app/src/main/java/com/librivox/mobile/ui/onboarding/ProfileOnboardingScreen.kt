package com.librivox.mobile.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.librivox.mobile.R
import com.librivox.mobile.catalog.CatalogAutocompleteRefreshWorker
import com.librivox.mobile.model.AvatarSeed
import com.librivox.mobile.playback.AutoDownloadMode
import com.librivox.mobile.playback.BookLanguagePreference
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.DownloadNetworkPolicy
import com.librivox.mobile.playback.normalizeForCatalogSource
import com.librivox.mobile.playback.selectableLanguages
import com.librivox.mobile.ui.components.rememberHaptics
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OnboardingBottomScrollThreshold = 8
private const val WelcomeCarouselIdleDelayMillis = 1_000L
private const val WelcomeCarouselAutoScrollPauseMillis = 4_000L
private const val WelcomeCarouselLoopCycles = 1_000
private const val WelcomeBackdropWidthFraction = 0.74f
private const val SetupBackdropWidthFraction = 0.64f

data class OnboardingPermissionSelection(
    val notifications: Boolean,
    val localNetworkCasting: Boolean,
)

private enum class RequiredOnboardingPermission(
    val title: String,
    val message: String,
) {
    Internet(
        title = "Internet access is required",
        message = "LibriVox Mobile needs internet access to search selected sources, stream chapters, and load book covers.",
    ),
    AppStorage(
        title = "App storage is required",
        message = "LibriVox Mobile keeps your library, settings, bookmarks, and saved chapters inside its own app storage.",
    ),
}

private enum class OnboardingStep(
    val title: String,
    val description: String,
) {
    Welcome(
        title = "Welcome to LibriVox Mobile",
        description = "Listen to free public-domain audiobook recordings and save chapters for offline listening.",
    ),
    Permissions(
        title = "Choose permissions",
        description = "LibriVox Mobile can work without optional permissions. Pick what you want the app to ask for now.",
    ),
    Sources(
        title = "Choose book sources",
        description = "Select where Discover should look for books. You can change this later in Settings.",
    ),
    Languages(
        title = "Choose audiobook languages",
        description = "Select the languages you want to browse. You can change this later in Settings.",
    ),
    Downloads(
        title = "Downloads and cache",
        description = "Choose how chapters are saved for offline listening and whether browsing should feel faster.",
    ),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileOnboardingScreen(
    onRequestPermissions: (OnboardingPermissionSelection) -> Unit = {},
) {
    val graph = LocalAppGraph.current
    val store = graph.app.playbackSettingsStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val steps = remember { OnboardingStep.entries }
    var stepIndex by remember { mutableStateOf(0) }
    val step = steps[stepIndex]
    val scrollState = rememberScrollState()
    val hasMoreContent by remember {
        derivedStateOf {
            scrollState.maxValue > 0 &&
                scrollState.value < scrollState.maxValue - OnboardingBottomScrollThreshold
        }
    }

    var permissionSelection by remember {
        mutableStateOf(
            OnboardingPermissionSelection(
                notifications = true,
                localNetworkCasting = true,
            ),
        )
    }
    var selectedSources by remember {
        mutableStateOf(normalizeOnboardingSources(store.bookSourcePreference.enabledSources))
    }
    var selectedLanguages by remember {
        mutableStateOf(normalizeOnboardingLanguages(store.preferredLanguages, selectedSources))
    }
    var automaticSearchCachingEnabled by remember {
        mutableStateOf(store.automaticSearchCachingEnabled)
    }
    var autoDownloadMode by remember { mutableStateOf(store.autoDownloadMode) }
    var networkPolicy by remember { mutableStateOf(store.downloadNetworkPolicy) }
    var inactiveLanguage by remember { mutableStateOf<BookLanguagePreference?>(null) }
    var requiredPermissionInfo by remember { mutableStateOf<RequiredOnboardingPermission?>(null) }
    var saving by remember { mutableStateOf(false) }
    var welcomeAccent by remember { mutableStateOf(onboardingCarouselTitles.first().accentColor) }
    val stepSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val stepEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val backgroundEffectsSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()

    fun updateSources(next: Set<CatalogSourcePreference>) {
        selectedSources = normalizeOnboardingSources(next)
        selectedLanguages = normalizeOnboardingLanguages(selectedLanguages, selectedSources)
    }

    fun updateLanguages(next: Set<BookLanguagePreference>) {
        selectedLanguages = normalizeOnboardingLanguages(next, selectedSources)
    }

    fun finishOnboarding() {
        if (saving) return
        saving = true
        scope.launch {
            val sourcePreference = BookSourcePreference.fromEnabledSources(normalizeOnboardingSources(selectedSources))
            val languages = normalizeOnboardingLanguages(selectedLanguages, selectedSources)
            store.saveBookSourcePreference(sourcePreference)
            store.savePreferredLanguages(languages)
            store.saveAutomaticSearchCachingEnabled(automaticSearchCachingEnabled)
            CatalogAutocompleteRefreshWorker.setAutomaticRefreshEnabled(context, automaticSearchCachingEnabled)
            if (automaticSearchCachingEnabled && selectedSources.isNotEmpty()) {
                CatalogAutocompleteRefreshWorker.enqueueManualRefresh(context)
            }
            store.saveAutoDownloadMode(autoDownloadMode)
            store.saveDownloadNetworkPolicy(networkPolicy)
            graph.app.profileRepository.create("Listener", AvatarSeed.Default)
        }
    }

    fun advanceStep() {
        when {
            step == OnboardingStep.Permissions -> {
                onRequestPermissions(permissionSelection)
                stepIndex++
            }
            stepIndex < steps.lastIndex -> stepIndex++
            else -> finishOnboarding()
        }
    }

    fun startSourceCacheRefresh() {
        if (!automaticSearchCachingEnabled || selectedSources.isEmpty()) return
        val languages = normalizeOnboardingLanguages(selectedLanguages, selectedSources)
        if (languages.isEmpty()) return
        val sourcePreference = BookSourcePreference.fromEnabledSources(normalizeOnboardingSources(selectedSources))
        store.saveBookSourcePreference(sourcePreference)
        store.savePreferredLanguages(languages)
        store.saveAutomaticSearchCachingEnabled(true)
        CatalogAutocompleteRefreshWorker.setAutomaticRefreshEnabled(context, true)
        CatalogAutocompleteRefreshWorker.enqueueManualRefresh(context)
    }

    LaunchedEffect(stepIndex) {
        scrollState.scrollTo(0)
    }

    LaunchedEffect(selectedSources, selectedLanguages, automaticSearchCachingEnabled) {
        startSourceCacheRefresh()
    }

    inactiveLanguage?.let { language ->
        AlertDialog(
            onDismissRequest = { inactiveLanguage = null },
            title = { Text("${language.label} is inactive") },
            text = {
                Text(languageInactiveMessage(language, selectedSources))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.key()
                        inactiveLanguage = null
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
    requiredPermissionInfo?.let { permission ->
        AlertDialog(
            onDismissRequest = { requiredPermissionInfo = null },
            title = { Text(permission.title) },
            text = { Text(permission.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.key()
                        requiredPermissionInfo = null
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
    val colorScheme = MaterialTheme.colorScheme
    val tonal = LocalTonalSurfaces.current
    val activeWelcomeAccent = if (step == OnboardingStep.Welcome) {
        welcomeAccent
    } else {
        colorScheme.primary
    }
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        OnboardingBackdrop(
            accent = activeWelcomeAccent,
            isWelcome = step == OnboardingStep.Welcome,
            animationSpec = backgroundEffectsSpec,
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Set up LibriVox Mobile") },
                    navigationIcon = {
                        if (stepIndex > 0) {
                            IconButton(
                                onClick = {
                                    haptics.key()
                                    stepIndex--
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = tonal.bottomChrome,
                    ),
                )
            },
            bottomBar = {
                OnboardingBottomBar(
                    canGoBack = stepIndex > 0,
                    canAdvance = !saving &&
                        (step != OnboardingStep.Sources || selectedSources.isNotEmpty()) &&
                        (step != OnboardingStep.Languages || selectedLanguages.isNotEmpty()),
                    showScrollDown = step != OnboardingStep.Welcome &&
                        hasMoreContent,
                    saving = saving,
                    isFirstStep = stepIndex == 0,
                    isLastStep = stepIndex == steps.lastIndex,
                    onBack = {
                        haptics.key()
                        stepIndex--
                    },
                    onScrollDown = {
                        haptics.tick()
                        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    },
                    onAdvance = {
                        if (stepIndex == steps.lastIndex) {
                            haptics.confirm()
                        } else {
                            haptics.key()
                        }
                        advanceStep()
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                OnboardingProgressRail(
                    progress = (stepIndex + 1).toFloat() / steps.size.toFloat(),
                )
                AnimatedContent(
                    targetState = stepIndex,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val enter = slideInHorizontally(animationSpec = stepSpatialSpec) { width ->
                            if (forward) width else -width
                        } + fadeIn(animationSpec = stepEffectsSpec)
                        val exit = slideOutHorizontally(animationSpec = stepSpatialSpec) { width ->
                            if (forward) -width else width
                        } + fadeOut(animationSpec = stepEffectsSpec)
                        enter togetherWith exit
                    },
                    label = "onboarding-step-transition",
                    modifier = Modifier.weight(1f),
                ) {
                    val targetStep = steps[it]
                    OnboardingStepPage(
                        step = targetStep,
                        stepIndex = it,
                        stepCount = steps.size,
                        scrollState = scrollState,
                        isScrollable = targetStep != OnboardingStep.Welcome,
                    ) {
                        when (targetStep) {
                            OnboardingStep.Welcome -> WelcomeStep(
                                onAccentChange = { welcomeAccent = it },
                            )
                            OnboardingStep.Permissions -> PermissionsStep(
                                selection = permissionSelection,
                                onSelectionChange = { selection ->
                                    permissionSelection = selection
                                },
                                onRequiredPermissionClick = { permission ->
                                    requiredPermissionInfo = permission
                                },
                            )
                            OnboardingStep.Sources -> SourcesStep(
                                selectedSources = selectedSources,
                                onSelectAll = { updateSources(BookSourcePreference.SelectableSources.toSet()) },
                                onSelectNone = { updateSources(emptySet()) },
                                onSourceToggle = { source ->
                                    val next = if (source in selectedSources) {
                                        selectedSources - source
                                    } else {
                                        selectedSources + source
                                    }
                                    updateSources(next)
                                },
                            )
                            OnboardingStep.Languages -> LanguagesStep(
                                selectedSources = selectedSources,
                                selectedLanguages = selectedLanguages,
                                onSelectAll = {
                                    updateLanguages(availableOnboardingLanguages(selectedSources).toSet())
                                },
                                onSelectNone = {
                                    updateLanguages(emptySet())
                                },
                                onLanguageToggle = { language ->
                                    if (!isLanguageAvailableForOnboarding(language, selectedSources)) {
                                        inactiveLanguage = language
                                        return@LanguagesStep
                                    }
                                    val normalized = normalizeOnboardingLanguages(selectedLanguages, selectedSources)
                                    val next = if (language in normalized) {
                                        normalized - language
                                    } else {
                                        normalized + language
                                    }
                                    updateLanguages(next)
                                },
                                onInactiveLanguageClick = { inactiveLanguage = it },
                            )
                            OnboardingStep.Downloads -> DownloadsStep(
                                autoDownloadMode = autoDownloadMode,
                                networkPolicy = networkPolicy,
                                automaticSearchCachingEnabled = automaticSearchCachingEnabled,
                                onAutoDownloadModeChange = { autoDownloadMode = it },
                                onNetworkPolicyChange = { networkPolicy = it },
                                onAutomaticSearchCachingChange = { automaticSearchCachingEnabled = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingBackdrop(
    accent: Color,
    isWelcome: Boolean,
    animationSpec: AnimationSpec<Color>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonal = LocalTonalSurfaces.current
    val canvas by animateColorAsState(
        targetValue = tonal.screenBackground,
        animationSpec = animationSpec,
        label = "onboarding-backdrop-canvas",
    )
    val upperPanel by animateColorAsState(
        targetValue = if (isWelcome) {
            lerp(tonal.floatingPane, accent, 0.24f)
        } else {
            lerp(colorScheme.surfaceContainerHigh, colorScheme.primaryContainer, 0.18f)
        },
        animationSpec = animationSpec,
        label = "onboarding-backdrop-upper-panel",
    )
    val lowerPanel by animateColorAsState(
        targetValue = if (isWelcome) {
            lerp(tonal.bottomChrome, accent, 0.16f)
        } else {
            lerp(colorScheme.surfaceContainerLow, colorScheme.secondaryContainer, 0.16f)
        },
        animationSpec = animationSpec,
        label = "onboarding-backdrop-lower-panel",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 118.dp, y = (-70).dp)
                .fillMaxWidth(if (isWelcome) WelcomeBackdropWidthFraction else SetupBackdropWidthFraction)
                .height(if (isWelcome) 282.dp else 204.dp),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
                bottomStart = if (isWelcome) 112.dp else 80.dp,
            ),
            color = upperPanel,
            tonalElevation = 0.dp,
            content = {},
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-92).dp, y = 76.dp)
                .fillMaxWidth(if (isWelcome) 0.70f else 0.58f)
                .height(if (isWelcome) 178.dp else 140.dp),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = if (isWelcome) 104.dp else 72.dp,
                bottomEnd = 0.dp,
                bottomStart = 0.dp,
            ),
            color = lowerPanel,
            tonalElevation = 0.dp,
            content = {},
        )
    }
}

@Composable
private fun OnboardingStepPage(
    step: OnboardingStep,
    stepIndex: Int,
    stepCount: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    isScrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val edgeToEdgeContent = when (step) {
        OnboardingStep.Sources,
        OnboardingStep.Languages,
        OnboardingStep.Downloads -> true
        else -> false
    }
    val baseModifier = Modifier
        .fillMaxSize()
        .padding(
            horizontal = if (step == OnboardingStep.Welcome || edgeToEdgeContent) 0.dp else 24.dp,
            vertical = if (step == OnboardingStep.Welcome) 10.dp else 20.dp,
        )
    Column(
        modifier = if (isScrollable) {
            baseModifier.verticalScroll(scrollState)
        } else {
            baseModifier
        },
        verticalArrangement = Arrangement.spacedBy(if (step == OnboardingStep.Welcome) 10.dp else 18.dp),
    ) {
        if (step != OnboardingStep.Welcome) {
            Text(
                text = "Step ${stepIndex + 1} of $stepCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (edgeToEdgeContent) Modifier.padding(horizontal = 24.dp) else Modifier,
            )
            Column(
                modifier = if (edgeToEdgeContent) Modifier.padding(horizontal = 24.dp) else Modifier,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun OnboardingProgressRail(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    canGoBack: Boolean,
    canAdvance: Boolean,
    showScrollDown: Boolean,
    saving: Boolean,
    isFirstStep: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onScrollDown: () -> Unit,
    onAdvance: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        OnboardingActionDockContent(
            canGoBack = canGoBack,
            canAdvance = canAdvance,
            showScrollDown = showScrollDown,
            saving = saving,
            isFirstStep = isFirstStep,
            isLastStep = isLastStep,
            onBack = onBack,
            onScrollDown = onScrollDown,
            onAdvance = onAdvance,
        )
    }
}

@Composable
private fun OnboardingActionDockContent(
    canGoBack: Boolean,
    canAdvance: Boolean,
    showScrollDown: Boolean,
    saving: Boolean,
    isFirstStep: Boolean,
    isLastStep: Boolean,
    onBack: () -> Unit,
    onScrollDown: () -> Unit,
    onAdvance: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = canGoBack) {
            TextButton(
                onClick = onBack,
                enabled = !saving,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                ),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        MorphingOnboardingAction(
            showScrollDown = showScrollDown,
            canAdvance = canAdvance,
            saving = saving,
            label = when {
                isLastStep -> "Start listening"
                isFirstStep -> "Get started"
                else -> "Next"
            },
            icon = if (isFirstStep) Icons.AutoMirrored.Filled.ArrowForward else null,
            onScrollDown = onScrollDown,
            onAdvance = onAdvance,
        )
    }
}

@Composable
private fun MorphingOnboardingAction(
    showScrollDown: Boolean,
    canAdvance: Boolean,
    saving: Boolean,
    label: String,
    icon: ImageVector?,
    onScrollDown: () -> Unit,
    onAdvance: () -> Unit,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val actionState = when {
        showScrollDown -> OnboardingActionVisual.ScrollDown
        saving -> OnboardingActionVisual.Saving
        else -> OnboardingActionVisual.Label(label = label, icon = icon)
    }
    val enabled = showScrollDown || (canAdvance && !saving)
    val width by animateDpAsState(
        targetValue = when (actionState) {
            OnboardingActionVisual.ScrollDown -> 104.dp
            OnboardingActionVisual.Saving -> 140.dp
            is OnboardingActionVisual.Label -> when (actionState.label) {
                "Get started" -> 176.dp
                "Start listening" -> 184.dp
                else -> 112.dp
            }
        },
        animationSpec = spatialSpec,
        label = "onboarding-action-width",
    )
    val corner by animateDpAsState(
        targetValue = 24.dp,
        animationSpec = spatialSpec,
        label = "onboarding-action-corner",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            showScrollDown -> MaterialTheme.colorScheme.surfaceContainerHighest
            canAdvance || saving -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = effectsSpec,
        label = "onboarding-action-container",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            showScrollDown -> MaterialTheme.colorScheme.onSurfaceVariant
            canAdvance || saving -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = effectsSpec,
        label = "onboarding-action-content",
    )

    Button(
        onClick = {
            if (showScrollDown) {
                onScrollDown()
            } else {
                onAdvance()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(corner),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = actionState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(160)) togetherWith
                        fadeOut(animationSpec = tween(110))
                },
                label = "onboarding-action-content",
            ) { state ->
                when (state) {
                    OnboardingActionVisual.ScrollDown -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("More")
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    OnboardingActionVisual.Saving -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = contentColor,
                        )
                    }
                    is OnboardingActionVisual.Label -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.label)
                            if (state.icon != null) {
                                Icon(state.icon, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface OnboardingActionVisual {
    data object ScrollDown : OnboardingActionVisual
    data object Saving : OnboardingActionVisual
    data class Label(
        val label: String,
        val icon: ImageVector?,
    ) : OnboardingActionVisual
}

@Composable
private fun WelcomeStep(
    onAccentChange: (Color) -> Unit,
) {
    WelcomeIntro()
    WelcomeCatalogCarousel(onAccentChange = onAccentChange)
    WelcomeFeatureDetails(modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun WelcomeIntro() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "LibriVox Mobile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Welcome to LibriVox Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Find free public-domain recordings, stream chapters, and save favorites for offline listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WelcomeCatalogCarousel(
    onAccentChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = onboardingCarouselTitles
    val virtualItemCount = titles.size * WelcomeCarouselLoopCycles
    val initialVirtualItem = remember(titles.size) {
        (WelcomeCarouselLoopCycles / 2) * titles.size
    }
    val carouselState = rememberCarouselState { virtualItemCount }
    var carouselInitialized by remember { mutableStateOf(false) }
    val currentVirtualItem = carouselState.currentItem
    val currentIndex = currentVirtualItem % titles.size

    LaunchedEffect(currentIndex) {
        onAccentChange(titles[currentIndex].accentColor)
    }
    LaunchedEffect(initialVirtualItem) {
        if (!carouselInitialized) {
            carouselState.scrollToItem(initialVirtualItem)
            carouselInitialized = true
        }
    }
    LaunchedEffect(carouselState, titles.size) {
        if (titles.size <= 1) return@LaunchedEffect
        while (true) {
            delay(WelcomeCarouselIdleDelayMillis)
            if (carouselState.isScrollInProgress) continue
            delay(WelcomeCarouselAutoScrollPauseMillis)
            if (carouselState.isScrollInProgress) continue
            val currentItem = carouselState.currentItem
            val safeCurrentItem = if (currentItem > virtualItemCount - titles.size * 2) {
                val resetItem = initialVirtualItem + currentItem % titles.size
                carouselState.scrollToItem(resetItem)
                resetItem
            } else {
                currentItem
            }
            carouselState.animateScrollToItem(safeCurrentItem + 1)
        }
    }

    HorizontalUncontainedCarousel(
        state = carouselState,
        itemWidth = 132.dp,
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(184.dp),
    ) { index ->
        val titleIndex = index % titles.size
        WelcomeBookTile(
            title = titles[titleIndex],
            selected = index == currentVirtualItem,
        )
    }
}

@Composable
private fun WelcomeBookTile(
    title: OnboardingCarouselTitle,
    selected: Boolean,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceVariant,
                0.22f,
            )
        },
        label = "welcome-title-container",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 20.dp,
        label = "welcome-title-corner",
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        tonalElevation = if (selected) 4.dp else 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Image(
                painter = painterResource(title.coverRes),
                contentDescription = "${title.title} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = title.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = title.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WelcomeFeatureDetails(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        SettingsGroup(title = "What you can do") {
            WelcomeFeatureRow(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                headline = "Browse classic audiobooks",
                supporting = "Start with LibriVox, then opt into other public audiobook sources when you want them.",
            )
            SectionDivider()
            WelcomeFeatureRow(
                icon = Icons.Filled.Download,
                headline = "Stream or save chapters",
                supporting = "Listen online, or download chapters when you want them offline.",
            )
            SectionDivider()
            WelcomeFeatureRow(
                icon = Icons.Filled.Cast,
                headline = "Listen anywhere",
                supporting = "Use background controls, bookmarks, speed, sleep timer, and Cast.",
            )
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    headline: String,
    supporting: String,
) {
    val tonal = LocalTonalSurfaces.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = MaterialTheme.shapes.medium,
            color = tonal.inlineAction,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PermissionsStep(
    selection: OnboardingPermissionSelection,
    onSelectionChange: (OnboardingPermissionSelection) -> Unit,
    onRequiredPermissionClick: (RequiredOnboardingPermission) -> Unit,
) {
    OpenSettingsSection(title = "Required access") {
        RequiredPermissionRow(
            permission = RequiredOnboardingPermission.Internet,
            icon = Icons.Filled.CheckCircle,
            onClick = { onRequiredPermissionClick(RequiredOnboardingPermission.Internet) },
        )
        SectionDivider()
        RequiredPermissionRow(
            permission = RequiredOnboardingPermission.AppStorage,
            icon = Icons.Filled.Storage,
            onClick = { onRequiredPermissionClick(RequiredOnboardingPermission.AppStorage) },
        )
    }
    OpenSettingsSection(title = "Optional permission requests") {
        PermissionCheckboxRow(
            selected = selection.notifications,
            headline = "Notifications",
            supporting = "Shows playback controls and listening status outside the app.",
            icon = Icons.Filled.Notifications,
            onClick = {
                onSelectionChange(selection.copy(notifications = !selection.notifications))
            },
        )
        SectionDivider()
        PermissionCheckboxRow(
            selected = selection.localNetworkCasting,
            headline = "Nearby devices for casting",
            supporting = "Finds speakers and TVs on your local network when you want to cast audio.",
            icon = Icons.Filled.Cast,
            onClick = {
                onSelectionChange(selection.copy(localNetworkCasting = !selection.localNetworkCasting))
            },
        )
    }
}

@Composable
private fun SourcesStep(
    selectedSources: Set<CatalogSourcePreference>,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSourceToggle: (CatalogSourcePreference) -> Unit,
) {
    SourceSelectionHeader(
        selectedCount = selectedSources.size,
        onSelectAll = onSelectAll,
        onSelectNone = onSelectNone,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selectedSources.isEmpty()) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = if (selectedSources.isEmpty()) {
                "Pick at least one source so Discover has somewhere to look."
            } else {
                "You can change sources later in Settings."
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (selectedSources.isEmpty()) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
    OpenSettingsSection(title = "Book sources") {
        onboardingPrimarySources.forEach { source ->
            SourceListItem(
                source = source,
                selected = source in selectedSources,
                enabled = true,
                onClick = { onSourceToggle(source) },
            )
        }
        SectionDivider()
        SourceGroupHeader("Other public audiobook sources")
        onboardingOtherPublicSources.forEachIndexed { index, source ->
            SourceListItem(
                source = source,
                selected = source in selectedSources,
                enabled = true,
                onClick = { onSourceToggle(source) },
            )
            if (index != onboardingOtherPublicSources.lastIndex) SectionDivider()
        }
    }
}

@Composable
private fun SourceSelectionHeader(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val haptics = rememberHaptics()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = tonal.inlineAction,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (selectedCount == 0) {
                    "Choose sources"
                } else {
                    "$selectedCount of ${BookSourcePreference.SelectableSources.size} selected"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = {
                    haptics.tick()
                    onSelectNone()
                },
            ) {
                Text("None")
            }
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onSelectAll()
                },
            ) {
                Text("All")
            }
        }
    }
}

@Composable
private fun SourceGroupHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SourceListItem(
    source: CatalogSourcePreference,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tonal = LocalTonalSurfaces.current
    val haptics = rememberHaptics()
    val hapticClick = {
        haptics.tick()
        onClick()
    }
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            tonal.listItem
        },
        animationSpec = effectsSpec,
        label = "source-row-container",
    )
    val logoContainerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            tonal.inlineAction
        },
        animationSpec = effectsSpec,
        label = "source-row-logo-container",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 16.dp,
        animationSpec = spatialSpec,
        label = "source-row-corner",
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (!enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (selected) {
        contentColor.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled, onClick = hapticClick)
            .animateContentSize(),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        tonalElevation = if (selected) 3.dp else 1.dp,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                SourceLogo(
                    source = source,
                    containerColor = logoContainerColor,
                    size = 48.dp,
                    padding = 7.dp,
                )
            },
            headlineContent = {
                Text(
                    text = source.onboardingTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = source.onboardingDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { hapticClick() },
                    enabled = enabled,
                )
            },
        )
    }
}

@Composable
private fun SourceLogo(
    source: CatalogSourcePreference,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    size: Dp = 64.dp,
    padding: Dp = 8.dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(source.logoRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun LanguagesStep(
    selectedSources: Set<CatalogSourcePreference>,
    selectedLanguages: Set<BookLanguagePreference>,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onLanguageToggle: (BookLanguagePreference) -> Unit,
    onInactiveLanguageClick: (BookLanguagePreference) -> Unit,
) {
    val normalizedLanguages = normalizeOnboardingLanguages(selectedLanguages, selectedSources)
    SelectionToolbar(
        title = when {
            normalizedLanguages.isEmpty() -> "No languages selected."
            normalizedLanguages.size == availableOnboardingLanguages(selectedSources).size -> "All available languages selected"
            else -> "${normalizedLanguages.size} selected"
        },
        onSelectAll = onSelectAll,
        onSelectNone = onSelectNone,
    )
    AnimatedVisibility(visible = normalizedLanguages.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = "Select at least one language to continue.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    OpenSettingsSection(title = "Audiobook languages") {
        BookLanguagePreference.ConcreteLanguages.forEach { language ->
            val available = isLanguageAvailableForOnboarding(language, selectedSources)
            val selected = language in normalizedLanguages
            PreferenceCheckboxRow(
                selected = selected,
                enabled = available,
                headline = language.label,
                supporting = languageSupportingText(language, selectedSources),
                icon = Icons.Filled.Language,
                onClick = { onLanguageToggle(language) },
                onDisabledClick = if (!available) {
                    { onInactiveLanguageClick(language) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun DownloadsStep(
    autoDownloadMode: AutoDownloadMode,
    networkPolicy: DownloadNetworkPolicy,
    automaticSearchCachingEnabled: Boolean,
    onAutoDownloadModeChange: (AutoDownloadMode) -> Unit,
    onNetworkPolicyChange: (DownloadNetworkPolicy) -> Unit,
    onAutomaticSearchCachingChange: (Boolean) -> Unit,
) {
    OpenSettingsSection(title = "Pre-download while listening") {
        AutoDownloadMode.entries.forEachIndexed { index, mode ->
            PreferenceRadioRow(
                selected = autoDownloadMode == mode,
                headline = mode.label,
                supporting = mode.description,
                icon = Icons.Filled.Download,
                onClick = { onAutoDownloadModeChange(mode) },
            )
            if (index != AutoDownloadMode.entries.lastIndex) SectionDivider()
        }
    }
    OpenSettingsSection(title = "Manual downloads") {
        val policies = DownloadNetworkPolicy.entries.filterNot { it == DownloadNetworkPolicy.Manual }
        policies.forEachIndexed { index, policy ->
            PreferenceRadioRow(
                selected = networkPolicy == policy,
                headline = policy.label,
                supporting = policy.description,
                icon = Icons.Filled.Download,
                onClick = { onNetworkPolicyChange(policy) },
            )
            if (index != policies.lastIndex) SectionDivider()
        }
    }
    OpenSettingsSection(title = "Search and browsing cache") {
        PreferenceSwitchRow(
            checked = automaticSearchCachingEnabled,
            headline = "Keep a search cache",
            supporting = "Save catalog lists on this device so search and shelves open faster.",
            icon = Icons.Filled.Search,
            onCheckedChange = onAutomaticSearchCachingChange,
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = tonal.listItem,
            tonalElevation = 1.dp,
            content = { Column(content = content) },
        )
    }
}

@Composable
private fun OpenSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun SelectionToolbar(
    title: String,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val haptics = rememberHaptics()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = tonal.inlineAction,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = {
                    haptics.tick()
                    onSelectNone()
                },
            ) {
                Text("None")
            }
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onSelectAll()
                },
            ) {
                Text("All")
            }
        }
    }
}

@Composable
private fun RequiredPermissionRow(
    permission: RequiredOnboardingPermission,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    PreferenceRow(
        enabled = false,
        headline = when (permission) {
            RequiredOnboardingPermission.Internet -> "Internet access"
            RequiredOnboardingPermission.AppStorage -> "App storage"
        },
        supporting = when (permission) {
            RequiredOnboardingPermission.Internet ->
                "Streams LibriVox books, searches the catalog, and loads covers while you browse."
            RequiredOnboardingPermission.AppStorage ->
                "Keeps saved chapters, library, bookmarks, and settings on this device."
        },
        icon = icon,
        onClick = {},
        onDisabledClick = {
            haptics.reject()
            onClick()
        },
        trailing = {
            Checkbox(
                checked = true,
                enabled = false,
                onCheckedChange = null,
            )
        },
    )
}

@Composable
private fun PermissionCheckboxRow(
    selected: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val hapticClick = {
        haptics.toggle(!selected)
        onClick()
    }
    PreferenceRow(
        enabled = true,
        headline = headline,
        supporting = supporting,
        icon = icon,
        onClick = hapticClick,
        trailing = {
            Checkbox(
                checked = selected,
                onCheckedChange = { hapticClick() },
            )
        },
    )
}

@Composable
private fun PreferenceCheckboxRow(
    selected: Boolean,
    enabled: Boolean = true,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onDisabledClick: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    val hapticClick = {
        haptics.tick()
        onClick()
    }
    val hapticDisabledClick = if (onDisabledClick != null) {
        {
            haptics.reject()
            onDisabledClick()
        }
    } else {
        null
    }
    PreferenceRow(
        enabled = enabled,
        headline = headline,
        supporting = supporting,
        icon = icon,
        onClick = hapticClick,
        onDisabledClick = hapticDisabledClick,
        trailing = {
            Checkbox(
                checked = selected,
                enabled = enabled,
                onCheckedChange = if (enabled) {
                    { hapticClick() }
                } else {
                    null
                },
            )
        },
    )
}

@Composable
private fun PreferenceRadioRow(
    selected: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val hapticClick = {
        if (selected) {
            haptics.key()
        } else {
            haptics.tick()
        }
        onClick()
    }
    PreferenceRow(
        enabled = true,
        headline = headline,
        supporting = supporting,
        icon = icon,
        onClick = hapticClick,
        trailing = { RadioButton(selected = selected, onClick = hapticClick) },
    )
}

@Composable
private fun PreferenceSwitchRow(
    checked: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    val hapticChange = { next: Boolean ->
        haptics.toggle(next)
        onCheckedChange(next)
    }
    PreferenceRow(
        enabled = true,
        headline = headline,
        supporting = supporting,
        icon = icon,
        onClick = { hapticChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = hapticChange) },
    )
}

@Composable
private fun PreferenceRow(
    enabled: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onDisabledClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    val canClick = enabled || onDisabledClick != null
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    }
    val rowColor = if (enabled) {
        tonal.listItem
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = canClick) {
                if (enabled) {
                    onClick()
                } else {
                    onDisabledClick?.invoke()
                }
            },
        shape = MaterialTheme.shapes.large,
        color = rowColor,
        tonalElevation = if (enabled) 1.dp else 0.dp,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (enabled) tonal.inlineAction else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = headline,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = supporting,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = trailing,
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
}

internal fun normalizeOnboardingSources(
    sources: Set<CatalogSourcePreference>,
): Set<CatalogSourcePreference> =
    sources.intersect(BookSourcePreference.SelectableSourceSet)

internal fun normalizeOnboardingLanguages(
    languages: Set<BookLanguagePreference>,
    sources: Set<CatalogSourcePreference>,
): Set<BookLanguagePreference> {
    val normalizedSources = normalizeOnboardingSources(sources)
    if (normalizedSources.isEmpty() || languages.isEmpty()) return emptySet()
    return languages.normalizeForCatalogSource(
        BookSourcePreference.fromEnabledSources(normalizedSources),
    )
}

internal fun availableOnboardingLanguages(
    sources: Set<CatalogSourcePreference>,
): List<BookLanguagePreference> {
    val normalizedSources = normalizeOnboardingSources(sources)
    return when {
        normalizedSources.isEmpty() -> emptyList()
        else -> BookSourcePreference.fromEnabledSources(normalizedSources).selectableLanguages()
    }
}

internal fun isLanguageAvailableForOnboarding(
    language: BookLanguagePreference,
    sources: Set<CatalogSourcePreference>,
): Boolean {
    val normalizedSources = normalizeOnboardingSources(sources)
    if (language == BookLanguagePreference.All || normalizedSources.isEmpty()) return false
    return language in BookSourcePreference.fromEnabledSources(normalizedSources).selectableLanguages()
}

private fun languageSupportingText(
    language: BookLanguagePreference,
    sources: Set<CatalogSourcePreference>,
): String =
    when {
        normalizeOnboardingSources(sources).isEmpty() ->
            "Choose at least one source before selecting languages."
        isLanguageAvailableForOnboarding(language, sources) ->
            "Show ${language.label.lowercase()} audiobooks when a selected source has them."
        language == BookLanguagePreference.Polish ->
            "Select Wolne Lektury to browse Polish audiobooks."
        else ->
            "Add LibriVox, Lit2Go, or Gutendex to browse this language."
    }

private fun languageInactiveMessage(
    language: BookLanguagePreference,
    sources: Set<CatalogSourcePreference>,
): String =
    when {
        normalizeOnboardingSources(sources).isEmpty() ->
            "Choose at least one source before selecting languages."
        language == BookLanguagePreference.Polish ->
            "Select Wolne Lektury before choosing Polish."
        else ->
            "Select LibriVox, Lit2Go, or Gutendex before choosing ${language.label}."
    }

internal data class OnboardingCarouselTitle(
    val title: String,
    val author: String,
    @param:DrawableRes val coverRes: Int,
    val accentColor: Color,
)

internal val onboardingCarouselTitles = listOf(
    OnboardingCarouselTitle(
        title = "Moby Dick",
        author = "Herman Melville",
        coverRes = R.drawable.onboarding_cover_moby_dick,
        accentColor = Color(0xFF86817C),
    ),
    OnboardingCarouselTitle(
        title = "Pride and Prejudice",
        author = "Jane Austen",
        coverRes = R.drawable.onboarding_cover_pride_prejudice,
        accentColor = Color(0xFF5F533F),
    ),
    OnboardingCarouselTitle(
        title = "Frankenstein",
        author = "Mary Shelley",
        coverRes = R.drawable.onboarding_cover_frankenstein,
        accentColor = Color(0xFF838773),
    ),
    OnboardingCarouselTitle(
        title = "Romeo and Juliet",
        author = "William Shakespeare",
        coverRes = R.drawable.onboarding_cover_romeo_juliet,
        accentColor = Color(0xFF897268),
    ),
    OnboardingCarouselTitle(
        title = "Crime and Punishment",
        author = "Fyodor Dostoyevsky",
        coverRes = R.drawable.onboarding_cover_crime_punishment,
        accentColor = Color(0xFF683325),
    ),
    OnboardingCarouselTitle(
        title = "The Count of Monte Cristo",
        author = "Alexandre Dumas",
        coverRes = R.drawable.onboarding_cover_monte_cristo,
        accentColor = Color(0xFFDED7C1),
    ),
    OnboardingCarouselTitle(
        title = "Alice's Adventures in Wonderland",
        author = "Lewis Carroll",
        coverRes = R.drawable.onboarding_cover_alice,
        accentColor = Color(0xFF97795B),
    ),
    OnboardingCarouselTitle(
        title = "The Adventures of Sherlock Holmes",
        author = "Arthur Conan Doyle",
        coverRes = R.drawable.onboarding_cover_sherlock,
        accentColor = Color(0xFF2F5977),
    ),
)

private val onboardingPrimarySources = BookSourcePreference.PrimarySelectableSources

private val onboardingOtherPublicSources = BookSourcePreference.OtherSelectableSources

private val CatalogSourcePreference.onboardingTitle: String
    get() = when (this) {
        CatalogSourcePreference.LibriVox -> "LibriVox"
        CatalogSourcePreference.Lit2Go -> "Lit2Go"
        CatalogSourcePreference.Gutendex -> "Project Gutenberg"
        CatalogSourcePreference.WolneLektury -> "Wolne Lektury"
    }

private val CatalogSourcePreference.onboardingDescription: String
    get() = when (this) {
        CatalogSourcePreference.LibriVox ->
            "Volunteer-read public-domain audiobooks, with a large catalog of classic novels, poetry, and nonfiction."
        CatalogSourcePreference.Lit2Go ->
            "Curated educational audiobooks and short works from the University of South Florida, often with readable text."
        CatalogSourcePreference.Gutendex ->
            "Project Gutenberg audiobook records and metadata, useful when a public-domain title has Gutenberg audio."
        CatalogSourcePreference.WolneLektury ->
            "Polish-only public-domain audiobooks, ebooks, and literary metadata from WolneLektury.pl."
    }

@get:DrawableRes
private val CatalogSourcePreference.logoRes: Int
    get() = when (this) {
        CatalogSourcePreference.LibriVox -> R.drawable.logo_librivox
        CatalogSourcePreference.Lit2Go -> R.drawable.logo_lit2go
        CatalogSourcePreference.Gutendex -> R.drawable.logo_project_gutenberg
        CatalogSourcePreference.WolneLektury -> R.drawable.logo_wolne_lektury
    }
