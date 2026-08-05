package com.android.xrayfa.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import com.android.xrayfa.database.dao.NodeDao
import com.android.xrayfa.database.dao.SubscriptionDao
import com.android.xrayfa.database.entity.NodeEntity
import com.android.xrayfa.database.entity.SubscriptionEntity

@Database(
    entities = [SubscriptionEntity::class, NodeEntity::class],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(XrayFADatabaseConstructor::class)
abstract class XrayFADatabase : RoomDatabase() {
    abstract fun NodeDao(): NodeDao
    abstract fun SubscriptionDao(): SubscriptionDao
}
