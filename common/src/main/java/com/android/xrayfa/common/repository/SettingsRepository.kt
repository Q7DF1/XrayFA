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

data class Rule(
    val domain:      List<String>? = null,
    val ip:          List<String>? = null,
    val port:        String? = null,
    val sourcePort:  String? = null,
    val localPort:   String? = null,
    val network:     String? = null,
    val source:      List<String>? = null, // 新增: 替代 sourceIP
    val sourceIP:    List<String>? = null, // 标记过时: 建议使用 source
    val user:        List<String>? = null,
    val vlessRoute:  String? = null,
    val inboundTag:  List<String>? = null,
    val protocol:    List<String>? = null,
    val attrs:       Map<String, String>? = null, // 修正: 应为键值对 Map
    val outboundTag: String? = null,
    val balancerTag: String? = null,
    val ruleTag:     String? = null,
    val domainMatcher: String? = null, // 新增: 覆盖全局配置
    val type: String? = "field"
)

val defaultRoutes = Gson().toJson(listOf(
    Rule(
        type = "field",
        inboundTag = listOf("api"),
        outboundTag = "api",
        ruleTag = "API Traffic"
    ),
    Rule(
        type = "field",
        inboundTag = listOf("tun"),
        outboundTag = "dns-out",
        port = "53",
        ruleTag = "DNS Traffic"
    ),
    Rule(
        type = "field",
        outboundTag = "proxy",
        domain = listOf("geosite:telegram", "geosite:google"),
        ruleTag = "Proxy Telegram & Google"
    ),
    Rule(
        type = "field",
        outboundTag = "direct",
        domain = listOf("geosite:cn", "geosite:geolocation-cn"),
        ip = listOf("geoip:cn"),
        ruleTag = "Bypass Mainland China"
    ),
    Rule(
        type = "field",
        outboundTag = "block",
        domain = listOf("geosite:category-ads-all"),
        ruleTag = "Ad Block"
    )
))

/**
 * Due to module dependencies, we cannot directly use the `com.android.xrayfa.model.RuleObject` object here.
 * Therefore, we can only define an identical one, serialize it into JSON,
 * and then deserialize it back into `RuleObject` when needed.
 */
data class SettingsState(
    val darkMode: Int = 0,
    val ipV6Enable: Boolean = false,
    val socksPort: Int = 10808,
    val socksUserName: String = "",
    val socksPassword: String = "",
    val socksListen: String = "",
    val dnsIPv4: String = "",
    val dnsIPv6: String = "",
    val delayTestUrl: String = DEFAULT_DELAY_TEST_URL,
    val xrayCoreVersion: String = "unknown",
    val version: String = "1.0.0",
    val geoLiteInstall: Boolean = false,
    val liveUpdateNotification: Boolean = false,
    val bootAutoStart: Boolean = false,
    val hexTunEnable: Boolean = true,
    val hideFromRecents: Boolean = false,
    val domainStrategy: Int = DomainStrategy.IP_IF_NON_MATCH,
    val routingRules: String = defaultRoutes,
    val routingMode: Int = RoutingMode.ROUTE
)
object SettingsKeys {
    val DARK_MODE = intPreferencesKey("dark_mode")
    val IPV6_ENABLE = booleanPreferencesKey("ipv6_enable")
    val SOCKS_PORT = intPreferencesKey("socks_port")
    val SOCKS_USERNAME = stringPreferencesKey("socks_username")
    val SOCKS_PASSWORD = stringPreferencesKey("socks_password")
    val SOCKS_LISTEN = stringPreferencesKey("socks_listen")
    val DNS_IPV4 = stringPreferencesKey("dns_ipv4")
    val DNS_IPV6 = stringPreferencesKey("dns_ipv6")
    val VERSION = stringPreferencesKey("version")
    val DELAY_TEST_URL = stringPreferencesKey("delay_test_site")
    //to json
    val ALLOW_PACKAGES = stringPreferencesKey("allow_packages")
    val XRAY_CORE_VERSION = stringPreferencesKey("xray_version")
    val GEO_LITE_INSTALL = booleanPreferencesKey("geo_lite_install")
    val LIVE_UPDATE_NOTIFICATION = booleanPreferencesKey("live_update_notification")
    val BOOT_AUTO_START = booleanPreferencesKey("boot_auto_start")

    val HEX_TUN_ENABLE = booleanPreferencesKey("hex_tun_open")

    val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
    val DOMAIN_STRATEGY = intPreferencesKey("DOMAIN_STRATEGY")
    val ROUTING_RULES = stringPreferencesKey("ROUTING_RULES")
    val ROUTING_MODE = intPreferencesKey("routing_mode")
}

const val DEFAULT_DELAY_TEST_URL = "https://www.google.com"

val listType = object : TypeToken<MutableList<String>>() {}.type

@IntDef(value = [
    Theme.LIGHT_MODE,
    Theme.DARK_MODE,
    Theme.AUTO_MODE
])
@Retention(AnnotationRetention.SOURCE)
annotation class Theme {
    companion object {
        const val LIGHT_MODE = 0
        const val DARK_MODE = 1
        const val AUTO_MODE = 2
    }
}

@IntDef(value = [
    RoutingMode.GLOBAL,
    RoutingMode.ROUTE
])
@Retention(AnnotationRetention.SOURCE)
annotation class RoutingMode {
    companion object {
        const val GLOBAL = 0
        const val ROUTE = 1
    }
}

@IntDef(value = [
    DomainStrategy.ASIS,
    DomainStrategy.IP_IF_NON_MATCH,
    DomainStrategy.IP_ON_DEMAND
])
@Retention(AnnotationRetention.SOURCE)
annotation class DomainStrategy {
    companion object {
        const val ASIS = 0
        const val IP_IF_NON_MATCH = 1
        const val IP_ON_DEMAND = 2
    }
}


@Singleton
class SettingsRepository
@Inject constructor(
    private val context: Context,
    private val gson: Gson
) {

    val settingsFlow = context.dataStore.data.map { prefs ->
        SettingsState(
            darkMode = prefs[SettingsKeys.DARK_MODE] ?: 0,
            ipV6Enable = prefs[SettingsKeys.IPV6_ENABLE] == true,
            socksPort = prefs[SettingsKeys.SOCKS_PORT] ?: 10808,
            socksUserName = prefs[SettingsKeys.SOCKS_USERNAME]?:"",
            socksPassword = prefs[SettingsKeys.SOCKS_PASSWORD]?:"",
            socksListen = prefs[SettingsKeys.SOCKS_LISTEN]?:"127.0.0.1",
            dnsIPv4 = prefs[SettingsKeys.DNS_IPV4] ?: "8.8.8.8,1.1.1.1",
            dnsIPv6 = prefs[SettingsKeys.DNS_IPV6] ?: "2001:4860:4860::8888",
            delayTestUrl = prefs[SettingsKeys.DELAY_TEST_URL] ?: DEFAULT_DELAY_TEST_URL,
            version = prefs[SettingsKeys.VERSION] ?: "1.0.0",
            xrayCoreVersion = prefs[SettingsKeys.XRAY_CORE_VERSION]?:"unknown",
            geoLiteInstall = prefs[SettingsKeys.GEO_LITE_INSTALL] == true,
            liveUpdateNotification = prefs[SettingsKeys.LIVE_UPDATE_NOTIFICATION] == true,
            bootAutoStart = prefs[SettingsKeys.BOOT_AUTO_START] == true,
            hexTunEnable =  prefs[SettingsKeys.HEX_TUN_ENABLE]?:true,
            hideFromRecents = prefs[SettingsKeys.HIDE_FROM_RECENTS] == true,
            domainStrategy = prefs[SettingsKeys.DOMAIN_STRATEGY]?: DomainStrategy.IP_IF_NON_MATCH,
            routingRules = prefs[SettingsKeys.ROUTING_RULES]?: defaultRoutes,
            routingMode = prefs[SettingsKeys.ROUTING_MODE] ?: RoutingMode.ROUTE
        )

    }

    val packagesFlow = context.dataStore.data.map { prefs ->
        Gson().fromJson<MutableList<String>>(prefs[SettingsKeys.ALLOW_PACKAGES], listType) ?: emptyList()
    }

    suspend fun setRoutingMode(@RoutingMode mode: Int) {
        context.dataStore.edit {
            it[SettingsKeys.ROUTING_MODE] = mode
        }
    }

    suspend fun setDarkMode(@Theme darkMode: Int) {
        context.dataStore.edit {
            it[SettingsKeys.DARK_MODE] = darkMode
        }
    }

    suspend fun setDomainStrategy(@DomainStrategy domainStrategy: Int) {
        context.dataStore.edit {
            it[SettingsKeys.DOMAIN_STRATEGY] = domainStrategy
        }
    }

    suspend fun setRoutingRules(rules: List<Rule>) {
        val rulesString = gson.toJson(rules)
        context.dataStore.edit {
            it[SettingsKeys.ROUTING_RULES] = rulesString
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

    suspend fun setGeoLiteInstall(installed: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.GEO_LITE_INSTALL] = installed
        }
    }

    suspend fun setLiveUpdateNotification(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.LIVE_UPDATE_NOTIFICATION] = enable
        }
    }

    suspend fun setBootAutoStart(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.BOOT_AUTO_START] = enable
        }
    }

    suspend fun setHexTunState(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.HEX_TUN_ENABLE] = enable
        }
    }

    suspend fun setHideFromRecentsState(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.HIDE_FROM_RECENTS] = enable
        }
    }

    suspend fun setSocksUsername(username: String) {
        context.dataStore.edit {
            it[SettingsKeys.SOCKS_USERNAME] = username
        }
    }

    suspend fun setSocksPassword(password: String) {
        context.dataStore.edit {
            it[SettingsKeys.SOCKS_PASSWORD] = password
        }
    }

    suspend fun setSocksListen(address: String) {
        context.dataStore.edit {
            it[SettingsKeys.SOCKS_LISTEN] = address
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