package com.librivox.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {
    @Test
    fun clamp_pinsBelowMinUp() {
        assertEquals(PlaybackSpeed.MIN, PlaybackSpeed.clamp(0.1f).value, 0.0001f)
    }

    @Test
    fun clamp_pinsAboveMaxDown() {
        assertEquals(PlaybackSpeed.MAX, PlaybackSpeed.clamp(5f).value, 0.0001f)
    }

    @Test
    fun normal_isOne() {
        assertEquals(1.0f, PlaybackSpeed.Normal.value, 0.0001f)
    }

    @Test
    fun notificationCycle_usesExpectedPresets() {
        assertEquals(
            listOf(0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f),
            PlaybackSpeed.NotificationCycle.map { it.value },
        )
    }

    @Test
    fun label_intLooksClean() {
        assertEquals("1×", PlaybackSpeed.Normal.label())
        assertEquals("2×", PlaybackSpeed(2f).label())
    }

    @Test
    fun label_fractionalIncludesDecimal() {
        assertEquals("1.5×", PlaybackSpeed(1.5f).label())
    }
}
