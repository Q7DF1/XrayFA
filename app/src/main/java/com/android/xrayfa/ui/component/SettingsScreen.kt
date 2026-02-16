package com.android.xrayfa.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.viewmodel.GEOFileType
import com.android.xrayfa.viewmodel.GEOFileType.Companion.FILE_TYPE_IP

@Composable
fun SettingsScreen(
    viewmodel: SettingsViewmodel,
    onNavigate: (NavigateDestination) -> Unit,
    modifier: Modifier
) {
    val settingsState by viewmodel.settingsState.collectAsState()
    val context = LocalContext.current
    var isShowEditDialog by remember { mutableStateOf(false) }
    var editInitValue by remember { mutableStateOf("") }
    var editType: Preferences.Key<*> by remember { mutableStateOf(SettingsKeys.SOCKS_PORT) }
    var validator: (String)->String? by remember { mutableStateOf({null}) }
    val geoIPDownloading by viewmodel.geoIPDownloading.collectAsState()
    val geoSiteDownloading by viewmodel.geoSiteDownloading.collectAsState()
    val geoLiteDownloading by viewmodel.geoLiteDownloading.collectAsState()
    val importException by viewmodel.importException.collectAsState()
    val downloadException by viewmodel.downloadException.collectAsState()
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
                viewmodel.onSelectFile(context,uri, GEOFileType.FILE_TYPE_SITE)
            }
        }
    val scrollState = rememberScrollState()
    val packageName = context.packageName
    val pm = context.packageManager
    val packageInfo = pm.getPackageInfo(packageName, 0)
    val versionName = packageInfo.versionName?:"unknown"
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            SettingsGroup(
                groupName = stringResource(R.string.general_part)
            ) {
                SettingsSelectBox(
                    title = R.string.theme_select,
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
                    //viewmodel.startAppsActivity(context)
                    onNavigate(Apps)
                }
                SettingsFieldBox(
                    title = R.string.logcat,
                    content = stringResource(R.string.logcat_desc)
                ) {
                    onNavigate(Logcat)
                }
                if (NotificationHelper.canPostPromotionsEnabled(LocalContext.current)) {
                    SettingsCheckBox(
                        title = R.string.live_update_notification,
                        description = R.string.live_update_notification_desc,
                        checked = settingsState.liveUpdateNotification,
                        onCheckedChange = { checked->
                            viewmodel.setLiveUpdateNotification(checked)
                        }
                    )
                }
            }

            SettingsGroup(
                groupName = stringResource(R.string.network_part)
            ) {

                SettingsFieldBox(
                    title = R.string.socks_port,
                    content = settingsState.socksPort.toString()
                ) {
                    editInitValue = settingsState.socksPort.toString()
                    isShowEditDialog = true
                    editType = SettingsKeys.SOCKS_PORT
                    validator = {
                        if (it.isBlank()) context.getString(R.string.can_not_be_empty) else null
                    }
                }
                SettingsFieldBox(
                    title = R.string.dns_ipv4,
                    content = settingsState.dnsIPv4
                ) {
                    editInitValue = settingsState.dnsIPv4
                    isShowEditDialog = true
                    editType = SettingsKeys.DNS_IPV4
                    validator = {validateIpv4List(it,context)}
                }
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
                            editInitValue = settingsState.dnsIPv6
                            isShowEditDialog = true
                            editType = SettingsKeys.DNS_IPV6
                            validator = {validateIpv6List(it,context)}
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
                SettingsWithBtnBox(
                    title = R.string.geo_lite_title,
                    description = R.string.geo_ip_lite_description,
                    onDownloadClick = {viewmodel.downloadGeoLite(context)},
                    downloading = geoLiteDownloading,
                    enable = settingsState.geoLiteInstall
                )
                SettingsFieldBox(
                    title = R.string.test_url,
                    content = settingsState.delayTestUrl
                ) {
                    //todo: domain validator
                    editInitValue = settingsState.delayTestUrl
                    isShowEditDialog = true
                    editType = SettingsKeys.DELAY_TEST_URL
                    validator = {
                        if (it.isBlank()) context.getString(R.string.can_not_be_empty) else null
                    }
                }
            }
            SettingsGroup(
                groupName = stringResource(R.string.about_part)
            ) {

                SettingsFieldBox(
                    title = R.string.xrayfa_version,
                    content = versionName,
                    icon = Icons.Default.Refresh
                ) {
                    // check version

                }

                SettingsFieldBox(
                    title = R.string.xray_core_version,
                    content = settingsState.xrayCoreVersion
                ) {
                }
                SettingsFieldBox(
                    title = R.string.repo_site,
                    content = stringResource(R.string.repo_description),
                    icon = ImageVector.vectorResource(R.drawable.ic_github)
                ) {
                    viewmodel.openRepo(context)
                }
            }
            if (isShowEditDialog) {
                EditTextDialog(
                    title = stringResource(R.string.edit),
                    dismissText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.save),
                    initialText = editInitValue,
                    isNumeric = editType.name == SettingsKeys.SOCKS_PORT.name,
                    validator = validator,
                    onConfirm = {
                        when(editType.name) {
                            SettingsKeys.SOCKS_PORT.name ->
                                viewmodel.setSocksPort(it.toIntOrNull()?:10808)

                            SettingsKeys.DNS_IPV4.name ->
                                viewmodel.setDnsIpV4(it)

                            SettingsKeys.DNS_IPV6.name ->
                                viewmodel.setDnsIpV6(it)

                            SettingsKeys.DELAY_TEST_URL.name ->
                                viewmodel.setDelayTestUrl(it)
                        }
                        isShowEditDialog = false
                    },
                    onDismiss = {
                        isShowEditDialog = false
                    }
                )
            }
        }
        ExceptionMessage(
            shown = importException || downloadException,
            msg = if (importException)
                stringResource(R.string.import_geo_failed)
            else
            stringResource(R.string.download_geo_failed)
        )
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
    onImportClick: (() -> Unit)? = null,
    enable: Boolean = true
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
            .clickable {}, //todo optional: download url
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
                color = if (enable) MaterialTheme.colorScheme.onBackground else Color.Gray
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enable) MaterialTheme.colorScheme.onBackground else Color.Gray
            )
        }
        Row(
            modifier = Modifier.weight(0.3f),
            horizontalArrangement = Arrangement.Center
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
            if (onImportClick != null) {
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
                onDismissRequest = {expand = false},
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.value,
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {
                            onSelected(option.key)
                            expand = false
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
    icon: ImageVector? = null,
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
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = "",
                modifier = Modifier.size(24.dp)
                    .weight(0.2f)
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


/**
 * Validate one or multiple IPv4 addresses separated by commas.
 *
 * @param input The input string (e.g., "192.168.0.1" or "8.8.8.8, 1.2.3.4")
 * @return null if all addresses are valid; otherwise a warning message.
 */
fun validateIpv4List(input: String,context: Context): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return context.getString(R.string.err_ipv4_empty)

    // Strict IPv4 regex matching 0-255 for each segment
    val ipv4Regex = Regex("""^(?:25[0-5]|2[0-4]\d|1?\d{1,2})(?:\.(?:25[0-5]|2[0-4]\d|1?\d{1,2})){3}$""")

    val parts = trimmed.split(",")
    if (parts.isEmpty()) return context.getString(R.string.err_ipv4_empty)

    val seen = mutableSetOf<String>()
    for ((index, raw) in parts.withIndex()) {
        val part = raw.trim()

        // Empty element (e.g. "1.1.1.1,,2.2.2.2")
        if (part.isEmpty()) {
            return context.getString(R.string.err_ipv4_item_empty,index + 1)
        }

        // IPv4 format check
        if (!ipv4Regex.matches(part)) {
            return context.getString(R.string.err_ipv4_invalid,index + 1 , part)
        }

        // Duplicate check
        if (!seen.add(part)) {
            return context.getString(R.string.err_ipv4_duplicate,index + 1 ,part)
        }
    }

    // All valid
    return null
}

/**
 * Validate one or multiple IPv6 addresses separated by commas.
 *
 * @param input The input string (e.g., "2001:0db8::1" or "fe80::1, 2001:db8::2")
 * @return null if all addresses are valid; otherwise a warning message.
 */
fun validateIpv6List(input: String, context: Context): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return context.getString(R.string.err_ipv6_empty)

    // IPv6 regex (simple, covers standard forms and shorthand)
    val ipv6Regex = Regex(
        // IPv6 pattern simplified to allow most valid IPv6 forms including ::
        "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|"+
                "([0-9a-fA-F]{1,4}:){1,7}:|"+
                "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|"+
                "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|"+
                "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|"+
                "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|"+
                "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|"+
                "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|"+
                ":((:[0-9a-fA-F]{1,4}){1,7}|:)|"+
                "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|"+
                "::(ffff(:0{1,4}){0,1}:){0,1}"+
                "((25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])\\.){3,3}"+
                "(25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])|"+
                "([0-9a-fA-F]{1,4}:){1,4}:"+
                "((25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9])\\.){3,3}"+
                "(25[0-5]|(2[0-4]|1{0,1}[0-9])?[0-9]))$"
    )

    val parts = trimmed.split(",")
    if (parts.isEmpty()) return context.getString(R.string.err_ipv6_empty)

    val seen = mutableSetOf<String>()
    for ((index, raw) in parts.withIndex()) {
        val part = raw.trim()

        // Empty element (e.g. "2001::1,,fe80::1")
        if (part.isEmpty()) {
            return context.getString(R.string.err_ipv6_item_empty, index + 1)
        }

        // IPv6 format check
        if (!ipv6Regex.matches(part)) {
            return context.getString(R.string.err_ipv6_invalid, index + 1, part)
        }

        // Duplicate check
        if (!seen.add(part)) {
            return context.getString(R.string.err_ipv6_duplicate, index + 1, part)
        }
    }

    // All valid
    return null
}