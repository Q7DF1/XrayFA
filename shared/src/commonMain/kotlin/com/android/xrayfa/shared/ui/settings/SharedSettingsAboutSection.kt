package com.android.xrayfa.shared.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.platform.AppMetadataProvider
import com.android.xrayfa.shared.platform.REPO_URL
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.koin.mp.KoinPlatform

@Composable
fun SharedSettingsAboutSection(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
    labels: SettingsUiLabels = SettingsUiLabels(),
    appMetadataProvider: AppMetadataProvider = remember { KoinPlatform.getKoin().get() },
) {
    val state by component.state.subscribeAsState()
    val appVersion = remember { appMetadataProvider.getAppVersion() }

    SharedSettingsGroup(groupName = labels.aboutSectionTitle, modifier = modifier) {
        SharedSettingsFieldRow(
            title = labels.appVersionTitle,
            content = appVersion,
            icon = Icons.Outlined.Info,
            onClick = {},
        )
        SharedSettingsFieldRow(
            title = labels.hwidTitle,
            content = state.hwid.ifEmpty { labels.unknownLabel },
            icon = Icons.Outlined.Info,
            onClick = {},
        )
        SharedSettingsFieldRow(
            title = labels.xrayCoreVersionTitle,
            content = state.xrayCoreVersion,
            icon = Icons.Outlined.Info,
            onClick = {},
        )
        SharedSettingsFieldRow(
            title = labels.repoTitle,
            content = labels.repoDescription,
            icon = Icons.Outlined.Info,
            onClick = { appMetadataProvider.openUrl(REPO_URL) },
        )
    }
}
