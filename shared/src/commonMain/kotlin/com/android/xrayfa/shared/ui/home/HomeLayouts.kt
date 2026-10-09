package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.android.xrayfa.common.core.homeDelayTestEnabled
import com.android.xrayfa.shared.navigation.HomeState
import com.android.xrayfa.model.isJsonConfig
import androidx.compose.material3.Text
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource

internal data class HomeLayoutCallbacks(
    val onConnectToggle: () -> Unit,
    val onTestDelay: () -> Unit,
)

@Composable
internal fun HomeBrandHeroLayout(
    state: HomeState,
    labels: HomeUiLabels,
    showNodeCard: Boolean,
    callbacks: HomeLayoutCallbacks,
    scrollEnabled: Boolean,
    largeStatusLabel: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (scrollEnabled) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = bottomContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        HomeConnectButton(
            isConnected = state.isConnected,
            enabled = !state.busy,
            onToggle = callbacks.onConnectToggle,
        )
        Spacer(Modifier.height(8.dp))
        HomeConnectionStatusLabel(
            isConnected = state.isConnected,
            connectedLabel = labels.connectedLabel,
            disconnectedLabel = labels.disconnectedLabel,
            connectedHint = labels.connectedHint,
            disconnectedHint = labels.disconnectedHint,
            large = largeStatusLabel,
        )
        Spacer(Modifier.height(28.dp))
        HomeSessionBundleCard(
            state = state,
            labels = labels,
            showNodeCard = showNodeCard,
            onTestDelay = callbacks.onTestDelay,
        )
        HomeLayoutErrors(state = state, labels = labels)
    }
}

@Composable
private fun HomeSessionBundleCard(
    state: HomeState,
    labels: HomeUiLabels,
    showNodeCard: Boolean,
    onTestDelay: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.selectedNode?.isJsonConfig == true) Text(stringResource(Res.string.json_config_stats_unavailable), Modifier.padding(16.dp))
            else HomeTrafficStatusCard(
                isConnected = state.isConnected,
                uploadSpeedKbps = state.uploadSpeedKbps,
                downloadSpeedKbps = state.downloadSpeedKbps,
                uploadLabel = labels.uploadLabel,
                downloadLabel = labels.downloadLabel,
                framed = false,
            )
            if (showNodeCard) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                HomeNodeBlock(
                    state = state,
                    labels = labels,
                    onTestDelay = onTestDelay,
                    framed = false,
                )
            }
        }
    }
}

@Composable
private fun HomeNodeBlock(
    state: HomeState,
    labels: HomeUiLabels,
    onTestDelay: () -> Unit,
    framed: Boolean,
) {
    val node = state.selectedNode
    if (node != null) {
        HomeSelectedNodeCard(
            node = node,
            unknownProtocolLabel = labels.unknownProtocolLabel,
            countryEmoji = node.countryISO,
            delayMs = state.delayMs,
            testing = state.testing,
            enableTest = !node.isJsonConfig && homeDelayTestEnabled(state.isConnected, state.testing),
            onTest = onTestDelay,
            framed = framed,
        )
    } else {
        HomeEmptyNodeCard(message = labels.emptyNodeMessage, framed = framed)
    }
}

@Composable
private fun ColumnScope.HomeLayoutErrors(
    state: HomeState,
    labels: HomeUiLabels,
) {
    if (state.showConfigError) {
        Spacer(Modifier.height(12.dp))
        HomeSectionHeader(
            text = labels.configNotReadyMessage,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.connectionErrorMessage?.let { message ->
        Spacer(Modifier.height(8.dp))
        HomeSectionHeader(
            text = if (message == "JSON_CONFIG_ERROR") stringResource(Res.string.json_config_invalid) else message,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
