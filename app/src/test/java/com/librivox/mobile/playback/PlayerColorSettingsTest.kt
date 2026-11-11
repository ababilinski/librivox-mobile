package com.librivox.mobile.playback

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PlayerColorSettingsTest {
    private lateinit var context: Context
    private lateinit var store: PlaybackSettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PlaybackSettingsStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = PlaybackSettingsStore(context)
    }

    @Test
    fun appColorsAreTheDefaultEvenWhenRetiredDetailSettingsRemainOnDisk() {
        context.getSharedPreferences(PlaybackSettingsStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("book_detail_use_artwork_color_scheme", true)
            .putBoolean("book_detail_use_cover_backdrop", true)
            .commit()

        assertEquals(PlayerColorMode.App, store.playerColorMode)
    }

    @Test
    fun playerColorChoiceSurvivesReopeningWithoutChangingAppPaletteOrTheme() {
        store.saveThemeMode(AppThemeMode.Dark)
        store.saveColorPalette(AppColorPalette.Device)

        PlayerColorMode.entries.forEach { mode ->
            store.savePlayerColorMode(mode)
            val reopened = PlaybackSettingsStore(context)
            assertEquals(mode, reopened.playerColorMode)
            assertEquals(AppThemeMode.Dark, reopened.themeMode)
            assertEquals(AppColorPalette.Device, reopened.colorPalette)
        }
    }

    @Test
    fun playerColorListenerRespondsOnlyToItsSettingAndCanBeRemoved() {
        val changes = mutableListOf<PlayerColorMode>()
        val listener = store.registerPlayerColorModeListener { changes += it }

        store.savePlayerColorMode(PlayerColorMode.Cover)
        shadowOf(Looper.getMainLooper()).idle()
        store.saveColorPalette(AppColorPalette.App)
        shadowOf(Looper.getMainLooper()).idle()
        store.unregisterListener(listener)
        store.savePlayerColorMode(PlayerColorMode.App)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(PlayerColorMode.Cover), changes)
    }
}
