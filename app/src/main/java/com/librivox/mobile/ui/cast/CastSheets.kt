package com.librivox.mobile.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.librivox.mobile.playback.PlayerState
import com.librivox.mobile.playback.PlaybackSettingsStore
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.theme.PlayerSheetTheme
import com.librivox.mobile.ui.theme.rememberPlayerSheetColorScheme
import kotlin.math.roundToInt

/** Whether the cast picker is currently mounted. */
sealed interface CastSheetTarget {
    data object None : CastSheetTarget
    data object Picker : CastSheetTarget
}

/**
 * Mounts the cast picker for [target] and renders nothing otherwise. The picker
 * stays open after a route is selected so the user can switch devices or hand
 * playback back to the phone — matching YouTube Music. Volume is controlled by
 * the hardware rocker via [com.librivox.mobile.MainActivity.dispatchKeyEvent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSheetHost(
    repository: CastRouteRepository,
    target: CastSheetTarget,
    onTargetChange: (CastSheetTarget) -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as com.librivox.mobile.AudiobookApplication }
    val store = app.playbackSettingsStore
    val useSdkUi = store.castUiMode == com.librivox.mobile.playback.CastUiMode.GoogleDefault

    LaunchedEffect(target, useSdkUi) {
        if (target == CastSheetTarget.Picker && useSdkUi) {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null) {
                com.librivox.mobile.cast.sdk.CastSdkUi
                    .showRouteChooserDialog(activity, store.castReceiverAppId)
            }
            onTargetChange(CastSheetTarget.None)
        }
    }

    when (target) {
        CastSheetTarget.None -> Unit
        CastSheetTarget.Picker -> if (!useSdkUi) {
            CastDevicePickerSheet(
                repository = repository,
                onDismiss = { onTargetChange(CastSheetTarget.None) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastDevicePickerSheet(
    repository: CastRouteRepository,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val store = graph.app.playbackSettingsStore
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val routes by repository.routes.collectAsStateWithLifecycle()
    val selectedRoute by repository.selectedRoute.collectAsStateWithLifecycle()
    val isDiscovering by repository.isDiscovering.collectAsStateWithLifecycle()
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val rawPlayerState by graph.playerStateRepository.state.collectAsStateWithLifecycle()
    val isCasting = connectionState == CastRouteRepository.ConnectionState.Connected ||
        connectionState == CastRouteRepository.ConnectionState.Connecting
    val isConnected = connectionState == CastRouteRepository.ConnectionState.Connected
    val isConnecting = connectionState == CastRouteRepository.ConnectionState.Connecting
    var latchedPlayerState by remember { mutableStateOf(rawPlayerState) }
    LaunchedEffect(rawPlayerState.hasMedia) {
        if (rawPlayerState.hasMedia) latchedPlayerState = rawPlayerState
    }
    val playerState = when {
        rawPlayerState.hasMedia -> rawPlayerState
        isCasting && latchedPlayerState.hasMedia -> latchedPlayerState
        else -> rawPlayerState
    }
    val activeRoute = selectedRoute ?: routes.firstOrNull { it.isSelected }
    val visibleRoutes = activeRoute?.let { route ->
        routes.filterNot { it.id == route.id || it.name == route.name }
    } ?: routes
    var useArtworkColorScheme by remember { mutableStateOf(store.castUseArtworkColorScheme) }
    val sheetColors = rememberPlayerSheetColorScheme(
        if (useArtworkColorScheme) playerState.artworkUri else null,
    )

    DisposableEffect(repository) {
        repository.startDiscovery()
        onDispose { repository.stopDiscovery() }
    }

    DisposableEffect(store) {
        val listener = store.registerCastSettingsListener { key ->
            if (key == PlaybackSettingsStore.KEY_CAST_USE_ARTWORK_COLOR_SCHEME) {
                useArtworkColorScheme = store.castUseArtworkColorScheme
            }
        }
        onDispose { store.unregisterListener(listener) }
    }

    // Auto-dismiss only after a Connecting→Connected transition so the user
    // sees the check briefly, then doesn't need to manually close the sheet.
    // Opening the picker while already Connected (to switch device) stays open.
    var previousState by remember { mutableStateOf(connectionState) }
    LaunchedEffect(connectionState) {
        if (
            previousState == CastRouteRepository.ConnectionState.Connecting &&
            connectionState == CastRouteRepository.ConnectionState.Connected
        ) {
            kotlinx.coroutines.delay(450)
            sheetState.hide()
            onDismiss()
        }
        previousState = connectionState
    }

    PlayerSheetTheme(scheme = sheetColors) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isCasting && activeRoute != null) {
                    ConnectedPlaybackSection(
                        playerState = playerState,
                        route = activeRoute,
                        isConnecting = isConnecting,
                        isConnected = isConnected,
                        onVolumeChange = { volume -> repository.setVolume(volume) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                } else {
                    LocalPlaybackSection(playerState = playerState)
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                }

                if (!isCasting && visibleRoutes.isEmpty()) {
                    SearchingDevicesPeek(
                        isDiscovering = isDiscovering,
                        hasSelectableRoutes = activeRoute != null,
                    )
                    return@Column
                }

                if (isCasting || visibleRoutes.isNotEmpty()) {
                    AllDevicesSection(
                        showLocalDevice = isCasting,
                        routes = visibleRoutes,
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onReturnToDevice = { repository.disconnect() },
                        onRouteClick = { route ->
                            if (route.isSelected && isConnected) {
                                repository.disconnect()
                            } else {
                                repository.selectRoute(route.id)
                            }
                        },
                    )
                }

                SearchingDevicesPeek(
                    isDiscovering = isDiscovering,
                    hasSelectableRoutes = visibleRoutes.isNotEmpty() || activeRoute != null,
                )
            }
        }
    }
}

@Composable
private fun LocalPlaybackSection(
    playerState: PlayerState,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (playerState.hasMedia) {
            Text(
                text = "Playing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PlaybackSummary(playerState = playerState)
        } else {
            Text(
                text = "Cast to a device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        CastRouteRow(
            name = "This device",
            icon = Icons.Filled.PhoneAndroid,
            isSelected = true,
            isConnecting = false,
            enabled = false,
            onClick = {},
        )
    }
}

@Composable
private fun ConnectedPlaybackSection(
    playerState: PlayerState,
    route: CastRouteRepository.CastRoute,
    isConnecting: Boolean,
    isConnected: Boolean,
    onVolumeChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Playing",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        PlaybackSummary(playerState = playerState)
        ConnectedRouteControls(
            route = route,
            isConnecting = isConnecting,
            isConnected = isConnected,
            onVolumeChange = onVolumeChange,
        )
    }
}

@Composable
private fun PlaybackSummary(playerState: PlayerState) {
    val title = playerState.albumTitle
        ?.takeIf { it.isNotBlank() }
        ?: playerState.title?.takeIf { it.isNotBlank() }
        ?: "Current audio"
    val subtitle = listOfNotNull(
        playerState.author?.takeIf { it.isNotBlank() },
        playerState.title?.takeIf { it.isNotBlank() && it != title },
    ).joinToString(" • ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (!playerState.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = playerState.artworkUri,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectedRouteControls(
    route: CastRouteRepository.CastRoute,
    isConnecting: Boolean,
    isConnected: Boolean,
    onVolumeChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(
                imageVector = route.deviceType.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = route.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when {
                isConnecting -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                isConnected -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isConnected) {
            Slider(
                value = route.volume.coerceIn(0, route.volumeMax).toFloat(),
                onValueChange = { value -> onVolumeChange(value.roundToInt()) },
                valueRange = 0f..route.volumeMax.coerceAtLeast(1).toFloat(),
                enabled = route.volumeMax > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, end = 6.dp),
            )
        } else {
            Text(
                text = "Connecting…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 46.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun AllDevicesSection(
    showLocalDevice: Boolean,
    routes: List<CastRouteRepository.CastRoute>,
    isConnected: Boolean,
    isConnecting: Boolean,
    onReturnToDevice: () -> Unit,
    onRouteClick: (CastRouteRepository.CastRoute) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel("All devices")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showLocalDevice) {
                CastRouteRow(
                    name = "This device",
                    icon = Icons.Filled.PhoneAndroid,
                    isSelected = false,
                    isConnecting = false,
                    onClick = onReturnToDevice,
                )
            }
            routes.forEach { route ->
                CastRouteRow(
                    name = route.name,
                    icon = route.deviceType.icon(),
                    isSelected = route.isSelected && isConnected,
                    isConnecting = route.isSelected && isConnecting,
                    onClick = { onRouteClick(route) },
                )
            }
        }
    }
}

@Composable
private fun SearchingDevicesPeek(
    isDiscovering: Boolean,
    hasSelectableRoutes: Boolean,
) {
    if (!isDiscovering && hasSelectableRoutes) return

    if (!hasSelectableRoutes) {
        Spacer(modifier = Modifier.height(18.dp))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (hasSelectableRoutes) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDiscovering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (isDiscovering) "Searching for devices" else "No Cast devices found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CastRouteRow(
    name: String,
    icon: ImageVector,
    isSelected: Boolean,
    isConnecting: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val emphasized = isSelected || isConnecting
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
    ListItem(
        modifier = if (enabled) {
            rowModifier.clickable(onClick = onClick)
        } else {
            rowModifier
        },
        headlineContent = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        trailingContent = when {
            isConnecting -> {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            isSelected -> {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            else -> null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private fun CastRouteRepository.CastDeviceType.icon(): ImageVector = when (this) {
    CastRouteRepository.CastDeviceType.Speaker -> Icons.Filled.Speaker
    CastRouteRepository.CastDeviceType.Tv -> Icons.Filled.Tv
    CastRouteRepository.CastDeviceType.Group -> Icons.Filled.GroupWork
    CastRouteRepository.CastDeviceType.Other -> Icons.Filled.CastConnected
}
