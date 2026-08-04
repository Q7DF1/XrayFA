package com.android.xrayfa.common.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.xrayfa.common.utils.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Due to module dependencies, we cannot directly use the `com.android.xrayfa.model.RuleObject` object here.
 * Therefore, we can only define an identical one, serialize it into JSON,
 * and then deserialize it back into `RuleObject` when needed.
 */
data class SettingsState(
    val darkMode: Int = 0,
    val ipV6Enable: Boolean = false,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val lanHttpProxyEnable: Boolean = false,
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
    val domainStrategy: Int = DomainStrategy.IP_IF_NON_MATCH.code,
    val routingRules: String = defaultRoutes,
    val routingMode: Int = RoutingMode.ROUTE.code,
    val hwid: String = "",
    val sendHwid: Boolean = true
)
object SettingsKeys {
    val DARK_MODE = intPreferencesKey("dark_mode")
    val IPV6_ENABLE = booleanPreferencesKey("ipv6_enable")
    val SOCKS_PORT = intPreferencesKey("socks_port")
    val HTTP_PORT = intPreferencesKey("http_port")
    val LAN_HTTP_PROXY_ENABLE = booleanPreferencesKey("lan_http_proxy_enable")
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
    val HWID = stringPreferencesKey("hwid")
    val SEND_HWID = booleanPreferencesKey("send_hwid")
}

const val DEFAULT_DELAY_TEST_URL = "https://www.google.com"

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val logger: Logger,
) : ConfigParserSettingsProvider {

    companion object {
        private const val TAG = "SettingsRepository"
    }

    val settingsFlow = dataStore.data.map { prefs ->
        SettingsState(
            darkMode = prefs[SettingsKeys.DARK_MODE] ?: 0,
            ipV6Enable = prefs[SettingsKeys.IPV6_ENABLE] == true,
            socksPort = prefs[SettingsKeys.SOCKS_PORT] ?: 10808,
            httpPort = prefs[SettingsKeys.HTTP_PORT] ?: 10809,
            lanHttpProxyEnable = prefs[SettingsKeys.LAN_HTTP_PROXY_ENABLE] == true,
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
            domainStrategy = prefs[SettingsKeys.DOMAIN_STRATEGY] ?: DomainStrategy.IP_IF_NON_MATCH.code,
            routingRules = prefs[SettingsKeys.ROUTING_RULES]?: defaultRoutes,
            routingMode = prefs[SettingsKeys.ROUTING_MODE] ?: RoutingMode.ROUTE.code,
            hwid = prefs[SettingsKeys.HWID] ?: "",
            sendHwid = prefs[SettingsKeys.SEND_HWID] ?: true
        )

    }

    val packagesFlow = dataStore.data.map { prefs ->
        decodeStringList(prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]")
    }

    suspend fun setRoutingMode(mode: RoutingMode) {
        dataStore.edit {
            it[SettingsKeys.ROUTING_MODE] = mode.code
        }
    }

    suspend fun setDarkMode(darkMode: Theme) {
        dataStore.edit {
            it[SettingsKeys.DARK_MODE] = darkMode.code
        }
    }

    suspend fun setDomainStrategy(domainStrategy: DomainStrategy) {
        dataStore.edit {
            it[SettingsKeys.DOMAIN_STRATEGY] = domainStrategy.code
        }
    }

    suspend fun setRoutingRules(rules: List<Rule>) {
        val rulesString = encodeRules(rules)
        dataStore.edit {
            it[SettingsKeys.ROUTING_RULES] = rulesString
        }
    }
    suspend fun setIpV6Enable(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.IPV6_ENABLE] = enable
        }
    }

    suspend fun setSocksPort(port: Int) {
        dataStore.edit {
            it[SettingsKeys.SOCKS_PORT] = port
        }
    }

    suspend fun setHttpPort(port: Int) {
        dataStore.edit {
            it[SettingsKeys.HTTP_PORT] = port
        }
    }

    suspend fun setLanHttpProxyEnable(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.LAN_HTTP_PROXY_ENABLE] = enable
        }
    }

    suspend fun setDnsIPv4(dns: String) {
        dataStore.edit {
            it[SettingsKeys.DNS_IPV4] = dns
        }
    }

    suspend fun setDnsIPv6(dns: String) {
        dataStore.edit {
            it[SettingsKeys.DNS_IPV6] = dns
        }
    }
    suspend fun setXrayCoreVersion(version: String) {
        dataStore.edit {
            it[SettingsKeys.XRAY_CORE_VERSION] = version
        }
    }

    suspend fun setDelayTestUrl(url:String) {
        dataStore.edit {
            it[SettingsKeys.DELAY_TEST_URL] = url
        }
    }

    suspend fun setAllowedPackages(packages: List<String>) {
        val listJson = encodeStringList(packages)
        dataStore.edit {
            it[SettingsKeys.ALLOW_PACKAGES] = listJson
        }
    }

    suspend fun setGeoLiteInstall(installed: Boolean) {
        dataStore.edit {
            it[SettingsKeys.GEO_LITE_INSTALL] = installed
        }
    }

    suspend fun setLiveUpdateNotification(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.LIVE_UPDATE_NOTIFICATION] = enable
        }
    }

    suspend fun setBootAutoStart(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.BOOT_AUTO_START] = enable
        }
    }

    suspend fun setHexTunState(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.HEX_TUN_ENABLE] = enable
        }
    }

    suspend fun setHideFromRecentsState(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.HIDE_FROM_RECENTS] = enable
        }
    }

    suspend fun setSocksUsername(username: String) {
        dataStore.edit {
            it[SettingsKeys.SOCKS_USERNAME] = username
        }
    }

    suspend fun setSocksPassword(password: String) {
        dataStore.edit {
            it[SettingsKeys.SOCKS_PASSWORD] = password
        }
    }

    suspend fun setSocksListen(address: String) {
        dataStore.edit {
            it[SettingsKeys.SOCKS_LISTEN] = address
        }
    }

    suspend fun setSendHwid(enable: Boolean) {
        dataStore.edit {
            it[SettingsKeys.SEND_HWID] = enable
        }
    }

    suspend fun addAllowedPackages(packageName: String) {
        dataStore.edit { prefs ->
            val listJson = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"
            val list = decodeStringList(listJson).toMutableList()

            if (!list.contains(packageName)) {
                list.add(packageName)
            }
            logger.i(TAG, "addAllowedPackages: ${list.size}")
            prefs[SettingsKeys.ALLOW_PACKAGES] = encodeStringList(list)
        }
    }

    suspend fun removeAllowedPackage(packageName: String) {
        dataStore.edit { prefs ->
            val listJson = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"
            val list = decodeStringList(listJson)
            val newList = list.filter { it != packageName }
            prefs[SettingsKeys.ALLOW_PACKAGES] = encodeStringList(newList)
        }
    }

    suspend fun getAllowedPackages(): List<String> {
        val prefs = dataStore.data.first()
        val json = prefs[SettingsKeys.ALLOW_PACKAGES] ?: "[]"
        return decodeStringList(json)
    }

    override suspend fun getConfigParserSettings(): ConfigParserSettings {
        val settings = settingsFlow.first()
        return ConfigParserSettings(
            socksListen = settings.socksListen,
            socksPort = settings.socksPort,
            socksUserName = settings.socksUserName,
            socksPassword = settings.socksPassword,
            lanHttpProxyEnable = settings.lanHttpProxyEnable,
            httpPort = settings.httpPort,
            dnsIPv4 = settings.dnsIPv4,
            dnsIPv6 = settings.dnsIPv6,
            ipV6Enable = settings.ipV6Enable,
            domainStrategy = settings.domainStrategy,
            routingRules = settings.routingRules,
            routingMode = settings.routingMode,
            geoLiteInstall = settings.geoLiteInstall,
        )
    }

}