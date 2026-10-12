package com.librivox.mobile.download

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal fun downloadHttpToFile(
    sourceUrl: String,
    target: File,
) {
    val connection = URL(sourceUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = CONNECT_TIMEOUT_MS
    connection.readTimeout = READ_TIMEOUT_MS
    connection.requestMethod = "GET"
    connection.setRequestProperty("User-Agent", "AudioPlayerPrototype/1.0")
    try {
        check(connection.responseCode in 200..299) {
            "HTTP ${connection.responseCode} while downloading $sourceUrl"
        }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } finally {
        connection.disconnect()
    }
}

private const val CONNECT_TIMEOUT_MS = 12_000
private const val READ_TIMEOUT_MS = 60_000
