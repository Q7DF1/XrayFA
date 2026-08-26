package com.android.xrayfa.ui.navigation

/** External intents / shortcuts routed into [com.android.xrayfa.ui.AndroidAppShell]. */
sealed interface AndroidRootAction {
    data object OpenQrScan : AndroidRootAction

    data object ConnectVpn : AndroidRootAction

    data object DisconnectVpn : AndroidRootAction
}
