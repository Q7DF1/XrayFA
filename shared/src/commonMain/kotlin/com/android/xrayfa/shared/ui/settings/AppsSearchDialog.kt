package com.android.xrayfa.shared.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
internal expect fun appsSearchDialogProperties(): DialogProperties

/** Paint the dialog status bar with the same surface as the search page. */
@Composable
internal expect fun AppsSearchDialogBars()
