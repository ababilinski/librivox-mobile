package com.librivox.mobile.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.playback.CastPreloadTime
import com.librivox.mobile.playback.CastUiMode
import com.librivox.mobile.playback.PlaybackSettingsStore
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSettingsScreen(
    store: PlaybackSettingsStore,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var uiMode by remember { mutableStateOf(store.castUiMode) }
    var introDismissed by remember { mutableStateOf(store.castIntroDismissed) }
    var useArtworkColorScheme by remember { mutableStateOf(store.castUseArtworkColorScheme) }
    var resumeSession by remember { mutableStateOf(store.castResumeSession) }
    var stopReceiver by remember { mutableStateOf(store.castStopReceiverOnDisconnect) }
    var preloadTime by remember { mutableStateOf(store.castPreloadTime) }
    var receiverAppId by remember { mutableStateOf(store.castReceiverAppId) }
    var forceLocalHttp by remember { mutableStateOf(store.castForceLocalHttp) }
    var verboseLogging by remember { mutableStateOf(store.castVerboseLogging) }
    var diagnosticsEnabled by remember { mutableStateOf(store.castDiagnosticsEnabled) }
    var diagnosticsOverlay by remember { mutableStateOf(store.castDiagnosticsOverlay) }
    var restartHintVisible by remember { mutableStateOf(false) }

    DisposableEffect(store) {
        val listener = store.registerCastSettingsListener { _ ->
            uiMode = store.castUiMode
            introDismissed = store.castIntroDismissed
            useArtworkColorScheme = store.castUseArtworkColorScheme
            resumeSession = store.castResumeSession
            stopReceiver = store.castStopReceiverOnDisconnect
            preloadTime = store.castPreloadTime
            receiverAppId = store.castReceiverAppId
            forceLocalHttp = store.castForceLocalHttp
            verboseLogging = store.castVerboseLogging
            diagnosticsEnabled = store.castDiagnosticsEnabled
            diagnosticsOverlay = store.castDiagnosticsOverlay
        }
        onDispose { store.unregisterListener(listener) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val tonal = LocalTonalSurfaces.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(restartHintVisible) {
        if (restartHintVisible) {
            snackbarHostState.showSnackbar(
                message = "Restart the app for the change to take effect.",
                actionLabel = "Dismiss",
            )
            restartHintVisible = false
        }
    }

    fun flagRestartIfNeeded(applied: Boolean) {
        if (applied) restartHintVisible = true
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = tonal.screenBackground,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Cast") },
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
                CastSection(title = "Cast UI") {
                    CastUiMode.entries.forEachIndexed { index, option ->
                        CastChoiceRow(
                            selected = uiMode == option,
                            headline = option.label,
                            supporting = option.description,
                            icon = Icons.Filled.Cast,
                            onClick = {
                                uiMode = option
                                store.saveCastUiMode(option)
                                flagRestartIfNeeded(true)
                            },
                        )
                        if (index != CastUiMode.entries.lastIndex) CastSectionDivider()
                    }
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Use audio colors in Cast sheet",
                        supporting = "Tint the custom Cast picker from the current artwork, like Now Playing.",
                        icon = Icons.Filled.Tune,
                        checked = useArtworkColorScheme,
                        onCheckedChange = {
                            useArtworkColorScheme = it
                            store.saveCastUseArtworkColorScheme(it)
                        },
                    )
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Show intro overlay on first cast",
                        supporting = if (introDismissed) {
                            "Dismissed. Reset below to show again."
                        } else {
                            "Show a tooltip the first time the user opens the Cast picker."
                        },
                        icon = Icons.Filled.Visibility,
                        checked = !introDismissed,
                        onCheckedChange = { showAgain ->
                            introDismissed = !showAgain
                            store.saveCastIntroDismissed(!showAgain)
                        },
                    )
                    if (introDismissed) {
                        CastSectionDivider()
                        TextRow(
                            headline = "Reset intro overlay state",
                            onClick = {
                                introDismissed = false
                                store.saveCastIntroDismissed(false)
                            },
                            icon = Icons.Filled.Refresh,
                        )
                    }
                }
            }

            item {
                CastSection(title = "Session") {
                    CastSwitchRow(
                        headline = "Resume previous Cast session on launch",
                        supporting = "Reconnect automatically if a session was active when you closed the app.",
                        icon = Icons.Filled.Cast,
                        checked = resumeSession,
                        onCheckedChange = {
                            resumeSession = it
                            store.saveCastResumeSession(it)
                            flagRestartIfNeeded(true)
                        },
                    )
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Stop receiver when disconnecting",
                        supporting = "End the receiver app when the phone ends the session.",
                        icon = Icons.Filled.Cast,
                        checked = stopReceiver,
                        onCheckedChange = {
                            stopReceiver = it
                            store.saveCastStopReceiverOnDisconnect(it)
                            flagRestartIfNeeded(true)
                        },
                    )
                    CastSectionDivider()
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Queue preload time",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "How far ahead the receiver buffers the next chapter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CastPreloadTime.entries.forEachIndexed { index, option ->
                        CastChoiceRow(
                            selected = preloadTime == option,
                            headline = option.label,
                            supporting = "${option.seconds}s preroll",
                            icon = Icons.Filled.Speed,
                            onClick = {
                                preloadTime = option
                                store.saveCastPreloadTime(option)
                            },
                        )
                        if (index != CastPreloadTime.entries.lastIndex) CastSectionDivider()
                    }
                }
            }

            item {
                CastExpandableSection(title = "Advanced") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Receiver application ID",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Override the default media receiver. Requires app restart.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = receiverAppId,
                            onValueChange = {
                                receiverAppId = it.uppercase().filter(Char::isLetterOrDigit)
                                store.saveCastReceiverAppId(receiverAppId)
                                flagRestartIfNeeded(true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            placeholder = { Text(PlaybackSettingsStore.DEFAULT_CAST_RECEIVER_APP_ID) },
                        )
                        TextButton(
                            onClick = {
                                receiverAppId = PlaybackSettingsStore.DEFAULT_CAST_RECEIVER_APP_ID
                                store.saveCastReceiverAppId(null)
                                flagRestartIfNeeded(true)
                            },
                        ) {
                            Text("Use default")
                        }
                    }
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Force local Cast HTTP server",
                        supporting = "Always route media through the local HTTP bridge, even for remote URLs.",
                        icon = Icons.Filled.Tune,
                        checked = forceLocalHttp,
                        onCheckedChange = {
                            forceLocalHttp = it
                            store.saveCastForceLocalHttp(it)
                        },
                    )
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Verbose Cast SDK logging",
                        supporting = "Increase log verbosity. Requires app restart to take full effect.",
                        icon = Icons.Filled.BugReport,
                        checked = verboseLogging,
                        onCheckedChange = {
                            verboseLogging = it
                            store.saveCastVerboseLogging(it)
                            flagRestartIfNeeded(true)
                        },
                    )
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Cast diagnostics logging",
                        supporting = "Capture Cast session and HTTP events to an in-app log.",
                        icon = Icons.Filled.BugReport,
                        checked = diagnosticsEnabled,
                        onCheckedChange = {
                            diagnosticsEnabled = it
                            store.saveCastDiagnosticsEnabled(it)
                        },
                    )
                    CastSectionDivider()
                    CastSwitchRow(
                        headline = "Show Cast diagnostics overlay",
                        supporting = "Floating overlay during cast sessions with realtime state.",
                        icon = Icons.Filled.Visibility,
                        checked = diagnosticsOverlay,
                        onCheckedChange = {
                            diagnosticsOverlay = it
                            store.saveCastDiagnosticsOverlay(it)
                        },
                    )
                    CastSectionDivider()
                    CastNavigationRow(
                        headline = "Open diagnostics log",
                        supporting = "View captured Cast session events.",
                        icon = Icons.Filled.BugReport,
                        onClick = onOpenDiagnostics,
                    )
                    CastSectionDivider()
                    TextRow(
                        headline = "Reset Cast preferences",
                        onClick = {
                            store.resetCastPreferences()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Cast preferences reset to defaults.")
                            }
                            flagRestartIfNeeded(true)
                        },
                        icon = Icons.Filled.RestartAlt,
                        tintError = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CastSection(title: String, content: @Composable () -> Unit) {
    val tonal = LocalTonalSurfaces.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
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
private fun CastExpandableSection(title: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tonal = LocalTonalSurfaces.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 32.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = tonal.listItem,
            tonalElevation = 1.dp,
        ) {
            Column {
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = {
                        Text("Debug & advanced options")
                    },
                    supportingContent = {
                        Text(
                            text = if (expanded) "Tap to collapse" else "Tap to expand",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                AnimatedVisibility(expanded) {
                    Column { content() }
                }
            }
        }
    }
}

@Composable
private fun CastChoiceRow(
    selected: Boolean,
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
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
            androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        },
    )
}

@Composable
private fun CastSwitchRow(
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(text = headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun CastNavigationRow(
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
        headlineContent = { Text(text = headline) },
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

@Composable
private fun TextRow(
    headline: String,
    onClick: () -> Unit,
    icon: ImageVector,
    tintError: Boolean = false,
) {
    val tint = if (tintError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        },
        headlineContent = { Text(text = headline, color = tint) },
    )
}

@Composable
private fun CastSectionDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}
