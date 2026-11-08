package io.niko.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.ktx.Logs
import io.niko.ktx.app
import libcore.Libcore
import io.niko.utils.CrashHandler
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object SendLog {
    // Create full log and send
    fun sendLog(context: Context, title: String) {
        val logFile = File.createTempFile(
            "$title ",
            ".log",
            File(app.cacheDir, "log").also { it.mkdirs() })

        val report = StringBuilder(CrashHandler.buildReportHeader())
        report.append("Logcat: \n\n")
        try {
            val logcat = Runtime.getRuntime().exec(arrayOf("logcat", "-d")).inputStream.bufferedReader().use { it.readText() }
            report.append(logcat).append("\n")
        } catch (e: IOException) {
            Logs.w(e)
            report.append("Export logcat error: " + CrashHandler.formatThrowable(e))
        }
        report.append("\n")
        // what raycontrol measured and decided on, per network (no keys, links or node addresses)
        report.append(runCatching { io.niko.raycontrol.RayDiagnostics.dump() }.getOrElse { "raycontrol: ${it.message}\n" })
        report.append("\n")
        report.append(String(getNekoLog(0)))
        // the core's crashes, each at its whole length (libcore setCrashOutput): the log keeps only its last part
        for (name in listOf("go-crash-bg.log", "go-crash.log", "go-crash-bg.log.old", "go-crash.log.old")) {
            val crash = File(SagerNet.application.cacheDir, name)
            if (crash.length() > 0) {
                report.append("\n\n==== $name (${crash.length()} bytes, ${java.util.Date(crash.lastModified())})\n")
                report.append(runCatching { crash.readText() }.getOrElse { it.toString() })
            }
        }
        // the whole export without secrets and node addresses, logcat and old log lines too (libcore redact.go);
        // the nodes as stored now, also those the service process added
        io.niko.raycontrol.RayNodes.syncLogRedactions()
        logFile.writeText(
            report.lines().chunked(2000).joinToString("\n") { chunk ->
                runCatching { Libcore.redactLog(chunk.joinToString("\n")) }.getOrDefault("<redaction failed>")
            }
        )

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
                stream.skip(len - max)
                // from the next whole line: the cut may fall inside a line or a UTF-8 character
                val bytes = stream.use { it.readBytes() }
                val start = bytes.indexOf('\n'.code.toByte()).let { if (it < 0) 0 else it + 1 }
                return bytes.copyOfRange(start, bytes.size)
            }
            stream.use { it.readBytes() }
        } catch (e: Exception) {
            e.stackTraceToString().toByteArray()
        }
    }
}
