package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.shared.navigation.DefaultSettingsComponent
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SharedSettingsGeneralSection(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
    labels: SettingsUiLabels = SettingsUiLabels(),
    scrollEnabled: Boolean = true,
    additionalGeneralContent: @Composable ColumnScope.() -> Unit = {},
    additionalNetworkContent: @Composable ColumnScope.() -> Unit = {},
) {
    val state by component.state.subscribeAsState()
    val themeOptions =
        mapOf(
            Theme.LIGHT_MODE.code to labels.lightModeLabel,
            Theme.DARK_MODE.code to labels.darkModeLabel,
            Theme.AUTO_MODE.code to labels.autoModeLabel,
        )
    val selectedThemeLabel =
        themeOptions[state.darkMode] ?: labels.autoModeLabel
    val lanSocksEnabled =
        state.socksListen == DefaultSettingsComponent.LAN_PROXY_LISTEN_ADDRESS

    val columnModifier =
        if (scrollEnabled) {
            modifier.verticalScroll(rememberScrollState())
        } else {
            modifier
        }

    androidx.compose.foundation.layout.Column(
        modifier = columnModifier,
    ) {
        SharedSettingsGroup(groupName = labels.generalSectionTitle) {
            SharedSettingsSelectRow(
                title = labels.themeTitle,
                description = labels.themeDescription,
                selectedLabel = selectedThemeLabel,
                options = themeOptions,
                onSelected = component::onSetTheme,
                icon = Icons.Outlined.Palette,
            )
            SharedSettingsSwitchRow(
                title = labels.bootAutoStartTitle,
                description = labels.bootAutoStartDescription,
                checked = state.bootAutoStart,
                onCheckedChange = component::onSetBootAutoStart,
                icon = Icons.Outlined.PowerSettingsNew,
            )
            SharedSettingsSwitchRow(
                title = labels.agentFunctionsTitle,
                description = labels.agentFunctionsDescription,
                checked = state.agentFunctionsEnabled,
                onCheckedChange = component::onSetAgentFunctionsEnabled,
                icon = Icons.Outlined.Lock,
            )
            SharedSettingsSwitchRow(
                title = labels.hideFromRecentsTitle,
                description = labels.hideFromRecentsDescription,
                checked = state.hideFromRecents,
                onCheckedChange = component::onSetHideFromRecents,
                icon = Icons.Outlined.VisibilityOff,
            )
            additionalGeneralContent()
        }

        SharedSettingsGroup(groupName = labels.networkSectionTitle) {
            SharedSettingsSwitchRow(
                title = labels.lanSocksProxyTitle,
                description = labels.lanSocksProxyDescription,
                checked = lanSocksEnabled,
                onCheckedChange = component::onSetLanSocksProxyEnable,
                icon = Icons.Outlined.Router,
            )
            SharedSettingsSwitchRow(
                title = labels.lanHttpProxyTitle,
                description = labels.lanHttpProxyDescription,
                checked = state.lanHttpProxyEnable,
                onCheckedChange = component::onSetLanHttpProxyEnable,
                icon = Icons.Outlined.Public,
            )
            SharedSettingsNetworkDetailsSection(
                component = component,
                labels = labels,
            )
            additionalNetworkContent()
        }
    }
}
