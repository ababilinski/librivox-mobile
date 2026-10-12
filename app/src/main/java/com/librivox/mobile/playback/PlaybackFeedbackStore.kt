package com.librivox.mobile.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackFeedback(val preferenceValue: String) {
    None("none"),
    Liked("liked"),
    Disliked("disliked");

    companion object {
        fun fromPreference(value: String?): PlaybackFeedback =
            entries.firstOrNull { it.preferenceValue == value } ?: None
    }
}

class PlaybackFeedbackStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun feedbackFor(
        bookId: String?,
        chapterId: String?,
        scope: FeedbackControlScope = FeedbackControlScope.Chapter,
    ): PlaybackFeedback {
        val key = playbackFeedbackPreferenceKey(bookId, chapterId, scope) ?: return PlaybackFeedback.None
        return PlaybackFeedback.fromPreference(preferences.getString(key, PlaybackFeedback.None.preferenceValue))
    }

    fun hasLikedFeedback(bookId: String?): Boolean {
        if (bookId.isNullOrBlank()) return false
        val bookKey = playbackFeedbackBookKey(bookId)
        val chapterPrefix = playbackFeedbackChapterKeyPrefix(bookId)
        return preferences.all.any { (key, value) ->
            (key == bookKey || key.startsWith(chapterPrefix)) &&
                value == PlaybackFeedback.Liked.preferenceValue
        }
    }

    fun toggleFeedback(
        bookId: String?,
        chapterId: String?,
        target: PlaybackFeedback,
        scope: FeedbackControlScope = FeedbackControlScope.Chapter,
    ): PlaybackFeedback {
        val key = playbackFeedbackPreferenceKey(bookId, chapterId, scope) ?: return PlaybackFeedback.None
        val current = PlaybackFeedback.fromPreference(preferences.getString(key, PlaybackFeedback.None.preferenceValue))
        val next = toggledPlaybackFeedback(current, target)
        preferences.edit().apply {
            if (next == PlaybackFeedback.None) {
                remove(key)
            } else {
                putString(key, next.preferenceValue)
            }
        }.apply()
        _revision.update { it + 1 }
        return next
    }

    private companion object {
        const val PREFERENCES_NAME = "playback_feedback"
    }
}

internal fun toggledPlaybackFeedback(
    current: PlaybackFeedback,
    target: PlaybackFeedback,
): PlaybackFeedback =
    if (current == target) PlaybackFeedback.None else target

internal fun playbackFeedbackPreferenceKey(
    bookId: String?,
    chapterId: String?,
    scope: FeedbackControlScope,
): String? =
    when (scope) {
        FeedbackControlScope.Hidden -> null
        FeedbackControlScope.Book -> bookId
            ?.takeIf { it.isNotBlank() }
            ?.let(::playbackFeedbackBookKey)
        FeedbackControlScope.Chapter -> {
            if (bookId.isNullOrBlank() || chapterId.isNullOrBlank()) {
                null
            } else {
                "${playbackFeedbackChapterKeyPrefix(bookId)}$chapterId"
            }
        }
    }

internal fun playbackFeedbackBookKey(bookId: String): String = "feedback_book::$bookId"

internal fun playbackFeedbackChapterKeyPrefix(bookId: String): String = "feedback::$bookId::"
