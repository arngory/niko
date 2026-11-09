package io.niko.fmt.trojan_go

import io.niko.IPv6Mode
import io.niko.database.DataStore
import io.niko.fmt.LOCALHOST
import io.niko.ktx.*
import io.niko.Protocols
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

fun parseTrojanGo(server: String): TrojanGoBean {
    val link = server.replace("trojan-go://", "https://").toHttpUrlOrNull() ?: error(
        "invalid trojan-go link"
    )
    return TrojanGoBean().apply {
        serverAddress = link.host
        serverPort = link.port
        password = link.username
        link.queryParameter("sni")?.let {
            sni = it
        }
        link.queryParameter("type")?.let { lType ->
            type = lType

            when (type) {
                "ws" -> {
                    link.queryParameter("host")?.let {
                        host = it
                    }
                    link.queryParameter("path")?.let {
                        path = it
                    }
                }
                else -> {
                }
            }
        }
        link.queryParameter("encryption")?.let {
            encryption = it
        }
        link.queryParameter("plugin")?.let {
            plugin = it
        }
        link.fragment.takeIf { !it.isNullOrBlank() }?.let {
            name = it
        }
    }
}

fun TrojanGoBean.toUri(): String {
    val builder = linkBuilder().username(password).host(serverAddress).port(serverPort)
    if (sni.isNotBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (type.isNotBlank() && type != "original") {
        builder.addQueryParameter("type", type)

        when (type) {
            "ws" -> {
                if (host.isNotBlank()) {
                    builder.addQueryParameter("host", host)
                }
                if (path.isNotBlank()) {
                    builder.addQueryParameter("path", path)
                }
            }
        }
    }
    if (type.isNotBlank() && type != "none") {
        builder.addQueryParameter("encryption", encryption)
    }
    if (plugin.isNotBlank()) {
        builder.addQueryParameter("plugin", plugin)
    }

    if (name.isNotBlank()) {
        builder.encodedFragment(name.urlSafe())
    }

    return builder.toLink("trojan-go")
}

fun TrojanGoBean.buildTrojanGoConfig(port: Int): String {
    return JSONObject().apply {
        put("run_type", "client")
        put("local_addr", LOCALHOST)
        put("local_port", port)
        put("remote_addr", finalAddress)
        put("remote_port", finalPort)
        put("password", JSONArray().apply {
            put(password)
        })
        put("log_level", if (DataStore.logLevel > 0) 0 else 2)
//        if (Protocols.shouldEnableMux("trojan-go")) put("mux", JSONObject().apply {
//            put("enabled", true)
//            put("concurrency", DataStore.muxConcurrency)
//        })
        put("tcp", JSONObject().apply {
            put("prefer_ipv4", DataStore.ipv6Mode <= IPv6Mode.ENABLE)
        })

        when (type) {
            "original" -> {
            }
            "ws" -> put("websocket", JSONObject().apply {
                put("enabled", true)
                put("host", host)
                put("path", path)
            })
        }

        if (sni.isBlank() && finalAddress == LOCALHOST && !serverAddress.isIpAddress()) {
            sni = serverAddress
        }

        put("ssl", JSONObject().apply {
            if (sni.isNotBlank()) put("sni", sni)
            if (allowInsecure) put("verify", false)
        })

        when {
            encryption == "none" -> {
            }
            encryption.startsWith("ss;") -> put("shadowsocks", JSONObject().apply {
                put("enabled", true)
                put("method", encryption.substringAfter(";").substringBefore(":"))
                put("password", encryption.substringAfter(":"))
            })
        }
    }.toStringPretty()
}

fun JSONObject.parseTrojanGo(): TrojanGoBean {
    return TrojanGoBean().applyDefaultValues().apply {
        serverAddress = optString("remote_addr", serverAddress)
        serverPort = optInt("remote_port", serverPort)
        when (val pass = get("password")) {
            is String -> {
                password = pass
            }
            is List<*> -> {
                password = pass[0] as String
            }
        }
        optJSONArray("ssl")?.apply {
            sni = optString("sni", sni)
        }
        optJSONArray("websocket")?.apply {
            if (optBoolean("enabled", false)) {
                type = "ws"
                host = optString("host", host)
                path = optString("path", path)
            }
        }
        optJSONArray("shadowsocks")?.apply {
            if (optBoolean("enabled", false)) {
                encryption = "ss;${optString("method", "")}:${optString("password", "")}"
            }
        }
    }
}
/** Trojan-Go's `encryption=ss;METHOD:PASSWORD` as the core's option, null for none. */
fun TrojanGoBean.shadowsocksLayer(): Map<String, String>? {
    val spec = encryption.orEmpty().trim()
    if (spec.isEmpty() || spec == "none") return null
    require(spec.startsWith("ss;")) { "Trojan-Go encryption $spec: only ss;method:password" }
    val methodAndPassword = spec.removePrefix("ss;")
    val method = methodAndPassword.substringBefore(':')
    val password = methodAndPassword.substringAfter(':', "")
    require(password.isNotEmpty()) { "Trojan-Go encryption: no Shadowsocks password" }
    return mapOf("method" to method.ifEmpty { "aes-128-gcm" }, "password" to password)
}

/** The core's Trojan outbound for a Trojan-Go profile: TLS to the server, WebSocket for type=ws. */
fun buildSingBoxOutboundTrojanGoBean(
    bean: TrojanGoBean, globalInsecure: Boolean = DataStore.globalAllowInsecure,
): io.niko.SingBoxOptions.Outbound_TrojanOptions {
    return io.niko.SingBoxOptions.Outbound_TrojanOptions().apply {
        // Trojan-Go's own layers, in the core (neko): the Shadowsocks AEAD inside TLS, the smux of many streams
        bean.shadowsocksLayer()?.let { _hack_config_map["trojan_go_shadowsocks"] = it }
        if (bean.mux == true) _hack_config_map["trojan_go_mux"] = true
        type = "trojan"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password
        tls = io.niko.SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni.takeIf { !it.isNullOrBlank() } ?: bean.serverAddress
            insecure = bean.allowInsecure == true || globalInsecure
        }
        if (bean.type == "ws") transport = io.niko.SingBoxOptions.V2RayTransportOptions_WebsocketOptions().apply {
            type = "ws"
            path = bean.path.takeIf { !it.isNullOrBlank() } ?: "/"
            headers = mutableMapOf<String, String>().apply {
                (bean.host.takeIf { !it.isNullOrBlank() } ?: bean.sni.takeIf { !it.isNullOrBlank() })?.let { put("Host", it) }
            }
        }
    }
}
