package com.android.xrayfa.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.xrayfa.common.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xrayfa.tun2socks.qualifier.Background
import java.util.concurrent.Executor
import javax.inject.Inject


data class AppInfo(
    val appName: String,
    val packageName: String
)

class AppsViewmodel(
    private val settingsRepo: SettingsRepository,
    @Background private val bgExecutor: Executor
): ViewModel() {



    private val _searchAppCompleted = MutableStateFlow(false)
    val searchAppCompleted = _searchAppCompleted.asStateFlow()

    lateinit var appInfoList: List<AppInfo>
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

    fun getInstalledPackages(context: Context){
        if (_searchAppCompleted.value) return
        bgExecutor.execute {
            val installedApplications =
                context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            appInfoList = installedApplications.map {
                AppInfo(
                    appName = it.name?:"unkonw",
                    packageName = it.packageName
                )
            }
            _searchAppCompleted.value = true
        }
    }
}

class AppsViewmodelFactory
@Inject constructor(
    val settingsRepo: SettingsRepository,
    @Background val bgExecutor: Executor
    ): ViewModelProvider.Factory {


    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppsViewmodel::class.java)) {
            return AppsViewmodel(settingsRepo,bgExecutor) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}