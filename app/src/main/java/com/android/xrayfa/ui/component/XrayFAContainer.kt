package com.android.xrayfa.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
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
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Navigator
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.ui.navigation.rememberNavigationState
import com.android.xrayfa.ui.navigation.toEntries
import com.android.xrayfa.ui.scene.XrayFASceneStrategy
import com.android.xrayfa.ui.scene.rememberXrayFASceneStrategy
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SubscriptionViewmodel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayFAContainer(
    xrayViewmodel: XrayViewmodel,
    detailViewmodel: DetailViewmodel,
    settingsViewmodel: SettingsViewmodel,
    subscriptViewmodel: SubscriptionViewmodel,
    appViewmodel: AppsViewmodel,
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
                navigator.navigate(it)
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
            SettingsContainer(settingsViewmodel) {
                navigator.navigate(it)
            }
        }
        entry<Subscription> {
            SubscriptionScreen(subscriptViewmodel) {
                navigator.navigate(Config)
            }
        }
        entry<Apps> {
            AppsScreen(appViewmodel)
        }

    }

    if (isLandScape) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {

            val backStack = rememberNavBackStack(
                Home, Settings
            )
            val left =  backStack.first()
            // NavigationNail
            XraySideNavOpt(
                items = list_navigation,
                currentScreen = left as NavigateDestination,
                onItemSelected = { item ->
                    backStack.addLeft(item)
                },
                labelProvider = { item -> item.route },
            )

            val sceneStrategy = rememberXrayFASceneStrategy<NavKey>()
            NavDisplay(
                    backStack = backStack,
                    onBack =  { backStack.removeLastOrNull() },
                    sceneStrategy = sceneStrategy,
                    entryProvider = entryProvider {
                        entry<Config>(
                            metadata = XrayFASceneStrategy.leftPane()
                        ) {
                            ConfigScreen(xrayViewmodel) {
                                backStack.addRight(it)
                            }
                        }
                        entry<Home>(
                            metadata = XrayFASceneStrategy.leftPane()
                        ) {
                            HomeScreen(xrayViewmodel) {
                                backStack.addRight(Settings)
                            }
                        }
                        entry<Logcat>(
                            metadata = XrayFASceneStrategy.leftPane()
                        ) {
                            LogcatScreen(xrayViewmodel)
                        }
                        entry<Detail>(
                            metadata = XrayFASceneStrategy.rightPane()
                        ) {
                            DetailContainer(it.protocol, it.content, detailViewmodel)
                        }
                        entry<Settings>(
                            metadata = XrayFASceneStrategy.rightPane()
                        ) {
                            SettingsContainer(settingsViewmodel) {
                            }
                        }
                        entry<Subscription>(
                            metadata = XrayFASceneStrategy.rightPane()
                        ) {
                            SubscriptionScreen(subscriptViewmodel) {
                            }
                        }
                        entry<Apps>(
                            metadata = XrayFASceneStrategy.rightPane()
                        ) {
                            AppsScreen(appViewmodel)
                        }
                    }
            )
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
    xrayViewmodel: XrayViewmodel,
    onNavigate: (NavigateDestination) -> Unit
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
        onDismissRequest = {expend = false},
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        DropdownMenuItem(
            text = {Text(stringResource(R.string.menu_subscription))},
            onClick = {
                expend = false
                onNavigate(Subscription)
                //xrayViewmodel.startSubscriptionActivity(context)
            }
        )
        DropdownMenuItem(
            text = {Text(stringResource(R.string.menu_delete_all))},
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
private fun NavBackStack<NavKey>.addRight(right: NavKey) {

    // Remove any existing detail routes, then add the new detail route
    if (size >= 2) {
        removeLast()
    }
    add(right)
}

private fun NavBackStack<NavKey>.addLeft(left: NavKey) {
    if (lastOrNull() == left) {
        return
    }
    removeAll(this)
    add(left)
    when(left) {
        is Home -> add(Settings)
        is Config -> add(Subscription)
    }
}