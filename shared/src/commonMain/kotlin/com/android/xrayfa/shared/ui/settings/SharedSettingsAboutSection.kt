package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.platform.LocalPlatformRootHooks
import com.android.xrayfa.shared.platform.AppMetadataProvider
import com.android.xrayfa.shared.platform.REPO_URL
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform

@Composable
fun SharedSettingsAboutSection(
    component: SettingsComponent,
    modifier: Modifier = Modifier,
    labels: SettingsUiLabels = SettingsUiLabels(),
    appMetadataProvider: AppMetadataProvider = remember { KoinPlatform.getKoin().get() },
) {
    val state by component.state.subscribeAsState()
    val platformHooks = LocalPlatformRootHooks.current
    val appVersion = remember { appMetadataProvider.getAppVersion() }
    var versionTapCount by remember { mutableStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showUdpDnsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(versionTapCount) {
        if (versionTapCount > 0) {
            delay(2_000)
            versionTapCount = 0
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3_000)
            toastMessage = null
        }
    }

    SharedSettingsGroup(groupName = labels.aboutSectionTitle, modifier = modifier) {
        SharedSettingsFieldRow(
            title = labels.appVersionTitle,
            content = appVersion,
            icon = Icons.Outlined.Info,
            onClick = {
                if (!state.developerModeEnabled) {
                    versionTapCount++
                    if (versionTapCount in 5..6) {
                        toastMessage =
                            labels.developerModeTapCountHint.replace(
                                "{count}",
                                (7 - versionTapCount).toString(),
                            )
                    } else if (versionTapCount >= 7) {
                        versionTapCount = 0
                        toastMessage = labels.developerModeEnabledMessage
                        component.onEnableDeveloperMode()
                    }
                }
            },
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

    toastMessage?.let { message ->
        val bottomOffset = with(LocalDensity.current) { -80.dp.roundToPx() }
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(x = 0, y = bottomOffset),
            properties = PopupProperties(focusable = false),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (state.developerModeEnabled) {
        SharedSettingsGroup(groupName = labels.developerOptionsTitle) {
            SharedSettingsSwitchRow(
                title = labels.agentFunctionsTitle,
                description = if (platformHooks.agentFunctionsAvailable) labels.agentFunctionsDescription
                    else "${labels.inDevelopmentLabel} · ${labels.agentFunctionsDescription}",
                checked = platformHooks.agentFunctionsAvailable && state.agentFunctionsEnabled,
                enabled = platformHooks.agentFunctionsAvailable,
                onCheckedChange = component::onSetAgentFunctionsEnabled,
                icon = Icons.Outlined.Lock,
            )
            SharedSettingsSwitchRow(
                title = labels.enableDohTitle,
                description = labels.enableDohDescription,
                checked = state.dnsOverHttpsEnabled,
                onCheckedChange = component::onSetDnsOverHttpsEnabled,
                icon = Icons.Outlined.Dns,
            )
            if (!state.dnsOverHttpsEnabled) {
                SharedSettingsFieldRow(
                    title = labels.udpDnsServersTitle,
                    content = state.udpDnsServers,
                    description = labels.udpDnsServersDescription,
                    icon = Icons.Outlined.Dns,
                    onClick = { showUdpDnsDialog = true },
                )
            }
            TextButton(
                onClick = component::onExitDeveloperMode,
                modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp),
            ) {
                Text(labels.exitDeveloperMode)
            }
        }
    }

    if (showUdpDnsDialog) {
        SharedSettingsEditDialog(
            initialText = state.udpDnsServers,
            title = labels.udpDnsServersTitle,
            confirmText = labels.saveLabel,
            dismissText = labels.cancelLabel,
            validator = { SettingsValidators.validateUdpDnsServerList(it, labels) },
            onConfirm = { servers ->
                component.onSetUdpDnsServers(servers)
                showUdpDnsDialog = false
            },
            onDismiss = { showUdpDnsDialog = false },
        )
    }
}
