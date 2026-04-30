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
import com.android.xrayfa.common.repository.Theme
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.common.repository.SettingsState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import com.android.xrayfa.R
import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.di.qualifier.LongTime
import com.android.xrayfa.common.utils.calculateFileHash
import com.android.xrayfa.core.XrayBaseServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import okio.buffer
import okio.sink
import java.io.File


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
    val okHttpClient: OkHttpClient,
    val xrayBaseServiceManager: XrayBaseServiceManager
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


    fun setDarkMode(@Theme darkMode: Int) {
        viewModelScope.launch {
            repository.setDarkMode(darkMode)
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

    suspend fun onConfigSettingsChanged() {
        xrayBaseServiceManager.restartXrayBaseServiceIfNeed()
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
            val downloaded = download(GEOFileType.FILE_TYPE_SITE,context)
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
            download(GEOFileType.FILE_TYPE_LITE,context)
            _geoLiteDownloading.value = false
            Log.i(TAG, "downloadGeoLite: download successful!")
            repository.setGeoLiteInstall(true)
        }

    }

    fun downloadGeoIP(context: Context) {

        if (_geoSiteDownloading.value || _geoIPDownloading.value || _geoLiteDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoIPDownloading.value = true
            val downloaded = download(GEOFileType.FILE_TYPE_IP,context)
            if (downloaded) onConfigChanged()
            _geoIPDownloading.value = false
        }
    }

    suspend fun onConfigChanged() {
        xrayBaseServiceManager.restartXrayBaseServiceIfNeed()
    }


    private suspend fun download(
        url: String,
        target:String,
        statusFlow: MutableStateFlow<Boolean>,
        progressFlow: MutableStateFlow<Float>,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {


        val request = Request.Builder()
            .url(url)
            .build()
        Log.i(TAG, "$url: downloading")
        try {
            okHttpClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Unexpected code $res")

                res.body?.let { body ->
                    val contentLength = body.contentLength()
                    val file = File(context.filesDir,target)
                    var totalRead = 0L
                    val buffer = ByteArray(8192)

                    body.byteStream().use { inputStream ->
                        file.outputStream().use { outputStream ->
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                outputStream.write(buffer, 0, read)
                                totalRead += read
                                if (contentLength > 0) {
                                    progressFlow.value = totalRead.toFloat() / contentLength
                                }
                            }
                        }
                    }
                }
            }
            return@withContext true
        }catch (e: Exception) {
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
        context: Context
    ):Boolean {

        return when(fileType) {
            GEOFileType.FILE_TYPE_IP ->
                download(geoIPUrlTest, GEO_IP,_geoIPDownloading, _geoIPProgress, context)
            GEOFileType.FILE_TYPE_SITE ->
                download(geoSiteUrlTest, GEO_SITE,_geoSiteDownloading, _geoSiteProgress, context)
            GEOFileType.FILE_TYPE_LITE ->
                download(geoLiteUrlTest, GEO_LITE,_geoLiteDownloading, _geoLiteProgress, context)
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

            val targetName = if (fileType == GEOFileType.FILE_TYPE_IP) GEO_IP else GEO_SITE
            val file = File(context.filesDir,targetName)
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


class SettingsViewmodelFactory
@Inject constructor(
    val repository: SettingsRepository,
    @LongTime val okHttpClient : OkHttpClient,
    val xrayBaseServiceManager: XrayBaseServiceManager
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewmodel::class.java)) {
            return SettingsViewmodel(repository,okHttpClient,xrayBaseServiceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}