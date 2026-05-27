package com.android.xrayfa.utils

import com.google.gson.JsonParser
import java.util.Base64

object LinkUtils {
    /**
     * Cleans a proxy URL for sharing by removing security-sensitive or personal-preference parameters
     * like 'allowInsecure'.
     */
    fun cleanUrlForSharing(url: String): String {
        return try {
            when {
                url.startsWith("vmess://") -> cleanVmess(url)
                url.startsWith("vless://") || url.startsWith("trojan://") || url.startsWith("hysteria2://") -> cleanUriBased(url)
                else -> url
            }
        } catch (e: Exception) {
            url // Fallback to original if parsing fails
        }
    }

    private fun cleanVmess(url: String): String {
        val encoded = url.removePrefix("vmess://")
        val decoded = String(Base64.getDecoder().decode(encoded))
        val json = JsonParser.parseString(decoded).asJsonObject
        
        // Remove allowInsecure
        json.remove("allowInsecure")
        
        val cleanedEncoded = Base64.getEncoder().encodeToString(json.toString().toByteArray())
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
