package com.librivox.mobile.ui.cast

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between [MediaRouter] / Cast SDK and Compose. Sheets call
 * [startDiscovery]/[stopDiscovery] to begin/stop scanning, observe [routes] to
 * render the picker, and call [selectRoute]/[disconnect]/[setVolume] to drive
 * routing. The existing [com.librivox.mobile.playback.AudiobookPlaybackService]
 * Cast session callbacks handle the local↔remote player swap when a Cast
 * session starts.
 */
class CastRouteRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val router = MediaRouter.getInstance(appContext)
    private var castContext: CastContext? = null
    private var castContextInitializing: Boolean = false

    private val routeSelector: MediaRouteSelector =
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                ),
            )
            .build()

    private val _routes = MutableStateFlow<List<CastRoute>>(emptyList())
    val routes: StateFlow<List<CastRoute>> = _routes.asStateFlow()

    private val _selectedRoute = MutableStateFlow<CastRoute?>(null)
    val selectedRoute: StateFlow<CastRoute?> = _selectedRoute.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var activeCallback: MediaRouter.Callback? = null
    private var discoverySubscriptions: Int = 0
    private var observedCastSession: CastSession? = null
    private var observedRemoteMediaClient: RemoteMediaClient? = null

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refresh()
            if (!route.isDefault && route.matchesSelector(routeSelector)) {
                Log.d(TAG, "MediaRouter route volume changed: ${route.volume}/${route.volumeMax} on ${route.name}")
            }
        }
    }

    private val castVolumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            logCastVolumeSnapshot("Cast.Listener.onVolumeChanged")
            refresh()
        }
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            logCastVolumeSnapshot("RemoteMediaClient.onStatusUpdated")
        }

        override fun onSendingRemoteMediaRequest() {
            Log.d(TAG, "RemoteMediaClient sending request.")
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _connectionState.value = ConnectionState.Connecting
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _connectionState.value = ConnectionState.Connected
            observeCastSession(session)
            logCastVolumeSnapshot("onSessionStarted")
            refresh()
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _connectionState.value = ConnectionState.Idle
            clearObservedCastSession()
            refresh()
        }
        override fun onSessionEnding(session: CastSession) {
            logCastVolumeSnapshot("onSessionEnding", session)
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            _connectionState.value = ConnectionState.Idle
            clearObservedCastSession()
            refresh()
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _connectionState.value = ConnectionState.Connecting
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _connectionState.value = ConnectionState.Connected
            observeCastSession(session)
            logCastVolumeSnapshot("onSessionResumed")
            refresh()
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _connectionState.value = ConnectionState.Idle
            clearObservedCastSession()
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _connectionState.value = ConnectionState.Connecting
            logCastVolumeSnapshot("onSessionSuspended", session)
        }
    }

    init {
        refresh()
    }

    @MainThread
    fun startDiscovery() {
        ensureCastContext()
        discoverySubscriptions++
        if (activeCallback == null) {
            router.addCallback(
                routeSelector,
                routerCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
            )
            activeCallback = routerCallback
            _isDiscovering.value = true
        }
        refresh()
    }

    @MainThread
    fun stopDiscovery() {
        discoverySubscriptions = (discoverySubscriptions - 1).coerceAtLeast(0)
        if (discoverySubscriptions == 0) {
            activeCallback?.let { router.removeCallback(it) }
            activeCallback = null
            _isDiscovering.value = false
        }
    }

    @MainThread
    fun selectRoute(routeId: String) {
        ensureCastContext()
        val route = router.routes.firstOrNull { it.id == routeId } ?: return
        router.selectRoute(route)
    }

    @MainThread
    fun disconnect() {
        ensureCastContext()
        router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
    }

    @MainThread
    fun setVolume(value: Int) {
        val selected = router.selectedRoute
        if (selected.isDefault || selected.volumeHandling != MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) return
        selected.requestSetVolume(value.coerceIn(0, selected.volumeMax))
    }

    @MainThread
    fun hasSelectedCastRoute(): Boolean {
        val selected = router.selectedRoute
        return !selected.isDefault && selected.isEnabled && selected.matchesSelector(routeSelector)
    }

    @MainThread
    fun hasConnectedCastSession(): Boolean {
        ensureCastContext()
        return castContext?.sessionManager?.currentCastSession?.isConnected == true
    }

    @MainThread
    fun adjustConnectedCastSessionVolume(delta: Int): Boolean {
        if (delta == 0) return false
        ensureCastContext()
        val session = castContext?.sessionManager?.currentCastSession
            ?.takeIf { it.isConnected }
            ?: return false
        val current = runCatching { session.volume }
            .onFailure { Log.w(TAG, "Unable to read Cast session volume.", it) }
            .getOrNull()
            ?: return false
        val direction = if (delta > 0) 1 else -1
        val target = (current + direction.toDouble() / CAST_VOLUME_STEPS.toDouble())
            .coerceIn(0.0, 1.0)
        return runCatching {
            Log.d(TAG, "Volume key delta=$delta target=${formatVolume(target)} before=${volumeDebugSnapshot(session)}")
            if (target != current) {
                session.setVolume(target)
            }
            refresh()
        }.onFailure {
            Log.w(TAG, "Unable to adjust Cast session volume.", it)
        }.isSuccess
    }

    @MainThread
    fun adjustSelectedRouteVolume(delta: Int): Boolean {
        if (delta == 0) return false
        val selected = router.selectedRoute
        if (selected.isDefault || !selected.isEnabled || !selected.matchesSelector(routeSelector)) return false
        selected.requestUpdateVolume(delta)
        refresh()
        return true
    }

    private fun refresh() {
        _routes.value = router.routes
            .asSequence()
            .filter { !it.isDefault && it.isEnabled }
            .filter { it.matchesSelector(routeSelector) }
            .map { it.toCastRoute(isSelected = it.id == router.selectedRoute.id) }
            .toList()
        val selected = router.selectedRoute.takeIf { !it.isDefault && it.matchesSelector(routeSelector) }
        _selectedRoute.value = selected?.toCastRoute(isSelected = true)
    }

    @MainThread
    private fun ensureCastContext() {
        if (castContext != null || castContextInitializing) return
        castContextInitializing = true
        CastContext.getSharedInstance(appContext, ContextCompat.getMainExecutor(appContext))
            .addOnSuccessListener { context ->
                castContextInitializing = false
                castContext = context
                context.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                context.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { session ->
                    _connectionState.value = ConnectionState.Connected
                    observeCastSession(session)
                }
                refresh()
                Log.i(TAG, "Initialized Cast context for route picker.")
            }
            .addOnFailureListener { exception ->
                castContextInitializing = false
                Log.w(TAG, "Unable to initialize Cast context for route picker.", exception)
            }
    }

    private fun observeCastSession(session: CastSession) {
        if (observedCastSession === session) {
            return
        }
        clearObservedCastSession()
        observedCastSession = session
        runCatching { session.addCastListener(castVolumeListener) }
            .onFailure { Log.w(TAG, "Unable to observe Cast session volume.", it) }
        observedRemoteMediaClient = session.remoteMediaClient?.also { client ->
            client.registerCallback(remoteMediaClientCallback)
        }
    }

    private fun clearObservedCastSession() {
        observedRemoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        observedRemoteMediaClient = null
        observedCastSession?.let { session ->
            runCatching { session.removeCastListener(castVolumeListener) }
                .onFailure { Log.w(TAG, "Unable to stop observing Cast session volume.", it) }
        }
        observedCastSession = null
    }

    private fun logCastVolumeSnapshot(reason: String, session: CastSession? = observedCastSession) {
        if (session == null) return
        Log.d(TAG, "$reason: ${volumeDebugSnapshot(session)}")
    }

    private fun volumeDebugSnapshot(session: CastSession): String {
        val deviceVolume = runCatching { session.volume }
            .getOrNull()
            ?.let(::formatVolume)
            ?: "unavailable"
        val deviceMuted = runCatching { session.isMute }
            .getOrNull()
            ?.toString()
            ?: "unavailable"
        val mediaStatus = session.remoteMediaClient?.mediaStatus
        val streamVolume = mediaStatus
            ?.streamVolume
            ?.let(::formatVolume)
            ?: "unavailable"
        val route = router.selectedRoute
        val routeName = route.name.ifBlank { "unknown" }
        return "device=$deviceVolume muted=$deviceMuted stream=$streamVolume " +
            "playerState=${mediaStatus?.playerState ?: "unavailable"} " +
            "route=$routeName routeVolume=${route.volume}/${route.volumeMax}"
    }

    private fun formatVolume(value: Double): String =
        String.format(java.util.Locale.US, "%.3f", value)

    @Suppress("DEPRECATION")
    private fun MediaRouter.RouteInfo.toCastRoute(isSelected: Boolean): CastRoute = CastRoute(
        id = id,
        name = name,
        deviceType = when {
            CastDevice.getFromBundle(extras)?.hasCapability(CastDevice.CAPABILITY_MULTIZONE_GROUP) == true -> CastDeviceType.Group
            deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV -> CastDeviceType.Tv
            deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER -> CastDeviceType.Speaker
            else -> CastDeviceType.Other
        },
        volume = if (isSelected) {
            observedCastSession?.takeIf { it.isConnected }?.let { session ->
                (session.volume * volumeMax).toInt().coerceIn(0, volumeMax.coerceAtLeast(0))
            } ?: volume
        } else volume,
        volumeMax = volumeMax,
        canSetVolume = volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && volumeMax > 0,
        isSelected = isSelected,
    )

    data class CastRoute(
        val id: String,
        val name: String,
        val deviceType: CastDeviceType,
        val volume: Int,
        val volumeMax: Int,
        val isSelected: Boolean,
        val canSetVolume: Boolean = true,
    )

    enum class CastDeviceType { Speaker, Tv, Group, Other }

    enum class ConnectionState { Idle, Connecting, Connected }

    companion object {
        private const val TAG = "CastRouteRepository"
        private const val CAST_VOLUME_STEPS = 20

        @Volatile
        private var INSTANCE: CastRouteRepository? = null

        fun getInstance(context: Context): CastRouteRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CastRouteRepository(context).also { INSTANCE = it }
            }
    }
}
