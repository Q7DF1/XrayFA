package com.android.xrayfa.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.android.xrayfa.common.di.qualifier.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级单例：缓存 "已安装且具有 INTERNET 权限的应用" 列表。
 *
 * 设计要点：
 * - 首次调用 [load] 时执行一次完整的 PackageManager 扫描，结果常驻内存。
 * - 通过监听系统包广播 (PACKAGE_ADDED / REMOVED / REPLACED / CHANGED) 与
 *   LOCALE_CHANGED 自动失效缓存，保证数据始终新鲜，不会陈旧或不可用。
 * - 收到广播时：清空缓存；若当前有订阅者（用户正停留在 Apps 页），
 *   则立即重载；否则延迟到下次 [load] 调用时懒加载。
 * - 用 Mutex + double-check 保证并发场景下只扫描一次。
 * - 缓存的数据 [CachedAppInfo] 是不可变元数据，不包含用户的"是否允许"状态，
 *   后者由 ViewModel 与 SettingsRepository.packagesFlow 组合派生。
 *
 * 权限处理：
 * - 在 [load] 前先用 [Manifest.permission.QUERY_ALL_PACKAGES] 静态判定（API 30+）。
 * - 扫描完成后，再用 PackageManager 实际返回的包数兜底：若只有 0~1 项，
 *   说明 OEM 隐私管控（如 MIUI/HyperOS）实际拦截了查询，等同于未授予。
 * - 任一判定失败 → [permissionState] 置为 DENIED，且不缓存任何不完整结果，
 *   下一次 [load] 仍会重试。
 */
@Singleton
class AppInfoRepository @Inject constructor(
    @Application private val context: Context,
) {

    enum class PermissionState { UNKNOWN, GRANTED, DENIED }

    data class CachedAppInfo(
        val appName: String,
        val packageName: String,
        val icon: Painter,
    )

    private val _apps = MutableStateFlow<List<CachedAppInfo>?>(null)
    /** null 表示尚未加载完成 */
    val apps: StateFlow<List<CachedAppInfo>?> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _permissionState = MutableStateFlow(PermissionState.UNKNOWN)
    /** 查询应用列表的权限状态。UI 据此决定显示列表 / "需要权限"页面。 */
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            Log.i(TAG, "package/locale changed: ${intent?.action} ${intent?.dataString}")
            // 失效缓存。若有订阅者立即重载；否则等下一次 load() 懒加载。
            _apps.value = null
            if (_apps.subscriptionCount.value > 0) {
                scope.launch { load(forceRefresh = true) }
            }
        }
    }

    init {
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            pkgFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // Locale 变化没有 data scheme，必须独立注册。
        val localeFilter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            localeFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * 静态权限检查：仅基于 manifest + 系统授予状态判断。
     * 在 API 30 以下没有此权限，默认视为已授予（旧系统不限制查询）。
     */
    private fun hasQueryAllPackagesPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.QUERY_ALL_PACKAGES,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 让 UI（onResume）能主动触发重检：仅刷新 [permissionState]，不扫描包。
     * 真正的扫描通过 [load] 触发。
     */
    fun recheckPermission() {
        if (!hasQueryAllPackagesPermission()) {
            _permissionState.value = PermissionState.DENIED
        } else if (_permissionState.value == PermissionState.DENIED) {
            // 之前是 DENIED，现在 manifest 权限通过，回到 UNKNOWN，
            // 等 load() 通过实际扫描结果再确认是否真的能用。
            _permissionState.value = PermissionState.UNKNOWN
        }
    }

    /**
     * 加载已安装应用列表。
     * - 已加载且未要求强制刷新 → 立即返回（命中缓存）。
     * - 否则在 IO 线程执行扫描。Mutex 保证并发只扫描一次。
     * - 权限未授予 / OEM 拦截导致扫描结果不完整 → 不缓存，[permissionState] 置 DENIED。
     */
    suspend fun load(forceRefresh: Boolean = false) {
        // 0. 静态权限快速判：未授予则直接返回，避免无意义的 PM 扫描
        if (!hasQueryAllPackagesPermission()) {
            _permissionState.value = PermissionState.DENIED
            return
        }
        // 1. 快速路径：无锁判断
        if (!forceRefresh && _apps.value != null) {
            _permissionState.value = PermissionState.GRANTED
            return
        }
        mutex.withLock {
            // 双重检查
            if (!hasQueryAllPackagesPermission()) {
                _permissionState.value = PermissionState.DENIED
                return
            }
            if (!forceRefresh && _apps.value != null) {
                _permissionState.value = PermissionState.GRANTED
                return
            }
            _loading.value = true
            try {
                val (rawCount, list) = withContext(Dispatchers.IO) { fetchFromPackageManager() }
                // 兜底：manifest 权限即使返回 GRANTED，部分 OEM（MIUI/HyperOS 等）
                // 仍可能在隐私层拦截 getInstalledPackages，使返回值仅有自身。
                // 此时不污染缓存，下次 load() 还能重试。
                if (rawCount <= 1) {
                    Log.w(TAG, "PackageManager returned only $rawCount package(s); " +
                            "QUERY_ALL_PACKAGES likely not effective. Skipping cache.")
                    _permissionState.value = PermissionState.DENIED
                } else {
                    _apps.value = list
                    _permissionState.value = PermissionState.GRANTED
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * 返回 (rawCount, filteredList)：
     * - rawCount：PackageManager 返回的原始包数量，用于权限是否真生效的兜底判定。
     * - filteredList：去掉无 INTERNET / 无图标 / 无标题等不合格项后的有序列表。
     */
    private fun fetchFromPackageManager(): Pair<Int, List<CachedAppInfo>> {
        val pm = context.packageManager
        val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val list = installedPackages.mapNotNull { pkgInfo ->
            val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null

            val label = runCatching {
                pm.getApplicationLabel(appInfo).toString().trim()
            }.getOrNull() ?: return@mapNotNull null

            val hasInternet = pkgInfo.requestedPermissions
                ?.contains(android.Manifest.permission.INTERNET) == true
            if (!hasInternet || label.isEmpty()) {
                return@mapNotNull null
            }

            val drawable = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()
                ?: return@mapNotNull null

            CachedAppInfo(
                appName = label,
                packageName = pkgInfo.packageName,
                icon = drawable.toPainter(),
            )
        }.sortedBy { it.appName.lowercase() }
        return installedPackages.size to list
    }

    companion object {
        private const val TAG = "AppInfoRepository"
    }
}

/**
 * 将 Drawable 缩放渲染为 Compose [Painter]。最大边长限制为 [maxSize]dp 等价像素，
 * 避免某些应用提供超大原始图标导致内存暴涨。
 */
internal fun Drawable.toPainter(maxSize: Int = 48): Painter {
    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1

    val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height, 1f)

    val bitmapWidth = (width * scale).toInt().coerceAtLeast(1)
    val bitmapHeight = (height * scale).toInt().coerceAtLeast(1)

    val bitmap = createBitmap(bitmapWidth, bitmapHeight)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, bitmapWidth, bitmapHeight)
    draw(canvas)

    return BitmapPainter(bitmap.asImageBitmap())
}
