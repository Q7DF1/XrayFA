package com.android.xrayfa.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.xrayfa.viewmodel.SettingsViewmodel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContainer(
    viewmodel: SettingsViewmodel
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {Text("settings")},
                navigationIcon = { Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "settings"
                ) },
            )
        }
    ) { innerPadding ->
        SettingsScreen(
            viewmodel = viewmodel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}