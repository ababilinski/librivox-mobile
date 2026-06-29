package com.librivox.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.librivox.mobile.playback.CoverArtDisplayMode
import com.librivox.mobile.playback.PlaybackSettingsStore

class SettingsCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_APPEARANCE) return

        val settings = PlaybackSettingsStore(context)
        readBooleanExtra(intent, EXTRA_COVER_BACKDROP_FADE, EXTRA_COVER_BACKDROP)?.let {
            settings.saveBookDetailUseCoverBackdrop(it)
        }
        readCoverArtDisplayMode(intent)?.let {
            settings.saveCoverArtDisplayMode(it)
        }
    }

    private fun readCoverArtDisplayMode(intent: Intent): CoverArtDisplayMode? {
        val raw = readStringExtra(intent, EXTRA_COVER_ART, EXTRA_COVER_ART_DISPLAY_MODE) ?: return null
        return CoverArtDisplayMode.fromPreference(raw.trim().lowercase())
            .takeUnless { it == CoverArtDisplayMode.Default && raw != CoverArtDisplayMode.Default.preferenceValue }
    }

    private fun readBooleanExtra(intent: Intent, vararg names: String): Boolean? =
        names.firstOrNull { intent.hasExtra(it) }?.let { intent.getBooleanExtra(it, false) }

    private fun readStringExtra(intent: Intent, vararg names: String): String? =
        names.firstNotNullOfOrNull { intent.getStringExtra(it) }

    companion object {
        const val ACTION_SET_APPEARANCE = "com.librivox.mobile.action.SET_APPEARANCE"
        const val EXTRA_COVER_BACKDROP_FADE = "cover_backdrop_fade"
        const val EXTRA_COVER_BACKDROP = "cover_backdrop"
        const val EXTRA_COVER_ART = "cover_art"
        const val EXTRA_COVER_ART_DISPLAY_MODE = "cover_art_display_mode"
    }
}
