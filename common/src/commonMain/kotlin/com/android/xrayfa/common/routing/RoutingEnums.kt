package com.android.xrayfa.common.routing

enum class RoutingMode(val code: Int) {
    GLOBAL(0),
    ROUTE(1);

    companion object {
        fun fromCode(code: Int): RoutingMode = entries.firstOrNull { it.code == code } ?: ROUTE
    }
}

enum class DomainStrategy(val code: Int) {
    ASIS(0),
    IP_IF_NON_MATCH(1),
    IP_ON_DEMAND(2);

    companion object {
        fun fromCode(code: Int): DomainStrategy =
            entries.firstOrNull { it.code == code } ?: IP_IF_NON_MATCH
    }
}
