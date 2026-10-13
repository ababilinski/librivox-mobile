package com.librivox.mobile.catalog

import com.librivox.mobile.playback.CatalogSourcePreference
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

data class CatalogSourceHealthEndpoint(
    val source: CatalogSourcePreference,
    val rootUrl: String,
    /** At least one marker must occur in the root page title. */
    val expectedTitleMarkers: Set<String>,
    /** A branded proxy/outage page can still mention the source name, so reject these first. */
    val outageContentMarkers: Set<String> = DefaultCatalogOutageContentMarkers,
)

private val DefaultCatalogOutageContentMarkers: Set<String> = setOf(
    "bad gateway",
    "gateway timeout",
    "internal server error",
    "service unavailable",
    "temporarily unavailable",
    "website is down",
    "origin is unreachable",
    "error 500",
    "error 502",
    "error 503",
    "error 504",
)

val DefaultCatalogSourceHealthEndpoints: Map<CatalogSourcePreference, CatalogSourceHealthEndpoint> =
    listOf(
        CatalogSourceHealthEndpoint(
            source = CatalogSourcePreference.LibriVox,
            rootUrl = "https://librivox.org/",
            expectedTitleMarkers = setOf("librivox"),
        ),
        CatalogSourceHealthEndpoint(
            source = CatalogSourcePreference.Lit2Go,
            rootUrl = "https://etc.usf.edu/lit2go/",
            expectedTitleMarkers = setOf("lit2go"),
        ),
        CatalogSourceHealthEndpoint(
            source = CatalogSourcePreference.WolneLektury,
            rootUrl = "https://wolnelektury.pl/",
            expectedTitleMarkers = setOf("wolnelektury", "wolne lektury"),
        ),
    ).associateBy(CatalogSourceHealthEndpoint::source)

/**
 * Probes source home pages independently from catalog field requests.
 *
 * A healthy root does not hide a failed search field, and a successful field request does not
 * overwrite a broken-root result. Successful and failed probes are briefly cached so debounced
 * searches do not hammer catalog home pages.
 */
class CatalogSourceHealthProbe(
    private val transport: CatalogHttpTransport,
    private val clock: CatalogClock = CatalogClock(System::currentTimeMillis),
    private val connectivity: CatalogConnectivity = CatalogConnectivity { true },
    private val endpoints: Map<CatalogSourcePreference, CatalogSourceHealthEndpoint> =
        DefaultCatalogSourceHealthEndpoints,
    private val availableFreshMillis: Long = CatalogSourceHealthAvailableFreshMillis,
    private val brokenFreshMillis: Long = CatalogSourceHealthBrokenFreshMillis,
) {
    private val cachedStatuses = ConcurrentHashMap<CatalogSourcePreference, CatalogSourceStatus>()
    private val sourceLocks = ConcurrentHashMap<CatalogSourcePreference, Mutex>()

    suspend fun probe(
        sources: Collection<CatalogSourcePreference>,
        force: Boolean = false,
        onStatus: suspend (CatalogSourceStatus) -> Unit = {},
    ): Map<CatalogSourcePreference, CatalogSourceStatus> = supervisorScope {
        sources
            .filter { it in CatalogSourcePreference.ActiveSources }
            .distinct()
            .map { source ->
                async {
                    source to probeSource(source, force, onStatus)
                }
            }
            .awaitAll()
            .toMap()
    }

    suspend fun probeSource(
        source: CatalogSourcePreference,
        force: Boolean = false,
        onStatus: suspend (CatalogSourceStatus) -> Unit = {},
    ): CatalogSourceStatus {
        if (source !in CatalogSourcePreference.ActiveSources) {
            return CatalogSourceStatus(
                source = source,
                state = CatalogSourceHealth.Broken,
                message = "This catalog is no longer available in the app.",
            )
        }
        val endpoint = requireNotNull(endpoints[source]) {
            "No root health endpoint is configured for ${source.name}."
        }
        return sourceLocks.getOrPut(source, ::Mutex).withLock {
            val now = clock.nowMillis()
            if (!connectivity.isOnline()) {
                val retained = cachedStatuses[source]
                val offline = retained?.copy(
                    state = CatalogSourceHealth.Offline,
                    message = "Root page not checked while offline",
                    failure = CatalogSourceHealthFailure(
                        kind = CatalogSourceHealthFailureKind.Offline,
                        message = "No internet connection",
                    ),
                    isOffline = true,
                ) ?: CatalogSourceStatus(
                    source = source,
                    state = CatalogSourceHealth.Offline,
                    rootUrl = endpoint.rootUrl,
                    message = "Root page not checked while offline",
                    failure = CatalogSourceHealthFailure(
                        kind = CatalogSourceHealthFailureKind.Offline,
                        message = "No internet connection",
                    ),
                    isOffline = true,
                )
                onStatus(offline)
                return@withLock offline
            }

            cachedStatuses[source]
                ?.takeUnless { force }
                ?.takeIf { it.isFresh(now) }
                ?.let { cached ->
                    onStatus(cached)
                    return@withLock cached
                }

            onStatus(
                CatalogSourceStatus(
                    source = source,
                    state = CatalogSourceHealth.Checking,
                    rootUrl = endpoint.rootUrl,
                    isOffline = false,
                ),
            )

            val status = try {
                val response = transport.execute(
                    CatalogHttpRequest(
                        url = endpoint.rootUrl,
                        headers = mapOf(
                            "Accept" to "text/html,application/xhtml+xml",
                            "User-Agent" to "LibriVoxMobile/1.0 catalog-health",
                        ),
                    ),
                )
                response.toHealthStatus(endpoint, clock.nowMillis())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (timeout: SocketTimeoutException) {
                uncertainStatus(
                    endpoint = endpoint,
                    failure = CatalogSourceHealthFailure(
                        kind = CatalogSourceHealthFailureKind.Timeout,
                        message = "Root page is responding slowly",
                    ),
                    checkedAtMillis = clock.nowMillis(),
                )
            } catch (interrupted: InterruptedIOException) {
                if (interrupted.message?.contains("timeout", ignoreCase = true) == true) {
                    uncertainStatus(
                        endpoint = endpoint,
                        failure = CatalogSourceHealthFailure(
                            kind = CatalogSourceHealthFailureKind.Timeout,
                            message = "Root page is responding slowly",
                        ),
                        checkedAtMillis = clock.nowMillis(),
                    )
                } else {
                    brokenStatus(
                        endpoint = endpoint,
                        failure = CatalogSourceHealthFailure(
                            kind = CatalogSourceHealthFailureKind.Network,
                            message = interrupted.message?.takeIf(String::isNotBlank)
                                ?: "Could not reach the root page",
                        ),
                        checkedAtMillis = clock.nowMillis(),
                    )
                }
            } catch (throwable: Throwable) {
                brokenStatus(
                    endpoint = endpoint,
                    failure = CatalogSourceHealthFailure(
                        kind = CatalogSourceHealthFailureKind.Network,
                        message = throwable.message?.takeIf(String::isNotBlank)
                            ?: "Could not reach the root page",
                    ),
                    checkedAtMillis = clock.nowMillis(),
                )
            }
            cachedStatuses[source] = status
            onStatus(status)
            status
        }
    }

    fun invalidate(sources: Collection<CatalogSourcePreference> = CatalogSourcePreference.ActiveSources) {
        sources.forEach(cachedStatuses::remove)
    }

    private fun CatalogSourceStatus.isFresh(nowMillis: Long): Boolean {
        if (lastCheckedAtMillis <= 0L) return false
        val maxAge = when (state) {
            CatalogSourceHealth.Available -> availableFreshMillis
            CatalogSourceHealth.Broken -> brokenFreshMillis
            CatalogSourceHealth.Unknown -> brokenFreshMillis
            CatalogSourceHealth.Checking,
            CatalogSourceHealth.Offline -> return false
        }
        return (nowMillis - lastCheckedAtMillis).coerceAtLeast(0L) <= maxAge
    }
}

class OkHttpCatalogHttpTransport(
    private val client: OkHttpClient,
) : CatalogHttpTransport {
    override suspend fun execute(request: CatalogHttpRequest): CatalogHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(
                Request.Builder()
                    .url(request.url)
                    .apply { request.headers.forEach(::header) }
                    .get()
                    .build(),
            )
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        response.use {
                            if (!continuation.isActive) return
                            val headers = response.headers.names().associateWith(response.headers::values)
                            continuation.resume(
                                CatalogHttpResponse(
                                    statusCode = response.code,
                                    body = response.body?.string().orEmpty(),
                                    headers = headers,
                                ),
                            )
                        }
                    }
                },
            )
        }
}

private fun CatalogHttpResponse.toHealthStatus(
    endpoint: CatalogSourceHealthEndpoint,
    checkedAtMillis: Long,
): CatalogSourceStatus {
    if (statusCode !in 200..299) {
        return brokenStatus(
            endpoint = endpoint,
            failure = CatalogSourceHealthFailure(
                kind = CatalogSourceHealthFailureKind.HttpStatus,
                message = "Root page returned HTTP $statusCode",
                httpStatusCode = statusCode,
            ),
            checkedAtMillis = checkedAtMillis,
        )
    }
    val normalizedBody = body.lowercase(Locale.ROOT)
    val normalizedTitle = Jsoup.parse(body).title().lowercase(Locale.ROOT)
    if (
        body.isBlank() ||
        endpoint.outageContentMarkers.any(normalizedBody::contains) ||
        endpoint.expectedTitleMarkers.none(normalizedTitle::contains)
    ) {
        return brokenStatus(
            endpoint = endpoint,
            failure = CatalogSourceHealthFailure(
                kind = CatalogSourceHealthFailureKind.InvalidContent,
                message = "Root page returned unexpected content",
                httpStatusCode = statusCode,
            ),
            checkedAtMillis = checkedAtMillis,
        )
    }
    return CatalogSourceStatus(
        source = endpoint.source,
        state = CatalogSourceHealth.Available,
        rootUrl = endpoint.rootUrl,
        lastCheckedAtMillis = checkedAtMillis,
    )
}

private fun brokenStatus(
    endpoint: CatalogSourceHealthEndpoint,
    failure: CatalogSourceHealthFailure,
    checkedAtMillis: Long,
): CatalogSourceStatus = CatalogSourceStatus(
    source = endpoint.source,
    state = CatalogSourceHealth.Broken,
    rootUrl = endpoint.rootUrl,
    message = failure.message,
    lastCheckedAtMillis = checkedAtMillis,
    failure = failure,
)

private fun uncertainStatus(
    endpoint: CatalogSourceHealthEndpoint,
    failure: CatalogSourceHealthFailure,
    checkedAtMillis: Long,
): CatalogSourceStatus = CatalogSourceStatus(
    source = endpoint.source,
    state = CatalogSourceHealth.Unknown,
    rootUrl = endpoint.rootUrl,
    message = failure.message,
    lastCheckedAtMillis = checkedAtMillis,
    failure = failure,
)

internal const val CatalogSourceHealthAvailableFreshMillis: Long = 5L * 60L * 1000L
internal const val CatalogSourceHealthBrokenFreshMillis: Long = 60L * 1000L
