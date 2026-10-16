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
import android.os.SystemClock
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
import com.android.xrayfa.core.VpnSessionTraffic
import com.android.xrayfa.core.XrayBaseService
import com.android.xrayfa.core.XrayBaseServiceManager
import com.android.xrayfa.core.XrayCoreManager
import com.android.xrayfa.model.Node
import com.android.xrayfa.repository.NodeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ConnectionWidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: NodeRepository,
    private val startup: VpnStartupStateStore,
    private val core: XrayCoreManager,
    private val manager: XrayBaseServiceManager,
) {
    private data class Snapshot(
        val node: Node?,
        val running: Boolean,
        val startup: VpnStartupState,
        val traffic: VpnSessionTraffic,
        val darkMode: Int,
    )

    private val snapshots = combine(
        repository.querySelectedNode(), XrayBaseService.statusFlow, startup.state, core.sessionTraffic,
        manager.settingsRepository.settingsFlow.map { it.darkMode }.distinctUntilChanged(),
    ) { node, running, startup, traffic, darkMode -> Snapshot(node, running, startup, traffic, darkMode) }
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
                    if (!enabled) return@collectLatest
                    var previous: Snapshot? = null
                    var lastUpdate = 0L
                    snapshots.collect { snapshot ->
                        val now = SystemClock.elapsedRealtime()
                        // Connection and node changes are immediate; byte totals update at most every 15 seconds.
                        val important = previous?.let {
                            it.node != snapshot.node || it.running != snapshot.running || it.startup != snapshot.startup ||
                                it.traffic.startedAtElapsed != snapshot.traffic.startedAtElapsed || it.darkMode != snapshot.darkMode
                        } ?: true
                        if (important || now - lastUpdate >= 15_000) {
                            update(snapshot)
                            previous = snapshot
                            lastUpdate = now
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
                    if (manager.settingsRepository.settingsFlow.first().homeScreenWidgetsEnabled) update(snapshots.first())
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

    private fun update(snapshot: Snapshot) {
        val widgets = AppWidgetManager.getInstance(context)
        val ids = widgets.getAppWidgetIds(ComponentName(context, ConnectionWidgetProvider::class.java))
        val fontScaleExtra = (context.resources.configuration.fontScale - 1f).coerceAtLeast(0f)
        // Larger system text needs more space before the detailed card can fit comfortably.
        val expandedWidth = 250f + 72f * fontScaleExtra
        val expandedHeight = 156f + 84f * fontScaleExtra
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
        val state = connectionWidgetState(snapshot.node != null, snapshot.running, snapshot.startup)
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
        val nodeName = snapshot.node?.remark?.takeIf { it.isNotBlank() }
            ?: if (snapshot.node != null) context.getString(R.string.widget_unnamed_node) else context.getString(R.string.widget_add_node)
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
            val statusLabel = if (compact) when (state) {
                ConnectionWidgetState.EMPTY -> R.string.widget_compact_empty
                ConnectionWidgetState.CONNECTING -> R.string.widget_compact_connecting
                ConnectionWidgetState.CONNECTED -> R.string.widget_compact_connected
                ConnectionWidgetState.FAILED -> R.string.widget_compact_failed
                ConnectionWidgetState.DISCONNECTED -> R.string.widget_compact_disconnected
            } else label
            setTextViewText(R.id.widget_status, context.getString(statusLabel))
            setContentDescription(R.id.widget_status, context.getString(label))
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
                setTextColor(R.id.widget_download, colors.onSurface.toArgb())
                setTextColor(R.id.widget_upload, colors.onSurface.toArgb())
                setTextColor(R.id.widget_switch, colors.primary.toArgb())
                setInt(R.id.widget_divider, "setColorFilter", colors.outlineVariant.toArgb())
                setTextViewText(R.id.widget_hint, context.getString(hint))
                val chrono = connected && snapshot.traffic.startedAtElapsed > 0
                setViewVisibility(R.id.widget_hint, if (chrono) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.widget_duration, if (chrono) View.VISIBLE else View.GONE)
                setChronometer(R.id.widget_duration, snapshot.traffic.startedAtElapsed,
                    context.getString(R.string.widget_duration_format), chrono)
                val stats = connected && snapshot.traffic.available
                val download = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.downloadedBytes) else "—"
                val upload = if (stats) Formatter.formatShortFileSize(context, snapshot.traffic.uploadedBytes) else "—"
                setTextViewText(R.id.widget_download, "↓ $download")
                setTextViewText(R.id.widget_upload, "↑ $upload")
                setContentDescription(R.id.widget_download, "${context.getString(R.string.widget_download_label)} $download")
                setContentDescription(R.id.widget_upload, "${context.getString(R.string.widget_upload_label)} $upload")
                setTextViewText(R.id.widget_switch, context.getString(if (empty) R.string.widget_add_node else R.string.widget_switch_node))
                setOnClickPendingIntent(R.id.widget_switch, openNodes)
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

    companion object {
        private const val TAG = "ConnectionWidget"
    }
}
