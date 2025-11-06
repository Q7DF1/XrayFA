package xrayfa.tun2socks.utils

import android.content.Context
import android.util.Log
import com.android.xrayfa.common.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import javax.inject.Inject

class Tun2SocksConfigUtil
@Inject constructor(
    private val settingsRepo: SettingsRepository
) {
    companion object {
        const val TAG = "Tun2SocksConfigUtil"
    }
     suspend fun configure(context: Context): String {
         val tproxyFile = File(context.cacheDir, "tproxy.conf")
         val settingState = settingsRepo.settingsFlow.first()
         val prefs = NetPreferences(context)
         try {
            if (!tproxyFile.exists()) {
                tproxyFile.createNewFile()
            }

            val tproxyConf = buildString {
                appendLine("misc:")
                appendLine("  task-stack-size: ${prefs.taskStackSize}")
                appendLine("tunnel:")
                appendLine("  mtu: ${prefs.tunnelMtu}")
                appendLine("socks5:")
                appendLine("  port: ${settingState.socksPort}")
                appendLine("  address: '${prefs.socksAddress}'")
                appendLine("  udp: '${if (prefs.udpInTcp) "tcp" else "udp"}'")

                if (prefs.socksUsername.isNotEmpty() && prefs.socksPassword.isNotEmpty()) {
                    appendLine("  username: '${prefs.socksUsername}'")
                    appendLine("  password: '${prefs.socksPassword}'")
                }

                if (prefs.remoteDns) {
                    appendLine("mapdns:")
                    appendLine("  address: ${prefs.mappedDns}")
                    appendLine("  port: 53")
                    appendLine("  network: 240.0.0.0")
                    appendLine("  netmask: 240.0.0.0")
                    appendLine("  cache-size: 10000")
                }
            }
             Log.i(TAG, "configure: $tproxyConf")
             tproxyFile.writeText(tproxyConf)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return tproxyFile.absolutePath
    }

}