package com.android.xrayfa.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.Config
import com.android.xrayfa.ui.navigation.Home
import com.android.xrayfa.ui.navigation.list_navigation
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.R
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.ui.navigation.Detail
import com.android.xrayfa.ui.navigation.Edit
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.ui.navigation.Subscription
import com.android.xrayfa.ui.scene.XrayFASceneStrategy
import com.android.xrayfa.ui.scene.rememberXrayFASceneStrategy
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import kotlin.collections.listOf


import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.ui.navigation.ScanQR
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayFAContainer(
    xrayViewmodel: XrayViewmodel,
    detailViewmodel: DetailViewmodel,
    settingsViewmodel: SettingsViewmodel,
    subscriptViewmodel: SubscriptionViewmodel,
    appViewmodel: AppsViewmodel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var customNavBarHeightDp by remember { mutableStateOf(0.dp) }

    val navBackStack = rememberNavBackStack(
        Home
    )

    val top = navBackStack.lastOrNull()
    val showNavigationBar by xrayViewmodel.showNavigationBar.collectAsState()
    val isTopLevel = top in list_navigation
    val pendingRoute by xrayViewmodel.pendingRoute.collectAsState()

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navBackStack.routeTo(route)
            xrayViewmodel.setPaddingRoute(null) // Reset after navigation
        }
    }

    // 1. Pager State for Top Level Navigation
    val pagerState = rememberPagerState(
        initialPage = list_navigation.indexOf(Home).coerceAtLeast(0),
        pageCount = { list_navigation.size }
    )

    // Sync Pager -> NavBackStack for instant gesture feedback
    LaunchedEffect(pagerState.currentPage) {
        val target = list_navigation[pagerState.currentPage]
        if (top in list_navigation && top != target) {
            navBackStack.routeTo(target)
        }
    }

    // Sync NavBackStack -> Pager for click navigation
    LaunchedEffect(top) {
        if (top in list_navigation) {
            val targetPage = list_navigation.indexOf(top)
            if (targetPage != -1 && pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    Box(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        SharedTransitionLayout {
            NavDisplay(
                backStack = navBackStack,
                onBack = {navBackStack.routeBack()},
                sceneStrategies = listOf(rememberXrayFASceneStrategy()),
                sharedTransitionScope = this,
                entryProvider = { key ->
                    when(key) {
                        in list_navigation -> NavEntry(
                            key = Home,
                            metadata = metadata {
                                put(NavDisplay.TransitionKey) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            }
                        ) {
                            val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = isTopLevel
                            ) { page ->
                                when (list_navigation[page]) {
                                    is Home -> HomeScreen(
                                        xrayViewmodel = xrayViewmodel,
                                        bottomPadding = customNavBarHeightDp,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    ) { navBackStack.routeTo(Settings) }

                                    is Config -> ConfigScreen(
                                        xrayViewmodel = xrayViewmodel,
                                        bottomPadding = customNavBarHeightDp,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    ) { navBackStack.routeTo(it) }
                                }
                            }
                        }
                        is Apps -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.subscreen()
                        ) {
                            AppsScreen(
                                viewmodel = appViewmodel,
                                sharedTransitionScope = this@SharedTransitionLayout
                            )
                        }

                        is Settings -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.settings()
                        ) {
                            SettingsScreen(
                                viewmodel = settingsViewmodel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                            ) { navBackStack.routeTo(it) }
                        }
                        is Logcat -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.subscreen()
                        ) {
                            LogcatScreen(
                                viewmodel = xrayViewmodel,
                                sharedTransitionScope = this@SharedTransitionLayout
                            )
                        }

                        is Subscription -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.subscription()
                        ) {
                            SubscriptionScreen(subscriptViewmodel) {
                                navBackStack.routeTo(Config)
                            }
                        }
                        is Edit -> NavEntry(key) { 
                            EditScreen(
                                detailViewmodel = detailViewmodel,
                                onBack = { navBackStack.routeBack() },
                                sharedTransitionScope = this@SharedTransitionLayout
                            ) 
                        }
                        is Detail -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.detail()
                        ) {
                            EditScreen(
                                nodeId = key.id,
                                remark = key.remark,
                                protocol = key.protocol,
                                initialContent = key.content,
                                detailViewmodel = detailViewmodel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                onBack = { navBackStack.routeBack() }
                            )
                        }
                        is ScanQR -> NavEntry(
                            key = key,
                            metadata = metadata {
                                put(NavDisplay.TransitionKey) {
                                    slideInHorizontally {it} togetherWith slideOutHorizontally {-it}
                                }
                                // Transition when navigating AWAY from this screen (Popping back)
                                put(NavDisplay.PopTransitionKey) {
                                    slideInHorizontally {-it} togetherWith slideOutHorizontally {it}
                                }
                            }
                        ) {
                            QRCodeScannerScreen(
                                onBack = {navBackStack.routeBack()},
                                onResult = { code ->
                                    key.onResult(code)
                                    navBackStack.routeBack()
                                }
                            )
                        }
                        is RouteSettings -> NavEntry(
                            key = key,
                            metadata = XrayFASceneStrategy.subscreen()
                        ) {
                            RouteSettingsScreen(sharedTransitionScope = this@SharedTransitionLayout)
                        }
                        else -> NavEntry(key) { Text("Unknown route") }
                    }
                },
                predictivePopTransitionSpec = { _ ->
                    EnterTransition.None togetherWith ExitTransition.None
                }
            )
        }

        AnimatedVisibility(
            visible = showNavigationBar && isTopLevel || top is Home,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coordinates ->
                    customNavBarHeightDp = with(density) { coordinates.size.height.toDp() }
                }
        ) {
            val currentDestination = if (isTopLevel) {
                list_navigation[pagerState.currentPage]
            } else {
                (top as? NavigateDestination) ?: list_navigation[pagerState.currentPage]
            }

            XrayModernFloatingNav(
                items = list_navigation,
                currentScreen = currentDestination,
                onItemSelected = { item ->
                    if (pagerState.isScrollInProgress) return@XrayModernFloatingNav
                    
                    if (navBackStack.lastOrNull() == item) {
                        val targetPage = list_navigation.indexOf(item)
                        if (targetPage != -1 && pagerState.currentPage != targetPage) {
                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                        }
                    } else {
                        navBackStack.routeTo(item)
                    }
                },
                labelProvider = { item -> item.route },
                modifier = Modifier
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
//                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
                    .padding(vertical = 3.dp)
            )
        }

        //XrayBottomNav(modifier = Modifier.align(Alignment.BottomCenter))
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
        offset = DpOffset(x = (-8).dp,y = 0.dp),
        shape = RoundedCornerShape(12.dp),
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


private fun NavBackStack<NavKey>.routeTo(key: NavKey) {
    if (lastOrNull() == key) {
        return
    }

    if (key in list_navigation) {
        removeAll(this)
    }else {
        if (contains(key)) {
            remove(key)
        }
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