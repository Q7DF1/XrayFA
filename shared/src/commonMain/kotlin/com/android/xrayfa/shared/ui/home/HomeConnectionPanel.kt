package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.vpn.VpnState
import com.android.xrayfa.vpn.isConnected

/**
 * First shared home UI slice (E.6b): VPN connect/disconnect + status.
 * Android full [HomeScreen] stays in `:androidApp` until Phase 4 migration.
 */
@Composable
fun HomeConnectionPanel(
    vpnState: VpnState,
    socksPort: Int,
    selectedNodeLabel: String,
    hasSelectedNode: Boolean,
    busy: Boolean,
    statusMessage: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (vpnState.isConnected) "VPN: Connected" else "VPN: Disconnected",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "SOCKS port: $socksPort",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Node: $selectedNodeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onConnect, enabled = !busy && !vpnState.isConnected && hasSelectedNode) {
                Text("Connect")
            }
            Button(onClick = onDisconnect, enabled = !busy && vpnState.isConnected) {
                Text("Disconnect")
            }
        }
    }
}
