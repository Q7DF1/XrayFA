package com.android.xrayfa.parser

import com.android.xrayfa.common.utils.UrlCodec

/** Split encoded components first: URI decoded getters lose escaped query separators. */
internal data class ShareLinkUri(
    val credential: String,
    val address: String,
    val port: Int,
    val params: Map<String, String>,
    val remark: String?,
) {
    companion object {
        fun parse(url: String, schemes: Set<String>, defaultPort: Int? = null): ShareLinkUri {
            val uri = UrlCodec.parseUri(url)
            require(uri.scheme?.lowercase() in schemes) { "Unsupported share link scheme" }
            val address = uri.host?.removeSurrounding("[", "]")
            require(!address.isNullOrBlank()) { "Share link server is missing or invalid" }
            val port = if (uri.port == -1) defaultPort else uri.port
            require(port != null && port in 1..65535) { "Share link port must be between 1 and 65535" }
            val credential = uri.userInfo
            require(!credential.isNullOrEmpty()) { "Share link credential is missing" }
            val rawQuery = url.substringBefore('#').substringAfter('?', "")
            val params = rawQuery.split('&').filter { it.isNotEmpty() }.associate { pair ->
                decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
            }
            return ShareLinkUri(credential, address, port, params,
                url.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let(::decode))
        }

        // Query/remark values keep compatibility with legacy URLEncoder links.
        // Credentials come from the URI getter, which preserves literal '+'.
        private fun decode(value: String): String = UrlCodec.decode(value)
    }
}

internal fun encodeShareLink(
    scheme: String, credential: String, address: String, port: Int,
    params: Map<String, String>, remark: String?,
): String {
    require(credential.isNotEmpty()) { "Share link credential is missing" }
    require(address.isNotBlank()) { "Share link server is missing" }
    require(port in 1..65535) { "Share link port must be between 1 and 65535" }
    fun encode(value: String) = UrlCodec.encode(value).replace("+", "%20")
    val host = address.removeSurrounding("[", "]").let { if (':' in it) "[$it]" else it }
    val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
    return buildString {
        append("$scheme://${encode(credential)}@$host:$port")
        if (query.isNotEmpty()) append("?$query")
        if (!remark.isNullOrEmpty()) append("#${encode(remark)}")
    }
}
