package com.android.xrayfa.common.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import okio.Path.Companion.toPath
import java.util.concurrent.ConcurrentHashMap

actual class SettingsDataStoreContext(val androidContext: Context)

private val storeByContext = ConcurrentHashMap<Context, DataStore<Preferences>>()

actual fun createSettingsDataStore(context: SettingsDataStoreContext): DataStore<Preferences> {
    val appContext = context.androidContext.applicationContext
    return storeByContext.getOrPut(appContext) {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                appContext.preferencesDataStoreFile(SETTINGS_DATA_STORE_NAME)
                    .absolutePath
                    .toPath()
            },
        )
    }
}
