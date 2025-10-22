package com.android.xrayfa.ui.component

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.xrayfa.R
import com.android.xrayfa.viewmodel.SettingsViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.repository.SettingsKeys

@Composable
fun SettingsScreen(
    viewmodel: SettingsViewmodel,
    modifier: Modifier
) {
    val settingsState by viewmodel.settingsState.collectAsState()
    val context = LocalContext.current
    var isShowEditDialog by remember { mutableStateOf(false) }
    var editInitValue by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(SettingsKeys.SOCKS_PORT) }
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingsGroup(
                groupName = "general"
            ) {
                SettingsSelectBox(
                    title = R.string.dark_mode,
                    description = R.string.dark_mode_description,
                    onSelected = { mode ->
                        viewmodel.setDarkMode(mode)
                    },
                    selected = when(settingsState.darkMode) {
                        0 -> stringResource(R.string.light_mode)
                        1 -> stringResource(R.string.dark_mode)
                        2 -> stringResource(R.string.auto_mode)
                        else -> stringResource(R.string.auto_mode)
                    },
                    options = mapOf(
                        0 to stringResource(R.string.light_mode),
                        1 to stringResource(R.string.dark_mode),
                        2 to stringResource(R.string.auto_mode)
                    )
                )
                SettingsFieldBox(
                    title = R.string.allow_app_settings,
                    content = stringResource(R.string.select_app_settings)
                ) {
                    viewmodel.startAppsActivity(context)
                }
            }

            SettingsGroup(
                groupName = "network"
            ) {

                SettingsFieldBox(
                    title = R.string.socks_port,
                    content = settingsState.socksPort.toString()
                ) {
                    isShowEditDialog = true
                    editType = SettingsKeys.SOCKS_PORT
                }
                SettingsFieldBox(
                    title = R.string.dns_ipv4,
                    content = settingsState.dnsIPv4
                ) { }
                SettingsCheckBox(
                    title = R.string.enable_ipv6,
                    description = R.string.enable_ipv6_description,
                    checked = settingsState.ipV6Enable,
                    onCheckedChange = { checked->
                        viewmodel.setIpV6Enable(checked)
                    }
                )
                SettingsFieldBox(
                    title = R.string.dns_ipv6,
                    content = settingsState.dnsIPv6,
                    onClick = {
                        if (settingsState.ipV6Enable) {
                            //todo
                        }
                    }
                )
            }
            if (isShowEditDialog) {
                EditTextDialog(
                    initialText = editInitValue,
                    isNumeric = true,
                    onConfirm = {
                        when(editType) {
                            SettingsKeys.SOCKS_PORT ->
                                viewmodel.setSocksPort(it.toIntOrNull()?:10808)
                        }
                        isShowEditDialog = false
                    },
                    onDismiss = {isShowEditDialog = false}
                )
            }
        }
    }
}

@Composable
fun SettingsCheckBox(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Box(
            modifier = Modifier.weight(0.2f)
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSelectBox(
    @StringRes title: Int,
    @StringRes description: Int,
    onSelected: (Int) -> Unit = {},
    selected: String = "dark",
    options: Map<Int,String> = mapOf()
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                expanded = it
            },
            modifier = Modifier.weight(0.2f)
                .padding(end = 8.dp)
        ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        expanded = !expanded
                    }
                        .clip(RoundedCornerShape(50))
                ) {
                    Text(
                        text = selected,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = "dark mode"
                    )
                }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {expanded = false}
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.value
                            )
                        },
                        onClick = {
                            onSelected(option.key)
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun SettingsFieldBox(
    @StringRes title: Int,
    content: String,
    onClick: () ->Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable{onClick()},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@Composable
fun SettingsGroup(
    groupName: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 8.dp)
    ) {
        Text(
            text = groupName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
@Preview
fun SettingsFieldBoxPreview() {
    SettingsFieldBox(
        R.string.enable_ipv6,
        "192.168.0.1",
    ) {
        //empty
    }
}

@Composable
@Preview
fun SettingsSelectBoxPreview() {
    SettingsSelectBox(
        R.string.delete,
        R.string.delete_notify
    )
}

@Composable
@Preview
fun SettingsCheckBoxPreview() {
    SettingsCheckBox(
        R.string.cancel,
        R.string.save
    )
}