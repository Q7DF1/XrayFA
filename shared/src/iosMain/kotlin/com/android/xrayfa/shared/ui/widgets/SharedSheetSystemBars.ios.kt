package com.android.xrayfa.shared.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
internal actual fun sharedSheetDialogProperties(): DialogProperties =
    DialogProperties(usePlatformDefaultWidth = false)

@Composable
internal actual fun SharedSheetSystemBars() = Unit
