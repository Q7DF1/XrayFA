package com.android.xrayfa.ui

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.xrayfa.ui.component.DetailContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import javax.inject.Inject

class DetailActivity @Inject constructor(): ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        setContent {
            V2rayForAndroidUITheme {
                DetailContainer()
            }
        }
    }
}