package com.android.xrayfa

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.V2rayFAContainer
import com.android.xrayfa.ui.theme.V2rayForAndroidUITheme
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.dao.LinkDatabase
import com.android.xrayfa.repository.LinkRepository
import com.android.xrayfa.viewmodel.XrayViewmodelFactory
import javax.inject.Inject

class MainActivity @Inject constructor(
    val xrayViewmodelFactory: XrayViewmodelFactory
) : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }


    @SuppressLint("SourceLockedOrientationActivity")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewmodel =
            ViewModelProvider(this, xrayViewmodelFactory)[XrayViewmodel::class.java]
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        checkNotificationPermission()
        enableEdgeToEdge()
        setContent {
            V2rayForAndroidUITheme {
                V2rayFAContainer(viewmodel)
            }
        }
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                //TODO migrate to after click the start button
            } else {
                Toast.makeText(this, "通知权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }

    fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    //todo migrate to after click the start button
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("notification permission")
                        .setMessage("need notification permission to keep service alive")
                        .setPositiveButton("grant") { _, _ ->
                            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("reject", null)
                        .show()
                }
                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            //todo migrate to before click the start button
        }
    }

}
