package com.android.xrayfa.shared.vpn

import com.android.xrayfa.vpn.VpnController
import com.android.xrayfa.vpn.isConnected
import com.android.xrayfa.vpn.readVpnTrafficSpeedsKbps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn

/**
 * Polls App Group traffic speeds written by PacketTunnel (same 3s cadence as Android
 * [com.android.xrayfa.core.XrayCoreManager.startTrafficDetection]).
 * Polling runs only while [VpnController.state] is connected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosTrafficStatsSource(
    scope: CoroutineScope,
    private val vpnController: VpnController,
) : TrafficStatsSource {
    override val speedsKbps: Flow<Pair<Double, Double>> =
        vpnController.state
            .flatMapLatest { vpnState ->
                if (!vpnState.isConnected) {
                    flowOf(0.0 to 0.0)
                } else {
                    trafficPollingFlow()
                }
            }
            .shareIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1,
            )

    private fun trafficPollingFlow(): Flow<Pair<Double, Double>> =
        flow {
            emit(0.0 to 0.0)
            while (true) {
                delay(TRAFFIC_POLL_INTERVAL_MS)
                emit(readVpnTrafficSpeedsKbps())
            }
        }

    private companion object {
        const val TRAFFIC_POLL_INTERVAL_MS = 3_000L
    }
}
