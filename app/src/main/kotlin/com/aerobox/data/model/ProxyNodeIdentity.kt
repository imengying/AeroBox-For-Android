package com.aerobox.data.model

import java.util.Locale

private fun String?.normalizedIdentityValue(): String {
    return this
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
}

private fun String?.normalizedPathValue(): String {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return ""
    return if (value.startsWith("/")) value.lowercase(Locale.ROOT) else "/${value.lowercase(Locale.ROOT)}"
}

private fun String.normalizedServerValue(): String {
    return trim()
        .removePrefix("[")
        .removeSuffix("]")
        .substringBefore('%')
        .lowercase(Locale.ROOT)
}

fun ProxyNode.connectionFingerprint(includeName: Boolean = true): String {
    return buildList {
        if (includeName) add(name.normalizedIdentityValue())
        add(type.name)
        add(server.normalizedServerValue())
        add(port.toString())
        add(detour.normalizedIdentityValue())
        add(bindInterface.normalizedIdentityValue())
        add(inet4BindAddress.normalizedIdentityValue())
        add(inet6BindAddress.normalizedIdentityValue())
        add(bindAddressNoPort?.toString().normalizedIdentityValue())
        add(routingMark.normalizedIdentityValue())
        add(reuseAddr?.toString().normalizedIdentityValue())
        add(netns.normalizedIdentityValue())
        add(connectTimeout.normalizedIdentityValue())
        add(tcpFastOpen?.toString().normalizedIdentityValue())
        add(tcpMultiPath?.toString().normalizedIdentityValue())
        add(disableTcpKeepAlive?.toString().normalizedIdentityValue())
        add(tcpKeepAlive.normalizedIdentityValue())
        add(tcpKeepAliveInterval.normalizedIdentityValue())
        add(udpFragment?.toString().normalizedIdentityValue())
        add(domainResolver.normalizedIdentityValue())
        add(networkStrategy.normalizedIdentityValue())
        add(networkType.normalizedIdentityValue())
        add(fallbackNetworkType.normalizedIdentityValue())
        add(fallbackDelay.normalizedIdentityValue())
        add(domainStrategy.normalizedIdentityValue())
        add(uuid.normalizedIdentityValue())
        add(alterId.toString())
        add(password.normalizedIdentityValue())
        add(method.normalizedIdentityValue())
        add(flow.normalizedIdentityValue())
        add(security.normalizedIdentityValue())
        add(effectiveEnabledNetwork().normalizedIdentityValue())
        add(effectiveTransportType().normalizedIdentityValue())
        add(globalPadding?.toString().normalizedIdentityValue())
        add(authenticatedLength?.toString().normalizedIdentityValue())
        add(tls.toString())
        add(sni.normalizedIdentityValue())
        add(transportHost.normalizedIdentityValue())
        add(transportPath.normalizedPathValue())
        add(transportServiceName.normalizedIdentityValue())
        add(transportMethod.normalizedIdentityValue())
        add(transportHeaders.normalizedIdentityValue())
        add(transportIdleTimeout.normalizedIdentityValue())
        add(transportPingTimeout.normalizedIdentityValue())
        add(transportPermitWithoutStream?.toString().normalizedIdentityValue())
        add(wsMaxEarlyData?.toString().normalizedIdentityValue())
        add(wsEarlyDataHeaderName.normalizedIdentityValue())
        add(alpn.normalizedIdentityValue())
        add(fingerprint.normalizedIdentityValue())
        add(publicKey.normalizedIdentityValue())
        add(shortId.normalizedIdentityValue())
        add(packetEncoding.normalizedIdentityValue())
        add(username.normalizedIdentityValue())
        add(socksVersion.normalizedIdentityValue())
        add(httpHeaders.normalizedIdentityValue())
        add(allowInsecure.toString())
        add(plugin.normalizedIdentityValue())
        add(pluginOpts.normalizedIdentityValue())
        add(udpOverTcpEnabled?.toString().normalizedIdentityValue())
        add(udpOverTcpVersion?.toString().normalizedIdentityValue())
        add(obfsType.normalizedIdentityValue())
        add(obfsPassword.normalizedIdentityValue())
        add(serverPorts.normalizedIdentityValue())
        add(hopInterval.normalizedIdentityValue())
        add(upMbps?.toString().normalizedIdentityValue())
        add(downMbps?.toString().normalizedIdentityValue())
        add(muxEnabled?.toString().normalizedIdentityValue())
        add(muxProtocol.normalizedIdentityValue())
        add(muxMaxConnections?.toString().normalizedIdentityValue())
        add(muxMinStreams?.toString().normalizedIdentityValue())
        add(muxMaxStreams?.toString().normalizedIdentityValue())
        add(muxPadding?.toString().normalizedIdentityValue())
        add(muxBrutalEnabled?.toString().normalizedIdentityValue())
        add(muxBrutalUpMbps?.toString().normalizedIdentityValue())
        add(muxBrutalDownMbps?.toString().normalizedIdentityValue())
        add(congestionControl.normalizedIdentityValue())
        add(udpRelayMode.normalizedIdentityValue())
        add(udpOverStream?.toString().normalizedIdentityValue())
        add(zeroRttHandshake?.toString().normalizedIdentityValue())
        add(heartbeat.normalizedIdentityValue())
    }.joinToString("|")
}

private fun ProxyNode.normalizedName(): String = name.normalizedIdentityValue()

fun ProxyNode.normalizedDisplayName(): String = normalizedName()

private fun ProxyNode.hasSameEndpoint(other: ProxyNode): Boolean {
    return server.normalizedServerValue() == other.server.normalizedServerValue() &&
        port == other.port
}

fun ProxyNode.matchScore(other: ProxyNode): Int {
    if (type != other.type) return Int.MIN_VALUE

    var score = 20
    if (connectionFingerprint(includeName = false) == other.connectionFingerprint(includeName = false)) {
        score += 100
    }
    if (normalizedName().isNotEmpty() && normalizedName() == other.normalizedName()) {
        score += 40
    }
    if (hasSameEndpoint(other)) {
        score += 30
    }
    return score
}
