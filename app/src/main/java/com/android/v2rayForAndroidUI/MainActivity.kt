package com.android.v2rayForAndroidUI

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.ui.component.V2rayFAContainer
import com.android.v2rayForAndroidUI.ui.theme.V2rayForAndroidUITheme
import com.android.v2rayForAndroidUI.viewmodel.XrayViewmodel
import com.android.v2rayForAndroidUI.V2rayBaseService
import javax.inject.Inject

class MainActivity @Inject constructor(
) : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("lishien", "onCreate: lishien++ ${applicationInfo.nativeLibraryDir}")

        val viewmodel = ViewModelProvider(this)[XrayViewmodel::class.java]

        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    V2rayFAContainer(modifier = Modifier.padding(innerPadding),viewmodel)
                }
            }
        }
    }

}
