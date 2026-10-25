package com.android.xrayfa.common.config

/** Defaults and validation for user-selected DNS upstreams. */
object DnsDefaults {
    const val DEFAULT_DOH_SERVERS =
        "https://dns.google/dns-query,https://cloudflare-dns.com/dns-query"
    const val DEFAULT_UDP_DNS_SERVERS = "8.8.8.8,1.1.1.1"

    val dohServers = DEFAULT_DOH_SERVERS.split(',')
    val udpDnsServers = DEFAULT_UDP_DNS_SERVERS.split(',')

    val bootstrapHosts = mapOf(
        "full:dns.google" to listOf("8.8.8.8", "8.8.4.4"),
        "full:cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
    )

    fun bootstrapHostsFor(servers: List<String>): Map<String, List<String>> {
        val hosts = servers.mapNotNull(::endpointHost).map(String::lowercase).toSet()
        return bootstrapHosts.filterKeys { it.removePrefix("full:").lowercase() in hosts }
    }

    /** Invalid or empty DoH values fall back to HTTPS defaults. */
    fun parseDohServers(value: String?): List<String> {
        val entries = value?.split(',')?.map(String::trim).orEmpty()
        if (entries.isNotEmpty() && entries.all(::isValidDohServer)) {
            return entries.distinctBy(String::lowercase)
        }
        return dohServers
    }

    fun isValidDohServer(value: String): Boolean {
        val endpoint = value.trim()
        if (!endpoint.startsWith("https://", ignoreCase = true)) return false
        if (endpoint.any(Char::isWhitespace) || endpoint.contains(',')) return false

        val afterScheme = endpoint.substring(8)
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isBlank() || authority.contains('@')) return false

        val host: String
        val port: String?
        if (authority.startsWith("[")) {
            val closeBracket = authority.indexOf(']')
            if (closeBracket <= 1) return false
            host = authority.substring(1, closeBracket)
            val suffix = authority.substring(closeBracket + 1)
            if (suffix.isNotEmpty() && !suffix.startsWith(':')) return false
            port = suffix.takeIf(String::isNotEmpty)?.drop(1)
        } else {
            if (authority.count { it == ':' } > 1) return false
            host = authority.substringBefore(':')
            port = if (':' in authority) authority.substringAfter(':') else null
        }

        if (host.isBlank()) return false
        if (!authority.startsWith("[") && host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return false
        if (port != null) {
            val portNumber = port.toIntOrNull() ?: return false
            if (portNumber !in 1..65535) return false
        }
        return afterScheme.none { it == '\\' }
    }

    /**
     * Parse literal IP upstreams for classic UDP DNS. Hostnames are rejected so
     * the UDP resolver cannot depend on the DNS path it is being configured to
     * provide. Invalid or empty persisted values fall back to the safe defaults.
     */
    fun parseUdpDnsServers(value: String?): List<String> {
        val entries = value?.split(',')?.map(String::trim).orEmpty()
        if (
            entries.isNotEmpty() &&
            entries.all(::isValidIpAddress) &&
            entries.distinctBy(String::lowercase).size == entries.size
        ) {
            return entries
        }
        return udpDnsServers
    }

    fun isValidUdpDnsServerList(value: String): Boolean {
        val entries = value.split(',').map(String::trim)
        return entries.isNotEmpty() &&
            entries.all(::isValidIpAddress) &&
            entries.distinctBy(String::lowercase).size == entries.size
    }

    fun isValidIpAddress(value: String): Boolean = isValidIpv4Address(value) || isValidIpv6Address(value)

    private fun isValidIpv4Address(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all { it in '0'..'9' } && (part.toIntOrNull()?.let { it in 0..255 } == true)
        }
    }

    private fun isValidIpv6Address(value: String): Boolean {
        if (value.isEmpty() || value.any {
                !(it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.')
            }
        ) {
            return false
        }
        if (value.contains(":::")) return false

        val compressionIndex = value.indexOf("::")
        if (compressionIndex != value.lastIndexOf("::")) return false
        if (compressionIndex < 0 && (value.startsWith(':') || value.endsWith(':'))) return false

        val leftText = if (compressionIndex >= 0) value.substring(0, compressionIndex) else value
        val rightText = if (compressionIndex >= 0) value.substring(compressionIndex + 2) else ""
        val segments = mutableListOf<String>()
        if (leftText.isNotEmpty()) segments += leftText.split(':')
        if (rightText.isNotEmpty()) segments += rightText.split(':')
        if (segments.any(String::isEmpty)) return false

        var segmentCount = 0
        for ((index, segment) in segments.withIndex()) {
            if ('.' in segment) {
                if (
                    index != segments.lastIndex ||
                    (compressionIndex >= 0 && rightText.isEmpty()) ||
                    !isValidIpv4Address(segment)
                ) {
                    return false
                }
                segmentCount += 2
            } else {
                if (
                    segment.length !in 1..4 ||
                    segment.any { !(it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F') }
                ) {
                    return false
                }
                segmentCount++
            }
        }

        return if (compressionIndex >= 0) segmentCount < 8 else segmentCount == 8
    }

    private fun endpointHost(endpoint: String): String? {
        if (!isValidDohServer(endpoint)) return null
        val authority = endpoint.trim().substring(8).takeWhile { it != '/' && it != '?' && it != '#' }
        return if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']')
        } else {
            authority.substringBefore(':')
        }
    }
}
