package com.android.xrayfa

import android.app.Application
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import com.android.xrayfa.repository.AUTO_MODE
import com.android.xrayfa.repository.SettingsKeys
import com.android.xrayfa.repository.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class XrayFAApplication: Application() {


    private val _isDarkTheme = MutableStateFlow(AUTO_MODE)
    val isDarkTheme: StateFlow<Int> get() = _isDarkTheme

    var contextAvailableCallback: ContextAvailableCallback? = null


    private fun observeDarkMode() {
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.data
                .map { prefs ->
                    prefs[SettingsKeys.DARK_MODE] ?: AUTO_MODE
                }
                .collect { value ->
                    _isDarkTheme.value = value
                }
        }
    }

    override fun onCreate() {
        super.onCreate()
        contextAvailableCallback?.onContextAvailable(applicationContext)
        observeDarkMode()
    }
}