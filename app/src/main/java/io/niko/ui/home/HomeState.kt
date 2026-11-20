package io.niko.ui.home

import io.niko.bg.BaseService

/** The two views of the main screen (D-NAV-01): kept in DataStore.homeView by these names. */
enum class HomeView(val key: String) {
    Simple("simple"), Advanced("advanced");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Simple
    }
}

/** The profile in use, as the main screen shows it (D-MAIN-01, D-MAIN-11…13). */
data class HomeProfile(
    /** the profile's name */
    val name: String,
    /** its kind: naive, Hysteria2, VLESS… */
    val type: String,
    /** the group it belongs to: the node's or the subscription's name, null for the ungrouped */
    val group: String?,
    /** the group's subscription: its expiry and traffic in one line, null when it sent none */
    val usage: String?,
    /** the last ping in ms, null when not measured; [unreachable] when the last test failed */
    val pingMs: Int?,
    val unreachable: Boolean,
)

/** How the phone's traffic goes: the whole of it through the VPN, or the local proxy only (D-NAV-02). */
data class HomeMode(val vpn: Boolean, val proxyPort: Int)

/** What the rules do, in one word for the tile and the row (D-MAIN-05). */
enum class HomeRoute { Russia, Own, None }

/** Which apps the VPN takes: all, all but [count], or only [count] (M-RULES-01). */
data class HomeApps(val perApp: Boolean, val bypass: Boolean, val count: Int)

/** Everything the main screen draws: the service's state and the data it reads from the database. */
data class HomeState(
    val service: BaseService.State = BaseService.State.Idle,
    /** the reason the last start failed (D-MAIN-16), null when it did not */
    val error: String? = null,
    /** when the core came up (System.currentTimeMillis), 0 when it is not up */
    val connectedSince: Long = 0,
    /** bytes per second through the proxy */
    val upRate: Long = 0,
    val downRate: Long = 0,
    val profile: HomeProfile? = null,
    val mode: HomeMode = HomeMode(vpn = true, proxyPort = 2080),
    val route: HomeRoute = HomeRoute.None,
    val apps: HomeApps = HomeApps(perApp = false, bypass = true, count = 0),
    /** how many profiles and subscriptions there are, for the sheet's tabs */
    val profiles: Int = 0,
    val subscriptions: Int = 0,
)

/** What the main screen asks of the activity. */
interface HomeActions {
    fun toggle()
    fun view(view: HomeView)
    fun mode(vpn: Boolean)
    fun profiles()
    fun subscriptions()
    fun route()
    fun apps()
    fun settings()
    fun nodes()
    fun log()
    fun help()
}

/** Bytes per second as the status shows them: 312 KB/s, 4.2 MB/s. */
fun rateText(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB/s", bytes / 1048576.0)
    bytes >= 1024 -> "${bytes / 1024} KB/s"
    else -> "$bytes B/s"
}

/** A time online as the status shows it: 00:42:17, then 1:02:03:04 past a day. */
fun elapsedText(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    val days = seconds / 86400
    val clock = String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
    return if (days > 0) "$days:$clock" else clock
}
