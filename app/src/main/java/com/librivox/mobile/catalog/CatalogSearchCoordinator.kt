package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface CatalogOperationLoader {
    suspend fun load(operation: CatalogPlannedOperation): List<AudioBook>
}

/** Executes independent source/field operations and publishes a snapshot after every transition. */
class CatalogSearchCoordinator(
    private val cache: CatalogOperationCache,
    private val clock: CatalogClock = CatalogClock(System::currentTimeMillis),
    private val connectivity: CatalogConnectivity = CatalogConnectivity { true },
) {
    suspend fun execute(
        plan: CatalogSearchPlan,
        loader: CatalogOperationLoader,
        onSnapshot: suspend (CatalogSearchSnapshot) -> Unit = {},
    ): CatalogSearchSnapshot = supervisorScope {
        val startedAt = clock.nowMillis()
        val mutex = Mutex()
        val outcomes = plan.operations.associateWith { it.initialOutcome }.toMutableMap()
        val hitsByOperation = mutableMapOf<CatalogPlannedOperation, List<CatalogSearchHit>>()
        val provenanceByOperation = mutableMapOf<CatalogPlannedOperation, CatalogResultProvenance>()

        fun snapshot(): CatalogSearchSnapshot = CatalogSearchSnapshot(
            plan = plan,
            outcomes = outcomes.toMap(),
            hits = hitsByOperation.values.flatten().mergeOperationHits(),
            provenanceByOperation = provenanceByOperation.toMap(),
            startedAtMillis = startedAt,
            updatedAtMillis = clock.nowMillis(),
            canLoadMore = false,
        )

        suspend fun publish(
            operation: CatalogPlannedOperation,
            outcome: CatalogOperationOutcome,
            books: List<AudioBook>? = null,
            provenance: CatalogResultProvenance = CatalogResultProvenance.Network,
        ) {
            val next = mutex.withLock {
                outcomes[operation] = outcome
                provenanceByOperation[operation] = provenance
                if (books != null) {
                    hitsByOperation[operation] = books.map { book ->
                        CatalogSearchHit(
                            book = book,
                            matchedFields = setOf(operation.operation.field),
                            contributingSources = setOf(operation.operation.source),
                            provenance = provenance,
                        )
                    }
                }
                snapshot()
            }
            onSnapshot(next)
        }

        onSnapshot(mutex.withLock { snapshot() })
        val jobs = plan.operations
            .filter { it.initialOutcome is CatalogOperationOutcome.Pending }
            .map { operation ->
                launch(Dispatchers.IO) {
                    val now = clock.nowMillis()
                    // Page zero owns the operation snapshot. Later pages must not replay or
                    // overwrite page zero; they are merged into the visible snapshot by the
                    // repository and will be cached by a subsequent complete refresh.
                    val cached = if (plan.offset == 0) cache.read(operation, now) else null
                    when (cached?.freshness(now)) {
                        CatalogCacheFreshness.Fresh -> {
                            publish(
                                operation = operation,
                                outcome = cached.outcome,
                                books = cached.books,
                                provenance = CatalogResultProvenance.FreshCache,
                            )
                            return@launch
                        }
                        CatalogCacheFreshness.Stale -> publish(
                            operation = operation,
                            outcome = CatalogOperationOutcome.Pending(cached.books.size),
                            books = cached.books,
                            provenance = CatalogResultProvenance.StaleCache,
                        )
                        CatalogCacheFreshness.Expired,
                        null -> Unit
                    }

                    if (!connectivity.isOnline()) {
                        if (cached != null) {
                            publish(
                                operation = operation,
                                outcome = cached.outcome,
                                books = cached.books,
                                provenance = CatalogResultProvenance.StaleCache,
                            )
                            return@launch
                        }
                        publish(
                            operation = operation,
                            outcome = CatalogOperationOutcome.Failed(
                                error = "Offline",
                                retryMetadata = CatalogRetryMetadata(attempt = 0, retryable = true),
                            ),
                            books = cached?.books,
                            provenance = CatalogResultProvenance.StaleCache,
                        )
                        return@launch
                    }

                    try {
                        val books = loader.load(operation).distinctBy { it.id }
                        val outcome = if (books.isEmpty()) {
                            CatalogOperationOutcome.AuthoritativeEmpty
                        } else {
                            CatalogOperationOutcome.Success(books.size)
                        }
                        val storedAt = clock.nowMillis()
                        if (plan.offset == 0) {
                            cache.write(
                                CatalogCachedOperation(
                                    operation = operation,
                                    outcome = outcome,
                                    books = books,
                                    storedAtMillis = storedAt,
                                    lastAccessedAtMillis = storedAt,
                                ),
                            )
                        }
                        publish(operation, outcome, books, CatalogResultProvenance.Network)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        publish(
                            operation = operation,
                            outcome = CatalogOperationOutcome.Failed(
                                error = throwable.catalogErrorMessage(),
                                retryMetadata = CatalogRetryMetadata(
                                    attempt = 0,
                                    retryable = throwable is IOException ||
                                        throwable is CatalogSourceUnavailableException,
                                ),
                            ),
                            // A failed refresh must never erase usable stale data.
                            books = cached?.books,
                            provenance = CatalogResultProvenance.StaleCache,
                        )
                    }
                }
            }
        jobs.forEach { it.join() }
        mutex.withLock { snapshot() }
    }
}

private fun List<CatalogSearchHit>.mergeOperationHits(): List<CatalogSearchHit> =
    groupBy { hit -> hit.operationIdentity() }
        .values
        .map { variants ->
            val first = variants.first()
            first.copy(
                matchedFields = variants.flatMapTo(linkedSetOf()) { it.matchedFields },
                contributingSources = variants.flatMapTo(linkedSetOf()) { it.contributingSources },
                provenance = variants.minBy { it.provenance.priority }.provenance,
            )
        }

private fun CatalogSearchHit.operationIdentity(): String =
    "${contributingSources.firstOrNull()?.name}:${book.id}"

private val CatalogResultProvenance.priority: Int
    get() = when (this) {
        CatalogResultProvenance.Network -> 0
        CatalogResultProvenance.FreshCache -> 1
        CatalogResultProvenance.StaleCache -> 2
        CatalogResultProvenance.BundledFallback -> 3
    }

private fun Throwable.catalogErrorMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
