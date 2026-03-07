package com.aerobox.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aerobox.core.config.ConfigGenerator
import com.aerobox.core.geo.GeoAssetManager
import com.aerobox.core.logging.RuntimeLogBuffer
import com.aerobox.core.native.SingBoxNative
import com.aerobox.data.model.ProxyNode
import com.aerobox.service.AeroBoxVpnService
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class VpnRepository(private val context: Context) {
    val isRunning: StateFlow<Boolean> = AeroBoxVpnService.isRunning

    fun startVpn(config: String, nodeId: Long? = null) {
        val intent = Intent(context, AeroBoxVpnService::class.java).apply {
            action = AeroBoxVpnService.ACTION_START
            putExtra(AeroBoxVpnService.EXTRA_CONFIG, config)
            if (nodeId != null && nodeId > 0L) {
                putExtra(AeroBoxVpnService.EXTRA_NODE_ID, nodeId)
            }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpn() {
        RuntimeLogBuffer.append("info", "Sending ACTION_STOP to VPN service")
        val intent = Intent(context, AeroBoxVpnService::class.java).apply {
            action = AeroBoxVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Validate config via libbox. Returns null if valid, error message otherwise.
     */
    fun checkConfig(config: String): String? {
        val error = SingBoxNative.checkConfig(config)
        if (error != null) {
            RuntimeLogBuffer.append("error", "Config check failed: $error")
        }
        return error
    }

    /**
     * Build a sing-box config JSON string for the given node,
     * reading all user preferences (routing, DNS, IPv6, geo paths, etc.).
     */
    suspend fun buildConfig(node: ProxyNode): String {
        RuntimeLogBuffer.append(
            "info",
            "Generating config for ${node.name.ifBlank { "unnamed node" }}"
        )
        RuntimeLogBuffer.append(
            "debug",
            buildString {
                append("Node summary: ")
                append("type=").append(node.type.name)
                append(", server=").append(node.server)
                append(":").append(node.port)
                node.network?.takeIf { it.isNotBlank() }?.let { append(", network=").append(it) }
                append(", tls=").append(node.tls)
                node.security?.takeIf { it.isNotBlank() }?.let { append(", security=").append(it) }
                node.flow?.takeIf { it.isNotBlank() }?.let { append(", flow=").append(it) }
                node.sni?.takeIf { it.isNotBlank() }?.let { append(", sni=").append(it) }
                node.transportHost?.takeIf { it.isNotBlank() }?.let { append(", host=").append(it) }
                node.transportPath?.takeIf { it.isNotBlank() }?.let { append(", path=").append(it) }
                node.transportServiceName?.takeIf { it.isNotBlank() }?.let { append(", service=").append(it) }
                node.alpn?.takeIf { it.isNotBlank() }?.let { append(", alpn=").append(it) }
                node.fingerprint?.takeIf { it.isNotBlank() }?.let { append(", fp=").append(it) }
                node.packetEncoding?.takeIf { it.isNotBlank() }?.let { append(", packetEncoding=").append(it) }
                if (!node.publicKey.isNullOrBlank()) append(", reality=true")
                if (node.allowInsecure) append(", insecure=true")
            }
        )
        withContext(Dispatchers.IO) {
            GeoAssetManager.ensureBundledAssets(context)
        }

        val routingMode = PreferenceManager.routingModeFlow(context).first()
        val remoteDns = PreferenceManager.remoteDnsFlow(context).first()
        val localDns = PreferenceManager.localDnsFlow(context).first()
        val enableDoh = PreferenceManager.enableDohFlow(context).first()
        val enableSocksInbound = PreferenceManager.enableSocksInboundFlow(context).first()
        val enableHttpInbound = PreferenceManager.enableHttpInboundFlow(context).first()
        val enableIPv6 = PreferenceManager.enableIPv6Flow(context).first()
        val enableGeoRules = PreferenceManager.enableGeoRulesFlow(context).first()
        val enableGeoCnDomainRule = PreferenceManager.enableGeoCnDomainRuleFlow(context).first()
        val enableGeoCnIpRule = PreferenceManager.enableGeoCnIpRuleFlow(context).first()
        val enableGeoAdsBlock = PreferenceManager.enableGeoAdsBlockFlow(context).first()
        val enableGeoBlockQuic = PreferenceManager.enableGeoBlockQuicFlow(context).first()
        RuntimeLogBuffer.append(
            "debug",
            "Config options: mode=$routingMode, doh=$enableDoh, socksIn=$enableSocksInbound, " +
                "httpIn=$enableHttpInbound, ipv6=$enableIPv6, geoRules=$enableGeoRules, " +
                "cnDomain=$enableGeoCnDomainRule, cnIp=$enableGeoCnIpRule, ads=$enableGeoAdsBlock, " +
                "blockQuic=$enableGeoBlockQuic"
        )

        val geoIpCnRuleSetPath = if (enableGeoRules) {
            GeoAssetManager
                .getGeoIpFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }
        val geoSiteCnRuleSetPath = if (enableGeoRules) {
            GeoAssetManager
                .getGeoSiteFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }
        val geoSiteAdsRuleSetPath = if (enableGeoRules) {
            GeoAssetManager
                .getGeoAdsFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }

        val config = ConfigGenerator.generateSingBoxConfig(
            node = node,
            routingMode = routingMode,
            remoteDns = remoteDns,
            localDns = localDns,
            enableDoh = enableDoh,
            enableSocksInbound = enableSocksInbound,
            enableHttpInbound = enableHttpInbound,
            enableIPv6 = enableIPv6,
            enableGeoCnDomainRule = enableGeoRules && enableGeoCnDomainRule,
            enableGeoCnIpRule = enableGeoRules && enableGeoCnIpRule,
            enableGeoAdsBlock = enableGeoRules && enableGeoAdsBlock,
            enableGeoBlockQuic = enableGeoRules && enableGeoBlockQuic,
            geoIpCnRuleSetPath = geoIpCnRuleSetPath,
            geoSiteCnRuleSetPath = geoSiteCnRuleSetPath,
            geoSiteAdsRuleSetPath = geoSiteAdsRuleSetPath
        )
        dumpGeneratedConfig(node, config)
        return config
    }

    private suspend fun dumpGeneratedConfig(node: ProxyNode, config: String) {
        withContext(Dispatchers.IO) {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val debugDir = File(baseDir, "debug").apply { mkdirs() }
            val safeName = node.name
                .ifBlank { "node" }
                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
                .trim('_')
                .ifBlank { "node" }

            val latestFile = File(debugDir, "last-sing-box.json")
            val nodeFile = File(debugDir, "last-sing-box-$safeName.json")
            latestFile.writeText(config)
            nodeFile.writeText(config)

            RuntimeLogBuffer.append("debug", "Config dumped: ${latestFile.absolutePath}")
            RuntimeLogBuffer.append("debug", "Config node dump: ${nodeFile.absolutePath}")

            runCatching { JSONObject(config) }.getOrNull()?.let { root ->
                RuntimeLogBuffer.append(
                    "debug",
                    "Config dns: ${root.optJSONObject("dns")?.toString() ?: "{}"}"
                )
                RuntimeLogBuffer.append(
                    "debug",
                    "Config inbound[0]: ${root.optJSONArray("inbounds")?.optJSONObject(0)?.toString() ?: "{}"}"
                )
                RuntimeLogBuffer.append(
                    "debug",
                    "Config outbound[0]: ${root.optJSONArray("outbounds")?.optJSONObject(0)?.toString() ?: "{}"}"
                )
                RuntimeLogBuffer.append(
                    "debug",
                    "Config route: ${root.optJSONObject("route")?.toString() ?: "{}"}"
                )
            }
        }
    }
}
