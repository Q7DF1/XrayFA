package com.android.xrayfa.shared.vpn
import com.android.xrayfa.common.config.DnsDefaults
import com.android.xrayfa.config.JsonVpnTransport
import com.android.xrayfa.datastore.SettingsState
// The extension uses a private loopback SOCKS channel, independent of Android settings.
actual fun jsonVpnTransport(settings: SettingsState) = JsonVpnTransport(
    dohServers = DnsDefaults.parseDohServers(settings.dnsOverHttpsServers),
    ipV6Enabled = settings.ipV6Enable,
)
