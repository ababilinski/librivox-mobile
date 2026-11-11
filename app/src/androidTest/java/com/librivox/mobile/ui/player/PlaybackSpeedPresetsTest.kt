package com.librivox.mobile.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackSpeedPresetsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allSixPresetsFitOneRowAtPhoneContentWidthWithFullTouchTargets() {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.width(363.dp)) {
                    PlaybackSpeedPresets(PlaybackSpeed.Normal, onSpeedSelected = {})
                }
            }
        }

        val bounds = PlaybackSpeed.Presets.map { preset ->
            composeRule.onNodeWithText(preset.label())
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .fetchSemanticsNode().boundsInRoot
        }
        bounds.drop(1).forEach { assertEquals(bounds.first().top, it.top, 0.5f) }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        composeRule.onNodeWithText("1×").assertIsSelected()
    }

    @Test fun largeTextKeepsSelectedPresetVisibleAndAllPresetsReachableByScrolling() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                    var selected by remember { mutableStateOf(PlaybackSpeed(2f)) }
                    Box(Modifier.width(240.dp)) {
                        PlaybackSpeedPresets(selected, onSpeedSelected = { selected = it })
                    }
                }
            }
        }

        composeRule.onNodeWithText("2×").assertIsDisplayed().assertIsSelected()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        composeRule.onNodeWithText("0.25×")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsSelected()
    }
}
