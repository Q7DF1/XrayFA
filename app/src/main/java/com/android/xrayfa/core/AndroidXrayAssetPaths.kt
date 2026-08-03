package com.android.xrayfa.core

import android.content.Context
import com.android.xrayfa.common.GEO_IP
import com.android.xrayfa.common.GEO_LITE
import com.android.xrayfa.common.GEO_SITE
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.di.qualifier.Application
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidXrayAssetPaths
@Inject constructor(
    @Application private val context: Context,
) : XrayAssetPaths {

    override val geoIpPath: String
        get() = "${context.filesDir.absolutePath}/$GEO_IP"

    override val geoSitePath: String
        get() = "${context.filesDir.absolutePath}/$GEO_SITE"

    override val geoLiteDatabasePath: String
        get() = "${context.filesDir.absolutePath}/$GEO_LITE"
}
