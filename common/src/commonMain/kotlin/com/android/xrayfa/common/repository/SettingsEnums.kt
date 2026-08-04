package com.android.xrayfa.common.repository

enum class Theme(val code: Int) {
    LIGHT_MODE(0),
    DARK_MODE(1),
    AUTO_MODE(2);

    companion object {
        fun fromCode(code: Int): Theme = entries.firstOrNull { it.code == code } ?: AUTO_MODE
    }
}

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
