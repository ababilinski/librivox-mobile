package com.librivox.mobile.ui.discover

import com.librivox.mobile.catalog.CatalogOperationOutcome
import com.librivox.mobile.catalog.CatalogSearchProgressItem
import com.librivox.mobile.catalog.CatalogSourceHealth
import com.librivox.mobile.catalog.CatalogSourceHealthState
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference

/** Maps root health and the current search plan into the passive source-status pills. */
internal fun discoverSourceStatusItems(
    sourceHealth: CatalogSourceHealthState,
    progressItems: List<CatalogSearchProgressItem>,
    sourceCounts: Map<CatalogSourcePreference, Int>,
    sourcePreference: BookSourcePreference,
    isOffline: Boolean,
): List<DiscoverSourceStatusItem> {
    if (isOffline) return emptyList()

    val operationsBySource = progressItems.groupBy { it.operation.source }
    return CatalogSourcePreference.entries.mapNotNull { source ->
        if (source !in sourcePreference.enabledSources) return@mapNotNull null

        val health = sourceHealth.statusFor(source)
        if (health.isOffline || health.state == CatalogSourceHealth.Offline) {
            return@mapNotNull null
        }

        val relevant = operationsBySource[source].orEmpty()
        if (relevant.isNotEmpty() && relevant.all { it.outcome is CatalogOperationOutcome.Skipped }) {
            return@mapNotNull null
        }
        val executed = relevant.filterNot { it.outcome is CatalogOperationOutcome.Skipped }
        val count = sourceCounts[source] ?: 0
        val hasPendingSearch = executed.any { it.outcome is CatalogOperationOutcome.Pending }
        val hasCompletedSearchResult = executed.any {
            it.outcome is CatalogOperationOutcome.Success ||
                it.outcome == CatalogOperationOutcome.AuthoritativeEmpty
        }

        val presentation = when {
            health.state == CatalogSourceHealth.Broken ->
                DiscoverSourceStatusPresentation.Broken(
                    reason = health.message,
                    searchIsLoading = hasPendingSearch,
                    resultCount = when {
                        hasPendingSearch -> count.takeIf { it > 0 }
                        hasCompletedSearchResult -> count
                        else -> null
                    },
                )

            health.state == CatalogSourceHealth.Checking ||
                executed.any { it.outcome is CatalogOperationOutcome.Pending } ->
                DiscoverSourceStatusPresentation.Loading(count.takeIf { it > 0 })

            executed.isNotEmpty() &&
                executed.all { it.outcome is CatalogOperationOutcome.Failed } ->
                DiscoverSourceStatusPresentation.Broken("Search failed")

            executed.isNotEmpty() -> DiscoverSourceStatusPresentation.Ready(count)

            else -> null
        } ?: return@mapNotNull null

        DiscoverSourceStatusItem(
            key = source.name,
            label = source.label,
            status = presentation,
        )
    }
}
