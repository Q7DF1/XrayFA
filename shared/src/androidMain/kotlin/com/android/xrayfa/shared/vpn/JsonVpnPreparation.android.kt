package com.android.xrayfa.shared.vpn
import com.android.xrayfa.config.JsonVpnTransport
import com.android.xrayfa.datastore.SettingsState
actual fun jsonVpnTransport(settings: SettingsState) = JsonVpnTransport(
    nativeTun = !settings.hexTunEnable, socksPort = settings.socksPort,
    socksUser = settings.socksUserName, socksCredential = settings.socksPassword,
)
