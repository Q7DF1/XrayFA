package com.android.xrayfa.shared.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.core.homeDelayTestEnabled
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.shared.navigation.HomeState
import com.android.xrayfa.shared.resources.*
import kotlin.math.roundToInt
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
    BoxWithConstraints(
        modifier.fillMaxSize().padding(bottom = bottomContentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compactAction = maxHeight < 480.dp
        val buttonDiameter = if (compactAction) 128.dp else 160.dp
        val actionBottomGap = if (compactAction) 8.dp else 16.dp

        Column(Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = 20.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val viewportHeight = maxHeight
                val scroll = rememberScrollState()
                MeasuredHomeInformation(
                    viewportHeight = viewportHeight,
                    modifier = Modifier.fillMaxWidth().verticalScroll(
                        scroll, enabled = scrollEnabled || scroll.maxValue > 0,
                    ),
                    map = {
                        HomeNetworkMap(mapLocations, labels.mapFromLabel, labels.mapToLabel, Modifier.fillMaxWidth())
                    },
                    status = {
                        HomeConnectionStatusLabel(
                            state.isConnected, labels.connectedLabel, labels.disconnectedLabel,
                            labels.connectedHint, labels.disconnectedHint, large = largeStatusLabel,
                            showHint = viewportHeight >= 420.dp,
                        )
                    },
                    failure = state.startupFailure?.let { failure ->
                        {
                            StartupFailureCard(
                                failure, state.busy || state.startupPending, state.canRestoreGeodata,
                                callbacks.onRetryStartup, callbacks.onRestoreGeodata,
                            )
                        }
                    },
                    dashboard = {
                        HomeSessionBundleCard(state, labels, showNodeCard, callbacks.onTestDelay)
                    },
                )
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = actionBottomGap),
                contentAlignment = Alignment.Center,
            ) {
                HomeConnectButton(
                    isConnected = state.isConnected,
                    enabled = !state.busy && !state.startupPending,
                    onToggle = callbacks.onConnectToggle,
                    diameter = buttonDiameter,
                    innerDiameter = buttonDiameter * 0.76f,
                )
            }
        }
    }
}

/** Measure important content once, then give the map and bounded gaps the remaining budget. */
@Composable
private fun MeasuredHomeInformation(
    viewportHeight: Dp,
    modifier: Modifier,
    map: @Composable () -> Unit,
    status: @Composable () -> Unit,
    failure: (@Composable () -> Unit)?,
    dashboard: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.layoutId(HomeInformationPart.Map)) { map() }
            Box(Modifier.layoutId(HomeInformationPart.Status)) { status() }
            if (failure != null) Box(Modifier.layoutId(HomeInformationPart.Failure)) { failure() }
            Box(Modifier.layoutId(HomeInformationPart.Dashboard)) { dashboard() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val natural = Constraints(maxWidth = width)
        fun measure(part: HomeInformationPart) = measurables.firstOrNull { it.layoutId == part }?.measure(natural)
        val statusPlaceable = requireNotNull(measure(HomeInformationPart.Status))
        val dashboardPlaceable = requireNotNull(measure(HomeInformationPart.Dashboard))
        val failurePlaceable = measure(HomeInformationPart.Failure)
        val failureGap = if (failurePlaceable != null) 12.dp.roundToPx() else 0
        val fixedHeight = statusPlaceable.height + dashboardPlaceable.height + (failurePlaceable?.height ?: 0) + failureGap
        val available = viewportHeight.roundToPx().coerceAtLeast(0)
        val preferredEdge = 24.dp.roundToPx()
        val preferredGap = 24.dp.roundToPx()
        val minimumEdge = 8.dp.roundToPx()
        val minimumGap = 12.dp.roundToPx()
        val minimumGaps = minimumEdge * 2 + minimumGap * 2
        val mapBudget = (available - fixedHeight - minimumGaps).coerceAtLeast(112.dp.roundToPx())
        val mapPlaceable = measurables.first { it.layoutId == HomeInformationPart.Map }
            .measure(Constraints(minWidth = width, maxWidth = width, maxHeight = mapBudget))
        val mapHeight = mapPlaceable.height
        val preferredGaps = preferredEdge * 2 + preferredGap * 2
        val shrinkableGaps = preferredGaps - minimumGaps
        val shortage = (fixedHeight + mapHeight + preferredGaps - available).coerceAtLeast(0)
        val gapCompression = if (shrinkableGaps == 0) 0f else (shortage.toFloat() / shrinkableGaps).coerceIn(0f, 1f)
        val edge = preferredEdge - ((preferredEdge - minimumEdge) * gapCompression).roundToInt()
        val gap = preferredGap - ((preferredGap - minimumGap) * gapCompression).roundToInt()
        val contentHeight = fixedHeight + mapHeight + edge * 2 + gap * 2
        val height = maxOf(available, contentHeight)
        // Extra height becomes outer whitespace, not increasingly wide gaps between components.
        val centerOffset = ((height - contentHeight) / 2).coerceAtMost(24.dp.roundToPx())
        layout(width, height) {
            var y = edge + centerOffset
            mapPlaceable.placeRelative(0, y)
            y += mapHeight + gap
            statusPlaceable.placeRelative((width - statusPlaceable.width) / 2, y)
            y += statusPlaceable.height
            if (failurePlaceable != null) {
                y += failureGap
                failurePlaceable.placeRelative((width - failurePlaceable.width) / 2, y)
                y += failurePlaceable.height
            }
            y += gap
            dashboardPlaceable.placeRelative((width - dashboardPlaceable.width) / 2, y)
        }
    }
}

private enum class HomeInformationPart { Map, Status, Failure, Dashboard }

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
