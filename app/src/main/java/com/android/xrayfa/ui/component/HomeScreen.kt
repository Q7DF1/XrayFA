package com.android.xrayfa.ui.component

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.viewmodel.XrayViewmodel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibilityScope

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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

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

        ConnectionStatusLabel(isRunning = isRunning)

        StatusCard(isRunning, upSpeed, downSpeed)

        selectedNode?.let { node ->
            SectionHeader(
                text = stringResource(R.string.connection_detail),
                modifier = Modifier.fillMaxWidth()
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
            text = stringResource(R.string.select_configuration_notify)
        )

        Spacer(modifier = Modifier.height(8.dp))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                V2rayStarterLarge(xrayViewmodel) { selectedNode != null }
                Spacer(modifier = Modifier.height(28.dp))
                ConnectionStatusLabel(isRunning = isRunning, large = true)
                Spacer(modifier = Modifier.height(36.dp))
                StatusCard(isRunning, upSpeed, downSpeed)
            }
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
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun ConnectionStatusLabel(isRunning: Boolean, large: Boolean = false) {
    val statusColor = if (isRunning) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (large) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (isRunning) R.string.connected else R.string.not_connected),
                style = if (large) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(if (isRunning) R.string.tap_to_disconnect else R.string.tap_to_connect),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

@Composable
fun StatusCard(isRunning: Boolean, upSpeed: Double, downSpeed: Double) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.upload_data),
                speed = upSpeed,
                icon = Icons.Default.KeyboardArrowUp,
                active = isRunning,
                color = MaterialTheme.colorScheme.primary
            )
            VerticalDivider(modifier = Modifier.height(36.dp).width(1.dp))
            SpeedItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.download_data),
                speed = downSpeed,
                icon = Icons.Default.KeyboardArrowDown,
                active = isRunning,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun SpeedItem(
    label: String,
    speed: Double,
    icon: ImageVector,
    color: Color,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format("%.1f", if (active) speed else 0.0)} KB/s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun V2rayStarterLarge(
    xrayViewmodel: XrayViewmodel,
    onCheck: () -> Boolean
) {
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    val context = LocalContext.current

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val buttonBrush = if (isRunning) {
        Brush.linearGradient(colors = listOf(primary, tertiary))
    } else {
        Brush.linearGradient(
            colors = listOf(
                surfaceVariant,
                surfaceVariant.copy(alpha = 0.65f)
            )
        )
    }

    val shadowColor = if (isRunning) primary.copy(alpha = 0.45f) else Color.Transparent

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            xrayViewmodel.startXrayService(context)
        }
    }
    val scale = remember { Animatable(1.0f) }

    // Re-launch the effect whenever the 'running' variable changes
    LaunchedEffect(isRunning) {
        // Step 1: Animate the scale up to 1.1f quickly
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 150)
        )

        // Step 2: Bounce back to 1.0f with a spring effect
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // Expanding pulse rings while connected
        if (isRunning) {
            PulseRings(color = primary)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(148.dp)
                .scale(scale.value)
                .shadow(
                    elevation = if (isRunning) 24.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = shadowColor,
                    ambientColor = shadowColor
                )
                .clip(CircleShape)
                .background(buttonBrush)
        ) {
            IconButton(
                onClick = {
                    if (!onCheck()) return@IconButton
                    if (!isRunning) {
                        val prepare = VpnService.prepare(context)
                        if (prepare != null) launcher.launch(prepare)
                        else xrayViewmodel.startXrayService(context)
                    } else {
                        xrayViewmodel.stopXrayService(context)
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Done else ImageVector.vectorResource(R.drawable.ic_power),
                    contentDescription = "Toggle Service",
                    tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(60.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PulseRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(2) { index ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                initialStartOffset = StartOffset(index * 1200)
            ),
            label = "ring$index"
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .scale(0.74f + progress * 0.26f)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = color.copy(alpha = (1f - progress) * 0.5f),
                    shape = CircleShape
                )
        )
    }
}
