package com.android.xrayfa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.android.xrayfa.datastore.Theme
import com.android.xrayfa.ui.theme.resolveAndroidColorScheme
import android.net.VpnService
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.android.xrayfa.MainActivity
import com.android.xrayfa.R
import com.android.xrayfa.common.core.VpnStartupState
import com.android.xrayfa.common.core.VpnStartupStateStore
import com.android.xrayfa.common.routing.RoutingMode
import com.android.xrayfa.core.ActiveVpnSession
import com.android.xrayfa.core.VpnSessionTraffic
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.core.XrayBaseServiceManager
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.model.Node
import com.android.xrayfa.model.isJsonConfig
import com.android.xrayfa.repository.NodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ConnectionWidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: NodeRepository,
    private val startup: VpnStartupStateStore,
    private val core: XrayCoreManager,
    private val manager: XrayBaseServiceManager,
    private val exitIpLookup: WidgetExitIpLookup,
) {
    private data class CoreSnapshot(
        val selectedNode: Node?,
        val running: Boolean,
        val startup: VpnStartupState,
        val activeSession: ActiveVpnSession?,
        val traffic: VpnSessionTraffic,
    )

    private data class WidgetSettings(val darkMode: Int, val routingMode: Int)

    private data class ExitIpSnapshot(
        val sessionId: Long,
        val address: String? = null,
        val loading: Boolean = false,
        val failed: Boolean = false,
        val unsupported: Boolean = false,
    )

    private data class Snapshot(
        val selectedNode: Node?,
        val running: Boolean,
        val startup: VpnStartupState,
        val activeSession: ActiveVpnSession?,
        val traffic: VpnSessionTraffic,
        val darkMode: Int,
        val routingMode: Int,
        val exitIp: ExitIpSnapshot?,
    )

    private val exitIp = MutableStateFlow<ExitIpSnapshot?>(null)
    private var exitIpJob: Job? = null
    private val coreSnapshots = combine(
        repository.querySelectedNode(), XrayBaseService.statusFlow, startup.state,
        XrayBaseService.activeSession, core.sessionTraffic,
    ) { selectedNode, running, startupState, activeSession, traffic ->
        CoreSnapshot(selectedNode, running, startupState, activeSession, traffic)
    }
    private val widgetSettings = manager.settingsRepository.settingsFlow
        .map { WidgetSettings(it.darkMode, it.routingMode) }
        .distinctUntilChanged()
    private val snapshots = combine(coreSnapshots, widgetSettings, exitIp) { state, settings, ip ->
        Snapshot(
            selectedNode = state.selectedNode,
            running = state.running,
            startup = state.startup,
            activeSession = state.activeSession,
            traffic = state.traffic,
            darkMode = settings.darkMode,
            routingMode = settings.routingMode,
            exitIp = ip,
        )
    }
    private val actionMutex = Mutex()
    private var observing = false

    fun start() {
        if (observing) return
        observing = true
        scope.launch {
            manager.settingsRepository.settingsFlow
                .map { it.homeScreenWidgetsEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    syncProviderEnabled(enabled)
                    if (!enabled) {
                        exitIpJob?.cancel()
                        exitIp.value = null
                        return@collectLatest
                    }
                    var previous: Snapshot? = null
                    snapshots.collect { snapshot ->
                        val previousSession = previous?.activeSession?.sessionId
                        val currentSession = snapshot.activeSession?.sessionId.takeIf {
                            snapshot.running && !snapshot.startup.pending
                        }
                        if (previous == null || previousSession != currentSession) {
                            startExitIpLookup(snapshot.activeSession.takeIf {
                                snapshot.running && !snapshot.startup.pending
                            })
                        }
                        val important = previous?.let {
                            it.selectedNode != snapshot.selectedNode || it.running != snapshot.running ||
                                it.startup != snapshot.startup || it.activeSession != snapshot.activeSession ||
                                it.traffic.startedAtElapsed != snapshot.traffic.startedAtElapsed ||
                                it.darkMode != snapshot.darkMode || it.routingMode != snapshot.routingMode ||
                                it.exitIp != snapshot.exitIp
                        } ?: true
                        // XrayCoreManager publishes one read-and-reset traffic snapshot every three seconds.
                        if (important || previous?.traffic != snapshot.traffic) {
                            update(snapshot)
                            previous = snapshot
                        }
                    }
                }
        }
    }

    private fun syncProviderEnabled(enabled: Boolean) {
        val provider = ComponentName(context, ConnectionWidgetProvider::class.java)
        val pm = context.packageManager
        // The provider is disabled in the manifest until the user opts in.
        val current = pm.getComponentEnabledSetting(provider)
        val currentlyEnabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (currentlyEnabled == enabled) return
        pm.setComponentEnabledSetting(
            provider,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun refresh(finished: () -> Unit) {
        scope.launch {
            try {
                withTimeout(8_000) {
                    if (manager.settingsRepository.settingsFlow.first().homeScreenWidgetsEnabled) {
                        val snapshot = snapshots.first()
                        val session = snapshot.activeSession.takeIf { snapshot.running && !snapshot.startup.pending }
                        if (session != null && exitIp.value?.sessionId != session.sessionId) {
                            startExitIpLookup(session)
                        }
                        update(snapshot)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Unable to refresh connection widget", e)
            } finally {
                finished()
            }
        }
    }

    fun performAction(connect: Boolean, finished: () -> Unit) {
        scope.launch {
            try {
                if (!actionMutex.tryLock()) return@launch
                try {
                    if (startup.state.value.pending) return@launch
                    withTimeout(8_000) {
                        if (!manager.settingsRepository.settingsFlow.first().homeScreenWidgetsEnabled) return@withTimeout
                        if (connect && !XrayBaseService.statusFlow.value) {
                            // The normal PendingIntent opens MainActivity when consent is missing.
                            // Permission may still have been revoked since the widget was rendered.
                            if (VpnService.prepare(context) != null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, R.string.widget_permission_required, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                manager.startXrayBaseService()
                            }
                        } else if (!connect && XrayBaseService.statusFlow.value) {
                            manager.stopXrayBaseService()
                        }
                        update(snapshots.first())
                    }
                } finally {
                    actionMutex.unlock()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Connection widget action failed", e)
            } finally {
                finished()
            }
        }
    }

    fun refreshExitIp(finished: () -> Unit = {}) {
        scope.launch {
            try {
                if (!manager.settingsRepository.settingsFlow.first().homeScreenWidgetsEnabled) return@launch
                val session = XrayBaseService.activeSession.value ?: return@launch
                if (startup.state.value.pending || !XrayBaseService.statusFlow.value || widgetIds().isEmpty()) {
                    return@launch
                }
                if (exitIp.value?.let { it.sessionId == session.sessionId && it.loading } == true) return@launch
                startExitIpLookup(session)
            } finally {
                finished()
            }
        }
    }

    private fun startExitIpLookup(session: ActiveVpnSession?) {
        exitIpJob?.cancel()
        if (session == null || widgetIds().isEmpty()) {
            exitIp.value = null
            return
        }
        if (!session.socksInboundAvailable) {
            exitIp.value = ExitIpSnapshot(sessionId = session.sessionId, unsupported = true)
            return
        }
        exitIp.value = ExitIpSnapshot(sessionId = session.sessionId, loading = true)
        exitIpJob = scope.launch {
            val result = try {
                withTimeout(EXIT_IP_TIMEOUT_MILLIS) { exitIpLookup.lookup() }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Timed out loading proxy exit IP", e)
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Unable to load proxy exit IP", e)
                null
            }
            if (XrayBaseService.activeSession.value?.sessionId != session.sessionId) return@launch
            exitIp.value = if (result != null) {
                ExitIpSnapshot(sessionId = session.sessionId, address = result)
            } else {
                ExitIpSnapshot(sessionId = session.sessionId, failed = true)
            }
        }
    }

    private fun widgetIds(): IntArray = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, ConnectionWidgetProvider::class.java))

    private fun update(snapshot: Snapshot) {
        val widgets = AppWidgetManager.getInstance(context)
        val ids = widgetIds()
        if (ids.isEmpty()) return
        val fontScaleExtra = (context.resources.configuration.fontScale - 1f).coerceAtLeast(0f)
        // Larger system text needs more space before the detailed card can fit comfortably.
        val expandedWidth = 250f + 72f * fontScaleExtra
        val expandedHeight = 176f + 84f * fontScaleExtra
        val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val colors = resolveAndroidColorScheme(context, Theme.fromCode(snapshot.darkMode).resolvesToDark(systemDark))
        for (id in ids) {
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(mapOf(
                    SizeF(130f, 64f) to render(snapshot, compact = true, colors),
                    SizeF(expandedWidth, expandedHeight) to render(snapshot, compact = false, colors),
                ))
            } else {
                val options = widgets.getAppWidgetOptions(id)
                val compact = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) < expandedWidth ||
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) < expandedHeight
                render(snapshot, compact, colors)
            }
            widgets.updateAppWidget(id, views)
        }
    }

    private fun render(snapshot: Snapshot, compact: Boolean, colors: ColorScheme): RemoteViews {
        val connectedSession = snapshot.activeSession.takeIf { snapshot.running && !snapshot.startup.pending }
        val selectedNode = snapshot.selectedNode
        val hasNode = connectedSession?.nodeId?.let { it > 0 } ?: (selectedNode != null)
        val state = connectionWidgetState(hasNode, snapshot.running, snapshot.startup)
        val connected = state == ConnectionWidgetState.CONNECTED
        val pending = state == ConnectionWidgetState.CONNECTING
        val empty = state == ConnectionWidgetState.EMPTY
        val failed = state == ConnectionWidgetState.FAILED
        val label = when (state) {
            ConnectionWidgetState.EMPTY -> R.string.widget_needs_setup
            ConnectionWidgetState.CONNECTING -> R.string.widget_connecting
            ConnectionWidgetState.CONNECTED -> R.string.connected
            ConnectionWidgetState.FAILED -> R.string.widget_failed
            ConnectionWidgetState.DISCONNECTED -> R.string.not_connected
        }
        val hint = when (state) {
            ConnectionWidgetState.EMPTY -> R.string.widget_setup_hint
            ConnectionWidgetState.CONNECTING -> R.string.widget_connecting_hint
            ConnectionWidgetState.CONNECTED -> R.string.widget_connected_hint
            ConnectionWidgetState.FAILED -> R.string.widget_retry
            ConnectionWidgetState.DISCONNECTED -> R.string.widget_tap_connect
        }
        val color = when {
            pending -> colors.secondary
            failed -> colors.error
            connected -> colors.primary
            else -> colors.onSurfaceVariant
        }.toArgb()
        val nodeName = if (connectedSession != null) {
            connectedSession.nodeName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_unnamed_node)
        } else {
            selectedNode?.remark?.takeIf { it.isNotBlank() }
                ?: if (hasNode) context.getString(R.string.widget_unnamed_node) else context.getString(R.string.widget_add_node)
        }
        val endpointAddress = if (connectedSession != null) connectedSession.serverAddress else selectedNode?.address
        val endpointPort = if (connectedSession != null) connectedSession.serverPort else selectedNode?.port
        val customConfiguration = connectedSession?.customConfiguration ?: (selectedNode?.isJsonConfig == true)
        val mode = if (!hasNode || customConfiguration) null
            else connectedSession?.routingMode ?: RoutingMode.fromCode(snapshot.routingMode)
        val statusLabel = if (compact) when (state) {
            ConnectionWidgetState.EMPTY -> R.string.widget_compact_empty
            ConnectionWidgetState.CONNECTING -> R.string.widget_compact_connecting
            ConnectionWidgetState.CONNECTED -> R.string.widget_compact_connected
            ConnectionWidgetState.FAILED -> R.string.widget_compact_failed
            ConnectionWidgetState.DISCONNECTED -> R.string.widget_compact_disconnected
        } else label
        val statusText = when {
            compact -> context.getString(statusLabel)
            customConfiguration -> context.getString(R.string.widget_status_mode_format,
                context.getString(statusLabel), context.getString(R.string.widget_mode_custom))
            mode != null -> context.getString(R.string.widget_status_mode_format,
                context.getString(statusLabel), context.getString(if (mode == RoutingMode.GLOBAL) R.string.widget_mode_global else R.string.widget_mode_route))
            else -> context.getString(statusLabel)
        }
        val openHome = activityIntent(MainActivity.ACTION_WIDGET_HOME)
        val openNodes = activityIntent(MainActivity.ACTION_WIDGET_NODES)
        val action = when {
            empty -> openNodes
            connected -> connectionIntent(connect = false)
            VpnService.prepare(context) != null -> activityIntent(MainActivity.ACTION_START_SERVICE)
            else -> connectionIntent(connect = true)
        }
        return RemoteViews(context.packageName, if (compact) R.layout.widget_connection_compact else R.layout.widget_connection).apply {
            setTextViewText(R.id.widget_node, nodeName)
            setTextViewText(R.id.widget_status, statusText)
            setContentDescription(R.id.widget_status, statusText)
            setTextColor(R.id.widget_status, color)
            setInt(R.id.widget_dot, "setColorFilter", color)
            applyWidgetTypography(compact)
            setTextColor(R.id.widget_node, colors.onSurface.toArgb())
            setInt(R.id.widget_surface, "setColorFilter", colors.surfaceContainerLow.toArgb())
            setInt(R.id.widget_power_surface, "setColorFilter", (if (connected) colors.primary else colors.secondaryContainer).toArgb())
            setImageViewResource(R.id.widget_power, if (empty) R.drawable.widget_ic_add else R.drawable.widget_ic_power)
            setInt(R.id.widget_power, "setColorFilter", (if (connected) colors.onPrimary else colors.onSecondaryContainer).toArgb())
            setBoolean(R.id.widget_power, "setEnabled", !pending)
            setInt(R.id.widget_power, "setImageAlpha", if (pending) 110 else 255)
            setContentDescription(R.id.widget_power, context.getString(when {
                empty -> R.string.widget_add_node
                pending -> R.string.widget_connecting
                connected -> R.string.stop
                else -> R.string.start
            }))
            setOnClickPendingIntent(R.id.widget_root, openHome)
            setOnClickPendingIntent(R.id.widget_power, action)
            if (!compact) {
                setTextColor(R.id.widget_brand, colors.onSurfaceVariant.toArgb())
                setTextColor(R.id.widget_hint, colors.onSurfaceVariant.toArgb())
                setTextColor(R.id.widget_duration, colors.onSurfaceVariant.toArgb())
                setTextColor(R.id.widget_endpoint, colors.onSurfaceVariant.toArgb())
                setTextColor(R.id.widget_download, colors.onSurface.toArgb())
                setTextColor(R.id.widget_upload, colors.onSurface.toArgb())
                setTextColor(R.id.widget_switch, colors.primary.toArgb())
                setInt(R.id.widget_divider, "setColorFilter", colors.outlineVariant.toArgb())
                val ip = snapshot.exitIp?.takeIf { it.sessionId == connectedSession?.sessionId }
                val hintText = if (connected) {
                    when {
                        ip?.loading == true -> context.getString(R.string.widget_public_ip_loading)
                        ip?.address != null -> context.getString(R.string.widget_public_ip_format, ip.address)
                        ip?.unsupported == true -> context.getString(R.string.widget_public_ip_unsupported)
                        ip?.failed == true -> context.getString(R.string.widget_public_ip_failed)
                        else -> context.getString(R.string.widget_public_ip_unavailable)
                    }
                } else context.getString(hint)
                setTextViewText(R.id.widget_hint, hintText)
                setContentDescription(R.id.widget_hint, if (connected && ip?.unsupported != true) {
                    context.getString(
                        R.string.widget_public_ip_accessibility_format,
                        hintText,
                        context.getString(R.string.widget_public_ip_refresh_description),
                    )
                } else hintText)
                val endpoint = endpointAddress?.takeIf { it.isNotBlank() }?.let { address ->
                    val host = if (address.contains(':') && !address.startsWith('[')) "[$address]" else address
                    if (endpointPort != null && endpointPort > 0) "$host:$endpointPort" else host
                }
                setTextViewText(R.id.widget_endpoint, endpoint ?: context.getString(R.string.widget_endpoint_unavailable))
                setViewVisibility(R.id.widget_endpoint, if (endpoint == null) View.GONE else View.VISIBLE)
                val chrono = connected && snapshot.traffic.startedAtElapsed > 0
                setViewVisibility(R.id.widget_hint, View.VISIBLE)
                setViewVisibility(R.id.widget_duration, if (chrono) View.VISIBLE else View.GONE)
                setChronometer(R.id.widget_duration, snapshot.traffic.startedAtElapsed,
                    context.getString(R.string.widget_duration_format), chrono)
                val stats = connected && snapshot.traffic.available
                val download = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.downloadedBytes) else "—"
                val upload = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.uploadedBytes) else "—"
                val downloadRate = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.downloadBytesPerSecond.toLong()) else "—"
                val uploadRate = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.uploadBytesPerSecond.toLong()) else "—"
                setTextViewText(R.id.widget_download, context.getString(R.string.widget_download_stats_format, downloadRate, download))
                setTextViewText(R.id.widget_upload, context.getString(R.string.widget_upload_stats_format, uploadRate, upload))
                setContentDescription(R.id.widget_download,
                    context.getString(R.string.widget_download_accessibility, downloadRate, download))
                setContentDescription(R.id.widget_upload,
                    context.getString(R.string.widget_upload_accessibility, uploadRate, upload))
                setTextViewText(R.id.widget_switch, context.getString(if (empty) R.string.widget_add_node else R.string.widget_switch_node))
                setOnClickPendingIntent(R.id.widget_switch, openNodes)
                setOnClickPendingIntent(R.id.widget_hint, when {
                    connected && ip?.unsupported != true -> exitIpRefreshIntent()
                    connected -> null
                    else -> openHome
                })
            }
        }
    }

    private fun activityIntent(action: String): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            this.action = "${context.packageName}.widget.$action"
            putExtra("shortcut_action", action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun connectionIntent(connect: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ConnectionWidgetActionReceiver::class.java).apply {
            action = if (connect) ConnectionWidgetActionReceiver.ACTION_CONNECT else ConnectionWidgetActionReceiver.ACTION_DISCONNECT
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun exitIpRefreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, ConnectionWidgetActionReceiver::class.java).apply {
            action = ConnectionWidgetActionReceiver.ACTION_REFRESH_EXIT_IP
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "ConnectionWidget"
        private const val EXIT_IP_TIMEOUT_MILLIS = 12_000L
    }
}
