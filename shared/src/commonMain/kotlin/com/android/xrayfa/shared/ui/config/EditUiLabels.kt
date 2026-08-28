package com.android.xrayfa.shared.ui.config

data class EditUiLabels(
    val editTitle: String = "Edit",
    val addTitle: String = "Add",
    val backContentDescription: String = "Back",
    val saveContentDescription: String = "Save",
    val protocolSectionTitle: String = "Protocol",
    val basicSettingsTitle: String = "Basic Settings",
    val remarksLabel: String = "Remarks",
    val addressLabel: String = "Address",
    val portLabel: String = "Port (0-65535)",
    val protocolSettingsTitleFormat: String = "%s Settings",
    val uuidLabel: String = "UUID",
    val encryptionLabel: String = "Encryption (default: none)",
    val flowLabel: String = "Flow",
    val securityLabel: String = "Security",
    val passwordLabel: String = "Password",
    val methodLabel: String = "Method",
    val usernameOptionalLabel: String = "Username (optional)",
    val passwordOptionalLabel: String = "Password (optional)",
    val authLabel: String = "Auth",
    val sniLabel: String = "SNI",
    val alpnLabel: String = "ALPN",
    val obfuscationLabel: String = "Obfuscation",
    val obfuscationPasswordLabel: String = "Obfuscation Password",
    val allowInsecureLabel: String = "Allow Insecure",
    val transportSettingsTitle: String = "Transport Settings",
    val networkLabel: String = "Network",
    val wsPathLabel: String = "WS Path",
    val wsHostLabel: String = "WS Host",
    val grpcServiceNameLabel: String = "gRPC Service Name",
    val sniServerNameLabel: String = "SNI (Server Name Indication)",
    val fingerprintLabel: String = "Fingerprint",
    val publicKeyLabel: String = "Public Key",
    val shortIdLabel: String = "Short ID",
    val noneOptionLabel: String = "none",
) {
    fun protocolSettingsTitle(protocolName: String): String =
        protocolSettingsTitleFormat
            .replace("%1\$s", protocolName)
            .replace("%s", protocolName)
}
