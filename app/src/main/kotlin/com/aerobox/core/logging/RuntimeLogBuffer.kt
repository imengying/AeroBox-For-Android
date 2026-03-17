package com.aerobox.core.logging

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RuntimeLogEntry(
    val timestamp: Long,
    val level: String,
    val message: String
)

object RuntimeLogBuffer {
    private const val TAG = "RuntimeLogBuffer"
    private const val MAX_LINES = 500
    private const val LOG_FILE_NAME = "runtime-events.log"
    private val uuidRegex = Regex(
        """\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"""
    )
    private val urlRegex = Regex("""https?://[^\s]+""")
    private val hostPortRegex = Regex("""\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?::\d{1,5})?\b""")
    private val ipv4PortRegex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
    private val bracketIpv6Regex = Regex("""\[[0-9A-Fa-f:%.]+\](?::\d{1,5})?""")

    private val _lines = MutableStateFlow<List<RuntimeLogEntry>>(emptyList())
    val lines: StateFlow<List<RuntimeLogEntry>> = _lines.asStateFlow()
    private val fileLock = Any()
    @Volatile
    private var logFile: File? = null

    fun initialize(context: Context) {
        synchronized(fileLock) {
            val debugDir = context.getExternalFilesDir("debug")
                ?: context.filesDir?.let { File(it, "debug") }
                ?: return
            if (!debugDir.exists()) {
                debugDir.mkdirs()
            }
            logFile = File(debugDir, LOG_FILE_NAME)
        }
    }

    fun append(level: String, message: String) {
        val normalizedMessage = sanitize(message.trim())
        if (normalizedMessage.isEmpty()) return

        val entry = RuntimeLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level.ifBlank { "info" },
            message = normalizedMessage
        )
        _lines.update { current ->
            (current + entry).takeLast(MAX_LINES)
        }
        appendToFile(entry)
    }

    fun clear() {
        _lines.value = emptyList()
        synchronized(fileLock) {
            runCatching { logFile?.writeText("") }
                .onFailure { Log.w(TAG, "Failed to clear runtime log file", it) }
        }
    }

    private fun appendToFile(entry: RuntimeLogEntry) {
        val line = buildString {
            append(formatTimestamp(entry.timestamp))
            append(" [")
            append(entry.level)
            append("] ")
            append(entry.message)
            append('\n')
        }
        synchronized(fileLock) {
            runCatching {
                logFile?.appendText(line)
            }.onFailure {
                Log.w(TAG, "Failed to append runtime log file", it)
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DATE_FORMAT.get().format(Date(timestamp))
    }

    private fun sanitize(message: String): String {
        if (message.isBlank()) return message
        return message
            .replace(urlRegex, "[url]")
            .replace(uuidRegex, "[uuid]")
            .replace(bracketIpv6Regex, "[ipv6]")
            .replace(ipv4PortRegex, "[ipv4]")
            .replace(hostPortRegex, "[host]")
    }

    private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
    }
}
