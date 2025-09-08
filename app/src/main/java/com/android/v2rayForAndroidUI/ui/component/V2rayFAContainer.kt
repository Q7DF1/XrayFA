package com.android.v2rayForAndroidUI.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import java.nio.file.WatchEvent


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
        targetValue = if (toggle) Color.Red else Color.Gray,
        label = "iconColorAnim"
    )
    IconButton(
        onClick = {
            toggle = !toggle
        },
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = ""
        )
    }
}


@Preview
@Composable
fun V2rayStarterPreview() {
    V2rayStarter()
}