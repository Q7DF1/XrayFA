package com.android.xrayfa.agent

import com.android.xrayfa.model.Node
import com.android.xrayfa.model.Subscription
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.repository.SubscriptionRepository
import kotlinx.coroutines.flow.first

/**
 * Node/subscription slice of [XrayAgentFacade], so Android tests can fake it
 * without Room and `:androidApp` can wrap it with VPN restart.
 */
interface XrayAgentNodeQueries {
    suspend fun getSelectedNode(): AgentNodeSummary?
    suspend fun listNodes(filter: AgentNodeFilter = AgentNodeFilter.All): List<AgentNodeSummary>
    suspend fun getNode(nodeId: Int): AgentNodeSummary?
    suspend fun listSubscriptions(): List<AgentSubscriptionSummary>
    suspend fun selectNode(nodeId: Int): AgentActionResult
    suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult
    suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult
}

/**
 * Domain-layer Agent queries over [NodeRepository] / [SubscriptionRepository].
 *
 * Strips share links, node JSON, and subscription URLs. Does **not** restart VPN
 * after [selectNode] — [XrayAgentFacade] on Android adds `restartIfNeeded()` (A2).
 */
class XrayAgentCatalog(
    private val nodeRepository: NodeRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : XrayAgentNodeQueries {
    override suspend fun getSelectedNode(): AgentNodeSummary? =
        nodeRepository.querySelectedNode().first()?.toAgentSummary()

    override suspend fun listNodes(filter: AgentNodeFilter): List<AgentNodeSummary> =
        nodeRepository.allNodes.first()
            .filter { it.matches(filter) }
            .map { it.toAgentSummary() }

    override suspend fun getNode(nodeId: Int): AgentNodeSummary? =
        nodeRepository.loadLinksById(nodeId).first()?.toAgentSummary()

    override suspend fun listSubscriptions(): List<AgentSubscriptionSummary> =
        subscriptionRepository.allSubscriptions.first().map { it.toAgentSummary() }

    override suspend fun selectNode(nodeId: Int): AgentActionResult {
        val node = nodeRepository.loadLinksById(nodeId).first()
            ?: return AgentActionResult.Failure(
                AgentErrorCode.NODE_NOT_FOUND,
                "Node $nodeId not found",
            )
        nodeRepository.clearSelection()
        nodeRepository.updateSelectById(node.id, selected = true)
        return AgentActionResult.Success()
    }

    override suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult {
        nodeRepository.loadLinksById(nodeId).first()
            ?: return AgentActionResult.Failure(
                AgentErrorCode.NODE_NOT_FOUND,
                "Node $nodeId not found",
            )
        nodeRepository.updateFavoriteById(nodeId, favorite)
        return AgentActionResult.Success()
    }

    override suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult {
        val subscription = subscriptionRepository.getSubscriptionById(subscriptionId).first()
            ?: return AgentActionResult.Failure(
                AgentErrorCode.SUBSCRIPTION_NOT_FOUND,
                "Subscription $subscriptionId not found",
            )
        return try {
            subscriptionRepository.fetchAndSaveNodes(subscription.url, subscription.id)
            AgentActionResult.Success()
        } catch (error: Exception) {
            AgentActionResult.Failure(
                AgentErrorCode.NETWORK_ERROR,
                error.message ?: "Failed to refresh subscription $subscriptionId",
            )
        }
    }
}

internal fun Node.toAgentSummary(): AgentNodeSummary = AgentNodeSummary(
    id = id,
    remark = remark,
    protocol = protocolName(protocolPrefix),
    address = address,
    port = port,
    selected = selected,
    favorite = favorite,
    subscriptionId = subscriptionId,
    countryIso = countryISO,
)

internal fun Subscription.toAgentSummary(): AgentSubscriptionSummary = AgentSubscriptionSummary(
    id = id,
    mark = mark,
    autoUpdate = isAutoUpdate,
)

private fun Node.matches(filter: AgentNodeFilter): Boolean = when (filter.kind) {
    AgentNodeFilterKind.All -> true
    AgentNodeFilterKind.Favorites -> favorite
    AgentNodeFilterKind.Manual -> subscriptionId == AgentNodeFilter.MANUAL_SUBSCRIPTION_ID
    AgentNodeFilterKind.SubscriptionId -> subscriptionId == filter.subscriptionId
}

internal fun protocolName(prefix: String): String =
    prefix.lowercase().removeSuffix("://").removeSuffix(":")
