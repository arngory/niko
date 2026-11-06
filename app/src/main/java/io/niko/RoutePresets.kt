package io.niko

import io.niko.database.ProfileManager
import io.niko.database.RuleEntity
import io.niko.database.SagerDatabase

/**
 * Ready-made rules. Russia: what is blocked there through the proxy, Russian services directly, the rest as before.
 * The lists are runetfreedom's (russia-v2ray-rules-dat, updated every 6 hours), as rule-sets the app downloads.
 */
object RoutePresets {

    private const val RUNETFREEDOM = "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box"

    const val RU_BLOCKED_DOMAINS = "${RuleSetFiles.PREFIX}$RUNETFREEDOM/rule-set-geosite/geosite-ru-blocked.srs"
    const val RU_BLOCKED_IPS = "${RuleSetFiles.PREFIX}$RUNETFREEDOM/rule-set-geoip/geoip-ru-blocked.srs"
    const val RU_INSIDE_DOMAINS = "${RuleSetFiles.PREFIX}$RUNETFREEDOM/rule-set-geosite/geosite-ru-available-only-inside.srs"
    const val RU_IPS = "${RuleSetFiles.PREFIX}$RUNETFREEDOM/rule-set-geoip/geoip-ru.srs"

    /**
     * The Russian preset's rules in their order. The blocked first: a blocked site under .ru, or a service that a
     * Russian address of its CDN serves (YouTube from a cache in a Russian network), still goes through the proxy.
     * They go after the rules there are (blocking ads and QUIC stays first); a rule the user put above them comes
     * first, as the dialog says. The direct rule waits for the blocked rule's lists (russiaDirectWaits).
     */
    fun russia(blockedName: String, directName: String) = listOf(
        RuleEntity(name = blockedName, domains = RU_BLOCKED_DOMAINS, ip = RU_BLOCKED_IPS, outbound = 0, enabled = true),
        RuleEntity(
            name = directName,
            domains = listOf(RU_INSIDE_DOMAINS, "domain:ru", "domain:su", "domain:xn--p1ai").joinToString("\n"),
            ip = RU_IPS,
            outbound = -1,
            enabled = true,
        ),
    )

    /** the preset's rule for what is blocked: both its lists */
    fun isRussiaBlocked(rule: RuleEntity) = RU_BLOCKED_DOMAINS in rule.domains && RU_BLOCKED_IPS in rule.ip

    private fun hasRussiaDirectLists(rule: RuleEntity) = RU_INSIDE_DOMAINS in rule.domains && RU_IPS in rule.ip

    /** the preset's rule that sends Russian services directly: both its lists, and directly */
    fun isRussiaDirect(rule: RuleEntity) = hasRussiaDirectLists(rule) && rule.outbound == -1L

    /**
     * whether a rule is the Russian preset's (by both lists of either rule): adding the preset again replaces it; a
     * rule of the user's that names one of runetfreedom's lists stays
     */
    fun isRussia(rule: RuleEntity) = isRussiaBlocked(rule) || hasRussiaDirectLists(rule)

    /**
     * Whether the preset's direct rule waits: its blocked rule is on, but not all of that rule's lists are here. Alone,
     * the direct rule would send what is blocked under .ru, or served from a Russian address, around the proxy.
     */
    fun russiaDirectWaits(enabled: List<RuleEntity>) =
        RuleSetFiles.itemsOf(enabled.filter(::isRussiaBlocked).flatMap { listOf(it.domains, it.ip) })
            .any { !RuleSetFiles.available(it) }

    /** Replaces the Russian preset's rules; the rule-sets to download. */
    suspend fun addRussia(blockedName: String, directName: String): List<String> {
        SagerDatabase.rulesDao.allRules().filter(::isRussia).forEach { ProfileManager.deleteRule(it.id) }
        val rules = russia(blockedName, directName)
        rules.forEach { ProfileManager.createRule(it) }
        return RuleSetFiles.itemsOf(rules.flatMap { listOf(it.domains, it.ip) })
    }
}
