package com.aerobox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.Subscription

@Database(entities = [ProxyNode::class, Subscription::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun proxyNodeDao(): ProxyNodeDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportType TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN socksVersion TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN plugin TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN pluginOpts TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN obfsType TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN obfsPassword TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN serverPorts TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN hopInterval TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN upMbps INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN downMbps INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN globalPadding INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN authenticatedLength INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportMethod TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportHeaders TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportIdleTimeout TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportPingTimeout TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN transportPermitWithoutStream INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN wsMaxEarlyData INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN wsEarlyDataHeaderName TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN httpHeaders TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN udpOverTcpEnabled INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN udpOverTcpVersion INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN udpOverStream INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN zeroRttHandshake INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN heartbeat TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN detour TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN bindInterface TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN inet4BindAddress TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN inet6BindAddress TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN bindAddressNoPort INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN routingMark TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN reuseAddr INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN netns TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN connectTimeout TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN tcpFastOpen INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN tcpMultiPath INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN disableTcpKeepAlive INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN tcpKeepAlive TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN tcpKeepAliveInterval TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN udpFragment INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN domainResolver TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN networkStrategy TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN networkType TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN fallbackNetworkType TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN fallbackDelay TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN domainStrategy TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxEnabled INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxProtocol TEXT")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxMaxConnections INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxMinStreams INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxMaxStreams INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxPadding INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxBrutalEnabled INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxBrutalUpMbps INTEGER")
                db.execSQL("ALTER TABLE proxy_nodes ADD COLUMN muxBrutalDownMbps INTEGER")
            }
        }
    }
}
