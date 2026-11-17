package com.android.xrayfa.cli

import kotlinx.serialization.json.*

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
            put("port", 10999)
            put("protocol", "socks")
            putJsonObject("settings") {
                put("auth", "noauth")
                put("udp", true)
            }
        })
    }
    putJsonArray("outbounds") { add(JsonObject(outbound + ("tag" to JsonPrimitive("proxy")))) }
}
