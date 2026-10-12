package com.librivox.mobile.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.librivox.mobile.ui.theme.AudioPlayerTheme
import com.librivox.mobile.catalog.CatalogSearchFacetCounts
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogSourceHealthState
import com.librivox.mobile.catalog.DiscoverSortOrder
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoverSearchChromeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filterSummary_usesReadableScrollablePillsWithMaterialTouchTargets() {
        val openedSections = mutableListOf<DiscoverFilterSheetSection>()
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                DiscoverFilterSummaryRow(
                    field = "All fields",
                    source = "All sources",
                    language = "Any language",
                    onOpenFilters = openedSections::add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeRule.onNodeWithTag("discover-filter-chip-field")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("discover-filter-summary").performScrollToIndex(1)
        composeRule.onNodeWithTag("discover-filter-chip-source")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("discover-filter-summary").performScrollToIndex(2)
        composeRule.onNodeWithTag("discover-filter-chip-language")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithTag("discover-filter-summary").performScrollToIndex(3)
        composeRule.onNodeWithTag("discover-filter-chip-overview")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    DiscoverFilterSheetSection.SearchField,
                    DiscoverFilterSheetSection.Source,
                    DiscoverFilterSheetSection.Language,
                    DiscoverFilterSheetSection.Overview,
                ),
                openedSections,
            )
        }
    }

    @Test
    fun filterSheet_initialSectionAlignsEachSpecificFilterAtTheTop() {
        var initialSection by mutableStateOf(DiscoverFilterSheetSection.SearchField)
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                ) {
                    DiscoverFilterSheet(
                        sortOrder = DiscoverSortOrder.MostPopular,
                        searchField = CatalogSearchField.All,
                        availableSources = listOf(
                            CatalogSourcePreference.LibriVox,
                            CatalogSourcePreference.Lit2Go,
                        ),
                        sourceFilter = null,
                        availableLanguages = listOf(
                            "English",
                            "French",
                            "German",
                            "Spanish",
                            "Italian",
                            "Portuguese",
                        ),
                        languageFilter = null,
                        facetCounts = CatalogSearchFacetCounts(),
                        showCounts = false,
                        progressItems = emptyList(),
                        sourceHealth = CatalogSourceHealthState(),
                        isOffline = false,
                        query = "",
                        initialSection = initialSection,
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
        }

        assertFilterSectionNearTop("discover-filter-section-search-field")

        composeRule.runOnIdle { initialSection = DiscoverFilterSheetSection.Source }
        assertFilterSectionNearTop("discover-filter-section-source")

        composeRule.runOnIdle { initialSection = DiscoverFilterSheetSection.Language }
        assertFilterSectionNearTop("discover-filter-section-language")

        composeRule.runOnIdle { initialSection = DiscoverFilterSheetSection.Overview }
        assertFilterSectionNearTop("discover-filter-section-overview")
        composeRule.onNodeWithTag("discover-filter-section-sort").assertIsDisplayed()
    }

    @Test
    fun filterSheet_sourceAndMinimalLanguageTargetsStillOpenTheirOwnSections() {
        var initialSection by mutableStateOf(DiscoverFilterSheetSection.Source)
        var sources by mutableStateOf(emptyList<CatalogSourcePreference>())
        var languages by mutableStateOf(emptyList<String>())
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                ) {
                    DiscoverFilterSheet(
                        sortOrder = DiscoverSortOrder.MostPopular,
                        searchField = CatalogSearchField.All,
                        availableSources = sources,
                        sourceFilter = null,
                        availableLanguages = languages,
                        languageFilter = null,
                        facetCounts = CatalogSearchFacetCounts(),
                        showCounts = false,
                        progressItems = emptyList(),
                        sourceHealth = CatalogSourceHealthState(),
                        isOffline = false,
                        query = "",
                        initialSection = initialSection,
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
        }

        assertFilterSectionNearTop("discover-filter-section-source")
        composeRule.onNodeWithText("No sources selected").assertIsDisplayed()

        composeRule.runOnIdle {
            sources = listOf(CatalogSourcePreference.WolneLektury)
            languages = listOf("Polish")
            initialSection = DiscoverFilterSheetSection.Language
        }
        assertFilterSectionNearTop("discover-filter-section-language")
        composeRule.onNodeWithText("Any language").assertIsDisplayed()
        composeRule.onNodeWithText("Polish").assertIsDisplayed()
    }

    @Test
    fun filterSummary_opensTheActualModalAtTheRequestedSection() {
        var openSection by mutableStateOf<DiscoverFilterSheetSection?>(null)
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DiscoverFilterSummaryRow(
                        field = "All fields",
                        source = "All sources",
                        language = "Any language",
                        onOpenFilters = { openSection = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    openSection?.let { section ->
                        DiscoverFilterSheet(
                            sortOrder = DiscoverSortOrder.MostPopular,
                            searchField = CatalogSearchField.All,
                            availableSources = listOf(
                                CatalogSourcePreference.LibriVox,
                                CatalogSourcePreference.Lit2Go,
                            ),
                            sourceFilter = null,
                            availableLanguages = listOf("English", "French"),
                            languageFilter = null,
                            facetCounts = CatalogSearchFacetCounts(),
                            showCounts = false,
                            progressItems = emptyList(),
                            sourceHealth = CatalogSourceHealthState(),
                            isOffline = false,
                            query = "",
                            initialSection = section,
                            onDismiss = { openSection = null },
                            onSortOrderChange = {},
                            onSearchFieldChange = {},
                            onSourceFilterChange = {},
                            onLanguageFilterChange = {},
                            onClearSearch = {},
                            onGenreSelected = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("discover-filter-chip-source").performClick()
        assertFilterSectionNearTop("discover-filter-section-source")
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }

    private fun assertFilterSectionNearTop(tag: String) {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        val viewport = composeRule.onNodeWithTag("discover-filter-sheet-scroll")
            .fetchSemanticsNode().boundsInRoot
        val section = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithTag("discover-filter-section-overview")
            .fetchSemanticsNode().boundsInRoot
        if (tag == "discover-filter-section-overview") {
            assertTrue(
                "The fixed overview header should stay above the filter list: header=$header viewport=$viewport",
                header.bottom <= viewport.top,
            )
            return
        }
        assertTrue(
            "$tag should start near the top of the filter sheet: viewport=$viewport section=$section",
            section.top >= viewport.top && section.top <= viewport.top + (viewport.height * 0.25f),
        )
        assertTrue(
            "$tag should not be covered by the sheet header: header=$header section=$section",
            section.top >= header.bottom,
        )
    }

    @Test
    fun sourceStatusPills_showLoadingReadyAndBrokenWithoutFilterErrorCopy() {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                DiscoverSourceStatusRow(
                    sources = listOf(
                        DiscoverSourceStatusItem(
                            key = "librivox",
                            label = "LibriVox",
                            status = DiscoverSourceStatusPresentation.Loading(interimCount = 2),
                        ),
                        DiscoverSourceStatusItem(
                            key = "wolne",
                            label = "Wolne Lektury",
                            status = DiscoverSourceStatusPresentation.Ready(count = 4),
                        ),
                        DiscoverSourceStatusItem(
                            key = "lit2go",
                            label = "Lit2Go",
                            status = DiscoverSourceStatusPresentation.Broken("Invalid root page"),
                        ),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        composeRule.onNodeWithText("LibriVox · Loading · 2").assertExists()
        composeRule.onNodeWithText("Wolne Lektury · Ready · 4").assertExists()
        composeRule.onNodeWithTag("discover-source-status").performScrollToIndex(2)
        composeRule.onNodeWithText("Lit2Go · Broken").assertExists()
        composeRule.onAllNodesWithText("so far", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Not searched", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Incompatible language", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun imeSearch_submitsCurrentQuery_andClearTargetIsAccessible() {
        var query by mutableStateOf("")
        var submittedQuery: String? = null

        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                DiscoverSearchChrome(
                    query = query,
                    hint = "Search all fields",
                    isBusy = false,
                    collapseRevision = 0,
                    onQueryChange = { query = it },
                    onSearch = { submittedQuery = it },
                    onClear = { query = "" },
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DiscoverExpandedSearchContent(
                        query = query,
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
        }

        composeRule.onAllNodes(hasSetTextAction()).onFirst()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .performTextInput("alice")
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Clear search").onFirst()
            .assertTouchHeightIsEqualTo(48.dp)
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performImeAction()

        composeRule.runOnIdle {
            assertEquals("alice", query)
            assertEquals("alice", submittedQuery)
        }
    }

    @Test
    fun partialResults_reportInterimProgress_withoutMisleadingZero() {
        setExpandedContent(
            query = "alice",
            results = listOf(
                DiscoverSearchRow(
                    key = "200",
                    title = "Alice's Adventures in Wonderland",
                    supportingText = "Lewis Carroll · LibriVox",
                ),
            ),
            hasExecutedSearch = true,
            isLoading = true,
            completedOperations = 3,
            totalOperations = 7,
        )

        composeRule.onNodeWithText("1 results so far").assertExists()
        composeRule.onNodeWithText("3 of 7 searches complete").assertExists()
        composeRule.onAllNodesWithText("(0)", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("0 results", substring = true).assertCountEquals(0)
    }

    @Test
    fun resultRows_keepMaterialMinimumTouchTargets() {
        setExpandedContent(
            query = "alice",
            results = listOf(
                DiscoverSearchRow(
                    key = "200",
                    title = "Alice's Adventures in Wonderland",
                    supportingText = "Lewis Carroll · LibriVox",
                ),
            ),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 7,
            totalOperations = 7,
        )

        composeRule.onNode(
            hasClickAction() and hasAnyDescendant(
                hasText("Alice's Adventures in Wonderland"),
            ),
            useUnmergedTree = true,
        ).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun retryAction_keepsMaterialMinimumTouchTarget() {
        setExpandedContent(
            query = "alice",
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 5,
            totalOperations = 7,
            unavailableMessage = "Offline. Cached matches remain available.",
        )

        composeRule.onNodeWithText("Try search again")
            .assertExists()
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun uncommittedSearch_hasUsefulPromptInsteadOfBlankPanel() {
        setExpandedContent(
            query = "alice",
            results = emptyList(),
            hasExecutedSearch = false,
            isLoading = false,
            completedOperations = 0,
            totalOperations = 7,
        )

        composeRule.onNodeWithText("Search all fields for “alice”").assertExists()
        composeRule.onNodeWithText(
            "Results will appear here as each catalog search finishes.",
        ).assertExists()
    }

    @Test
    fun noResults_offersAnyLanguageRecovery() {
        setExpandedContent(
            query = "zzzz-no-match",
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 7,
            totalOperations = 7,
        )

        composeRule.onNodeWithText("No results for “zzzz-no-match”").assertExists()
        composeRule.onNodeWithText(
            "Try another field, choose Any language, or broaden your selected sources.",
        ).assertExists()
    }

    @Test
    fun offlineState_isExplicitAndRetainsRetryPromise() {
        setExpandedContent(
            query = "alice",
            results = emptyList(),
            hasExecutedSearch = true,
            isLoading = false,
            completedOperations = 4,
            totalOperations = 7,
            unavailableMessage = "Offline. Cached matches remain available and unfinished searches will retry once after reconnection.",
        )

        composeRule.onNodeWithText("Some catalogs are unavailable").assertExists()
        composeRule.onNodeWithText(
            "Offline. Cached matches remain available and unfinished searches will retry once after reconnection.",
        ).assertExists()
    }

    private fun setExpandedContent(
        query: String,
        results: List<DiscoverSearchRow>,
        hasExecutedSearch: Boolean,
        isLoading: Boolean,
        completedOperations: Int,
        totalOperations: Int,
        unavailableMessage: String? = null,
    ) {
        composeRule.setContent {
            AudioPlayerTheme(darkTheme = false, dynamicColor = false) {
                DiscoverExpandedSearchContent(
                    query = query,
                    fieldLabel = "all fields",
                    history = emptyList(),
                    suggestions = emptyList(),
                    results = results,
                    hasExecutedSearch = hasExecutedSearch,
                    isLoading = isLoading,
                    completedOperations = completedOperations,
                    totalOperations = totalOperations,
                    unavailableMessage = unavailableMessage,
                    onHistorySelected = {},
                    onHistoryDeleted = {},
                    onClearHistory = {},
                    onSuggestionSelected = {},
                    onResultSelected = {},
                    onSubmitQuery = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
