package com.android.xrayfa.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.SubscriptionScreen
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import com.android.xrayfa.viewmodel.SubscriptionViewmodelFactory
import javax.inject.Inject

class SubscriptionActivity
@Inject constructor(
    val factory: SubscriptionViewmodelFactory
): XrayBaseActivity(){
    @Composable
    override fun Content() {
        val viewmodel = ViewModelProvider.create(this, factory)[SubscriptionViewmodel::class.java]
        SubscriptionScreen(viewmodel) { finish() }
    }


}