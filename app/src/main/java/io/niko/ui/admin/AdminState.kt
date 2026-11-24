package io.niko.ui.admin

import io.niko.ui.settings.FieldInput

/**
 * The admin panel of a raycontrol node (P-Admin, D-ADMIN-*): [node] its label; [pages] the pages opened one over
 * another, the last one shown; [busy] a call to the node is running; [error] why the shown page could not be read;
 * [sheet] what is asked over the page.
 */
data class AdminState(
    val node: String,
    val pages: List<AdminPage> = listOf(AdminPage.Status()),
    val busy: Boolean = false,
    val error: String? = null,
    val sheet: AdminSheet? = null,
) {
    val page get() = pages.last()

    /** The page shows what registers a device or holds a user's keys: no screenshots of it. */
    val secret get() = page.let { (it is AdminPage.Invite && it.result != null) || (it is AdminPage.Detail && it.share != null) }

    fun top(page: AdminPage) = copy(pages = pages.dropLast(1) + page)
    fun push(page: AdminPage) = copy(pages = pages + page, error = null)
    fun pop() = if (pages.size > 1) copy(pages = pages.dropLast(1), error = null) else this
}

sealed interface AdminPage {
    /** P-Admin. [name] and [note] as typed; null: as the node has them. [noteError]: why the note was not taken. */
    data class Status(val data: StatusData? = null, val name: String? = null, val note: String? = null, val noteError: String? = null) : AdminPage {
        val nameShown get() = name ?: data?.name.orEmpty()
        val noteShown get() = note ?: data?.note.orEmpty()
        val nameChanged get() = name != null && name != data?.name
        val noteChanged get() = note != null && note != data?.note
    }

    /** The node's users; null until read. */
    data class Users(val users: List<UserRow>? = null) : AdminPage

    /** P-AdminUser. [info] null until the users are read; [devices] null until the user's devices are. */
    data class User(val name: String, val info: UserInfo? = null, val devices: List<DeviceRow>? = null) : AdminPage

    /** A new user in one form: the limits and the node's services they get. */
    data class NewUser(
        val name: String = "", val days: String = "0", val gb: String = "0", val note: String = "",
        val services: List<Tick> = emptyList(),
    ) : AdminPage

    /** P-AdminInvite: one device's invite for [user]; [created]: the user was made just now. [ttl] of [INVITE_TTLS]. */
    data class Invite(
        val user: String, val created: Boolean = false, val device: String = "phone", val role: String = "user",
        val ttl: Int = 1, val pin: Boolean = false, val result: InviteResult? = null,
    ) : AdminPage

    /** P-AdminServices; null until read. */
    data class Services(val services: List<ServiceRow>? = null) : AdminPage

    /** A service: its settings and keys. [info] null until the services are read. */
    data class Service(val name: String, val info: ServiceInfo? = null) : AdminPage

    /** The node's DNS and its relays; null until read. */
    data class Network(val data: NetworkData? = null) : AdminPage

    /**
     * A page of text and rows: [text] to read ([mono] the node's own lines), [toggle] its switch, [rows] what it
     * offers; [share] what its text holds to share or copy (a user's links).
     */
    data class Detail(
        val kind: DetailKind, val title: String, val text: String? = null, val mono: Boolean = false,
        val toggle: Tick? = null, val rows: List<DetailRow> = emptyList(), val share: String? = null,
    ) : AdminPage
}

/** How long an invite holds, seconds: an hour, a day, a week (R.array.raycontrol_admin_ttls names them). */
val INVITE_TTLS = longArrayOf(3600, 86400, 7 * 86400)

enum class DetailKind { AUDIT, UPDATE, MIRROR, SUMMARY, PORTS, TEXT }

/** Where the status page's rows lead. */
enum class AdminDest { USERS, SERVICES, NETWORK, AUDIT, UPDATE, MIRROR, SUMMARY, PORTS, ACCESS }

/** A field of the new user's and the invite's forms. */
enum class AdminField { USER_NAME, USER_DAYS, USER_GB, USER_NOTE, DEVICE }

/** The keys a service changes. */
enum class ServiceKey { REALITY, SEAL, SERVICE }

/**
 * What GetStatus and its neighbours say. [hasName], [hasNote]: the node takes one; [update] null from a node without
 * updates by request; [mirror], [summary]: on or off, null from a node without them; [ports]: the node lists them.
 */
data class StatusData(
    val device: String,
    val host: String,
    val version: String,
    val nodeId: String,
    val warnings: List<String> = emptyList(),
    val hasName: Boolean = true,
    val name: String = "",
    val hasNote: Boolean = true,
    val note: String = "",
    val users: Long = 0,
    val services: Long = 0,
    val devices: Long = 0,
    val spaPorts: String = "",
    val update: UpdateRow? = null,
    val mirror: Boolean? = null,
    val summary: Boolean? = null,
    val ports: Boolean = true,
    val access: List<String> = emptyList(),
)

/** The version row: [versions] installed, or installed → latest; [offer] the release its button takes. */
data class UpdateRow(val versions: String, val offer: String? = null)

data class UserRow(val name: String, val enabled: Boolean, val facts: String)

/** A user: [facts] the expiry, the limit and the note; [services] the node's, the user's ticked. */
data class UserInfo(val enabled: Boolean, val facts: String, val services: List<Tick>)

/** A device of a user, or an invite not used yet ([invite]); [revocable]: not revoked already. */
data class DeviceRow(
    val id: String, val name: String, val title: String, val sub: String,
    val invite: Boolean = false, val revocable: Boolean = true,
)

/** Something to tick or switch: a service by [id], a page's switch. */
data class Tick(val id: String, val title: String, val on: Boolean)

/** A made invite: its [code] for the QR, what to tell with it, the [pin] told apart. */
data class InviteResult(val code: String, val message: String, val pin: String = "")

data class ServiceRow(val name: String, val kind: String, val enabled: Boolean, val facts: String)

data class ServiceInfo(val row: ServiceRow, val options: List<OptionRow> = emptyList(), val keys: List<ServiceKey> = emptyList())

data class OptionRow(val key: String, val title: String, val value: String)

/** [dns] the provider's lines, null from a node without the call; [zones] the last check of the tunnels' zones. */
data class NetworkData(val dns: String? = null, val zones: String? = null, val relays: List<DetailRow> = emptyList())

/** A row of a page: [id] what a press on it means to the page's owner; not [action]: only read. */
data class DetailRow(val id: String, val title: String, val sub: String? = null, val action: Boolean = true)

sealed interface AdminSheet {
    /** A question before something is done. */
    data class Confirm(val text: String) : AdminSheet

    /** What was done, to read. */
    data class Message(val text: String) : AdminSheet

    /** Rows to pick one of; [selected] the one in effect, null where they are actions. */
    data class Choice(val title: String, val options: List<String>, val selected: Int? = null) : AdminSheet

    /** Fields to fill: [help] over them, [error] why the last try was not taken. */
    data class Input(val title: String, val fields: List<InputField>, val help: String? = null, val error: String? = null) : AdminSheet
}

data class InputField(val label: String, val value: String = "", val input: FieldInput = FieldInput.TEXT, val multiLine: Boolean = false)

/** What the panel's pages ask of their owner. */
interface AdminActions {
    fun back()
    /** the shown page read again */
    fun refresh()
    fun open(dest: AdminDest)

    fun name(text: String)
    fun saveName()
    fun note(text: String)
    fun saveNote()
    /** the node to the release the version row offers, after a question */
    fun update()

    fun user(name: String)
    fun newUser()
    fun form(field: AdminField, text: String)
    fun tick(service: String, on: Boolean)
    fun createUser()

    fun userEnabled(on: Boolean)
    fun userService(service: String, on: Boolean)
    fun invite()
    /** a device revoked, an invite withdrawn; after a question */
    fun revoke(device: DeviceRow)
    fun links()
    fun renew()
    fun userMenu()

    fun role(role: String)
    fun ttl()
    fun pin(on: Boolean)
    fun createInvite()
    fun copy()
    fun share()

    fun service(name: String)
    fun newService()
    fun serviceEnabled(name: String, on: Boolean)
    fun option(key: String)
    fun key(key: ServiceKey)
    fun deleteService()

    fun dns()
    fun zones()
    fun relay(name: String)

    fun detail(id: String)
    fun toggle(on: Boolean)

    fun input(index: Int, text: String)
    fun submit()
    fun choose(index: Int)
    fun confirm()
    fun dismiss()
}
