package com.librivox.mobile.ui.cast

import android.media.AudioDeviceInfo
import android.media.MediaRoute2Info
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioOutputRouteRepositoryTest {
    @Test
    fun audioOutputRouteKindForType_hidesBuiltInSpeakerRoutes() {
        assertNull(audioOutputRouteKindForType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertNull(audioOutputRouteKindForType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(audioOutputRouteKindForType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE))
    }

    @Test
    fun audioOutputRouteKindForType_detectsExternalDeviceRoutes() {
        assertEquals(
            AudioOutputRouteKind.Bluetooth,
            audioOutputRouteKindForType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
        )
        assertEquals(
            AudioOutputRouteKind.WiredHeadphones,
            audioOutputRouteKindForType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES),
        )
        assertEquals(
            AudioOutputRouteKind.Usb,
            audioOutputRouteKindForType(AudioDeviceInfo.TYPE_USB_HEADSET),
        )
        assertEquals(
            AudioOutputRouteKind.Remote,
            audioOutputRouteKindForType(MediaRoute2Info.TYPE_REMOTE_SPEAKER),
        )
    }
}
