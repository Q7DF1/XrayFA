package com.android.xrayfa.config

import kotlinx.serialization.json.*

enum class JsonConfigError { INVALID_JSON, TOO_LARGE, INVALID_CONFIG, DUPLICATE_KEY, TOO_DEEP, INBOUND_REQUIRED, CONFLICT, EXTERNAL_FILE }
class JsonConfigException(val reason: JsonConfigError) : IllegalArgumentException(reason.name)

data class JsonVpnTransport(
    val nativeTun: Boolean = false,
    val socksPort: Int = 10808,
    val socksUser: String = "",
    val socksCredential: String = "",
    val mtu: Int = 1500,
)

object JsonVpnConfig {
    const val MAX_BYTES = 1024 * 1024
    const val VPN_DNS = "198.18.0.1"
    private fun fail(reason: JsonConfigError): Nothing = throw JsonConfigException(reason)

    fun inspect(text: String): JsonObject {
        if (text.encodeToByteArray().size > MAX_BYTES) fail(JsonConfigError.TOO_LARGE)
        val input = text.removePrefix("\uFEFF")
        // Track object keys before the JSON tree parser can discard duplicates.
        val scopes = mutableListOf<MutableSet<String>?>()
        var i = 0
        while (i < input.length) {
            when (input[i]) {
                '{', '[' -> {
                    scopes.add(if (input[i] == '{') mutableSetOf() else null)
                    if (scopes.size > 64) fail(JsonConfigError.TOO_DEEP)
                }
                '}', ']' -> if (scopes.isNotEmpty()) scopes.removeAt(scopes.lastIndex)
                '"' -> {
                    val start = i++
                    while (i < input.length && input[i] != '"') {
                        if (input[i] == '\\') i++
                        i++
                    }
                    if (i >= input.length) fail(JsonConfigError.INVALID_JSON)
                    var next = i + 1
                    while (next < input.length && input[next].isWhitespace()) next++
                    if (next < input.length && input[next] == ':') {
                        val key = try { Json.decodeFromString<String>(input.substring(start, i + 1)) } catch (_: Exception) { fail(JsonConfigError.INVALID_JSON) }
                        if (scopes.lastOrNull()?.add(key) == false) fail(JsonConfigError.DUPLICATE_KEY)
                    }
                }
            }
            i++
        }
        val root = try { Json.parseToJsonElement(input) as? JsonObject } catch (_: Exception) { fail(JsonConfigError.INVALID_JSON) }
            ?: fail(JsonConfigError.INVALID_CONFIG)
        val outbounds = root["outbounds"] as? JsonArray ?: fail(JsonConfigError.INVALID_CONFIG)
        if (outbounds.isEmpty() || outbounds.any { it !is JsonObject || it.string("protocol").isNullOrBlank() }) fail(JsonConfigError.INVALID_CONFIG)
        for (name in listOf("inbounds", "outbounds")) {
            val entries = root[name] as? JsonArray ?: if (root[name] == null) continue else fail(JsonConfigError.INVALID_CONFIG)
            if (entries.any { it !is JsonObject }) fail(JsonConfigError.INVALID_CONFIG)
            val tags = entries.mapNotNull { (it as JsonObject).string("tag") }
            if (tags.size != tags.toSet().size) fail(JsonConfigError.CONFLICT)
        }
        fun checkFiles(element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (key in setOf("certificateFile", "keyFile", "dhparam", "configFile") && value is JsonPrimitive && value.content.isNotBlank()) fail(JsonConfigError.EXTERNAL_FILE)
                    if (key == "log" && value is JsonObject && listOf("access", "error").any { value.string(it)?.let { v -> v.isNotBlank() && v != "none" } == true }) fail(JsonConfigError.EXTERNAL_FILE)
                    checkFiles(value)
                }
                is JsonArray -> element.forEach(::checkFiles)
                else -> Unit
            }
        }
        checkFiles(root)
        if (root["routing"] != null && root["routing"] !is JsonObject) fail(JsonConfigError.INVALID_CONFIG)
        if (root["dns"] != null && root["dns"] !is JsonObject) fail(JsonConfigError.INVALID_CONFIG)
        if (root["stats"] != null && root["stats"] !is JsonObject) fail(JsonConfigError.INVALID_CONFIG)
        return root
    }

    fun inboundTags(text: String): List<String> =
        (inspect(text)["inbounds"] as? JsonArray).orEmpty().mapNotNull { (it as JsonObject).string("tag") }

    fun prepare(text: String, transport: JsonVpnTransport, inboundTag: String? = null): String {
        if (!transport.nativeTun && transport.socksPort !in 1..65535) fail(JsonConfigError.CONFLICT)
        val source = inspect(text)
        val originalInbounds = (source["inbounds"] as? JsonArray).orEmpty().map { it as JsonObject }
        val routing = (source["routing"] as? JsonObject) ?: JsonObject(emptyMap())
        val rules = (routing["rules"] as? JsonArray) ?: if (routing["rules"] == null) JsonArray(emptyList()) else fail(JsonConfigError.INVALID_CONFIG)
        if (rules.any { it !is JsonObject }) fail(JsonConfigError.INVALID_CONFIG)
        val selected = inboundTag?.takeIf { it.isNotBlank() }
        if (selected != null && originalInbounds.none { it.string("tag") == selected }) fail(JsonConfigError.INBOUND_REQUIRED)
        if (selected == null && rules.any { (it as? JsonObject)?.containsKey("inboundTag") == true }) fail(JsonConfigError.INBOUND_REQUIRED)
        val allTags = originalInbounds.mapNotNull { it.string("tag") }.toMutableSet()
        (source["outbounds"] as JsonArray).forEach { (it as JsonObject).string("tag")?.let(allTags::add) }
        fun unique(base: String): String {
            var tag = base
            var suffix = 0
            while (tag in allTags) tag = "$base-${++suffix}"
            allTags.add(tag)
            return tag
        }
        val tag = selected ?: unique("xrayfa-vpn")
        val others = originalInbounds.filter { it.string("tag") != selected || selected == null }
        if (others.any { it.string("protocol") == "tun" }) fail(JsonConfigError.CONFLICT)
        if (!transport.nativeTun && others.any {
                (it["port"] as? JsonPrimitive)?.content?.split(',')?.any { range ->
                    val parts = range.trim().split('-')
                    val start = parts.firstOrNull()?.toIntOrNull()
                    val end = parts.lastOrNull()?.toIntOrNull()
                    start != null && end != null && transport.socksPort in start..end
                } == true &&
                    it.string("listen").let { host -> host == null || host in listOf("0.0.0.0", "127.0.0.1", "::", "::1", "localhost") }
            }) fail(JsonConfigError.CONFLICT)
        val original = originalInbounds.firstOrNull { it.string("tag") == selected }.orEmpty()
        // The app owns this transport. Keep routing identity and sniffing, but
        // never carry TLS/WebSocket socket settings into the local VPN inbound.
        val inbound = JsonObject((original - setOf("listen", "streamSettings", "allocate")) + buildJsonObject {
            put("tag", tag)
            if (transport.nativeTun) {
                put("protocol", "tun")
                put("port", 0)
                putJsonObject("settings") { put("name", "xray0"); put("MTU", transport.mtu); put("userLevel", 8) }
            } else {
                put("listen", "127.0.0.1")
                put("port", transport.socksPort)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("udp", true)
                    put("auth", if (transport.socksUser.isNotBlank() && transport.socksCredential.isNotBlank()) "password" else "noauth")
                    if (transport.socksUser.isNotBlank() && transport.socksCredential.isNotBlank()) putJsonArray("accounts") {
                        addJsonObject { put("user", transport.socksUser); put("pass", transport.socksCredential) }
                    }
                }
            }
        })
        val dnsTag = unique("xrayfa-dns")
        val dnsOutbound = buildJsonObject { put("protocol", "dns"); put("tag", dnsTag) }
        val dnsRule = buildJsonObject {
            put("type", "field")
            putJsonArray("inboundTag") { add(tag) }
            putJsonArray("ip") { add(VPN_DNS) }
            put("port", "53")
            put("outboundTag", dnsTag)
        }
        val runtime = source.toMutableMap()
        runtime["inbounds"] = JsonArray(listOf(inbound) + others)
        runtime["outbounds"] = JsonArray((source["outbounds"] as JsonArray) + dnsOutbound)
        runtime["routing"] = JsonObject(routing + ("rules" to JsonArray(listOf(dnsRule) + rules)))
        if (!runtime.containsKey("stats")) runtime["stats"] = JsonObject(emptyMap())
        // This is a bootstrap only when the user supplied no DNS configuration.
        if (!runtime.containsKey("dns")) runtime["dns"] = buildJsonObject { putJsonArray("servers") { add("1.1.1.1"); add("8.8.8.8") } }
        return JsonObject(runtime).toString()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
