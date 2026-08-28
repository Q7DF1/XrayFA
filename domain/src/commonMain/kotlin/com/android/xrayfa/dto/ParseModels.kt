package com.android.xrayfa.dto

import com.android.xrayfa.model.Node

/**
 * Platform-neutral link input for subscription / node pre-parsing.
 */
data class ParseLinkInput(
    val id: Int = 0,
    val protocolPrefix: String,
    val content: String,
    val subscriptionId: Int,
    val selected: Boolean = false,
)

/** Parser output before persistence; same shape as [Node]. */
typealias ParsedNode = Node
