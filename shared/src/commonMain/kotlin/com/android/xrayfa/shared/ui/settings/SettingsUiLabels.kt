package com.android.xrayfa.shared.ui.settings

data class SettingsUiLabels(
    val title: String = "Settings",
    val generalSectionTitle: String = "General",
    val networkSectionTitle: String = "Network",
    val themeTitle: String = "Theme",
    val themeDescription: String = "Choose light, dark, or follow system",
    val lightModeLabel: String = "Light",
    val darkModeLabel: String = "Dark",
    val autoModeLabel: String = "Auto",
    val bootAutoStartTitle: String = "Start on boot",
    val bootAutoStartDescription: String = "Automatically start VPN when device boots",
    val hideFromRecentsTitle: String = "Hide from recents",
    val hideFromRecentsDescription: String = "Exclude app from recent apps list",
    val lanSocksProxyTitle: String = "LAN SOCKS proxy",
    val lanSocksProxyDescription: String = "Listen on all interfaces for SOCKS proxy",
    val lanHttpProxyTitle: String = "LAN HTTP proxy",
    val lanHttpProxyDescription: String = "Enable HTTP proxy on local network",
    val subscriptionSectionTitle: String = "Subscription",
    val sendHwidTitle: String = "Send HWID",
    val sendHwidDescription: String = "Include device HWID in subscription requests",
)
