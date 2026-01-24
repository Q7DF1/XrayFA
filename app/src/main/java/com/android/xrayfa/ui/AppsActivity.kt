package com.android.xrayfa.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.AppsScreen
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.AppsViewmodelFactory
import javax.inject.Inject


@Deprecated("single Activity")
class AppsActivity
@Inject constructor(
    val viewmodelFactory: AppsViewmodelFactory
): XrayBaseActivity() {
    @Composable
    override fun Content(isLandScape: Boolean) {

        val viewmodel = ViewModelProvider.create(this,viewmodelFactory)[AppsViewmodel::class.java]
        AppsScreen(viewmodel)
    }
}