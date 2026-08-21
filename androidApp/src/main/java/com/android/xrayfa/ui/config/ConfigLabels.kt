package com.android.xrayfa.ui.config

import androidx.compose.runtime.Composable
import com.android.xrayfa.shared.navigation.ConfigFilterLabels
import com.android.xrayfa.shared.ui.config.ConfigUiLabels
import com.android.xrayfa.shared.ui.rememberConfigFilterLabels as sharedRememberConfigFilterLabels
import com.android.xrayfa.shared.ui.rememberConfigUiLabels as sharedRememberConfigUiLabels

@Composable
fun rememberConfigFilterLabels(): ConfigFilterLabels = sharedRememberConfigFilterLabels()

@Composable
fun rememberConfigUiLabels(): ConfigUiLabels = sharedRememberConfigUiLabels()
