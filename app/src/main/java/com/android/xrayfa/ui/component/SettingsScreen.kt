package com.android.xrayfa.ui.component

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.viewmodel.FILE_TYPE_IP
import com.android.xrayfa.viewmodel.FILE_TYPE_SITE

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

    val geoIPDownloading by viewmodel.geoIPDownloading.collectAsState()
    val geoSiteDownloading by viewmodel.geoSiteDownloading.collectAsState()
    val importException by viewmodel.importException.collectAsState()
    val ipFilePickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                viewmodel.onSelectFile(context,uri, FILE_TYPE_IP)
            }
    }

    val domainFilePickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data?: return@rememberLauncherForActivityResult
                viewmodel.onSelectFile(context,uri, FILE_TYPE_SITE)
            }
        }
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
                    editInitValue = settingsState.socksPort.toString()
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
                    enable = settingsState.ipV6Enable,
                    onClick = {
                        if (settingsState.ipV6Enable) {
                            //todo
                        }
                    }
                )
                SettingsWithBtnBox(
                    title = R.string.geo_ip,
                    description = R.string.geo_ip_description,
                    downloading = geoIPDownloading,
                    onDownloadClick = {viewmodel.downloadGeoIP(context = context)},
                    onImportClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        ipFilePickLauncher.launch(intent)
                    }
                )
                SettingsWithBtnBox(
                    title = R.string.geo_site,
                    description = R.string.geo_site_description,
                    onDownloadClick = {viewmodel.downloadGeoSite(context)},
                    downloading = geoSiteDownloading,
                    onImportClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        domainFilePickLauncher.launch(intent)
                    }
                )
            }
            SettingsGroup(
                groupName = "other"
            ) {
                SettingsFieldBox(
                    title = R.string.repo_site,
                    content = stringResource(R.string.repo_description)
                ) {
                    viewmodel.openRepo(context)
                }
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
                    onDismiss = {
                        isShowEditDialog = false
                    }
                )
            }
        }
        ExceptionMessage(importException,stringResource(R.string.import_geo_failed))
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.8f)
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


@Composable
fun SettingsWithBtnBox(
    @StringRes title: Int,
    @StringRes description: Int,
    downloading: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onImportClick: () -> Unit = {}
) {

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (downloading) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.7f)
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
        Row(
            modifier = Modifier.weight(0.3f)
        ) {
            IconButton(
                onClick = onDownloadClick
            ) {
                Icon(
                    imageVector = if (!downloading)
                        ImageVector.vectorResource(R.drawable.ic_download)
                    else
                        Icons.Default.Refresh,
                    contentDescription = "download",
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(angle)
                )
            }
            IconButton(
                onClick = onImportClick
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_import),
                    contentDescription = "import",
                    modifier = Modifier.size(24.dp)
                )
            }
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
    var expand by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.7f)
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
        Row(
            modifier = Modifier
                .weight(0.3f)
                .padding(end = 8.dp)
        ) {

            TextButton(
                onClick = {
                    expand = !expand
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .weight(0.3f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = if (expand)
                            Icons.Default.KeyboardArrowUp
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = "dark mode"
                    )
                }
            }
            DropdownMenu(
                expanded = expand,
                onDismissRequest = {expand = false}
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
                            expand = false
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
    enable: Boolean = true,
    onClick: () ->Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enable
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                color = if (enable) MaterialTheme.colorScheme.onBackground else Color.Gray
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enable) MaterialTheme.colorScheme.onBackground else Color.Gray
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
        modifier = Modifier
            .fillMaxWidth()
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