package com.aerobox.core.config

import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.ProxyType
import com.aerobox.data.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object ConfigGenerator {

    fun generateSingBoxConfig(
        node: ProxyNode,
        routingMode: RoutingMode = RoutingMode.RULE_BASED,
        remoteDns: String = "8.8.8.8",
        localDns: String = "223.5.5.5",
        enableDoh: Boolean = true,
        enableSocksInbound: Boolean = false,
        enableHttpInbound: Boolean = false,
        enableIPv6: Boolean = true,
        enableGeoCnDomainRule: Boolean = true,
        enableGeoCnIpRule: Boolean = true,
        enableGeoAdsBlock: Boolean = true,
        enableGeoBlockQuic: Boolean = true,
        geoIpCnRuleSetPath: String? = null,
        geoSiteCnRuleSetPath: String? = null,
        geoSiteAdsRuleSetPath: String? = null
    ): String {
        val config = JSONObject()
        val hasGeoSiteCn = !geoSiteCnRuleSetPath.isNullOrBlank()
        val hasGeoIpCn = !geoIpCnRuleSetPath.isNullOrBlank()
        val hasGeoAds = !geoSiteAdsRuleSetPath.isNullOrBlank()

        config.put(
            "log",
            JSONObject()
                .put("level", "info")
                .put("timestamp", true)
        )

        config.put(
            "dns",
            buildDns(
                remoteDns = remoteDns,
                localDns = localDns,
                enableDoh = enableDoh,
                routingMode = routingMode,
                enableGeoCnDomainRule = enableGeoCnDomainRule && hasGeoSiteCn
            )
        )
        config.put("inbounds", buildInbounds(enableSocksInbound, enableHttpInbound, enableIPv6))

        val proxyOutbound = buildProxyOutbound(node).put("tag", "proxy")
        config.put(
            "outbounds",
            JSONArray()
                .put(proxyOutbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
        )

        config.put(
            "route",
            buildRoute(
                routingMode = routingMode,
                geoIpCnRuleSetPath = geoIpCnRuleSetPath,
                geoSiteCnRuleSetPath = geoSiteCnRuleSetPath,
                geoSiteAdsRuleSetPath = geoSiteAdsRuleSetPath,
                enableGeoCnDomainRule = enableGeoCnDomainRule && hasGeoSiteCn,
                enableGeoCnIpRule = enableGeoCnIpRule && hasGeoIpCn,
                enableGeoAdsBlock = enableGeoAdsBlock && hasGeoAds,
                enableGeoBlockQuic = enableGeoBlockQuic
            )
        )

        return config.toString(2)
    }

    // ── DNS ──────────────────────────────────────────────────────────

    private fun buildDns(
        remoteDns: String,
        localDns: String,
        enableDoh: Boolean,
        routingMode: RoutingMode,
        enableGeoCnDomainRule: Boolean
    ): JSONObject {
        val localServer = JSONObject()
            .put("tag", "local")
            .put("address", localDns)
            .put("detour", "direct")

        // Strict direct mode: force DNS to local resolver only.
        if (routingMode == RoutingMode.DIRECT) {
            return JSONObject()
                .put("servers", JSONArray().put(localServer))
                .put("final", "local")
        }

        val remoteAddress = normalizeRemoteDnsAddress(remoteDns, enableDoh)

        val servers = JSONArray()
            .put(
                JSONObject()
                    .put("tag", "remote")
                    .put("address", remoteAddress)
            )
            .put(localServer)

        val dns = JSONObject().put("servers", servers)

        // Only add DNS routing rules for rule-based modes
        if (routingMode == RoutingMode.RULE_BASED) {
            val dnsRules = JSONArray()
            fun addDnsLocalRule(country: String) {
                dnsRules.put(
                    JSONObject()
                        .put("rule_set", JSONArray().put("geosite-$country"))
                        .put("server", "local")
                )
            }

            if (enableGeoCnDomainRule) addDnsLocalRule("cn")

            if (dnsRules.length() > 0) {
                dns.put("rules", dnsRules)
            }
        }

        return dns
    }

    private fun normalizeRemoteDnsAddress(remoteDns: String, enableDoh: Boolean): String {
        val trimmed = remoteDns.trim()
        if (trimmed.isBlank()) {
            return if (enableDoh) "tls://8.8.8.8" else "8.8.8.8"
        }

        if (enableDoh) {
            return if (trimmed.startsWith("tls://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "tls://$trimmed"
            }
        }

        return when {
            trimmed.startsWith("tls://") -> trimmed.removePrefix("tls://")
            trimmed.startsWith("https://") -> {
                runCatching { URI(trimmed).host }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: trimmed.removePrefix("https://").substringBefore('/')
            }
            else -> trimmed
        }
    }

    // ── Inbounds ─────────────────────────────────────────────────────

    private fun buildInbounds(
        enableSocks: Boolean,
        enableHttp: Boolean,
        enableIPv6: Boolean = true
    ): JSONArray {
        val inbounds = JSONArray()

        // TUN (always present)
        val tunInbound = JSONObject()
            .put("type", "tun")
            .put("interface_name", "tun0")
            .put("inet4_address", JSONArray().put("172.19.0.1/30"))
            .put("mtu", 9000)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "mixed")
            .put("sniff", true)

        if (enableIPv6) {
            tunInbound.put("inet6_address", JSONArray().put("fdfe:dcba:9876::1/126"))
        }

        inbounds.put(tunInbound)

        // Optional SOCKS5 inbound (for Phase 6)
        if (enableSocks) {
            inbounds.put(
                JSONObject()
                    .put("type", "socks")
                    .put("tag", "socks-in")
                    .put("listen", "::")
                    .put("listen_port", 2080)
            )
        }

        // Optional HTTP inbound (for Phase 6)
        if (enableHttp) {
            inbounds.put(
                JSONObject()
                    .put("type", "http")
                    .put("tag", "http-in")
                    .put("listen", "::")
                    .put("listen_port", 2081)
            )
        }

        return inbounds
    }

    // ── Route ────────────────────────────────────────────────────────

    private fun buildRoute(
        routingMode: RoutingMode,
        geoIpCnRuleSetPath: String? = null,
        geoSiteCnRuleSetPath: String? = null,
        geoSiteAdsRuleSetPath: String? = null,
        enableGeoCnDomainRule: Boolean = true,
        enableGeoCnIpRule: Boolean = true,
        enableGeoAdsBlock: Boolean = true,
        enableGeoBlockQuic: Boolean = true
    ): JSONObject {
        val route = JSONObject()
            .put("auto_detect_interface", true)

        val ruleSets = JSONArray()
        if (!geoIpCnRuleSetPath.isNullOrBlank()) {
            ruleSets.put(buildLocalRuleSet("geoip-cn", geoIpCnRuleSetPath))
        }
        if (!geoSiteCnRuleSetPath.isNullOrBlank()) {
            ruleSets.put(buildLocalRuleSet("geosite-cn", geoSiteCnRuleSetPath))
        }
        if (!geoSiteAdsRuleSetPath.isNullOrBlank()) {
            ruleSets.put(buildLocalRuleSet("geosite-category-ads-all", geoSiteAdsRuleSetPath))
        }
        if (ruleSets.length() > 0) {
            route.put("rule_set", ruleSets)
        }

        when (routingMode) {
            RoutingMode.GLOBAL_PROXY -> {
                route.put("final", "proxy")
                route.put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("protocol", "dns")
                            .put("action", "hijack-dns")
                    )
                )
            }

            RoutingMode.RULE_BASED -> {
                route.put("final", "proxy")
                val rules = JSONArray()
                    .put(
                        JSONObject()
                            .put("protocol", "dns")
                            .put("action", "hijack-dns")
                    )

                if (enableGeoBlockQuic) {
                    rules.put(
                        JSONObject()
                            .put("network", JSONArray().put("udp"))
                            .put("port", JSONArray().put(443))
                            .put("action", "reject")
                    )
                }

                if (enableGeoCnDomainRule) {
                    rules.put(
                        JSONObject()
                            .put("rule_set", JSONArray().put("geosite-cn"))
                            .put("outbound", "direct")
                    )
                }

                if (enableGeoCnIpRule) {
                    rules.put(
                        JSONObject()
                            .put("rule_set", JSONArray().put("geoip-cn"))
                            .put("outbound", "direct")
                    )
                }

                if (enableGeoAdsBlock) {
                    rules.put(
                        JSONObject()
                            .put("rule_set", JSONArray().put("geosite-category-ads-all"))
                            .put("action", "reject")
                    )
                }

                route.put(
                    "rules",
                    rules
                )
            }

            RoutingMode.DIRECT -> {
                route.put("final", "direct")
                route.put(
                    "rules",
                    JSONArray().put(
                        JSONObject()
                            .put("protocol", "dns")
                            .put("action", "hijack-dns")
                    )
                )
            }

        }

        return route
    }

    private fun buildLocalRuleSet(tag: String, path: String): JSONObject {
        return JSONObject()
            .put("tag", tag)
            .put("type", "local")
            .put("format", "binary")
            .put("path", path)
    }

    // ── Proxy Outbound ───────────────────────────────────────────────

    private fun buildProxyOutbound(node: ProxyNode): JSONObject {
        val outbound = JSONObject()
            .put("server", node.server)
            .put("server_port", node.port)

        when (node.type) {
            ProxyType.SHADOWSOCKS,
            ProxyType.SHADOWSOCKS_2022 -> {
                outbound.put("type", "shadowsocks")
                outbound.put("method", node.method ?: "aes-128-gcm")
                outbound.put("password", node.password ?: "")
            }

            ProxyType.VMESS -> {
                outbound.put("type", "vmess")
                outbound.put("uuid", node.uuid ?: "")
                outbound.put("security", node.security ?: "auto")
                outbound.put("alter_id", 0)
                outbound.put("tls", buildTlsObject(node))
            }

            ProxyType.VLESS -> {
                outbound.put("type", "vless")
                outbound.put("uuid", node.uuid ?: "")
                node.flow?.let { outbound.put("flow", it) }
                outbound.put("tls", buildTlsObject(node, includeReality = true))
            }

            ProxyType.TROJAN -> {
                outbound.put("type", "trojan")
                outbound.put("password", node.password ?: "")
                outbound.put("tls", buildTlsObject(node))
            }

            ProxyType.HYSTERIA2 -> {
                outbound.put("type", "hysteria2")
                outbound.put("password", node.password ?: "")
                outbound.put("tls", buildTlsObject(node))
            }

            ProxyType.TUIC -> {
                outbound.put("type", "tuic")
                outbound.put("uuid", node.uuid ?: "")
                outbound.put("password", node.password ?: "")
                outbound.put("tls", buildTlsObject(node))
            }

            ProxyType.WIREGUARD -> {
                outbound.put("type", "wireguard")
                node.privateKey?.let { outbound.put("private_key", it) }
                node.localAddress?.let {
                    outbound.put("local_address", JSONArray().put(it))
                }
                node.mtu?.let { outbound.put("mtu", it) }
                if (!node.reserved.isNullOrBlank()) {
                    // reserved can be "1,2,3" or a base64 string
                    val parts = node.reserved!!.split(",")
                    if (parts.size == 3 && parts.all { it.trim().toIntOrNull() != null }) {
                        val arr = JSONArray()
                        parts.forEach { arr.put(it.trim().toInt()) }
                        outbound.put("reserved", arr)
                    }
                }
                // Peer configuration
                val peer = JSONObject()
                    .put("server", node.server)
                    .put("server_port", node.port)
                node.peerPublicKey?.let { peer.put("public_key", it) }
                    ?: node.publicKey?.let { peer.put("public_key", it) }
                node.preSharedKey?.let { peer.put("pre_shared_key", it) }
                peer.put("allowed_ips", JSONArray().put("0.0.0.0/0").put("::/0"))
                outbound.put("peers", JSONArray().put(peer))
            }

            ProxyType.SOCKS -> {
                outbound.put("type", "socks")
                node.username?.let { outbound.put("username", it) }
                node.password?.let { outbound.put("password", it) }
            }

            ProxyType.HTTP -> {
                outbound.put("type", "http")
                node.username?.let { outbound.put("username", it) }
                node.password?.let { outbound.put("password", it) }
                if (node.tls) {
                    outbound.put("tls", JSONObject().put("enabled", true))
                }
            }
        }

        node.network?.let { network ->
            if (network != "tcp" && network.isNotBlank()) {
                outbound.put("transport", buildTransport(node))
            }
        }
        return outbound
    }

    private fun buildTlsObject(node: ProxyNode, includeReality: Boolean = false): JSONObject {
        val tls = JSONObject()
            .put("enabled", node.tls)
        node.sni?.let { tls.put("server_name", it) }

        if (!node.alpn.isNullOrBlank()) {
            val alpnArray = JSONArray()
            node.alpn!!.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { alpnArray.put(it) }
            if (alpnArray.length() > 0) {
                tls.put("alpn", alpnArray)
            }
        }

        if (includeReality && !node.publicKey.isNullOrBlank()) {
            tls.put(
                "reality",
                JSONObject()
                    .put("enabled", true)
                    .put("public_key", node.publicKey)
                    .put("short_id", node.shortId ?: "")
            )
        }

        if (!node.fingerprint.isNullOrBlank()) {
            tls.put(
                "utls",
                JSONObject()
                    .put("enabled", true)
                    .put("fingerprint", node.fingerprint)
            )
        }

        return tls
    }

    // ── Transport ───────────────────────────────────────────────────

    private fun buildTransport(node: ProxyNode): JSONObject {
        val transport = JSONObject()
        when (node.network?.lowercase()) {
            "ws", "websocket" -> {
                transport.put("type", "ws")
                normalizedTransportPath(node.transportPath)?.let { transport.put("path", it) }
                firstTransportHost(node)?.let { transport.put("headers", JSONObject().put("Host", it)) }
            }
            "grpc" -> {
                transport.put("type", "grpc")
                node.transportServiceName?.takeIf { it.isNotBlank() }?.let {
                    transport.put("service_name", it)
                }
            }
            "h2", "http" -> {
                transport.put("type", "http")
                normalizedTransportPath(node.transportPath)?.let { transport.put("path", it) }
                firstTransportHost(node)?.let { hostValue ->
                    val hostArray = JSONArray()
                    hostValue.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { hostArray.put(it) }
                    if (hostArray.length() > 0) {
                        transport.put("host", hostArray)
                    }
                }
            }
            "httpupgrade", "http-upgrade" -> {
                transport.put("type", "httpupgrade")
                normalizedTransportPath(node.transportPath)?.let { transport.put("path", it) }
                firstTransportHost(node)?.let { transport.put("host", it) }
            }
        }
        return transport
    }

    private fun firstTransportHost(node: ProxyNode): String? {
        return node.transportHost?.takeIf { it.isNotBlank() }
            ?: node.sni?.takeIf { it.isNotBlank() }
    }

    private fun normalizedTransportPath(path: String?): String? {
        val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (value.startsWith("/")) value else "/$value"
    }
}
