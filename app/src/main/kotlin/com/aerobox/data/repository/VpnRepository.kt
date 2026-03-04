package com.aerobox.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aerobox.core.native.SingBoxNative
import com.aerobox.service.AeroBoxVpnService
import kotlinx.coroutines.flow.StateFlow

class VpnRepository(private val context: Context) {
    val isRunning: StateFlow<Boolean> = AeroBoxVpnService.isRunning

    fun startVpn(config: String) {
        val intent = Intent(context, AeroBoxVpnService::class.java).apply {
            action = AeroBoxVpnService.ACTION_START
            putExtra(AeroBoxVpnService.EXTRA_CONFIG, config)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpn() {
        val intent = Intent(context, AeroBoxVpnService::class.java).apply {
            action = AeroBoxVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun getTrafficStats(): LongArray =
        runCatching { SingBoxNative.getTrafficStats() }.getOrDefault(longArrayOf(0L, 0L))

    fun testConfig(config: String): Boolean =
        runCatching { SingBoxNative.testConfig(config) }.getOrDefault(false)
}
