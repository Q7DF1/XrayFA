package com.android.xrayfa

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.V2rayFAContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.dao.LinkDatabase
import com.android.xrayfa.repository.LinkRepository
import com.android.xrayfa.viewmodel.XrayViewmodelFactory
import javax.inject.Inject

class MainActivity @Inject constructor(
) : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = LinkDatabase.getLinkDatabase(this)
        val linkRepository = LinkRepository(database.LinkDao())
        val viewmodel =
            ViewModelProvider(this, XrayViewmodelFactory(linkRepository))[XrayViewmodel::class.java]
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                V2rayFAContainer(viewmodel)
            }
        }
    }

}
