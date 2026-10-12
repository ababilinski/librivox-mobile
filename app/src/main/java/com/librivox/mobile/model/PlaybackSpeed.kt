package com.librivox.mobile.model

import kotlin.math.roundToInt

@JvmInline
value class PlaybackSpeed(val value: Float) {
    init {
        require(value in MIN..MAX) { "Speed $value outside [$MIN, $MAX]" }
    }

    fun label(): String =
        if (value == value.toInt().toFloat()) {
            "${value.toInt()}×"
        } else {
            "${(value * 100).roundToInt() / 100f}×"
        }

    companion object {
        const val MIN: Float = 0.25f
        const val MAX: Float = 2.0f
        val Normal: PlaybackSpeed = PlaybackSpeed(1.0f)

        val Presets: List<PlaybackSpeed> = listOf(
            0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f,
        ).map(::PlaybackSpeed)
        val NotificationCycle: List<PlaybackSpeed> = Presets

        fun clamp(value: Float): PlaybackSpeed = PlaybackSpeed(value.coerceIn(MIN, MAX))
    }
}
