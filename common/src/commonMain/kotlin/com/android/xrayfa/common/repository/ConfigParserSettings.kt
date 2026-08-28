package com.android.xrayfa.common.repository

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
    val dnsIPv4: String,
    val dnsIPv6: String,
    val ipV6Enable: Boolean,
    val domainStrategy: Int,
    val routingRules: String,
    val routingMode: Int,
    val geoLiteInstall: Boolean,
)

interface ConfigParserSettingsProvider {
    suspend fun getConfigParserSettings(): ConfigParserSettings
}
