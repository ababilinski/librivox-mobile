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

        CatalogSourcePreference.entries.forEach { source ->
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
            .markUnavailable(CatalogSourcePreference.Gutendex, "HTTP 503")
            .markAvailable(CatalogSourcePreference.Gutendex)

        assertTrue(state.isAvailable(CatalogSourcePreference.Gutendex))
        assertEquals(
            CatalogSourceHealth.Available,
            state.statusFor(CatalogSourcePreference.Gutendex).state,
        )
        assertFalse(state.statusFor(CatalogSourcePreference.Gutendex).message?.isNotBlank() == true)
    }
}
