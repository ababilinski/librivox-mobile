package com.librivox.mobile.readalong

import com.librivox.mobile.download.downloadHttpToFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reader cache only: keep source text/timing paths intact without downloading embedded audio. */
internal fun downloadReadAlongArchive(sourceUrl: String, target: File) {
    target.parentFile?.mkdirs()
    val compact = File.createTempFile("reader-archive-", ".part", target.absoluteFile.parentFile)
    try {
        try {
            RemoteReaderZip(sourceUrl).writeTo(compact)
        } catch (_: IOException) {
            // Servers without byte ranges, ZIP64, and unusual ZIPs retain the existing path.
            downloadHttpToFile(sourceUrl, target)
            return
        }
        compact.copyTo(target, overwrite = true)
    } finally {
        compact.delete()
    }
}

private class RemoteReaderZip(private val sourceUrl: String) {
    private var archiveSize = 0L
    private var validator: String? = null
    private var transferredBytes = 0L
    private var requestCount = 0
    private val cachedRanges = mutableListOf<ArchiveRange>()

    fun writeTo(target: File) {
        val tail = requestRange(start = null, end = null)
        archiveSize = tail.totalSize
        cachedRanges += tail
        val endRecord = findEndRecord(tail)
        val directory = readRange(endRecord.directoryOffset, endRecord.directorySize.toLong())
        val entries = parseDirectory(directory, endRecord)
        val sorted = entries.sortedBy { it.localOffset }
        requireArchive(sorted.map { it.localOffset }.distinct().size == sorted.size)
        val records = sorted.mapIndexedNotNull { index, entry ->
            if (!entry.isReaderSource()) return@mapIndexedNotNull null
            requireArchive(entry.flags and 1 == 0 && entry.method in setOf(0, 8))
            requireArchive(entry.uncompressedSize <= MAX_ENTRY_BYTES && entry.compressedSize <= MAX_ENTRY_BYTES)
            val end = sorted.getOrNull(index + 1)?.localOffset ?: endRecord.directoryOffset
            val recordSize = end - entry.localOffset
            requireArchive(recordSize >= 30L + entry.rawName.size + entry.compressedSize)
            requireArchive(recordSize <= entry.compressedSize + MAX_LOCAL_OVERHEAD)
            ReaderRecord(entry, end)
        }.sortedWith(compareBy<ReaderRecord> { it.entry.name != "mimetype" }.thenBy { it.entry.localOffset })
        requireArchive(records.isNotEmpty() && records.size <= MAX_READER_ENTRIES)
        requireArchive(records.any { it.entry.name.substringAfterLast('.', "").lowercase() in setOf("opf", "smil", "html", "htm", "xhtml", "xml") })
        requireArchive(records.sumOf { it.entry.uncompressedSize } <= MAX_TOTAL_TEXT_BYTES)
        requireArchive(records.sumOf { it.entry.compressedSize } <= MAX_TRANSFER_BYTES)

        ZipOutputStream(target.outputStream().buffered()).use { output ->
            var index = 0
            while (index < records.size) {
                val first = records[index]
                var endIndex = index + 1
                var rangeEnd = first.end
                // Adjacent text records share one request; never bridge an embedded audio entry.
                while (endIndex < records.size && records[endIndex].entry.localOffset == rangeEnd &&
                    records[endIndex].end - first.entry.localOffset <= BATCH_BYTES
                ) {
                    rangeEnd = records[endIndex].end
                    endIndex++
                }
                val bytes = readRange(first.entry.localOffset, rangeEnd - first.entry.localOffset)
                for (record in records.subList(index, endIndex)) {
                    val relativeOffset = (record.entry.localOffset - first.entry.localOffset).toInt()
                    val recordEnd = (record.end - first.entry.localOffset).toInt()
                    val contents = readEntry(bytes, relativeOffset, recordEnd, record.entry)
                    val zipEntry = ZipEntry(record.entry.name)
                    if (record.entry.name == "mimetype") {
                        zipEntry.method = ZipEntry.STORED
                        zipEntry.size = contents.size.toLong()
                        zipEntry.crc = record.entry.crc
                    }
                    output.putNextEntry(zipEntry)
                    output.write(contents)
                    output.closeEntry()
                }
                index = endIndex
            }
        }
    }

    private fun readRange(start: Long, size: Long): ByteArray {
        requireArchive(start >= 0L && size in 1..MAX_RANGE_BYTES && start <= archiveSize - size)
        cachedRanges.firstOrNull { start >= it.start && start + size <= it.start + it.bytes.size }?.let {
            val offset = (start - it.start).toInt()
            return it.bytes.copyOfRange(offset, offset + size.toInt())
        }
        return requestRange(start, start + size - 1L).also {
            if (cachedRanges.size < 2) cachedRanges += it
        }.bytes
    }

    private fun requestRange(start: Long?, end: Long?): ArchiveRange {
        requireArchive(++requestCount <= MAX_REQUESTS)
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "AudioPlayerPrototype/1.0")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Range", if (start == null) "bytes=-$TAIL_BYTES" else "bytes=$start-$end")
        validator?.let { connection.setRequestProperty("If-Range", it) }
        try {
            requireArchive(connection.responseCode == HttpURLConnection.HTTP_PARTIAL)
            requireArchive(connection.contentEncoding.isNullOrBlank() || connection.contentEncoding.equals("identity", true))
            val match = CONTENT_RANGE.matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                ?: throw IOException("Missing or invalid archive Content-Range")
            val actualStart = match.groupValues[1].toLongOrNull() ?: throw IOException("Invalid range start")
            val actualEnd = match.groupValues[2].toLongOrNull() ?: throw IOException("Invalid range end")
            val total = match.groupValues[3].toLongOrNull() ?: throw IOException("Invalid archive length")
            requireArchive(total > 0 && actualStart >= 0 && actualEnd >= actualStart && actualEnd < total)
            val expectedStart = start ?: (total - TAIL_BYTES).coerceAtLeast(0L)
            val expectedEnd = end ?: (total - 1L)
            requireArchive(actualStart == expectedStart && actualEnd == expectedEnd)
            requireArchive(archiveSize == 0L || archiveSize == total)
            val size = actualEnd - actualStart + 1L
            requireArchive(size <= MAX_RANGE_BYTES && transferredBytes + size <= MAX_TRANSFER_BYTES)
            val responseValidator = connection.getHeaderField("ETag")?.takeUnless { it.startsWith("W/") }
                ?: connection.getHeaderField("Last-Modified")
            requireArchive(validator == null || responseValidator == null || validator == responseValidator)
            if (validator == null) validator = responseValidator
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
            requireArchive(contentLength == null || contentLength == size)
            val body = connection.inputStream.use { it.readExactly(size.toInt()) }
            transferredBytes += size
            return ArchiveRange(actualStart, total, body)
        } finally {
            connection.disconnect()
        }
    }
}

private data class ArchiveRange(val start: Long, val totalSize: Long, val bytes: ByteArray)
private data class ZipEndRecord(val entryCount: Int, val directoryOffset: Long, val directorySize: Int)
private data class ReaderRecord(val entry: RemoteZipEntry, val end: Long)
private data class RemoteZipEntry(
    val name: String,
    val rawName: ByteArray,
    val flags: Int,
    val method: Int,
    val crc: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val localOffset: Long,
) {
    fun isReaderSource(): Boolean = name == "mimetype" ||
        name.substringAfterLast('.', "").lowercase() in READER_EXTENSIONS
}

private fun findEndRecord(tail: ArchiveRange): ZipEndRecord {
    val bytes = tail.bytes
    for (offset in bytes.size - 22 downTo 0) {
        if (bytes.uint32(offset) != 0x06054b50L) continue
        if (offset + 22 + bytes.uint16(offset + 20) != bytes.size) continue
        requireArchive(bytes.uint16(offset + 4) == 0 && bytes.uint16(offset + 6) == 0)
        val count = bytes.uint16(offset + 10)
        requireArchive(count in 1 until 65_535 && count <= MAX_ARCHIVE_ENTRIES)
        requireArchive(bytes.uint16(offset + 8) == count)
        val size = bytes.uint32(offset + 12)
        val start = bytes.uint32(offset + 16)
        requireArchive(size in 1..MAX_DIRECTORY_BYTES && start != 0xffffffffL)
        requireArchive(start + size == tail.start + offset)
        return ZipEndRecord(count, start, size.toInt())
    }
    throw IOException("Archive has no supported ZIP directory")
}

private fun parseDirectory(bytes: ByteArray, end: ZipEndRecord): List<RemoteZipEntry> {
    val entries = ArrayList<RemoteZipEntry>(end.entryCount)
    var offset = 0
    repeat(end.entryCount) {
        requireArchive(offset + 46 <= bytes.size && bytes.uint32(offset) == 0x02014b50L)
        val flags = bytes.uint16(offset + 8)
        val nameLength = bytes.uint16(offset + 28)
        val extraLength = bytes.uint16(offset + 30)
        val commentLength = bytes.uint16(offset + 32)
        val recordEnd = offset + 46 + nameLength + extraLength + commentLength
        requireArchive(nameLength > 0 && recordEnd <= bytes.size && bytes.uint16(offset + 34) == 0)
        val rawName = bytes.copyOfRange(offset + 46, offset + 46 + nameLength)
        val name = rawName.toString(if (flags and 0x800 != 0) Charsets.UTF_8 else ZIP_LEGACY_CHARSET)
        requireArchive(name.none { it == '\u0000' } && !name.startsWith('/') && !name.contains('\\'))
        requireArchive(name.split('/').none { it == ".." })
        val compressedSize = bytes.uint32(offset + 20)
        val uncompressedSize = bytes.uint32(offset + 24)
        val localOffset = bytes.uint32(offset + 42)
        requireArchive(compressedSize != 0xffffffffL && uncompressedSize != 0xffffffffL && localOffset < end.directoryOffset)
        entries += RemoteZipEntry(name, rawName, flags, bytes.uint16(offset + 10), bytes.uint32(offset + 16), compressedSize, uncompressedSize, localOffset)
        offset = recordEnd
    }
    requireArchive(offset == bytes.size && entries.map { it.name }.distinct().size == entries.size)
    return entries
}

private fun readEntry(bytes: ByteArray, offset: Int, recordEnd: Int, entry: RemoteZipEntry): ByteArray {
    requireArchive(offset + 30 <= recordEnd && recordEnd <= bytes.size && bytes.uint32(offset) == 0x04034b50L)
    requireArchive(bytes.uint16(offset + 6) == entry.flags && bytes.uint16(offset + 8) == entry.method)
    val nameLength = bytes.uint16(offset + 26)
    val extraLength = bytes.uint16(offset + 28)
    val dataOffset = offset + 30 + nameLength + extraLength
    requireArchive(nameLength == entry.rawName.size && dataOffset <= recordEnd)
    requireArchive(bytes.copyOfRange(offset + 30, offset + 30 + nameLength).contentEquals(entry.rawName))
    requireArchive(dataOffset.toLong() + entry.compressedSize <= recordEnd)
    if (entry.flags and 8 == 0) {
        requireArchive(bytes.uint32(offset + 14) == entry.crc && bytes.uint32(offset + 18) == entry.compressedSize && bytes.uint32(offset + 22) == entry.uncompressedSize)
    }
    val compressed = ByteArrayInputStream(bytes, dataOffset, entry.compressedSize.toInt())
    val contents = when (entry.method) {
        0 -> compressed.use { it.readExactly(entry.uncompressedSize.toInt()) }
        8 -> {
            val inflater = Inflater(true)
            try {
                InflaterInputStream(compressed, inflater).use {
                    val inflated = it.readExactly(entry.uncompressedSize.toInt())
                    requireArchive(inflater.finished() && inflater.remaining == 0)
                    inflated
                }
            } finally {
                inflater.end()
            }
        }
        else -> throw IOException("Unsupported ZIP compression")
    }
    requireArchive(CRC32().apply { update(contents) }.value == entry.crc)
    return contents
}

private fun InputStream.readExactly(size: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(size, 65_536))
    val buffer = ByteArray(16_384)
    var remaining = size
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) throw IOException("Truncated archive range or entry")
        output.write(buffer, 0, read)
        remaining -= read
    }
    requireArchive(read() == -1)
    return output.toByteArray()
}

private fun ByteArray.uint16(offset: Int): Int =
    (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

private fun ByteArray.uint32(offset: Int): Long =
    uint16(offset).toLong() or (uint16(offset + 2).toLong() shl 16)

private fun requireArchive(valid: Boolean) {
    if (!valid) throw IOException("Unsupported or inconsistent reader archive")
}

private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)", RegexOption.IGNORE_CASE)
private val ZIP_LEGACY_CHARSET: Charset = Charset.forName("CP437")
private val READER_EXTENSIONS = setOf("opf", "smil", "xml", "xhtml", "html", "htm", "ncx", "css", "txt", "svg", "json", "js", "xsl", "xslt", "xsd", "dtd", "ent")
private const val TAIL_BYTES = 65_557L
private const val MAX_DIRECTORY_BYTES = 8L * 1024 * 1024
private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
private const val MAX_LOCAL_OVERHEAD = 30L + 65_535L + 65_535L + 24L
private const val MAX_RANGE_BYTES = MAX_ENTRY_BYTES + MAX_LOCAL_OVERHEAD
private const val MAX_TOTAL_TEXT_BYTES = 64L * 1024 * 1024
private const val MAX_TRANSFER_BYTES = 32L * 1024 * 1024
private const val BATCH_BYTES = 2L * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 10_000
private const val MAX_READER_ENTRIES = 2_000
private const val MAX_REQUESTS = 256
