package com.android.v2rayForAndroidUI.ui.component

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.v2rayForAndroidUI.R


@Composable
fun V2rayFAContainer(
    modifier: Modifier
) {
    Column (
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        V2rayFAHeader()
        V2rayStarter()
    }
}

@Composable
fun V2rayFAHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ){
        Text(
            text = "app",
        )
        Text(
            text = "menu"
        )
    }
}

@Composable
fun V2rayStarter() {
    var toggle by remember {mutableStateOf(false)}
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
    IconButton(
        onClick = {
            toggle = !toggle

        },
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .size(64.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            )
    ) {
//        Icon(
//            imageVector = if (!toggle)Icons.Filled.PlayArrow else Icons.Filled.Done,
//            contentDescription = "",
//            tint = Color.White
//        )

        AnimatedContent(
            targetState = toggle,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.6f, animationSpec = tween(300))) togetherWith
                        (fadeOut(tween(300)) + scaleOut(targetScale = 1.4f, animationSpec = tween(300)))
            },
            label = "iconSwitchAnim"
        ) { state ->
            Icon(
                imageVector = if (state) Icons.Filled.Done else ImageVector.vectorResource(R.drawable.ic_power),
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


@Preview
@Composable
fun V2rayStarterPreview() {
    V2rayStarter()
}