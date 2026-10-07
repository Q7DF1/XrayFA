package com.android.xrayfa.shared.platform

import androidx.compose.runtime.Composable

data class JsonConfigFile(val name: String, val text: String)

@Composable
expect fun rememberJsonConfigFilePicker(onResult: (JsonConfigFile?, String?) -> Unit): () -> Unit
