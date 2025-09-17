package com.android.v2rayForAndroidUI.ui.navigation

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.v2rayForAndroidUI.R
import com.android.v2rayForAndroidUI.V2rayBaseService
import com.android.v2rayForAndroidUI.model.Node
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.ui.component.NodeCard
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel

data class Home(
    val node: Node? = null
): NavigateDestination {
    override val icon: ImageVector
        get() = Icons.Default.Home
    override val route: String
        get() = "home"

}


@Composable
fun HomeScreen(
    node: Node? = null,
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    var config  by remember { mutableStateOf("111")}

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(16.dp)
    ) {
        NodeCard(
            node = Node(0,Protocol.VLESS,"122.212.121.32",18880),
            modifier = Modifier.align(Alignment.Center)
        )
        V2rayStarter(xrayViewmodel,modifier = Modifier.align(BiasAlignment(0f,0.8f)))
    }
}

@Composable
fun V2rayStarter(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier
) {
    val context = LocalContext.current
    var toggle by remember {mutableStateOf(xrayViewmodel.isV2rayServiceRunning())}
    val color by animateColorAsState(
        targetValue = if (toggle) Color.Blue else Color.Red,
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
            val intent = Intent(context, V2rayBaseService::class.java).apply {
                action = "connect"
            }
            context.startForegroundService(intent)
        }
    }

    IconButton(
        onClick = {
            toggle = !toggle
            if (toggle) {
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
                imageVector = if (state) Icons.Filled.Done else ImageVector.vectorResource(R.drawable.ic_power),
                contentDescription = "",
                tint = Color.White,
                modifier = modifier.size(36.dp)
            )
        }
    }
}



@Preview
@Composable
fun V2rayStarterPreview() {
}
