package io.niko.ui

import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.fmt.TAG_BLOCK
import io.niko.fmt.TAG_BYPASS
import io.niko.fmt.TAG_PROXY
import io.niko.ktx.Logs
import io.niko.ktx.dp2px
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.utils.PackageCache
import org.json.JSONArray
import org.json.JSONObject

/**
 * The route tester (after ThroneForAndroid's; the owner's decision of 2026-11-15): which of the running core's rules
 * a connection to a host, from an app or not, would match, and where it would go. Nothing is dialled; the core
 * walks its rules as its router does (libcore RouteTest), in the service's process, through ISagerNetService.
 */
fun Fragment.showRouteTest() {
    val context = requireContext()
    val padding = dp2px(20)
    val target = EditText(context).apply {
        hint = getString(R.string.route_test_target_hint)
        isSingleLine = true
    }
    // the installed apps by their label, for a rule by app: "Label (package)"
    val apps = runCatching {
        PackageCache.awaitLoadSync()
        PackageCache.installedApps.keys.map { "${PackageCache.loadLabel(it)} ($it)" }.sorted()
    }.getOrDefault(emptyList())
    val app = AutoCompleteTextView(context).apply {
        hint = getString(R.string.route_test_app_hint)
        isSingleLine = true
        threshold = 1
        setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, apps))
    }
    val udp = CheckBox(context).apply { text = getString(R.string.route_test_udp) }
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, 0)
        addView(target)
        addView(app)
        addView(udp)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.route_test)
        .setView(layout)
        .setPositiveButton(R.string.route_test_run) { _, _ ->
            val parsed = parseRouteTestTarget(target.text.toString())
            if (parsed == null) {
                MaterialAlertDialogBuilder(context).setTitle(R.string.route_test)
                    .setMessage(R.string.route_test_bad_target).setPositiveButton(android.R.string.ok, null).show()
                return@setPositiveButton
            }
            val pkg = packageOfChoice(app.text.toString())
            val query = JSONObject()
                .put("network", if (udp.isChecked) "udp" else "tcp")
                .put("host", parsed.first)
                .put("port", parsed.second)
                .put("resolve", true)
            if (pkg != null) {
                query.put("packages", JSONArray().put(pkg))
                PackageCache[pkg]?.let { query.put("uid", it) }
            }
            val activity = requireActivity() as MainActivity
            runOnDefaultDispatcher {
                val text = try {
                    describeRouteTest(JSONObject(activity.routeTest(query.toString())), activity::getString)
                } catch (e: Exception) {
                    Logs.w(e)
                    e.readableMessage
                }
                onMainDispatcher {
                    MaterialAlertDialogBuilder(activity).setTitle(R.string.route_test)
                        .setView(TextView(activity).apply {
                            this.text = text
                            setTextIsSelectable(true)
                            setPadding(padding, padding / 2, padding, 0)
                        })
                        .setPositiveButton(android.R.string.ok, null).show()
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
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

/** The core's answer as the user reads it: the rule that decided, or none, where it goes, and the steps before. */
internal fun describeRouteTest(result: JSONObject, string: (Int) -> String): String {
    fun outbound(tag: String) = when (tag) {
        TAG_PROXY -> string(R.string.route_test_out_proxy)
        TAG_BYPASS -> string(R.string.route_test_out_direct)
        TAG_BLOCK -> string(R.string.route_test_out_block)
        else -> tag
    }
    val lines = mutableListOf<String>()
    val index = result.optInt("index", -1)
    val out = result.optString("outbound")
    if (index < 0) {
        lines += string(R.string.route_test_no_rule) + (if (out.isNotEmpty()) " → " + outbound(out) else "")
    } else {
        lines += "#$index ${result.optString("rule")}"
        lines += "→ " + if (out.isNotEmpty()) outbound(out) else result.optString("action")
    }
    val steps = result.optJSONArray("steps") ?: JSONArray()
    val before = (0 until steps.length()).map { steps.getJSONObject(it) }.filter { it.optInt("index") != index }
    if (before.isNotEmpty()) {
        lines += ""
        lines += string(R.string.route_test_steps)
        for (step in before) {
            var line = "#${step.optInt("index")} ${step.optString("action")}"
            step.optString("note").takeIf { it.isNotEmpty() }?.let { line += " ($it)" }
            step.optJSONArray("resolved")?.let { list ->
                if (list.length() > 0) line += ": " + (0 until list.length()).joinToString(", ") { list.getString(it) }
            }
            lines += line
        }
    }
    return lines.joinToString("\n")
}
