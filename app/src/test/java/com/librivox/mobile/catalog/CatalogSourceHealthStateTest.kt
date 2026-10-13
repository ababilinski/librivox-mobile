package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSourceHealthStateTest {

    @Test
    fun defaults_allSourcesUnknownButUsable() {
        val state = CatalogSourceHealthState()

        CatalogSourcePreference.ActiveSources.forEach { source ->
            assertEquals(CatalogSourceHealth.Unknown, state.statusFor(source).state)
            assertTrue(state.isAvailable(source))
        }
    }

    @Test
    fun availablePreference_filtersUnavailableSelectedSources() {
        val state = CatalogSourceHealthState()
            .markUnavailable(CatalogSourcePreference.Lit2Go, "Timeout")

        assertEquals(
            BookSourcePreference.LibriVoxAndWolneLektury,
            state.availablePreference(BookSourcePreference.All),
        )
        assertEquals(
            listOf(CatalogSourcePreference.Lit2Go),
            state.unavailableSelectedSources(BookSourcePreference.All),
        )
    }

    @Test
    fun availablePreference_returnsNoneWhenAllSelectedSourcesUnavailable() {
        val state = CatalogSourceHealthState()
            .markUnavailable(CatalogSourcePreference.LibriVox, "Offline")

        assertEquals(
            BookSourcePreference.None,
            state.availablePreference(BookSourcePreference.LibriVoxOnly),
        )
    }

    @Test
    fun markAvailable_restoresSourceAfterFailure() {
        val state = CatalogSourceHealthState()
            .markUnavailable(CatalogSourcePreference.Lit2Go, "HTTP 503")
            .markAvailable(CatalogSourcePreference.Lit2Go)

        assertTrue(state.isAvailable(CatalogSourcePreference.Lit2Go))
        assertEquals(
            CatalogSourceHealth.Available,
            state.statusFor(CatalogSourcePreference.Lit2Go).state,
        )
        assertFalse(state.statusFor(CatalogSourcePreference.Lit2Go).message?.isNotBlank() == true)
    }
}
