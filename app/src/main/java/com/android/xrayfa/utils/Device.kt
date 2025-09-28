package com.android.xrayfa.utils

import android.provider.Settings
import android.util.Base64
import android.util.Log

object Device {
    const val TAG = "Device"
    fun getDeviceIdForXUDPBaseKey(): String {
        return try {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate device ID", e)
            ""
        }
    }
}