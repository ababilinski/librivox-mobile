package com.librivox.mobile.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSheetLayoutTest {
    @Test
    fun miniPlayerHeightIncludesScrubBarWhenProgressIsShown() {
        assertEquals(104.dp, miniPlayerHeight(showProgressBar = true))
    }

    @Test
    fun miniPlayerHeightShrinksWhenProgressIsHidden() {
        assertEquals(72.dp, miniPlayerHeight(showProgressBar = false))
    }
}
