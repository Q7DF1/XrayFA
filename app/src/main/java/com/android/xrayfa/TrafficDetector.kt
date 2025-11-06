package com.android.xrayfa

import kotlinx.coroutines.CoroutineScope

/**
 * Traffic detector, used to calculate upload and download speeds for front-end display
 */
interface TrafficDetector {

    fun startTrafficDetection()

    fun stopTrafficDetection()


    suspend fun consumeTraffic(onConsume: suspend (Pair<Long, Long>) -> Unit)

}