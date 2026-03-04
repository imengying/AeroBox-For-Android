package com.aerobox.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    val darkMode: StateFlow<Boolean> = PreferenceManager.darkModeFlow(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val dynamicColor: StateFlow<Boolean> = PreferenceManager.dynamicColorFlow(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoConnect: StateFlow<Boolean> = PreferenceManager.autoConnectFlow(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoUpdateSubscription: StateFlow<Boolean> =
        PreferenceManager.autoUpdateSubscriptionFlow(appContext)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showNotification: StateFlow<Boolean> = PreferenceManager.showNotificationFlow(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    suspend fun setDarkMode(enabled: Boolean) {
        PreferenceManager.setDarkMode(appContext, enabled)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        PreferenceManager.setDynamicColor(appContext, enabled)
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        PreferenceManager.setAutoConnect(appContext, enabled)
    }

    suspend fun setAutoUpdateSubscription(enabled: Boolean) {
        PreferenceManager.setAutoUpdateSubscription(appContext, enabled)
    }

    suspend fun setShowNotification(enabled: Boolean) {
        PreferenceManager.setShowNotification(appContext, enabled)
    }
}
