package com.android.xrayfa.parser

import com.android.xrayfa.common.core.parseIpLiteral

/** Mirrors common/geodata/consts.go at core commit 701af60772cd; no DNS lookup. */
fun isXrayPrivateEndpoint(address: String): Boolean {
    val host = address.removeSurrounding("[", "]").lowercase().trimEnd('.')
    var ip = parseIpLiteral(host)
    if (ip == null) {
        return privateDomains.any { host == it || host.endsWith(".$it") } ||
            Regex("^[a-z]([a-z0-9-]{0,61}[a-z0-9])?$").matches(host)
    }
    if (ip.size == 16 && ip.take(10).all { it == 0.toByte() } && ip[10] == (-1).toByte() && ip[11] == (-1).toByte()) {
        ip = ip.copyOfRange(12, 16)
    }
    return privateNetworks.any { (network, bits) ->
        ip.size == network.size && (0 until bits).all { bit ->
            val mask = 1 shl (7 - bit % 8)
            (ip[bit / 8].toInt() and mask) == (network[bit / 8].toInt() and mask)
        }
    }
}

private val privateDomains = listOf("lan", "localdomain", "example", "invalid", "localhost", "test", "local", "home.arpa", "internal")
private val privateNetworks = listOf(
    "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
    "192.0.0.0/24", "192.0.2.0/24", "192.88.99.0/24", "192.168.0.0/16", "198.18.0.0/15",
    "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/3", "::/127", "fc00::/7", "fe80::/10", "ff00::/8",
).map { parseIpLiteral(it.substringBefore('/'))!! to it.substringAfter('/').toInt() }
