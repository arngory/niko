package io.niko.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import io.niko.R
import io.niko.SagerNet
import io.niko.databinding.LayoutRayAdminBinding
import io.niko.databinding.LayoutRayAdminItemBinding
import io.niko.ktx.FixedLinearLayoutManager
import io.niko.ktx.Logs
import io.niko.ktx.getColorAttr
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.raycontrol.RayApi
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RaySubscription
import io.niko.utils.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The admin mode for a raycontrol node (api-v1.md §5): the node's NodeService over the remote API, for a device
 * registered as admin through an invite. Status, users with their devices and invites, services, the audit log.
 */
class RayAdminActivity : ThemedActivity() {

    companion object {
        const val EXTRA_NODE = "io.niko.raycontrol.NODE"
        private const val STATUS = 0
        private const val USERS = 1
        private const val SERVICES = 2
        private const val NETWORK = 3
        private const val AUDIT = 4
        private const val MENU_ADD = 1
    }

    private class Row(val title: String, val summary: String = "", val action: (() -> Unit)? = null)

    private lateinit var binding: LayoutRayAdminBinding
    private lateinit var node: RayNode
    private val adapter = RowAdapter()
    private var tab = STATUS
    private var services: List<JsonObject> = emptyList()
    private var kinds: List<String> = emptyList()
    // the node answered GetPorts unimplemented (before raycontrol v0.27.0): its row goes
    private var noPorts = false
    private val texts = AdminTexts { id, args -> getString(id, *args) }

    override fun snackbarInternal(text: CharSequence): Snackbar =
        Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        node = intent.getStringExtra(EXTRA_NODE)?.let { RayNodes.get(it) }?.takeIf { it.isAdmin } ?: run {
            finish()
            return
        }
        binding = LayoutRayAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.raycontrol_admin)
            subtitle = node.label
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }
        binding.recyclerView.layoutManager = FixedLinearLayoutManager(binding.recyclerView)
        binding.recyclerView.adapter = adapter
        binding.refreshLayout.setColorSchemeColors(getColorAttr(R.attr.primaryOrTextPrimary))
        binding.refreshLayout.setOnRefreshListener { load() }
        for (title in listOf(R.string.raycontrol_admin_status, R.string.raycontrol_admin_users,
            R.string.raycontrol_admin_services, R.string.raycontrol_admin_network, R.string.raycontrol_admin_audit)) {
            binding.tabs.addTab(binding.tabs.newTab().setText(title))
        }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                tab = t.position
                invalidateOptionsMenu()
                load()
            }

            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) = load()
        })
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (tab == USERS || tab == SERVICES) {
            menu.add(0, MENU_ADD, 0, R.string.raycontrol_admin_add)
                .setIcon(R.drawable.ic_action_note_add)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    // the close (X) on the toolbar: no parent activity is declared, so nothing handled it
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_ADD) {
            if (tab == USERS) createUser() else if (tab == SERVICES) createService()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ---- calls

    /** Runs [call] off the main thread with the refresh indicator; errors go to a snackbar, but those [failed] asks about ([quiet]). */
    private fun <T> api(call: () -> T, quiet: (Throwable) -> Boolean = { false }, failed: (Throwable) -> Unit = {}, done: (T) -> Unit) {
        binding.refreshLayout.isRefreshing = true
        runOnDefaultDispatcher {
            val result = try {
                Result.success(call())
            } catch (e: Exception) {
                Logs.w(e)
                Result.failure(e)
            }
            onMainDispatcher {
                if (isFinishing || isDestroyed) return@onMainDispatcher
                binding.refreshLayout.isRefreshing = false
                result.onSuccess(done).onFailure {
                    if (!quiet(it)) snackbar(failure(it)).show()
                    failed(it)
                }
            }
        }
    }

    // rayapi.ErrAnswerLost: sent once, not again, and it may have taken effect
    private fun lost(e: Throwable) = e.message?.contains("answer was lost") == true

    private fun failure(e: Throwable): String = when {
        RayApi.code(e) == "permission_denied" -> getString(R.string.raycontrol_admin_denied)
        RayApi.code(e) == "unauthenticated" -> getString(R.string.raycontrol_admin_unauthenticated)
        // rayapi.ErrAnswerLost: not sent again, it may have taken effect
        e.message?.contains("answer was lost") == true -> getString(R.string.raycontrol_admin_lost)
        else -> e.readableMessage
    }

    // the stored node each time: a key rotation in the background changes the client certificate
    private fun current() = RayNodes.get(node.spa.nodeId) ?: node

    private fun call(method: String, body: JsonObject = JsonObject()) = RayApi.node(current(), method, body)

    private fun body(vararg pairs: Pair<String, Any?>) = JsonObject().apply {
        for ((k, v) in pairs) when (v) {
            null -> {}
            is String -> addProperty(k, v)
            is Number -> addProperty(k, v)
            is Boolean -> addProperty(k, v)
            is List<*> -> add(k, JsonArray().apply { v.forEach { add(it.toString()) } })
            else -> error("unsupported $k")
        }
    }

    private fun load(): Unit = when (tab) {
        STATUS -> loadStatus()
        USERS -> loadUsers()
        SERVICES -> loadServices()
        NETWORK -> loadNetwork()
        else -> loadAudit()
    }

    /** [call], or null where the node does not have the method yet (an older raycontrol): its row is left out. */
    private fun <T> optional(call: () -> T): T? = try {
        call()
    } catch (e: Exception) {
        if (RayApi.unimplemented(e)) null else throw e
    }

    private fun show(rows: List<Row>) {
        adapter.rows = rows.ifEmpty { listOf(Row(getString(R.string.raycontrol_admin_empty))) }
    }

    // ---- status

    private class StatusData(
        val status: JsonObject, val self: JsonObject, val access: JsonObject,
        val mirror: JsonObject?, val summary: JsonObject?, val update: JsonObject?,
    )

    private fun loadStatus(): Unit = api({
        StatusData(
            call("GetStatus"), RayApi.call(current(), RayApi.DEVICE + "GetSelf"), call("GetAdminAccess"),
            // raycontrol v0.18.0 and v0.11.0: not on an older node
            optional { call("GetAppMirror") }, optional { call("GetSummary") },
            // raycontrol v0.19.0 (node-update-v1)
            optional { call("GetUpdateStatus") },
        )
    }) { data ->
        val status = data.status
        val self = data.self
        val access = data.access
        val extra = mutableListOf<Row>()
        // the node's name (raycontrol v0.15.0: GetStatus has it); clients show it in place of the address
        if (status.has("name") || status.s("version").let { it.isNotEmpty() && isNewerVersion(it, "0.14.99") }) {
            val name = status.s("name")
            extra += Row(getString(R.string.raycontrol_admin_node_name),
                name.ifEmpty { getString(R.string.raycontrol_admin_node_name_none) }) { editNodeName(name) }
        }
        // the note for the node's users (raycontrol v0.21.0): GetStatus leaves it out while there is none
        if (status.has("note") || status.s("version").let { it.isNotEmpty() && isNewerVersion(it, "0.20.99") }) {
            val note = status.s("note")
            extra += Row(getString(R.string.raycontrol_admin_node_note), noteShown(note)) { editNodeNote(note) }
        }
        data.update?.let { update -> extra += Row(getString(R.string.raycontrol_admin_update), updateSummary(update)) { updateActions(update) } }
        data.mirror?.let { mirror -> extra += Row(getString(R.string.raycontrol_admin_mirror), mirrorSummary(mirror)) { mirrorActions(mirror) } }
        data.summary?.let { summary -> extra += Row(getString(R.string.raycontrol_admin_summary), summarySummary(summary)) { summaryActions(summary) } }
        show(listOf(
            Row(getString(R.string.raycontrol_admin_this_device),
                "${self.s("user")}/${self.s("name")} · ${self.s("role")}"),
            Row(getString(R.string.raycontrol_admin_node), "${status.s("host")} · ${status.s("version")}\n${status.s("nodeId")}"),
        ) + extra + listOfNotNull(
            Row(getString(R.string.raycontrol_admin_counts, status.l("users"), status.l("services"), status.l("activeDevices"))),
            Row(getString(R.string.raycontrol_admin_spa_ports), status.strs("spaPorts").joinToString(", ")),
            // every port for a provider's firewall (raycontrol v0.27.0); left out once an older node said it has none
            Row(getString(R.string.raycontrol_admin_ports), getString(R.string.raycontrol_admin_ports_summary)) { showPorts() }
                .takeIf { !noPorts },
            Row(getString(R.string.raycontrol_admin_metrics), getString(R.string.raycontrol_admin_metrics_summary)) { showMetrics() },
            Row(getString(R.string.raycontrol_admin_access),
                access.strs("cidrs").joinToString(", ").ifEmpty { getString(R.string.raycontrol_admin_access_none) }) {
                editAdminAccess(access.strs("cidrs"))
            },
        ))
    }

    /** The node's name (SetNodeName): clients show it in place of its address; empty takes it away. */
    private fun editNodeName(current: String) {
        val input = editText(getString(R.string.raycontrol_admin_node_name), current)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_node_name)
            .setMessage(R.string.raycontrol_admin_node_name_help)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                api({ call("SetNodeName", body("name" to input.text.toString().trim())) }) { answer ->
                    snackbar(getString(R.string.raycontrol_admin_node_name_set,
                        answer.s("name").ifEmpty { getString(R.string.raycontrol_admin_node_name_none) })).show()
                    loadStatus()
                    // this device's own group takes the name with its next subscription: now
                    runOnDefaultDispatcher { runCatching { RaySubscription.refresh(node.spa.nodeId) } }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A note as clients show it: cleaned, in an isolate; "none" for none. */
    private fun noteShown(note: String) =
        RayNode.nodeNote(note)?.let { "\u2068$it\u2069" } ?: getString(R.string.raycontrol_admin_node_note_none)

    /**
     * The note for the node's users (SetNodeNote): one line of plain text, empty takes it away. What is typed is
     * cleaned as clients show a note (line breaks become spaces; invisible and direction characters, stray joiners and
     * marks go), so the node does not refuse it for them; longer than the node takes, or refused, the dialog comes
     * back with the text.
     */
    private fun editNodeNote(current: String, error: String? = null) {
        val input = editText(getString(R.string.raycontrol_admin_node_note), current, multiLine = true)
        input.error = error
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_node_note)
            .setMessage(R.string.raycontrol_admin_node_note_help)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val typed = input.text.toString()
                // cleaned whole: a note longer than the node takes is not cut here, it comes back to be shortened
                val note = RayNode.nodeNote(typed, Int.MAX_VALUE).orEmpty()
                val length = note.codePointCount(0, note.length)
                if (length > RayNode.MAX_NOTE) {
                    editNodeNote(typed, getString(R.string.raycontrol_admin_node_note_too_long, length, RayNode.MAX_NOTE))
                    return@setPositiveButton
                }
                api({ call("SetNodeNote", body("note" to note)) }, failed = { editNodeNote(typed) }) { answer ->
                    snackbar(getString(R.string.raycontrol_admin_node_note_set, noteShown(answer.s("note")))).show()
                    loadStatus()
                    // this device's own card takes the note with its next subscription: now
                    runOnDefaultDispatcher { runCatching { RaySubscription.refresh(node.spa.nodeId) } }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- the node's update by request (raycontrol node-update-v1): a signed release, while root allows it

    private fun updateSummary(update: JsonObject): String {
        val installed = update.s("installed")
        val latest = update.s("latest")
        val parts = mutableListOf(
            if (latest.isNotEmpty() && isNewerVersion(latest, installed)) getString(R.string.raycontrol_admin_update_available, installed, latest)
            else getString(R.string.raycontrol_admin_update_current, installed),
            getString(if (update.s("remote") == "on") R.string.raycontrol_admin_update_remote_on else R.string.raycontrol_admin_update_remote_off),
        )
        latestPatch(update)?.let { parts.add(1, getString(R.string.raycontrol_admin_update_patch, installed, it)) }
        if (update.s("auto") == "patch") parts += getString(R.string.raycontrol_admin_update_auto, update.s("window"), update.s("timezone"))
        update.getAsJsonObject("status")?.let { updateState(it) }?.let { parts += it }
        update.strs("held").takeIf { it.isNotEmpty() }?.let { parts += getString(R.string.raycontrol_admin_update_held, it.joinToString(", ")) }
        relaysSummary(update, texts)?.let { parts += it }
        return parts.joinToString("\n")
    }

    /** The relays' lines (relay-update-remote-v1 §8), with what they are about first; null without relays. */
    private fun relaysText(update: JsonObject): String? {
        val relays = update.arr("relays").takeIf { it.isNotEmpty() } ?: return null
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        val now = System.currentTimeMillis() / 1000
        return getString(R.string.raycontrol_admin_relays_help) + "\n\n" +
            relays.joinToString("\n\n") { relayLine(it, texts, now) { s -> time.format(Date(s * 1000)) } }
    }

    /** The last update in a line; null before any (idle). */
    private fun updateState(status: JsonObject): String? {
        val state = status.s("state").ifEmpty { "idle" }
        val release = status.s("release").ifEmpty { status.s("target") }
        val line = when (state) {
            "idle" -> return null
            "requested" -> getString(R.string.raycontrol_admin_update_requested, release, status.s("by"))
            "running" -> getString(R.string.raycontrol_admin_update_running, status.s("from"), release)
            "done" -> getString(R.string.raycontrol_admin_update_done, status.s("from"), status.s("to"), date(status.l("endedAt")))
            "rolled_back" -> getString(R.string.raycontrol_admin_update_rolled_back, release, status.s("from"))
            "failed" -> getString(R.string.raycontrol_admin_update_failed, release)
            else -> state
        }
        val error = status.s("error").takeIf { it.isNotBlank() && state in setOf("rolled_back", "failed") }
        return if (error != null) "$line: $error" else line
    }

    private fun updateActions(update: JsonObject) {
        val relays = relaysText(update)
        if (update.s("remote") != "on") return textDialog(getString(R.string.raycontrol_admin_update),
            listOfNotNull(getString(R.string.raycontrol_admin_update_remote_help), relays).joinToString("\n\n"))
        // the node's own update under way, or its relays still following it: watched
        val nodeUpdating = update.getAsJsonObject("status")?.s("state") in setOf("requested", "running")
        if (nodeUpdating || relaysPending(update)) return watchUpdate(nodeUpdating)
        val installed = update.s("installed")
        val latest = update.s("latest")
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            if (latest.isNotEmpty() && isNewerVersion(latest, installed)) getString(R.string.raycontrol_admin_update_to, latest) to { startUpdate(latest) }
            else getString(R.string.raycontrol_admin_update_latest) to { startUpdate("latest") },
            getString(R.string.raycontrol_admin_update_version) to {
                val input = editText(getString(R.string.raycontrol_admin_update_version_hint))
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.raycontrol_admin_update_version)
                    .setView(padded(input))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val version = input.text.toString().trim().let { if (it.startsWith("v")) it else "v$it" }
                        if (Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(version)) startUpdate(version)
                        else snackbar(R.string.raycontrol_admin_update_version_bad).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            },
        )
        if (relays != null) actions += getString(R.string.raycontrol_admin_relays) to {
            textDialog(getString(R.string.raycontrol_admin_relays), relays)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_update)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** StartUpdate answers at once; the update runs after it, rayd and its API restarting on the way. */
    private fun startUpdate(version: String) = confirm(getString(R.string.raycontrol_admin_update_confirm, version)) {
        // an answer lost while rayd restarts, or "an update is requested already" on a second tap: the update may be
        // under way, and its status only reads
        api({ call("StartUpdate", body("version" to version)) }, failed = { e ->
            if (lost(e) || RayApi.code(e) == "failed_precondition") watchUpdate(nodeUpdating = true)
        }) { watchUpdate(nodeUpdating = true) }
    }

    /**
     * The update as it goes (GetUpdateStatus every 7 s, node-update-v1 §2), until it ends or 20 min have gone. The
     * node is out of reach while rayd, its API and the data plane restart: asked again. Then its relays, which follow
     * it one at a time (relay-update-remote-v1 §8): asked every 30 s while one is waiting, offered, requested or
     * running, each of which ends, for three hours at most (a silent relay holds the queue up to 40 minutes before
     * its turn and 40 in it). Closing leaves it all running. [nodeUpdating]: the node's own update is asked for or
     * under way; else only its relays are on their way (a relay back after a while gets the node's version offered
     * with no update of the node's), and the node's outcome is not waited for.
     */
    private fun watchUpdate(nodeUpdating: Boolean) {
        val text = TextView(this).apply { setText(R.string.raycontrol_admin_update_waiting) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_update)
            .setView(padded(text))
            .setNegativeButton(R.string.raycontrol_admin_update_close, null)
            .create()
        val job = lifecycleScope.launch {
            val started = System.currentTimeMillis()
            var ended = !nodeUpdating
            // the node's update asked for and still not requested or running a minute on: none to wait for
            var gaveUp = false
            while (isActive) {
                val update = withContext(Dispatchers.IO) { runCatching { call("GetUpdateStatus") } }.getOrNull()
                val status = update?.getAsJsonObject("status")
                val show = {
                    val node = when {
                        status != null -> updateState(status) ?: getString(
                            if (nodeUpdating && !gaveUp) R.string.raycontrol_admin_update_waiting else R.string.raycontrol_admin_update_idle)
                        else -> getString(R.string.raycontrol_admin_update_unreachable)
                    }
                    text.text = listOfNotNull(node, update?.let { relaysText(it) }).joinToString("\n\n")
                }
                show()
                if (!ended && status?.s("state") in setOf("done", "rolled_back", "failed")) {
                    ended = true
                    if (tab == STATUS) loadStatus()
                }
                if (!ended && update != null && status?.s("state") !in setOf("requested", "running") &&
                    System.currentTimeMillis() - started > 60_000L) {
                    // asked for and gone a minute on without being requested or running (StartUpdate refused for
                    // another reason than one under way, an idle status after a restart, a state this build does not
                    // know): no outcome of the node's to wait for, only its relays; said so, not "waiting"
                    ended = true
                    gaveUp = true
                    show()
                    if (tab == STATUS) loadStatus()
                }
                val elapsed = System.currentTimeMillis() - started
                if (ended && update != null && !relaysPending(update)) return@launch
                if (!ended && elapsed > 20 * 60_000L) {
                    // no outcome is not a failure: the node turns a run without one into failed only after an hour
                    text.text = getString(R.string.raycontrol_admin_update_no_outcome)
                    return@launch
                }
                if (ended && elapsed > 3 * 60 * 60_000L) return@launch
                delay(if (ended) 30_000L else 7_000L)
            }
        }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    // ---- the app's releases on the node (app-update-v1)

    private fun mirrorSummary(mirror: JsonObject): String {
        if (!mirror.b("enabled")) return getString(R.string.raycontrol_admin_mirror_off)
        val held = mirror.arr("releases").joinToString(", ") { r ->
            "${r.s("tag")} (${Formatter.formatFileSize(this, r.l("bytes"))})"
        }.ifEmpty { getString(R.string.raycontrol_admin_mirror_empty) }
        val parts = mutableListOf(getString(R.string.raycontrol_admin_mirror_on, held))
        parts += getString(R.string.raycontrol_admin_mirror_checked, date(mirror.l("checked")))
        mirror.s("error").takeIf { it.isNotBlank() }?.let { parts += getString(R.string.raycontrol_admin_mirror_error, it) }
        return parts.joinToString("\n")
    }

    private fun mirrorActions(mirror: JsonObject) {
        val on = mirror.b("enabled")
        confirm(getString(if (on) R.string.raycontrol_admin_mirror_turn_off else R.string.raycontrol_admin_mirror_turn_on)) {
            api({ call("SetAppMirror", body("enabled" to !on)) }) { loadStatus() }
        }
    }

    // ---- client summaries (probe-v1 §6)

    private fun summarySummary(summary: JsonObject): String {
        if (!summary.b("enabled")) return getString(R.string.raycontrol_admin_summary_off)
        return getString(R.string.raycontrol_admin_summary_on, summary.arr("networks").size, summary.l("otherReports"),
            summary.l("days").takeIf { it > 0 } ?: 7)
    }

    private fun summaryActions(summary: JsonObject) {
        val on = summary.b("enabled")
        val actions = mutableListOf<Pair<Int, () -> Unit>>(
            (if (on) R.string.disable else R.string.enable) to {
                api({ call("SetSummary", body("enabled" to if (on) "off" else "on")) }) { loadStatus() }
            },
        )
        if (on) actions += R.string.raycontrol_admin_summary_networks to {
            val text = summary.arr("networks").joinToString("\n") { n ->
                "AS${n.l("asn")} ${n.s("name")} ${n.s("country")}: ${n.l("reports")}"
            }.ifEmpty { getString(R.string.raycontrol_admin_empty) }
            textDialog(getString(R.string.raycontrol_admin_summary_networks), text)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_summary)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMetrics(): Unit = api({ call("GetMetrics").s("text") }) { text ->
        // the samples only: the HELP and TYPE lines double the size
        val samples = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.joinToString("\n")
        textDialog(getString(R.string.raycontrol_admin_metrics), samples.ifEmpty { text })
    }

    /**
     * Every port of the node for a provider's firewall (GetPorts, raycontrol v0.27.0), a line each. A node before it
     * answers unimplemented: said once, and the row goes.
     */
    private fun showPorts(): Unit = api({ call("GetPorts").arr("ports") }, quiet = { RayApi.unimplemented(it) }, failed = { e ->
        if (RayApi.unimplemented(e)) {
            noPorts = true
            snackbar(R.string.raycontrol_admin_ports_old).show()
            if (tab == STATUS) loadStatus()
        }
    }) { ports ->
        val time = DateFormat.getTimeInstance(DateFormat.SHORT)
        val lines = ports.joinToString("\n") { p -> portLine(p, texts) { time.format(Date(it * 1000)) } }
            .ifEmpty { getString(R.string.raycontrol_admin_empty) }
        textDialog(getString(R.string.raycontrol_admin_ports), getString(R.string.raycontrol_admin_ports_help) + "\n\n" + lines)
    }

    private fun editAdminAccess(current: List<String>) {
        val input = editText(getString(R.string.raycontrol_admin_access_hint), current.joinToString("\n"), multiLine = true)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_access)
            .setMessage(R.string.raycontrol_admin_access_help)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val cidrs = input.text.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                api({ call("SetAdminAccess", body("cidrs" to cidrs)) }) { loadStatus() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- users

    private fun loadUsers(): Unit = api({ call("ListUsers").arr("users") to call("ListServices") }) { (users, list) ->
        services = list.arr("services")
        kinds = list.strs("kinds")
        // how a user gets the node, and the way to make one, above the users
        show(listOf(Row("＋ " + getString(R.string.raycontrol_admin_user_new), getString(R.string.raycontrol_admin_users_help)) { createUser() }) +
            users.map { u ->
            val title = u.s("name") + if (u.b("enabled")) "" else " · " + getString(R.string.raycontrol_admin_disabled)
            val parts = mutableListOf(u.strs("services").joinToString(", ").ifEmpty { getString(R.string.raycontrol_admin_no_services) })
            parts += u.l("expiresAt").let { if (it == 0L) getString(R.string.raycontrol_admin_no_expiry) else getString(R.string.raycontrol_admin_expires, date(it)) }
            parts += u.l("trafficLimitBytes").let { if (it == 0L) getString(R.string.raycontrol_admin_no_limit) else getString(R.string.raycontrol_admin_limit, Formatter.formatFileSize(this, it)) }
            u.s("note").takeIf { it.isNotBlank() }?.let { parts += it }
            Row(title, parts.joinToString(" · ")) { userActions(u) }
        })
    }

    private fun userActions(user: JsonObject) {
        val name = user.s("name")
        val enabled = user.b("enabled")
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.raycontrol_admin_invite to { invite(name) },
            R.string.raycontrol_admin_devices to { showDevices(name) },
            R.string.raycontrol_admin_user_services to { chooseServices(user.strs("services")) { chosen ->
                api({ call("SetUserServices", body("name" to name, "services" to chosen)) }) { loadUsers() }
            } },
            R.string.raycontrol_admin_links to { exportLinks(name) },
            R.string.raycontrol_admin_renew to { renew(name) },
            (if (enabled) R.string.disable else R.string.enable) to {
                api({ call("SetUserEnabled", body("name" to name, "enabled" to !enabled)) }) { loadUsers() }
            },
            R.string.delete to { confirm(getString(R.string.raycontrol_admin_delete_user, name)) {
                api({ call("DeleteUser", body("name" to name)) }) { loadUsers() }
            } },
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A user in one form: their limits and the node's services they get (the enabled ones ticked), then a device's invite. */
    private fun createUser() {
        val name = editText(getString(R.string.raycontrol_admin_user_name))
        val days = editText(getString(R.string.raycontrol_admin_user_days), "0", number = true)
        val gb = editText(getString(R.string.raycontrol_admin_user_gb), "0", number = true)
        val note = editText(getString(R.string.raycontrol_admin_user_note))
        val boxes = services.map { s ->
            CheckBox(this).apply {
                text = "${s.s("name")} (${s.s("kind")})"
                isChecked = s.b("enabled")
                tag = s.s("name")
            }
        }
        val header = TextView(this).apply {
            setText(if (boxes.isEmpty()) R.string.raycontrol_admin_user_no_services else R.string.raycontrol_admin_user_services)
            setPadding(0, 24, 0, 0)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_user_new)
            .setView(ScrollView(this).apply { addView(padded(name, days, gb, note, header, *boxes.toTypedArray())) })
            .setPositiveButton(R.string.raycontrol_admin_user_create) { _, _ ->
                val userName = name.text.toString().trim()
                if (userName.isEmpty()) return@setPositiveButton snackbar(R.string.raycontrol_admin_user_name_required).show()
                val expires = days.text.toString().toLongOrNull()?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it * 86400 } ?: 0L
                val limit = gb.text.toString().toLongOrNull()?.takeIf { it > 0 }?.let { it * 1_000_000_000L } ?: 0L
                val chosen = boxes.filter { it.isChecked }.map { it.tag as String }
                api({
                    call("CreateUser", body("name" to userName, "expiresAt" to expires.toString(),
                        "trafficLimitBytes" to limit.toString(), "note" to note.text.toString().trim(), "services" to chosen))
                }, failed = { e ->
                    // created with its answer lost: the user may be there, the list shows it; no invite on its own,
                    // as "already exists" may be another user of that name (shown as the error it is)
                    if (lost(e)) loadUsers()
                }) {
                    loadUsers()
                    // a user reaches the node through their devices: the first one's invite now
                    invite(userName, created = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * New credentials for a user at once (RenewUser, raycontrol v0.27.0), for a leak: asked first, as every device of
     * the user is cut until it fetches its subscription. A node before it answers unimplemented.
     */
    private fun renew(user: String) = confirm(getString(R.string.raycontrol_admin_renew_confirm, user)) {
        api({ call("RenewUser", body("name" to user)) }, quiet = { RayApi.unimplemented(it) }, failed = { e ->
            if (RayApi.unimplemented(e)) snackbar(R.string.raycontrol_admin_renew_old).show()
        }) { answer -> message(renewText(answer, user, texts)) }
    }

    private fun chooseServices(current: List<String>, done: (List<String>) -> Unit) {
        val names = services.map { it.s("name") }
        if (names.isEmpty()) return done(emptyList())
        val checked = BooleanArray(names.size) { names[it] in current }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_user_services)
            .setMultiChoiceItems(names.map { n -> "$n (${services.first { it.s("name") == n }.s("kind")})" }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton(android.R.string.ok) { _, _ -> done(names.filterIndexed { i, _ -> checked[i] }) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- devices and invites

    private fun showDevices(user: String): Unit = api({ call("ListDevices", body("user" to user)) }) { list ->
        val rows = list.arr("devices").map { d ->
            val state = if (d.l("revokedAt") != 0L) getString(R.string.raycontrol_admin_revoked) else getString(R.string.raycontrol_admin_active)
            val via = if (d.b("registered")) " · " + getString(R.string.raycontrol_admin_registered) else ""
            Row("${d.s("name")} · ${d.s("role")}", "$state$via · ${date(d.l("createdAt"))}") {
                if (d.l("revokedAt") == 0L) revoke(user, d.s("id"), d.s("name"), invite = false)
            }
        } + list.arr("invites").map { inv ->
            Row("${inv.s("name")} · ${inv.s("role")}", getString(R.string.raycontrol_admin_invite_pending, date(inv.l("expiresAt")))) {
                revoke(user, inv.s("id"), inv.s("name"), invite = true)
            }
        }
        val labels = rows.map { "${it.title}\n${it.summary}" }.ifEmpty { listOf(getString(R.string.raycontrol_admin_empty)) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.raycontrol_admin_devices_of, user))
            .setItems(labels.toTypedArray()) { _, i -> rows.getOrNull(i)?.action?.invoke() }
            .setPositiveButton(R.string.raycontrol_admin_invite) { _, _ -> invite(user) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * A device revoked, or an unused invite withdrawn (RevokeDevice). Since raycontrol v0.27.0 a revocation gives the
     * device's user new credentials, which cuts the user's other devices until they fetch their subscription: asked
     * with that, and said after. An older node says nothing of it, and the list shows what changed.
     */
    private fun revoke(user: String, id: String, name: String, invite: Boolean): Unit =
        confirm(getString(if (invite) R.string.raycontrol_admin_invite_withdraw else R.string.raycontrol_admin_revoke, user, name)) {
            api({ call("RevokeDevice", body("id" to id)) }) { answer ->
                val text = revokeText(answer, user, name, texts)
                if (text == null) showDevices(user) else message(text) { showDevices(user) }
            }
        }

    /**
     * One device's invite for [user] in one form: its name, its role, how long the invite holds, and a PIN told by
     * another channel. The short RCINV2 code where the node gives one: a smaller QR.
     */
    private fun invite(user: String, created: Boolean = false) {
        val help = TextView(this).apply {
            text = getString(if (created) R.string.raycontrol_admin_invite_help_created else R.string.raycontrol_admin_invite_help, user)
            setPadding(0, 0, 0, 16)
        }
        val name = editText(getString(R.string.raycontrol_admin_device_name), "phone")
        val roles = arrayOf("user", "admin")
        val role = radios(getString(R.string.raycontrol_admin_invite_role),
            listOf(getString(R.string.raycontrol_admin_role_user), getString(R.string.raycontrol_admin_role_admin)), 0)
        val ttls = longArrayOf(3600, 86400, 7 * 86400)
        val ttl = radios(getString(R.string.raycontrol_admin_invite_ttl), resources.getStringArray(R.array.raycontrol_admin_ttls).toList(), 1)
        val pin = CheckBox(this).apply { setText(R.string.raycontrol_admin_invite_pin) }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.raycontrol_admin_invite_for, user))
            .setView(ScrollView(this).apply { addView(padded(help, name, role.first, role.second, ttl.first, ttl.second, pin)) })
            .setPositiveButton(R.string.raycontrol_admin_invite_create) { _, _ ->
                val deviceName = name.text.toString().trim().ifEmpty { "phone" }
                val request = body("user" to user, "name" to deviceName, "role" to roles[role.second.selected()],
                    "ttlSeconds" to ttls[ttl.second.selected()].toString())
                // only when asked: a node before the PIN (api-v1 §3) refuses a field it does not know
                if (pin.isChecked) request.addProperty("pin", true)
                api({ call("CreateInvite", request) }) { answer ->
                    val code = answer.s("inviteCodeShort").ifEmpty { answer.s("inviteCode") }
                    val expires = answer.getAsJsonObject("invite")?.l("expiresAt") ?: 0L
                    var message = getString(R.string.raycontrol_admin_invite_created, user, deviceName, date(expires))
                    answer.s("pin").takeIf { it.isNotEmpty() }?.let { message += "\n\n" + getString(R.string.raycontrol_admin_invite_pin_is, it) }
                    showInvite(code, message)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A titled group of choices, [checked] picked. */
    private fun radios(title: String, labels: List<String>, checked: Int): Pair<TextView, RadioGroup> {
        val label = TextView(this).apply {
            text = title
            setPadding(0, 24, 0, 0)
        }
        val group = RadioGroup(this).apply {
            labels.forEachIndexed { i, text ->
                addView(RadioButton(this@RayAdminActivity).apply {
                    id = android.view.View.generateViewId()
                    this.text = text
                    isChecked = i == checked
                })
            }
        }
        return label to group
    }

    private fun RadioGroup.selected() = (0 until childCount).firstOrNull { (getChildAt(it) as RadioButton).isChecked } ?: 0

    /**
     * The user's profiles as links (ExportUser), for apps other than Niko: they hold the user's keys, and such an app
     * does not knock, so it reaches only what is open to everyone. Audited by the node.
     */
    private fun exportLinks(user: String): Unit = api({ call("ExportUser", body("name" to user)).arr("profiles") }) { profiles ->
        val links = profiles.mapNotNull { p -> p.s("link").takeIf { it.isNotBlank() } }
        if (links.isEmpty()) return@api snackbar(R.string.raycontrol_admin_empty).show()
        val text = links.joinToString("\n")
        val view = TextView(this).apply {
            this.text = getString(R.string.raycontrol_admin_links_help, user) + "\n\n" +
                profiles.joinToString("\n\n") { p -> "${p.s("service")}:\n${p.s("link")}" }
            setTextIsSelectable(true)
            textSize = 12f
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_links)
            .setView(ScrollView(this).apply { addView(padded(view)) })
            .setOnDismissListener { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            .setPositiveButton(R.string.share) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.share)))
            }
            .setNeutralButton(R.string.action_copy) { _, _ ->
                if (SagerNet.trySetPrimaryClip(text, sensitive = true)) snackbar(R.string.copy_success).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply { window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            .show()
    }

    /** The invite as a QR code to scan from the new device, or to share or copy as text. */
    private fun showInvite(code: String, message: String) {
        val size = (resources.displayMetrics.widthPixels * 0.7).toInt()
        val image = ImageView(this).apply {
            try {
                setImageBitmap(qr(code, size))
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
        val text = TextView(this).apply {
            this.text = message
            setPadding(0, 0, 0, 24)
        }
        // the invite registers a device: no screenshots of the dialog (its own window) and no recent-apps
        // thumbnail of the activity while it is shown
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_invite)
            .setView(padded(text, image))
            .setOnDismissListener { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            .setPositiveButton(R.string.share) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code), getString(R.string.share)))
            }
            .setNeutralButton(R.string.action_copy) { _, _ ->
                if (SagerNet.trySetPrimaryClip(code, sensitive = true)) snackbar(R.string.copy_success).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply { window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            .show()
    }

    private fun qr(text: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(matrix.width * matrix.height) {
            if (matrix.get(it % matrix.width, it / matrix.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
    }

    // ---- services

    private fun loadServices(): Unit = api({ call("ListServices") }) { list ->
        services = list.arr("services")
        kinds = list.strs("kinds")
        show(services.map { s ->
            val state = if (s.b("enabled")) getString(R.string.raycontrol_admin_active) else getString(R.string.raycontrol_admin_disabled)
            Row("${s.s("name")} (${s.s("kind")})", "$state · id ${s.l("id")} · ${date(s.l("createdAt"))}") { serviceActions(s) }
        })
    }

    private fun serviceActions(service: JsonObject) {
        val name = service.s("name")
        val enabled = service.b("enabled")
        val actions = mutableListOf<Pair<Int, () -> Unit>>(
            (if (enabled) R.string.disable else R.string.enable) to { setServiceEnabled(service, !enabled) },
        )
        val kind = service.s("kind")
        if (optionsOf(service).isNotEmpty()) actions += R.string.raycontrol_admin_options to { serviceOptions(service) }
        if (kind.contains("reality")) actions += R.string.raycontrol_admin_rotate to { rotate(name) }
        // dnstt's seal key (dnsseal-v1 §8), masterdns's service key (masterdns-key-rotation §2): a new one at once
        if (kind == "dnstt") actions += R.string.raycontrol_admin_rotate_seal to { rotateKey(service, "seal_prev_until", R.string.raycontrol_admin_rotate_seal) }
        if (kind == "masterdns") actions += R.string.raycontrol_admin_rotate_service to { rotateKey(service, "key_prev_until", R.string.raycontrol_admin_rotate_service) }
        actions += R.string.delete to { confirm(getString(R.string.raycontrol_admin_delete_service, name)) { deleteService(service) } }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // raycontrol v0.27.0: the node's last enabled ssh service is not taken out unless forced (failed_precondition), as
    // SSH is then closed to everyone at the next snapshot, the admin addresses included. Asked here, and forced only on
    // a yes: a node before v0.27.0 has no such field and sees the request as before
    private fun setServiceEnabled(service: JsonObject, enabled: Boolean, force: Boolean = false) {
        val name = service.s("name")
        val refused = { e: Throwable -> !enabled && lastSshRefused(service.s("kind"), RayApi.code(e), force) }
        api({ call("SetServiceEnabled", body("name" to name, "enabled" to enabled, "force" to force.takeIf { it })) },
            quiet = refused,
            failed = { e ->
                if (refused(e)) confirm(getString(R.string.raycontrol_admin_last_ssh_disable, name)) {
                    setServiceEnabled(service, false, force = true)
                }
            },
        ) { loadServices() }
    }

    private fun deleteService(service: JsonObject, force: Boolean = false) {
        val name = service.s("name")
        val refused = { e: Throwable -> lastSshRefused(service.s("kind"), RayApi.code(e), force) }
        api({ call("DeleteService", body("name" to name, "force" to force.takeIf { it })) },
            quiet = refused,
            failed = { e ->
                if (refused(e)) confirm(getString(R.string.raycontrol_admin_last_ssh_delete, name)) { deleteService(service, force = true) }
            },
        ) { loadServices() }
    }

    /** REALITY keys: on the lead time (clients get the next keys first), or now (a leaked key, a blocked name). */
    private fun rotate(name: String) {
        val options = arrayOf(getString(R.string.raycontrol_admin_rotate_planned), getString(R.string.raycontrol_admin_rotate_now))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_rotate)
            .setItems(options) { _, i ->
                api({ call("RotateService", body("name" to name, "now" to (i == 1))) }) { answer ->
                    snackbar(getString(R.string.raycontrol_admin_rotate_at, date(answer.l("switchAt")))).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * A key that switches at once (a dnstt seal key, a masterdns service key): the previous one accepted for the
     * credential grace, so clients that have not fetched their subscription go on; or not at all (a leaked key).
     */
    private fun rotateKey(service: JsonObject, prevUntilKey: String, title: Int) {
        val name = service.s("name")
        // a rotation still in its grace (the previous key accepted until then): a second one drops that key, which the
        // clients that have not fetched since still use, as a rotation "now" would. Asked first, as a retry after an
        // answer that was lost would do it unasked
        val prevUntil = runCatching {
            com.google.gson.JsonParser.parseString(service.s("settingsJson")).asJsonObject.get(prevUntilKey)?.asLong
        }.getOrNull() ?: 0L
        val inGrace = prevUntil > System.currentTimeMillis() / 1000
        val options = arrayOf(getString(R.string.raycontrol_admin_rotate_key_grace), getString(R.string.raycontrol_admin_rotate_key_now))
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(options) { _, i ->
                when {
                    i == 1 -> confirm(getString(R.string.raycontrol_admin_rotate_key_now_confirm)) { rotateKeyNow(name, true) }
                    inGrace -> confirm(getString(R.string.raycontrol_admin_rotate_key_in_grace, date(prevUntil))) { rotateKeyNow(name, false, force = true) }
                    else -> rotateKeyNow(name, false)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // raycontrol v0.24.0: a switch with a grace while the previous key is in its own is refused (failed_precondition)
    // unless forced. It is asked about here where this list did not show that grace (a list from before another
    // admin's rotation, or this one's own whose answer was lost): the time is the node's
    private fun graceRefused(now: Boolean, force: Boolean, e: Throwable) =
        !now && !force && RayApi.code(e) == "failed_precondition" && RayApi.graceUntil(e) > 0

    private fun rotateKeyNow(name: String, now: Boolean, force: Boolean = false): Unit = api(
        // force only when asked: a node before v0.24.0 has no such field and switches as it did
        { call("RotateService", body("name" to name, "now" to now, "force" to force.takeIf { it })) },
        quiet = { e -> graceRefused(now, force, e) },
        failed = { e ->
            when {
                // it may have rotated: the list shows the rotation (the grace guards a second one), never a retry here
                lost(e) -> loadServices()
                graceRefused(now, force, e) -> {
                    loadServices()
                    confirm(getString(R.string.raycontrol_admin_rotate_key_in_grace, date(RayApi.graceUntil(e)))) {
                        rotateKeyNow(name, false, force = true)
                    }
                }
            }
        },
    ) { answer ->
        val until = answer.l("prevUntil")
        snackbar(if (until > 0) getString(R.string.raycontrol_admin_rotate_key_until, date(until))
        else getString(R.string.raycontrol_admin_rotate_key_gone)).show()
        loadServices()
    }

    /**
     * A setting of a service that may change after its creation (SetServiceOption): its key, what it takes (values to
     * pick from, else a number, or text when [text]).
     */
    private class Option(val key: String, val title: Int, val values: List<String>?, val current: String, val text: Boolean = false)

    private fun optionsOf(service: JsonObject): List<Option> {
        val settings = runCatching { com.google.gson.JsonParser.parseString(service.s("settingsJson")).asJsonObject }
            .getOrNull() ?: JsonObject()
        // the setting as kept (a list: its size, spare_zones's count), "—" where the node keeps its default
        fun now(key: String) = settings.get(key)?.let {
            when {
                it.isJsonArray -> it.asJsonArray.size().toString()
                it.isJsonPrimitive -> it.asString
                else -> null
            }
        } ?: "—"
        fun list(key: String) = settings.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString } ?: emptyList()
        val modes = listOf("off", "optional", "required")
        val onOff = listOf("true", "false")
        return when (service.s("kind")) {
            "dnstt", "masterdns" -> listOf(
                Option("auth", R.string.raycontrol_admin_option_auth, modes, now("auth")),
                Option("auth_hint", R.string.raycontrol_admin_option_auth_hint, modes, now("auth_hint")),
                Option("seal", R.string.raycontrol_admin_option_seal, modes, now("seal")),
                Option("spare_zones", R.string.raycontrol_admin_option_spare_zones, null, now("spare_zones")),
            ) + listOfNotNull(
                // a spare zone made the main one (dnszones-v1 §5): clients have it already; the main one leaves
                list("spare_zones").takeIf { it.isNotEmpty() }?.let { Option("zone", R.string.raycontrol_admin_option_zone, it, now("zone")) },
            )
            "slipstream" -> listOf(
                Option("max_connections", R.string.raycontrol_admin_option_max_connections, null, now("max_connections")),
                Option("device_connections", R.string.raycontrol_admin_option_device_connections, null, now("device_connections")),
            )
            "site" -> listOf(Option("answer", R.string.raycontrol_admin_option_answer, listOf("404", "403", "silent", "pages"), now("answer")))
            "reality" -> listOfNotNull(
                Option("rotate_sni", R.string.raycontrol_admin_option_rotate_sni, onOff, now("rotate_sni")),
                Option("open", R.string.raycontrol_admin_option_open, onOff, now("open")),
                // who answers those who are not clients (raycontrol v0.27.0), for a target of the service's own: one
                // that steals from the node's site has that site's name, which no foreign target has a certificate
                // for, and the node refuses it
                Option("target", R.string.raycontrol_admin_option_target, null, now("target"), text = true)
                    .takeIf { settings.get("site")?.takeIf { it.isJsonPrimitive }?.asString.isNullOrEmpty() },
            )
            "hy2-masq", "hy2-obfs" -> listOf(
                Option("up_mbps", R.string.raycontrol_admin_option_up_mbps, null, now("up_mbps")),
                Option("down_mbps", R.string.raycontrol_admin_option_down_mbps, null, now("down_mbps")),
            )
            "yggdrasil" -> listOf(
                Option("peers", R.string.raycontrol_admin_option_peers, null, list("peers").joinToString(", ").ifEmpty { "—" }, text = true),
            )
            else -> emptyList()
        }
    }

    /** The service's settings: each with its value now; the node checks what it takes and says why not. */
    private fun serviceOptions(service: JsonObject) {
        val name = service.s("name")
        val options = optionsOf(service)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.raycontrol_admin_options_of, name))
            .setItems(options.map { "${getString(it.title)}: ${it.current}" }.toTypedArray()) { _, i ->
                val option = options[i]
                // applied at once (rayd renders and restarts the service): asked first, a wrong tap may cut its clients
                val set = { value: String ->
                    confirm(getString(R.string.raycontrol_admin_option_confirm, getString(option.title), name, option.current, value)) {
                        api({ call("SetServiceOption", body("name" to name, "key" to option.key, "value" to value)) }) {
                            snackbar(getString(R.string.raycontrol_admin_option_set, option.key, value)).show()
                            loadServices()
                        }
                    }
                }
                if (option.key == "target") {
                    editTarget(name, option)
                } else if (option.values != null) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(option.title)
                        .setSingleChoiceItems(option.values.toTypedArray(), option.values.indexOf(option.current)) { dialog, which ->
                            dialog.dismiss()
                            set(option.values[which])
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    val input = if (option.text) editText(getString(option.title), option.current.takeIf { it != "—" } ?: "")
                    else editText(getString(option.title), option.current.takeIf { it != "—" } ?: "0", number = true)
                    MaterialAlertDialogBuilder(this)
                        .setTitle(option.title)
                        .setView(padded(input))
                        .setPositiveButton(android.R.string.ok) { _, _ -> set(input.text.toString().trim()) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * A REALITY service's target (SetServiceOption target, raycontrol v0.27.0): its form checked here, the rest by the
     * node, which also reaches it as a client would before it takes it. Refused, the dialog comes back with what was
     * typed and the node's words.
     */
    private fun editTarget(name: String, option: Option, typed: String? = null, error: String? = null) {
        val input = editText(getString(option.title), typed ?: option.current.takeIf { it != "—" }.orEmpty())
        val help = getString(R.string.raycontrol_admin_option_target_help)
        MaterialAlertDialogBuilder(this)
            .setTitle(option.title)
            .setMessage(if (error == null) help else "$help\n\n⚠ $error")
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString()
                val target = realityTarget(text)
                if (target == null) {
                    editTarget(name, option, text, getString(R.string.raycontrol_admin_option_target_bad))
                    return@setPositiveButton
                }
                confirm(getString(R.string.raycontrol_admin_option_target_confirm, name, option.current, target)) {
                    api({ call("SetServiceOption", body("name" to name, "key" to "target", "value" to target)) },
                        quiet = { e -> RayApi.code(e) == "invalid_argument" || targetUnknown(e.message) },
                        failed = { e ->
                            when {
                                // a node before v0.27.0 has no such option
                                targetUnknown(e.message) -> snackbar(R.string.raycontrol_admin_option_target_old).show()
                                RayApi.code(e) == "invalid_argument" -> editTarget(name, option, target, nodeMessage(e.message.orEmpty()))
                            }
                        },
                    ) {
                        snackbar(getString(R.string.raycontrol_admin_option_set, option.key, target)).show()
                        loadServices()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createService() {
        if (kinds.isEmpty()) return loadServices()
        var kind = 0
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_service_kind)
            .setSingleChoiceItems(kinds.toTypedArray(), kind) { _, i -> kind = i }
            .setPositiveButton(R.string.raycontrol_admin_next) { _, _ ->
                val name = editText(getString(R.string.raycontrol_admin_service_name), kinds[kind])
                MaterialAlertDialogBuilder(this)
                    .setTitle(kinds[kind])
                    .setMessage(R.string.raycontrol_admin_service_apply)
                    .setView(padded(name))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        api({ call("CreateService", body("kind" to kinds[kind], "name" to name.text.toString().trim())) }) { loadServices() }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- network: the node's DNS (its zone at the provider, the tunnels' zones) and its relays

    private fun loadNetwork(): Unit = api({
        Triple(optional { call("GetDNS") }, optional { call("ListRelays").arr("relays") },
            // the last periodic check (now=false runs none); raycontrol v0.13.0, and a rayd that checks zones
            runCatching { optional { call("CheckDNSZones") } }.getOrNull())
    }) { (dns, relays, zones) ->
        val rows = mutableListOf<Row>()
        dns?.getAsJsonObject("status")?.let { status ->
            val provider = status.s("provider")
            val summary = if (provider.isEmpty()) getString(R.string.raycontrol_admin_dns_none) else {
                val parts = mutableListOf("$provider · ${status.s("zone")}",
                    getString(R.string.raycontrol_admin_dns_records, status.arr("records").size))
                status.strs("problems").forEach { parts += "⚠ $it" }
                parts.joinToString("\n")
            }
            rows += Row(getString(R.string.raycontrol_admin_dns), summary) { dnsActions(provider.isNotEmpty()) }
        } ?: run { if (dns == null) rows += Row(getString(R.string.raycontrol_admin_dns), getString(R.string.raycontrol_admin_dns_none)) { dnsActions(false) } }
        zones?.let { last ->
            val checked = last.l("checkedAt")
            val states = last.arr("zones").groupingBy { it.s("state").ifEmpty { "?" } }.eachCount()
                .entries.joinToString(", ") { "${it.key} ${it.value}" }
            val summary = if (checked == 0L) getString(R.string.raycontrol_admin_dns_check_summary)
            else getString(R.string.raycontrol_admin_dns_check_last, date(checked), states.ifEmpty { "—" })
            rows += Row(getString(R.string.raycontrol_admin_dns_check), summary) { checkZones() }
        }
        relays?.forEach { r ->
            val probe = r.l("probePort").takeIf { it > 0 }?.let { " · probe $it" }.orEmpty()
            rows += Row(getString(R.string.raycontrol_admin_relay, r.s("name")), "${r.s("address")} · ${date(r.l("createdAt"))}$probe") {
                relayAccess(r.s("name"))
            }
        }
        // AddRelay, RemoveRelay and a relay's bundle are root's on the node only (api-v1 §5)
        rows += Row(getString(R.string.raycontrol_admin_relays_root))
        show(rows)
    }

    private fun dnsActions(configured: Boolean) {
        val actions = mutableListOf<Pair<Int, () -> Unit>>()
        if (configured) actions += R.string.raycontrol_admin_dns_sync to {
            api({ call("SyncDNS").getAsJsonObject("status") ?: JsonObject() }) { status ->
                val text = (status.strs("changes") + status.strs("problems").map { "⚠ $it" })
                    .ifEmpty { listOf(getString(R.string.raycontrol_admin_dns_in_sync)) }.joinToString("\n")
                textDialog(getString(R.string.raycontrol_admin_dns_sync), text)
                loadNetwork()
            }
        }
        actions += R.string.raycontrol_admin_dns_token to { dnsToken() }
        if (configured) actions += R.string.raycontrol_admin_dns_remove to {
            confirm(getString(R.string.raycontrol_admin_dns_remove_confirm)) {
                api({ call("RemoveDNS", body("keepRecords" to false)) }) { loadNetwork() }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_dns)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The provider's API token (Cloudflare): kept by rayd, never shown again. */
    private fun dnsToken() {
        val input = editText(getString(R.string.raycontrol_admin_dns_token_hint)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_dns_token)
            .setMessage(R.string.raycontrol_admin_dns_token_help)
            .setView(padded(input))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                api({ call("SetDNSProvider", body("provider" to "cloudflare", "token" to input.text.toString().trim())) }) { loadNetwork() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The tunnels' zones from the node (CheckDNSZones now): the delegation, the front and backend, the resolvers. */
    private fun checkZones(): Unit = api({ call("CheckDNSZones", body("now" to true)) }) { answer ->
        val zones = answer.arr("zones")
        val text = zones.joinToString("\n\n") { z ->
            val lines = mutableListOf("${z.s("zone")} (${z.s("service")}, ${z.s("kind")}): ${z.s("state")}")
            z.s("summary").takeIf { it.isNotBlank() }?.let { lines += it }
            z.s("error").takeIf { it.isNotBlank() }?.let { lines += "⚠ $it" }
            for (step in listOf("parent", "front", "backend")) {
                z.getAsJsonObject(step)?.let { st ->
                    val fix = st.s("fix").takeIf { it.isNotBlank() }?.let { " → $it" }.orEmpty()
                    lines += "  $step: ${st.s("state")} ${st.s("detail")}$fix"
                }
            }
            z.arr("resolvers").forEach { r ->
                val worst = listOf("soa", "small", "large").mapNotNull { r.getAsJsonObject(it)?.s("state") }
                    .firstOrNull { it == "fail" } ?: listOf("soa", "small", "large").mapNotNull { r.getAsJsonObject(it)?.s("state") }
                    .firstOrNull { it == "warn" } ?: "ok"
                lines += "  ${r.s("resolver")} ${r.s("network")}: $worst"
            }
            lines.joinToString("\n")
        }.ifEmpty { getString(R.string.raycontrol_admin_empty) }
        val head = getString(R.string.raycontrol_admin_dns_checked, date(answer.l("checkedAt"))) +
                if (answer.b("limited")) "\n" + getString(R.string.raycontrol_admin_dns_check_limited) else ""
        textDialog(getString(R.string.raycontrol_admin_dns_check), head + "\n\n" + text)
    }

    /** A relay host's SSH: its port and the addresses that reach it without SPA (SetRelayAccess; empty keeps). */
    private fun relayAccess(name: String) {
        val port = editText(getString(R.string.raycontrol_admin_relay_ssh_port), number = true)
        val admin = editText(getString(R.string.raycontrol_admin_access_hint), multiLine = true)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.raycontrol_admin_relay, name))
            .setMessage(R.string.raycontrol_admin_relay_access_help)
            .setView(padded(port, admin))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val cidrs = admin.text.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                api({
                    call("SetRelayAccess", body("name" to name, "sshPort" to (port.text.toString().toIntOrNull() ?: 0),
                        "admin" to cidrs))
                }) { loadNetwork() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- audit

    private fun loadAudit(): Unit = api({ call("ListAudit", body("limit" to 200)).arr("entries") }) { entries ->
        show(entries.map { e ->
            Row("${e.s("action")} ${e.s("target")}", "${date(e.l("time"))} · ${e.s("actor")} · ${e.s("result")}")
        })
    }

    // ---- small UI helpers

    private fun date(seconds: Long) = if (seconds == 0L) "—" else
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds * 1000))

    private fun editText(hint: String, value: String = "", number: Boolean = false, multiLine: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER
            multiLine -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
    }

    private fun padded(vararg views: android.view.View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        views.forEach { addView(it) }
    }

    private fun confirm(message: String, yes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ -> yes() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** A message to read, longer than a snackbar stays for; [then] once it is closed. */
    private fun message(text: String, then: () -> Unit = {}) {
        MaterialAlertDialogBuilder(this)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { then() }
            .show()
    }

    private fun textDialog(title: String, text: String) {
        val view = TextView(this).apply {
            this.text = text
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(android.widget.ScrollView(this).apply { addView(padded(view)) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        var rows: List<Row> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            RowHolder(LayoutRayAdminItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = rows[position]
            holder.binding.title.text = row.title
            holder.binding.summary.text = row.summary
            holder.binding.summary.visibility = if (row.summary.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            holder.binding.root.setOnClickListener(row.action?.let { { _: android.view.View -> it() } })
            holder.binding.root.isClickable = row.action != null
        }
    }

    private class RowHolder(val binding: LayoutRayAdminItemBinding) : RecyclerView.ViewHolder(binding.root)
}

/** A string with its arguments, as the activity's getString gives it: the parts below take one, and tests their own. */
internal class AdminTexts(private val get: (Int, Array<out Any>) -> String) {
    operator fun invoke(id: Int, vararg args: Any) = get(id, args)
}

/**
 * What a revocation did, from RevokeDeviceResponse (raycontrol v0.27.0): an unused invite withdrawn, a device revoked
 * already, or the device revoked and its user's credentials new, which cuts the user's other devices until they fetch
 * their subscription. Null from a node before v0.27.0, which answers with none of it and changes no credentials.
 */
internal fun revokeText(answer: JsonObject, user: String, name: String, t: AdminTexts): String? {
    if (answer.b("invite")) return t(R.string.raycontrol_admin_revoked_invite, user, name)
    val owner = answer.s("user").ifEmpty { return null }
    val device = answer.s("device").ifEmpty { name }
    val credentials = answer.l("credentials")
    return when {
        answer.b("already") -> t(R.string.raycontrol_admin_revoked_already, owner, device)
        credentials == 0L -> t(R.string.raycontrol_admin_revoked_no_credentials, owner, device)
        else -> t(R.string.raycontrol_admin_revoked_renewed, owner, device, credentials)
    }
}

/** What RenewUser did: how many of the user's credentials are new; none for a user who had none yet. */
internal fun renewText(answer: JsonObject, user: String, t: AdminTexts): String {
    val name = answer.s("name").ifEmpty { user }
    val credentials = answer.l("credentials")
    return if (credentials == 0L) t(R.string.raycontrol_admin_renewed_none, name) else t(R.string.raycontrol_admin_renewed, name, credentials)
}

/**
 * The node refused to disable or delete its last enabled ssh service unforced (raycontrol v0.27.0): the one
 * failed_precondition of those calls that a force answers. Another service's refusal is not asked about.
 */
internal fun lastSshRefused(kind: String, code: String?, force: Boolean) = !force && kind == "ssh" && code == "failed_precondition"

/**
 * A REALITY target as typed, HOST:PORT (a name or an address, an IPv6 one in brackets, a port 1 to 65535), trimmed;
 * null when it is not of that form. The form only: the node checks the target itself and says why it refuses one.
 */
internal fun realityTarget(text: String): String? {
    val value = text.trim()
    val host: String
    val port: String
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end < 0 || value.getOrNull(end + 1) != ':') return null
        host = value.substring(1, end)
        port = value.substring(end + 2)
        if (':' !in host || !host.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) return null
    } else {
        host = value.substringBeforeLast(':', "")
        port = value.substringAfterLast(':', "")
        // an IPv6 address goes in brackets, as Go's SplitHostPort on the node wants it
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
    }
    if (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65535) return null
    return value
}

/** A node before v0.27.0 refuses the target as an option it does not have (provision's "has no option"). */
internal fun targetUnknown(message: String?) = message?.contains("no option \"target\"") == true

/** The node's own words in an error of its API: without the core's "raycontrol api: CODE: " in front. */
internal fun nodeMessage(message: String): String =
    Regex("^raycontrol api: [a-z_]+: ").find(message)?.let { message.substring(it.range.last + 1) }?.ifBlank { null } ?: message

/**
 * A port of the node, or a range of them (GetPorts' NodePort), in a line for a provider's firewall: the protocols and
 * the ports, the name, who reaches it and how it was set; for a rotating range, its port now and until when ([time]
 * of unix seconds).
 */
internal fun portLine(port: JsonObject, t: AdminTexts, time: (Long) -> String): String {
    val from = port.l("from")
    val to = port.l("to")
    val span = if (to > from) "$from-$to" else "$from"
    val access = when (val a = port.s("access")) {
        "everyone" -> t(R.string.raycontrol_admin_port_everyone)
        "spa" -> t(R.string.raycontrol_admin_port_spa)
        "admin" -> t(R.string.raycontrol_admin_port_admin)
        "acme" -> t(R.string.raycontrol_admin_port_acme)
        else -> a
    }
    val source = when (val s = port.s("source")) {
        "fixed" -> t(R.string.raycontrol_admin_port_fixed)
        "random" -> t(R.string.raycontrol_admin_port_random)
        "chosen" -> t(R.string.raycontrol_admin_port_chosen)
        "sshd" -> t(R.string.raycontrol_admin_port_sshd)
        else -> s
    }
    val protos = port.strs("protos").joinToString("+") { it.uppercase() }
    var line = "$protos $span ${port.s("name")}: " + listOf(access, source).filter { it.isNotEmpty() }.joinToString(", ")
    val current = port.l("current")
    if (current > 0) line += "; " + t(R.string.raycontrol_admin_port_current, current, time(port.l("currentUntil")))
    return line
}

/**
 * The newest patch release of the node's minor version (GetUpdateStatus latest_patch, raycontrol v0.27.0), which the
 * auto update takes; null without one, or when it is the latest release, which the summary names already.
 */
internal fun latestPatch(update: JsonObject): String? = update.s("latestPatch").takeIf {
    it.isNotEmpty() && it != update.s("latest") && isNewerVersion(it, update.s("installed"))
}

// protobuf JSON as the node answers: int64 as strings, defaults (false, 0, empty) left out
private fun JsonObject.s(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
private fun JsonObject.l(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: 0L
private fun JsonObject.b(name: String): Boolean = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
private fun JsonObject.arr(name: String): List<JsonObject> = (get(name) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.strs(name: String): List<String> = (get(name) as? JsonArray)?.map { it.asString }.orEmpty()

// ---- the relays' updates (relay-update-remote-v1 §8, raycontrol v0.28.0)

/** The states a relay leaves by itself, each within its bound: while one is in them, the update is still going. */
private val RELAY_PENDING = setOf("waiting", "offered", "requested", "running")

/** A relay is still on its way to the node's version (GetUpdateStatusResponse.relays). */
internal fun relaysPending(update: JsonObject) = update.arr("relays").any { it.s("state") in RELAY_PENDING }

/** How many relays are in which state, in a line; null without relays (or from a node before v0.28.0). */
internal fun relaysSummary(update: JsonObject, t: AdminTexts): String? {
    val relays = update.arr("relays").takeIf { it.isNotEmpty() } ?: return null
    val counts = relays.groupingBy { it.s("state") }.eachCount()
    return t(R.string.raycontrol_admin_relays_count, counts.entries.joinToString(", ") { (state, n) -> "${relayState(state, t)} $n" })
}

/** A relay's state in words; a state this build does not know (a newer node) as the node says it, in one line. */
internal fun relayState(state: String, t: AdminTexts): String = when (state) {
    "waiting" -> t(R.string.raycontrol_admin_relay_waiting)
    "offered" -> t(R.string.raycontrol_admin_relay_offered)
    "requested" -> t(R.string.raycontrol_admin_relay_requested)
    "running" -> t(R.string.raycontrol_admin_relay_running)
    "done" -> t(R.string.raycontrol_admin_relay_done)
    "rolled_back" -> t(R.string.raycontrol_admin_relay_rolled_back)
    "failed" -> t(R.string.raycontrol_admin_relay_failed)
    "current" -> t(R.string.raycontrol_admin_relay_current)
    "off" -> t(R.string.raycontrol_admin_relay_off)
    "unknown", "" -> t(R.string.raycontrol_admin_relay_unknown)
    else -> relayText(state, RELAY_WORD)
}

/**
 * Why a relay's update did not go (§3); a code this build does not know (a relay newer than its node) as it is, in one
 * line.
 */
internal fun relayReason(reason: String, t: AdminTexts): String = when (reason) {
    "policy" -> t(R.string.raycontrol_admin_relay_reason_policy)
    "stale" -> t(R.string.raycontrol_admin_relay_reason_stale)
    "version" -> t(R.string.raycontrol_admin_relay_reason_version)
    "busy" -> t(R.string.raycontrol_admin_relay_reason_busy)
    "unfinished" -> t(R.string.raycontrol_admin_relay_reason_unfinished)
    "bundle" -> t(R.string.raycontrol_admin_relay_reason_bundle)
    "ssh" -> t(R.string.raycontrol_admin_relay_reason_ssh)
    "download" -> t(R.string.raycontrol_admin_relay_reason_download)
    "signature" -> t(R.string.raycontrol_admin_relay_reason_signature)
    "place" -> t(R.string.raycontrol_admin_relay_reason_place)
    "install" -> t(R.string.raycontrol_admin_relay_reason_install)
    "check" -> t(R.string.raycontrol_admin_relay_reason_check)
    "interrupted" -> t(R.string.raycontrol_admin_relay_reason_interrupted)
    "open" -> t(R.string.raycontrol_admin_relay_reason_open)
    "not_taken" -> t(R.string.raycontrol_admin_relay_reason_not_taken)
    "no_word" -> t(R.string.raycontrol_admin_relay_reason_no_word)
    else -> relayText(reason, RELAY_WORD)
}

/** A word of the node's this build does not know (a state, a reason) is shown up to this many characters. */
private const val RELAY_WORD = 32

/**
 * A relay's text as the node passes it on ([raw]: its name and version, what it said of a failure, which v0.28.1 makes
 * the last line of rayctl's error on the relay, up to 200 bytes) in one line: the note's rules (control and format
 * characters go, line breaks become a space, more than [max] characters end in "…"), so a relay cannot add lines of
 * its own or turn the text around it.
 */
private fun relayText(raw: String, max: Int): String = RayNode.nodeNote(raw, max).orEmpty()

/**
 * A relay not heard for longer than this is marked so, whatever its state says: well above the 10 minutes the node
 * leaves between refreshes of a connected relay's seen_at (relay-update-remote-v1 §8.3), and above a few minutes of
 * the phone's clock against the node's, which the answer does not carry.
 */
private const val RELAY_QUIET_SECONDS = 30 * 60L

/** A relay's name, version and target are shown up to this many characters; its error up to the node's 200 bytes. */
private const val RELAY_VERSION = 64
private const val RELAY_ERROR = 200

/**
 * A relay's lines: its name, version, state, the version it is offered and since when; why it failed (the reason in
 * words, the error as the node gives it); that the node has not heard it for a while; and what root on the relay can
 * do for one that is off or unknown. The relay's times are unix seconds by the node's clock, [now] the phone's (the
 * answer carries no time of the node's), which [RELAY_QUIET_SECONDS] leaves room for; [time] shows a time.
 */
internal fun relayLine(relay: JsonObject, t: AdminTexts, now: Long, time: (Long) -> String): String {
    val state = relay.s("state")
    val version = relayText(relay.s("version"), RELAY_VERSION)
    val shown = version.ifEmpty { t(R.string.raycontrol_admin_relay_no_version) }
    var head = "${relayText(relay.s("name"), RELAY_VERSION)} · $shown · ${relayState(state, t)}"
    relayText(relay.s("target"), RELAY_VERSION).takeIf { it.isNotEmpty() && it != version }?.let { head += " → $it" }
    relay.l("changedAt").takeIf { it > 0 }?.let { head += " · " + t(R.string.raycontrol_admin_relay_since, time(it)) }
    val lines = mutableListOf(head)
    val reason = relay.s("reason").takeIf { it.isNotEmpty() }?.let { relayReason(it, t) }
    // the relay's own words, isolated from the line's direction
    val error = relayText(relay.s("error"), RELAY_ERROR).takeIf { it.isNotEmpty() }?.let { "\u2068$it\u2069" }
    listOfNotNull(reason, error).takeIf { it.isNotEmpty() }?.let { lines += it.joinToString(": ") }
    val seen = relay.l("seenAt")
    when {
        seen == 0L -> lines += t(R.string.raycontrol_admin_relay_never_heard)
        now - seen > RELAY_QUIET_SECONDS -> lines += t(R.string.raycontrol_admin_relay_last_heard, time(seen))
    }
    when (state) {
        "off" -> lines += t(R.string.raycontrol_admin_relay_hint_off)
        "unknown", "" -> lines += t(R.string.raycontrol_admin_relay_hint_unknown)
    }
    return lines.joinToString("\n")
}
