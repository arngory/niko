package io.niko.ktx

import libcore.Libcore
import java.io.InputStream
import java.io.OutputStream

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].className.substringAfterLast(".")
    }

    // level int use logrus.go

    /** libcore's log; a JVM unit test has not its native library and prints the line (the parsers log as they go). */
    private fun printLine(line: String) {
        try {
            Libcore.nekoLogPrintln(line)
        } catch (e: LinkageError) {
            println(line)
        }
    }

    fun d(message: String) {
        printLine("[Debug] [${mkTag()}] $message")
    }

    fun d(message: String, exception: Throwable) {
        printLine("[Debug] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun i(message: String) {
        printLine("[Info] [${mkTag()}] $message")
    }

    fun i(message: String, exception: Throwable) {
        printLine("[Info] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(message: String) {
        io.niko.utils.Stats.warnings.incrementAndGet()
        printLine("[Warning] [${mkTag()}] $message")
    }

    fun w(message: String, exception: Throwable) {
        io.niko.utils.Stats.warnings.incrementAndGet()
        printLine("[Warning] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun w(exception: Throwable) {
        io.niko.utils.Stats.warnings.incrementAndGet()
        printLine("[Warning] [${mkTag()}] " + exception.stackTraceToString())
    }

    fun e(message: String) {
        io.niko.utils.Stats.errors.incrementAndGet()
        printLine("[Error] [${mkTag()}] $message")
    }

    fun e(message: String, exception: Throwable) {
        io.niko.utils.Stats.errors.incrementAndGet()
        printLine("[Error] [${mkTag()}] $message" + "\n" + exception.stackTraceToString())
    }

    fun e(exception: Throwable) {
        io.niko.utils.Stats.errors.incrementAndGet()
        printLine("[Error] [${mkTag()}] " + exception.stackTraceToString())
    }

}

fun InputStream.use(out: OutputStream) {
    use { input ->
        out.use { output ->
            input.copyTo(output)
        }
    }
}