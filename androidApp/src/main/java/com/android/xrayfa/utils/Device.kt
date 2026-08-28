package com.android.xrayfa.utils

import android.provider.Settings
import android.util.Log
import com.android.xrayfa.common.utils.Base64Compat

object Device {
    const val TAG = "Device"

    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64Compat.encodeUrlSafeNoPadding(androidId.copyOf(32))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device ID", e)
            ""
        }
    }
}
