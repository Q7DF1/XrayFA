package com.android.xrayfa.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.android.xrayfa.common.repository.SETTINGS_DATA_STORE_NAME

/**
 * Android settings DataStore; file name must match [SETTINGS_DATA_STORE_NAME] for user upgrades.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATA_STORE_NAME,
)
