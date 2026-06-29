package com.librivox.mobile.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSettingsDefaultsTest {
    @Test
    fun miniPlayerSkipModeDefaultsToChapter() {
        assertEquals(MiniPlayerSkipMode.Chapter, MiniPlayerSkipMode.fromPreference(null))
        assertEquals(MiniPlayerSkipMode.Chapter, MiniPlayerSkipMode.fromPreference("legacy"))
    }

    @Test
    fun miniPlayerSkipModeReadsTenSecondPreference() {
        assertEquals(
            MiniPlayerSkipMode.TenSeconds,
            MiniPlayerSkipMode.fromPreference(MiniPlayerSkipMode.TenSeconds.preferenceValue),
        )
    }

    @Test
    fun feedbackControlScopeDefaultsToChapter() {
        assertEquals(FeedbackControlScope.Chapter, FeedbackControlScope.fromPreference(null))
        assertEquals(FeedbackControlScope.Chapter, FeedbackControlScope.fromPreference("legacy"))
    }

    @Test
    fun feedbackControlScopeReadsBookAndHiddenPreferences() {
        assertEquals(
            FeedbackControlScope.Book,
            FeedbackControlScope.fromPreference(FeedbackControlScope.Book.preferenceValue),
        )
        assertEquals(
            FeedbackControlScope.Hidden,
            FeedbackControlScope.fromPreference(FeedbackControlScope.Hidden.preferenceValue),
        )
    }

    @Test
    fun automaticSearchCachingDefaultsToEnabled() {
        assertTrue(PlaybackSettingsStore.DEFAULT_AUTOMATIC_SEARCH_CACHING_ENABLED)
    }

    @Test
    fun playbackAudioContentTypeAlwaysUsesSpeech() {
        assertEquals(PlaybackAudioContentType.Speech, PlaybackAudioContentType.fromPreference(null))
        assertEquals(PlaybackAudioContentType.Speech, PlaybackAudioContentType.fromPreference("speech"))
        assertEquals(PlaybackAudioContentType.Speech, PlaybackAudioContentType.fromPreference("music"))
        assertEquals(PlaybackAudioContentType.Speech, PlaybackAudioContentType.fromPreference("legacy"))
        assertEquals(C.AUDIO_CONTENT_TYPE_SPEECH, PlaybackAudioContentType.Default.media3ContentType)
    }
}
