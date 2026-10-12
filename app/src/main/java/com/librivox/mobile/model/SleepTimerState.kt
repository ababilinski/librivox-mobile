package com.librivox.mobile.model

sealed interface SleepTimerState {
    object Idle : SleepTimerState

    data class Running(
        val totalMs: Long,
        val remainingMs: Long,
    ) : SleepTimerState

    object EndOfChapter : SleepTimerState

    object Expired : SleepTimerState
}

enum class SleepTimerPreset(val label: String, val durationMs: Long) {
    FiveMinutes("5 min", 5 * 60_000L),
    TenMinutes("10 min", 10 * 60_000L),
    FifteenMinutes("15 min", 15 * 60_000L),
    ThirtyMinutes("30 min", 30 * 60_000L),
    FortyFiveMinutes("45 min", 45 * 60_000L),
    SixtyMinutes("1 hour", 60 * 60_000L);
}
