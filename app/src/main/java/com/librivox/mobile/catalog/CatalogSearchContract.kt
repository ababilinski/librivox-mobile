package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.util.Locale

/**
 * Android's implementation of the versioned catalog search contract shared with iOS.
 *
 * The contract deliberately contains no Android framework types. Keeping planning, outcome,
 * freshness, and provenance semantics here makes the behavior deterministic in JVM tests and
 * leaves a small extraction seam for a future shared runtime.
 */
const val CatalogContractVersion: Int = 1

val DefaultCatalogSourcePriority: List<CatalogSourcePreference> = listOf(
    CatalogSourcePreference.Lit2Go,
    CatalogSourcePreference.LibriVox,
    CatalogSourcePreference.WolneLektury,
)

enum class CatalogSearchMode { Search, Browse }

enum class CatalogOperationSkipReason {
    IncompatibleLanguage,
    UnsupportedField,
    NoSources,
}

enum class CatalogResultProvenance {
    Network,
    FreshCache,
    StaleCache,
    BundledFallback,
}

data class CatalogRetryMetadata(
    val attempt: Int = 0,
    val retryable: Boolean = true,
    val retryAfterMillis: Long? = null,
)

sealed interface CatalogOperationOutcome {
    data class Pending(val interimCount: Int = 0) : CatalogOperationOutcome
    data class Success(val count: Int) : CatalogOperationOutcome
    data object AuthoritativeEmpty : CatalogOperationOutcome
    data class Skipped(val reason: CatalogOperationSkipReason) : CatalogOperationOutcome
    data class Failed(
        val error: String,
        val retryMetadata: CatalogRetryMetadata = CatalogRetryMetadata(),
    ) : CatalogOperationOutcome
}

data class CatalogSourceCapabilities(
    val source: CatalogSourcePreference,
    val supportedFields: Set<CatalogSearchField>,
    /** Empty means that an unfiltered, any-language request is supported. */
    val supportedLanguages: Set<String>,
) {
    fun supportsLanguage(language: String): Boolean {
        if (language.isBlank()) return true
        return normalizedCatalogSearchText(language) in supportedLanguages
    }
}

data class CatalogPlannedOperation(
    val operation: CatalogSearchOperation,
    val normalizedQuery: String,
    val language: String,
    val mode: CatalogSearchMode,
    val initialOutcome: CatalogOperationOutcome = CatalogOperationOutcome.Pending(),
) {
    val cacheKey: String
        get() = listOf(
            "catalog-operation-v$CatalogContractVersion",
            normalizedQuery,
            operation.source.name.lowercase(Locale.ROOT),
            operation.field.name.lowercase(Locale.ROOT),
            normalizedCatalogSearchText(language),
        ).joinToString("|")
}

data class CatalogSearchPlan(
    val contractVersion: Int = CatalogContractVersion,
    val query: String,
    val normalizedQuery: String,
    val mode: CatalogSearchMode,
    val operations: List<CatalogPlannedOperation>,
    val offset: Int = 0,
    val pageSize: Int = 50,
    val sourcePriority: List<CatalogSourcePreference> = emptyList(),
)

data class CatalogSearchHit(
    val book: AudioBook,
    val matchedFields: Set<CatalogSearchField>,
    val contributingSources: Set<CatalogSourcePreference>,
    val provenance: CatalogResultProvenance,
)

data class CatalogSearchSnapshot(
    val plan: CatalogSearchPlan,
    val outcomes: Map<CatalogPlannedOperation, CatalogOperationOutcome>,
    /** Unmerged variants are retained so source facets remain truthful. */
    val hits: List<CatalogSearchHit>,
    val provenanceByOperation: Map<CatalogPlannedOperation, CatalogResultProvenance> = emptyMap(),
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val canLoadMore: Boolean = false,
) {
    val isComplete: Boolean
        get() = outcomes.values.all { outcome ->
            outcome is CatalogOperationOutcome.Success ||
                outcome == CatalogOperationOutcome.AuthoritativeEmpty ||
                outcome is CatalogOperationOutcome.Skipped
        }

    val completedOperationCount: Int
        get() = outcomes.values.count { it !is CatalogOperationOutcome.Pending }

    val canPersistAsFresh: Boolean
        get() = isComplete && provenanceByOperation.values.none {
            it == CatalogResultProvenance.StaleCache ||
                it == CatalogResultProvenance.BundledFallback
        }
}

fun interface CatalogClock {
    fun nowMillis(): Long
}

fun interface CatalogConnectivity {
    fun isOnline(): Boolean
}

data class CatalogHttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

data class CatalogHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>> = emptyMap(),
)

fun interface CatalogHttpTransport {
    suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse
}

data class CatalogCachedOperation(
    val operation: CatalogPlannedOperation,
    val outcome: CatalogOperationOutcome,
    val books: List<AudioBook>,
    val storedAtMillis: Long,
    val lastAccessedAtMillis: Long = storedAtMillis,
) {
    fun freshness(nowMillis: Long): CatalogCacheFreshness {
        val age = (nowMillis - storedAtMillis).coerceAtLeast(0L)
        val freshFor = when (outcome) {
            is CatalogOperationOutcome.Success -> CatalogPositiveFreshMillis
            CatalogOperationOutcome.AuthoritativeEmpty -> CatalogEmptyFreshMillis
            is CatalogOperationOutcome.Pending,
            is CatalogOperationOutcome.Skipped,
            is CatalogOperationOutcome.Failed -> return CatalogCacheFreshness.Expired
        }
        return when {
            age <= freshFor -> CatalogCacheFreshness.Fresh
            age <= CatalogStaleUsableMillis -> CatalogCacheFreshness.Stale
            else -> CatalogCacheFreshness.Expired
        }
    }
}

enum class CatalogCacheFreshness { Fresh, Stale, Expired }

interface CatalogOperationCache {
    suspend fun read(
        operation: CatalogPlannedOperation,
        nowMillis: Long,
    ): CatalogCachedOperation?

    suspend fun write(entry: CatalogCachedOperation)
}

object CatalogSearchPlanner {
    val capabilities: Map<CatalogSourcePreference, CatalogSourceCapabilities> = mapOf(
        CatalogSourcePreference.LibriVox to CatalogSourceCapabilities(
            source = CatalogSourcePreference.LibriVox,
            supportedFields = setOf(
                CatalogSearchField.All,
                CatalogSearchField.Title,
                CatalogSearchField.Author,
                CatalogSearchField.Chapter,
                CatalogSearchField.Reader,
                CatalogSearchField.Genre,
            ),
            // The product contract intentionally treats Polish-only discovery as incompatible
            // with LibriVox. Wolne Lektury owns that explicitly selected catalog lane.
            supportedLanguages = setOf("english", "french", "german", "spanish", "italian", "portuguese"),
        ),
        CatalogSourcePreference.Lit2Go to CatalogSourceCapabilities(
            source = CatalogSourcePreference.Lit2Go,
            supportedFields = setOf(
                CatalogSearchField.All,
                CatalogSearchField.Title,
                CatalogSearchField.Author,
            ),
            supportedLanguages = setOf("english"),
        ),
        CatalogSourcePreference.WolneLektury to CatalogSourceCapabilities(
            source = CatalogSourcePreference.WolneLektury,
            supportedFields = setOf(
                CatalogSearchField.All,
                CatalogSearchField.Title,
                CatalogSearchField.Author,
                CatalogSearchField.Genre,
                CatalogSearchField.Epoch,
                CatalogSearchField.Kind,
            ),
            supportedLanguages = setOf("polish"),
        ),
    )

    fun plan(
        query: String,
        field: CatalogSearchField,
        languages: List<String>,
        sourcePreference: BookSourcePreference,
        sourcePriority: List<CatalogSourcePreference> = DefaultCatalogSourcePriority,
        offset: Int = 0,
        pageSize: Int = 50,
        mode: CatalogSearchMode = CatalogSearchMode.Search,
    ): CatalogSearchPlan {
        val displayQuery = sanitizeCatalogSearchQuery(query)
        val normalizedQuery = normalizedCatalogSearchText(displayQuery)
        if (mode == CatalogSearchMode.Search && normalizedQuery.isBlank()) {
            return CatalogSearchPlan(
                query = displayQuery,
                normalizedQuery = normalizedQuery,
                mode = mode,
                operations = emptyList(),
                offset = offset.coerceAtLeast(0),
                pageSize = pageSize.coerceAtLeast(1),
                sourcePriority = sourcePriority.filter { it in sourcePreference.enabledSources },
            )
        }
        val requestedLanguages = languages
            .map { it.trim() }
            .distinctBy(::normalizedCatalogSearchText)
            .ifEmpty { listOf("") }
        val anyLanguage = requestedLanguages.any { it.isBlank() }
        val operations = CatalogSourcePreference.ActiveSources
            .filter { it in sourcePreference.enabledSources }
            .flatMap { source ->
                val capabilities = requireNotNull(capabilities[source])
                val fields = when {
                    mode == CatalogSearchMode.Browse -> listOf(CatalogSearchField.All)
                    field == CatalogSearchField.All ->
                        listOf(
                            CatalogSearchField.Title,
                            CatalogSearchField.Author,
                            CatalogSearchField.Reader,
                            CatalogSearchField.Chapter,
                            CatalogSearchField.Genre,
                        )
                    else -> listOf(field)
                }
                fields.flatMap { operationField ->
                    val base = CatalogSearchOperation(source, operationField)
                    if (operationField !in capabilities.supportedFields) {
                        listOf(
                            CatalogPlannedOperation(
                                operation = base,
                                normalizedQuery = normalizedQuery,
                                language = requestedLanguages.joinToString("|"),
                                mode = mode,
                                initialOutcome = CatalogOperationOutcome.Skipped(
                                    CatalogOperationSkipReason.UnsupportedField,
                                ),
                            ),
                        )
                    } else if (anyLanguage) {
                        listOf(
                            CatalogPlannedOperation(
                                operation = base,
                                normalizedQuery = normalizedQuery,
                                language = "",
                                mode = mode,
                            ),
                        )
                    } else {
                        val compatible = requestedLanguages.filter(capabilities::supportsLanguage)
                        if (compatible.isEmpty()) {
                            listOf(
                                CatalogPlannedOperation(
                                    operation = base,
                                    normalizedQuery = normalizedQuery,
                                    language = requestedLanguages.joinToString("|"),
                                    mode = mode,
                                    initialOutcome = CatalogOperationOutcome.Skipped(
                                        CatalogOperationSkipReason.IncompatibleLanguage,
                                    ),
                                ),
                            )
                        } else {
                            compatible.map { language ->
                                CatalogPlannedOperation(
                                    operation = base,
                                    normalizedQuery = normalizedQuery,
                                    language = language,
                                    mode = mode,
                                )
                            }
                        }
                    }
                }
            }
        return CatalogSearchPlan(
            query = displayQuery,
            normalizedQuery = normalizedQuery,
            mode = mode,
            operations = operations,
            offset = offset.coerceAtLeast(0),
            pageSize = pageSize.coerceAtLeast(1),
            sourcePriority = (
                sourcePriority.filter { it in sourcePreference.enabledSources } +
                    CatalogSourcePreference.ActiveSources.filter { it in sourcePreference.enabledSources }
                ).distinct(),
        )
    }
}

internal fun CatalogSearchPlan.progressItems(): List<CatalogSearchProgressItem> =
    operations
        .groupBy { it.operation }
        .map { (operation, planned) ->
            CatalogSearchProgressItem(
                operation = operation,
                outcome = planned.map { it.initialOutcome }.aggregateCatalogOutcome(),
            )
        }

internal fun CatalogSearchSnapshot.progressItems(): List<CatalogSearchProgressItem> =
    plan.operations
        .groupBy { it.operation }
        .map { (operation, planned) ->
            CatalogSearchProgressItem(
                operation = operation,
                outcome = planned.map { outcomes[it] ?: it.initialOutcome }.aggregateCatalogOutcome(),
            )
        }

private fun List<CatalogOperationOutcome>.aggregateCatalogOutcome(): CatalogOperationOutcome {
    if (any { it is CatalogOperationOutcome.Pending }) {
        return CatalogOperationOutcome.Pending(
            interimCount = sumOf { (it as? CatalogOperationOutcome.Pending)?.interimCount ?: 0 },
        )
    }
    val successCount = sumOf { (it as? CatalogOperationOutcome.Success)?.count ?: 0 }
    if (successCount > 0) return CatalogOperationOutcome.Success(successCount)
    if (any { it == CatalogOperationOutcome.AuthoritativeEmpty }) {
        return CatalogOperationOutcome.AuthoritativeEmpty
    }
    firstOrNull { it is CatalogOperationOutcome.Failed }?.let { return it }
    firstOrNull { it is CatalogOperationOutcome.Skipped }?.let { return it }
    return CatalogOperationOutcome.AuthoritativeEmpty
}

internal const val CatalogPositiveFreshMillis: Long = 30L * 60L * 1000L
internal const val CatalogEmptyFreshMillis: Long = 5L * 60L * 1000L
internal const val CatalogStaleUsableMillis: Long = 7L * 24L * 60L * 60L * 1000L
internal const val CatalogMaximumOperationSnapshots: Int = 100
