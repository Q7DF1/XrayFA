package com.android.xrayfa.shared.navigation

import com.android.xrayfa.datastore.DomainStrategy
import com.android.xrayfa.datastore.RoutingMode
import com.android.xrayfa.datastore.Rule
import com.android.xrayfa.datastore.SettingsState

import com.arkivanov.decompose.value.Value

interface SettingsComponent {
    val state: Value<SettingsState>

    fun onSetTheme(themeCode: Int)

    fun onSetBootAutoStart(enable: Boolean)

    fun onSetHideFromRecents(enable: Boolean)

    fun onSetLanSocksProxyEnable(enable: Boolean)

    fun onSetLanHttpProxyEnable(enable: Boolean)

    fun onSetSendHwid(enable: Boolean)

    fun onSetSocksPort(port: Int)

    fun onSetHttpPort(port: Int)

    fun onSetSocksUsername(username: String)

    fun onSetSocksPassword(password: String)

    fun onSetDnsIPv4(dns: String)

    fun onSetDnsIPv6(dns: String)

    fun onSetIpV6Enable(enable: Boolean)

    fun onSetRoutingMode(mode: RoutingMode)

    fun onSetDomainStrategy(strategy: DomainStrategy)

    fun onSetRoutingRules(rules: List<Rule>)
}

/** Typealias for tab naming consistency with [HomeTabComponent]. */
typealias SettingsTabComponent = SettingsComponent
