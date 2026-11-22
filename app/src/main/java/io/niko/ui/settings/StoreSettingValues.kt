package io.niko.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.preference.PreferenceDataStore
import io.niko.DEFAULT_REMOTE_DNS
import io.niko.Key
import io.niko.fmt.DpiDirect

/**
 * What a changed setting asks of a running service: nothing, a reload of its config, or a restart of the app (the
 * log's level and buffer are the core's from its start).
 */
enum class SettingEffect { NONE, RELOAD, RESTART }

/** The settings' keys the screens change, their defaults where the store has none, and what changing them asks. */
object SettingKeys {

    const val CONNECTION_TEST_CONCURRENT = "connectionTestConcurrent"
    const val SHOW_GROUP_IN_NOTIFICATION = "showGroupInNotification"
    const val STRATEGY_REMOTE = "domain_strategy_for_remote"
    const val STRATEGY_DIRECT = "domain_strategy_for_direct"
    const val STRATEGY_SERVER = "domain_strategy_for_server"

    /**
     * The defaults the old screen's preferences wrote on its first showing (global_preferences.xml): a key the app
     * reads with another default of its own got that one as soon as the settings were opened. [StoreSettingValues]
     * writes the missing ones the same way, so the app behaves as before.
     */
    val defaults: Map<String, Any> = mapOf(
        Key.PERSIST_ACROSS_REBOOT to false,
        Key.NIGHT_THEME to "0",
        Key.SERVICE_MODE to Key.MODE_VPN,
        Key.TUN_IMPLEMENTATION to "0",
        Key.MTU to "9000",
        Key.SPEED_INTERVAL to "1000",
        Key.PROFILE_TRAFFIC_STATISTICS to true,
        Key.SHOW_DIRECT_SPEED to true,
        Key.LOG_LEVEL to "0",
        Key.HY2_PROBE to "0",
        Key.DPI_DIRECT to "0",
        Key.DPI_DIRECT_PRESET to DpiDirect.DEFAULT_PRESET,
        Key.RULE_SET_UPDATE_HOURS to "24",
        Key.TRAFFIC_SNIFFING to "1",
        Key.IPV6_MODE to "0",
        Key.RULES_PROVIDER to "0",
        Key.REMOTE_DNS to DEFAULT_REMOTE_DNS,
        STRATEGY_REMOTE to "auto",
        Key.DIRECT_DNS to "local",
        STRATEGY_DIRECT to "auto",
        STRATEGY_SERVER to "auto",
        Key.ENABLE_DNS_ROUTING to true,
        Key.ENABLE_FAKEDNS to true,
        Key.YGGDRASIL_TLS_CHROME to true,
        Key.RAYCONTROL_AUTO_SWITCH to true,
        Key.RAYCONTROL_DNSTT_RESOLVER to "auto",
        Key.RAYCONTROL_MASTERDNS_RESOLVERS to "auto",
        Key.CONNECTION_TEST_URL to "http://cp.cloudflare.com/",
        Key.NETWORK_CHANGE_RESET_CONNECTIONS to true,
        Key.APP_TLS_VERSION to "1.2",
        Key.ROOT_STORE to "chrome",
    )

    /** Kept as numbers in the store, not as text. */
    val numbers = setOf(CONNECTION_TEST_CONCURRENT, Key.LOG_BUF_SIZE)

    /** Their default where the store has none (DataStore's). */
    private val numberDefaults = mapOf(CONNECTION_TEST_CONCURRENT to 5, Key.LOG_BUF_SIZE to 0)
    fun numberDefault(key: String) = numberDefaults[key] ?: 0

    private val restart = setOf(Key.LOG_LEVEL, Key.LOG_BUF_SIZE, Key.HY2_LOG_CONNECTIONS)

    // what the core's config is built of; the rest (the view, the node's options read when they are used, the
    // check's address) takes effect without it, as the old screen's preferences did
    private val reload = setOf(
        Key.MIXED_PORT, Key.MIXED_USERNAME, Key.MIXED_PASSWORD, Key.LOCAL_PROXY_NO_AUTH_IN_VPN, Key.APPEND_HTTP_PROXY,
        Key.ALLOW_ACCESS, Key.METERED_NETWORK, Key.ACQUIRE_WAKE_LOCK, Key.SHOW_DIRECT_SPEED, Key.SPEED_INTERVAL,
        Key.TRAFFIC_SNIFFING, Key.RESOLVE_DESTINATION, Key.BYPASS_LAN, Key.BYPASS_LAN_IN_CORE,
        Key.REFUSE_QUIC_OVER_STREAMS, Key.HONEST_CONNECT, Key.ICMP_THROUGH_NODE, Key.ICMP_NO_LOCAL_REPLY,
        Key.NAIVE_QUIC_PHONE_ROOTS, Key.ROOT_STORE, Key.DPI_DIRECT, Key.DPI_DIRECT_PRESET,
        Key.DPI_DIRECT_CUSTOM, Key.RULE_SET_UPDATE_HOURS,
        Key.RULES_PROVIDER, Key.MTU, Key.TUN_IMPLEMENTATION, Key.IPV6_MODE, Key.ALLOW_TETHERING, Key.ENABLE_FAKEDNS,
        Key.DNS_OPTIMISTIC_CACHE, Key.REMOTE_DNS, Key.DIRECT_DNS, Key.ENABLE_DNS_ROUTING, STRATEGY_REMOTE,
        STRATEGY_DIRECT, STRATEGY_SERVER, Key.GLOBAL_CUSTOM_CONFIG, Key.ENABLE_CLASH_API, Key.YGGDRASIL_ENABLED,
        Key.YGGDRASIL_PEERS, Key.YGGDRASIL_PEERS_VIA_PROXY, Key.YGGDRASIL_TLS_CHROME, Key.YGGDRASIL_ALLOW_INBOUND,
        Key.YGGDRASIL_INBOUND_PORTS, Key.HY2_PROBE,
    )

    fun effect(key: String) = when (key) {
        in restart -> SettingEffect.RESTART
        in reload -> SettingEffect.RELOAD
        else -> SettingEffect.NONE
    }

    /**
     * A typed value as the store may keep it, or null where it may not: a port 1–65535, an MTU 1280–65535, at once
     * 1–64, the log's size in KB (0: the core's 512), a DNS server not empty, a ByeDPI string the app takes or none;
     * the rest as typed, trimmed.
     */
    fun clean(key: String, value: String): String? {
        val text = value.trim()
        fun number(range: IntRange) = text.toIntOrNull()?.takeIf { it in range }?.toString()
        return when (key) {
            Key.MIXED_PORT -> number(1..65535)
            Key.MTU -> number(1280..65535)
            CONNECTION_TEST_CONCURRENT -> number(1..64)
            Key.LOG_BUF_SIZE -> if (text.isEmpty()) "0" else number(0..65536)
            Key.REMOTE_DNS, Key.DIRECT_DNS, Key.CONNECTION_TEST_URL -> text.ifEmpty { null }
            Key.DPI_DIRECT_CUSTOM -> text.takeIf { it.isEmpty() || DpiDirect.parseByeDpi(it) is DpiDirect.Parsed.Ok }
            else -> text
        }
    }
}

/**
 * The settings' values in [store] (DataStore.configurationStore in the app). Switches and choices are written at once;
 * text is held while it is typed and written by [commit] (leaving the page), so a service is reloaded once, not on
 * every key. [onChange] hears each key a switch or a choice changes, at once. The screens read the values while they
 * compose: [version] makes a write redraw them.
 */
class StoreSettingValues(private val store: PreferenceDataStore, var onChange: (String) -> Unit = {}) : SettingValues {

    private val version = mutableIntStateOf(0)
    private val typed = mutableStateMapOf<String, String>()

    /** Writes the defaults of [SettingKeys.defaults] the store has none for (see there). */
    fun writeMissingDefaults(has: (String) -> Boolean) {
        for ((key, value) in SettingKeys.defaults) {
            if (has(key)) continue
            when (value) {
                is Boolean -> store.putBoolean(key, value)
                else -> store.putString(key, value.toString())
            }
        }
    }

    override fun flag(key: String): Boolean {
        version.intValue
        return store.getBoolean(key, SettingKeys.defaults[key] as? Boolean ?: false)
    }

    override fun setFlag(key: String, value: Boolean) {
        if (flag(key) == value) return
        store.putBoolean(key, value)
        changed(key)
    }

    private fun stored(key: String): String {
        version.intValue
        return if (key in SettingKeys.numbers) {
            store.getInt(key, SettingKeys.numberDefault(key)).toString()
        } else {
            store.getString(key, null) ?: SettingKeys.defaults[key] as? String ?: ""
        }
    }

    override fun text(key: String) = typed[key] ?: stored(key)

    override fun setText(key: String, value: String) {
        typed[key] = value
    }

    override fun setChoice(key: String, value: String) {
        typed.remove(key)
        if (stored(key) == value) return
        write(key, value)
        changed(key)
    }

    private fun write(key: String, value: String) {
        if (key in SettingKeys.numbers) store.putInt(key, value.toInt()) else store.putString(key, value)
    }

    private fun changed(key: String) {
        version.intValue++
        onChange(key)
    }

    /**
     * Writes the text typed since (what the store may not keep, as [SettingKeys.clean] says, is dropped and the stored
     * value shows again) and gives the keys it changed.
     */
    fun commit(): List<String> {
        val written = mutableListOf<String>()
        for ((key, value) in typed.toMap()) {
            val clean = SettingKeys.clean(key, value)
            if (clean != null && clean != stored(key)) {
                write(key, clean)
                written += key
            }
        }
        typed.clear()
        version.intValue++
        return written
    }
}
