package com.android.xrayfa.viewmodel

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import okio.buffer
import okio.sink
import java.io.File


class SettingsViewmodel(
    val repository: SettingsRepository,
    val okHttpClient: OkHttpClient
): ViewModel() {

    companion object {
        const val REPO = "https://github.com/Q7DF1/XrayFA"
        const val TAG = "SettingsViewmodel"
    }

    val geoIPUrlTest = "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat"

    private val _geoIPDownloading = MutableStateFlow(false)
    val geoIPDownloading = _geoIPDownloading.asStateFlow()

    private val _geoSiteDownloading = MutableStateFlow(false)
    val geoSiteDownloading = _geoSiteDownloading.asStateFlow()

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


    fun downloadGeoIP(url:String = geoIPUrlTest, context: Context) {
        val request = Request.Builder()
            .url(url)
            .build()
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "downloadGeoIP: downloading")
            _geoIPDownloading.value = true
            okHttpClient.newCall(request)
                .execute().use { res ->
                    if (!res.isSuccessful) throw IOException("Unexpected code $res")

                    res.body?.let { body ->
                        val externalFilesDir =
                            context.applicationContext.getExternalFilesDir("assert")
                        val geoIpFile = File(externalFilesDir,"geoip.dat")
                        geoIpFile.sink().buffer().use {sink ->
                            sink.writeAll(body.source())
                        }
                        _geoIPDownloading.value = false
                        Log.i(TAG, "downloadGeoIP: download completed")
                    }
                }
        }

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