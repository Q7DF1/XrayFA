package com.android.xrayfa.ui

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.DetailContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodelFactory
import com.android.xrayfa.viewmodel.XrayViewmodel
import javax.inject.Inject

class DetailActivity
@Inject constructor(
    val detailViewmodelFactory: DetailViewmodelFactory
): ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val protocol = intent.getStringExtra(XrayViewmodel.EXTRA_PROTOCOL)
        val content = intent.getStringExtra(XrayViewmodel.EXTRA_LINK)
        val viewmodel =
            ViewModelProvider.create(this,detailViewmodelFactory)[DetailViewmodel::class.java]
        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                DetailContainer(
                    protocol = protocol!!,
                    content = content!!,
                    detailViewmodel = viewmodel
                )
            }
        }
    }
}