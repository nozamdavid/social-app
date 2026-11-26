package am.noz.caelum.core.model

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val BASE32 = "abcdefghijklmnopqrstuvwxyz234567"

/** Hash the canonical DAG-CBOR representation of an AT Protocol record. */
internal fun recordCid(record: JsonObject): String {
    val cbor = ByteArrayOutputStream().apply { writeDagCbor(record) }.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256").digest(cbor)
    val cid = byteArrayOf(1, 0x71, 0x12, digest.size.toByte()) + digest
    return buildString {
        append('b')
        var bits = 0
        var buffer = 0
        for (byte in cid) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                append(BASE32[(buffer ushr bits) and 31])
            }
        }
        if (bits > 0) append(BASE32[(buffer shl (5 - bits)) and 31])
    }
}

private fun ByteArrayOutputStream.writeDagCbor(value: JsonElement) {
    when (value) {
        JsonNull -> write(0xf6)
        is JsonObject -> {
            val link = value["\$link"] as? JsonPrimitive
            if (value.size == 1 && link?.isString == true) {
                val bytes = decodeCid(link.content)
                writeHeader(6, 42)
                writeHeader(2, bytes.size.toLong() + 1)
                write(0)
                write(bytes)
            } else {
                val fields = value.entries.map { it.key.encodeToByteArray() to it.value }
                    .sortedWith { first, second -> compareCborKeys(first.first, second.first) }
                writeHeader(5, fields.size.toLong())
                for ((key, item) in fields) {
                    writeHeader(3, key.size.toLong())
                    write(key)
                    writeDagCbor(item)
                }
            }
        }
        is JsonArray -> {
            writeHeader(4, value.size.toLong())
            value.forEach { writeDagCbor(it) }
        }
        is JsonPrimitive -> when {
            value.isString -> {
                val bytes = value.content.encodeToByteArray()
                writeHeader(3, bytes.size.toLong())
                write(bytes)
            }
            value.content == "true" -> write(0xf5)
            value.content == "false" -> write(0xf4)
            else -> {
                val integer = value.content.toLong()
                if (integer >= 0) writeHeader(0, integer) else writeHeader(1, -1 - integer)
            }
        }
    }
}

private fun compareCborKeys(first: ByteArray, second: ByteArray): Int {
    val lengthOrder = first.size.compareTo(second.size)
    if (lengthOrder != 0) return lengthOrder
    for (index in first.indices) {
        val order = (first[index].toInt() and 0xff).compareTo(second[index].toInt() and 0xff)
        if (order != 0) return order
    }
    return 0
}

private fun ByteArrayOutputStream.writeHeader(type: Int, argument: Long) {
    val prefix = type shl 5
    val bytes = when {
        argument < 24 -> {
            write(prefix or argument.toInt())
            return
        }
        argument <= 0xff -> 1
        argument <= 0xffff -> 2
        argument <= 0xffffffff -> 4
        else -> 8
    }
    write(prefix or (24 + Integer.numberOfTrailingZeros(bytes)))
    for (shift in (bytes - 1) * 8 downTo 0 step 8) {
        write((argument ushr shift).toInt())
    }
}

private fun decodeCid(value: String): ByteArray {
    require(value.startsWith('b')) { "Expected a base32 CID" }
    return ByteArrayOutputStream().apply {
        var bits = 0
        var buffer = 0
        for (character in value.drop(1)) {
            val digit = BASE32.indexOf(character)
            require(digit >= 0) { "Invalid base32 CID" }
            buffer = (buffer shl 5) or digit
            bits += 5
            if (bits >= 8) {
                bits -= 8
                write(buffer ushr bits)
            }
        }
        require(size() > 0 && buffer and ((1 shl bits) - 1) == 0) { "Invalid base32 CID" }
    }.toByteArray()
}
