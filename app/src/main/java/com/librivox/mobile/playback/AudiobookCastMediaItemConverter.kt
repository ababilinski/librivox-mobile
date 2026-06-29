package com.librivox.mobile.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.librivox.mobile.model.MEDIA_METADATA_BOOK_AUTHORS
import com.librivox.mobile.model.MEDIA_METADATA_BOOK_NARRATORS
import com.librivox.mobile.model.MEDIA_METADATA_CAST_CHAPTER_NUMBER
import com.librivox.mobile.model.MEDIA_METADATA_CAST_CONTENT_TYPE
import com.librivox.mobile.model.MEDIA_METADATA_CAST_DURATION_MS
import com.librivox.mobile.model.MEDIA_METADATA_CAST_URL
import com.librivox.mobile.model.MEDIA_METADATA_NARRATOR
import com.librivox.mobile.model.MEDIA_METADATA_PUBLISHER
import com.librivox.mobile.model.MEDIA_METADATA_RELEASE_DATE
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata

class AudiobookCastMediaItemConverter(
    private val localCastUrlProvider: (MediaItem) -> String? = { null },
    private val coverUrlProvider: (MediaItem) -> String? = { null },
    private val preloadTimeSeconds: () -> Double = { DEFAULT_PRELOAD_SECONDS },
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val extras = mediaItem.mediaMetadata.extras
        val localCastUrl = runCatching { localCastUrlProvider(mediaItem) }.getOrNull()
        val metadataCastUrl = extras?.getString(MEDIA_METADATA_CAST_URL)
        val castUrl = selectCastUrl(localCastUrl, metadataCastUrl)
            ?: return delegate.toMediaQueueItem(mediaItem)

        val contentType = extras?.getString(MEDIA_METADATA_CAST_CONTENT_TYPE)
            ?: mediaItem.localConfiguration?.mimeType
            ?: guessAudioContentType(castUrl)
        val durationMs = extras?.getLong(MEDIA_METADATA_CAST_DURATION_MS, 0L)?.coerceAtLeast(0L) ?: 0L
        val chapterNumber = extras?.getInt(MEDIA_METADATA_CAST_CHAPTER_NUMBER, 0)?.coerceAtLeast(0) ?: 0
        val cleanTitle = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "Chapter" }
        val bookTitle = mediaItem.mediaMetadata.albumTitle?.toString().orEmpty()
        val author = mediaItem.mediaMetadata.artist?.toString().orEmpty()
        val narrator = extras?.getString(MEDIA_METADATA_NARRATOR).orEmpty()
        val publisher = extras?.getString(MEDIA_METADATA_PUBLISHER).orEmpty()
        val releaseDate = extras?.getString(MEDIA_METADATA_RELEASE_DATE).orEmpty()
        val bookAuthors = extras?.getStringArray(MEDIA_METADATA_BOOK_AUTHORS)?.toList().orEmpty()
        val bookNarrators = extras?.getStringArray(MEDIA_METADATA_BOOK_NARRATORS)?.toList().orEmpty()

        val resolvedCoverUrl = runCatching { coverUrlProvider(mediaItem) }.getOrNull()
            ?: mediaItem.mediaMetadata.artworkUri?.takeIf { it.isRemoteHttpUri() }?.toString()

        val customData = JSONObject()
            .put(CUSTOM_DATA_MEDIA_ID, mediaItem.mediaId)
            .put(CUSTOM_DATA_BOOK_TITLE, bookTitle)
            .put(CUSTOM_DATA_CHAPTER_TITLE, cleanTitle)
            .put(CUSTOM_DATA_AUTHOR, author)
            .put(CUSTOM_DATA_CHAPTER_NUMBER, chapterNumber)
            .apply {
                val container = JSONObject().apply {
                    if (bookTitle.isNotBlank()) put("title", bookTitle)
                    if (bookAuthors.isNotEmpty()) put("authors", JSONArray(bookAuthors))
                    if (bookNarrators.isNotEmpty()) put("narrators", JSONArray(bookNarrators))
                    if (publisher.isNotBlank()) put("publisher", publisher)
                    if (releaseDate.isNotBlank()) put("releaseDate", releaseDate)
                }
                if (container.length() > 0) put(CUSTOM_DATA_CONTAINER, container)
            }

        val castMetadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_AUDIOBOOK_CHAPTER).apply {
            putString(CastMediaMetadata.KEY_TITLE, cleanTitle)
            putString(CastMediaMetadata.KEY_CHAPTER_TITLE, cleanTitle)
            if (bookTitle.isNotBlank()) {
                putString(CastMediaMetadata.KEY_SUBTITLE, bookTitle)
                putString(CastMediaMetadata.KEY_BOOK_TITLE, bookTitle)
                putString(CastMediaMetadata.KEY_ALBUM_TITLE, bookTitle)
            }
            if (author.isNotBlank()) {
                putString(CastMediaMetadata.KEY_ARTIST, author)
                putString(CastMediaMetadata.KEY_ALBUM_ARTIST, author)
            }
            if (narrator.isNotBlank()) {
                putString(CastMediaMetadata.KEY_COMPOSER, narrator)
            }
            if (releaseDate.isNotBlank()) {
                putString(CastMediaMetadata.KEY_RELEASE_DATE, releaseDate)
            }
            if (chapterNumber > 0) {
                putInt(CastMediaMetadata.KEY_CHAPTER_NUMBER, chapterNumber)
            }
            if (durationMs > 0L) {
                putTimeMillis(CastMediaMetadata.KEY_SECTION_DURATION, durationMs)
            }
            putTimeMillis(CastMediaMetadata.KEY_SECTION_START_TIME_IN_MEDIA, 0L)
            resolvedCoverUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { addImage(WebImage(Uri.parse(it))) }
        }

        val mediaInfoBuilder = MediaInfo.Builder(castUrl)
            .setContentUrl(castUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(castMetadata)
            .setCustomData(customData)
        if (durationMs > 0L) {
            mediaInfoBuilder.setStreamDuration(durationMs)
        }

        return MediaQueueItem.Builder(mediaInfoBuilder.build())
            .setAutoplay(true)
            .setPreloadTime(preloadTimeSeconds())
            .setCustomData(customData)
            .build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media ?: return delegate.toMediaItem(mediaQueueItem)
        val castUrl = mediaInfo.contentUrl?.takeIf { it.isNotBlank() }
            ?: mediaInfo.contentId.takeIf { it.isNotBlank() }
            ?: return delegate.toMediaItem(mediaQueueItem)
        val customData = mediaQueueItem.customData ?: mediaInfo.customData
        val castMetadata = mediaInfo.metadata
        val mediaId = customData?.optString(CUSTOM_DATA_MEDIA_ID).orEmpty().ifBlank { castUrl }
        val chapterTitle = castMetadata?.getString(CastMediaMetadata.KEY_CHAPTER_TITLE)
            ?: castMetadata?.getString(CastMediaMetadata.KEY_TITLE)
            ?: customData?.optString(CUSTOM_DATA_CHAPTER_TITLE)
        val bookTitle = castMetadata?.getString(CastMediaMetadata.KEY_BOOK_TITLE)
            ?: castMetadata?.getString(CastMediaMetadata.KEY_ALBUM_TITLE)
            ?: customData?.optString(CUSTOM_DATA_BOOK_TITLE)
        val author = castMetadata?.getString(CastMediaMetadata.KEY_ARTIST)
            ?: castMetadata?.getString(CastMediaMetadata.KEY_ALBUM_ARTIST)
            ?: customData?.optString(CUSTOM_DATA_AUTHOR)
        val artworkUri = castMetadata?.images?.firstOrNull()?.url
        val durationMs = mediaInfo.streamDuration.takeIf { it > 0L }
            ?: castMetadata?.getTimeMillis(CastMediaMetadata.KEY_SECTION_DURATION)?.takeIf { it > 0L }
            ?: 0L
        val chapterNumber = castMetadata?.getInt(CastMediaMetadata.KEY_CHAPTER_NUMBER)
            ?.takeIf { it > 0 }
            ?: customData?.optInt(CUSTOM_DATA_CHAPTER_NUMBER, 0)?.takeIf { it > 0 }

        val extras = Bundle().apply {
            putString(MEDIA_METADATA_CAST_URL, castUrl)
            putString(MEDIA_METADATA_CAST_CONTENT_TYPE, mediaInfo.contentType ?: guessAudioContentType(castUrl))
            putLong(MEDIA_METADATA_CAST_DURATION_MS, durationMs)
            if (chapterNumber != null) putInt(MEDIA_METADATA_CAST_CHAPTER_NUMBER, chapterNumber)
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(castUrl)
            .setMimeType(mediaInfo.contentType ?: guessAudioContentType(castUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(chapterTitle)
                    .setAlbumTitle(bookTitle)
                    .setArtist(author)
                    .setArtworkUri(artworkUri)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    private fun Uri.isRemoteHttpUri(): Boolean {
        val scheme = scheme?.lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun guessAudioContentType(url: String): String =
        when (url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()) {
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "oga",
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            else -> "audio/mpeg"
        }

    companion object {
        const val DEFAULT_PRELOAD_SECONDS = 20.0
        const val CUSTOM_DATA_MEDIA_ID = "mediaId"
        const val CUSTOM_DATA_BOOK_TITLE = "bookTitle"
        const val CUSTOM_DATA_CHAPTER_TITLE = "chapterTitle"
        const val CUSTOM_DATA_AUTHOR = "author"
        const val CUSTOM_DATA_CHAPTER_NUMBER = "chapterNumber"
        const val CUSTOM_DATA_CONTAINER = "container"
    }
}

internal fun castSubtitle(bookTitle: String, author: String): String =
    listOf(bookTitle, author)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" • ")

internal fun selectCastUrl(
    localCastUrl: String?,
    metadataCastUrl: String?,
): String? =
    localCastUrl?.takeIf { it.isRemoteHttpUrl() }
        ?: metadataCastUrl?.takeIf { it.isRemoteHttpUrl() }

private fun String.isRemoteHttpUrl(): Boolean {
    val value = trim().lowercase()
    return value.startsWith("http://") || value.startsWith("https://")
}
