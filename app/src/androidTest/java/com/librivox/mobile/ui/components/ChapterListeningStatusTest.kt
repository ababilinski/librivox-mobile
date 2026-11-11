package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import org.junit.Rule
import org.junit.Test

class ChapterListeningStatusTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun pausedProgressExposesExactRangeAndWrapsAtLargeTextSize() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                    Column(Modifier.width(200.dp)) {
                        ChapterListeningStatus(
                            ChapterListeningProgress(ChapterListeningState.Paused, 15_000L, 60_000L),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Paused • 0:45 left").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Chapter listening progress")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.25f, 0f..1f),
                ),
            )
    }

    @Test fun unknownDurationKeepsElapsedTextWithoutAnIndeterminateOrGuessedRange() {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                ChapterListeningStatus(
                    ChapterListeningProgress(ChapterListeningState.LastListened, 15_000L, 0L),
                )
            }
        }

        composeRule.onNodeWithText("Last listened • 0:15 listened • Duration unknown").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Chapter listening progress").assertDoesNotExist()
    }

    @Test fun completionIsExplicitTextAndAFullAccessibleRange() {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                ChapterListeningStatus(
                    ChapterListeningProgress(ChapterListeningState.Finished, 59_000L, 60_000L),
                )
            }
        }

        composeRule.onNodeWithText("Finished • 1:00 total").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Chapter listening progress")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(1f, 0f..1f),
                ),
            )
    }
}
