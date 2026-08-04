@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.common.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS placeholder context; App Group path wiring comes with the iOS app shell. */
actual class SettingsDataStoreContext

private var cachedStore: DataStore<Preferences>? = null

actual fun createSettingsDataStore(context: SettingsDataStoreContext): DataStore<Preferences> {
    cachedStore?.let { return it }
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val documentsDir = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )!!.path!!
            "$documentsDir/$SETTINGS_DATA_STORE_NAME.preferences_pb".toPath()
        },
    ).also { cachedStore = it }
}
