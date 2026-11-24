package io.niko.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import io.niko.R
import io.niko.SagerNet
import io.niko.bg.BaseService
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.toUniversalLink
import io.niko.group.GroupUpdater
import io.niko.ktx.Logs
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.showAllowingStateLoss
import io.niko.ktx.snackbar
import io.niko.ktx.startFilesForResult
import io.niko.raycontrol.RayNodes
import io.niko.ui.GroupSettingsActivity
import io.niko.ui.admin.AdminActivity
import io.niko.ui.compose.NikoDialog
import io.niko.ui.node.NodeFormat
import io.niko.ui.node.NodeFragment
import io.niko.ui.updateAllGroups
import io.niko.ui.updateRayNode
import io.niko.widget.QRCodeDialog
import io.niko.widget.UndoSnackbarManager
import java.text.DateFormat
import java.util.Date
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoTheme

/**
 * The main screen (D-MAIN, D-NAV), the first the app opens: [HomeScreen] over the activity's state. The activity keeps
 * the state (the service's callbacks and the database come to it) and does what the screen asks.
 */
class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val main = requireActivity() as MainActivity
            setContent {
                // the configs' sheet's test is the app's: it outlives this screen and the activity
                val testing by ConfigsTest.testing.collectAsState()
                val results by ConfigsTest.results.collectAsState()
                val stored by ConfigsTest.stored.collectAsState()
                val testTotal by ConfigsTest.total.collectAsState()
                // a test stored (also while this screen was away): its results into the rows, then read again
                LaunchedEffect(stored) { if (stored > 0) main.testStored() }
                NikoTheme {
                    HomeScreen(
                        main.homeState.value, main.homeView.value, main.homeActions,
                        // a profile deleted, its undo still offered, is out of the list already
                        configs = main.configsState.value?.let { ConfigsLoader.without(it, deleting.value) },
                        testing = testing, results = results,
                        configsActions = main.configsActions,
                        subs = main.subsState.value, subsActions = subsActions, format = format(),
                        configsMenus = configsMenus, testTotal = testTotal,
                        ask = main.homeSheet.value, onAsked = { main.homeSheet.value = null },
                    )
                }
            }
        }

    private fun format(): NodeFormat {
        val context = requireContext()
        val day = DateFormat.getDateInstance(DateFormat.MEDIUM)
        val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        return NodeFormat(
            day = { day.format(Date(it * 1000)) },
            time = { time.format(Date(it * 1000)) },
            bytes = { Formatter.formatShortFileSize(context, it) },
        )
    }

    private companion object {
        const val EXPORT_GROUP = "exportGroup"
        const val EXPORT_CONFIG = "exportedConfig"
    }

    // the group whose configs go to the file the user picks
    private var exportGroup = 0L

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val groupId = exportGroup
        if (uri == null || groupId == 0L) return@registerForActivityResult
        val resolver = requireContext().contentResolver
        runOnDefaultDispatcher {
            val message = try {
                resolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(links(groupId)) }
                getString(R.string.action_export_msg)
            } catch (e: Exception) {
                Logs.w(e)
                e.readableMessage
            }
            onMainDispatcher { if (isAdded) snackbar(message).show() }
        }
    }

    private fun links(groupId: Long) =
        SagerDatabase.proxyDao.getByGroup(groupId).joinToString("\n") { it.toStdLink(compact = true) }

    private fun groupSettings(id: Long) =
        startActivity(Intent(requireContext(), GroupSettingsActivity::class.java).putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, id))

    /**
     * How another device gets a node: an invite of its own, not this device's rows (salamander-device-v1 §6); an
     * admin of the node is sent to the panel that makes one.
     */
    private fun anotherDevice(row: SubRow) {
        val dialog = NikoDialog(requireContext()).title(R.string.subs_another_device)
            .message(if (row.admin) R.string.subs_another_device_admin else R.string.subs_another_device_message)
        val node = row.nodeId
        if (row.admin && node != null) {
            dialog.ghost(android.R.string.cancel).primary(R.string.raycontrol_admin) { _, _ ->
                startActivity(Intent(requireContext(), AdminActivity::class.java).putExtra(AdminActivity.EXTRA_NODE, node))
            }
        } else {
            dialog.primary(android.R.string.ok)
        }
        dialog.show()
    }

    private fun ask(message: String, no: Int = android.R.string.cancel, then: () -> Unit) {
        NikoDialog(requireContext()).title(R.string.confirm).message(message)
            .ghost(no)
            .primary(R.string.yes, danger = true) { _, _ -> then() }
            .show()
    }

    // the profiles deleted whose undo is still offered: gone from the sheet, not yet from the database
    private val deleting = mutableStateOf<Set<Long>>(emptySet())

    // as the old list: the delete is done when its snackbar goes (or the screen does), undone from it until then
    private var undo: UndoSnackbarManager<ProxyEntity>? = null

    private fun undoManager() = undo ?: UndoSnackbarManager(
        requireActivity() as MainActivity,
        object : UndoSnackbarManager.Interface<ProxyEntity> {
            override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                deleting.value -= actions.map { it.second.id }.toSet()
            }

            override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.map { it.second }
                val main = activity as? MainActivity
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                    onMainDispatcher {
                        main?.refreshHome()
                        deleting.value -= profiles.map { it.id }.toSet()
                    }
                }
            }
        },
    ).also { undo = it }

    // the configuration that goes to the file the user picks
    private var exportedConfig: String? = null

    private val exportConfig = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val config = exportedConfig
        exportedConfig = null
        if (uri == null || config == null) return@registerForActivityResult
        val resolver = requireContext().contentResolver
        runOnDefaultDispatcher {
            val message = try {
                resolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(config) }
                getString(R.string.action_export_msg)
            } catch (e: Exception) {
                Logs.w(e)
                e.readableMessage
            }
            onMainDispatcher { if (isAdded) snackbar(message).show() }
        }
    }

    /** Asks before [profiles] are deleted, [names] listed under the question; the sheet reads its rows again after. */
    private suspend fun askDelete(profiles: List<ProxyEntity>, names: String? = null) {
        if (profiles.isEmpty()) return
        onMainDispatcher {
            if (!isAdded) return@onMainDispatcher
            val main = requireActivity() as MainActivity
            ask(getString(R.string.delete_confirm_prompt) + names?.let { "\n" + it }.orEmpty(), no = R.string.no) {
                runOnDefaultDispatcher {
                    ConfigsOps.delete(profiles)
                    onMainDispatcher { main.refreshHome() }
                }
            }
        }
    }

    /** What the sheet's menus ask (a group's ⋮, a profile's sheet): the old profiles' list's actions, from here. */
    private val configsMenus = object : ConfigsMenuActions {
        override fun group(groupId: Long, action: GroupAction) {
            val main = requireActivity() as MainActivity
            when (action) {
                // from the device itself, not through the service
                GroupAction.TCP_PING -> ConfigsTest.start(groupId, null, ConfigsTest.Mode.TCP)
                GroupAction.URL_TEST -> main.configsActions.test(groupId)
                GroupAction.CLEAR_RESULTS -> runOnDefaultDispatcher {
                    ConfigsOps.clearResults(groupId)
                    onMainDispatcher { main.refreshHome() }
                }
                GroupAction.CLEAR_TRAFFIC -> runOnDefaultDispatcher {
                    ConfigsOps.clearTraffic(groupId)
                    onMainDispatcher { main.refreshHome() }
                }
                GroupAction.DELETE_UNAVAILABLE -> runOnDefaultDispatcher { askDelete(ConfigsOps.unavailable(groupId)) }
                GroupAction.REMOVE_DUPLICATES -> runOnDefaultDispatcher {
                    val toClear = ConfigsOps.duplicates(groupId)
                    askDelete(toClear, ConfigsOps.names(toClear))
                }
            }
        }

        override fun share(id: Long, action: ShareAction) {
            fun export(link: String) =
                snackbar(if (SagerNet.trySetPrimaryClip(link)) R.string.action_export_msg else R.string.action_export_err).show()
            runOnDefaultDispatcher {
                try {
                    val entity = ProfileManager.getProfile(id) ?: return@runOnDefaultDispatcher
                    val name = entity.displayName()
                    val text = when (action) {
                        ShareAction.QR, ShareAction.LINK -> entity.toStdLink()
                        ShareAction.QR_SN, ShareAction.LINK_SN -> entity.requireBean().toUniversalLink()
                        ShareAction.CONFIG_CLIPBOARD, ShareAction.CONFIG_FILE -> ""
                    }
                    val config = if (text.isEmpty()) entity.exportConfig() else null
                    onMainDispatcher {
                        if (!isAdded) return@onMainDispatcher
                        when (action) {
                            ShareAction.QR, ShareAction.QR_SN -> QRCodeDialog(text, name).showAllowingStateLoss(parentFragmentManager)
                            ShareAction.LINK, ShareAction.LINK_SN -> export(text)
                            ShareAction.CONFIG_CLIPBOARD -> export(config!!.first)
                            ShareAction.CONFIG_FILE -> {
                                exportedConfig = config!!.first
                                startFilesForResult(exportConfig, config.second)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher { if (isAdded) snackbar(e.readableMessage).show() }
                }
            }
        }

        override fun delete(id: Long) {
            runOnDefaultDispatcher {
                val entity = ProfileManager.getProfile(id) ?: return@runOnDefaultDispatcher
                // as the old list: not the one the running service uses
                if (DataStore.serviceState.started && DataStore.selectedProxy == id && DataStore.currentProfile == id) return@runOnDefaultDispatcher
                onMainDispatcher {
                    if (!isAdded) return@onMainDispatcher
                    deleting.value += id
                    undoManager().remove(0 to entity)
                }
            }
        }

        // as the old list's drag: not while the service connects or stops
        override fun move(id: Long, other: Long) {
            if (!DataStore.serviceState.let { it.canStop || it == BaseService.State.Stopped }) return
            val main = requireActivity() as MainActivity
            runOnDefaultDispatcher {
                ConfigsOps.moveProfile(id, other)
                main.refreshConfigs()
            }
        }
    }

    // the system's file picker may outlive this screen (a rotation under it): what waits for its answer is kept
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            exportGroup = it.getLong(EXPORT_GROUP)
            exportedConfig = it.getString(EXPORT_CONFIG)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(EXPORT_GROUP, exportGroup)
        // a config is a few kilobytes; one too large for the saved state is given up rather than crash the save
        exportedConfig?.takeIf { it.length < 200_000 }?.let { outState.putString(EXPORT_CONFIG, it) }
    }

    // done now: after the screen there is no undo to offer
    override fun onDestroy() {
        undo?.flush()
        super.onDestroy()
    }

    /** What the sheet's Subscriptions tab asks (P-Subs): the old groups' list's actions, from here. */
    private val subsActions = object : SubsActions {
        override fun open(row: SubRow) {
            val node = row.nodeId
            when {
                node != null -> (requireActivity() as MainActivity).displayFragment(NodeFragment.of(node, R.id.nav_home))
                row.kind != SubKind.UNGROUPED -> groupSettings(row.id)
            }
        }

        override fun update(row: SubRow) {
            runOnDefaultDispatcher {
                val group = SagerDatabase.groupDao.getById(row.id) ?: return@runOnDefaultDispatcher
                val node = row.nodeId?.let { RayNodes.get(it) }
                onMainDispatcher {
                    if (!isAdded) return@onMainDispatcher
                    if (node != null) updateRayNode(node, row.id) else GroupUpdater.startUpdate(group, true)
                }
            }
        }

        override fun updateAll() = updateAllGroups()

        override fun add() = startActivity(Intent(requireContext(), GroupSettingsActivity::class.java))

        override fun move(row: SubRow, other: SubRow) {
            val main = requireActivity() as MainActivity
            runOnDefaultDispatcher {
                ConfigsOps.moveGroup(row.id, other.id)
                main.refreshConfigs()
            }
        }

        override fun action(row: SubRow, action: SubAction) {
            when (action) {
                // the sheet asks to move with what to change places with (move)
                SubAction.MOVE_UP, SubAction.MOVE_DOWN -> Unit
                SubAction.SETTINGS -> groupSettings(row.id)
                SubAction.ADMIN -> row.nodeId?.let {
                    startActivity(Intent(requireContext(), AdminActivity::class.java).putExtra(AdminActivity.EXTRA_NODE, it))
                }
                SubAction.LAN -> row.nodeId?.let { startActivity(io.niko.ui.node.LanActivity.intent(requireContext(), it)) }
                SubAction.QR, SubAction.COPY_LINK -> runOnDefaultDispatcher {
                    val group = SagerDatabase.groupDao.getById(row.id) ?: return@runOnDefaultDispatcher
                    val link = group.toUniversalLink()
                    onMainDispatcher {
                        if (!isAdded) return@onMainDispatcher
                        if (action == SubAction.QR) QRCodeDialog(link, group.displayName()).showAllowingStateLoss(parentFragmentManager)
                        else snackbar(if (SagerNet.trySetPrimaryClip(link)) R.string.action_export_msg else R.string.action_export_err).show()
                    }
                }
                SubAction.ANOTHER_DEVICE -> anotherDevice(row)
                SubAction.EXPORT_CLIPBOARD -> runOnDefaultDispatcher {
                    val text = links(row.id)
                    onMainDispatcher {
                        if (!isAdded) return@onMainDispatcher
                        snackbar(if (SagerNet.trySetPrimaryClip(text)) R.string.action_export_msg else R.string.action_export_err).show()
                    }
                }
                SubAction.EXPORT_FILE -> {
                    exportGroup = row.id
                    startFilesForResult(exportFile, "profiles_${row.name}.txt")
                }
                SubAction.CLEAR -> ask(getString(R.string.subs_clear_confirm, row.name)) {
                    runOnDefaultDispatcher { GroupManager.clearGroup(row.id) }
                }
                SubAction.DELETE -> ask(getString(R.string.subs_delete_confirm, row.name)) {
                    runOnDefaultDispatcher { GroupManager.deleteGroup(row.id) }
                }
            }
        }
    }

    // back from another screen, or the app back to the front: profiles, rules or pings may have changed meanwhile
    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).refreshHome()
    }
}
