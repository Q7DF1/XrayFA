package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.xrayfa.R
import com.android.xrayfa.dto.Node
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.viewmodel.XrayViewmodel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    xrayViewmodel: XrayViewmodel,
    bottomPadding: Dp = 0.dp,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    var notConfig by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()){
        Dashboard(
            xrayViewmodel = xrayViewmodel,
            node = selectedNode,
            onSettingsClick = onSettingsClick,
            showError = notConfig
        )
        //Dashboard(xrayViewmodel,modifier = Modifier.align(Alignment.TopCenter))

        V2rayStarter(
            xrayViewmodel,
            modifier = Modifier.align(BiasAlignment(
                0f,
                if (bottomPadding == 0.dp)0.8f else 0.9f)
            ).padding(bottom = bottomPadding)
        ) {
            if (selectedNode == null) {
                coroutineScope.launch {
                    notConfig = true
                    delay(2000L)
                    notConfig = false
                }
                false
            }else {
                true
            }
        }

    }
}

@Composable
fun V2rayStarter(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier,
    onCheck: () -> Boolean
) {
    val toggle by xrayViewmodel.isServiceRunning.collectAsState()
    val context = LocalContext.current
    val color by animateColorAsState(
        targetValue = if (toggle) MaterialTheme.colorScheme.primary else Color.Gray,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "iconColorAnim"
    )
    val scale by animateFloatAsState(
        targetValue = if (toggle) 1.3f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "iconScaleAnim"
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            xrayViewmodel.startV2rayService(context)
        }
    }

    AnimatedContent(
        targetState = toggle,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(initialScale = 0.6f, animationSpec = tween(300))) togetherWith
                    (fadeOut(tween(300)) + scaleOut(targetScale = 1.4f, animationSpec = tween(300)))
        },
        label = "iconSwitchAnim",
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .size(64.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
    ){ state ->
        IconButton(
            onClick = {
                if (!onCheck()) {
                    return@IconButton
                }
                if (!toggle) {
                    val prepare = VpnService.prepare(context)
                    if (prepare != null) {
                        launcher.launch(prepare)
                    }else {
                        xrayViewmodel.startV2rayService(context)
                    }
                }else{
                    xrayViewmodel.stopV2rayService(context)
                }
            }
        ) {
            Icon(
                imageVector = if (state) Icons.Filled.Done
                else ImageVector.vectorResource(R.drawable.ic_power),
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(
    xrayViewmodel: XrayViewmodel,
    node: Node?,
    onSettingsClick: () -> Unit = {},
    showError: Boolean = false
) {
    val context = LocalContext.current
    val delay by xrayViewmodel.delay.collectAsState()
    val test by xrayViewmodel.testing.collectAsState()
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = ArcBottomShape(arcHeight = 80f),
        modifier = Modifier.fillMaxWidth()
            .fillMaxHeight(0.5f)
    ) {
        Column(modifier = Modifier.fillMaxSize()){
            TopAppBar(
                title = {Text(context.getString(Home.title))},
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = ""
                    )
                },
                actions = {HomeActionButton(onSettingsClick)},
                colors = TopAppBarDefaults.topAppBarColors(MaterialTheme.colorScheme.primary),
                modifier = Modifier.zIndex(1f)
                )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                node?.let {
                    NodeCard(
                        node = node,
                        modifier = Modifier.align(BiasAlignment(0f,-0.5f)),
                        onTest = {xrayViewmodel.measureDelay(context = context)},
                        delayMs = delay,
                        testing = test,
                        enableTest = isRunning
                    )
                }?: Text(
                    //TODO more beautiful
                    text = stringResource(R.string.select_configuration_notify),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(BiasAlignment(0f,-0.5f))
                )
                DashboardContent(
                    xrayViewmodel = xrayViewmodel,
                    modifier = Modifier.align(BiasAlignment(0f,0.7f))
                )
                ExceptionMessage(
                    shown = showError,
                    msg = stringResource(R.string.config_not_ready),
                    modifier = Modifier.zIndex(0f)) // hide in appTopBar
            }
        }

    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "DefaultLocale")
@Composable
fun DashboardContent(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val upSpeed by xrayViewmodel.upSpeed.collectAsState()
    val downSpeed by xrayViewmodel.downSpeed.collectAsState()
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.padding(horizontal = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.
            padding(horizontal = 8.dp, vertical = 8.dp)

        ) {
            //upload
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ){
                Box(
                    modifier = Modifier
                        .size((screenWidth*0.08).dp.coerceIn(24.dp,48.dp)) // 整体大小
                        .clip(CircleShape) // 裁剪成圆形
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "upload icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.upload_data),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${String.format("%.1f",upSpeed)} KB/s",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            VerticalDivider(
                modifier = Modifier.height((screenWidth*0.1).dp.coerceIn(24.dp,48.dp))
            )
            //download
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
                    .padding(start = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size((screenWidth*0.08).dp.coerceIn(24.dp,48.dp)) // 整体大小
                        .clip(CircleShape) // 裁剪成圆形
                        .background(MaterialTheme.colorScheme.surface), // 背景色
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "download icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 8.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.download_data),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "${String.format("%.1f",downSpeed)} KB/s",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

