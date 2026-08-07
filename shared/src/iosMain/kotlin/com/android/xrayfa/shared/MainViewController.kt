package com.android.xrayfa.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.android.xrayfa.shared.ui.AppShell
import platform.UIKit.UIViewController

/** iOS entry: embed in SwiftUI via [UIViewControllerRepresentable]. */
fun MainViewController(): UIViewController {
    IosKoinInit.ensureStarted()
    return ComposeUIViewController {
        AppShell()
    }
}
