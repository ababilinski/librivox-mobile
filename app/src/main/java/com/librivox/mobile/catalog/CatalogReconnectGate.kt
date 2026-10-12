package com.librivox.mobile.catalog

enum class CatalogConnectivityTransition {
    None,
    WentOffline,
    Reconnected,
}

/** Ensures one automatic retry owner and one retry per offline-to-online transition. */
class CatalogReconnectGate(initiallyOnline: Boolean) {
    private var wasOnline: Boolean = initiallyOnline

    @Synchronized
    fun update(isOnline: Boolean): CatalogConnectivityTransition {
        if (isOnline == wasOnline) return CatalogConnectivityTransition.None
        val transition = if (isOnline) {
            CatalogConnectivityTransition.Reconnected
        } else {
            CatalogConnectivityTransition.WentOffline
        }
        wasOnline = isOnline
        return transition
    }
}
