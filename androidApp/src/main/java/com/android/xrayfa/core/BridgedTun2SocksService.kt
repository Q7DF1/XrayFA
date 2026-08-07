package com.android.xrayfa.core

import android.content.Context
import android.util.Log
import com.android.xrayfa.nativebridge.TunBridge
import xrayfa.tun2socks.Tun2SocksService
import xrayfa.tun2socks.utils.Tun2SocksConfigUtil

/**
 * [Tun2SocksService] backed by [TunBridge] from `:core:native-bridge`.
 * Mirrors legacy [xrayfa.tun2socks.TProxyService] orchestration (config path + running flag).
 */
class BridgedTun2SocksService(
    private val context: Context,
    private val util: Tun2SocksConfigUtil,
    private val tunBridge: TunBridge,
) : Tun2SocksService {

    var running: Boolean = false

    override suspend fun startTun2Socks(fd: Int) {
        val path = util.configure(context)
        try {
            val started = tunBridge.startTun2Socks(path, fd)
            if (!started) {
                Log.e(TAG, "startTun2Socks: native start failed or already running")
            }
            running = true
        } catch (e: Exception) {
            Log.e(TAG, "startTun2Socks: ${e.message}")
        }
    }

    override suspend fun stopTun2Socks() {
        if (running) {
            tunBridge.stopTun2Socks()
            running = false
        }
    }

    override fun isRunning(): Boolean = running

    private companion object {
        const val TAG = "TProxyService"
    }
}
