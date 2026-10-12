package com.librivox.mobile.playback

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalCastHttpServerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private var server: LocalCastNanoServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun get_servesFullAudioWithReceiverHeaders() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/token")

        assertEquals(200, response.responseCode)
        assertEquals("bytes", response.getHeaderField("Accept-Ranges"))
        assertEquals("10", response.getHeaderField("Content-Length"))
        assertEquals("*", response.getHeaderField("Access-Control-Allow-Origin"))
        assertTrue(response.getHeaderField("Access-Control-Allow-Headers").contains("Range"))
        assertEquals("abcdefghij", response.inputStream.bufferedReader().readText())
    }

    @Test
    fun head_returnsHeadersWithoutBody() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/token", method = "HEAD")

        assertEquals(200, response.responseCode)
        assertEquals("10", response.getHeaderField("Content-Length"))
        assertEquals("bytes", response.getHeaderField("Accept-Ranges"))
    }

    @Test
    fun get_withSingleRange_returnsPartialAudio() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/token", range = "bytes=2-5")

        assertEquals(206, response.responseCode)
        assertEquals("bytes 2-5/10", response.getHeaderField("Content-Range"))
        assertEquals("4", response.getHeaderField("Content-Length"))
        assertEquals("cdef", response.inputStream.bufferedReader().readText())
    }

    @Test
    fun get_withInvalidRange_returnsRangeNotSatisfiable() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/token", range = "bytes=25-30")

        assertEquals(416, response.responseCode)
        assertEquals("bytes */10", response.getHeaderField("Content-Range"))
    }

    @Test
    fun get_withUnknownToken_returnsNotFound() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/missing")

        assertEquals(404, response.responseCode)
    }

    @Test
    fun post_returnsMethodNotAllowed() {
        val baseUrl = startServer("abcdefghij".toByteArray())
        val response = open("$baseUrl/audio/token", method = "POST")

        assertEquals(405, response.responseCode)
        assertEquals("GET, HEAD, OPTIONS", response.getHeaderField("Allow"))
    }

    private fun startServer(bytes: ByteArray): String {
        val audioFile = temporaryFolder.newFile("chapter.mp3").apply { writeBytes(bytes) }
        val sources = ConcurrentHashMap<String, LocalCastAudioSource>()
        sources["token"] = LocalCastAudioSource(
            key = "test",
            file = audioFile,
            mimeType = "audio/mpeg",
            debugName = "chapter.mp3",
        )
        val port = freePort()
        server = LocalCastNanoServer(
            port = port,
            sources = sources,
            coverSources = ConcurrentHashMap(),
        ).also { it.start(3_000, false) }
        return "http://127.0.0.1:$port"
    }

    private fun open(
        url: String,
        method: String = "GET",
        range: String? = null,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            range?.let { setRequestProperty("Range", it) }
        }

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }
}
