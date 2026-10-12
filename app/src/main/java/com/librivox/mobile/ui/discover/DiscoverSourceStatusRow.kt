package com.librivox.mobile.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Immutable
internal data class DiscoverSourceStatusItem(
    val key: String,
    val label: String,
    val status: DiscoverSourceStatusPresentation,
)

@Immutable
internal sealed interface DiscoverSourceStatusPresentation {
    data class Loading(val interimCount: Int? = null) : DiscoverSourceStatusPresentation
    data class Ready(val count: Int? = null) : DiscoverSourceStatusPresentation
    data class Broken(
        val reason: String? = null,
        val searchIsLoading: Boolean = false,
        val resultCount: Int? = null,
    ) : DiscoverSourceStatusPresentation
}

/**
 * Passive status for sources included in the current search plan. Callers intentionally omit
 * incompatible, unsupported, and non-executed sources instead of presenting them as errors.
 */
@Composable
internal fun DiscoverSourceStatusRow(
    sources: List<DiscoverSourceStatusItem>,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return

    LazyRow(
        modifier = modifier.testTag("discover-source-status"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sources, key = { it.key }) { source ->
            DiscoverSourceStatusPill(source)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscoverSourceStatusPill(source: DiscoverSourceStatusItem) {
    val colors = MaterialTheme.colorScheme
    val visual = when (val status = source.status) {
        is DiscoverSourceStatusPresentation.Loading -> SourceStatusVisual(
            label = buildString {
                append("Loading")
                status.interimCount?.takeIf { it > 0 }?.let { append(" · $it") }
            },
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
            icon = SourceStatusIcon.Loading,
        )
        is DiscoverSourceStatusPresentation.Ready -> SourceStatusVisual(
            label = buildString {
                append("Ready")
                status.count?.let { append(" · $it") }
            },
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
            icon = SourceStatusIcon.Ready,
        )
        is DiscoverSourceStatusPresentation.Broken -> SourceStatusVisual(
            label = buildString {
                append("Broken")
                if (status.searchIsLoading) {
                    append(" · Loading")
                    status.resultCount?.takeIf { it > 0 }?.let { append(" · $it") }
                } else {
                    status.resultCount?.let { append(" · $it results") }
                }
            },
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            icon = SourceStatusIcon.Broken,
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = visual.containerColor,
        contentColor = visual.contentColor,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (visual.icon == SourceStatusIcon.Loading) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = visual.contentColor,
                )
            } else {
                Icon(
                    imageVector = when (visual.icon) {
                        SourceStatusIcon.Ready -> Icons.Filled.CheckCircle
                        SourceStatusIcon.Broken -> Icons.Filled.Error
                        SourceStatusIcon.Loading -> error("Loading uses LoadingIndicator")
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "${source.label} · ${visual.label}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class SourceStatusVisual(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: SourceStatusIcon,
)

private enum class SourceStatusIcon {
    Loading,
    Ready,
    Broken,
}
