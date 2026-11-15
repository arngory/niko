package io.niko.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jakewharton.processphoenix.ProcessPhoenix
import io.niko.BuildConfig
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.preference.PublicDatabase
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ui.BlankActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

object CrashHandler : Thread.UncaughtExceptionHandler {

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // note: libc / go panic is in android log

        try {
            Log.e(thread.toString(), throwable.stackTraceToString())
        } catch (e: Exception) {
        }

        try {
            Logs.e(thread.toString())
            Logs.e(throwable.stackTraceToString())
        } catch (e: Exception) {
        }

        // a crash soon after the last restart for one (the report itself failing, or the start): no other restart,
        // or the app would loop restarting (ThroneForAndroid 3c4f9cb found it in NekoBox's code)
        val now = System.currentTimeMillis()
        val marker = try {
            java.io.File(app.noBackupFilesDir, "crash-rebirth")
        } catch (e: Exception) {
            null
        }
        val last = try {
            marker?.readText()?.trim()?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
        if (!rebirthAllowed(now, last)) {
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
        try {
            marker?.writeText(now.toString())
        } catch (e: Exception) {
        }
        ProcessPhoenix.triggerRebirth(app, Intent(app, BlankActivity::class.java).apply {
            putExtra("sendLog", "Niko Crash")
        })
    }

    private const val REBIRTH_GAP = 30_000L

    /** Whether a crash at [now] restarts the app for its report: not within [REBIRTH_GAP] of the last such restart. */
    internal fun rebirthAllowed(now: Long, last: Long) = now - last !in 0 until REBIRTH_GAP

    fun formatThrowable(throwable: Throwable): String {
        var format = throwable.javaClass.name
        val message = throwable.message
        if (!message.isNullOrBlank()) {
            format += ": $message"
        }
        format += "\n"

        format += throwable.stackTrace.joinToString("\n") {
            "    at ${it.className}.${it.methodName}(${it.fileName}:${if (it.isNativeMethod) "native" else it.lineNumber})"
        }

        val cause = throwable.cause
        if (cause != null) {
            format += "\n\nCaused by: " + formatThrowable(cause)
        }

        return format
    }

    fun buildReportHeader(): String {
        var report = ""
        report += "Niko ${SagerNet.appVersionNameForDisplay} (${BuildConfig.VERSION_CODE})\n"
        report += "Date: ${getCurrentMilliSecondUTCTimeStamp()}\n\n"
        report += "OS_VERSION: ${getSystemPropertyWithAndroidAPI("os.version")}\n"
        report += "SDK_INT: ${Build.VERSION.SDK_INT}\n"
        report += if ("REL" == Build.VERSION.CODENAME) {
            "RELEASE: ${Build.VERSION.RELEASE}"
        } else {
            "CODENAME: ${Build.VERSION.CODENAME}"
        } + "\n"
        report += "ID: ${Build.ID}\n"
        report += "DISPLAY: ${Build.DISPLAY}\n"
        report += "INCREMENTAL: ${Build.VERSION.INCREMENTAL}\n"

        val systemProperties = getSystemProperties()

        report += "SECURITY_PATCH: ${systemProperties.getProperty("ro.build.version.security_patch")}\n"
        report += "IS_DEBUGGABLE: ${systemProperties.getProperty("ro.debuggable")}\n"
        report += "IS_EMULATOR: ${systemProperties.getProperty("ro.boot.qemu")}\n"
        report += "IS_TREBLE_ENABLED: ${systemProperties.getProperty("ro.treble.enabled")}\n"

        report += "TYPE: ${Build.TYPE}\n"
        report += "TAGS: ${Build.TAGS}\n\n"

        report += "MANUFACTURER: ${Build.MANUFACTURER}\n"
        report += "BRAND: ${Build.BRAND}\n"
        report += "MODEL: ${Build.MODEL}\n"
        report += "PRODUCT: ${Build.PRODUCT}\n"
        report += "BOARD: ${Build.BOARD}\n"
        report += "HARDWARE: ${Build.HARDWARE}\n"
        report += "DEVICE: ${Build.DEVICE}\n"
        report += "SUPPORTED_ABIS: ${
            Build.SUPPORTED_ABIS.filter { it.isNotBlank() }.joinToString(", ")
        }\n\n"


        try {
            report += "Settings: \n"
            for (pair in PublicDatabase.kvPairDao.all()) {
                report += "\n"
                // the report is meant to be shared: keep passwords and tokens out of it
                report += pair.key + ": " + if (DataStore.isSensitiveKey(pair.key)) "<redacted>" else pair.toString()
            }
        } catch (e: Exception) {
            report += "Export settings failed: " + formatThrowable(e)
        }

        report += "\n\n"

        return report
    }

    private fun getSystemProperties(): Properties {
        val systemProperties = Properties()

        // getprop commands returns values in the format `[key]: [value]`
        // Regex matches string starting with a literal `[`,
        // followed by one or more characters that do not match a closing square bracket as the key,
        // followed by a literal `]: [`,
        // followed by one or more characters as the value,
        // followed by string ending with literal `]`
        // multiline values will be ignored
        val propertiesPattern = Pattern.compile("^\\[([^]]+)]: \\[(.+)]$")
        try {
            val process = ProcessBuilder().command("/system/bin/getprop")
                .redirectErrorStream(true)
                .start()
            val inputStream = process.inputStream
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var key: String
            var value: String
            while (bufferedReader.readLine().also { line = it } != null) {
                val matcher = propertiesPattern.matcher(line)
                if (matcher.matches()) {
                    key = matcher.group(1)
                    value = matcher.group(2)
                    if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) systemProperties[key] =
                        value
                }
            }
            bufferedReader.close()
            process.destroy()
        } catch (e: IOException) {
            Logs.e(
                "Failed to get run \"/system/bin/getprop\" to get system properties.", e
            )
        }

        //for (String key : systemProperties.stringPropertyNames()) {
        //    Logger.logVerbose(key + ": " +  systemProperties.get(key));
        //}
        return systemProperties
    }

    private fun getSystemPropertyWithAndroidAPI(property: String): String? {
        return try {
            System.getProperty(property)
        } catch (e: Exception) {
            Logs.e("Failed to get system property \"" + property + "\":" + e.message)
            null
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun getCurrentMilliSecondUTCTimeStamp(): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

}