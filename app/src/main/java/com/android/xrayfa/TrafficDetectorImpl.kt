package com.android.xrayfa

import com.android.xrayfa.rpc.XrayStatsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TrafficDetectorImpl: TrafficDetector  {

    private val trafficDetectJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + trafficDetectJob)

    private val trafficChannel = Channel<Pair<Long, Long>>(capacity = 0)

    override fun startTrafficDetection() {
        serviceScope.launch(Dispatchers.IO) {
            var upSpeed: Long
            var downSpeed: Long
            val client = XrayStatsClient()
            client.connect()
            var lastUp = 0L
            var lastDown = 0L
            while (XrayStatsClient.isConnect) {
                val (uplink, downlink) = client.getTraffic("proxy")
                upSpeed = (uplink - lastUp) / 1024
                downSpeed= (downlink - lastDown) / 1024
                lastUp = uplink
                lastDown = downlink
                //put in container

                trafficChannel.send(Pair(upSpeed, downSpeed))
            }
        }
    }

    override fun stopTrafficDetection() {
        serviceScope.cancel()
        trafficChannel.close()
    }

    override suspend fun consumeTraffic(onConsume: suspend (Pair<Long, Long>) -> Unit) {
        for (pair in trafficChannel) {
            onConsume(pair)
            delay(1000L) // delay 1s
        }
    }
}