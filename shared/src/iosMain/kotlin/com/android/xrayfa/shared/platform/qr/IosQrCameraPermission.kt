package com.android.xrayfa.shared.platform.qr

/**
 * iOS camera permission bridge.
 * [requestHandler] is registered from Swift (iosApp) where AVFoundation APIs are stable.
 */
object IosQrCameraPermission {
    var requestHandler: ((onGranted: () -> Unit, onDenied: () -> Unit) -> Unit)? = null

    fun ensureAccess(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        val handler = requestHandler
        if (handler != null) {
            handler(onGranted, onDenied)
        } else {
            // Fallback when Swift registration is missing (e.g. unit tests).
            onGranted()
        }
    }
}
