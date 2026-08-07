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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.ui.home.HomeConnectButton
import com.android.xrayfa.shared.ui.home.HomeConnectionStatusLabel
import com.android.xrayfa.shared.ui.home.HomeEmptyNodeCard
import com.android.xrayfa.shared.ui.home.HomeSectionHeader
import com.android.xrayfa.shared.ui.home.HomeSelectedNodeCard
import com.android.xrayfa.shared.ui.home.HomeTrafficStatusCard
import com.arkivanov.decompose.extensions.compose.subscribeAsState

/** Shared home section driven by [HomeComponent] (E.6g). Layout matches Android `CompactHomeContent`. */
@Composable
fun SharedHomeSection(
    component: HomeComponent,
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()

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
            isConnected = state.isConnected,
            enabled = !state.busy,
            onToggle = component::onConnectToggle,
        )

        HomeConnectionStatusLabel(
            isConnected = state.isConnected,
            connectedLabel = "Connected",
            disconnectedLabel = "Not connected",
            connectedHint = "Tap the button to disconnect",
            disconnectedHint = "Tap the button to connect",
        )

        HomeTrafficStatusCard(
            isConnected = state.isConnected,
            uploadSpeedKbps = state.uploadSpeedKbps,
            downloadSpeedKbps = state.downloadSpeedKbps,
            uploadLabel = "Upload",
            downloadLabel = "Download",
        )

        state.selectedNode?.let { node ->
            HomeSectionHeader(
                text = "Connection Details",
                modifier = Modifier.fillMaxWidth(),
            )
            HomeSelectedNodeCard(
                node = node,
                unknownProtocolLabel = "Unknown",
                enableTest = state.isConnected,
            )
        } ?: HomeEmptyNodeCard(message = "select configuration first")

        if (state.showConfigError) {
            HomeSectionHeader(
                text = "Configuration not ready",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
