package com.android.xrayfa.agent

import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.VpnState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DUMMY_LOCAL_SOCKS_AUTH = "dummy-local-socks-auth"

class DefaultXrayAgentFacadeTest {

    @Test
    fun getVpnStatus_reportsConnectedAndLastError() = runBlocking {
        val facade = facade(
            vpn = FakeVpnController(connected = true, error = "timeout"),
        )

        val status = facade.getVpnStatus()

        assertTrue(status.connected)
        assertEquals("timeout", status.lastError)
    }

    @Test
    fun getVpnStatus_reportsDisconnected() = runBlocking {
        val status = facade(vpn = FakeVpnController(connected = false)).getVpnStatus()

        assertFalse(status.connected)
        assertNull(status.lastError)
    }

    @Test
    fun getSettingsSummary_mapsFieldsAndOmitsSocksPassword() = runBlocking {
        val settings = SettingsState(
            darkMode = 2,
            routingMode = RoutingMode.GLOBAL.code,
            socksPort = 1080,
            dnsIPv4 = "1.1.1.1",
            ipV6Enable = true,
            socksPassword = DUMMY_LOCAL_SOCKS_AUTH,
        )

        val summary = facade(settings = settings).getSettingsSummary()

        assertEquals(2, summary.darkMode)
        assertEquals("GLOBAL", summary.routingMode)
        assertEquals(1080, summary.socksPort)
        assertEquals("1.1.1.1", summary.dnsIpv4)
        assertTrue(summary.ipv6Enabled)
        assertFalse(summary.agentFunctionsEnabled)
        assertFalse(summary.toString().contains(DUMMY_LOCAL_SOCKS_AUTH))
    }

    @Test
    fun getSettingsSummary_readsAgentFunctionsEnabledFromSettings() = runBlocking {
        val summary = facade(
            settings = SettingsState(agentFunctionsEnabled = true),
        ).getSettingsSummary()

        assertTrue(summary.agentFunctionsEnabled)
    }

    @Test
    fun getTrafficSpeeds_readsSource() = runBlocking {
        val speeds = facade(
            traffic = FakeTrafficStatsSource(upload = 1.5, download = 8.25),
        ).getTrafficSpeeds()

        assertEquals(1.5, speeds.uploadKbps, 0.0)
        assertEquals(8.25, speeds.downloadKbps, 0.0)
    }

    @Test
    fun getTrafficSpeeds_emptyFlow_returnsZeros() = runBlocking {
        val speeds = facade(
            traffic = NeverEmitTraffic(),
            trafficWaitMs = 50,
        ).getTrafficSpeeds()

        assertEquals(0.0, speeds.uploadKbps, 0.0)
        assertEquals(0.0, speeds.downloadKbps, 0.0)
    }

    @Test
    fun getAppInfo_returnsInjectedBuildInfo() = runBlocking {
        val info = facade().getAppInfo()

        assertEquals("test-version", info.versionName)
        assertEquals(99, info.versionCode)
    }

    @Test
    fun nodeQueries_delegateToCatalog() = runBlocking {
        val node = sampleNode(id = 4, selected = true)
        val catalog = FakeCatalog(
            selected = node,
            nodes = listOf(node),
            subscriptions = listOf(AgentSubscriptionSummary(3, "work", true)),
        )
        val facade = facade(catalog = catalog)

        assertEquals(4, facade.getSelectedNode()?.id)
        assertEquals(listOf(4), facade.listNodes().map { it.id })
        assertEquals(4, facade.getNode(4)?.id)
        assertEquals("work", facade.listSubscriptions().single().mark)
    }

    @Test
    fun selectNode_success_restartsVpn() = runBlocking {
        val vpn = FakeVpnController()
        val catalog = FakeCatalog(selectResult = AgentActionResult.Success())
        val result = facade(catalog = catalog, vpn = vpn).selectNode(7)

        assertTrue(result is AgentActionResult.Success)
        assertEquals(listOf(7), catalog.selectCalls)
        assertEquals(1, vpn.restartCalls)
    }

    @Test
    fun selectNode_failure_doesNotRestartVpn() = runBlocking {
        val vpn = FakeVpnController()
        val catalog = FakeCatalog(
            selectResult = AgentActionResult.Failure(AgentErrorCode.NODE_NOT_FOUND, "missing"),
        )
        val result = facade(catalog = catalog, vpn = vpn).selectNode(99)

        assertTrue(result is AgentActionResult.Failure)
        assertEquals(AgentErrorCode.NODE_NOT_FOUND, (result as AgentActionResult.Failure).code)
        assertEquals(0, vpn.restartCalls)
    }

    @Test
    fun setFavorite_delegatesToCatalog() = runBlocking {
        val catalog = FakeCatalog()
        val result = facade(catalog = catalog).setFavorite(2, true)

        assertTrue(result is AgentActionResult.Success)
        assertEquals(listOf(2 to true), catalog.favoriteCalls)
    }

    @Test
    fun connectVpn_noSelectedNode_fails() = runBlocking {
        val vpn = FakeVpnController()
        val coordinator = FakeVpnConnectCoordinator(vpn)
        val result = facade(vpn = vpn, coordinator = coordinator).connectVpn()

        assertEquals(AgentErrorCode.NO_SELECTED_NODE, (result as AgentActionResult.Failure).code)
        assertEquals(0, coordinator.connectCalls)
    }

    @Test
    fun connectVpn_notPrepared_needsConsentAndDoesNotConnect() = runBlocking {
        var consentCalls = 0
        val vpn = FakeVpnController()
        val coordinator = FakeVpnConnectCoordinator(vpn)
        val result = facade(
            catalog = FakeCatalog(selected = sampleNode(id = 1, selected = true)),
            vpn = vpn,
            coordinator = coordinator,
            vpnPermissionGranted = { false },
            requestVpnConsent = { consentCalls += 1 },
        ).connectVpn()

        assertTrue(result is AgentActionResult.NeedsUserConsent)
        assertEquals(1, consentCalls)
        assertEquals(0, coordinator.connectCalls)
    }

    @Test
    fun connectVpn_alreadyConnected_doesNotReconnect() = runBlocking {
        val vpn = FakeVpnController(connected = true)
        val coordinator = FakeVpnConnectCoordinator(vpn)
        val result = facade(
            catalog = FakeCatalog(selected = sampleNode(id = 1, selected = true)),
            vpn = vpn,
            coordinator = coordinator,
        ).connectVpn()

        assertTrue(result is AgentActionResult.Success)
        assertEquals(0, coordinator.connectCalls)
    }

    @Test
    fun connectVpn_prepared_connects() = runBlocking {
        val vpn = FakeVpnController()
        val coordinator = FakeVpnConnectCoordinator(vpn)
        val result = facade(
            catalog = FakeCatalog(selected = sampleNode(id = 1, selected = true)),
            vpn = vpn,
            coordinator = coordinator,
        ).connectVpn()

        assertTrue(result is AgentActionResult.Success)
        assertEquals(1, coordinator.prepareCalls)
        assertEquals(1, coordinator.connectCalls)
    }

    @Test
    fun connectVpn_prepareConfigFails_noSelectedNode() = runBlocking {
        val vpn = FakeVpnController()
        val coordinator = FakeVpnConnectCoordinator(vpn, prepareResult = false)
        val result = facade(
            catalog = FakeCatalog(selected = sampleNode(id = 1, selected = true)),
            vpn = vpn,
            coordinator = coordinator,
        ).connectVpn()

        assertEquals(AgentErrorCode.NO_SELECTED_NODE, (result as AgentActionResult.Failure).code)
        assertEquals(0, coordinator.connectCalls)
    }

    @Test
    fun connectVpn_connectReturnsFalse_reportsFailure() = runBlocking {
        val vpn = FakeVpnController(error = "tun failed")
        val coordinator = FakeVpnConnectCoordinator(vpn, connectResult = false)
        val result = facade(
            catalog = FakeCatalog(selected = sampleNode(id = 1, selected = true)),
            vpn = vpn,
            coordinator = coordinator,
        ).connectVpn()

        val failure = result as AgentActionResult.Failure
        assertEquals(AgentErrorCode.VPN_CONNECT_FAILED, failure.code)
        assertEquals("tun failed", failure.message)
    }

    @Test
    fun disconnectVpn_callsCoordinator() = runBlocking {
        val vpn = FakeVpnController(connected = true)
        val coordinator = FakeVpnConnectCoordinator(vpn)
        val result = facade(vpn = vpn, coordinator = coordinator).disconnectVpn()

        assertTrue(result is AgentActionResult.Success)
        assertEquals(1, coordinator.disconnectCalls)
    }

    @Test
    fun refreshSubscription_delegatesToCatalog() = runBlocking {
        val catalog = FakeCatalog(refreshResult = AgentActionResult.Success())
        val result = facade(catalog = catalog).refreshSubscription(4)

        assertTrue(result is AgentActionResult.Success)
        assertEquals(listOf(4), catalog.refreshCalls)
    }

    @Test
    fun measureNodeDelay_missingNode() = runBlocking {
        val result = facade().measureNodeDelay(99)

        assertEquals(99, result.nodeId)
        assertNull(result.delayMs)
        assertEquals(AgentErrorCode.NODE_NOT_FOUND, result.error)
    }

    @Test
    fun measureNodeDelay_usesSettingsUrlAndProbe() = runBlocking {
        var probedUrl: String? = null
        val catalog = FakeCatalog(nodes = listOf(sampleNode(id = 2)))
        val result = facade(
            catalog = catalog,
            settings = SettingsState(delayTestUrl = "https://example.invalid/gen204"),
            measureDelay = { id, url ->
                probedUrl = url
                AgentDelayResult(nodeId = id, delayMs = 42L)
            },
        ).measureNodeDelay(2, url = null)

        assertEquals(42L, result.delayMs)
        assertEquals("https://example.invalid/gen204", probedUrl)
    }

    @Test
    fun measureNodeDelay_rateLimitedPerNode() = runBlocking {
        var now = 1_000L
        val catalog = FakeCatalog(nodes = listOf(sampleNode(id = 2)))
        val probe = { id: Int, _: String -> AgentDelayResult(nodeId = id, delayMs = 10L) }
        val instance = facade(
            catalog = catalog,
            measureDelay = probe,
            delayMinIntervalMs = 5_000,
            clockMs = { now },
        )

        assertEquals(10L, instance.measureNodeDelay(2).delayMs)
        val limited = instance.measureNodeDelay(2)
        assertEquals(AgentErrorCode.RATE_LIMITED, limited.error)
        now = 6_001L
        assertEquals(10L, instance.measureNodeDelay(2).delayMs)
    }

    @Test
    fun openScreen_dispatches() = runBlocking {
        val opened = mutableListOf<AgentScreen>()
        val result = facade(openScreen = {
            opened += it
            AgentActionResult.Success()
        }).openScreen(AgentScreen.Settings)

        assertTrue(result is AgentActionResult.Success)
        assertEquals(listOf(AgentScreen.Settings), opened)
    }

    private fun facade(
        catalog: XrayAgentNodeQueries = FakeCatalog(),
        vpn: FakeVpnController = FakeVpnController(),
        settings: SettingsState = SettingsState(),
        traffic: TrafficStatsSource = FakeTrafficStatsSource(0.0, 0.0),
        coordinator: FakeVpnConnectCoordinator = FakeVpnConnectCoordinator(vpn),
        vpnPermissionGranted: () -> Boolean = { true },
        requestVpnConsent: () -> Unit = {},
        measureDelay: suspend (Int, String) -> AgentDelayResult = { id, _ ->
            AgentDelayResult(nodeId = id, delayMs = null, error = AgentErrorCode.UNSUPPORTED)
        },
        openScreen: (AgentScreen) -> AgentActionResult = {
            AgentActionResult.Failure(AgentErrorCode.UNSUPPORTED, "openScreen")
        },
        trafficWaitMs: Long = 1_500,
        delayMinIntervalMs: Long = 5_000,
        clockMs: () -> Long = { 0L },
    ): DefaultXrayAgentFacade = DefaultXrayAgentFacade(
        catalog = catalog,
        vpnController = vpn,
        loadSettings = { settings },
        trafficStatsSource = traffic,
        appInfo = AgentAppInfo(versionName = "test-version", versionCode = 99),
        vpnConnectCoordinator = coordinator,
        vpnPermissionGranted = vpnPermissionGranted,
        requestVpnConsent = requestVpnConsent,
        measureDelay = measureDelay,
        openScreenAction = openScreen,
        trafficWaitMs = trafficWaitMs,
        delayMinIntervalMs = delayMinIntervalMs,
        clockMs = clockMs,
    )
}

private fun sampleNode(id: Int, selected: Boolean = false) = AgentNodeSummary(
    id = id,
    remark = "n$id",
    protocol = "vless",
    address = "example.com",
    port = 443,
    selected = selected,
    favorite = false,
    subscriptionId = AgentNodeFilter.MANUAL_SUBSCRIPTION_ID,
    countryIso = "US",
)

private class FakeCatalog(
    var selected: AgentNodeSummary? = null,
    var nodes: List<AgentNodeSummary> = emptyList(),
    var subscriptions: List<AgentSubscriptionSummary> = emptyList(),
    var selectResult: AgentActionResult = AgentActionResult.Success(),
    var favoriteResult: AgentActionResult = AgentActionResult.Success(),
    var refreshResult: AgentActionResult = AgentActionResult.Success(),
) : XrayAgentNodeQueries {
    val selectCalls = mutableListOf<Int>()
    val favoriteCalls = mutableListOf<Pair<Int, Boolean>>()
    val refreshCalls = mutableListOf<Int>()

    override suspend fun getSelectedNode(): AgentNodeSummary? = selected
    override suspend fun listNodes(filter: AgentNodeFilter): List<AgentNodeSummary> = nodes
    override suspend fun getNode(nodeId: Int): AgentNodeSummary? = nodes.find { it.id == nodeId }
    override suspend fun listSubscriptions(): List<AgentSubscriptionSummary> = subscriptions
    override suspend fun selectNode(nodeId: Int): AgentActionResult {
        selectCalls += nodeId
        return selectResult
    }
    override suspend fun setFavorite(nodeId: Int, favorite: Boolean): AgentActionResult {
        favoriteCalls += nodeId to favorite
        return favoriteResult
    }

    override suspend fun refreshSubscription(subscriptionId: Int): AgentActionResult {
        refreshCalls += subscriptionId
        return refreshResult
    }
}

private class FakeVpnController(
    connected: Boolean = false,
    error: String? = null,
) : VpnController {
    override val state = MutableStateFlow(
        if (connected) VpnState.Connected else VpnState.Disconnected,
    )
    override val connectError = MutableStateFlow(error)
    var restartCalls = 0
        private set

    override suspend fun connect(): Boolean = true
    override fun disconnect() = Unit
    override suspend fun restartIfNeeded() {
        restartCalls += 1
    }
}

private class FakeVpnConnectCoordinator(
    private val vpn: FakeVpnController,
    private val prepareResult: Boolean = true,
    private val connectResult: Boolean = true,
) : com.android.xrayfa.shared.vpn.VpnConnectCoordinator {
    var prepareCalls = 0
        private set
    var connectCalls = 0
        private set
    var disconnectCalls = 0
        private set

    override suspend fun prepareConfigForConnect(): Boolean {
        prepareCalls += 1
        return prepareResult
    }

    override suspend fun connect(): Boolean {
        connectCalls += 1
        return connectResult && vpn.connect()
    }

    override fun disconnect() {
        disconnectCalls += 1
        vpn.disconnect()
    }
}

private class FakeTrafficStatsSource(
    upload: Double,
    download: Double,
) : TrafficStatsSource {
    override val speedsKbps: Flow<Pair<Double, Double>> = flowOf(upload to download)
}

private class NeverEmitTraffic : TrafficStatsSource {
    override val speedsKbps: Flow<Pair<Double, Double>> = kotlinx.coroutines.flow.emptyFlow()
}
