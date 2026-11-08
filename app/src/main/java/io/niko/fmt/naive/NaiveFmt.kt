package io.niko.fmt.naive

import io.niko.SingBoxOptions
import io.niko.database.DataStore
import io.niko.fmt.LOCALHOST
import io.niko.ktx.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

fun parseNaive(link: String): NaiveBean {
    val proto = link.substringAfter("+").substringBefore(":")
    val url = ("https://" + link.substringAfter("://")).toHttpUrlOrNull()
        ?: error("Invalid naive link")
    return NaiveBean().also {
        it.proto = proto
    }.apply {
        serverAddress = url.host
        serverPort = url.port
        username = url.username
        password = url.password
        sni = url.queryParameter("sni")
        certificates = url.queryParameter("cert")
        extraHeaders = url.queryParameter("extra-headers")?.unUrlSafe()?.replace("\r\n", "\n")
        insecureConcurrency = url.queryParameter("insecure-concurrency")?.toIntOrNull()
        // UDP over TCP (sing-box's UoT v2), only where the link says the server takes it: a server without it answers
        // the tunnel to its magic name and closes it, so every UDP flow would end in EOF
        sUoT = url.queryParameter("uot")?.lowercase() in setOf("2", "true", "1")
        name = url.fragment
        initializeDefaultValues()
    }
}

fun NaiveBean.toUri(proxyOnly: Boolean = false): String {
    val builder = linkBuilder().host(finalAddress).port(finalPort)
    if (username.isNotBlank()) {
        builder.username(username)
        if (password.isNotBlank()) {
            builder.password(password)
        }
    }
    if (!proxyOnly) {
        if (sni.isNotBlank()) {
            builder.addQueryParameter("sni", sni)
        }
        if (certificates.isNotBlank()) {
            builder.addQueryParameter("cert", certificates)
        }
        if (extraHeaders.isNotBlank()) {
            builder.addQueryParameter("extra-headers", extraHeaders)
        }
        if (name.isNotBlank()) {
            builder.encodedFragment(name.urlSafe())
        }
        if (insecureConcurrency > 0) {
            builder.addQueryParameter("insecure-concurrency", "$insecureConcurrency")
        }
        if (sUoT == true) builder.addQueryParameter("uot", "2")
    }
    return builder.toLink(if (proxyOnly) proto else "naive+$proto", false)
}

fun NaiveBean.buildNaiveConfig(port: Int): String {
    return JSONObject().apply {
        // process ipv6
        finalAddress = finalAddress.wrapIPV6Host()
        serverAddress = serverAddress.wrapIPV6Host()

        // process sni
        if (sni.isNotBlank()) {
            put("host-resolver-rules", "MAP $sni $finalAddress")
            finalAddress = sni
        } else {
            if (serverAddress.isIpAddress()) {
                // for naive, using IP as SNI name hardly happens
                // and host-resolver-rules cannot resolve the SNI problem
                // so do nothing
            } else {
                put("host-resolver-rules", "MAP $serverAddress $finalAddress")
                finalAddress = serverAddress
            }
        }

        put("listen", "socks://$LOCALHOST:$port")
        put("proxy", toUri(true))
        if (extraHeaders.isNotBlank()) {
            put("extra-headers", extraHeaders.split("\n").joinToString("\r\n"))
        }
        if (DataStore.logLevel > 0) {
            put("log", "")
        }
        if (insecureConcurrency > 0) {
            put("insecure-concurrency", insecureConcurrency)
        }
    }.toStringPretty()
}
/**
 * The core's naive outbound: Chromium's network stack (cronet) in the core, so the TLS and HTTP/2 of the tunnel are
 * Chrome's by construction, its sockets come from the core's dialer (the VPN's protect, a knock detour) and no
 * plugin process runs (review S8, FINGERPRINT.md §2).
 */
fun buildSingBoxOutboundNaiveBean(bean: NaiveBean): SingBoxOptions.Outbound = SingBoxOptions.Outbound().apply {
    type = "naive"
    _hack_config_map["server"] = bean.serverAddress
    _hack_config_map["server_port"] = bean.serverPort
    if (bean.username.isNotBlank()) _hack_config_map["username"] = bean.username
    if (bean.password.isNotBlank()) _hack_config_map["password"] = bean.password
    if ((bean.insecureConcurrency ?: 0) > 0) _hack_config_map["insecure_concurrency"] = bean.insecureConcurrency
    if (bean.proto == "quic") _hack_config_map["quic"] = true
    naiveHeaders(bean.extraHeaders).takeIf { it.isNotEmpty() }?.let { _hack_config_map["extra_headers"] = it }
    _hack_config_map["tls"] = buildMap {
        put("enabled", true)
        // the name naive shows: the SNI, else a server name (an address gets none of its own)
        val name = bean.sni.takeIf { it.isNotBlank() } ?: bean.serverAddress.takeUnless { it.isIpAddress() }
        if (name != null) put("server_name", name)
        // naive takes no "insecure": the certificate is checked, against these roots when given
        if (bean.certificates.isNotBlank()) put("certificate", listOf(bean.certificates))
    }
}

/** "Name: value" lines of a naive profile's extra headers. */
fun naiveHeaders(text: String?): Map<String, String> = text.orEmpty().lines().mapNotNull { line ->
    val name = line.substringBefore(':', "").trim()
    if (name.isEmpty()) null else name to line.substringAfter(':').trim()
}.toMap()
