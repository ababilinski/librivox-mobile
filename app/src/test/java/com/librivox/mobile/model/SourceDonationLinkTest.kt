package com.librivox.mobile.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceDonationLinkTest {
    @Test
    fun donationLinks_includeSupportedSourceLinks() {
        val linksBySource = sourceDonationLinks.associateBy { it.source }

        assertEquals(
            "https://librivox.org/pages/donate-to-librivox/",
            linksBySource.getValue(BookSource.LibriVox).url,
        )
        assertEquals(
            "https://etc.usf.edu/lit2go/giving/",
            linksBySource.getValue(BookSource.Lit2Go).url,
        )
        assertEquals(
            setOf(BookSource.LibriVox, BookSource.Lit2Go),
            linksBySource.keys,
        )
    }

    @Test
    fun sourcesWithoutDonationLinks_doNotShowDonateButtons() {
        assertNull(BookSource.WolneLektury.donationLink())
        assertNull(BookSource.Gutendex.donationLink())
        assertNull(BookSource.LocalAsset.donationLink())
        assertNull(BookSource.CustomLocal.donationLink())
    }

    @Test
    fun bookDonationLink_usesBookSource() {
        val book = AudioBook(
            id = "librivox-test",
            title = "LibriVox Test",
            author = "Tester",
            source = BookSource.LibriVox,
        )

        assertEquals("Donate to LibriVox", book.donationLink()?.actionLabel)
    }
}
