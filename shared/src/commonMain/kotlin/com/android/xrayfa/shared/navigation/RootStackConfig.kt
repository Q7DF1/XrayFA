package com.android.xrayfa.shared.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface RootStackConfig {
    @Serializable data object Idle : RootStackConfig
    @Serializable data object Settings : RootStackConfig
    @Serializable data object Subscriptions : RootStackConfig
    @Serializable data object QrScanner : RootStackConfig
    @Serializable data object Apps : RootStackConfig
    @Serializable data object Logcat : RootStackConfig
    @Serializable data object RouteSettings : RootStackConfig
    @Serializable data class NodeEdit(val nodeId: Int) : RootStackConfig
}
