package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import com.librivox.mobile.playback.PlaybackSettingsStore
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AggregateCatalogClient(
    private val librivox: CatalogSource,
    private val lit2go: CatalogSource,
    private val gutendex: CatalogSource,
    private val wolneLektury: CatalogSource,
    private val settings: PlaybackSettingsStore,
) : CatalogSource {

    override suspend fun featuredBooks(): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.featuredBooks() },
            lit2goCall = { lit2go.featuredBooks() },
            gutendexCall = { gutendex.featuredBooks() },
            wolneLekturyCall = { wolneLektury.featuredBooks() },
        )

    override suspend fun fetchByIds(vararg ids: String): List<AudioBook> {
        if (ids.isEmpty()) return emptyList()
        val lit2goIds = ids.filter { it.startsWith(LIT2GO_ID_PREFIX) }.toTypedArray()
        val gutendexIds = ids.filter { it.startsWith(GUTENDEX_ID_PREFIX) }.toTypedArray()
        val wolneLekturyIds = ids.filter { it.startsWith(WOLNE_LEKTURY_ID_PREFIX) }.toTypedArray()
        val librivoxIds = ids
            .filterNot {
                it.startsWith(LIT2GO_ID_PREFIX) ||
                    it.startsWith(GUTENDEX_ID_PREFIX) ||
                    it.startsWith(WOLNE_LEKTURY_ID_PREFIX)
            }
            .toTypedArray()
        return coroutineScope {
            val librivoxAsync = librivoxIds.takeIf { it.isNotEmpty() }?.let { sourceIds ->
                async { runCatching { librivox.fetchByIds(*sourceIds) }.getOrDefault(emptyList()) }
            }
            val lit2goAsync = lit2goIds.takeIf { it.isNotEmpty() }?.let { sourceIds ->
                async { runCatching { lit2go.fetchByIds(*sourceIds) }.getOrDefault(emptyList()) }
            }
            val gutendexAsync = gutendexIds.takeIf { it.isNotEmpty() }?.let { sourceIds ->
                async { runCatching { gutendex.fetchByIds(*sourceIds) }.getOrDefault(emptyList()) }
            }
            val wolneLekturyAsync = wolneLekturyIds.takeIf { it.isNotEmpty() }?.let { sourceIds ->
                async { runCatching { wolneLektury.fetchByIds(*sourceIds) }.getOrDefault(emptyList()) }
            }
            BookMerger.merge(
                librivoxAsync?.await().orEmpty(),
                lit2goAsync?.await().orEmpty(),
                wolneLekturyAsync?.await().orEmpty(),
                gutendexAsync?.await().orEmpty(),
            )
        }
    }

    override suspend fun search(
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.search(query, field, limit, offset, language) },
            lit2goCall = { lit2go.search(query, field, limit, offset, language) },
            gutendexCall = { gutendex.search(query, field, limit, offset, language) },
            wolneLekturyCall = { wolneLektury.search(query, field, limit, offset, language) },
        ).rankForCatalogSearch(query, field)

    override suspend fun browse(limit: Int, offset: Int, language: String): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.browse(limit, offset, language) },
            lit2goCall = { lit2go.browse(limit, offset, language) },
            gutendexCall = { gutendex.browse(limit, offset, language) },
            wolneLekturyCall = { wolneLektury.browse(limit, offset, language) },
        )

    suspend fun browseSource(
        source: CatalogSourcePreference,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        when (source) {
            CatalogSourcePreference.Lit2Go -> lit2go.browse(limit, offset, language)
            CatalogSourcePreference.LibriVox -> librivox.browse(limit, offset, language)
            CatalogSourcePreference.Gutendex -> gutendex.browse(limit, offset, language)
            CatalogSourcePreference.WolneLektury -> wolneLektury.browse(limit, offset, language)
        }

    suspend fun searchSource(
        source: CatalogSourcePreference,
        query: String,
        field: CatalogSearchField,
        limit: Int,
        offset: Int,
        language: String,
    ): List<AudioBook> =
        when (source) {
            CatalogSourcePreference.Lit2Go -> lit2go.search(query, field, limit, offset, language)
            CatalogSourcePreference.LibriVox -> librivox.search(query, field, limit, offset, language)
            CatalogSourcePreference.Gutendex -> gutendex.search(query, field, limit, offset, language)
            CatalogSourcePreference.WolneLektury -> wolneLektury.search(query, field, limit, offset, language)
        }

    suspend fun featuredBooksSource(source: CatalogSourcePreference): List<AudioBook> =
        sourceCallOrEmpty {
            when (source) {
                CatalogSourcePreference.Lit2Go -> lit2go.featuredBooks()
                CatalogSourcePreference.LibriVox -> librivox.featuredBooks()
                CatalogSourcePreference.Gutendex -> gutendex.featuredBooks()
                CatalogSourcePreference.WolneLektury -> wolneLektury.featuredBooks()
            }
        }

    override suspend fun recent(sinceEpochSeconds: Long, limit: Int): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.recent(sinceEpochSeconds, limit) },
            lit2goCall = { lit2go.recent(sinceEpochSeconds, limit) },
            gutendexCall = { gutendex.recent(sinceEpochSeconds, limit) },
            wolneLekturyCall = { wolneLektury.recent(sinceEpochSeconds, limit) },
        )

    override suspend fun byGenre(genre: String, limit: Int, language: String): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.byGenre(genre, limit, language) },
            lit2goCall = { lit2go.byGenre(genre, limit, language) },
            gutendexCall = { gutendex.byGenre(genre, limit, language) },
            wolneLekturyCall = { wolneLektury.byGenre(genre, limit, language) },
        )

    override suspend fun byAuthor(author: String, limit: Int, language: String): List<AudioBook> =
        fanOutSelected(
            librivoxCall = { librivox.byAuthor(author, limit, language) },
            lit2goCall = { lit2go.byAuthor(author, limit, language) },
            gutendexCall = { gutendex.byAuthor(author, limit, language) },
            wolneLekturyCall = { wolneLektury.byAuthor(author, limit, language) },
        )

    private suspend inline fun fanOutSelected(
        crossinline librivoxCall: suspend () -> List<AudioBook>,
        crossinline lit2goCall: suspend () -> List<AudioBook>,
        crossinline gutendexCall: suspend () -> List<AudioBook>,
        crossinline wolneLekturyCall: suspend () -> List<AudioBook>,
    ): List<AudioBook> = coroutineScope {
        val selected = BookSourcePreference
            .fromPreference(settings.bookSourcePreference.preferenceValue)
            .enabledSources
        val librivoxAsync: Deferred<List<AudioBook>>? = if (CatalogSourcePreference.LibriVox in selected) {
            async { sourceCallOrEmpty { librivoxCall() } }
        } else {
            null
        }
        val lit2goAsync: Deferred<List<AudioBook>>? = if (CatalogSourcePreference.Lit2Go in selected) {
            async { sourceCallOrEmpty { lit2goCall() } }
        } else {
            null
        }
        val gutendexAsync: Deferred<List<AudioBook>>? = if (CatalogSourcePreference.Gutendex in selected) {
            async { sourceCallOrEmpty { gutendexCall() } }
        } else {
            null
        }
        val wolneLekturyAsync: Deferred<List<AudioBook>>? = if (CatalogSourcePreference.WolneLektury in selected) {
            async { sourceCallOrEmpty { wolneLekturyCall() } }
        } else {
            null
        }
        BookMerger.merge(
            librivoxAsync?.await().orEmpty(),
            lit2goAsync?.await().orEmpty(),
            wolneLekturyAsync?.await().orEmpty(),
            gutendexAsync?.await().orEmpty(),
        )
    }

    private suspend inline fun sourceCallOrEmpty(
        crossinline block: suspend () -> List<AudioBook>,
    ): List<AudioBook> =
        runCatching { block() }.getOrDefault(emptyList())

    private companion object {
        const val LIT2GO_ID_PREFIX = "lit2go-"
        const val GUTENDEX_ID_PREFIX = "gutendex-"
        const val WOLNE_LEKTURY_ID_PREFIX = "wolnelektury-"
    }
}
