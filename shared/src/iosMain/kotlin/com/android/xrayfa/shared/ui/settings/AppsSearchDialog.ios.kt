package com.android.xrayfa.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun appsSearchDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false)

@Composable
internal actual fun AppsSearchDialogBars() = Unit
