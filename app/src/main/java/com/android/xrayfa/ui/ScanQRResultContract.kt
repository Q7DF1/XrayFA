package com.android.xrayfa.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class ScanQRResultContract: ActivityResultContract<Intent, String>() {

    companion object {
        const val SCAN_QR_EXTRA_RESULT = "com.android.xrayfa.scan_qr_result"
    }
    override fun createIntent(
        context: Context,
        input: Intent
    ): Intent  = input

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): String {
        return if (resultCode == Activity.RESULT_OK) {
            val result = intent?.getStringExtra(SCAN_QR_EXTRA_RESULT)
            return result ?: ""
        } else ""
    }

}