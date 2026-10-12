package com.librivox.mobile.model

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.librivox.mobile.playback.AppColorPalette
import com.librivox.mobile.playback.AppThemeMode
import com.librivox.mobile.playback.PlaybackSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueListeningPersistenceTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "polish-test-${System.nanoTime()}"
    private val preferenceFiles = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "$namespace-$name"
            preferenceFiles += isolatedName
            return base.getSharedPreferences(isolatedName, mode)
        }
    }

    @After
    fun cleanUp() {
        preferenceFiles.forEach(base::deleteSharedPreferences)
    }

    @Test
    fun dismissalPersistsThroughProgressSavingAndRestoreKeepsProgress() {
        val store = ProgressStore(context)
        store.savePosition("book", "chapter", 3, 14_000L)
        store.dismissFromContinueListening("book")
        store.savePosition("book", "chapter", 3, 18_000L)
        store.saveLastBook("book", "chapter", 3)
        store.markBookActivity("book", "chapter", 3)

        val recreated = ProgressStore(context)
        assertTrue("book" in recreated.dismissedContinueListeningBookIds)
        assertEquals(18_000L, recreated.savedPositionMs("book", "chapter"))
        assertEquals(3, recreated.lastChapterIndex("book"))
        recreated.restoreToContinueListening("book")
        assertFalse("book" in ProgressStore(context).dismissedContinueListeningBookIds)
        assertEquals(18_000L, recreated.savedPositionMs("book", "chapter"))
    }

    @Test
    fun palettePersistsAndDoesNotChangeThemeOrArtworkPreference() {
        val settings = PlaybackSettingsStore(context)
        assertEquals(AppColorPalette.DeviceAndApp, settings.colorPalette)
        settings.saveThemeMode(AppThemeMode.Dark)
        settings.saveBookDetailUseArtworkColorScheme(true)
        AppColorPalette.entries.forEach { palette ->
            settings.saveColorPalette(palette)
            val recreated = PlaybackSettingsStore(context)
            assertEquals(palette, recreated.colorPalette)
            assertEquals(AppThemeMode.Dark, recreated.themeMode)
            assertTrue(recreated.bookDetailUseArtworkColorScheme)
        }
    }
}
