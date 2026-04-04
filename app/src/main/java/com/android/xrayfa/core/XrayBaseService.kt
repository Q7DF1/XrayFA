package com.android.xrayfa.core

import android.annotation.SuppressLint
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import com.android.xrayfa.R
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.helper.NotificationHelper
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_LINK
import com.android.xrayfa.viewmodel.XrayViewmodel.Companion.EXTRA_PROTOCOL
import xrayfa.tun2socks.utils.NetPreferences
import xrayfa.tun2socks.Tun2SocksService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SuppressLint("VpnServicePolicy")
class XrayBaseService
@Inject constructor(
    private val tun2SocksService: Tun2SocksService,
    private val xrayCoreManager: XrayCoreManager,
    private val settingsRepo: SettingsRepository,
    private val notificationHelper: NotificationHelper
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

    var tunFd: ParcelFileDescriptor? = null


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val action = intent?.action
        return when(action) {
            DISCONNECT -> {
                Log.i(TAG, "onStartCommand: stop...")
                serviceScope.launch {
                    stopXrayCoreService()
                    updateStatus(false)
                    stopSelf()
                    notificationHelper.hideNotification()
                }

                START_NOT_STICKY
            }
            CONNECT -> {
                Log.i(TAG, "onStartCommand: start...")
                val link = intent.getStringExtra(EXTRA_LINK)
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL)
                serviceScope.launch {
                    notificationHelper.showNotification()
                    xrayCoreManager.addConsumer { data->
                        notificationHelper.updateNotification(data)
                    }
                    startXrayCoreService(link!!,protocol!!)
                    updateStatus(true)
                }
                START_STICKY
            }
            RESTART -> {
                Log.i(TAG, "onStartCommand: restart...")
                serviceScope.launch {
                    val link = intent.getStringExtra(EXTRA_LINK)
                    val protocol = intent.getStringExtra(EXTRA_PROTOCOL)
                    stopXrayCoreService()
                    startXrayCoreService(link!!,protocol!!)
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
        tunFd?.close()
        tunFd = null
    }



    private suspend fun startVpn() {
        val prefs  = NetPreferences(this)
        val builder = Builder()
        val settings = settingsRepo.settingsFlow.first()
        val dnsServers = settings.dnsIPv4.split(",").filter { it.isNotBlank() }

        if (dnsServers.isNotEmpty()) {
            dnsServers.forEach { builder.addDnsServer(it.trim()) }
        } else {
            builder.addDnsServer("8.8.8.8")
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
    private suspend fun startXrayCoreService(link: String, protocol: String) {
        val settingState = settingsRepo.settingsFlow.first()
        startVpn()
        if (settingState.hexTunEnable) {
            xrayCoreManager.startXrayCore(link,protocol,0)
            tunFd?.let {
                tun2SocksService.startTun2Socks(it.fd)
            }
        }else {
            xrayCoreManager.startXrayCore(link,protocol,tunFd?.fd)
        }
    }

    private suspend fun stopXrayCoreService() {
        if (tun2SocksService.isRunning()) tun2SocksService.stopTun2Socks()
        stopVPN()
        xrayCoreManager.stopXrayCore()
    }

}