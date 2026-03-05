package com.aerobox.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aerobox.core.config.ConfigGenerator
import com.aerobox.core.geo.GeoAssetManager
import com.aerobox.core.native.SingBoxNative
import com.aerobox.data.model.ProxyNode
import com.aerobox.service.AeroBoxVpnService
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {
    private companion object {
        private const val TAG = "VpnRepository"
    }

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

    /**
     * Validate config via libbox. Returns null if valid, error message otherwise.
     */
    fun checkConfig(config: String): String? =
        SingBoxNative.checkConfig(config)

    /**
     * Build a sing-box config JSON string for the given node,
     * reading all user preferences (routing, DNS, IPv6, geo paths, etc.).
     */
    suspend fun buildConfig(node: ProxyNode): String {
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
        val enableGeoCnDirect = PreferenceManager.enableGeoCnDirectFlow(context).first()
        val enableGeoAdsBlock = PreferenceManager.enableGeoAdsBlockFlow(context).first()

        val geoIpPath = if (enableGeoRules) {
            GeoAssetManager
                .getGeoIpFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }
        val geoSitePath = if (enableGeoRules) {
            GeoAssetManager
                .getGeoSiteFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }

        val configWithGeo = ConfigGenerator.generateSingBoxConfig(
            node = node,
            routingMode = routingMode,
            remoteDns = remoteDns,
            localDns = localDns,
            enableDoh = enableDoh,
            enableSocksInbound = enableSocksInbound,
            enableHttpInbound = enableHttpInbound,
            enableIPv6 = enableIPv6,
            enableGeoCnDirect = enableGeoCnDirect,
            enableGeoAdsBlock = enableGeoAdsBlock,
            geoipPath = geoIpPath,
            geositePath = geoSitePath
        )

        val geoError = checkConfig(configWithGeo)
        if (geoError != null && isGeoConfigError(geoError)) {
            Log.w(TAG, "Geo rules invalid, fallback to no-geo config: $geoError")
            return ConfigGenerator.generateSingBoxConfig(
                node = node,
                routingMode = routingMode,
                remoteDns = remoteDns,
                localDns = localDns,
                enableDoh = enableDoh,
                enableSocksInbound = enableSocksInbound,
                enableHttpInbound = enableHttpInbound,
                enableIPv6 = enableIPv6,
                enableGeoCnDirect = false,
                enableGeoAdsBlock = false,
                geoipPath = null,
                geositePath = null
            )
        }

        return configWithGeo
    }

    private fun isGeoConfigError(error: String): Boolean {
        val msg = error.lowercase()
        return msg.contains("geosite") ||
                msg.contains("geoip") ||
                (msg.contains("router") && msg.contains("rule") && msg.contains("database"))
    }
}
