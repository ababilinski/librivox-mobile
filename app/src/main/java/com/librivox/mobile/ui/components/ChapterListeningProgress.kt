package com.librivox.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.ChapterProgressSnapshot

enum class ChapterListeningState {
    NotStarted,
    InProgress,
    LastListened,
    Paused,
    Playing,
    Finished,
}

/** Exact listening state, kept separate from download state and chapter attribution. */
data class ChapterListeningProgress(
    val state: ChapterListeningState,
    val positionMs: Long,
    val durationMs: Long,
    val isHighlighted: Boolean = state == ChapterListeningState.Playing ||
        state == ChapterListeningState.Paused || state == ChapterListeningState.LastListened,
) {
    val statusLabel: String?
        get() = when (state) {
            ChapterListeningState.NotStarted -> null
            ChapterListeningState.InProgress -> "In progress"
            ChapterListeningState.LastListened -> "Last listened"
            ChapterListeningState.Paused -> "Paused"
            ChapterListeningState.Playing -> "Now playing"
            ChapterListeningState.Finished -> "Finished"
        }

    val isFinished: Boolean get() = state == ChapterListeningState.Finished

    val progressFraction: Float?
        get() = when {
            durationMs <= 0L || state == ChapterListeningState.NotStarted -> null
            isFinished -> 1f
            else -> (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
        }

    val timingLabel: String?
        get() = when {
            durationMs > 0L && (isFinished || state == ChapterListeningState.NotStarted) ->
                "${formatHmsFromMs(durationMs)} total"
            durationMs > 0L -> "${formatHmsFromMs((durationMs - positionMs).coerceAtLeast(0L))} left"
            isFinished -> null
            positionMs > 0L -> "${formatHmsFromMs(positionMs)} listened • Duration unknown"
            else -> "Duration unknown"
        }
}

fun chapterListeningProgress(
    savedProgress: ChapterProgressSnapshot,
    chapterDurationMs: Long,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLastListened: Boolean,
    currentPositionMs: Long = 0L,
    currentDurationMs: Long = 0L,
): ChapterListeningProgress {
    val durationMs = when {
        isCurrent && currentDurationMs > 0L -> currentDurationMs
        savedProgress.durationMs > 0L -> savedProgress.durationMs
        else -> chapterDurationMs.coerceAtLeast(0L)
    }
    val positionMs = (if (isCurrent) currentPositionMs else savedProgress.positionMs)
        .coerceAtLeast(0L)
    // Completion is authoritative, including a manual mark during current playback.
    // Explicit replay clears it in ProgressStore before playback starts again.
    val state = when {
        savedProgress.isCompleted -> ChapterListeningState.Finished
        isCurrent && isPlaying -> ChapterListeningState.Playing
        isCurrent -> ChapterListeningState.Paused
        isLastListened -> ChapterListeningState.LastListened
        positionMs > 0L -> ChapterListeningState.InProgress
        else -> ChapterListeningState.NotStarted
    }
    return ChapterListeningProgress(
        state = state,
        positionMs = positionMs,
        durationMs = durationMs,
        isHighlighted = isCurrent || isLastListened,
    )
}

/** Read-only progress: the chapter row remains the only playback action. */
@Composable
fun ChapterListeningStatus(
    progress: ChapterListeningProgress,
    modifier: Modifier = Modifier,
) {
    val statusColor = if (progress.isHighlighted || progress.isFinished) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val icon = when (progress.state) {
                ChapterListeningState.Playing -> Icons.Filled.PlayArrow
                ChapterListeningState.Paused -> Icons.Filled.Pause
                ChapterListeningState.LastListened -> Icons.Filled.Restore
                ChapterListeningState.Finished -> Icons.Filled.CheckCircle
                else -> null
            }
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = listOfNotNull(progress.statusLabel, progress.timingLabel).joinToString(" • "),
                color = statusColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (progress.isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        progress.progressFraction?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .semantics { contentDescription = "Chapter listening progress" },
            )
        }
    }
}
