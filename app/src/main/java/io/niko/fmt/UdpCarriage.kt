package io.niko.fmt

import io.niko.SingBoxOptions.Rule_DefaultOptions
import io.niko.database.ProxyEntity
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.socks.SOCKSBean

/**
 * Whether a profile carries UDP as datagrams of its own (Hysteria, TUIC, WireGuard, Shadowsocks, SOCKS5): true; inside
 * an ordered stream or not at all (VLESS/VMess/Trojan on any transport, REALITY among them: even the QUIC transport
 * is one QUIC stream a connection, its UDP packed into it; naive, HTTP, SSH, AnyTLS, ShadowTLS, a DNS tunnel, a
 * raycontrol Yggdrasil profile, UDP over TCP): false; not known (a custom config, a plugin, Mieru): null.
 */
fun ProxyEntity.carriesUdpAsDatagrams(): Boolean? {
    val bean = requireBean()
    if (DnsTunnel.of(bean).enabled) return false
    if (!bean.yggPeer.isNullOrBlank()) return false
    return when (type) {
        ProxyEntity.TYPE_HYSTERIA, ProxyEntity.TYPE_TUIC, ProxyEntity.TYPE_WG -> true
        ProxyEntity.TYPE_SS -> (bean as ShadowsocksBean).sUoT != true
        ProxyEntity.TYPE_SOCKS -> (bean as SOCKSBean).let { it.protocol == SOCKSBean.PROTOCOL_SOCKS5 && it.sUoT != true }
        ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_TROJAN,
        ProxyEntity.TYPE_NAIVE, ProxyEntity.TYPE_HTTP, ProxyEntity.TYPE_SSH, ProxyEntity.TYPE_TROJAN_GO,
        ProxyEntity.TYPE_ANYTLS, ProxyEntity.TYPE_SHADOWTLS -> false
        else -> null
    }
}

/**
 * QUIC over a chain where some hop carries UDP inside a TCP stream (or not at all) is TCP inside TCP: head-of-line
 * blocking, worse than the browser's own fallback to TCP. True when every hop is known and one of them is such.
 */
fun udpRidesAStream(chain: List<ProxyEntity>): Boolean {
    val carriage = chain.map { it.carriesUdpAsDatagrams() }
    return carriage.isNotEmpty() && carriage.none { it == null } && carriage.any { it == false }
}

/** The rule that refuses QUIC (an ICMP unreachable each time, never turned into a silent drop): [sniffed] by protocol. */
fun quicRefusal(sniffed: Boolean) = Rule_DefaultOptions().apply {
    network = listOf("udp")
    if (sniffed) protocol = listOf("quic") else port = listOf(443)
    action = "reject"
    // sing-box drops instead of rejecting past 50 rejects in 30 s: the browser would wait out QUIC, not go to TCP
    _hack_config_map["no_drop"] = true
}

/**
 * A copy of the user's [rule] to the proxy that refuses its QUIC, to go just before it: the rule would take QUIC into
 * the proxy ahead of the refusal at the end. Null where a copy would not mean "this rule's QUIC": an inverted rule
 * (the added conditions would invert too), one for TCP only or for another sniffed protocol, and without sniffing one
 * with ports of its own (UDP 443 is the only sign of QUIC then).
 */
fun quicRefusalBefore(rule: Rule_DefaultOptions, sniffed: Boolean): Rule_DefaultOptions? {
    if (rule.invert == true) return null
    if (!rule.network.isNullOrEmpty() && "udp" !in rule.network) return null
    if (!rule.protocol.isNullOrEmpty() && "quic" !in rule.protocol) return null
    if (!sniffed && (!rule.port.isNullOrEmpty() || !rule.port_range.isNullOrEmpty())) return null
    // the rule as it goes to the core (its typed fields, its extra keys, the user's own JSON), its outbound out
    val match = rule.asMap().toMutableMap()
    for (key in listOf("outbound", "action", "network", "protocol", "tls_fragment", "tls_record_fragment")) match.remove(key)
    return quicRefusal(sniffed).also { refusal ->
        val own = refusal.asMap()
        refusal.network = null
        refusal.protocol = null
        refusal.port = null
        refusal.action = null
        refusal._hack_config_map.clear()
        refusal._hack_config_map.putAll(match)
        refusal._hack_config_map.putAll(own)
    }
}
