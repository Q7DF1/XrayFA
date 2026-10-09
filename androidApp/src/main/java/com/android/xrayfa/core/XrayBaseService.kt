package com.android.xrayfa.core

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.android.xrayfa.MainActivity
import com.android.xrayfa.MainActivity.Companion.ACTION_START_SERVICE
import com.android.xrayfa.MainActivity.Companion.ACTION_STOP_SERVICE
import com.android.xrayfa.R
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.repository.NodeRepository
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.shared.vpn.prepareJsonVpn
import com.android.xrayfa.config.JsonVpnConfig
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.core.StartOptions.Companion.EXTRA_START_OPTIONS
import com.android.xrayfa.helper.NotificationHelper
import xrayfa.tun2socks.utils.NetPreferences
import xrayfa.tun2socks.Tun2SocksService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
@SuppressLint("VpnServicePolicy")
class XrayBaseService constructor(
    private val tun2SocksService: Tun2SocksService,
    private val xrayCore: XrayCore,
    private val settingsRepo: SettingsRepository,
    private val notificationHelper: NotificationHelper,
    private val nodeRepository: NodeRepository,
): VpnService(){

    companion object {

        const val TAG = "XrayBaseService"

        const val CONNECT = "connect"

        const val DISCONNECT = "disconnect"

        const val RESTART = "restart"

        private val _statusFlow = MutableStateFlow(false)
        val statusFlow = _statusFlow.asStateFlow()


        fun updateStatus(isRunning: Boolean) {
            _statusFlow.tryEmit(isRunning)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val connectionMutex = kotlinx.coroutines.sync.Mutex()

    var tunFd: ParcelFileDescriptor? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action = intent?.action
        return when(action) {
            DISCONNECT -> {
                Log.i(TAG, "onStartCommand: stop...")
                serviceScope.launch {
                    connectionMutex.withLock {
                        stopXrayCoreService()
                        updateStatus(false)
                        updateToggleShortcut(false)
                        stopSelf()
                        notificationHelper.hideNotification()
                    }
                }

                START_NOT_STICKY
            }
            CONNECT -> {
                Log.i(TAG, "onStartCommand: start...")
                val options = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_START_OPTIONS,
                        StartOptions::class.java)
                } else {
                    intent.getParcelableExtra<StartOptions>(EXTRA_START_OPTIONS)
                }

                notificationHelper.showNotification(this)
                serviceScope.launch {
                    connectionMutex.lock()
                    val start = try {
                        val started = if (statusFlow.value) true else options?.let { startXrayCoreService(it) } ?: false
                        updateStatus(started)
                        updateToggleShortcut(started)
                        started
                    } finally { connectionMutex.unlock() }
                    // Collect traffic data for notification updates
                    if (start) {
                        xrayCore.trafficFlow.collect { data ->
                            notificationHelper.updateNotificationIfNeeded(data)
                        }
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                START_STICKY
            }
            RESTART -> {
                Log.i(TAG, "onStartCommand: restart...")
                serviceScope.launch {
                    val options = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_START_OPTIONS,
                            StartOptions::class.java)
                    } else {
                        intent.getParcelableExtra<StartOptions>(EXTRA_START_OPTIONS)
                    }
                    connectionMutex.lock()
                    try {
                        val prepared = options?.let { resolveCoreOptions(it) }
                        if (prepared == null) {
                            stopXrayCoreService()
                            updateStatus(false)
                            updateToggleShortcut(false)
                        } else {
                            stopXrayCoreService()
                            val started = startXrayCoreService(options, prepared)
                            updateStatus(started)
                            updateToggleShortcut(started)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Preflight failed before stopping the existing connection.
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@XrayBaseService, R.string.core_start_failed, Toast.LENGTH_SHORT).show()
                        }
                    } finally { connectionMutex.unlock() }
                    restartToast()
                }
                START_STICKY
            }
            else -> { START_NOT_STICKY }
        }
    }


    override fun onDestroy() {
        Log.i(TAG, "onDestroy: close VPN")
        super.onDestroy()
        // Cancel the service scope so the trafficFlow collector (running against the
        // singleton XrayCoreManager's SharedFlow) is torn down. Otherwise the suspended
        // collect keeps a slot in the SharedFlow that references this Service, leaking it.
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.hideNotification()
        updateStatus(false)
        tunFd?.close()
        tunFd = null
    }

    /**
     * Called when the system configuration changes at runtime — most importantly
     * when the user toggles the system dark/light theme. The notification is
     * rendered by SystemUI using the system theme, so we need to re-post it so
     * SystemUI applies the current notification theme.
     *
     * Note: this fires only while the service is running, which is exactly when
     * the foreground notification is visible.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged: uiMode=${newConfig.uiMode}")
        notificationHelper.refreshNotification()
    }



    private suspend fun startVpn(jsonConfig: Boolean = false) {
        val prefs  = NetPreferences(this)
        val builder = Builder()
        val settings = settingsRepo.settingsFlow.first()
        val dnsServers = if (jsonConfig) listOf(JsonVpnConfig.VPN_DNS) else settings.dnsIPv4.split(",").filter { it.isNotBlank() }

        if (dnsServers.isNotEmpty()) {
            dnsServers.forEach { builder.addDnsServer(it.trim()) }
        } else {
            builder.addDnsServer("8.8.8.8")
        }
        if (settings.ipV6Enable && !jsonConfig) {
            val dnsV6Servers = settings.dnsIPv6.split(",").filter { it.isNotBlank() }
            dnsV6Servers.forEach { builder.addDnsServer(it.trim()) }
        }
        val allowedPackages = settingsRepo.getAllowedPackages()
        if (!allowedPackages.isEmpty()) {
            allowedPackages.forEach {
                builder.addAllowedApplication(it)
            }
        }else {
            builder.addDisallowedApplication(applicationContext.packageName)
        }
        if (settings.ipV6Enable) {
            builder.addAddress(prefs.tunnelIpv6Address,prefs.tunnelIpv6Prefix)
            builder.addRoute("::", 0)
        }
        tunFd = builder.setSession(resources.getString(R.string.app_label))
            .addAddress(prefs.tunnelIpv4Address, prefs.tunnelIpv4Prefix)
            .addRoute("0.0.0.0",0)
            .setMtu(prefs.tunnelMtu)
            .setBlocking(false)
            .establish()
    }

    private fun stopVPN() {
        tunFd?.close()
        tunFd = null
    }

    @SuppressLint("ShowToast")
    private suspend fun restartToast() {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                applicationContext,
                R.string.service_restart_toast,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    private suspend fun resolveCoreOptions(options: StartOptions): CoreStartOptions {
        if (options.jsonNodeId <= 0) return options.toCoreStartOptions()
        val node = nodeRepository.loadLinksById(options.jsonNodeId).first() ?: error("JSON_CONFIG_ERROR")
        check(node.isJsonConfig)
        return CoreStartOptions(url = node.url, jsonConfig = prepareJsonVpn(node, settingsRepo.settingsFlow.first()))
    }

    private suspend fun startXrayCoreService(startOptions: StartOptions, prepared: CoreStartOptions? = null): Boolean {
        try {
            val coreOptions = prepared ?: resolveCoreOptions(startOptions)
            val settingState = settingsRepo.settingsFlow.first()
            startVpn(coreOptions.jsonConfig != null)
            val fd = tunFd?.fd ?: return false
            val started = if (settingState.hexTunEnable) {
                xrayCore.startXrayCore(coreOptions, 0).also { running ->
                    if (running) tun2SocksService.startTun2Socks(fd)
                }
            } else xrayCore.startXrayCore(coreOptions, fd)
            if (!started) stopXrayCoreService()
            return started
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            stopXrayCoreService()
            return false
        }
    }

    private suspend fun stopXrayCoreService() {
        if (tun2SocksService.isRunning()) tun2SocksService.stopTun2Socks()
        stopVPN()
        xrayCore.stopXrayCore()
    }

    // Call this method whenever VPN state changes
    fun updateToggleShortcut(isRunning: Boolean) {
        // Define the intent that the shortcut will fire
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "com.android.xrayfa.ACTION_TOGGLE_PROXY"
            putExtra("shortcut_action",if (isRunning) ACTION_STOP_SERVICE else ACTION_START_SERVICE)
        }

        // Configure shortcut label and icon based on state
        val label = if (isRunning) getString(R.string.stop) else getString(R.string.start)
        val iconRes = if (isRunning) R.drawable.ic_turn_on else R.drawable.ic_turn_off

        val shortcut = ShortcutInfoCompat.Builder(this, "shortcut_toggle")
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(this, iconRes))
            .setIntent(intent)
            .build()

        // Update the shortcut on the launcher
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

}
