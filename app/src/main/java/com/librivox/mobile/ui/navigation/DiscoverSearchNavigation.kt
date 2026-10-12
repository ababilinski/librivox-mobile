package com.librivox.mobile.ui.navigation

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import com.librivox.mobile.catalog.CatalogSearchField
import java.util.UUID

internal const val DiscoverSearchRequestKey = "discover.searchRequest"

data class DiscoverSearchRequest(
    val id: String,
    val query: String,
    val field: CatalogSearchField,
    val language: String,
)

/** Metadata searches belong to the destination receiving them, never to a global collector. */
fun NavController.openDiscoverSearch(
    query: String,
    field: CatalogSearchField,
    language: String = "",
) {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return
    if (currentDestination?.route != Routes.DISCOVER) {
        navigate(Routes.DISCOVER)
    }
    currentBackStackEntry?.savedStateHandle?.requestDiscoverSearch(trimmed, field, language)
}

internal fun SavedStateHandle.requestDiscoverSearch(
    query: String,
    field: CatalogSearchField,
    language: String,
): DiscoverSearchRequest {
    val request = DiscoverSearchRequest(UUID.randomUUID().toString(), query, field, language)
    this[DiscoverSearchRequestKey] = Bundle().apply {
        putString("id", request.id)
        putString("query", request.query)
        putString("field", request.field.name)
        putString("language", request.language)
    }
    return request
}

internal fun Bundle.toDiscoverSearchRequest(): DiscoverSearchRequest? {
    val id = getString("id") ?: return null
    val query = getString("query")?.takeIf { it.isNotBlank() } ?: return null
    val field = CatalogSearchField.entries.firstOrNull { it.name == getString("field") }
        ?: return null
    return DiscoverSearchRequest(id, query, field, getString("language").orEmpty())
}

internal fun SavedStateHandle.acknowledgeDiscoverSearch(id: String) {
    if (get<Bundle>(DiscoverSearchRequestKey)?.getString("id") == id) {
        // Keep the StateFlow attached to its key for another request on this same entry.
        this[DiscoverSearchRequestKey] = null
    }
}
