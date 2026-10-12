package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves Android planning consumes the same versioned scenario corpus as LibriVoxCore. */
class CatalogContractCorpusTest {
    @Test
    fun normalizationAndPlanningScenariosMatchSharedCorpus() {
        val corpus = Json.parseToJsonElement(contractFile().readText()).jsonObject
        assertEquals(CatalogContractVersion, corpus["contractVersion"]?.jsonPrimitive?.content?.toInt())
        val scenarios = corpus["scenarios"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it.string("kind") in setOf("normalization", "planning") }
        assertTrue("Expected shared normalization/planning scenarios", scenarios.isNotEmpty())

        scenarios.forEach { scenario ->
            val request = scenario["request"]!!.jsonObject
            val expected = scenario["expected"]!!.jsonObject
            val sources = request.strings("sources").map(::catalogSource).toSet()
            val preference = BookSourcePreference.fromEnabledSources(sources)
            val languages = request.strings("languages").map { language ->
                if (language == "any") "" else language.replaceFirstChar { it.titlecase() }
            }
            val plans = request.strings("fields").map { rawField ->
                CatalogSearchPlanner.plan(
                    query = request.string("query"),
                    field = catalogField(rawField),
                    languages = languages,
                    sourcePreference = preference,
                )
            }
            val normalized = plans.firstOrNull()?.normalizedQuery
                ?: normalizedCatalogSearchText(request.string("query"))
            val operations = plans.flatMap { it.operations }
            val actualKeys = operations.map { planned -> planned.contractOperationKey() }.distinct()

            assertEquals(scenario.string("id"), expected.string("normalizedQuery"), normalized)
            assertEquals(
                scenario.string("id"),
                expected.strings("plannedOperationKeys"),
                actualKeys,
            )
            val expectedOutcomes = (expected["outcomes"] as? JsonObject).orEmpty()
            expectedOutcomes.forEach { (key, value) ->
                val operation = operations.single { it.contractOperationKey() == key }
                assertEquals(
                    scenario.string("id"),
                    value.jsonPrimitive.content,
                    operation.initialOutcome.contractName(),
                )
            }
        }
    }

    private fun contractFile(): File {
        val relative = "catalog-contract/scenarios/v1/catalog-search-scenarios.json"
        return generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .map { File(it, relative) }
            .firstOrNull(File::isFile)
            ?: error("Could not locate $relative from ${System.getProperty("user.dir")}")
    }
}

private fun JsonObject.string(key: String): String =
    get(key)?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.strings(key: String): List<String> =
    (get(key) as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

private fun catalogSource(raw: String): CatalogSourcePreference = when (raw) {
    "librivox" -> CatalogSourcePreference.LibriVox
    "lit2go" -> CatalogSourcePreference.Lit2Go
    "gutendex" -> CatalogSourcePreference.Gutendex
    "wolne_lektury" -> CatalogSourcePreference.WolneLektury
    else -> error("Unknown catalog source $raw")
}

private fun catalogField(raw: String): CatalogSearchField = when (raw) {
    "title" -> CatalogSearchField.Title
    "author" -> CatalogSearchField.Author
    "reader" -> CatalogSearchField.Reader
    "chapter" -> CatalogSearchField.Chapter
    "genre" -> CatalogSearchField.Genre
    "epoch" -> CatalogSearchField.Epoch
    "kind" -> CatalogSearchField.Kind
    else -> error("Unknown catalog field $raw")
}

private fun CatalogPlannedOperation.contractOperationKey(): String {
    val source = when (operation.source) {
        CatalogSourcePreference.LibriVox -> "librivox"
        CatalogSourcePreference.Lit2Go -> "lit2go"
        CatalogSourcePreference.Gutendex -> "gutendex"
        CatalogSourcePreference.WolneLektury -> "wolne_lektury"
    }
    val field = operation.field.name.lowercase()
    val contractLanguage = normalizedCatalogSearchText(language).ifBlank { "any" }
    return "$source:$field:$contractLanguage"
}

private fun CatalogOperationOutcome.contractName(): String = when (this) {
    is CatalogOperationOutcome.Pending -> "pending"
    is CatalogOperationOutcome.Success -> "success"
    CatalogOperationOutcome.AuthoritativeEmpty -> "authoritative_empty"
    is CatalogOperationOutcome.Skipped -> "skipped"
    is CatalogOperationOutcome.Failed -> "failed"
}
