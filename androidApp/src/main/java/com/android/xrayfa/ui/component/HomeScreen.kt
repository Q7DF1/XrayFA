package com.android.xrayfa.ui.component

import android.app.Activity
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.ui.home.HomeMapLocation
import com.android.xrayfa.shared.ui.home.HomeMapLocations
import com.android.xrayfa.shared.ui.SharedHomeSection
import com.android.xrayfa.shared.ui.rememberHomeUiLabels
import com.android.xrayfa.ui.home.AndroidHomeIpLocationLookup
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun CompactHomeContent(
    homeComponent: HomeComponent,
) {
    val context = LocalContext.current
    val homeState by homeComponent.state.subscribeAsState()
    val mapLocations = rememberHomeMapLocations(
        nodeAddress = homeState.selectedNode?.address.takeIf { homeState.isConnected },
    )
    val homeLabels = rememberHomeUiLabels()

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeComponent.onConnectToggle()
            } else {
                homeComponent.onVpnPermissionDenied()
            }
        }

    SharedHomeSection(
        component = homeComponent,
        labels = homeLabels,
        mapLocations = mapLocations,
        modifier = Modifier.fillMaxSize(),
        onConnectToggle = {
            val state = homeComponent.state.value
            if (state.isConnected || state.selectedNode == null) {
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

@Composable
fun ExpandedHomeContent(
    homeComponent: HomeComponent,
) {
    val homeState by homeComponent.state.subscribeAsState()
    val selectedNode = homeState.selectedNode
    val context = LocalContext.current
    val mapLocations = rememberHomeMapLocations(
        nodeAddress = selectedNode?.address.takeIf { homeState.isConnected },
    )

    val homeLabels = rememberHomeUiLabels()

    val vpnPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeComponent.onConnectToggle()
            } else {
                homeComponent.onVpnPermissionDenied()
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
                mapLocations = mapLocations,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                onConnectToggle = {
                    val state = homeComponent.state.value
                    if (state.isConnected || state.selectedNode == null) {
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
                    onTest = homeComponent::onTestDelay,
                    delayMs = homeState.delayMs,
                    testing = homeState.testing,
                    roundCorner = true,
                    enableTest = homeState.isConnected,
                )
            } ?: EmptyNodeCard(
                text = stringResource(R.string.no_configuration),
                contentPadding = 40.dp
            )
        }
    }
}

@Composable
private fun rememberHomeMapLocations(nodeAddress: String?): HomeMapLocations {
    val lookup: AndroidHomeIpLocationLookup = koinInject()
    val context = LocalContext.current.applicationContext
    val connectivityManager = remember(context) {
        context.getSystemService(ConnectivityManager::class.java)
    }
    var networkEpoch by remember { mutableIntStateOf(0) }
    var locations by remember { mutableStateOf(HomeMapLocations()) }
    var lastNodeAddress by remember { mutableStateOf<String?>(null) }
    var knownServerLocations by remember { mutableStateOf<Map<String, HomeMapLocation>>(emptyMap()) }

    DisposableEffect(connectivityManager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkEpoch++
            }

            override fun onLost(network: Network) {
                networkEpoch++
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    LaunchedEffect(nodeAddress, networkEpoch) {
        if (nodeAddress != lastNodeAddress || nodeAddress == null) {
            locations = locations.copy(server = nodeAddress?.let(knownServerLocations::get))
        }
        lastNodeAddress = nodeAddress
        while (true) {
            val refreshed = lookup.lookup(nodeAddress)
            if (nodeAddress != null) {
                refreshed.server?.let { serverLocation ->
                    knownServerLocations = knownServerLocations + (nodeAddress to serverLocation)
                }
            }
            locations = HomeMapLocations(
                user = refreshed.user ?: locations.user,
                server = if (nodeAddress == null) null else {
                    refreshed.server ?: knownServerLocations[nodeAddress] ?: locations.server
                },
            )
            delay(15 * 60 * 1_000L)
        }
    }
    return locations
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
