package com.aerobox.data.database

import android.util.Log
import androidx.room.TypeConverter
import com.aerobox.data.model.ProxyType
import com.aerobox.data.model.SubscriptionType

class Converters {
    @TypeConverter
    fun fromProxyType(type: ProxyType): String = type.name

    @TypeConverter
    fun toProxyType(value: String): ProxyType =
        runCatching { ProxyType.valueOf(value) }.getOrElse {
            Log.w("Converters", "Unknown ProxyType '$value', falling back to SHADOWSOCKS")
            ProxyType.SHADOWSOCKS
        }

    @TypeConverter
    fun fromSubscriptionType(type: SubscriptionType): String = type.name

    @TypeConverter
    fun toSubscriptionType(value: String): SubscriptionType =
        runCatching { SubscriptionType.valueOf(value) }.getOrElse {
            Log.w("Converters", "Unknown SubscriptionType '$value', falling back to BASE64")
            SubscriptionType.BASE64
        }
}
