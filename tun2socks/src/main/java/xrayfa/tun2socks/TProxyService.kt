package xrayfa.tun2socks

import android.content.Context
import android.util.Log
import xrayfa.tun2socks.qualifier.Application
import xrayfa.tun2socks.utils.Tun2SocksConfigUtil
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
open class TProxyService @Inject constructor(
    @Application private val context: Context,
    private val util: Tun2SocksConfigUtil
) : Tun2SocksService {

    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }

        @JvmStatic
        external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        external fun TProxyStopService()

        @JvmStatic
        external fun TProxyGetStats(): LongArray
    }


    override suspend fun startTun2Socks(fd: Int) {
        val path = util.configure(context)
        try {
            TProxyStartService(path, fd)
        } catch (e: Exception) {
            Log.e("TProxyService", "startTun2Socks: ${e.message}")
        }
    }

    override suspend fun stopTun2Socks() {
        TProxyStopService()
    }


}
