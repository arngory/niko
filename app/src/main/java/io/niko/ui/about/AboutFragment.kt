package io.niko.ui.about

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import io.niko.BuildConfig
import io.niko.DONATE_URL
import io.niko.R
import io.niko.RELEASE_REPO
import io.niko.SagerNet
import io.niko.bg.proto.TestInstance
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.LOCAL_PROXY_USER
import io.niko.fmt.localProxyRequired
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.launchCustomTab
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.runOnIoDispatcher
import io.niko.ktx.runOnMainDispatcher
import io.niko.plugin.PluginManager.loadString
import io.niko.plugin.PluginTrust
import io.niko.plugin.Plugins
import io.niko.raycontrol.RayAppMirror
import io.niko.raycontrol.RayAppRelease
import io.niko.raycontrol.RayClient
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RaySubscription
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoDialog
import io.niko.ui.compose.NikoTheme
import io.niko.utils.BackgroundLimits
import io.niko.utils.PackageCache
import io.niko.utils.UpdateNotice
import io.niko.utils.Updates
import io.niko.utils.Util
import io.niko.utils.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import libcore.HTTPClient
import libcore.Libcore
import org.json.JSONArray
import org.json.JSONObject

/**
 * О программе on Compose (P-About), in place of the old AboutFragment: the version, the
 * update (GitHub, the node's mirror when GitHub is not reached, the selected profile's own core when the network blocks
 * GitHub with the VPN off; the dialogs as before), the phone's limits on the background, the plugins and the project.
 * Opened from the settings, and by [checkingUpdate] from a node that requires a newer Niko.
 */
class AboutFragment : Fragment() {

    companion object {
        private const val CHECK_UPDATE = "check_update"

        /** The screen with the update check begun: a raycontrol node requires a newer Niko (client-min-v1). */
        fun checkingUpdate() = AboutFragment().apply { arguments = Bundle().apply { putBoolean(CHECK_UPDATE, true) } }
    }

    private var state by mutableStateOf(
        AboutState(SagerNet.appVersionNameForDisplay, runCatching { Libcore.versionBox() }.getOrDefault(""), preview = DataStore.updatePreview),
    )

    // the update found: its dialog again, from the row's button
    private var offer: (() -> Unit)? = null

    private val main get() = requireActivity() as MainActivity

    private val requestIgnoreBatteryOptimizations = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> if (result.resultCode == Activity.RESULT_OK) reloadLimits() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { NikoTheme { AboutScreen(state, actions) } }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // once: the screen made again (a rotation) does not check again
        if (savedInstanceState == null && arguments?.getBoolean(CHECK_UPDATE) == true) {
            arguments?.remove(CHECK_UPDATE)
            checkUpdate(state.preview)
        }
    }

    override fun onResume() {
        super.onResume()
        // back from the system's settings: the limits and the plugins as they are now
        reloadLimits()
        reloadPlugins()
    }

    private fun reloadLimits() {
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        state = state.copy(
            batteryOptimized = !pm.isIgnoringBatteryOptimizations(app.packageName),
            restricted = BackgroundLimits.restricted(app),
            vendor = BackgroundLimits.hasVendorManager(),
        )
    }

    /** The plugins installed and whether each is trusted (the package list: off the main thread). */
    private fun reloadPlugins() = runOnDefaultDispatcher {
        PackageCache.awaitLoadSync()
        val rows = PackageCache.installedPluginPackages.values.mapNotNull { pkg ->
            try {
                val id = pkg.providers?.get(0)?.loadString(Plugins.METADATA_KEY_ID)
                if (id.isNullOrBlank()) null else PluginRow(
                    id, Plugins.displayExeProvider(pkg.packageName), pkg.packageName, pkg.versionName.orEmpty(),
                    PluginTrust.isTrusted(pkg.packageName),
                )
            } catch (e: Exception) {
                Logs.w(e)
                null
            }
        }
        onMainDispatcher { state = state.copy(plugins = rows) }
    }

    private val actions = object : AboutActions {
        override fun back() {
            main.displayFragmentWithId(R.id.nav_settings)
        }
        override fun releases() = requireContext().launchCustomTab("https://github.com/$RELEASE_REPO/releases")

        override fun update() {
            val found = offer
            if (state.update is UpdateState.Found && found != null) found() else checkUpdate(state.preview)
        }

        override fun preview(on: Boolean) {
            DataStore.updatePreview = on
            // what was found on the other channel does not hold for this one
            offer = null
            state = state.copy(preview = on, update = UpdateState.Idle)
        }

        // the old screen's request, which the lint baseline held: Niko is not on Play, and a VPN killed in Doze drops
        // the user's connection (M-SET-02)
        @SuppressLint("BatteryLife")
        override fun battery() = requestIgnoreBatteryOptimizations.launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${app.packageName}".toUri()),
        )

        override fun restricted() {
            BackgroundLimits.openAppInfo(requireContext())
        }
        override fun vendor() {
            BackgroundLimits.openVendorManager(requireContext())
        }
        override fun plugin(row: PluginRow) = reviewPlugin(row.id, row.packageName)
        override fun licenses() = startActivity(Intent(requireContext(), LicensesActivity::class.java))
        override fun license() = startActivity(LicenseTextActivity.ofAsset(requireContext(), "LICENSE", "Niko"))
        override fun source() = requireContext().launchCustomTab("https://github.com/$RELEASE_REPO")
        override fun upstream() = requireContext().launchCustomTab("https://github.com/MatsuriDayo/NekoBoxForAndroid")
        override fun donate() = requireContext().launchCustomTab(DONATE_URL)
    }

    /** The plugin's signer and whether to trust it (S8): a plugin runs only once the user said yes. */
    private fun reviewPlugin(pluginId: String, packageName: String) {
        val context = context ?: return
        val signer = PluginTrust.signer(packageName) ?: return
        val trusted = PluginTrust.isTrusted(packageName)
        NikoDialog(context)
            .title(R.string.plugin_trust_title)
            .message(getString(R.string.plugin_trust_message, pluginId, packageName, signer.replace(",", "\n")))
            .ghost(R.string.plugin_app_info) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            .ghost(android.R.string.cancel)
            .apply {
                if (trusted) primary(R.string.plugin_revoke) { _, _ ->
                    PluginTrust.revoke(packageName)
                    reloadPlugins()
                } else primary(R.string.plugin_trust) { _, _ ->
                    PluginTrust.trust(packageName)
                    reloadPlugins()
                }
            }
            .show()
    }

    // ---- the update, as the old screen checked and installed it

    /** The update check's client: through the core when it runs, never around it in proxy mode. */
    private fun updateClient() = Libcore.newHttpClient().apply {
        modernTLS()
        setTimeout(if (DataStore.serviceState.started) Updates.CHECK_TIMEOUT_MS else Updates.CHECK_DIRECT_TIMEOUT_MS)
        trySocks5(DataStore.mixedPort, LOCAL_PROXY_USER, DataStore.localProxyPassword)
        // proxy mode with the service running: never around it (the device's own address)
        if (localProxyRequired()) requireSocks5()
    }

    /**
     * [block] through [profile]'s own core (a test instance, the service off), or with [updateClient] for null:
     * through the running core, or direct.
     */
    private suspend fun <T> viaWay(profile: ProxyEntity?, block: (HTTPClient) -> T): T {
        if (profile == null) return block(updateClient())
        return TestInstance(profile, "", 0).through { box ->
            block(Libcore.newHttpClient().apply {
                modernTLS()
                setTimeout(Updates.CHECK_TIMEOUT_MS)
                useInstance(box)
            })
        }
    }

    /**
     * [block] the first way that reaches GitHub, and that way: through the running core, or direct while the service
     * is off (null); where GitHub is not reached so with the service off, through the selected profile in a core of
     * its own (GitHub blocked on the network, the VPN not on).
     */
    private suspend fun <T> firstWay(block: (HTTPClient) -> T): Pair<T, ProxyEntity?> {
        try {
            return block(updateClient()) to null
        } catch (e: Exception) {
            if (DataStore.serviceState.started || !Updates.unreached(e)) throw e
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: throw e
            Logs.w("update: GitHub not reached directly (${e.message}): through ${profile.displayName()}")
            onMainDispatcher {
                Toast.makeText(app, app.getString(R.string.update_via_profile, profile.displayName()), Toast.LENGTH_SHORT).show()
            }
            return try {
                viaWay(profile, block) to profile
            } catch (through: Exception) {
                if (!Updates.unreached(through)) throw through
                throw Exception("${e.readableMessage}; ${profile.displayName()}: ${through.readableMessage}")
            }
        }
    }

    /** What the row says when the screen is still there to say it. */
    private fun show(update: UpdateState, found: (() -> Unit)? = null) {
        offer = found
        state = state.copy(update = update)
    }

    private fun checkUpdate(checkPreview: Boolean) {
        // an update downloads already (a tap before): no second check, whose dialog could not install it
        if (Updates.busy) {
            Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            return
        }
        show(UpdateState.Checking)
        runOnIoDispatcher {
            try {
                // the check is GitHub's; where it is not reached at all, a raycontrol node's newest release
                // (app-update-v1), whose signature the install checks
                val (content, way) = try {
                    firstWay { client ->
                        try {
                            client.newRequest().apply {
                                if (checkPreview) {
                                    // pre-releases are published from v* tags with a suffix, e.g. v1.5.1-beta.1
                                    setURL("https://api.github.com/repos/$RELEASE_REPO/releases?per_page=20")
                                } else {
                                    setURL("https://api.github.com/repos/$RELEASE_REPO/releases/latest")
                                }
                            }.execute().let { Util.getStringBox(it.contentString) }
                        } catch (e: Exception) {
                            // no release published yet
                            if (e.message?.startsWith("HTTP 404") == true) null else throw e
                        }
                    }
                } catch (e: Exception) {
                    val fromNode = if (Updates.canInstall && Updates.unreached(e)) {
                        RayAppMirror.newest(RayNodes.list(), checkPreview, BuildConfig.VERSION_NAME)
                    } else null
                    if (fromNode == null) throw e
                    Logs.w("update: GitHub not reached (${e.message}): ${fromNode.second.tag} of the node ${fromNode.first.host}")
                    val (node, nodeRelease) = fromNode
                    // NOTICE_BOUND_MS for the whole read, the wait for the node's files (Updates.nodeFiles: one read or
                    // install of them at a time) and carriers, relays and the node's busy answers within it: past it
                    // the update is offered as an ordinary one
                    val notice = readNotice {
                        val deadline = System.currentTimeMillis() + Updates.NOTICE_BOUND_MS
                        // held is set right after lock() with no suspension between: a timeout that lands after the
                        // lock was taken still makes withTimeoutOrNull return null, and the lock must be released then
                        // too (kotlinx.coroutines: asynchronous timeout and resources)
                        var held = false
                        withTimeoutOrNull(Updates.NOTICE_BOUND_MS) {
                            Updates.nodeFiles.lock()
                            held = true
                        }
                        if (!held) null else try {
                            Updates.noticeFromMirror(app, nodeRelease.tag) { name, out, etag ->
                                RaySubscription.appFile(node, nodeRelease.tag, name, out, etag, deadline)
                            }
                        } finally {
                            Updates.nodeFiles.unlock()
                        }
                    }
                    onMainDispatcher {
                        if (!isAdded) return@onMainDispatcher
                        // an install began meanwhile (an earlier dialog's): no second dialog
                        if (Updates.busy) {
                            show(UpdateState.Idle)
                            Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
                        } else if (!notice.fitsHere()) {
                            show(UpdateState.None)
                            notForThisAndroid(nodeRelease.tag, notice)
                        } else {
                            val again = { offerFromNode(node, nodeRelease, notice) }
                            show(UpdateState.Found(nodeRelease.tag), again)
                            again()
                        }
                    }
                    return@runOnIoDispatcher
                }
                val release = when {
                    content == null -> null
                    checkPreview -> JSONArray(content).let { releases ->
                        (0 until releases.length()).map { releases.getJSONObject(it) }
                            .firstOrNull { it.optBoolean("prerelease") && !it.optBoolean("draft") }
                    }

                    else -> JSONObject(content)
                }
                val releaseTag = release?.optString("tag_name").orEmpty()
                val releaseName = release?.optString("name").orEmpty().ifBlank { releaseTag }
                // open only release pages of the owner's repositories, whatever the API returned: after a rename the
                // API redirects and answers with the new repository name
                val owner = Regex.escape(RELEASE_REPO.substringBefore('/'))
                val releaseUrl = release?.optString("html_url").orEmpty()
                    .takeIf { Regex("^https://github\\.com/$owner/[A-Za-z0-9._-]+/releases/").containsMatchIn(it) }
                    .orEmpty()
                // offer only strictly newer versions: a beta user is not sent back to stable
                val haveUpdate = releaseTag.isNotBlank() && releaseUrl.isNotBlank() &&
                    isNewerVersion(releaseTag, BuildConfig.VERSION_NAME)
                // also where the release page is offered in place of the installer: its min_android is said too
                val notice = if (haveUpdate && Updates.enabled && release != null) {
                    readNotice { viaWay(way) { client -> Updates.notice(client, release) } }
                } else null
                runOnMainDispatcher {
                    // the user may have left the screen while the request ran
                    val context = context ?: return@runOnMainDispatcher
                    if (haveUpdate && Updates.busy) {
                        // an install began meanwhile (an earlier dialog's): no second dialog
                        show(UpdateState.Idle)
                        Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
                    } else if (haveUpdate && !notice.fitsHere()) {
                        show(UpdateState.None)
                        notForThisAndroid(releaseName, notice)
                    } else if (haveUpdate) {
                        val again = { offerFromGitHub(context, release, releaseTag, releaseName, releaseUrl, notice, way) }
                        show(UpdateState.Found(releaseName), again)
                        again()
                    } else {
                        show(UpdateState.None)
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
                runOnMainDispatcher { if (isAdded) show(UpdateState.Failed(e.readableMessage)) }
            }
        }
    }

    /** The newer GitHub [release]: install it (the GitHub build), or its page; "No" where it may wait. */
    private fun offerFromGitHub(
        context: Context,
        release: JSONObject?,
        releaseTag: String,
        releaseName: String,
        releaseUrl: String,
        notice: UpdateNotice?,
        way: ProxyEntity?,
    ) {
        if (!isAdded) return
        val openPage = { context.startActivity(Intent(Intent.ACTION_VIEW, releaseUrl.toUri())) }
        NikoDialog(context)
            .title(R.string.update_dialog_title)
            .message(
                context.getString(R.string.update_dialog_message, SagerNet.appVersionNameForDisplay, releaseName) +
                    noticeText(context, notice),
            )
            .apply {
                // the GitHub build: the APK of the release, its signature and hash checked
                if (Updates.canInstall && release != null) {
                    // the files from a node that holds this very release, GitHub behind it
                    val mirror = RayAppMirror.holding(RayNodes.list(), releaseTag)
                    ghost(R.string.update_release_page) { openPage() }
                    withoutLater(notice)
                    primary(R.string.update_install) { _, _ ->
                        if (mirror != null) installFromNode(mirror.spa.nodeId, releaseTag, release, way)
                        else installUpdate(release, way)
                    }
                } else {
                    withoutLater(notice)
                    primary(R.string.yes) { _, _ -> openPage() }
                }
            }
            .show()
    }

    /**
     * The release's UPDATE.json by [read] (client-min-v1 §6), null where it has none or it does not hold: the update
     * is offered as an ordinary one then, and the install checks the release's files again.
     */
    private suspend fun readNotice(read: suspend () -> UpdateNotice?): UpdateNotice? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logs.w("update: ${UpdateNotice.NAME}: ${e.message}")
        null
    }

    /** What [notice] adds to the update's dialog: that it is required, and the release's word in the app's language. */
    private fun noticeText(context: Context, notice: UpdateNotice?): String {
        if (notice == null) return ""
        val language = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "en"
        val lines = listOfNotNull(
            context.getString(R.string.update_required).takeIf { notice.required(RayClient.version) },
            notice.message(language),
        )
        return if (lines.isEmpty()) "" else "\n\n" + lines.joinToString("\n")
    }

    /** "No" where the update may wait; a required one has none and the dialog does not close by back or a tap outside. */
    private fun NikoDialog.withoutLater(notice: UpdateNotice?) {
        if (notice?.required(RayClient.version) == true) cancelable(false)
        else ghost(R.string.no)
    }

    /** Whether the release of [this] notice installs on this Android (its min_android); no notice: it does. */
    private fun UpdateNotice?.fitsHere() = this?.fits(android.os.Build.VERSION.SDK_INT) ?: true

    /** The newer [release] does not install here (its min_android): said, not offered, never mandatory. */
    private fun notForThisAndroid(release: String, notice: UpdateNotice?) {
        val context = context ?: return
        val needs = UpdateNotice.androidName(notice?.minAndroid ?: return)
        NikoDialog(context)
            .title(R.string.update_dialog_title)
            .message(context.getString(R.string.update_needs_android, release, needs))
            .primary(android.R.string.ok)
            .show()
    }

    /** The update [release] that [node] holds: from its mirror, the release page beside it. */
    private fun offerFromNode(node: RayNode, release: RayAppRelease, notice: UpdateNotice?) {
        val context = context ?: return
        NikoDialog(context)
            .title(R.string.update_dialog_title)
            .message(
                context.getString(R.string.update_dialog_message, SagerNet.appVersionNameForDisplay, release.tag) +
                    "\n\n" + context.getString(R.string.update_from_node, node.name ?: node.host) +
                    noticeText(context, notice),
            )
            .ghost(R.string.update_release_page) {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/$RELEASE_REPO/releases/tag/${release.tag}".toUri()))
            }
            .apply { withoutLater(notice) }
            .primary(R.string.update_install) { _, _ -> installFromNode(node.spa.nodeId, release.tag, null, null) }
            .show()
    }

    /**
     * Downloads the release [tag]'s APK for this device from the node's mirror, checks it and installs it; where the
     * node does not give it whole and signed, from GitHub's [github] release the way its check went ([way]), when the
     * check reached GitHub.
     */
    private fun installFromNode(nodeId: String, tag: String, github: JSONObject?, way: ProxyEntity?) = installWith {
        try {
            val node = RayNodes.get(nodeId) ?: throw Exception("raycontrol node $nodeId was removed")
            Updates.nodeFiles.withLock {
                Updates.downloadFromMirror(app, tag) { name, out, etag ->
                    RaySubscription.appFile(node, tag, name, out, etag)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the files refused, or not held after all: the node is not asked for this release again
            if (e is Updates.Refused || e.message?.contains(RaySubscription.APP_NOT_HELD) == true) {
                RayAppMirror.failed(nodeId, tag)
            }
            if (github == null) throw e
            Logs.w("update: from the node: ${e.message}; from GitHub")
            viaWay(way) { client -> Updates.download(app, client, github) }
        }
    }

    /** Downloads the release's APK for this device, the way its check went ([way]), checks it against the signed SHA256SUMS and installs it. */
    private fun installUpdate(release: JSONObject, way: ProxyEntity?) = installWith {
        viaWay(way) { client -> Updates.download(app, client, release) }
    }

    /** [download] (one at a time: a second tap waits for the first), then the installer, or why not. */
    private fun installWith(download: suspend () -> java.io.File) {
        Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        runOnIoDispatcher {
            val message = try {
                val file = Updates.oneDownload { download() }
                val tooNew = file?.let { Updates.needsNewerAndroid(app, it) }
                if (file == null) {
                    // the download of a tap before still runs
                    null
                } else if (tooNew != null) {
                    // its UPDATE.json, with min_android, was not read in time: the APK itself says
                    app.getString(R.string.update_needs_android, "Niko ${tooNew.first}", UpdateNotice.androidName(tooNew.second))
                } else {
                    onMainDispatcher { if (Updates.install(app, file)) null else app.getString(R.string.update_allow_install) }
                }
            } catch (e: Updates.Refused) {
                Logs.w(e)
                app.getString(R.string.update_refused, app.getString(e.reason))
            } catch (e: Exception) {
                Logs.w(e)
                e.readableMessage
            }
            if (message != null) onMainDispatcher { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }
}
