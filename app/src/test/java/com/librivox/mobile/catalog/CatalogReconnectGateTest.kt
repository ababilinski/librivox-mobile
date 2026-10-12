package com.librivox.mobile.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogReconnectGateTest {
    @Test
    fun duplicateOnlineSignalsProduceExactlyOneReconnectRetry() {
        val gate = CatalogReconnectGate(initiallyOnline = true)

        assertEquals(CatalogConnectivityTransition.None, gate.update(isOnline = true))
        assertEquals(CatalogConnectivityTransition.WentOffline, gate.update(isOnline = false))
        assertEquals(CatalogConnectivityTransition.None, gate.update(isOnline = false))
        assertEquals(CatalogConnectivityTransition.Reconnected, gate.update(isOnline = true))
        assertEquals(CatalogConnectivityTransition.None, gate.update(isOnline = true))
    }

    @Test
    fun eachDistinctOfflineOnlineCycleAllowsOneRetry() {
        val gate = CatalogReconnectGate(initiallyOnline = false)

        assertEquals(CatalogConnectivityTransition.Reconnected, gate.update(true))
        assertEquals(CatalogConnectivityTransition.None, gate.update(true))
        assertEquals(CatalogConnectivityTransition.WentOffline, gate.update(false))
        assertEquals(CatalogConnectivityTransition.Reconnected, gate.update(true))
    }
}
