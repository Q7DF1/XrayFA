package com.android.xrayfa.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.Mode
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.common.repository.SettingsState
import com.android.xrayfa.ui.AppsActivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri
import com.android.xrayfa.common.di.qualifier.LongTime
import com.android.xrayfa.common.utils.calculateFileHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import okio.buffer
import okio.sink
import java.io.File

const val FILE_TYPE_SITE = 0
const val FILE_TYPE_IP = 1
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
    private val _geoIPDownloading = MutableStateFlow(false)
    val geoIPDownloading = _geoIPDownloading.asStateFlow()

    private val _geoSiteDownloading = MutableStateFlow(false)
    val geoSiteDownloading = _geoSiteDownloading.asStateFlow()

    private val _importException = MutableStateFlow(false)
    val importException = _importException.asStateFlow()

    val settingsState = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )


    fun setDarkMode(@Mode darkMode: Int) {
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

    fun startAppsActivity(context: Context) {
        val intent = Intent(context, AppsActivity::class.java)
        context.startActivity(intent)
    }

    fun openRepo(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, REPO.toUri())
        context.startActivity(intent)
    }


    fun downloadGeoSite(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _geoSiteDownloading.value = true
            download(FILE_TYPE_SITE,context)
            _geoSiteDownloading.value = false
        }
    }

    fun downloadGeoIP(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _geoIPDownloading.value = true
            download(FILE_TYPE_IP,context)
            _geoIPDownloading.value = false
        }
    }
    private suspend fun download(fileType: Int, context: Context) {
        val url = if (fileType == FILE_TYPE_IP) geoIPUrlTest else geoSiteUrlTest
        val request = Request.Builder()
            .url(url)
            .build()
            Log.i(TAG, "$url: downloading")
        okHttpClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("Unexpected code $res")

            res.body?.let { body ->
                val file =
                    File(context.filesDir,if (FILE_TYPE_IP == fileType)"geoip.dat" else "geosite.dat")
                file.sink().buffer().use { sink ->
                        sink.writeAll(body.source())
                }
            }

        }
    }

    fun onSelectFile(context: Context,uri: Uri,fileType: Int) {
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

            val targetName = if (fileType == FILE_TYPE_IP)
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