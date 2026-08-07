package com.android.xrayfa.shared.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SharedSettingsSubscriptionSection(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
    labels: SettingsUiLabels = SettingsUiLabels(),
) {
    val state by component.state.subscribeAsState()

    SharedSettingsGroup(groupName = labels.subscriptionSectionTitle, modifier = modifier) {
        SharedSettingsSwitchRow(
            title = labels.sendHwidTitle,
            description = labels.sendHwidDescription,
            checked = state.sendHwid,
            onCheckedChange = component::onSetSendHwid,
            icon = Icons.Outlined.Security,
        )
    }
}
