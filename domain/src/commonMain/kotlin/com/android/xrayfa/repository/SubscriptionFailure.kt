package com.android.xrayfa.repository

/** Identifies the failed step without copying subscription content into error messages. */
class SubscriptionFailure(val stage: Stage, cause: Exception) : Exception(stage.name, cause) {
    enum class Stage { FETCH, PARSE, SAVE }
}

/** Context contains only an ordinal and an allowlisted protocol, never a node URL. */
class SubscriptionParseFailure(
    val step: String,
    val nodeIndex: Int?,
    protocol: String?,
    cause: Exception,
) : Exception("Subscription parse failed", cause) {
    val protocol: String = protocol?.lowercase()?.takeIf {
        it in setOf("vless", "vmess", "trojan", "ss", "socks", "socks5", "http", "https", "hysteria2", "hy2")
    } ?: "unknown"
}
