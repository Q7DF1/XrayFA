package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.ui.home.HomeConnectButton
import com.android.xrayfa.shared.ui.home.HomeConnectionStatusLabel
import com.android.xrayfa.shared.ui.home.HomeEmptyNodeCard
import com.android.xrayfa.shared.ui.home.HomeSectionHeader
import com.android.xrayfa.shared.ui.home.HomeSelectedNodeCard
import com.android.xrayfa.shared.ui.home.HomeTrafficStatusCard
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared home section wired through Koin (E.6b+). Layout matches Android `CompactHomeContent`. */
@Composable
fun SharedHomeSection(modifier: Modifier = Modifier) {
    val vpnController: VpnController = koinInject()
    val nodeRepository: NodeRepository = koinInject()
    val coordinator: VpnConnectCoordinator = koinInject()
    val scope = rememberCoroutineScope()

    val vpnState by vpnController.state.collectAsState()
    val selectedNode by nodeRepository.querySelectedNode().collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }
    var showConfigError by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        HomeConnectButton(
            isConnected = vpnState.isConnected,
            enabled = !busy,
            onToggle = {
                if (selectedNode == null) {
                    scope.launch {
                        showConfigError = true
                        delay(2000L)
                        showConfigError = false
                    }
                    return@HomeConnectButton
                }

                if (vpnState.isConnected) {
                    coordinator.disconnect()
                } else {
                    busy = true
                    scope.launch {
                        val prepared = coordinator.prepareConfigForConnect()
                        if (!prepared) {
                            busy = false
                            return@launch
                        }
                        coordinator.connect()
                        busy = false
                    }
                }
            },
        )

        HomeConnectionStatusLabel(
            isConnected = vpnState.isConnected,
            connectedLabel = "Connected",
            disconnectedLabel = "Not connected",
            connectedHint = "Tap the button to disconnect",
            disconnectedHint = "Tap the button to connect",
        )

        HomeTrafficStatusCard(
            isConnected = vpnState.isConnected,
            uploadSpeedKbps = 0.0,
            downloadSpeedKbps = 0.0,
            uploadLabel = "Upload",
            downloadLabel = "Download",
        )

        selectedNode?.let { node ->
            HomeSectionHeader(
                text = "Connection Details",
                modifier = Modifier.fillMaxWidth(),
            )
            HomeSelectedNodeCard(
                node = node,
                unknownProtocolLabel = "Unknown",
                enableTest = vpnState.isConnected,
            )
        } ?: HomeEmptyNodeCard(message = "select configuration first")

        if (showConfigError) {
            HomeSectionHeader(
                text = "Configuration not ready",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
