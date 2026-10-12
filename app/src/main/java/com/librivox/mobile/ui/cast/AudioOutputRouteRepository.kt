package com.librivox.mobile.ui.cast

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Summarizes the currently useful external audio route for player UI.
 *
 * Route selection itself stays with Android's system Output Switcher. This
 * class only decides whether an external route is present so the player can
 * show a small "Playing on ..." affordance for Bluetooth, wired, USB, HDMI,
 * hearing-aid, and remote routes.
 */
class AudioOutputRouteRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mediaRouter = MediaRouter2.getInstance(appContext)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AudioOutputRouteState(currentExternalRoute()))
    val state: StateFlow<AudioOutputRouteState> = _state.asStateFlow()

    private var subscriptions: Int = 0

    private val controllerCallback = object : MediaRouter2.ControllerCallback() {
        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
            refresh()
        }
    }

    private val transferCallback = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(
            oldController: MediaRouter2.RoutingController,
            newController: MediaRouter2.RoutingController,
        ) {
            refresh()
        }

        override fun onTransferFailure(requestedRoute: MediaRoute2Info) {
            refresh()
        }

        override fun onStop(controller: MediaRouter2.RoutingController) {
            refresh()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    @MainThread
    fun start() {
        subscriptions++
        if (subscriptions == 1) {
            val executor = ContextCompat.getMainExecutor(appContext)
            mediaRouter.registerControllerCallback(executor, controllerCallback)
            mediaRouter.registerTransferCallback(executor, transferCallback)
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        }
        refresh()
    }

    @MainThread
    fun stop() {
        subscriptions = (subscriptions - 1).coerceAtLeast(0)
        if (subscriptions == 0) {
            mediaRouter.unregisterControllerCallback(controllerCallback)
            mediaRouter.unregisterTransferCallback(transferCallback)
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
    }

    @MainThread
    fun refresh() {
        _state.value = AudioOutputRouteState(currentExternalRoute())
    }

    @MainThread
    fun showSystemOutputSwitcher(sessionToken: MediaSession.Token?): Boolean {
        val tokenResult = sessionToken?.let { token ->
            runCatching {
                val method = MediaRouter2::class.java.getMethod(
                    "showSystemOutputSwitcher",
                    MediaSession.Token::class.java,
                )
                method.invoke(mediaRouter, token) as? Boolean
            }.getOrNull()
        }
        return tokenResult ?: mediaRouter.showSystemOutputSwitcher()
    }

    private fun currentExternalRoute(): AudioOutputRoute? {
        val mediaRoute = mediaRouter.controllers
            .asSequence()
            .flatMap { it.selectedRoutes.asSequence() }
            .distinctBy { it.id }
            .mapNotNull { it.toExternalAudioOutputRoute() }
            .minByOrNull { it.kind.priority }
        if (mediaRoute != null) return mediaRoute

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .asSequence()
            .filter { it.isSink }
            .mapNotNull { it.toExternalAudioOutputRoute() }
            .minByOrNull { it.kind.priority }
    }

    private fun MediaRoute2Info.toExternalAudioOutputRoute(): AudioOutputRoute? {
        val kind = audioOutputRouteKindForType(type) ?: return null
        return AudioOutputRoute(
            name = name.toString().takeIf { it.isNotBlank() } ?: kind.fallbackName,
            kind = kind,
        )
    }

    private fun AudioDeviceInfo.toExternalAudioOutputRoute(): AudioOutputRoute? {
        val kind = audioOutputRouteKindForType(type) ?: return null
        return AudioOutputRoute(
            name = productName?.toString()?.takeIf { it.isNotBlank() } ?: kind.fallbackName,
            kind = kind,
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioOutputRouteRepository? = null

        fun getInstance(context: Context): AudioOutputRouteRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioOutputRouteRepository(context).also { INSTANCE = it }
            }
    }
}

data class AudioOutputRouteState(
    val route: AudioOutputRoute? = null,
) {
    val hasExternalRoute: Boolean get() = route != null
}

data class AudioOutputRoute(
    val name: String,
    val kind: AudioOutputRouteKind,
)

enum class AudioOutputRouteKind(
    val fallbackName: String,
    internal val priority: Int,
) {
    Remote("Remote device", 0),
    Bluetooth("Bluetooth", 1),
    WiredHeadphones("Headphones", 2),
    Usb("USB audio", 3),
    Hdmi("External display", 4),
    HearingAid("Hearing aid", 5),
    OtherExternal("Audio output", 6),
}

internal fun audioOutputRouteKindForType(type: Int): AudioOutputRouteKind? =
    when (type) {
        MediaRoute2Info.TYPE_REMOTE_TV,
        MediaRoute2Info.TYPE_REMOTE_SPEAKER,
        MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER,
        MediaRoute2Info.TYPE_REMOTE_TABLET,
        MediaRoute2Info.TYPE_REMOTE_TABLET_DOCKED,
        MediaRoute2Info.TYPE_REMOTE_COMPUTER,
        MediaRoute2Info.TYPE_REMOTE_GAME_CONSOLE,
        MediaRoute2Info.TYPE_REMOTE_CAR,
        MediaRoute2Info.TYPE_REMOTE_SMARTWATCH,
        MediaRoute2Info.TYPE_REMOTE_SMARTPHONE,
        -> AudioOutputRouteKind.Remote
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        -> AudioOutputRouteKind.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        -> AudioOutputRouteKind.WiredHeadphones
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> AudioOutputRouteKind.Usb
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        -> AudioOutputRouteKind.Hdmi
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputRouteKind.HearingAid
        AudioDeviceInfo.TYPE_DOCK -> AudioOutputRouteKind.OtherExternal
        else -> null
    }
