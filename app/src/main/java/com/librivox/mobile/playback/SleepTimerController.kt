package com.librivox.mobile.playback

import android.util.Log
import androidx.media3.common.Player
import com.librivox.mobile.model.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the sleep-timer state machine inside the playback service. Runs a
 * coroutine ticking every TICK_MS, fades the player volume over the final
 * [FADE_OUT_MS], then pauses. Supports an end-of-chapter mode that defers
 * the pause to the next chapter boundary (signalled by [onChapterTransition]).
 */
class SleepTimerController(
    private val scope: CoroutineScope,
    private val player: Player,
    private val onStateChanged: (SleepTimerState) -> Unit = {},
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    fun start(durationMs: Long) {
        cancelInternal()
        val total = durationMs.coerceAtLeast(MIN_DURATION_MS)
        publish(SleepTimerState.Running(totalMs = total, remainingMs = total))
        tickerJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            val endsAt = startedAt + total
            var lastFadeRatio = 1f
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = (endsAt - now).coerceAtLeast(0L)
                publish(SleepTimerState.Running(totalMs = total, remainingMs = remaining))
                if (remaining <= 0L) {
                    pauseAndReset("timer reached zero")
                    break
                }
                if (remaining <= FADE_OUT_MS) {
                    val ratio = (remaining.toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
                    if (ratio != lastFadeRatio) {
                        player.volume = ratio
                        lastFadeRatio = ratio
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    fun startEndOfChapter() {
        cancelInternal()
        publish(SleepTimerState.EndOfChapter)
    }

    fun cancel() {
        cancelInternal()
        restoreVolume()
        publish(SleepTimerState.Idle)
    }

    fun onChapterTransition() {
        if (_state.value is SleepTimerState.EndOfChapter) {
            pauseAndReset("end of chapter")
        }
    }

    private fun pauseAndReset(reason: String) {
        Log.i(TAG, "Sleep timer firing: $reason")
        player.pause()
        restoreVolume()
        publish(SleepTimerState.Expired)
        publish(SleepTimerState.Idle)
        cancelInternal()
    }

    private fun cancelInternal() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun restoreVolume() {
        if (player.volume != 1f) {
            player.volume = 1f
        }
    }

    private fun publish(state: SleepTimerState) {
        _state.value = state
        onStateChanged(state)
    }

    companion object {
        const val FADE_OUT_MS: Long = 10_000L
        private const val TICK_MS: Long = 500L
        private const val MIN_DURATION_MS: Long = 1_000L
        private const val TAG = "SleepTimerController"
    }
}
