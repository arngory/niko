package io.niko.ui.admin

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.raycontrol.RayApi
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RaySubscription
import io.niko.ui.settings.FieldInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The admin mode for a raycontrol node (api-v1.md §5): the node's NodeService over the remote API, for a device
 * registered as admin through an invite. The work RayAdminActivity did, moved as it was: the same calls with the same
 * bodies, the same questions before them; what they answer goes to [state] in place of the old rows and dialogs. It
 * outlives its activity's rotation (a call under way, a sheet, an update watched); what was typed is in [save] for a
 * process the system killed.
 */
class AdminModel : ViewModel(), AdminActions {

    /** What the model asks of the activity that shows it. */
    interface Host {
        fun text(id: Int, args: Array<out Any>): String
        fun size(bytes: Long): String
        /** how long an invite holds, in words: [INVITE_TTLS] */
        fun ttls(): List<String>
        /** a line for a snackbar */
        fun say(text: String)
        fun share(text: String)
        /** to the clipboard, as a secret */
        fun copy(text: String)
        fun close()
    }

    var host: Host? = null
    val state = mutableStateOf(AdminState(""))
    val started get() = ::node.isInitialized

    private lateinit var node: RayNode
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val texts = AdminTexts { id, args -> host?.text(id, args).orEmpty() }

    // the node's answers the pages are made of
    private class StatusRaw(
        val status: JsonObject, val self: JsonObject, val access: JsonObject,
        val mirror: JsonObject?, val summary: JsonObject?, val update: JsonObject?,
    )
    private class NetworkRaw(val dns: JsonObject?, val relays: List<JsonObject>?, val zones: JsonObject?)

    private var status: StatusRaw? = null
    private var users: List<JsonObject>? = null
    private var services: List<JsonObject>? = null
    private var kinds: List<String> = emptyList()
    private var devices: Pair<String, JsonObject>? = null
    private var network: NetworkRaw? = null
    private var audit: List<JsonObject>? = null
    // the node answered GetPorts unimplemented (before raycontrol v0.27.0): its row goes
    private var noPorts = false
    // the update as it goes, while it is watched
    private var watchText: String? = null
    private var watchJob: Job? = null
    // a new user's services ticked before the process was killed
    private var restoredTicks: Set<String>? = null
    private var calls = 0

    // what the sheet's answer does
    private var onConfirm: (() -> Unit)? = null
    private var onChoose: ((Int) -> Unit)? = null
    private var onSubmit: ((List<String>) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    fun start(node: RayNode, saved: Bundle?) {
        this.node = node
        state.value = AdminState(node.label)
        if (saved != null) restore(saved)
        load()
    }

    override fun onCleared() {
        scope.cancel()
    }

    private inline fun set(change: AdminState.() -> AdminState) {
        state.value = state.value.change()
    }

    private fun say(text: String) {
        host?.say(text)
    }

    private fun say(id: Int) = say(texts(id))

    // ---- calls

    /**
     * Runs [call] off the main thread, the page busy meanwhile; errors go to a snackbar, but those [failed] asks about
     * ([quiet]). [page]: the call reads the shown page, and its error stays on it.
     */
    private fun <T> api(
        call: () -> T,
        quiet: (Throwable) -> Boolean = { false },
        failed: (Throwable) -> Unit = {},
        page: Boolean = false,
        done: (T) -> Unit,
    ) {
        calls++
        set { copy(busy = true, error = if (page) null else error) }
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    Result.success(call())
                } catch (e: Exception) {
                    Logs.w(e)
                    Result.failure(e)
                }
            }
            calls--
            set { copy(busy = calls > 0) }
            result.onSuccess(done).onFailure {
                if (!quiet(it)) {
                    val text = failureText(it, texts) { e -> e.readableMessage }
                    if (page) set { copy(error = text) } else say(text)
                }
                failed(it)
            }
        }
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

    /** [call], or null where the node does not have the method yet (an older raycontrol): its row is left out. */
    private fun <T> optional(call: () -> T): T? = try {
        call()
    } catch (e: Exception) {
        if (RayApi.unimplemented(e)) null else throw e
    }

    private fun date(seconds: Long) = if (seconds == 0L) "—" else
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds * 1000))

    private fun time(seconds: Long) = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(seconds * 1000))

    private fun size(bytes: Long) = host?.size(bytes) ?: bytes.toString()

    private fun now() = System.currentTimeMillis() / 1000

    /** The shown page read from the node. */
    private fun load() {
        when (val page = state.value.page) {
            is AdminPage.Status -> loadStatus()
            is AdminPage.Users, is AdminPage.NewUser -> loadUsers()
            is AdminPage.User -> {
                loadUsers()
                loadDevices(page.name)
            }
            is AdminPage.Invite -> {}
            is AdminPage.Services, is AdminPage.Service -> loadServices()
            is AdminPage.Network -> loadNetwork()
            is AdminPage.Detail -> when (page.kind) {
                DetailKind.AUDIT -> loadAudit()
                DetailKind.UPDATE -> {
                    stopWatch()
                    loadStatus { watchIfUpdating() }
                }
                DetailKind.MIRROR, DetailKind.SUMMARY -> loadStatus()
                DetailKind.PORTS, DetailKind.TEXT -> {}
            }
        }
    }

    /** Every opened page made anew from what the node answered; a user or a service that is gone takes its page along. */
    private fun render() = set {
        copy(pages = pages.mapNotNull { page ->
            when (page) {
                is AdminPage.Status -> status?.let { page.copy(data = statusData(it)) } ?: page
                is AdminPage.Users -> users?.let { page.copy(users = it.map(::userRow)) } ?: page
                is AdminPage.User -> {
                    val list = users
                    if (list == null) page else list.firstOrNull { it.s("name") == page.name }?.let { user ->
                        page.copy(
                            info = userInfo(user),
                            devices = devices?.takeIf { it.first == page.name }?.let { deviceRows(it.second, texts, ::date) },
                        )
                    }
                }
                is AdminPage.NewUser -> {
                    val list = services
                    if (page.services.isNotEmpty() || list == null) page else {
                        val ticked = restoredTicks
                        restoredTicks = null
                        page.copy(services = list.map { Tick(it.s("name"), serviceTitle(it), ticked?.contains(it.s("name")) ?: it.b("enabled")) })
                    }
                }
                is AdminPage.Invite -> page
                is AdminPage.Services -> services?.let { page.copy(services = it.map(::serviceRow)) } ?: page
                is AdminPage.Service -> {
                    val list = services
                    if (list == null) page else list.firstOrNull { it.s("name") == page.name }?.let { service ->
                        page.copy(info = ServiceInfo(
                            serviceRow(service),
                            optionsOf(service).map { OptionRow(it.key, texts(it.title), it.current) },
                            keysOf(service.s("kind")),
                        ))
                    }
                }
                is AdminPage.Network -> network?.let { page.copy(data = networkData(it)) } ?: page
                is AdminPage.Detail -> when (page.kind) {
                    DetailKind.AUDIT -> audit?.let { entries ->
                        page.copy(rows = entries.mapIndexed { i, e ->
                            DetailRow(i.toString(), "${e.s("action")} ${e.s("target")}", "${date(e.l("time"))} · ${e.s("actor")} · ${e.s("result")}", action = false)
                        })
                    } ?: page
                    DetailKind.UPDATE -> status?.update?.let { updatePage(it) } ?: page
                    DetailKind.MIRROR -> status?.mirror?.let { mirrorPage(it) } ?: page
                    DetailKind.SUMMARY -> status?.summary?.let { summaryPage(it) } ?: page
                    DetailKind.PORTS -> portsPage()
                    DetailKind.TEXT -> page
                }
            }
        })
    }

    private fun serviceTitle(service: JsonObject) = "${service.s("name")} (${service.s("kind")})"

    // ---- sheets

    private fun closeSheet() {
        onConfirm = null
        onChoose = null
        onSubmit = null
        onDismiss = null
        set { copy(sheet = null) }
    }

    private fun ask(message: String, yes: () -> Unit) {
        closeSheet()
        onConfirm = yes
        set { copy(sheet = AdminSheet.Confirm(message)) }
    }

    /** A message to read, longer than a snackbar stays for; [then] once it is closed. */
    private fun message(text: String, then: () -> Unit = {}) {
        closeSheet()
        onDismiss = then
        set { copy(sheet = AdminSheet.Message(text)) }
    }

    private fun choice(title: String, options: List<String>, selected: Int? = null, pick: (Int) -> Unit) {
        closeSheet()
        onChoose = pick
        set { copy(sheet = AdminSheet.Choice(title, options, selected)) }
    }

    private fun actions(title: String, actions: List<Pair<String, () -> Unit>>) = choice(title, actions.map { it.first }) { actions[it].second() }

    private fun input(title: String, fields: List<InputField>, help: String? = null, error: String? = null, submit: (List<String>) -> Unit) {
        closeSheet()
        onSubmit = submit
        set { copy(sheet = AdminSheet.Input(title, fields, help, error)) }
    }

    override fun input(index: Int, text: String) = set {
        val sheet = sheet as? AdminSheet.Input ?: return@set this
        copy(sheet = sheet.copy(fields = sheet.fields.mapIndexed { i, f -> if (i == index) f.copy(value = text) else f }))
    }

    override fun submit() {
        val sheet = state.value.sheet as? AdminSheet.Input ?: return
        val then = onSubmit
        closeSheet()
        then?.invoke(sheet.fields.map { it.value })
    }

    override fun choose(index: Int) {
        val then = onChoose
        closeSheet()
        then?.invoke(index)
    }

    override fun confirm() {
        val then = onConfirm
        closeSheet()
        then?.invoke()
    }

    override fun dismiss() {
        val then = onDismiss
        closeSheet()
        then?.invoke()
    }

    // ---- pages

    override fun back() {
        if (state.value.pages.size == 1) {
            host?.close()
            return
        }
        // closing leaves the update running
        if ((state.value.page as? AdminPage.Detail)?.kind == DetailKind.UPDATE) stopWatch()
        set { pop() }
        // a page under one restored after the process was killed has nothing yet
        val unread = when (val page = state.value.page) {
            is AdminPage.Status -> page.data == null
            is AdminPage.Users -> page.users == null
            is AdminPage.User -> page.info == null
            is AdminPage.Services -> page.services == null
            is AdminPage.Service -> page.info == null
            is AdminPage.Network -> page.data == null
            else -> false
        }
        if (unread) load()
    }

    override fun refresh() = load()

    private fun push(page: AdminPage) {
        set { push(page) }
        render()
    }

    override fun open(dest: AdminDest) {
        when (dest) {
            AdminDest.USERS -> {
                push(AdminPage.Users())
                loadUsers()
            }
            AdminDest.SERVICES -> {
                push(AdminPage.Services())
                loadServices()
            }
            AdminDest.NETWORK -> {
                push(AdminPage.Network())
                loadNetwork()
            }
            AdminDest.AUDIT -> {
                audit = null
                push(AdminPage.Detail(DetailKind.AUDIT, texts(R.string.ad_audit)))
                loadAudit()
            }
            AdminDest.UPDATE -> status?.update?.let {
                push(updatePage(it))
                watchIfUpdating()
            }
            AdminDest.MIRROR -> status?.mirror?.let { push(mirrorPage(it)) }
            AdminDest.SUMMARY -> status?.summary?.let { push(summaryPage(it)) }
            AdminDest.PORTS -> push(portsPage())
            AdminDest.ACCESS -> status?.let { editAdminAccess(it.access.strs("cidrs")) }
        }
    }

    // ---- status

    private fun loadStatus(then: () -> Unit = {}): Unit = api({
        StatusRaw(
            call("GetStatus"), RayApi.call(current(), RayApi.DEVICE + "GetSelf"), call("GetAdminAccess"),
            // raycontrol v0.18.0 and v0.11.0: not on an older node
            optional { call("GetAppMirror") }, optional { call("GetSummary") },
            // raycontrol v0.19.0 (node-update-v1)
            optional { call("GetUpdateStatus") },
        )
    }, page = true) { data ->
        status = data
        render()
        then()
    }

    private fun statusData(raw: StatusRaw): StatusData {
        val status = raw.status
        val self = raw.self
        return StatusData(
            device = "${self.s("user")}/${self.s("name")} · ${self.s("role")}",
            host = status.s("host"),
            version = status.s("version"),
            nodeId = status.s("nodeId"),
            // what the node warns its admin of (raycontrol v0.35.1)
            warnings = warningLines(status),
            // the node's name (raycontrol v0.15.0): clients show it in place of the address
            hasName = hasName(status),
            name = status.s("name"),
            // the note for the node's users (raycontrol v0.21.0)
            hasNote = hasNote(status),
            note = status.s("note"),
            users = status.l("users"),
            services = status.l("services"),
            devices = status.l("activeDevices"),
            spaPorts = status.strs("spaPorts").joinToString(", "),
            update = raw.update?.let { update ->
                val installed = update.s("installed")
                val latest = newerRelease(update)
                UpdateRow(
                    if (latest != null) "$installed → $latest" else installed,
                    // by request only while root allows it, and not over an update under way
                    latest.takeIf { update.s("remote") == "on" && !nodeUpdating(update) && !relaysPending(update) },
                )
            },
            mirror = raw.mirror?.b("enabled"),
            summary = raw.summary?.b("enabled"),
            // every port for a provider's firewall (raycontrol v0.27.0); left out once an older node said it has none
            ports = !noPorts,
            access = raw.access.strs("cidrs"),
        )
    }

    private fun statusPage(change: AdminPage.Status.() -> AdminPage.Status) = set {
        copy(pages = pages.map { if (it is AdminPage.Status) it.change() else it })
    }

    override fun name(text: String) = statusPage { copy(name = text) }

    override fun note(text: String) = statusPage { copy(note = text, noteError = null) }

    /** The node's name (SetNodeName): clients show it in place of its address; empty takes it away. */
    override fun saveName() {
        val typed = (state.value.pages.first() as? AdminPage.Status)?.takeIf { it.nameChanged }?.name ?: return
        api({ call("SetNodeName", body("name" to typed.trim())) }) { answer ->
            say(texts(R.string.raycontrol_admin_node_name_set, answer.s("name").ifEmpty { texts(R.string.raycontrol_admin_node_name_none) }))
            statusPage { copy(name = null) }
            loadStatus()
            // this device's own group takes the name with its next subscription: now
            runOnDefaultDispatcher { runCatching { RaySubscription.refresh(node.spa.nodeId) } }
        }
    }

    /**
     * The note for the node's users (SetNodeNote): one line of plain text, empty takes it away. What is typed is
     * cleaned as clients show a note (line breaks become spaces; invisible and direction characters, stray joiners and
     * marks go), so the node does not refuse it for them; longer than the node takes, or refused, it stays as typed.
     */
    override fun saveNote() {
        val typed = (state.value.pages.first() as? AdminPage.Status)?.takeIf { it.noteChanged }?.note ?: return
        val (note, length) = noteTyped(typed)
        if (length > RayNode.MAX_NOTE) {
            statusPage { copy(noteError = texts(R.string.raycontrol_admin_node_note_too_long, length, RayNode.MAX_NOTE)) }
            return
        }
        api({ call("SetNodeNote", body("note" to note)) }) { answer ->
            say(texts(R.string.raycontrol_admin_node_note_set, noteShown(answer.s("note"), texts)))
            statusPage { copy(note = null, noteError = null) }
            loadStatus()
            // this device's own card takes the note with its next subscription: now
            runOnDefaultDispatcher { runCatching { RaySubscription.refresh(node.spa.nodeId) } }
        }
    }

    private fun editAdminAccess(current: List<String>) = input(
        texts(R.string.raycontrol_admin_access),
        listOf(InputField(texts(R.string.raycontrol_admin_access_hint), current.joinToString("\n"), multiLine = true)),
        help = texts(R.string.raycontrol_admin_access_help),
    ) { typed ->
        api({ call("SetAdminAccess", body("cidrs" to cidrs(typed[0]))) }) { loadStatus() }
    }

    // ---- the node's update by request (raycontrol node-update-v1): a signed release, while root allows it

    private fun updatePage(update: JsonObject): AdminPage.Detail {
        val title = texts(R.string.raycontrol_admin_update)
        watchText?.let { return AdminPage.Detail(DetailKind.UPDATE, title, it) }
        val summary = updateSummary(update, texts, ::date)
        val relays = update.arr("relays").takeIf { it.isNotEmpty() }?.let { DetailRow("relays", texts(R.string.ad_relays)) }
        if (update.s("remote") != "on") {
            return AdminPage.Detail(
                DetailKind.UPDATE, title, summary + "\n\n" + texts(R.string.raycontrol_admin_update_remote_help),
                rows = listOfNotNull(relays),
            )
        }
        val latest = newerRelease(update)
        return AdminPage.Detail(
            DetailKind.UPDATE, title, summary,
            rows = listOfNotNull(
                if (latest != null) DetailRow("to", texts(R.string.raycontrol_admin_update_to, latest))
                else DetailRow("latest", texts(R.string.raycontrol_admin_update_latest)),
                DetailRow("version", texts(R.string.raycontrol_admin_update_version)),
                relays,
            ),
        )
    }

    /** The version row's button: to the release it names. */
    override fun update() {
        status?.update?.let { newerRelease(it) }?.let { startUpdate(it) }
    }

    private fun updateAction(id: String) {
        val update = status?.update ?: return
        when (id) {
            "to" -> newerRelease(update)?.let { startUpdate(it) }
            "latest" -> startUpdate("latest")
            "version" -> input(
                texts(R.string.raycontrol_admin_update_version),
                listOf(InputField(texts(R.string.raycontrol_admin_update_version_hint))),
            ) { typed ->
                updateVersion(typed[0])?.let { startUpdate(it) } ?: say(R.string.raycontrol_admin_update_version_bad)
            }
            "relays" -> relaysText(update, texts, now(), ::time)?.let {
                push(AdminPage.Detail(DetailKind.TEXT, texts(R.string.ad_relays), it))
            }
        }
    }

    /** StartUpdate answers at once; the update runs after it, rayd and its API restarting on the way. */
    private fun startUpdate(version: String) = ask(texts(R.string.raycontrol_admin_update_confirm, version)) {
        // an answer lost while rayd restarts, or "an update is requested already" on a second tap: the update may be
        // under way, and its status only reads
        api({ call("StartUpdate", body("version" to version)) }, failed = { e ->
            if (answerLost(e) || RayApi.code(e) == "failed_precondition") watchUpdate(nodeUpdating = true)
        }) { watchUpdate(nodeUpdating = true) }
    }

    /** The node's own update under way, or its relays still following it: watched, while root allows updates by request. */
    private fun watchIfUpdating() {
        val update = status?.update ?: return
        if (update.s("remote") != "on") return
        val node = nodeUpdating(update)
        if (node || relaysPending(update)) watchUpdate(node)
    }

    private fun stopWatch() {
        watchJob?.cancel()
        watchJob = null
        watchText = null
    }

    /**
     * The update as it goes (GetUpdateStatus every 7 s, node-update-v1 §2), until it ends or 20 min have gone. The
     * node is out of reach while rayd, its API and the data plane restart: asked again. Then its relays, which follow
     * it one at a time (relay-update-remote-v1 §8): asked every 30 s while one is waiting, offered, requested or
     * running, each of which ends, for three hours at most (a silent relay holds the queue up to 40 minutes before
     * its turn and 40 in it). Leaving the page leaves it all running. [nodeUpdating]: the node's own update is asked
     * for or under way; else only its relays are on their way (a relay back after a while gets the node's version
     * offered with no update of the node's), and the node's outcome is not waited for.
     */
    private fun watchUpdate(nodeUpdating: Boolean) {
        stopWatch()
        val shown = { text: String ->
            watchText = text
            if (state.value.pages.none { it is AdminPage.Detail && it.kind == DetailKind.UPDATE }) {
                set { push(AdminPage.Detail(DetailKind.UPDATE, texts(R.string.raycontrol_admin_update), text)) }
            } else {
                set { copy(pages = pages.map { if (it is AdminPage.Detail && it.kind == DetailKind.UPDATE) AdminPage.Detail(DetailKind.UPDATE, it.title, text) else it }) }
            }
        }
        shown(texts(R.string.raycontrol_admin_update_waiting))
        watchJob = scope.launch {
            val started = System.currentTimeMillis()
            var ended = !nodeUpdating
            // the node's update asked for and still not requested or running a minute on: none to wait for
            var gaveUp = false
            while (isActive) {
                val update = withContext(Dispatchers.IO) { runCatching { call("GetUpdateStatus") } }.getOrNull()
                val status = update?.getAsJsonObject("status")
                val show = {
                    val node = when {
                        status != null -> updateState(status, texts, ::date) ?: texts(
                            if (nodeUpdating && !gaveUp) R.string.raycontrol_admin_update_waiting else R.string.raycontrol_admin_update_idle)
                        else -> texts(R.string.raycontrol_admin_update_unreachable)
                    }
                    shown(listOfNotNull(node, update?.let { relaysText(it, texts, now(), ::time) }).joinToString("\n\n"))
                }
                show()
                if (!ended && status?.s("state") in setOf("done", "rolled_back", "failed")) {
                    ended = true
                    loadStatus()
                }
                if (!ended && update != null && status?.s("state") !in setOf("requested", "running") &&
                    System.currentTimeMillis() - started > 60_000L) {
                    // asked for and gone a minute on without being requested or running (StartUpdate refused for
                    // another reason than one under way, an idle status after a restart, a state this build does not
                    // know): no outcome of the node's to wait for, only its relays; said so, not "waiting"
                    ended = true
                    gaveUp = true
                    show()
                    loadStatus()
                }
                val elapsed = System.currentTimeMillis() - started
                if (ended && update != null && !relaysPending(update)) return@launch
                if (!ended && elapsed > 20 * 60_000L) {
                    // no outcome is not a failure: the node turns a run without one into failed only after an hour
                    shown(texts(R.string.raycontrol_admin_update_no_outcome))
                    return@launch
                }
                if (ended && elapsed > 3 * 60 * 60_000L) return@launch
                delay(if (ended) 30_000L else 7_000L)
            }
        }
    }

    // ---- the app's releases on the node (app-update-v1), client summaries (probe-v1 §6)

    private fun mirrorPage(mirror: JsonObject) = AdminPage.Detail(
        DetailKind.MIRROR, texts(R.string.ad_releases), mirrorSummary(mirror, texts, ::date, ::size),
        toggle = Tick("", texts(R.string.raycontrol_admin_mirror), mirror.b("enabled")),
    )

    private fun summaryPage(summary: JsonObject) = AdminPage.Detail(
        DetailKind.SUMMARY, texts(R.string.raycontrol_admin_summary), summarySummary(summary, texts),
        toggle = Tick("", texts(R.string.raycontrol_admin_summary), summary.b("enabled")),
        rows = if (summary.b("enabled")) listOf(DetailRow("networks", texts(R.string.raycontrol_admin_summary_networks))) else emptyList(),
    )

    private fun portsPage() = AdminPage.Detail(
        DetailKind.PORTS, texts(R.string.ad_ports_metrics),
        rows = listOfNotNull(
            DetailRow("ports", texts(R.string.raycontrol_admin_ports), texts(R.string.raycontrol_admin_ports_summary)).takeIf { !noPorts },
            DetailRow("metrics", texts(R.string.raycontrol_admin_metrics), texts(R.string.raycontrol_admin_metrics_summary)),
        ),
    )

    override fun toggle(on: Boolean) {
        when ((state.value.page as? AdminPage.Detail)?.kind) {
            DetailKind.MIRROR -> ask(texts(if (on) R.string.raycontrol_admin_mirror_turn_on else R.string.raycontrol_admin_mirror_turn_off)) {
                api({ call("SetAppMirror", body("enabled" to on)) }) { loadStatus() }
            }
            DetailKind.SUMMARY -> api({ call("SetSummary", body("enabled" to if (on) "on" else "off")) }) { loadStatus() }
            else -> {}
        }
    }

    override fun detail(id: String) {
        when ((state.value.page as? AdminPage.Detail)?.kind) {
            DetailKind.UPDATE -> updateAction(id)
            DetailKind.SUMMARY -> status?.summary?.let { summary ->
                push(AdminPage.Detail(
                    DetailKind.TEXT, texts(R.string.raycontrol_admin_summary_networks),
                    summaryNetworks(summary).ifEmpty { texts(R.string.raycontrol_admin_empty) }, mono = true,
                ))
            }
            DetailKind.PORTS -> if (id == "ports") showPorts() else showMetrics()
            else -> {}
        }
    }

    private fun showMetrics(): Unit = api({ call("GetMetrics").s("text") }) { text ->
        push(AdminPage.Detail(DetailKind.TEXT, texts(R.string.raycontrol_admin_metrics), metricSamples(text), mono = true))
    }

    /**
     * Every port of the node for a provider's firewall (GetPorts, raycontrol v0.27.0), a line each. A node before it
     * answers unimplemented: said once, and the row goes.
     */
    private fun showPorts(): Unit = api({ call("GetPorts").arr("ports") }, quiet = { RayApi.unimplemented(it) }, failed = { e ->
        if (RayApi.unimplemented(e)) {
            noPorts = true
            say(R.string.raycontrol_admin_ports_old)
            render()
        }
    }) { ports ->
        val lines = ports.joinToString("\n") { p -> portLine(p, texts) { time(it) } }.ifEmpty { texts(R.string.raycontrol_admin_empty) }
        push(AdminPage.Detail(
            DetailKind.TEXT, texts(R.string.raycontrol_admin_ports), texts(R.string.raycontrol_admin_ports_help) + "\n\n" + lines, mono = true,
        ))
    }

    // ---- users

    private fun loadUsers(): Unit = api({ call("ListUsers").arr("users") to call("ListServices") }, page = true) { (list, all) ->
        users = list
        services = all.arr("services")
        kinds = all.strs("kinds")
        render()
    }

    private fun userRow(user: JsonObject) = UserRow(user.s("name"), user.b("enabled"), userFacts(user, texts, ::date, ::size))

    private fun userInfo(user: JsonObject): UserInfo {
        val has = user.strs("services")
        return UserInfo(
            user.b("enabled"), userFacts(user, texts, ::date, ::size, services = false),
            services.orEmpty().map { Tick(it.s("name"), serviceTitle(it), it.s("name") in has) },
        )
    }

    private fun shownUser() = (state.value.page as? AdminPage.User)?.name

    override fun user(name: String) {
        devices = null
        push(AdminPage.User(name))
        loadDevices(name)
    }

    override fun userEnabled(on: Boolean) {
        val name = shownUser() ?: return
        api({ call("SetUserEnabled", body("name" to name, "enabled" to on)) }) { loadUsers() }
    }

    override fun userService(service: String, on: Boolean) {
        val name = shownUser() ?: return
        val user = users?.firstOrNull { it.s("name") == name } ?: return
        val has = user.strs("services")
        // the node's services in their order, the user's among them
        val chosen = services.orEmpty().map { it.s("name") }.filter { if (it == service) on else it in has }
        api({ call("SetUserServices", body("name" to name, "services" to chosen)) }) { loadUsers() }
    }

    override fun userMenu() {
        val name = shownUser() ?: return
        actions(name, listOf(texts(R.string.delete) to {
            ask(texts(R.string.raycontrol_admin_delete_user, name)) {
                api({ call("DeleteUser", body("name" to name)) }) { loadUsers() }
            }
        }))
    }

    /** A user in one form: their limits and the node's services they get (the enabled ones ticked), then a device's invite. */
    override fun newUser() = push(AdminPage.NewUser())

    private fun newUserPage(change: AdminPage.NewUser.() -> AdminPage.NewUser) = set {
        (page as? AdminPage.NewUser)?.let { top(it.change()) } ?: this
    }

    private fun invitePage(change: AdminPage.Invite.() -> AdminPage.Invite) = set {
        (page as? AdminPage.Invite)?.let { top(it.change()) } ?: this
    }

    override fun form(field: AdminField, text: String) = when (field) {
        AdminField.USER_NAME -> newUserPage { copy(name = text) }
        AdminField.USER_DAYS -> newUserPage { copy(days = text) }
        AdminField.USER_GB -> newUserPage { copy(gb = text) }
        AdminField.USER_NOTE -> newUserPage { copy(note = text) }
        AdminField.DEVICE -> invitePage { copy(device = text) }
    }

    override fun tick(service: String, on: Boolean) = newUserPage {
        copy(services = services.map { if (it.id == service) it.copy(on = on) else it })
    }

    override fun createUser() {
        val form = state.value.page as? AdminPage.NewUser ?: return
        val name = form.name.trim()
        if (name.isEmpty()) return say(R.string.raycontrol_admin_user_name_required)
        val request = newUserBody(name, form.days, form.gb, form.note, form.services.filter { it.on }.map { it.id }, now())
        api({ call("CreateUser", request) }, failed = { e ->
            // created with its answer lost: the user may be there, the list shows it; no invite on its own,
            // as "already exists" may be another user of that name (shown as the error it is)
            if (answerLost(e)) loadUsers()
        }) {
            // a user reaches the node through their devices: the first one's invite now, in the form's place
            set { if (page is AdminPage.NewUser) top(AdminPage.Invite(name, created = true)) else this }
            loadUsers()
        }
    }

    /**
     * New credentials for a user at once (RenewUser, raycontrol v0.27.0), for a leak: asked first, as every device of
     * the user is cut until it fetches its subscription. A node before it answers unimplemented.
     */
    override fun renew() {
        val user = shownUser() ?: return
        ask(texts(R.string.raycontrol_admin_renew_confirm, user)) {
            api({ call("RenewUser", body("name" to user)) }, quiet = { RayApi.unimplemented(it) }, failed = { e ->
                if (RayApi.unimplemented(e)) say(R.string.raycontrol_admin_renew_old)
            }) { answer -> message(renewText(answer, user, texts)) }
        }
    }

    // ---- devices and invites

    private fun loadDevices(user: String): Unit = api({ call("ListDevices", body("user" to user)) }) { list ->
        devices = user to list
        render()
    }

    /**
     * A device revoked, or an unused invite withdrawn (RevokeDevice). Since raycontrol v0.27.0 a revocation gives the
     * device's user new credentials, which cuts the user's other devices until they fetch their subscription: asked
     * with that, and said after. An older node says nothing of it, and the list shows what changed.
     */
    override fun revoke(device: DeviceRow) {
        val user = shownUser() ?: return
        ask(texts(if (device.invite) R.string.raycontrol_admin_invite_withdraw else R.string.raycontrol_admin_revoke, user, device.name)) {
            api({ call("RevokeDevice", body("id" to device.id)) }) { answer ->
                val text = revokeText(answer, user, device.name, texts)
                if (text == null) loadDevices(user) else message(text) { loadDevices(user) }
            }
        }
    }

    override fun invite() {
        shownUser()?.let { push(AdminPage.Invite(it)) }
    }

    override fun role(role: String) = invitePage { copy(role = role) }

    override fun pin(on: Boolean) = invitePage { copy(pin = on) }

    override fun ttl() {
        val page = state.value.page as? AdminPage.Invite ?: return
        choice(texts(R.string.raycontrol_admin_invite_ttl), host?.ttls().orEmpty(), page.ttl) { picked -> invitePage { copy(ttl = picked) } }
    }

    /**
     * One device's invite for the user: its name, its role, how long the invite holds, and a PIN told by another
     * channel. The short RCINV2 code where the node gives one: a smaller QR.
     */
    override fun createInvite() {
        val form = state.value.page as? AdminPage.Invite ?: return
        val user = form.user
        val deviceName = form.device.trim().ifEmpty { "phone" }
        val request = body("user" to user, "name" to deviceName, "role" to form.role,
            "ttlSeconds" to INVITE_TTLS[form.ttl.coerceIn(INVITE_TTLS.indices)].toString())
        // only when asked: a node before the PIN (api-v1 §3) refuses a field it does not know
        if (form.pin) request.addProperty("pin", true)
        api({ call("CreateInvite", request) }) { answer ->
            val code = answer.s("inviteCodeShort").ifEmpty { answer.s("inviteCode") }
            val expires = answer.getAsJsonObject("invite")?.l("expiresAt") ?: 0L
            var message = texts(R.string.raycontrol_admin_invite_created, user, deviceName, date(expires))
            val pin = answer.s("pin")
            if (pin.isNotEmpty()) message += "\n\n" + texts(R.string.raycontrol_admin_invite_pin_is, pin)
            // the invite registers a device: the page takes no screenshots while it shows one (AdminState.secret)
            set {
                copy(pages = pages.map { if (it is AdminPage.Invite && it.user == user) it.copy(result = InviteResult(code, message, pin)) else it })
            }
            // the user's page under it lists the invite
            if (devices?.first == user) loadDevices(user)
        }
    }

    /**
     * The user's profiles as links (ExportUser), for apps other than Niko: they hold the user's keys, and such an app
     * does not knock, so it reaches only what is open to everyone. Audited by the node.
     */
    override fun links() {
        val user = shownUser() ?: return
        api({ call("ExportUser", body("name" to user)).arr("profiles") }) { profiles ->
            val (share, shown) = linksText(profiles, user, texts) ?: return@api say(R.string.raycontrol_admin_empty)
            push(AdminPage.Detail(DetailKind.TEXT, texts(R.string.raycontrol_admin_links), shown, share = share))
        }
    }

    private fun shared() = when (val page = state.value.page) {
        is AdminPage.Invite -> page.result?.code
        is AdminPage.Detail -> page.share
        else -> null
    }

    override fun copy() {
        shared()?.let { host?.copy(it) }
    }

    override fun share() {
        shared()?.let { host?.share(it) }
    }

    // ---- services

    private fun loadServices(): Unit = api({ call("ListServices") }, page = true) { list ->
        services = list.arr("services")
        kinds = list.strs("kinds")
        render()
    }

    private fun serviceRow(service: JsonObject) = ServiceRow(
        service.s("name"), service.s("kind"), service.b("enabled"), "id ${service.l("id")} · ${date(service.l("createdAt"))}",
    )

    private fun rawService(name: String) = services?.firstOrNull { it.s("name") == name }

    private fun shownService() = (state.value.page as? AdminPage.Service)?.name?.let { rawService(it) }

    override fun service(name: String) = push(AdminPage.Service(name))

    override fun serviceEnabled(name: String, on: Boolean) {
        rawService(name)?.let { setServiceEnabled(it, on) }
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
                if (refused(e)) ask(texts(R.string.raycontrol_admin_last_ssh_disable, name)) {
                    setServiceEnabled(service, false, force = true)
                }
            },
        ) { loadServices() }
    }

    override fun deleteService() {
        val service = shownService() ?: return
        ask(texts(R.string.raycontrol_admin_delete_service, service.s("name"))) { deleteService(service) }
    }

    private fun deleteService(service: JsonObject, force: Boolean = false) {
        val name = service.s("name")
        val refused = { e: Throwable -> lastSshRefused(service.s("kind"), RayApi.code(e), force) }
        api({ call("DeleteService", body("name" to name, "force" to force.takeIf { it })) },
            quiet = refused,
            failed = { e ->
                if (refused(e)) ask(texts(R.string.raycontrol_admin_last_ssh_delete, name)) { deleteService(service, force = true) }
            },
        ) { loadServices() }
    }

    override fun key(key: ServiceKey) {
        val service = shownService() ?: return
        when (key) {
            ServiceKey.REALITY -> rotate(service.s("name"))
            // dnstt's seal key (dnsseal-v1 §8), masterdns's service key (masterdns-key-rotation §2): a new one at once
            ServiceKey.SEAL -> rotateKey(service, "seal_prev_until", R.string.raycontrol_admin_rotate_seal)
            ServiceKey.SERVICE -> rotateKey(service, "key_prev_until", R.string.raycontrol_admin_rotate_service)
        }
    }

    /** REALITY keys: on the lead time (clients get the next keys first), or now (a leaked key, a blocked name). */
    private fun rotate(name: String) = choice(
        texts(R.string.raycontrol_admin_rotate),
        listOf(texts(R.string.raycontrol_admin_rotate_planned), texts(R.string.raycontrol_admin_rotate_now)),
    ) { i ->
        api({ call("RotateService", body("name" to name, "now" to (i == 1))) }) { answer ->
            say(texts(R.string.raycontrol_admin_rotate_at, date(answer.l("switchAt"))))
        }
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
        val prevUntil = settingLong(service, prevUntilKey)
        val inGrace = prevUntil > now()
        choice(texts(title), listOf(texts(R.string.raycontrol_admin_rotate_key_grace), texts(R.string.raycontrol_admin_rotate_key_now))) { i ->
            when {
                i == 1 -> ask(texts(R.string.raycontrol_admin_rotate_key_now_confirm)) { rotateKeyNow(name, true) }
                inGrace -> ask(texts(R.string.raycontrol_admin_rotate_key_in_grace, date(prevUntil))) { rotateKeyNow(name, false, force = true) }
                else -> rotateKeyNow(name, false)
            }
        }
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
                answerLost(e) -> loadServices()
                graceRefused(now, force, e) -> {
                    loadServices()
                    ask(texts(R.string.raycontrol_admin_rotate_key_in_grace, date(RayApi.graceUntil(e)))) {
                        rotateKeyNow(name, false, force = true)
                    }
                }
            }
        },
    ) { answer ->
        val until = answer.l("prevUntil")
        say(if (until > 0) texts(R.string.raycontrol_admin_rotate_key_until, date(until)) else texts(R.string.raycontrol_admin_rotate_key_gone))
        loadServices()
    }

    /** A setting of the service: what it takes, then a question, as the node applies it at once; it says why not. */
    override fun option(key: String) {
        val service = shownService() ?: return
        val name = service.s("name")
        val option = optionsOf(service).firstOrNull { it.key == key } ?: return
        val title = texts(option.title)
        // applied at once (rayd renders and restarts the service): asked first, a wrong tap may cut its clients
        val change = { value: String ->
            ask(texts(R.string.raycontrol_admin_option_confirm, title, name, option.current, value)) {
                api({ call("SetServiceOption", body("name" to name, "key" to option.key, "value" to value)) }) {
                    say(texts(R.string.raycontrol_admin_option_set, option.key, value))
                    loadServices()
                }
            }
        }
        when {
            option.key == "target" -> editTarget(name, option)
            option.values != null -> choice(title, option.values, option.values.indexOf(option.current)) { change(option.values[it]) }
            else -> {
                val value = option.current.takeIf { it != "—" } ?: if (option.text) "" else "0"
                // a count or a rate may be longer than a port's five digits: text, as the node checks it
                input(title, listOf(InputField(title, value))) { typed -> change(typed[0].trim()) }
            }
        }
    }

    /**
     * A REALITY service's target (SetServiceOption target, raycontrol v0.27.0): its form checked here, the rest by the
     * node, which also reaches it as a client would before it takes it. Refused, the sheet comes back with what was
     * typed and the node's words.
     */
    private fun editTarget(name: String, option: ServiceOption, typed: String? = null, error: String? = null) {
        val title = texts(option.title)
        input(
            title, listOf(InputField(title, typed ?: option.current.takeIf { it != "—" }.orEmpty())),
            help = texts(R.string.raycontrol_admin_option_target_help), error = error,
        ) { fields ->
            val text = fields[0]
            val target = realityTarget(text)
            if (target == null) {
                editTarget(name, option, text, texts(R.string.raycontrol_admin_option_target_bad))
                return@input
            }
            ask(texts(R.string.raycontrol_admin_option_target_confirm, name, option.current, target)) {
                api({ call("SetServiceOption", body("name" to name, "key" to "target", "value" to target)) },
                    quiet = { e -> RayApi.code(e) == "invalid_argument" || targetUnknown(e.message) },
                    failed = { e ->
                        when {
                            // a node before v0.27.0 has no such option
                            targetUnknown(e.message) -> say(R.string.raycontrol_admin_option_target_old)
                            RayApi.code(e) == "invalid_argument" -> editTarget(name, option, target, nodeMessage(e.message.orEmpty()))
                        }
                    },
                ) {
                    say(texts(R.string.raycontrol_admin_option_set, option.key, target))
                    loadServices()
                }
            }
        }
    }

    /** A new service: its kind of those the node has, then its name. */
    override fun newService() {
        if (kinds.isEmpty()) return loadServices()
        val all = kinds
        choice(texts(R.string.raycontrol_admin_service_kind), all) { i ->
            val kind = all[i]
            input(
                kind, listOf(InputField(texts(R.string.raycontrol_admin_service_name), kind)),
                help = texts(R.string.raycontrol_admin_service_apply),
            ) { typed ->
                api({ call("CreateService", body("kind" to kind, "name" to typed[0].trim())) }) { loadServices() }
            }
        }
    }

    // ---- network: the node's DNS (its zone at the provider, the tunnels' zones) and its relays

    private fun loadNetwork(): Unit = api({
        NetworkRaw(
            optional { call("GetDNS") }, optional { call("ListRelays").arr("relays") },
            // the last periodic check (now=false runs none); raycontrol v0.13.0, and a rayd that checks zones
            runCatching { optional { call("CheckDNSZones") } }.getOrNull(),
        )
    }, page = true) { data ->
        network = data
        render()
    }

    private fun networkData(raw: NetworkRaw) = NetworkData(
        dns = dnsSummary(raw.dns, texts)?.first,
        zones = raw.zones?.let { zonesSummary(it, texts, ::date) },
        relays = raw.relays.orEmpty().map { r ->
            val probe = r.l("probePort").takeIf { it > 0 }?.let { " · probe $it" }.orEmpty()
            DetailRow(r.s("name"), texts(R.string.raycontrol_admin_relay, r.s("name")), "${r.s("address")} · ${date(r.l("createdAt"))}$probe")
        },
    )

    override fun dns() {
        val configured = dnsSummary(network?.dns, texts)?.second == true
        val list = mutableListOf<Pair<String, () -> Unit>>()
        if (configured) list += texts(R.string.raycontrol_admin_dns_sync) to {
            api({ call("SyncDNS").getAsJsonObject("status") ?: JsonObject() }) { status ->
                push(AdminPage.Detail(DetailKind.TEXT, texts(R.string.raycontrol_admin_dns_sync), dnsSyncText(status, texts), mono = true))
                loadNetwork()
            }
        }
        list += texts(R.string.raycontrol_admin_dns_token) to { dnsToken() }
        if (configured) list += texts(R.string.raycontrol_admin_dns_remove) to {
            ask(texts(R.string.raycontrol_admin_dns_remove_confirm)) {
                api({ call("RemoveDNS", body("keepRecords" to false)) }) { loadNetwork() }
            }
        }
        actions(texts(R.string.raycontrol_admin_dns), list)
    }

    /** The provider's API token (Cloudflare): kept by rayd, never shown again. */
    private fun dnsToken() = input(
        texts(R.string.raycontrol_admin_dns_token),
        listOf(InputField(texts(R.string.raycontrol_admin_dns_token_hint), input = FieldInput.SECRET)),
        help = texts(R.string.raycontrol_admin_dns_token_help),
    ) { typed ->
        api({ call("SetDNSProvider", body("provider" to "cloudflare", "token" to typed[0].trim())) }) { loadNetwork() }
    }

    /** The tunnels' zones from the node (CheckDNSZones now): the delegation, the front and backend, the resolvers. */
    override fun zones(): Unit = api({ call("CheckDNSZones", body("now" to true)) }) { answer ->
        push(AdminPage.Detail(DetailKind.TEXT, texts(R.string.raycontrol_admin_dns_check), zonesText(answer, texts, ::date), mono = true))
    }

    /** A relay host's SSH: its port and the addresses that reach it without SPA (SetRelayAccess; empty keeps). */
    override fun relay(name: String) = input(
        texts(R.string.raycontrol_admin_relay, name),
        listOf(
            InputField(texts(R.string.raycontrol_admin_relay_ssh_port), input = FieldInput.PORT),
            InputField(texts(R.string.raycontrol_admin_access_hint), multiLine = true),
        ),
        help = texts(R.string.raycontrol_admin_relay_access_help),
    ) { typed ->
        api({
            call("SetRelayAccess", body("name" to name, "sshPort" to (typed[0].toIntOrNull() ?: 0), "admin" to cidrs(typed[1])))
        }) { loadNetwork() }
    }

    // ---- audit

    private fun loadAudit(): Unit = api({ call("ListAudit", body("limit" to 200)).arr("entries") }, page = true) { entries ->
        audit = entries
        render()
    }

    // ---- what was typed, for a process the system killed: the pages by name and their forms; sheets are not kept

    fun save() = Bundle().apply {
        val pages = state.value.pages
        putStringArrayList(PAGES, ArrayList(pages.mapNotNull { page ->
            when (page) {
                is AdminPage.Status -> "status"
                is AdminPage.Users -> "users"
                is AdminPage.User -> "user\n${page.name}"
                is AdminPage.NewUser -> "new"
                is AdminPage.Invite -> "invite\n${page.user}"
                is AdminPage.Services -> "services"
                is AdminPage.Service -> "service\n${page.name}"
                is AdminPage.Network -> "network"
                // a page of the node's answer is asked for again
                is AdminPage.Detail -> page.kind.takeIf { it != DetailKind.TEXT }?.let { "detail\n${it.name}" }
            }
        }))
        (pages.first() as? AdminPage.Status)?.let {
            putString(NAME, it.name)
            putString(NOTE, it.note)
        }
        pages.filterIsInstance<AdminPage.NewUser>().lastOrNull()?.let {
            putStringArray(NEW_USER, arrayOf(it.name, it.days, it.gb, it.note))
            if (it.services.isNotEmpty()) putStringArrayList(TICKS, ArrayList(it.services.filter { s -> s.on }.map { s -> s.id }))
        }
        pages.filterIsInstance<AdminPage.Invite>().lastOrNull()?.let {
            putStringArray(INVITE, arrayOf(it.device, it.role, it.ttl.toString(), it.pin.toString(), it.created.toString()))
        }
    }

    private fun restore(saved: Bundle) {
        val form = saved.getStringArray(NEW_USER)
        val invite = saved.getStringArray(INVITE)
        restoredTicks = saved.getStringArrayList(TICKS)?.toSet()
        val pages = saved.getStringArrayList(PAGES).orEmpty().mapNotNull { token ->
            val name = token.substringAfter('\n', "")
            when (token.substringBefore('\n')) {
                "status" -> AdminPage.Status(name = saved.getString(NAME), note = saved.getString(NOTE))
                "users" -> AdminPage.Users()
                "user" -> AdminPage.User(name)
                "new" -> if (form?.size == 4) AdminPage.NewUser(form[0], form[1], form[2], form[3]) else AdminPage.NewUser()
                "invite" -> if (invite?.size == 5) {
                    AdminPage.Invite(name, invite[4].toBoolean(), invite[0], invite[1], invite[2].toIntOrNull() ?: 1, invite[3].toBoolean())
                } else AdminPage.Invite(name)
                "services" -> AdminPage.Services()
                "service" -> AdminPage.Service(name)
                "network" -> AdminPage.Network()
                "detail" -> when (runCatching { DetailKind.valueOf(name) }.getOrNull()) {
                    DetailKind.AUDIT -> AdminPage.Detail(DetailKind.AUDIT, texts(R.string.ad_audit))
                    DetailKind.UPDATE -> AdminPage.Detail(DetailKind.UPDATE, texts(R.string.raycontrol_admin_update))
                    DetailKind.MIRROR -> AdminPage.Detail(DetailKind.MIRROR, texts(R.string.ad_releases))
                    DetailKind.SUMMARY -> AdminPage.Detail(DetailKind.SUMMARY, texts(R.string.raycontrol_admin_summary))
                    DetailKind.PORTS -> portsPage()
                    else -> null
                }
                else -> null
            }
        }
        if (pages.firstOrNull() is AdminPage.Status) set { copy(pages = pages) }
    }

    private companion object {
        const val PAGES = "pages"
        const val NAME = "name"
        const val NOTE = "note"
        const val NEW_USER = "new_user"
        const val TICKS = "ticks"
        const val INVITE = "invite"
    }
}
