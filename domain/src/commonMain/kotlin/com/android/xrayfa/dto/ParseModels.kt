package com.android.xrayfa.dto

/**
 * Platform-neutral link input for subscription / node pre-parsing.
 * Android [Link] (Room entity) converts at the app boundary.
 */
data class ParseLinkInput(
    val id: Int = 0,
    val protocolPrefix: String,
    val content: String,
    val subscriptionId: Int,
    val selected: Boolean = false,
)

/**
 * Parsed node metadata returned by config parsers before persistence.
 * Android [Node] (Room entity) converts at the app boundary.
 */
data class ParsedNode(
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
