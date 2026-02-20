package com.android.xrayfa.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodelFactory
import javax.inject.Inject

@Deprecated("single Activity")
class SettingsActivity
@Inject constructor(
    val factory: SettingsViewmodelFactory
): XrayBaseActivity() {

    @Composable
    override fun Content(isLandscape: Boolean) {
        val viewmodel = ViewModelProvider.create(this, factory)[SettingsViewmodel::class.java]
//        SettingsContainer(viewmodel)
    }
}