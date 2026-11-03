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
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
        private const val AUDIT = 3
        private const val MENU_ADD = 1
    }

    private class Row(val title: String, val summary: String = "", val action: (() -> Unit)? = null)

    private lateinit var binding: LayoutRayAdminBinding
    private lateinit var node: RayNode
    private val adapter = RowAdapter()
    private var tab = STATUS
    private var services: List<JsonObject> = emptyList()
    private var kinds: List<String> = emptyList()

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
            subtitle = node.host
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }
        binding.recyclerView.layoutManager = FixedLinearLayoutManager(binding.recyclerView)
        binding.recyclerView.adapter = adapter
        binding.refreshLayout.setColorSchemeColors(getColorAttr(R.attr.primaryOrTextPrimary))
        binding.refreshLayout.setOnRefreshListener { load() }
        for (title in listOf(R.string.raycontrol_admin_status, R.string.raycontrol_admin_users,
            R.string.raycontrol_admin_services, R.string.raycontrol_admin_audit)) {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_ADD) {
            if (tab == USERS) createUser() else if (tab == SERVICES) createService()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ---- calls

    /** Runs [call] off the main thread with the refresh indicator; errors go to a snackbar. */
    private fun <T> api(call: () -> T, done: (T) -> Unit) {
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
                result.onSuccess(done).onFailure { snackbar(failure(it)).show() }
            }
        }
    }

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
        else -> loadAudit()
    }

    private fun show(rows: List<Row>) {
        adapter.rows = rows.ifEmpty { listOf(Row(getString(R.string.raycontrol_admin_empty))) }
    }

    // ---- status

    private fun loadStatus(): Unit = api({
        Triple(call("GetStatus"), RayApi.call(current(), RayApi.DEVICE + "GetSelf"), call("GetAdminAccess"))
    }) { (status, self, access) ->
        show(listOf(
            Row(getString(R.string.raycontrol_admin_this_device),
                "${self.s("user")}/${self.s("name")} · ${self.s("role")}"),
            Row(getString(R.string.raycontrol_admin_node), "${status.s("host")} · ${status.s("version")}\n${status.s("nodeId")}"),
            Row(getString(R.string.raycontrol_admin_counts, status.l("users"), status.l("services"), status.l("activeDevices"))),
            Row(getString(R.string.raycontrol_admin_spa_ports), status.strs("spaPorts").joinToString(", ")),
            Row(getString(R.string.raycontrol_admin_metrics), getString(R.string.raycontrol_admin_metrics_summary)) { showMetrics() },
            Row(getString(R.string.raycontrol_admin_access),
                access.strs("cidrs").joinToString(", ").ifEmpty { getString(R.string.raycontrol_admin_access_none) }) {
                editAdminAccess(access.strs("cidrs"))
            },
        ))
    }

    private fun showMetrics(): Unit = api({ call("GetMetrics").s("text") }) { text ->
        // the samples only: the HELP and TYPE lines double the size
        val samples = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.joinToString("\n")
        textDialog(getString(R.string.raycontrol_admin_metrics), samples.ifEmpty { text })
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
        show(users.map { u ->
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
            R.string.raycontrol_admin_devices to { showDevices(name) },
            R.string.raycontrol_admin_invite to { invite(name) },
            (if (enabled) R.string.disable else R.string.enable) to {
                api({ call("SetUserEnabled", body("name" to name, "enabled" to !enabled)) }) { loadUsers() }
            },
            R.string.raycontrol_admin_user_services to { chooseServices(user.strs("services")) { chosen ->
                api({ call("SetUserServices", body("name" to name, "services" to chosen)) }) { loadUsers() }
            } },
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

    private fun createUser() {
        val name = editText(getString(R.string.raycontrol_admin_user_name))
        val days = editText(getString(R.string.raycontrol_admin_user_days), "0", number = true)
        val gb = editText(getString(R.string.raycontrol_admin_user_gb), "0", number = true)
        val note = editText(getString(R.string.raycontrol_admin_user_note))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_user_new)
            .setView(padded(name, days, gb, note))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val userName = name.text.toString().trim()
                if (userName.isEmpty()) return@setPositiveButton
                val expires = days.text.toString().toLongOrNull()?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it * 86400 } ?: 0L
                val limit = gb.text.toString().toLongOrNull()?.takeIf { it > 0 }?.let { it * 1_000_000_000L } ?: 0L
                chooseServices(emptyList()) { chosen ->
                    api({
                        call("CreateUser", body("name" to userName, "expiresAt" to expires.toString(),
                            "trafficLimitBytes" to limit.toString(), "note" to note.text.toString().trim(), "services" to chosen))
                    }) { loadUsers() }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                if (d.l("revokedAt") == 0L) revoke(user, d.s("id"), d.s("name"))
            }
        } + list.arr("invites").map { inv ->
            Row("${inv.s("name")} · ${inv.s("role")}", getString(R.string.raycontrol_admin_invite_pending, date(inv.l("expiresAt")))) {
                revoke(user, inv.s("id"), inv.s("name"))
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

    private fun revoke(user: String, id: String, name: String): Unit =
        confirm(getString(R.string.raycontrol_admin_revoke, name)) {
            api({ call("RevokeDevice", body("id" to id)) }) { showDevices(user) }
        }

    private fun invite(user: String) {
        val name = editText(getString(R.string.raycontrol_admin_device_name), "phone")
        val roles = arrayOf("user", "admin")
        val ttls = longArrayOf(3600, 86400, 7 * 86400)
        var role = 0
        var ttl = 1
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.raycontrol_admin_invite_for, user))
            .setView(padded(name))
            .setPositiveButton(R.string.raycontrol_admin_next) { _, _ ->
                val deviceName = name.text.toString().trim().ifEmpty { "phone" }
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.raycontrol_admin_invite_role)
                    .setSingleChoiceItems(roles, role) { _, i -> role = i }
                    .setPositiveButton(R.string.raycontrol_admin_next) { _, _ ->
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.raycontrol_admin_invite_ttl)
                            .setSingleChoiceItems(resources.getStringArray(R.array.raycontrol_admin_ttls), ttl) { _, i -> ttl = i }
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                api({
                                    call("CreateInvite", body("user" to user, "name" to deviceName, "role" to roles[role],
                                        "ttlSeconds" to ttls[ttl].toString()))
                                }) { answer ->
                                    val code = answer.s("inviteCode")
                                    val expires = answer.getAsJsonObject("invite")?.l("expiresAt") ?: 0L
                                    showInvite(code, getString(R.string.raycontrol_admin_invite_created, user, deviceName, date(expires)))
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.raycontrol_admin_invite)
            .setView(padded(text, image))
            .setPositiveButton(R.string.share) { _, _ ->
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code), getString(R.string.share)))
            }
            .setNeutralButton(R.string.action_copy) { _, _ ->
                if (SagerNet.trySetPrimaryClip(code)) snackbar(R.string.copy_success).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
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
            (if (enabled) R.string.disable else R.string.enable) to {
                api({ call("SetServiceEnabled", body("name" to name, "enabled" to !enabled)) }) { loadServices() }
            },
        )
        if (service.s("kind").contains("reality")) actions += R.string.raycontrol_admin_rotate to { rotate(name) }
        actions += R.string.delete to { confirm(getString(R.string.raycontrol_admin_delete_service, name)) {
            api({ call("DeleteService", body("name" to name)) }) { loadServices() }
        } }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

// protobuf JSON as the node answers: int64 as strings, defaults (false, 0, empty) left out
private fun JsonObject.s(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
private fun JsonObject.l(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: 0L
private fun JsonObject.b(name: String): Boolean = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
private fun JsonObject.arr(name: String): List<JsonObject> = (get(name) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.strs(name: String): List<String> = (get(name) as? JsonArray)?.map { it.asString }.orEmpty()
