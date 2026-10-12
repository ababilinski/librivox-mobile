package com.librivox.mobile.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExpandedDockedSearchBarWithGap
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.librivox.mobile.ui.theme.LocalTonalSurfaces
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val MediumWidth = 600.dp

@Immutable
internal data class DiscoverSearchRow(
    val key: String,
    val title: String,
    val supportingText: String,
)

/**
 * State-based Material search chrome. Phones use the contained full-screen search experience;
 * medium and expanded windows keep the search field anchored above a gapped docked result pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscoverSearchChrome(
    query: String,
    hint: String,
    isBusy: Boolean,
    collapseRevision: Int,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val compact = with(density) { windowInfo.containerSize.width.toDp() } < MediumWidth
    if (compact) {
        ContainedDiscoverSearchChrome(
            query = query,
            hint = hint,
            isBusy = isBusy,
            collapseRevision = collapseRevision,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onExpandedChange = onExpandedChange,
            modifier = modifier,
            expandedContent = expandedContent,
        )
    } else {
        DockedDiscoverSearchChrome(
            query = query,
            hint = hint,
            isBusy = isBusy,
            collapseRevision = collapseRevision,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            onExpandedChange = onExpandedChange,
            modifier = modifier,
            expandedContent = expandedContent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainedDiscoverSearchChrome(
    query: String,
    hint: String,
    isBusy: Boolean,
    collapseRevision: Int,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.material3.rememberContainedSearchBarState()
    val colors = SearchBarDefaults.containedColors(state)
    val inputField = discoverSearchInputField(
        query = query,
        hint = hint,
        isBusy = isBusy,
        state = state,
        collapseRevision = collapseRevision,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        onClear = onClear,
        onExpandedChange = onExpandedChange,
    )

    SearchBar(
        state = state,
        inputField = inputField,
        colors = colors,
        modifier = modifier,
    )
    ExpandedFullScreenContainedSearchBar(
        state = state,
        inputField = inputField,
        colors = colors,
    ) {
        expandedContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockedDiscoverSearchChrome(
    query: String,
    hint: String,
    isBusy: Boolean,
    collapseRevision: Int,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.material3.rememberSearchBarWithGapState()
    val tonal = LocalTonalSurfaces.current
    val colors = SearchBarDefaults.colors(containerColor = tonal.listItem)
    val inputField = discoverSearchInputField(
        query = query,
        hint = hint,
        isBusy = isBusy,
        state = state,
        collapseRevision = collapseRevision,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        onClear = onClear,
        onExpandedChange = onExpandedChange,
    )

    SearchBar(
        state = state,
        inputField = inputField,
        colors = colors,
        modifier = modifier,
    )
    ExpandedDockedSearchBarWithGap(
        state = state,
        inputField = inputField,
        colors = colors,
        modifier = Modifier.heightIn(max = 560.dp),
    ) {
        expandedContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun discoverSearchInputField(
    query: String,
    hint: String,
    isBusy: Boolean,
    state: SearchBarState,
    collapseRevision: Int,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
): @Composable () -> Unit {
    val textState = rememberTextFieldState(initialText = query)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(query) {
        if (textState.text.toString() != query) {
            textState.edit {
                replace(0, length, query)
                placeCursorAtEnd()
            }
        }
    }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect(onQueryChange)
    }
    LaunchedEffect(collapseRevision) {
        if (state.currentValue != SearchBarValue.Collapsed) {
            state.animateToCollapsed()
        }
    }
    LaunchedEffect(state.currentValue) {
        onExpandedChange(state.currentValue == SearchBarValue.Expanded)
    }

    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textState,
            searchBarState = state,
            onSearch = { submittedQuery ->
                onSearch(submittedQuery)
                focusManager.clearFocus(force = true)
                scope.launch { state.animateToCollapsed() }
            },
            placeholder = {
                Text(
                    text = hint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (state.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus(force = true)
                            scope.launch { state.animateToCollapsed() }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                        )
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBusy) {
                        LoadingIndicator(
                            modifier = Modifier
                                .padding(end = if (query.isBlank()) 12.dp else 0.dp)
                                .size(28.dp),
                        )
                    }
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            },
        )
    }
    return inputField
}

@Composable
internal fun DiscoverExpandedSearchContent(
    query: String,
    fieldLabel: String,
    history: List<String>,
    suggestions: List<DiscoverSearchRow>,
    results: List<DiscoverSearchRow>,
    modifier: Modifier = Modifier,
    totalResultCount: Int = results.size,
    hasExecutedSearch: Boolean,
    isLoading: Boolean,
    completedOperations: Int,
    totalOperations: Int,
    unavailableMessage: String?,
    onHistorySelected: (String) -> Unit,
    onHistoryDeleted: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSuggestionSelected: (DiscoverSearchRow) -> Unit,
    onResultSelected: (DiscoverSearchRow) -> Unit,
    onSubmitQuery: () -> Unit,
) {
    val tonal = LocalTonalSurfaces.current
    when {
        query.isBlank() && history.isNotEmpty() -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "history-title") {
                    SearchSectionTitle("Recent searches")
                }
                items(history, key = { "history-$it" }) { entry ->
                    ListItem(
                        onClick = { onHistorySelected(entry) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(Icons.Filled.History, contentDescription = null)
                        },
                        supportingContent = { Text("Search again") },
                        trailingContent = {
                            IconButton(onClick = { onHistoryDeleted(entry) }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Delete $entry")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(entry)
                    }
                }
                item(key = "history-clear") {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text("Clear history")
                    }
                }
            }
        }

        query.isBlank() -> {
            SearchPrompt(
                title = "Find your next listen",
                body = "Search titles, chapters, authors, readers, genres, and metadata across your selected catalogs.",
                modifier = modifier.fillMaxSize(),
            )
        }

        results.isNotEmpty() -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "result-status") {
                    SearchProgressStatus(
                        resultCount = totalResultCount,
                        isLoading = isLoading,
                        completedOperations = completedOperations,
                        totalOperations = totalOperations,
                    )
                }
                items(results, key = { "result-${it.key}" }) { result ->
                    ListItem(
                        onClick = { onResultSelected(result) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        leadingContent = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        supportingContent = {
                            Text(
                                result.supportingText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        !hasExecutedSearch && suggestions.isNotEmpty() -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item(key = "suggestion-title") {
                    SearchSectionTitle("Suggestions")
                }
                items(suggestions, key = { "suggestion-${it.key}" }) { suggestion ->
                    ListItem(
                        onClick = { onSuggestionSelected(suggestion) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        supportingContent = {
                            Text(
                                suggestion.supportingText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    ) {
                        Text(suggestion.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item(key = "submit-query") {
                    ListItem(
                        onClick = onSubmitQuery,
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        supportingContent = { Text("Search every selected catalog") },
                        colors = ListItemDefaults.colors(containerColor = tonal.inlineAction),
                    ) {
                        Text("Search $fieldLabel for \u201c$query\u201d")
                    }
                }
            }
        }

        isLoading -> {
            SearchPrompt(
                title = "Searching $fieldLabel",
                body = operationProgressText(completedOperations, totalOperations),
                loading = true,
                modifier = modifier.fillMaxSize(),
            )
        }

        unavailableMessage != null -> {
            SearchPrompt(
                title = "Some catalogs are unavailable",
                body = unavailableMessage,
                iconIsError = true,
                actionLabel = "Try search again",
                onAction = onSubmitQuery,
                modifier = modifier.fillMaxSize(),
            )
        }

        hasExecutedSearch -> {
            SearchPrompt(
                title = "No results for \u201c$query\u201d",
                body = "Try another field, choose Any language, or broaden your selected sources.",
                iconIsError = true,
                actionLabel = "Search again",
                onAction = onSubmitQuery,
                modifier = modifier.fillMaxSize(),
            )
        }

        else -> {
            SearchPrompt(
                title = "Search $fieldLabel for \u201c$query\u201d",
                body = "Results will appear here as each catalog search finishes.",
                actionLabel = "Search",
                onAction = onSubmitQuery,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun SearchProgressStatus(
    resultCount: Int,
    isLoading: Boolean,
    completedOperations: Int,
    totalOperations: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isLoading) "$resultCount results so far" else "$resultCount results",
                style = MaterialTheme.typography.titleMedium,
            )
            if (totalOperations > 0) {
                Text(
                    text = "$completedOperations of $totalOperations searches complete",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
        }
    }
    HorizontalDivider()
}

@Composable
private fun SearchPrompt(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    iconIsError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(52.dp))
        } else {
            Icon(
                imageVector = if (iconIsError) Icons.Filled.SearchOff else Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.size(20.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

private fun operationProgressText(completed: Int, total: Int): String =
    if (total > 0) {
        "$completed of $total searches complete. Results appear as each catalog responds."
    } else {
        "Results appear as each catalog responds."
    }
