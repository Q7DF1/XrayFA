package com.android.xrayfa.config

import com.android.xrayfa.dto.ParseLinkInput
import com.android.xrayfa.dto.VLESSConfig
import com.android.xrayfa.model.*
import com.android.xrayfa.parser.encodeShareLink
import kotlinx.serialization.json.*
import kotlin.random.Random

/** Basic VLESS information only. Never serialize storage, credentials or unknown JSON fields. */
object XDriveShareLink {
    private fun basic(form: XDriveForm) = XDriveForm(
        id = form.id, encryption = form.encryption, flow = form.flow,
        address = form.address, port = form.port,
        security = form.security, serverName = form.serverName, alpn = form.alpn, fingerprint = form.fingerprint,
        service = "local", remoteFolder = "/basic-share-validation",
    ).also { require(it.issue() == null) { "Invalid XDRIVE VLESS information" } }

    fun encode(form: XDriveForm, remark: String?): String {
        val safe = basic(form)
        val params = linkedMapOf("type" to "xdrive", "encryption" to safe.encryption, "security" to safe.security)
        if (safe.security == "tls") {
            for ((key, value) in mapOf("sni" to safe.serverName, "alpn" to safe.alpn, "fp" to safe.fingerprint)) {
                if (value.isNotBlank()) params[key] = value
            }
        }
        // Keep the user's VLESS authority even though storage carries XDRIVE data.
        return encodeShareLink("vless", safe.id, safe.address, safe.port, params, remark)
    }

    fun fromJson(text: String, remark: String?): String? = runCatching {
        encode(requireNotNull(XDriveForm.fromJson(text)), remark)
    }.getOrNull()

    fun importDraft(link: ParseLinkInput, config: VLESSConfig): Node {
        require(link.subscriptionId == -1) { "Import XDRIVE basic links individually, then complete their storage settings" }
        val params = config.param
        val safe = basic(XDriveForm(id = config.uuid, address = config.server, port = config.port,
            encryption = params["encryption"].orEmpty(),
            flow = params["flow"].orEmpty(), security = params["security"] ?: "none",
            serverName = params["sni"].orEmpty(), alpn = params["alpn"].orEmpty(), fingerprint = params["fp"].orEmpty()))
        val source = safe.build()
        val outbound = source["outbounds"]!!.jsonArray.single().jsonObject
        val stream = outbound["streamSettings"]!!.jsonObject
        val draftStorage = buildJsonObject {
            put("service", "google-drive")
            put("remoteFolder", "")
            put("secrets", JsonArray(emptyList()))
        }
        val draftOutbound = JsonObject(outbound + ("streamSettings" to JsonObject(stream + ("xdriveSettings" to draftStorage))))
        val draft = JsonObject(source + ("outbounds" to JsonArray(listOf(draftOutbound))))
        return Node(id = link.id, protocolPrefix = JSON_CONFIG_PREFIX, address = "", port = 0,
            subscriptionId = link.subscriptionId, selected = false, remark = config.remark ?: "VLESS · XDRIVE",
            url = jsonConfigIdentifier("xdrive-draft-${Random.nextLong().toULong().toString(16)}", null), jsonData = draft.toString())
    }
}
