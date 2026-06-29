package com.librivox.mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val OfflinePublishDelayMillis = 3_500L

data class NetworkStatus(
    val isOnline: Boolean,
)

class NetworkStatusMonitor(context: Context) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(
        NetworkStatus(connectivityManager.hasValidatedInternet()),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var offlineJob: Job? = null

    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh()
        }

        override fun onLost(network: Network) {
            refresh()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            refresh()
        }
    }

    init {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    private fun refresh() {
        if (connectivityManager.hasValidatedInternet()) {
            offlineJob?.cancel()
            _status.value = NetworkStatus(isOnline = true)
            return
        }
        offlineJob?.cancel()
        offlineJob = scope.launch {
            delay(OfflinePublishDelayMillis)
            if (!connectivityManager.hasValidatedInternet()) {
                _status.value = NetworkStatus(isOnline = false)
            }
        }
    }

    private fun ConnectivityManager.hasValidatedInternet(): Boolean {
        val active = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
