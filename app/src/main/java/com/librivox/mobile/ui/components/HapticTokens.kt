package com.librivox.mobile.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Production haptic tokens — wraps [View.performHapticFeedback] with semantic
 * names matching the M3 Expressive plugin recipe (haptics.md).
 *
 * Reference apps (Rhythm, PixelPlayer) miss this layer — they route through a
 * Vibrator abstraction and never use SEGMENT_TICK / DRAG_START / CONFIRM,
 * which is one of the largest published-vs-prototype tells.
 */
class Haptics internal constructor(private val view: View) {

    fun tick() {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
    }

    fun dragStart() {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.DRAG_START
        } else {
            HapticFeedbackConstants.GESTURE_START
        }
        view.performHapticFeedback(constant)
    }

    fun gestureEnd() {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    fun confirm() {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun key() {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun toggle(checked: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            val constant = if (checked) {
                HapticFeedbackConstants.TOGGLE_ON
            } else {
                HapticFeedbackConstants.TOGGLE_OFF
            }
            view.performHapticFeedback(constant)
        } else {
            tick()
        }
    }

    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun reject() {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> {
    error("Haptics not provided. Wrap content in rememberHaptics().")
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return Haptics(view)
}
