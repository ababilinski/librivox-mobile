package com.librivox.mobile.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerInteractionTest {
    @get:Rule val rule = createComposeRule()
    private val longTitle = "Tom II, Rozdział I — Pamiętnik starego subiekta — długi tytuł rozdziału"

    @Test fun titleDragMovesTextWithoutExpandingAndTapShowsAllLines() {
        rule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                InteractiveChapterTitle(longTitle, "one", Modifier.width(240.dp), automaticScrollEnabled = false)
            }
        }
        val title = rule.onNodeWithTag("now-playing-chapter-title")
        val collapsedHeight = title.fetchSemanticsNode().boundsInRoot.height
        title.performTouchInput { swipeLeft() }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        assertTrue(horizontalOffset() > 0f)
        title.performTouchInput { click() }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        assertTrue(title.fetchSemanticsNode().boundsInRoot.height > collapsedHeight)
        title.performTouchInput { click() }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test fun tappingDuringAutomaticTitleMovementExpandsImmediately() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                InteractiveChapterTitle(longTitle, "one", Modifier.width(240.dp))
            }
        }
        rule.mainClock.advanceTimeBy(1_600)
        val title = rule.onNodeWithTag("now-playing-chapter-title")
        val collapsedHeight = title.fetchSemanticsNode().boundsInRoot.height
        assertTrue("The regression must tap a moving ticker", horizontalOffset() > 0f)
        title.performTouchInput { click() }
        rule.mainClock.advanceTimeByFrame()
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        assertTrue(title.fetchSemanticsNode().boundsInRoot.height > collapsedHeight)
    }

    @Test fun tappingDuringManualTitleFlingExpandsInsteadOfOnlyStoppingTheFling() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                InteractiveChapterTitle(longTitle, "one", Modifier.width(240.dp))
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val title = rule.onNodeWithTag("now-playing-chapter-title")
        title.performTouchInput { swipeLeft(durationMillis = 100) }
        rule.mainClock.advanceTimeByFrame()
        title.performTouchInput { click() }
        rule.mainClock.advanceTimeByFrame()
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
    }

    @Test fun chapterIdentityChangeResetsBothExpansionAndManualScroll() {
        var identity by mutableStateOf("one")
        rule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                InteractiveChapterTitle(longTitle, identity, Modifier.width(240.dp), automaticScrollEnabled = false)
            }
        }
        val title = rule.onNodeWithTag("now-playing-chapter-title")
        title.performTouchInput { swipeLeft() }
        assertTrue(horizontalOffset() > 0f)
        title.performClick()
        rule.runOnIdle { identity = "two" }
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        assertEquals(0f, horizontalOffset())
    }

    @Test fun artworkSupportsBothDirectionsAndDoesNotStartAHoldDuringPaging() {
        val commits = mutableListOf<Float>()
        var holds = 0
        rule.setContent {
            Box(Modifier.size(240.dp).testTag("artwork").artworkPlaybackGestures(
                identity = "chapter", chapterSwipeEnabled = true,
                onDoubleTap = {}, onHoldStart = { holds++ }, onHoldEnd = {},
                onChapterDragStart = {}, onChapterDrag = {},
                onChapterDragEnd = { distance, _ -> commits.add(distance) },
                onChapterDragCancel = {},
            ))
        }
        rule.onNodeWithTag("artwork").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("artwork").performTouchInput { swipeRight() }
        rule.runOnIdle {
            assertEquals(2, commits.size)
            assertTrue(commits[0] < 0)
            assertTrue(commits[1] > 0)
            assertEquals(0, holds)
        }
    }

    @Test fun verticalArtworkSwipeScrollsTheBodyWithoutSwitchingChapter() {
        var chapterDrags = 0
        var observedScroll = 0
        rule.setContent {
            val scroll = rememberScrollState()
            observedScroll = scroll.value
            Column(Modifier.width(240.dp).height(300.dp).verticalScroll(scroll)) {
                Box(Modifier.size(240.dp).testTag("artwork").artworkPlaybackGestures(
                    identity = "chapter", chapterSwipeEnabled = true,
                    onDoubleTap = {}, onHoldStart = {}, onHoldEnd = {},
                    onChapterDragStart = { chapterDrags++ }, onChapterDrag = {},
                    onChapterDragEnd = { _, _ -> }, onChapterDragCancel = {},
                ))
                Box(Modifier.height(600.dp))
            }
        }
        rule.onNodeWithTag("artwork").performTouchInput { swipeUp() }
        rule.runOnIdle {
            assertTrue(observedScroll > 0)
            assertEquals(0, chapterDrags)
        }
    }

    private fun horizontalOffset(): Float = rule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
}
