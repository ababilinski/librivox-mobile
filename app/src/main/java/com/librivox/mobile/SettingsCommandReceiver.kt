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
        readCoverArtDisplayMode(intent)?.let {
            settings.saveCoverArtDisplayMode(it)
        }
    }

    private fun readCoverArtDisplayMode(intent: Intent): CoverArtDisplayMode? {
        val raw = readStringExtra(intent, EXTRA_COVER_ART, EXTRA_COVER_ART_DISPLAY_MODE) ?: return null
        return CoverArtDisplayMode.fromPreference(raw.trim().lowercase())
            .takeUnless { it == CoverArtDisplayMode.Default && raw != CoverArtDisplayMode.Default.preferenceValue }
    }

    private fun readStringExtra(intent: Intent, vararg names: String): String? =
        names.firstNotNullOfOrNull { intent.getStringExtra(it) }

    companion object {
        const val ACTION_SET_APPEARANCE = "com.librivox.mobile.action.SET_APPEARANCE"
        const val EXTRA_COVER_ART = "cover_art"
        const val EXTRA_COVER_ART_DISPLAY_MODE = "cover_art_display_mode"
    }
}
