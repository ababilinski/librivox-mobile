package com.librivox.mobile.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a content-based Material dynamic [ColorScheme] pair from a cover URL.
 *
 * Recipe:
 *   1. Decode bitmap via Coil, downscale to [SAMPLE_SIZE].
 *   2. Use Material Color Utilities quantization + scoring to choose a source color.
 *   3. Generate light + dark schemes using [SchemeContent], which is intended
 *      for content-based dynamic color sources such as artwork.
 *
 * Cached per cover URL via [AlbumArtColorCache].
 */
object AlbumArtColorExtractor {

    private const val SAMPLE_SIZE = 128

    data class Schemes(
        val light: ColorScheme,
        val dark: ColorScheme,
    )

    suspend fun extract(context: Context, coverUrl: String): Schemes? =
        withContext(Dispatchers.IO) {
            AlbumArtColorCache.get(coverUrl)?.let { return@withContext it }
            val bitmap = loadBitmap(context, coverUrl) ?: return@withContext null
            val seedArgb = extractSeedColor(bitmap)
                ?: return@withContext null
            val schemes = buildSchemes(seedArgb)
            AlbumArtColorCache.put(coverUrl, schemes)
            schemes
        }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        val loader = ImageLoader(context)
        return runCatching {
            val result = loader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
            )
            (result as? SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }

    private fun buildSchemes(seedArgb: Int): Schemes {
        val hct = Hct.fromInt(seedArgb)
        return Schemes(
            light = colorSchemeFromDynamicScheme(SchemeContent(hct, false, 0.0), darkTheme = false),
            dark = colorSchemeFromDynamicScheme(SchemeContent(hct, true, 0.0), darkTheme = true),
        )
    }

    private fun extractSeedColor(bitmap: Bitmap): Int? {
        val sampled = bitmap.sampledForColor()
        val pixels = IntArray(sampled.width * sampled.height)
        sampled.getPixels(pixels, 0, sampled.width, 0, 0, sampled.width, sampled.height)
        val opaquePixels = pixels.filter { AndroidColor.alpha(it) > 0x80 }.toIntArray()
        if (opaquePixels.isEmpty()) return null
        val colorsToPopulation = QuantizerCelebi.quantize(opaquePixels, 128)
        return Score.score(colorsToPopulation).firstOrNull()
    }

    private fun Bitmap.sampledForColor(): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= SAMPLE_SIZE) return this
        val scale = SAMPLE_SIZE.toFloat() / longestSide.toFloat()
        val targetWidth = maxOf(1, (width * scale).toInt())
        val targetHeight = maxOf(1, (height * scale).toInt())
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }
}

/**
 * Process-lifetime cache of [AlbumArtColorExtractor.Schemes] keyed by cover URL.
 * The LinkedHashMap eviction keeps the most recently accessed 64 entries.
 */
object AlbumArtColorCache {
    private const val MAX_ENTRIES = 64
    private val map: MutableMap<String, AlbumArtColorExtractor.Schemes> =
        object : LinkedHashMap<String, AlbumArtColorExtractor.Schemes>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, AlbumArtColorExtractor.Schemes>?,
            ): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(url: String): AlbumArtColorExtractor.Schemes? = map[url]

    @Synchronized
    fun put(url: String, schemes: AlbumArtColorExtractor.Schemes) {
        map[url] = schemes
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
