package com.android.xrayfa.shared.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.ColumnScope
import com.android.xrayfa.shared.navigation.SettingsComponent
import com.android.xrayfa.shared.ui.rememberSettingsUiLabels
import com.android.xrayfa.shared.ui.settings.SharedAppsInfoScreen
import com.android.xrayfa.shared.ui.settings.SharedInProcessAppLogScreen

/**
 * Platform-specific hooks for [com.android.xrayfa.shared.ui.RootContent].
 * Android provides Apps/Logcat pickers and ViewModel-backed settings extras; iOS uses defaults.
 */
interface PlatformRootHooks {
    @Composable
    fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent)

    @Composable
    fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent)

    @Composable
    fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
    )

    @Composable
    fun LogcatScreen(onBack: () -> Unit)
}

private object DefaultPlatformRootHooks : PlatformRootHooks {
    @Composable
    override fun ColumnScope.SettingsGeneralExtras(component: SettingsComponent) = Unit

    @Composable
    override fun ColumnScope.SettingsNetworkExtras(component: SettingsComponent) = Unit

    @Composable
    override fun AppsScreen(
        component: SettingsComponent,
        onBack: () -> Unit,
    ) {
        SharedAppsInfoScreen(
            component = component,
            onBack = onBack,
            labels = rememberSettingsUiLabels(),
        )
    }

    @Composable
    override fun LogcatScreen(onBack: () -> Unit) {
        SharedInProcessAppLogScreen(
            onBack = onBack,
            labels = rememberSettingsUiLabels(),
        )
    }
}

val LocalPlatformRootHooks = staticCompositionLocalOf<PlatformRootHooks> { DefaultPlatformRootHooks }
