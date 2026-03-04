package com.aerobox.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ln
import kotlin.math.pow
import kotlin.system.measureTimeMillis

object NetworkUtils {
    suspend fun pingTcp(server: String, port: Int, timeout: Int = 3000): Int = withContext(Dispatchers.IO) {
        runCatching {
            measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(server, port), timeout)
                }
            }.toInt()
        }.getOrDefault(-1)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return "%.2f %s".format(value, units[digitGroups])
    }

    fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"

    fun isValidUrl(url: String): Boolean = "^https?://.*".toRegex().matches(url)
}
