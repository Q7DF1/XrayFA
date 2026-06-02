package com.android.xrayfa.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.SettingsRepository
import com.android.xrayfa.repository.AppInfoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Painter,
    var allow: Boolean = false
)

class AppsViewmodel(
    private val settingsRepo: SettingsRepository,
    private val appInfoRepo: AppInfoRepository,
) : ViewModel() {

    /** 搜索关键字。空串表示不过滤。 */
    private val searchQuery = MutableStateFlow("")

    /** 当前已选中的允许包列表，供 UI 直接订阅。 */
    val allowedPackagesState = settingsRepo.packagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /**
     * 加载状态：缓存仓库内部维护，UI 用此值控制 Loading 指示器。
     * 命中缓存时 [AppInfoRepository.load] 立即返回，loading 不会被翻转。
     */
    val loading: StateFlow<Boolean> = appInfoRepo.loading

    /** 查询应用列表的权限状态，UI 据此切换"需要权限"页面 / 列表。 */
    val permissionState: StateFlow<AppInfoRepository.PermissionState> = appInfoRepo.permissionState

    /**
     * 实际给 UI 渲染的列表：
     *   缓存的应用元数据 × 用户允许列表 × 搜索关键字 → List<AppInfo>
     *
     * - 缓存为 null（首次加载未完成）→ 返回空列表，由 [loading] 控制 Loading UI。
     * - 切换勾选 / Clear All → 仅触发 combine 重新发射，不重扫描 PM。
     * - 系统包变化 → 仓库自动重载，combine 自动重新发射。
     */
    val displayedApps: StateFlow<List<AppInfo>> = combine(
        appInfoRepo.apps,
        settingsRepo.packagesFlow,
        searchQuery,
    ) { cached, allowed, query ->
        if (cached == null) return@combine emptyList()
        val allowedSet = allowed.toHashSet()
        val q = query.trim()
        val filtered = if (q.isEmpty()) {
            cached
        } else {
            cached.filter { it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        }
        filtered.map { meta ->
            AppInfo(
                appName = meta.appName,
                packageName = meta.packageName,
                icon = meta.icon,
                allow = meta.packageName in allowedSet,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** 触发首次加载（或在 forceRefresh 时强制刷新），仓库内部去重。 */
    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch { appInfoRepo.load(forceRefresh) }
    }

    /**
     * 仅刷新权限状态（不扫描包）。供 UI 在 onResume 时调用：
     * 用户从系统设置授予权限回到 App 后，先把状态从 DENIED 推回 UNKNOWN，
     * 再触发 [load] 重新尝试。
     */
    fun recheckPermission() {
        appInfoRepo.recheckPermission()
    }

    fun setAllowedPackages(packages: List<String>, callback: suspend () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setAllowedPackages(packages)
            callback()
        }
    }

    fun addAllowPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.addAllowedPackages(packageName)
        }
    }

    fun removeAllowPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.removeAllowedPackage(packageName)
        }
    }

    fun onSearch(query: String) {
        searchQuery.value = query
    }
}

class AppsViewmodelFactory
@Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val appInfoRepo: AppInfoRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppsViewmodel::class.java)) {
            return AppsViewmodel(settingsRepo, appInfoRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
