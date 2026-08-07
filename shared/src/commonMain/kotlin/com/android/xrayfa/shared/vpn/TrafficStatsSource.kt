package com.android.xrayfa.shared.vpn

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Cross-platform VPN traffic speeds (upload, download) in KB/s. */
interface TrafficStatsSource {
    val speedsKbps: Flow<Pair<Double, Double>>
}

object EmptyTrafficStatsSource : TrafficStatsSource {
    override val speedsKbps: Flow<Pair<Double, Double>> = flowOf(0.0 to 0.0)
}
