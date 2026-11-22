package io.niko.ui.log

/**
 * A log line's colour, as the desktop's log has it (log_screen.dart): an error, a fatal one or a panic in danger, a
 * debug or trace line ink-2, the rest ink. The core's lines carry their level as "ERROR[0012]", libcore's as " [Error]".
 */
enum class LogTone {
    Danger, Quiet, Plain;

    companion object {
        private val danger = listOf("ERROR[", "FATAL[", "PANIC[", " [Error]", " [Fatal]", " [Panic]")
        private val quiet = listOf("DEBUG[", "TRACE[", " [Debug]", " [Trace]")

        fun of(line: String): LogTone = when {
            danger.any { line.contains(it) } -> Danger
            quiet.any { line.contains(it) } -> Quiet
            else -> Plain
        }
    }
}

/**
 * One line of the log as P-Log draws it: [time] the clock alone (ink-2), [text] what follows it. The core writes
 * "+0300 2026-11-20 10:00:00 INFO[0001] …", libcore Go's "2026/11/20 10:00:00 [Info] …"; the export keeps them whole.
 */
class LogLine(val time: String?, val text: String, val tone: LogTone) {
    companion object {
        private val core = Regex("""^[+-]\d{4} \d{4}-\d{2}-\d{2} (\d{2}:\d{2}:\d{2}) (.*)$""")
        private val go = Regex("""^\d{4}/\d{2}/\d{2} (\d{2}:\d{2}:\d{2}) (.*)$""")

        fun of(line: String): LogLine {
            val tone = LogTone.of(line)
            val m = core.find(line) ?: go.find(line) ?: return LogLine(null, line, tone)
            return LogLine(m.groupValues[1], m.groupValues[2], tone)
        }
    }
}

/** P-Log's «Все / Ошибки»: every line, or those in danger. */
enum class LogFilter { All, Errors }

/** What P-Log shows: the log's last part, a line each, and [crashes], the core's crash outputs kept (D-LOG-02). */
class LogState(val lines: List<LogLine>, val crashes: Int) {
    fun shown(filter: LogFilter) = if (filter == LogFilter.Errors) lines.filter { it.tone == LogTone.Danger } else lines

    companion object {
        fun read(log: ByteArray, crashes: Int) =
            LogState(String(log).lineSequence().filter { it.isNotBlank() }.map(LogLine::of).toList(), crashes)
    }
}
