package com.librivox.mobile.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.librivox.mobile.catalog.CatalogOperationOutcome
import com.librivox.mobile.catalog.CatalogSearchFacetCounts
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogSearchOperation
import com.librivox.mobile.catalog.CatalogSearchProgressItem
import com.librivox.mobile.catalog.CatalogSourceHealth
import com.librivox.mobile.catalog.CatalogSourceHealthState
import com.librivox.mobile.catalog.CatalogSourceStatus
import com.librivox.mobile.catalog.DiscoverSortOrder
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import com.librivox.mobile.ui.theme.LocalTonalSurfaces

private const val PreviewWidth = 412
private const val PreviewHeight = 892

private val aliceRows = listOf(
    DiscoverSearchRow(
        key = "200",
        title = "Alice's Adventures in Wonderland",
        supportingText = "Lewis Carroll · LibriVox",
    ),
    DiscoverSearchRow(
        key = "1122",
        title = "Alice's Adventures Under Ground",
        supportingText = "Lewis Carroll · LibriVox",
    ),
    DiscoverSearchRow(
        key = "lit2go-alice",
        title = "Alice in Wonderland",
        supportingText = "Lewis Carroll · Lit2Go",
    ),
    DiscoverSearchRow(
        key = "librivox-alice-version-2",
        title = "Alice's Adventures in Wonderland",
        supportingText = "Lewis Carroll · LibriVox",
    ),
)

@PreviewTest
@Preview(
    name = "Discover idle",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverIdleScreenshot() {
    DiscoverPreviewFrame(query = "", expanded = false) {
        DiscoverExpandedSearchContent(
            query = "",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = false,
            isLoading = false,
            completedOperations = 0,
            totalOperations = 0,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover focused empty",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverFocusedEmptyScreenshot() {
    DiscoverPreviewFrame(query = "", expanded = true) {
        DiscoverExpandedSearchContent(
            query = "",
            fieldLabel = "all fields",
            history = listOf("Common Sense", "Antek"),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = false,
            isLoading = false,
            completedOperations = 0,
            totalOperations = 0,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover typing prompt",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverTypingPromptScreenshot() {
    DiscoverPreviewFrame(query = "alice", expanded = true) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = false,
            isLoading = false,
            completedOperations = 0,
            totalOperations = 7,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover partial results",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverPartialResultsScreenshot() {
    DiscoverPreviewFrame(
        query = "alice",
        expanded = true,
        sourceStatuses = listOf(
            DiscoverSourceStatusItem(
                key = "librivox",
                label = "LibriVox",
                status = DiscoverSourceStatusPresentation.Loading(interimCount = 2),
            ),
            DiscoverSourceStatusItem(
                key = "lit2go",
                label = "Lit2Go",
                status = DiscoverSourceStatusPresentation.Ready(count = 1),
            ),
        ),
    ) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = aliceRows.take(2),
            hasExecutedSearch = true,
            isLoading = true,
            completedOperations = 3,
            totalOperations = 7,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover partial results dark",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverPartialResultsDarkScreenshot() {
    DiscoverPreviewFrame(
        query = "alice",
        expanded = true,
        darkTheme = true,
        sourceStatuses = listOf(
            DiscoverSourceStatusItem(
                key = "librivox",
                label = "LibriVox",
                status = DiscoverSourceStatusPresentation.Loading(interimCount = 2),
            ),
            DiscoverSourceStatusItem(
                key = "lit2go",
                label = "Lit2Go",
                status = DiscoverSourceStatusPresentation.Broken(),
            ),
        ),
    ) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = aliceRows.take(2),
            hasExecutedSearch = true,
            isLoading = true,
            completedOperations = 3,
            totalOperations = 7,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover complete results",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverCompleteResultsScreenshot() {
    DiscoverPreviewFrame(
        query = "alice",
        expanded = true,
        sourceStatuses = listOf(
            DiscoverSourceStatusItem(
                key = "librivox",
                label = "LibriVox",
                status = DiscoverSourceStatusPresentation.Ready(count = 2),
            ),
            DiscoverSourceStatusItem(
                key = "lit2go",
                label = "Lit2Go",
                status = DiscoverSourceStatusPresentation.Ready(count = 1),
            ),
        ),
    ) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = aliceRows,
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 7,
            totalOperations = 7,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover unavailable",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverUnavailableScreenshot() {
    DiscoverPreviewFrame(
        query = "alice",
        expanded = true,
        sourceStatuses = listOf(
            DiscoverSourceStatusItem(
                key = "librivox",
                label = "LibriVox",
                status = DiscoverSourceStatusPresentation.Broken(),
            ),
            DiscoverSourceStatusItem(
                key = "lit2go",
                label = "Lit2Go",
                status = DiscoverSourceStatusPresentation.Ready(count = 1),
            ),
        ),
    ) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 5,
            totalOperations = 7,
            unavailableMessage = "LibriVox did not respond. Results from other catalogs remain available.",
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover offline",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverOfflineScreenshot() {
    DiscoverPreviewFrame(query = "alice", expanded = true) {
        DiscoverExpandedSearchContent(
            query = "alice",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 4,
            totalOperations = 7,
            unavailableMessage = "Offline. Cached matches remain available and unfinished searches will retry once after reconnection.",
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover no results",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Preview(
    name = "Discover no results large font",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun DiscoverNoResultsScreenshot() {
    DiscoverPreviewFrame(query = "zzzz-no-match", expanded = true) {
        DiscoverExpandedSearchContent(
            query = "zzzz-no-match",
            fieldLabel = "all fields",
            history = emptyList(),
            suggestions = emptyList(),
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 7,
            totalOperations = 7,
            unavailableMessage = null,
            onHistorySelected = {},
            onHistoryDeleted = {},
            onClearHistory = {},
            onSuggestionSelected = {},
            onResultSelected = {},
            onSubmitQuery = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Discover filter sheet",
    widthDp = PreviewWidth,
    heightDp = PreviewHeight,
    showBackground = true,
)
@Composable
fun DiscoverFilterSheetScreenshot() {
    AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
        DiscoverFilterSheet(
            sortOrder = DiscoverSortOrder.MostPopular,
            searchField = CatalogSearchField.All,
            availableSources = listOf(
                CatalogSourcePreference.LibriVox,
                CatalogSourcePreference.Lit2Go,
            ),
            sourceFilter = null,
            availableLanguages = listOf("English", "Polish"),
            languageFilter = null,
            facetCounts = CatalogSearchFacetCounts(
                total = 3,
                sourceCounts = mapOf(
                    CatalogSourcePreference.LibriVox to 2,
                    CatalogSourcePreference.Lit2Go to 1,
                ),
                fieldCounts = mapOf(
                    CatalogSearchField.All to 3,
                    CatalogSearchField.Title to 2,
                    CatalogSearchField.Author to 1,
                ),
                languageCounts = mapOf("English" to 3),
            ),
            showCounts = true,
            progressItems = listOf(
                CatalogSearchProgressItem(
                    CatalogSearchOperation(
                        CatalogSourcePreference.LibriVox,
                        CatalogSearchField.Title,
                    ),
                    CatalogOperationOutcome.Pending(2),
                ),
                CatalogSearchProgressItem(
                    CatalogSearchOperation(
                        CatalogSourcePreference.LibriVox,
                        CatalogSearchField.Author,
                    ),
                    CatalogOperationOutcome.Success(1),
                ),
                CatalogSearchProgressItem(
                    CatalogSearchOperation(
                        CatalogSourcePreference.Lit2Go,
                        CatalogSearchField.Title,
                    ),
                    CatalogOperationOutcome.Failed("HTTP 503"),
                ),
            ),
            sourceHealth = CatalogSourceHealthState().withStatus(
                CatalogSourceStatus(
                    CatalogSourcePreference.LibriVox,
                    state = CatalogSourceHealth.Checking,
                ),
            ).withStatus(
                CatalogSourceStatus(
                    CatalogSourcePreference.Lit2Go,
                    state = CatalogSourceHealth.Broken,
                ),
            ),
            isOffline = false,
            query = "alice",
            onDismiss = {},
            onSortOrderChange = {},
            onSearchFieldChange = {},
            onSourceFilterChange = {},
            onLanguageFilterChange = {},
            onClearSearch = {},
            onGenreSelected = {},
            contentOnly = true,
        )
    }
}

@Composable
private fun DiscoverPreviewFrame(
    query: String,
    expanded: Boolean,
    darkTheme: Boolean = false,
    sourceStatuses: List<DiscoverSourceStatusItem> = emptyList(),
    content: @Composable () -> Unit,
) {
    AudioPlayerTheme(darkTheme = darkTheme, dynamicColor = false) {
        val tonal = LocalTonalSurfaces.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (expanded) {
                    ExpandedSearchHeader(query)
                } else {
                    DiscoverSearchChrome(
                        query = query,
                        hint = "Search all fields",
                        isBusy = false,
                        collapseRevision = 0,
                        onQueryChange = {},
                        onSearch = {},
                        onClear = {},
                        onExpandedChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        content()
                    }
                }
                DiscoverFilterSummaryRow(
                    field = "All fields",
                    source = "All sources",
                    language = "Any language",
                    onOpenFilters = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                DiscoverSourceStatusRow(
                    sources = sourceStatuses,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = tonal.listItem,
                    tonalElevation = 1.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedSearchHeader(query: String) {
    val tonal = LocalTonalSurfaces.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = tonal.listItem,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(
                text = query.ifBlank { "Search all fields" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
