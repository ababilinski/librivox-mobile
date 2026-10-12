package com.librivox.mobile.playback

import androidx.media3.common.Player
import com.librivox.mobile.model.mediaId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ContinueListeningTransportTest {
    @Test
    fun onlyCompletedPlayRestoresDismissedBook() {
        val play = Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build()
        val mediaId = mediaId("book", "chapter")
        assertEquals("book", bookIdToRestoreAfterTransportInteraction(play, true, mediaId))
        assertNull(bookIdToRestoreAfterTransportInteraction(play, false, mediaId))
        assertNull(bookIdToRestoreAfterTransportInteraction(play, true, null))
    }

    @Test
    fun seekQueueAndPassivePlayerUpdatesCannotRestoreDismissedBook() {
        val mediaId = mediaId("book", "chapter")
        for (commands in listOf(
            Player.Commands.EMPTY,
            Player.Commands.Builder().add(Player.COMMAND_SEEK_TO_MEDIA_ITEM).build(),
            Player.Commands.Builder().add(Player.COMMAND_CHANGE_MEDIA_ITEMS).build(),
        )) {
            assertNull(bookIdToRestoreAfterTransportInteraction(commands, true, mediaId))
        }
    }
}
