package com.android.xrayfa.ui.component

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.window.core.layout.WindowWidthSizeClass
import com.android.xrayfa.R
import com.android.xrayfa.ui.home.rememberAndroidHomeComponent
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.home.HomeUiLabels
import com.android.xrayfa.shared.ui.home.HomeSectionHeader
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.viewmodel.XrayViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSettingsClick: () -> Unit = {}
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val isMedium = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Home.title), fontWeight = FontWeight.Bold) },
                actions = {
                    with(sharedTransitionScope) {
                        IconButton(
                            onClick = onSettingsClick
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.sharedElement(
                                    sharedTransitionScope.rememberSharedContentState(key = Settings.route),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(bottom = bottomPadding)
        ) {
            if (isExpanded || isMedium) {
                ExpandedHomeContent(xrayViewmodel)
            } else {
                CompactHomeContent(xrayViewmodel)
            }
        }
    }
}

@Composable
fun CompactHomeContent(xrayViewmodel: XrayViewmodel) {
    val homeComponent = rememberAndroidHomeComponent()
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val delayMs by xrayViewmodel.delay.collectAsState()
    val testing by xrayViewmodel.testing.collectAsState()
    val context = LocalContext.current

    val homeLabels =
        HomeUiLabels(
            connectedLabel = stringResource(R.string.connected),
            disconnectedLabel = stringResource(R.string.not_connected),
            connectedHint = stringResource(R.string.tap_to_disconnect),
            disconnectedHint = stringResource(R.string.tap_to_connect),
            uploadLabel = stringResource(R.string.upload_data),
            downloadLabel = stringResource(R.string.download_data),
            connectionDetailsHeader = stringResource(R.string.connection_detail),
            configNotReadyMessage = stringResource(R.string.config_not_ready),
        )

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeComponent.onConnectToggle()
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SharedHomeSection(
            component = homeComponent,
            showNodeCard = false,
            scrollEnabled = false,
            labels = homeLabels,
            onConnectToggle = {
                val state = homeComponent.state.value
                if (state.isConnected) {
                    homeComponent.onConnectToggle()
                    return@SharedHomeSection
                }
                val prepare = VpnService.prepare(context)
                if (prepare != null) {
                    vpnPermissionLauncher.launch(prepare)
                } else {
                    homeComponent.onConnectToggle()
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            selectedNode?.let { node ->
                HomeSectionHeader(
                    text = stringResource(R.string.connection_detail),
                    modifier = Modifier.fillMaxWidth(),
                )
                NodeCard(
                    node = node,
                    onTest = { xrayViewmodel.measureDelay(context) },
                    delayMs = delayMs,
                    testing = testing,
                    roundCorner = true,
                    enableTest = isRunning,
                )
            } ?: EmptyNodeCard(
                text = stringResource(R.string.select_configuration_notify),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun ExpandedHomeContent(
    xrayViewmodel: XrayViewmodel
) {
    val homeComponent = rememberAndroidHomeComponent()
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val delayMs by xrayViewmodel.delay.collectAsState()
    val testing by xrayViewmodel.testing.collectAsState()
    val context = LocalContext.current

    val homeLabels =
        HomeUiLabels(
            connectedLabel = stringResource(R.string.connected),
            disconnectedLabel = stringResource(R.string.not_connected),
            connectedHint = stringResource(R.string.tap_to_disconnect),
            disconnectedHint = stringResource(R.string.tap_to_connect),
            uploadLabel = stringResource(R.string.upload_data),
            downloadLabel = stringResource(R.string.download_data),
            connectionDetailsHeader = stringResource(R.string.connection_detail),
            configNotReadyMessage = stringResource(R.string.config_not_ready),
        )

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeComponent.onConnectToggle()
            }
        }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            SharedHomeSection(
                component = homeComponent,
                showNodeCard = false,
                scrollEnabled = false,
                largeStatusLabel = true,
                labels = homeLabels,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                onConnectToggle = {
                    val state = homeComponent.state.value
                    if (state.isConnected) {
                        homeComponent.onConnectToggle()
                        return@SharedHomeSection
                    }
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        vpnPermissionLauncher.launch(prepare)
                    } else {
                        homeComponent.onConnectToggle()
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.connection_detail),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            selectedNode?.let { node ->
                NodeCard(
                    node = node,
                    onTest = { xrayViewmodel.measureDelay(context) },
                    delayMs = delayMs,
                    testing = testing,
                    roundCorner = true,
                    enableTest = isRunning,
                )
            } ?: EmptyNodeCard(
                text = stringResource(R.string.no_configuration),
                contentPadding = 40.dp
            )
        }
    }
}

@Composable
fun EmptyNodeCard(text: String, contentPadding: Dp = 28.dp) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
