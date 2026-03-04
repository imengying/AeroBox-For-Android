package com.aerobox.utils

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

object PreferenceManager {
    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    private val AUTO_UPDATE_SUBSCRIPTION = booleanPreferencesKey("auto_update_subscription")
    private val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
    private val LAST_SELECTED_NODE_ID = longPreferencesKey("last_selected_node_id")

    fun darkModeFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[DARK_MODE] ?: false }

    fun dynamicColorFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }

    fun autoConnectFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_CONNECT] ?: false }

    fun autoUpdateSubscriptionFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_UPDATE_SUBSCRIPTION] ?: false }

    fun showNotificationFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[SHOW_NOTIFICATION] ?: true }

    fun lastSelectedNodeIdFlow(context: Context): Flow<Long> =
        context.dataStore.data.map { it[LAST_SELECTED_NODE_ID] ?: -1L }

    suspend fun setDarkMode(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAutoConnect(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    suspend fun setAutoUpdateSubscription(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[AUTO_UPDATE_SUBSCRIPTION] = enabled }
    }

    suspend fun setShowNotification(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[SHOW_NOTIFICATION] = enabled }
    }

    suspend fun setLastSelectedNodeId(context: Context, nodeId: Long) {
        context.dataStore.edit { preferences: MutablePreferences ->
            preferences[LAST_SELECTED_NODE_ID] = nodeId
        }
    }
}
