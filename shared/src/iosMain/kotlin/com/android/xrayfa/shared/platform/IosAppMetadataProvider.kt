@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.shared.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

class IosAppMetadataProvider : AppMetadataProvider {
    override fun getAppVersion(): String {
        val bundle = NSBundle.mainBundle
        val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
        return when {
            version != null && build != null -> "$version ($build)"
            version != null -> version
            else -> "unknown"
        }
    }

    override fun getOsName(): String = "iOS"

    override fun getOsVersion(): String = UIDevice.currentDevice.systemVersion

    override fun getDeviceModel(): String = memScoped {
        val info = alloc<utsname>()
        if (uname(info.ptr) == 0) {
            val machine = info.machine.toKString()
            if (machine.isNotEmpty()) machine else UIDevice.currentDevice.model
        } else {
            UIDevice.currentDevice.model
        }
    }

    override fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(
            nsUrl,
            options = emptyMap<Any?, Any>(),
            completionHandler = null,
        )
    }
}
