package com.android.v2rayForAndroidUI.ui.component

import android.app.Activity
import android.content.Intent
import android.icu.number.Scale
import android.net.VpnService
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHost
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.android.v2rayForAndroidUI.R
import com.android.v2rayForAndroidUI.V2rayBaseService
import com.android.v2rayForAndroidUI.V2rayCoreManager
import com.android.v2rayForAndroidUI.ui.navigation.About
import com.android.v2rayForAndroidUI.ui.navigation.AboutScreen
import com.android.v2rayForAndroidUI.ui.navigation.Config
import com.android.v2rayForAndroidUI.ui.navigation.ConfigScreen
import com.android.v2rayForAndroidUI.ui.navigation.Home
import com.android.v2rayForAndroidUI.ui.navigation.HomeScreen
import com.android.v2rayForAndroidUI.ui.navigation.list_navigation
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel


@Composable
fun V2rayFAContainer(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {

    val naviController = rememberNavController()
    var selected by remember { mutableStateOf("home") }
    Scaffold(
        bottomBar = {

            XrayBottomNav(
                items = list_navigation,
                selectedRoute = selected,
                onItemSelected = { item ->
                    naviController.navigate(route = item.route)
                    selected = item.route
                },
                labelProvider = { item -> item.route },
            )
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) { innerPadding->

        NavHost(
            navController = naviController,
            startDestination = Home().route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(route = Home().route) {
                HomeScreen(
                    xrayViewmodel = xrayViewmodel,
                    modifier = modifier
                )
            }

            composable(route = Config.route) {
                ConfigScreen(
                    onNavigate2Home = { node->
                        naviController.navigate(route = Home(node).route)
                    }
                )
            }

            composable(route = About.route) {
                AboutScreen()
            }
        }
    }

}
