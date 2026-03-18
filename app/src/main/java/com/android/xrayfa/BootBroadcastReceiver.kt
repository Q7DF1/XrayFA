package com.android.xrayfa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.xrayfa.common.di.qualifier.Background
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.core.XrayBaseServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Listen for power-on and power-off broadcasts.
 * If automatic startup is enabled during power-on,
 * it will automatically shut down during power-off.
 */
class BootBroadcastReceiver
@Inject constructor(
    val manager: XrayBaseServiceManager,
    @Background private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
): BroadcastReceiver() {
    companion object {
        const val TAG = "BootBroadcastReceiver"
    }
    override fun onReceive(context: Context?, intent: Intent?) {

        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d("DeviceEvent", "Device Booted!")
                context?.let {
                    coroutineScope.launch {
                        val enable = settingsRepository.settingsFlow.first().bootAutoStart;
                        Log.d(TAG, "onReceive: Boot auto start enable: $enable")
                        if (enable) {
                            manager.startXrayBaseService(it)
                        }

                    }
                }
            }

            Intent.ACTION_SHUTDOWN -> {
                Log.d("DeviceEvent", "Device Shutting Down!")
                context?.let {
                    coroutineScope.launch {
                        manager.stopXrayBaseService(it)
                    }
                }
            }
        }
    }
}