package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.getString
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.xrayfa.ui.navigation.About
import com.android.xrayfa.ui.navigation.AboutScreen
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.list_navigation
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2rayFAContainer(
    xrayViewmodel: XrayViewmodel,
    modifier: Modifier = Modifier
) {
    val naviController = rememberNavController()
    var selected by remember { mutableStateOf("home") }
    var imageVector by remember { mutableStateOf(Icons.Default.Home) }
    var actionImageVector by remember { mutableStateOf(Icons.Default.Menu) }
    var title by remember { mutableIntStateOf(R.string.home) }
    var isHome by remember { mutableStateOf(true)}
    var onActionbarClick by remember { mutableStateOf({}) } //TODO

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = getString(LocalContext.current,title),
                    )
                },
                navigationIcon = {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = ""
                    )
                },
                actions = {
                    IconButton(
                        onClick = onActionbarClick
                    ) {
                        Icon(
                            imageVector = actionImageVector,
                            contentDescription = ""
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isHome) {
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
                selectedRoute = selected,
                onItemSelected = { item ->
                    naviController.navigate(route = item.route) {
                        launchSingleTop= true
                        restoreState = true
                        popUpTo(naviController.graph.findStartDestination().id) {
                            saveState = true
                        }
                    }
                    selected = item.route
                    imageVector = item.icon
                    isHome = item.route == "home"
                    when(item.route) {
                        "home" -> {
                            title = R.string.home
                            actionImageVector = Icons.Default.Menu
                        }
                        "config" -> {
                            title = R.string.config
                            actionImageVector = Icons.Default.Search
                        }
                        "about" -> {
                            title = R.string.about
                            actionImageVector = Icons.Default.Star
                        }
                        else -> throw RuntimeException("unknown route")
                    }
                },
                labelProvider = { item -> item.route },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) { innerPadding->

        NavHost(
            navController = naviController,
            startDestination = Home().route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = "home?{id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id")
                HomeScreen(
                    id = if (id == -1) null else id,
                    xrayViewmodel = xrayViewmodel,
                    modifier = modifier,
                )
            }

            composable(route = Config.route) {
                ConfigScreen(
                    onNavigate2Home = { id->
                        if (!xrayViewmodel.isServiceRunning.value) {
                            naviController.navigate(route = Home().route) {
                                launchSingleTop= true
                                restoreState = true
                                popUpTo(naviController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                            selected = Home().route
                            isHome = true
                        }
                    },
                    xrayViewmodel = xrayViewmodel
                )
            }

            composable(route = About.route) {
                AboutScreen()
            }
        }
    }
}
