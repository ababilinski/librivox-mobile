package com.librivox.mobile.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NetworkStatusMonitorTest {
    @Test fun validCapabilitiesCallbackMakesStartupOnlineBeforeActiveNetworkQueryCatchesUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).setActiveNetworkInfo(null)
        val monitor = NetworkStatusMonitor(context)
        assertFalse(monitor.status.value.isOnline)
        val callback = ReflectionHelpers.getField<ConnectivityManager.NetworkCallback>(monitor, "callback")
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        callback.onCapabilitiesChanged(
            ShadowNetwork.newInstance(100),
            capabilities,
        )
        assertTrue(monitor.status.value.isOnline)
    }
}
