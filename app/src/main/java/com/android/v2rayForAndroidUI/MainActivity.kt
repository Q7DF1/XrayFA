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
import com.android.v2rayForAndroidUI.model.protocol.Protocol
import com.android.v2rayForAndroidUI.ui.component.V2rayFAContainer
import com.android.v2rayForAndroidUI.ui.theme.V2rayForAndroidUITheme
import hev.htproxy.V2rayBaseService
import javax.inject.Inject

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("lishien", "onCreate: lishien++ ${applicationInfo.nativeLibraryDir}")
        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            activityResult ->
                if (activityResult.resultCode == RESULT_OK) {
                    startVpnServiceByToggle()
                    //startV2rayCoreByToggle()
                }
        }

        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    V2rayFAContainer(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun startVpnServiceByToggle() {
        Log.i(TAG, "startVpnServiceByToggle: lishien__")
        val intent = Intent(this, V2rayBaseService::class.java).apply {
                action = "connect"
            }
        startForegroundService(intent)
    }

    private fun stopVpnServiceByToggle() {
        Log.i(TAG, "stopVpnServiceByToggle: lishien++")
        val intent = Intent(this, V2rayBaseService::class.java).apply {
            action = "disconnect"
        }
        startService(intent)
    }
}
