package com.android.xrayfa

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.android.xrayfa.ui.component.XrayFAContainer
import com.android.xrayfa.viewmodel.XrayViewmodel
import com.android.xrayfa.ui.XrayBaseActivity
import com.android.xrayfa.ui.navigation.ScanQR
import com.android.xrayfa.viewmodel.AppsViewmodel
import com.android.xrayfa.viewmodel.AppsViewmodelFactory
import com.android.xrayfa.viewmodel.DetailViewmodel
import com.android.xrayfa.viewmodel.DetailViewmodelFactory
import com.android.xrayfa.viewmodel.SettingsViewmodel
import com.android.xrayfa.viewmodel.SettingsViewmodelFactory
import com.android.xrayfa.viewmodel.SubscriptionViewmodel
import com.android.xrayfa.viewmodel.SubscriptionViewmodelFactory
import com.android.xrayfa.viewmodel.XrayViewmodelFactory
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainActivity @Inject constructor(
    val xrayViewmodelFactory: XrayViewmodelFactory,
    val detailViewmodelFactory: DetailViewmodelFactory,
    val settingsViewmodelFactory: SettingsViewmodelFactory,
    val subscriptionViewmodelFactory: SubscriptionViewmodelFactory,
    val appViewmodelFactory: AppsViewmodelFactory
) : XrayBaseActivity() {

    private lateinit var xrayViewmodel: XrayViewmodel

    private lateinit var settingsViewmodel: SettingsViewmodel
    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    override fun Content(isLandscape: Boolean) {

        val detailViewmodel =
            ViewModelProvider.create(this,detailViewmodelFactory)[DetailViewmodel::class.java]
        val subscriptionViewmodel = ViewModelProvider
            .create(this, subscriptionViewmodelFactory)[SubscriptionViewmodel::class.java]
        val appViewmodel =
            ViewModelProvider.create(this, appViewmodelFactory)[AppsViewmodel::class.java]

        checkNotificationPermission()
        XrayFAContainer(
            xrayViewmodel,
            detailViewmodel,
            settingsViewmodel,
            subscriptionViewmodel,
            appViewmodel
        )
    }

    companion object {
        const val TAG = "MainActivity"
        const val ACTION_OPEN_SCAN = "open_scan"
        const val ACTION_START_SERVICE = "start_service"
        const val ACTION_STOP_SERVICE = "stop_service"
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        xrayViewmodel =
            ViewModelProvider(this, xrayViewmodelFactory)[XrayViewmodel::class.java]
        settingsViewmodel = ViewModelProvider
            .create(this, settingsViewmodelFactory)[SettingsViewmodel::class.java]
        handleShortcutIntent(intent)
        lifecycleScope.launch {
            settingsViewmodel.settingsState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { state ->
                    val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                    activityManager.appTasks.forEach {
                        val taskInfo = it.taskInfo
                        val currentTaskId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            taskInfo.taskId
                        } else {
                            @Suppress("DEPRECATION")
                            taskInfo.id
                        }
                        if (currentTaskId == taskId) {
                            //set flag: Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            it.setExcludeFromRecents(state.hideFromRecents)
                        }
                    }
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


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent) {
        // Retrieve the extra defined in shortcuts.xml
        val action = intent.getStringExtra("shortcut_action")
        when(action) {
            ACTION_OPEN_SCAN -> {
                xrayViewmodel.setPaddingRoute(ScanQR { result ->
                    if (result.isEmpty()) {
                        Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
                    }else {
                        xrayViewmodel.addLink(result)
                    }
                })
            }
            ACTION_START_SERVICE -> {
                if (!xrayViewmodel.isServiceRunning()) {
                    xrayViewmodel.startXrayService(this)
                    finish()
                }
            }
            ACTION_STOP_SERVICE -> {
                if (xrayViewmodel.isServiceRunning()) {
                    xrayViewmodel.stopXrayService(this)
                    finish()
                }
            }
        }
    }

}
