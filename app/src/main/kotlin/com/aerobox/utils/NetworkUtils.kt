package com.aerobox.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.ln
import kotlin.math.pow
import kotlin.system.measureTimeMillis

object NetworkUtils {

    /**
     * TCP Ping — NekoBox 方式 2：TCP 连接到节点服务器测量延迟。
     * 不需要 VPN 连接，直接测试节点可达性。
     */
    suspend fun pingTcp(
        server: String,
        port: Int,
        timeout: Int = 3000,
        attempts: Int = 3
    ): Int = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Int>()
        repeat(attempts.coerceAtLeast(1)) { index ->
            val latency = singleTcpPing(server, port, timeout)
            if (latency > 0) {
                samples += latency
            }
            if (index < attempts - 1) {
                delay(120)
            }
        }
        if (samples.isEmpty()) {
            -1
        } else {
            val sorted = samples.sorted()
            sorted[sorted.size / 2]
        }
    }

    private fun singleTcpPing(server: String, port: Int, timeout: Int): Int {
        return runCatching {
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


    fun formatBytesCompact(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return "%.1f %s".format(value, units[digitGroups])
    }

    fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"
}
