package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.ui.home.HomeConnectionStatusLabel
import com.android.xrayfa.shared.ui.home.HomeConnectionPanel
import com.android.xrayfa.shared.ui.home.HomeEmptyNodeCard
import com.android.xrayfa.shared.ui.home.HomeSectionHeader
import com.android.xrayfa.shared.ui.home.HomeSelectedNodeCard
import com.android.xrayfa.shared.ui.home.HomeTrafficStatusCard
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared home section wired through Koin (E.6b+). */
@Composable
fun SharedHomeSection(modifier: Modifier = Modifier) {
    val vpnController: VpnController = koinInject()
    val nodeRepository: NodeRepository = koinInject()
    val coordinator: VpnConnectCoordinator = koinInject()
    val settingsRepository: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()

    val vpnState by vpnController.state.collectAsState()
    val settings by settingsRepository.settingsFlow.collectAsState(initial = SettingsState())
    val selectedNode by nodeRepository.querySelectedNode().collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val selectedNodeLabel =
        selectedNode?.remark?.takeIf { it.isNotBlank() }
            ?: selectedNode?.address
            ?: "None"

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeConnectionStatusLabel(
            isConnected = vpnState.isConnected,
            connectedLabel = "Connected",
            disconnectedLabel = "Not connected",
            connectedHint = "Tap disconnect below",
            disconnectedHint = "Tap connect below",
        )

        HomeTrafficStatusCard(
            isConnected = vpnState.isConnected,
            uploadSpeedKbps = 0.0,
            downloadSpeedKbps = 0.0,
            uploadLabel = "Upload",
            downloadLabel = "Download",
        )

        HomeSectionHeader(text = "Connection detail", modifier = Modifier.fillMaxWidth())
        if (selectedNode != null) {
            HomeSelectedNodeCard(
                node = selectedNode!!,
                unknownProtocolLabel = "Unknown",
            )
        } else {
            HomeEmptyNodeCard(message = "Select a configuration in Config")
        }

        HomeConnectionPanel(
            modifier = Modifier.fillMaxWidth(),
            vpnState = vpnState,
            socksPort = settings.socksPort,
            selectedNodeLabel = selectedNodeLabel,
            hasSelectedNode = selectedNode != null,
            busy = busy,
            statusMessage = statusMessage,
            onConnect = {
                busy = true
                statusMessage = "Connecting…"
                scope.launch {
                    val prepared = coordinator.prepareConfigForConnect()
                    if (!prepared) {
                        busy = false
                        statusMessage = "No selected node / config prepare failed"
                        return@launch
                    }
                    val ok = coordinator.connect()
                    busy = false
                    statusMessage =
                        if (ok) {
                            "Tunnel start requested"
                        } else {
                            "Connect failed (config / entitlement?)"
                        }
                }
            },
            onDisconnect = {
                coordinator.disconnect()
                statusMessage = "Disconnected"
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}
