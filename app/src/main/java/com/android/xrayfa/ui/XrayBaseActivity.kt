package com.android.xrayfa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.android.xrayfa.XrayFAApplication
import com.android.xrayfa.common.repository.DARK_MODE
import com.android.xrayfa.common.repository.LIGHT_MODE
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme

abstract class XrayBaseActivity(
): ComponentActivity(){

    @Composable
    abstract fun Content()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as XrayFAApplication
        enableEdgeToEdge()
        setContent {
            val theme = app.isDarkTheme.collectAsState()
            V2rayForAndroidUITheme(
                darkTheme = when (theme.value) {
                    LIGHT_MODE -> false
                    DARK_MODE -> true
                    else -> isSystemInDarkTheme()
                }
            ) {
                Content()
            }
        }
    }
}