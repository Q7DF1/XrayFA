package com.android.xrayfa.shared.vpn

import com.android.xrayfa.vpn.readVpnTrafficSpeedsKbps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Polls App Group traffic speeds written by PacketTunnel (same 3s cadence as Android
 * [com.android.xrayfa.core.XrayCoreManager.startTrafficDetection]).
 */
class IosTrafficStatsSource(
    scope: CoroutineScope,
) : TrafficStatsSource {
    private val _speedsKbps = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val speedsKbps: Flow<Pair<Double, Double>> = _speedsKbps.asSharedFlow()

    init {
        scope.launch {
            _speedsKbps.emit(0.0 to 0.0)
            delay(TRAFFIC_POLL_INTERVAL_MS)
            while (true) {
                _speedsKbps.emit(readVpnTrafficSpeedsKbps())
                delay(TRAFFIC_POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val TRAFFIC_POLL_INTERVAL_MS = 3_000L
    }
}
