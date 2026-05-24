package com.android.xrayfa.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import com.android.xrayfa.XrayFAApplication
import com.android.xrayfa.common.repository.Theme
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme

abstract class XrayBaseActivity: ComponentActivity(){

    @Composable
    abstract fun Content(isLandscape: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as XrayFAApplication
        setContent {
            val theme = app.isDarkTheme.collectAsState()
            val darkTheme = when (theme.value) {
                Theme.LIGHT_MODE -> false
                Theme.DARK_MODE -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            V2rayForAndroidUITheme(
                darkTheme = darkTheme
            ) {
                Content(false)
            }
        }
    }

}

@Deprecated("use scene instead")
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ShowContentWithOrientation(
    content: @Composable (isLandscape: Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    content(isLandscape)
}