package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription

data class ConfigFilterOption(
    val id: Int,
    val label: String,
)

sealed interface NodeEditTarget {
    data object Create : NodeEditTarget

    data class Edit(
        val node: Node,
    ) : NodeEditTarget
}

/** Config tab presentation state owned by [ConfigComponent]. */
data class ConfigState(
    val nodes: List<Node> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val filters: List<ConfigFilterOption> = emptyList(),
    val selectedFilterId: Int = ConfigFilterIds.SUB_ALL,
    val nodeEditTarget: NodeEditTarget? = null,
    val deleteTarget: Node? = null,
    val editError: Boolean = false,
    val nodeDelayMap: Map<Int, Long> = emptyMap(),
    val testingAll: Boolean = false,
)
