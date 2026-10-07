package com.android.xrayfa.model

import com.android.xrayfa.common.utils.UrlCodec

const val JSON_CONFIG_PREFIX = "json-config"
val Node.isJsonConfig: Boolean get() = protocolPrefix == JSON_CONFIG_PREFIX
val Node.jsonInboundTag: String?
    get() = if (isJsonConfig) url.substringAfter("?inbound=", "").takeIf { it.isNotEmpty() }?.let(UrlCodec::decode) else null

fun jsonConfigIdentifier(id: String, inboundTag: String?): String =
    "$JSON_CONFIG_PREFIX://$id" + (inboundTag?.takeIf { it.isNotBlank() }?.let { "?inbound=${UrlCodec.encode(it)}" } ?: "")
