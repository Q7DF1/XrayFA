package com.android.xrayfa.ui.component

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.window.core.layout.WindowWidthSizeClass
import com.android.xrayfa.R
import com.android.xrayfa.dto.Node
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.viewmodel.XrayViewmodel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
    sharedTransitionScope: SharedTransitionScope,
    onSettingsClick: () -> Unit = {}
) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpanded = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val isMedium = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
    var showError by remember { mutableStateOf(false) }

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
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
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
                CompactHomeContent(xrayViewmodel) { showError = it }
            }

            ExceptionMessage(
                shown = showError,
                msg = stringResource(R.string.config_not_ready),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun CompactHomeContent(
    xrayViewmodel: XrayViewmodel,
    onShowError: (Boolean) -> Unit
) {
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val upSpeed by xrayViewmodel.upSpeed.collectAsState()
    val downSpeed by xrayViewmodel.downSpeed.collectAsState()
    val delayMs by xrayViewmodel.delay.collectAsState()
    val testing by xrayViewmodel.testing.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        StatusCard(isRunning, upSpeed, downSpeed)

        Spacer(modifier = Modifier.height(32.dp))

        V2rayStarterLarge(xrayViewmodel) {
            if (selectedNode == null) {
                coroutineScope.launch {
                    onShowError(true)
                    delay(2000L)
                    onShowError(false)
                }
                false
            } else true
        }

        Text(
            text = stringResource(if (isRunning) R.string.connected else R.string.disconnect),
            style = MaterialTheme.typography.titleMedium,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        selectedNode?.let { node ->
            Text(
                text = "Connection Details",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            NodeCard(
                node = node,
                onTest = { xrayViewmodel.measureDelay(context) },
                delayMs = delayMs,
                testing = testing,
                roundCorner = true,
                enableTest = isRunning,
//                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        } ?: ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.select_configuration_notify), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ExpandedHomeContent(
    xrayViewmodel: XrayViewmodel
) {
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val upSpeed by xrayViewmodel.upSpeed.collectAsState()
    val downSpeed by xrayViewmodel.downSpeed.collectAsState()
    val delayMs by xrayViewmodel.delay.collectAsState()
    val testing by xrayViewmodel.testing.collectAsState()
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            V2rayStarterLarge(xrayViewmodel) { selectedNode != null }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(if (isRunning) R.string.connected else R.string.disconnect),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(32.dp))
            StatusCard(isRunning, upSpeed, downSpeed)
        }

        Column(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Connection Details",
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
//                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            } ?: ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(Modifier.padding(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No Node Selected", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun StatusCard(isRunning: Boolean, upSpeed: Double, downSpeed: Double) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedItem(
                label = stringResource(R.string.upload_data),
                speed = upSpeed,
                icon = Icons.Default.KeyboardArrowUp,
                color = MaterialTheme.colorScheme.primary
            )
            VerticalDivider(modifier = Modifier.height(40.dp).width(1.dp))
            SpeedItem(
                label = stringResource(R.string.download_data),
                speed = downSpeed,
                icon = Icons.Default.KeyboardArrowDown,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun SpeedItem(label: String, speed: Double, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "${String.format("%.1f", speed)} KB/s",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun V2rayStarterLarge(
    xrayViewmodel: XrayViewmodel,
    onCheck: () -> Boolean
) {
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val context = LocalContext.current

    val color by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 500),
        label = "color"
    )

    val shadowColor = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            xrayViewmodel.startV2rayService(context)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(160.dp)
            .shadow(
                elevation = if (isRunning) 20.dp else 0.dp,
                shape = CircleShape,
                spotColor = shadowColor,
                ambientColor = shadowColor
            )
            .clip(CircleShape)
            .background(color)
    ) {
        IconButton(
            onClick = {
                if (!onCheck()) return@IconButton
                if (!isRunning) {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) launcher.launch(prepare)
                    else xrayViewmodel.startV2rayService(context)
                } else {
                    xrayViewmodel.stopV2rayService(context)
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Done else ImageVector.vectorResource(R.drawable.ic_power),
                contentDescription = "Toggle Service",
                tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
