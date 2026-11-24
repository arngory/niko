package io.niko.ui

import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.lifecycle.withResumed
import io.niko.GroupType
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.SagerDatabase
import io.niko.group.GroupUpdater
import io.niko.ktx.*
import io.niko.raycontrol.RayChannel
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RaySubscription
import io.niko.ui.admin.AdminActivity
import io.niko.ui.compose.NikoDialog

// A raycontrol node's actions as dialogs over any screen: the settings' node list and the node's card (ui/node) take
// them alike. Moved as they were from SettingsPreferenceFragment.

/**
 * The node's actions in a list. [card]: from the node's card, which has the admin panel, the update, the channel check
 * and the test run of its own: the rest only.
 */
fun Fragment.showRayNodeActions(node: RayNode, card: Boolean = false, onRemoved: () -> Unit = {}) {
    val actions = mutableListOf<Pair<Int, () -> Unit>>()
    if (!card) {
        if (node.isAdmin) actions += R.string.raycontrol_admin to { openRayAdmin(node) }
        if (node.subscription != null) actions += R.string.raycontrol_sub_update to { updateRaySubscription(node) }
        if (!node.profileIds.isNullOrEmpty()) actions += R.string.raycontrol_channel_check to { checkRayChannel(node) }
        if (!node.profileIds.isNullOrEmpty()) actions += R.string.raycontrol_test_run to { rayTestRun(node) }
    }
    io.niko.raycontrol.RayTestRun.lastFindings?.let { findings ->
        actions += R.string.raycontrol_test_run_last to {
            showRayFindings(findings, io.niko.raycontrol.RayTestRun.lastFile?.takeIf { it.exists() })
        }
    }
    // the summary with consent; the node's hints for this network come with the subscription either way
    val hasHints = RayChannel.network()?.let { RayChannel.hints(node.spa.nodeId, it) } != null
    if (DataStore.raycontrolReports || hasHints) actions += R.string.raycontrol_report_show to { showRayReport(node) }
    actions += R.string.raycontrol_node_remove to { confirmRayNodeRemove(node, onRemoved) }
    NikoDialog(requireContext())
        .title(node.label)
        .items(actions.map { getString(it.first) }) { i -> actions[i].second() }
        .ghost(android.R.string.cancel)
        .show()
}

fun Fragment.openRayAdmin(node: RayNode) {
    startActivity(Intent(requireContext(), AdminActivity::class.java).putExtra(AdminActivity.EXTRA_NODE, node.spa.nodeId))
}

/**
 * A raycontrol node's group: its subscription anew from the node, the group's progress meanwhile (GroupUpdater), the
 * result in a snackbar; [done] after it, on the main thread, while the fragment is there.
 */
fun Fragment.updateRayNode(node: RayNode, groupId: Long, done: () -> Unit = {}) {
    if (!GroupUpdater.updating.add(groupId)) return
    val context = requireContext().applicationContext
    runOnDefaultDispatcher {
        GroupManager.postReload(groupId)
        val message = try {
            RaySubscription.refreshForUser(context, node)
        } finally {
            GroupUpdater.updating.remove(groupId)
            GroupManager.postReload(groupId)
        }
        onMainDispatcher {
            if (!isAdded) return@onMainDispatcher
            snackbar(message).show()
            done()
        }
    }
}

/**
 * Every provider's subscription and every raycontrol node that serves one fetched anew, after the question (the
 * groups' list and the Subscriptions tab ask it alike).
 */
fun Fragment.updateAllGroups() {
    NikoDialog(requireContext()).title(R.string.confirm)
        .message(R.string.update_all_subscription)
        .ghost(R.string.no)
        .primary(R.string.yes) { _, _ ->
            runOnDefaultDispatcher {
                val subscriptions = SagerDatabase.groupDao.allGroups().filter { it.type == GroupType.SUBSCRIPTION }
                // raycontrol nodes too: their subscriptions come from the nodes
                val nodes = RayNodes.list().filter { it.subscription != null && it.groupId != 0L }
                onMainDispatcher {
                    subscriptions.forEach { GroupUpdater.startUpdate(it, true) }
                    if (isAdded) nodes.forEach { updateRayNode(it, it.groupId) }
                }
            }
        }
        .show()
}

fun Fragment.updateRaySubscription(node: RayNode) {
    snackbar(R.string.raycontrol_sub_updating).show()
    val context = requireContext().applicationContext
    runOnDefaultDispatcher {
        val message = RaySubscription.refreshForUser(context, node)
        onMainDispatcher { if (isAdded) snackbar(message).show() }
    }
}

/** A test run with an id the node's admin logs under too (RayTestRun), then what it found and its file to share. */
fun Fragment.rayTestRun(node: RayNode) {
    NikoDialog(requireContext())
        .title(R.string.raycontrol_test_run)
        .message(R.string.raycontrol_test_run_help)
        .field(initial = io.niko.raycontrol.RayTestRun.defaultId())
        .ghost(android.R.string.cancel)
        .primary(R.string.raycontrol_test_run_start) { typed, _ ->
            val id = typed.trim()
            if (!io.niko.raycontrol.RayTestRun.validId(id)) {
                snackbar(R.string.raycontrol_test_run_bad_id).show()
                return@primary
            }
            startRayTestRun(node, id)
        }
        .show()
}

private fun Fragment.startRayTestRun(node: RayNode, id: String) {
    val context = requireContext().applicationContext
    val lines = mutableListOf(getString(R.string.raycontrol_test_run_running, id))
    val words = NikoDialog(requireContext())
        .title(getString(R.string.raycontrol_test_run_title, id))
        .message(lines.joinToString("\n"))
        .primary(android.R.string.ok)
    val dialog = words.show()
    // the lines change on the main thread only; the run may outlive this screen (minutes): the app's context
    // for strings and the share sheet, the dialog only while the screen is there
    fun show(line: String) = runOnMainDispatcher {
        lines += line
        if (isAdded) words.message(lines.takeLast(20).joinToString("\n"))
    }
    runOnDefaultDispatcher {
        val outcome = try {
            io.niko.raycontrol.RayTestRun.run(node, id) { line ->
                // the step's name and outcome, the full line goes to the file
                show(line.substringAfter("\"step\":\"").substringBefore('"') +
                        (Regex("\"error\":\"([^\"]*)\"").find(line)?.groupValues?.get(1)?.let { ": $it" } ?: ""))
            }
        } catch (e: Exception) {
            show(e.readableMessage)
            return@runOnDefaultDispatcher
        }
        // what it found in place of the steps, once this screen is in front again; a screen gone meanwhile leaves
        // them and the log to the node's menu (the last test run)
        onMainDispatcher {
            if (!isAdded) return@onMainDispatcher
            try {
                lifecycle.withResumed {
                    dialog.dismiss()
                    showRayFindings(outcome.findings, outcome.file)
                }
            } catch (_: Exception) {
            }
        }
    }
}

/** What a test run found, and its log to share (a button that leaves the findings open). */
fun Fragment.showRayFindings(findings: io.niko.raycontrol.RayTestFindings.Result, file: java.io.File?) {
    val context = context ?: return
    NikoDialog(context)
        .title(R.string.raycontrol_findings_title)
        .message(io.niko.raycontrol.RayTestFindingsText.text(context, findings))
        .apply { if (file != null) ghost(R.string.raycontrol_test_run_share, stays = true) { shareRayRunLog(file) } }
        .primary(android.R.string.ok)
        .show()
}

private fun Fragment.shareRayRunLog(file: java.io.File) {
    val context = context ?: return
    startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/x-log")
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(
                    Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        context, io.niko.BuildConfig.APPLICATION_ID + ".cache", file
                    )
                ), getString(R.string.raycontrol_test_run_share)
        )
    )
}

/** Probes every profile of [node] on this network, one by one, and shows the results as they come. */
fun Fragment.checkRayChannel(node: RayNode) {
    val network = RayChannel.network() ?: run {
        snackbar(R.string.raycontrol_channel_no_network).show()
        return
    }
    val context = requireContext().applicationContext
    val lines = mutableListOf(getString(R.string.raycontrol_channel_checking))
    val words = NikoDialog(requireContext())
        .title(getString(R.string.raycontrol_channel_title, node.label))
        .message(lines.joinToString("\n"))
        .primary(android.R.string.ok)
    val dialog = words.show()
    val job = runOnDefaultDispatcher {
        // the direct path first: which SPA carrier opens the node's port here, how large a UDP datagram passes
        RayChannel.directPath(node, network)?.let { path ->
            lines += when {
                path.error != null -> context.getString(R.string.raycontrol_direct_failed, path.error)
                path.spaCarrier == null -> context.getString(R.string.raycontrol_direct_none)
                !path.udpProbed -> context.getString(R.string.raycontrol_direct_spa, path.spaCarrier.uppercase())
                !path.udpMeasured -> context.getString(R.string.raycontrol_direct_spa, path.spaCarrier.uppercase()) +
                    context.getString(
                        when {
                            // a Retry-After past a probe's half minute: the day's probe volume is spent
                            path.udpStatus == 429 && path.udpRetryAfter > 30 -> R.string.raycontrol_direct_udp_quota
                            path.udpStatus == 429 -> R.string.raycontrol_direct_udp_busy
                            else -> R.string.raycontrol_direct_udp_unmeasured
                        }
                    )
                path.udpMtu == 0 -> context.getString(R.string.raycontrol_direct_no_udp, path.spaCarrier.uppercase())
                else -> context.getString(R.string.raycontrol_direct_mtu, path.spaCarrier.uppercase(), path.udpMtu) +
                    if (path.udpMtu < 1200) context.getString(R.string.raycontrol_direct_mtu_low) else ""
            }
            onMainDispatcher { words.message(lines.joinToString("\n")) }
        }
        // the DNS tunnels' resolvers on this network, anew (dnsprobe-v1), before their profiles are measured
        io.niko.raycontrol.RayDnsResolvers.detectForNode(node, network) { line ->
            lines += line
            // the text as of this line: the list grows on while the main thread gets to it
            val text = lines.joinToString("\n")
            runOnMainDispatcher { words.message(text) }
        }
        for ((key, id) in node.profileIds.orEmpty()) {
            val profile = SagerDatabase.proxyDao.getById(id) ?: continue
            val score = RayChannel.probe(profile, deep = true, mobile = network.mobile)
            if (score == null) {
                lines += context.getString(R.string.raycontrol_channel_in_use, profile.displayName())
            } else {
                RayChannel.record(node.spa.nodeId, network, key, score)
                lines += RayChannel.describe(context, profile.displayName(), score)
            }
            onMainDispatcher { words.message(lines.joinToString("\n")) }
            // a Yggdrasil profile: its public peers from here, each with a node of the app's own
            val via = profile.requireBean().yggVia.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (via.isNotEmpty()) {
                val checks = io.niko.raycontrol.RayYggPeers.check(via)
                val picked = io.niko.raycontrol.RayYggPeers.pick(checks)
                lines += context.getString(R.string.raycontrol_ygg_peers, checks.count { it.up }, checks.size)
                for (c in checks) {
                    val name = io.niko.raycontrol.RayYggPeers.label(c.uri)
                    lines += when {
                        c.up -> context.getString(
                            if (c.uri in picked) R.string.raycontrol_ygg_peer_taken else R.string.raycontrol_ygg_peer_up,
                            name, c.latencyMs.toInt()
                        )
                        else -> context.getString(R.string.raycontrol_ygg_peer_down, name, c.error ?: "—")
                    }
                }
                onMainDispatcher { words.message(lines.joinToString("\n")) }
            }
        }
        lines[0] = context.getString(R.string.raycontrol_channel_done)
        // with consent the day's summary of this network goes out now, the results just measured in it
        rayReportLine(context, io.niko.raycontrol.RayReport.send(RayNodes.get(node.spa.nodeId) ?: node))?.let { lines += it }
        onMainDispatcher { words.message(lines.joinToString("\n")) }
    }
    // a probe already running ends within its window; no new one starts
    dialog.setOnDismissListener { job.cancel() }
}

private fun rayReportLine(context: android.content.Context, outcome: io.niko.raycontrol.RayReport.Outcome?): String? = when (outcome) {
    null -> null
    io.niko.raycontrol.RayReport.Outcome.SENT -> context.getString(R.string.raycontrol_report_sent)
    io.niko.raycontrol.RayReport.Outcome.ALREADY_SENT -> context.getString(R.string.raycontrol_report_already)
    io.niko.raycontrol.RayReport.Outcome.NOT_TAKEN -> context.getString(R.string.raycontrol_report_not_taken)
    io.niko.raycontrol.RayReport.Outcome.REFUSED -> context.getString(R.string.raycontrol_report_refused)
    io.niko.raycontrol.RayReport.Outcome.FAILED -> context.getString(R.string.raycontrol_report_failed)
}

/**
 * The summary for [node] from this network's probes, exactly as it goes out (RayReport), whether today's went
 * out, and the node's hints for this network.
 */
fun Fragment.showRayReport(node: RayNode) {
    val network = RayChannel.network() ?: run {
        snackbar(R.string.raycontrol_channel_no_network).show()
        return
    }
    val report = io.niko.raycontrol.RayReport.build(node, network)
    val message = buildString {
        append(report?.let { com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(it) }
            ?: getString(R.string.raycontrol_report_empty))
        val state = RayChannel.reportState(node.spa.nodeId, network)
        val today = System.currentTimeMillis() / 1000 / (24 * 3600)
        if (state?.get("day")?.asLong == today) when (state.get("status")?.asInt) {
            200, 204, 429 -> append("\n\n").append(getString(R.string.raycontrol_report_already))
            404 -> append("\n\n").append(getString(R.string.raycontrol_report_not_taken))
        }
        RayChannel.hints(node.spa.nodeId, network)?.let { h ->
            val names = node.services.associate { it.id to it.name.ifBlank { "svc ${it.id}" } }
            fun list(ids: List<Int>) = ids.joinToString(", ") { names[it] ?: "svc $it" }
            append("\n\n").append(getString(R.string.raycontrol_hints, h.devices, h.days))
            if (h.order.isNotEmpty()) append("\n").append(getString(R.string.raycontrol_hints_order, list(h.order)))
            if (h.avoid.isNotEmpty()) append("\n").append(getString(R.string.raycontrol_hints_avoid, list(h.avoid)))
            if (h.udpBlocked) append("\n").append(getString(R.string.raycontrol_hints_udp_blocked))
            if (h.tcpStallKib > 0) append("\n").append(getString(R.string.raycontrol_hints_stall, h.tcpStallKib))
            h.udpMtu?.let { append("\n").append(getString(R.string.raycontrol_hints_udp_mtu, it)) }
        }
    }
    NikoDialog(requireContext())
        .title(R.string.raycontrol_report_show)
        .message(message)
        .primary(android.R.string.ok)
        .show()
}

fun Fragment.confirmRayNodeRemove(node: RayNode, onRemoved: () -> Unit = {}) {
    NikoDialog(requireContext())
        .message(getString(R.string.raycontrol_node_remove_confirm, node.label))
        .ghost(android.R.string.cancel)
        .primary(R.string.yes, danger = true) { _, _ ->
            RayNodes.remove(node.spa.nodeId)
            RaySubscription.schedule()
            onRemoved()
            // the running core still holds the key and knocks: restart it now, not on "Apply"
            if (DataStore.serviceState.started) SagerNet.reloadService()
        }
        .show()
}
