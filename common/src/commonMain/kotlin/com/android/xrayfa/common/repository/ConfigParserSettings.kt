package com.android.xrayfa.common.repository

import com.android.xrayfa.common.config.DnsDefaults

/**
 * Settings snapshot consumed by Xray config parsers.
 *
 * Decouples `:domain` parsers from [SettingsRepository] / DataStore while
 * preserving the same field values used when building Xray JSON.
 */
data class ConfigParserSettings(
    val socksListen: String,
    val socksPort: Int,
    val socksUserName: String,
    val socksPassword: String,
    val lanHttpProxyEnable: Boolean,
    val httpPort: Int,
    val ipV6Enable: Boolean,
    val domainStrategy: Int,
    val routingRules: String,
    val routingMode: Int,
    val geoLiteInstall: Boolean,
    val dnsOverHttpsEnabled: Boolean = true,
    val dnsOverHttpsServers: String = DnsDefaults.DEFAULT_DOH_SERVERS,
    val udpDnsServers: String = DnsDefaults.DEFAULT_UDP_DNS_SERVERS,
)

interface ConfigParserSettingsProvider {
    suspend fun getConfigParserSettings(): ConfigParserSettings
}
