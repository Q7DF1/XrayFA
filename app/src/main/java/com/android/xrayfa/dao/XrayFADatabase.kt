package com.android.xrayfa.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription


@Database(entities = [Subscription::class, Node::class], version = 2)
abstract class XrayFADatabase: RoomDatabase() {


    abstract fun NodeDao(): NodeDao

    abstract fun SubscriptionDao(): SubscriptionDao

    companion object {

        @Volatile
        var INSTANCE: XrayFADatabase? = null

        fun getXrayDatabase(context: Context): XrayFADatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    XrayFADatabase::class.java,
                    "xrayfa_database"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
val MIGRATION_1_2 = object: Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS Link")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Node (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                protocolPrefix TEXT NOT NULL,
                address TEXT NOT NULL,
                port INTEGER NOT NULL,
                selected INTEGER NOT NULL,
                remark TEXT,
                subscriptionId INTEGER NOT NULL,
                url TEXT NOT NULL,
                countryISO TEXT NOT NULL
            )
        """.trimIndent())
    }
}