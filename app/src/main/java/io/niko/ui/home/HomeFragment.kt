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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.SagerNet
import io.niko.database.GroupManager
import io.niko.database.SagerDatabase
import io.niko.fmt.toUniversalLink
import io.niko.group.GroupUpdater
import io.niko.ktx.Logs
import io.niko.ktx.danger
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.showAllowingStateLoss
import io.niko.ktx.snackbar
import io.niko.ktx.startFilesForResult
import io.niko.raycontrol.RayNodes
import io.niko.ui.GroupSettingsActivity
import io.niko.ui.admin.AdminActivity
import io.niko.ui.node.NodeFormat
import io.niko.ui.node.NodeFragment
import io.niko.ui.updateAllGroups
import io.niko.ui.updateRayNode
import io.niko.widget.QRCodeDialog
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
                // a test stored (also while this screen was away): its results into the rows, then read again
                LaunchedEffect(stored) { if (stored > 0) main.testStored() }
                NikoTheme {
                    HomeScreen(
                        main.homeState.value, main.homeView.value, main.homeActions,
                        configs = main.configsState.value, testing = testing, results = results,
                        configsActions = main.configsActions,
                        subs = main.subsState.value, subsActions = subsActions, format = format(),
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
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.subs_another_device)
            .setMessage(if (row.admin) R.string.subs_another_device_admin else R.string.subs_another_device_message)
        val node = row.nodeId
        if (row.admin && node != null) {
            dialog.setPositiveButton(R.string.raycontrol_admin) { _, _ ->
                startActivity(Intent(requireContext(), AdminActivity::class.java).putExtra(AdminActivity.EXTRA_NODE, node))
            }.setNegativeButton(android.R.string.cancel, null)
        } else {
            dialog.setPositiveButton(android.R.string.ok, null)
        }
        dialog.show()
    }

    private fun ask(message: String, then: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm).setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ -> then() }
            .setNegativeButton(android.R.string.cancel, null)
            .show().danger()
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

        override fun action(row: SubRow, action: SubAction) {
            when (action) {
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
