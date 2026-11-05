package io.niko.fmt

// spaMasked: SPA v1m, the time masked so no byte of the datagram is fixed (S9)
data class KnockStep(
    val port: Int, val udp: Boolean, val payloadHex: String, val spa: Boolean = false,
    val spaMasked: Boolean = false,
)

private const val MAX_KNOCK_STEPS = 32
private const val MAX_KNOCK_PAYLOAD = 1024

/**
 * Parses a knock sequence like "7000, 8000/udp, 9000/tcp, 1234/udp:68656c6c6f, 62201/spa:<key>".
 * Steps are separated by commas, spaces or new lines; a hex payload is only allowed for UDP.
 * A spa step sends one Niko SPA v1 datagram, its payload is the 32-byte hex key; spa1m the same with the time
 * masked (v1m), for servers that read it.
 */
fun parseKnockSequence(sequence: String): List<KnockStep> {
    val items = sequence.split(',', ' ', '\n', '\t').map { it.trim() }.filter { it.isNotEmpty() }
    require(items.size <= MAX_KNOCK_STEPS) { "too many steps (max $MAX_KNOCK_STEPS)" }
    return items.map { item ->
        val payloadHex = item.substringAfter(':', "")
        val target = item.substringBefore(':')
        val port = target.substringBefore('/').toIntOrNull()
        require(port != null && port in 1..65535) { "invalid port: $item" }
        val network = target.substringAfter('/', "tcp").lowercase()
        if (network == "spa" || network == "spa1m") {
            require(payloadHex.length == 64 && payloadHex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "spa needs a 64 hex digit key: $port/$network"
            }
            return@map KnockStep(port, true, payloadHex.lowercase(), spa = true, spaMasked = network == "spa1m")
        }
        val udp = when (network) {
            "tcp" -> false
            "udp" -> true
            else -> throw IllegalArgumentException("unknown network: $item")
        }
        if (payloadHex.isNotEmpty()) {
            require(udp) { "payload is only supported for udp: $item" }
            require(payloadHex.length % 2 == 0 && payloadHex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "payload must be hex: $item"
            }
            require(payloadHex.length / 2 <= MAX_KNOCK_PAYLOAD) { "payload too large: $item" }
        }
        KnockStep(port, udp, payloadHex.lowercase())
    }
}

fun AbstractBean.hasPortKnocking() = !knockSequence.isNullOrBlank()

/** Options of the libcore "knock" outbound for this profile. */
fun AbstractBean.buildKnockOutboundOptions(): Map<String, Any> {
    val steps = try {
        parseKnockSequence(knockSequence)
    } catch (e: IllegalArgumentException) {
        throw Exception("Port knocking (${displayName()}): ${e.message}")
    }
    return mapOf(
        "sequence" to steps.map {
            buildMap {
                put("port", it.port)
                put("network", if (it.spaMasked) "spa1m" else if (it.spa) "spa" else if (it.udp) "udp" else "tcp")
                if (it.payloadHex.isNotEmpty()) put("payload", it.payloadHex)
            }
        },
        "interval" to "${(knockInterval ?: 200).coerceIn(10, 10_000)}ms",
        "ttl" to "${(knockTtl ?: 10).coerceIn(1, 3600)}s",
        "silence" to "${(knockSilence ?: 15).coerceIn(0, 3600)}s",
    )
}
