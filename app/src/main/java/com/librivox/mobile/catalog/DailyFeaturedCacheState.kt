package com.librivox.mobile.catalog

import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

data class DailyFeaturedCacheState(
    val dayKey: Long = Long.MIN_VALUE,
    val bookSource: BookSourcePreference = BookSourcePreference.Default,
    val books: List<AudioBook> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loadedAtMillis: Long = 0L,
    val featuredSource: CatalogSourcePreference? = null,
) {
    fun matches(
        dayKey: Long,
        bookSource: BookSourcePreference,
        featuredSource: CatalogSourcePreference? = this.featuredSource,
    ): Boolean =
        this.dayKey == dayKey &&
            this.bookSource == bookSource &&
            this.featuredSource == featuredSource
}

fun currentDailyFeaturedDayKey(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long =
    Instant.ofEpochMilli(nowMillis)
        .atZone(zoneId)
        .toLocalDate()
        .toEpochDay()

fun selectDailyFeaturedAudiobooks(
    candidates: List<AudioBook>,
    bookSource: BookSourcePreference,
    dayKey: Long,
    limit: Int = DEFAULT_DAILY_FEATURED_LIMIT,
    preserveSourceOrder: Boolean = false,
): List<AudioBook> {
    if (limit <= 0) return emptyList()
    val selected = candidates
        .asSequence()
        .filter { it.title.isNotBlank() }
        .distinctBy { it.id }
        .toList()
    if (preserveSourceOrder) {
        return selected.take(limit)
    }
    val seed = dailyFeaturedSeed(dayKey, bookSource)
    return selected
        .shuffled(Random(seed))
        .take(limit)
}

private fun dailyFeaturedSeed(dayKey: Long, bookSource: BookSourcePreference): Long =
    bookSource.preferenceValue.fold(dayKey) { acc, char ->
        acc * 31L + char.code
    }

fun BookSourcePreference.featuredCatalogSource(): CatalogSourcePreference? {
    val selected = enabledSources
    return PreferredFeaturedSources.firstOrNull { it in selected }
}

fun CatalogSourcePreference.asSingleSourcePreference(): BookSourcePreference =
    BookSourcePreference.fromEnabledSources(setOf(this))

private val PreferredFeaturedSources = listOf(
    CatalogSourcePreference.Lit2Go,
    CatalogSourcePreference.LibriVox,
    CatalogSourcePreference.WolneLektury,
    CatalogSourcePreference.Gutendex,
)

private const val DEFAULT_DAILY_FEATURED_LIMIT = 12
