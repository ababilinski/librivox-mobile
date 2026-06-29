package com.librivox.mobile.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavySliderMarkerTest {
    @Test
    fun bookmarkMarkerActivation_isInactiveWithoutScrubInteraction() {
        assertEquals(
            0f,
            bookmarkMarkerActivation(
                markerFraction = 0.5f,
                interactionFraction = null,
                activationRadiusFraction = 0.1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun bookmarkMarkerActivation_isFullWhenScrubbingOverMarker() {
        assertEquals(
            1f,
            bookmarkMarkerActivation(
                markerFraction = 0.5f,
                interactionFraction = 0.5f,
                activationRadiusFraction = 0.1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun bookmarkMarkerActivation_fadesInNearMarker() {
        val activation = bookmarkMarkerActivation(
            markerFraction = 0.5f,
            interactionFraction = 0.46f,
            activationRadiusFraction = 0.1f,
        )

        assertTrue(activation in 0f..1f)
        assertTrue(activation > 0f)
    }

    @Test
    fun bookmarkMarkerActivation_isInactiveOutsideRadius() {
        assertEquals(
            0f,
            bookmarkMarkerActivation(
                markerFraction = 0.5f,
                interactionFraction = 0.2f,
                activationRadiusFraction = 0.1f,
            ),
            0.0001f,
        )
    }
}
