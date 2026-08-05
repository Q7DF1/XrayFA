package com.android.xrayfa.core

import android.content.Context
import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.core.XrayAssetPaths

class AndroidXrayAssetPaths(
    private val context: Context,
) : XrayAssetPaths {

    override val basePath: String
        get() = context.filesDir.absolutePath

    override val geoIpPath: String
        get() = "$basePath/$GEO_IP"

    override val geoSitePath: String
        get() = "$basePath/$GEO_SITE"

    override val geoLiteDatabasePath: String
        get() = "$basePath/$GEO_LITE"
}
