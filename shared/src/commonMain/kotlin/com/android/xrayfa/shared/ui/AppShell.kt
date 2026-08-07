package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.RootComponent
import com.android.xrayfa.shared.ui.theme.XrayTheme

/**
 * Minimal shared Compose shell (E.6). Decompose [RootContent] drives tab navigation (E.6e).
 */
@Composable
fun AppShell(rootComponent: RootComponent) {
    XrayTheme {
        RootContent(
            component = rootComponent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
