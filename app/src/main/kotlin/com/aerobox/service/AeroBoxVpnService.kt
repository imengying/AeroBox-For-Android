package com.aerobox.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.aerobox.AeroBoxApplication
import com.aerobox.MainActivity
import com.aerobox.R
import com.aerobox.core.native.SingBoxNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AeroBoxVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.aerobox.action.START"
        const val ACTION_STOP = "com.aerobox.action.STOP"
        const val EXTRA_CONFIG = "extra_config"
        const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                startVpn(config)
            }

            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn(config: String) {
        serviceScope.launch {
            runCatching {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notification_connecting))
                )

                vpnInterface?.close()
                vpnInterface = Builder()
                    .setSession("AeroBox VPN")
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .setMtu(9000)
                    .establish()

                val fd = vpnInterface?.fd ?: -1
                if (fd < 0) {
                    throw IllegalStateException("Failed to establish VPN interface")
                }

                val started = SingBoxNative.startService(config, fd)
                if (!started) {
                    throw IllegalStateException("Failed to start sing-box service")
                }

                _isRunning.value = true
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(getString(R.string.notification_connected))
                )
            }.onFailure {
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        runCatching {
            SingBoxNative.stopService()
            vpnInterface?.close()
            vpnInterface = null
        }
        _isRunning.value = false
        VpnStateManager.updateConnectionState(false, null)
        VpnStateManager.resetStats()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AeroBoxApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
