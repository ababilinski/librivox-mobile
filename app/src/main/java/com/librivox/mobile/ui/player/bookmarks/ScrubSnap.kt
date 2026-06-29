package com.librivox.mobile.ui.player.bookmarks

import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class ScrubSnapTargetKind {
    Bookmark,
    PreScrubPosition,
}

internal data class ScrubSnapTarget(
    val positionMs: Long,
    val kind: ScrubSnapTargetKind,
)

internal data class ScrubSnapResult(
    val positionMs: Long,
    val target: ScrubSnapTarget?,
)

internal const val PLAYHEAD_BOOKMARK_TOLERANCE_MS = 5_000L

internal fun scrubSnapThresholdMs(durationMs: Long): Long {
    val proportional = (durationMs.coerceAtLeast(0L).toDouble() * SCRUB_SNAP_THRESHOLD_FRACTION).roundToLong()
    return maxOf(SCRUB_SNAP_MIN_MS, proportional).coerceAtMost(SCRUB_SNAP_MAX_MS)
}

internal fun nearestBookmarkPositionAtPlayhead(
    positionMs: Long,
    bookmarkPositionsMs: List<Long>,
    toleranceMs: Long = PLAYHEAD_BOOKMARK_TOLERANCE_MS,
): Long? {
    val safeToleranceMs = toleranceMs.coerceAtLeast(0L)
    return bookmarkPositionsMs
        .distinct()
        .minWithOrNull(
            compareBy<Long> { abs(it - positionMs) }
                .thenBy { it },
        )
        ?.takeIf { abs(it - positionMs) <= safeToleranceMs }
}

internal fun snapScrubPosition(
    rawPositionMs: Long,
    durationMs: Long,
    bookmarkPositionsMs: List<Long>,
    preScrubPositionMs: Long?,
): ScrubSnapResult {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val clampedPositionMs = rawPositionMs.coerceIn(0L, safeDurationMs)
    if (safeDurationMs == 0L) {
        return ScrubSnapResult(positionMs = 0L, target = null)
    }

    val targets = buildList {
        bookmarkPositionsMs
            .map { it.coerceIn(0L, safeDurationMs) }
            .distinct()
            .forEach { positionMs ->
                add(ScrubSnapTarget(positionMs, ScrubSnapTargetKind.Bookmark))
            }
        preScrubPositionMs?.let {
            add(
                ScrubSnapTarget(
                    positionMs = it.coerceIn(0L, safeDurationMs),
                    kind = ScrubSnapTargetKind.PreScrubPosition,
                ),
            )
        }
    }
    if (targets.isEmpty()) {
        return ScrubSnapResult(positionMs = clampedPositionMs, target = null)
    }

    val thresholdMs = scrubSnapThresholdMs(safeDurationMs)
    val nearest = targets.minWithOrNull(
        compareBy<ScrubSnapTarget> { abs(it.positionMs - clampedPositionMs) }
            .thenBy { if (it.kind == ScrubSnapTargetKind.Bookmark) 0 else 1 }
            .thenBy { it.positionMs },
    )
    return if (nearest != null && abs(nearest.positionMs - clampedPositionMs) <= thresholdMs) {
        ScrubSnapResult(positionMs = nearest.positionMs, target = nearest)
    } else {
        ScrubSnapResult(positionMs = clampedPositionMs, target = null)
    }
}

private const val SCRUB_SNAP_THRESHOLD_FRACTION = 0.02
private const val SCRUB_SNAP_MIN_MS = 3_000L
private const val SCRUB_SNAP_MAX_MS = 12_000L
