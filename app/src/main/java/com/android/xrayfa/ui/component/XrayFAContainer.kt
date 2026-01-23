package com.android.xrayfa.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.list_navigation
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.R

import com.android.xrayfa.ui.SettingsActivity
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.ListDetailSceneStrategy
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Navigator
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.ui.navigation.rememberListDetailSceneStrategy
import com.android.xrayfa.ui.navigation.rememberNavigationState
import com.android.xrayfa.ui.navigation.toEntries
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayFAContainer(
    xrayViewmodel: XrayViewmodel,
    detailViewmodel: DetailViewmodel,
    settingsViewmodel: SettingsViewmodel,
    isLandScape: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // migrate to navigation 3
    val navigationState = rememberNavigationState(
        startRoute = Home,
        topLevelRoutes = setOf(Home, Config, Logcat)
    )
    val navigator = remember { Navigator(navigationState) }
    val current = navigationState.topLevelRoute

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<Home> { key ->
            HomeScreen(xrayViewmodel) {
                navigator.navigate(Settings)
            }
        }
        entry<Config> {
            ConfigScreen(xrayViewmodel) {
                when(it) {
                    is Home -> {
                        if (!xrayViewmodel.isServiceRunning.value) {
                            navigator.navigate(Home)
                        }
                    }
                    is Detail -> {
                        navigator.navigate(Detail(it.protocol,it.content))
                    }
                }
            }
        }
        entry<Logcat> {
            LogcatScreen(xrayViewmodel)
        }
        entry<Detail> { key ->
            DetailContainer(
                protocol = key.protocol,
                content = key.content,
                detailViewmodel = detailViewmodel
            )
        }
        entry<Settings> {
            SettingsContainer(settingsViewmodel)
        }

    }

    if (isLandScape) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // NavigationNail
//
//            XraySideNavOpt(
//                items = list_navigation,
//                currentScreen = current as NavigateDestination,
//                onItemSelected = { item ->
//                    navigator.navigate(item)
//                },
//                labelProvider = { item -> item.route },
//            )
            // Content
//            NavDisplay(
//                entries = navigationState.toEntries(entryProvider),
//                onBack = {navigator.goBack()},
//                sceneStrategy = remember { DialogSceneStrategy() },
//            )
            val backStack = rememberNavBackStack(Config,Home)
            val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
            Scaffold { paddingValues ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.padding(paddingValues = paddingValues),
                    onBack =  { backStack.removeLastOrNull() },
                    sceneStrategy = listDetailSceneStrategy,
                    entryProvider = entryProvider {
                        entry<Home>(
                            metadata = ListDetailSceneStrategy.detailPane()
                        ) {
                            HomeScreen(xrayViewmodel)
                        }
                        entry<Config>(
                            metadata = ListDetailSceneStrategy.listPane()
                        ) {
                            ConfigScreen(xrayViewmodel) {
                                backStack.addDetail(Home)
                            }
                        }
                    }
                )
            }
        }
    }else {

        Scaffold(
            bottomBar = {

                XrayBottomNavOpt(
                    items = list_navigation,
                    currentScreen = current as NavigateDestination,
                    onItemSelected = { item ->
                        navigator.navigate(item)
                    },
                    labelProvider = { item -> item.route },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) { innerPadding->

            NavDisplay(
                entries = navigationState.toEntries(entryProvider),
                onBack = {navigator.goBack()},
                sceneStrategy = remember { DialogSceneStrategy() },
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }

}

@Composable
fun HomeActionButton(
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    IconButton(
        onClick = {onSettingsClick()}
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
        DropdownMenuItem(
            text = {Text("delete All")},
            onClick = {
                expend = false
                xrayViewmodel.showDeleteDialog(/*delete all*/)
            }
        )
    }
}

@Deprecated("single Activity")
fun onSettingsClick(context: Context) {
    context.startActivity(Intent(context, SettingsActivity::class.java))
}
val popAnimationSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, // 弹性阻尼：中等回弹
    stiffness = Spring.StiffnessLow // 刚度：低（越低越慢越Q）
)
val subtleAnimSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
private fun NavBackStack<NavKey>.addDetail(detailRoute: Home) {

    // Remove any existing detail routes, then add the new detail route
    removeIf { it is Home }
    add(detailRoute)
}