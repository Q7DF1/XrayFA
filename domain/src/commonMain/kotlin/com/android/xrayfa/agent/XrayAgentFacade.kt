package com.android.xrayfa.agent

/**
 * Agent-callable app capabilities. Android implements this in `:androidApp`
 * (`DefaultXrayAgentFacade`, A2). iOS has no implementation in this phase.
 *
 * Node/subscription queries that only need domain repositories live in
 * [XrayAgentCatalog] so they can be unit-tested in `commonTest`.
 */
interface XrayAgentFacade {

    // ── Phase A: read ──
    suspend fun getVpnStatus(): AgentVpnStatus
    suspend fun getSelectedNode(): AgentNodeSummary?
    suspend fun listNodes(filter: AgentNodeFilter = AgentNodeFilter.All): List<AgentNodeSummary>
    suspend fun getNode(nodeId: Int): AgentNodeSummary?
    suspend fun listSubscriptions(): List<AgentSubscriptionSummary>
    suspend fun getSettingsSummary(): AgentSettingsSummary
    suspend fun getTrafficSpeeds(): AgentTrafficSpeeds
    suspend fun getAppInfo(): AgentAppInfo

    // ── Phase B: write ──
    suspend fun selectNode(nodeId: Int): AgentActionResult
    suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult
    suspend fun connectVpn(): AgentActionResult
    suspend fun disconnectVpn(): AgentActionResult
    suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult
    suspend fun measureNodeDelay(nodeId: Int, url: String? = null): AgentDelayResult
    suspend fun openScreen(target: AgentScreen): AgentActionResult
}
