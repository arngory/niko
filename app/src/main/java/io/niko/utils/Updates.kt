package io.niko.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.niko.BuildConfig
import io.niko.R
import io.niko.RELEASE_REPO
import libcore.HTTPClient
import libcore.Libcore
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app's own updates (release.yml): a release's SHA256SUMS signed with minisign by the key of niko-update.pub
 * (BuildConfig.UPDATE_PUBLIC_KEY, in the GitHub build alone), its trusted comment `niko <tag>`; the APK of this
 * device's ABI is checked against it before the installer gets it. Android then checks that the APK is signed as the
 * installed app is.
 */
object Updates {

    /** Whether this build installs its updates itself: it carries the key its releases are signed with. */
    val enabled get() = BuildConfig.UPDATE_PUBLIC_KEY.isNotBlank()

    /**
     * Whether it can on this device: Android 7.0 and later, whose installer takes the APK by a content:// address (6.0's
     * takes file:// only); the release page otherwise.
     */
    val canInstall get() = enabled && Build.VERSION.SDK_INT >= 24

    // a request's bound, its body read included: the signed list and its signature are small, the APK some tens of MB
    // through the core at its slowest (a hung transfer through a cut tunnel no longer holds the download for good)
    private const val SMALL_TIMEOUT_MS = 60_000
    private const val APK_TIMEOUT_MS = 20 * 60_000

    const val SUMS = "SHA256SUMS"
    const val SIGNATURE = "SHA256SUMS.minisig"

    /** An update not installed: [reason] says why to the user, [detail] to the log. */
    class Refused(val reason: Int, detail: String) : Exception(detail)

    /** The trusted comment of a release's signature: the tag it was signed for. */
    fun trustedComment(tag: String) = "niko $tag"

    private val sumLine = Regex("^([0-9a-fA-F]{64}) [ *]([^/\\\\]+)$")

    /** sha256sum's lines: the hash of each file by its name. */
    fun sums(text: String): Map<String, String> = text.lineSequence().mapNotNull { line ->
        sumLine.find(line.trimEnd('\r'))?.let { it.groupValues[2] to it.groupValues[1].lowercase() }
    }.toMap()

    /** The APK among [sums] for the first of [abis] it has (Niko-<version>-<abi>.apk), with its hash. */
    fun apkFor(sums: Map<String, String>, abis: List<String>): Pair<String, String>? = abis.firstNotNullOfOrNull { abi ->
        sums.entries.firstOrNull { (name, _) -> name.startsWith("Niko-") && name.endsWith("-$abi.apk") }?.toPair()
    }

    // the owner's releases, whatever the repository's name: after a rename the API answers with the new one (as the
    // release page's check in AboutFragment); the trust is the signature's, not the address's
    private val assetUrl = Regex(
        "^https://github\\.com/${Regex.escape(RELEASE_REPO.substringBefore('/'))}/[A-Za-z0-9._-]+/releases/download/[^/?#]+/[^/?#]+$"
    )

    /** A release's assets by name (the GitHub API's release object), only those under the owner's releases. */
    fun assets(release: JSONObject): Map<String, String> {
        val list = release.optJSONArray("assets") ?: return emptyMap()
        return (0 until list.length()).mapNotNull { i ->
            val asset = list.optJSONObject(i) ?: return@mapNotNull null
            val url = asset.optString("browser_download_url")
            asset.optString("name").takeIf { it.isNotEmpty() && assetUrl.matches(url) }?.let { it to url }
        }.toMap()
    }

    /**
     * Checks [sums] against [signature] under [publicKey] for the release [tag]: the names and hashes it lists, or
     * Refused. [verify] is minisign's check (libcore's), the trusted comment its result.
     */
    fun checkedSums(
        publicKey: String, tag: String, sums: ByteArray, signature: String,
        verify: (String, String, ByteArray) -> String,
    ): Map<String, String> {
        val trusted = try {
            verify(publicKey, signature, sums)
        } catch (e: Exception) {
            throw Refused(R.string.update_bad_signature, "the signature does not hold: ${e.message}")
        }
        if (trusted != trustedComment(tag)) {
            throw Refused(R.string.update_other_release, "signed for \"$trusted\", not for $tag")
        }
        return sums(String(sums, Charsets.UTF_8)).ifEmpty { throw Refused(R.string.update_no_apk, "$SUMS lists nothing") }
    }

    /** The ABIs the APK is chosen by: the running app's own first, then the device's in its order. */
    fun abis(context: Context): List<String> {
        val own = when (File(context.applicationInfo.nativeLibraryDir ?: "").name) {
            "arm64" -> "arm64-v8a"
            "arm" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> null
        }
        return (listOfNotNull(own) + Build.SUPPORTED_ABIS).distinct()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private val downloading = AtomicBoolean()

    /**
     * The APK of [release] for this device, its signature and hash checked; through [client] (the update check's:
     * through the core when it runs). One at a time: null while another runs. A verified APK already in the cache is
     * not downloaded again (the user allowed the installs in between).
     */
    fun download(context: Context, client: HTTPClient, release: JSONObject): File? {
        if (!downloading.compareAndSet(false, true)) return null
        try {
            val tag = release.optString("tag_name")
            val assets = assets(release)
            val sumsUrl = assets[SUMS] ?: throw Refused(R.string.update_no_signature, "the release has no $SUMS")
            val signatureUrl = assets[SIGNATURE] ?: throw Refused(R.string.update_no_signature, "the release has no signature")
            client.setTimeout(SMALL_TIMEOUT_MS)
            val sums = client.newRequest().apply { setURL(sumsUrl) }.execute().content
            val signature = Util.getStringBox(client.newRequest().apply { setURL(signatureUrl) }.execute().contentString)
            val listed = checkedSums(BuildConfig.UPDATE_PUBLIC_KEY, tag, sums, signature, Libcore::minisignVerify)
            val (name, hash) = apkFor(listed, abis(context))
                ?: throw Refused(R.string.update_no_apk, "no APK for ${abis(context)}")
            val url = assets[name] ?: throw Refused(R.string.update_no_apk, "the release lists $name but has no such file")
            val dir = File(context.cacheDir, "update")
            val file = File(dir, name)
            if (file.isFile && sha256(file) == hash) return file
            dir.deleteRecursively()
            dir.mkdirs()
            client.setTimeout(APK_TIMEOUT_MS)
            client.newRequest().apply { setURL(url) }.execute().writeTo(file.canonicalPath)
            if (sha256(file) != hash) {
                file.delete()
                throw Refused(R.string.update_bad_apk, "the downloaded $name is not the signed one")
            }
            return file
        } finally {
            downloading.set(false)
        }
    }

    /**
     * Hands [file] to the system's installer; false when the user has to allow this app to install apps first (the
     * settings page opened for it).
     */
    fun install(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
