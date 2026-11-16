package com.android.xrayfa.config

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.model.isJsonConfig

enum class NodePreProxyError(val code: String) {
    INVALID("PRE_NODE_INVALID"),
    UNAVAILABLE("PRE_NODE_UNAVAILABLE"),
}

/** Fixed error codes avoid including node URLs or credentials in diagnostics. */
class NodePreProxyException(val reason: NodePreProxyError) : IllegalArgumentException(reason.code)

/** Rules shared by the editor, picker and connection preparation. */
object NodePreProxyPolicy {
    const val DISABLED = -1

    fun isEligible(hop: Node, ownerId: Int): Boolean =
        hop.id > 0 && hop.id != ownerId && !hop.isJsonConfig && !hasIndividualPreNode(hop)

    fun hasIndividualPreNode(node: Node): Boolean = (node.preNodeId ?: DISABLED) > 0

    fun requireHop(ownerId: Int, preNodeId: Int, hop: Node?): Node {
        if (preNodeId <= 0 || preNodeId == ownerId) throw NodePreProxyException(NodePreProxyError.INVALID)
        if (hop == null || hop.id != preNodeId || !isEligible(hop, ownerId)) {
            throw NodePreProxyException(NodePreProxyError.UNAVAILABLE)
        }
        return hop
    }

    fun validateAssignment(
        ownerId: Int,
        preNodeId: Int?,
        nodes: List<Node>,
        subscriptions: List<Subscription>,
    ) {
        if (preNodeId == null || preNodeId == DISABLED) return
        requireHop(ownerId, preNodeId, nodes.find { it.id == preNodeId })
        val usedByNode = nodes.any { it.preNodeId == ownerId }
        val usedBySubscription = subscriptions.any { it.preNodeId == ownerId || it.nextNodeId == ownerId }
        if (ownerId > 0 && (usedByNode || usedBySubscription)) {
            throw NodePreProxyException(NodePreProxyError.INVALID)
        }
    }
}
