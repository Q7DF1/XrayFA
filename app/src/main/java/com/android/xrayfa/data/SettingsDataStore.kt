package com.android.xrayfa.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.datastore.SettingsDataStoreContext
import com.android.xrayfa.common.datastore.createSettingsDataStore

/**
 * Android settings DataStore; delegates to the KMP factory so the on-disk path
 * stays identical to the legacy `preferencesDataStore` delegate.
 */
val Context.settingsDataStore: DataStore<Preferences>
    get() = createSettingsDataStore(SettingsDataStoreContext(applicationContext))
