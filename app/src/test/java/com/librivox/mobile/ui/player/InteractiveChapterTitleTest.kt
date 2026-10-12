package com.librivox.mobile.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveChapterTitleTest {
    @Test fun delayedFlingRestartsTheEntireAutomaticScrollPause() = runTest {
        val moving = MutableStateFlow(false)
        var resumed = false
        backgroundScope.launch {
            awaitContinuousTitleIdle(moving, 3_000)
            resumed = true
        }
        runCurrent()
        advanceTimeBy(1_000)
        moving.value = true
        runCurrent()
        advanceTimeBy(4_000)
        assertFalse(resumed)
        moving.value = false
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertFalse(resumed)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(resumed)
    }
}
