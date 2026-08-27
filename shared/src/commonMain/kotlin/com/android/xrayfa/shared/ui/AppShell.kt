package com.android.xrayfa.shared.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.datastore.SettingsState
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.ui.theme.XrayTheme
import org.koin.compose.koinInject

/**
 * Shared Compose shell. Decompose [RootContent] drives Config/Home pager plus overlays.
 * When [applyTheme] is true (iOS), Material dark/light follows Settings `darkMode`.
 */
@Composable
fun AppShell(
    rootComponent: RootComponent,
    applyTheme: Boolean = true,
) {
    val content: @Composable () -> Unit = {
        RootContent(
            component = rootComponent,
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (applyTheme) {
        val settingsRepository: SettingsRepository = koinInject()
        val settings by settingsRepository.settingsFlow.collectAsState(initial = SettingsState())
        val darkTheme = Theme.fromCode(settings.darkMode).resolvesToDark(isSystemInDarkTheme())
        XrayTheme(darkTheme = darkTheme, content = content)
    } else {
        content()
    }
}
