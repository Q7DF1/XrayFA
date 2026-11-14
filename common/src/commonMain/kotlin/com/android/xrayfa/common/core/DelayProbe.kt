package com.android.xrayfa.common.core

import kotlin.coroutines.cancellation.CancellationException

/** Delay UI sentinels aligned with Android `XrayViewmodel` (`-1` testing, `-2` timeout). */
object DelayMeasurement {
    const val TESTING_SENTINEL = -1L
    const val TIMEOUT_SENTINEL = -2L
    const val HOME_TIMEOUT_MS = 5_000L
}

fun mapNativeDelayResult(rawMs: Long): Long =
    if (rawMs <= 0L) DelayMeasurement.TIMEOUT_SENTINEL else rawMs

fun homeDelayTestEnabled(vpnConnected: Boolean, testing: Boolean): Boolean =
    vpnConnected && !testing

fun configDelayTestAllEnabled(testingAll: Boolean): Boolean = !testingAll

/**
 * Home uses the live core when it reports a positive delay (Android VPN path).
 * Otherwise falls back to [measureOutboundDelay] for the selected node (iOS: core
 * runs in the Network Extension, so live measure is always unavailable in-app).
 */
class DelayProbe(
    private val measureLive: (testUrl: String) -> Long,
    private val parseConfig: suspend (nodeUrl: String) -> String,
    private val measureOutbound: (config: String, testUrl: String) -> Long,
    private val parseNodeConfig: (suspend (String, String?) -> String)? = null,
    private val parseStartOptions: (suspend (CoreStartOptions) -> String)? = null,
) {
    suspend fun measureHome(
        testUrl: String,
        selectedNodeUrl: String?,
        muxSettings: String? = null,
        startOptions: CoreStartOptions? = null,
    ): Long {
        val live =
            try {
                measureLive(testUrl)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                -1L
            }
        if (live > 0L) return live
        val nodeUrl = selectedNodeUrl ?: return DelayMeasurement.TIMEOUT_SENTINEL
        return measureNode(nodeUrl, testUrl, muxSettings, startOptions)
    }

    suspend fun measureNode(
        nodeUrl: String,
        testUrl: String,
        muxSettings: String? = null,
        startOptions: CoreStartOptions? = null,
    ): Long {
        return try {
            val config = if (startOptions != null) {
                requireNotNull(parseStartOptions).invoke(startOptions)
            } else {
                parseNodeConfig?.invoke(nodeUrl, muxSettings) ?: parseConfig(nodeUrl)
            }
            mapNativeDelayResult(measureOutbound(config, testUrl))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            DelayMeasurement.TIMEOUT_SENTINEL
        }
    }
}
