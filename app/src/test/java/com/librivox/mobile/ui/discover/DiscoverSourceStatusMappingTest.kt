package com.librivox.mobile.ui.discover

import com.librivox.mobile.catalog.CatalogOperationOutcome
import com.librivox.mobile.catalog.CatalogOperationSkipReason
import com.librivox.mobile.catalog.CatalogSearchField
import com.librivox.mobile.catalog.CatalogSearchOperation
import com.librivox.mobile.catalog.CatalogSearchProgressItem
import com.librivox.mobile.catalog.CatalogSourceHealth
import com.librivox.mobile.catalog.CatalogSourceHealthState
import com.librivox.mobile.catalog.CatalogSourceStatus
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverSourceStatusMappingTest {
    @Test
    fun globalOfflineOmitsAllSourcePills() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Available),
            operations = listOf(operation(CatalogOperationOutcome.Success(2))),
            isOffline = true,
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun sourceOfflineStateIsOmittedBecauseGlobalCopyOwnsConnectivity() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Offline, isOffline = true),
            operations = listOf(operation(CatalogOperationOutcome.Pending())),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun allSkippedOperationsAreOmittedEvenWhenRootIsBroken() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Broken, message = "HTTP 500"),
            operations = listOf(
                operation(
                    CatalogOperationOutcome.Skipped(
                        CatalogOperationSkipReason.IncompatibleLanguage,
                    ),
                ),
                operation(
                    CatalogOperationOutcome.Skipped(
                        CatalogOperationSkipReason.UnsupportedField,
                    ),
                    field = CatalogSearchField.Reader,
                ),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun confirmedBrokenRootPreservesPendingOperationAndInterimCount() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Broken, message = "Root page returned HTTP 503"),
            operations = listOf(operation(CatalogOperationOutcome.Pending(4))),
            count = 4,
        )

        assertEquals(
            DiscoverSourceStatusPresentation.Broken(
                reason = "Root page returned HTTP 503",
                searchIsLoading = true,
                resultCount = 4,
            ),
            items.single().status,
        )
    }

    @Test
    fun confirmedBrokenRootPreservesCompletedSearchCount() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Broken, message = "Unexpected root page"),
            operations = listOf(operation(CatalogOperationOutcome.Success(5))),
            count = 5,
        )

        assertEquals(
            DiscoverSourceStatusPresentation.Broken(
                reason = "Unexpected root page",
                resultCount = 5,
            ),
            items.single().status,
        )
    }

    @Test
    fun pendingOperationMapsToLoadingWithInterimSourceCount() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Available),
            operations = listOf(operation(CatalogOperationOutcome.Pending(3))),
            count = 3,
        )

        assertEquals(DiscoverSourceStatusPresentation.Loading(3), items.single().status)
    }

    @Test
    fun checkingRootMapsToLoadingBeforeTerminalSearchState() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Checking),
            operations = listOf(operation(CatalogOperationOutcome.Success(5))),
            count = 5,
        )

        assertEquals(DiscoverSourceStatusPresentation.Loading(5), items.single().status)
    }

    @Test
    fun allExecutedOperationFailuresMapToSearchFailed() {
        listOf(CatalogSourceHealth.Available, CatalogSourceHealth.Unknown).forEach { rootHealth ->
            val items = mapItems(
                health = health(rootHealth),
                operations = listOf(
                    operation(CatalogOperationOutcome.Failed("HTTP 500")),
                    operation(
                        CatalogOperationOutcome.Failed("Malformed response"),
                        field = CatalogSearchField.Author,
                    ),
                    operation(
                        CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
                        field = CatalogSearchField.Reader,
                    ),
                ),
            )

            assertEquals(
                DiscoverSourceStatusPresentation.Broken("Search failed"),
                items.single().status,
            )
        }
    }

    @Test
    fun terminalExecutedSourceMapsToReadyAndUsesFacetCount() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Available),
            operations = listOf(
                operation(CatalogOperationOutcome.Success(7)),
                operation(
                    CatalogOperationOutcome.Failed("Genre endpoint failed"),
                    field = CatalogSearchField.Genre,
                ),
            ),
            count = 6,
        )

        assertEquals(DiscoverSourceStatusPresentation.Ready(6), items.single().status)
    }

    @Test
    fun authoritativeEmptyMapsToReadyZeroRatherThanBroken() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Available),
            operations = listOf(operation(CatalogOperationOutcome.AuthoritativeEmpty)),
        )

        assertEquals(DiscoverSourceStatusPresentation.Ready(0), items.single().status)
    }

    @Test
    fun unknownRootWithNoExecutedOperationIsOmitted() {
        val items = mapItems(
            health = health(CatalogSourceHealth.Unknown),
            operations = emptyList(),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun unselectedSourcesAreNeverIncluded() {
        val source = CatalogSourcePreference.Lit2Go
        val items = discoverSourceStatusItems(
            sourceHealth = CatalogSourceHealthState().withStatus(
                CatalogSourceStatus(source, state = CatalogSourceHealth.Available),
            ),
            progressItems = listOf(
                CatalogSearchProgressItem(
                    CatalogSearchOperation(source, CatalogSearchField.Title),
                    CatalogOperationOutcome.Success(1),
                ),
            ),
            sourceCounts = mapOf(source to 1),
            sourcePreference = preference(CatalogSourcePreference.LibriVox),
            isOffline = false,
        )

        assertTrue(items.isEmpty())
    }

    private fun mapItems(
        health: CatalogSourceHealthState,
        operations: List<CatalogSearchProgressItem>,
        count: Int = 0,
        isOffline: Boolean = false,
    ): List<DiscoverSourceStatusItem> = discoverSourceStatusItems(
        sourceHealth = health,
        progressItems = operations,
        sourceCounts = mapOf(Source to count),
        sourcePreference = preference(Source),
        isOffline = isOffline,
    )

    private fun health(
        state: CatalogSourceHealth,
        message: String? = null,
        isOffline: Boolean = false,
    ): CatalogSourceHealthState = CatalogSourceHealthState().withStatus(
        CatalogSourceStatus(
            source = Source,
            state = state,
            message = message,
            isOffline = isOffline,
        ),
    )

    private fun operation(
        outcome: CatalogOperationOutcome,
        field: CatalogSearchField = CatalogSearchField.Title,
    ): CatalogSearchProgressItem = CatalogSearchProgressItem(
        operation = CatalogSearchOperation(Source, field),
        outcome = outcome,
    )

    private fun preference(source: CatalogSourcePreference): BookSourcePreference =
        BookSourcePreference.fromEnabledSources(setOf(source))

    private companion object {
        val Source = CatalogSourcePreference.LibriVox
    }
}
