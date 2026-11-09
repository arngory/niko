/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.niko.fmt.mieru

import io.niko.ktx.toStringPretty
import org.json.JSONArray
import org.json.JSONObject

fun MieruBean.buildMieruConfig(port: Int): String {
    val serverInfo = JSONArray().apply {
        put(JSONObject().apply {
            put("ipAddress", finalAddress)
            put("portBindings", JSONArray().apply {
                put(JSONObject().apply {
                    put("port", finalPort)
                    put("protocol", protocol)
                })
            })
        })
    }
    return JSONObject().apply {
        put("activeProfile", "default")
        put("socks5Port", port)
        // the app's log level (mieru: FATAL, ERROR, WARN, INFO, DEBUG, TRACE)
        put("loggingLevel", when (io.niko.database.DataStore.logLevel) {
            0 -> "FATAL"
            1 -> "WARN"
            3 -> "DEBUG"
            4 -> "TRACE"
            else -> "INFO"
        })
        put("profiles", JSONArray().apply {
            put(JSONObject().apply {
                put("profileName", "default")
                put("user", JSONObject().apply {
                    put("name", username)
                    put("password", password)
                })
                put("servers", serverInfo)
                put("mtu", mtu)
            })
        })
    }.toStringPretty()
}

/**
 * The core's Mieru outbound (mieru's own client library in the core, no plugin): the server, its port, the
 * transport, the user, and what a mierus:// link sets besides.
 */
fun buildSingBoxOutboundMieruBean(bean: MieruBean): io.niko.SingBoxOptions.Outbound = io.niko.SingBoxOptions.Outbound().apply {
    type = "mieru"
    _hack_config_map["server"] = bean.serverAddress
    _hack_config_map["server_port"] = bean.serverPort
    val udp = bean.protocol.equals("UDP", ignoreCase = true)
    _hack_config_map["transport"] = if (udp) "udp" else "tcp"
    _hack_config_map["username"] = bean.username
    _hack_config_map["password"] = bean.password
    if (udp && bean.mtu in 1280..1500) _hack_config_map["mtu"] = bean.mtu
    bean.multiplexing.takeIf { !it.isNullOrBlank() }?.let { _hack_config_map["multiplexing"] = it }
    bean.handshakeMode.takeIf { !it.isNullOrBlank() }?.let { _hack_config_map["handshake_mode"] = it }
    bean.trafficPattern.takeIf { !it.isNullOrBlank() }?.let { _hack_config_map["traffic_pattern"] = it }
}

/**
 * A mierus:// link (mieru's pkg/appctl/url.go): mierus://USER:PASS@HOST?profile=NAME&mtu=N&multiplexing=…
 * &handshake-mode=…&traffic-pattern=…&port=P&protocol=TCP, the port and protocol pairs repeated. A profile has one
 * port and transport: the first pair (a range: its first port).
 */
fun parseMieru(link: String): MieruBean {
    val uri = java.net.URI(link.trim())
    require(uri.scheme.equals("mierus", ignoreCase = true)) { "not a mierus:// link" }
    val user = uri.rawUserInfo ?: error("mierus: no user")
    val params = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.map {
        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }
    fun param(name: String) = params.firstOrNull { it.first == name }?.second
    val port = param("port")?.substringBefore('-')?.substringBefore(':')?.trim()?.toIntOrNull()
        ?: error("mierus: no port")
    require(port in 1..65535) { "mierus: port $port" }
    return MieruBean().apply {
        initializeDefaultValues()
        serverAddress = uri.host?.trim('[', ']') ?: error("mierus: no host")
        serverPort = port
        username = java.net.URLDecoder.decode(user.substringBefore(':'), "UTF-8")
        password = java.net.URLDecoder.decode(user.substringAfter(':', ""), "UTF-8")
        protocol = if (param("protocol").equals("UDP", ignoreCase = true)) "UDP" else "TCP"
        param("mtu")?.toIntOrNull()?.takeIf { it in 1280..1500 }?.let { mtu = it }
        multiplexing = param("multiplexing").orEmpty()
        handshakeMode = param("handshake-mode").orEmpty()
        trafficPattern = param("traffic-pattern").orEmpty()
        name = param("profile").orEmpty().ifBlank { uri.rawFragment?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty() }
    }
}
