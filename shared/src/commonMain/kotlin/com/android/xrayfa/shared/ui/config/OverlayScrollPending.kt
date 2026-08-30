package com.android.xrayfa.shared.ui.config

import com.android.xrayfa.model.Node

internal data class OverlayScrollPending(
    val nodeId: Int,
    val nodesAtTap: List<Node>,
    val queryWasBlankAtTap: Boolean,
)

internal fun shouldCommitOverlayScroll(
    pending: OverlayScrollPending?,
    searchQuery: String,
    currentNodes: List<Node>,
): Boolean {
    if (pending == null) return false
    if (searchQuery.isNotBlank()) return false
    if (pending.queryWasBlankAtTap) return true
    return currentNodes !== pending.nodesAtTap
}
