package com.android.xrayfa.repository

import android.content.Context
import androidx.annotation.IntDef
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.xrayfa.viewmodel.SettingsState
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val DARK_MODE = intPreferencesKey("dark_mode")
    val IPV6_ENABLE = booleanPreferencesKey("ipv6_enable")

}
const val LIGHT_MODE = 0
const val DARK_MODE = 1
const val AUTO_MODE = 2

@IntDef(LIGHT_MODE, DARK_MODE, AUTO_MODE)
@Retention(AnnotationRetention.SOURCE)
annotation class Mode

class SettingsRepository
@Inject constructor(private val context: Context) {

    val settingsFlow = context.dataStore.data.map { prefs ->
        SettingsState(
            darkMode = prefs[SettingsKeys.DARK_MODE] ?: 0,
            ipV6Enable = prefs[SettingsKeys.IPV6_ENABLE] == true
        )

    }

    suspend fun setDarkMode(@Mode darkMode: Int) {
        context.dataStore.edit {
            it[SettingsKeys.DARK_MODE] = darkMode
        }
    }

    suspend fun setIpV6Enable(enable: Boolean) {
        context.dataStore.edit {
            it[SettingsKeys.IPV6_ENABLE] = enable
        }
    }
}