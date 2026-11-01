package com.android.xrayfa.shared.navigation

import com.android.xrayfa.model.Node

/** Stable ordering: positive delays first, with unmeasured and failed nodes last. */
internal fun sortNodesByDelay(nodes: List<Node>, delays: Map<Int, Long>): List<Node> =
    nodes.sortedBy { node -> delays[node.id]?.takeIf { it > 0L } ?: Long.MAX_VALUE }
