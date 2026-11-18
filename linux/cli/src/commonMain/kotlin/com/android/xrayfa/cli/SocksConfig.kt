package com.android.xrayfa.cli

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val SOCKS_PORT = 10808

/** Loopback-only explicit proxy; never changes host routing or DNS. */
internal fun socksConfig(outbound: JsonObject, level: LogLevel): JsonObject = buildJsonObject {
    putJsonObject("log") {
        put("loglevel", level.wire)
        put("access", "none")
    }
    putJsonArray("inbounds") {
        add(buildJsonObject {
            put("tag", "socks")
            put("listen", "127.0.0.1")
            put("port", SOCKS_PORT)
            put("protocol", "socks")
            putJsonObject("settings") {
                put("auth", "noauth")
                put("udp", true)
            }
        })
    }
    putJsonArray("outbounds") { add(JsonObject(outbound + ("tag" to JsonPrimitive("proxy")))) }
}
