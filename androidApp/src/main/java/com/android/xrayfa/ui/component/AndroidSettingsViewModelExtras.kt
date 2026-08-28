package com.android.xrayfa.ui.component

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.xrayfa.R
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.viewmodel.GEOFileType
import com.android.xrayfa.viewmodel.GEOFileType.Companion.FILE_TYPE_IP
import com.android.xrayfa.viewmodel.SettingsViewmodel

@Composable
fun ColumnScope.AndroidSettingsGeneralViewModelExtras(viewmodel: SettingsViewmodel) {
    val settingsState by viewmodel.settingsState.collectAsState()
    if (NotificationHelper.canPostPromotionsEnabled(LocalContext.current)) {
        SettingsCheckBox(
            title = R.string.live_update_notification,
            description = R.string.live_update_notification_desc,
            icon = Icons.Outlined.NotificationsActive,
            checked = settingsState.liveUpdateNotification,
            onCheckedChange = viewmodel::setLiveUpdateNotification,
        )
    }
}

@Composable
fun ColumnScope.AndroidSettingsNetworkViewModelExtras(viewmodel: SettingsViewmodel) {
    val settingsState by viewmodel.settingsState.collectAsState()
    val isVpnConnected by viewmodel.isVpnConnected.collectAsState()
    val context = LocalContext.current
    val geoIPDownloading by viewmodel.geoIPDownloading.collectAsState()
    val geoIPProgress by viewmodel.geoIPProgress.collectAsState()
    val geoSiteDownloading by viewmodel.geoSiteDownloading.collectAsState()
    val geoSiteProgress by viewmodel.geoSiteProgress.collectAsState()

    val ipFilePickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                viewmodel.onSelectFile(context, uri, FILE_TYPE_IP)
            }
        }

    val domainFilePickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@rememberLauncherForActivityResult
                viewmodel.onSelectFile(context, uri, GEOFileType.FILE_TYPE_SITE)
            }
        }

    SettingsWithBtnBox(
        title = R.string.geo_ip,
        description = R.string.geo_ip_description,
        icon = Icons.Outlined.Language,
        downloading = geoIPDownloading,
        progress = geoIPProgress,
        onDownloadClick = { viewmodel.downloadGeoIP(context = context) },
        downloadEnable = isVpnConnected,
        downloadDisabledHint = R.string.geo_download_need_service_hint,
        onImportClick = {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            ipFilePickLauncher.launch(Intent.createChooser(intent, "Select a file via..."))
        },
    )
    SettingsWithBtnBox(
        title = R.string.geo_site,
        description = R.string.geo_site_description,
        icon = Icons.Outlined.Public,
        onDownloadClick = { viewmodel.downloadGeoSite(context) },
        downloading = geoSiteDownloading,
        progress = geoSiteProgress,
        downloadEnable = isVpnConnected,
        downloadDisabledHint = R.string.geo_download_need_service_hint,
        onImportClick = {
            val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            domainFilePickLauncher.launch(Intent.createChooser(intent, "Select a file via..."))
        },
    )
    SettingsCheckBox(
        title = R.string.enable_hextun_title,
        description = R.string.enable_hex_tun_desc,
        icon = Icons.Outlined.Security,
        checked = settingsState.hexTunEnable,
        onCheckedChange = viewmodel::setHexTunEnable,
    )

    val importException by viewmodel.importException.collectAsState()
    val downloadException by viewmodel.downloadException.collectAsState()
    ExceptionMessage(
        shown = importException || downloadException,
        msg =
            if (importException) {
                stringResource(R.string.import_geo_failed)
            } else {
                stringResource(R.string.download_geo_failed)
            },
    )
}
