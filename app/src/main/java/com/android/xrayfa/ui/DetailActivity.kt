package com.android.xrayfa.ui

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.android.xrayfa.ui.component.DetailContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme

class DetailActivity: ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                DetailContainer()
            }
        }
    }
}