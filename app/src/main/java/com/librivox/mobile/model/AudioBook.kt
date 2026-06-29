package com.librivox.mobile.model

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.librivox.mobile.R
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class AudioBook(
    val id: String,
    val title: String,
    val author: String,
    val description: String = "",
    val source: BookSource = BookSource.LibriVox,
    val libraryStatus: LibraryStatus = LibraryStatus.NotInLibrary,
    val isFavorite: Boolean = false,
    val coverImageUrl: String? = null,
    val fullCoverImageUrl: String? = null,
    val localCoverFileName: String? = null,
    val localCoverSourceUrl: String? = null,
    val epubUrl: String? = null,
    val audioEpubUrl: String? = null,
    val daisyUrl: String? = null,
    val localAudioEpubFileName: String? = null,
    val localAudioEpubSourceUrl: String? = null,
    val audioEpubDownloadState: DownloadState = DownloadState.NotDownloaded,
    val fallbackCover: FallbackCover = FallbackCover.Generated,
    val chapters: List<AudioBookChapter> = emptyList(),
    val totalDurationSeconds: Long = chapters.sumOf { it.durationSeconds },
    val librivoxUrl: String? = null,
    val lit2goUrl: String? = null,
    val wolneLekturyUrl: String? = null,
    val gutenbergUrl: String? = null,
    val language: String? = null,
    val originalLanguage: String? = null,
    val translators: List<String> = emptyList(),
    val translationMetadataChecked: Boolean = false,
    val narrators: List<String> = emptyList(),
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genres: List<String> = emptyList(),
    val authorTags: List<CatalogTag> = emptyList(),
    val literaryEpochs: List<CatalogTag> = emptyList(),
    val literaryKinds: List<CatalogTag> = emptyList(),
    val literaryGenres: List<CatalogTag> = emptyList(),
    val sourcePopularityRank: Int? = null,
    val addedAtMillis: Long = 0L,
)

@Serializable
data class AudioBookChapter(
    val id: String,
    val title: String,
    val number: Int,
    val reader: String? = null,
    val director: String? = null,
    val durationSeconds: Long = 0L,
    val listenUrl: String? = null,
    val assetFileName: String? = null,
    val localFileName: String? = null,
    val mimeType: String = "audio/mpeg",
    val downloadState: DownloadState = DownloadState.NotDownloaded,
)

@Serializable
data class CatalogTag(
    val name: String,
    val slug: String = "",
)

@Serializable
enum class BookSource {
    LibriVox,
    Lit2Go,
    WolneLektury,
    Gutendex,
    LocalAsset,
    CustomLocal,
}

@Serializable
enum class LibraryStatus {
    NotInLibrary,
    InLibrary,
}

@Serializable
enum class DownloadState {
    NotDownloaded,
    Queued,
    Downloading,
    Downloaded,
    Failed,
}

@Serializable
enum class FallbackCover {
    Cathedral,
    Jungle,
    Pride,
    Generated,
}

data class MediaChapterIds(
    val bookId: String,
    val chapterId: String,
)

data class BookSourceAssetDownload(
    val url: String,
    val targetFileName: String,
    val primary: Boolean,
)

const val MEDIA_METADATA_CAST_URL = "com.librivox.mobile.CAST_URL"
const val MEDIA_METADATA_CAST_CONTENT_TYPE = "com.librivox.mobile.CAST_CONTENT_TYPE"
const val MEDIA_METADATA_CAST_DURATION_MS = "com.librivox.mobile.CAST_DURATION_MS"
const val MEDIA_METADATA_CAST_CHAPTER_NUMBER = "com.librivox.mobile.CAST_CHAPTER_NUMBER"
const val MEDIA_METADATA_NARRATOR = "com.librivox.mobile.NARRATOR"
const val MEDIA_METADATA_PUBLISHER = "com.librivox.mobile.PUBLISHER"
const val MEDIA_METADATA_RELEASE_DATE = "com.librivox.mobile.RELEASE_DATE"
const val MEDIA_METADATA_BOOK_AUTHORS = "com.librivox.mobile.BOOK_AUTHORS"
const val MEDIA_METADATA_BOOK_NARRATORS = "com.librivox.mobile.BOOK_NARRATORS"

object AudioBookLibrary {
    const val LEGACY_CATHEDRAL_ID = "cathedral"
    const val THE_JUNGLE_ID = "3269"
    const val PRIDE_AND_PREJUDICE_ID = "253"

    val jungle = AudioBook(
        id = THE_JUNGLE_ID,
        title = "The Jungle",
        author = "Upton Sinclair",
        description = "A LibriVox recording read by Tom Weiss. It is the end of the 19th century, and the Rudkus family has emigrated from Lithuania to Chicago in search of a better life. In Packingtown, the family faces poverty, unsafe work, exploitation, and the brutal realities of the meatpacking industry.",
        source = BookSource.LibriVox,
        libraryStatus = LibraryStatus.InLibrary,
        fallbackCover = FallbackCover.Jungle,
        totalDurationSeconds = 57_768,
        librivoxUrl = "https://librivox.org/the-jungle-by-upton-sinclair/",
        language = "English",
        chapters = listOf(
                AudioBookChapter(
                    id = "3269-182898",
                    title = "Chapter 1",
                    number = 1,
                    reader = "Tom Weiss",
                    durationSeconds = 3424,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_01_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182899",
                    title = "Chapter 2",
                    number = 2,
                    reader = "Tom Weiss",
                    durationSeconds = 1664,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_02_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182900",
                    title = "Chapter 3",
                    number = 3,
                    reader = "Tom Weiss",
                    durationSeconds = 1990,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_03_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182901",
                    title = "Chapter 4",
                    number = 4,
                    reader = "Tom Weiss",
                    durationSeconds = 2007,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_04_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182902",
                    title = "Chapter 5",
                    number = 5,
                    reader = "Tom Weiss",
                    durationSeconds = 1656,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_05_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182903",
                    title = "Chapter 6",
                    number = 6,
                    reader = "Tom Weiss",
                    durationSeconds = 1596,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_06_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182904",
                    title = "Chapter 7",
                    number = 7,
                    reader = "Tom Weiss",
                    durationSeconds = 1813,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_07_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182905",
                    title = "Chapter 8",
                    number = 8,
                    reader = "Tom Weiss",
                    durationSeconds = 1254,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_08_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182906",
                    title = "Chapter 9",
                    number = 9,
                    reader = "Tom Weiss",
                    durationSeconds = 1527,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_09_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182907",
                    title = "Chapter 10",
                    number = 10,
                    reader = "Tom Weiss",
                    durationSeconds = 1629,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_10_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182908",
                    title = "Chapter 11",
                    number = 11,
                    reader = "Tom Weiss",
                    durationSeconds = 1597,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_11_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182909",
                    title = "Chapter 12",
                    number = 12,
                    reader = "Tom Weiss",
                    durationSeconds = 1207,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_12_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182910",
                    title = "Chapter 13",
                    number = 13,
                    reader = "Tom Weiss",
                    durationSeconds = 1359,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_13_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182911",
                    title = "Chapter 14",
                    number = 14,
                    reader = "Tom Weiss",
                    durationSeconds = 1217,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_14_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182912",
                    title = "Chapter 15",
                    number = 15,
                    reader = "Tom Weiss",
                    durationSeconds = 2144,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_15_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182913",
                    title = "Chapter 16",
                    number = 16,
                    reader = "Tom Weiss",
                    durationSeconds = 1488,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_16_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182914",
                    title = "Chapter 17",
                    number = 17,
                    reader = "Tom Weiss",
                    durationSeconds = 1575,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_17_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182915",
                    title = "Chapter 18",
                    number = 18,
                    reader = "Tom Weiss",
                    durationSeconds = 1631,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_18_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182916",
                    title = "Chapter 19",
                    number = 19,
                    reader = "Tom Weiss",
                    durationSeconds = 1528,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_19_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182917",
                    title = "Chapter 20",
                    number = 20,
                    reader = "Tom Weiss",
                    durationSeconds = 1663,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_20_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182918",
                    title = "Chapter 21",
                    number = 21,
                    reader = "Tom Weiss",
                    durationSeconds = 1561,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_21_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182919",
                    title = "Chapter 22",
                    number = 22,
                    reader = "Tom Weiss",
                    durationSeconds = 1806,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_22_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182920",
                    title = "Chapter 23",
                    number = 23,
                    reader = "Tom Weiss",
                    durationSeconds = 1639,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_23_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182921",
                    title = "Chapter 24",
                    number = 24,
                    reader = "Tom Weiss",
                    durationSeconds = 1875,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_24_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182922",
                    title = "Chapter 25",
                    number = 25,
                    reader = "Tom Weiss",
                    durationSeconds = 3080,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_25_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182923",
                    title = "Chapter 26",
                    number = 26,
                    reader = "Tom Weiss",
                    durationSeconds = 2691,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_26_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182924",
                    title = "Chapter 27",
                    number = 27,
                    reader = "Tom Weiss",
                    durationSeconds = 2276,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_27_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182925",
                    title = "Chapter 28",
                    number = 28,
                    reader = "Tom Weiss",
                    durationSeconds = 2451,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_28_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182926",
                    title = "Chapter 29",
                    number = 29,
                    reader = "Tom Weiss",
                    durationSeconds = 1494,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_29_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182927",
                    title = "Chapter 30",
                    number = 30,
                    reader = "Tom Weiss",
                    durationSeconds = 2011,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_30_sinclair_64kb.mp3",
                ),
                AudioBookChapter(
                    id = "3269-182928",
                    title = "Chapter 31",
                    number = 31,
                    reader = "Tom Weiss",
                    durationSeconds = 2915,
                    listenUrl = "https://www.archive.org/download/jungle_tw_0908_librivox/thejungle_31_sinclair_64kb.mp3",
                ),
        ),
    )

    val prideAndPrejudice = AudioBook(
        id = PRIDE_AND_PREJUDICE_ID,
        title = "Pride and Prejudice",
        author = "Jane Austen",
        description = "",
        source = BookSource.LibriVox,
        libraryStatus = LibraryStatus.NotInLibrary,
        fallbackCover = FallbackCover.Pride,
        librivoxUrl = "https://librivox.org/pride-and-prejudice-by-jane-austen/",
        language = "English",
        chapters = emptyList(),
    )

    val featuredPlaceholders = listOf(
        jungle,
        prideAndPrejudice,
    )

    val defaultBook: AudioBook = jungle
    val seededBooks: List<AudioBook> = featuredPlaceholders

    fun byId(id: String?): AudioBook =
        seededBooks.firstOrNull { it.id == id } ?: defaultBook

    fun mediaItems(context: Context): List<MediaItem> =
        defaultBook.toMediaItems(context)
}

fun mediaId(bookId: String, chapterId: String): String = "$bookId::$chapterId"

fun parseMediaId(mediaId: String?): MediaChapterIds? {
    if (mediaId.isNullOrBlank()) {
        return null
    }
    val parts = mediaId.split("::", limit = 2)
    if (parts.size != 2 || parts.any { it.isBlank() }) {
        return MediaChapterIds(bookId = mediaId, chapterId = "")
    }
    return MediaChapterIds(bookId = parts[0], chapterId = parts[1])
}

fun AudioBook.withLibraryStatus(
    status: LibraryStatus,
    favorite: Boolean = isFavorite,
): AudioBook =
    copy(libraryStatus = status, isFavorite = favorite)

fun AudioBook.coverResId(): Int =
    fallbackCover.toDrawableRes()

fun FallbackCover.toDrawableRes(): Int =
    when (this) {
        FallbackCover.Cathedral -> R.drawable.cover_cathedral
        FallbackCover.Jungle -> R.drawable.cover_the_jungle
        FallbackCover.Pride -> R.drawable.cover_pride_and_prejudice
        FallbackCover.Generated -> R.drawable.cover_cathedral
    }

fun AudioBookChapter.downloadedFileName(bookId: String): String =
    "offline/${bookId.safePathSegment()}/${id.safePathSegment()}.mp3"

fun AudioBook.downloadedAudioEpubFileName(): String =
    downloadedReadAlongAssetFileName()

fun AudioBook.downloadedReadAlongAssetFileName(): String =
    downloadableReadAlongAssets()
        .firstOrNull { it.primary }
        ?.targetFileName
        ?: "offline/${id.safePathSegment()}/book.epub"

fun AudioBook.downloadableBookEpubUrl(): String? =
    epubUrl
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

fun AudioBook.downloadableAudioEpubUrl(): String? =
    audioEpubUrl
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

fun AudioBook.downloadableDaisyUrl(): String? =
    daisyUrl
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

fun AudioBook.downloadableSourceSyncAssetUrl(): String? =
    downloadableAudioEpubUrl() ?: downloadableDaisyUrl()

fun AudioBook.downloadableReadAlongAssetUrl(): String? =
    downloadableReadAlongAssets().firstOrNull { it.primary }?.url

fun AudioBook.downloadableReadAlongAssets(): List<BookSourceAssetDownload> {
    val offlinePath = "offline/${id.safePathSegment()}"
    val audioEpub = downloadableAudioEpubUrl()
    val daisy = downloadableDaisyUrl()
    val epub = downloadableBookEpubUrl()
    return buildList {
        audioEpub?.let {
            add(
                BookSourceAssetDownload(
                    url = it,
                    targetFileName = "$offlinePath/book.audio.epub",
                    primary = true,
                ),
            )
        }
        daisy?.let {
            add(
                BookSourceAssetDownload(
                    url = it,
                    targetFileName = "$offlinePath/book.daisy.zip",
                    primary = audioEpub == null,
                ),
            )
        }
        epub?.let {
            add(
                BookSourceAssetDownload(
                    url = it,
                    targetFileName = "$offlinePath/book.epub",
                    primary = audioEpub == null && daisy == null,
                ),
            )
        }
    }.distinctBy { it.targetFileName }
}

fun AudioBook.downloadableCoverImageUrl(): String? =
    remoteCoverImageUrl(preferFullQuality = true)
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

fun AudioBook.downloadedCoverFileName(): String {
    val coverUrl = downloadableCoverImageUrl()
    return "offline/${id.safePathSegment()}/cover-${coverUrl.coverSourceKey()}.${coverUrl.coverFileExtensionOrDefault()}"
}

fun AudioBook.remoteCoverImageUrl(preferFullQuality: Boolean = false): String? {
    val primary = if (preferFullQuality) fullCoverImageUrl ?: coverImageUrl else coverImageUrl ?: fullCoverImageUrl
    return primary?.trim()?.takeIf { it.isNotBlank() }
}

fun AudioBook.localCoverFile(filesDir: File): File? =
    localCoverFileName
        ?.let { File(filesDir, it) }
        ?.takeIf { it.isFile && it.length() > 0L }
        ?: detectedLocalCoverFileName(filesDir)
            ?.let { File(filesDir, it) }
            ?.takeIf { it.isFile && it.length() > 0L }

fun AudioBook.localAudioEpubFile(filesDir: File): File? =
    detectedLocalAudioEpubFileName(filesDir)
        ?.let { File(filesDir, it) }
        ?.takeIf { it.isFile && it.length() > 0L }
        ?: localAudioEpubFileName
            ?.let { File(filesDir, it) }
            ?.takeIf { it.isFile && it.length() > 0L }

fun AudioBook.toMediaItems(context: Context): List<MediaItem> =
    chapters.mapNotNull { chapter -> chapter.toMediaItem(context, this) }

fun AudioBook.chapterOrFirst(chapterId: String?): AudioBookChapter? =
    chapters.firstOrNull { it.id == chapterId } ?: chapters.firstOrNull()

fun AudioBook.chapterIndexOrZero(chapterId: String?): Int =
    chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: 0

fun AudioBookChapter.isPlayable(): Boolean =
    assetFileName != null || localFileName != null || listenUrl != null

fun AudioBookChapter.isDownloaded(): Boolean =
    downloadState == DownloadState.Downloaded || assetFileName != null || localFileName != null

fun AudioBook.canReadSourceText(): Boolean =
    source == BookSource.WolneLektury &&
        (id.startsWith("wolnelektury-") || !wolneLekturyUrl.isNullOrBlank() || hasKnownReadAlongAsset())

fun AudioBook.hasKnownReadAlongAsset(): Boolean =
    downloadableReadAlongAssets().isNotEmpty() || localAudioEpubFileName != null

fun AudioBook.hasDownloadedAudioEpub(): Boolean =
    audioEpubDownloadState == DownloadState.Downloaded || localAudioEpubFileName != null

fun AudioBook.downloadableChapterCount(): Int =
    chapters.count {
        it.assetFileName != null ||
            it.localFileName != null ||
            !it.listenUrl.isNullOrBlank() ||
            it.downloadState == DownloadState.Downloaded
    }

fun AudioBook.downloadedChapterCount(): Int =
    chapters.count { it.isDownloaded() }

fun AudioBook.downloadableBookAssetCount(): Int =
    downloadableChapterCount() + if (hasKnownReadAlongAsset()) 1 else 0

fun AudioBook.downloadedBookAssetCount(): Int =
    downloadedChapterCount() + if (hasKnownReadAlongAsset() && hasDownloadedAudioEpub()) 1 else 0

fun AudioBook.missingAudioDownloadCount(): Int =
    (downloadableChapterCount() - downloadedChapterCount()).coerceAtLeast(0)

fun AudioBook.missingDownloadAssetCount(): Int =
    (downloadableBookAssetCount() - downloadedBookAssetCount()).coerceAtLeast(0)

fun AudioBook.activeDownloadChapterCount(): Int =
    chapters.count {
        it.downloadState == DownloadState.Queued ||
            it.downloadState == DownloadState.Downloading
    }

fun AudioBook.activeDownloadBookAssetCount(): Int =
    activeDownloadChapterCount() + if (
        audioEpubDownloadState == DownloadState.Queued ||
        audioEpubDownloadState == DownloadState.Downloading
    ) {
        1
    } else {
        0
    }

fun AudioBook.hasDownloadedChapters(): Boolean =
    downloadedChapterCount() > 0

fun AudioBook.isAudioFullyDownloaded(): Boolean =
    downloadableChapterCount().let { total -> total > 0 && downloadedChapterCount() == total }

fun AudioBook.hasDownloadedBookAssets(): Boolean =
    downloadedBookAssetCount() > 0

fun AudioBook.isFullyDownloaded(): Boolean =
    downloadableBookAssetCount().let { total -> total > 0 && downloadedBookAssetCount() == total }

fun AudioBook.downloadProgressFraction(): Float {
    val total = downloadableBookAssetCount().takeIf { it > 0 } ?: return 0f
    return (downloadedBookAssetCount().toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun AudioBook.withDetectedLocalDownloads(filesDir: File): AudioBook {
    val detectedChapters = chapters.ifEmpty { detectedLocalDownloadChapters(filesDir) }
    val detectedLocalCoverFileName = localCoverFileName
        ?.takeIf { File(filesDir, it).isFile }
        ?: detectedLocalCoverFileName(filesDir)
    val detectedAudioEpubFileName = detectedLocalAudioEpubFileName(filesDir)
        ?: localAudioEpubFileName
            ?.takeIf { File(filesDir, it).isFile }
    return copy(
        localCoverFileName = detectedLocalCoverFileName,
        localCoverSourceUrl = localCoverSourceUrl.takeIf { detectedLocalCoverFileName != null },
        localAudioEpubFileName = detectedAudioEpubFileName,
        localAudioEpubSourceUrl = localAudioEpubSourceUrl.takeIf { detectedAudioEpubFileName != null },
        audioEpubDownloadState = if (detectedAudioEpubFileName != null) {
            DownloadState.Downloaded
        } else {
            audioEpubDownloadState
        },
        chapters = detectedChapters.map { chapter ->
            if (chapter.assetFileName != null || chapter.localFileName != null) {
                chapter
            } else {
                val downloadedFileName = chapter.downloadedFileName(id)
                val downloadedFile = File(filesDir, downloadedFileName)
                if (downloadedFile.isFile) {
                    chapter.copy(
                        localFileName = downloadedFileName,
                        downloadState = DownloadState.Downloaded,
                    )
                } else {
                    chapter
                }
            }
        },
    )
}

private fun AudioBook.detectedLocalAudioEpubFileName(filesDir: File): String? {
    val offlinePath = "offline/${id.safePathSegment()}"
    val candidates = listOf(
        File(filesDir, "$offlinePath/book.audio.epub"),
        File(filesDir, "$offlinePath/book.daisy.zip"),
        File(filesDir, "$offlinePath/book.epub"),
    )
    return candidates.firstOrNull { it.isFile && it.length() > 0L }
        ?.let { "$offlinePath/${it.name}" }
}

private fun AudioBook.detectedLocalDownloadChapters(filesDir: File): List<AudioBookChapter> {
    val offlineDirectory = File(filesDir, "offline/${id.safePathSegment()}")
    val downloadedFiles = offlineDirectory
        .listFiles { file -> file.isFile && file.extension.equals("mp3", ignoreCase = true) }
        ?.sortedWith(
            compareBy<File> { it.nameWithoutExtension.trailingNumber() ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )
        .orEmpty()

    return downloadedFiles.mapIndexed { index, file ->
        AudioBookChapter(
            id = file.nameWithoutExtension,
            title = title,
            number = file.nameWithoutExtension.trailingNumber() ?: index + 1,
            localFileName = "offline/${id.safePathSegment()}/${file.name}",
            downloadState = DownloadState.Downloaded,
        )
    }
}

private fun AudioBook.detectedLocalCoverFileName(filesDir: File): String? {
    val offlinePath = "offline/${id.safePathSegment()}"
    val offlineDirectory = File(filesDir, offlinePath)
    val expectedCover = File(offlineDirectory, "cover-${downloadableCoverImageUrl().coverSourceKey()}.${downloadableCoverImageUrl().coverFileExtensionOrDefault()}")
    if (expectedCover.isFile && expectedCover.length() > 0L) {
        return "$offlinePath/${expectedCover.name}"
    }
    offlineDirectory
        .listFiles { file ->
            file.isFile &&
                file.name.startsWith("cover-") &&
                file.extension.lowercase() in CoverImageExtensions
        }
        ?.maxByOrNull { it.lastModified() }
        ?.takeIf { it.length() > 0L }
        ?.let { return "$offlinePath/${it.name}" }
    return CoverImageExtensions
        .asSequence()
        .map { extension -> File(offlineDirectory, "cover.$extension") }
        .firstOrNull { it.isFile && it.length() > 0L }
        ?.let { "$offlinePath/${it.name}" }
}

fun AudioBookChapter.isCastable(localNetworkAvailable: Boolean = false): Boolean =
    (!listenUrl.isNullOrBlank() && isCastSupportedAudioType(mimeType, listenUrl)) ||
        (localNetworkAvailable && isLocalCastCandidate())

fun AudioBookChapter.isLocalCastCandidate(): Boolean {
    val localSource = localFileName ?: assetFileName ?: return false
    return isCastSupportedAudioType(mimeType, localSource)
}

fun AudioBookChapter.cleanTitle(): String =
    title.cleanedChapterTitle(fallback = "Chapter $number")

fun AudioBookChapter.numberedTitle(): String {
    return numberedChapterTitle(cleanTitle(), number)
}

fun MediaItem.chapterDisplayTitle(): String {
    val rawTitle = mediaMetadata.title?.toString().orEmpty()
    val number = mediaMetadata.extras?.getInt(MEDIA_METADATA_CAST_CHAPTER_NUMBER, 0) ?: 0
    return numberedChapterTitle(rawTitle, number)
}

fun String.cleanedChapterTitle(fallback: String = "Chapter"): String =
    replace(LeadingChapterDescriptorRegex, "")
        .replace(LeadingChapterNumberRegex, "")
        .trim()
        .ifBlank { fallback }

fun numberedChapterTitle(title: String, number: Int, fallback: String = "Chapter"): String {
    val cleanTitle = title.cleanedChapterTitle(fallback)
    if (number <= 0 || cleanTitle.hasLeadingChapterOrdinal()) return cleanTitle
    return "${number.toString().padStart(2, '0')}: $cleanTitle"
}

fun AudioBookChapter.toMediaItem(
    context: Context,
    book: AudioBook,
): MediaItem? {
    val uri = playableUri(context) ?: return null
    return MediaItem.Builder()
        .setMediaId(mediaId(book.id, id))
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(cleanTitle())
                .setArtist(book.author)
                .setAlbumTitle(book.title)
                .setArtworkUri(book.systemArtworkUri(context))
                .setExtras(castMetadataExtras(book))
                .build(),
        )
        .build()
}

private fun AudioBookChapter.castMetadataExtras(book: AudioBook): Bundle {
    val castUrl = listenUrl?.takeIf { isCastSupportedAudioType(mimeType, it) }
    return Bundle().apply {
        if (castUrl != null) {
            putString(MEDIA_METADATA_CAST_URL, castUrl)
        }
        putString(
            MEDIA_METADATA_CAST_CONTENT_TYPE,
            normalizedCastAudioMimeType(mimeType, castUrl ?: ""),
        )
        putLong(MEDIA_METADATA_CAST_DURATION_MS, durationSeconds.coerceAtLeast(0L) * 1_000L)
        putInt(MEDIA_METADATA_CAST_CHAPTER_NUMBER, number)
        reader?.takeIf { it.isNotBlank() }
            ?.let { putString(MEDIA_METADATA_NARRATOR, it) }
        book.publisher?.takeIf { it.isNotBlank() }
            ?.let { putString(MEDIA_METADATA_PUBLISHER, it) }
        book.releaseDate?.takeIf { it.isNotBlank() }
            ?.let { putString(MEDIA_METADATA_RELEASE_DATE, it) }
        if (book.author.isNotBlank()) {
            putStringArray(MEDIA_METADATA_BOOK_AUTHORS, arrayOf(book.author))
        }
        val narratorList = (book.narrators + listOfNotNull(reader))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (narratorList.isNotEmpty()) {
            putStringArray(MEDIA_METADATA_BOOK_NARRATORS, narratorList.toTypedArray())
        }
    }
}

fun AudioBook.coverUri(context: Context): Uri =
    localCoverFile(context.filesDir)?.toUri()
        ?: remoteCoverImageUrl(preferFullQuality = true)?.toUri()
        ?: coverUri(context, coverResId())

fun AudioBook.systemArtworkUri(context: Context): Uri =
    localCoverFile(context.filesDir)?.toUri()
        ?: remoteCoverImageUrl(preferFullQuality = true)?.toUri()
        ?: coverUri(context, coverResId())

fun AudioBookChapter.playableUri(context: Context): Uri? =
    when {
        localFileName != null -> File(context.filesDir, localFileName).toUri()
        assetFileName != null -> Uri.Builder()
            .scheme("asset")
            .authority("")
            .appendPath("audiobooks")
            .appendPath(assetFileName)
            .build()
        listenUrl != null -> listenUrl.toUri()
        else -> null
    }

fun MediaItem.isCastableAudiobookItem(localNetworkAvailable: Boolean = false): Boolean {
    val localConfig = localConfiguration ?: return false
    val uri = localConfig.uri
    val scheme = uri.scheme?.lowercase()
    return when (scheme) {
        "http",
        "https" -> isCastSupportedAudioType(localConfig.mimeType, uri.toString())
        "asset",
        "file" -> localNetworkAvailable && isCastSupportedAudioType(localConfig.mimeType, uri.toString())
        else -> false
    }
}

fun MediaItem.isLocalCastCandidateAudiobookItem(): Boolean {
    val localConfig = localConfiguration ?: return false
    val uri = localConfig.uri
    val scheme = uri.scheme?.lowercase()
    return (scheme == "asset" || scheme == "file") &&
        isCastSupportedAudioType(localConfig.mimeType, uri.toString())
}

fun List<AudioBook>.mergedWithIncoming(incoming: List<AudioBook>): List<AudioBook> {
    val mergedById = LinkedHashMap<String, AudioBook>()
    dedupedByBookId().forEach { book ->
        mergedById[book.id] = book
    }
    incoming.dedupedByBookId().forEach { next ->
        val existing = mergedById[next.id]
        mergedById[next.id] = if (existing == null) {
            next
        } else {
            existing.mergeIncomingBook(next)
        }
    }
    return mergedById.values
        .sortedWith(compareBy<AudioBook> { it.source.ordinal }.thenBy { it.title })
}

fun List<AudioBook>.dedupedByBookId(): List<AudioBook> {
    val deduped = LinkedHashMap<String, AudioBook>()
    forEach { next ->
        val existing = deduped[next.id]
        deduped[next.id] = if (existing == null) {
            next
        } else {
            existing.mergeDuplicateBook(next)
        }
    }
    return deduped.values.toList()
}

private fun AudioBook.mergeIncomingBook(next: AudioBook): AudioBook {
    val clearsStalePridePreview = next.isEmptyPridePlaceholder()
    val mergedChapters = when {
        clearsStalePridePreview -> emptyList()
        next.chapters.isEmpty() -> chapters.dedupedByChapterId()
        else -> next.chapters.mergeChapterState(chapters)
    }
    return next.copy(
        libraryStatus = if (clearsStalePridePreview) next.libraryStatus else libraryStatus,
        isFavorite = isFavorite,
        chapters = mergedChapters,
        totalDurationSeconds = next.totalDurationSeconds
            .takeIf { it > 0L }
            ?: mergedChapters.sumOf { it.durationSeconds }
                .takeIf { it > 0L }
            ?: totalDurationSeconds,
        addedAtMillis = addedAtMillis.coerceAtLeast(next.addedAtMillis),
        coverImageUrl = next.coverImageUrl ?: coverImageUrl,
        fullCoverImageUrl = next.fullCoverImageUrl ?: fullCoverImageUrl,
        localCoverFileName = localCoverFileName ?: next.localCoverFileName,
        localCoverSourceUrl = localCoverSourceUrl ?: next.localCoverSourceUrl,
        epubUrl = next.epubUrl ?: epubUrl,
        audioEpubUrl = next.audioEpubUrl ?: audioEpubUrl,
        daisyUrl = next.daisyUrl ?: daisyUrl,
        localAudioEpubFileName = localAudioEpubFileName ?: next.localAudioEpubFileName,
        localAudioEpubSourceUrl = localAudioEpubSourceUrl ?: next.localAudioEpubSourceUrl,
        audioEpubDownloadState = when {
            localAudioEpubFileName != null || next.localAudioEpubFileName != null -> DownloadState.Downloaded
            audioEpubDownloadState != DownloadState.NotDownloaded -> audioEpubDownloadState
            else -> next.audioEpubDownloadState
        },
        language = next.language ?: language,
        originalLanguage = next.originalLanguage ?: originalLanguage,
        translators = next.translators.ifEmpty { translators },
        translationMetadataChecked = translationMetadataChecked || next.translationMetadataChecked,
        genres = next.genres.ifEmpty { genres },
        authorTags = next.authorTags.ifEmpty { authorTags },
        literaryEpochs = next.literaryEpochs.ifEmpty { literaryEpochs },
        literaryKinds = next.literaryKinds.ifEmpty { literaryKinds },
        literaryGenres = next.literaryGenres.ifEmpty { literaryGenres },
        librivoxUrl = next.librivoxUrl ?: librivoxUrl,
        lit2goUrl = next.lit2goUrl ?: lit2goUrl,
        wolneLekturyUrl = next.wolneLekturyUrl ?: wolneLekturyUrl,
        gutenbergUrl = next.gutenbergUrl ?: gutenbergUrl,
    )
}

private fun AudioBook.mergeDuplicateBook(next: AudioBook): AudioBook {
    val clearsStalePridePreview = next.isEmptyPridePlaceholder()
    val mergedChapters = when {
        clearsStalePridePreview -> emptyList()
        next.chapters.isEmpty() -> chapters.dedupedByChapterId()
        else -> next.chapters.mergeChapterStatePreservingDownloads(chapters)
    }
    return next.copy(
        libraryStatus = when {
            clearsStalePridePreview -> next.libraryStatus
            libraryStatus == LibraryStatus.InLibrary || next.libraryStatus == LibraryStatus.InLibrary ->
                LibraryStatus.InLibrary
            else -> LibraryStatus.NotInLibrary
        },
        isFavorite = isFavorite || next.isFavorite,
        chapters = mergedChapters,
        totalDurationSeconds = next.totalDurationSeconds
            .coerceAtLeast(totalDurationSeconds)
            .takeIf { it > 0L }
            ?: mergedChapters.sumOf { it.durationSeconds },
        addedAtMillis = addedAtMillis.coerceAtLeast(next.addedAtMillis),
        coverImageUrl = next.coverImageUrl ?: coverImageUrl,
        fullCoverImageUrl = next.fullCoverImageUrl ?: fullCoverImageUrl,
        localCoverFileName = localCoverFileName ?: next.localCoverFileName,
        localCoverSourceUrl = localCoverSourceUrl ?: next.localCoverSourceUrl,
        epubUrl = next.epubUrl ?: epubUrl,
        audioEpubUrl = next.audioEpubUrl ?: audioEpubUrl,
        daisyUrl = next.daisyUrl ?: daisyUrl,
        localAudioEpubFileName = localAudioEpubFileName ?: next.localAudioEpubFileName,
        localAudioEpubSourceUrl = localAudioEpubSourceUrl ?: next.localAudioEpubSourceUrl,
        audioEpubDownloadState = when {
            localAudioEpubFileName != null || next.localAudioEpubFileName != null -> DownloadState.Downloaded
            audioEpubDownloadState != DownloadState.NotDownloaded -> audioEpubDownloadState
            else -> next.audioEpubDownloadState
        },
        language = next.language ?: language,
        originalLanguage = next.originalLanguage ?: originalLanguage,
        translators = next.translators.ifEmpty { translators },
        translationMetadataChecked = translationMetadataChecked || next.translationMetadataChecked,
        genres = next.genres.ifEmpty { genres },
        authorTags = next.authorTags.ifEmpty { authorTags },
        literaryEpochs = next.literaryEpochs.ifEmpty { literaryEpochs },
        literaryKinds = next.literaryKinds.ifEmpty { literaryKinds },
        literaryGenres = next.literaryGenres.ifEmpty { literaryGenres },
        librivoxUrl = next.librivoxUrl ?: librivoxUrl,
        lit2goUrl = next.lit2goUrl ?: lit2goUrl,
        wolneLekturyUrl = next.wolneLekturyUrl ?: wolneLekturyUrl,
        gutenbergUrl = next.gutenbergUrl ?: gutenbergUrl,
    )
}

private fun AudioBook.isEmptyPridePlaceholder(): Boolean =
    id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID && chapters.isEmpty()

private fun List<AudioBookChapter>.mergeChapterState(existing: List<AudioBookChapter>): List<AudioBookChapter> {
    val existingById = existing.dedupedByChapterId().associateBy { it.id }
    return dedupedByChapterId().map { next ->
        val old = existingById[next.id]
        if (old == null) {
            next
        } else {
            next.copy(
                localFileName = old.localFileName ?: next.localFileName,
                downloadState = when {
                    old.localFileName != null -> DownloadState.Downloaded
                    old.downloadState != DownloadState.NotDownloaded -> old.downloadState
                    else -> next.downloadState
                },
            )
        }
    }
}

private fun List<AudioBookChapter>.mergeChapterStatePreservingDownloads(
    existing: List<AudioBookChapter>,
): List<AudioBookChapter> {
    val merged = mergeChapterState(existing)
    val mergedIds = merged.mapTo(mutableSetOf()) { it.id }
    val missingDownloadedChapters = existing.dedupedByChapterId()
        .filter { it.id !in mergedIds && it.isDownloaded() }
    return merged + missingDownloadedChapters
}

private fun List<AudioBookChapter>.dedupedByChapterId(): List<AudioBookChapter> {
    val deduped = LinkedHashMap<String, AudioBookChapter>()
    forEach { next ->
        val existing = deduped[next.id]
        deduped[next.id] = if (existing == null) {
            next
        } else {
            existing.mergeDuplicateChapter(next)
        }
    }
    return deduped.values.toList()
}

private fun AudioBookChapter.mergeDuplicateChapter(next: AudioBookChapter): AudioBookChapter =
    next.copy(
        localFileName = localFileName ?: next.localFileName,
        downloadState = when {
            localFileName != null || next.localFileName != null -> DownloadState.Downloaded
            downloadState != DownloadState.NotDownloaded -> downloadState
            else -> next.downloadState
        },
    )

private fun coverUri(context: Context, @DrawableRes coverResId: Int): Uri =
    "android.resource://${context.packageName}/$coverResId".toUri()

private val LeadingChapterDescriptorRegex = Regex(
    pattern = """^\s*(?:(?:rozdział)(?:\s+[\p{L}\p{M}]+)?|chapter)\s*[:.]\s*""",
    option = RegexOption.IGNORE_CASE,
)
private val LeadingChapterNumberRegex = Regex("""^\s*\d{1,3}\s*[:.\-]\s*""")
private val LeadingArabicNumberRegex = Regex("""^\s*\d+\b""")
private val LeadingRomanNumeralRegex = Regex(
    pattern = """^\s*[ivxlcdm]{1,8}\s*[:.\-)]\s+""",
    option = RegexOption.IGNORE_CASE,
)
private val LeadingPartTitleRegex = Regex(
    pattern = """^\s*(?:part|udział|udzial)(?=\s|$)""",
    option = RegexOption.IGNORE_CASE,
)

private fun String.hasLeadingChapterOrdinal(): Boolean =
    LeadingArabicNumberRegex.containsMatchIn(this) ||
        LeadingRomanNumeralRegex.containsMatchIn(this) ||
        LeadingPartTitleRegex.containsMatchIn(this)

private val CastSupportedAudioMimeTypes = setOf(
    "audio/aac",
    "audio/flac",
    "audio/mp3",
    "audio/mp4",
    "audio/mpeg",
    "audio/ogg",
    "audio/opus",
    "audio/wav",
    "audio/webm",
    "audio/x-flac",
    "audio/x-m4a",
    "audio/x-wav",
)

private val CastSupportedAudioExtensions = setOf(
    "aac",
    "flac",
    "m4a",
    "mp3",
    "oga",
    "ogg",
    "opus",
    "wav",
    "webm",
)

private val CoverImageExtensions = listOf("jpg", "jpeg", "png", "webp")

private fun String?.coverFileExtensionOrDefault(): String {
    val extension = this
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        .orEmpty()
    return extension.takeIf { it in CoverImageExtensions } ?: "jpg"
}

private fun String?.coverSourceKey(): String =
    takeUnless { it.isNullOrBlank() }
        ?.let { Integer.toHexString(it.hashCode()) }
        ?: "default"

private fun isCastSupportedAudioType(mimeType: String?, source: String?): Boolean {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (normalizedMime in CastSupportedAudioMimeTypes) return true

    val path = source
        ?.substringBefore('?')
        ?.substringBefore('#')
        ?.lowercase()
        .orEmpty()
    val extension = path.substringAfterLast('.', missingDelimiterValue = "")
    return extension in CastSupportedAudioExtensions
}

private fun normalizedCastAudioMimeType(mimeType: String?, source: String): String {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        .orEmpty()
    if (normalizedMime in CastSupportedAudioMimeTypes) return normalizedMime

    return when (source.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()) {
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
}

private fun String.safePathSegment(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "item" }

private fun String.trailingNumber(): Int? =
    TrailingNumberRegex.find(this)?.value?.toIntOrNull()

private val TrailingNumberRegex = Regex("""\d+$""")
