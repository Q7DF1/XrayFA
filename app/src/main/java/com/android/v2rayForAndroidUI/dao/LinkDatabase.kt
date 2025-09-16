package com.android.v2rayForAndroidUI.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.android.v2rayForAndroidUI.model.Link


@Database(entities = [Link::class], version = 1)
abstract class LinkDatabase: RoomDatabase() {

    abstract fun LinkDao(): LinkDao


    companion object {

        @Volatile
        var INSTANCE: LinkDatabase? = null

        fun getLinkDatabase(context: Context): LinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LinkDatabase::class.java,
                    "link_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}