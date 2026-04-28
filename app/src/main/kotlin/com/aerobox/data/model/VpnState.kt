package com.aerobox.data.model

data class TrafficStats(
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val totalUpload: Long = 0,
    val totalDownload: Long = 0
)

data class VpnState(
    val isConnected: Boolean = false,
    val currentNode: ProxyNode? = null,
    val connectionTime: Long = 0,
    val traffic: TrafficStats = TrafficStats()
) {
    // Convenience accessors to avoid breaking existing callers
    val uploadSpeed: Long get() = traffic.uploadSpeed
    val downloadSpeed: Long get() = traffic.downloadSpeed
    val totalUpload: Long get() = traffic.totalUpload
    val totalDownload: Long get() = traffic.totalDownload
}
