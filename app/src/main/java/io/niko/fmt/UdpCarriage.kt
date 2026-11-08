package io.niko.fmt

import io.niko.database.ProxyEntity
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.v2ray.StandardV2RayBean

/**
 * Whether a profile carries UDP as datagrams of its own (Hysteria, TUIC, WireGuard, Shadowsocks, SOCKS5, a QUIC
 * transport): true; over a TCP stream or not at all (REALITY and other VLESS/VMess/Trojan over TCP, naive, HTTP, SSH,
 * AnyTLS, ShadowTLS, a DNS tunnel, a raycontrol Yggdrasil profile, UDP over TCP): false; not known (a custom config, a
 * plugin, Mieru): null.
 */
fun ProxyEntity.carriesUdpAsDatagrams(): Boolean? {
    val bean = requireBean()
    if (DnsTunnel.of(bean).enabled) return false
    if (!bean.yggPeer.isNullOrBlank()) return false
    return when (type) {
        ProxyEntity.TYPE_HYSTERIA, ProxyEntity.TYPE_TUIC, ProxyEntity.TYPE_WG -> true
        ProxyEntity.TYPE_SS -> (bean as ShadowsocksBean).sUoT != true
        ProxyEntity.TYPE_SOCKS -> (bean as SOCKSBean).let { it.protocol == SOCKSBean.PROTOCOL_SOCKS5 && it.sUoT != true }
        ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_TROJAN -> (bean as StandardV2RayBean).type == "quic"
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
