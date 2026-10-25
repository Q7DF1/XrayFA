package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.core.homeDelayTestEnabled
import com.android.xrayfa.shared.navigation.HomeState
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.shared.resources.*
import org.jetbrains.compose.resources.stringResource

internal data class HomeLayoutCallbacks(
    val onConnectToggle: () -> Unit,
    val onTestDelay: () -> Unit,
    val onRestoreGeodata: () -> Unit,
    val onRetryStartup: () -> Unit,
)

@Composable
internal fun HomeBrandHeroLayout(
    state: HomeState,
    labels: HomeUiLabels,
    mapLocations: HomeMapLocations,
    showNodeCard: Boolean,
    callbacks: HomeLayoutCallbacks,
    scrollEnabled: Boolean,
    largeStatusLabel: Boolean,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val mapHeight = (maxHeight * 0.27f).coerceIn(112.dp, 180.dp)
        val buttonDiameter = (maxHeight * 0.28f).coerceIn(154.dp, 192.dp)
        val bottomReachGap =
            (maxHeight - mapHeight - 62.dp - 150.dp - buttonDiameter - 44.dp)
                .coerceAtLeast(8.dp)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (scrollEnabled || state.startupFailure != null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .padding(bottom = bottomContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(2.dp))
            HomeNetworkMap(
                locations = mapLocations,
                fromLabel = labels.mapFromLabel,
                toLabel = labels.mapToLabel,
                modifier = Modifier.fillMaxWidth().height(mapHeight),
            )
            Spacer(Modifier.height(4.dp))
            HomeConnectionStatusLabel(
                isConnected = state.isConnected,
                connectedLabel = labels.connectedLabel,
                disconnectedLabel = labels.disconnectedLabel,
                connectedHint = labels.connectedHint,
                disconnectedHint = labels.disconnectedHint,
                large = largeStatusLabel,
            )
            state.startupFailure?.let { failure ->
                Spacer(Modifier.height(12.dp))
                StartupFailureCard(
                    failure,
                    state.busy || state.startupPending,
                    state.canRestoreGeodata,
                    callbacks.onRetryStartup,
                    callbacks.onRestoreGeodata,
                )
            }
            Spacer(Modifier.height(12.dp))
            HomeSessionBundleCard(
                state = state,
                labels = labels,
                showNodeCard = showNodeCard,
                onTestDelay = callbacks.onTestDelay,
            )
            Spacer(Modifier.height(bottomReachGap))
            HomeConnectButton(
                isConnected = state.isConnected,
                enabled = !state.busy && !state.startupPending,
                onToggle = callbacks.onConnectToggle,
                diameter = buttonDiameter,
                innerDiameter = buttonDiameter * 0.76f,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeSessionBundleCard(
    state: HomeState,
    labels: HomeUiLabels,
    showNodeCard: Boolean,
    onTestDelay: () -> Unit,
) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(24.dp)
    val fillColor = if (darkTheme) Color(0xCC151C29) else Color(0xEAFBFDFF)
    val borderBrush =
        Brush.linearGradient(
            if (darkTheme) {
                listOf(Color(0x6657D9E6), Color(0x332F78C4), Color(0x554EC5B7))
            } else {
                listOf(Color(0x6680CBD5), Color(0x444E83CC), Color(0x6680CFC4))
            },
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(fillColor, shape)
                .border(1.dp, borderBrush, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (state.selectedNode?.isJsonConfig == true) {
            Text(stringResource(Res.string.json_config_stats_unavailable), Modifier.padding(16.dp))
        } else {
            HomeTrafficStatusCard(
                isConnected = state.isConnected,
                uploadSpeedKbps = state.uploadSpeedKbps,
                downloadSpeedKbps = state.downloadSpeedKbps,
                uploadLabel = labels.uploadLabel,
                downloadLabel = labels.downloadLabel,
                framed = false,
            )
        }
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
