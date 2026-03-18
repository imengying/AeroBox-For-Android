package com.aerobox.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

enum class ProxyType {
    SHADOWSOCKS,
    SHADOWSOCKS_2022,
    VMESS,
    VLESS,
    TROJAN,
    HYSTERIA2,
    TUIC,
    SOCKS,
    HTTP;

    fun displayName(): String {
        return name
            .lowercase(Locale.ROOT)
            .split('_')
            .joinToString(" ") { token ->
                token.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                }
            }
    }
}

object NodeLatencyState {
    const val UNTESTED = -1
    const val TESTING = -2
    const val FAILED = -3
}

@Entity(
    tableName = "proxy_nodes",
    indices = [Index(value = ["subscriptionId"])]
)
data class ProxyNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ProxyType,
    val server: String,
    val port: Int,
    val detour: String? = null,
    val bindInterface: String? = null,
    val inet4BindAddress: String? = null,
    val inet6BindAddress: String? = null,
    val bindAddressNoPort: Boolean? = null,
    val routingMark: String? = null,
    val reuseAddr: Boolean? = null,
    val netns: String? = null,
    val connectTimeout: String? = null,
    val tcpFastOpen: Boolean? = null,
    val tcpMultiPath: Boolean? = null,
    val disableTcpKeepAlive: Boolean? = null,
    val tcpKeepAlive: String? = null,
    val tcpKeepAliveInterval: String? = null,
    val udpFragment: Boolean? = null,
    val domainResolver: String? = null,
    val networkStrategy: String? = null,
    val networkType: String? = null,
    val fallbackNetworkType: String? = null,
    val fallbackDelay: String? = null,
    val domainStrategy: String? = null,
    val uuid: String? = null,
    val alterId: Int = 0,
    val password: String? = null,
    val method: String? = null,
    val flow: String? = null,
    val security: String? = null,
    val network: String? = null,
    val transportType: String? = null,
    val globalPadding: Boolean? = null,
    val authenticatedLength: Boolean? = null,
    val tls: Boolean = false,
    val sni: String? = null,
    val transportHost: String? = null,
    val transportPath: String? = null,
    val transportServiceName: String? = null,
    val transportMethod: String? = null,
    val transportHeaders: String? = null,
    val transportIdleTimeout: String? = null,
    val transportPingTimeout: String? = null,
    val transportPermitWithoutStream: Boolean? = null,
    val wsMaxEarlyData: Int? = null,
    val wsEarlyDataHeaderName: String? = null,
    val alpn: String? = null,
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val packetEncoding: String? = null,
    val subscriptionId: Long = 0,
    val latency: Int = NodeLatencyState.UNTESTED,
    val createdAt: Long = System.currentTimeMillis(),
    // SOCKS/HTTP auth
    val username: String? = null,
    val socksVersion: String? = null,
    val httpHeaders: String? = null,
    val allowInsecure: Boolean = false,
    // Shadowsocks SIP003
    val plugin: String? = null,
    val pluginOpts: String? = null,
    val udpOverTcpEnabled: Boolean? = null,
    val udpOverTcpVersion: Int? = null,
    // Hysteria2
    val obfsType: String? = null,
    val obfsPassword: String? = null,
    val serverPorts: String? = null,
    val hopInterval: String? = null,
    val upMbps: Int? = null,
    val downMbps: Int? = null,
    // Shared multiplex
    val muxEnabled: Boolean? = null,
    val muxProtocol: String? = null,
    val muxMaxConnections: Int? = null,
    val muxMinStreams: Int? = null,
    val muxMaxStreams: Int? = null,
    val muxPadding: Boolean? = null,
    val muxBrutalEnabled: Boolean? = null,
    val muxBrutalUpMbps: Int? = null,
    val muxBrutalDownMbps: Int? = null,
    // TUIC-specific
    val congestionControl: String? = null,
    val udpRelayMode: String? = null,
    val udpOverStream: Boolean? = null,
    val zeroRttHandshake: Boolean? = null,
    val heartbeat: String? = null
)

private val supportedEnabledNetworks = setOf("tcp", "udp")
private val supportedTransportTypes = setOf("ws", "grpc", "http", "h2", "httpupgrade", "quic")

private fun String?.normalizedProxyField(): String? {
    val normalized = this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return when (normalized) {
        "websocket" -> "ws"
        "http-upgrade" -> "httpupgrade"
        else -> normalized
    }
}

fun ProxyNode.effectiveEnabledNetwork(): String? {
    return network
        .normalizedProxyField()
        ?.takeIf { it in supportedEnabledNetworks }
}

fun ProxyNode.effectiveTransportType(): String? {
    return transportType
        .normalizedProxyField()
        ?.takeIf { it in supportedTransportTypes }
        ?: network
            .normalizedProxyField()
            ?.takeIf { it in supportedTransportTypes }
}
