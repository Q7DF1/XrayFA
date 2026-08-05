package com.android.xrayfa.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS Link")

        db.execSQL(
            """
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
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS Link")
        db.execSQL("ALTER TABLE Node ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Node ADD COLUMN jsonData TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS Link")
        db.execSQL("ALTER TABLE Node ADD COLUMN isPreNode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Node ADD COLUMN isNextNode INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Subscription ADD COLUMN preNodeId INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE Subscription ADD COLUMN nextNodeId INTEGER NOT NULL DEFAULT -1")
    }
}
