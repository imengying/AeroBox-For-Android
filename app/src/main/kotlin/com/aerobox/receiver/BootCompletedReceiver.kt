package com.aerobox.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aerobox.service.AeroBoxVpnService
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    private companion object {
        const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Ignoring unexpected action: ${intent?.action}")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autoConnect = PreferenceManager.autoConnectFlow(context).first()
                if (!autoConnect) return@launch
                if (android.net.VpnService.prepare(context) != null) return@launch

                // Delegate heavy work (config build, geo check) to the foreground
                // VPN service.  The service's prepareStartRequest() will resolve the
                // selected node and build the config safely, avoiding the goAsync()
                // ANR window (~10-30 s) that connectSelectedNode() could exceed.
                val startIntent = Intent(context, AeroBoxVpnService::class.java).apply {
                    action = AeroBoxVpnService.ACTION_START
                }
                ContextCompat.startForegroundService(context, startIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Auto-connect failed after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
