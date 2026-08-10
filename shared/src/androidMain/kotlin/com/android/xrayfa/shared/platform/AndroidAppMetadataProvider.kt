package com.android.xrayfa.shared.platform

import android.content.Intent
import androidx.core.net.toUri

class AndroidAppMetadataProvider(
    private val context: android.content.Context,
) : AppMetadataProvider {
    override fun getAppVersion(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return packageInfo.versionName ?: "unknown"
    }

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
