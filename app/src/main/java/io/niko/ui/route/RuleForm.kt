package io.niko.ui.route

import android.os.Parcelable
import io.niko.database.RuleEntity
import kotlinx.parcelize.Parcelize

/**
 * A rule as its editor (P-Rule) holds it while it is changed: RuleEntity's fields as typed, the old editor's
 * (RouteSettingsActivity's) way. [outbound] 0 the proxy, -1 direct, -2 block, or a profile's id; [packages] the apps
 * in their order.
 */
@Parcelize
data class RuleForm(
    val name: String = "",
    val config: String = "",
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val source: String = "",
    val protocol: String = "",
    val outbound: Long = 0L,
    val packages: List<String> = emptyList(),
    val dpiBypass: Boolean = false,
) : Parcelable {

    val out get() = ruleOut(outbound)

    /** the rule's fields from the form; the order, the switch and the id stay the rule's */
    fun applyTo(rule: RuleEntity) {
        rule.name = name
        rule.config = config
        rule.domains = domains
        rule.ip = ip
        rule.port = port
        rule.sourcePort = sourcePort
        rule.network = network
        rule.source = source
        rule.protocol = protocol
        rule.outbound = outbound
        rule.packages = packages.filter { it.isNotBlank() }.toSet()
        rule.dpiBypass = dpiBypass
    }

    companion object {
        fun of(rule: RuleEntity) = RuleForm(
            rule.name, rule.config, rule.domains, rule.ip, rule.port, rule.sourcePort, rule.network, rule.source,
            rule.protocol, rule.outbound, rule.packages.sorted(), rule.dpiBypass,
        )

        /** the apps as AppListActivity keeps them (DataStore.routePackages), a package a line */
        fun packagesOf(lines: String) = lines.split("\n").filter { it.isNotBlank() }.distinct().sorted()
    }
}

/** What leaving or saving the editor does with the form. */
enum class RuleSave {
    /** nothing to write: the rule stays as it was */
    UNCHANGED,
    /** a new rule with nothing set: «Установите какие-либо правила» */
    EMPTY,
    /** written: a new rule made, or the rule changed */
    WRITE,
}

/**
 * What saving does: an existing rule as it was read is left as it is (the old editor said «Установите какие-либо
 * правила» there, as if it were empty), a new one untouched is empty.
 */
fun ruleSave(form: RuleForm, original: RuleForm, existing: Boolean) = when {
    form != original -> RuleSave.WRITE
    existing -> RuleSave.UNCHANGED
    else -> RuleSave.EMPTY
}
