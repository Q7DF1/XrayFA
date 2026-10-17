package com.android.xrayfa.common.config

/** Defaults and validation for encrypted DNS upstreams. */
object DnsDefaults {
    const val DEFAULT_DOH_SERVERS =
        "https://dns.google/dns-query,https://cloudflare-dns.com/dns-query"

    val dohServers = DEFAULT_DOH_SERVERS.split(',')

    val bootstrapHosts = mapOf(
        "full:dns.google" to listOf("8.8.8.8", "8.8.4.4"),
        "full:cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1"),
    )

    fun bootstrapHostsFor(servers: List<String>): Map<String, List<String>> {
        val hosts = servers.mapNotNull(::endpointHost).map(String::lowercase).toSet()
        return bootstrapHosts.filterKeys { it.removePrefix("full:").lowercase() in hosts }
    }

    /**
     * Keep invalid or empty persisted values from ever creating a plaintext DNS
     * upstream. Settings UI validates these values before saving; this is the
     * final guard at the config-generation boundary.
     */
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
