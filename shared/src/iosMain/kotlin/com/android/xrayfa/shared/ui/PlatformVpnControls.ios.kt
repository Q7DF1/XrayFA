package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.IosSharedInit
import com.android.xrayfa.vpn.VpnState

@Composable
actual fun PlatformVpnControls() {
    var status by remember { mutableStateOf("Disconnected") }
    var busy by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(12.dp))
    Text(text = status)
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        enabled = !busy,
        onClick = {
            busy = true
            status = "Connecting…"
            val trialConfig =
                """
                {"log":{"loglevel":"warning"},"inbounds":[{"port":10808,"protocol":"socks","listen":"127.0.0.1","settings":{"udp":true}}],"outbounds":[{"protocol":"freedom","tag":"direct"}]}
                """.trimIndent()
            IosSharedInit.setPendingVpnConfig(trialConfig)
            IosSharedInit.connectVpn { ok ->
                busy = false
                status =
                    if (ok) {
                        "Tunnel start requested"
                    } else {
                        "Connect failed (config / entitlement?)"
                    }
            }
        },
    ) {
        Text("Connect (trial)")
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        enabled = !busy,
        onClick = {
            IosSharedInit.disconnectVpn()
            status =
                if (IosSharedInit.vpnState().value == VpnState.Connected) {
                    "Disconnecting…"
                } else {
                    "Disconnected"
                }
        },
    ) {
        Text("Disconnect")
    }
}
