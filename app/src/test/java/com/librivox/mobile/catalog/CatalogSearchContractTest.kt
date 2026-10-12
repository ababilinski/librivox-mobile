package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSearchContractTest {
    @Test
    fun planner_normalizesWhitespaceCaseAndDiacriticsBeforeCaching() {
        val accented = CatalogSearchPlanner.plan(
            query = "  Bolesław   Prus  ",
            field = CatalogSearchField.Author,
            languages = listOf("Polish"),
            sourcePreference = BookSourcePreference.WolneLekturyOnly,
        )
        val ascii = CatalogSearchPlanner.plan(
            query = "boleslaw prus",
            field = CatalogSearchField.Author,
            languages = listOf("Polish"),
            sourcePreference = BookSourcePreference.WolneLekturyOnly,
        )

        assertEquals("boleslaw prus", accented.normalizedQuery)
        assertEquals(accented.operations.single().cacheKey, ascii.operations.single().cacheKey)
    }

    @Test
    fun planner_trailingSpaceAliceUsesSameCacheIdentity() {
        val trailing = planAlice("alice ")
        val clean = planAlice("Alice")

        assertEquals("alice", trailing.normalizedQuery)
        assertEquals(
            clean.operations.map { it.cacheKey },
            trailing.operations.map { it.cacheKey },
        )
    }

    @Test
    fun cacheIdentityUsesOnlyContractVersionQuerySourceFieldAndLanguage() {
        val search = CatalogSearchPlanner.plan(
            query = "alice",
            field = CatalogSearchField.Title,
            languages = listOf("English"),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
            mode = CatalogSearchMode.Search,
        ).operations.single()
        val browseMetadata = search.copy(mode = CatalogSearchMode.Browse)

        assertEquals(search.cacheKey, browseMetadata.cacheKey)
        assertEquals("catalog-operation-v1|alice|librivox|title|english", search.cacheKey)
    }

    @Test
    fun normalizationUsesSharedNfkdCompatibilityMap() {
        assertEquals(
            "dorde dorde thor aesop oeuvre smorrebrod strasse",
            normalizedCatalogSearchText("Đorđe ðorđe Þór Æsop Œuvre Smørrebrød Straße"),
        )
        assertEquals(
            normalizedCatalogSearchText("Dorde thór aesop oeuvre smorrebrod strasse"),
            normalizedCatalogSearchText("Đorđe Þór Æsop Œuvre Smørrebrød Straße"),
        )
    }

    @Test
    fun polishOnlyAllSearchSkipsEveryLibriVoxFieldInsteadOfReturningZero() {
        val plan = CatalogSearchPlanner.plan(
            query = "alice ",
            field = CatalogSearchField.All,
            languages = listOf("Polish"),
            sourcePreference = BookSourcePreference.LibriVoxAndWolneLektury,
        )

        val librivox = plan.operations.filter {
            it.operation.source == CatalogSourcePreference.LibriVox
        }
        assertEquals(5, librivox.size)
        assertTrue(
            librivox.all {
                it.initialOutcome == CatalogOperationOutcome.Skipped(
                    CatalogOperationSkipReason.IncompatibleLanguage,
                )
            },
        )
        val wolne = plan.operations
            .filter { it.operation.source == CatalogSourcePreference.WolneLektury }
        assertTrue(
            wolne
                .filter {
                    it.operation.field in setOf(
                        CatalogSearchField.Title,
                        CatalogSearchField.Author,
                        CatalogSearchField.Genre,
                    )
                }
                .all { it.initialOutcome is CatalogOperationOutcome.Pending },
        )
        assertTrue(
            wolne
                .filter { it.operation.field in setOf(CatalogSearchField.Reader, CatalogSearchField.Chapter) }
                .all { it.initialOutcome is CatalogOperationOutcome.Skipped },
        )
    }

    @Test
    fun anyLanguageAllSearchExecutesIndependentLibriVoxFields() {
        val plan = planAlice("alice ")
        val fields = plan.operations.map { it.operation.field }

        assertEquals(
            listOf(
                CatalogSearchField.Title,
                CatalogSearchField.Author,
                CatalogSearchField.Reader,
                CatalogSearchField.Chapter,
                CatalogSearchField.Genre,
            ),
            fields,
        )
        assertTrue(plan.operations.all { it.language.isBlank() })
        assertTrue(plan.operations.all { it.initialOutcome is CatalogOperationOutcome.Pending })
    }

    @Test
    fun unsupportedFieldIsSkippedWithoutNetworkWork() {
        val plan = CatalogSearchPlanner.plan(
            query = "Alice",
            field = CatalogSearchField.Reader,
            languages = listOf("English"),
            sourcePreference = BookSourcePreference.Lit2GoOnly,
        )

        assertEquals(
            CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
            plan.operations.single().initialOutcome,
        )
    }

    @Test
    fun incompleteLit2GoAndWolneChapterCoverageIsSkippedNotAuthoritativeEmpty() {
        listOf(
            BookSourcePreference.Lit2GoOnly,
            BookSourcePreference.WolneLekturyOnly,
        ).forEach { source ->
            val language = if (source == BookSourcePreference.WolneLekturyOnly) "Polish" else "English"
            val plan = CatalogSearchPlanner.plan(
                query = "Advice from a Caterpillar",
                field = CatalogSearchField.Chapter,
                languages = listOf(language),
                sourcePreference = source,
            )
            assertEquals(
                CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
                plan.operations.single().initialOutcome,
            )
        }
    }

    @Test
    fun allFieldsPlansEverySourceFieldAndMarksUnsupportedCombinationsSkipped() {
        val plan = CatalogSearchPlanner.plan(
            query = "alice",
            field = CatalogSearchField.All,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.AllWithGutendex,
        )

        assertEquals(20, plan.operations.size)
        val lit2Go = plan.operations.filter {
            it.operation.source == CatalogSourcePreference.Lit2Go
        }
        assertTrue(
            lit2Go.single { it.operation.field == CatalogSearchField.Title }
                .initialOutcome is CatalogOperationOutcome.Pending,
        )
        listOf(CatalogSearchField.Reader, CatalogSearchField.Chapter, CatalogSearchField.Genre)
            .forEach { field ->
                assertEquals(
                    CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.UnsupportedField),
                    lit2Go.single { it.operation.field == field }.initialOutcome,
                )
            }
    }

    @Test
    fun noSourcePlanIsCompleteAndContainsNoOperations() {
        val plan = CatalogSearchPlanner.plan(
            query = "alice",
            field = CatalogSearchField.All,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.None,
        )

        assertTrue(plan.operations.isEmpty())
    }

    @Test
    fun positiveEmptyAndStaleFreshnessFollowContractTtls() {
        val operation = planAlice("alice").operations.first()
        val positive = CatalogCachedOperation(
            operation = operation,
            outcome = CatalogOperationOutcome.Success(1),
            books = emptyList(),
            storedAtMillis = 1_000L,
        )
        val empty = positive.copy(outcome = CatalogOperationOutcome.AuthoritativeEmpty)

        assertEquals(
            CatalogCacheFreshness.Fresh,
            positive.freshness(1_000L + CatalogPositiveFreshMillis),
        )
        assertEquals(
            CatalogCacheFreshness.Stale,
            positive.freshness(1_001L + CatalogPositiveFreshMillis),
        )
        assertEquals(
            CatalogCacheFreshness.Fresh,
            empty.freshness(1_000L + CatalogEmptyFreshMillis),
        )
        assertEquals(
            CatalogCacheFreshness.Stale,
            empty.freshness(1_001L + CatalogEmptyFreshMillis),
        )
        assertEquals(
            CatalogCacheFreshness.Expired,
            positive.freshness(1_001L + CatalogStaleUsableMillis),
        )
    }

    @Test
    fun pendingAndSkippedAreNeverFreshCacheEntries() {
        val operation = planAlice("alice").operations.first()
        listOf(
            CatalogOperationOutcome.Pending(),
            CatalogOperationOutcome.Skipped(CatalogOperationSkipReason.IncompatibleLanguage),
            CatalogOperationOutcome.Failed("timeout"),
        ).forEach { outcome ->
            val cached = CatalogCachedOperation(
                operation = operation,
                outcome = outcome,
                books = emptyList(),
                storedAtMillis = 1_000L,
            )
            assertFalse(cached.freshness(1_001L) == CatalogCacheFreshness.Fresh)
        }
    }

    private fun planAlice(query: String): CatalogSearchPlan =
        CatalogSearchPlanner.plan(
            query = query,
            field = CatalogSearchField.All,
            languages = listOf(""),
            sourcePreference = BookSourcePreference.LibriVoxOnly,
        )
}
