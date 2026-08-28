package com.android.xrayfa.common.utils

import com.android.xrayfa.common.json.AppJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Strips personal-preference query/json keys (currently `allowInsecure`) so a
 * shared node URL does not leak the sharer's TLS-skip setting.
 *
 * Android `LinkUtils.cleanUrlForSharing` is the historical JVM copy; keep both
 * in sync if the strip list grows.
 */
object ShareLinkCleaner {
    fun cleanUrlForSharing(url: String): String {
        return try {
            when {
                url.startsWith("vmess://") -> cleanVmess(url)
                url.startsWith("vless://") ||
                    url.startsWith("trojan://") ||
                    url.startsWith("hysteria2://") -> cleanUriBased(url)
                else -> url
            }
        } catch (_: Exception) {
            url
        }
    }

    private fun cleanVmess(url: String): String {
        val encoded = url.removePrefix("vmess://")
        val decoded = Base64Compat.decode(encoded).decodeToString()
        val obj = AppJson.parseToJsonElement(decoded).jsonObject
        val cleaned: JsonObject = buildJsonObject {
            obj.forEach { (key, value) ->
                if (key != "allowInsecure") put(key, value)
            }
        }
        val cleanedEncoded = Base64Compat.encode(cleaned.toString().encodeToByteArray())
        return "vmess://$cleanedEncoded"
    }

    private fun cleanUriBased(url: String): String {
        val parts = url.split("#", limit = 2)
        val mainPart = parts[0]
        val fragment = if (parts.size > 1) "#${parts[1]}" else ""

        val subParts = mainPart.split("?", limit = 2)
        if (subParts.size < 2) return url

        val base = subParts[0]
        val query = subParts[1]
        val cleanedQuery = query.split("&")
            .filter { !it.startsWith("allowInsecure=") }
            .joinToString("&")

        return if (cleanedQuery.isEmpty()) {
            "$base$fragment"
        } else {
            "$base?$cleanedQuery$fragment"
        }
    }
}
