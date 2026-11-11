package com.android.xrayfa.config

import com.android.xrayfa.common.utils.Base64Compat
import com.android.xrayfa.common.utils.UrlCodec
import kotlinx.serialization.json.*

/** Local JSON configuration only. This form deliberately has no share-link encoder. */
data class XDriveForm(
    val id: String = "",
    val encryption: String = "",
    val flow: String = "",
    val service: String = "google-drive",
    val remoteFolder: String = "",
    val secrets: List<String> = listOf("", "", ""),
    val endpoint: String = "",
    val region: String = "",
    val bucket: String = "",
    val pathStyle: Boolean = true,
    val security: String = "none",
    val serverName: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val pinnedPeerCertSha256: String = "",
    val verifyPeerCertByName: String = "",
    val echConfigList: String = "",
    val advanced: String = "{}",
    private val original: JsonObject? = null,
    private val outboundIndex: Int = 0,
) {
    /** Fixed field paths only: parser exceptions may contain storage credentials. */
    fun issue(): String? {
        if (id.isBlank() || (id.encodeToByteArray().size > 30 && !Regex("[a-fA-F0-9]{8}-?[a-fA-F0-9]{4}-?[a-fA-F0-9]{4}-?[a-fA-F0-9]{4}-?[a-fA-F0-9]{12}").matches(id))) return "id"
        if (!validEncryption(encryption)) return "encryption"
        if (flow.isNotEmpty()) return "flow"
        if (security !in listOf("none", "tls")) return "security"
        if (security == "tls" && pinnedPeerCertSha256.split(',').map(String::trim).filter(String::isNotEmpty)
                .any { !Regex("[a-fA-F0-9]{64}").matches(it.replace(":", "")) }) return "pinnedPeerCertSha256"
        if (service !in services) return "service"
        if (remoteFolder.isBlank()) return "remoteFolder"
        if (service == "google-drive") {
            if (remoteFolder.any { it !in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-" }) return "remoteFolder"
            if (secrets.size != 3 || secrets.any(String::isBlank)) return "secrets"
        }
        if (service == "s3") {
            val uri = runCatching { UrlCodec.parseUri(endpoint) }.getOrNull()
            if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank() ||
                uri.userInfo != null || '?' in endpoint || '#' in endpoint) return "s3.endpoint"
            if (region.any { it.code < 32 || it.code == 127 || it in "/, \t" }) return "s3.region"
            if (!validPath(bucket) || '/' in bucket) return "s3.bucket"
            if (!validPath(remoteFolder.removeSuffix("/"))) return "remoteFolder"
            if (secrets.size !in 2..3 || secrets.take(2).any(String::isBlank) ||
                secrets.any { s -> s.any { it.code < 32 || it.code == 127 } } ||
                secrets[0].any { it in "/ ,\t" }) return "secrets"
            if (!pathStyle && (uri.host!!.any { it == ':' } || uri.host!!.all { it.isDigit() || it == '.' } ||
                    bucket.any { it !in "abcdefghijklmnopqrstuvwxyz0123456789-." } ||
                    ".." in bucket || bucket.startsWith('.') || bucket.endsWith('.'))) return "s3.bucket"
        }
        val extra = runCatching { Json.parseToJsonElement(advanced) as? JsonObject }.getOrNull() ?: return "advanced"
        if (extra.keys.any { it in ownedKeys }) return "advanced"
        for (key in numericKeys) {
            val value = extra[key] ?: continue
            val primitive = value as? JsonPrimitive ?: return key
            if (primitive.isString || primitive.longOrNull !in 0L..4294967295L) return key
        }
        for (key in listOf("template", "xmux")) if (extra.containsKey(key) && extra[key] !is JsonObject) return key
        if (service == "template" && extra["template"] !is JsonObject) return "template"
        if (service == "template") {
            val template = extra["template"]!!.jsonObject
            for (operation in listOf("put", "get", "list", "delete")) {
                val op = template[operation] as? JsonObject ?: return "template.$operation"
                if (op.str("url").isBlank()) return "template.$operation.url"
            }
            val names = template.obj("list").str("namesRegex")
            if (names.isBlank() || runCatching { Regex(names) }.isFailure) return "template.list.namesRegex"
        }
        val xmux = extra["xmux"] as? JsonObject
        if (xmux != null) {
            val ranges = mutableMapOf<String, Int>()
            for (key in listOf("maxConnections", "maxConcurrency", "cMaxReuseTimes", "hMaxRequestTimes", "hMaxReusableSecs")) {
                val value = xmux[key] ?: continue
                val primitive = value as? JsonPrimitive ?: return "xmux.$key"
                val ends = if (primitive.isString) {
                    val text = primitive.content
                    if (text.isEmpty()) listOf(0)
                    else if (text.toIntOrNull() != null) listOf(text.toInt())
                    else {
                        val match = Regex("([+-]?[0-9]+)-([+-]?[0-9]+)").matchEntire(text) ?: return "xmux.$key"
                        listOf(match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
                    }
                } else listOf(primitive.intOrNull)
                if (ends.any { it == null }) return "xmux.$key"
                ranges[key] = ends.filterNotNull().max()
            }
            xmux["hKeepAlivePeriod"]?.let {
                val primitive = it as? JsonPrimitive
                if (primitive == null || primitive.isString || primitive.longOrNull == null) return "xmux.hKeepAlivePeriod"
            }
            if ((ranges["maxConnections"] ?: 0) > 0 && (ranges["maxConcurrency"] ?: 0) > 0) return "xmux"
        }
        val mux = originalOutbound()?.get("mux") as? JsonObject
        if (original != null && (mux == null || mux["enabled"] != JsonPrimitive(true) ||
                mux["concurrency"]?.let { (it as? JsonPrimitive)?.intOrNull?.let { n -> n < 0 } ?: true } == true ||
                mux["xudpConcurrency"]?.let { (it as? JsonPrimitive)?.intOrNull?.let { n -> n < 0 } ?: true } == true ||
                mux["xudpProxyUDP443"]?.let { it !in listOf(JsonPrimitive("reject"), JsonPrimitive("allow"), JsonPrimitive("")) } == true)) return "mux"
        if (originalUser()?.get("testpre")?.let { (it as? JsonPrimitive)?.intOrNull != 0 } == true) return "testpre"
        return null
    }

    fun build(): JsonObject {
        require(issue() == null) { "Invalid XDRIVE configuration" }
        val outbound = originalOutbound().orEmpty()
        val settings = outbound.obj("settings")
        val server = settings["vnext"]?.jsonArray?.firstOrNull()?.jsonObject.orEmpty()
        val user = originalUser().orEmpty()
        val stream = outbound.obj("streamSettings")
        val oldDrive = stream.obj("xdriveSettings")
        val extra = Json.parseToJsonElement(advanced).jsonObject
        val drive = buildJsonObject {
            extra.forEach { (k, v) -> put(k, v) }
            put("service", service)
            put("remoteFolder", remoteFolder)
            if (service != "local") put("secrets", JsonArray(secrets.map(::JsonPrimitive)))
            if (service == "s3") put("s3", JsonObject(oldDrive.obj("s3") + buildJsonObject {
                put("endpoint", endpoint); put("region", region); put("bucket", bucket); put("pathStyle", pathStyle)
            }))
        }
        val newStream = stream.toMutableMap().apply {
            put("network", JsonPrimitive("xdrive")); put("security", JsonPrimitive(security)); put("xdriveSettings", drive)
            if (security == "tls") {
                val oldTls = stream.obj("tlsSettings")
                val oldAlpn = (oldTls["alpn"] as? JsonArray)?.joinToString(",") { it.jsonPrimitive.content }.orEmpty()
                put("tlsSettings", JsonObject(oldTls.toMutableMap().apply {
                    for ((key, value) in mapOf("fingerprint" to fingerprint, "pinnedPeerCertSha256" to pinnedPeerCertSha256,
                        "verifyPeerCertByName" to verifyPeerCertByName, "echConfigList" to echConfigList)) {
                        if (original == null || value != oldTls.str(key)) {
                            if (value.isBlank()) remove(key) else put(key, JsonPrimitive(value))
                        }
                    }
                    if (original == null || serverName != oldTls.str("serverName")) {
                        if (serverName.isBlank()) remove("serverName") else put("serverName", JsonPrimitive(serverName))
                    }
                    if (original == null || alpn != oldAlpn) {
                        if (alpn.isBlank()) remove("alpn") else put("alpn", JsonArray(alpn.split(',').map { JsonPrimitive(it.trim()) }))
                    }
                }))
            } else remove("tlsSettings")
        }
        val newUser = JsonObject(user + buildJsonObject { put("id", id); put("encryption", encryption); put("flow", flow) })
        val newServer = JsonObject(server + buildJsonObject {
            if (!server.containsKey("address")) put("address", "127.0.0.1")
            if (!server.containsKey("port")) put("port", 443)
            put("users", JsonArray(listOf(newUser)))
        })
        val newOutbound = JsonObject(outbound + buildJsonObject {
            put("protocol", "vless")
            if (!outbound.containsKey("tag")) put("tag", "proxy")
            put("settings", JsonObject(settings + ("vnext" to JsonArray(listOf(newServer)))))
            put("streamSettings", JsonObject(newStream))
            put("mux", outbound["mux"] ?: buildJsonObject {
                put("enabled", true); put("concurrency", 8); put("xudpConcurrency", 8); put("xudpProxyUDP443", "reject")
            })
        })
        val outbounds: MutableList<JsonElement> = (original?.get("outbounds") as? JsonArray)?.toMutableList() ?: mutableListOf(newOutbound)
        outbounds[outboundIndex] = newOutbound
        return JsonObject(original.orEmpty() + ("outbounds" to JsonArray(outbounds)))
    }

    private fun originalOutbound(): JsonObject? = (original?.get("outbounds") as? JsonArray)?.get(outboundIndex) as? JsonObject
    private fun originalUser(): JsonObject? = originalOutbound()?.obj("settings")?.get("vnext")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("users")?.jsonArray?.firstOrNull() as? JsonObject

    companion object {
        val services = listOf("google-drive", "s3", "template", "local")
        private val ownedKeys = setOf("service", "remoteFolder", "secrets", "s3")
        private val numericKeys = listOf("segmentBytes", "flushIntervalMs", "pollIntervalMs", "maxPollIntervalMs",
            "sessionTtlSeconds", "concurrency", "eagerWindowMs", "holeTimeoutMs")

        /** Unsupported shapes stay in the full JSON editor rather than losing fields. */
        fun fromJson(text: String): XDriveForm? = runCatching {
            val root = JsonVpnConfig.inspect(text)
            val outbounds = root["outbounds"]!!.jsonArray
            val candidates = outbounds.withIndex().filter { (_, v) -> v.jsonObject.obj("streamSettings").str("network") == "xdrive" }
            if (candidates.size != 1) return null
            val (index, value) = candidates.single()
            val outbound = value.jsonObject
            if (outbound.str("protocol") != "vless") return null
            val servers = outbound.obj("settings")["vnext"] as? JsonArray ?: return null
            if (servers.size != 1) return null
            val users = servers.single().jsonObject["users"] as? JsonArray ?: return null
            if (users.size != 1) return null
            val user = users.single().jsonObject
            val stream = outbound.obj("streamSettings")
            val drive = stream["xdriveSettings"] as? JsonObject ?: return null
            // Leave malformed or dormant owned subtrees to the raw editor. Otherwise opening
            // structured controls would normalize or remove data the user has not edited.
            if (!user.stringFields("id", "encryption", "flow") || !stream.stringFields("security") ||
                !drive.stringFields("service", "remoteFolder")) return null
            val service = drive.str("service").let { if (it == "Google Drive") "google-drive" else it }
            val security = stream.str("security").ifBlank { "none" }
            if (drive.containsKey("s3") && (service != "s3" || drive["s3"] !is JsonObject)) return null
            if (service == "local" && drive.containsKey("secrets")) return null
            if (stream.containsKey("tlsSettings") && (security != "tls" || stream["tlsSettings"] !is JsonObject)) return null
            val s3 = drive.obj("s3")
            val tls = stream.obj("tlsSettings")
            if (!s3.stringFields("endpoint", "region", "bucket") ||
                s3["pathStyle"]?.let { it != JsonPrimitive(true) && it != JsonPrimitive(false) } == true ||
                !tls.stringFields("serverName", "fingerprint", "pinnedPeerCertSha256", "verifyPeerCertByName", "echConfigList")) return null
            if (tls.containsKey("alpn") && ((tls["alpn"] as? JsonArray)?.any { it !is JsonPrimitive || !it.isString } != false)) return null
            val secretArray = drive["secrets"] as? JsonArray
            if (drive.containsKey("secrets") && secretArray == null) return null
            if (secretArray?.any { it !is JsonPrimitive || !it.isString } == true) return null
            XDriveForm(id = user.str("id"), encryption = user.str("encryption"), flow = user.str("flow"),
                service = service,
                remoteFolder = drive.str("remoteFolder"), secrets = secretArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                endpoint = s3.str("endpoint"), region = s3.str("region"), bucket = s3.str("bucket"),
                pathStyle = (s3["pathStyle"] as? JsonPrimitive)?.booleanOrNull ?: true,
                security = security, serverName = tls.str("serverName"),
                alpn = (tls["alpn"] as? JsonArray)?.joinToString(",") { it.jsonPrimitive.content }.orEmpty(),
                fingerprint = tls.str("fingerprint"), pinnedPeerCertSha256 = tls.str("pinnedPeerCertSha256"),
                verifyPeerCertByName = tls.str("verifyPeerCertByName"), echConfigList = tls.str("echConfigList"),
                advanced = JsonObject(drive - ownedKeys).toString(), original = root, outboundIndex = index)
        }.getOrNull()

        private fun validPath(value: String): Boolean = value.isNotEmpty() &&
            value.none { it.code < 32 || it.code == 127 || it == '\\' } &&
            value.split('/').none { it in listOf("", ".", "..") }

        private fun validEncryption(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size < 4 || parts[0] != "mlkem768x25519plus" ||
                parts[1] !in listOf("native", "xorpub", "random") || parts[2] !in listOf("0rtt", "1rtt")) return false
            return parts.drop(3).all { part -> part.length < 20 || runCatching {
                part.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-" } &&
                    Base64Compat.decode(part.replace('-', '+').replace('_', '/')).size in listOf(32, 1184)
            }.getOrDefault(false) }
        }
    }
}

private fun Map<String, JsonElement>.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
private fun Map<String, JsonElement>.str(key: String): String = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
private fun Map<String, JsonElement>.stringFields(vararg keys: String): Boolean = keys.all { key ->
    !containsKey(key) || (get(key) as? JsonPrimitive)?.isString == true
}
