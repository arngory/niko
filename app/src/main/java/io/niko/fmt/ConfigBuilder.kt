package io.niko.fmt

import android.widget.Toast
import io.niko.*
import io.niko.bg.VpnService
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.ProxyEntity.Companion.TYPE_CONFIG
import io.niko.database.SagerDatabase
import io.niko.fmt.ConfigBuildResult.IndexEntity
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.naive.buildSingBoxOutboundNaiveBean
import io.niko.fmt.hysteria.buildSingBoxOutboundHysteriaBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.shadowsocks.buildSingBoxOutboundShadowsocksBean
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.socks.buildSingBoxOutboundSocksBean
import io.niko.fmt.ssh.SSHBean
import io.niko.fmt.ssh.buildSingBoxOutboundSSHBean
import io.niko.fmt.tuic.TuicBean
import io.niko.fmt.tuic.buildSingBoxOutboundTuicBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.niko.fmt.wireguard.WireGuardBean
import io.niko.fmt.wireguard.buildSingBoxEndpointWireguardBean
import io.niko.ktx.Logs
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayBrutal
import io.niko.raycontrol.RayChannel
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.knockOptions
import io.niko.ktx.isIpAddress
import io.niko.ktx.mkPort
import io.niko.utils.PrivateDns
import io.niko.utils.PackageCache
import io.niko.*
import io.niko.SingBoxOptions.*
import io.niko.plugin.Plugins
import io.niko.proxy.anytls.AnyTLSBean
import io.niko.proxy.anytls.buildSingBoxOutboundAnyTLSBean
import io.niko.proxy.config.ConfigBean
import io.niko.proxy.shadowtls.ShadowTLSBean
import io.niko.proxy.shadowtls.buildSingBoxOutboundShadowTLSBean
import io.niko.utils.JavaUtil.gson
import io.niko.utils.Util
import io.niko.utils.listByLineOrComma
import java.io.File
import libcore.Libcore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val TAG_MIXED = "mixed-in"

// user name the app itself uses for the local proxy, see DataStore.localProxyPassword
const val LOCAL_PROXY_USER = "nekobox-app"

/** Stands for a secret of this install in an exported config. */
const val EXPORT_SECRET_PLACEHOLDER = "<a secret of the app, not exported>"

const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
const val TAG_BYPASS = "bypass"
const val TAG_BLOCK = "block"
const val TAG_YGGDRASIL = "yggdrasil"

/** the Yggdrasil address space */
private val YGGDRASIL_PREFIX = "200::/7"

const val LOCALHOST = "127.0.0.1"

private val TLS_FRAGMENT_TYPES = setOf("vmess", "vless", "trojan", "http", "anytls", "shadowtls")

// QUIC clients whose UDP the core can shape (common/udpdesync)
private val UDP_DESYNC_TYPES = setOf("hysteria2", "tuic")
private val TLS_PIN_TYPES = TLS_FRAGMENT_TYPES + setOf("hysteria", "hysteria2", "tuic")

/**
 * Credentials of the local mixed inbound. In VPN mode any app on the device could otherwise
 * use it to learn the proxy exit address, so it requires authentication unless allowed.
 * The app always authenticates with its own credentials (see LOCAL_PROXY_USER).
 */
fun localProxyUsers(isVPN: Boolean): List<User>? {
    val custom = DataStore.mixedPassword.takeIf { it.isNotBlank() }?.let {
        User().apply {
            username = DataStore.mixedUsername.ifBlank { "user" }
            password = it
        }
    }
    if (custom == null && (!isVPN || DataStore.localProxyNoAuthInVpn)) return null
    return listOfNotNull(User().apply {
        username = LOCAL_PROXY_USER
        password = DataStore.localProxyPassword
    }, custom)
}

/**
 * The log level of test cores in this process while a test run goes (RayTestRun): never stored, so a killed run or
 * the service leaves no trace in the settings.
 */
@Volatile
var testLogLevel: Int? = null

/** Whether the local proxy port opens: always in proxy mode; in VPN mode when the user set it up for other apps. */
fun localProxyWanted(isVPN: Boolean) =
    !isVPN || DataStore.mixedPassword.isNotBlank() || DataStore.localProxyNoAuthInVpn

/**
 * Proxy mode with the service started: the app's own requests go through its port and never around it, which would
 * show the device's address (a core restarting is waited for).
 */
fun localProxyRequired() = DataStore.serviceState.started && DataStore.serviceMode != Key.MODE_VPN

class ConfigBuildResult(
    var config: String,
    var externalIndex: List<IndexEntity>,
    var mainEntId: Long,
    var trafficMap: Map<String, List<ProxyEntity>>,
    var profileTagMap: Map<Long, String>,
    val selectorGroupId: Long,
    // the device keys of the raycontrol Yggdrasil profiles in this config (their mesh nodes)
    val yggKeys: Set<String> = emptySet(),
    // the slipstream clients the config's DNS tunnels dial through, processes of their own
    val slipstream: List<SlipstreamLaunch> = emptyList(),
) {
    data class IndexEntity(var chain: LinkedHashMap<Int, ProxyEntity>)

    /**
     * A slipstream client to run with the config: its arguments (no secret among them), its environment (dnsauth's
     * key, SLIPSTREAM_DNSAUTH_KEY, there only) and the port it listens on, 127.0.0.1's (one of the app's own
     * listeners: the mesh never reaches it, S7).
     */
    class SlipstreamLaunch(val args: List<String>, val env: Map<String, String>, val localPort: Int) {
        override fun toString() = "SlipstreamLaunch(args=$args, env=${env.keys}, localPort=$localPort)"
    }
}

/**
 * [notes]: the config will run, so raycontrol records what it applies (Brutal rates, Yggdrasil public peers) for
 * the watchdog; a temporary build to compare with (a selector switch) must not, or the watchdog would take the
 * running core for one that has them (P5).
 */
fun buildConfig(
    proxy: ProxyEntity, forTest: Boolean = false, forExport: Boolean = false, notes: Boolean = !forTest && !forExport,
): ConfigBuildResult {

    // raycontrol: a removed node's device key leaves this process's core with the next build (running outbounds
    // copied their keys when they started; keys of current nodes stay for builds running in parallel)
    if (!forExport) RayNodes.retainCoreKeys()
    // what the DNS tunnels of a config that will run take, noted anew below (a network change compares, RayWatchdog)
    if (notes) io.niko.raycontrol.RayDnsResolvers.clearRunning()

    if (proxy.type == TYPE_CONFIG) {
        val bean = proxy.requireBean() as ConfigBean
        if (bean.type == 0) {
            return ConfigBuildResult(
                bean.config,
                listOf(),
                proxy.id, //
                mapOf(TAG_PROXY to listOf(proxy)), //
                mapOf(proxy.id to TAG_PROXY), //
                -1L
            )
        }
    }

    val trafficMap = HashMap<String, List<ProxyEntity>>()
    val tagMap = HashMap<Long, String>()
    val globalOutbounds = HashMap<Long, String>()
    val selectorNames = ArrayList<String>()
    val group = SagerDatabase.groupDao.getById(proxy.groupId)

    fun ProxyEntity.resolveChainInternal(): MutableList<ProxyEntity> {
        val bean = requireBean()
        if (bean is ChainBean) {
            val beans = SagerDatabase.proxyDao.getEntities(bean.proxies)
            val beansMap = beans.associateBy { it.id }
            val beanList = ArrayList<ProxyEntity>()
            for (proxyId in bean.proxies) {
                val item = beansMap[proxyId] ?: continue
                beanList.addAll(item.resolveChainInternal())
            }
            return beanList.asReversed()
        }
        return mutableListOf(this)
    }

    fun selectorName(name_: String): String {
        var name = name_
        var count = 0
        while (selectorNames.contains(name)) {
            count++
            name = "$name_-$count"
        }
        selectorNames.add(name)
        return name
    }

    fun ProxyEntity.resolveChain(): MutableList<ProxyEntity> {
        val thisGroup = SagerDatabase.groupDao.getById(groupId)
        val frontProxy = thisGroup?.frontProxy?.let { SagerDatabase.proxyDao.getById(it) }
        val landingProxy = thisGroup?.landingProxy?.let { SagerDatabase.proxyDao.getById(it) }
        val list = resolveChainInternal()
        if (frontProxy != null) {
            list.add(frontProxy)
        }
        if (landingProxy != null) {
            list.add(0, landingProxy)
        }
        return list
    }

    val extraRules = if (forTest) listOf() else SagerDatabase.rulesDao.enabledRules()
    val extraProxies =
        if (forTest) mapOf() else SagerDatabase.proxyDao.getEntities(extraRules.mapNotNull { rule ->
            rule.outbound.takeIf { it > 0 && it != proxy.id }
        }.toHashSet().toList()).associateBy { it.id }
    // auto select (urltest) takes precedence over the manual selector
    val buildAuto = !forTest && group?.autoSelect == true && !forExport
    val buildSelector = !forTest && group?.isSelector == true && !forExport && !buildAuto
    val userDNSRuleList = mutableListOf<DNSRule_DefaultOptions>()
    val domainListDNSDirectForce = mutableListOf<String>()
    val bypassDNSBeans = hashSetOf<AbstractBean>()
    val isVPN = DataStore.serviceMode == Key.MODE_VPN
    val bind = if (!forTest && DataStore.allowAccess) "0.0.0.0" else LOCALHOST
    val remoteDns = DataStore.remoteDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val directDNS = DataStore.directDns.split("\n")
        .mapNotNull { dns -> dns.trim().takeIf { it.isNotBlank() && !it.startsWith("#") } }
    val enableDnsRouting = DataStore.enableDnsRouting
    val useFakeDns = DataStore.enableFakeDns && !forTest
    val needSniff = DataStore.trafficSniffing > 0
    val externalIndexMap = ArrayList<IndexEntity>()
    // slipstream's clients, and the rules of their mapping ports (above the DNS hijack: their queries go to port 53)
    val slipstreamLaunches = mutableListOf<ConfigBuildResult.SlipstreamLaunch>()
    val slipstreamRules = mutableListOf<Rule_DefaultOptions>()
    val ipv6Mode = if (forTest) IPv6Mode.ENABLE else DataStore.ipv6Mode

    fun genDomainStrategy(noAsIs: Boolean): String {
        return when {
            !noAsIs -> ""
            ipv6Mode == IPv6Mode.DISABLE -> "ipv4_only"
            ipv6Mode == IPv6Mode.PREFER -> "prefer_ipv6"
            ipv6Mode == IPv6Mode.ONLY -> "ipv6_only"
            else -> "prefer_ipv4"
        }
    }

    // the mesh nodes of raycontrol Yggdrasil profiles, one per device key (key to tag and peer): nothing comes in
    // through them; the service's result names their keys
    val yggProfileEndpoints = mutableMapOf<String, Pair<String, String>>()
    // per node: whether a mesh node of its Yggdrasil profiles runs with the node's public peers
    val yggViaNotes = mutableMapOf<String, Boolean>()
    return MyOptions().apply {
        // an exported config is shared: it carries no secret of this install (Clash secret, local proxy
        // passwords, the Yggdrasil key)
        if (!forTest && !forExport && DataStore.enableClashAPI) experimental = ExperimentalOptions().apply {
            clash_api = ClashAPIOptions().apply {
                external_controller = "127.0.0.1:9090"
                external_ui = "../files/yacd"
                secret = DataStore.clashApiSecret
            }
        }

        log = LogOptions().apply {
            level = when (if (forTest) testLogLevel ?: DataStore.logLevel else DataStore.logLevel) {
                0 -> "panic"
                1 -> "warn"
                2 -> "info"
                3 -> "debug"
                4 -> "trace"
                else -> "info"
            }
        }

        dns = DNSOptions().apply {
            servers = mutableListOf()
            rules = mutableListOf()
            // answer from an expired cache entry at once and refresh it in the background
            if (!forTest && DataStore.dnsOptimisticCache) _hack_config_map["optimistic"] = true
        }

        fun autoDnsDomainStrategy(s: String): String? {
            if (s.isNotEmpty()) {
                return s
            }
            return when (ipv6Mode) {
                IPv6Mode.DISABLE -> "ipv4_only"
                IPv6Mode.ENABLE -> "prefer_ipv4"
                IPv6Mode.PREFER -> "prefer_ipv6"
                IPv6Mode.ONLY -> "ipv6_only"
                else -> null
            }
        }

        inbounds = mutableListOf()

        if (!forTest) {
            if (isVPN) inbounds.add(Inbound_TunOptions().apply {
                type = "tun"
                tag = "tun-in"
                stack = when (DataStore.tunImplementation) {
                    TunImplementation.GVISOR -> "gvisor"
                    TunImplementation.SYSTEM -> "system"
                    else -> "mixed"
                }
                mtu = DataStore.mtu
                address = when (ipv6Mode) {
                    IPv6Mode.DISABLE -> listOf(VpnService.PRIVATE_VLAN4_CLIENT + "/28")
                    IPv6Mode.ONLY -> listOf(VpnService.PRIVATE_VLAN6_CLIENT + "/126")
                    else -> listOf(
                        VpnService.PRIVATE_VLAN4_CLIENT + "/28",
                        VpnService.PRIVATE_VLAN6_CLIENT + "/126"
                    )
                }
            })
            // in VPN mode the app itself goes through the TUN: the port only serves what the user set up for it (an
            // own login, or no login allowed). Without that it stays closed, so no app on the device can learn the
            // exit address through it (R3)
            if (localProxyWanted(isVPN)) inbounds.add(Inbound_MixedOptions().apply {
                type = "mixed"
                tag = TAG_MIXED
                listen = bind
                listen_port = DataStore.mixedPort
                users = localProxyUsers(isVPN)?.onEach { if (forExport) it.password = EXPORT_SECRET_PLACEHOLDER }
            })
        }

        outbounds = mutableListOf()
        endpoints = mutableListOf()

        // init routing object
        route = RouteOptions().apply {
            auto_detect_interface = true
            default_domain_resolver = "dns-direct"
            rules = mutableListOf()
            rule_set = mutableListOf()
        }

        if (!forTest) {
            val inboundTags = mutableListOf(TAG_MIXED)
            if (isVPN) inboundTags.add(0, "tun-in")
            if (DataStore.resolveDestination) route.rules.add(Rule_DefaultOptions().apply {
                inbound = inboundTags
                action = "resolve"
                strategy = genDomainStrategy(true)
            })
        }

        // Yggdrasil (experimental): the mesh endpoint, 200::/7 through it ahead of user rules
        val yggdrasil = DataStore.yggdrasilEnabled
        var yggdrasilEndpoint: Outbound? = null
        // the proxy reaches its server through the mesh: then the mesh cannot reach its peers
        // through the proxy
        var proxyThroughYggdrasil = false
        fun addYggdrasilEndpoint(privateKey: String) {
            if (yggdrasilEndpoint != null) return
            yggdrasilEndpoint = Outbound().apply {
                type = "yggdrasil"
                tag = TAG_YGGDRASIL
                _hack_config_map["private_key"] = privateKey
                _hack_config_map["peers"] = DataStore.yggdrasilPeers.lines().map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }.filter { peer ->
                        // the core resolves a quic:// name with the system resolver, and outside a VPN that is the
                        // network's DNS: in proxy mode such a peer goes, an address stays
                        val name = peer.substringAfter("://").substringBefore('/').substringBefore('?')
                            .substringBeforeLast(':').trim('[', ']')
                        val dropped = !isVPN && peer.startsWith("quic://", ignoreCase = true) && !name.isIpAddress()
                        if (dropped) Logs.w("Yggdrasil: $peer left out in proxy mode, the system resolver would look it up")
                        !dropped
                    }
                _hack_config_map["node_info_privacy"] = true
                // a Chrome ClientHello on tls:// links unless turned off (core default: chrome)
                if (!DataStore.yggdrasilTlsChrome) _hack_config_map["tls_fingerprint"] = "go"
            }
            endpoints.add(yggdrasilEndpoint!!)
        }
        // other nodes reach this device only when allowed, and then only its own services; it
        // goes first, above the DNS hijack, or every node would get a resolver
        var yggdrasilInboundRule: Rule_DefaultOptions? = null
        // a hop behind a DNS tunnel may carry no UDP at all (SOCKS, HTTP, SSH, Shadowsocks
        // without UoT): remote DNS through the proxy then goes over TCP
        var tunnelledHop = false
        // the chains behind TAG_PROXY, which remote DNS uses, as opposed to route-rule outbounds
        var buildingProxy = false
        // the main profile dials a raycontrol node with a DoH service: that DoH is the remote DNS, through the
        // node's knock outbound (SPA, then straight to the node)
        var nodeDoh: Pair<RayNode, String>? = null
        if (yggdrasil && !forTest) {
            addYggdrasilEndpoint(if (forExport) EXPORT_SECRET_PLACEHOLDER else DataStore.yggdrasilPrivateKey)
            yggdrasilInboundRule = Rule_DefaultOptions().apply {
                inbound = listOf(TAG_YGGDRASIL)
                if (DataStore.yggdrasilAllowInbound) outbound = TAG_DIRECT else action = "reject"
            }
            route.rules.add(Rule_DefaultOptions().apply {
                ip_cidr = listOf(YGGDRASIL_PREFIX)
                outbound = TAG_YGGDRASIL
            })
        }

        // returns outbound tag
        fun buildChain(
            chainId: Long, entity: ProxyEntity
        ): String {
            val profileList = entity.resolveChain()
            val chainTrafficSet = HashSet<ProxyEntity>().apply {
                plusAssign(profileList)
                add(entity)
            }

            var currentOutbound: SingBoxOption
            lateinit var pastOutbound: SingBoxOption
            lateinit var pastInboundTag: String
            var pastEntity: ProxyEntity? = null
            // where the next hop has to be attached: the past outbound or its knock outbound
            var pastDetourHolder: SingBoxOption? = null
            val externalChainMap = LinkedHashMap<Int, ProxyEntity>()
            externalIndexMap.add(IndexEntity(externalChainMap))
            val chainOutbounds = ArrayList<SingBoxOption>()

            // chainTagOut: v2ray outbound tag for this chain
            var chainTagOut = ""
            val chainTag = "c-$chainId"
            var muxApplied = false

            val defaultServerDomainStrategy = SingBoxOptionsUtil.domainStrategy("server")

            profileList.forEachIndexed { index, proxyEntity ->
                val bean = proxyEntity.requireBean()

                // tagOut: v2ray outbound tag for a profile
                // profile2 (in) (global)   tag g-(id)
                // profile1                 tag (chainTag)-(id)
                // profile0 (out)           tag (chainTag)-(id) / single: "proxy"
                var tagOut = "$chainTag-${proxyEntity.id}"

                // needGlobal: can only contain one?
                var needGlobal = false

                // first profile set as global
                if (index == profileList.lastIndex) {
                    needGlobal = true
                    tagOut = "g-" + proxyEntity.id
                    bypassDNSBeans += proxyEntity.requireBean()
                }

                // last profile set as "proxy"
                if (chainId == 0L && index == 0) {
                    tagOut = TAG_PROXY
                }

                // selector human readable name
                if (buildSelector && index == 0) {
                    tagOut = selectorName(bean.displayName())
                }


                // chain rules
                if (index > 0) {
                    // chain route/proxy rules
                    if (pastEntity!!.needExternal()) {
                        route.rules.add(Rule_DefaultOptions().apply {
                            inbound = listOf(pastInboundTag)
                            outbound = tagOut
                        })
                    } else {
                        (pastDetourHolder ?: pastOutbound)._hack_config_map["detour"] = tagOut
                    }
                } else {
                    // index == 0 means last profile in chain / not chain
                    chainTagOut = tagOut
                }

                // now tagOut is determined
                if (needGlobal) {
                    globalOutbounds[proxyEntity.id]?.let {
                        if (index == 0) chainTagOut = it // single, duplicate chain
                        return@forEachIndexed
                    }
                    globalOutbounds[proxyEntity.id] = tagOut
                }

                if (proxyEntity.needExternal()) { // externel outbound
                    val localPort = mkPort()
                    externalChainMap[localPort] = proxyEntity
                    currentOutbound = Outbound_SocksOptions().apply {
                        type = "socks"
                        server = LOCALHOST
                        server_port = localPort
                    }
                } else {
                    // internal outbound

                    currentOutbound = when (bean) {
                        is ConfigBean -> CustomSingBoxOption(bean.config)

                        is ShadowTLSBean -> // before StandardV2RayBean
                            buildSingBoxOutboundShadowTLSBean(bean)

                        is StandardV2RayBean -> // http/trojan/vmess/vless
                            buildSingBoxOutboundStandardV2RayBean(bean)

                        is HysteriaBean ->
                            buildSingBoxOutboundHysteriaBean(bean)

                        is TuicBean ->
                            buildSingBoxOutboundTuicBean(bean)

                        is SOCKSBean ->
                            buildSingBoxOutboundSocksBean(bean)

                        is ShadowsocksBean ->
                            buildSingBoxOutboundShadowsocksBean(bean)

                        is WireGuardBean ->
                            buildSingBoxEndpointWireguardBean(bean)

                        is SSHBean ->
                            buildSingBoxOutboundSSHBean(bean)

                        is AnyTLSBean ->
                            buildSingBoxOutboundAnyTLSBean(bean)

                        is NaiveBean ->
                            buildSingBoxOutboundNaiveBean(bean)

                        else -> throw IllegalStateException("can't reach")
                    }

                    // internal mux
                    if (!muxApplied) {
                        val muxObj = proxyEntity.singMux()
                        if (muxObj != null && muxObj.enabled) {
                            muxApplied = true
                            currentOutbound._hack_config_map["multiplex"] = muxObj.asMap()
                            // keep the old Neko core behaviour: mux replaces VLESS flow
                            if (currentOutbound is Outbound_VLESSOptions) {
                                (currentOutbound as Outbound_VLESSOptions).flow = null
                            }
                        }
                    }
                }

                // internal & external
                currentOutbound.apply {
                    // udp over tcp
                    try {
                        val sUoT = bean.javaClass.getField("sUoT").get(bean)
                        if (sUoT is Boolean && sUoT) {
                            _hack_config_map["udp_over_tcp"] = true
                        }
                    } catch (_: Exception) {
                    }

                    // domain_strategy
                    pastEntity?.requireBean()?.apply {
                        // don't loopback
                        if (defaultServerDomainStrategy != "" && !serverAddress.isIpAddress()) {
                            domainListDNSDirectForce.add("full:$serverAddress")
                        }
                    }
                    // only the entry hop dials the server itself, inner hops resolve on their detour
                    if (!forTest && defaultServerDomainStrategy != "" && index == profileList.lastIndex) {
                        _hack_config_map["domain_resolver"] = mapOf(
                            "server" to "dns-direct",
                            "strategy" to defaultServerDomainStrategy,
                        )
                    }

                    _hack_config_map["tag"] = tagOut

                    _hack_custom_config = bean.customOutboundJson
                }

                // a raycontrol Yggdrasil profile (niko-ygg) as the entry hop: the VLESS server is the node's mesh
                // address, the link to the mesh goes to the node itself
                val yggPeerAsGiven = bean.yggPeer?.takeIf { it.isNotBlank() && index == profileList.lastIndex && !proxyEntity.needExternal() }
                // the row's node: its peer names the service, not the node, so the row's owner when the name is not a
                // node's host (without it the link went out with no SPA, to a port closed until a knock). The peer is
                // dialled at the node's address with its name as the TLS name: SPA opens that address, and the name
                // is not looked up in the clear
                val yggNode = yggPeerAsGiven?.let { RayNodes.forHost(yggPeerHost(it)) ?: RayNodes.ownerOf(proxyEntity.id)?.first }
                val yggPeer = yggPeerAsGiven?.let { peer -> yggNode?.let { yggPeerAtNode(peer, it.host) } ?: peer }
                // a raycontrol node (imported rcdev1) this hop dials; a DNS tunnel never reaches its address
                // a profile row through a relay in front of the node (relay-v1 §4): knocked at the relay
                val rayRelay = if (DnsTunnel.of(bean).enabled || yggPeer != null) null
                else RayNodes.forRelayHost(bean.serverAddress)
                // a row of a node's subscription belongs to it whatever its host: naive dials a name of its own (its
                // SNI, a wildcard name of the node), which is not the node's address, and still needs the node's SPA
                val rayNode = when {
                    DnsTunnel.of(bean).enabled -> null
                    yggPeer != null -> yggNode
                    rayRelay != null -> rayRelay.first
                    else -> RayNodes.forHost(bean.serverAddress) ?: RayNodes.ownerOf(proxyEntity.id)?.first
                }

                // per-profile TLS tweaks, merged into the outbound's tls object
                val tlsTweaks = mutableMapOf<String, Any>()
                val outboundType = (currentOutbound as? Outbound)?.type
                if (rayNode != null && !proxyEntity.needExternal()) {
                    tlsTweaks += nodeTlsTweaks(outboundType, currentOutbound.asMap()["tls"] as? Map<*, *>) { fingerprint ->
                        Logs.w("raycontrol node ${rayNode.host}: uTLS fingerprint $fingerprint replaced by chrome (post-quantum key share)")
                    }
                }
                // fragmentation is only meaningful for TCP-based TLS outbounds
                if (!proxyEntity.needExternal() && (bean.tlsFragment ?: 0) > 0 &&
                    outboundType in TLS_FRAGMENT_TYPES
                ) {
                    tlsTweaks[if (bean.tlsFragment == 2) "fragment" else "record_fragment"] = true
                }
                if (!bean.certPins.isNullOrBlank()) {
                    // pins that would be silently ignored fail the start instead
                    val tls = (currentOutbound.asMap()["tls"] as? Map<*, *>)
                    val reason = when {
                        proxyEntity.needExternal() || outboundType !in TLS_PIN_TYPES ->
                            "not supported for this profile type"
                        tls?.get("enabled") != true -> "the profile does not use TLS"
                        (tls["reality"] as? Map<*, *>)?.get("enabled") == true -> "not supported with REALITY"
                        else -> null
                    }
                    if (reason != null) throw Exception("Certificate pinning (${bean.displayName()}): $reason")
                    tlsTweaks["certificate_public_key_sha256"] = parseCertificatePins(bean.certPins)
                    // the pin replaces CA verification; sing-box rejects it next to a custom CA
                    tlsTweaks["certificate"] = emptyList<String>()
                }
                if (tlsTweaks.isNotEmpty()) {
                    currentOutbound._hack_config_map["tls"] =
                        mergeTls(currentOutbound._hack_config_map["tls"] as? Map<*, *>, tlsTweaks)
                }

                // Brutal at the rates measured on this network (RayBrutal, setting raycontrolBrutal), each direction
                // apart; none measured: BBR. The main config only: a test runs as the profile says
                if (!forTest && rayNode != null && bean is HysteriaBean && bean.protocolVersion == 2) {
                    val rates = RayBrutal.ratesFor(rayNode, RayChannel.network())
                    // what this core declares: the watchdog restarts it when the network wants other rates
                    if (notes) RayBrutal.noteApplied(rayNode, rates)
                    rates?.let {
                        if (it.upKbps > 0) currentOutbound._hack_config_map["up_mbps"] = RayBrutal.mbps(it.upKbps)
                        if (it.downKbps > 0) currentOutbound._hack_config_map["down_mbps"] = RayBrutal.mbps(it.downKbps)
                    }
                }
                // an idle QUIC connection to the node pings every 10 s by default, and the radio never sleeps: 25 s
                // still holds a mobile NAT binding (around 30 s and more) (P1). quic-go pings at most every half of
                // the idle timeout both sides agree on (the smaller one): 60 s here, so the node's own (30 s by
                // default, 15 s pings) decides until it allows 50 s or more
                if (rayNode != null && bean is HysteriaBean && bean.protocolVersion == 2 && !proxyEntity.needExternal()) {
                    currentOutbound._hack_config_map["keep_alive_period"] = "25s"
                    currentOutbound._hack_config_map["idle_timeout"] = "60s"
                }
                // honest connect (experiment, off by default): an app's TCP connection opens only once the node has
                // reached the destination, so a refusal or a dead host fails it at once instead of a connection that
                // ends empty; only where the link says the server answers right after its dial (niko-ack=1). A field
                // of the core fork's: not in an export
                if (!forExport && DataStore.honestConnect && bean is HysteriaBean && bean.protocolVersion == 2 &&
                    bean.nikoAck == true && !proxyEntity.needExternal()
                ) {
                    currentOutbound._hack_config_map["await_dial"] = true
                }

                // UDP desync of QUIC clients (core common/udpdesync)
                if (!proxyEntity.needExternal() && outboundType in UDP_DESYNC_TYPES &&
                    ((bean.udpPrelude ?: 0) > 0 || bean.udpReorderInitial == true)
                ) {
                    currentOutbound._hack_config_map["udp_desync"] = buildMap {
                        (bean.udpPrelude ?: 0).coerceIn(0, 8).takeIf { it > 0 }?.let { put("prelude", it) }
                        // never a name of the node: its front would pin the flow to that backend
                        bean.udpPreludeSni?.trim()?.takeIf { it.isNotBlank() }?.let { sni ->
                            val nodeName = RayNodes.forHost(bean.serverAddress)?.let { sni.lowercase() in RayNodes.names(it) } == true
                            if (nodeName) Logs.w("UDP desync (${bean.displayName()}): $sni is a name of the node, the fakes are random bytes")
                            else put("prelude_sni", sni)
                        }
                        (bean.udpPreludeTtl ?: 0).coerceIn(0, 255).takeIf { it > 0 }?.let { put("prelude_ttl", it) }
                        // under Hysteria2 obfs the core never sees a QUIC Initial: reordering is moot
                        if (bean.udpReorderInitial == true && !(bean is HysteriaBean && bean.obfuscation.isNotBlank())) {
                            put("reorder_initial", true)
                        }
                    }
                }

                // port knocking: a libcore "knock" outbound dials the server for this hop. A raycontrol node
                // gets SPA v2 instead of the profile's own sequence
                var knockTag: String? = null
                var knockHolder: Outbound? = null
                pastDetourHolder = null
                if (rayNode != null || bean.hasPortKnocking()) {
                    if (proxyEntity.needExternal() && index != profileList.lastIndex) {
                        throw Exception("Port knocking (${bean.displayName()}): plugin profiles support it only as the first hop of a chain")
                    }
                    knockTag = "$tagOut-knock"
                    val knockOutbound = Outbound().apply {
                        type = "knock"
                        tag = knockTag
                    }
                    knockHolder = knockOutbound
                    // an export carries only the key reference: the key never leaves the app
                    knockOutbound._hack_config_map.putAll(
                        rayNode?.let {
                            val relay = rayRelay?.second
                            if (forExport) it.knockOptions(relay) else RayNodes.prepareKnock(it, relay)
                        }
                            ?: bean.buildKnockOutboundOptions()
                    )
                    // the node's UDP service port rotates (spa-v2.md §11): the knock outbound follows it
                    // a relay forwards UDP 443 only (relay-v1 §4): no slot ports through it
                    rayNode?.takeIf { rayRelay == null }?.let { rotatingUdpPorts(it, proxyEntity, bean) }?.let {
                        knockOutbound._hack_config_map["udp_ports"] = it
                    }
                    // only for the node's TCP 443 profiles (REALITY, naive): with Hysteria2 or a DNS tunnel the node's
                    // TCP 443 may be unreachable (raycontrol niko-r3-reply.md §2)
                    // not through a relay: the DoH goes to the node's own address, which the relay's SPA does not open
                    if (buildingProxy && entity.id == proxy.id && index == profileList.lastIndex && rayNode?.doh != null &&
                        rayRelay == null &&
                        (bean is StandardV2RayBean || bean is NaiveBean)
                    ) {
                        nodeDoh = rayNode to knockTag
                    }
                    currentOutbound._hack_config_map.remove("domain_resolver")?.let {
                        knockOutbound._hack_config_map["domain_resolver"] = it
                    }
                    if (!proxyEntity.needExternal()) {
                        currentOutbound._hack_config_map["detour"] = knockTag
                        pastDetourHolder = knockOutbound
                    }
                    outbounds.add(knockOutbound)
                }

                // the Yggdrasil profile's own mesh node: the device's key, the node as its only peer (P-14), the
                // peer link through the node's SPA (TCP 443). The VLESS to the node's mesh address goes through it
                if (yggPeer != null) {
                    val seed = bean.yggKey.orEmpty()
                    // a second mesh node with the device's key would claim its address next to the running one: the
                    // node would send the running connection's packets down either link
                    // The reverse order is not covered: a test already running when the service starts with this key
                    // overlaps it for the test's window (at most 20 s), and the node settles on the remaining link
                    if (forTest && yggKeyRunning(seed)) throw YggInUseException(bean.displayName())
                    // one mesh node per key: the same key with another peer would be a second node on that address
                    yggProfileEndpoints[seed]?.let { (_, peer) ->
                        if (peer != yggPeer) throw Exception("${bean.displayName()}: its Yggdrasil key is used with another node in this configuration")
                    }
                    // the node's public peers (niko-ygg-via, R4-18) once the direct peer failed on this network: the
                    // ones the check took there (RayYggPeers), all of them only for a record from before the check.
                    // Peers the subscription has dropped since are not taken; none left: none, and the watchdog
                    // checks the new list (RayChannel.tryYggVia)
                    val allVia = bean.yggVia.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val viaActive = !forTest && allVia.isNotEmpty() && rayNode != null &&
                        RayChannel.yggViaActive(rayNode.spa.nodeId, RayChannel.network())
                    val via = if (viaActive && rayNode != null) {
                        RayChannel.yggViaPeers(rayNode.spa.nodeId, RayChannel.network())?.filter { it in allVia } ?: allVia
                    } else emptyList()
                    val viaOn = via.isNotEmpty()
                    // what this core runs with, per node (any of its mesh nodes): the watchdog adds the peers when
                    // the network wants them
                    if (!forTest && rayNode != null) {
                        val on = yggViaNotes[rayNode.spa.nodeId] == true || viaOn
                        yggViaNotes[rayNode.spa.nodeId] = on
                        if (notes) RayChannel.noteYggVia(rayNode.spa.nodeId, on)
                    }
                    // only the node gets its SPA: the knock outbound knocks at the node's own names (and what they
                    // resolve to) and dials anything else plainly, the public peers among them, by name or address
                    knockHolder?._hack_config_map?.set(
                        "hosts", listOfNotNull(rayNode?.host, yggPeerHost(yggPeer)).map { it.trim('[', ']') }.distinct()
                    )
                    val yggTag = yggProfileEndpoints.getOrPut(seed) {
                        "$tagOut-ygg".also { tag ->
                            endpoints.add(Outbound().apply {
                                type = "yggdrasil"
                                this.tag = tag
                                // the device's key never leaves the app, as the SPA key
                                _hack_config_map["private_key"] = if (forExport) "<the device key stays in the app>" else seed
                                _hack_config_map["peers"] = listOf(yggPeer) + (if (viaOn) via else emptyList())
                                _hack_config_map["node_info_privacy"] = true
                                knockTag?.let { _hack_config_map["detour"] = it }
                            })
                        } to yggPeer
                    }.first
                    // a key an earlier profile started the mesh node with: this profile's public peers join it
                    if (viaOn) endpoints.filterIsInstance<Outbound>().firstOrNull { it.tag == yggTag }?.let { endpoint ->
                        val peers = (endpoint._hack_config_map["peers"] as? List<*>).orEmpty().map { it.toString() }
                        endpoint._hack_config_map["peers"] = (peers + via).distinct()
                    }
                    currentOutbound._hack_config_map.remove("domain_resolver")
                    currentOutbound._hack_config_map["detour"] = yggTag
                    pastDetourHolder = null
                }

                // an entry hop whose server is a Yggdrasil address dials through the mesh; with
                // port knocking the knocks go through it too
                if (yggdrasil && yggPeer == null && index == profileList.lastIndex &&
                    !DnsTunnel.of(bean).enabled && !proxyEntity.needExternal() &&
                    bean.serverAddress.isYggdrasilAddress()
                ) {
                    // a latency test runs next to the main node: a node of its own, with a
                    // one-time key, so two nodes never claim one address
                    if (forTest) addYggdrasilEndpoint(Libcore.yggdrasilGenerateKey())
                    val dialing = pastDetourHolder ?: currentOutbound
                    dialing._hack_config_map.remove("domain_resolver")
                    dialing._hack_config_map["detour"] = TAG_YGGDRASIL
                    if (buildingProxy) proxyThroughYggdrasil = true
                }

                // DNS tunnel (experimental): the entry hop reaches its server through the core's
                // dnstt or masterdnsvpn outbound; a resolver left to the client ("auto") is for dnstt the one picked
                // for this network (dnsprobe-v1), else the default of before; for masterdns the network's own and the
                // country's (RayDnsResolvers)
                // dnszones-v1: one zone at a time, the main one unless this network's pick or failures moved it,
                // decided on the tunnel as the profile has it (RayDnsResolvers.forConfig)
                val dnsTunnel = DnsTunnel.of(bean).let { t ->
                    if (!t.enabled) t
                    else {
                        val owner = RayNodes.ownerOf(proxyEntity.id)?.first
                        if (notes) io.niko.raycontrol.RayDnsResolvers.noteRunningZone(owner, t)
                        io.niko.raycontrol.RayDnsResolvers.forConfig(t, owner) {
                            io.niko.raycontrol.RayDnsResolvers.resolverFor(owner, t.engine).also { resolver ->
                                if (notes) {
                                    io.niko.raycontrol.RayDnsResolvers.noteRunning(owner, t.engine, resolver)
                                    // no pick on this network yet: one now, in the background
                                    io.niko.raycontrol.RayDnsResolvers.pickSoon(owner, t)
                                }
                            }
                        }
                    }
                }
                if (dnsTunnel.enabled) {
                    val prefix = "DNS tunnel (${bean.displayName()})"
                    dnsTunnel.validate()?.let { throw Exception("${bean.displayName()}: $it") }
                    if (knockTag != null) throw Exception("$prefix: not together with port knocking")
                    if (proxyEntity.needExternal()) throw Exception("$prefix: not supported for plugin profiles")
                    // the tunnels carry TCP only
                    if (bean is HysteriaBean || bean is TuicBean || bean is WireGuardBean ||
                        bean is StandardV2RayBean && bean.type == "quic"
                    ) {
                        throw Exception("$prefix: the tunnel carries TCP, this profile needs UDP")
                    }
                    if (bean is ConfigBean) throw Exception("$prefix: not supported for custom configs")
                    if (index != profileList.lastIndex) throw Exception("$prefix: only on the first hop of a chain")
                    if (buildingProxy) tunnelledHop = true
                    if (dnsTunnel.engine == DnsTunnel.SLIPSTREAM) {
                        if (forExport) throw Exception("$prefix: slipstream runs a client of its own, not in an exported config")
                        // the client's queries leave through the core's own sockets, a mapping port per resolver
                        // (a path each), so they stay out of the VPN as the plugins' traffic does
                        val mapped = dnsTunnel.slipResolvers.mapIndexed { i, (host, port) ->
                            val mappingPort = mkPort()
                            val mappingTag = "$tagOut-slipstream-$i"
                            inbounds.add(Inbound_DirectOptions().apply {
                                type = "direct"
                                listen = LOCALHOST
                                listen_port = mappingPort
                                tag = mappingTag
                                network = "udp"
                                override_address = host
                                override_port = port
                            })
                            slipstreamRules += Rule_DefaultOptions().apply {
                                inbound = listOf(mappingTag)
                                outbound = TAG_DIRECT
                            }
                            "$LOCALHOST:$mappingPort"
                        }
                        // dnsauth-v2: the key of the device for the service (K), into the client's environment
                        val env = mutableMapOf<String, String>()
                        var clockOffset = 0L
                        dnsTunnel.authNode?.let { (nodeId, serviceId) ->
                            val node = RayNodes.get(nodeId)
                                ?: throw Exception("$prefix: the raycontrol node of this tunnel is not imported (dnsauth)")
                            env["SLIPSTREAM_DNSAUTH_KEY"] = RayNodes.dnsAuthKeyHex(node, serviceId)
                            clockOffset = node.clockSkew
                        }
                        val localPort = mkPort()
                        slipstreamLaunches += ConfigBuildResult.SlipstreamLaunch(
                            dnsTunnel.slipstreamArgs(localPort, mapped, clockOffset), env, localPort,
                        )
                        // the hop dials the client; the server forwards each stream to its one upstream. A TLS hop
                        // that names no server took the server's name, 127.0.0.1 from here: the name goes in
                        (runCatching { currentOutbound.javaClass.getField("tls").get(currentOutbound) }.getOrNull()
                            as? OutboundTLSOptions)
                            ?.takeIf { it.enabled == true && it.server_name.isNullOrBlank() && !bean.serverAddress.isIpAddress() }
                            ?.server_name = bean.serverAddress
                        currentOutbound._hack_config_map.remove("domain_resolver")
                        currentOutbound._hack_config_map["server"] = LOCALHOST
                        currentOutbound._hack_config_map["server_port"] = localPort
                    } else {
                        val tunnelTag = "$tagOut-dnstunnel"
                        // libcore's protect server runs with the main instance; a latency test without
                        // one must not point the tunnel's own sockets at a missing socket
                        val protectPath = "protect_path".takeIf {
                            !forTest || File(SagerNet.application.noBackupFilesDir, it).exists()
                        }
                        // dnsauth-v1: the node's device key goes into the core by reference, never into the config; the tag's
                        // epoch is the node's, by the subscription's skew
                        val authNode = dnsTunnel.authNode?.let { (nodeId, _) ->
                            RayNodes.get(nodeId)
                                ?: throw Exception("$prefix: the raycontrol node of this tunnel is not imported (dnsauth)")
                        }
                        if (authNode != null && !forExport) RayNodes.prepareDeviceKey(authNode)
                        outbounds.add(Outbound().apply {
                            tag = tunnelTag
                            _hack_config_map.putAll(
                                dnsTunnel.outboundOptions(protectPath, authNode?.keyRef, authNode?.clockSkew ?: 0)
                            )
                        })
                        // the tunnel server resolves or ignores the hop's server name
                        currentOutbound._hack_config_map.remove("domain_resolver")
                        currentOutbound._hack_config_map["detour"] = tunnelTag
                    }
                }

                // External proxy need a dokodemo-door inbound to forward the traffic
                // For external proxy software, their traffic must goes to v2ray-core to use protected fd.
                bean.finalAddress = bean.serverAddress
                bean.finalPort = bean.serverPort
                // the knock outbound only sees plugin traffic that goes through the mapping inbound,
                // the hysteria plugin always dials serverAddress:serverPorts itself
                if (knockTag != null && proxyEntity.needExternal() && (bean is HysteriaBean || !bean.canMapping())) {
                    throw Exception("Port knocking (${bean.displayName()}): not supported for this plugin profile")
                }
                if (bean.canMapping() && proxyEntity.needExternal()) {
                    // With ss protect, don't use mapping
                    var needExternal = true
                    if (index == profileList.lastIndex) {
                        val pluginId = when (bean) {
                            is HysteriaBean -> if (bean.protocolVersion == 1) "hysteria-plugin" else "hysteria2-plugin"
                            else -> ""
                        }
                        if (Plugins.isUsingMatsuriExe(pluginId)) {
                            needExternal = false
                        } else if (Plugins.getPluginExternal(pluginId) != null) {
                            throw Exception("You are using an unsupported $pluginId, please download the correct plugin.")
                        }
                    }
                    if (needExternal) {
                        val mappingPort = mkPort()
                        bean.finalAddress = LOCALHOST
                        bean.finalPort = mappingPort

                        inbounds.add(Inbound_DirectOptions().apply {
                            type = "direct"
                            listen = LOCALHOST
                            listen_port = mappingPort
                            tag = "$chainTag-mapping-${proxyEntity.id}"

                            override_address = bean.serverAddress
                            override_port = bean.serverPort

                            pastInboundTag = tag

                            // no chain rule and not outbound, so need to set to direct
                            if (index == profileList.lastIndex) {
                                route.rules.add(Rule_DefaultOptions().apply {
                                    inbound = listOf(tag)
                                    outbound = knockTag ?: TAG_DIRECT
                                })
                            }
                        })
                    }
                }

                if (currentOutbound is Endpoint_WireGuardOptions) {
                    endpoints.add(currentOutbound)
                } else {
                    outbounds.add(currentOutbound)
                }
                chainOutbounds.add(currentOutbound)
                pastOutbound = currentOutbound
                pastEntity = proxyEntity
            }

            trafficMap[chainTagOut] = chainTrafficSet.toList()
            return chainTagOut
        }

        // build outbounds
        buildingProxy = true
        if (buildSelector) {
            val list = group.id.let { SagerDatabase.proxyDao.getByGroup(it) }
            list.forEach {
                tagMap[it.id] = buildChain(it.id, it)
            }
            outbounds.add(0, Outbound_SelectorOptions().apply {
                type = "selector"
                tag = TAG_PROXY
                default_ = tagMap[proxy.id]
                outbounds = tagMap.values.toList()
            })
            route.final_ = TAG_PROXY
        } else if (buildAuto) {
            SagerDatabase.proxyDao.getByGroup(group!!.id).forEach {
                tagMap[it.id] = buildChain(it.id, it)
            }
            outbounds.add(0, Outbound_URLTestOptions().apply {
                type = "urltest"
                tag = TAG_PROXY
                outbounds = tagMap.values.toList()
                url = DataStore.connectionTestURL
                interval = "3m"
                tolerance = 50
            })
            route.final_ = TAG_PROXY
        } else {
            route.final_ = buildChain(0, proxy)
        }
        // a single profile (not a group, whose members differ) that carries UDP inside a stream: QUIC refused, so
        // browsers go to TCP at once instead of QUIC inside TCP
        val refuseQuic = !forTest && !buildSelector && !buildAuto && DataStore.refuseQuicOverStreams &&
                udpRidesAStream(proxy.resolveChain())
        buildingProxy = false
        // Yggdrasil peers through the proxy, unless the proxy itself needs the mesh
        if (yggdrasil && !forTest && DataStore.yggdrasilPeersViaProxy) {
            if (proxyThroughYggdrasil) {
                Logs.w("Yggdrasil: the profile goes through the mesh, peers are dialled directly")
            } else {
                yggdrasilEndpoint?._hack_config_map?.set("detour", TAG_PROXY)
            }
        }
        // build outbounds from route item
        extraProxies.forEach { (key, p) ->
            tagMap[key] = buildChain(key, p)
        }

        // WireGuard requires a real destination for UDP, including FakeDNS
        // addresses. Let the native resolve action restore it before routing.
        if (!forTest && !DataStore.resolveDestination && endpoints.any { it is Endpoint_WireGuardOptions }) {
            route.rules.add(Rule_DefaultOptions().apply {
                inbound = if (isVPN) listOf("tun-in", TAG_MIXED) else listOf(TAG_MIXED)
                network = listOf("udp")
                action = "resolve"
                strategy = genDomainStrategy(true)
            })
        }

        // apply user rules
        // an exported config keeps the rules as they are: the rule-sets go by their URLs
        val russiaDirectWaits = !forExport && RoutePresets.russiaDirectWaits(extraRules)
        val missingInConfig = mutableSetOf<String>()
        for (rule in extraRules) {
            if (russiaDirectWaits && RoutePresets.isRussiaDirect(rule)) {
                Logs.i("routes: ${rule.displayName()} waits for the blocked lists (Russian services go through the proxy meanwhile)")
                continue
            }
            if (rule.packages.isNotEmpty()) {
                PackageCache.awaitLoadSync()
            }
            val uidList = rule.packages.map {
                if (!isVPN) {
                    Toast.makeText(
                        SagerNet.application,
                        SagerNet.application.getString(R.string.route_need_vpn, rule.displayName()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                PackageCache[it]?.takeIf { uid -> uid >= 1000 }
            }.toHashSet().filterNotNull()
            val ruleSets = mutableListOf<RuleSet>()
            var dropRule = false

            val ruleObj = Rule_DefaultOptions().apply {
                if (uidList.isNotEmpty()) {
                    PackageCache.awaitLoadSync()
                    user_id = uidList
                }
                var domainList: List<String>? = null
                // a rule-set the app has not downloaded yet is left out; a rule it leaves without any address is
                // left out whole (its port or app alone would match far more)
                var missingRuleSet = false
                fun downloaded(list: List<String>): List<String> {
                    // an export: those the app would fetch (https), by their URLs; the running config: those here
                    val (kept, missing) = if (forExport) {
                        list.partition { !RuleSetFiles.isItem(it) || RuleSetFiles.fetchable(it) }
                    } else {
                        RuleSetFiles.withoutMissing(list)
                    }
                    if (missing.isNotEmpty()) missingRuleSet = true
                    if (!forExport) missingInConfig += missing
                    return kept
                }
                if (rule.domains.isNotBlank()) {
                    domainList = downloaded(rule.domains.listByLineOrComma())
                    makeSingBoxRule(domainList, false)
                }
                if (rule.ip.isNotBlank()) {
                    makeSingBoxRule(downloaded(rule.ip.listByLineOrComma()), true)
                }
                dropRule = missingRuleSet && !hasAddress()

                if (rule_set != null) generateRuleSet(rule_set, ruleSets, forExport = forExport)

                if (rule.port.isNotBlank()) {
                    port = mutableListOf<Int>()
                    port_range = mutableListOf<String>()
                    rule.port.listByLineOrComma().map {
                        if (it.contains(":")) {
                            port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { port.add(this) }
                        }
                    }
                }
                if (rule.sourcePort.isNotBlank()) {
                    source_port = mutableListOf<Int>()
                    source_port_range = mutableListOf<String>()
                    rule.sourcePort.listByLineOrComma().map {
                        if (it.contains(":")) {
                            source_port_range.add(it)
                        } else {
                            it.toIntOrNull()?.apply { source_port.add(this) }
                        }
                    }
                }
                if (rule.network.isNotBlank()) {
                    network = listOf(rule.network)
                }
                if (rule.source.isNotBlank()) {
                    source_ip_cidr = rule.source.listByLineOrComma()
                }
                if (rule.protocol.isNotBlank()) {
                    protocol = rule.protocol.listByLineOrComma()
                }

                fun makeDnsRuleObj(): DNSRule_DefaultOptions {
                    return DNSRule_DefaultOptions().apply {
                        if (uidList.isNotEmpty()) user_id = uidList
                        domainList?.let { makeSingBoxRule(it, forExport) }
                    }
                }

                // a rule given domains whose DNS rule is left with none (a rule-set not here yet, an IP-only one) has
                // no DNS rule: with the app alone it would take every name the app looks up
                fun addDnsRule(dnsRule: DNSRule_DefaultOptions) {
                    if (rule.domains.isBlank() || dnsRule.hasDomain()) userDNSRuleList += dnsRule
                }

                if (!dropRule) when (rule.outbound) {
                    -1L -> {
                        addDnsRule(makeDnsRuleObj().apply { server = "dns-direct" })
                    }

                    0L -> {
                        if (useFakeDns) addDnsRule(makeDnsRuleObj().apply {
                            server = "dns-fake"
                            inbound = listOf("tun-in")
                        })
                        addDnsRule(makeDnsRuleObj().apply {
                            server = "dns-remote"
                        })
                    }

                    -2L -> {
                        addDnsRule(makeDnsRuleObj().apply {
                            action = "predefined"
                            rcode = "NOERROR"
                        })
                    }
                }

                outbound = when (val outId = rule.outbound) {
                    0L -> TAG_PROXY
                    -1L -> TAG_BYPASS
                    -2L -> TAG_BLOCK
                    // in Auto mode "proxy" is the urltest: a rule naming a profile means that profile
                    else -> if (outId == proxy.id && !buildAuto) TAG_PROXY else tagMap[outId] ?: ""
                }

                _hack_custom_config = rule.config
            }
            if (dropRule) continue

            if (!ruleObj.checkEmpty()) {
                if (ruleObj.outbound.isNullOrBlank()) {
                    Toast.makeText(
                        SagerNet.application,
                        "Warning: " + rule.displayName() + ": A non-existent outbound was specified.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // block 改用新的写法
                    if (ruleObj.outbound == TAG_BLOCK) {
                        ruleObj.outbound = null
                        ruleObj.action = "reject"
                    }
                    // split ClientHello of directly routed TLS, e.g. for SNI-throttled sites
                    if (ruleObj.outbound == TAG_BYPASS) when (DataStore.tlsFragmentBypass) {
                        1 -> ruleObj._hack_config_map["tls_record_fragment"] = true
                        2 -> ruleObj._hack_config_map["tls_fragment"] = true
                    }
                    // its QUIC refused before it takes it into the proxy (refuseQuic): otherwise only what reaches
                    // the final outbound would be, and the user's lists to the proxy are what the setting is for
                    if (refuseQuic && ruleObj.outbound == TAG_PROXY) quicRefusalBefore(ruleObj, needSniff)?.let(route.rules::add)
                    route.rules.add(ruleObj)
                    route.rule_set.addAll(ruleSets)
                }
            }
        }
        // the running config: the service restarts the core once a list it left out is downloaded
        if (notes) RuleSetFiles.missingInConfig = missingInConfig

        // 对 rule_set tag 去重
        if (route.rule_set != null) {
            route.rule_set = route.rule_set.distinctBy { it.tag }
        }

        // remote rule-sets live in the cache file across restarts: without it every start downloads
        // them again, and sing-box refuses to start when that first download fails. FakeDNS's addresses too: the
        // system keeps the ones it was given for the VPN's network, which a restart of the core under the same VPN
        // keeps (a handover in place), the strict Private DNS host's above all; a core that forgot them refuses
        // them ("missing fakeip record") or gives them to other names, and with the strict mode no app resolves
        if (!forTest && (useFakeDns || route.rule_set?.any { it.type == "remote" } == true)) {
            if (experimental == null) experimental = ExperimentalOptions()
            experimental.cache_file = CacheFile().apply {
                enabled = true
                path = "cache.db" // relative to the core's no_backup working directory
                if (useFakeDns) store_fakeip = true
            }
        }

        for (freedom in arrayOf(TAG_DIRECT, TAG_BYPASS)) outbounds.add(Outbound().apply {
            tag = freedom
            type = "direct"
        })

        // Bypass Lookup for the first profile
        bypassDNSBeans.forEach {
            var serverAddr = it.serverAddress

            if (it is ConfigBean) {
                var config = mutableMapOf<String, Any>()
                config = gson.fromJson(it.config, config.javaClass)
                config["server"]?.apply {
                    serverAddr = toString()
                }
            }

            if (!serverAddr.isIpAddress()) {
                domainListDNSDirectForce.add("full:${serverAddr}")
            }
        }

        remoteDns.forEach {
            var address = it
            if (address.contains("://")) {
                address = address.substringAfter("://")
            }
            "https://$address".toHttpUrlOrNull()?.apply {
                if (!host.isIpAddress()) {
                    domainListDNSDirectForce.add("full:$host")
                }
            }
        }

        dns.servers.add(DNSServerOptions.fromAddress("local", "dns-local", null, null))
        dns.servers.add(DNSServerOptions.fromAddress(
            directDNS.firstOrNull() ?: throw Exception("No direct DNS, check your settings!"),
            "dns-direct", null, "dns-local"
        ))
        val dohOfNode = nodeDoh
        if (!forTest && dohOfNode != null) dns.servers.add(nodeDohServer(dohOfNode.first, dohOfNode.second))
        else if (!forTest) dns.servers.add(DNSServerOptions.fromAddress(
            remoteDns.firstOrNull() ?: throw Exception("No remote DNS, check your settings!"),
            "dns-remote", TAG_PROXY, "dns-direct"
        ).apply {
            // the same resolver over a TCP transport: UDP, DoQ and DoH3 cannot pass a DNS tunnel
            if (tunnelledHop) when (type) {
                "udp" -> type = "tcp"
                // a DoQ or DoH3 port is not the DoT or DoH port: back to the default
                "quic" -> {
                    type = "tls"
                    server_port = null
                }

                "h3" -> {
                    type = "https"
                    server_port = null
                }
            }
        })

        // server address lookups go straight to a transport, so they need the direct strategy
        autoDnsDomainStrategy(SingBoxOptionsUtil.domainStrategy("dns-direct"))?.let { strategy ->
            dns.servers.forEach {
                val resolver = it.domain_resolver ?: return@forEach
                it.domain_resolver = null
                it._hack_config_map["domain_resolver"] = mapOf("server" to resolver, "strategy" to strategy)
            }
            route.default_domain_resolver = null
            route._hack_config_map["default_domain_resolver"] =
                mapOf("server" to "dns-direct", "strategy" to strategy)
        }

        dns.final_ = if (forTest) "dns-direct" else "dns-remote"

        // dns object user rules
        if (enableDnsRouting) {
            userDNSRuleList.forEach {
                if (!it.checkEmpty()) dns.rules.add(it)
            }
        }

        if (forTest) {
            dns.rules = listOf()
        } else {
            // built-in DNS rules
            route.rules.add(0, Rule_DefaultOptions().apply {
                protocol = listOf("dns")
                action = "hijack-dns"
            })
            route.rules.add(0, Rule_DefaultOptions().apply {
                port = listOf(53)
                action = "hijack-dns"
            })
            // sniff must run before protocol matching
            if (needSniff) route.rules.add(0, Rule_DefaultOptions().apply {
                inbound = if (isVPN) listOf("tun-in", TAG_MIXED) else listOf(TAG_MIXED)
                action = "sniff"
            })
            yggdrasilInboundRule?.let { rule ->
                val meshRules = mutableListOf<Rule_DefaultOptions>()
                if (rule.outbound == TAG_DIRECT) {
                    // the mesh arrives at 127.0.0.1: never at this app's own listeners (the local proxy, the Clash
                    // API, the plugins' SOCKS and forwarding ports, the slipstream clients'), whatever their
                    // password (S7)
                    val own = (inbounds.mapNotNull {
                        (it as? Inbound_MixedOptions)?.listen_port ?: (it as? Inbound_DirectOptions)?.listen_port
                    } + externalIndexMap.flatMap { it.chain.keys } + slipstreamLaunches.map { it.localPort } +
                            listOfNotNull(9090.takeIf { experimental?.clash_api != null })).distinct()
                    if (own.isNotEmpty()) meshRules += Rule_DefaultOptions().apply {
                        inbound = listOf(TAG_YGGDRASIL)
                        port = own
                        action = "reject"
                    }
                    // the ports the user opened, or every other one
                    val open = MeshPorts.parse(DataStore.yggdrasilInboundPorts)
                    if (open != null) {
                        meshRules += Rule_DefaultOptions().apply {
                            inbound = listOf(TAG_YGGDRASIL)
                            if (open.ports.isNotEmpty()) port = open.ports
                            if (open.ranges.isNotEmpty()) port_range = open.ranges
                            outbound = TAG_DIRECT
                        }
                        meshRules += Rule_DefaultOptions().apply {
                            inbound = listOf(TAG_YGGDRASIL)
                            action = "reject"
                        }
                    } else meshRules += rule
                } else meshRules += rule
                route.rules.addAll(0, meshRules)
            }
            if (DataStore.bypassLanInCore) {
                route.rules.add(Rule_DefaultOptions().apply {
                    outbound = TAG_BYPASS
                    ip_is_private = true
                })
            }
            // block mcast
            route.rules.add(Rule_DefaultOptions().apply {
                ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                source_ip_cidr = listOf("224.0.0.0/3", "ff00::/8")
                action = "reject"
            })
            // QUIC that would reach the final outbound, the proxy, refused with an ICMP unreachable (the user's rules
            // to the proxy got a copy of their own above them; what they send elsewhere keeps it): by the sniffed
            // protocol, else UDP 443 (a WireGuard on 443 then goes too)
            if (refuseQuic) route.rules.add(quicRefusal(needSniff))
            // FakeDNS obj
            if (useFakeDns) {
                dns.servers.add(DNSServerOptions().apply {
                    type = "fakeip"
                    tag = "dns-fake"
                    inet4_range = "198.18.0.0/15"
                    inet6_range = "fc00::/18"
                })
                dns.rules.add(DNSRule_DefaultOptions().apply {
                    inbound = listOf("tun-in")
                    server = "dns-fake"
                    disable_cache = true
                })
                // the host of strict Private DNS resolved for real, above every rule that would give it a fake
                // address: the system keeps what it got for the VPN's network and sends every app's DNS there, and a
                // fake address holds only while the core that gave it remembers it. Not in an exported config: the
                // host may name this install's account (a NextDNS profile's ID); sing-box matches the rule's domain
                // as written against the lower-case name asked
                if (!forExport) PrivateDns.strictHost()?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() }?.let { host ->
                    dns.rules.add(0, DNSRule_DefaultOptions().apply {
                        domain = listOf(host)
                        server = "dns-remote"
                    })
                }
            }
            // force bypass (always top DNS rule)
            if (domainListDNSDirectForce.isNotEmpty()) {
                dns.rules.add(0, DNSRule_DefaultOptions().apply {
                    makeSingBoxRule(domainListDNSDirectForce.toHashSet().toList())
                    server = "dns-direct"
                })
            }
        }
        // nothing reaches the device through the mesh node of a raycontrol Yggdrasil profile: the node and the mesh
        // behind it would get the device's resolver, its local listeners or the proxy. Above every other rule
        if (yggProfileEndpoints.isNotEmpty()) route.rules.add(0, Rule_DefaultOptions().apply {
            inbound = yggProfileEndpoints.values.map { it.first }
            action = "reject"
        })
        // slipstream's queries from its mapping ports go out as they are, above the DNS hijack (port 53)
        route.rules.addAll(0, slipstreamRules)

        dns.strategy = autoDnsDomainStrategy(SingBoxOptionsUtil.domainStrategy(dns.final_))
        dns.rules.filterIsInstance<DNSRule_DefaultOptions>().forEach { rule ->
            rule.strategy = when (rule.server) {
                "dns-direct", "dns-remote" -> autoDnsDomainStrategy(SingBoxOptionsUtil.domainStrategy(rule.server))
                "dns-fake" -> "ipv4_only"
                else -> null
            }
        }

        if (!forTest) _hack_custom_config = DataStore.globalCustomConfig
    }.let {
        val configMap = it.asMap()
        Util.mergeJSON(configMap, proxy.requireBean().customConfigJson)
        ConfigBuildResult(
            gson.toJson(configMap),
            externalIndexMap,
            proxy.id,
            trafficMap,
            tagMap,
            if (buildSelector) group.id else -1L,
            yggProfileEndpoints.keys.toSet(),
            slipstreamLaunches.toList(),
        )
    }

}

/** a literal address in 200::/7 */
fun String.isYggdrasilAddress(): Boolean {
    val host = trim().removePrefix("[").removeSuffix("]")
    if (!host.contains(':')) return false
    val first = host.substringBefore(':').ifEmpty { return false }
    val value = first.toIntOrNull(16) ?: return false
    // 200::/7: the first 16 bits are 0x0200-0x03ff
    return value in 0x200..0x3ff && host.isIpAddress()
}

/**
 * The rotating port of a raycontrol UDP service (spa-v2.md §11) for a Hysteria2 row of the node's subscription:
 * the row's key names the service. Null for other profiles, or when the profile hops ports itself.
 */
/**
 * What a raycontrol node needs on an outbound's TLS: post-quantum key exchange only, no fallback to classical groups.
 * TLS offers X25519MLKEM768 alone (Go TLS: Hysteria2, TUIC, XHTTP over HTTP/3; uTLS takes the Chrome fingerprint and
 * drops its other groups); REALITY requires the hybrid share. naive (Chromium, cronet) offers it by default and takes
 * none of these settings.
 */
internal fun nodeTlsTweaks(outboundType: String?, tls: Map<*, *>?, replacedFingerprint: (Any?) -> Unit = {}): Map<String, Any> {
    if (outboundType == "naive" || tls?.get("enabled") != true) return emptyMap()
    val tweaks = mutableMapOf<String, Any>()
    // only the Chrome fingerprint of our uTLS carries an X25519MLKEM768 share: any other (firefox, safari, random, ...)
    // would fail every handshake to the node
    val utls = tls["utls"] as? Map<*, *>
    if (utls?.get("enabled") == true && utls["fingerprint"] != "chrome") {
        replacedFingerprint(utls["fingerprint"])
        tweaks["utls"] = mapOf("fingerprint" to "chrome")
    }
    if ((tls["reality"] as? Map<*, *>)?.get("enabled") == true) {
        tweaks["reality"] = mapOf("key_share" to "hybrid")
    } else {
        tweaks["curve_preferences"] = listOf("X25519MLKEM768")
    }
    return tweaks
}

/**
 * The tweaks over an outbound's own TLS map, when it keeps one among its raw options (naive): a tweak never drops
 * what the outbound set. A typed outbound has none there, and its TLS object takes the tweaks when it is written out.
 */
internal fun mergeTls(own: Map<*, *>?, tweaks: Map<String, Any>): Map<String, Any?> {
    if (own == null) return tweaks
    return own.entries.associate { it.key.toString() to it.value } + tweaks
}

private fun rotatingUdpPorts(node: RayNode, entity: ProxyEntity, bean: AbstractBean): List<Map<String, Any>>? {
    if (bean !is HysteriaBean || bean.serverPorts.contains(',') || bean.serverPorts.contains('-')) return null
    val (owner, key) = RayNodes.ownerOf(entity.id) ?: return null
    if (owner.spa.nodeId != node.spa.nodeId) return null
    val svc = key.substringBefore('#').toIntOrNull() ?: return null
    val ports = node.services.firstOrNull { it.id == svc }?.ports ?: return null
    val port = bean.serverPorts.trim().toIntOrNull() ?: return null
    return listOf(mapOf("port" to port, "port_key" to ports.portKey, "port_base" to ports.portBase, "port_range" to ports.portRange))
}

/**
 * The node's DoH as the remote DNS (raycontrol niko-r3-note.md §2 p. 2): RFC 8484 over h2 to the node's own
 * address, the one SPA opened, with the DoH name as SNI and post-quantum key exchange only.
 */
private fun nodeDohServer(node: RayNode, knockTag: String): DNSServerOptions {
    val url = node.doh!!.toHttpUrlOrNull() ?: throw Exception("raycontrol node ${node.host}: bad DoH URL")
    return DNSServerOptions().apply {
        type = "https"
        tag = "dns-remote"
        server = node.host
        server_port = url.port
        path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        detour = knockTag
        if (!node.host.isIpAddress()) domain_resolver = "dns-direct"
        _hack_config_map["tls"] = mapOf(
            "enabled" to true,
            "server_name" to url.host,
            "curve_preferences" to listOf("X25519MLKEM768"),
        )
    }
}
