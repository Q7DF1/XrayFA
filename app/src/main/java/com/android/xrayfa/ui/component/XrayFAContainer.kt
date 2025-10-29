package com.android.xrayfa.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = getString(LocalContext.current,currentScreen.title),
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = currentScreen.icon,
                        contentDescription = ""
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            when(currentScreen) {
                                Home -> onSettingsClick(context)
                                Config -> {}
                                Logcat -> {xrayViewmodel.exportLogcatToClipboard(context)}
                            else -> throw RuntimeException("unknown route")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = when(currentScreen) {
                                Home -> Icons.Default.Settings
                                Config -> Icons.Default.Star
                                Logcat -> ImageVector.vectorResource(R.drawable.copu)
                                else -> throw RuntimeException("unknown route")
                            },
                            contentDescription = "",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (currentScreen.route == "home") {
                        MaterialTheme.colorScheme.primary
                    }else {
                        MaterialTheme.colorScheme.background
                    }
                )
            )
        },
        bottomBar = {

            XrayBottomNav(
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Home.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { x->
                        if (initialState.destination.route == Config.route) x else -x
                    }, animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { x ->
                        if (targetState.destination.route == Config.route) x else -x
                    }, animationSpec = tween(400))
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
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400))
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
                    slideInHorizontally(initialOffsetX = {it}, animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = {-it}, animationSpec = tween(400))
                }
            ) {
                LogcatScreen(xrayViewmodel)
            }
        }
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
