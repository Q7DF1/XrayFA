package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.HomeComponent
import com.android.xrayfa.shared.ui.home.HomeTopBar
import com.android.xrayfa.shared.ui.nav.rememberFloatingNavClearance
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.transitions.TransitionDestinations
import com.android.xrayfa.shared.ui.transitions.sharedContainer

@Composable
internal fun HomeTabScreen(
    component: HomeComponent,
    onSettingsClick: () -> Unit,
) {
    val homeLabels = rememberHomeUiLabels()
    val platformHooks = LocalPlatformRootHooks.current
    val homeNavClearance = rememberFloatingNavClearance(extraAboveBar = 8.dp)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HomeTopBar(
                onSettingsClick = onSettingsClick,
                settingsIconModifier =
                    Modifier.sharedContainer(
                        destination = TransitionDestinations.SETTINGS,
                        shape = CircleShape,
                        containerColor = Color.Transparent,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        platformHooks.HomeSection(
            component = component,
            labels = homeLabels,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = homeNavClearance),
        )
    }
}
