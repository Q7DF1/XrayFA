package com.android.xrayfa.cli

import kotlinx.serialization.json.*

/** Only decoding/semantic checks are shared; file ownership and locking stay in the adapters. */
internal object NodeStoreCodec {
    private val protocols =
        setOf("vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "socks", "socks5", "http")

    fun decode(text: String): JsonObject = validate(Json.parseToJsonElement(text).jsonObject)

    fun validate(store: JsonObject): JsonObject {
        require(store.getValue("schemaVersion").jsonPrimitive.int == 1)
        val next = store.getValue("nextId").jsonPrimitive.int
        val ids =
            store.getValue("nodes").jsonArray.map {
                val node = it.jsonObject
                require(node.getValue("url").jsonPrimitive.content.isNotBlank())
                require(node.getValue("port").jsonPrimitive.int in 1..65535)
                require(node.getValue("address").jsonPrimitive.content.isNotBlank())
                require(node.getValue("name").jsonPrimitive.isString)
                require(node.getValue("protocol").jsonPrimitive.content in protocols)
                node.getValue("id").jsonPrimitive.int
            }
        require(next > 0 && ids.all { it > 0 && it < next } && ids.distinct().size == ids.size)
        return store
    }

    fun empty(): JsonObject = buildJsonObject {
        put("schemaVersion", 1)
        put("nextId", 1)
        putJsonArray("nodes") {}
    }

    fun find(store: JsonObject, id: Int): JsonObject? =
        store
            .getValue("nodes")
            .jsonArray
            .firstOrNull { it.jsonObject.getValue("id").jsonPrimitive.int == id }
            ?.jsonObject
}

internal data class ValidatedNode(val record: JsonObject, val outbound: JsonObject) {
    val link
        get() = record.getValue("url").jsonPrimitive.content

    companion object {
        fun from(record: JsonObject, outbound: JsonObject): ValidatedNode {
            val storedProtocol = record.getValue("protocol").jsonPrimitive.content
            val expected =
                when (storedProtocol) {
                    "ss" -> "shadowsocks"
                    "hy2",
                    "hysteria2" -> "hysteria"
                    "socks5" -> "socks"
                    else -> storedProtocol
                }
            require(outbound.getValue("protocol").jsonPrimitive.content == expected) {
                "Stored protocol does not match parsed outbound"
            }
            val endpoint = outboundEndpoint(outbound)
            require(
                endpoint["address"] == record["address"] && endpoint["port"] == record["port"]
            ) {
                "Stored endpoint does not match parsed outbound"
            }
            return ValidatedNode(record, outbound)
        }
    }
}

internal fun outboundEndpoint(outbound: JsonObject): JsonObject {
    val settings = outbound.getValue("settings").jsonObject
    return when (outbound.getValue("protocol").jsonPrimitive.content) {
        "vless",
        "vmess" -> settings.getValue("vnext").jsonArray.single().jsonObject
        "hysteria" -> settings
        else -> settings.getValue("servers").jsonArray.single().jsonObject
    }
}
