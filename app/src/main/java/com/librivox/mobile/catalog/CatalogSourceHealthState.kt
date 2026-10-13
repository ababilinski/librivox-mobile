package com.librivox.mobile.catalog

import com.librivox.mobile.playback.BookSourcePreference
import com.librivox.mobile.playback.CatalogSourcePreference

enum class CatalogSourceHealth {
    Unknown,
    Checking,
    Available,
    Broken,
    Offline,
}

enum class CatalogSourceHealthFailureKind {
    Offline,
    HttpStatus,
    InvalidContent,
    Timeout,
    Network,
}

data class CatalogSourceHealthFailure(
    val kind: CatalogSourceHealthFailureKind,
    val message: String,
    val httpStatusCode: Int? = null,
)

data class CatalogSourceStatus(
    val source: CatalogSourcePreference,
    val state: CatalogSourceHealth = CatalogSourceHealth.Unknown,
    val rootUrl: String = DefaultCatalogSourceHealthEndpoints[source]?.rootUrl.orEmpty(),
    val message: String? = null,
    val lastCheckedAtMillis: Long = 0L,
    val failure: CatalogSourceHealthFailure? = null,
    /** True when connectivity was lost after the retained root check. */
    val isOffline: Boolean = false,
) {
    /** A source being checked remains usable. Only a confirmed broken root is excluded. */
    val isAvailable: Boolean
        get() = state != CatalogSourceHealth.Broken
}

data class CatalogSourceHealthState(
    val statuses: Map<CatalogSourcePreference, CatalogSourceStatus> =
        CatalogSourcePreference.ActiveSources.associateWith { CatalogSourceStatus(source = it) },
) {
    fun statusFor(source: CatalogSourcePreference): CatalogSourceStatus =
        statuses[source] ?: CatalogSourceStatus(source = source)

    fun isAvailable(source: CatalogSourcePreference): Boolean =
        source in CatalogSourcePreference.ActiveSources && statusFor(source).isAvailable

    fun unavailableSelectedSources(preference: BookSourcePreference): List<CatalogSourcePreference> =
        preference.enabledSources
            .filterNot { isAvailable(it) }
            .sortedBy { CatalogSourcePreference.ActiveSources.indexOf(it) }

    fun availablePreference(preference: BookSourcePreference): BookSourcePreference =
        BookSourcePreference.fromEnabledSources(
            preference.enabledSources.filter { isAvailable(it) }.toSet(),
        )

    fun withStatus(status: CatalogSourceStatus): CatalogSourceHealthState =
        copy(statuses = statuses + (status.source to status))

    fun markChecking(source: CatalogSourcePreference): CatalogSourceHealthState =
        withStatus(
            statusFor(source).copy(
                state = CatalogSourceHealth.Checking,
                message = null,
                failure = null,
                isOffline = false,
            ),
        )

    fun markAvailable(
        source: CatalogSourcePreference,
        nowMillis: Long = System.currentTimeMillis(),
    ): CatalogSourceHealthState =
        copy(
            statuses = statuses + (
                source to CatalogSourceStatus(
                    source = source,
                    state = CatalogSourceHealth.Available,
                    lastCheckedAtMillis = nowMillis,
                    isOffline = false,
                )
                ),
        )

    fun markUnavailable(
        source: CatalogSourcePreference,
        message: String?,
        nowMillis: Long = System.currentTimeMillis(),
        failure: CatalogSourceHealthFailure? = null,
    ): CatalogSourceHealthState =
        copy(
            statuses = statuses + (
                source to CatalogSourceStatus(
                    source = source,
                    state = CatalogSourceHealth.Broken,
                    message = message,
                    lastCheckedAtMillis = nowMillis,
                    failure = failure,
                    isOffline = false,
                )
                ),
        )
}
