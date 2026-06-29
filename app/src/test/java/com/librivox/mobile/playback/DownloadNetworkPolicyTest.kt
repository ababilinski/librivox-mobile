package com.librivox.mobile.playback

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNetworkPolicyTest {
    @Test
    fun default_isWifiOnly() {
        assertEquals(DownloadNetworkPolicy.WifiOnly, DownloadNetworkPolicy.fromPreference(null))
    }

    @Test
    fun manual_doesNotAutoDownloadButAllowsExplicitConnectedWork() {
        val policy = DownloadNetworkPolicy.Manual

        assertFalse(policy.autoDownloadsEnabled)
        assertEquals(NetworkType.CONNECTED, policy.audioWorkNetworkType)
        assertEquals(NetworkType.CONNECTED, policy.bookInfoWorkNetworkType)
    }

    @Test
    fun wifiOnly_audioDownloadsOnUnmeteredNetworkButBookInfoUsesAnyConnection() {
        val policy = DownloadNetworkPolicy.WifiOnly

        assertTrue(policy.autoDownloadsEnabled)
        assertEquals(NetworkType.UNMETERED, policy.audioWorkNetworkType)
        assertEquals(NetworkType.CONNECTED, policy.bookInfoWorkNetworkType)
    }

    @Test
    fun wifiAndData_audioAndBookInfoUseAnyConnection() {
        val policy = DownloadNetworkPolicy.WifiAndData

        assertTrue(policy.autoDownloadsEnabled)
        assertEquals(NetworkType.CONNECTED, policy.audioWorkNetworkType)
        assertEquals(NetworkType.CONNECTED, policy.bookInfoWorkNetworkType)
    }

    @Test
    fun autoDownloadMode_tracksChapterLookAheadSeparately() {
        assertEquals(AutoDownloadMode.Off, AutoDownloadMode.fromPreference(null))
        assertEquals(0, AutoDownloadMode.Off.lookAheadCount)
        assertEquals(1, AutoDownloadMode.CurrentChapter.lookAheadCount)
        assertEquals(4, AutoDownloadMode.CurrentAndNextFew.lookAheadCount)
    }
}
