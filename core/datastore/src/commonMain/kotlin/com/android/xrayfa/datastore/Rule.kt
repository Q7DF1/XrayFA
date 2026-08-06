package com.android.xrayfa.datastore

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Routing rule stored in DataStore; structurally identical to [com.android.xrayfa.model.RuleObject].
 *
 * JSON field names match legacy Gson output for upgrade compatibility.
 */
@Serializable
data class Rule(
    @SerialName("domain") val domain: List<String>? = null,
    @SerialName("ip") val ip: List<String>? = null,
    @SerialName("port") val port: String? = null,
    @SerialName("sourcePort") val sourcePort: String? = null,
    @SerialName("localPort") val localPort: String? = null,
    @SerialName("network") val network: String? = null,
    @SerialName("source") val source: List<String>? = null,
    @SerialName("sourceIP") val sourceIP: List<String>? = null,
    @SerialName("user") val user: List<String>? = null,
    @SerialName("vlessRoute") val vlessRoute: String? = null,
    @SerialName("inboundTag") val inboundTag: List<String>? = null,
    @SerialName("protocol") val protocol: List<String>? = null,
    @SerialName("attrs") val attrs: Map<String, String>? = null,
    @SerialName("outboundTag") val outboundTag: String? = null,
    @SerialName("balancerTag") val balancerTag: String? = null,
    @SerialName("ruleTag") val ruleTag: String? = null,
    @SerialName("domainMatcher") val domainMatcher: String? = null,
    @SerialName("type") val type: String = "field",
)

val defaultRouteList = listOf(
    Rule(
        type = "field",
        inboundTag = listOf("api"),
        outboundTag = "api",
        ruleTag = "API Traffic",
    ),
    Rule(
        type = "field",
        inboundTag = listOf("tun"),
        outboundTag = "dns-out",
        port = "53",
        ruleTag = "DNS Traffic",
    ),
    Rule(
        type = "field",
        outboundTag = "proxy",
        domain = listOf("geosite:telegram", "geosite:google"),
        ruleTag = "Proxy Telegram & Google",
    ),
    Rule(
        type = "field",
        outboundTag = "direct",
        domain = listOf("geosite:cn", "geosite:geolocation-cn"),
        ip = listOf("geoip:cn"),
        ruleTag = "Bypass Mainland China",
    ),
    Rule(
        type = "field",
        outboundTag = "block",
        domain = listOf("geosite:category-ads-all"),
        ruleTag = "Ad Block",
    ),
)

/** Default routing rules JSON persisted for new installs and fallback recovery. */
val defaultRoutes: String by lazy { encodeRules(defaultRouteList) }
