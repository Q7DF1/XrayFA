package com.android.xrayfa.shared.ui.home

data class HomeUiLabels(
    val connectedLabel: String = "Connected",
    val disconnectedLabel: String = "Not connected",
    val connectedHint: String = "Tap the button to disconnect",
    val disconnectedHint: String = "Tap the button to connect",
    val uploadLabel: String = "Upload",
    val downloadLabel: String = "Download",
    val connectionDetailsHeader: String = "Connection Details",
    val unknownProtocolLabel: String = "Unknown",
    val emptyNodeMessage: String = "select configuration first",
    val configNotReadyMessage: String = "Configuration not ready",
)
