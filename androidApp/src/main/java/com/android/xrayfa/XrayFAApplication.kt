package com.android.xrayfa

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.appfunctions.service.AppFunctionConfiguration
import androidx.datastore.preferences.core.edit
import com.android.xrayfa.XrayAppCompatFactory.Companion.TAG
import com.android.xrayfa.agent.appfunctions.AgentAppFunctionEnableSync
import com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions
import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.utils.SocksConfigGenerator
import com.android.xrayfa.data.settingsDataStore
import com.android.xrayfa.datastore.SettingsKeys
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.di.androidKoinModules
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class XrayFAApplication : Application(), AppFunctionConfiguration.Provider {

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(XrayFAAppFunctions::class.java) {
                XrayFAAppFunctions(GlobalContext.get().get())
            }
            .build()


    private val _isDarkTheme = MutableStateFlow(Theme.AUTO_MODE.code)
    val isDarkTheme: StateFlow<Int> get() = _isDarkTheme

    private val appCoroutineScope = CoroutineScope(Dispatchers.IO)

    private val xrayAssetPaths: XrayAssetPaths by inject()

    private fun observeDarkMode() {
        appCoroutineScope.launch {
            settingsDataStore.data
                .map { prefs ->
                    prefs[SettingsKeys.DARK_MODE] ?: Theme.AUTO_MODE.code
                }
                .collect { value ->
                    _isDarkTheme.value = value
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initKoin()
        observeDarkMode()
        observeAgentFunctionEnabled()
        initXrayFile()
        initSocksConfig()
        initHwid()
    }

    private fun observeAgentFunctionEnabled() {
        val settingsRepository: SettingsRepository = GlobalContext.get().get()
        val sync: AgentAppFunctionEnableSync = GlobalContext.get().get()
        appCoroutineScope.launch {
            settingsRepository.settingsFlow
                .map { it.agentFunctionsEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    sync.sync(enabled)
                }
        }
    }

    private fun initKoin() {
        startKoin {
            androidContext(this@XrayFAApplication)
            modules(androidKoinModules())
        }
    }

    private fun initXrayFile() {
        appCoroutineScope.launch {
            val geoipFile = File(xrayAssetPaths.geoIpPath)
            val geositeFile = File(xrayAssetPaths.geoSitePath)
            if (!geoipFile.exists()) {
                Log.i(TAG, "copy geoip.dat")
                assets.open(GEO_IP).use { input ->
                    FileOutputStream(geoipFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!geositeFile.exists()) {
                assets.open(GEO_SITE).use { input ->
                    FileOutputStream(geositeFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun initSocksConfig() {
        appCoroutineScope.launch {
            settingsDataStore.edit {
                val port = it[SettingsKeys.SOCKS_PORT]
                if (port == null || port !in SocksConfigGenerator.portRange) {
                    it[SettingsKeys.SOCKS_PORT] = SocksConfigGenerator.generatePort()
                }
                val password = it[SettingsKeys.SOCKS_PASSWORD]
                if (password == null || password.isBlank()) {
                    it[SettingsKeys.SOCKS_PASSWORD] = SocksConfigGenerator.generatePassword()
                }
                val username = it[SettingsKeys.SOCKS_USERNAME]
                if (username == null || username.isBlank()) {
                    it[SettingsKeys.SOCKS_USERNAME] = SocksConfigGenerator.generateUsername()
                }
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun initHwid(){
        appCoroutineScope.launch {
            settingsDataStore.edit {
                it[SettingsKeys.HWID] = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            }
        }
    }
}
