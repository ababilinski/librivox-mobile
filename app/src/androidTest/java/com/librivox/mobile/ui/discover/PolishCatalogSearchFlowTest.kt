package com.librivox.mobile.ui.discover

import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.librivox.mobile.AudiobookApplication
import com.librivox.mobile.MainActivity
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.DiscoverLoadRequest
import com.librivox.mobile.catalog.DiscoverSortOrder
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.model.Profile
import com.librivox.mobile.model.AvatarSeed
import com.librivox.mobile.playback.BookSourcePreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in connected-device checks use the real source adapters, settings, and Discover state. */
@RunWith(AndroidJUnit4::class)
class PolishCatalogSearchFlowTest {
    @Test fun switchFromLibriVoxToPolishThenSearchLalkaAndAntek() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AudiobookApplication>()
        val store = app.playbackSettingsStore
        val previousSources = store.bookSourcePreference
        val previousCaching = store.automaticSearchCachingEnabled
        val previousProfile = app.profileRepository.snapshot()
        app.profileRepository.completeOnboarding()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            val connectivity = app.getSystemService(android.net.ConnectivityManager::class.java)
            android.util.Log.i("PolishSearchCheck", "Status=${app.networkStatusMonitor.status.value}, Network=${connectivity.activeNetwork}, capabilities=${connectivity.getNetworkCapabilities(connectivity.activeNetwork)}")
            withTimeout(30_000) { app.networkStatusMonitor.status.first { it.isOnline } }
            withContext(Dispatchers.Main) {
                store.saveAutomaticSearchCachingEnabled(false)
                store.saveBookSourcePreference(BookSourcePreference.LibriVoxOnly)
                app.discoverRepository.loadDiscoverPage(request("Lalka", CatalogSearchField.All, BookSourcePreference.LibriVoxOnly))
            }
            withTimeout(10_000) { app.discoverRepository.discoverCache.first { it.isLoading && it.query.equals("Lalka", ignoreCase = true) } }

            withContext(Dispatchers.Main) { store.saveBookSourcePreference(BookSourcePreference.WolneLekturyOnly) }
            for (field in listOf(CatalogSearchField.All, CatalogSearchField.Title)) {
                for (title in listOf("Lalka", "Antek")) {
                    withContext(Dispatchers.Main) {
                        app.discoverRepository.loadDiscoverPage(request(title, field, BookSourcePreference.WolneLekturyOnly))
                    }
                    val result = withTimeout(60_000) {
                        app.discoverRepository.discoverCache.first {
                            it.query.equals(title, ignoreCase = true) && it.searchField == field && !it.isLoading && it.loadedAtMillis > 0
                        }
                    }
                    assertTrue("$field/$title: ${result.errorMessage}; ${result.searchProgressItems}", result.results.any { it.id == "wolnelektury-${title.lowercase()}" })
                    assertTrue(result.results.all { it.source == BookSource.WolneLektury })
                }
            }
            withContext(Dispatchers.Main) {
                app.discoverRepository.loadDiscoverPage(request("", CatalogSearchField.All, BookSourcePreference.WolneLekturyOnly))
            }
            val browse = withTimeout(60_000) {
                app.discoverRepository.discoverCache.first { it.query.isBlank() && !it.isLoading && it.loadedAtMillis > 0 }
            }
            assertTrue("Polish browse: ${browse.errorMessage}", browse.results.isNotEmpty())
            assertTrue(browse.results.all { it.source == BookSource.WolneLektury })
        } finally {
            activity.close()
            withContext(Dispatchers.Main) {
                store.saveBookSourcePreference(previousSources)
                store.saveAutomaticSearchCachingEnabled(previousCaching)
                app.discoverRepository.clearDiscoverState()
            }
            if (previousProfile != null) app.profileRepository.update(previousProfile) else app.profileRepository.signOut()
        }
    }

    @Test fun completingUpgradeOnboardingPreservesTheExistingProfile() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AudiobookApplication>()
        val repository = app.profileRepository
        val previous = repository.snapshot()
        val existing = previous ?: Profile("upgrade-test", "Listener", AvatarSeed.Plum, 123)
        try {
            val legacy = existing.copy(completedOnboardingVersion = 0)
            repository.update(legacy)
            assertTrue(repository.snapshot()!!.requiresOnboarding)
            repository.completeOnboarding()
            val completed = repository.snapshot()!!
            assertEquals(existing.id, completed.id)
            assertEquals(existing.displayName, completed.displayName)
            assertTrue(!completed.requiresOnboarding)
        } finally {
            if (previous != null) repository.update(previous) else repository.signOut()
        }
    }

    private fun request(query: String, field: CatalogSearchField, source: BookSourcePreference) = DiscoverLoadRequest(
        query = query,
        field = field,
        languages = listOf(""),
        languageKey = "",
        sourcePreference = source,
        sortOrder = DiscoverSortOrder.MostPopular,
        reset = true,
        offset = 0,
        pageSize = 50,
    )
}
