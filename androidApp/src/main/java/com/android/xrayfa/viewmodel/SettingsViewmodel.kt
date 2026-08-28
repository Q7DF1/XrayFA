package com.android.xrayfa.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.android.xrayfa.R
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.routing.DomainStrategy
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.common.routing.Rule
import com.android.xrayfa.common.utils.calculateFileHash
import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.android.xrayfa.network.FileDownloader
import java.io.File

const val LOCAL_PROXY_LISTEN_ADDRESS = "127.0.0.1"
const val LAN_PROXY_LISTEN_ADDRESS = "0.0.0.0"

fun resolveSocksListenAddressForLan(enable: Boolean): String {
    return if (enable) LAN_PROXY_LISTEN_ADDRESS else LOCAL_PROXY_LISTEN_ADDRESS
}

@IntDef(value = [
    GEOFileType.FILE_TYPE_SITE,
    GEOFileType.FILE_TYPE_IP,
    GEOFileType.FILE_TYPE_LITE
])
annotation class GEOFileType {
    companion object {
        const val FILE_TYPE_SITE = 0
        const val FILE_TYPE_IP = 1
        const val FILE_TYPE_LITE = 2
    }
}

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val assets: List<GithubAsset>
)

@Serializable
data class GithubAsset(
    @SerialName("browser_download_url") val downloadUrl: String,
    val name: String
)
class SettingsViewmodel(
    val repository: SettingsRepository,
    private val fileDownloader: FileDownloader,
    private val vpnController: VpnController,
    private val assetPaths: XrayAssetPaths,
): ViewModel() {

    companion object {
        const val REPO = "https://github.com/Q7DF1/XrayFA"
        const val TAG = "SettingsViewmodel"
    }

    val geoIPUrlTest = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"
    val geoSiteUrlTest = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
    val geoLiteUrlTest = "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb"
    val xrayFaReleaseUrl = "https://api.github.com/repos/q7df1/xrayfa/releases/latest"
    private val _geoIPDownloading = MutableStateFlow(false)
    val geoIPDownloading = _geoIPDownloading.asStateFlow()

    private val _geoIPProgress = MutableStateFlow(0f)
    val geoIPProgress = _geoIPProgress.asStateFlow()

    private val _geoSiteDownloading = MutableStateFlow(false)
    val geoSiteDownloading = _geoSiteDownloading.asStateFlow()

    private val _geoSiteProgress = MutableStateFlow(0f)
    val geoSiteProgress = _geoSiteProgress.asStateFlow()

    private val _geoLiteDownloading = MutableStateFlow(false)
    val geoLiteDownloading = _geoLiteDownloading.asStateFlow()

    private val _geoLiteProgress = MutableStateFlow(0f)
    val geoLiteProgress = _geoLiteProgress.asStateFlow()

    private val _xrayFaDownloading = MutableStateFlow(false)
    val xrayFaDownloading = _xrayFaDownloading.asStateFlow()


    private val _importException = MutableStateFlow(false)
    val importException = _importException.asStateFlow()

    private val _downloadException = MutableStateFlow(false)
    val downloadException = _downloadException.asStateFlow()

    val settingsState = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    val isVpnConnected: StateFlow<Boolean> = vpnController.state
        .map { it.isConnected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = vpnController.state.value.isConnected,
        )


    fun setDarkMode(darkMode: Int) {
        viewModelScope.launch {
            repository.setDarkMode(Theme.fromCode(darkMode))
        }
    }

    fun setDomainStrategy(strategy: DomainStrategy) {
        viewModelScope.launch {
            repository.setDomainStrategy(strategy)
            onConfigSettingsChanged()
        }
    }

    fun setRoutingMode(mode: RoutingMode) {
        viewModelScope.launch {
            repository.setRoutingMode(mode)
            onConfigSettingsChanged()
        }
    }

    fun setIpV6Enable(enable: Boolean) {
        viewModelScope.launch {
            repository.setIpV6Enable(enable)
        }
    }

    fun setSocksPort(port: Int) {
        viewModelScope.launch {
            repository.setSocksPort(port)
            onConfigSettingsChanged()
        }
    }

    fun setHttpPort(port: Int) {
        viewModelScope.launch {
            repository.setHttpPort(port)
            onConfigSettingsChanged()
        }
    }

    fun setLanHttpProxyEnable(enable: Boolean) {
        viewModelScope.launch {
            repository.setLanHttpProxyEnable(enable)
            onConfigSettingsChanged()
        }
    }

    fun setLanSocksProxyEnable(enable: Boolean) {
        setSocksListen(resolveSocksListenAddressForLan(enable))
    }

    fun setDnsIpV4(dns: String) {
        viewModelScope.launch {
            repository.setDnsIPv4(dns)
            onConfigSettingsChanged()
        }
    }

    fun setDnsIpV6(dns: String) {
        viewModelScope.launch {
            repository.setDnsIPv6(dns)
            onConfigSettingsChanged()
        }
    }

    fun setDelayTestUrl(url: String) {
        viewModelScope.launch {
            repository.setDelayTestUrl(url)
        }
    }

    fun setLiveUpdateNotification(enable: Boolean) {
        viewModelScope.launch {
            repository.setLiveUpdateNotification(enable)
        }
    }

    fun setEnableBootAutoStart(enable: Boolean) {
        viewModelScope.launch {
            repository.setBootAutoStart(enable)
        }
    }

    fun setHexTunEnable(enable: Boolean) {
        viewModelScope.launch {
            repository.setHexTunState(enable)
            onConfigSettingsChanged()
        }
    }

    fun setHideFromRecents(enable: Boolean) {
        viewModelScope.launch {
            repository.setHideFromRecentsState(enable)
        }
    }

    fun setSocksUsername(username: String) {
        viewModelScope.launch {
            repository.setSocksUsername(username)
            onConfigSettingsChanged()
        }
    }

    fun setSocksPassword(password: String) {
        viewModelScope.launch {
            repository.setSocksPassword(password)
            onConfigSettingsChanged()
        }
    }

    fun setSocksListen(address: String) {
        viewModelScope.launch {
            repository.setSocksListen(address)
            onConfigSettingsChanged()
        }
    }

    fun setRoutingRules(rules: List<Rule>) {
        viewModelScope.launch {
            repository.setRoutingRules(rules)
            onConfigSettingsChanged()
        }
    }

    fun setSendHwid(enable: Boolean){
        viewModelScope.launch {
            repository.setSendHwid(enable)
        }
    }

    suspend fun onConfigSettingsChanged() {
        vpnController.restartIfNeeded()
    }


    fun openRepo(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, REPO.toUri())
        context.startActivity(intent)
    }


    fun downloadGeoSite(context: Context) {
        if (_geoSiteDownloading.value || _geoIPDownloading.value || _geoLiteDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoSiteDownloading.value = true
            val downloaded = download(GEOFileType.FILE_TYPE_SITE)
            if (downloaded) onConfigChanged()
            _geoSiteDownloading.value = false
        }
    }

    fun downloadGeoLite(context: Context) {
        if (_geoSiteDownloading.value || _geoIPDownloading.value || _geoLiteDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoLiteDownloading.value = true
            val downloaded = download(GEOFileType.FILE_TYPE_LITE)
            _geoLiteDownloading.value = false
            if (downloaded) {
                Log.i(TAG, "downloadGeoLite: download successful!")
                repository.setGeoLiteInstall(true)
            }
        }

    }

    fun downloadGeoIP(context: Context) {

        if (_geoSiteDownloading.value || _geoIPDownloading.value || _geoLiteDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoIPDownloading.value = true
            val downloaded = download(GEOFileType.FILE_TYPE_IP)
            if (downloaded) onConfigChanged()
            _geoIPDownloading.value = false
        }
    }

    suspend fun onConfigChanged() {
        vpnController.restartIfNeeded()
    }


    private suspend fun download(
        url: String,
        targetPath: String,
        statusFlow: MutableStateFlow<Boolean>,
        progressFlow: MutableStateFlow<Float>,
    ): Boolean = withContext(Dispatchers.IO) {


        Log.i(TAG, "$url: downloading")
        try {
            fileDownloader.downloadToFile(url, targetPath) { progress ->
                progressFlow.value = progress
            }
            return@withContext true
        } catch (e: Exception) {
            statusFlow.value = false
            launch {
                _downloadException.value = true
                delay(2000L)
                _downloadException.value = false
            }
            Log.e(TAG, "download: exception $e")
            return@withContext false
        } finally {
            progressFlow.value = 0f
        }
    }
    private suspend fun download(
        @GEOFileType fileType: Int,
    ):Boolean {

        return when(fileType) {
            GEOFileType.FILE_TYPE_IP ->
                download(geoIPUrlTest, assetPaths.geoIpPath, _geoIPDownloading, _geoIPProgress)
            GEOFileType.FILE_TYPE_SITE ->
                download(geoSiteUrlTest, assetPaths.geoSitePath, _geoSiteDownloading, _geoSiteProgress)
            GEOFileType.FILE_TYPE_LITE ->
                download(geoLiteUrlTest, assetPaths.geoLiteDatabasePath, _geoLiteDownloading, _geoLiteProgress)
            else -> {
                Log.e(TAG, "download: download type error")
                false
            }
        }

    }

    fun onSelectFile(context: Context,uri: Uri,@GEOFileType fileType: Int) {
        if (_geoSiteDownloading.value || _geoIPDownloading.value) {
            Toast.makeText(context,R.string.geo_import_try_later,Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val name = getFileName(uri,context)
            if (name?.endsWith(".dat", ignoreCase = true) != true) {
                Log.e(TAG, "onSelectFile: file type error")
                launch {
                    _importException.value = true
                    delay(2000L)
                    _importException.value = false
                }
                return@launch
            }

            val filePath = if (fileType == GEOFileType.FILE_TYPE_IP) {
                assetPaths.geoIpPath
            } else {
                assetPaths.geoSitePath
            }
            val file = File(filePath)
            val calculateFileHash = calculateFileHash(file)
            Log.i(TAG, "onSelectFile: $calculateFileHash")
            val input = context.contentResolver.openInputStream(uri)
            input?.use { input ->
                file.outputStream().use { output->
                    input.copyTo(output)
                }
            }
            val calculateFileHash1 = calculateFileHash(file)
            Log.i(TAG, "onSelectFile: $calculateFileHash1")
            Log.i(TAG, "onSelectFile: import successful")
        }
    }


    private fun getFileName(uri: Uri,context: Context): String? {
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return uri.path?.substringAfterLast('/')
    }



}


class SettingsViewmodelFactory(
    val repository: SettingsRepository,
    val fileDownloader: FileDownloader,
    val vpnController: VpnController,
    val assetPaths: XrayAssetPaths,
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewmodel::class.java)) {
            return SettingsViewmodel(repository, fileDownloader, vpnController, assetPaths) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}