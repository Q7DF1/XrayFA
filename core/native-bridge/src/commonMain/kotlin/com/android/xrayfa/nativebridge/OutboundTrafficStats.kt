package com.android.xrayfa.nativebridge

/**
 * Parses libv2ray [CoreController.queryAllOutboundTrafficStats] output:
 * `tag,direction,value;tag,direction,value;`
 */
fun parseOutboundTrafficStats(raw: String): Map<Pair<String, String>, Long> {
    if (raw.isEmpty()) return emptyMap()
    val result = mutableMapOf<Pair<String, String>, Long>()
    for (entry in raw.split(';')) {
        if (entry.isEmpty()) continue
        val parts = entry.split(',')
        if (parts.size != 3) continue
        val value = parts[2].toLongOrNull() ?: continue
        result[parts[0] to parts[1]] = value
    }
    return result
}

fun outboundTrafficValue(raw: String, tag: String, direction: String): Long =
    parseOutboundTrafficStats(raw)[tag to direction] ?: 0L
