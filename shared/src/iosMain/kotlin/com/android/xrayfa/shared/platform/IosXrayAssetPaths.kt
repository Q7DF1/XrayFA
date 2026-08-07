@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.IosPlatformConstants
import com.android.xrayfa.common.core.XrayAssetPaths
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** App Group container paths for Xray assets (aligns with NE [Libv2rayInitCoreEnv]). */
class IosXrayAssetPaths : XrayAssetPaths {
    private val baseDir: String =
        NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(IosPlatformConstants.APP_GROUP_ID)
            ?.path
            ?: NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )!!.path!!

    override val basePath: String
        get() = baseDir

    override val geoIpPath: String
        get() = "$baseDir/$GEO_IP"

    override val geoSitePath: String
        get() = "$baseDir/$GEO_SITE"

    override val geoLiteDatabasePath: String
        get() = "$baseDir/$GEO_LITE"
}
