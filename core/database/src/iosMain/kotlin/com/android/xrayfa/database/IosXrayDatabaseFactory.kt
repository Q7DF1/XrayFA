package com.android.xrayfa.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.android.xrayfa.common.IosPlatformConstants
import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.database.dao.SubscriptionDao
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

object IosXrayDatabaseFactory {

    private const val DATABASE_NAME = "xrayfa_database"

    private val database: XrayFADatabase by lazy { buildDatabase() }

    fun getDatabase(): XrayFADatabase = database

    fun getNodeDao(): NodeDao = database.NodeDao()

    fun getSubscriptionDao(): SubscriptionDao = database.SubscriptionDao()

    private fun buildDatabase(): XrayFADatabase {
        return Room.databaseBuilder<XrayFADatabase>(
            name = databasePath(),
        )
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun databasePath(): String {
        return "${appGroupOrDocumentsDirectory()}/$DATABASE_NAME"
    }

    @OptIn(ExperimentalForeignApi::class)
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
}
