package com.android.xrayfa.model

import com.android.xrayfa.common.utils.UrlCodec

const val JSON_CONFIG_PREFIX = "json-config"
val Node.isJsonConfig: Boolean get() = protocolPrefix == JSON_CONFIG_PREFIX
/** An app-created VLESS node backed by local JSON; global JSON imports stay generic. */
val Node.isManagedXDrive: Boolean get() = isJsonConfig && url.substringAfter("://").substringBefore('?').startsWith("xdrive-")
/** JSON records may contain cloud-storage credentials and have no public sharing contract. */
val Node.canShareConfiguration: Boolean get() = !isJsonConfig && jsonData == null
val Node.jsonInboundTag: String?
    get() = if (isJsonConfig) url.substringAfter("?inbound=", "").takeIf { it.isNotEmpty() }?.let(UrlCodec::decode) else null

fun jsonConfigIdentifier(id: String, inboundTag: String?): String =
    "$JSON_CONFIG_PREFIX://$id" + (inboundTag?.takeIf { it.isNotBlank() }?.let { "?inbound=${UrlCodec.encode(it)}" } ?: "")
