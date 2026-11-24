package io.niko.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.view.KeyEvent
import android.view.View
import androidx.core.net.toUri
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceDataStore
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.niko.RELEASE_REPO
import io.niko.BuildConfig
import io.niko.GroupType
import io.niko.Key
import io.niko.R
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.Rcdev1
import io.niko.raycontrol.RayImport
import io.niko.SagerNet
import io.niko.aidl.ISagerNetService
import io.niko.aidl.SpeedDisplayData
import io.niko.aidl.TrafficData
import io.niko.bg.BaseService
import io.niko.bg.SagerConnection
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyGroup
import io.niko.database.SagerDatabase
import io.niko.database.SubscriptionBean
import io.niko.database.preference.OnPreferenceDataStoreChangeListener
import io.niko.databinding.LayoutMainBinding
import io.niko.fmt.AbstractBean
import io.niko.fmt.KryoConverters
import io.niko.fmt.PluginEntry
import io.niko.group.parseSubscriptionDeepLink
import io.niko.group.GroupInterfaceAdapter
import io.niko.group.GroupUpdater
import io.niko.ktx.alert
import io.niko.ktx.app
import io.niko.ktx.isPreview
import io.niko.ktx.launchCustomTab
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.parseProxies
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ui.home.ConfigGroup
import io.niko.ui.home.ConfigsActions
import io.niko.ui.home.ConfigsLoader
import io.niko.ui.home.ConfigsTest
import io.niko.ui.home.HomeActions
import io.niko.ui.home.HomeCheck
import io.niko.ui.home.HomeFragment
import io.niko.ui.home.HomeLoader
import io.niko.ui.home.HomeState
import io.niko.ui.home.HomeView
import io.niko.ktx.Logs
import io.niko.utils.PrivateDns
import io.niko.utils.Util
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// onSaveInstanceState: a link's question was open when the activity went
private const val LINK_QUESTION = "link_question"

/** How long the connection check's answer stays on the main screen's status card. */
private const val CHECK_SHOWN_MS = 8_000L

class MainActivity : ThemedActivity(),
    SagerConnection.Callback,
    OnPreferenceDataStoreChangeListener {

    lateinit var binding: LayoutMainBinding

    /**
     * The question a link asks (import a raycontrol node, a profile, a subscription, a scanned config) while it is open.
     * The system may relaunch the activity under it (the theme, an overlay changed while the device settles after its
     * boot), which closes it with the window: the relaunched activity asks again (onSaveInstanceState, onCreate).
     */
    private var linkQuestion: AlertDialog? = null

    /** A link being read before its question (a profile's, a subscription's): a relaunch now is to ask too. */
    private var linkReading = false

    /**
     * What the main screen (ui/home) draws: the service's state and speeds from its callbacks here, the rest read by
     * [refreshHome]. Written on the main thread only.
     */
    val homeState = mutableStateOf(HomeState())
    val homeView = mutableStateOf(HomeView.of(DataStore.homeView))

    /** What the main screen asks for: the connect button's toggle, the old screens behind its rows and tiles. */
    val homeActions = object : HomeActions {
        override fun toggle() = if (DataStore.serviceState.canStop) SagerNet.stopService() else connect.launch(null)

        override fun view(view: HomeView) {
            homeView.value = view
            DataStore.homeView = view.key
        }

        // as the setting does: a running service stops, the new mode takes effect on the next connect
        override fun mode(vpn: Boolean) {
            if (DataStore.serviceState.started) SagerNet.stopService()
            DataStore.serviceMode = if (vpn) Key.MODE_VPN else Key.MODE_PROXY
        }

        override fun profiles() {
            displayFragmentWithId(R.id.nav_configuration)
        }

        override fun configs(open: Boolean) {
            configsOpen = open
            if (open) refreshConfigs() else {
                configsState.value = null
                subsState.value = null
                ConfigsTest.forget()
            }
        }

        override fun subscriptions() {
            displayFragmentWithId(R.id.nav_group)
        }

        override fun route() {
            displayFragmentWithId(R.id.nav_route)
        }

        override fun apps() = startActivity(Intent(this@MainActivity, io.niko.ui.apps.AppsActivity::class.java))

        override fun settings() {
            displayFragmentWithId(R.id.nav_settings)
        }

        // one node: its card at once; more or none: the groups, where each node's group opens its card
        override fun nodes() {
            runOnDefaultDispatcher {
                val nodes = RayNodes.list()
                onMainDispatcher {
                    if (nodes.size == 1) displayFragment(io.niko.ui.node.NodeFragment.of(nodes[0].spa.nodeId, R.id.nav_home))
                    else displayFragmentWithId(R.id.nav_group)
                }
            }
        }

        override fun log() {
            displayFragmentWithId(R.id.nav_logcat)
        }

        override fun help() {
            displayFragmentWithId(R.id.nav_faq)
        }

        // the connection check through the running core (as the old status bar's tap), its answer on the card for a
        // while; one at a time
        override fun check() {
            if (!DataStore.serviceState.connected || homeState.value.check == HomeCheck.Testing) return
            homeState.value = homeState.value.copy(check = HomeCheck.Testing)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.Default) {
                    try {
                        HomeCheck.Done(urlTest())
                    } catch (e: Exception) {
                        Logs.w(e.toString())
                        HomeCheck.Failed(e.readableMessage)
                    }
                }
                if (homeState.value.check != HomeCheck.Testing) return@launch // disconnected meanwhile
                homeState.value = homeState.value.copy(check = result)
                delay(CHECK_SHOWN_MS)
                if (homeState.value.check === result) homeState.value = homeState.value.copy(check = null)
            }
        }

        override fun exitAddress() {
            if (DataStore.serviceState.connected) showExitAddress()
        }
    }

    /** The configs' sheet (ui/home/ConfigsSheet): its groups while open (null: closed or not read yet). Main thread. */
    val configsState = mutableStateOf<List<ConfigGroup>?>(null)

    @Volatile
    private var configsOpen = false
    // one read at a time: the asks while one runs make one more after it (a burst of changes, one read)
    private val configsReading = java.util.concurrent.atomic.AtomicBoolean()
    @Volatile
    private var configsDirty = false

    /** Reads the sheet's groups again while it is open (any thread); asks while a read runs are merged into one more. */
    fun refreshConfigs() {
        if (!configsOpen) return
        configsDirty = true
        if (!configsReading.compareAndSet(false, true)) return
        runOnDefaultDispatcher {
            try {
                while (configsDirty && configsOpen) {
                    configsDirty = false
                    val loaded = try {
                        ConfigsLoader.load() to io.niko.ui.home.SubsLoader.load()
                    } catch (e: Exception) {
                        Logs.w(e)
                        continue
                    }
                    onMainDispatcher {
                        if (configsOpen) {
                            configsState.value = loaded.first
                            subsState.value = loaded.second
                        }
                    }
                }
            } finally {
                configsReading.set(false)
                // asked between the loop's last check and the flag's reset
                if (configsDirty) refreshConfigs()
            }
        }
    }

    /**
     * A sheet's test stored (ConfigsTest.stored): its results go into the rows shown, then stop standing over them, and
     * the main screen and the sheet read the database again. Main thread.
     */
    fun testStored() {
        if (ConfigsTest.testing.value.isEmpty()) {
            configsState.value = configsState.value?.let { ConfigsLoader.withResults(it, ConfigsTest.results.value) }
            ConfigsTest.forget()
        }
        refreshHome()
    }

    /** What the configs' sheet asks for: the profile to use, a group's test, the editor; adding in the old list. */
    val configsActions = object : ConfigsActions {
        // as the old list's row does: the chosen profile at once, a running service reloaded with it
        override fun select(id: Long) {
            runOnDefaultDispatcher {
                val last = DataStore.selectedProxy
                if (last == id) return@runOnDefaultDispatcher
                DataStore.selectedProxy = id
                ProfileManager.postUpdate(last)
                ProfileManager.postUpdate(id)
                if (DataStore.serviceState.canStop) SagerNet.reloadService()
            }
        }

        // held by the app (ConfigsTest): a rotation keeps it, the sheet shows its results as they come
        override fun test(groupId: Long) {
            ConfigsTest.start(groupId, connection.service)
        }

        override fun stopTest() = ConfigsTest.stop()

        override fun edit(id: Long) {
            runOnDefaultDispatcher {
                val profile = ProfileManager.getProfile(id) ?: return@runOnDefaultDispatcher
                onMainDispatcher { startActivity(profile.settingIntent(this@MainActivity)) }
            }
        }

        override fun add() {
            displayFragmentWithId(R.id.nav_configuration)
        }
    }

    /** The groups the sheet's Subscriptions tab lists (P-Subs), read with its configs while it is open. */
    val subsState = mutableStateOf<List<io.niko.ui.home.SubRow>?>(null)

    // a group added, changed, fetched or gone: the sheet's tabs read again (while it is open)
    private val groupsListener = object : GroupManager.Listener {
        override suspend fun groupAdd(group: ProxyGroup) = onMainDispatcher { refreshHome() }
        override suspend fun groupUpdated(group: ProxyGroup) = onMainDispatcher { refreshHome() }
        override suspend fun groupRemoved(groupId: Long) = onMainDispatcher { refreshHome() }
        override suspend fun groupUpdated(groupId: Long) = onMainDispatcher { refreshConfigs() }
    }

    // refreshHome's reads, counted (it is called from any thread: the store's listener), and the last one shown
    private val homeGeneration = java.util.concurrent.atomic.AtomicInteger()
    private var homeShown = 0 // the main thread's

    /** Reads what the main screen shows from the database, off the main thread; the service's fields stay as they are. */
    fun refreshHome() {
        // a newer read shown first wins: an older one that finishes after it would bring back what it replaced. A newer
        // read that failed does not hold an older one back
        val generation = homeGeneration.incrementAndGet()
        runOnDefaultDispatcher {
            val loaded = try {
                HomeLoader.load(app, HomeState())
            } catch (e: Exception) {
                Logs.w(e)
                return@runOnDefaultDispatcher
            }
            onMainDispatcher {
                if (generation <= homeShown) return@onMainDispatcher
                homeShown = generation
                val now = homeState.value
                homeState.value = loaded.copy(
                    service = now.service, error = now.error, connectedSince = now.connectedSince,
                    upRate = now.upRate, downRate = now.downRate,
                )
            }
        }
        // what changed the main screen (a profile chosen, a test, an edit) changes the sheet's rows too
        refreshConfigs()
    }

    private fun MaterialAlertDialogBuilder.showLinkQuestion(): AlertDialog {
        // one question at a time: a newer link or scan replaces one not answered yet, which counts as cancelled (a
        // scanned batch is given up, not imported with the next one)
        linkQuestion?.takeIf { it.isShowing }?.cancel()
        return show().also { dialog ->
            linkQuestion = dialog
            dialog.setOnDismissListener { if (linkQuestion === dialog) linkQuestion = null }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutMainBinding.inflate(layoutInflater)
        binding.fab.initProgress(binding.fabProgress)

        // the bars follow the screen shown, the one the fragment manager restores after a recreation too
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f.id == R.id.fragment_holder) applyBars(f)
            }
        }, false)
        if (savedInstanceState == null) {
            displayFragmentWithId(R.id.nav_home)
        }
        // the main screen first (D-NAV): back from any other comes to it, back on it leaves the app
        onBackPressedDispatcher.addCallback {
            when (val shown = supportFragmentManager.findFragmentById(R.id.fragment_holder)) {
                is HomeFragment -> moveTaskToBack(true)
                // a node's card goes back where it was opened from
                is io.niko.ui.node.NodeFragment -> shown.back()
                // a page of the settings goes back to their list, a choice open closes
                is SettingsFragment -> shown.back()
                // the route check and the rule sets go back to the rules
                is io.niko.ui.route.RoutingFragment -> shown.back()
                // the screens the settings open go back to them, the sing-box panel to the log it is opened from
                is io.niko.ui.about.AboutFragment, is io.niko.ui.backup.BackupFragment -> displayFragmentWithId(R.id.nav_settings)
                is WebviewFragment -> displayFragmentWithId(R.id.nav_logcat)
                else -> displayFragmentWithId(R.id.nav_home)
            }
        }

        binding.fab.setOnClickListener {
            if (DataStore.serviceState.canStop) SagerNet.stopService() else connect.launch(
                null
            )
        }
        binding.stats.setOnClickListener { if (DataStore.serviceState.connected) binding.stats.testConnection() }
        // a long press: the exit address, asked of a service the user picks (it learns the node's address)
        binding.stats.setOnLongClickListener {
            DataStore.serviceState.connected.also { if (it) showExitAddress() }
        }

        setContentView(binding.root)
        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        DataStore.configurationStore.registerChangeListener(this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)
        GroupManager.addListener(groupsListener)

        // on a fresh start, and after a relaunch that closed the link's question unanswered; a relaunch after it was
        // answered or cancelled (theme, rotation) does not ask again
        val asking = savedInstanceState?.getBoolean(LINK_QUESTION) == true
        if ((savedInstanceState == null || asking) &&
            (intent?.action == Intent.ACTION_VIEW || intent?.action == ImportReview.ACTION_REVIEW)
        ) {
            onNewIntent(intent)
        }

        // sdk 33 notification
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            val checkPermission =
                ContextCompat.checkSelfPermission(this@MainActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                //动态申请
                ActivityCompat.requestPermissions(
                    this@MainActivity, arrayOf(POST_NOTIFICATIONS), 0
                )
            }
        }

        if (isPreview) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.PRE_VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // a relaunch asks about this link, not about the one the activity started with (LINK_QUESTION)
        if (intent.action == Intent.ACTION_VIEW || intent.action == ImportReview.ACTION_REVIEW) setIntent(intent)

        // profiles of a scanned QR code that rewrite the whole config: asked here (ImportReview)
        if (intent.action == ImportReview.ACTION_REVIEW) {
            // this scan's batch, with ImportReview until the answer: a relaunch under the question asks about it again;
            // an answered batch is gone, and its relaunch asks nothing
            val batch = intent.getLongExtra(ImportReview.EXTRA_BATCH, -1)
            val proxies = ImportReview.peek(batch)
            val question = ImportReview.warning(this, proxies) ?: run {
                ImportReview.drop(batch)
                return
            }
            MaterialAlertDialogBuilder(this).setTitle(R.string.profile_import)
                .setMessage(question)
                .setPositiveButton(R.string.yes) { _, _ ->
                    // imported once: only while the batch still waits
                    if (ImportReview.drop(batch)) runOnDefaultDispatcher { proxies.forEach { finishImportProfile(it) } }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> ImportReview.drop(batch) }
                .setOnCancelListener { ImportReview.drop(batch) }
                .showLinkQuestion()
            return
        }

        val uri = intent.data ?: return

        // a raycontrol invite or device package: any page or app can open such a link, so ask first
        // rcinv2: in upper case for the QR code, a scheme Android keeps as it comes
        val scheme = uri.scheme?.lowercase()
        if (scheme == "rcinv1" || scheme == "rcinv2" || scheme == "rcinv3" || scheme == "rcdev1") {
            val text = uri.toString()
            // a link (or a scanned QR code) always asks; replacing a stored node says so (RayImport.replaceQuestion)
            val question = try {
                val host = if (scheme == "rcdev1") Rcdev1.parse(text).host else RayImport.parseInvite(text).host
                RayImport.replaceQuestion(app, text) ?: getString(R.string.raycontrol_link_confirm, host)
            } catch (e: Exception) {
                alert(e.readableMessage).show()
                return
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.raycontrol_nodes)
                .setMessage(question)
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        RayImport.import(app, text, { host, left, wrong ->
                            onMainDispatcher { if (isFinishing) null else Dialogs.askPin(this@MainActivity, host, left, wrong) }
                        }) { message -> onMainDispatcher { snackbar(message).show() } }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showLinkQuestion()
            return
        }

        linkReading = true
        runOnDefaultDispatcher {
            try {
                if (uri.scheme == "sn" && uri.host == "subscription" || uri.scheme == "clash" ||
                    parseSubscriptionDeepLink(uri.toString()) != null
                ) {
                    importSubscription(uri)
                } else {
                    importProfile(uri)
                }
            } finally {
                onMainDispatcher { linkReading = false }
            }
        }
    }

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error("not started")
        }
        return connection.service!!.urlTest()
    }

    /** Which rule of the running core a connection would match (the route tester): JSON in, JSON out. */
    fun routeTest(query: String): String {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error(getString(R.string.route_test_not_running))
        }
        return connection.service!!.routeTest(query)
    }

    /** The running profile's exit as the lookup service [provider] sees it: JSON (libcore ExitAddress). */
    fun exitAddress(provider: String): String {
        if (!DataStore.serviceState.connected || connection.service == null) {
            error(getString(R.string.route_test_not_running))
        }
        return connection.service!!.exitAddress(provider)
    }

    suspend fun importSubscription(uri: Uri) {
        val group: ProxyGroup

        val cleartext = parseSubscriptionDeepLink(uri.toString())
        if (cleartext != null) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            val subscription = SubscriptionBean()
            group.subscription = subscription

            // cleartext format, also of other clients' "add subscription" links
            subscription.link = cleartext.url
            group.name = cleartext.name
        } else {
            val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return
            try {
                group = KryoConverters.strict {
                    KryoConverters.deserialize(
                        ProxyGroup().apply { export = true }, Util.zlibDecompress(Util.b64Decode(data))
                    )
                }.apply {
                    export = false
                }
            } catch (e: Exception) {
                onMainDispatcher {
                    alert(e.readableMessage).show()
                }
                return
            }
        }

        var name = group.name.takeIf { !it.isNullOrBlank() } ?: group.subscription?.link
        ?: group.subscription?.token
        if (name.isNullOrBlank()) return
        // the name comes from the link: show where the subscription is actually fetched from
        group.subscription?.link?.toUri()?.host?.takeIf { it.isNotBlank() }?.let { host ->
            if (!name.contains(host)) name = "$name ($host)"
        }

        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: ("Subscription #" + System.currentTimeMillis())

        onMainDispatcher {
            // an activity relaunched meanwhile asks in its successor (linkReading), not on its own dead window
            if (!isFinishing && !isDestroyed) {
                displayFragmentWithId(R.id.nav_group)

                MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.subscription_import)
                    .setMessage(getString(R.string.subscription_import_message, name))
                    .setPositiveButton(R.string.yes) { _, _ ->
                        runOnDefaultDispatcher {
                            finishImportSubscription(group)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showLinkQuestion()
            }
        }

    }

    private suspend fun finishImportSubscription(subscription: ProxyGroup) {
        GroupManager.createGroup(subscription)
        GroupUpdater.startUpdate(subscription, true)
    }

    suspend fun importProfile(uri: Uri) {
        val profile = try {
            parseProxies(uri.toString()).getOrNull(0) ?: error(getString(R.string.no_proxies_found))
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
            }
            return
        }

        onMainDispatcher {
            // an activity relaunched meanwhile asks in its successor (linkReading), not on its own dead window
            if (!isFinishing && !isDestroyed) {
                MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.profile_import)
                    // a web page can open this link: say so, with its keys, when it rewrites the whole config
                    .setMessage(
                        ImportReview.warning(this@MainActivity, listOf(profile))
                            ?: getString(R.string.profile_import_message, profile.displayName())
                    )
                    .setPositiveButton(R.string.yes) { _, _ ->
                        runOnDefaultDispatcher {
                            finishImportProfile(profile)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .showLinkQuestion()
            }
        }

    }

    private suspend fun finishImportProfile(profile: AbstractBean) {
        val targetId = DataStore.selectedGroupForImport()

        ProfileManager.createProfile(targetId, profile)

        onMainDispatcher {
            displayFragmentWithId(R.id.nav_configuration)

            snackbar(resources.getQuantityString(R.plurals.added, 1, 1)).show()
        }
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        val pluginEntity = PluginEntry.find(pluginName)

        // unknown exe or neko plugin
        if (pluginEntity == null) {
            snackbar(getString(R.string.plugin_unknown, pluginName)).show()
            return
        }

        // official exe

        MaterialAlertDialogBuilder(this).setTitle(R.string.missing_plugin)
            .setMessage(
                getString(
                    R.string.profile_requiring_plugin, profileName, pluginEntity.displayName
                )
            )
            .setPositiveButton(R.string.action_download) { _, _ ->
                showDownloadDialog(pluginEntity)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDownloadDialog(pluginEntry: PluginEntry) {
        var index = 0
        var playIndex = -1
        var fdroidIndex = -1

        val items = mutableListOf<String>()
        if (pluginEntry.downloadSource.playStore) {
            items.add(getString(R.string.install_from_play_store))
            playIndex = index++
        }
        if (pluginEntry.downloadSource.fdroid) {
            items.add(getString(R.string.install_from_fdroid))
            fdroidIndex = index++
        }

        items.add(getString(R.string.download))
        val downloadIndex = index

        MaterialAlertDialogBuilder(this).setTitle(pluginEntry.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    playIndex -> launchCustomTab("https://play.google.com/store/apps/details?id=${pluginEntry.packageName}")
                    fdroidIndex -> launchCustomTab("https://f-droid.org/packages/${pluginEntry.packageName}/")
                    downloadIndex -> launchCustomTab(pluginEntry.downloadSource.downloadLink)
                }
            }
            .show()
    }


    private fun showStats() {
        binding.stats.allowShow = true
        binding.fab.show()
        // the bar shows itself on connecting only if allowed then: connected on the main screen, it waits for this
        if (DataStore.serviceState.connected) binding.stats.performShow()
    }

    /**
     * The old bar at the bottom (state, speeds, the connect button): only on the old screens, and only with the
     * setting on (off by default). The main screen has its own button and state; the screens drawn as the canvas has
     * them (a node's card, the settings, the log, routing) have none. Applied to whatever screen is shown, the restored one
     * too: an activity the system recreated would otherwise keep the layout's default, the bar shown on the main
     * screen over its configs.
     */
    private fun applyBars(fragment: Fragment) {
        val old = fragment !is HomeFragment && fragment !is io.niko.ui.node.NodeFragment && fragment !is SettingsFragment &&
            fragment !is io.niko.ui.log.LogFragment && fragment !is io.niko.ui.route.RoutingFragment &&
            fragment !is io.niko.ui.about.AboutFragment && fragment !is io.niko.ui.backup.BackupFragment
        if (old && DataStore.showBottomBar) {
            showStats()
        } else {
            binding.stats.allowShow = false
            binding.stats.performHide()
            binding.fab.hide()
        }
    }

    @SuppressLint("CommitTransaction")
    fun displayFragment(fragment: Fragment) {
        applyBars(fragment)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, fragment)
            .commitAllowingStateLoss()
    }

    fun displayFragmentWithId(@IdRes id: Int): Boolean {
        when (id) {
            R.id.nav_home -> displayFragment(HomeFragment())
            R.id.nav_configuration -> {
                displayFragment(ConfigurationFragment())
            }

            R.id.nav_group -> displayFragment(GroupFragment())
            R.id.nav_route -> displayFragment(io.niko.ui.route.RoutingFragment())
            R.id.nav_settings -> displayFragment(SettingsFragment())
            R.id.nav_traffic -> displayFragment(WebviewFragment())
            R.id.nav_logcat -> displayFragment(io.niko.ui.log.LogFragment())
            R.id.nav_faq -> {
                launchCustomTab("https://github.com/$RELEASE_REPO#readme")
                return false
            }

            R.id.nav_about -> displayFragment(io.niko.ui.about.AboutFragment())

            else -> return false
        }
        return true
    }

    private fun changeState(
        state: BaseService.State,
        msg: String? = null,
        animate: Boolean = false,
    ) {
        DataStore.serviceState = state

        binding.fab.changeState(state, DataStore.serviceState, animate, failed = msg != null)
        binding.stats.changeState(state)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()

        // the main screen: why the last start failed stays until the next one; the time online from the service's
        // process, which wrote it as the core came up (BaseService)
        val home = homeState.value
        val connected = state == BaseService.State.Connected
        homeState.value = home.copy(
            service = state,
            error = when {
                msg != null -> msg
                state == BaseService.State.Connecting || connected -> null
                else -> home.error
            },
            connectedSince = if (connected) {
                DataStore.connectedSince.takeIf { it > 0 } ?: home.connectedSince.takeIf { it > 0 } ?: System.currentTimeMillis()
            } else 0,
            upRate = if (connected) home.upRate else 0,
            downRate = if (connected) home.downRate else 0,
            check = if (connected) home.check else null,
        )
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.coordinator, text, Snackbar.LENGTH_LONG).apply {
            if (binding.fab.isShown) {
                anchorView = binding.fab
            }
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg, true)
        if (state == BaseService.State.Connected) warnStrictPrivateDns()
    }

    private var privateDnsWarned = false

    /**
     * Strict Private DNS sends the apps' DNS over TLS to its host, past the VPN's DNS (R8): say so once a run. The
     * underlying network's setting is the one in force.
     */
    private fun warnStrictPrivateDns() {
        if (privateDnsWarned || DataStore.serviceMode != Key.MODE_VPN) return
        val host = PrivateDns.strictHost() ?: return
        privateDnsWarned = true
        snackbar(getString(R.string.private_dns_strict, host)).show()
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: ISagerNetService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) {
        if (it) snackbar(R.string.vpn_permission_denied).show()
    }

    // may NOT called when app is in background
    // ONLY do UI update here, write DB in bg process
    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        binding.stats.updateSpeed(stats.txRateProxy, stats.rxRateProxy)
        homeState.value = homeState.value.copy(upRate = stats.txRateProxy, downRate = stats.rxRateProxy)
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(data)
        }
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
        refreshHome()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        // what the main screen shows of the settings (any thread: refreshHome posts to the main one)
        when (key) {
            Key.PROFILE_ID, Key.SERVICE_MODE, Key.MIXED_PORT, Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL -> refreshHome()
        }
        when (key) {
            Key.SERVICE_MODE -> onBinderDied()
            Key.PROXY_APPS, Key.BYPASS_MODE, Key.INDIVIDUAL -> {
                if (DataStore.serviceState.canStop) {
                    snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                        SagerNet.reloadService()
                    }.show()
                }
            }
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(LINK_QUESTION, linkReading || linkQuestion?.isShowing == true)
    }

    override fun onDestroy() {
        // the question goes with the window, not leaked; onSaveInstanceState has said whether a relaunch asks again.
        // An activity that goes for good (finished, not relaunched) asks nothing again: its question counts as
        // cancelled, and a scanned batch is given up rather than left for the next scan's question
        linkQuestion?.run {
            setOnDismissListener(null)
            if (this@MainActivity.isFinishing) cancel() else dismiss()
        }
        linkQuestion = null
        super.onDestroy()
        GroupManager.userInterface = null
        GroupManager.removeListener(groupsListener)
        DataStore.configurationStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (super.onKeyDown(keyCode, event)) return true
        val fragment =
            supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ToolbarFragment
        return fragment != null && fragment.onKeyDown(keyCode, event)
    }

}
