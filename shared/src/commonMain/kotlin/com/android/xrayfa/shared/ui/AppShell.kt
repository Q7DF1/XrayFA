package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.XrayFAShared

/**
 * Minimal shared Compose shell (E.6). Full screens migrate from `:androidApp` incrementally.
 */
@Composable
fun AppShell() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "XrayFA",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "KMP ${XrayFAShared.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = platformLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlatformVpnControls()
        }
    }
}

/** Platform-specific VPN trial controls (iOS only for now). */
@Composable
expect fun PlatformVpnControls()
