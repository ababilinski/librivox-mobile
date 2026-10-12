package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.ChapterPresentation
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChapterSupportingInfoTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun longNamesAtDoubleFontScaleKeepAttributionInOrder() {
        val author = "Bolesław Prus and another author with a long name"
        val reader = "Albert Osik and another reader with a long name"
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AudioPlayerTheme(darkTheme = true, dynamicColor = false) {
                    Column(Modifier.width(320.dp)) {
                        Text("Pamiętnik starego subiekta")
                        ChapterSupportingInfo(
                            ChapterPresentation("Pamiętnik starego subiekta", "Tom II, Rozdział I", author, reader),
                            "Chapter 1 • 10:00 total",
                        )
                    }
                }
            }
        }
        val authorBounds = composeRule.onNodeWithText(author).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val readerBounds = composeRule.onNodeWithText("Read by $reader").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val metadataBounds = composeRule.onNodeWithText("Tom II, Rozdział I • Chapter 1 • 10:00 total").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(authorBounds.bottom <= readerBounds.top)
        assertTrue(readerBounds.bottom <= metadataBounds.top)
    }
}
