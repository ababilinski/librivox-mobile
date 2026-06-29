package com.librivox.mobile

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.librivox.mobile.catalog.AggregateCatalogClient
import com.librivox.mobile.catalog.CatalogAutocompleteRefreshWorker
import com.librivox.mobile.catalog.CatalogAutocompleteRepository
import com.librivox.mobile.catalog.CatalogSource
import com.librivox.mobile.catalog.DiscoverPrefill
import com.librivox.mobile.catalog.DiscoverRepository
import com.librivox.mobile.catalog.GutendexCatalogSource
import com.librivox.mobile.catalog.LibriVoxCatalogSource
import com.librivox.mobile.catalog.Lit2GoCatalogSource
import com.librivox.mobile.catalog.WolneLekturyCatalogSource
import com.librivox.mobile.cast.diagnostics.CastDiagnostics
import com.librivox.mobile.cast.diagnostics.CastDiagnosticsLogger
import com.librivox.mobile.download.DownloadManager
import com.librivox.mobile.model.AudioBook
import com.librivox.mobile.model.AudioBookLibrary
import com.librivox.mobile.model.BookmarkRepository
import com.librivox.mobile.model.LibraryRepository
import com.librivox.mobile.model.ProfileRepository
import com.librivox.mobile.model.ProgressStore
import com.librivox.mobile.model.QueueRepository
import com.librivox.mobile.model.SearchHistoryRepository
import com.librivox.mobile.network.NetworkStatusMonitor
import com.librivox.mobile.playback.PlaybackBus
import com.librivox.mobile.playback.PlaybackFeedbackStore
import com.librivox.mobile.playback.PlaybackSettingsStore
import com.librivox.mobile.readalong.ReadAlongRepository
import com.librivox.mobile.ui.cast.CastRouteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val StagedBookDetailCacheLimit = 24
private const val StagedBookPersistDelayMillis = 1_500L
private const val StartupBackgroundWorkDelayMillis = 30_000L

class AudiobookApplication : Application(), Configuration.Provider {

    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var bookmarkRepository: BookmarkRepository
        private set
    lateinit var queueRepository: QueueRepository
        private set
    lateinit var searchHistoryRepository: SearchHistoryRepository
        private set
    lateinit var catalogAutocompleteRepository: CatalogAutocompleteRepository
        private set
    lateinit var progressStore: ProgressStore
        private set
    lateinit var playbackSettingsStore: PlaybackSettingsStore
        private set
    lateinit var playbackFeedbackStore: PlaybackFeedbackStore
        private set
    lateinit var catalogClient: CatalogSource
        private set
    lateinit var discoverRepository: DiscoverRepository
        private set
    lateinit var downloadManager: DownloadManager
        private set
    lateinit var readAlongRepository: ReadAlongRepository
        private set
    lateinit var castRouteRepository: CastRouteRepository
        private set
    lateinit var castDiagnostics: CastDiagnosticsLogger
        private set
    lateinit var networkStatusMonitor: NetworkStatusMonitor
        private set

    val playbackBus: PlaybackBus = PlaybackBus()
    val discoverPrefill: MutableStateFlow<DiscoverPrefill?> = MutableStateFlow(null)
    val stagedBookDetails: MutableStateFlow<Map<String, AudioBook>> = MutableStateFlow(emptyMap())
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    fun stageBookDetail(book: AudioBook) {
        stagedBookDetails.update { current ->
            buildMap {
                current.entries
                    .toList()
                    .takeLast((StagedBookDetailCacheLimit - 1).coerceAtLeast(0))
                    .forEach { (id, cachedBook) ->
                        if (id != book.id) put(id, cachedBook)
                    }
                put(book.id, book)
            }
        }
    }

    fun persistStagedBookDetail(
        book: AudioBook,
        delayMillis: Long = StagedBookPersistDelayMillis,
    ) {
        appScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            libraryRepository.upsertCatalogBooks(listOf(book))
        }
    }

    override fun onCreate() {
        super.onCreate()
        libraryRepository = LibraryRepository(this)
        profileRepository = ProfileRepository(this)
        bookmarkRepository = BookmarkRepository(this)
        queueRepository = QueueRepository(this)
        searchHistoryRepository = SearchHistoryRepository(this)
        playbackSettingsStore = PlaybackSettingsStore(this)
        networkStatusMonitor = NetworkStatusMonitor(this)
        catalogAutocompleteRepository = CatalogAutocompleteRepository(this, playbackSettingsStore)
        progressStore = ProgressStore(this)
        castDiagnostics = CastDiagnostics.install(this, playbackSettingsStore)
        playbackFeedbackStore = PlaybackFeedbackStore(this)
        val librivoxCatalog = LibriVoxCatalogSource(this)
        catalogClient = AggregateCatalogClient(
            librivox = librivoxCatalog,
            lit2go = Lit2GoCatalogSource(this),
            gutendex = GutendexCatalogSource(this),
            wolneLektury = WolneLekturyCatalogSource(this),
            settings = playbackSettingsStore,
        )
        discoverRepository = DiscoverRepository(
            scope = appScope,
            catalogClient = catalogClient,
            catalogCache = catalogAutocompleteRepository,
            libraryRepository = libraryRepository,
            settings = playbackSettingsStore,
        )
        downloadManager = DownloadManager(this, libraryRepository, playbackSettingsStore)
        readAlongRepository = ReadAlongRepository(this)
        castRouteRepository = CastRouteRepository.getInstance(this)
        scheduleStartupBackgroundWork(librivoxCatalog)
    }

    private fun scheduleStartupBackgroundWork(librivoxCatalog: CatalogSource) {
        appScope.launch {
            delay(StartupBackgroundWorkDelayMillis)
            CatalogAutocompleteRefreshWorker.setAutomaticRefreshEnabled(
                context = this@AudiobookApplication,
                enabled = playbackSettingsStore.automaticSearchCachingEnabled,
            )
            refreshSeededLibriVoxBooks(librivoxCatalog)
        }
    }

    private suspend fun refreshSeededLibriVoxBooks(librivoxCatalog: CatalogSource) {
        val books = runCatching {
            librivoxCatalog.fetchByIds(
                AudioBookLibrary.THE_JUNGLE_ID,
                AudioBookLibrary.PRIDE_AND_PREJUDICE_ID,
            )
        }.getOrDefault(emptyList())
        if (books.isEmpty()) return
        libraryRepository.upsertCatalogBooks(books)
        catalogAutocompleteRepository.upsertBooks(books)
    }
}

val Context.audiobookApp: AudiobookApplication
    get() = applicationContext as AudiobookApplication
