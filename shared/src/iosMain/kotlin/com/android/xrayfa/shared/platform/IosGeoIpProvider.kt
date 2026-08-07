package com.android.xrayfa.shared.platform

import com.android.xrayfa.common.core.GeoIpProvider

/** GeoIP stub until MMDB reader lands on iOS. */
class IosGeoIpProvider : GeoIpProvider {
    override fun countryIsoFromIp(ip: String): String = ""
}
