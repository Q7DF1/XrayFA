package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.ConfigComponent
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.navigation.RootTab
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.config.SharedConfigImportMenu
import com.android.xrayfa.shared.ui.config.SharedConfigSection
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.nav.XrayFloatingNav
import com.android.xrayfa.shared.ui.settings.SharedSettingsGeneralSection
import com.arkivanov.decompose.extensions.compose.pages.ChildPages
import com.arkivanov.decompose.extensions.compose.pages.PagesScrollAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val pages by component.pages.subscribeAsState()
    val selectedTab = pages.items.getOrNull(pages.selectedIndex)?.configuration ?: RootTab.Home
    val showBottomNav = selectedTab != RootTab.Settings

    Box(modifier = modifier.fillMaxSize()) {
        ChildPages(
            modifier = Modifier.fillMaxSize(),
            pages = component.pages,
            onPageSelected = component::selectTab,
            scrollAnimation = PagesScrollAnimation.Default,
        ) { _, child ->
            when (child) {
                is RootComponent.Child.Home ->
                    HomeTabScreen(
                        component = child.component,
                        onSettingsClick = { component.selectTab(RootTab.Settings) },
                    )
                is RootComponent.Child.Config ->
                    ConfigTabScreen(
                        component = child.component,
                        onNodeSelectedNavigateHome = { component.selectTab(RootTab.Home) },
                    )
                is RootComponent.Child.Settings ->
                    SettingsTabScreen(
                        component = child.component,
                        onBack = { component.selectTab(RootTab.Home) },
                    )
            }
        }

        if (showBottomNav) {
            XrayFloatingNav(
                selectedTab =
                    when (selectedTab) {
                        RootTab.Config -> RootTab.Config
                        else -> RootTab.Home
                    },
                onTabSelected = component::selectTab,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigTabScreen(
    component: ConfigComponent,
    onNodeSelectedNavigateHome: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Config", fontWeight = FontWeight.Bold) },
                actions = {
                    SharedConfigImportMenu(
                        onImportFromClipboard = component::onImportFromClipboard,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        SharedConfigSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 88.dp),
            onNodeSelected = { node ->
                component.onSelectNode(node.id)
                onNodeSelectedNavigateHome()
            },
            onEmptyAddClick = component::onImportFromClipboard,
        )
    }
}

@Composable
private fun HomeTabScreen(
    component: com.android.xrayfa.shared.navigation.HomeComponent,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            HomeTopBar(
                title = "Home",
                onSettingsClick = onSettingsClick,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        SharedHomeSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 88.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTabScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        SharedSettingsGeneralSection(
            component = component,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        )
    }
}
