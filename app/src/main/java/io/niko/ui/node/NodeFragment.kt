package io.niko.ui.node

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.niko.R
import io.niko.database.GroupManager
import io.niko.database.ProxyGroup
import io.niko.group.GroupUpdater
import io.niko.ktx.Logs
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.raycontrol.RayNodes
import io.niko.ui.about.AboutFragment
import io.niko.ui.MainActivity
import io.niko.ui.checkRayChannel
import io.niko.ui.compose.NikoTheme
import io.niko.ui.home.ConfigsTest
import io.niko.ui.openRayAdmin
import io.niko.ui.rayTestRun
import io.niko.ui.showRayLanDialog
import io.niko.ui.showRayNodeActions
import io.niko.ui.updateRayNode
import java.text.DateFormat
import java.util.Date

/**
 * P-Node: one raycontrol node's card, opened from its group or from the main screen's nodes. It reads the node off the
 * main thread and again on what changes it: its group reloaded (an update, a test stored), the screen back in front.
 */
class NodeFragment : Fragment(), GroupManager.Listener {

    companion object {
        private const val NODE = "node"
        private const val FROM = "from"

        /** [from]: the drawer's screen back leads to (the groups, or the main screen). */
        fun of(nodeId: String, @IdRes from: Int) = NodeFragment().apply {
            arguments = Bundle().apply {
                putString(NODE, nodeId)
                putInt(FROM, from)
            }
        }
    }

    private val nodeId get() = requireArguments().getString(NODE)!!
    private val state = mutableStateOf<NodeCardState?>(null)
    private val updating = mutableStateOf(false)
    private var groupId = 0L

    // reads one at a time, the asks meanwhile merged into one more (a test stores its results in a burst of reloads)
    private val reading = java.util.concurrent.atomic.AtomicBoolean()
    @Volatile
    private var dirty = false

    private val main get() = requireActivity() as MainActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val context = inflater.context
            val day = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val format = NodeFormat(
                day = { day.format(Date(it * 1000)) },
                time = { time.format(Date(it * 1000)) },
                bytes = { Formatter.formatShortFileSize(context, it) },
            )
            setContent {
                val testing by ConfigsTest.testing.collectAsState()
                val results by ConfigsTest.results.collectAsState()
                val stored by ConfigsTest.stored.collectAsState()
                // a test stored: its pings are in the rows now, the test's own results let go (as the configs' sheet does)
                LaunchedEffect(stored) { if (stored > 0) main.testStored() }
                NikoTheme {
                    NodeScreen(
                        state.value, testing, results, format, System.currentTimeMillis() / 1000, actions,
                        updating = updating.value,
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GroupManager.addListener(this)
    }

    override fun onDestroy() {
        GroupManager.removeListener(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Reads the card again (any thread); a node no longer here (removed meanwhile) leads back. */
    fun reload() {
        dirty = true
        if (!reading.compareAndSet(false, true)) return
        runOnDefaultDispatcher {
            try {
                while (dirty) {
                    dirty = false
                    val loaded = try {
                        NodeLoader.load(nodeId)
                    } catch (e: Exception) {
                        Logs.w(e)
                        continue
                    }
                    onMainDispatcher {
                        if (!isAdded) return@onMainDispatcher
                        if (loaded == null) {
                            back()
                            return@onMainDispatcher
                        }
                        groupId = loaded.groupId
                        state.value = loaded
                        updating.value = loaded.groupId in GroupUpdater.updating
                    }
                }
            } finally {
                reading.set(false)
            }
            // an ask that came after the loop's last check and before the flag went down
            if (dirty) reload()
        }
    }

    /** Back where the card was opened from: the groups or the main screen. */
    fun back() {
        val from = requireArguments().getInt(FROM, R.id.nav_home)
        main.displayFragmentWithId(from)
    }

    // the node's group changed: its rows, its update's progress, its pings
    override suspend fun groupAdd(group: ProxyGroup) {}
    override suspend fun groupUpdated(group: ProxyGroup) = groupUpdated(group.id)
    override suspend fun groupRemoved(groupId: Long) = groupUpdated(groupId)
    override suspend fun groupUpdated(groupId: Long) {
        if (groupId == this.groupId) reload()
    }

    private val actions = object : NodeActions {
        override fun back() = this@NodeFragment.back()

        // the node as it is now, read off the main thread (the card's copy may be older than the store)
        private fun withNode(block: (io.niko.raycontrol.RayNode) -> Unit) {
            runOnDefaultDispatcher {
                val node = RayNodes.get(nodeId) ?: return@runOnDefaultDispatcher
                onMainDispatcher { if (isAdded) block(node) }
            }
        }

        override fun update() = withNode { node ->
            updating.value = true
            updateRayNode(node, node.groupId) { reload() }
        }

        override fun more() = withNode { node -> showRayNodeActions(node, card = true) { back() } }

        override fun test(groupId: Long) = main.configsActions.test(groupId)

        override fun stopTest() = main.configsActions.stopTest()

        override fun select(id: Long) {
            // chosen at once on the card; the store follows (the service reloaded with it)
            state.value = state.value?.let { card -> card.copy(profiles = card.profiles.map { it.copy(selected = it.id == id) }) }
            main.configsActions.select(id)
        }

        override fun channel() = withNode { checkRayChannel(it) }

        override fun lan() {
            showRayLanDialog(nodeId)
        }

        override fun why() = withNode { rayTestRun(it) }

        override fun admin() = withNode { openRayAdmin(it) }

        override fun appUpdate() {
            main.displayFragment(AboutFragment.checkingUpdate())
        }
    }
}
