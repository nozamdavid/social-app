package am.noz.caelum.core.storage

import am.noz.caelum.core.model.AtProtoJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Reads the unencrypted MMKV stores written by React Native without modifying them. */
internal object LegacyMmkvReader {
    private const val HEADER_SIZE = 4
    private const val ACTUAL_SIZE_VERSION = 3
    private const val FLAGS_VERSION = 4
    private const val FLAGS_OFFSET = 104
    private const val EXPIRATION_FLAG = 1L

    /**
     * A missing store is empty. A null result means that neither the current record stream nor
     * its last confirmed version could be read.
     */
    fun read(file: File): Map<String, JsonElement>? {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val metadataFile = File(file.parentFile, "${file.name}.crc")
            val metadata = if (metadataFile.exists()) {
                RandomAccessFile(metadataFile, "r").use { metadata ->
                    val bytes = ByteArray(minOf(metadata.length(), (FLAGS_OFFSET + Long.SIZE_BYTES).toLong()).toInt())
                    metadata.readFully(bytes)
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                }
            } else {
                null
            }

            RandomAccessFile(file, "r").use { input ->
                val headerSize = Integer.reverseBytes(input.readInt())
                if (metadata == null) {
                    return@use readRecords(input, headerSize, null, expires = false)
                }

                val checksum = metadata.getInt(0)
                val version = metadata.getInt(4)
                val expires = version >= FLAGS_VERSION &&
                    metadata.getLong(FLAGS_OFFSET) and EXPIRATION_FLAG != 0L
                if (version < ACTUAL_SIZE_VERSION) {
                    return@use readRecords(input, headerSize, checksum, expires)
                }

                // The mmap header can retain an old length after MMKV starts updating the sidecar.
                val actualSize = metadata.getInt(28)
                readRecords(input, actualSize, checksum, expires)
                    ?: readRecords(input, headerSize, checksum, expires)
                    ?: readRecords(input, metadata.getInt(32), metadata.getInt(36), expires)
            }
        }.getOrNull()
    }

    private fun readRecords(
        input: RandomAccessFile,
        size: Int,
        checksum: Int?,
        expires: Boolean,
    ): Map<String, JsonElement>? {
        if (size < 0 || size > input.length() - HEADER_SIZE) return null
        val bytes = ByteArray(size)
        input.seek(HEADER_SIZE.toLong())
        input.readFully(bytes)
        if (checksum != null && CRC32().apply { update(bytes) }.value.toInt() != checksum) return null
        if (size == 0) return emptyMap()

        return runCatching {
            val records = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val values = mutableMapOf<String, JsonElement>()
            records.readVarint() // MMKV's map-size placeholder
            val now = System.currentTimeMillis() / 1000
            while (records.hasRemaining()) {
                val key = records.readString()
                require(key.isNotEmpty())
                val length = records.readVarint()
                require(length in 0..records.remaining())
                val value = records.slice().order(ByteOrder.LITTLE_ENDIAN)
                value.limit(length)
                records.position(records.position() + length)

                // Later values replace earlier ones, including tombstones and unsupported values.
                values.remove(key)
                if (length == 0) continue
                if (expires) {
                    if (length < Int.SIZE_BYTES) continue
                    val deadline = Integer.toUnsignedLong(value.getInt(length - Int.SIZE_BYTES))
                    if (deadline != 0L && deadline <= now) continue
                    value.limit(length - Int.SIZE_BYTES)
                }

                val envelope = runCatching {
                    val string = value.readString()
                    if (value.hasRemaining()) null else AtProtoJson.parseToJsonElement(string)
                }.getOrNull()
                if (envelope is JsonObject && "data" in envelope) values[key] = envelope
            }
            values
        }.getOrNull()
    }

    private fun ByteBuffer.readString(): String {
        val size = readVarint()
        require(size in 0..remaining())
        val string = ByteArray(size)
        get(string)
        return string.decodeToString(throwOnInvalidSequence = true)
    }

    private fun ByteBuffer.readVarint(): Int {
        var value = 0
        for (shift in 0..28 step 7) {
            val byte = get().toInt() and 0xff
            require(shift < 28 || byte <= 0x0f)
            value = value or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) return value
        }
        error("Invalid MMKV varint")
    }
}
