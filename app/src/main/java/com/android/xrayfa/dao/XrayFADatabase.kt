package com.android.xrayfa.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription


@Database(entities = [Subscription::class, Node::class], version = 4)
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
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
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

// Migration logic from database version 2 to 3
val MIGRATION_2_3 = object: Migration(2,3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The 'Link' table was already dropped in MIGRATION_1_2,
        // but keeping this line is harmless if you want to be defensive.
        db.execSQL("DROP TABLE IF EXISTS Link")

        // Add the 'favorite' column.
        // Since it is NOT NULL (Boolean), we MUST provide a DEFAULT value (0 for false).
        db.execSQL("ALTER TABLE Node ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")

        // Add the 'jsonData' column.
        // Since it is nullable (String?), no default value is required.
        db.execSQL("ALTER TABLE Node ADD COLUMN jsonData TEXT")
    }
}

val MIGRATION_3_4 = object: Migration(3,4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The 'Link' table was already dropped in MIGRATION_1_2,
        // but keeping this line is harmless if you want to be defensive.
        db.execSQL("DROP TABLE IF EXISTS Link")

        // Add the 'isPreNode,isNextNode' column.
        // Since it is NOT NULL (Boolean), we MUST provide a DEFAULT value (0 for false).
        db.execSQL("ALTER TABLE Node ADD COLUMN isPreNode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Node ADD COLUMN isNextNode INTEGER NOT NULL DEFAULT 0")
        // Add preNodeId column with a default value of -1
        db.execSQL("ALTER TABLE Subscription ADD COLUMN preNodeId INTEGER NOT NULL DEFAULT -1")
        // Add nextNodeId column with a default value of -1
        db.execSQL("ALTER TABLE Subscription ADD COLUMN nextNodeId INTEGER NOT NULL DEFAULT -1")
    }
}