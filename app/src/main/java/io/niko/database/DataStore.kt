package io.niko.database

import android.os.Binder
import androidx.preference.PreferenceDataStore
import io.niko.CONNECTION_TEST_URL
import io.niko.GroupType
import io.niko.IPv6Mode
import io.niko.Key
import io.niko.TunImplementation
import io.niko.bg.BaseService
import io.niko.bg.VpnService
import io.niko.database.preference.KeyValuePair
import io.niko.database.preference.OnPreferenceDataStoreChangeListener
import io.niko.database.preference.PublicDatabase
import io.niko.database.preference.RoomPreferenceDataStore
import io.niko.ktx.boolean
import io.niko.ktx.int
import io.niko.ktx.long
import io.niko.ktx.parsePort
import io.niko.ktx.string
import io.niko.ktx.stringToInt
import io.niko.ktx.stringToIntIfExists
import io.niko.TempDatabase
import libcore.Libcore
import java.security.SecureRandom

object DataStore : OnPreferenceDataStoreChangeListener {

    fun migrateLegacySettings() {
        // sniff override destination was removed in sing-box 1.13
        if (trafficSniffing > 1) trafficSniffing = 1
    }

    // share service state in main & bg process
    @Volatile
    var serviceState = BaseService.State.Idle

    val configurationStore = RoomPreferenceDataStore(PublicDatabase.kvPairDao)
    val profileCacheStore = RoomPreferenceDataStore(TempDatabase.profileCacheDao)

    // last used, but may not be running
    var currentProfile by configurationStore.long(Key.PROFILE_CURRENT)

    var selectedProxy by configurationStore.long(Key.PROFILE_ID)
    var selectedGroup by configurationStore.long(Key.PROFILE_GROUP) { currentGroupId() } // "ungrouped" group id = 1

    // only in bg process
    var vpnService: VpnService? = null
    var baseService: BaseService.Interface? = null

    // main

    var runningTest = false

    fun currentGroupId(): Long {
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) return currentSelected
        val groups = SagerDatabase.groupDao.allGroups()
        if (groups.isNotEmpty()) {
            val groupId = groups[0].id
            selectedGroup = groupId
            return groupId
        }
        val groupId = SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
        selectedGroup = groupId
        return groupId
    }

    fun currentGroup(): ProxyGroup {
        var group: ProxyGroup? = null
        val currentSelected = configurationStore.getLong(Key.PROFILE_GROUP, -1)
        if (currentSelected > 0L) {
            group = SagerDatabase.groupDao.getById(currentSelected)
        }
        if (group != null) return group
        val groups = SagerDatabase.groupDao.allGroups()
        if (groups.isEmpty()) {
            group = ProxyGroup(ungrouped = true).apply {
                id = SagerDatabase.groupDao.createGroup(this)
            }
        } else {
            group = groups[0]
        }
        selectedGroup = group.id
        return group
    }

    fun selectedGroupForImport(): Long {
        val current = currentGroup()
        if (current.type == GroupType.BASIC) return current.id
        val groups = SagerDatabase.groupDao.allGroups()
        return groups.find { it.type == GroupType.BASIC }!!.id
    }

    var appTLSVersion by configurationStore.string(Key.APP_TLS_VERSION)
    var enableClashAPI by configurationStore.boolean(Key.ENABLE_CLASH_API)
    var yggdrasilEnabled by configurationStore.boolean(Key.YGGDRASIL_ENABLED)
    var yggdrasilPeers by configurationStore.string(Key.YGGDRASIL_PEERS)
    var yggdrasilAllowInbound by configurationStore.boolean(Key.YGGDRASIL_ALLOW_INBOUND)
    var yggdrasilPeersViaProxy by configurationStore.boolean(Key.YGGDRASIL_PEERS_VIA_PROXY)
    var yggdrasilTlsChrome by configurationStore.boolean(Key.YGGDRASIL_TLS_CHROME) { true }
    // raycontrol links leave the DNS tunnel resolver to the client: these defaults fill it (Yandex, reachable in RU whitelists)
    var raycontrolDnsttResolver by configurationStore.string(Key.RAYCONTROL_DNSTT_RESOLVER) { "https://common.dot.dns.yandex.net/dns-query" }
    var raycontrolMasterDnsResolvers by configurationStore.string(Key.RAYCONTROL_MASTERDNS_RESOLVERS) { "77.88.8.8\n77.88.8.1" }
    // a node's profile that stops working on this network gives way to its best working one (RayChannel.failover)
    var raycontrolAutoSwitch by configurationStore.boolean(Key.RAYCONTROL_AUTO_SWITCH) { true }

    // the node identity of this install, made on first use and not part of backups
    val yggdrasilPrivateKey: String
        get() = persistentToken(Key.YGGDRASIL_PRIVATE_KEY) { Libcore.yggdrasilGenerateKey() }
    var showBottomBar by configurationStore.boolean(Key.SHOW_BOTTOM_BAR)

    var allowInsecureOnRequest by configurationStore.boolean(Key.ALLOW_INSECURE_ON_REQUEST)
    var networkChangeResetConnections by configurationStore.boolean(Key.NETWORK_CHANGE_RESET_CONNECTIONS) { true }
    var wakeResetConnections by configurationStore.boolean(Key.WAKE_RESET_CONNECTIONS)

    //

    var isExpert by configurationStore.boolean(Key.APP_EXPERT)
    var appTheme by configurationStore.int(Key.APP_THEME)
    var nightTheme by configurationStore.stringToInt(Key.NIGHT_THEME)
    var serviceMode by configurationStore.string(Key.SERVICE_MODE) { Key.MODE_VPN }

    var trafficSniffing by configurationStore.stringToInt(Key.TRAFFIC_SNIFFING) { 1 }
    var resolveDestination by configurationStore.boolean(Key.RESOLVE_DESTINATION)

    var mtu by configurationStore.stringToInt(Key.MTU) { 9000 }

    var bypassLan by configurationStore.boolean(Key.BYPASS_LAN)
    var bypassLanInCore by configurationStore.boolean(Key.BYPASS_LAN_IN_CORE)
    var tlsFragmentBypass by configurationStore.stringToInt(Key.TLS_FRAGMENT_BYPASS)
    var ruleSetUpdateHours by configurationStore.stringToInt(Key.RULE_SET_UPDATE_HOURS) { 24 }

    var allowAccess by configurationStore.boolean(Key.ALLOW_ACCESS)
    var speedInterval by configurationStore.stringToInt(Key.SPEED_INTERVAL)
    var showGroupInNotification by configurationStore.boolean("showGroupInNotification")

    var globalCustomConfig by configurationStore.string(Key.GLOBAL_CUSTOM_CONFIG) { "" }

    var remoteDns by configurationStore.string(Key.REMOTE_DNS) { "https://dns.google/dns-query" }
    var directDns by configurationStore.string(Key.DIRECT_DNS) { "https://223.5.5.5/dns-query" }
    var enableDnsRouting by configurationStore.boolean(Key.ENABLE_DNS_ROUTING) { true }
    var enableFakeDns by configurationStore.boolean(Key.ENABLE_FAKEDNS) { true }
    var dnsOptimisticCache by configurationStore.boolean(Key.DNS_OPTIMISTIC_CACHE)

    var rulesProvider by configurationStore.stringToInt(Key.RULES_PROVIDER)
    var logLevel by configurationStore.stringToInt(Key.LOG_LEVEL)
    var logBufSize by configurationStore.int(Key.LOG_BUF_SIZE) { 0 }
    var acquireWakeLock by configurationStore.boolean(Key.ACQUIRE_WAKE_LOCK)

    // hopefully hashCode = mHandle doesn't change, currently this is true from KitKat to Nougat
    private val userIndex by lazy { Binder.getCallingUserHandle().hashCode() }
    var mixedPort: Int
        get() = getLocalPort(Key.MIXED_PORT, 2080)
        set(value) = saveLocalPort(Key.MIXED_PORT, value)
    var mixedUsername by configurationStore.string(Key.MIXED_USERNAME)
    var mixedPassword by configurationStore.string(Key.MIXED_PASSWORD)
    var localProxyNoAuthInVpn by configurationStore.boolean(Key.LOCAL_PROXY_NO_AUTH_IN_VPN)

    // credentials the app itself uses for the local proxy, never shown to the user
    val localProxyPassword get() = persistentToken("localProxyPassword")

    fun initGlobal() {
        if (configurationStore.getString(Key.MIXED_PORT) == null) {
            mixedPort = mixedPort
        }
        localProxyPassword // generate once, before the service and the UI race for it
    }


    private fun getLocalPort(key: String, default: Int): Int {
        return parsePort(configurationStore.getString(key), default + userIndex)
    }

    private fun saveLocalPort(key: String, value: Int) {
        configurationStore.putString(key, "$value")
    }

    var ipv6Mode by configurationStore.stringToInt(Key.IPV6_MODE) { IPv6Mode.DISABLE }

    var meteredNetwork by configurationStore.boolean(Key.METERED_NETWORK)
    var proxyApps by configurationStore.boolean(Key.PROXY_APPS)
    var bypass by configurationStore.boolean(Key.BYPASS_MODE) { true }
    var individual by configurationStore.string(Key.INDIVIDUAL)
    var showDirectSpeed by configurationStore.boolean(Key.SHOW_DIRECT_SPEED) { true }

    val persistAcrossReboot by configurationStore.boolean(Key.PERSIST_ACROSS_REBOOT) { false }

    var appendHttpProxy by configurationStore.boolean(Key.APPEND_HTTP_PROXY)
    var connectionTestURL by configurationStore.string(Key.CONNECTION_TEST_URL) { CONNECTION_TEST_URL }
    var connectionTestConcurrent by configurationStore.int("connectionTestConcurrent") { 5 }
    var alwaysShowAddress by configurationStore.boolean(Key.ALWAYS_SHOW_ADDRESS)

    var tunImplementation by configurationStore.stringToInt(Key.TUN_IMPLEMENTATION) { TunImplementation.GVISOR }
    var profileTrafficStatistics by configurationStore.boolean(Key.PROFILE_TRAFFIC_STATISTICS) { true }

    var yacdURL by configurationStore.string("yacdURL") { "http://127.0.0.1:9090/ui" }

    // every app on the device can reach 127.0.0.1, so the Clash API and exported shortcuts need a token
    val clashApiSecret get() = persistentToken("clashApiSecret")
    /** Random secret the per-provider x-hwid is derived from (never the Android ID). */
    val subscriptionHwid get() = persistentToken("subscriptionHwid")
    val shortcutToken get() = persistentToken("shortcutToken")

    /** Secrets of this installation: not exported, not restored from another device's backup. */
    val installSecretKeys =
        setOf("localProxyPassword", "clashApiSecret", "shortcutToken", "subscriptionHwid", Key.YGGDRASIL_PRIVATE_KEY, Key.RAYCONTROL_NODES,
            // what this device measured on its networks: of no use on another one
            Key.RAYCONTROL_CHANNEL,
            // the service's state of this installation
            Key.RAYCONTROL_YGG_RUNNING)

    /** Settings whose values must not appear in logs or crash reports. */
    fun isSensitiveKey(key: String) = key in installSecretKeys || key in secretValueKeys ||
        Regex("password|secret|token|psk|private", RegexOption.IGNORE_CASE).containsMatchIn(key)

    // secrets inside the value: outbound credentials in custom JSON, tokens in DoH URLs
    private val secretValueKeys = setOf(Key.GLOBAL_CUSTOM_CONFIG, Key.REMOTE_DNS, Key.DIRECT_DNS)

    // main and :bg may both start without a token: insert-or-ignore lets the first one win for both
    private fun persistentToken(
        key: String,
        generate: () -> String = { ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) } },
    ): String {
        configurationStore.getString(key)?.takeIf { it.isNotBlank() }?.let { return it }
        val token = generate()
        PublicDatabase.kvPairDao.putIfAbsent(KeyValuePair(key).put(token))
        return configurationStore.getString(key)?.takeIf { it.isNotBlank() } ?: token
    }

    // protocol

    var globalAllowInsecure by configurationStore.boolean(Key.GLOBAL_ALLOW_INSECURE) { false }

    // old cache, DO NOT ADD

    var dirty by profileCacheStore.boolean(Key.PROFILE_DIRTY)
    var editingId by profileCacheStore.long(Key.PROFILE_ID)
    var editingGroup by profileCacheStore.long(Key.PROFILE_GROUP)
    var profileName by profileCacheStore.string(Key.PROFILE_NAME)
    var serverAddress by profileCacheStore.string(Key.SERVER_ADDRESS)
    var serverPort by profileCacheStore.stringToInt(Key.SERVER_PORT)
    var serverPorts by profileCacheStore.string("serverPorts")
    var serverUsername by profileCacheStore.string(Key.SERVER_USERNAME)
    var serverPassword by profileCacheStore.string(Key.SERVER_PASSWORD)
    var serverPassword1 by profileCacheStore.string(Key.SERVER_PASSWORD1)
    var serverMethod by profileCacheStore.string(Key.SERVER_METHOD)

    var sharedStorage by profileCacheStore.string("sharedStorage")

    var serverProtocol by profileCacheStore.string(Key.SERVER_PROTOCOL)
    var serverObfs by profileCacheStore.string(Key.SERVER_OBFS)

    var serverNetwork by profileCacheStore.string(Key.SERVER_NETWORK)
    var serverHost by profileCacheStore.string(Key.SERVER_HOST)
    var serverPath by profileCacheStore.string(Key.SERVER_PATH)
    var serverSNI by profileCacheStore.string(Key.SERVER_SNI)
    var serverEncryption by profileCacheStore.string(Key.SERVER_ENCRYPTION)
    var serverALPN by profileCacheStore.string(Key.SERVER_ALPN)
    var serverCertificates by profileCacheStore.string(Key.SERVER_CERTIFICATES)
    var serverMTU by profileCacheStore.stringToInt(Key.SERVER_MTU)
    var serverHeaders by profileCacheStore.string(Key.SERVER_HEADERS)
    var serverAllowInsecure by profileCacheStore.boolean(Key.SERVER_ALLOW_INSECURE)

    var serverAuthType by profileCacheStore.stringToInt(Key.SERVER_AUTH_TYPE)
    var serverUploadSpeed by profileCacheStore.stringToInt(Key.SERVER_UPLOAD_SPEED)
    var serverDownloadSpeed by profileCacheStore.stringToInt(Key.SERVER_DOWNLOAD_SPEED)
    var serverStreamReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_STREAM_RECEIVE_WINDOW)
    var serverConnectionReceiveWindow by profileCacheStore.stringToIntIfExists(Key.SERVER_CONNECTION_RECEIVE_WINDOW)
    var serverDisableMtuDiscovery by profileCacheStore.boolean(Key.SERVER_DISABLE_MTU_DISCOVERY)
    var serverHopInterval by profileCacheStore.stringToInt(Key.SERVER_HOP_INTERVAL) { 10 }
    var serverHopIntervalMax by profileCacheStore.stringToInt(Key.SERVER_HOP_INTERVAL_MAX)

    var protocolVersion by profileCacheStore.stringToInt(Key.PROTOCOL_VERSION) { 2 } // default is SOCKS5

    var serverProtocolInt by profileCacheStore.stringToInt(Key.SERVER_PROTOCOL)
    var serverPrivateKey by profileCacheStore.string(Key.SERVER_PRIVATE_KEY)
    var serverInsecureConcurrency by profileCacheStore.stringToInt(Key.SERVER_INSECURE_CONCURRENCY)

    var serverUDPRelayMode by profileCacheStore.string(Key.SERVER_UDP_RELAY_MODE)
    var serverCongestionController by profileCacheStore.string(Key.SERVER_CONGESTION_CONTROLLER)
    var serverDisableSNI by profileCacheStore.boolean(Key.SERVER_DISABLE_SNI)
    var serverReduceRTT by profileCacheStore.boolean(Key.SERVER_REDUCE_RTT)

    var routeName by profileCacheStore.string(Key.ROUTE_NAME)
    var routeDomain by profileCacheStore.string(Key.ROUTE_DOMAIN)
    var routeIP by profileCacheStore.string(Key.ROUTE_IP)
    var routePort by profileCacheStore.string(Key.ROUTE_PORT)
    var routeSourcePort by profileCacheStore.string(Key.ROUTE_SOURCE_PORT)
    var routeNetwork by profileCacheStore.string(Key.ROUTE_NETWORK)
    var routeSource by profileCacheStore.string(Key.ROUTE_SOURCE)
    var routeProtocol by profileCacheStore.string(Key.ROUTE_PROTOCOL)
    var routeOutbound by profileCacheStore.stringToInt(Key.ROUTE_OUTBOUND)
    var routeOutboundRule by profileCacheStore.long(Key.ROUTE_OUTBOUND + "Long")
    var routePackages by profileCacheStore.string(Key.ROUTE_PACKAGES)

    var frontProxy by profileCacheStore.long(Key.GROUP_FRONT_PROXY + "Long")
    var landingProxy by profileCacheStore.long(Key.GROUP_LANDING_PROXY + "Long")
    var frontProxyTmp by profileCacheStore.stringToInt(Key.GROUP_FRONT_PROXY)
    var landingProxyTmp by profileCacheStore.stringToInt(Key.GROUP_LANDING_PROXY)

    var serverConfig by profileCacheStore.string(Key.SERVER_CONFIG)
    var serverCustom by profileCacheStore.string(Key.SERVER_CUSTOM)
    var serverCustomOutbound by profileCacheStore.string(Key.SERVER_CUSTOM_OUTBOUND)
    var serverKnockSequence by profileCacheStore.string(Key.SERVER_KNOCK_SEQUENCE)
    var serverKnockInterval by profileCacheStore.stringToInt(Key.SERVER_KNOCK_INTERVAL) { 200 }
    var serverKnockTtl by profileCacheStore.stringToInt(Key.SERVER_KNOCK_TTL) { 10 }
    var serverKnockSilence by profileCacheStore.stringToInt(Key.SERVER_KNOCK_SILENCE) { 15 }
    var serverTlsFragment by profileCacheStore.stringToInt(Key.SERVER_TLS_FRAGMENT)
    var serverCertPins by profileCacheStore.string(Key.SERVER_CERT_PINS)
    var serverDnsTunnel by profileCacheStore.string(Key.SERVER_DNS_TUNNEL)
    var serverDnsTunnelResolver by profileCacheStore.string(Key.SERVER_DNS_TUNNEL_RESOLVER)
    var serverDnsTunnelDomain by profileCacheStore.string(Key.SERVER_DNS_TUNNEL_DOMAIN)
    var serverDnsTunnelKey by profileCacheStore.string(Key.SERVER_DNS_TUNNEL_KEY)
    var serverDnsTunnelMethod by profileCacheStore.stringToInt(Key.SERVER_DNS_TUNNEL_METHOD) { 2 }
    var serverUdpPrelude by profileCacheStore.stringToInt(Key.SERVER_UDP_PRELUDE) { 0 }
    var serverUdpPreludeSni by profileCacheStore.string(Key.SERVER_UDP_PRELUDE_SNI)
    var serverUdpPreludeTtl by profileCacheStore.stringToInt(Key.SERVER_UDP_PRELUDE_TTL) { 0 }
    var serverUdpReorderInitial by profileCacheStore.boolean(Key.SERVER_UDP_REORDER_INITIAL)
    var serverDnsTunnelFixedUpstream by profileCacheStore.boolean(Key.SERVER_DNS_TUNNEL_FIXED_UPSTREAM)

    var groupName by profileCacheStore.string(Key.GROUP_NAME)
    var groupType by profileCacheStore.stringToInt(Key.GROUP_TYPE)
    var groupOrder by profileCacheStore.stringToInt(Key.GROUP_ORDER)
    var groupIsSelector by profileCacheStore.boolean(Key.GROUP_IS_SELECTOR)
    var groupAutoSelect by profileCacheStore.boolean(Key.GROUP_AUTO_SELECT)

    var subscriptionLink by profileCacheStore.string(Key.SUBSCRIPTION_LINK)
    var subscriptionForceResolve by profileCacheStore.boolean(Key.SUBSCRIPTION_FORCE_RESOLVE)
    var subscriptionDeduplication by profileCacheStore.boolean(Key.SUBSCRIPTION_DEDUPLICATION)
    var subscriptionUpdateWhenConnectedOnly by profileCacheStore.boolean(Key.SUBSCRIPTION_UPDATE_WHEN_CONNECTED_ONLY)
    var subscriptionUserAgent by profileCacheStore.string(Key.SUBSCRIPTION_USER_AGENT)
    var subscriptionSendHwid by profileCacheStore.boolean(Key.SUBSCRIPTION_SEND_HWID)
    var subscriptionAutoUpdate by profileCacheStore.boolean(Key.SUBSCRIPTION_AUTO_UPDATE)
    var subscriptionAutoUpdateDelay by profileCacheStore.stringToInt(Key.SUBSCRIPTION_AUTO_UPDATE_DELAY) { 360 }

    var rulesFirstCreate by profileCacheStore.boolean("rulesFirstCreate")

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
    }
}