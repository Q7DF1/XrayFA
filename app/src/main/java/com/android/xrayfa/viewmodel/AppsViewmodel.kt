package com.android.xrayfa.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xrayfa.tun2socks.qualifier.Background
import java.util.concurrent.Executor
import javax.inject.Inject
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.delay
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map


data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Painter,
    val allow: Boolean = false
)

class AppsViewmodel(
    private val settingsRepo: SettingsRepository,
): ViewModel() {


    var searchAppCompleted by mutableStateOf(false)

    val appInfoList = mutableStateListOf<AppInfo>()
    val allowedPackagesState = settingsRepo.packagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setAllowedPackages(packages: List<String>) {
        viewModelScope.launch {
            settingsRepo.setAllowedPackages(packages)
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

    fun getInstalledPackages(context: Context){
        if (searchAppCompleted) return
        val pm = context.packageManager
        viewModelScope.launch(Dispatchers.IO) {
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

                val allow = settingsRepo.getAllowedPackages().contains(pkgInfo.packageName)
                AppInfo(
                    appName = label,
                    packageName = pkgInfo.packageName,
                    icon = drawable.toPainter(),
                    allow = allow
                )
            }.sortedBy { it.appName.lowercase() }
            withContext(Dispatchers.Main) {
                appInfoList.clear()
                appInfoList.addAll(list)
                searchAppCompleted = true
            }
        }
    }
}

fun Drawable.toPainter(maxSize: Int = 48): Painter {
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
class AppsViewmodelFactory
@Inject constructor(
    val settingsRepo: SettingsRepository,
    ): ViewModelProvider.Factory {


    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppsViewmodel::class.java)) {
            return AppsViewmodel(settingsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}