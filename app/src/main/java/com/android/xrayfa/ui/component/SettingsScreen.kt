package com.android.xrayfa.ui.component

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.xrayfa.R
import com.android.xrayfa.viewmodel.SettingsViewmodel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.android.xrayfa.common.repository.SettingsKeys
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.ui.navigation.Apps
import com.android.xrayfa.ui.navigation.Logcat
import com.android.xrayfa.ui.navigation.NavigateDestination
import com.android.xrayfa.ui.navigation.RouteSettings
import com.android.xrayfa.ui.navigation.Settings
import com.android.xrayfa.viewmodel.GEOFileType
import com.android.xrayfa.viewmodel.GEOFileType.Companion.FILE_TYPE_IP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewmodel: SettingsViewmodel,
    sharedTransitionScope: SharedTransitionScope,
    onNavigate: (NavigateDestination) -> Unit,
) {


    val scrollState = rememberScrollState()
    // Observe the overlap fraction to determine if the list is scrolled
    val isScrolled by remember {
        derivedStateOf { scrollState.value > 0 }
    }

    val currentTopbarColor = if (isScrolled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.background
    }

    // Animate the shadow elevation for a smooth transition
    val appBarElevation by animateDpAsState(
        targetValue = if (isScrolled) 4.dp else 0.dp,
        label = "TopBarShadowElevation"
    )

    val settingsState by viewmodel.settingsState.collectAsState()
    val context = LocalContext.current
    var isShowEditDialog by remember { mutableStateOf(false) }
    var editInitValue by remember { mutableStateOf("") }
    var editType: Preferences.Key<*> by remember { mutableStateOf(SettingsKeys.SOCKS_PORT) }
    var validator : (String)->String? by remember { mutableStateOf({null}) }
    val geoIPDownloading by viewmodel.geoIPDownloading.collectAsState()
    val geoIPProgress by viewmodel.geoIPProgress.collectAsState()
    val geoSiteDownloading by viewmodel.geoSiteDownloading.collectAsState()
    val geoSiteProgress by viewmodel.geoSiteProgress.collectAsState()
    val geoLiteDownloading by viewmodel.geoLiteDownloading.collectAsState()
    val geoLiteProgress by viewmodel.geoLiteProgress.collectAsState()
    val importException by viewmodel.importException.collectAsState()
    val downloadException by viewmodel.downloadException.collectAsState()
    val xrayFaDownloading by viewmodel.xrayFaDownloading.collectAsState()
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
    val packageName = context.packageName
    val pm = context.packageManager
    val packageInfo = pm.getPackageInfo(packageName, 0)
    val versionName = packageInfo.versionName?:"unknown"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    with(sharedTransitionScope) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "settings",
                                modifier = Modifier.sharedElement(
                                    sharedTransitionScope.rememberSharedContentState(key = Settings.route),
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                )
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = currentTopbarColor,
                    scrolledContainerColor = currentTopbarColor
                ),
                modifier = Modifier.shadow(appBarElevation)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
                SettingsGroup(
                    groupName = stringResource(R.string.general_part)
                ) {
                    SettingsSelectBox(
                        title = R.string.theme_select,
                        description = R.string.dark_mode_description,
                        icon = Icons.Outlined.Palette,
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
                    with(sharedTransitionScope) {
                        SettingsFieldBox(
                            title = R.string.allow_app_settings,
                            content = stringResource(R.string.select_app_settings),
                            icon = Icons.Outlined.Apps,
                            trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = Apps.route),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            )
                        ) {
                            //viewmodel.startAppsActivity(context)
                            onNavigate(Apps)
                        }
                    }

                    with(sharedTransitionScope) {
                        SettingsFieldBox(
                            title = R.string.logcat,
                            content = stringResource(R.string.logcat_desc),
                            icon = Icons.Outlined.BugReport,
                            trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = Logcat.route),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current
                            )
                        ) {
                            onNavigate(Logcat)
                        }
                    }

                    SettingsCheckBox(
                        title = R.string.boot_auto_start,
                        description = R.string.boot_auto_start_desc,
                        icon = Icons.Outlined.PowerSettingsNew,
                        checked = settingsState.bootAutoStart,
                        onCheckedChange = { checked ->
                            viewmodel.setEnableBootAutoStart(checked)
                        }
                    )
                    SettingsCheckBox(
                        title = R.string.hide_from_recents_title,
                        description = R.string.hide_from_recents_desc,
                        icon = Icons.Outlined.VisibilityOff,
                        checked = settingsState.hideFromRecents,
                        onCheckedChange = { checked ->
                            viewmodel.setHideFromRecents(checked)
                        }
                    )
                    if (NotificationHelper.canPostPromotionsEnabled(LocalContext.current)) {
                        SettingsCheckBox(
                            title = R.string.live_update_notification,
                            description = R.string.live_update_notification_desc,
                            icon = Icons.Outlined.NotificationsActive,
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
                    SettingsSelectBox(
                        title = R.string.socks_address_listen_title,
                        description = R.string.socks_address_listen_desc,
                        icon = Icons.Outlined.Router,
                        onSelected = { mode ->
                            when(mode) {
                                0 -> viewmodel.setSocksListen("127.0.0.1")
                                1 -> viewmodel.setSocksListen("0.0.0.0")
                            }
                        },
                        selected = settingsState.socksListen,
                        options = mapOf(
                            0 to "127.0.0.1",
                            1 to "0.0.0.0"
                        )
                    )
                    SettingsFieldBox(
                        title = R.string.socks_port,
                        content = settingsState.socksPort.toString(),
                        icon = Icons.Outlined.Numbers
                    ) {
                        editInitValue = settingsState.socksPort.toString()
                        isShowEditDialog = true
                        editType = SettingsKeys.SOCKS_PORT
                        validator = {
                            if (it.isBlank()) context.getString(R.string.can_not_be_empty) else null
                        }
                    }
                    SettingsFieldBox(
                        title = R.string.socks_username_title,
                        content = settingsState.socksUserName,
                        icon = Icons.Outlined.Person
                    ) {
                        editInitValue = settingsState.socksUserName
                        isShowEditDialog = true
                        editType = SettingsKeys.SOCKS_USERNAME
                        validator = {validateSocks(it,context,false)}
                    }
                    SettingsFieldBox(
                        title = R.string.socks_password_title,
                        content = settingsState.socksPassword,
                        icon = Icons.Outlined.Password
                    ) {
                        editInitValue = settingsState.socksPassword
                        isShowEditDialog = true
                        editType = SettingsKeys.SOCKS_PASSWORD
                        validator = {validateSocks(it,context,true)}
                    }

                    SettingsFieldBox(
                        title = R.string.dns_ipv4,
                        content = settingsState.dnsIPv4,
                        icon = Icons.Outlined.Dns
                    ) {
                        editInitValue = settingsState.dnsIPv4
                        isShowEditDialog = true
                        editType = SettingsKeys.DNS_IPV4
                        validator = {validateIpv4List(it,context)}
                    }
                    SettingsCheckBox(
                        title = R.string.enable_ipv6,
                        description = R.string.enable_ipv6_description,
                        icon = Icons.Outlined.NetworkCheck,
                        checked = settingsState.ipV6Enable,
                        onCheckedChange = { checked->
                            viewmodel.setIpV6Enable(checked)
                        }
                    )
                    SettingsFieldBox(
                        title = R.string.dns_ipv6,
                        content = settingsState.dnsIPv6,
                        icon = Icons.Outlined.Dns,
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
                    with(sharedTransitionScope) {
                        SettingsFieldBox(
                            title = R.string.route_settings_title,
                            content = stringResource(R.string.route_settings_desc),
                            icon = Icons.Outlined.Route,
                            trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            modifier = Modifier.sharedElement(
                                sharedTransitionScope.rememberSharedContentState(key = RouteSettings.route),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            )
                        ) {
                            onNavigate(RouteSettings)
                        }
                    }
                    SettingsWithBtnBox(
                        title = R.string.geo_ip,
                        description = R.string.geo_ip_description,
                        icon = Icons.Outlined.Language,
                        downloading = geoIPDownloading,
                        progress = geoIPProgress,
                        onDownloadClick = {viewmodel.downloadGeoIP(context = context)},
                        downloadEnable = XrayBaseService.statusFlow.collectAsState().value,
                        onImportClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            val chooserIntent =
                                Intent.createChooser(intent, "Select a file via...")
                            ipFilePickLauncher.launch(chooserIntent)
                        }
                    )
                    SettingsWithBtnBox(
                        title = R.string.geo_site,
                        description = R.string.geo_site_description,
                        icon = Icons.Outlined.Public,
                        onDownloadClick = {viewmodel.downloadGeoSite(context)},
                        downloading = geoSiteDownloading,
                        progress = geoSiteProgress,
                        downloadEnable = XrayBaseService.statusFlow.collectAsState().value,
                        onImportClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            val chooserIntent =
                                Intent.createChooser(intent, "Select a file via...")
                             domainFilePickLauncher.launch(chooserIntent)
                        }
                    )
                    SettingsWithBtnBox(
                        title = R.string.geo_lite_title,
                        description = R.string.geo_ip_lite_description,
                        icon = Icons.Outlined.DataUsage,
                        onDownloadClick = {viewmodel.downloadGeoLite(context)},
                        downloading = geoLiteDownloading,
                        downloadEnable = XrayBaseService.statusFlow.collectAsState().value,
                        progress = geoLiteProgress,
                        enable = settingsState.geoLiteInstall
                    )
                    SettingsCheckBox(
                        title = R.string.enable_hextun_title,
                        description = R.string.enable_hex_tun_desc,
                        icon = Icons.Outlined.Security,
                        checked = settingsState.hexTunEnable,
                        onCheckedChange = {
                            viewmodel.setHexTunEnable(it)
                        }
                    )
                    SettingsFieldBox(
                        title = R.string.test_url,
                        content = settingsState.delayTestUrl,
                        icon = Icons.Outlined.Speed
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
                        icon = Icons.Outlined.Info,
                        onClick = {}
                    )

                    SettingsFieldBox(
                        title = R.string.xray_core_version,
                        content = settingsState.xrayCoreVersion,
                        icon = Icons.Outlined.Info
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

                                SettingsKeys.SOCKS_USERNAME.name ->
                                    viewmodel.setSocksUsername(it)

                                SettingsKeys.SOCKS_PASSWORD.name ->
                                    viewmodel.setSocksPassword(it)
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
    icon: ImageVector? = null,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {}
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = icon?.let { { 
            Icon(
                imageVector = it, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) 
        } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}


@Composable
fun SettingsWithBtnBox(
    @StringRes title: Int,
    @StringRes description: Int? = null,
    content: String = "",
    icon: ImageVector? = null,
    downloading: Boolean = false,
    progress: Float = 0f,
    onDownloadClick: () -> Unit = {},
    onImportClick: (() -> Unit)? = null,
    enable: Boolean = true,
    downloadEnable: Boolean = true
) {

    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (downloading) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        )
    )

    ListItem(
        headlineContent = {
            Text(
                text = stringResource(title),
                color = if (enable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = if (description != null)stringResource(description) else content,
                    color = if (enable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
                if (downloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        leadingContent = icon?.let { {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (enable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        } },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onDownloadClick,
                    enabled = downloadEnable
                ) {
                    Icon(
                        imageVector = if (!downloading)
                            Icons.Outlined.Download
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
                        onClick = onImportClick,
                        enabled = enable
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = "import",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSelectBox(
    @StringRes title: Int,
    @StringRes description: Int,
    icon: ImageVector? = null,
    onSelected: (Int) -> Unit = {},
    selected: String = "dark",
    options: Map<Int,String> = mapOf()
) {
    var expand by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = icon?.let { { 
            Icon(
                imageVector = it, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) 
        } },
        trailingContent = {
            Box {
                TextButton(
                    onClick = {
                        expand = !expand
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = selected,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = if (expand)
                                Icons.Default.KeyboardArrowUp
                            else
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
                DropdownMenu(
                    expanded = expand,
                    onDismissRequest = {expand = false},
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    offset = DpOffset(0.dp, 0.dp),
                    shape = RoundedCornerShape(12.dp)
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
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expand = !expand }
    )
}


@Composable
fun SettingsFieldBox(
    @StringRes title: Int,
    content: String,
    enable: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () ->Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(title),
                color = if (enable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = {
            Text(
                text = content,
                color = if (enable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        leadingContent = icon?.let { {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = if (enable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        } },
        trailingContent = trailingIcon?.let { {
            Icon(
                it,
                contentDescription = null,
                tint = if (enable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(enabled = enable) { onClick() }
    )
}


@Composable
fun SettingsGroup(
    groupName: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = groupName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                content()
            }
        }
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

/**
 * Validates a single credential field (username or password).
 * @return Error message string resource ID or null if valid.
 */
fun validateSocks(input: String, context: Context, isPassword: Boolean): String? {
    val trimmed = input.trim()
    val credentialsRegex = Regex("^[\\x21-\\x7E]+$")
    // 1. Empty Check
    if (trimmed.isEmpty()) {
        return if (isPassword) context.getString(R.string.err_socks_pass_empty)
        else context.getString(R.string.err_socks_user_empty)
    }

    // 2. Byte Length Check (RFC 1929 requirement: 1-255 bytes)
    val byteLength = trimmed.toByteArray(Charsets.UTF_8).size
    if (byteLength > 255) {
        return context.getString(R.string.err_socks_length_exceeded)
    }

    // 3. Character Validity Check
    // To prevent detection and maintain compatibility, we avoid spaces and control chars.
    if (!credentialsRegex.matches(trimmed)) {
        return context.getString(R.string.err_socks_invalid_chars)
    }

    return null
}