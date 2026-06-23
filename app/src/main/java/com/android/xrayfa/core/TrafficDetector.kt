package com.android.xrayfa.core

import kotlinx.coroutines.flow.SharedFlow


/**
 * Traffic detector, used to calculate upload and download speeds for front-end display.
 * Consumers should collect [trafficFlow] to receive speed updates.
 */
interface TrafficDetector {

    val trafficFlow: SharedFlow<Pair<Double, Double>>

    fun startTrafficDetection()

    fun stopTrafficDetection()

}