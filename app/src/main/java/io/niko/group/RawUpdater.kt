package io.niko.group

import io.niko.fmt.LOCAL_PROXY_USER
import android.annotation.SuppressLint
import android.os.Build
import io.niko.R
import io.niko.bg.SubscriptionUpdater
import io.niko.database.*
import io.niko.fmt.AbstractBean
import io.niko.fmt.http.HttpBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.hysteria.parseHysteria1Json
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.shadowsocks.parseShadowsocks
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.trojan.TrojanBean
import io.niko.fmt.trojan_go.parseTrojanGo
import io.niko.fmt.tuic.TuicBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.isTLS
import io.niko.fmt.v2ray.setTLS
import io.niko.fmt.wireguard.AmneziaWG
import io.niko.fmt.wireguard.WireGuardBean
import io.niko.ktx.*
import libcore.Libcore
import io.niko.Protocols
import io.niko.proxy.anytls.AnyTLSBean
import io.niko.proxy.config.ConfigBean
import io.niko.utils.Util
import org.ini4j.Ini
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.Tag
import java.io.StringReader
import androidx.core.net.toUri

@Suppress("EXPERIMENTAL_API_USAGE")
object RawUpdater : GroupUpdater() {

    @SuppressLint("Recycle")
    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    ) {

        val link = subscription.link
        val autoUpdateBefore = subscription.autoUpdate to subscription.autoUpdateDelay
        var proxies: List<AbstractBean>
        if (link.startsWith("content://")) {
            val contentText = app.contentResolver.openInputStream(link.toUri())
                ?.bufferedReader()
                ?.readText()

            proxies = contentText?.let { parseRaw(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                trySocks5(DataStore.mixedPort, LOCAL_PROXY_USER, DataStore.localProxyPassword)
                tryH3Direct()
                when (DataStore.appTLSVersion) {
                    "1.3" -> restrictedTLS()
                }
            }.newRequest().apply {
                if (DataStore.allowInsecureOnRequest) {
                    allowInsecure()
                }
                setURL(subscription.link)
                setUserAgent(subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT)
                // panels that limit devices (Remnawave, Marzban) identify them by these headers
                if (subscription.sendHwid) {
                    setHeader("x-hwid", subscriptionDeviceId(DataStore.subscriptionHwid, subscription.link))
                    setHeader("x-device-os", "Android")
                    setHeader("x-ver-os", Build.VERSION.RELEASE)
                    setHeader("x-device-model", Build.MODEL)
                }
            }.execute()
            proxies = parseRaw(Util.getStringBox(response.contentString))
                ?: error(app.getString(R.string.no_proxies_found))

            subscription.subscriptionUserinfo =
                Util.getStringBox(response.getHeader("Subscription-Userinfo"))

            subscription.announce =
                decodeSubscriptionHeader(Util.getStringBox(response.getHeader("announce")))

            // a freshly imported subscription (default name) takes the provider's name and interval
            if (proxyGroup.name?.startsWith("Subscription #") == true) {
                var remoteName =
                    decodeSubscriptionHeader(Util.getStringBox(response.getHeader("profile-title")))
                if (remoteName.isBlank()) {
                    remoteName = Util.getStringBox(response.getHeader("content-disposition"))
                    if (remoteName.isNotBlank()) remoteName = Util.decodeFilename(remoteName)
                }
                if (remoteName.isNotBlank()) {
                    proxyGroup.name = remoteName
                }
            }
            // the provider's interval only on the first update: later the user's choice wins
            if (subscription.lastUpdated == 0) subscriptionUpdateIntervalMinutes(
                Util.getStringBox(response.getHeader("profile-update-interval"))
            )?.let {
                subscription.autoUpdate = true
                subscription.autoUpdateDelay = it
            }
        }

        // the provider must not rewrite the whole local config (inbounds, DNS, Clash API) of the app
        proxies = proxies.filter { bean ->
            if (bean is ConfigBean && bean.type == 0) {
                Logs.w("skip ${bean.displayName()}: a subscription can not ship a full sing-box config")
                false
            } else {
                bean.customConfigJson = ""
                true
            }
        }

        val proxiesMap = LinkedHashMap<String, AbstractBean>()
        for (proxy in proxies) {
            var index = 0
            var name = proxy.displayName()
            while (proxiesMap.containsKey(name)) {
                println("Exists name: $name")
                index++
                name = name.replace(" (${index - 1})", "")
                name = "$name ($index)"
                proxy.name = name
            }
            proxiesMap[proxy.displayName()] = proxy
        }
        proxies = proxiesMap.values.toList()

        if (subscription.forceResolve) forceResolve(proxies, proxyGroup.id)

        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            Logs.d("Before deduplication: ${proxies.size}")
            val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
            val uniqueNames = HashMap<Protocols.Deduplication, String>()
            for (_proxy in proxies) {
                val proxy = Protocols.Deduplication(_proxy, _proxy.javaClass.toString())
                if (!uniqueProxies.add(proxy)) {
                    val index = uniqueProxies.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(_proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = _proxy.displayName()
                }
            }
            uniqueProxies.retainAll(uniqueNames.keys)
            proxies = uniqueProxies.toList().map { it.bean }
        }

        Logs.d("New profiles: ${proxies.size}")

        val nameMap = proxies.associateBy { bean ->
            bean.displayName()
        }

        Logs.d("Unique profiles: ${nameMap.size}")

        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val name = entity.displayName()
            if (nameMap.contains(name)) name to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((name, bean) in nameMap.entries) {
            if (toReplace.contains(name)) {
                val entity = toReplace[name]!!
                val existsBean = entity.requireBean()
                // 更新订阅，保留自定义覆写设置
                bean.customOutboundJson = existsBean.customOutboundJson
                bean.customConfigJson = existsBean.customConfigJson
                bean.knockSequence = existsBean.knockSequence
                bean.knockInterval = existsBean.knockInterval
                bean.knockTtl = existsBean.knockTtl
                bean.knockSilence = existsBean.knockSilence
                bean.tlsFragment = existsBean.tlsFragment
                bean.certPins = existsBean.certPins
                bean.dnsTunnel = existsBean.dnsTunnel
                bean.dnsTunnelResolver = existsBean.dnsTunnelResolver
                bean.dnsTunnelDomain = existsBean.dnsTunnelDomain
                bean.dnsTunnelKey = existsBean.dnsTunnelKey
                bean.dnsTunnelMethod = existsBean.dnsTunnelMethod
                bean.dnsTunnelFixedUpstream = existsBean.dnsTunnelFixedUpstream
                bean.dnsTunnelAuth = existsBean.dnsTunnelAuth
                bean.udpPrelude = existsBean.udpPrelude
                bean.udpPreludeSni = existsBean.udpPreludeSni
                bean.udpPreludeTtl = existsBean.udpPreludeTtl
                bean.udpReorderInitial = existsBean.udpReorderInitial
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: $name")
                    }

                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: $name")
                    }

                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(
                    ProxyEntity(
                        groupId = proxyGroup.id, userOrder = userOrder
                    ).apply {
                        putBean(bean)
                    })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate).also {
            Logs.d("Updated profiles: $it")
        }

        SagerDatabase.proxyDao.deleteProxy(toDelete).also {
            Logs.d("Deleted profiles: $it")
        }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()

        if (existCount != proxies.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${proxies.size}")
        }

        subscription.lastUpdated = (System.currentTimeMillis() / 1000).toInt()
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        // profile-update-interval may have turned the auto update on
        if (autoUpdateBefore != (subscription.autoUpdate to subscription.autoUpdateDelay)) {
            SubscriptionUpdater.reconfigureUpdater()
        }
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup, changed, added, updated, deleted, duplicate, byUser
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        if (text.contains("proxies:")) {

            // clash & meta

            try {

                val yaml = loadClashYaml(text)

                val globalClientFingerprint = yaml["global-client-fingerprint"]?.toString() ?: ""

                for (item in (yaml["proxies"] as? List<*> ?: error(
                    app.getString(R.string.no_proxies_found_in_file)
                ))) {
                    val proxy = item as? Map<String, Any?> ?: continue
                    // Note: YAML numbers parsed as "Long"

                    // one broken entry must not drop the whole subscription
                    try {
                    when (proxy["type"] as String) {
                        "socks5" -> {
                            proxies.add(SOCKSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }

                        "http" -> {
                            proxies.add(HttpBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                name = proxy["name"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                            })
                        }

                        "ss" -> {
                            val ssPlugin = mutableListOf<String>()
                            if (proxy.contains("plugin")) {
                                val opts = proxy["plugin-opts"] as Map<String, Any?>
                                when (proxy["plugin"]) {
                                    "obfs" -> {
                                        ssPlugin.apply {
                                            add("obfs-local")
                                            add("obfs=" + (opts["mode"]?.toString() ?: ""))
                                            add("obfs-host=" + (opts["host"]?.toString() ?: ""))
                                        }
                                    }

                                    "v2ray-plugin" -> {
                                        ssPlugin.apply {
                                            add("v2ray-plugin")
                                            add("mode=" + (opts["mode"]?.toString() ?: ""))
                                            if (opts["tls"]?.toString() == "true") add("tls")
                                            add("host=" + (opts["host"]?.toString() ?: ""))
                                            add("path=" + (opts["path"]?.toString() ?: ""))
                                            if (opts["mux"]?.toString() == "true") add("mux=8")
                                        }
                                    }
                                }
                            }
                            proxies.add(ShadowsocksBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                password = proxy["password"]?.toString()
                                method = clashCipher(proxy["cipher"] as String)
                                plugin = ssPlugin.joinToString(";")
                                name = proxy["name"]?.toString()
                            })
                        }

                        "vmess", "vless", "trojan" -> {
                            val bean = when (proxy["type"] as String) {
                                "vmess" -> VMessBean()
                                "vless" -> VMessBean().apply {
                                    alterId = -1 // make it VLESS
                                    packetEncoding = 2 // clash meta default XUDP
                                }

                                "trojan" -> TrojanBean().apply {
                                    security = "tls"
                                }

                                else -> error("impossible")
                            }

                            bean.serverAddress = proxy["server"]?.toString() ?: continue
                            bean.serverPort = proxy["port"]?.toString()?.toIntOrNull() ?: continue

                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> bean.name = opt.value?.toString()
                                    "password" -> if (bean is TrojanBean) bean.password =
                                        opt.value?.toString()

                                    "uuid" -> if (bean is VMessBean) bean.uuid =
                                        opt.value?.toString()

                                    "alterId" -> if (bean is VMessBean && !bean.isVLESS) bean.alterId =
                                        opt.value?.toString()?.toIntOrNull()

                                    "cipher" -> if (bean is VMessBean && !bean.isVLESS) bean.encryption =
                                        (opt.value as? String)

                                    "flow" -> if (bean is VMessBean && bean.isVLESS) {
                                        (opt.value as? String)?.let {
                                            if (it.contains("xtls-rprx-vision")) {
                                                bean.encryption = "xtls-rprx-vision"
                                            }
                                        }
                                    }

                                    // mihomo's VLESS post-quantum encryption, "none" = off
                                    "encryption" -> if (bean is VMessBean && bean.isVLESS) {
                                        (opt.value as? String)?.trim()
                                            ?.takeIf { it.isNotEmpty() && it != "none" }
                                            ?.let { bean.vlessEncryption = it }
                                    }

                                    "packet-encoding" -> if (bean is VMessBean) {
                                        bean.packetEncoding = when ((opt.value as? String)) {
                                            "packetaddr" -> 1
                                            "xudp" -> 2
                                            else -> 0
                                        }
                                    }

                                    "tls" -> if (bean is VMessBean) {
                                        bean.security =
                                            if (opt.value as? Boolean == true) "tls" else ""
                                    }

                                    "servername", "sni" -> bean.sni = opt.value?.toString()

                                    "alpn" -> bean.alpn =
                                        (opt.value as? List<Any>)?.joinToString("\n")

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value as? Boolean == true

                                    "client-fingerprint" -> bean.utlsFingerprint =
                                        opt.value as String

                                    "reality-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (realityOpt in it) {
                                            bean.security = "tls"

                                            when (realityOpt.key) {
                                                "public-key" -> bean.realityPubKey =
                                                    realityOpt.value?.toString()

                                                "short-id" -> bean.realityShortId =
                                                    realityOpt.value?.toString()
                                            }
                                        }
                                    }

                                    "network" -> {
                                        when (opt.value) {
                                            "h2", "http" -> bean.type = "http"
                                            "ws", "grpc" -> bean.type = opt.value as String
                                        }
                                    }

                                    "ws-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (wsOpt in it) {
                                            when (wsOpt.key) {
                                                "headers" -> (wsOpt.value as? Map<Any, Any?>)?.forEach { (key, value) ->
                                                    when (key.toString().lowercase()) {
                                                        "host" -> {
                                                            bean.host = value?.toString()
                                                        }
                                                    }
                                                }

                                                "path" -> {
                                                    bean.path = wsOpt.value?.toString()
                                                }

                                                "max-early-data" -> {
                                                    bean.wsMaxEarlyData =
                                                        wsOpt.value?.toString()?.toIntOrNull()
                                                }

                                                "early-data-header-name" -> {
                                                    bean.earlyDataHeaderName =
                                                        wsOpt.value?.toString()
                                                }

                                                "v2ray-http-upgrade" -> {
                                                    if (wsOpt.value as? Boolean == true) {
                                                        bean.type = "httpupgrade"
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "h2-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (h2Opt in it) {
                                            when (h2Opt.key) {
                                                "host" -> bean.host =
                                                    (h2Opt.value as? List<Any>)?.joinToString("\n")

                                                "path" -> bean.path = h2Opt.value?.toString()
                                            }
                                        }
                                    }

                                    "http-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (httpOpt in it) {
                                            when (httpOpt.key) {
                                                "path" -> bean.path =
                                                    (httpOpt.value as? List<Any>)?.joinToString("\n")

                                                "headers" -> {
                                                    (httpOpt.value as? Map<Any, List<Any>>)?.forEach { (key, value) ->
                                                        when (key.toString().lowercase()) {
                                                            "host" -> {
                                                                bean.host = value.joinToString("\n")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "grpc-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (grpcOpt in it) {
                                            when (grpcOpt.key) {
                                                "grpc-service-name" -> bean.path =
                                                    grpcOpt.value?.toString()
                                            }
                                        }
                                    }

                                    "smux" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (smuxOpt in it) {
                                            when (smuxOpt.key) {
                                                "enabled" -> bean.enableMux =
                                                    smuxOpt.value.toString() == "true"

                                                "max-streams" -> bean.muxConcurrency =
                                                    smuxOpt.value.toString().toInt()

                                                "padding" -> bean.muxPadding =
                                                    smuxOpt.value.toString() == "true"
                                            }
                                        }
                                    }

                                    "ech-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (echOpt in it) {
                                            when (echOpt.key) {
                                                "enable" -> bean.enableECH =
                                                    echOpt.value.toString() == "true"
                                            }
                                        }
                                    }
                                }
                            }
                            proxies.add(bean)
                        }

                        "anytls" -> {
                            val bean = AnyTLSBean()
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPort = opt.value.toString().toInt()
                                    "password" -> bean.password = opt.value.toString()
                                    "client-fingerprint" -> bean.utlsFingerprint =
                                        opt.value as String

                                    "sni" -> bean.sni = opt.value.toString()
                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n")
                                    }
                                }
                            }
                            proxies.add(bean)
                        }

                        "hysteria" -> {
                            val bean = HysteriaBean()
                            bean.protocolVersion = 1
                            var hopPorts = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPorts = opt.value.toString()
                                    "ports" -> hopPorts = opt.value.toString()

                                    "obfs" -> bean.obfuscation = opt.value.toString()

                                    "auth-str" -> {
                                        bean.authPayloadType = HysteriaBean.TYPE_STRING
                                        bean.authPayload = opt.value.toString()
                                    }

                                    "sni" -> bean.sni = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "up" -> bean.uploadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull()
                                            ?: 100

                                    "down" -> bean.downloadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull()
                                            ?: 100

                                    "recv-window-conn" -> bean.connectionReceiveWindow =
                                        opt.value.toString().toIntOrNull() ?: 0

                                    "recv-window" -> bean.streamReceiveWindow =
                                        opt.value.toString().toIntOrNull() ?: 0

                                    "disable-mtu-discovery" -> bean.disableMtuDiscovery =
                                        opt.value.toString() == "true" || opt.value.toString() == "1"

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n") ?: "h3"
                                    }
                                }
                            }
                            if (hopPorts.isNotBlank()) {
                                bean.serverPorts = hopPorts
                            }
                            proxies.add(bean)
                        }

                        "hysteria2" -> {
                            val bean = HysteriaBean()
                            bean.protocolVersion = 2
                            var hopPorts = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPorts = opt.value.toString()
                                    "ports" -> hopPorts = opt.value.toString()

                                    "obfs-password" -> bean.obfuscation = opt.value.toString()

                                    "password" -> bean.authPayload = opt.value.toString()

                                    "sni" -> bean.sni = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "up" -> bean.uploadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull() ?: 0

                                    "down" -> bean.downloadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull() ?: 0
                                }
                            }
                            if (hopPorts.isNotBlank()) {
                                bean.serverPorts = hopPorts
                            }
                            proxies.add(bean)
                        }

                        "tuic" -> {
                            val bean = TuicBean()
                            var ip = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value.toString()
                                    "ip" -> ip = opt.value.toString()
                                    "port" -> bean.serverPort = opt.value.toString().toInt()

                                    "token" -> {
                                        bean.protocolVersion = 4
                                        bean.token = opt.value.toString()
                                    }

                                    "uuid" -> bean.uuid = opt.value.toString()

                                    "password" -> bean.token = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "disable-sni" -> bean.disableSNI =
                                        opt.value.toString() == "true"

                                    "reduce-rtt" -> bean.reduceRTT =
                                        opt.value.toString() == "true"

                                    "sni" -> bean.sni = opt.value.toString()

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n")
                                    }

                                    "congestion-controller" -> bean.congestionController =
                                        opt.value.toString()

                                    "udp-relay-mode" -> bean.udpRelayMode = opt.value.toString()

                                }
                            }
                            if (ip.isNotBlank()) {
                                bean.serverAddress = ip
                                if (bean.sni.isNullOrBlank() && !bean.serverAddress.isNullOrBlank() && !bean.serverAddress.isIpAddress()) {
                                    bean.sni = bean.serverAddress
                                }
                            }
                            proxies.add(bean)
                        }
                    }
                    } catch (e: Exception) {
                        Logs.w("skip clash proxy ${proxy["name"]}: $e")
                    }
                }

                // Fix ent
                proxies.forEach {
                    it.initializeDefaultValues()
                    if (it is StandardV2RayBean) {
                        // 1. SNI
                        if (it.isTLS() && it.sni.isNullOrBlank() && !it.host.isNullOrBlank() && !it.host.isIpAddress()) {
                            it.sni = it.host
                        }
                        // 2. globalClientFingerprint
                        if (!it.realityPubKey.isNullOrBlank() && it.utlsFingerprint.isNullOrBlank()) {
                            it.utlsFingerprint = globalClientFingerprint
                            if (it.utlsFingerprint.isNullOrBlank()) it.utlsFingerprint = "chrome"
                        }
                    }
                }
                return proxies
            } catch (e: YAMLException) {
                Logs.w(e)
            }
        } else if (text.contains("[Interface]")) {
            // wireguard
            try {
                proxies.addAll(parseWireGuard(text).map {
                    if (fileName.isNotBlank()) it.name = fileName.removeSuffix(".conf")
                    it
                })
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONTokener(text).nextValue()
            return parseJSON(json)
        } catch (ignored: Exception) {
        }

        try {
            return parseProxies(text.decodeBase64UrlSafe()).takeIf { it.isNotEmpty() }
                ?: error("Not found")
        } catch (e: Exception) {
            Logs.w(e)
        }

        try {
            return parseProxies(text).takeIf { it.isNotEmpty() } ?: error("Not found")
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (ignored: Exception) {
        }

        return null
    }

    fun clashCipher(cipher: String): String {
        return when (cipher) {
            "dummy" -> "none"
            else -> cipher
        }
    }

    fun parseWireGuard(conf: String): List<WireGuardBean> {
        val ini = Ini(StringReader(conf))
        val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
        val bean = WireGuardBean().applyDefaultValues()
        val localAddresses = iface.getAll("Address")
        if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
        bean.localAddress = localAddresses.flatMap { it.split(",") }.joinToString("\n")
        bean.privateKey = iface["PrivateKey"]
        // AmneziaWG: keep its [Interface] keys as written, they are checked when the profile starts
        bean.amneziaWG = iface.keys.filter { AmneziaWG.isKey(it) }
            .joinToString("\n") { "$it = ${iface[it].orEmpty().trim()}" }
        // without an MTU line keep the default (a null MTU failed the import); AmneziaWG's s4
        // junk rides on every data packet, so the plain 1420 would overflow the path there
        bean.mtu = iface["MTU"]?.trim()?.toIntOrNull() ?: if (bean.amneziaWG.isNotBlank()) 1280 else 1420
        val peers = ini.getAll("Peer")
        if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
        val beans = mutableListOf<WireGuardBean>()
        for (peer in peers) {
            val endpoint = peer["Endpoint"]
            if (endpoint.isNullOrBlank() || !endpoint.contains(":")) {
                continue
            }

            val peerBean = bean.clone()
            peerBean.serverAddress = endpoint.substringBeforeLast(":")
            peerBean.serverPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
            peerBean.peerPublicKey = peer["PublicKey"] ?: continue
            peerBean.peerPreSharedKey = peer["PresharedKey"]
            val keepalive = peer["PersistentKeepalive"]?.trim().orEmpty()
            peerBean.persistentKeepalive = keepalive.toIntOrNull()?.coerceIn(0, 65535) ?: 0
            if (keepalive.contains("-")) {
                // an AmneziaWG 3.x range travels with the AmneziaWG parameters
                peerBean.amneziaWG = listOf(peerBean.amneziaWG, "PersistentKeepalive = $keepalive")
                    .filter { it.isNotBlank() }.joinToString("\n")
            }
            beans.add(peerBean.applyDefaultValues())
        }
        if (beans.isEmpty()) error("Empty available peer list")
        return beans
    }

    fun parseJSON(json: Any): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (json is JSONObject) {
            when {
                json.has("server") && (json.has("up") || json.has("up_mbps")) -> {
                    return listOf(json.parseHysteria1Json())
                }

                json.has("method") -> {
                    return listOf(json.parseShadowsocks())
                }

                json.has("remote_addr") -> {
                    return listOf(json.parseTrojanGo())
                }

                // SIP008 online configuration: {"version": 1, "servers": [...]}
                json.optJSONArray("servers") != null -> {
                    return json.getJSONArray("servers")
                        .filterIsInstance<JSONObject>()
                        .filter { it.has("server") && it.has("method") }
                        .map { it.parseShadowsocks().apply { initializeDefaultValues() } }
                }

                json.has("outbounds") -> {
                    return json.getJSONArray("outbounds")
                        .filterIsInstance<JSONObject>()
                        .mapNotNull {
                            val ty = it.getStr("type")
                            if (ty == null || ty == "" ||
                                ty == "dns" || ty == "block" || ty == "direct" || ty == "selector" || ty == "urltest"
                            ) {
                                null
                            } else {
                                it
                            }
                        }.map {
                            ConfigBean().apply {
                                applyDefaultValues()
                                type = 1
                                config = it.toStringPretty()
                                name = it.getStr("tag")
                            }
                        }
                }

                json.has("server") && json.has("server_port") -> {
                    return listOf(ConfigBean().applyDefaultValues().apply {
                        type = 1
                        config = json.toStringPretty()
                    })
                }
            }
        } else {
            json as JSONArray
            json.forEach { _, it ->
                if (isJsonObjectValid(it)) {
                    proxies.addAll(parseJSON(it))
                }
            }
        }

        proxies.forEach { it.initializeDefaultValues() }
        return proxies
    }

}

/** Loads a clash config; throws [YAMLException] for anything but a mapping of plain values. */
internal fun loadClashYaml(text: String): Map<*, *> =
    Yaml(ClashYamlConstructor()).load<Any?>(text) as? Map<*, *>
        ?: throw YAMLException("not a clash config")

/**
 * Builds only plain maps, lists and scalars: a global tag in a subscription must not
 * instantiate arbitrary classes (CVE-2022-1471). The local "!str" tag is kept as a string.
 */
private class ClashYamlConstructor : SafeConstructor(LoaderOptions().apply {
    codePointLimit = 64 * 1024 * 1024
}) {
    init {
        yamlConstructors[Tag("str")] = yamlConstructors[Tag.STR]
        yamlConstructors[Tag("!str")] = yamlConstructors[Tag.STR]
    }
}
