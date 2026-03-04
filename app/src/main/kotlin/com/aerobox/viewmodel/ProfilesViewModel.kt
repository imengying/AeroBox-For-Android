package com.aerobox.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.Subscription
import com.aerobox.data.repository.SubscriptionRepository
import com.aerobox.utils.NetworkUtils
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = SubscriptionRepository(appContext)

    val subscriptions: StateFlow<List<Subscription>> = repository.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun nodesFlow(subscriptionId: Long) = repository.getNodesBySubscription(subscriptionId)

    fun addSubscription(name: String, url: String) {
        if (name.isBlank() || url.isBlank() || !NetworkUtils.isValidUrl(url)) {
            _error.value = "invalid_url"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching {
                repository.addSubscription(name.trim(), url.trim())
            }.onFailure {
                _error.value = it.message ?: "operation_failed"
            }
            _isLoading.value = false
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            runCatching { repository.deleteSubscription(subscription) }
                .onFailure { _error.value = it.message ?: "operation_failed" }
        }
    }

    fun updateSubscription(subscription: Subscription) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.updateSubscription(subscription) }
                .onFailure { _error.value = it.message ?: "operation_failed" }
            _isLoading.value = false
        }
    }

    fun testLatency(node: ProxyNode) {
        viewModelScope.launch {
            val latency = NetworkUtils.pingTcp(node.server, node.port)
            repository.updateNodeLatency(node.id, latency)
        }
    }

    fun testAllLatencies(subscriptionId: Long) {
        viewModelScope.launch {
            runCatching {
                val nodes = repository.getNodesBySubscriptionOnce(subscriptionId)
                coroutineScope {
                    nodes.map { node ->
                        async {
                            val latency = NetworkUtils.pingTcp(node.server, node.port)
                            repository.updateNodeLatency(node.id, latency)
                        }
                    }.awaitAll()
                }
            }.onFailure {
                _error.value = it.message ?: "operation_failed"
            }
        }
    }

    fun selectNode(node: ProxyNode) {
        viewModelScope.launch {
            PreferenceManager.setLastSelectedNodeId(appContext, node.id)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
