package com.android.xrayfa.shared.navigation

import com.arkivanov.decompose.value.Value
import com.android.xrayfa.datastore.SettingsState

interface SettingsComponent {
    val state: Value<SettingsState>

    fun onSetTheme(themeCode: Int)

    fun onSetBootAutoStart(enable: Boolean)

    fun onSetHideFromRecents(enable: Boolean)

    fun onSetLanSocksProxyEnable(enable: Boolean)

    fun onSetLanHttpProxyEnable(enable: Boolean)
}

/** Typealias for tab naming consistency with [HomeTabComponent]. */
typealias SettingsTabComponent = SettingsComponent
