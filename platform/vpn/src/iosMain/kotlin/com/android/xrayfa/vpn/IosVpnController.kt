@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.vpn

import com.android.xrayfa.common.IosPlatformConstants
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.NetworkExtension.NEVPNConnection
import platform.NetworkExtension.NEVPNStatusConnected
import platform.NetworkExtension.NEVPNStatusConnecting
import platform.NetworkExtension.NEVPNStatusDisconnecting
import platform.NetworkExtension.NEVPNStatusInvalid
import platform.NetworkExtension.NEVPNStatusReasserting
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NETunnelProviderSession
import platform.darwin.NSObjectProtocol
import kotlin.coroutines.resume

private const val VPN_STATUS_NOTIFICATION = "NEVPNStatusDidChangeNotification"

/**
 * iOS [VpnController] using [NETunnelProviderManager] + App Group IPC with PacketTunnel.
 *
 * Runtime VPN requires Apple Network Extension entitlements; simulator builds compile-only.
 */
class IosVpnController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : VpnController {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    override val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    override val connectError: StateFlow<String?> = _connectError.asStateFlow()

    private var manager: NETunnelProviderManager? = null
    private var statusObserver: NSObjectProtocol? = null

    init {
        scope.launch {
            refreshManagerAndState()
            observeVpnStatusChanges()
        }
    }

    override suspend fun connect(): Boolean {
        if (IosAppGroupStorage.readPendingConfig().isNullOrBlank()) {
            _connectError.value = "No VPN configuration"
            return false
        }
        _connectError.value = null
        IosAppGroupStorage.clearTunnelLastError()
        return withContext(Dispatchers.Main) {
            runCatching {
                val tunnelManager = ensureManager()
                persistManager(tunnelManager)
                if (!startTunnelSession(tunnelManager)) {
                    return@withContext false
                }
                delay(TUNNEL_START_GRACE_MS)
                val connected =
                    _state.value.isConnected || IosAppGroupStorage.isTunnelConnected()
                if (connected) {
                    _connectError.value = null
                    true
                } else {
                    _connectError.value =
                        IosAppGroupStorage.readTunnelLastError()
                            ?: "VPN tunnel failed to start"
                    false
                }
            }.getOrElse { error ->
                _connectError.value = error.message ?: "VPN connect failed"
                false
            }
        }
    }

    override fun disconnect() {
        manager?.connection?.stopVPNTunnel()
        IosAppGroupStorage.setTunnelConnected(false)
        IosAppGroupStorage.clearTrafficSpeeds()
        _state.value = VpnState.Disconnected
    }

    override suspend fun restartIfNeeded() {
        if (!_state.value.isConnected) {
            return
        }
        disconnect()
        connect()
    }

    private suspend fun ensureManager(): NETunnelProviderManager {
        manager?.let { return it }
        val existing = loadManagers().firstOrNull()
        if (existing != null) {
            manager = existing
            return existing
        }
        val created = NETunnelProviderManager()
        val protocolConfig = NETunnelProviderProtocol()
        protocolConfig.providerBundleIdentifier = IosPlatformConstants.PACKET_TUNNEL_BUNDLE_ID
        protocolConfig.serverAddress = "XrayFA"
        created.protocolConfiguration = protocolConfig
        created.localizedDescription = "XrayFA"
        created.enabled = true
        manager = created
        return created
    }

    private suspend fun persistManager(tunnelManager: NETunnelProviderManager) {
        suspendCancellableCoroutine { continuation ->
            tunnelManager.saveToPreferencesWithCompletionHandler { saveError ->
                if (saveError != null) {
                    _connectError.value = saveError.localizedDescription
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                    return@saveToPreferencesWithCompletionHandler
                }
                tunnelManager.loadFromPreferencesWithCompletionHandler { _ ->
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            }
        }
    }

    private suspend fun startTunnelSession(tunnelManager: NETunnelProviderManager): Boolean =
        withContext(Dispatchers.Main) {
            memScoped {
                val session = tunnelManager.connection as? NETunnelProviderSession ?: return@withContext false
                val errorVar = alloc<ObjCObjectVar<NSError?>>()
                val started = session.startTunnelWithOptions(null, errorVar.ptr)
                updateStateFromConnection(tunnelManager.connection)
                if (!started) {
                    _connectError.value =
                        errorVar.value?.localizedDescription
                            ?: IosAppGroupStorage.readTunnelLastError()
                            ?: "VPN tunnel failed to start"
                    IosAppGroupStorage.setTunnelConnected(false)
                    return@withContext false
                }
                true
            }
        }

    private suspend fun loadManagers(): List<NETunnelProviderManager> =
        suspendCancellableCoroutine { continuation ->
            NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, _ ->
                val tunnels = managers
                    ?.mapNotNull { it as? NETunnelProviderManager }
                    .orEmpty()
                if (continuation.isActive) {
                    continuation.resume(tunnels)
                }
            }
        }

    private suspend fun refreshManagerAndState() {
        val managers = loadManagers()
        manager = managers.firstOrNull()
        val connection = manager?.connection
        if (connection != null) {
            updateStateFromConnection(connection)
        } else {
            _state.value =
                if (IosAppGroupStorage.isTunnelConnected()) {
                    VpnState.Connected
                } else {
                    VpnState.Disconnected
                }
        }
    }

    private fun observeVpnStatusChanges() {
        statusObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        statusObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = VPN_STATUS_NOTIFICATION,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            manager?.connection?.let { connection ->
                updateStateFromConnection(connection)
                if (!connection.status.isConnectedLike() && !IosAppGroupStorage.isTunnelConnected()) {
                    IosAppGroupStorage.readTunnelLastError()?.let { message ->
                        _connectError.value = message
                    }
                }
            }
        }
    }

    private fun updateStateFromConnection(connection: NEVPNConnection) {
        _state.value =
            when (connection.status) {
                NEVPNStatusConnected,
                NEVPNStatusReasserting,
                -> VpnState.Connected

                NEVPNStatusConnecting,
                NEVPNStatusDisconnecting,
                NEVPNStatusInvalid,
                -> VpnState.Disconnected

                else -> {
                    if (IosAppGroupStorage.isTunnelConnected()) {
                        VpnState.Connected
                    } else {
                        VpnState.Disconnected
                    }
                }
            }
    }

    private companion object {
        const val TUNNEL_START_GRACE_MS = 500L
    }
}

private fun platform.NetworkExtension.NEVPNStatus.isConnectedLike(): Boolean =
    this == NEVPNStatusConnected || this == NEVPNStatusReasserting
