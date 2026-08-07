package com.android.xrayfa.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.shared.ui.home.HomeConnectionPanel
import com.android.xrayfa.shared.vpn.VpnConnectCoordinator
import com.android.xrayfa.vpn.VpnController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shared home section wired through Koin (E.6b). */
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

    Spacer(modifier = Modifier.height(16.dp))
    HomeConnectionPanel(
        modifier = modifier.fillMaxWidth(),
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
}
