package com.android.xrayfa.cli

import kotlinx.serialization.json.*

internal fun runtimeConfig(
    outbound: JsonObject,
    level: LogLevel,
    policy: TunPolicy = TunPolicy.CURRENT,
): JsonObject {
    val proxy = outbound.toMutableMap().apply { put("tag", JsonPrimitive("proxy")) }
    val udpUnsupported = outbound["protocol"]?.jsonPrimitive?.content in setOf("http", "socks")
    return buildJsonObject {
        putJsonObject("log") {
            put("loglevel", level.wire)
            put("access", "none")
        }
        putJsonArray("inbounds") {
            add(
                buildJsonObject {
                    put("tag", "tun")
                    put("protocol", "tun")
                    put("port", 0)
                    putJsonObject("settings") {
                        put("name", policy.interfaceName)
                        put("mtu", policy.mtu)
                        putJsonArray("gateway") { add(policy.gatewayCidr) }
                        putJsonArray("autoSystemRoutingTable") { policy.routes.forEach(::add) }
                        put("autoOutboundsInterface", "auto")
                        put("autoSystemDnsToGateway", false)
                    }
                }
            )
        }
        putJsonArray("outbounds") {
            add(JsonObject(proxy))
            add(
                buildJsonObject {
                    put("tag", "dns-out")
                    put("protocol", "dns")
                }
            )
            add(
                buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                }
            )
        }
        putJsonObject("dns") {
            putJsonArray("servers") { add("1.1.1.1") }
            put("queryStrategy", "UseIPv4")
        }
        putJsonObject("routing") {
            put("domainStrategy", "AsIs")
            putJsonArray("rules") {
                add(
                    buildJsonObject {
                        put("type", "field")
                        putJsonArray("inboundTag") { add("tun") }
                        put("port", "53")
                        put("outboundTag", "dns-out")
                    }
                )
                add(
                    buildJsonObject {
                        put("type", "field")
                        putJsonArray("ip") { add("::/0") }
                        put("outboundTag", "block")
                    }
                )
                if (udpUnsupported)
                    add(
                        buildJsonObject {
                            put("type", "field")
                            put("network", "udp")
                            put("outboundTag", "block")
                        }
                    )
                add(
                    buildJsonObject {
                        put("type", "field")
                        putJsonArray("inboundTag") { add("tun") }
                        put("outboundTag", "proxy")
                    }
                )
            }
        }
    }
}
