package com.librivox.mobile.catalog

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.librivox.mobile.AudiobookApplication
import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class CatalogAutocompleteRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AudiobookApplication ?: return Result.failure()
        if (!app.playbackSettingsStore.automaticSearchCachingEnabled) {
            return Result.success()
        }
        val repository = app.catalogAutocompleteRepository
        if (inputData.getBoolean(KEY_STARTUP_REFRESH, false)) {
            val lastCompletedAt = repository.lastRefreshCompletedAt()
            if (lastCompletedAt > 0L && System.currentTimeMillis() - lastCompletedAt < StartupThrottleMillis) {
                return Result.success()
            }
        }

        val sourcePreference = app.playbackSettingsStore.bookSourcePreference
        if (sourcePreference.enabledSources.isEmpty()) return Result.success()
        val aggregateClient = app.catalogClient as? AggregateCatalogClient ?: return Result.failure()
        val cacheSizeLimit = app.playbackSettingsStore.searchCacheSizeLimit
        var fetchedAny = false
        var failedAny = false

        for (source in sourcePreference.enabledSources) {
            val singleSourcePreference = BookSourcePreference.fromEnabledSources(setOf(source))
            val languages = app.playbackSettingsStore.preferredLanguages
                .catalogLanguageRequestFilters(singleSourcePreference)
            for (language in languages) {
                val syncKey = repository.refreshSyncKey(source, language)
                if (source == CatalogSourcePreference.WolneLektury) {
                    repository.markRefreshStarted(syncKey)
                    val page = runCatching {
                        aggregateClient.browseSource(
                            source = source,
                            limit = WolneMetadataPageSize,
                            offset = 0,
                            language = language,
                        )
                    }.getOrNull()
                    if (page == null) {
                        failedAny = true
                        repository.markRefreshFailed(syncKey)
                        continue
                    }
                    repository.upsertBooks(
                        books = page,
                        rankOffset = 0,
                    )
                    repository.markRefreshSucceeded(
                        syncKey = syncKey,
                        requestedOffset = 0,
                        fetchedCount = page.size,
                        pageSize = WolneMetadataPageSize,
                    )
                    repository.enforceSizeLimit(cacheSizeLimit.maxBytes)
                    fetchedAny = fetchedAny || page.isNotEmpty()
                    continue
                }

                var pagesFetched = 0
                while (pagesFetched < MaxPagesPerRun) {
                    val offset = repository.nextRefreshOffset(syncKey)
                    repository.markRefreshStarted(syncKey)
                    val page = runCatching {
                        aggregateClient.browseSource(
                            source = source,
                            limit = PageSize,
                            offset = offset,
                            language = language,
                        )
                    }.getOrNull()
                    if (page == null) {
                        failedAny = true
                        repository.markRefreshFailed(syncKey)
                        break
                    }
                    pagesFetched++
                    repository.upsertBooks(
                        books = page,
                        rankOffset = offset,
                    )
                    repository.markRefreshSucceeded(
                        syncKey = syncKey,
                        requestedOffset = offset,
                        fetchedCount = page.size,
                        pageSize = PageSize,
                    )
                    repository.enforceSizeLimit(cacheSizeLimit.maxBytes)
                    fetchedAny = fetchedAny || page.isNotEmpty()
                    if (page.size < PageSize) break
                    delay(InterPageDelayMillis)
                }
            }
        }

        return if (failedAny && !fetchedAny) Result.retry() else Result.success()
    }

    companion object {
        private const val KEY_STARTUP_REFRESH = "startup_refresh"
        private const val STARTUP_WORK_NAME = "catalog-autocomplete-startup"
        private const val PERIODIC_WORK_NAME = "catalog-autocomplete-periodic"
        private const val MANUAL_WORK_NAME = "catalog-autocomplete-manual"
        private const val PageSize = 24
        private const val WolneMetadataPageSize = Int.MAX_VALUE
        private const val MaxPagesPerRun = 2
        private const val InterPageDelayMillis = 500L
        private const val StartupThrottleMillis = 6L * 60L * 60L * 1000L

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            val app = appContext as? AudiobookApplication
            if (app != null && !app.playbackSettingsStore.automaticSearchCachingEnabled) {
                cancelAutomaticRefresh(appContext)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = PeriodicWorkRequestBuilder<CatalogAutocompleteRefreshWorker>(
                12,
                TimeUnit.HOURS,
                4,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic,
            )

            val startup = OneTimeWorkRequestBuilder<CatalogAutocompleteRefreshWorker>()
                .setConstraints(constraints)
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_STARTUP_REFRESH to true))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                STARTUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                startup,
            )
        }

        fun enqueueManualRefresh(context: Context) {
            val appContext = context.applicationContext
            val app = appContext as? AudiobookApplication
            if (app != null && !app.playbackSettingsStore.automaticSearchCachingEnabled) {
                cancelAutomaticRefresh(appContext)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CatalogAutocompleteRefreshWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                MANUAL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun setAutomaticRefreshEnabled(context: Context, enabled: Boolean) {
            if (enabled) {
                schedule(context)
            } else {
                cancelAutomaticRefresh(context)
            }
        }

        fun cancelAutomaticRefresh(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(STARTUP_WORK_NAME)
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(MANUAL_WORK_NAME)
        }
    }
}
