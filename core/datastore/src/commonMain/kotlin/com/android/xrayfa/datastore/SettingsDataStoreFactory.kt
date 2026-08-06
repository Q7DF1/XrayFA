package com.android.xrayfa.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

/** DataStore file name; must stay stable across app upgrades and KMP platform actuals. */
const val SETTINGS_DATA_STORE_NAME = "settings"

/**
 * Platform handle used to resolve the on-disk settings preferences file.
 *
 * Android actual wraps [android.content.Context]; iOS actual is a placeholder until App Group wiring.
 */
expect class SettingsDataStoreContext

/**
 * Creates the process-wide settings [DataStore].
 *
 * Must use [SETTINGS_DATA_STORE_NAME] on every platform so upgrades keep the same file.
 */
expect fun createSettingsDataStore(context: SettingsDataStoreContext): DataStore<Preferences>

/** Suggested preferences filename stem (without `.preferences_pb` suffix). */
const val SETTINGS_DATA_STORE_FILE_STEM: String = SETTINGS_DATA_STORE_NAME
