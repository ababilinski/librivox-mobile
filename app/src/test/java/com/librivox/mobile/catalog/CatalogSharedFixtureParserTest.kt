package com.librivox.mobile.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSharedFixtureParserTest {
    @Test
    fun androidAdaptersParseSharedVersionOneFixtures() {
        val librivox = LibriVoxCatalogSource().parseBooksJson(fixture("librivox/feed-title-alice.json"))
        assertEquals(listOf("200", "900"), librivox.map { it.id })
        assertTrue(librivox.first().chapters.isNotEmpty())

        val advanced = LibriVoxCatalogSource().parseAdvancedSearchJson(
            fixture("librivox/advanced-author-alice.json"),
        )
        assertTrue(advanced.titleCandidates.any { "Alice in Wonderland" in it })
        assertTrue(advanced.personPointers.any { it.category == "author" && it.id == "2770" })
        val advancedReader = LibriVoxCatalogSource().parseAdvancedSearchJson(
            fixture("librivox/advanced-reader-alice.json"),
        )
        assertTrue(advancedReader.titleCandidates.any { "old-man-coyote" in it })
        val advancedChapter = LibriVoxCatalogSource().parseAdvancedSearchJson(
            fixture("librivox/advanced-chapter-advice.json"),
        )
        assertTrue(advancedChapter.titleCandidates.any { "coffee-break-collection" in it })

        val lit2GoSource = Lit2GoCatalogSource()
        val litStub = lit2GoSource.parseLetterIndex(fixture("lit2go/index-a.html")).single()
        val litBook = lit2GoSource.parseBookDetail(
            numericId = litStub.id,
            slug = litStub.slug,
            html = fixture("lit2go/detail-alice.html"),
        )
        assertEquals("lit2go-1__alices-adventures-in-wonderland", litBook.id)
        assertTrue(litBook.chapters.any { "Advice from a Caterpillar" in it.title })

        val gutendex = GutendexCatalogSource().parseBooksJson(fixture("gutendex/search-alice.json"))
        assertEquals(setOf("gutendex-19573", "gutendex-12974"), gutendex.map { it.id }.toSet())

        val wolneSource = WolneLekturyCatalogSource()
        val wolneStubs = wolneSource.parseAudiobookIndexJson(fixture("wolne/index-antek.json"))
        assertTrue(wolneStubs.any { it.slug == "antek" })
        val antek = wolneSource.parseBookDetail(
            slug = "antek",
            rawJson = fixture("wolne/detail-antek.json"),
        )
        assertEquals("wolnelektury-antek", antek.id)
        assertEquals("Bolesław Prus", antek.author)
        assertTrue(antek.chapters.isNotEmpty())
    }

    @Test
    fun sharedFailureFixturesRemainStrictFailures() {
        val librivox = LibriVoxCatalogSource()
        assertTrue(runCatching { librivox.parseBooksJson(fixture("failures/malformed-json.txt")) }.isFailure)
        assertTrue(runCatching { librivox.parseAdvancedSearchJson(fixture("failures/blank.html")) }.isFailure)
        assertTrue(runCatching { librivox.parseBooksJson("{}") }.isFailure)

        val gutendex = GutendexCatalogSource()
        assertTrue(runCatching { gutendex.parseBooksJson("{}") }.isFailure)
        assertTrue(gutendex.parseBooksJson("""{"results":[]}""").isEmpty())

        val wolne = WolneLekturyCatalogSource()
        assertTrue(runCatching { wolne.parseAudiobookIndexJson("{}") }.isFailure)
        assertTrue(wolne.parseAudiobookIndexJson("[]").isEmpty())
    }

    private fun fixture(relative: String): String =
        contractRoot().resolve("fixtures/v1/$relative").readText()

    private fun contractRoot(): File =
        generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .map { File(it, "catalog-contract") }
            .firstOrNull(File::isDirectory)
            ?: error("Could not locate catalog-contract")
}
