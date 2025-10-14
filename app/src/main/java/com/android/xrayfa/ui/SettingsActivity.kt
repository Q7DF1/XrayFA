package com.android.xrayfa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.DetailContainer
import com.android.xrayfa.ui.component.SettingsContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodelFactory
import javax.inject.Inject

class SettingsActivity
@Inject constructor(
    val factory: SettingsViewmodelFactory
): ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewmodel = ViewModelProvider.create(this, factory)[SettingsViewmodel::class.java]
        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                SettingsContainer(viewmodel)
            }
        }
    }
}