package com.librivox.mobile.playback

import com.librivox.mobile.model.PlaybackSpeed
import com.librivox.mobile.model.SleepTimerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-shared state for things Media3 doesn't track natively (sleep timer,
 * effective playback speed). The playback service is the writer; UI ViewModels
 * are readers. Lives on the [Application] so it survives Activity recreation.
 */
class PlaybackBus {
    private val _sleepTimer = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private val _speed = MutableStateFlow(PlaybackSpeed.Normal)
    val speed: StateFlow<PlaybackSpeed> = _speed.asStateFlow()

    fun updateSleepTimer(state: SleepTimerState) {
        _sleepTimer.value = state
    }

    fun updateSpeed(speed: PlaybackSpeed) {
        _speed.value = speed
    }
}
