package com.librivox.mobile.ui.discover

import com.librivox.mobile.catalog.CatalogOperationOutcome
import com.librivox.mobile.catalog.CatalogOperationSkipReason
import com.librivox.mobile.catalog.CatalogSearchFacetCounts
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogSearchOperation
import com.librivox.mobile.catalog.CatalogSearchProgressItem
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverFacetPresentationTest {
    @Test
    fun filterSheetSectionOrderKeepsSpecificTargetsStable() {
        assertEquals(
            listOf(
                DiscoverFilterSheetSection.Sort,
                DiscoverFilterSheetSection.SearchField,
                DiscoverFilterSheetSection.Source,
                DiscoverFilterSheetSection.Language,
            ),
            discoverFilterSheetSectionOrder(),
        )
    }

    @Test
    fun validUnsearchedAlternativesRemainSelectableWithoutAStatusLabel() {
        assertEquals(
            true,
            isCatalogFieldFilterOptionAvailable(
                field = CatalogSearchField.Author,
                sources = listOf(CatalogSourcePreference.LibriVox),
                language = null,
            ),
        )
        assertEquals(
            true,
            isCatalogSourceFilterOptionAvailable(
                source = CatalogSourcePreference.Lit2Go,
                field = CatalogSearchField.Title,
                language = null,
            ),
        )
        assertEquals(
            null,
            facetPresentation(
                count = 0,
                operations = emptyList(),
                showCounts = true,
            ),
        )
    }

    @Test
    fun onlyExecutedFieldSourceAndLanguageScopesReceiveCounts() {
        assertEquals(
            true,
            shouldPresentFieldFacetStatus(CatalogSearchField.All, CatalogSearchField.Author),
        )
        assertEquals(
            false,
            shouldPresentFieldFacetStatus(CatalogSearchField.Title, CatalogSearchField.All),
        )
        assertEquals(
            true,
            shouldPresentFieldFacetStatus(CatalogSearchField.Title, CatalogSearchField.Title),
        )
        assertEquals(true, shouldPresentNullableFacetStatus<String>(null, "English"))
        assertEquals(false, shouldPresentNullableFacetStatus("English", null))
        assertEquals(false, shouldPresentNullableFacetStatus("English", "Polish"))
        assertEquals(true, shouldPresentNullableFacetStatus("English", "English"))
    }

    @Test
    fun anyLanguageCountIncludesResultsWithoutAnExplicitLanguageFacet() {
        assertEquals(
            5,
            anyLanguageFacetCount(
                CatalogSearchFacetCounts(
                    total = 5,
                    languageCounts = mapOf("English" to 2, "Polish" to 1),
                ),
            ),
        )
    }

    @Test
    fun offlineSuppressesFailuresAndUncoveredScopesButPreservesReadyCounts() {
        val failed = listOf(
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(
                    CatalogSourcePreference.LibriVox,
                    CatalogSearchField.Title,
                ),
                outcome = CatalogOperationOutcome.Failed("Offline"),
            ),
        )

        assertEquals(
            null,
            filterFacetPresentation(
                count = 0,
                operations = failed,
                showCounts = true,
                isOffline = true,
                isCovered = true,
            ),
        )
        assertEquals(
            null,
            filterFacetPresentation(
                count = 0,
                operations = failed,
                showCounts = true,
                isOffline = false,
                isCovered = false,
            ),
        )
        assertEquals(
            FacetPresentation.Ready(3),
            filterFacetPresentation(
                count = 3,
                operations = listOf(
                    CatalogSearchProgressItem(
                        operation = CatalogSearchOperation(
                            CatalogSourcePreference.LibriVox,
                            CatalogSearchField.Title,
                        ),
                        outcome = CatalogOperationOutcome.Success(3),
                    ),
                ),
                showCounts = true,
                isOffline = true,
                isCovered = true,
            ),
        )
    }

    @Test
    fun structurallyUnsupportedOrIncompatibleAlternativesAreUnavailable() {
        assertEquals(
            false,
            isCatalogSourceFilterOptionAvailable(
                source = CatalogSourcePreference.Lit2Go,
                field = CatalogSearchField.Chapter,
                language = "English",
            ),
        )
        assertEquals(
            false,
            isCatalogSourceFilterOptionAvailable(
                source = CatalogSourcePreference.LibriVox,
                field = CatalogSearchField.Author,
                language = "Polish",
            ),
        )
        assertEquals(
            true,
            isCatalogSourceFilterOptionAvailable(
                source = CatalogSourcePreference.WolneLektury,
                field = CatalogSearchField.Epoch,
                language = "Polish",
            ),
        )
    }

    @Test
    fun skippedFacetOptionsAreOmittedFromTheFilterSheet() {
        assertEquals(
            false,
            shouldShowFacetOption(FacetPresentation.Skipped("Incompatible language")),
        )
        assertEquals(
            false,
            shouldShowFacetOption(FacetPresentation.Skipped("Not searched")),
        )
        assertEquals(true, shouldShowFacetOption(FacetPresentation.Pending(2)))
        assertEquals(true, shouldShowFacetOption(FacetPresentation.Ready(0)))
        assertEquals(true, shouldShowFacetOption(FacetPresentation.Unavailable))
    }

    @Test
    fun filterStatusLabelsUseLoadingAndNeverExposeSkippedReasons() {
        assertEquals("Loading…", facetStatusLabel(FacetPresentation.Pending(0)))
        assertEquals("Loading · 3", facetStatusLabel(FacetPresentation.Pending(3)))
        assertEquals("12", facetStatusLabel(FacetPresentation.Ready(12)))
        assertEquals("Broken", facetStatusLabel(FacetPresentation.Unavailable, "Broken"))
        assertEquals(
            "Broken · Loading · 3",
            facetStatusLabel(FacetPresentation.RootBroken(FacetPresentation.Pending(3))),
        )
        assertEquals(
            "Broken · 12",
            facetStatusLabel(FacetPresentation.RootBroken(FacetPresentation.Ready(12))),
        )
        assertEquals(
            null,
            facetStatusLabel(FacetPresentation.Skipped("Not searched")),
        )
        assertEquals(
            null,
            facetStatusLabel(FacetPresentation.Skipped("Incompatible language")),
        )
    }

    @Test
    fun failedSupportedOperationsRemainUnavailableWhenOtherFieldsWereSkipped() {
        val source = CatalogSourcePreference.Lit2Go
        val operations = listOf(
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(source, CatalogSearchField.Title),
                outcome = CatalogOperationOutcome.Failed("HTTP 503"),
            ),
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(source, CatalogSearchField.Author),
                outcome = CatalogOperationOutcome.Failed("HTTP 503"),
            ),
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(source, CatalogSearchField.Reader),
                outcome = CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
            ),
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(source, CatalogSearchField.Chapter),
                outcome = CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
            ),
            CatalogSearchProgressItem(
                operation = CatalogSearchOperation(source, CatalogSearchField.Genre),
                outcome = CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
            ),
        )

        assertEquals(
            FacetPresentation.Unavailable,
            facetPresentation(
                count = 0,
                operations = operations,
                showCounts = true,
            ),
        )
    }
}
