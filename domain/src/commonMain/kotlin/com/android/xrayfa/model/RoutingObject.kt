package com.android.xrayfa.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoutingObject(
    @SerialName("domainStrategy") val domainStrategy: String? = null,
    @SerialName("domainMatcher") val domainMatcher: String? = null,
    @SerialName("rules") val rules: List<RuleObject>? = null,
    @SerialName("balancers") val balancers: List<BalancerObject>? = null,
)

@Serializable
data class RuleObject(
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

@Serializable
data class BalancerObject(
    @SerialName("tag") val tag: String = "balancer",
    @SerialName("selector") val selector: List<String>,
    @SerialName("fallbackTag") val fallbackTag: String? = null,
    @SerialName("strategy") val strategy: StrategyObject,
)

@Serializable
data class StrategyObject(
    @SerialName("type") val type: String,
    @SerialName("settings") val settings: StrategySettingsObject? = null,
)

@Serializable
data class StrategySettingsObject(
    @SerialName("expected") val expected: Int? = null,
    @SerialName("maxRTT") val maxRTT: String? = null,
    @SerialName("tolerance") val tolerance: Float? = null,
    @SerialName("baselines") val baselines: List<String>? = null,
    @SerialName("costs") val costs: List<CostObject>? = null,
)

@Serializable
data class CostObject(
    @SerialName("regexp") val regexp: Boolean,
    @SerialName("match") val match: String,
    @SerialName("value") val value: Float,
)
