package com.aerobox.data.repository

import android.content.Context
import com.aerobox.AeroBoxApplication
import com.aerobox.core.config.ConfigGenerator
import com.aerobox.core.geo.GeoAssetManager
import com.aerobox.core.logging.RuntimeLogBuffer
import com.aerobox.core.native.SingBoxNative
import com.aerobox.data.model.ProxyNode
import com.aerobox.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class VpnConfigResolver(private val context: Context) {
    private val nodeDao = AeroBoxApplication.database.proxyNodeDao()
    private val subscriptionRepository by lazy {
        SubscriptionRepository(context)
    }

    suspend fun resolveSelectedNode(): ProxyNode? {
        val selectedId = PreferenceManager.lastSelectedNodeIdFlow(context).first()
        val allNodes = nodeDao.getAllNodes().first()
        val node = allNodes.firstOrNull { it.id == selectedId } ?: allNodes.firstOrNull() ?: return null
        if (node.id != selectedId && node.id > 0L) {
            PreferenceManager.setLastSelectedNodeId(context, node.id)
        }
        return node
    }

    suspend fun resolveNodeForAction(
        node: ProxyNode,
        allowSelectedFallback: Boolean
    ): ProxyNode? {
        subscriptionRepository.resolveNode(node)?.let { return it }
        nodeDao.getNodeById(node.id)?.let { return it }

        if (allowSelectedFallback) {
            return resolveSelectedNode()
        }

        return node.takeIf { it.subscriptionId == 0L }
    }

    suspend fun resolveNodeById(
        nodeId: Long?,
        fallbackToSelected: Boolean = true
    ): ProxyNode? {
        if (nodeId != null && nodeId > 0L) {
            nodeDao.getNodeById(nodeId)?.let { return it }
        }
        return if (fallbackToSelected) resolveSelectedNode() else null
    }

    suspend fun buildConfig(node: ProxyNode): String {
        RuntimeLogBuffer.append(
            "info",
            "Generating config for ${node.name.ifBlank { "unnamed node" }}"
        )
        withContext(Dispatchers.IO) {
            GeoAssetManager.ensureBundledAssets(context)
        }

        val prefs = PreferenceManager.readVpnConfigPreferences(context)
        val geoIpCnRuleSetPath = if (prefs.enableGeoRules) {
            GeoAssetManager
                .getGeoIpFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }
        val geoSiteCnRuleSetPath = if (prefs.enableGeoRules) {
            GeoAssetManager
                .getGeoSiteFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }
        val geoSiteAdsRuleSetPath = if (prefs.enableGeoRules) {
            GeoAssetManager
                .getGeoAdsFile(context)
                .takeIf { it.exists() && it.length() > 0L }
                ?.absolutePath
        } else {
            null
        }

        return ConfigGenerator.generateSingBoxConfig(
            node = node,
            routingMode = prefs.routingMode,
            remoteDns = prefs.remoteDns,
            localDns = prefs.localDns,
            enableDoh = prefs.enableDoh,
            enableSocksInbound = prefs.enableSocksInbound,
            enableHttpInbound = prefs.enableHttpInbound,
            ipv6Mode = prefs.ipv6Mode,
            enableGeoCnDomainRule = prefs.enableGeoRules && prefs.enableGeoCnDomainRule,
            enableGeoCnIpRule = prefs.enableGeoRules && prefs.enableGeoCnIpRule,
            enableGeoAdsBlock = prefs.enableGeoRules && prefs.enableGeoAdsBlock,
            enableGeoBlockQuic = prefs.enableGeoRules && prefs.enableGeoBlockQuic,
            geoIpCnRuleSetPath = geoIpCnRuleSetPath,
            geoSiteCnRuleSetPath = geoSiteCnRuleSetPath,
            geoSiteAdsRuleSetPath = geoSiteAdsRuleSetPath
        )
    }

    fun validateConfig(config: String): String? {
        val error = SingBoxNative.checkConfig(config)
        if (error != null) {
            RuntimeLogBuffer.append("error", "Config check failed: $error")
        }
        return error
    }
}
