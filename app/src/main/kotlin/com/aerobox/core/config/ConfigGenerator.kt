package com.aerobox.core.config

import com.aerobox.data.model.ProxyNode
import com.aerobox.data.model.ProxyType
import org.json.JSONArray
import org.json.JSONObject

object ConfigGenerator {
    fun generateSingBoxConfig(node: ProxyNode): String {
        val config = JSONObject()

        config.put(
            "log",
            JSONObject()
                .put("level", "info")
                .put("timestamp", true)
        )

        config.put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("tag", "google")
                                .put("address", "tls://8.8.8.8")
                        )
                        .put(
                            JSONObject()
                                .put("tag", "local")
                                .put("address", "223.5.5.5")
                                .put("detour", "direct")
                        )
                )
                .put(
                    "rules",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("geosite", "cn")
                                .put("server", "local")
                        )
                )
        )

        config.put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("type", "tun")
                    .put("interface_name", "tun0")
                    .put("inet4_address", JSONArray().put("172.19.0.1/30"))
                    .put("mtu", 9000)
                    .put("auto_route", true)
                    .put("strict_route", true)
                    .put("stack", "mixed")
                    .put("sniff", true)
            )
        )

        val proxyOutbound = buildProxyOutbound(node).put("tag", "proxy")
        config.put(
            "outbounds",
            JSONArray()
                .put(proxyOutbound)
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
                .put(JSONObject().put("type", "dns").put("tag", "dns-out"))
        )

        config.put(
            "route",
            JSONObject()
                .put("auto_detect_interface", true)
                .put("final", "proxy")
                .put(
                    "rules",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("protocol", "dns")
                                .put("outbound", "dns-out")
                        )
                        .put(
                            JSONObject()
                                .put("geosite", JSONArray().put("cn"))
                                .put("geoip", JSONArray().put("cn"))
                                .put("outbound", "direct")
                        )
                        .put(
                            JSONObject()
                                .put("geosite", JSONArray().put("category-ads-all"))
                                .put("outbound", "block")
                        )
                )
        )

        return config.toString(2)
    }

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
            }
        }

        node.network?.let { outbound.put("network", it) }
        return outbound
    }

    private fun buildTlsObject(node: ProxyNode, includeReality: Boolean = false): JSONObject {
        val tls = JSONObject()
            .put("enabled", node.tls)
        node.sni?.let { tls.put("server_name", it) }

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
}
