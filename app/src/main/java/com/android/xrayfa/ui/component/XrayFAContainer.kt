package com.android.xrayfa.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getString
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.list_navigation
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.R

import com.android.xrayfa.ui.SettingsActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayFAContainer(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    val naviController = rememberNavController()
    val currentBackStack by naviController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination
    val currentScreen = list_navigation.find { it.route == currentDestination?.route } ?: Home
    val context = LocalContext.current

    var checked by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {

            XrayBottomNavOpt(
                items = list_navigation,
                currentScreen = currentScreen,
                onItemSelected = { item ->
                    naviController.navigateSingleTopTo(item.route)
                },
                labelProvider = { item -> item.route },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) { innerPadding->

        NavHost(
            navController = naviController,
            startDestination = Home.route,
            modifier = Modifier.padding(
                bottom = innerPadding.calculateBottomPadding(),
            )
        ) {
            composable(
                route = Home.route,
                enterTransition = {
                    scaleIn(initialScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeIn(animationSpec = subtleAnimSpec)
                },
                exitTransition = {
                    scaleOut(targetScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeOut(animationSpec = subtleAnimSpec)
                }
            ) { backStackEntry ->
                HomeScreen(
                    xrayViewmodel = xrayViewmodel,
                    modifier = modifier,
                )
            }

            composable(
                route = Config.route,
                enterTransition = {
                    scaleIn(initialScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeIn(animationSpec = subtleAnimSpec)
                },
                exitTransition = {
                    scaleOut(targetScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeOut(animationSpec = subtleAnimSpec)
                }
            ) {
                ConfigScreen(
                    onNavigate2Home = { id->
                        if (!xrayViewmodel.isServiceRunning.value) {
                            naviController.navigateSingleTopTo(Home.route)
                        }
                    },
                    xrayViewmodel = xrayViewmodel
                )
            }

            composable(
                route = Logcat.route,
                enterTransition = {
                    scaleIn(initialScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeIn(animationSpec = subtleAnimSpec)
                },
                exitTransition = {
                    scaleOut(targetScale = 0.92f, animationSpec = subtleAnimSpec) +
                            fadeOut(animationSpec = subtleAnimSpec)
                }
            ) {
                LogcatScreen(xrayViewmodel)
            }
        }
    }
}

@Composable
fun HomeActionButton() {
    val context = LocalContext.current
    IconButton(
        onClick = {onSettingsClick(context)}
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = ""
        )
    }
}

@Composable
fun LogcatActionButton(
    xrayViewmodel: XrayViewmodel
) {
    val context = LocalContext.current
    IconButton(
        onClick = {xrayViewmodel.exportLogcatToClipboard(context)}
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.copu),
            contentDescription = "",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ConfigActionButton(
    xrayViewmodel: XrayViewmodel
) {
    var expend by remember { mutableStateOf(false) }
    val context = LocalContext.current
    IconButton(
        onClick = {expend = !expend}
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = ""
        )
    }
    DropdownMenu(
        expanded = expend,
        onDismissRequest = {expend = false}
    ) {
        DropdownMenuItem(
            text = {Text("subscription")},
            onClick = {
                expend = false
                xrayViewmodel.startSubscriptionActivity(context)
            }
        )
    }
}

fun onSettingsClick(context: Context) {
    context.startActivity(Intent(context, SettingsActivity::class.java))
}


fun NavHostController.navigateSingleTopTo(route: String) {
    this.navigate(route) {
        popUpTo(
            this@navigateSingleTopTo.graph.findStartDestination().id
        ) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

val popAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, // 弹性阻尼：中等回弹
    stiffness = Spring.StiffnessLow // 刚度：低（越低越慢越Q）
)
val subtleAnimSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
