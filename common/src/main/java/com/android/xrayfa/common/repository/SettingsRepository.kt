package com.android.xrayfa.common.repository

import android.content.Context
import android.util.Log
import androidx.annotation.IntDef
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsState(
    val darkMode: Int = 0,
    val ipV6Enable: Boolean = false,
    val socksPort: Int = 10808,
    val dnsIPv4: String = "",
    val dnsIPv6: String = "",
    val delayTestUrl: String = DEFAULT_DELAY_TEST_URL,
    val xrayCoreVersion: String = "unknown",
    val version: String = "1.0.0"
)
object SettingsKeys {
    val DARK_MODE = intPreferencesKey("dark_mode")
    val IPV6_ENABLE = booleanPreferencesKey("ipv6_enable")
    val SOCKS_PORT = intPreferencesKey("socks_port")
    val DNS_IPV4 = stringPreferencesKey("dns_ipv4")
    val DNS_IPV6 = stringPreferencesKey("dns_ipv6")
    val VERSION = stringPreferencesKey("version")
    val DELAY_TEST_URL = stringPreferencesKey("delay_test_site")
    //to json
    val ALLOW_PACKAGES = stringPreferencesKey("allow_packages")
    val XRAY_CORE_VERSION = stringPreferencesKey("xray_version")
}
const val LIGHT_MODE = 0
const val DARK_MODE = 1
const val AUTO_MODE = 2

const val DEFAULT_DELAY_TEST_URL = "https://www.google.com"

val listType = object : TypeToken<MutableList<String>>() {}.type

@IntDef(LIGHT_MODE, DARK_MODE, AUTO_MODE)
@Retention(AnnotationRetention.SOURCE)
annotation class Mode


@Singleton
class SettingsRepository
@Inject constructor(private val context: Context) {

    val settingsFlow = context.dataStore.data.map { prefs ->
        SettingsState(
            darkMode = prefs[SettingsKeys.DARK_MODE] ?: 0,
            ipV6Enable = prefs[SettingsKeys.IPV6_ENABLE] == true,
            socksPort = prefs[SettingsKeys.SOCKS_PORT] ?: 10808,
            dnsIPv4 = prefs[SettingsKeys.DNS_IPV4] ?: "8.8.8.8,1.1.1.1",
            dnsIPv6 = prefs[SettingsKeys.DNS_IPV6] ?: "2001:4860:4860::8888",
            delayTestUrl = prefs[SettingsKeys.DELAY_TEST_URL] ?: DEFAULT_DELAY_TEST_URL,
            version = prefs[SettingsKeys.VERSION] ?: "1.0.0",
            xrayCoreVersion = prefs[SettingsKeys.XRAY_CORE_VERSION]?:"unknown"
        )

    }

    val packagesFlow = context.dataStore.data.map { prefs ->
        Gson().fromJson<MutableList<String>>(prefs[SettingsKeys.ALLOW_PACKAGES], listType) ?: emptyList()
    }

    suspend fun setDarkMode(@Mode darkMode: Int) {
        context.dataStore.edit {
            it[SettingsKeys.DARK_MODE] = darkMode
        }
    }

    suspend fun setIpV6Enable(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.IPV6_ENABLE] = enable
        }
    }

    suspend fun setSocksPort(port: Int) {
        context.dataStore.edit {
            it[SettingsKeys.SOCKS_PORT] = port
        }
    }

    suspend fun setDnsIPv4(dns: String) {
        context.dataStore.edit {
            it[SettingsKeys.DNS_IPV4] = dns
        }
    }

    suspend fun setDnsIPv6(dns: String) {
        context.dataStore.edit {
            it[SettingsKeys.DNS_IPV6] = dns
        }
    }
    suspend fun setXrayCoreVersion(version: String) {
        context.dataStore.edit {
            it[SettingsKeys.XRAY_CORE_VERSION] = version
        }
    }

    suspend fun setDelayTestUrl(url:String) {
        context.dataStore.edit {
            it[SettingsKeys.DELAY_TEST_URL] = url
        }
    }

    suspend fun setAllowedPackages(packages: List<String>) {
        val listJson = Gson().toJson(packages, listType)
        context.dataStore.edit {
            it[SettingsKeys.ALLOW_PACKAGES] = listJson
        }
    }

    suspend fun addAllowedPackages(packageName: String) {
        context.dataStore.edit { prefs ->
            val listJson = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"
            val list: MutableList<String> = Gson().fromJson(listJson, listType) ?: mutableListOf()

            if (!list.contains(packageName)) {
                list.add(packageName)
            }
            Log.i("test", "addAllowedPackages: ${list.size}")
            prefs[SettingsKeys.ALLOW_PACKAGES] = Gson().toJson(list, listType)
        }
    }

    suspend fun removeAllowedPackage(packageName: String) {
        context.dataStore.edit { prefs ->
            val listJson = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"

            val list: MutableList<String> = Gson().fromJson(listJson, listType) ?: mutableListOf()

            val newList = list.filter { it != packageName }

            prefs[SettingsKeys.ALLOW_PACKAGES] = Gson().toJson(newList, listType)
        }
    }

    suspend fun getAllowedPackages(): List<String> {
        val prefs = context.dataStore.data.first()
        val json = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"
        return Gson().fromJson(json, listType) ?: emptyList()
    }

}