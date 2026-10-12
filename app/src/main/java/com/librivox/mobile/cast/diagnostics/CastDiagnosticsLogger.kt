package com.librivox.mobile.cast.diagnostics

import android.content.Context
import android.util.Log
import com.librivox.mobile.playback.PlaybackSettingsStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastDiagnosticsLogger internal constructor(
    private val appContext: Context,
    private val settings: PlaybackSettingsStore,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxLogBytes: Long = MAX_LOG_BYTES,
) {
    enum class Level(val androidPriority: Int, val short: String) {
        Verbose(Log.VERBOSE, "V"),
        Debug(Log.DEBUG, "D"),
        Info(Log.INFO, "I"),
        Warn(Log.WARN, "W"),
        Error(Log.ERROR, "E"),
    }

    data class Entry(
        val timestampMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val lock = Any()

    fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        Log.println(level.androidPriority, tag, message)
        if (throwable != null) {
            Log.println(level.androidPriority, tag, Log.getStackTraceString(throwable))
        }
        val verbose = settings.castVerboseLogging
        if (!verbose && level == Level.Verbose) return
        if (!settings.castDiagnosticsEnabled) return

        val entry = Entry(System.currentTimeMillis(), level, tag, message, throwable)
        synchronized(lock) {
            val next = _entries.value.toMutableList()
            next.add(entry)
            while (next.size > maxEntries) next.removeAt(0)
            _entries.value = next
            appendToFile(entry)
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.Verbose, tag, message, throwable)

    fun d(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.Debug, tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.Info, tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.Warn, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.Error, tag, message, throwable)

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            runCatching { logFile().delete() }
            runCatching { rotatedLogFile().delete() }
        }
    }

    fun snapshotLogFile(): File? {
        synchronized(lock) {
            val file = logFile()
            return file.takeIf { it.isFile && it.length() > 0L }
        }
    }

    private fun appendToFile(entry: Entry) {
        val file = logFile()
        runCatching {
            if (file.exists() && file.length() > maxLogBytes) {
                rotateLog(file)
            }
            file.parentFile?.mkdirs()
            file.appendText(formatLine(entry))
            if (entry.throwable != null) {
                file.appendText(Log.getStackTraceString(entry.throwable))
                file.appendText("\n")
            }
        }
    }

    private fun rotateLog(current: File) {
        try {
            val rotated = rotatedLogFile()
            if (rotated.exists()) rotated.delete()
            current.renameTo(rotated)
        } catch (ioe: IOException) {
            Log.w(TAG, "Cast diagnostics log rotation failed", ioe)
        }
    }

    private fun formatLine(entry: Entry): String {
        val ts = TIMESTAMP_FORMAT.format(Date(entry.timestampMs))
        return "$ts ${entry.level.short}/${entry.tag}: ${entry.message}\n"
    }

    private fun logFile(): File = File(appContext.filesDir, LOG_FILE_NAME)

    private fun rotatedLogFile(): File = File(appContext.filesDir, ROTATED_LOG_FILE_NAME)

    private companion object {
        const val TAG = "CastDiagnostics"
        const val MAX_ENTRIES = 1000
        const val MAX_LOG_BYTES = 256L * 1024L
        const val LOG_FILE_NAME = "cast-diagnostics.log"
        const val ROTATED_LOG_FILE_NAME = "cast-diagnostics.log.1"
        val TIMESTAMP_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

object CastDiagnostics {
    @Volatile
    private var instance: CastDiagnosticsLogger? = null

    fun install(context: Context, settings: PlaybackSettingsStore): CastDiagnosticsLogger {
        val existing = instance
        if (existing != null) return existing
        synchronized(this) {
            val current = instance
            if (current != null) return current
            val created = CastDiagnosticsLogger(context.applicationContext, settings)
            instance = created
            return created
        }
    }

    fun get(): CastDiagnosticsLogger? = instance
}
