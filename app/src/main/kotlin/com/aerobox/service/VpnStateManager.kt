package com.aerobox.service

import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnStateManager {
    private val _vpnState = MutableStateFlow(VpnState())
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()
    private val _serviceActive = MutableStateFlow(false)
    val serviceActive: StateFlow<Boolean> = _serviceActive.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun updateServiceActive(active: Boolean) {
        _serviceActive.value = active
    }

    fun updateConnectionState(isConnected: Boolean, node: ProxyNode?) {
        val current = _vpnState.value
        _vpnState.value = current.copy(
            isConnected = isConnected,
            currentNode = if (isConnected) node else null,
            connectionTime = if (isConnected) System.currentTimeMillis() else 0L
        )
        if (isConnected) {
            _lastError.value = null
        }
    }


    fun updateCurrentNode(node: ProxyNode?) {
        if (!_vpnState.value.isConnected) return
        _vpnState.value = _vpnState.value.copy(currentNode = node)
    }

    fun updateLastError(error: String?) {
        _lastError.value = error?.takeIf { it.isNotBlank() }
    }

    fun clearLastError() {
        _lastError.value = null
    }

    fun updateTrafficStats(
        uploadSpeed: Long,
        downloadSpeed: Long,
        uploadDelta: Long,
        downloadDelta: Long
    ) {
        val current = _vpnState.value
        _vpnState.value = _vpnState.value.copy(
            uploadSpeed = uploadSpeed,
            downloadSpeed = downloadSpeed,
            totalUpload = (current.totalUpload + uploadDelta).coerceAtLeast(0L),
            totalDownload = (current.totalDownload + downloadDelta).coerceAtLeast(0L)
        )
    }

    fun resetSpeedStats() {
        _vpnState.value = _vpnState.value.copy(
            uploadSpeed = 0,
            downloadSpeed = 0
        )
    }

    fun resetTrafficSession() {
        _vpnState.value = _vpnState.value.copy(
            uploadSpeed = 0,
            downloadSpeed = 0,
            totalUpload = 0,
            totalDownload = 0
        )
    }
}
