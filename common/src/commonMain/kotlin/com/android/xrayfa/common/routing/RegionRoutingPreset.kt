package com.android.xrayfa.common.routing

/** Stable preset identities; persisted rules retain the existing CN representation. */
enum class RegionRoutingPreset(val rules: List<Rule>) {
    CHINA(chinaDirectRules),
    RUSSIA(
        listOf(
            Rule(domain = listOf("geosite:category-ru"), outboundTag = "direct", ruleTag = "region:ru:domain"),
            Rule(ip = listOf("geoip:ru"), outboundTag = "direct", ruleTag = "region:ru:ip"),
        ),
    );

    fun isEnabled(rules: List<Rule>): Boolean = rules.containsAll(this.rules)

    fun setEnabled(rules: List<Rule>, enabled: Boolean): List<Rule> =
        if (enabled) rules + this.rules.filterNot { it in rules } else rules.filterNot { it in this.rules }
}

fun isRegionPresetRule(rule: Rule): Boolean = RegionRoutingPreset.entries.any { rule in it.rules }
