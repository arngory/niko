package io.niko.ui.route

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.RELEASE_REPO
import io.niko.RoutePresets
import io.niko.RuleSetFiles
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.database.RuleEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.LOCAL_PROXY_USER
import io.niko.fmt.localProxyRequired
import io.niko.ktx.Logs
import io.niko.ktx.alert
import io.niko.ktx.app
import io.niko.ktx.danger
import io.niko.ktx.filterIsInstance
import io.niko.ktx.getStr
import io.niko.ktx.launchCustomTab
import io.niko.ktx.needReload
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.snackbar
import io.niko.ktx.startFilesForResult
import io.niko.ktx.tryToShow
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoTheme
import io.niko.utils.PackageCache
import io.niko.utils.Util
import io.niko.widget.UndoSnackbarManager
import libcore.Libcore
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * Маршрутизация on Compose: the rules (P-Rules), the route check (P-Check) and the rule sets (P-Lists) as pages of
 * one screen of the main activity, which holds the service the check asks; a rule opens in [RuleActivity]. The rules
 * are read again on every change ProfileManager tells of, and a change the running core has to take asks for a reload,
 * as the old list (RouteFragment) did; a swipe's delete waits for its undo (UndoSnackbarManager), as before.
 */
class RoutingFragment : Fragment(), ProfileManager.RuleListener {

    enum class Page { RULES, CHECK, LISTS }

    companion object {
        /** Read by the core at start (libcore/niko.go) and added to the system roots. */
        const val CA_FILE = "ca.pem"
        private const val PAGE = "page"
        private val GEO = listOf("geoip.db", "geosite.db")
    }

    private var page by mutableStateOf(Page.RULES)
    private var rules by mutableStateOf(RulesState(null))
    private var check by mutableStateOf(CheckState())
    private var lists by mutableStateOf(ListsState(emptyList()))

    private val main get() = requireActivity() as MainActivity

    // the rules as shown, main thread: a deleted one waits for its undo, a dragged one's new order for the drop
    private val entities = ArrayList<RuleEntity>()
    private var profileNames = emptyMap<Long, String>()
    private var listsDate: String? = null
    private val pendingRules = HashSet<Long>()
    private val moved = LinkedHashMap<Long, RuleEntity>()

    // the rule sets' files, main thread
    private val pendingFiles = HashSet<String>()
    private val updating = HashSet<String>()
    private var appsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(PAGE)?.let { saved -> Page.entries.firstOrNull { it.name == saved } }?.let { page = it }
        ProfileManager.addListener(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NikoTheme {
                    when (page) {
                        Page.RULES -> RulesScreen(rules, rulesActions)
                        Page.CHECK -> CheckScreen(check, checkActions)
                        Page.LISTS -> ListsScreen(lists, listsActions)
                    }
                }
            }
        }

    override fun onResume() {
        super.onResume()
        reloadRules()
        if (page == Page.LISTS) reloadAssets()
        if (page == Page.CHECK) loadApps()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PAGE, page.name)
    }

    override fun onDestroy() {
        ProfileManager.removeListener(this)
        super.onDestroy()
    }

    /** Back (MainActivity's): the check and the rule sets go back to the rules, the rules to the main screen. */
    fun back() {
        if (page == Page.RULES) {
            main.displayFragmentWithId(R.id.nav_home)
        } else {
            page = Page.RULES
            reloadRules()
        }
    }

    // ---- the rules

    private fun reloadRules() = runOnDefaultDispatcher {
        val all = ProfileManager.getRules()
        val names = all.filter { it.outbound > 0 }.mapNotNull { rule ->
            ProfileManager.getProfile(rule.outbound)?.displayName()?.let { rule.outbound to it }
        }.toMap()
        val newest = GEO.map { File(app.routeAssets, it) }.filter { it.isFile }.maxOfOrNull { it.lastModified() }
        val date = newest?.let { DateFormat.getDateFormat(app).format(Date(it)) }
        onMainDispatcher {
            // a drag in hand keeps its own order until it is let go
            if (moved.isNotEmpty()) return@onMainDispatcher
            entities.clear()
            entities.addAll(all.filter { it.id !in pendingRules })
            profileNames = names
            listsDate = date
            publishRules()
        }
    }

    private fun publishRules() {
        rules = RulesState(
            entities.map { RuleRow.of(it, profileNames[it.outbound]) },
            if (entities.any(RoutePresets::isRussia)) RulePreset.RUSSIA else RulePreset.NONE,
            listsDate,
        )
    }

    private suspend fun changed() = onMainDispatcher {
        if (!isAdded) return@onMainDispatcher
        reloadRules()
        needReload()
    }

    override suspend fun onAdd(rule: RuleEntity) = changed()
    override suspend fun onUpdated(rule: RuleEntity) = changed()
    override suspend fun onRemoved(ruleId: Long) = changed()
    override suspend fun onCleared() = changed()

    private val ruleUndo by lazy {
        UndoSnackbarManager(main, object : UndoSnackbarManager.Interface<RuleEntity> {
            override fun undo(actions: List<Pair<Int, RuleEntity>>) {
                actions.forEach { pendingRules -= it.second.id }
                reloadRules()
            }

            override fun commit(actions: List<Pair<Int, RuleEntity>>) {
                val gone = actions.map { it.second }
                runOnDefaultDispatcher {
                    ProfileManager.deleteRules(gone)
                    onMainDispatcher { gone.forEach { pendingRules -= it.id } }
                }
            }
        })
    }

    /** The guide in the language the app shows: Russian, or English for any other. */
    private fun openGuide() {
        val context = requireContext()
        val ru = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language == "ru"
        val guide = if (ru) "routing.ru.md" else "routing.md"
        context.launchCustomTab("https://github.com/$RELEASE_REPO/blob/main/docs/guide/$guide")
    }

    private val rulesActions = object : RulesActions {
        override fun back() = this@RoutingFragment.back()

        override fun guide() = openGuide()

        override fun add() {
            startActivity(Intent(requireContext(), RuleActivity::class.java))
        }

        override fun edit(id: Long) {
            startActivity(Intent(requireContext(), RuleActivity::class.java).putExtra(RuleActivity.EXTRA_RULE_ID, id))
        }

        override fun enable(id: Long, on: Boolean) {
            val rule = entities.firstOrNull { it.id == id } ?: return
            rule.enabled = on
            publishRules()
            runOnDefaultDispatcher {
                SagerDatabase.rulesDao.updateRule(rule)
                onMainDispatcher { if (isAdded) needReload() }
            }
        }

        override fun move(from: Int, to: Int) {
            for (rule in moveRule(entities, from, to)) moved[rule.id] = rule
            publishRules()
        }

        override fun moved() {
            if (moved.isEmpty()) return
            val changed = moved.values.toList()
            moved.clear()
            runOnDefaultDispatcher {
                SagerDatabase.rulesDao.updateRules(changed)
                onMainDispatcher { if (isAdded) needReload() }
            }
        }

        override fun delete(id: Long) {
            val index = entities.indexOfFirst { it.id == id }
            if (index < 0) return
            val rule = entities.removeAt(index)
            pendingRules += id
            publishRules()
            ruleUndo.remove(index to rule)
        }

        override fun preset(preset: RulePreset) {
            when (preset) {
                RulePreset.RUSSIA -> {
                    val hours = DataStore.ruleSetUpdateHours.coerceIn(1, 24 * 30)
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.route_preset_ru_title)
                        .setMessage(getString(R.string.route_preset_ru_message, hours))
                        .setPositiveButton(R.string.route_preset_ru_add) { _, _ -> addRussiaPreset() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                RulePreset.NONE -> if (rules.preset != RulePreset.NONE) {
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rt_preset_remove_title)
                        .setMessage(R.string.rt_preset_remove_message)
                        .setPositiveButton(R.string.rt_preset_remove) { _, _ ->
                            runOnDefaultDispatcher {
                                SagerDatabase.rulesDao.allRules().filter(RoutePresets::isRussia).forEach { ProfileManager.deleteRule(it.id) }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show().danger()
                }
            }
        }

        override fun lists() {
            page = Page.LISTS
            reloadAssets()
        }

        override fun check() {
            page = Page.CHECK
            loadApps()
        }

        override fun reset() {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rt_reset_title)
                .setMessage(R.string.rt_reset_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        SagerDatabase.rulesDao.reset()
                        DataStore.rulesFirstCreate = false
                        reloadRules()
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show().danger()
        }
    }

    /** The Russian preset's rules (they replace its earlier ones), then their lists, through the proxy if it runs. */
    private fun addRussiaPreset() {
        val blocked = getString(R.string.route_preset_ru_blocked)
        val direct = getString(R.string.route_preset_ru_direct)
        runOnDefaultDispatcher {
            val items = RoutePresets.addRussia(blocked, direct)
            val got = RuleSetFiles.update(items, 0)
            onMainDispatcher {
                if (!isAdded) return@onMainDispatcher
                if (got < items.size) {
                    snackbar(getString(R.string.route_preset_ru_partial, got, items.size)).show()
                } else if (DataStore.serviceState.started) {
                    snackbar(getString(R.string.route_preset_ru_ready)).setAction(R.string.apply) {
                        SagerNet.reloadService()
                    }.show()
                } else {
                    snackbar(getString(R.string.route_preset_ru_ready)).show()
                }
            }
        }
    }

    // ---- the route check

    /** The installed apps by their label, for a rule by app: "Label (package)"; once, off the main thread. */
    private fun loadApps() {
        if (appsLoaded) return
        appsLoaded = true
        runOnDefaultDispatcher {
            val apps = runCatching {
                PackageCache.awaitLoadSync()
                PackageCache.installedApps.keys.map { "${PackageCache.loadLabel(it)} ($it)" }.sorted()
            }.onFailure { Logs.w(it) }.getOrDefault(emptyList())
            onMainDispatcher { check = check.copy(apps = apps) }
        }
    }

    private val checkActions = object : CheckActions {
        override fun back() = this@RoutingFragment.back()

        override fun form(form: CheckForm) {
            check = check.copy(form = form)
        }

        override fun more(open: Boolean) {
            check = check.copy(more = open)
        }

        override fun run() {
            if (check.outcome == CheckOutcome.Running) return
            val form = check.form
            val parsed = parseRouteTestTarget(form.target)
            if (parsed == null) {
                check = check.copy(outcome = CheckOutcome.BadTarget)
                return
            }
            check = check.copy(outcome = CheckOutcome.Running)
            val activity = main
            runOnDefaultDispatcher {
                val outcome = try {
                    val pkg = packageOfChoice(form.app)
                    val uid = pkg?.let { runCatching { PackageCache[it] }.getOrNull() }
                    // the inbound is the service's to say (BaseService.routeTest): the VPN's or the local proxy's
                    val query = checkQuery(parsed.first, parsed.second, form, pkg, uid)
                    CheckOutcome.Done(CheckResult.of(JSONObject(activity.routeTest(query.toString()))))
                } catch (e: Exception) {
                    Logs.w(e)
                    CheckOutcome.Failed(e.readableMessage)
                }
                onMainDispatcher { check = check.copy(outcome = outcome) }
            }
        }
    }

    // ---- the rule sets

    private fun reloadAssets() = runOnDefaultDispatcher {
        val dir = app.routeAssets
        val own = dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".db") && it.name !in GEO || it.name == CA_FILE) }
            ?.sortedBy { it.name }
            .orEmpty()
        val files = GEO.map { File(dir, it) } + own
        val rows = files.filter { it.name !in pendingFiles }.map { file ->
            val kind = when (file.name) {
                in GEO -> AssetKind.GEO
                CA_FILE -> AssetKind.CA
                else -> AssetKind.DB
            }
            AssetRow(file.name, localVersion(file), kind)
        }
        onMainDispatcher {
            lists = ListsState(rows.map { AssetRow(it.name, it.version, it.kind, it.name in updating) })
        }
    }

    private fun publishUpdating() {
        lists = ListsState(lists.rows.map { AssetRow(it.name, it.version, it.kind, it.name in updating) })
    }

    private fun versionFile(file: File) = File(file.parentFile, "${file.nameWithoutExtension}.version.txt")

    /** A file's version as the old screen said it: the release's tag (or Custom), else Unknown and its date. */
    private fun localVersion(file: File): String {
        if (!file.isFile) return "<unknown>"
        val versionFile = versionFile(file)
        return if (versionFile.isFile) {
            try {
                versionFile.readText().trim()
            } catch (e: Throwable) {
                Logs.w(e)
                "<unknown>"
            }
        } else {
            "Unknown-" + DateFormat.getDateFormat(app).format(Date(file.lastModified()))
        }
    }

    private val fileUndo by lazy {
        UndoSnackbarManager(main, object : UndoSnackbarManager.Interface<File> {
            override fun undo(actions: List<Pair<Int, File>>) {
                actions.forEach { pendingFiles -= it.second.name }
                reloadAssets()
            }

            override fun commit(actions: List<Pair<Int, File>>) {
                val gone = actions.map { it.second }
                runOnDefaultDispatcher {
                    gone.forEach { it.deleteRecursively() }
                    onMainDispatcher {
                        gone.forEach { pendingFiles -= it.name }
                        reloadAssets()
                    }
                }
            }
        })
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file == null) return@registerForActivityResult
        val context = requireContext()
        // the name comes from a third-party DocumentsProvider: only its last segment is used
        val fileName = File(
            context.contentResolver.query(file, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }?.takeIf { it.isNotBlank() } ?: file.pathSegments.last()
                .substringAfterLast('/')
                .substringAfter(':')
        ).name

        val dir = app.routeAssets
        when {
            fileName.endsWith(".pem") -> {
                // a root CA is trusted by every TLS connection without a pin: ask first
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.route_import_ca)
                    .setMessage(getString(R.string.route_import_ca_message, fileName))
                    .setPositiveButton(R.string.yes) { _, _ -> importAsset(file, dir, CA_FILE, false) }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }

            fileName.endsWith(".db") && fileName != ".db" -> importAsset(file, dir, fileName, true)
            else -> alert(getString(R.string.route_not_asset, fileName)).show()
        }
    }

    private fun importAsset(uri: android.net.Uri, dir: File, fileName: String, versioned: Boolean) {
        val resolver = requireContext().contentResolver
        runOnDefaultDispatcher {
            try {
                dir.mkdirs()
                val outFile = File(dir, fileName)
                require(outFile.canonicalFile.parentFile == dir.canonicalFile) { "bad file name: $fileName" }
                val tmp = File(dir, "$fileName.tmp")
                resolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                } ?: error("cannot open $fileName")
                if (!tmp.renameTo(outFile)) error("cannot write $fileName")
                if (versioned) {
                    File(dir, outFile.nameWithoutExtension + ".version.txt").writeText("Custom")
                }
                reloadAssets()
                if (!versioned) onMainDispatcher { if (isAdded) snackbar(R.string.route_import_ca_done).show() }
            } catch (e: Exception) {
                onMainDispatcher { if (isAdded) alert(e.readableMessage).tryToShow() }
            }
        }
    }

    private val listsActions = object : ListsActions {
        override fun back() = this@RoutingFragment.back()

        override fun import() = startFilesForResult(importFile, "*/*")

        override fun updateAll() = GEO.forEach(::update)

        override fun update(name: String) {
            if (name !in GEO || !updating.add(name)) return
            publishUpdating()
            val file = File(app.routeAssets, name)
            runOnDefaultDispatcher {
                runCatching {
                    updateAsset(file, versionFile(file), localVersion(file))
                }.onFailure {
                    onMainDispatcher { if (isAdded) alert(it.readableMessage).tryToShow() }
                }
                onMainDispatcher {
                    updating -= name
                    reloadAssets()
                }
            }
        }

        override fun delete(name: String) {
            if (name in GEO) return
            val index = lists.rows.indexOfFirst { it.name == name }
            if (index < 0) return
            pendingFiles += name
            lists = ListsState(lists.rows.filter { it.name != name })
            fileUndo.remove(index to File(app.routeAssets, name))
        }
    }

    private class RuleAssetsProvider(val repoByFileName: Map<String, String>) {
        constructor(geoipRepo: String, geositeRepo: String = geoipRepo) : this(
            mapOf("geoip.db" to geoipRepo, "geosite.db" to geositeRepo)
        )
    }

    // the setting's sources (Key.RULES_PROVIDER), in its order
    private val rulesProviders = listOf(
        RuleAssetsProvider("SagerNet/sing-geoip", "SagerNet/sing-geosite"),
        RuleAssetsProvider("soffchen/sing-geoip", "soffchen/sing-geosite"),
        RuleAssetsProvider("Chocolate4U/Iran-sing-box-rules"),
        RuleAssetsProvider("L11R/antizapret-sing-box-geo"),
    )

    /** [file] from the latest release of the chosen source, unless its tag is the one here. */
    private suspend fun updateAsset(file: File, versionFile: File, localVersion: String) {
        val fileName = file.name
        val repo = rulesProviders[DataStore.rulesProvider].repoByFileName[fileName]

        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort, LOCAL_PROXY_USER, DataStore.localProxyPassword)
            // proxy mode with the service running: never around it (the device's own address)
            if (localProxyRequired()) requireSocks5()
            setTimeout(120_000)
            setIdleTimeout(30_000)
        }

        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()

            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name")

            if (tagName == localVersion) {
                onMainDispatcher { if (isAdded) snackbar(R.string.route_asset_no_update).show() }
                return
            }

            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val assetToDownload = releaseAssets.find { it.getStr("name") == fileName }
                ?: error("File $fileName not found in release ${release["url"]}")
            val browserDownloadUrl = assetToDownload.getStr("browser_download_url")

            response = client.newRequest().apply {
                setURL(browserDownloadUrl)
            }.execute()

            val cacheFile = File(file.parentFile, file.name + ".tmp")
            cacheFile.parentFile?.mkdirs()

            response.writeTo(cacheFile.canonicalPath)

            if (fileName.endsWith(".xz")) {
                Libcore.unxz(cacheFile.absolutePath, file.absolutePath)
                cacheFile.delete()
            } else {
                cacheFile.renameTo(file)
            }

            versionFile.writeText(tagName)

            onMainDispatcher { if (isAdded) snackbar(R.string.route_asset_updated).show() }
        } finally {
            client.close()
        }
    }
}
