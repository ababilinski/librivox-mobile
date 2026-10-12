package com.librivox.mobile.model

import androidx.media3.common.C
import java.util.Locale
import kotlin.math.max

fun formatDuration(positionMs: Long): String {
    val totalSeconds = max(0L, positionMs) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun normalizedDurationMs(durationMs: Long): Long =
    if (durationMs == C.TIME_UNSET || durationMs <= 0L) 0L else durationMs
