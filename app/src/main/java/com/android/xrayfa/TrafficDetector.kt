package com.android.xrayfa

import java.util.function.Consumer


/**
 * Traffic detector, used to calculate upload and download speeds for front-end display
 */
interface TrafficDetector {

    fun startTrafficDetection()

    fun stopTrafficDetection()


    fun addConsumer(consume: Consumer<Pair<Double, Double>>)

    suspend fun consumeTraffic()


}