package com.librivox.mobile.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.audiobookLibraryDataStore by preferencesDataStore(name = "audiobook_library")

@Serializable
data class LibraryState(
    val books: List<AudioBook> = AudioBookLibrary.seededBooks,
    val updatedAtMillis: Long = 0L,
) {
    @Transient
    private val dedupedBooksCache: List<AudioBook> = books.dedupedByBookId()

    @Transient
    private val booksByIdCache: Map<String, AudioBook> = dedupedBooksCache.associateBy { it.id }

    val libraryBooks: List<AudioBook>
        get() = dedupedBooksCache.filter { it.libraryStatus == LibraryStatus.InLibrary }

    val featuredBooks: List<AudioBook>
        get() = AudioBookLibrary.featuredPlaceholders.map { placeholder ->
            booksByIdCache[placeholder.id] ?: placeholder
        }.filterNot { book ->
            book.id == AudioBookLibrary.PRIDE_AND_PREJUDICE_ID && book.chapters.isEmpty()
        }

    fun bookById(bookId: String?): AudioBook? =
        bookId?.let { booksByIdCache[it] }
}

class LibraryRepository(private val context: Context) {
    @Volatile
    private var latestState: LibraryState? = null

    val state: Flow<LibraryState> =
        context.audiobookLibraryDataStore.data
            .map { preferences ->
                decode(preferences[STATE_JSON]).withSeedDefaults().withLocalDownloadFiles().sanitized()
            }
            .catch {
                emit(LibraryState().withSeedDefaults().withLocalDownloadFiles().sanitized())
            }
            .onEach { latestState = it }

    suspend fun snapshot(): LibraryState =
        state.first()

    fun cachedState(): LibraryState? =
        latestState

    suspend fun upsertCatalogBooks(books: List<AudioBook>) {
        if (books.isEmpty()) return
        updateState { current ->
            current.copy(books = current.books.mergedWithIncoming(books))
        }
    }

    suspend fun addToLibrary(book: AudioBook) {
        updateState { current ->
            val now = System.currentTimeMillis()
            val merged = current.books.mergedWithIncoming(listOf(book))
            current.copy(
                books = merged.map {
                    if (it.id == book.id) {
                        it.withLibraryStatus(LibraryStatus.InLibrary)
                            .copy(addedAtMillis = it.addedAtMillis.takeIf { ts -> ts > 0L } ?: now)
                    } else {
                        it
                    }
                },
            )
        }
    }

    suspend fun addCustomBook(book: AudioBook) {
        updateState { current ->
            val now = System.currentTimeMillis()
            current.copy(
                books = current.books
                    .filterNot { it.id == book.id }
                    .plus(
                        book.withLibraryStatus(LibraryStatus.InLibrary, favorite = true)
                            .copy(addedAtMillis = now),
                    ),
            )
        }
    }

    suspend fun removeFromLibrary(bookId: String) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id == bookId) {
                        book.withLibraryStatus(LibraryStatus.NotInLibrary, favorite = false)
                    } else {
                        book
                    }
                },
            )
        }
    }

    suspend fun toggleFavorite(bookId: String) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id == bookId) {
                        book.copy(
                            libraryStatus = LibraryStatus.InLibrary,
                            isFavorite = !book.isFavorite,
                        )
                    } else {
                        book
                    }
                },
            )
        }
    }

    suspend fun updateChapterDownload(
        bookId: String,
        chapterId: String,
        downloadState: DownloadState,
        localFileName: String? = null,
        clearLocalFileName: Boolean = false,
    ) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id != bookId) {
                        book
                    } else {
                        book.copy(
                            libraryStatus = if (downloadState == DownloadState.NotDownloaded) {
                                book.libraryStatus
                            } else {
                                LibraryStatus.InLibrary
                            },
                            addedAtMillis = if (
                                downloadState == DownloadState.NotDownloaded ||
                                book.addedAtMillis > 0L
                            ) {
                                book.addedAtMillis
                            } else {
                                System.currentTimeMillis()
                            },
                            chapters = book.chapters.map { chapter ->
                                if (chapter.id != chapterId) {
                                    chapter
                                } else {
                                    chapter.copy(
                                        downloadState = downloadState,
                                        localFileName = when {
                                            clearLocalFileName -> null
                                            localFileName != null -> localFileName
                                            else -> chapter.localFileName
                                        },
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    suspend fun updateBookLocalCover(
        bookId: String,
        localCoverFileName: String,
        localCoverSourceUrl: String,
    ) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id != bookId) {
                        book
                    } else {
                        book.copy(
                            localCoverFileName = localCoverFileName,
                            localCoverSourceUrl = localCoverSourceUrl,
                        )
                    }
                },
            )
        }
    }

    suspend fun updateBookAudioEpubDownload(
        bookId: String,
        downloadState: DownloadState,
        localFileName: String? = null,
        sourceUrl: String? = null,
        clearLocalFileName: Boolean = false,
    ) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id != bookId) {
                        book
                    } else {
                        book.copy(
                            libraryStatus = if (downloadState == DownloadState.NotDownloaded) {
                                book.libraryStatus
                            } else {
                                LibraryStatus.InLibrary
                            },
                            addedAtMillis = if (
                                downloadState == DownloadState.NotDownloaded ||
                                book.addedAtMillis > 0L
                            ) {
                                book.addedAtMillis
                            } else {
                                System.currentTimeMillis()
                            },
                            audioEpubDownloadState = downloadState,
                            localAudioEpubFileName = when {
                                clearLocalFileName -> null
                                localFileName != null -> localFileName
                                else -> book.localAudioEpubFileName
                            },
                            localAudioEpubSourceUrl = when {
                                clearLocalFileName -> null
                                sourceUrl != null -> sourceUrl
                                else -> book.localAudioEpubSourceUrl
                            },
                        )
                    }
                },
            )
        }
    }

    suspend fun clearBookDownloads(bookId: String) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id != bookId) {
                        book
                    } else {
                        book.copy(
                            localCoverFileName = null,
                            localCoverSourceUrl = null,
                            localAudioEpubFileName = null,
                            localAudioEpubSourceUrl = null,
                            audioEpubDownloadState = DownloadState.NotDownloaded,
                            chapters = book.chapters.map { chapter ->
                                if (chapter.assetFileName == null) {
                                    chapter.copy(
                                        downloadState = DownloadState.NotDownloaded,
                                        localFileName = null,
                                    )
                                } else {
                                    chapter
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    suspend fun markMissingChaptersQueued(bookId: String, chapterIds: Set<String>) {
        updateState { current ->
            current.copy(
                books = current.books.map { book ->
                    if (book.id != bookId) {
                        book
                    } else {
                        book.copy(
                            libraryStatus = LibraryStatus.InLibrary,
                            addedAtMillis = book.addedAtMillis.takeIf { it > 0L }
                                ?: System.currentTimeMillis(),
                            chapters = book.chapters.map { chapter ->
                                if (chapter.id in chapterIds && chapter.localFileName == null) {
                                    chapter.copy(downloadState = DownloadState.Queued)
                                } else {
                                    chapter
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    private suspend fun updateState(transform: (LibraryState) -> LibraryState) {
        context.audiobookLibraryDataStore.edit { preferences ->
            val current = decode(preferences[STATE_JSON]).withSeedDefaults().withLocalDownloadFiles().sanitized()
            val next = transform(current)
                .withSeedDefaults()
                .withLocalDownloadFiles()
                .sanitized()
            if (next.copy(updatedAtMillis = current.updatedAtMillis) == current) {
                latestState = current
                return@edit
            }
            val stamped = next.copy(updatedAtMillis = System.currentTimeMillis())
            latestState = stamped
            preferences[STATE_JSON] = stamped.let { json.encodeToString(it) }
        }
    }

    private fun decode(raw: String?): LibraryState =
        raw?.let {
            runCatching { json.decodeFromString<LibraryState>(it) }.getOrNull()
        } ?: LibraryState()

    private fun LibraryState.withLocalDownloadFiles(): LibraryState =
        copy(books = books.map { book -> book.withDetectedLocalDownloads(context.filesDir) })
            .sanitized()

    private companion object {
        val STATE_JSON = stringPreferencesKey("library_state_json")
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

internal fun LibraryState.withSeedDefaults(): LibraryState {
    val hadLegacyStarter = books.any {
        it.id == AudioBookLibrary.LEGACY_CATHEDRAL_ID && it.source == BookSource.LocalAsset
    }
    val migratedBooks = books.filterNot {
        it.id == AudioBookLibrary.LEGACY_CATHEDRAL_ID && it.source == BookSource.LocalAsset
    }
    val seeded = migratedBooks.mergedWithIncoming(AudioBookLibrary.seededBooks)
    return copy(books = seeded.map { book ->
        when (book.id) {
            AudioBookLibrary.THE_JUNGLE_ID -> {
                if (hadLegacyStarter || book.libraryStatus == LibraryStatus.InLibrary) {
                    book.withLibraryStatus(LibraryStatus.InLibrary)
                        .copy(addedAtMillis = book.addedAtMillis.takeIf { it > 0L } ?: 1L)
                } else {
                    book
                }
            }
            AudioBookLibrary.PRIDE_AND_PREJUDICE_ID -> {
                if (book.chapters.isEmpty()) {
                    book.withLibraryStatus(LibraryStatus.NotInLibrary).copy(addedAtMillis = 0L)
                } else {
                    book
                }
            }
            else -> {
                if (book.hasDownloadedBookAssets() && book.source != BookSource.LocalAsset) {
                    book.withLibraryStatus(LibraryStatus.InLibrary)
                        .copy(addedAtMillis = book.addedAtMillis.takeIf { it > 0L } ?: 1L)
                } else {
                    book
                }
            }
        }
    }).sanitized()
}

internal fun LibraryState.sanitized(): LibraryState =
    copy(books = books.dedupedByBookId())
