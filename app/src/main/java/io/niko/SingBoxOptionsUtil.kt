package io.niko

import io.niko.database.DataStore
import io.niko.SingBoxOptions.RuleSet

object SingBoxOptionsUtil {

    fun domainStrategy(tag: String): String {
        fun auto2(key: String, newS: String): String {
            return (DataStore.configurationStore.getString(key) ?: "").replace("auto", newS)
        }
        return when (tag) {
            "dns-remote" -> {
                auto2("domain_strategy_for_remote", "")
            }

            "dns-direct" -> {
                auto2("domain_strategy_for_direct", "")
            }

            // server
            else -> {
                auto2("domain_strategy_for_server", "prefer_ipv4")
            }
        }
    }

}

fun SingBoxOptions.DNSRule_DefaultOptions.makeSingBoxRule(list: List<String>) {
    rule_set = mutableListOf<String>()
    domain = mutableListOf<String>()
    domain_suffix = mutableListOf<String>()
    domain_regex = mutableListOf<String>()
    domain_keyword = mutableListOf<String>()
    list.forEach {
        if (it.startsWith("geosite:")) {
            rule_set.plusAssign(it)
        } else if (isRemoteRuleSet(it) || RuleSetFiles.isItem(it)) {
            // an IP-only rule-set makes a DNS rule invalid in sing-box 1.14; skip what looks like one
            if (!isIPRuleSetName(it)) rule_set.plusAssign(it)
        } else if (it.startsWith("full:")) {
            domain.plusAssign(it.removePrefix("full:").lowercase())
        } else if (it.startsWith("domain:")) {
            domain_suffix.plusAssign(it.removePrefix("domain:").lowercase())
        } else if (it.startsWith("regexp:")) {
            domain_regex.plusAssign(it.removePrefix("regexp:").lowercase())
        } else if (it.startsWith("keyword:")) {
            domain_keyword.plusAssign(it.removePrefix("keyword:").lowercase())
        } else {
            domain_suffix.plusAssign(it.lowercase())
        }
    }
    rule_set?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }
    if (rule_set?.isEmpty() == true) rule_set = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
}

fun SingBoxOptions.DNSRule_DefaultOptions.checkEmpty(): Boolean {
    if (rule_set?.isNotEmpty() == true) return false
    if (domain?.isNotEmpty() == true) return false
    if (domain_suffix?.isNotEmpty() == true) return false
    if (domain_regex?.isNotEmpty() == true) return false
    if (domain_keyword?.isNotEmpty() == true) return false
    if (user_id?.isNotEmpty() == true) return false
    return true
}

/** Prefix of a remote rule-set in rule lists: `rule-set:https://example.com/geosite-ru.srs`. */
const val REMOTE_RULE_SET_PREFIX = "rule-set:"

fun isRemoteRuleSet(item: String) = item.startsWith(REMOTE_RULE_SET_PREFIX)

/** Rule-sets named geoip-… or ip-… usually hold IP CIDRs only. */
fun isIPRuleSetName(item: String) = item.substringAfterLast('/').substringBefore('?').lowercase()
    .let { it.startsWith("geoip") || it.startsWith("ip-") || it.startsWith("ipcidr") }

fun generateRuleSet(
    ruleSetString: List<String>,
    ruleSet: MutableList<RuleSet>,
    updateHours: Int = DataStore.ruleSetUpdateHours,
) {
    ruleSetString.forEach {
        when {
            isRemoteRuleSet(it) -> {
                val url = it.removePrefix(REMOTE_RULE_SET_PREFIX)
                ruleSet.add(RuleSet().apply {
                    type = "remote"
                    tag = it // the tag is also the cache key: the same URL keeps its download
                    format = if (url.substringBefore('?').endsWith(".json")) "source" else "binary"
                    this.url = url
                    update_interval = "${updateHours.coerceIn(1, 24 * 30)}h"
                    // downloaded through the final outbound, i.e. the proxy in the default mode
                })
            }

            RuleSetFiles.isItem(it) -> {
                // the app's download (ConfigBuilder leaves out those not downloaded yet)
                ruleSet.add(RuleSet().apply {
                    type = "local"
                    tag = it
                    format = RuleSetFiles.format(it)
                    path = RuleSetFiles.file(it).absolutePath
                })
            }

            it.startsWith("geoip:") -> {
                ruleSet.add(RuleSet().apply {
                    type = "local"
                    tag = it
                    format = "binary"
                    path = it
                })
            }

            it.startsWith("geosite:") -> {
                ruleSet.add(RuleSet().apply {
                    type = "local"
                    tag = it
                    format = "binary"
                    path = it
                })
            }
        }
    }
}

fun SingBoxOptions.Rule_DefaultOptions.makeSingBoxRule(list: List<String>, isIP: Boolean) {
    // a rule may have both domains and IPs: keep the rule-sets added for the other list
    if (rule_set == null) rule_set = mutableListOf<String>()
    if (isIP) {
        ip_cidr = mutableListOf<String>()
    } else {
        domain = mutableListOf<String>()
        domain_suffix = mutableListOf<String>()
        domain_regex = mutableListOf<String>()
        domain_keyword = mutableListOf<String>()
    }
    list.forEach {
        if (isRemoteRuleSet(it) || RuleSetFiles.isItem(it)) {
            rule_set.plusAssign(it)
            return@forEach
        }
        if (isIP) {
            if (it.startsWith("geoip:")) {
                if (it == "geoip:private") {
                    ip_is_private = true
                } else {
                    rule_set.plusAssign(it)
                }
            } else {
                ip_cidr.plusAssign(it)
            }
            return@forEach
        }
        if (it.startsWith("geosite:")) {
            rule_set.plusAssign(it)
        } else if (it.startsWith("full:")) {
            domain.plusAssign(it.removePrefix("full:").lowercase())
        } else if (it.startsWith("domain:")) {
            domain_suffix.plusAssign(it.removePrefix("domain:").lowercase())
        } else if (it.startsWith("regexp:")) {
            domain_regex.plusAssign(it.removePrefix("regexp:").lowercase())
        } else if (it.startsWith("keyword:")) {
            domain_keyword.plusAssign(it.removePrefix("keyword:").lowercase())
        } else {
            domain_suffix.plusAssign(it.lowercase())
        }
    }
    ip_cidr?.removeIf { it.isNullOrBlank() }
    rule_set?.removeIf { it.isNullOrBlank() }
    domain?.removeIf { it.isNullOrBlank() }
    domain_suffix?.removeIf { it.isNullOrBlank() }
    domain_regex?.removeIf { it.isNullOrBlank() }
    domain_keyword?.removeIf { it.isNullOrBlank() }
    if (ip_cidr?.isEmpty() == true) ip_cidr = null
    if (domain?.isEmpty() == true) domain = null
    if (domain_suffix?.isEmpty() == true) domain_suffix = null
    if (domain_regex?.isEmpty() == true) domain_regex = null
    if (domain_keyword?.isEmpty() == true) domain_keyword = null
}

fun SingBoxOptions.Rule_DefaultOptions.checkEmpty(): Boolean {
    if (ip_cidr?.isNotEmpty() == true) return false
    if (domain?.isNotEmpty() == true) return false
    if (rule_set?.isNotEmpty() == true) return false
    if (domain_suffix?.isNotEmpty() == true) return false
    if (domain_regex?.isNotEmpty() == true) return false
    if (domain_keyword?.isNotEmpty() == true) return false
    if (user_id?.isNotEmpty() == true) return false
    //
    if (port?.isNotEmpty() == true) return false
    if (port_range?.isNotEmpty() == true) return false
    if (source_ip_cidr?.isNotEmpty() == true) return false
    //
    if (!_hack_custom_config.isNullOrBlank()) return false
    return true
}

/** Whether the rule matches by an address at all: a domain, an IP, a rule-set. */
fun SingBoxOptions.Rule_DefaultOptions.hasAddress(): Boolean =
    !domain.isNullOrEmpty() || !domain_suffix.isNullOrEmpty() || !domain_keyword.isNullOrEmpty() ||
        !domain_regex.isNullOrEmpty() || !ip_cidr.isNullOrEmpty() || ip_is_private == true || !rule_set.isNullOrEmpty()
