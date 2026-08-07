package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.ui.placeholder.PlaceholderScreen
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val pages by component.pages.subscribeAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                pages.items.forEachIndexed { index, page ->
                    val tab = page.configuration
                    val (icon, label) = tab.toNavItem()
                    NavigationBarItem(
                        selected = pages.selectedIndex == index,
                        onClick = { component.selectTab(index) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        ChildPages(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            pages = component.pages,
            onPageSelected = component::selectTab,
            scrollAnimation = PagesScrollAnimation.Default,
        ) { _, child ->
            when (child) {
                is RootComponent.Child.Home -> SharedHomeSection()
                is RootComponent.Child.Config -> PlaceholderScreen(child.component)
                is RootComponent.Child.Settings -> PlaceholderScreen(child.component)
            }
        }
    }
}

private fun RootTab.toNavItem(): Pair<ImageVector, String> =
    when (this) {
        RootTab.Config -> Icons.Default.Tune to "Config"
        RootTab.Home -> Icons.Default.Home to "Home"
        RootTab.Settings -> Icons.Default.Settings to "Settings"
    }
