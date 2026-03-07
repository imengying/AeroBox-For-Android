package com.aerobox.service

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aerobox.AeroBoxApplication
import com.aerobox.MainActivity
import com.aerobox.R
import com.aerobox.core.logging.RuntimeLogBuffer
import com.aerobox.data.repository.VpnRepository
import com.aerobox.utils.NetworkUtils
import com.aerobox.utils.PreferenceManager
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * AeroBox VPN Service — implements PlatformInterfaceWrapper so libbox
 * can call openTun / autoDetectInterfaceControl etc.
 *
 * The core lifecycle follows SFA:
 *   CommandServer(handler, platformInterface) → startOrReloadService(config, overrides)
 */
class AeroBoxVpnService : VpnService(), PlatformInterfaceWrapper, CommandServerHandler {

    companion object {
        const val ACTION_START = "com.aerobox.action.START"
        const val ACTION_STOP = "com.aerobox.action.STOP"
        const val ACTION_SWITCH = "com.aerobox.action.SWITCH"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_NODE_ID = "extra_node_id"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "AeroBoxVpnService"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var speedTickerJob: Job? = null
    private var commandServer: CommandServer? = null
    private var receiverRegistered = false

    private var lastConfig: String? = null
    private var lastNodeId: Long = -1L
    private var userRequestedStop = false
    private var reconnectAttempts = 0

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP -> {
                    userRequestedStop = true
                    stopService("Stopping service: notification action")
                    stopSelf()
                }

                ACTION_SWITCH -> {
                    switchNodeFromNotification()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                val nodeId = intent.getLongExtra(EXTRA_NODE_ID, -1L)
                userRequestedStop = false
                reconnectAttempts = 0
                lastConfig = config
                if (nodeId > 0L) {
                    lastNodeId = nodeId
                }
                startVpn(config)
            }
            ACTION_STOP -> {
                userRequestedStop = true
                stopService("Stopping service: ACTION_STOP intent")
                stopSelf()
            }
            ACTION_SWITCH -> {
                switchNodeFromNotification()
            }
        }
        return START_STICKY
    }

    // ─── VPN Lifecycle ───

    private fun startVpn(config: String) {
        serviceScope.launch {
            runCatching {
                RuntimeLogBuffer.append("info", "Starting sing-box service")
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(connected = false)
                )

                // Register close receiver
                if (!receiverRegistered) {
                    val filter = IntentFilter().apply {
                        addAction(ACTION_STOP)
                        addAction(ACTION_SWITCH)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(closeReceiver, filter, RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(closeReceiver, filter)
                    }
                    receiverRegistered = true
                }

                DefaultNetworkMonitor.start()

                val server = commandServer ?: CommandServer(this@AeroBoxVpnService, this@AeroBoxVpnService).also {
                    it.start()
                    commandServer = it
                    RuntimeLogBuffer.append("debug", "CommandServer started")
                }

                val overrides = buildOverrideOptions()
                server.startOrReloadService(config, overrides)
                RuntimeLogBuffer.append("info", "startOrReloadService invoked")

            }.onFailure { e ->
                Log.e(TAG, "startVpn failed", e)
                RuntimeLogBuffer.append("error", "startVpn failed: ${e.message ?: e}")
                VpnStateManager.updateLastError(e.message ?: e.toString())
                stopService("Stopping service after start failure")
            }
        }
    }

    private fun switchNodeFromNotification() {
        serviceScope.launch {
            runCatching {
                val nodes = AeroBoxApplication.database.proxyNodeDao().getAllNodes().first()
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No nodes available for switch action")
                    RuntimeLogBuffer.append("warn", "No nodes available for switch action")
                    return@runCatching
                }

                val selectedId = PreferenceManager.lastSelectedNodeIdFlow(applicationContext).first()
                val currentId = if (selectedId > 0L) selectedId else lastNodeId
                val currentIndex = nodes.indexOfFirst { it.id == currentId }
                val nextNode = if (currentIndex >= 0) {
                    nodes[(currentIndex + 1) % nodes.size]
                } else {
                    nodes.first()
                }

                PreferenceManager.setLastSelectedNodeId(applicationContext, nextNode.id)

                val vpnRepository = VpnRepository(applicationContext)
                val nextConfig = vpnRepository.buildConfig(nextNode)
                val configError = vpnRepository.checkConfig(nextConfig)
                if (configError != null) {
                    Log.e(TAG, "Switch node config invalid: $configError")
                    RuntimeLogBuffer.append("error", "Switch node config invalid: $configError")
                    return@runCatching
                }

                userRequestedStop = false
                reconnectAttempts = 0
                lastNodeId = nextNode.id
                lastConfig = nextConfig
                startVpn(nextConfig)
            }.onFailure { e ->
                Log.e(TAG, "switchNodeFromNotification failed", e)
                RuntimeLogBuffer.append("error", "switchNodeFromNotification failed: ${e.message ?: e}")
            }
        }
    }

    private fun buildOverrideOptions(): OverrideOptions {
        return OverrideOptions().apply {
            // Per-app proxy
            val perAppEnabled = runBlocking {
                PreferenceManager.perAppProxyEnabledFlow(applicationContext).first()
            }
            if (perAppEnabled) {
                val mode = runBlocking {
                    PreferenceManager.perAppProxyModeFlow(applicationContext).first()
                }
                val packages = runBlocking {
                    PreferenceManager.perAppProxyPackagesFlow(applicationContext).first()
                }
                if (mode == "whitelist") {
                    includePackage = PlatformInterfaceWrapper.StringArray(
                        (packages + packageName).iterator()
                    )
                } else {
                    excludePackage = PlatformInterfaceWrapper.StringArray(
                        (packages - packageName).iterator()
                    )
                }
            }
        }
    }

    private suspend fun resolveCurrentNode(explicitNodeId: Long?): com.aerobox.data.model.ProxyNode? {
        val nodeId = when {
            explicitNodeId != null && explicitNodeId > 0L -> explicitNodeId
            lastNodeId > 0L -> lastNodeId
            else -> PreferenceManager.lastSelectedNodeIdFlow(applicationContext).first()
        }
        if (nodeId <= 0L) return null
        return AeroBoxApplication.database.proxyNodeDao().getNodeById(nodeId)
    }

    private fun stopService(reason: String = "Stopping service") {
        RuntimeLogBuffer.append("info", reason)
        speedTickerJob?.cancel()
        speedTickerJob = null

        DefaultNetworkMonitor.stop()

        runCatching { commandServer?.closeService() }
        runCatching {
            commandServer?.close()
            commandServer = null
        }

        // Close VPN tunnel
        runCatching {
            vpnInterface?.close()
            vpnInterface = null
        }

        // Unregister receiver
        if (receiverRegistered) {
            runCatching { unregisterReceiver(closeReceiver) }
            receiverRegistered = false
        }

        _isRunning.value = false
        VpnStateManager.updateConnectionState(false, null)
        VpnStateManager.resetStats()

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ─── CommandServerHandler callbacks ───

    override fun serviceStop() {
        RuntimeLogBuffer.append(
            if (userRequestedStop) "info" else "warn",
            if (userRequestedStop) "Service stopped" else "Service stopped unexpectedly"
        )
        // Called by libbox when the service stops (may be unexpected)
        vpnInterface?.close()
        vpnInterface = null
        _isRunning.value = false
        VpnStateManager.updateConnectionState(false, null)

        if (!userRequestedStop) {
            // Unexpected disconnect — try auto-reconnect
            attemptReconnect()
        } else {
            stopService("Stopping service after serviceStop callback")
        }
    }

    override fun serviceReload() {
        // Called by libbox for hot-reload — not used in our simple flow
        RuntimeLogBuffer.append("info", "Service reloaded")
    }

    override fun getSystemProxyStatus(): SystemProxyStatus {
        return SystemProxyStatus()
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        // Not applicable to VPN mode
    }

    override fun writeDebugMessage(message: String) {
        Log.d(TAG, "libbox-debug: $message")
        RuntimeLogBuffer.append("debug", message)
    }

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {
        Log.i(TAG, "libbox notification: ${notification.title} - ${notification.body}")
        val content = buildString {
            if (notification.title.isNotBlank()) append(notification.title)
            if (notification.body.isNotBlank()) {
                if (isNotEmpty()) append(" - ")
                append(notification.body)
            }
        }.ifBlank { "libbox notification" }
        RuntimeLogBuffer.append("info", content)
    }

    // ─── PlatformInterfaceWrapper overrides ───

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")
        RuntimeLogBuffer.append("debug", "Opening VPN TUN interface")

        val builder = Builder()
            .setSession("AeroBox")
            .setMtu(options.mtu)

        builder.setMetered(false)

        val inet4Addresses = mutableListOf<Pair<String, Int>>()
        val inet4Address = options.inet4Address
        while (inet4Address.hasNext()) {
            val addr = inet4Address.next()
            inet4Addresses.add(addr.address() to addr.prefix())
        }
        inet4Addresses.forEach { (address, prefix) -> builder.addAddress(address, prefix) }

        val inet6Addresses = mutableListOf<Pair<String, Int>>()
        val inet6Address = options.inet6Address
        while (inet6Address.hasNext()) {
            val addr = inet6Address.next()
            inet6Addresses.add(addr.address() to addr.prefix())
        }
        inet6Addresses.forEach { (address, prefix) -> builder.addAddress(address, prefix) }

        if (options.autoRoute) {
            builder.addDnsServer(options.dnsServerAddress.value)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4Routes = mutableListOf<Pair<String, Int>>()
                val inet4RouteAddress = options.inet4RouteAddress
                while (inet4RouteAddress.hasNext()) {
                    val route = inet4RouteAddress.next()
                    inet4Routes.add(route.address() to route.prefix())
                }
                if (inet4Routes.isNotEmpty()) {
                    inet4Routes.forEach { (address, prefix) -> builder.addRoute(address, prefix) }
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6Routes = mutableListOf<Pair<String, Int>>()
                val inet6RouteAddress = options.inet6RouteAddress
                while (inet6RouteAddress.hasNext()) {
                    val route = inet6RouteAddress.next()
                    inet6Routes.add(route.address() to route.prefix())
                }
                if (inet6Routes.isNotEmpty()) {
                    inet6Routes.forEach { (address, prefix) -> builder.addRoute(address, prefix) }
                } else if (inet6Addresses.isNotEmpty()) {
                    builder.addRoute("::", 0)
                }
                RuntimeLogBuffer.append(
                    "debug",
                    "Tun DNS=${options.dnsServerAddress.value}, ipv4=${inet4Addresses.size}, ipv6=${inet6Addresses.size}, " +
                        "ipv4Routes=${inet4Routes.size}, ipv6Routes=${inet6Routes.size}"
                )
            } else {
                val inet4Routes = mutableListOf<Pair<String, Int>>()
                val inet4RouteRange = options.inet4RouteRange
                while (inet4RouteRange.hasNext()) {
                    val route = inet4RouteRange.next()
                    inet4Routes.add(route.address() to route.prefix())
                }
                if (inet4Routes.isNotEmpty()) {
                    inet4Routes.forEach { (address, prefix) -> builder.addRoute(address, prefix) }
                } else {
                    builder.addRoute("0.0.0.0", 0)
                }

                val inet6Routes = mutableListOf<Pair<String, Int>>()
                val inet6RouteRange = options.inet6RouteRange
                while (inet6RouteRange.hasNext()) {
                    val route = inet6RouteRange.next()
                    inet6Routes.add(route.address() to route.prefix())
                }
                if (inet6Routes.isNotEmpty()) {
                    inet6Routes.forEach { (address, prefix) -> builder.addRoute(address, prefix) }
                } else if (inet6Addresses.isNotEmpty()) {
                    builder.addRoute("::", 0)
                }
                RuntimeLogBuffer.append(
                    "debug",
                    "Tun DNS=${options.dnsServerAddress.value}, ipv4=${inet4Addresses.size}, ipv6=${inet6Addresses.size}, " +
                        "ipv4Routes=${inet4Routes.size}, ipv6Routes=${inet6Routes.size}"
                )
            }
        }

        // Per-app proxy from OverrideOptions (handled by libbox include/exclude)
        val include = options.includePackage
        while (include.hasNext()) {
            runCatching { builder.addAllowedApplication(include.next()) }
        }
        val exclude = options.excludePackage
        while (exclude.hasNext()) {
            runCatching { builder.addDisallowedApplication(exclude.next()) }
        }

        val pfd = builder.establish()
            ?: error("android: failed to establish VPN interface")
        vpnInterface = pfd
        val connectedNode = runBlocking {
            resolveCurrentNode(null)
        }
        _isRunning.value = true
        VpnStateManager.clearLastError()
        VpnStateManager.updateConnectionState(true, connectedNode)
        RuntimeLogBuffer.append(
            "info",
            "VPN interface established" + (
                connectedNode?.name?.takeIf { it.isNotBlank() }?.let { " for $it" } ?: ""
            )
        )
        val notification = buildNotification(connected = true)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
        startSpeedTicker()
        return pfd.fd
    }

    // ─── Notification ───

    private fun buildNotification(contentText: String = "", connected: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        val switchIntent = Intent(ACTION_SWITCH).setPackage(packageName)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val switchPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            switchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (connected) {
            VpnStateManager.vpnState.value.currentNode
                ?.name
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.notification_title)
        } else {
            getString(R.string.notification_title)
        }
        val statusText = if (connected) {
            getString(R.string.notification_connected)
        } else {
            getString(R.string.notification_connecting)
        }
        val mergedContent = when {
            contentText.isBlank() -> statusText
            connected && contentText != statusText -> "$statusText · $contentText"
            else -> contentText
        }

        val builder = NotificationCompat.Builder(this, AeroBoxApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(mergedContent)
            .setSubText(statusText)
            .setSmallIcon(R.drawable.ic_stat_aerobox)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)

        if (connected) {
            builder
                .addAction(
                    android.R.drawable.ic_menu_rotate,
                    getString(R.string.notification_action_switch),
                    switchPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.notification_action_stop),
                    stopPendingIntent
                )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
        }

        return builder.build()
    }

    private fun startSpeedTicker() {
        speedTickerJob?.cancel()
        speedTickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val state = VpnStateManager.vpnState.value
                val upSpeed = NetworkUtils.formatBytes(state.uploadSpeed) + "/s"
                val downSpeed = NetworkUtils.formatBytes(state.downloadSpeed) + "/s"
                val text = "↑ $upSpeed  ↓ $downSpeed"
                val notification = buildNotification(contentText = text, connected = true)
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        userRequestedStop = true
        stopService("Stopping service: onDestroy")
        serviceScope.cancel()
        super.onDestroy()
    }

    // ─── Auto-Reconnect ───

    private fun attemptReconnect() {
        val config = lastConfig ?: return
        serviceScope.launch {
            val autoReconnect = runBlocking {
                PreferenceManager.autoReconnectFlow(applicationContext).first()
            }
            if (!autoReconnect) {
                stopService("Stopping service: auto reconnect disabled")
                return@launch
            }

            reconnectAttempts++
            val backoffMs = 1000L * (1L shl (reconnectAttempts - 1).coerceAtMost(5))
            Log.i(TAG, "Auto-reconnect attempt $reconnectAttempts in ${backoffMs}ms")
            RuntimeLogBuffer.append(
                "warn",
                "Auto-reconnect attempt $reconnectAttempts in ${backoffMs}ms"
            )
            delay(backoffMs)

            if (userRequestedStop) return@launch
            startVpn(config)
        }
    }
}
