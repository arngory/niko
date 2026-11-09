package io.niko.ui

import io.niko.fmt.LOCAL_PROXY_USER
import io.niko.fmt.localProxyRequired
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import android.widget.Toast
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import io.niko.BuildConfig
import io.niko.R
import io.niko.RELEASE_REPO
import io.niko.databinding.LayoutAboutBinding
import io.niko.ktx.*
import io.niko.plugin.PluginManager.loadString
import io.niko.utils.PackageCache
import io.niko.utils.Updates
import io.niko.utils.isNewerVersion
import io.niko.widget.ListListener
import libcore.Libcore
import io.niko.plugin.PluginTrust
import io.niko.plugin.Plugins
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.SagerNet
import io.niko.bg.proto.TestInstance
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.raycontrol.RayAppMirror
import kotlinx.coroutines.CancellationException
import io.niko.raycontrol.RayAppRelease
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RaySubscription
import libcore.HTTPClient
import io.niko.utils.Util
import org.json.JSONArray
import org.json.JSONObject

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutAboutBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(view, ListListener)
        toolbar.setTitle(R.string.menu_about)

        parentFragmentManager.beginTransaction()
            .replace(R.id.about_fragment_holder, AboutContent())
            .commitAllowingStateLoss()

        runOnDefaultDispatcher {
            val license = view.context.assets.open("LICENSE").bufferedReader().readText()
            onMainDispatcher {
                binding.license.text = license
                Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
            }
        }
    }

    class AboutContent : MaterialAboutFragment() {

        val requestIgnoreBatteryOptimizations = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (resultCode, _) ->
            if (resultCode == Activity.RESULT_OK) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.about_fragment_holder, AboutContent())
                    .commitAllowingStateLoss()
            }
        }

        /** The plugin's signer and whether to trust it (S8): a plugin runs only once the user said yes. */
        private fun reviewPlugin(pluginId: String, packageName: String) {
            val context = context ?: return
            val signer = PluginTrust.signer(packageName) ?: return
            val trusted = PluginTrust.isTrusted(packageName)
            fun reload() = parentFragmentManager.beginTransaction()
                .replace(R.id.about_fragment_holder, AboutContent())
                .commitAllowingStateLoss()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.plugin_trust_title)
                .setMessage(
                    getString(R.string.plugin_trust_message, pluginId, packageName, signer.replace(",", "\n"))
                )
                .apply {
                    if (trusted) setPositiveButton(R.string.plugin_revoke) { _, _ ->
                        PluginTrust.revoke(packageName)
                        reload()
                    } else setPositiveButton(R.string.plugin_trust) { _, _ ->
                        PluginTrust.trust(packageName)
                        reload()
                    }
                }
                .setNeutralButton(R.string.plugin_app_info) { _, _ ->
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", packageName, null)
                    })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        override fun getMaterialAboutList(activityContext: Context): MaterialAboutList {
            return MaterialAboutList.Builder()
                .addCard(
                    MaterialAboutCard.Builder()
                        .outline(false)
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_update_24)
                                .text(R.string.app_version)
                                .subText(SagerNet.appVersionNameForDisplay)
                                .setOnClickAction {
                                    requireContext().launchCustomTab(
                                        "https://github.com/$RELEASE_REPO/releases"
                                    )
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .text(R.string.check_update_release)
                                .setOnClickAction {
                                    checkUpdate(false)
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .text(R.string.check_update_preview)
                                .setOnClickAction {
                                    checkUpdate(true)
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_layers_24)
                                .text(getString(R.string.version_x, "sing-box"))
                                .subText(Libcore.versionBox())
                                .setOnClickAction { }
                                .build())
                        .apply {
                            PackageCache.awaitLoadSync()
                            for ((_, pkg) in PackageCache.installedPluginPackages) {
                                try {
                                    val pluginId =
                                        pkg.providers?.get(0)?.loadString(Plugins.METADATA_KEY_ID)
                                    if (pluginId.isNullOrBlank()) continue
                                    addItem(
                                        MaterialAboutActionItem.Builder()
                                            .icon(R.drawable.ic_baseline_nfc_24)
                                            .text(
                                                getString(
                                                    R.string.version_x,
                                                    pluginId
                                                ) + " (${Plugins.displayExeProvider(pkg.packageName)})"
                                            )
                                            .subText("v" + pkg.versionName + " · " + getString(
                                                if (PluginTrust.isTrusted(pkg.packageName)) R.string.plugin_trusted
                                                else R.string.plugin_not_trusted
                                            ))
                                            .setOnClickAction { reviewPlugin(pluginId, pkg.packageName) }
                                            .build())
                                } catch (e: Exception) {
                                    Logs.w(e)
                                }
                            }
                        }
                        .apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                                if (!pm.isIgnoringBatteryOptimizations(app.packageName)) {
                                    addItem(
                                        MaterialAboutActionItem.Builder()
                                            .icon(R.drawable.ic_baseline_running_with_errors_24)
                                            .text(R.string.ignore_battery_optimizations)
                                            .subText(R.string.ignore_battery_optimizations_sum)
                                            .setOnClickAction {
                                                requestIgnoreBatteryOptimizations.launch(
                                                    Intent(
                                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                        "package:${app.packageName}".toUri()
                                                    )
                                                )
                                            }
                                            .build())
                                }
                            }
                        }
                        .build())
                .addCard(
                    MaterialAboutCard.Builder()
                        .outline(false)
                        .title(R.string.project)
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_baseline_sanitizer_24)
                                .text(R.string.github)
                                .setOnClickAction {
                                    requireContext().launchCustomTab("https://github.com/$RELEASE_REPO")
                                }
                                .build())
                        .addItem(
                            MaterialAboutActionItem.Builder()
                                .icon(R.drawable.ic_qu_shadowsocks_foreground)
                                .text(R.string.upstream_project)
                                .setOnClickAction {
                                    requireContext().launchCustomTab(
                                        "https://github.com/MatsuriDayo/NekoBoxForAndroid"
                                    )
                                }
                                .build())
                        .build())
                .build()

        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<RecyclerView>(R.id.mal_recyclerview).apply {
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            }
        }

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
                    Toast.makeText(app, app.getString(R.string.update_via_profile, profile.displayName()), Toast.LENGTH_SHORT)
                        .show()
                }
                return try {
                    viaWay(profile, block) to profile
                } catch (through: Exception) {
                    if (!Updates.unreached(through)) throw through
                    throw Exception("${e.readableMessage}; ${profile.displayName()}: ${through.readableMessage}")
                }
            }
        }

        fun checkUpdate(checkPreview: Boolean) {
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
                        onMainDispatcher { offerFromNode(fromNode.first, fromNode.second) }
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
                    // open only release pages of the owner's repositories, whatever the API returned:
                    // after a rename the API redirects and answers with the new repository name
                    val owner = Regex.escape(RELEASE_REPO.substringBefore('/'))
                    val releaseUrl = release?.optString("html_url").orEmpty()
                        .takeIf { Regex("^https://github\\.com/$owner/[A-Za-z0-9._-]+/releases/").containsMatchIn(it) }
                        .orEmpty()
                    // offer only strictly newer versions: a beta user is not sent back to stable
                    val haveUpdate = releaseTag.isNotBlank() && releaseUrl.isNotBlank() &&
                            isNewerVersion(releaseTag, BuildConfig.VERSION_NAME)
                    runOnMainDispatcher {
                        // the user may have left the screen while the request ran
                        val context = context ?: return@runOnMainDispatcher
                        if (haveUpdate) {
                            val openPage = { context.startActivity(Intent(Intent.ACTION_VIEW, releaseUrl.toUri())) }
                            MaterialAlertDialogBuilder(context)
                                .setTitle(R.string.update_dialog_title)
                                .setMessage(
                                    context.getString(
                                        R.string.update_dialog_message,
                                        SagerNet.appVersionNameForDisplay,
                                        releaseName
                                    )
                                )
                                .apply {
                                    // the GitHub build: the APK of the release, its signature and hash checked
                                    if (Updates.canInstall && release != null) {
                                        // the files from a node that holds this very release, GitHub behind it
                                        val mirror = RayAppMirror.holding(RayNodes.list(), releaseTag)
                                        setPositiveButton(R.string.update_install) { _, _ ->
                                            if (mirror != null) installFromNode(mirror.spa.nodeId, releaseTag, release, way)
                                            else installUpdate(release, way)
                                        }
                                        setNeutralButton(R.string.update_release_page) { _, _ -> openPage() }
                                    } else {
                                        setPositiveButton(R.string.yes) { _, _ -> openPage() }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        } else {
                            Toast.makeText(app, R.string.check_update_no, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    runOnMainDispatcher {
                        Toast.makeText(app, e.readableMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        /** The update [release] that [node] holds: from its mirror, the release page beside it. */
        private fun offerFromNode(node: RayNode, release: RayAppRelease) {
            val context = context ?: return
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.update_dialog_title)
                .setMessage(
                    context.getString(R.string.update_dialog_message, SagerNet.appVersionNameForDisplay, release.tag) +
                            "\n\n" + context.getString(R.string.update_from_node, node.name ?: node.host)
                )
                .setPositiveButton(R.string.update_install) { _, _ -> installFromNode(node.spa.nodeId, release.tag, null, null) }
                .setNeutralButton(R.string.update_release_page) { _, _ ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://github.com/$RELEASE_REPO/releases/tag/${release.tag}".toUri())
                    )
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }

        /**
         * Downloads the release [tag]'s APK for this device from the node's mirror, checks it and installs it; where the
         * node does not give it whole and signed, from GitHub's [github] release the way its check went ([way]), when
         * the check reached GitHub.
         */
        private fun installFromNode(nodeId: String, tag: String, github: JSONObject?, way: ProxyEntity?) = installWith {
            try {
                val node = RayNodes.get(nodeId) ?: throw Exception("raycontrol node $nodeId was removed")
                Updates.downloadFromMirror(app, tag) { name, out, etag -> RaySubscription.appFile(node, tag, name, out, etag) }
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

        /**
         * Downloads the release's APK for this device, the way its check went ([way]), checks it against the signed
         * SHA256SUMS and installs it.
         */
        private fun installUpdate(release: JSONObject, way: ProxyEntity?) = installWith {
            viaWay(way) { client -> Updates.download(app, client, release) }
        }

        /** [download] (one at a time: a second tap waits for the first), then the installer, or why not. */
        private fun installWith(download: suspend () -> java.io.File) {
            Toast.makeText(app, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            runOnIoDispatcher {
                val message = try {
                    val file = Updates.oneDownload { download() }
                    if (file == null) {
                        // the download of a tap before still runs
                        null
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

}