package com.android.xrayfa.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.xrayfa.dto.Link
import com.android.xrayfa.dto.Node
import com.android.xrayfa.dto.Subscription


@Database(entities = [Link::class, Subscription::class, Node::class], version = 2)
abstract class XrayFADatabase: RoomDatabase() {

    abstract fun LinkDao(): LinkDao

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}