package com.android.xrayfa.ui.component

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.ui.scene.XrayFASceneStrategy
import com.android.xrayfa.ui.scene.rememberXrayFASceneStrategy
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials


@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
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
    val density = LocalDensity.current
    var customNavBarHeightDp by remember { mutableStateOf(0.dp) }
//    // migrate to navigation 3
//    val navigationState = rememberNavigationState(
//        startRoute = Home,
//        topLevelRoutes = setOf(Home, Config, Logcat)
//    )
//    val navigator = remember { Navigator(navigationState) }
//    val current = navigationState.topLevelRoute
    val navBackStack = rememberNavBackStack(
        Home
    )

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
        val top = navBackStack.lastOrNull()
        val hazeState = remember { HazeState() }
        val showNavigationBar by xrayViewmodel.showNavigationBar.collectAsState()
        val isTopLevel = top in list_navigation
        val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<Home> { key ->
                HomeScreen(xrayViewmodel,bottomPadding = customNavBarHeightDp) {
                    navBackStack.routeTo(Settings)
                }
            }
            entry<Config> {
                ConfigScreen(xrayViewmodel, bottomPadding = customNavBarHeightDp) {
                    navBackStack.routeTo(it)
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
                    navBackStack.routeTo(it)
                }
            }
            entry<Subscription> {
                SubscriptionScreen(subscriptViewmodel) {
                    navBackStack.routeTo(Config)
                }
            }
            entry<Apps> {
                AppsScreen(appViewmodel)
            }

        }
        Box(
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            NavDisplay(
                backStack = navBackStack,
                entryProvider = entryProvider,
                onBack = {navBackStack.routeBack()},
                modifier = Modifier.hazeSource(state = hazeState)
            )
            AnimatedVisibility(
                // todo try another way(#182)
                visible = showNavigationBar && isTopLevel || top is Home,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onGloballyPositioned { coordinates ->
                        // Convert measured pixel height to Dp and update state
                        val heightPx = coordinates.size.height
                        customNavBarHeightDp = with(density) { heightPx.toDp() }
                    }
            ) {
                XrayBottomNavOpt(
                    items = list_navigation,
                    currentScreen = navBackStack.last() as NavigateDestination,
                    onItemSelected = { item ->
                        navBackStack.routeTo(item)
                    },
                    labelProvider = { item -> item.route },
                    modifier = Modifier
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
                        .padding(vertical = 3.dp)
                )
            }

            //XrayBottomNav(modifier = Modifier.align(Alignment.BottomCenter))
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

/**
 * change right content of screen  
 */
private fun NavBackStack<NavKey>.addRight(right: NavKey) {

    // Remove any existing detail routes, then add the new detail route
    if (size >= 2) {
        removeLast()
    }
    add(right)
}

/**
 * change right content of screen  
 */
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
private fun NavBackStack<NavKey>.routeTo(key: NavKey) {
    if (key in list_navigation) {
        removeAll(this)
    }
    add(key)
}

private fun NavBackStack<NavKey>.routeBack() {
    val nav = lastOrNull()
    if (nav in list_navigation) {
        if (nav is Home) {
            // exit the application
        }else {
            remove(nav)
            add(Home)
        }
        return
    }

    removeLastOrNull()

}