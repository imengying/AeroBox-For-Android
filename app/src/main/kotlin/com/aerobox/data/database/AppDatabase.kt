package com.aerobox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.Subscription

@Database(entities = [ProxyNode::class, Subscription::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun proxyNodeDao(): ProxyNodeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN naiveProtocol TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN naiveExtraHeaders TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN naiveInsecureConcurrency INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN naiveCertificate TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN naiveCertificatePath TEXT")
            }
        }
    }
}
