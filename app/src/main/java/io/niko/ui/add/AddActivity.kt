package io.niko.ui.add

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.king.zxing.CameraScan
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.DefaultCameraScan
import com.king.zxing.analyze.QRCodeAnalyzer
import com.king.zxing.util.CodeUtils
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.fmt.AbstractBean
import io.niko.fmt.overridesFullConfig
import io.niko.group.RawUpdater
import io.niko.ktx.Logs
import io.niko.ktx.SubscriptionFoundException
import io.niko.ktx.app
import io.niko.ktx.forEachTry
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.startFilesForResult
import io.niko.raycontrol.RayImport
import io.niko.ui.Dialogs
import io.niko.ui.ImportReview
import io.niko.ui.MainActivity
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoDialog
import io.niko.ui.compose.NikoTheme
import io.niko.ui.profile.ProfileActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

/**
 * Добавить on Compose (P-Scan, P-ScanDenied), in place of ScannerActivity and the old list's «+»: the camera reads a
 * QR code as before (ZXingLite, the whole frame, a code light on dark too); the clipboard, pictures and files are
 * imported as the old list imported them; a profile by hand opens its editor. The launcher's «scan» shortcut opens it.
 */
class AddActivity : ThemedActivity(), CameraScan.OnScanResultCallback {

    companion object {
        private const val ASKED = "asked"
    }

    private var state by mutableStateOf(AddState())
    private lateinit var preview: PreviewView
    private var cameraScan: CameraScan? = null

    // the camera asked for once by itself; after a refusal only «Разрешить» asks
    private var asked = false

    override fun snackbarInternal(text: CharSequence): Snackbar =
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")
        asked = savedInstanceState?.getBoolean(ASKED) ?: false
        preview = PreviewView(this)
        setContent {
            NikoTheme { AddScreen(state, actions) { AndroidView({ preview }, Modifier.fillMaxSize()) } }
        }
        when {
            cameraAllowed() -> startCamera()
            !asked -> {
                asked = true
                askCamera.launch(Manifest.permission.CAMERA)
            }
            else -> state = state.copy(camera = AddCamera.DENIED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ASKED, asked)
    }

    override fun onResume() {
        super.onResume()
        // allowed in the system's settings meanwhile
        if (state.camera == AddCamera.DENIED && cameraAllowed()) startCamera()
    }

    private fun cameraAllowed() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else state = state.copy(camera = AddCamera.DENIED)
    }

    private fun startCamera() {
        if (cameraScan != null) return
        cameraScan = DefaultCameraScan(this, preview).apply {
            // a code light on dark too (a QR drawn in a dark terminal), tried when a frame has no other
            setAnalyzer(
                QRCodeAnalyzer(
                    DecodeConfig().setHints(DecodeFormatManager.QR_CODE_HINTS).setSupportLuminanceInvert(true)
                        // the whole frame, not its centre only: a code off the middle of the screen is read too
                        .setFullAreaScan(true),
                ),
            )
            setOnScanResultCallback(this@AddActivity)
            setNeedAutoZoom(true)
            startCamera()
        }
        state = state.copy(camera = AddCamera.ON)
    }

    override fun onDestroy() {
        cameraScan?.release()
        super.onDestroy()
        if (importedN.get() > 0) {
            Toast.makeText(app, resources.getQuantityString(R.plurals.added, importedN.get(), importedN.get()), Toast.LENGTH_LONG).show()
        }
    }

    private val finished = AtomicBoolean(false)
    private val importedN = AtomicInteger(0)

    override fun onScanResultCallback(result: Result?): Boolean = onScanResult(result, false)

    /**
     * A code read: by the camera the screen closes at once and the import goes on; of a picture ([multi]) there may
     * be more to come. A node's invite and a subscription go to [MainActivity], which asks first; so does a profile
     * that rewrites the whole config ([ImportReview]).
     */
    private fun onScanResult(result: Result?, multi: Boolean): Boolean {
        if (!multi && finished.getAndSet(true)) return true
        if (!multi) finish()
        runOnDefaultDispatcher {
            try {
                val text = result?.text ?: throw Exception("QR code not found")
                if (RayImport.matches(text)) {
                    // a QR code may come from anywhere, like a link: MainActivity asks before the import
                    startActivity(
                        Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse(RayImport.token(text))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    return@runOnDefaultDispatcher
                }
                val results = RawUpdater.parseRaw(text)
                if (!results.isNullOrEmpty()) {
                    val currentGroupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != currentGroupId) {
                        DataStore.selectedGroup = currentGroupId
                    }
                    // a QR code may come from anywhere: a profile that rewrites the whole config is asked first
                    val (review, direct) = results.partition { it.overridesFullConfig() }
                    for (profile in direct) {
                        ProfileManager.createProfile(currentGroupId, profile)
                        importedN.addAndGet(1)
                    }
                    if (review.isNotEmpty()) ImportReview.handOff(app, review)
                } else {
                    onMainDispatcher { Toast.makeText(app, R.string.action_import_err, Toast.LENGTH_SHORT).show() }
                }
            } catch (e: SubscriptionFoundException) {
                openSubscription(e.link)
            } catch (e: Throwable) {
                Logs.w(e)
                onMainDispatcher {
                    Toast.makeText(app, getString(R.string.action_import_err) + "\n" + e.readableMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    /** A subscription's link: [MainActivity] asks about it, as for a link opened from outside. */
    private fun openSubscription(link: String) {
        startActivity(
            Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(link.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private val importCodeFile = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        runOnDefaultDispatcher {
            try {
                uris.forEachTry { uri ->
                    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                    val result = CodeUtils.parseCodeResult(bitmap) ?: invertedCode(bitmap)
                    onMainDispatcher { onScanResult(result, true) }
                }
                finish()
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher { Toast.makeText(app, e.readableMessage, Toast.LENGTH_LONG).show() }
            }
        }
    }

    /**
     * The QR code in [bitmap] with its light and dark swapped (one drawn in a dark terminal: rayctl device invite -qr),
     * or null; a large picture scaled to 2048 px first.
     */
    private fun invertedCode(bitmap: Bitmap): Result? {
        val scale = minOf(1f, 2048f / maxOf(bitmap.width, bitmap.height))
        val image = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return CodeUtils.parseCodeResult(RGBLuminanceSource(image.width, image.height, pixels).invert(), DecodeFormatManager.QR_CODE_HINTS)
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
        if (file != null) busy {
            val fileName = contentResolver.query(file, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
            }
            val proxies = mutableListOf<AbstractBean>()
            if (fileName != null && fileName.endsWith(".zip")) {
                // WireGuard's archive: a config in each entry
                ZipInputStream(contentResolver.openInputStream(file)!!).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val fileText = zip.bufferedReader().readText()
                        RawUpdater.parseRaw(fileText, entry.name)?.let { pl -> proxies.addAll(pl) }
                        zip.closeEntry()
                    }
                }
            } else {
                val fileText = contentResolver.openInputStream(file)!!.use { it.bufferedReader().readText() }
                // `rayctl device add -o phone.bundle`: a raycontrol device package
                if (importRaycontrol(fileText)) return@busy
                RawUpdater.parseRaw(fileText, fileName ?: "")?.let { pl -> proxies.addAll(pl) }
            }
            if (proxies.isEmpty()) say(getString(R.string.no_proxies_found_in_file)) else import(proxies)
        }
    }

    /** [work] off the main thread with the rows waiting; a subscription found goes to its question, an error is said. */
    private fun busy(work: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true)
        runOnDefaultDispatcher {
            try {
                work()
            } catch (e: SubscriptionFoundException) {
                openSubscription(e.link)
                onMainDispatcher { finish() }
            } catch (e: Exception) {
                Logs.w(e)
                say(e.readableMessage)
            } finally {
                onMainDispatcher { state = state.copy(busy = false) }
            }
        }
    }

    private suspend fun say(message: String) = onMainDispatcher {
        if (!isFinishing && !isDestroyed) snackbar(message).show()
    }

    /** [proxies] into the group imports go to, after the question a profile that rewrites the whole config raises. */
    private suspend fun import(proxies: List<AbstractBean>) {
        // clipboard and file: the same question as a link when a profile rewrites the whole config
        ImportReview.warning(app, proxies)?.let { question ->
            if (!onMainDispatcher { confirm(question) }) return
        }
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            DataStore.editingGroup = targetId
            Toast.makeText(app, resources.getQuantityString(R.plurals.added, proxies.size, proxies.size), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * A raycontrol invite or device package anywhere in [text] (also the whole output of `rayctl device add`):
     * registers or imports the node and fetches its subscription. Returns false when the text holds none.
     */
    private suspend fun importRaycontrol(text: String): Boolean {
        if (!RayImport.matches(text)) return false
        // replacing a stored node (its key, its admin access) is asked first, whatever the source
        val question = RayImport.replaceQuestion(app, text)
        if (question != null && !onMainDispatcher { confirm(question) }) return true
        // the registration and the fetch take seconds: the screen may be gone by then
        RayImport.import(app, text, { host, left, wrong ->
            onMainDispatcher { if (!isFinishing && !isDestroyed) Dialogs.askPin(this@AddActivity, host, left, wrong) else null }
        }) { message -> onMainDispatcher { Toast.makeText(app, message, Toast.LENGTH_LONG).show() } }
        return true
    }

    private suspend fun confirm(message: String): Boolean = suspendCancellableCoroutine { continuation ->
        if (isFinishing || isDestroyed) return@suspendCancellableCoroutine continuation.resume(false)
        var answered = false
        val answer = { yes: Boolean ->
            if (!answered) {
                answered = true
                continuation.resume(yes)
            }
        }
        NikoDialog(this)
            .title(R.string.add_title)
            .message(message)
            .ghost(R.string.dialog_cancel) { answer(false) }
            .primary(R.string.yes) { _, _ -> answer(true) }
            .onDismiss { answer(false) }
            .show()
    }

    private val actions = object : AddActions {
        override fun back() = finish()

        override fun torch() {
            val scan = cameraScan ?: return
            val on = !scan.isTorchEnabled
            scan.enableTorch(on)
            state = state.copy(torch = on)
        }

        override fun allow() {
            // the system asks while it still may; after «не спрашивать» only its settings can allow
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                askCamera.launch(Manifest.permission.CAMERA)
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }

        override fun clipboard() {
            val text = SagerNet.getClipboardText()
            if (text.isBlank()) {
                snackbar(getString(R.string.clipboard_empty)).show()
                return
            }
            busy {
                if (RayImport.matches(text)) {
                    // a package or invite is a secret: do not leave it in the clipboard
                    SagerNet.clearClipboard()
                    importRaycontrol(text)
                    return@busy
                }
                val proxies = RawUpdater.parseRaw(text)
                if (proxies.isNullOrEmpty()) say(getString(R.string.no_proxies_found_in_clipboard)) else import(proxies)
            }
        }

        override fun qrFile() = startFilesForResult(importCodeFile, "image/*")

        override fun file() = startFilesForResult(importFile, "*/*")

        override fun manual(open: Boolean) {
            state = state.copy(manual = open)
        }

        override fun create(kind: AddManual) {
            state = state.copy(manual = false)
            startActivity(ProfileActivity.create(this@AddActivity, kind.type, vless = kind.vless))
            finish()
        }
    }
}
