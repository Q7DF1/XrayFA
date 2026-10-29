package com.android.xrayfa.common.core

/** DNS wire format for RFC 8484 POST requests, including compressed CNAME answers. */
object DohDnsMessage {
    const val A = 1
    const val AAAA = 28
    data class Answer(val addresses: List<String>, val ttlSeconds: Long, val canonicalName: String? = null)

    fun query(host: String, type: Int): ByteArray {
        require(type == A || type == AAAA)
        val name = host.trimEnd('.').lowercase()
        require(name.length in 1..253)
        val bytes = mutableListOf<Byte>(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)
        for (label in name.split('.')) {
            require(label.length in 1..63 && label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' })
            bytes.add(label.length.toByte())
            bytes.addAll(label.encodeToByteArray().toList())
        }
        bytes.addAll(listOf(0, 0, type.toByte(), 0, 1))
        return bytes.toByteArray()
    }

    fun answer(message: ByteArray, host: String, type: Int): Answer {
        val reader = Reader(message)
        require(message.size in 12..65535)
        require(reader.u16(0) == 0 && reader.u16(2) and 0xfa0f == 0x8000) { "Invalid DNS response" }
        require(reader.u16(4) == 1)
        val question = reader.name(12)
        require(question.first == host.trimEnd('.').lowercase()) { "DNS question mismatch" }
        require(reader.u16(question.second) == type && reader.u16(question.second + 2) == 1)
        var cursor = question.second + 4
        data class Record(val owner: String, val type: Int, val ttl: Long, val ip: String?, val alias: String?)
        val records = mutableListOf<Record>()
        repeat(reader.u16(6)) {
            val owner = reader.name(cursor)
            cursor = owner.second
            val recordType = reader.u16(cursor)
            val recordClass = reader.u16(cursor + 2)
            val ttl = reader.u32(cursor + 4)
            val length = reader.u16(cursor + 8)
            cursor += 10
            require(cursor + length <= message.size)
            if (recordClass == 1) {
                val ip = when {
                    recordType == A && length == 4 -> (0..3).joinToString(".") { reader.u8(cursor + it).toString() }
                    recordType == AAAA && length == 16 -> (0..7).joinToString(":") { reader.u16(cursor + it * 2).toString(16) }
                    else -> null
                }
                val alias = if (recordType == 5) reader.name(cursor).also {
                    require(it.second == cursor + length)
                }.first else null
                records += Record(owner.first, recordType, ttl, ip, alias)
            }
            cursor += length
        }
        var current = question.first
        var ttl = 900L
        val visited = mutableSetOf<String>()
        repeat(16) {
            require(visited.add(current)) { "DNS CNAME loop" }
            val addresses = records.filter { it.owner == current && it.type == type && it.ip != null }
            if (addresses.isNotEmpty()) return Answer(addresses.map { requireNotNull(it.ip) }.distinct(), minOf(ttl, addresses.minOf { it.ttl }))
            val alias = records.firstOrNull { it.owner == current && it.alias != null }
                ?: return Answer(emptyList(), ttl, current.takeIf { it != question.first })
            ttl = minOf(ttl, alias.ttl)
            current = requireNotNull(alias.alias)
        }
        error("Too many DNS aliases")
    }

    private class Reader(private val bytes: ByteArray) {
        fun u8(offset: Int): Int { require(offset in bytes.indices); return bytes[offset].toInt() and 255 }
        fun u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)
        fun u32(offset: Int): Long = (u16(offset).toLong() shl 16) or u16(offset + 2).toLong()
        fun name(start: Int): Pair<String, Int> {
            var cursor = start
            var end: Int? = null
            val labels = mutableListOf<String>()
            val visited = mutableSetOf<Int>()
            var size = 0
            while (true) {
                require(visited.add(cursor)) { "DNS compression loop" }
                val length = u8(cursor)
                if (length and 0xc0 == 0xc0) {
                    val target = u16(cursor) and 0x3fff
                    require(target < cursor) { "Invalid DNS compression pointer" }
                    if (end == null) end = cursor + 2
                    cursor = target
                } else {
                    require(length <= 63)
                    cursor++
                    if (length == 0) return labels.joinToString(".").lowercase() to (end ?: cursor)
                    require(cursor + length <= bytes.size)
                    size += length + 1
                    require(size <= 254)
                    labels += bytes.copyOfRange(cursor, cursor + length).decodeToString(throwOnInvalidSequence = true)
                    cursor += length
                }
            }
        }
    }
}
