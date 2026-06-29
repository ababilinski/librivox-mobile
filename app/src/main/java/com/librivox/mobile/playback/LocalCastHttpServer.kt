package com.librivox.mobile.playback

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.math.min

class LocalCastHttpServer(
    context: Context,
    private val lanAddressProvider: () -> String? = { resolveLanIpv4Address(context) },
    private val portCandidates: List<Int> = DEFAULT_PORTS,
) {
    private val appContext = context.applicationContext
    private val sources = ConcurrentHashMap<String, LocalCastAudioSource>()
    private val coverSources = ConcurrentHashMap<String, LocalCastAudioSource>()
    private val tokenBySourceKey = ConcurrentHashMap<String, String>()
    private val coverTokenByKey = ConcurrentHashMap<String, String>()
    private var server: LocalCastNanoServer? = null
    private var activePort: Int? = null

    @Synchronized
    fun prepareUrl(mediaItem: MediaItem): String? {
        val source = mediaItem.localCastSource(appContext) ?: return null
        if (!hasLocalNetworkAccess(appContext)) return null
        val host = lanAddressProvider()?.takeIf { it.isUsableLanHost() } ?: return null
        val port = ensureStarted() ?: return null
        val token = register(source)
        return "http://$host:$port$AUDIO_PATH_PREFIX$token"
    }

    @Synchronized
    fun prepareCoverUrl(coverUri: Uri?, mediaItem: MediaItem? = null): String? {
        if (coverUri == null && mediaItem == null) return null
        coverUri?.let {
            val scheme = it.scheme?.lowercase(Locale.US)
            if (scheme == "http" || scheme == "https") {
                return it.toString()
            }
        }
        val source = resolveCoverSource(coverUri, mediaItem) ?: return null
        if (!hasLocalNetworkAccess(appContext)) return null
        val host = lanAddressProvider()?.takeIf { it.isUsableLanHost() } ?: return null
        val port = ensureStarted() ?: return null
        val token = registerCover(source)
        return "http://$host:$port$COVER_PATH_PREFIX$token"
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
        activePort = null
        sources.clear()
        coverSources.clear()
        tokenBySourceKey.clear()
        coverTokenByKey.clear()
    }

    @Synchronized
    private fun ensureStarted(): Int? {
        activePort?.let { return it }
        for (port in portCandidates) {
            val next = LocalCastNanoServer(port, sources, coverSources)
            val started = runCatching {
                next.start(SOCKET_READ_TIMEOUT_MS, false)
                next
            }.onFailure { error ->
                Log.w(TAG, "Unable to start local Cast HTTP server on port=$port", error)
            }.getOrNull()
            if (started != null) {
                server = started
                activePort = port
                Log.i(TAG, "Local Cast HTTP server started on 0.0.0.0:$port")
                return port
            }
        }
        return null
    }

    private fun register(source: LocalCastAudioSource): String {
        val token = tokenBySourceKey.computeIfAbsent(source.key) {
            UUID.randomUUID().toString().replace("-", "")
        }
        sources[token] = source
        return token
    }

    private fun registerCover(source: LocalCastAudioSource): String {
        val token = coverTokenByKey.computeIfAbsent(source.key) {
            UUID.randomUUID().toString().replace("-", "")
        }
        coverSources[token] = source
        return token
    }

    private fun resolveCoverSource(coverUri: Uri?, mediaItem: MediaItem?): LocalCastAudioSource? {
        val scheme = coverUri?.scheme?.lowercase(Locale.US)
        // For our stylized fallback drawable, prefer real embedded artwork when present.
        if (scheme == ContentResolver.SCHEME_ANDROID_RESOURCE) {
            mediaItem?.embeddedCoverSource()?.let { return it }
            coverUri?.coverFromAndroidResource()?.let { return it }
            return null
        }
        coverUri?.let { uri ->
            when (scheme) {
                "file" -> uri.coverFromFile()?.let { return it }
                ContentResolver.SCHEME_CONTENT -> uri.coverFromContent()?.let { return it }
                "asset" -> uri.coverFromAsset()?.let { return it }
            }
        }
        mediaItem?.embeddedCoverSource()?.let { return it }
        return null
    }

    private fun Uri.coverFromFile(): LocalCastAudioSource? {
        val path = path ?: return null
        val file = File(path).canonicalFile
        if (!file.isFile) return null
        val mime = file.extension.toImageMimeType() ?: "image/jpeg"
        return LocalCastAudioSource(
            key = "cover-file:${file.path}:${file.length()}:${file.lastModified()}",
            file = file,
            mimeType = mime,
            debugName = "cover:${file.name}",
        )
    }

    private fun Uri.coverFromAndroidResource(): LocalCastAudioSource? {
        val resources = appContext.resources
        val resId = runCatching {
            val authority = authority ?: appContext.packageName
            val type = pathSegments.getOrNull(0)
            val name = pathSegments.getOrNull(1) ?: lastPathSegment
            val lastSegment = lastPathSegment
            val direct = lastSegment?.toIntOrNull()
            direct ?: resources.getIdentifier(name.orEmpty(), type, authority)
        }.getOrDefault(0)
        if (resId == 0) return null
        val cacheDir = File(appContext.cacheDir, "local-cast-covers").apply { mkdirs() }
        val cacheFile = File(cacheDir, "drawable-$resId.png")
        if (!cacheFile.isFile) {
            val drawable = runCatching { ContextCompat.getDrawable(appContext, resId) }.getOrNull()
                ?: return null
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1024
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1024
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            runCatching {
                FileOutputStream(cacheFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }.onFailure {
                cacheFile.delete()
                bitmap.recycle()
                return null
            }
            bitmap.recycle()
        }
        return LocalCastAudioSource(
            key = "cover-resource:$resId:${cacheFile.length()}",
            file = cacheFile,
            mimeType = "image/png",
            debugName = "cover:res/$resId",
        )
    }

    private fun Uri.coverFromContent(): LocalCastAudioSource? {
        val cacheDir = File(appContext.cacheDir, "local-cast-covers").apply { mkdirs() }
        val hash = toString().sha256()
        val cacheFile = File(cacheDir, "content-$hash.bin")
        if (!cacheFile.isFile) {
            runCatching {
                appContext.contentResolver.openInputStream(this)?.use { input ->
                    FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                } ?: return null
            }.onFailure {
                cacheFile.delete()
                return null
            }
        }
        val mime = appContext.contentResolver.getType(this) ?: cacheFile.detectImageMimeType()
        return LocalCastAudioSource(
            key = "cover-content:$hash:${cacheFile.length()}",
            file = cacheFile,
            mimeType = mime ?: "image/jpeg",
            debugName = "cover:content/$hash",
        )
    }

    private fun Uri.coverFromAsset(): LocalCastAudioSource? {
        val assetPath = pathSegments.joinToString("/")
        if (assetPath.isBlank() || assetPath.contains("..")) return null
        val cacheDir = File(appContext.cacheDir, "local-cast-covers").apply { mkdirs() }
        val fileName = lastPathSegment?.safeFileName() ?: return null
        val cacheFile = File(cacheDir, "${assetPath.sha256()}-$fileName")
        if (!cacheFile.isFile) {
            runCatching {
                appContext.assets.open(assetPath).use { input ->
                    FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                }
            }.onFailure {
                cacheFile.delete()
                return null
            }
        }
        return LocalCastAudioSource(
            key = "cover-asset:$assetPath:${cacheFile.length()}",
            file = cacheFile,
            mimeType = fileName.substringAfterLast('.', "").toImageMimeType() ?: "image/jpeg",
            debugName = "cover:asset/$assetPath",
        )
    }

    private fun MediaItem.embeddedCoverSource(): LocalCastAudioSource? {
        val uri = localConfiguration?.uri ?: return null
        val cacheDir = File(appContext.cacheDir, "local-cast-covers").apply { mkdirs() }
        val key = uri.toString().sha256()
        val cacheFile = File(cacheDir, "embedded-$key.bin")
        if (!cacheFile.isFile) {
            val picture = extractEmbeddedPicture(uri) ?: return null
            runCatching {
                FileOutputStream(cacheFile).use { output -> output.write(picture) }
            }.onFailure {
                cacheFile.delete()
                return null
            }
        }
        val mime = cacheFile.detectImageMimeType() ?: "image/jpeg"
        return LocalCastAudioSource(
            key = "cover-embedded:$key:${cacheFile.length()}",
            file = cacheFile,
            mimeType = mime,
            debugName = "cover:embedded/$key",
        )
    }

    private fun extractEmbeddedPicture(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            when (uri.scheme?.lowercase(Locale.US)) {
                "file" -> retriever.setDataSource(uri.path)
                "content" -> retriever.setDataSource(appContext, uri)
                "asset" -> {
                    val assetPath = uri.pathSegments.joinToString("/")
                    appContext.assets.openFd(assetPath).use { fd ->
                        retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    }
                }
                else -> return null
            }
            retriever.embeddedPicture
        } catch (e: Exception) {
            Log.w(TAG, "Embedded artwork extraction failed for uri=$uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun File.detectImageMimeType(): String? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream().use { BitmapFactory.decodeStream(it, null, options) }
            options.outMimeType
        }.getOrNull()
    }

    private fun String.toImageMimeType(): String? = when (lowercase(Locale.US)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> null
    }

    private fun MediaItem.localCastSource(context: Context): LocalCastAudioSource? {
        val localConfiguration = localConfiguration ?: return null
        val mimeType = normalizedAudioMimeType(localConfiguration.mimeType, localConfiguration.uri.toString())
        return when (localConfiguration.uri.scheme?.lowercase(Locale.US)) {
            "file" -> localConfiguration.uri.fileSource(context, mimeType)
            "asset" -> localConfiguration.uri.assetSource(context, mimeType)
            else -> null
        }
    }

    private fun Uri.fileSource(context: Context, mimeType: String): LocalCastAudioSource? {
        val path = path ?: return null
        val file = File(path).canonicalFile
        val filesRoot = context.filesDir.canonicalFile
        if (!file.isFile || !file.path.startsWith(filesRoot.path + File.separator)) {
            return null
        }
        return LocalCastAudioSource(
            key = "file:${file.path}:${file.length()}:${file.lastModified()}",
            file = file,
            mimeType = mimeType,
            debugName = file.name,
        )
    }

    private fun Uri.assetSource(context: Context, mimeType: String): LocalCastAudioSource? {
        val assetPath = pathSegments.joinToString("/")
        if (!assetPath.startsWith("audiobooks/") || assetPath.contains("..")) return null
        if (pathSegments.any { it.isBlank() || it.contains('/') || it.contains('\\') }) return null
        val fileName = pathSegments.lastOrNull()?.safeFileName() ?: return null
        val cacheDir = File(context.cacheDir, "local-cast-assets").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${assetPath.sha256()}-$fileName")
        if (!cacheFile.isFile) {
            val temp = File(cacheDir, "${cacheFile.name}.part")
            runCatching {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                if (cacheFile.exists()) cacheFile.delete()
                if (!temp.renameTo(cacheFile)) {
                    temp.copyTo(cacheFile, overwrite = true)
                    temp.delete()
                }
            }.onFailure { error ->
                temp.delete()
                Log.w(TAG, "Unable to cache asset for local Cast streaming. asset=$assetPath", error)
                return null
            }
        }
        return LocalCastAudioSource(
            key = "asset:$assetPath:${cacheFile.length()}:${cacheFile.lastModified()}",
            file = cacheFile,
            mimeType = mimeType,
            debugName = assetPath,
        )
    }

    companion object {
        private const val TAG = "LocalCastHttpServer"
        private const val SOCKET_READ_TIMEOUT_MS = 5_000
        const val AUDIO_PATH_PREFIX = "/audio/"
        const val COVER_PATH_PREFIX = "/cover/"
        private val DEFAULT_PORTS = (8080..8088).toList()
        private const val LOCAL_NETWORK_READINESS_CACHE_MS = 2_000L

        @Volatile
        private var cachedReadiness: Pair<Long, Boolean>? = null

        fun hasUsableLocalNetwork(context: Context): Boolean {
            val now = System.currentTimeMillis()
            cachedReadiness?.let { (timestamp, ready) ->
                if (now - timestamp < LOCAL_NETWORK_READINESS_CACHE_MS) return ready
            }
            val ready = hasLocalNetworkAccess(context) &&
                resolveLanIpv4Address(context)?.isUsableLanHost() == true
            cachedReadiness = now to ready
            return ready
        }

        fun hasLocalNetworkAccess(context: Context): Boolean =
            Build.VERSION.SDK_INT < 36 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                ) == PackageManager.PERMISSION_GRANTED

        fun resolveLanIpv4Address(context: Context): String? {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = connectivityManager?.activeNetwork
            val capabilities = activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            val activeLanAddress = if (
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            ) {
                connectivityManager.getLinkProperties(activeNetwork)
                    ?.linkAddresses
                    ?.asSequence()
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.mapNotNull { it.hostAddress }
                    ?.firstOrNull { it.isUsableLanHost() }
            } else {
                null
            }
            return activeLanAddress ?: networkInterfaceLanIpv4Address()
        }

        private fun networkInterfaceLanIpv4Address(): String? =
            runCatching {
                NetworkInterface.getNetworkInterfaces()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .firstOrNull { it.isUsableLanHost() }
            }.getOrNull()
    }
}

internal class LocalCastNanoServer(
    port: Int,
    private val sources: ConcurrentMap<String, LocalCastAudioSource>,
    private val coverSources: ConcurrentMap<String, LocalCastAudioSource>,
) : NanoHTTPD("0.0.0.0", port) {
    override fun serve(session: IHTTPSession): Response {
        val sourceMap = when {
            session.uri.startsWith(LocalCastHttpServer.AUDIO_PATH_PREFIX) -> sources
            session.uri.startsWith(LocalCastHttpServer.COVER_PATH_PREFIX) -> coverSources
            else -> return notFound()
        }
        val prefix = if (sourceMap === sources) LocalCastHttpServer.AUDIO_PATH_PREFIX
            else LocalCastHttpServer.COVER_PATH_PREFIX
        val token = session.uri.removePrefix(prefix)
        if (token.isBlank() || token.contains('/')) return notFound()

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                .withCommonHeaders(contentLength = 0L)
        }
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
                .withCommonHeaders(contentLength = 0L)
                .apply { addHeader("Allow", "GET, HEAD, OPTIONS") }
        }

        val source = sourceMap[token] ?: return notFound()
        val length = source.length
        val rangeHeader = session.headers["range"] ?: session.headers["Range"]
        return when (val range = selectRange(rangeHeader, length)) {
            RangeSelection.Unsatisfiable -> rangeNotSatisfiable(length)
            is RangeSelection.Full -> source.responseFor(
                method = session.method,
                status = Response.Status.OK,
                start = 0L,
                endInclusive = (length - 1L).coerceAtLeast(0L),
                contentLength = length,
            )
            is RangeSelection.Partial -> source.responseFor(
                method = session.method,
                status = Response.Status.PARTIAL_CONTENT,
                start = range.start,
                endInclusive = range.endInclusive,
                contentLength = range.contentLength,
                contentRange = "bytes ${range.start}-${range.endInclusive}/$length",
            )
        }
    }

    private fun LocalCastAudioSource.responseFor(
        method: Method,
        status: Response.Status,
        start: Long,
        endInclusive: Long,
        contentLength: Long,
        contentRange: String? = null,
    ): Response {
        val body = if (method == Method.HEAD || contentLength == 0L) {
            ByteArrayInputStream(ByteArray(0))
        } else {
            openStream(start)
        }
        return newFixedLengthResponse(status, mimeType, body, if (method == Method.HEAD) 0L else contentLength)
            .withCommonHeaders(contentLength, contentRange)
            .also {
                runCatching {
                    Log.d(
                        TAG,
                        "Serving local Cast audio. status=${status.requestStatus} " +
                            "range=$start-$endInclusive length=$contentLength source=$debugName",
                    )
                }
            }
    }

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
            .withCommonHeaders(contentLength = 0L)

    private fun rangeNotSatisfiable(length: Long): Response =
        newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
            .withCommonHeaders(contentLength = 0L, contentRange = "bytes */$length")

    private fun Response.withCommonHeaders(
        contentLength: Long,
        contentRange: String? = null,
    ): Response = apply {
        addHeader("Accept-Ranges", "bytes")
        addHeader("Content-Length", contentLength.toString())
        contentRange?.let { addHeader("Content-Range", it) }
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Range, Content-Type, Origin, Accept")
        addHeader("Access-Control-Expose-Headers", "Accept-Ranges, Content-Length, Content-Range")
        addHeader("Cache-Control", "no-store")
    }

    private companion object {
        const val TAG = "LocalCastNanoServer"
    }
}

internal data class LocalCastAudioSource(
    val key: String,
    val file: File,
    val mimeType: String,
    val debugName: String,
) {
    val length: Long get() = file.length().coerceAtLeast(0L)

    fun openStream(start: Long): InputStream {
        val input = BufferedInputStream(file.inputStream())
        input.skipFully(start)
        return input
    }
}

private sealed interface RangeSelection {
    data class Full(val length: Long) : RangeSelection
    data class Partial(val start: Long, val endInclusive: Long) : RangeSelection {
        val contentLength: Long get() = endInclusive - start + 1L
    }
    data object Unsatisfiable : RangeSelection
}

private fun selectRange(rangeHeader: String?, length: Long): RangeSelection {
    if (rangeHeader.isNullOrBlank()) return RangeSelection.Full(length)
    if (!rangeHeader.startsWith("bytes=") || length <= 0L) return RangeSelection.Unsatisfiable
    val spec = rangeHeader.removePrefix("bytes=").trim()
    if (spec.contains(',') || !spec.contains('-')) return RangeSelection.Unsatisfiable
    val startText = spec.substringBefore('-').trim()
    val endText = spec.substringAfter('-').trim()
    if (startText.isBlank()) {
        val suffixLength = endText.toLongOrNull() ?: return RangeSelection.Unsatisfiable
        if (suffixLength <= 0L) return RangeSelection.Unsatisfiable
        val start = (length - suffixLength).coerceAtLeast(0L)
        return RangeSelection.Partial(start, length - 1L)
    }
    val start = startText.toLongOrNull() ?: return RangeSelection.Unsatisfiable
    if (start < 0L || start >= length) return RangeSelection.Unsatisfiable
    val end = if (endText.isBlank()) {
        length - 1L
    } else {
        min(endText.toLongOrNull() ?: return RangeSelection.Unsatisfiable, length - 1L)
    }
    if (end < start) return RangeSelection.Unsatisfiable
    return RangeSelection.Partial(start, end)
}

private fun InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) return
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private fun normalizedAudioMimeType(mimeType: String?, source: String): String {
    val normalized = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
        .orEmpty()
    if (normalized.isNotBlank()) return normalized

    return when (source.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase(Locale.US)) {
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

private fun String.isUsableLanHost(): Boolean =
    isNotBlank() &&
        this != "0.0.0.0" &&
        !startsWith("127.") &&
        !startsWith("169.254.")

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "audio" }

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
