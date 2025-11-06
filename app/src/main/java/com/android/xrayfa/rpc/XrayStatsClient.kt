package com.android.xrayfa.rpc


import com.xray.app.stats.command.StatsServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xray.app.stats.command.QueryStatsRequest



class XrayStatsClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 10085
) {

    companion object {
        const val TAG = "XrayStatsClient"


        var isConnect: Boolean = false
    }


    private var channel: ManagedChannel? = null
    private var stub: StatsServiceGrpc.StatsServiceBlockingStub? = null

    fun connect() {
        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext() // Xray API 默认不加密
            .build()

        stub = StatsServiceGrpc.newBlockingStub(channel)
        isConnect = true
    }

    suspend fun getTraffic(tag: String): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val request = QueryStatsRequest.newBuilder()
            .setPattern("outbound>>>$tag>>>") // inbound 或 outbound
            .setReset(false)
            .build()

        val response = stub?.queryStats(request)
        var uplink = 0L
        var downlink = 0L
        response?.statList?.forEach {
            when {
                it.name.contains("uplink") -> uplink = it.value
                it.name.contains("downlink") -> downlink = it.value
            }
        }
        Pair(uplink, downlink)
    }

    fun shutdown() {
        stub = null
        channel?.shutdownNow()
        isConnect = false
    }
}
