@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.android.xrayfa.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.android.xrayfa.common.IosPlatformConstants
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS placeholder context; file path resolves via App Group when entitled. */
actual class SettingsDataStoreContext

private var cachedStore: DataStore<Preferences>? = null

actual fun createSettingsDataStore(context: SettingsDataStoreContext): DataStore<Preferences> {
    cachedStore?.let { return it }
    return PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val baseDir = appGroupOrDocumentsDirectory()
            "$baseDir/$SETTINGS_DATA_STORE_NAME.preferences_pb".toPath()
        },
    ).also { cachedStore = it }
}

private fun appGroupOrDocumentsDirectory(): String {
    val appGroupDir = NSFileManager.defaultManager
        .containerURLForSecurityApplicationGroupIdentifier(IosPlatformConstants.APP_GROUP_ID)
        ?.path
    if (appGroupDir != null) {
        return appGroupDir
    }
    return NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )!!.path!!
}
