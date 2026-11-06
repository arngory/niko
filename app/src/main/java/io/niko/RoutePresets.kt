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

    /** whether a rule is the Russian preset's (by its lists): adding the preset again replaces it */
    fun isRussia(rule: RuleEntity) = RUNETFREEDOM in rule.domains || RUNETFREEDOM in rule.ip

    /** Replaces the Russian preset's rules; the rule-sets to download. */
    suspend fun addRussia(blockedName: String, directName: String): List<String> {
        SagerDatabase.rulesDao.allRules().filter(::isRussia).forEach { ProfileManager.deleteRule(it.id) }
        val rules = russia(blockedName, directName)
        rules.forEach { ProfileManager.createRule(it) }
        return RuleSetFiles.itemsOf(rules.flatMap { listOf(it.domains, it.ip) })
    }
}
