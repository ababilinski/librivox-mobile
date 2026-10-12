package com.librivox.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookCastMediaItemConverterTest {
    @Test
    fun castSubtitle_includesBookTitleAndAuthor() {
        assertEquals(
            "Pride and Prejudice • Jane Austen",
            castSubtitle("Pride and Prejudice", "Jane Austen"),
        )
    }

    @Test
    fun castSubtitle_omitsBlankParts() {
        assertEquals("Jane Austen", castSubtitle("", " Jane Austen "))
        assertEquals("Pride and Prejudice", castSubtitle(" Pride and Prejudice ", ""))
    }

    @Test
    fun selectCastUrl_remoteOnly_usesMetadataUrl() {
        assertEquals(
            "https://example.com/chapter.mp3",
            selectCastUrl(
                localCastUrl = null,
                metadataCastUrl = "https://example.com/chapter.mp3",
            ),
        )
    }

    @Test
    fun selectCastUrl_localUrl_preferredOverRemoteMetadata() {
        assertEquals(
            "http://192.168.1.45:8080/audio/token",
            selectCastUrl(
                localCastUrl = "http://192.168.1.45:8080/audio/token",
                metadataCastUrl = "https://example.com/chapter.mp3",
            ),
        )
    }

    @Test
    fun selectCastUrl_localFailure_fallsBackToRemoteMetadata() {
        assertEquals(
            "https://example.com/chapter.mp3",
            selectCastUrl(
                localCastUrl = null,
                metadataCastUrl = "https://example.com/chapter.mp3",
            ),
        )
    }

    @Test
    fun selectCastUrl_localFailureWithoutRemote_returnsNull() {
        assertNull(
            selectCastUrl(
                localCastUrl = null,
                metadataCastUrl = null,
            ),
        )
    }

    @Test
    fun selectCastUrl_rejectsNonReceiverUrls() {
        assertNull(
            selectCastUrl(
                localCastUrl = "file:///data/user/0/book.mp3",
                metadataCastUrl = "asset:/audiobooks/book.mp3",
            ),
        )
    }
}
