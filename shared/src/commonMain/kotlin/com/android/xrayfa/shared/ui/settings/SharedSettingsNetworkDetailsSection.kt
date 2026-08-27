package com.android.xrayfa.shared.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.common.core.geoLiteDownloadEnabled
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.arkivanov.decompose.extensions.compose.subscribeAsState

private enum class NetworkEditField {
    HTTP_PORT,
    SOCKS_PORT,
    SOCKS_USERNAME,
    SOCKS_PASSWORD,
    DNS_IPV4,
    DNS_IPV6,
    DELAY_TEST_URL,
}

@Composable
fun SharedSettingsNetworkDetailsSection(
    component: SettingsComponent,
    labels: SettingsUiLabels = SettingsUiLabels(),
    modifier: Modifier = Modifier,
) {
    val state by component.state.subscribeAsState()
    val geoLite by component.geoLiteDownload.subscribeAsState()
    var editField by remember { mutableStateOf<NetworkEditField?>(null) }
    var editInitialValue by remember { mutableStateOf("") }

    fun openEdit(
        field: NetworkEditField,
        value: String,
    ) {
        editField = field
        editInitialValue = value
    }

    SharedSettingsFieldRow(
        title = labels.httpProxyPortTitle,
        content = state.httpPort.toString(),
        icon = Icons.Outlined.Numbers,
        enabled = state.lanHttpProxyEnable,
        onClick = { openEdit(NetworkEditField.HTTP_PORT, state.httpPort.toString()) },
        modifier = modifier,
    )
    SharedSettingsFieldRow(
        title = labels.socksPortTitle,
        content = state.socksPort.toString(),
        icon = Icons.Outlined.Numbers,
        onClick = { openEdit(NetworkEditField.SOCKS_PORT, state.socksPort.toString()) },
    )
    SharedSettingsFieldRow(
        title = labels.socksUsernameTitle,
        content = state.socksUserName,
        icon = Icons.Outlined.Person,
        onClick = { openEdit(NetworkEditField.SOCKS_USERNAME, state.socksUserName) },
    )
    SharedSettingsFieldRow(
        title = labels.socksPasswordTitle,
        content =
            if (state.socksPassword.isEmpty()) {
                ""
            } else {
                "••••••"
            },
        icon = Icons.Outlined.Password,
        onClick = { openEdit(NetworkEditField.SOCKS_PASSWORD, state.socksPassword) },
    )
    SharedSettingsFieldRow(
        title = labels.dnsIpv4Title,
        content = state.dnsIPv4,
        icon = Icons.Outlined.Dns,
        onClick = { openEdit(NetworkEditField.DNS_IPV4, state.dnsIPv4) },
    )
    SharedSettingsSwitchRow(
        title = labels.enableIpv6Title,
        description = labels.enableIpv6Description,
        checked = state.ipV6Enable,
        onCheckedChange = component::onSetIpV6Enable,
        icon = Icons.Outlined.NetworkCheck,
    )
    SharedSettingsFieldRow(
        title = labels.dnsIpv6Title,
        content = state.dnsIPv6,
        icon = Icons.Outlined.Dns,
        enabled = state.ipV6Enable,
        onClick = {
            if (state.ipV6Enable) {
                openEdit(NetworkEditField.DNS_IPV6, state.dnsIPv6)
            }
        },
    )

    SharedSettingsDownloadRow(
        title = labels.geoLiteTitle,
        description = labels.geoLiteDescription,
        installed = state.geoLiteInstall,
        downloading = geoLite.downloading,
        progress = geoLite.progress,
        downloadEnabled =
            geoLiteDownloadEnabled(
                geoLite.vpnConnected,
                geoLite.downloading,
                geoLite.downloadSupported,
            ),
        downloadDisabledHint =
            if (geoLite.downloadSupported) {
                labels.geoDownloadNeedServiceHint
            } else {
                labels.geoLiteDownloadUnavailableHint
            },
        onDownloadClick = component::onDownloadGeoLite,
        icon = Icons.Outlined.DataUsage,
    )
    SharedSettingsFieldRow(
        title = labels.delayTestUrlTitle,
        content = state.delayTestUrl,
        icon = Icons.Outlined.Speed,
        onClick = { openEdit(NetworkEditField.DELAY_TEST_URL, state.delayTestUrl) },
    )

    editField?.let { field ->
        val isNumeric = field == NetworkEditField.HTTP_PORT || field == NetworkEditField.SOCKS_PORT
        val validator: (String) -> String? =
            when (field) {
                NetworkEditField.HTTP_PORT,
                NetworkEditField.SOCKS_PORT,
                -> {
                    { input ->
                        SettingsValidators.validatePort(input, labels)
                    }
                }
                NetworkEditField.SOCKS_USERNAME -> {
                    { input ->
                        SettingsValidators.validateSocksCredential(input, labels, isPassword = false)
                    }
                }
                NetworkEditField.SOCKS_PASSWORD -> {
                    { input ->
                        SettingsValidators.validateSocksCredential(input, labels, isPassword = true)
                    }
                }
                NetworkEditField.DNS_IPV4 -> {
                    { input ->
                        SettingsValidators.validateIpv4List(input, labels)
                    }
                }
                NetworkEditField.DNS_IPV6 -> {
                    { input ->
                        SettingsValidators.validateIpv6List(input, labels)
                    }
                }
                NetworkEditField.DELAY_TEST_URL -> {
                    { input ->
                        SettingsValidators.validateNonEmpty(input, labels)
                    }
                }
            }

        SharedSettingsEditDialog(
            initialText = editInitialValue,
            title = labels.editDialogTitle,
            confirmText = labels.saveLabel,
            dismissText = labels.cancelLabel,
            isNumeric = isNumeric,
            validator = validator,
            onConfirm = { value ->
                when (field) {
                    NetworkEditField.HTTP_PORT ->
                        component.onSetHttpPort(value.toIntOrNull() ?: 10809)
                    NetworkEditField.SOCKS_PORT ->
                        component.onSetSocksPort(value.toIntOrNull() ?: 10808)
                    NetworkEditField.SOCKS_USERNAME -> component.onSetSocksUsername(value)
                    NetworkEditField.SOCKS_PASSWORD -> component.onSetSocksPassword(value)
                    NetworkEditField.DNS_IPV4 -> component.onSetDnsIPv4(value)
                    NetworkEditField.DNS_IPV6 -> component.onSetDnsIPv6(value)
                    NetworkEditField.DELAY_TEST_URL -> component.onSetDelayTestUrl(value)
                }
                editField = null
            },
            onDismiss = { editField = null },
        )
    }
}
