package com.librivox.mobile.catalog

import com.librivox.mobile.model.CatalogTag

internal object CatalogGenreTranslations {
    private val translations = listOf(
        Translation("Powieść", "Novel"),
        Translation("Wiersz", "Poem", listOf("Poetry")),
        Translation("Baśń", "Fairy tale", listOf("Children")),
        Translation("Bajka", "Fable", listOf("Children")),
        Translation("Nowela", "Novella", listOf("Short story", "Short stories")),
        Translation("Opowiadanie", "Short story", listOf("Short stories")),
        Translation("Epos", "Epic"),
        Translation("Poemat", "Narrative poem", listOf("Poem", "Poetry")),
        Translation("Ballada", "Ballad"),
        Translation("Sonet", "Sonnet"),
        Translation("Oda", "Ode"),
        Translation("Hymn", "Hymn"),
        Translation("Pieśń", "Song"),
        Translation("Satyra", "Satire"),
        Translation("Komedia", "Comedy"),
        Translation("Tragedia", "Tragedy"),
        Translation("Dramat", "Drama"),
        Translation("Pamiętnik", "Memoir"),
        Translation("Reportaż", "Reportage"),
        Translation("List", "Letter"),
        Translation("Fraszka", "Epigram"),
        Translation("Przypowieść", "Parable"),
        Translation("Traktat", "Treatise"),
        Translation("Powiastka filozoficzna", "Philosophical tale", listOf("Philosophy")),
        Translation("Romans", "Romance"),
        Translation("Epika", "Prose"),
        Translation("Liryka", "Lyric poetry", listOf("Poetry")),
        Translation("Antyk", "Antiquity"),
        Translation("Średniowiecze", "Middle Ages"),
        Translation("Renesans", "Renaissance"),
        Translation("Barok", "Baroque"),
        Translation("Oświecenie", "Enlightenment"),
        Translation("Romantyzm", "Romanticism"),
        Translation("Pozytywizm", "Positivism"),
        Translation("Modernizm", "Modernism"),
        Translation("Młoda Polska", "Young Poland"),
        Translation("Dwudziestolecie międzywojenne", "Interwar period"),
        Translation("Współczesność", "Contemporary"),
    )

    private val byPolishName = translations.associateBy { normalizedCatalogSearchText(it.polishName) }
    private val polishByEnglishName = buildMap<String, List<String>> {
        val grouped = linkedMapOf<String, MutableList<String>>()
        translations.forEach { translation ->
            (listOf(translation.englishName) + translation.englishAliases).forEach { english ->
                val key = normalizedCatalogSearchText(english)
                if (key.isNotBlank()) {
                    grouped.getOrPut(key) { mutableListOf() } += translation.polishName
                }
            }
        }
        grouped.forEach { (key, names) -> put(key, names.distinct()) }
    }

    fun displayName(name: String): String =
        byPolishName[normalizedCatalogSearchText(name)]?.englishName ?: name

    fun displayTag(tag: CatalogTag): CatalogTag =
        tag.copy(name = displayName(tag.name))

    fun searchTermsForName(name: String): List<String> {
        val normalized = normalizedCatalogSearchText(name)
        if (normalized.isBlank()) return emptyList()
        val translatedFromPolish = byPolishName[normalized]
        val polishMatches = polishByEnglishName[normalized].orEmpty()
        return (listOf(name) +
            listOfNotNull(translatedFromPolish?.englishName) +
            translatedFromPolish?.englishAliases.orEmpty() +
            polishMatches)
            .filter { it.isNotBlank() }
            .distinctBy { normalizedCatalogSearchText(it) }
    }

    fun queryVariants(query: String): List<String> {
        val normalized = normalizedCatalogSearchText(query)
        if (normalized.isBlank()) return emptyList()
        return (polishByEnglishName[normalized].orEmpty() + query)
            .filter { it.isNotBlank() }
            .distinctBy { normalizedCatalogSearchText(it) }
    }

    private data class Translation(
        val polishName: String,
        val englishName: String,
        val englishAliases: List<String> = emptyList(),
    )
}
