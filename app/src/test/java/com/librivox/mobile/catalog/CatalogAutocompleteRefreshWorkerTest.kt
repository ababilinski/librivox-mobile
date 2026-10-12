package com.librivox.mobile.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAutocompleteRefreshWorkerTest {
    @Test
    fun allSourceFailureRetries() {
        assertTrue(catalogRefreshShouldRetry(fetchedAny = false, failedAny = true))
    }

    @Test
    fun partialFailureKeepsProgressAndLetsNextPeriodicRunContinue() {
        assertFalse(catalogRefreshShouldRetry(fetchedAny = true, failedAny = true))
    }

    @Test
    fun successfulOrEmptyCompletedRunDoesNotRetry() {
        assertFalse(catalogRefreshShouldRetry(fetchedAny = true, failedAny = false))
        assertFalse(catalogRefreshShouldRetry(fetchedAny = false, failedAny = false))
    }
}
