package com.android.xrayfa.ui.component

import android.annotation.SuppressLint
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.xrayfa.R
import com.android.xrayfa.model.Node
import com.android.xrayfa.ui.ArcBottomShape
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.viewmodel.XrayViewmodel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

@Composable
fun HomeScreen(
    id: Int? = null,
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedNode by xrayViewmodel.getSelectedNode().collectAsState(null)
    var notConfig by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Dashboard(xrayViewmodel = xrayViewmodel,node = selectedNode)
        //Dashboard(xrayViewmodel,modifier = Modifier.align(Alignment.TopCenter))

        V2rayStarter(xrayViewmodel,modifier = Modifier.align(BiasAlignment(0f,0.8f))) {
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
        NoConfigMessage(notConfig)
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
        targetValue = if (toggle) Home().containerColor else Color.Gray,
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
        },
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .size(64.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
    ) {

        AnimatedContent(
            targetState = toggle,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.6f, animationSpec = tween(300))) togetherWith
                        (fadeOut(tween(300)) + scaleOut(targetScale = 1.4f, animationSpec = tween(300)))
            },
            label = "iconSwitchAnim",
        ) { state ->
            Icon(
                imageVector = if (state) Icons.Filled.Done
                else ImageVector.vectorResource(R.drawable.ic_power),
                contentDescription = "",
                tint = Color.White,
                modifier = modifier.size(36.dp)
            )
        }
    }
}


@Composable
fun Dashboard(
    xrayViewmodel: XrayViewmodel,
    node: Node?,
) {
    val context = LocalContext.current
    val delay by xrayViewmodel.delay.collectAsState()
    val test by xrayViewmodel.testing.collectAsState()
    val isRunning by xrayViewmodel.isServiceRunning.collectAsState()
    Surface(
        color = Color(0xFF00BFFF),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = ArcBottomShape(arcHeight = 80f),
        modifier = Modifier.fillMaxWidth()
            .fillMaxHeight(0.5f)
    ) {
        Box(
            modifier = Modifier.padding(8.dp)
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
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
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
                        tint = Color.Black
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
                        text = "$upSpeed KB/s",
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
                        tint = Color.Black
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
                        text = "$downSpeed KB/s",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NoConfigMessage(shown: Boolean) {
    AnimatedVisibility(
        visible = shown,
        enter = slideInVertically(
            // Enters by sliding in from offset -fullHeight to 0.
            initialOffsetY = { fullHeight -> -fullHeight },
            animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
        ),
        exit = slideOutVertically(
            // Exits by sliding out from offset 0 to -fullHeight.
            targetOffsetY = { fullHeight -> -fullHeight },
            animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondary,
            shadowElevation = 4.dp
        ) {
            Text(
                text = stringResource(R.string.config_not_ready),
                color = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}