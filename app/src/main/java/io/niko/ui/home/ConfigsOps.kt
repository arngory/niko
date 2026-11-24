package io.niko.ui.home

import io.niko.Protocols
import io.niko.database.ProfileManager
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase

/**
 * What the configs' sheet's menus do to the stored profiles and groups (D-PROFILE-06, 08; D-SUB-10): the old lists'
 * actions, moved here without their dialogs. All off the main thread.
 */
object ConfigsOps {

    /** how many names a question about duplicates lists before it trails off */
    const val NAMES_SHOWN = 20

    /** The group's test results forgotten: no ping, no failure. */
    suspend fun clearResults(groupId: Long) {
        val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
        val toClear = mutableListOf<ProxyEntity>()
        if (profiles.isNotEmpty()) for (profile in profiles) {
            if (profile.status != 0) {
                profile.status = 0
                profile.ping = 0
                profile.error = null
                toClear.add(profile)
            }
        }
        ProfileManager.updateTestResult(toClear)
    }

    /** The group's traffic counters back to zero. */
    suspend fun clearTraffic(groupId: Long) {
        val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
        val toClear = mutableListOf<ProxyEntity>()
        if (profiles.isNotEmpty()) for (profile in profiles) {
            if (profile.tx != 0L || profile.rx != 0L) {
                profile.tx = 0
                profile.rx = 0
                toClear.add(profile)
            }
        }
        ProfileManager.updateTraffic(toClear)
    }

    /** The group's profiles whose last test failed. */
    fun unavailable(groupId: Long): List<ProxyEntity> = unavailable(SagerDatabase.proxyDao.getByGroup(groupId))

    fun unavailable(profiles: List<ProxyEntity>) = profiles.filter { it.status != 0 && it.status != 1 }

    /** The group's profiles that repeat an earlier one. */
    fun duplicates(groupId: Long): List<ProxyEntity> = duplicates(SagerDatabase.proxyDao.getByGroup(groupId))

    /** Those of [profiles] with the server, port and kind (a custom config: the text) of one before them; the first stays. */
    fun duplicates(profiles: List<ProxyEntity>): List<ProxyEntity> {
        val toClear = mutableListOf<ProxyEntity>()
        val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
        for (pf in profiles) {
            val proxy = Protocols.Deduplication(pf.requireBean(), pf.displayType())
            if (!uniqueProxies.add(proxy)) {
                toClear += pf
            }
        }
        return toClear
    }

    /** The names a question lists, one a line: the first [NAMES_SHOWN], then dots for the rest. */
    fun names(profiles: List<ProxyEntity>): String = profiles.mapIndexedNotNull { index, proxyEntity ->
        if (index < NAMES_SHOWN) {
            proxyEntity.displayName()
        } else if (index == NAMES_SHOWN) {
            "......"
        } else {
            null
        }
    }.joinToString("\n")

    /** [profiles] deleted at once, no undo (after a question). */
    suspend fun delete(profiles: List<ProxyEntity>) {
        for (profile in profiles) {
            ProfileManager.deleteProfile2(profile.groupId, profile.id)
        }
    }

    /**
     * [a] and [b] change places in [orders] (each id with its userOrder, as listed): the ids whose userOrder changes,
     * with the new one. As the old lists' drag by one row: the two take each other's order; orders that do not tell
     * the rows apart (equal, or not rising) are numbered anew from 1.
     */
    fun swapped(orders: List<Pair<Long, Long>>, a: Long, b: Long): Map<Long, Long> {
        val from = orders.indexOfFirst { it.first == a }
        val to = orders.indexOfFirst { it.first == b }
        if (from < 0 || to < 0 || from == to) return emptyMap()
        val ids = orders.map { it.first }.toMutableList()
        ids[from] = b
        ids[to] = a
        val rising = orders.zipWithNext().all { (x, y) -> x.second < y.second }
        val places = if (rising) orders.map { it.second } else orders.indices.map { it + 1L }
        val before = orders.toMap()
        return ids.mapIndexedNotNull { i, id -> (id to places[i]).takeIf { before[id] != places[i] } }.toMap()
    }

    /** Two profiles of a group change places; the order alone is written, as the old list's drag did. */
    fun moveProfile(a: Long, b: Long) {
        val groupId = SagerDatabase.proxyDao.getById(a)?.groupId ?: return
        val changed = swapped(SagerDatabase.proxyDao.getByGroup(groupId).map { it.id to it.userOrder }, a, b)
        SagerDatabase.instance.runInTransaction {
            changed.forEach { (id, order) -> SagerDatabase.proxyDao.updateOrder(id, order) }
        }
    }

    /** Two groups change places, as the old groups' list's drag did. */
    fun moveGroup(a: Long, b: Long) {
        val groups = SagerDatabase.groupDao.allGroups()
        val changed = swapped(groups.map { it.id to it.userOrder }, a, b)
        SagerDatabase.instance.runInTransaction {
            groups.forEach { group ->
                val order = changed[group.id] ?: return@forEach
                group.userOrder = order
                SagerDatabase.groupDao.updateGroup(group)
            }
        }
    }
}
