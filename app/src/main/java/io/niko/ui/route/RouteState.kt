package io.niko.ui.route

import io.niko.database.RuleEntity
import io.niko.utils.listByLineOrComma
import org.json.JSONArray
import org.json.JSONObject

// What the routing screens show, apart from the service and the database: the rule list's rows (P-Rules), the route
// check's question and answer (P-Check), the rule sets' files (P-Lists). Pure, for the tests and Paparazzi.

/** Where a rule sends what it matches: RuleEntity.outbound 0, -1, -2, or a profile's id. */
enum class RuleOut { PROXY, DIRECT, BLOCK, PROFILE }

fun ruleOut(outbound: Long) = when (outbound) {
    0L -> RuleOut.PROXY
    -1L -> RuleOut.DIRECT
    -2L -> RuleOut.BLOCK
    else -> RuleOut.PROFILE
}

/**
 * A rule as its row shows it: the name, what it matches ([apps] the apps it is for, [conditions] the rest in short),
 * where it goes ([profile] the profile's name for [RuleOut.PROFILE], null: the profile is gone), on or off.
 */
class RuleRow(
    val id: Long,
    val name: String,
    val apps: Int,
    val conditions: List<String>,
    val out: RuleOut,
    val profile: String?,
    val enabled: Boolean,
) {
    companion object {
        fun of(rule: RuleEntity, profileName: String?) = RuleRow(
            rule.id, rule.displayName(), rule.packages.size, ruleConditions(rule), ruleOut(rule.outbound), profileName, rule.enabled,
        )
    }
}

/** The ready-made rules the list has: none or Russia's (RoutePresets). */
enum class RulePreset { NONE, RUSSIA }

/** P-Rules: the rules in their order (null: not read yet), the preset in them, when the geo bases were last written. */
class RulesState(val rules: List<RuleRow>?, val preset: RulePreset = RulePreset.NONE, val listsDate: String? = null)

/**
 * A rule's conditions besides its apps, each in short as the old list's summary named them: a list by its first entry
 * (a rule-set by its file's name) and how many more, the rest with their sing-box names.
 */
internal fun ruleConditions(rule: RuleEntity): List<String> = buildList {
    if (rule.config.isNotBlank()) add("JSON")
    shortList(rule.domains)?.let(::add)
    shortList(rule.ip)?.let(::add)
    shortList(rule.source)?.let { add("src ip: $it") }
    rule.sourcePort.trim().takeIf { it.isNotEmpty() }?.let { add("src port: $it") }
    rule.port.trim().takeIf { it.isNotEmpty() }?.let { add("dst port: $it") }
    rule.network.trim().takeIf { it.isNotEmpty() }?.let { add("network: $it") }
    rule.protocol.trim().takeIf { it.isNotEmpty() }?.let { add("protocol: $it") }
}

/** A list by line or comma in short: its first entry (a URL by its file's name, no extension) and "+N" for the rest. */
internal fun shortList(text: String): String? {
    val entries = text.listByLineOrComma().map { it.trim() }.filter { it.isNotEmpty() }
    if (entries.isEmpty()) return null
    val first = entries[0].let { entry ->
        if ("://" in entry) entry.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifEmpty { entry } else entry
    }
    return if (entries.size > 1) "$first +${entries.size - 1}" else first
}

/**
 * The list's order after a rule is dragged from [from] to [to]: the rules between take each other's userOrder, as
 * the old list's drag did (one step at a time, a swap). Returns the rules whose order changed, to be written.
 */
internal fun moveRule(list: MutableList<RuleEntity>, from: Int, to: Int): List<RuleEntity> {
    if (from == to || from !in list.indices || to !in list.indices) return emptyList()
    val low = minOf(from, to)
    val high = maxOf(from, to)
    val orders = (low..high).map { list[it].userOrder }
    list.add(to, list.removeAt(from))
    val changed = ArrayList<RuleEntity>()
    for ((i, position) in (low..high).withIndex()) {
        val rule = list[position]
        if (rule.userOrder != orders[i]) {
            rule.userOrder = orders[i]
            changed += rule
        }
    }
    return changed
}

/** P-Check's question: the address, and under «Ещё» the app it comes from, UDP, whether the core's DNS is asked. */
data class CheckForm(val target: String = "", val app: String = "", val udp: Boolean = false, val resolve: Boolean = true)

/** A rule the core took on the way, before the one that decided. */
class CheckStep(val index: Int, val rule: String, val action: String, val note: String, val resolved: List<String>)

/**
 * The core's answer (libcore/routetest Result): the rule that decided ([index] -1: none, the final outbound), its
 * action and outbound, or why there is no connection ([error]); the address the app's DNS query gave, what the rules
 * saw (a FakeDNS address's domain), the rules on the way, what was taken on trust.
 */
class CheckResult(
    val index: Int,
    val rule: String,
    val action: String,
    val outbound: String,
    val error: String,
    val resolved: List<String>,
    val destination: String,
    val fakeIp: Boolean,
    val steps: List<CheckStep>,
    val notes: List<String>,
) {
    companion object {
        private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map { getString(it) }

        fun of(json: JSONObject): CheckResult {
            val index = json.optInt("index", -1)
            val steps = json.optJSONArray("steps") ?: JSONArray()
            return CheckResult(
                index,
                json.optString("rule"),
                json.optString("action"),
                json.optString("outbound"),
                json.optString("error"),
                json.optJSONArray("resolved").strings(),
                json.optString("destination"),
                json.optBoolean("fake_ip"),
                (0 until steps.length()).map { steps.getJSONObject(it) }.filter { it.optInt("index") != index }.map {
                    CheckStep(it.optInt("index"), it.optString("rule"), it.optString("action"), it.optString("note"), it.optJSONArray("resolved").strings())
                },
                json.optJSONArray("notes").strings(),
            )
        }
    }
}

/** Where the check is: asking the core, its answer, an error in words (the core not running, or its own). */
sealed interface CheckOutcome {
    data object Running : CheckOutcome
    data object BadTarget : CheckOutcome
    class Done(val result: CheckResult) : CheckOutcome
    class Failed(val message: String) : CheckOutcome
}

/** P-Check: the question, whether «Ещё» is open, the answer, the installed apps as "Label (package)" to pick from. */
data class CheckState(
    val form: CheckForm = CheckForm(),
    val more: Boolean = false,
    val outcome: CheckOutcome? = null,
    val apps: List<String> = emptyList(),
)

/** The apps offered for what was typed: those whose "Label (package)" has it, a few; none once one is picked. */
internal fun appSuggestions(apps: List<String>, typed: String, limit: Int = 4): List<String> {
    val text = typed.trim()
    if (text.isEmpty() || apps.any { it == text }) return emptyList()
    return apps.filter { it.contains(text, ignoreCase = true) }.take(limit)
}

/** The package of "Label (package)" as the list offers it, or of what was typed as a package name; null: no app. */
internal fun packageOfChoice(text: String): String? {
    val choice = text.trim()
    if (choice.isEmpty()) return null
    val inParens = Regex("""\(([A-Za-z0-9_.]+)\)$""").find(choice)?.groupValues?.get(1)
    return inParens ?: choice.takeIf { Regex("""^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$""").matches(it) }
}

/**
 * A host and a port out of what was typed: "example.com", "example.com:80", "192.0.2.1:53", "[2001:db8::1]:443" or
 * a bare IPv6 address. No port: 443. Null for nothing usable.
 */
internal fun parseRouteTestTarget(text: String): Pair<String, Int>? {
    var value = text.trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
    if (value.isEmpty()) return null
    var port = 443
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end < 0) return null
        val rest = value.substring(end + 1)
        if (rest.isNotEmpty()) port = rest.removePrefix(":").toIntOrNull() ?: return null
        value = value.substring(1, end)
    } else if (value.count { it == ':' } == 1) {
        port = value.substringAfter(':').toIntOrNull() ?: return null
        value = value.substringBefore(':')
    }
    if (value.isEmpty() || port !in 1..65535 || value.any { it.isWhitespace() }) return null
    return value to port
}

/** What the core is asked (BaseService.routeTest adds the inbound): [uid] the app's, if it is installed. */
internal fun checkQuery(host: String, port: Int, form: CheckForm, pkg: String?, uid: Int?): JSONObject {
    val query = JSONObject()
        .put("network", if (form.udp) "udp" else "tcp")
        .put("host", host)
        .put("port", port)
        .put("resolve", form.resolve)
    if (pkg != null) {
        query.put("packages", JSONArray().put(pkg))
        uid?.let { query.put("uid", it) }
    }
    return query
}

/** What a rule-set file is: a geo base the app updates, an own .db, the root certificate. */
enum class AssetKind { GEO, DB, CA }

/** A file of the rule sets: its name, its version (a release's tag, Custom, Unknown-date), whether it is updating. */
class AssetRow(val name: String, val version: String, val kind: AssetKind, val updating: Boolean = false)

/** P-Lists: the geo bases, then the own files. */
class ListsState(val rows: List<AssetRow>)
