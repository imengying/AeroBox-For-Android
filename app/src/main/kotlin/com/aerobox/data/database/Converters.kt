package com.aerobox.data.database

import androidx.room.TypeConverter
import com.aerobox.data.model.ProxyType
import com.aerobox.data.model.SubscriptionType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromProxyType(type: ProxyType): String = type.name

    @TypeConverter
    fun toProxyType(value: String): ProxyType =
        runCatching { ProxyType.valueOf(value) }.getOrDefault(ProxyType.SHADOWSOCKS)

    @TypeConverter
    fun fromSubscriptionType(type: SubscriptionType): String = type.name

    @TypeConverter
    fun toSubscriptionType(value: String): SubscriptionType =
        runCatching { SubscriptionType.valueOf(value) }.getOrDefault(SubscriptionType.BASE64)

    @TypeConverter
    fun fromStringList(list: List<String>?): String =
        json.encodeToString(list ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
    }
}
