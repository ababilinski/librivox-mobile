package com.librivox.mobile.catalog

import com.librivox.mobile.playback.CatalogSourcePreference
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogSourceHealthProbeTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun validRoot_emitsCheckingThenAvailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<title>LibriVox</title>"))
        val states = mutableListOf<CatalogSourceHealth>()

        val status = probe().probeSource(CatalogSourcePreference.LibriVox) {
            states += it.state
        }

        assertEquals(listOf(CatalogSourceHealth.Checking, CatalogSourceHealth.Available), states)
        assertEquals(CatalogSourceHealth.Available, status.state)
        assertEquals(server.url("/").toString(), status.rootUrl)
        assertEquals("/", server.takeRequest().path)
    }

    @Test
    fun errorPageWithHttp200_isBrokenInvalidContent() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "<html><title>LibriVox service unavailable</title><p>Please try again later.</p></html>",
            ),
        )

        val status = probe().probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Broken, status.state)
        assertEquals(CatalogSourceHealthFailureKind.InvalidContent, status.failure?.kind)
        assertEquals(200, status.failure?.httpStatusCode)
    }

    @Test
    fun brandedBodyWithNonSourceTitle_isNotMistakenForHealthyRoot() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "<html><title>Please wait</title><body>Connecting to LibriVox</body></html>",
            ),
        )

        val status = probe().probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Broken, status.state)
        assertEquals(CatalogSourceHealthFailureKind.InvalidContent, status.failure?.kind)
    }

    @Test
    fun defaultEndpointsUseEachCatalogRootPage() {
        assertEquals(
            "https://librivox.org/",
            DefaultCatalogSourceHealthEndpoints[CatalogSourcePreference.LibriVox]?.rootUrl,
        )
        assertEquals(
            "https://etc.usf.edu/lit2go/",
            DefaultCatalogSourceHealthEndpoints[CatalogSourcePreference.Lit2Go]?.rootUrl,
        )
        assertFalse(DefaultCatalogSourceHealthEndpoints.containsKey(CatalogSourcePreference.Gutendex))
        assertEquals(
            "https://wolnelektury.pl/",
            DefaultCatalogSourceHealthEndpoints[CatalogSourcePreference.WolneLektury]?.rootUrl,
        )
    }

    @Test
    fun http404_isBrokenWithStatusMetadata() = runTest {
        assertHttpFailure(404)
    }

    @Test
    fun http500_isBrokenWithStatusMetadata() = runTest {
        assertHttpFailure(500)
    }

    @Test
    fun timeout_isUnknownRatherThanFalselyBroken() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val status = probe(readTimeoutMillis = 100).probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Unknown, status.state)
        assertEquals(CatalogSourceHealthFailureKind.Timeout, status.failure?.kind)
        assertTrue(status.isAvailable)
    }

    @Test
    fun okhttpCallTimeout_isUnknownRatherThanFalselyBroken() = runTest {
        val healthProbe = CatalogSourceHealthProbe(
            transport = CatalogHttpTransport { throw InterruptedIOException("timeout") },
            endpoints = endpoints(),
        )

        val status = healthProbe.probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Unknown, status.state)
        assertEquals(CatalogSourceHealthFailureKind.Timeout, status.failure?.kind)
        assertTrue(status.isAvailable)
    }

    @Test
    fun genericNetworkFailure_isBrokenWhenDeviceReportsOnline() = runTest {
        val healthProbe = CatalogSourceHealthProbe(
            transport = CatalogHttpTransport { throw IOException("DNS unavailable") },
            endpoints = endpoints(),
        )

        val status = healthProbe.probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Broken, status.state)
        assertEquals(CatalogSourceHealthFailureKind.Network, status.failure?.kind)
        assertEquals("DNS unavailable", status.message)
        assertFalse(status.isAvailable)
    }

    @Test
    fun offlineWithoutPriorCheck_isOfflineAndDoesNotRequestRoot() = runTest {
        val status = probe(isOnline = false).probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Offline, status.state)
        assertTrue(status.isOffline)
        assertTrue(status.isAvailable)
        assertEquals(CatalogSourceHealthFailureKind.Offline, status.failure?.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun offlinePublishesOfflineAndRetainsPriorAvailableCacheForReconnect() = runTest {
        var online = true
        server.enqueue(MockResponse().setResponseCode(200).setBody("<title>LibriVox</title>"))
        val healthProbe = probe(connectivity = CatalogConnectivity { online })
        val available = healthProbe.probeSource(CatalogSourcePreference.LibriVox)
        online = false

        val retained = healthProbe.probeSource(CatalogSourcePreference.LibriVox)
        online = true
        val restored = healthProbe.probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Available, available.state)
        assertEquals(CatalogSourceHealth.Offline, retained.state)
        assertTrue(retained.isOffline)
        assertTrue(retained.isAvailable)
        assertEquals(CatalogSourceHealth.Available, restored.state)
        assertFalse(restored.isOffline)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cancellation_stopsCallAndDoesNotPublishBroken() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val checkingPublished = CompletableDeferred<Unit>()
        val states = mutableListOf<CatalogSourceHealth>()
        val job = launch {
            probe(readTimeoutMillis = 30_000).probeSource(CatalogSourcePreference.LibriVox) {
                states += it.state
                if (it.state == CatalogSourceHealth.Checking) checkingPublished.complete(Unit)
            }
        }
        checkingPublished.await()

        job.cancelAndJoin()

        assertEquals(listOf(CatalogSourceHealth.Checking), states)
        assertFalse(states.contains(CatalogSourceHealth.Broken))
    }

    @Test
    fun cachedHealthDebouncesRepeatedSearchProbesUntilTtlExpires() = runTest {
        var now = 10_000L
        server.enqueue(MockResponse().setResponseCode(200).setBody("<title>LibriVox</title>"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("failure"))
        val healthProbe = probe(clock = CatalogClock { now })

        val first = healthProbe.probeSource(CatalogSourcePreference.LibriVox)
        val cached = healthProbe.probeSource(CatalogSourcePreference.LibriVox)
        now += CatalogSourceHealthAvailableFreshMillis + 1L
        val refreshed = healthProbe.probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Available, first.state)
        assertEquals(CatalogSourceHealth.Available, cached.state)
        assertEquals(CatalogSourceHealth.Broken, refreshed.state)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun probesEnabledRootsIndependently() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/librivox" -> MockResponse().setResponseCode(200).setBody("<title>LibriVox</title>")
                else -> MockResponse().setResponseCode(500).setBody("failure")
            }
        }
        val endpoints = mapOf(
            CatalogSourcePreference.LibriVox to endpoint(
                CatalogSourcePreference.LibriVox,
                server.url("/librivox").toString(),
                "librivox",
            ),
            CatalogSourcePreference.Lit2Go to endpoint(
                CatalogSourcePreference.Lit2Go,
                server.url("/lit2go").toString(),
                "lit2go",
            ),
        )
        val healthProbe = CatalogSourceHealthProbe(
            transport = transport(),
            endpoints = endpoints,
        )

        val statuses = healthProbe.probe(endpoints.keys)

        assertEquals(CatalogSourceHealth.Available, statuses[CatalogSourcePreference.LibriVox]?.state)
        assertEquals(CatalogSourceHealth.Broken, statuses[CatalogSourcePreference.Lit2Go]?.state)
        assertEquals(2, server.requestCount)
    }

    private suspend fun assertHttpFailure(code: Int) {
        server.enqueue(MockResponse().setResponseCode(code).setBody("failure"))

        val status = probe().probeSource(CatalogSourcePreference.LibriVox)

        assertEquals(CatalogSourceHealth.Broken, status.state)
        assertEquals(CatalogSourceHealthFailureKind.HttpStatus, status.failure?.kind)
        assertEquals(code, status.failure?.httpStatusCode)
        assertTrue(status.message.orEmpty().contains(code.toString()))
    }

    private fun probe(
        isOnline: Boolean = true,
        connectivity: CatalogConnectivity = CatalogConnectivity { isOnline },
        clock: CatalogClock = CatalogClock(System::currentTimeMillis),
        readTimeoutMillis: Long = 5_000L,
    ): CatalogSourceHealthProbe = CatalogSourceHealthProbe(
        transport = transport(readTimeoutMillis),
        clock = clock,
        connectivity = connectivity,
        endpoints = endpoints(),
    )

    private fun transport(readTimeoutMillis: Long = 5_000L): CatalogHttpTransport =
        OkHttpCatalogHttpTransport(
            OkHttpClient.Builder()
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .callTimeout(readTimeoutMillis + 1_000L, TimeUnit.MILLISECONDS)
                .build(),
        )

    private fun endpoints(): Map<CatalogSourcePreference, CatalogSourceHealthEndpoint> = mapOf(
        CatalogSourcePreference.LibriVox to endpoint(
            source = CatalogSourcePreference.LibriVox,
            url = server.url("/").toString(),
            marker = "librivox",
        ),
    )

    private fun endpoint(
        source: CatalogSourcePreference,
        url: String,
        marker: String,
    ): CatalogSourceHealthEndpoint = CatalogSourceHealthEndpoint(
        source = source,
        rootUrl = url,
        expectedTitleMarkers = setOf(marker),
    )
}
