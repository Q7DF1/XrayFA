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
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.arkivanov.decompose.extensions.compose.subscribeAsState

/** Shared home section driven by [HomeComponent] (E.6g). Layout matches Android `CompactHomeContent`. */
@Composable
fun SharedHomeSection(
    component: HomeComponent,
    modifier: Modifier = Modifier,
    showNodeCard: Boolean = true,
    onConnectToggle: (() -> Unit)? = null,
    labels: HomeUiLabels = HomeUiLabels(),
    scrollEnabled: Boolean = true,
    largeStatusLabel: Boolean = false,
) {
    val state by component.state.subscribeAsState()
    val scrollModifier =
        if (scrollEnabled) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        HomeConnectButton(
            isConnected = state.isConnected,
            enabled = !state.busy,
            onToggle = onConnectToggle ?: component::onConnectToggle,
        )

        HomeConnectionStatusLabel(
            isConnected = state.isConnected,
            connectedLabel = labels.connectedLabel,
            disconnectedLabel = labels.disconnectedLabel,
            connectedHint = labels.connectedHint,
            disconnectedHint = labels.disconnectedHint,
            large = largeStatusLabel,
        )

        HomeTrafficStatusCard(
            isConnected = state.isConnected,
            uploadSpeedKbps = state.uploadSpeedKbps,
            downloadSpeedKbps = state.downloadSpeedKbps,
            uploadLabel = labels.uploadLabel,
            downloadLabel = labels.downloadLabel,
        )

        state.selectedNode?.let { node ->
            if (showNodeCard) {
                HomeSectionHeader(
                    text = labels.connectionDetailsHeader,
                    modifier = Modifier.fillMaxWidth(),
                )
                HomeSelectedNodeCard(
                    node = node,
                    unknownProtocolLabel = labels.unknownProtocolLabel,
                    enableTest = state.isConnected,
                )
            }
        } ?: run {
            if (showNodeCard) {
                HomeEmptyNodeCard(message = labels.emptyNodeMessage)
            }
        }

        if (state.showConfigError) {
            HomeSectionHeader(
                text = labels.configNotReadyMessage,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
