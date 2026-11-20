package io.niko.ui.home

import android.content.Context
import io.niko.GroupType
import io.niko.Key
import io.niko.RoutePresets
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.database.SagerDatabase
import io.niko.ui.subscriptionStatusLine

/**
 * What the main screen reads from the database (on a background thread): the profile in use and its group, the mode,
 * the rules and the apps, how many profiles and subscriptions there are. The service's state comes from the activity.
 */
object HomeLoader {

    fun load(context: Context, base: HomeState): HomeState {
        val profile = ProfileManager.getProfile(DataStore.selectedProxy)?.let { proxy ->
            val group = SagerDatabase.groupDao.getById(proxy.groupId)
            HomeProfile(
                name = proxy.displayName(),
                type = proxy.displayType(),
                group = group?.takeUnless { it.ungrouped }?.displayName(),
                usage = group?.subscriptionStatusLine(context),
                pingMs = proxy.ping.takeIf { proxy.status == 1 && it > 0 },
                unreachable = proxy.status > 1,
            )
        }
        val rules = SagerDatabase.rulesDao.enabledRules()
        val route = when {
            rules.any { RoutePresets.isRussia(it) } -> HomeRoute.Russia
            rules.isNotEmpty() -> HomeRoute.Own
            else -> HomeRoute.None
        }
        val apps = DataStore.individual.lineSequence().count { it.isNotBlank() }
        return base.copy(
            profile = profile,
            mode = HomeMode(vpn = DataStore.serviceMode == Key.MODE_VPN, proxyPort = DataStore.mixedPort),
            route = route,
            apps = HomeApps(perApp = DataStore.proxyApps, bypass = DataStore.bypass, count = apps),
            profiles = SagerDatabase.proxyDao.count().toInt(),
            subscriptions = SagerDatabase.groupDao.allGroups().count { it.type == GroupType.SUBSCRIPTION },
        )
    }
}
