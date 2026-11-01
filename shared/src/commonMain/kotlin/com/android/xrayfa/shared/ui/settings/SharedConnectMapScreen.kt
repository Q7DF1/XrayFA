package com.android.xrayfa.shared.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.chrome.SharedListScaffold
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.arkivanov.decompose.extensions.compose.subscribeAsState

@Composable
fun SharedConnectMapScreen(
    component: SettingsComponent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = rememberSettingsUiLabels()
    val state by component.state.subscribeAsState()
    val cityDatabase by component.cityDatabase.subscribeAsState()
    val geoLite by component.geoLiteDownload.subscribeAsState()
    var showOriginPicker by remember { mutableStateOf(false) }

    SharedListScaffold(
        title = labels.connectMapTitle,
        modifier = modifier,
        lockCollapsedTitle = true,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.cancelLabel)
            }
        },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SharedSettingsGroup(groupName = labels.generalSectionTitle) {
                SharedSettingsSwitchRow(
                    title = labels.connectMapTitle,
                    description = labels.connectMapDescription,
                    checked = state.connectMapEnabled,
                    enabled = state.connectMapEnabled || cityDatabase.installed ||
                        (geoLite.downloadSupported && state.connectMapHttpExitEnabled),
                    onCheckedChange = component::onSetConnectMapEnabled,
                    icon = Icons.Outlined.Public,
                )
                if (geoLite.downloadSupported) SharedSettingsSwitchRow(
                    title = labels.mapHttpExitTitle,
                    description = labels.mapHttpExitDescription,
                    checked = state.connectMapHttpExitEnabled,
                    onCheckedChange = component::onSetConnectMapHttpExitEnabled,
                    icon = Icons.Outlined.Public,
                )
                SharedSettingsFieldRow(
                    title = labels.mapOriginTitle,
                    content = state.connectMapOrigin?.displayCoordinates() ?: labels.mapOriginUnset,
                    description = labels.mapOriginHint,
                    icon = Icons.Outlined.MyLocation,
                    trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    onClick = { showOriginPicker = true },
                )
                SharedSettingsDownloadRow(
                    title = labels.cityDatabaseTitle,
                    description = if (cityDatabase.failed) labels.cityDatabaseFailed else labels.cityDatabaseDescription,
                    installed = cityDatabase.installed,
                    downloading = cityDatabase.downloading,
                    progress = cityDatabase.progress,
                    downloadEnabled = geoLite.downloadSupported && geoLite.vpnConnected && !cityDatabase.downloading,
                    downloadDisabledHint = if (geoLite.downloadSupported) labels.geoDownloadNeedServiceHint
                        else labels.geoLiteDownloadUnavailableHint,
                    onDownloadClick = component::onDownloadCityDatabase,
                    onCancelClick = component::onCancelCityDatabaseDownload,
                    cancelLabel = labels.cancelLabel,
                    icon = Icons.Outlined.Public,
                )
            }
        }
    }
    if (showOriginPicker) {
        ConnectMapOriginDialog(
            origin = state.connectMapOrigin,
            labels = labels,
            onSave = {
                component.onSetConnectMapOrigin(it)
                showOriginPicker = false
            },
            onDismiss = { showOriginPicker = false },
        )
    }
}
