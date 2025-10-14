package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.xrayfa.viewmodel.SettingsViewmodel

@Composable
fun SettingsContainer(
    viewmodel: SettingsViewmodel
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Settings Content",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}