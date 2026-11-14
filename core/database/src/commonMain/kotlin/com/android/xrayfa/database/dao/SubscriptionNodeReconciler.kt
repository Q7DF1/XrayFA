package com.android.xrayfa.database.dao

import com.android.xrayfa.database.entity.NodeEntity

/** Matches refreshed nodes before the DAO atomically replaces the subscription rows. */
internal fun reconcileSubscriptionNodes(previous: List<NodeEntity>, incoming: List<NodeEntity>): List<NodeEntity> {
    val byUrl = previous.groupBy(NodeEntity::url)
    val byIdentity = previous.groupBy { it.identity() }
    val byEndpoint = previous.groupBy { it.endpoint() }
    val incomingByEndpoint = incoming.groupBy { it.endpoint() }
    val matchesByIndex = incoming.map { node ->
        val endpoint = node.endpoint()
        val uniqueEndpoint = byEndpoint[endpoint]?.takeIf {
            it.size == 1 && incomingByEndpoint[endpoint]?.size == 1
        }
        byUrl[node.url] ?: byIdentity[node.identity()] ?: uniqueEndpoint.orEmpty()
    }
    // Reuse an ID only when the match is unique in both directions.
    val candidateCounts = matchesByIndex.mapNotNull { it.singleOrNull()?.id }.groupingBy { it }.eachCount()
    return incoming.mapIndexed { index, node ->
        val matches = matchesByIndex[index]
        val savedMux = matches.map(NodeEntity::muxSettings).distinct().singleOrNull()
        val match = matches.singleOrNull()?.takeIf { candidateCounts[it.id] == 1 }
        node.copy(
            id = match?.id ?: 0,
            muxSettings = savedMux ?: node.muxSettings,
            preNodeId = match?.preNodeId ?: node.preNodeId,
            selected = match?.selected ?: node.selected,
            favorite = match?.favorite ?: node.favorite,
        )
    }
}

private data class NodeEndpoint(val protocol: String, val address: String, val port: Int)
private data class NodeIdentity(val endpoint: NodeEndpoint, val remark: String?)

private fun NodeEntity.endpoint() = NodeEndpoint(protocolPrefix, address, port)
private fun NodeEntity.identity() = NodeIdentity(endpoint(), remark)
