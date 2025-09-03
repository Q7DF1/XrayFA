package com.android.v2rayForAndroidUI

import android.app.ComponentCaller
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
import androidx.core.app.ServiceCompat.stopForeground
import com.android.v2rayForAndroidUI.ui.theme.V2rayForAndroidUITheme
import hev.htproxy.TProxyService

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
        val intent = Intent(this, TProxyService::class.java).apply {
                action = "connect"
            }
        startForegroundService(intent)
    }

    private fun stopVpnServiceByToggle() {
        Log.i(TAG, "stopVpnServiceByToggle: lishien++")
        val intent = Intent(this, TProxyService::class.java).apply {
            action = "disconnect"
        }
        startService(intent)
    }
}
