package com.librivox.mobile.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.librivox.mobile.model.PlaybackSpeed

/** All presets share one row; larger type can scroll without shrinking labels or targets. */
@Composable
internal fun PlaybackSpeedPresets(
    selectedSpeed: PlaybackSpeed,
    onSpeedSelected: (PlaybackSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val selectedIndex = PlaybackSpeed.Presets.indexOf(selectedSpeed)
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val spacing = 4.dp
        val minimumLabelWidth = PlaybackSpeed.Presets.maxOf { preset ->
            with(density) {
                textMeasurer.measure(preset.label(), style = labelStyle).size.width.toDp()
            }
        }
        val balancedWidth = with(density) {
            ((maxWidth.roundToPx() - spacing.roundToPx() * (PlaybackSpeed.Presets.size - 1)) /
                PlaybackSpeed.Presets.size).coerceAtLeast(0).toDp()
        }
        val chipWidth = maxOf(48.dp, minimumLabelWidth + 12.dp, balancedWidth)

        LaunchedEffect(selectedIndex, chipWidth) {
            if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(PlaybackSpeed.Presets, key = { it.value }) { preset ->
                val selected = selectedSpeed == preset
                Surface(
                    selected = selected,
                    onClick = { onSpeedSelected(preset) },
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .width(chipWidth)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = preset.label(),
                            style = labelStyle,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
