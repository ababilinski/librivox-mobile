package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.BookSource
import com.librivox.mobile.playback.BookSourcePreference
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchCoordinatorTest {
    @Test
    fun independentOperationsPublishProgressivelyAndOneFailureDoesNotHideSuccess() = runBlocking {
        val plan = CatalogSearchPlanner.plan(
            query = "alice",
            field = CatalogSearchField.All,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        )
        val cache = MemoryOperationCache()
        val snapshotCount = AtomicInteger()
        val final = CatalogSearchCoordinator(cache).execute(
            plan = plan,
            loader = CatalogOperationLoader { operation ->
                if (operation.operation.field == CatalogSearchField.Genre) {
                    throw IOException("HTTP 500")
                }
                listOf(book(operation.operation.field.name))
            },
            onSnapshot = { snapshotCount.incrementAndGet() },
        )

        assertEquals(plan.operations.size + 1, snapshotCount.get())
        assertFalse(final.isComplete)
        assertEquals(4, final.hits.size)
        assertTrue(final.outcomes.values.any { it is CatalogOperationOutcome.Failed })
        assertEquals(4, cache.entries.size)
    }

    @Test
    fun freshPositiveCacheAvoidsNetwork() = runBlocking {
        val plan = singleTitlePlan()
        val operation = plan.operations.single()
        val cache = MemoryOperationCache().apply {
            entries[operation.cacheKey] = CatalogCachedOperation(
                operation = operation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(book("200")),
                storedAtMillis = 10_000L,
            )
        }
        var networkCalls = 0
        val final = CatalogSearchCoordinator(
            cache = cache,
            clock = CatalogClock { 10_001L },
        ).execute(plan, CatalogOperationLoader { networkCalls++; emptyList() })

        assertEquals(0, networkCalls)
        assertEquals("200", final.hits.single().book.id)
        assertEquals(CatalogResultProvenance.FreshCache, final.hits.single().provenance)
    }

    @Test
    fun staleCacheIsPublishedThenRevalidated() = runBlocking {
        val plan = singleTitlePlan()
        val operation = plan.operations.single()
        val cache = MemoryOperationCache().apply {
            entries[operation.cacheKey] = CatalogCachedOperation(
                operation = operation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(book("stale")),
                storedAtMillis = 1L,
            )
        }
        val observedIds = mutableListOf<List<String>>()
        val now = CatalogPositiveFreshMillis + 2L
        val final = CatalogSearchCoordinator(
            cache = cache,
            clock = CatalogClock { now },
        ).execute(
            plan,
            CatalogOperationLoader { listOf(book("fresh")) },
            onSnapshot = { observedIds += it.hits.map { hit -> hit.book.id } },
        )

        assertTrue(observedIds.contains(listOf("stale")))
        assertEquals(listOf("fresh"), final.hits.map { it.book.id })
        assertEquals("fresh", cache.entries[operation.cacheKey]?.books?.single()?.id)
    }

    @Test
    fun failedRefreshKeepsStaleResultsAndDoesNotOverwriteCache() = runBlocking {
        val plan = singleTitlePlan()
        val operation = plan.operations.single()
        val staleEntry = CatalogCachedOperation(
            operation = operation,
            outcome = CatalogOperationOutcome.Success(1),
            books = listOf(book("stale")),
            storedAtMillis = 1L,
        )
        val cache = MemoryOperationCache().apply { entries[operation.cacheKey] = staleEntry }
        val final = CatalogSearchCoordinator(
            cache = cache,
            clock = CatalogClock { CatalogPositiveFreshMillis + 2L },
        ).execute(plan, CatalogOperationLoader { throw IOException("timeout") })

        assertEquals(listOf("stale"), final.hits.map { it.book.id })
        assertTrue(final.outcomes.values.single() is CatalogOperationOutcome.Failed)
        assertEquals(staleEntry, cache.entries[operation.cacheKey])
    }

    @Test
    fun offlineMissDoesNotCallNetworkAndIsRetryable() = runBlocking {
        val plan = singleTitlePlan()
        var networkCalls = 0
        val final = CatalogSearchCoordinator(
            cache = MemoryOperationCache(),
            connectivity = CatalogConnectivity { false },
        ).execute(plan, CatalogOperationLoader { networkCalls++; emptyList() })

        assertEquals(0, networkCalls)
        val failed = final.outcomes.values.single() as CatalogOperationOutcome.Failed
        assertEquals("Offline", failed.error)
        assertTrue(failed.retryMetadata.retryable)
    }

    @Test
    fun offlineFreshCacheIsACompleteHitWithoutNetwork() = runBlocking {
        val plan = singleTitlePlan()
        val operation = plan.operations.single()
        val cache = MemoryOperationCache().apply {
            entries[operation.cacheKey] = CatalogCachedOperation(
                operation = operation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(book("200")),
                storedAtMillis = 1_000L,
            )
        }
        var networkCalls = 0
        val final = CatalogSearchCoordinator(
            cache = cache,
            clock = CatalogClock { 1_001L },
            connectivity = CatalogConnectivity { false },
        ).execute(plan, CatalogOperationLoader { networkCalls++; emptyList() })

        assertEquals(0, networkCalls)
        assertTrue(final.isComplete)
        assertEquals(CatalogResultProvenance.FreshCache, final.hits.single().provenance)
    }

    @Test
    fun offlineStaleCacheRemainsVisibleAndCompleteButCannotRefreshAggregateCache() = runBlocking {
        val plan = singleTitlePlan()
        val operation = plan.operations.single()
        val cache = MemoryOperationCache().apply {
            entries[operation.cacheKey] = CatalogCachedOperation(
                operation = operation,
                outcome = CatalogOperationOutcome.Success(1),
                books = listOf(book("stale")),
                storedAtMillis = 1L,
            )
        }
        val final = CatalogSearchCoordinator(
            cache = cache,
            clock = CatalogClock { CatalogPositiveFreshMillis + 2L },
            connectivity = CatalogConnectivity { false },
        ).execute(plan, CatalogOperationLoader { error("network should not run") })

        assertTrue(final.isComplete)
        assertEquals("stale", final.hits.single().book.id)
        assertEquals(CatalogResultProvenance.StaleCache, final.hits.single().provenance)
        assertEquals(CatalogOperationOutcome.Success(1), final.outcomes.values.single())
        assertFalse(final.canPersistAsFresh)
    }

    @Test
    fun authoritativeEmptyUsesShortNegativeCache() = runBlocking {
        val plan = singleTitlePlan()
        val cache = MemoryOperationCache()
        val final = CatalogSearchCoordinator(cache).execute(
            plan,
            CatalogOperationLoader { emptyList() },
        )

        assertEquals(CatalogOperationOutcome.AuthoritativeEmpty, final.outcomes.values.single())
        assertTrue(cache.entries.values.single().books.isEmpty())
        assertEquals(
            CatalogOperationOutcome.AuthoritativeEmpty,
            cache.entries.values.single().outcome,
        )
    }

    @Test
    fun incompatibleLanguageCompletesWithoutCacheOrNetwork() = runBlocking {
        val plan = CatalogSearchPlanner.plan(
            query = "alice",
            field = CatalogSearchField.Title,
            languages = listOf("Polish"),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        )
        val cache = MemoryOperationCache()
        var networkCalls = 0
        val final = CatalogSearchCoordinator(cache).execute(
            plan,
            CatalogOperationLoader { networkCalls++; emptyList() },
        )

        assertEquals(0, networkCalls)
        assertTrue(cache.entries.isEmpty())
        assertTrue(final.isComplete)
        assertTrue(final.outcomes.values.single() is CatalogOperationOutcome.Skipped)
    }

    @Test
    fun whitespaceOrNoSourcePlanIsACompleteNoOp() = runBlocking {
        val plan = CatalogSearchPlanner.plan(
            query = "   ",
            field = CatalogSearchField.All,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.None,
        )
        var networkCalls = 0
        val final = CatalogSearchCoordinator(MemoryOperationCache()).execute(
            plan,
            CatalogOperationLoader { networkCalls++; emptyList() },
        )

        assertTrue(final.isComplete)
        assertTrue(final.outcomes.isEmpty())
        assertEquals(0, networkCalls)
    }

    @Test
    fun sourceChangeCancellationStopsOldOperationWithoutPoisoningCache() = runBlocking {
        val oldPlan = singleTitlePlan()
        val newPlan = CatalogSearchPlanner.plan(
            query = "Common Sense",
            field = CatalogSearchField.Title,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        )
        val cache = MemoryOperationCache()
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val oldJob = launch {
            try {
                CatalogSearchCoordinator(cache).execute(
                    oldPlan,
                    CatalogOperationLoader {
                        started.complete(Unit)
                        delay(Long.MAX_VALUE)
                        emptyList()
                    },
                )
            } finally {
                cancelled = true
            }
        }
        started.await()
        oldJob.cancelAndJoin()

        val replacement = CatalogSearchCoordinator(cache).execute(
            newPlan,
            CatalogOperationLoader { listOf(book("common-sense")) },
        )
        assertTrue(cancelled)
        assertTrue(oldPlan.operations.single().cacheKey !in cache.entries)
        assertEquals("common-sense", replacement.hits.single().book.id)
    }

    @Test
    fun reconnectRetriesFailedOperationExactlyOnceThenFreshCacheSuppressesDuplicates() = runBlocking {
        val plan = singleTitlePlan()
        val cache = MemoryOperationCache()
        var networkCalls = 0
        val loader = CatalogOperationLoader {
            networkCalls++
            if (networkCalls == 1) throw IOException("connection lost")
            listOf(book("200"))
        }
        val coordinator = CatalogSearchCoordinator(cache)

        val disconnected = coordinator.execute(plan, loader)
        assertFalse(disconnected.isComplete)
        val reconnected = coordinator.execute(plan, loader)
        assertTrue(reconnected.isComplete)
        val duplicateRecoverySignal = coordinator.execute(plan, loader)

        assertEquals(2, networkCalls)
        assertEquals("200", duplicateRecoverySignal.hits.single().book.id)
        assertEquals(CatalogResultProvenance.FreshCache, duplicateRecoverySignal.hits.single().provenance)
    }

    private fun singleTitlePlan(): CatalogSearchPlan = CatalogSearchPlanner.plan(
        query = "alice",
        field = CatalogSearchField.Title,
        languages = listOf(""),
        sourcePreference = BookSourcePreference.LibriVoxOnly,
    )

    private fun book(id: String): AudioBook = AudioBook(
        id = id,
        title = "Alice's Adventures in Wonderland",
        author = "Lewis Carroll",
        source = BookSource.LibriVox,
        language = "English",
    )

    private class MemoryOperationCache : CatalogOperationCache {
        // The coordinator reads and writes independent operations on Dispatchers.IO.
        val entries: MutableMap<String, CatalogCachedOperation> = ConcurrentHashMap()

        override suspend fun read(
            operation: CatalogPlannedOperation,
            nowMillis: Long,
        ): CatalogCachedOperation? = entries[operation.cacheKey]

        override suspend fun write(entry: CatalogCachedOperation) {
            assertFalse(entry.outcome is CatalogOperationOutcome.Failed)
            entries[entry.operation.cacheKey] = entry
        }
    }
}
