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
import com.android.v2rayForAndroidUI.ui.theme.V2rayForAndroidUITheme
import hev.htproxy.V2rayVpnService

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    var v2rayCoreManager: V2rayCoreManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("lishien", "onCreate: lishien++ ${applicationInfo.nativeLibraryDir}")
        v2rayCoreManager = V2rayCoreManager(this)
        val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            activityResult ->
                if (activityResult.resultCode == RESULT_OK) {
                    startVpnServiceByToggle()
                }
        }

        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var vpnState by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        Text("hello")
                        Button(
                            onClick = {
                                if (!vpnState) {
                                    val intent = VpnService.prepare(this@MainActivity)
                                    if (intent != null) {
                                        launcher.launch(intent)
                                    }else {
                                        startVpnServiceByToggle()
                                    }
                                }else {
                                    stopVpnServiceByToggle()
                                }
                                vpnState = !vpnState
                            }
                        ) {
                            Text(
                                text =
                                    if (!vpnState) resources.getString(R.string.start_vpn)
                                    else resources.getString(R.string.stop_vpn)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startVpnServiceByToggle() {
        Log.i(TAG, "startVpnServiceByToggle: lishien__")
        val intent = Intent(this, V2rayVpnService::class.java).apply {
                action = "connect"
            }
        startForegroundService(intent)
    }

    private fun startV2rayCoreByToggle() {
        v2rayCoreManager?.startV2rayCore()
    }

    private fun stopV2rayCoreByToggle() {
        v2rayCoreManager?.stopV2rayCore()
    }

    private fun stopVpnServiceByToggle() {
        Log.i(TAG, "stopVpnServiceByToggle: lishien++")
        val intent = Intent(this, V2rayVpnService::class.java).apply {
            action = "disconnect"
        }
        startService(intent)
    }
}
