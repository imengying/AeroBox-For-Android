package com.aerobox.utils

import android.content.Context
import android.widget.Toast
import android.net.VpnService
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aerobox.data.model.TrafficStats
import com.aerobox.data.model.VpnState
import kotlin.math.max

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.checkVpnPermission(): Boolean = VpnService.prepare(this) == null

fun VpnState.toTrafficStats(): TrafficStats {
    return TrafficStats(
        uploadSpeed = uploadSpeed,
        downloadSpeed = downloadSpeed,
        totalUpload = totalUpload,
        totalDownload = totalDownload
    )
}

@Composable
fun Int.getLatencyColor(): Color {
    return when {
        this < 0 -> MaterialTheme.colorScheme.surfaceVariant
        this < 100 -> MaterialTheme.colorScheme.primaryContainer
        this < 300 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
}

fun Long.formatDuration(): String {
    if (this <= 0L) return "00:00:00"
    val elapsedSeconds = max(0L, (System.currentTimeMillis() - this) / 1000)
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (length <= maxLength) this else take(maxLength) + suffix
}
