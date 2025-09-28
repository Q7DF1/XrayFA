package com.android.xrayfa.model

data class RoutingObject(
    val domainStrategy: String? = null,
    val rules: List<RuleObject>?= null,
    val balancer: BalancerObject? =null
)

data class RuleObject(
    val domain:      List<String>? = null,
    val ip:          List<String>? = null,
    val port:        String? = null, //"53,443,1000-2000"
    val sourcePort:  String? = null, //"53,443,1000-2000"
    val localPort:   String? = null, //"53,443,1000-2000"
    val network:     String? = null,
    val sourceIP:    List<String>? = null,
    val user:        List<String>? = null,
    val vlessRoute:  String? = null,
    val inboundTag:  List<String>? = null,
    val protocol:    List<String>? = null,
    val attrs:       List<String>? = null,
    val outboundTag: String? = null,
    val balancerTag: String? = null,
    val ruleTag:     String? = null,
    val type: String? = null
)

data class BalancerObject(
    val tag: String = "balancer",
    val selector: String,
    val fallbackTag: String,
    val strategy: StrategyObject,
)

data class StrategyObject(
    val type: String,
    val settings: SettingsObject?
)

data class SettingsObject(
    val expected: Int,
    val maxRTT:String,
    val tolerance: Float,
    val baselines: List<String>,
    val costs: List<CostObject>

)

data class CostObject(
    val regexp: Boolean,
    val match: String,
    val value: Float
)


