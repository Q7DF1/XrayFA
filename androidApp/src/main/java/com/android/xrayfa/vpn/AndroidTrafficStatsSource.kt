package com.android.xrayfa.vpn

import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.shared.vpn.TrafficStatsSource
import kotlinx.coroutines.flow.Flow

class AndroidTrafficStatsSource(
    private val xrayCore: XrayCore,
) : TrafficStatsSource {
    override val speedsKbps: Flow<Pair<Double, Double>> = xrayCore.trafficFlow
}
