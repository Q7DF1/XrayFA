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
import com.android.xrayfa.ui.AppsActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import com.android.xrayfa.R
import com.android.xrayfa.common.di.qualifier.LongTime
import com.android.xrayfa.common.utils.calculateFileHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
class SettingsViewmodel(
    val repository: SettingsRepository,
    val okHttpClient: OkHttpClient
): ViewModel() {

    companion object {
        const val REPO = "https://github.com/Q7DF1/XrayFA"
        const val TAG = "SettingsViewmodel"
    }

    val geoIPUrlTest = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"
    val geoSiteUrlTest = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
    val geoLiteUrlTest = "https://github.com/P3TERX/GeoLite.mmdb/raw/download/GeoLite2-Country.mmdb"
    private val _geoIPDownloading = MutableStateFlow(false)
    val geoIPDownloading = _geoIPDownloading.asStateFlow()

    private val _geoSiteDownloading = MutableStateFlow(false)
    val geoSiteDownloading = _geoSiteDownloading.asStateFlow()


    private val _geoLiteDownloading = MutableStateFlow(false)
    val geoLiteDownloading = _geoLiteDownloading.asStateFlow()

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
        }
    }

    fun setDnsIpV4(dns: String) {
        viewModelScope.launch {
            repository.setDnsIPv4(dns)
        }
    }

    fun setDnsIpV6(dns: String) {
        viewModelScope.launch {
            repository.setDnsIPv6(dns)
        }
    }

    fun startAppsActivity(context: Context) {
        val intent = Intent(context, AppsActivity::class.java)
        context.startActivity(intent)
    }

    fun openRepo(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, REPO.toUri())
        context.startActivity(intent)
    }


    fun downloadGeoSite(context: Context) {
        if (_geoSiteDownloading.value || _geoIPDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoSiteDownloading.value = true
            download(GEOFileType.FILE_TYPE_SITE,context)
            _geoSiteDownloading.value = false
        }
    }

    fun downloadGeoLite(context: Context) {
        if (_geoSiteDownloading.value || _geoIPDownloading.value || _geoLiteDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO){
            _geoLiteDownloading.value = true
            download(GEOFileType.FILE_TYPE_LITE,context)
            _geoLiteDownloading.value = false
            repository.setGeoLiteInstall(true)
        }

    }

    fun downloadGeoIP(context: Context) {

        if (_geoSiteDownloading.value || _geoIPDownloading.value) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _geoIPDownloading.value = true
            download(GEOFileType.FILE_TYPE_IP,context)
            _geoIPDownloading.value = false
        }
    }


    private suspend fun download(
        url: String,
        target:String,
        flow: MutableStateFlow<Boolean>,
        context: Context
    ) = withContext(Dispatchers.IO) {


        val request = Request.Builder()
            .url(url)
            .build()
        Log.i(TAG, "$url: downloading")
        try {
            okHttpClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Unexpected code $res")

                res.body?.let { body ->
                    val file =
                        File(context.filesDir,target)
                    file.sink().buffer().use { sink ->
                        sink.writeAll(body.source())
                    }
                }
            }
        }catch (e: Exception) {
            flow.value = false
            launch {
                _downloadException.value = true
                delay(2000L)
                _downloadException.value = false
            }
            Log.e(TAG, "download: exception $e")
        }
    }
    private suspend fun download(
        @GEOFileType fileType: Int,
        context: Context
    ) {

        when(fileType) {
            GEOFileType.FILE_TYPE_IP ->
                download(geoIPUrlTest, "geoip.dat",_geoIPDownloading,context)
            GEOFileType.FILE_TYPE_SITE ->
                download(geoSiteUrlTest,"geosite.dat",_geoSiteDownloading,context)
            GEOFileType.FILE_TYPE_LITE ->
                download(geoLiteUrlTest,"GeoLite2-Country.mmdb",_geoLiteDownloading,context)
            else -> {
                Log.e(TAG, "download: download type error")
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

            val targetName = if (fileType == GEOFileType.FILE_TYPE_IP)
                "geoip.dat"
            else "geosite.dat"
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
    @LongTime val okHttpClient : OkHttpClient
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewmodel::class.java)) {
            return SettingsViewmodel(repository,okHttpClient) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}