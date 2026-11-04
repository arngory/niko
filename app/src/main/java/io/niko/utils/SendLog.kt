package io.niko.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.use
import io.niko.utils.CrashHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object SendLog {
    // Create full log and send
    fun sendLog(context: Context, title: String) {
        val logFile = File.createTempFile(
            "$title ",
            ".log",
            File(app.cacheDir, "log").also { it.mkdirs() })

        var report = CrashHandler.buildReportHeader()

        report += "Logcat: \n\n"

        logFile.writeText(report)

        try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.use(
                FileOutputStream(
                    logFile, true
                )
            )
            logFile.appendText("\n")
        } catch (e: IOException) {
            Logs.w(e)
            logFile.appendText("Export logcat error: " + CrashHandler.formatThrowable(e))
        }

        logFile.appendText("\n")
        // what raycontrol measured and decided on, per network (no keys, links or node addresses)
        logFile.appendText(runCatching { io.niko.raycontrol.RayDiagnostics.dump() }.getOrElse { "raycontrol: ${it.message}\n" })
        logFile.appendText("\n")
        logFile.appendBytes(getNekoLog(0))

        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/x-log")
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(
                        Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                            context, BuildConfig.APPLICATION_ID + ".cache", logFile
                        )
                    ), context.getString(R.string.abc_shareactionprovider_share_with)
            )
        )
    }

    // Get log bytes from neko.log
    fun getNekoLog(max: Long): ByteArray {
        return try {
            val file = File(
                SagerNet.application.cacheDir,
                "neko.log"
            )
            val len = file.length()
            val stream = FileInputStream(file)
            if (max in 1 until len) {
                stream.skip(len - max) // TODO string?
            }
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            e.stackTraceToString().toByteArray()
        }
    }
}
