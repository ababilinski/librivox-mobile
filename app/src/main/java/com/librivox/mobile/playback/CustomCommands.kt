package com.librivox.mobile.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object PlayerCustomCommands {
    const val SET_SPEED = "com.librivox.mobile.SET_SPEED"
    const val SET_TEMPORARY_SPEED = "com.librivox.mobile.SET_TEMPORARY_SPEED"
    const val ADJUST_SPEED = "com.librivox.mobile.ADJUST_SPEED"
    const val CYCLE_SPEED = "com.librivox.mobile.CYCLE_SPEED"
    const val SET_SLEEP_TIMER_DURATION = "com.librivox.mobile.SET_SLEEP_TIMER_DURATION"
    const val SET_SLEEP_TIMER_END_OF_CHAPTER = "com.librivox.mobile.SET_SLEEP_TIMER_END_OF_CHAPTER"
    const val CANCEL_SLEEP_TIMER = "com.librivox.mobile.CANCEL_SLEEP_TIMER"
    const val PLAY_QUEUE_ENTRY = "com.librivox.mobile.PLAY_QUEUE_ENTRY"
    const val PLAY_BOOK = "com.librivox.mobile.PLAY_BOOK"
    const val TOGGLE_LIKE = "com.librivox.mobile.TOGGLE_LIKE"
    const val TOGGLE_DISLIKE = "com.librivox.mobile.TOGGLE_DISLIKE"

    const val EXTRA_SPEED = "speed"
    const val EXTRA_SPEED_DELTA = "speedDelta"
    const val EXTRA_DURATION_MS = "durationMs"
    const val EXTRA_BOOK_ID = "bookId"
    const val EXTRA_CHAPTER_ID = "chapterId"

    fun setSpeed(speed: Float): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply { putFloat(EXTRA_SPEED, speed) }
        return SessionCommand(SET_SPEED, bundle) to bundle
    }

    fun setTemporarySpeed(speed: Float): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply { putFloat(EXTRA_SPEED, speed) }
        return SessionCommand(SET_TEMPORARY_SPEED, bundle) to bundle
    }

    fun adjustSpeed(delta: Float): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply { putFloat(EXTRA_SPEED_DELTA, delta) }
        return SessionCommand(ADJUST_SPEED, bundle) to bundle
    }

    fun cycleSpeed(): Pair<SessionCommand, Bundle> =
        SessionCommand(CYCLE_SPEED, Bundle.EMPTY) to Bundle.EMPTY

    fun setSleepTimerDuration(durationMs: Long): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply { putLong(EXTRA_DURATION_MS, durationMs) }
        return SessionCommand(SET_SLEEP_TIMER_DURATION, bundle) to bundle
    }

    fun setSleepTimerEndOfChapter(): Pair<SessionCommand, Bundle> =
        SessionCommand(SET_SLEEP_TIMER_END_OF_CHAPTER, Bundle.EMPTY) to Bundle.EMPTY

    fun cancelSleepTimer(): Pair<SessionCommand, Bundle> =
        SessionCommand(CANCEL_SLEEP_TIMER, Bundle.EMPTY) to Bundle.EMPTY

    fun playBook(bookId: String, chapterId: String? = null): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply {
            putString(EXTRA_BOOK_ID, bookId)
            if (chapterId != null) putString(EXTRA_CHAPTER_ID, chapterId)
        }
        return SessionCommand(PLAY_BOOK, bundle) to bundle
    }

    fun playQueueEntry(bookId: String, chapterId: String? = null): Pair<SessionCommand, Bundle> {
        val bundle = Bundle().apply {
            putString(EXTRA_BOOK_ID, bookId)
            if (chapterId != null) putString(EXTRA_CHAPTER_ID, chapterId)
        }
        return SessionCommand(PLAY_QUEUE_ENTRY, bundle) to bundle
    }

    fun toggleLike(): Pair<SessionCommand, Bundle> =
        SessionCommand(TOGGLE_LIKE, Bundle.EMPTY) to Bundle.EMPTY

    fun toggleDislike(): Pair<SessionCommand, Bundle> =
        SessionCommand(TOGGLE_DISLIKE, Bundle.EMPTY) to Bundle.EMPTY

    val ALL_COMMAND_ACTIONS: Set<String> = setOf(
        SET_SPEED,
        SET_TEMPORARY_SPEED,
        ADJUST_SPEED,
        CYCLE_SPEED,
        SET_SLEEP_TIMER_DURATION,
        SET_SLEEP_TIMER_END_OF_CHAPTER,
        CANCEL_SLEEP_TIMER,
        PLAY_QUEUE_ENTRY,
        PLAY_BOOK,
        TOGGLE_LIKE,
        TOGGLE_DISLIKE,
    )
}

object PlayerSessionExtras {
    const val KEY_SPEED = "extra_speed"
    const val KEY_SLEEP_REMAINING_MS = "extra_sleep_remaining_ms"
    const val KEY_SLEEP_END_OF_CHAPTER = "extra_sleep_end_of_chapter"
    const val KEY_SLEEP_TOTAL_MS = "extra_sleep_total_ms"
}
