package com.android.xrayfa.model

data class RoutingObject(
    val domainStrategy: String? = null,
    val domainMatcher: String? = null, // 新增: "hybrid" | "linear"
    val rules: List<RuleObject>? = null,
    val balancers: List<BalancerObject>? = null // 修正: 应为列表
)

data class RuleObject(
    val domain:      List<String>? = null,
    val ip:          List<String>? = null,
    val port:        String? = null,
    val sourcePort:  String? = null,
    val localPort:   String? = null,
    val network:     String? = null,
    val source:      List<String>? = null, // 新增: 替代 sourceIP
    val sourceIP:    List<String>? = null, // 标记过时: 建议使用 source
    val user:        List<String>? = null,
    val vlessRoute:  String? = null,
    val inboundTag:  List<String>? = null,
    val protocol:    List<String>? = null,
    val attrs:       Map<String, String>? = null, // 修正: 应为键值对 Map
    val outboundTag: String? = null,
    val balancerTag: String? = null,
    val ruleTag:     String? = null,
    val domainMatcher: String? = null, // 新增: 覆盖全局配置
    val type: String? = "field"
)

data class BalancerObject(
    val tag: String = "balancer",
    val selector: List<String>, // 修正: 应为字符串数组
    val fallbackTag: String? = null, // 新增: 故障转移标签
    val strategy: StrategyObject,
)

data class StrategyObject(
    val type: String, // "random" | "roundRobin" | "leastPing" | "leastLoad"
    val settings: StrategySettingsObject? = null
)

data class StrategySettingsObject(
    val expected: Int? = null,
    val maxRTT: String? = null,
    val tolerance: Float? = null,
    val baselines: List<String>? = null,
    val costs: List<CostObject>? = null
)

data class CostObject(
    val regexp: Boolean,
    val match: String,
    val value: Float
)


