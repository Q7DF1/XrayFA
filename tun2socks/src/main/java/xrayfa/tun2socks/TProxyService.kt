package xrayfa.tun2socks

import android.content.Context
import android.util.Log
import xrayfa.tun2socks.utils.Tun2SocksConfigUtil

open class TProxyService constructor(
    private val context: Context,
    private val util: Tun2SocksConfigUtil,
) : Tun2SocksService {

    var running: Boolean = false
    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStartService(configPath: String, fd: Int): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyStopService(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyIsRunning(): Boolean

        @JvmStatic
        @Suppress("FunctionName")
        external fun TProxyGetStats(): LongArray
    }


    override suspend fun startTun2Socks(fd: Int) {
        val path = util.configure(context)
        try {
            val started = TProxyStartService(path, fd)
            if (!started) {
                Log.e("TProxyService", "startTun2Socks: native start failed or already running")
            }
            running = true
        } catch (e: Exception) {
            Log.e("TProxyService", "startTun2Socks: ${e.message}")
        }
    }

    override suspend fun stopTun2Socks() {
        if (running) {
            TProxyStopService()
            running = false
        }

    }

    override fun isRunning(): Boolean {
        return running
    }


}
