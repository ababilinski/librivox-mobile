package com.librivox.mobile.playback

import android.content.Context
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

class AudiobookCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val settings = PlaybackSettingsStore(context.applicationContext)
        return CastOptions.Builder()
            .setResumeSavedSession(settings.castResumeSession)
            .setEnableReconnectionService(false)
            .setLaunchOptions(
                LaunchOptions.Builder()
                    .setAndroidReceiverCompatible(true)
                    .build(),
            )
            .setReceiverApplicationId(settings.castReceiverAppId)
            .setCastMediaOptions(castMediaOptions())
            .setStopReceiverApplicationWhenEndingSession(settings.castStopReceiverOnDisconnect)
            .setRemoteToLocalEnabled(true)
            .setSessionTransferEnabled(true)
            .setMediaTransferRestrictedToSelfProviders(false)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> =
        emptyList()

    // Media3's AudiobookPlaybackService already owns the MediaSession and media
    // notification around CastPlayer. Letting Cast SDK create its own session
    // or notification publishes extra remote VolumeProviders, which Pixel's
    // volume panel shows as duplicate Cast sliders.
    private fun castMediaOptions(): CastMediaOptions =
        CastMediaOptions.Builder()
            .setMediaSessionEnabled(false)
            .setNotificationOptions(null)
            .build()
}
