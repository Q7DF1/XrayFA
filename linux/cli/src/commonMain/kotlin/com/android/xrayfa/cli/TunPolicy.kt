package com.android.xrayfa.cli

import kotlinx.serialization.json.*

internal data class TunPolicy(
    val interfaceName: String,
    val gateway: String,
    val prefixLength: Int,
    val dns: String,
    val mtu: Int,
    val routes: List<String>,
    val subnet: String,
) {
    val gatewayCidr
        get() = "$gateway/$prefixLength"

    init {
        require(Regex("[a-zA-Z0-9_.-]{1,15}").matches(interfaceName))
        require(prefixLength in 1..32 && mtu in 576..65535)
        require(ipv4(gateway) && ipv4(dns))
        require(
            routes.isNotEmpty() &&
                (routes + subnet).all { cidr ->
                    val parts = cidr.split('/')
                    parts.size == 2 && ipv4(parts[0]) && parts[1].toIntOrNull() in 0..32
                }
        )
    }

    fun addressed(link: JsonObject): Boolean =
        link["addr_info"]?.jsonArray?.any {
            val address = it.jsonObject
            address["local"]?.jsonPrimitive?.content == gateway &&
                address["prefixlen"]?.jsonPrimitive?.int == prefixLength
        } == true

    fun ready(link: JsonObject, installedRoutes: List<JsonObject>): Boolean =
        link["ifname"]?.jsonPrimitive?.content == interfaceName &&
            link["mtu"]?.jsonPrimitive?.int == mtu &&
            addressed(link) &&
            routes.all { destination ->
                installedRoutes.any {
                    it["dst"]?.jsonPrimitive?.content == destination &&
                        it["dev"]?.jsonPrimitive?.content == interfaceName
                }
            }

    fun encode(): JsonObject = buildJsonObject {
        put("interfaceName", interfaceName)
        put("gateway", gateway)
        put("prefixLength", prefixLength)
        put("dns", dns)
        put("mtu", mtu)
        put("routes", JsonArray(routes.map(::JsonPrimitive)))
        put("subnet", subnet)
    }

    companion object {
        // Never edit V1: schema-1 journals without an explicit policy use these historical values.
        val V1 =
            TunPolicy(
                "xrayfa0",
                "198.18.0.1",
                30,
                "198.18.0.2",
                1500,
                listOf("0.0.0.0/1", "128.0.0.0/1"),
                "198.18.0.0/30",
            )
        val CURRENT = V1

        fun fromJournal(record: JsonObject): TunPolicy {
            val raw = record["tunPolicy"]?.jsonObject ?: return V1
            return TunPolicy(
                    raw.getValue("interfaceName").jsonPrimitive.content,
                    raw.getValue("gateway").jsonPrimitive.content,
                    raw.getValue("prefixLength").jsonPrimitive.int,
                    raw.getValue("dns").jsonPrimitive.content,
                    raw.getValue("mtu").jsonPrimitive.int,
                    raw.getValue("routes").jsonArray.map { it.jsonPrimitive.content },
                    raw.getValue("subnet").jsonPrimitive.content,
                )
                .also {
                    // Runtime only emits V1 today. Unknown policies must not authorize cleanup.
                    require(it == V1) { "Unsupported journal TUN policy" }
                }
        }

        private fun ipv4(value: String) =
            value.split('.').let { parts ->
                parts.size == 4 && parts.all { it.isNotEmpty() && it.toIntOrNull() in 0..255 }
            }
    }
}
