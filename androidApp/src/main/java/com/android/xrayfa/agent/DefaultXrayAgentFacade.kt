package com.android.xrayfa.agent

import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Android [XrayAgentFacade]. Node/subscription calls go through [XrayAgentNodeQueries];
 * VPN restart after [selectNode] is Android-specific.
 */
class DefaultXrayAgentFacade(
    private val catalog: XrayAgentNodeQueries,
    private val vpnController: VpnController,
    private val loadSettings: suspend () -> SettingsState,
    private val trafficStatsSource: TrafficStatsSource,
    private val appInfo: AgentAppInfo,
    private val vpnConnectCoordinator: VpnConnectCoordinator,
    private val vpnPermissionGranted: () -> Boolean,
    private val requestVpnConsent: () -> Unit,
    private val measureDelay: suspend (nodeId: Int, testUrl: String) -> AgentDelayResult,
    private val openScreenAction: (AgentScreen) -> AgentActionResult,
    private val trafficWaitMs: Long = 1_500,
    private val delayMinIntervalMs: Long = 5_000,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : XrayAgentFacade {

    private val lastDelayByNodeMs = ConcurrentHashMap<Int, Long>()

    override suspend fun getVpnStatus(): AgentVpnStatus = AgentVpnStatus(
        connected = vpnController.state.value.isConnected,
        lastError = vpnController.connectError.value,
    )

    override suspend fun getSelectedNode(): AgentNodeSummary? = catalog.getSelectedNode()

    override suspend fun listNodes(filter: AgentNodeFilter): List<AgentNodeSummary> =
        catalog.listNodes(filter)

    override suspend fun getNode(nodeId: Int): AgentNodeSummary? = catalog.getNode(nodeId)

    override suspend fun listSubscriptions(): List<AgentSubscriptionSummary> =
        catalog.listSubscriptions()

    override suspend fun getSettingsSummary(): AgentSettingsSummary {
        val settings = loadSettings()
        return AgentSettingsSummary(
            darkMode = settings.darkMode,
            routingMode = RoutingMode.fromCode(settings.routingMode).name,
            socksPort = settings.socksPort,
            dnsIpv4 = settings.dnsIPv4,
            ipv6Enabled = settings.ipV6Enable,
            agentFunctionsEnabled = settings.agentFunctionsEnabled,
        )
    }

    override suspend fun getTrafficSpeeds(): AgentTrafficSpeeds {
        val pair = withTimeoutOrNull(trafficWaitMs) {
            trafficStatsSource.speedsKbps.firstOrNull()
        }
        val (upload, download) = pair ?: (0.0 to 0.0)
        return AgentTrafficSpeeds(uploadKbps = upload, downloadKbps = download)
    }

    override suspend fun getAppInfo(): AgentAppInfo = appInfo

    override suspend fun selectNode(nodeId: Int): AgentActionResult {
        val result = catalog.selectNode(nodeId)
        if (result is AgentActionResult.Success) {
            vpnController.restartIfNeeded()
        }
        return result
    }

    override suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult =
        catalog.setFavorite(nodeId, favorite)

    override suspend fun connectVpn(): AgentActionResult {
        if (catalog.getSelectedNode() == null) {
            return AgentActionResult.Failure(
                AgentErrorCode.NO_SELECTED_NODE,
                "No node is selected",
            )
        }
        if (vpnController.state.value.isConnected) {
            return AgentActionResult.Success("already connected")
        }
        if (!vpnPermissionGranted()) {
            requestVpnConsent()
            return AgentActionResult.NeedsUserConsent("VPN permission")
        }
        if (!vpnConnectCoordinator.prepareConfigForConnect()) {
            return AgentActionResult.Failure(
                AgentErrorCode.NO_SELECTED_NODE,
                "No connectable node configuration",
            )
        }
        val connected = vpnConnectCoordinator.connect()
        if (!connected) {
            return AgentActionResult.Failure(
                AgentErrorCode.VPN_CONNECT_FAILED,
                vpnController.connectError.value ?: "VPN connect failed",
            )
        }
        return AgentActionResult.Success()
    }

    override suspend fun disconnectVpn(): AgentActionResult {
        vpnConnectCoordinator.disconnect()
        return AgentActionResult.Success()
    }

    override suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult =
        catalog.refreshSubscription(subscriptionId)

    override suspend fun measureNodeDelay(nodeId: Int, url: String?): AgentDelayResult {
        catalog.getNode(nodeId)
            ?: return AgentDelayResult(
                nodeId = nodeId,
                delayMs = null,
                error = AgentErrorCode.NODE_NOT_FOUND,
            )
        val now = clockMs()
        val last = lastDelayByNodeMs[nodeId]
        if (last != null && now - last < delayMinIntervalMs) {
            return AgentDelayResult(
                nodeId = nodeId,
                delayMs = null,
                error = AgentErrorCode.RATE_LIMITED,
            )
        }
        val testUrl = url?.takeIf { it.isNotBlank() } ?: loadSettings().delayTestUrl
        val result = measureDelay(nodeId, testUrl)
        if (result.error != AgentErrorCode.RATE_LIMITED) {
            lastDelayByNodeMs[nodeId] = now
        }
        return result
    }

    override suspend fun openScreen(target: AgentScreen): AgentActionResult = openScreenAction(target)
}
