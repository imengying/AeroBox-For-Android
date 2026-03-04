package com.aerobox.data.model

data class VpnState(
    val isConnected: Boolean = false,
    val currentNode: ProxyNode? = null,
    val connectionTime: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val totalUpload: Long = 0,
    val totalDownload: Long = 0
)

data class TrafficStats(
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val totalUpload: Long = 0,
    val totalDownload: Long = 0
)
