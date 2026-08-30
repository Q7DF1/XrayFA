package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription

data class ConfigFilterOption(
    val id: Int,
    val label: String,
)

/** Config tab presentation state owned by [ConfigComponent]. */
data class ConfigState(
    val nodes: List<Node> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val filters: List<ConfigFilterOption> = emptyList(),
    val selectedFilterId: Int = ConfigFilterIds.SUB_ALL,
    val deleteTarget: Node? = null,
    val editError: Boolean = false,
    val nodeDelayMap: Map<Int, Long> = emptyMap(),
    val testingAll: Boolean = false,
    val searchQuery: String = "",
    val pendingDeleteAll: Boolean = false,
)
