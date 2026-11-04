package io.niko.raycontrol

import io.niko.fmt.AbstractBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.hysteria.parseHysteria2
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.naive.parseNaive
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.parseDuckSoft
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The profile rows of a raycontrol subscription (subscription-v1.md §4). They are parsed here, not by the general
 * importer: a node (or an attacker's node behind an `rcinv1:` link opened from a web page) may only send the
 * profiles the spec has, never a full config, custom JSON or an insecure TLS setting.
 */
object RayRows {

    private val SCHEMES = listOf("vless://", "hysteria2://", "hy2://", "naive+https://", "naive+quic://")

    /** The row's profile, or an exception that names what the link breaks. */
    fun parse(link: String): AbstractBean {
        val scheme = SCHEMES.firstOrNull { link.startsWith(it) }
            ?: throw IllegalArgumentException("not a raycontrol profile scheme: ${link.substringBefore("://")}")
        val bean: AbstractBean = when (scheme) {
            "vless://" -> {
                // the standard form only, never the VMess formats the general importer also tries
                val url = ("https://" + link.substringAfter("://")).toHttpUrl()
                require(url.password.isEmpty()) { "vless: not the standard link form" }
                val b = VMessBean().apply { alterId = -1 }
                b.parseDuckSoft(url)
                // every VLESS profile of a node carries the ML-KEM-768 second layer (R1, R2, R4)
                require(!b.vlessEncryption.isNullOrBlank() && b.vlessEncryption != "none") { "vless: no encryption" }
                // the bean keeps REALITY as security=tls with a public key
                if (url.queryParameter("security") == "reality" || !b.realityPubKey.isNullOrBlank()) {
                    // without pqv the profile is not post-quantum and the client must not take it (§4 R2)
                    require(!b.realityMldsa65Verify.isNullOrBlank()) { "reality: no pqv" }
                }
                require(b.allowInsecure != true) { "vless: insecure TLS" }
                b.certificates = ""
                b
            }

            "hysteria2://", "hy2://" -> {
                val b = parseHysteria2(link)
                require(b.protocolVersion == 2) { "hysteria2: version ${b.protocolVersion}" }
                // hy2-obfs is self-signed but pinned by niko-pin; insecure is never sent (§4 R3)
                require(b.allowInsecure != true) { "hysteria2: insecure TLS" }
                b.caText = ""
                b.protocol = HysteriaBean.PROTOCOL_UDP
                b
            }

            else -> {
                val b = parseNaive(link)
                b.certificates = ""
                b.extraHeaders = ""
                b
            }
        }
        // nothing outside the link's own fields: no config merged over the whole config, no raw outbound
        bean.customConfigJson = ""
        bean.customOutboundJson = ""
        bean.knockSequence = ""
        return bean
    }

    /**
     * The names that give a node away in the log: its address, the names its profile links dial or present (the
     * server, the TLS name, its own Yggdrasil peer) and its DoH host. A REALITY name is a public site's (cover,
     * github.com) unless it is under one of the node's own names (self-steal): only then is it the node's.
     * The public peers (niko-ygg-via) are not the user's and stay readable.
     */
    fun nodeHosts(host: String, doh: String?, links: List<String>): List<String> {
        val own = mutableListOf(host)
        doh?.toHttpUrlOrNull()?.host?.let(own::add)
        val realityNames = mutableListOf<String>()
        for (link in links) {
            val url = ("https://" + link.substringAfter("://")).toHttpUrlOrNull() ?: continue
            own += url.host
            url.queryParameter("niko-ygg")?.substringBefore(';')
                ?.let { ("https://" + it.substringAfter("://")).toHttpUrlOrNull()?.host }?.let(own::add)
            val name = url.queryParameter("sni") ?: url.queryParameter("tlsServerName") ?: continue
            if (url.queryParameter("security") == "reality") realityNames += name else own += name
        }
        val domains = own.filter { it.any(Char::isLetter) && '.' in it }
            .flatMap { listOf(it, it.substringAfter('.')) }.filter { '.' in it }
        own += realityNames.filter { name -> domains.any { name == it || name.endsWith(".$it") } }
        return own.map { it.trim('[', ']') }.filter { it.isNotBlank() && it != "inner" }.distinct()
    }

    /** The path of a subscription link, without its first slash, when it is long enough to hold a token. */
    fun subscriptionSecret(link: String): String? =
        link.toHttpUrlOrNull()?.encodedPath?.trim('/')?.takeIf { it.length >= 8 }
}
