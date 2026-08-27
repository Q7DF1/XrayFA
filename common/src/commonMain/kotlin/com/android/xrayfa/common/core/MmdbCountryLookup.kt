package com.android.xrayfa.common.core

/**
 * Country ISO lookup against a MaxMind MMDB (GeoLite2-Country / GeoIP2-Country).
 *
 * Returns `null` when the IP is not in the database, is not a literal IPv4/IPv6
 * address, or the file is not a valid MMDB. A present record with no `iso_code`
 * returns `""` so [GeoIpCountryDisplay] can show ❓.
 */
object MmdbCountryLookup {
    fun isoCode(database: ByteArray, ip: String): String? {
        val ipBytes = parseIpLiteral(ip) ?: return null
        return try {
            MmdbReader(database).countryIso(ipBytes)
        } catch (_: Exception) {
            null
        }
    }

    fun isIpLiteral(ip: String): Boolean = parseIpLiteral(ip) != null

    fun countryFlag(database: ByteArray, ip: String): String =
        GeoIpCountryDisplay.fromIsoCode(isoCode(database, ip))
}

internal fun parseIpLiteral(ip: String): ByteArray? {
    val trimmed = ip.trim()
    if (trimmed.isEmpty()) return null
    return if (':' in trimmed) parseIpv6(trimmed) else parseIpv4(trimmed)
}

private fun parseIpv4(ip: String): ByteArray? {
    val parts = ip.split('.')
    if (parts.size != 4) return null
    val out = ByteArray(4)
    for (i in 0..3) {
        val n = parts[i].toIntOrNull() ?: return null
        if (n !in 0..255) return null
        out[i] = n.toByte()
    }
    return out
}

private fun parseIpv6(ip: String): ByteArray? {
    var input = ip
    val lastColon = input.lastIndexOf(':')
    if ('.' in input) {
        if (lastColon < 0) return null
        val v4 = parseIpv4(input.substring(lastColon + 1)) ?: return null
        val hi = ((v4[0].toInt() and 0xFF) shl 8) or (v4[1].toInt() and 0xFF)
        val lo = ((v4[2].toInt() and 0xFF) shl 8) or (v4[3].toInt() and 0xFF)
        input = input.substring(0, lastColon + 1) +
            hi.toString(16) + ":" + lo.toString(16)
    }
    val doubleColon = input.indexOf("::")
    val groups: List<String> =
        if (doubleColon >= 0) {
            if (input.indexOf("::", doubleColon + 2) >= 0) return null
            val left =
                if (doubleColon == 0) {
                    emptyList()
                } else {
                    input.substring(0, doubleColon).split(':')
                }
            val rightStart = doubleColon + 2
            val right =
                if (rightStart >= input.length) {
                    emptyList()
                } else {
                    input.substring(rightStart).split(':')
                }
            if (left.any { it.isEmpty() } || right.any { it.isEmpty() }) return null
            val missing = 8 - left.size - right.size
            if (missing < 1) return null
            left + List(missing) { "0" } + right
        } else {
            val parts = input.split(':')
            if (parts.size != 8 || parts.any { it.isEmpty() }) return null
            parts
        }
    if (groups.size != 8) return null
    val out = ByteArray(16)
    for (i in 0..7) {
        val g = groups[i]
        if (g.length > 4) return null
        val v = g.toIntOrNull(16) ?: return null
        if (v !in 0..0xFFFF) return null
        out[i * 2] = (v ushr 8).toByte()
        out[i * 2 + 1] = (v and 0xFF).toByte()
    }
    return out
}

private class MmdbReader(private val data: ByteArray) {
    private val metadataOffset = findMetadataStart()
    private val metadata = decodeValue(Cursor(data, metadataOffset), pointerBase = metadataOffset) as? Val.Map
        ?: error("MMDB metadata is not a map")
    private val nodeCount = metadata.long("node_count")
    private val recordSize = metadata.long("record_size").toInt()
    private val ipVersion = metadata.long("ip_version").toInt()
    private val nodeByteSize = recordSize / 4
    private val searchTreeSize = nodeCount * nodeByteSize
    private val ipV4Start = findIpV4Start()

    fun countryIso(ipBytes: ByteArray): String? {
        if (ipVersion == 4 && ipBytes.size == 16) return null
        val record = traverse(ipBytes)
        if (record <= nodeCount) return null
        val resolved = (record - nodeCount) + searchTreeSize
        val pointerBase = searchTreeSize + DATA_SECTION_SEPARATOR
        val decoded = decodeValue(Cursor(data, resolved.toInt()), pointerBase.toInt())
        val map = (decoded as? Val.Map)?.v ?: return ""
        val country = (map["country"] as? Val.Map)?.v
        val iso = (country?.get("iso_code") as? Val.Str)?.v
        return iso ?: ""
    }

    private fun traverse(ipBytes: ByteArray): Long {
        val bitLength = ipBytes.size * 8
        var record = startNode(bitLength)
        var i = 0
        while (i < bitLength && record < nodeCount) {
            val b = ipBytes[i / 8].toInt() and 0xFF
            val bit = 1 and (b shr (7 - (i % 8)))
            record = readNode(record, bit)
            i++
        }
        return record
    }

    private fun startNode(bitLength: Int): Long =
        if (ipVersion == 6 && bitLength == 32) ipV4Start else 0L

    private fun findIpV4Start(): Long {
        if (ipVersion == 4) return 0L
        var node = 0L
        var i = 0
        while (i < 96 && node < nodeCount) {
            node = readNode(node, 0)
            i++
        }
        return node
    }

    private fun readNode(nodeNumber: Long, index: Int): Long {
        val baseOffset = nodeNumber * nodeByteSize
        return when (recordSize) {
            24 -> decodeLongAt(baseOffset + index * 3L, 0, 3)
            28 -> {
                val middleByte = unsigned(data[(baseOffset + 3).toInt()])
                val middle = if (index == 0) (0xF0 and middleByte) ushr 4 else 0x0F and middleByte
                decodeLongAt(baseOffset + index * 4L, middle, 3)
            }
            32 -> decodeLongAt(baseOffset + index * 4L, 0, 4)
            else -> error("Unknown MMDB record size: $recordSize")
        }
    }

    private fun decodeLongAt(offset: Long, base: Int, size: Int): Long {
        var integer = base.toLong()
        var pos = offset.toInt()
        repeat(size) {
            integer = (integer shl 8) or unsigned(data[pos]).toLong()
            pos++
        }
        return integer
    }

    private fun findMetadataStart(): Int {
        val marker = METADATA_START_MARKER
        val fileSize = data.size
        var i = 0
        while (i < fileSize - marker.size + 1) {
            var j = 0
            while (j < marker.size) {
                if (data[fileSize - i - j - 1] != marker[marker.size - j - 1]) break
                j++
            }
            if (j == marker.size) return fileSize - i
            i++
        }
        error("MaxMind DB metadata marker not found")
    }

    private companion object {
        const val DATA_SECTION_SEPARATOR = 16L
        val METADATA_START_MARKER =
            byteArrayOf(
                0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(),
                'M'.code.toByte(), 'a'.code.toByte(), 'x'.code.toByte(),
                'M'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(),
                'd'.code.toByte(), '.'.code.toByte(),
                'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            )
    }
}

private sealed class Val {
    data class Str(val v: String) : Val()
    data class Num(val v: Long) : Val()
    data class Bool(val v: Boolean) : Val()
    data class Map(val v: kotlin.collections.Map<String, Val>) : Val()
    data class Arr(val v: List<Val>) : Val()
    data object Other : Val()
}

private fun Val.Map.long(key: String): Long =
    (v[key] as? Val.Num)?.v ?: error("MMDB metadata missing $key")

private class Cursor(val data: ByteArray, var pos: Int) {
    fun getU(): Int {
        if (pos >= data.size) error("MMDB truncated")
        return unsigned(data[pos++])
    }

    fun take(n: Int): ByteArray {
        if (pos + n > data.size) error("MMDB truncated")
        val slice = data.copyOfRange(pos, pos + n)
        pos += n
        return slice
    }
}

private fun unsigned(b: Byte): Int = b.toInt() and 0xFF

private fun decodeValue(cursor: Cursor, pointerBase: Int): Val {
    val ctrlByte = cursor.getU()
    var type = ctrlByte ushr 5
    if (type == TYPE_POINTER) {
        val pointerSize = ((ctrlByte ushr 3) and 0x3) + 1
        val base = if (pointerSize == 4) 0 else ctrlByte and 0x7
        val packed = decodeLong(cursor, base, pointerSize)
        val pointer = packed + pointerBase + POINTER_VALUE_OFFSETS[pointerSize]
        val saved = cursor.pos
        cursor.pos = pointer.toInt()
        val value = decodeValue(cursor, pointerBase)
        cursor.pos = saved
        return value
    }
    if (type == TYPE_EXTENDED) {
        val typeNum = cursor.getU() + 7
        if (typeNum < 8) error("MMDB extended type < 8")
        type = typeNum
    }
    var size = ctrlByte and 0x1f
    if (size >= 29) {
        size =
            when (size) {
                29 -> 29 + cursor.getU()
                30 -> 285 + decodeLong(cursor, 0, 2).toInt()
                else -> 65821 + decodeLong(cursor, 0, 3).toInt()
            }
    }
    return decodeByType(cursor, pointerBase, type, size)
}

private fun decodeByType(cursor: Cursor, pointerBase: Int, type: Int, size: Int): Val =
    when (type) {
        TYPE_UTF8 -> Val.Str(cursor.take(size).decodeToString())
        TYPE_DOUBLE -> {
            cursor.take(8)
            Val.Other
        }
        TYPE_BYTES -> {
            cursor.take(size)
            Val.Other
        }
        TYPE_UINT16, TYPE_UINT32, TYPE_INT32 -> Val.Num(decodeLong(cursor, 0, size))
        TYPE_MAP -> {
            val map = LinkedHashMap<String, Val>(size)
            repeat(size) {
                val key = (decodeValue(cursor, pointerBase) as? Val.Str)?.v ?: error("MMDB map key")
                map[key] = decodeValue(cursor, pointerBase)
            }
            Val.Map(map)
        }
        TYPE_UINT64, TYPE_UINT128 -> {
            val bytes = cursor.take(size)
            var n = 0L
            for (b in bytes) n = (n shl 8) or unsigned(b).toLong()
            Val.Num(n)
        }
        TYPE_ARRAY -> {
            val list = ArrayList<Val>(size)
            repeat(size) { list.add(decodeValue(cursor, pointerBase)) }
            Val.Arr(list)
        }
        TYPE_BOOLEAN ->
            Val.Bool(
                when (size) {
                    0 -> false
                    1 -> true
                    else -> error("MMDB bad boolean")
                },
            )
        TYPE_FLOAT -> {
            cursor.take(4)
            Val.Other
        }
        else -> {
            cursor.take(size)
            Val.Other
        }
    }

private fun decodeLong(cursor: Cursor, base: Int, size: Int): Long {
    var integer = base.toLong()
    repeat(size) {
        integer = (integer shl 8) or cursor.getU().toLong()
    }
    return integer
}

private val POINTER_VALUE_OFFSETS = intArrayOf(0, 0, 1 shl 11, (1 shl 19) + (1 shl 11), 0)

private const val TYPE_EXTENDED = 0
private const val TYPE_POINTER = 1
private const val TYPE_UTF8 = 2
private const val TYPE_DOUBLE = 3
private const val TYPE_BYTES = 4
private const val TYPE_UINT16 = 5
private const val TYPE_UINT32 = 6
private const val TYPE_MAP = 7
private const val TYPE_INT32 = 8
private const val TYPE_UINT64 = 9
private const val TYPE_UINT128 = 10
private const val TYPE_ARRAY = 11
private const val TYPE_BOOLEAN = 14
private const val TYPE_FLOAT = 15
