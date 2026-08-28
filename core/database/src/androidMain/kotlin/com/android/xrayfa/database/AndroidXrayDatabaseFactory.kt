package com.android.xrayfa.database

import android.content.Context
import androidx.room.Room
import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.database.migration.MIGRATION_1_2
import com.android.xrayfa.database.migration.MIGRATION_2_3
import com.android.xrayfa.database.migration.MIGRATION_3_4
import com.android.xrayfa.database.migration.MIGRATION_4_5

object AndroidXrayDatabaseFactory {

    private const val DATABASE_NAME = "xrayfa_database"

    @Volatile
    private var instance: XrayFADatabase? = null

    fun getDatabase(context: Context): XrayFADatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }
    }

    fun getNodeDao(context: Context): NodeDao = getDatabase(context).NodeDao()

    fun getSubscriptionDao(context: Context): SubscriptionDao = getDatabase(context).SubscriptionDao()

    private fun buildDatabase(context: Context): XrayFADatabase {
        return Room.databaseBuilder(
            context = context,
            klass = XrayFADatabase::class.java,
            name = DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }
}
