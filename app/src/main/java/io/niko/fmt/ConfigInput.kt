package io.niko.fmt

import android.widget.Toast
import io.niko.CONNECTION_TEST_URL
import io.niko.DEFAULT_REMOTE_DNS
import io.niko.IPv6Mode
import io.niko.Key
import io.niko.R
import io.niko.SagerNet
import io.niko.TunImplementation
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.RuleEntity
import io.niko.database.SagerDatabase
import io.niko.ktx.mkPort
import io.niko.plugin.Plugins
import io.niko.raycontrol.BrutalRates
import io.niko.raycontrol.RayBrutal
import io.niko.raycontrol.RayChannel
import io.niko.raycontrol.RayDnsResolvers
import io.niko.raycontrol.RayLan
import io.niko.raycontrol.RayLanDirect
import io.niko.raycontrol.RayLanNet
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RayRelay
import io.niko.utils.PackageCache
import io.niko.utils.PrivateDns
import libcore.Libcore
import java.io.File

/**
 * Everything [buildConfig] reads (docs/design/desktop-stage3.md, section 5, «Шов в Kotlin»): the profile, the
 * settings it takes, the stored profiles, groups and rules, raycontrol's decisions and what only the phone knows.
 * [fromApp] gathers them as the build read them before; a test or the desktop gives its own. The same input gives
 * the same config, and Go's builder takes the same input.
 *
 * Not here yet: the rule-set files (RuleSetFiles, by the directory it is given).
 */
class ConfigInput(
    val proxy: ProxyEntity,
    val settings: ConfigSettings,
    val store: ConfigStore,
    val ray: ConfigRay,
    val device: ConfigDevice,
) {
    companion object {
        fun fromApp(proxy: ProxyEntity) =
            ConfigInput(proxy, ConfigSettings.fromDataStore(), ConfigStore.App, ConfigRay.App, ConfigDevice.App)
    }
}

/** The settings the build reads, the app's defaults for a test. Secrets of this install apart, in [secrets]. */
data class ConfigSettings(
    val serviceMode: String = Key.MODE_VPN,
    val allowAccess: Boolean = false,
    val remoteDns: String = DEFAULT_REMOTE_DNS,
    val directDns: String = "local",
    val enableDnsRouting: Boolean = true,
    val enableFakeDns: Boolean = true,
    val dnsOptimisticCache: Boolean = false,
    val trafficSniffing: Int = 1,
    val resolveDestination: Boolean = false,
    val ipv6Mode: Int = IPv6Mode.DISABLE,
    /** the domain strategies as stored (domain_strategy_for_remote, _direct, _server), see [domainStrategy] */
    val domainStrategyForRemote: String? = null,
    val domainStrategyForDirect: String? = null,
    val domainStrategyForServer: String? = null,
    val enableClashAPI: Boolean = false,
    val logLevel: Int = 0,
    val tunImplementation: Int = TunImplementation.GVISOR,
    val mtu: Int = 9000,
    val mixedPort: Int = 2080,
    val mixedUsername: String = "",
    val localProxyNoAuthInVpn: Boolean = false,
    val proxyApps: Boolean = false,
    val bypassLanInCore: Boolean = false,
    val yggdrasilEnabled: Boolean = false,
    val yggdrasilPeers: String = "",
    val yggdrasilTlsChrome: Boolean = true,
    val yggdrasilAllowInbound: Boolean = false,
    val yggdrasilInboundPorts: String = "",
    val yggdrasilPeersViaProxy: Boolean = false,
    val lanInboundPorts: String = "",
    val honestConnect: Boolean = false,
    val rootStore: String = "chrome",
    val naiveQuicPhoneRoots: Boolean = false,
    val icmpThroughNode: Boolean = false,
    val icmpNoLocalReply: Boolean = false,
    val refuseQuicOverStreams: Boolean = false,
    val tlsFragmentBypass: Int = 0,
    val connectionTestURL: String = CONNECTION_TEST_URL,
    val ruleSetUpdateHours: Int = 24,
    val globalAllowInsecure: Boolean = false,
    val globalCustomConfig: String = "",
    val secrets: ConfigSecrets = ConfigSecrets(),
) {
    val isVPN get() = serviceMode == Key.MODE_VPN

    /** The domain strategy the user set for a DNS server's tag, "" for the core's own: "auto" is none, for the
     * servers' addresses prefer_ipv4. */
    fun domainStrategy(tag: String): String = when (tag) {
        "dns-remote" -> domainStrategyForRemote.orEmpty().replace("auto", "")
        "dns-direct" -> domainStrategyForDirect.orEmpty().replace("auto", "")
        else -> domainStrategyForServer.orEmpty().replace("auto", "prefer_ipv4")
    }

    companion object {
        fun fromDataStore(): ConfigSettings {
            val store = DataStore.configurationStore
            return ConfigSettings(
                serviceMode = DataStore.serviceMode,
                allowAccess = DataStore.allowAccess,
                remoteDns = DataStore.remoteDns,
                directDns = DataStore.directDns,
                enableDnsRouting = DataStore.enableDnsRouting,
                enableFakeDns = DataStore.enableFakeDns,
                dnsOptimisticCache = DataStore.dnsOptimisticCache,
                trafficSniffing = DataStore.trafficSniffing,
                resolveDestination = DataStore.resolveDestination,
                ipv6Mode = DataStore.ipv6Mode,
                domainStrategyForRemote = store.getString("domain_strategy_for_remote"),
                domainStrategyForDirect = store.getString("domain_strategy_for_direct"),
                domainStrategyForServer = store.getString("domain_strategy_for_server"),
                enableClashAPI = DataStore.enableClashAPI,
                logLevel = DataStore.logLevel,
                tunImplementation = DataStore.tunImplementation,
                mtu = DataStore.mtu,
                mixedPort = DataStore.mixedPort,
                mixedUsername = DataStore.mixedUsername,
                localProxyNoAuthInVpn = DataStore.localProxyNoAuthInVpn,
                proxyApps = DataStore.proxyApps,
                bypassLanInCore = DataStore.bypassLanInCore,
                yggdrasilEnabled = DataStore.yggdrasilEnabled,
                yggdrasilPeers = DataStore.yggdrasilPeers,
                yggdrasilTlsChrome = DataStore.yggdrasilTlsChrome,
                yggdrasilAllowInbound = DataStore.yggdrasilAllowInbound,
                yggdrasilInboundPorts = DataStore.yggdrasilInboundPorts,
                yggdrasilPeersViaProxy = DataStore.yggdrasilPeersViaProxy,
                lanInboundPorts = DataStore.lanInboundPorts,
                honestConnect = DataStore.honestConnect,
                rootStore = DataStore.rootStore,
                naiveQuicPhoneRoots = DataStore.naiveQuicPhoneRoots,
                icmpThroughNode = DataStore.icmpThroughNode,
                icmpNoLocalReply = DataStore.icmpNoLocalReply,
                refuseQuicOverStreams = DataStore.refuseQuicOverStreams,
                tlsFragmentBypass = DataStore.tlsFragmentBypass,
                connectionTestURL = DataStore.connectionTestURL,
                ruleSetUpdateHours = DataStore.ruleSetUpdateHours,
                globalAllowInsecure = DataStore.globalAllowInsecure,
                globalCustomConfig = DataStore.globalCustomConfig,
                secrets = ConfigSecrets(
                    // made on first use: read only where the build takes them, as before
                    clashApi = if (DataStore.enableClashAPI) DataStore.clashApiSecret else "",
                    localProxyPassword = DataStore.localProxyPassword,
                    mixedPassword = DataStore.mixedPassword,
                    yggdrasilKey = if (DataStore.yggdrasilEnabled) DataStore.yggdrasilPrivateKey else "",
                ),
            )
        }
    }
}

/** The install's secrets a config may carry: the Clash API's, the local proxy's passwords, the mesh key. Never in a
 * log: [toString] names none. */
class ConfigSecrets(
    val clashApi: String = "",
    val localProxyPassword: String = "",
    /** the user's own password for the local proxy, "" for none */
    val mixedPassword: String = "",
    val yggdrasilKey: String = "",
) {
    override fun toString() = "ConfigSecrets(not shown)"
}

/** The stored profiles, groups and rules, as the build asks for them. */
interface ConfigStore {
    fun group(id: Long): ProxyGroup?
    fun profile(id: Long): ProxyEntity?
    fun profiles(ids: List<Long>): List<ProxyEntity>

    /** a group's profiles in their order */
    fun profilesOf(groupId: Long): List<ProxyEntity>

    /** the enabled rules in their order */
    fun enabledRules(): List<RuleEntity>

    /** the app's database */
    object App : ConfigStore {
        override fun group(id: Long) = SagerDatabase.groupDao.getById(id)
        override fun profile(id: Long) = SagerDatabase.proxyDao.getById(id)
        override fun profiles(ids: List<Long>) = SagerDatabase.proxyDao.getEntities(ids)
        override fun profilesOf(groupId: Long) = SagerDatabase.proxyDao.getByGroup(groupId)
        override fun enabledRules() = SagerDatabase.rulesDao.enabledRules()
    }

    /** These profiles, groups and rules, in the database's orders. */
    class Of(
        private val all: List<ProxyEntity>,
        groups: List<ProxyGroup> = emptyList(),
        private val rules: List<RuleEntity> = emptyList(),
    ) : ConfigStore {
        private val groupsById = groups.associateBy { it.id }
        private val byId = all.associateBy { it.id }
        override fun group(id: Long) = groupsById[id]
        override fun profile(id: Long) = byId[id]
        override fun profiles(ids: List<Long>) = all.sortedBy { it.id }.filter { it.id in ids }
        override fun profilesOf(groupId: Long) = all.filter { it.groupId == groupId }.sortedBy { it.userOrder }
        override fun enabledRules() = rules.filter { it.enabled }.sortedBy { it.userOrder }
    }
}

/**
 * What raycontrol decides for a build: the imported nodes and what this network measured for them, and what the
 * running core is noted to have (only with the build's notes, for the watchdog). [None]: no node imported.
 */
interface ConfigRay {
    /** a removed node's device key leaves this process's core */
    fun retainCoreKeys()
    fun nodes(): List<RayNode>
    fun node(nodeId: String): RayNode?
    fun forHost(address: String?): RayNode?

    /** the node of a row of its subscription, and the row's key */
    fun ownerOf(profileId: Long): Pair<RayNode, String>?
    fun forRelayHost(address: String?): Pair<RayNode, RayRelay>?
    fun relayOfRow(profileId: Long): Pair<RayNode, RayRelay>?

    /** the node's names, past ones with them */
    fun names(node: RayNode): Set<String>

    /** the knock outbound's options, the device key handed to the core */
    fun prepareKnock(node: RayNode, relay: RayRelay?): Map<String, Any>
    fun prepareDeviceKey(node: RayNode)
    fun dnsAuthKeyHex(node: RayNode, serviceId: Int): String

    /** Brutal's rates measured on this network */
    fun brutalRates(node: RayNode): BrutalRates?
    fun noteBrutal(node: RayNode, rates: BrutalRates?)

    /** the node's public Yggdrasil peers on this network: whether they are on, and which */
    fun yggViaActive(nodeId: String): Boolean
    fun yggViaPeers(nodeId: String): List<String>?
    fun noteYggVia(nodeId: String, on: Boolean)

    /** the DNS tunnels' resolvers and zones noted anew */
    fun clearDnsRunning()

    /** the DNS tunnel as this network takes it: its zone and resolver; with [notes], noted and picked soon */
    fun dnsTunnel(tunnel: DnsTunnel, owner: RayNode?, notes: Boolean): DnsTunnel

    /** lan-v1 */
    fun lanOf(node: RayNode): RayLan?
    fun lanNeighbours(node: RayNode, now: Long): List<String>
    fun lanRow(node: RayNode): ProxyEntity?
    fun lanNames(nodes: List<RayNode>, now: Long): Map<String, String>
    fun lanDirectPassword(node: RayNode, now: Long): String?
    fun noteLan(endpoints: Boolean, nodes: List<RayNode>, now: Long, direct: Boolean, nextSwitch: Long?)

    /** the app's raycontrol */
    object App : ConfigRay {
        override fun retainCoreKeys() = RayNodes.retainCoreKeys()
        override fun nodes() = RayNodes.list()
        override fun node(nodeId: String) = RayNodes.get(nodeId)
        override fun forHost(address: String?) = RayNodes.forHost(address)
        override fun ownerOf(profileId: Long) = RayNodes.ownerOf(profileId)
        override fun forRelayHost(address: String?) = RayNodes.forRelayHost(address)
        override fun relayOfRow(profileId: Long) = RayNodes.relayOfRow(profileId)
        override fun names(node: RayNode) = RayNodes.names(node)
        override fun prepareKnock(node: RayNode, relay: RayRelay?) = RayNodes.prepareKnock(node, relay)
        override fun prepareDeviceKey(node: RayNode) = RayNodes.prepareDeviceKey(node)
        override fun dnsAuthKeyHex(node: RayNode, serviceId: Int) = RayNodes.dnsAuthKeyHex(node, serviceId)
        override fun brutalRates(node: RayNode) = RayBrutal.ratesFor(node, RayChannel.network())
        override fun noteBrutal(node: RayNode, rates: BrutalRates?) = RayBrutal.noteApplied(node, rates)
        override fun yggViaActive(nodeId: String) = RayChannel.yggViaActive(nodeId, RayChannel.network())
        override fun yggViaPeers(nodeId: String) = RayChannel.yggViaPeers(nodeId, RayChannel.network())
        override fun noteYggVia(nodeId: String, on: Boolean) = RayChannel.noteYggVia(nodeId, on)
        override fun clearDnsRunning() = RayDnsResolvers.clearRunning()

        // dnszones-v1: one zone at a time, the main one unless this network's pick or failures moved it, decided on
        // the tunnel as the profile has it (RayDnsResolvers.forConfig)
        override fun dnsTunnel(tunnel: DnsTunnel, owner: RayNode?, notes: Boolean): DnsTunnel {
            if (notes) RayDnsResolvers.noteRunningZone(owner, tunnel)
            return RayDnsResolvers.forConfig(tunnel, owner) {
                RayDnsResolvers.resolverFor(owner, tunnel.engine).also { resolver ->
                    if (notes) {
                        RayDnsResolvers.noteRunning(owner, tunnel.engine, resolver)
                        // no pick on this network yet: one now, in the background
                        RayDnsResolvers.pickSoon(owner, tunnel)
                    }
                }
            }
        }

        override fun lanOf(node: RayNode) = RayLanNet.of(node)
        override fun lanNeighbours(node: RayNode, now: Long) = RayLanNet.neighbours(node, now)
        override fun lanRow(node: RayNode) = RayLanNet.row(node)
        override fun lanNames(nodes: List<RayNode>, now: Long) = RayLanNet.names(nodes, now)
        override fun lanDirectPassword(node: RayNode, now: Long) = RayLanNet.directPassword(node, now)
        override fun noteLan(endpoints: Boolean, nodes: List<RayNode>, now: Long, direct: Boolean, nextSwitch: Long?) {
            RayLanNet.running = endpoints
            RayLanNet.noteRunning(nodes, now)
            RayLanDirect.noteRunning(direct, nextSwitch)
        }
    }

    /** No node imported: nothing measured, nothing noted; a DNS tunnel as its profile has it. */
    object None : ConfigRay {
        override fun retainCoreKeys() {}
        override fun nodes() = emptyList<RayNode>()
        override fun node(nodeId: String): RayNode? = null
        override fun forHost(address: String?): RayNode? = null
        override fun ownerOf(profileId: Long): Pair<RayNode, String>? = null
        override fun forRelayHost(address: String?): Pair<RayNode, RayRelay>? = null
        override fun relayOfRow(profileId: Long): Pair<RayNode, RayRelay>? = null
        override fun names(node: RayNode) = emptySet<String>()
        override fun prepareKnock(node: RayNode, relay: RayRelay?) = throw IllegalStateException("no raycontrol node")
        override fun prepareDeviceKey(node: RayNode) {}
        override fun dnsAuthKeyHex(node: RayNode, serviceId: Int) = throw IllegalStateException("no raycontrol node")
        override fun brutalRates(node: RayNode): BrutalRates? = null
        override fun noteBrutal(node: RayNode, rates: BrutalRates?) {}
        override fun yggViaActive(nodeId: String) = false
        override fun yggViaPeers(nodeId: String): List<String>? = null
        override fun noteYggVia(nodeId: String, on: Boolean) {}
        override fun clearDnsRunning() {}
        override fun dnsTunnel(tunnel: DnsTunnel, owner: RayNode?, notes: Boolean) = tunnel
        override fun lanOf(node: RayNode): RayLan? = null
        override fun lanNeighbours(node: RayNode, now: Long) = emptyList<String>()
        override fun lanRow(node: RayNode): ProxyEntity? = null
        override fun lanNames(nodes: List<RayNode>, now: Long) = emptyMap<String, String>()
        override fun lanDirectPassword(node: RayNode, now: Long): String? = null
        override fun noteLan(endpoints: Boolean, nodes: List<RayNode>, now: Long, direct: Boolean, nextSwitch: Long?) {}
    }
}

/** What a build tells the user (a toast on the phone). */
sealed class ConfigWarning {
    /** a rule names apps, which only the VPN mode tells apart */
    data class RuleNeedsVpn(val rule: String) : ConfigWarning()

    /** a rule names a profile that is not there */
    data class NoSuchOutbound(val rule: String) : ConfigWarning()
}

/** What only the device knows: free ports, apps, Private DNS, plugins, the core's own files, the clock. */
interface ConfigDevice {
    /** a free local port */
    fun port(): Int

    /** the uid of an installed app */
    fun uid(packageName: String): Int?

    /** the host of strict Private DNS, if set */
    fun strictPrivateDnsHost(): String?

    /** whether the plugin is the app's own build (Matsuri's), and whether an outside one is installed */
    fun pluginBundled(id: String): Boolean
    fun pluginExternal(id: String): Boolean

    /** whether libcore's protect server listens (it runs with the main instance) */
    fun protectPathReady(): Boolean

    /** a one-time Yggdrasil key */
    fun yggdrasilKey(): String

    /** whether the running core has a mesh node with this device key */
    fun yggKeyRunning(seed: String): Boolean

    /** the time, in seconds */
    fun now(): Long
    fun warn(warning: ConfigWarning)

    /** the phone */
    object App : ConfigDevice {
        override fun port() = mkPort()
        override fun uid(packageName: String): Int? {
            PackageCache.awaitLoadSync()
            return PackageCache[packageName]
        }

        override fun strictPrivateDnsHost() = PrivateDns.strictHost()
        override fun pluginBundled(id: String) = Plugins.isUsingMatsuriExe(id)
        override fun pluginExternal(id: String) = Plugins.getPluginExternal(id) != null
        override fun protectPathReady() = File(SagerNet.application.noBackupFilesDir, "protect_path").exists()
        override fun yggdrasilKey(): String = Libcore.yggdrasilGenerateKey()
        override fun yggKeyRunning(seed: String) = io.niko.fmt.yggKeyRunning(seed)
        override fun now() = System.currentTimeMillis() / 1000
        override fun warn(warning: ConfigWarning) {
            val (text, length) = when (warning) {
                is ConfigWarning.RuleNeedsVpn ->
                    SagerNet.application.getString(R.string.route_need_vpn, warning.rule) to Toast.LENGTH_SHORT

                is ConfigWarning.NoSuchOutbound ->
                    "Warning: " + warning.rule + ": A non-existent outbound was specified." to Toast.LENGTH_LONG
            }
            Toast.makeText(SagerNet.application, text, length).show()
        }
    }
}
