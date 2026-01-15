package com.android.xrayfa

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.android.xrayfa.ui.component.XrayFAContainer
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.ui.XrayBaseActivity
import com.android.xrayfa.viewmodel.XrayViewmodelFactory
import javax.inject.Inject

class MainActivity @Inject constructor(
    val xrayViewmodelFactory: XrayViewmodelFactory
) : XrayBaseActivity() {
    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    override fun Content() {
        val viewmodel =
            ViewModelProvider(this, xrayViewmodelFactory)[XrayViewmodel::class.java]
        checkNotificationPermission()
        XrayFAContainer(viewmodel)
    }

    companion object {
        const val TAG = "MainActivity"
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
