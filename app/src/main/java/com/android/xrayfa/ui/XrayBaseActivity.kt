package com.android.xrayfa.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
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
        enableEdgeToEdge()
        setContent {
            val theme = app.isDarkTheme.collectAsState()
            V2rayForAndroidUITheme(
                darkTheme = when (theme.value) {
                    Theme.LIGHT_MODE -> false
                    Theme.DARK_MODE -> true
                    else -> isSystemInDarkTheme()
                }
            ) {
                ShowContentWithOrientation { isLandscape -> Content(isLandscape) }
            }
        }
    }

}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ShowContentWithOrientation(
    content: @Composable (isLandscape: Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    content(isLandscape)
}