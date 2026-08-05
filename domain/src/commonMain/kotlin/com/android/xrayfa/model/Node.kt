package com.android.xrayfa.model

/**
 * Persisted VPN node — platform-neutral domain model.
 * Room [com.android.xrayfa.dto.NodeEntity] maps at the app data boundary.
 */
data class Node(
    val id: Int = 0,
    val protocolPrefix: String,
    val address: String,
    val port: Int,
    val selected: Boolean = false,
    val isPreNode: Boolean = false,
    val isNextNode: Boolean = false,
    val remark: String? = null,
    val subscriptionId: Int,
    val favorite: Boolean = false,
    val jsonData: String? = null,
    val url: String,
    val countryISO: String = "",
)
