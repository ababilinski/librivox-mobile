package com.librivox.mobile.ui.components

import java.util.Locale

fun formatHmsFromMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatHmsFromSeconds(seconds: Long): String = formatHmsFromMs(seconds.coerceAtLeast(0L) * 1000)
