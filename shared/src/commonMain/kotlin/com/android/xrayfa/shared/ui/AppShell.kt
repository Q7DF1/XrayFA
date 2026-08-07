package com.android.xrayfa.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.xrayfa.shared.navigation.RootComponent

/**
 * Minimal shared Compose shell (E.6). Decompose [RootContent] drives tab navigation (E.6e).
 */
@Composable
fun AppShell(rootComponent: RootComponent) {
    MaterialTheme {
        RootContent(
            component = rootComponent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
