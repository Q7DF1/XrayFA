package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.resources.*
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.ui.settings.SharedSettingsAboutSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsGeneralSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsPlatformSection
import com.android.xrayfa.shared.ui.settings.SharedSettingsSubscriptionSection
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsTabScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    onAppsClick: () -> Unit,
    onLogcatClick: () -> Unit,
    onRouteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsLabels = rememberSettingsUiLabels()
    val platformHooks = LocalPlatformRootHooks.current

    SharedListScaffold(
        title = stringResource(Res.string.settings_title),
        modifier = modifier,
        lockCollapsedTitle = true,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = settingsLabels.cancelLabel,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
        ) {
            SharedSettingsGeneralSection(
                component = component,
                scrollEnabled = false,
                labels = settingsLabels,
                additionalGeneralContent = {
                    with(platformHooks) { SettingsGeneralExtras(component) }
                },
                additionalNetworkContent = {
                    with(platformHooks) { SettingsNetworkExtras(component) }
                },
            )
            SharedSettingsPlatformSection(
                labels = settingsLabels,
                onAppsClick = onAppsClick,
                onLogcatClick = onLogcatClick,
                onRouteClick = onRouteClick,
            )
            SharedSettingsSubscriptionSection(component = component, labels = settingsLabels)
            SharedSettingsAboutSection(component = component, labels = settingsLabels)
        }
    }
}
