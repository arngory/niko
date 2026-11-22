package io.niko.fmt

import com.google.gson.JsonParser

/**
 * «Обход DPI для прямых соединений» (docs/design/dpi.md §5.2): the core's `tcp_desync` (sing-box fork,
 * common/tcpdesync) on the direct rules that have their «Обход DPI» flag. The setting is off, a ready-made strategy
 * ([presets]) or the user's own in ByeDPI's syntax ([parseByeDpi]), a subset: the cuts the core makes without fakes.
 * Pure: libcore/nikoconf's dpi.go is its twin, both held by shared/golden/dpi/byedpi.json.
 */
object DpiDirect {

    /** The setting's values (Key.DPI_DIRECT, ConfigSettings.dpiDirect). */
    const val OFF = 0
    const val PRESET = 1
    const val CUSTOM = 2

    /** The most cuts a strategy has, segments and records together: ByeDPI's repeats could make thousands. */
    const val MAX_CUTS = 64

    /** The core's marker offsets are within this, either way. */
    private const val MAX_OFFSET = 65535

    const val SPLIT = "split"
    const val DISORDER = "disorder"
    const val OOB = "oob"
    const val DISOOB = "disoob"

    /** A segment cut: at a core's marker ("1", "-1", "sni+1", "midsld"), the part that ends there sent by [mode]. */
    data class Cut(val at: String, val mode: String)

    /** A `tcp_desync` strategy: segment cuts, TLS record cuts and the out-of-band byte (null: the core's "a"). */
    data class Strategy(val split: List<Cut> = emptyList(), val tlsrec: List<String> = emptyList(), val oobByte: String? = null) {
        /** A part goes with TTL 1 (disorder, disoob): needs IP_TTL on a TCP socket. */
        val needsTtl get() = split.any { it.mode == DISORDER || it.mode == DISOOB }

        /** A part is followed by an MSG_OOB byte (oob, disoob). */
        val needsOob get() = split.any { it.mode == OOB || it.mode == DISOOB }

        /** As the route rule's `tcp_desync` has it. */
        fun toConfig(): Map<String, Any> = linkedMapOf<String, Any>().apply {
            if (split.isNotEmpty()) put("split", split.map { linkedMapOf("at" to it.at, "mode" to it.mode) })
            if (tlsrec.isNotEmpty()) put("tlsrec", tlsrec)
            oobByte?.let { put("oob_byte", it) }
        }
    }

    /** A ready-made strategy: none needs a fake or a TTL found by a probe (design §7, 1.67). */
    class Preset(val id: String, val strategy: Strategy)

    const val DEFAULT_PRESET = "tlsrec_sni"

    val presets = listOf(
        // the record header in the server name: the cheapest, the old «Записи» (tls_record_fragment)
        Preset(DEFAULT_PRESET, Strategy(tlsrec = listOf("sni+1"))),
        // zapret's tpws default (--split-pos=1,midsld): the first byte alone, then the second-level label cut in half;
        // the old «TCP» (tls_fragment) cut within the server name too
        Preset("split_sni", Strategy(split = listOf(Cut("1", SPLIT), Cut("midsld", SPLIT)))),
        // ByeDPI's --disoob in the server name: the part up to the middle of the label with TTL 1 and an OOB byte
        Preset("disoob_sld", Strategy(split = listOf(Cut("midsld", DISOOB)))),
        // ByeDPI's advice for Linux: --disorder 1
        Preset("disorder_1", Strategy(split = listOf(Cut("1", DISORDER)))),
    )

    fun preset(id: String) = presets.firstOrNull { it.id == id }

    /** The old «Фрагментация TLS для прямых правил» ("0", "1" records, "2" TCP) as this setting and its preset. */
    fun migrated(old: String): Pair<Int, String> = when (old.trim()) {
        "1" -> PRESET to DEFAULT_PRESET
        "2" -> PRESET to "split_sni"
        else -> OFF to DEFAULT_PRESET
    }

    /** The strategy of the setting; null: off, an unknown preset, an own string that does not parse. */
    fun strategy(mode: Int, preset: String, custom: String): Strategy? = when (mode) {
        PRESET -> preset(preset)?.strategy
        CUSTOM -> (parseByeDpi(custom) as? Parsed.Ok)?.strategy
        else -> null
    }

    /** What the platform lets the core do (tcpdesync.Capabilities): TTL for disorder and disoob, OOB for oob and disoob. */
    data class Capabilities(val ttl: Boolean, val oob: Boolean) {
        /** What [strategy] needs that is not here: "TTL", "OOB". */
        fun lacks(strategy: Strategy) = listOfNotNull("TTL".takeIf { strategy.needsTtl && !ttl }, "OOB".takeIf { strategy.needsOob && !oob })
    }

    /** libcore's TcpDesyncCapabilities JSON; null where the probe did not run (its "error"), and nothing is hidden. */
    fun capabilities(json: String): Capabilities? = try {
        val o = JsonParser.parseString(json).asJsonObject
        if (o.get("error")?.asString.isNullOrEmpty()) {
            Capabilities(o.get("ttl")?.asBoolean == true, o.get("oob")?.asBoolean == true)
        } else null
    } catch (_: Exception) {
        null
    }

    /** A strategy in short, as the core's log has it: "split 1, disoob midsld, tlsrec sni+1". */
    fun describe(strategy: Strategy) =
        (strategy.split.map { "${it.mode} ${it.at}" } + strategy.tlsrec.map { "tlsrec $it" }).joinToString(", ")

    /** Why a ByeDPI string is not taken; [flag] the flag as typed, [value] what was wrong. */
    enum class ErrorKind(val code: String) {
        EMPTY("empty"),
        /** a zapret nfqws string (--dpi-desync=…): raw packets, not ours */
        NFQWS("nfqws"),
        /** a fake's flag (-f, -t, -n, -Q, -O, -l, -S, --fake…): 1.68 */
        FAKE("fake"),
        /** the automatic mode's (-A, -L, -T, -u, -y): 1.68 */
        AUTO("auto"),
        /** any other flag of ByeDPI's, or one it does not have */
        UNSUPPORTED("unsupported"),
        NO_VALUE("no_value"),
        BAD_POSITION("bad_position"),
        /** -e takes one printable ASCII character */
        BAD_OOB_DATA("bad_oob_data"),
        /** a word that is not a flag */
        UNEXPECTED("unexpected"),
        /** no cut: only -e, or every cut past the end */
        NOTHING("nothing"),
        TOO_MANY("too_many"),
    }

    sealed class Parsed {
        data class Ok(val strategy: Strategy) : Parsed()
        data class Error(val kind: ErrorKind, val flag: String = "", val value: String = "") : Parsed()
    }

    private val longNames = mapOf(
        "split" to 's', "disorder" to 'd', "oob" to 'o', "disoob" to 'q', "tlsrec" to 'r', "oob-data" to 'e',
        "fake" to 'f', "ttl" to 't', "fake-sni" to 'n', "fake-tls-mod" to 'Q', "fake-offset" to 'O', "fake-data" to 'l',
        "md5sig" to 'S', "auto" to 'A', "auto-mode" to 'L', "timeout" to 'T', "cache-ttl" to 'u', "cache-dump" to 'y',
    )
    private const val FAKE_FLAGS = "ftnQOlS"
    private const val AUTO_FLAGS = "ALTuy"
    private val modes = mapOf('s' to SPLIT, 'd' to DISORDER, 'o' to OOB, 'q' to DISOOB)

    /** nfqws' and tpws' own words, which ByeDPI has none of */
    private fun isZapret(token: String) = token == "nfqws" || token == "tpws" || token == "--new" ||
        listOf("--dpi-desync", "--wf-", "--filter-", "--hostlist", "--qnum", "--split-pos", "--orig-", "--dup").any(token::startsWith)

    /**
     * A ByeDPI command line, its subset (docs/design/dpi.md §5.2): `-s` (split), `-d` (disorder), `-o` (oob), `-q`
     * (disoob), `-r` (tlsrec) with ByeDPI's positions `offset[:repeats[:skip]][+flags]` and `-e` (the OOB byte), as
     * `-s 1`, `-s1`, `--split 1` or `--split=1`; a leading `ciadpi` is skipped. A position's flags: `+s`/`+h` from the
     * host name's start (core: sni/host), `e` its end (endhost), `m` its middle (midhost), `+n` none. A `-r` offset
     * without flags counts in the record's payload, as ByeDPI's does: `-r 1` cuts at byte 6. The rest of ByeDPI's flags
     * are refused with their name; a zapret nfqws string as a whole.
     */
    fun parseByeDpi(text: String): Parsed {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.firstOrNull()?.substringAfterLast('/') in setOf("ciadpi", "byedpi")) tokens.removeAt(0)
        if (tokens.isEmpty()) return Parsed.Error(ErrorKind.EMPTY)
        if (tokens.any(::isZapret)) return Parsed.Error(ErrorKind.NFQWS)
        val split = mutableListOf<Cut>()
        val tlsrec = mutableListOf<String>()
        var oobByte: String? = null
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i++]
            val flag: String
            val option: Char
            var value: String?
            if (token.startsWith("--") && token.length > 2) {
                val eq = token.indexOf('=')
                flag = if (eq >= 0) token.substring(0, eq) else token
                value = if (eq >= 0) token.substring(eq + 1) else null
                option = longNames[flag.substring(2)]
                    ?: return Parsed.Error(if (flag.startsWith("--fake")) ErrorKind.FAKE else ErrorKind.UNSUPPORTED, flag)
            } else if (token.length >= 2 && token[0] == '-' && token[1].isLetter()) {
                flag = token.substring(0, 2)
                option = token[1]
                value = token.substring(2).ifEmpty { null }
            } else {
                return Parsed.Error(ErrorKind.UNEXPECTED, value = token)
            }
            when (option) {
                in FAKE_FLAGS -> return Parsed.Error(ErrorKind.FAKE, flag)
                in AUTO_FLAGS -> return Parsed.Error(ErrorKind.AUTO, flag)
                's', 'd', 'o', 'q', 'r', 'e' -> {}
                else -> return Parsed.Error(ErrorKind.UNSUPPORTED, flag)
            }
            // getopt takes the next word as the value whatever it is, "-1" too
            if (value == null) value = tokens.getOrNull(i++) ?: return Parsed.Error(ErrorKind.NO_VALUE, flag)
            when (option) {
                'e' -> {
                    if (value.length != 1 || value[0] !in '!'..'~') return Parsed.Error(ErrorKind.BAD_OOB_DATA, flag, value)
                    oobByte = value
                }

                'r' -> tlsrec += positions(value, record = true) ?: return Parsed.Error(ErrorKind.BAD_POSITION, flag, value)
                else -> {
                    val at = positions(value, record = false) ?: return Parsed.Error(ErrorKind.BAD_POSITION, flag, value)
                    at.mapTo(split) { Cut(it, modes.getValue(option)) }
                }
            }
            if (split.size + tlsrec.size > MAX_CUTS) return Parsed.Error(ErrorKind.TOO_MANY, flag, value)
        }
        if (split.isEmpty() && tlsrec.isEmpty()) return Parsed.Error(ErrorKind.NOTHING)
        return Parsed.Ok(Strategy(split, tlsrec, oobByte))
    }

    private val position = Regex("""(-?\d+)(?::(\d+)(?::(\d+))?)?(?:\+([shn])([emrs])?)?""")

    /** A decimal number as strtol takes it, but no octal ("010") and no hex: those are refused, not misread. */
    private fun number(text: String?): Int? {
        if (text == null) return null
        val digits = text.removePrefix("-")
        if (digits.length > 1 && digits.startsWith("0")) return null
        return text.toIntOrNull()
    }

    /**
     * ByeDPI's position as the core's markers, one for each repeat (`1:3:5` is 1, 6, 11); null where it is not one of
     * ours. [record]: a `-r` position, whose plain offset ≥ 0 counts after the record's header.
     */
    private fun positions(text: String, record: Boolean): List<String>? {
        val m = position.matchEntire(text) ?: return null
        val (offsetText, repeatsText, skipText, baseText, endText) = m.destructured
        val offset = number(offsetText) ?: return null
        val repeats = if (repeatsText.isEmpty()) 1 else number(repeatsText) ?: return null
        val skip = if (skipText.isEmpty()) 0 else number(skipText) ?: return null
        if (repeats < 1 || repeats > MAX_CUTS) return null
        // ByeDPI's flags: s and h the host name's (s TLS only, the core's host is HTTP's Host too), n none; then e its
        // end, m its middle, s the "start" flag, which matters only past the first request; r (random) is not ours
        var base = when (baseText) {
            "s" -> "sni"
            "h" -> "host"
            else -> null
        }
        when (endText) {
            "e" -> if (base != null) base = "endhost" else if (offset >= 0) return null
            "m", "r" -> if (base != null && endText == "m") base = "midhost" else return null
        }
        val flagged = baseText == "s" || baseText == "h" || endText.isNotEmpty()
        // a record's plain offset ≥ 0 is the payload's (ByeDPI's part_tls); the core counts the whole write
        val shift = if (record && !flagged && offset >= 0) 5 else 0
        return (0 until repeats).mapNotNull { k ->
            val at = offset + shift + skip * k
            when {
                at > MAX_OFFSET || at < -MAX_OFFSET -> return null
                // from the end, repeated past it: ByeDPI's cut falls at the end, which is none
                base == null && offset < 0 && at >= 0 -> null
                base == null -> at.toString()
                at > 0 -> "$base+$at"
                at < 0 -> "$base$at"
                else -> base
            }
        }
    }
}
