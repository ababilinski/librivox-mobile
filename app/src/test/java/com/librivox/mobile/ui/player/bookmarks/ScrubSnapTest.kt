package com.librivox.mobile.ui.player.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubSnapTest {
    @Test
    fun snapsToNearestBookmarkWithinThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 119_000,
            durationMs = 600_000,
            bookmarkPositionsMs = listOf(120_000),
            preScrubPositionMs = null,
        )

        assertEquals(120_000L, result.positionMs)
        assertEquals(ScrubSnapTargetKind.Bookmark, result.target?.kind)
    }

    @Test
    fun snapsToBookmarkWithinVisualActivationThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 109_000,
            durationMs = 600_000,
            bookmarkPositionsMs = listOf(120_000),
            preScrubPositionMs = null,
        )

        assertEquals(120_000L, result.positionMs)
        assertEquals(ScrubSnapTargetKind.Bookmark, result.target?.kind)
    }

    @Test
    fun doesNotSnapPastVisualActivationThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 107_000,
            durationMs = 600_000,
            bookmarkPositionsMs = listOf(120_000),
            preScrubPositionMs = null,
        )

        assertEquals(107_000L, result.positionMs)
        assertNull(result.target)
    }

    @Test
    fun snapsToPreScrubPositionWithinThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 302_000,
            durationMs = 600_000,
            bookmarkPositionsMs = emptyList(),
            preScrubPositionMs = 300_000,
        )

        assertEquals(300_000L, result.positionMs)
        assertEquals(ScrubSnapTargetKind.PreScrubPosition, result.target?.kind)
    }

    @Test
    fun snapsToPreScrubPositionWithinVisualActivationThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 311_000,
            durationMs = 600_000,
            bookmarkPositionsMs = emptyList(),
            preScrubPositionMs = 300_000,
        )

        assertEquals(300_000L, result.positionMs)
        assertEquals(ScrubSnapTargetKind.PreScrubPosition, result.target?.kind)
    }

    @Test
    fun usesBoundedVisualActivationThresholds() {
        assertEquals(3_000L, scrubSnapThresholdMs(20_000))
        assertEquals(12_000L, scrubSnapThresholdMs(600_000))
        assertEquals(12_000L, scrubSnapThresholdMs(3_600_000))
    }

    @Test
    fun choosesNearestTargetWhenMultipleAreClose() {
        val result = snapScrubPosition(
            rawPositionMs = 101_000,
            durationMs = 600_000,
            bookmarkPositionsMs = listOf(100_000),
            preScrubPositionMs = 103_000,
        )

        assertEquals(100_000L, result.positionMs)
        assertEquals(ScrubSnapTargetKind.Bookmark, result.target?.kind)
    }

    @Test
    fun doesNotSnapOutsideThreshold() {
        val result = snapScrubPosition(
            rawPositionMs = 200_000,
            durationMs = 600_000,
            bookmarkPositionsMs = listOf(120_000),
            preScrubPositionMs = 300_000,
        )

        assertEquals(200_000L, result.positionMs)
        assertNull(result.target)
    }

    @Test
    fun clampsToDurationRange() {
        assertEquals(
            0L,
            snapScrubPosition(
                rawPositionMs = -500,
                durationMs = 10_000,
                bookmarkPositionsMs = emptyList(),
                preScrubPositionMs = null,
            ).positionMs,
        )
        assertEquals(
            10_000L,
            snapScrubPosition(
                rawPositionMs = 12_000,
                durationMs = 10_000,
                bookmarkPositionsMs = emptyList(),
                preScrubPositionMs = null,
            ).positionMs,
        )
    }

    @Test
    fun findsNearestBookmarkAtPlayheadWithinTolerance() {
        val result = nearestBookmarkPositionAtPlayhead(
            positionMs = 120_600,
            bookmarkPositionsMs = listOf(100_000, 120_000, 121_000),
        )

        assertEquals(121_000L, result)
    }

    @Test
    fun findsBookmarkAtPlayheadWithinExpandedTolerance() {
        val result = nearestBookmarkPositionAtPlayhead(
            positionMs = 15_000,
            bookmarkPositionsMs = listOf(11_000),
        )

        assertEquals(11_000L, result)
    }

    @Test
    fun doesNotFindBookmarkAtPlayheadOutsideTolerance() {
        val result = nearestBookmarkPositionAtPlayhead(
            positionMs = 125_100,
            bookmarkPositionsMs = listOf(120_000),
        )

        assertNull(result)
    }

    @Test
    fun choosesEarlierBookmarkAtPlayheadWhenDistanceTies() {
        val result = nearestBookmarkPositionAtPlayhead(
            positionMs = 120_500,
            bookmarkPositionsMs = listOf(121_000, 120_000),
        )

        assertEquals(120_000L, result)
    }
}
