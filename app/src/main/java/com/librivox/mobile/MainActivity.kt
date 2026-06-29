package com.librivox.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.librivox.mobile.model.Profile
import com.librivox.mobile.playback.AudiobookPlaybackService
import com.librivox.mobile.playback.AppThemeMode
import com.librivox.mobile.playback.PlayerStateRepository
import com.librivox.mobile.ui.cast.CastRouteRepository
import com.librivox.mobile.ui.navigation.AppGraph
import com.librivox.mobile.ui.navigation.BookOpenIntentRequest
import com.librivox.mobile.ui.navigation.AudiobookHost
import com.librivox.mobile.ui.navigation.LocalAppGraph
import com.librivox.mobile.ui.navigation.toBookOpenIntentRequest
import com.librivox.mobile.ui.onboarding.OnboardingPermissionSelection
import com.librivox.mobile.ui.onboarding.ProfileOnboardingScreen
import com.librivox.mobile.ui.theme.AudioPlayerTheme

class MainActivity : FragmentActivity() {

    private lateinit var playerStateRepository: PlayerStateRepository
    private lateinit var castRouteRepository: CastRouteRepository
    private var pendingDeepLinkUrl by mutableStateOf<String?>(null)
    private var pendingBookOpenRequest by mutableStateOf<BookOpenIntentRequest?>(null)

    private val runtimePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* surfaced by system UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        val app = applicationContext as AudiobookApplication
        castRouteRepository = app.castRouteRepository
        playerStateRepository = PlayerStateRepository(
            context = applicationContext,
            playbackBus = app.playbackBus,
            libraryRepository = app.libraryRepository,
            serviceClass = AudiobookPlaybackService::class.java,
        )

        val graph = AppGraph(app = app, playerStateRepository = playerStateRepository)

        setContent {
            var themeMode by remember { mutableStateOf(app.playbackSettingsStore.themeMode) }
            var profileLoaded by remember { mutableStateOf(false) }
            var profile by remember { mutableStateOf<Profile?>(null) }
            val systemDark = isSystemInDarkTheme()
            DisposableEffect(app.playbackSettingsStore) {
                val modeListener = app.playbackSettingsStore.registerThemeModeListener { themeMode = it }
                onDispose {
                    app.playbackSettingsStore.unregisterListener(modeListener)
                }
            }
            LaunchedEffect(app.profileRepository) {
                app.profileRepository.profile.collect { currentProfile ->
                    profile = currentProfile
                    profileLoaded = true
                }
            }
            val darkTheme = when (themeMode) {
                AppThemeMode.Auto -> systemDark
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }
            AudioPlayerTheme(
                darkTheme = darkTheme,
            ) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    when {
                        !profileLoaded -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        profile == null -> {
                            ProfileOnboardingScreen(
                                onRequestPermissions = ::requestSelectedRuntimePermissions,
                            )
                        }
                        else -> {
                            AudiobookHost(
                                incomingLinkUrl = pendingDeepLinkUrl,
                                incomingBookOpenRequest = pendingBookOpenRequest,
                                onIncomingLinkHandled = { handledUrl ->
                                    if (pendingDeepLinkUrl == handledUrl) {
                                        pendingDeepLinkUrl = null
                                    }
                                },
                                onIncomingBookOpenRequestHandled = { handledRequest ->
                                    if (pendingBookOpenRequest?.requestId == handledRequest.requestId) {
                                        pendingBookOpenRequest = null
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        playerStateRepository.connect()
    }

    override fun onResume() {
        super.onResume()
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volumeDelta = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> 0
        }
        if (volumeDelta != 0 && shouldHandleCastHardwareVolume()) {
            if (event.action == KeyEvent.ACTION_DOWN && !adjustCastHardwareVolume(volumeDelta)) {
                return super.dispatchKeyEvent(event)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun shouldHandleCastHardwareVolume(): Boolean =
        castRouteRepository.hasConnectedCastSession() ||
            playerStateRepository.shouldHandleHardwareVolume() ||
            castRouteRepository.hasSelectedCastRoute()

    private fun adjustCastHardwareVolume(volumeDelta: Int): Boolean =
        castRouteRepository.adjustConnectedCastSessionVolume(volumeDelta) ||
            playerStateRepository.adjustDeviceVolume(volumeDelta, AudioManager.FLAG_SHOW_UI) ||
            castRouteRepository.adjustSelectedRouteVolume(volumeDelta)

    private fun handleIncomingIntent(intent: Intent?) {
        val openRequest = intent?.toBookOpenIntentRequest()
        if (openRequest != null) {
            Log.d("BookOpenIntent", "Received open request: $openRequest")
        }
        pendingBookOpenRequest = openRequest
        pendingDeepLinkUrl = if (openRequest == null) intent?.dataString else null
    }

    override fun onStop() {
        playerStateRepository.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        playerStateRepository.release()
        super.onDestroy()
    }

    private fun requestSelectedRuntimePermissions(selection: OnboardingPermissionSelection) {
        val permissions = buildList {
            if (selection.localNetworkCasting) {
                add(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
            if (selection.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isNotEmpty()) {
            runtimePermissionsLauncher.launch(permissions.toTypedArray())
        }
    }
}
