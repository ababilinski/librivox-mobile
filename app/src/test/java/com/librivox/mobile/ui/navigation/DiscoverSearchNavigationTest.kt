package com.librivox.mobile.ui.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import com.librivox.mobile.catalog.CatalogSearchField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DiscoverSearchNavigationTest {
    @Test fun authorSearchPushesAboveDetailAndBackRestoresEarlierDiscoverEntry() {
        Robolectric.buildActivity(ComponentActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val nav = NavHostController(activity).apply {
                setLifecycleOwner(activity)
                setViewModelStore(ViewModelStore())
            }
            val navigator = EntryNavigator()
            nav.navigatorProvider.addNavigator(navigator)
            nav.graph = NavGraph(NavGraphNavigator(nav.navigatorProvider)).apply {
                addDestination(navigator.createDestination().apply { route = Routes.HOME })
                addDestination(navigator.createDestination().apply { route = Routes.DISCOVER })
                addDestination(navigator.createDestination().apply { route = "book/test" })
                setStartDestination(Routes.HOME)
            }
            nav.openDiscoverSearch("Original search", CatalogSearchField.Title)
            val originalEntry = requireNotNull(nav.currentBackStackEntry)
            nav.navigate("book/test")
            nav.openDiscoverSearch("Bolesław Prus", CatalogSearchField.Author, "Polish")
            val authorEntry = requireNotNull(nav.currentBackStackEntry)
            assertNotEquals(originalEntry.id, authorEntry.id)
            assertEquals("Bolesław Prus", authorEntry.savedStateHandle.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest()?.query)
            nav.openDiscoverSearch("Bolesław Prus", CatalogSearchField.Author, "Polish")
            assertEquals(authorEntry.id, nav.currentBackStackEntry?.id)
            nav.popBackStack()
            assertEquals("book/test", nav.currentDestination?.route)
            nav.popBackStack()
            assertEquals(originalEntry.id, nav.currentBackStackEntry?.id)
            assertEquals("Original search", originalEntry.savedStateHandle.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest()?.query)
        }
    }

    @Test fun oldDestinationCannotConsumeNewDestinationsSearch() {
        val oldEntry = SavedStateHandle()
        val newEntry = SavedStateHandle()
        val old = oldEntry.requestDiscoverSearch("Prus", CatalogSearchField.Author, "Polish")
        val current = newEntry.requestDiscoverSearch("Prus", CatalogSearchField.Author, "Polish")
        oldEntry.acknowledgeDiscoverSearch(old.id)
        assertNull(oldEntry.get<Bundle>(DiscoverSearchRequestKey))
        assertEquals(current, newEntry.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest())
    }

    @Test fun repeatedAuthorIsNewRequestAndOlderAcknowledgmentCannotClearIt() {
        val entry = SavedStateHandle()
        val first = entry.requestDiscoverSearch("Prus", CatalogSearchField.Author, "Polish")
        val second = entry.requestDiscoverSearch("Prus", CatalogSearchField.Author, "Polish")
        assertNotEquals(first.id, second.id)
        entry.acknowledgeDiscoverSearch(first.id)
        assertEquals(second, entry.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest())
        entry.acknowledgeDiscoverSearch(second.id)
        assertNull(entry.get<Bundle>(DiscoverSearchRequestKey))
        val third = entry.requestDiscoverSearch("Dickens", CatalogSearchField.Author, "English")
        assertEquals(third, entry.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest())
    }

    @Test fun requestRoundTripPreservesUnicodeAndDoesNotChangeSavedSearch() {
        val entry = SavedStateHandle(mapOf("saved.query" to "Lalka"))
        val request = entry.requestDiscoverSearch("Bolesław Prus", CatalogSearchField.Author, "Polish")
        val restored = SavedStateHandle(mapOf(DiscoverSearchRequestKey to entry.get<Bundle>(DiscoverSearchRequestKey)))
        assertEquals(request, restored.get<Bundle>(DiscoverSearchRequestKey)?.toDiscoverSearchRequest())
        entry.acknowledgeDiscoverSearch(request.id)
        assertEquals("Lalka", entry.get<String>("saved.query"))
    }
}

@Navigator.Name("discover-test")
private class EntryNavigator : Navigator<NavDestination>() {
    override fun createDestination() = NavDestination(this)

    override fun navigate(entries: List<NavBackStackEntry>, navOptions: NavOptions?, navigatorExtras: Extras?) {
        entries.forEach(state::push)
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.pop(popUpTo, savedState)
    }
}
