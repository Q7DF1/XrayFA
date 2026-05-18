package com.android.xrayfa.model

import com.google.gson.annotations.SerializedName

data class RoutingObject(
    @SerializedName("domainStrategy") val domainStrategy: String? = null,
    @SerializedName("domainMatcher") val domainMatcher: String? = null,
    @SerializedName("rules") val rules: List<RuleObject>? = null,
    @SerializedName("balancers") val balancers: List<BalancerObject>? = null
)

data class RuleObject(
    @SerializedName("domain") val domain: List<String>? = null,
    @SerializedName("ip") val ip: List<String>? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("sourcePort") val sourcePort: String? = null,
    @SerializedName("localPort") val localPort: String? = null,
    @SerializedName("network") val network: String? = null,
    @SerializedName("source") val source: List<String>? = null,
    @SerializedName("sourceIP") val sourceIP: List<String>? = null,
    @SerializedName("user") val user: List<String>? = null,
    @SerializedName("vlessRoute") val vlessRoute: String? = null,
    @SerializedName("inboundTag") val inboundTag: List<String>? = null,
    @SerializedName("protocol") val protocol: List<String>? = null,
    @SerializedName("attrs") val attrs: Map<String, String>? = null,
    @SerializedName("outboundTag") val outboundTag: String? = null,
    @SerializedName("balancerTag") val balancerTag: String? = null,
    @SerializedName("ruleTag") val ruleTag: String? = null,
    @SerializedName("domainMatcher") val domainMatcher: String? = null,
    @SerializedName("type") val type: String? = "field"
)

data class BalancerObject(
    @SerializedName("tag") val tag: String = "balancer",
    @SerializedName("selector") val selector: List<String>,
    @SerializedName("fallbackTag") val fallbackTag: String? = null,
    @SerializedName("strategy") val strategy: StrategyObject,
)

data class StrategyObject(
    @SerializedName("type") val type: String,
    @SerializedName("settings") val settings: StrategySettingsObject? = null
)

data class StrategySettingsObject(
    @SerializedName("expected") val expected: Int? = null,
    @SerializedName("maxRTT") val maxRTT: String? = null,
    @SerializedName("tolerance") val tolerance: Float? = null,
    @SerializedName("baselines") val baselines: List<String>? = null,
    @SerializedName("costs") val costs: List<CostObject>? = null
)

data class CostObject(
    @SerializedName("regexp") val regexp: Boolean,
    @SerializedName("match") val match: String,
    @SerializedName("value") val value: Float
)


