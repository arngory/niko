package io.niko.raycontrol

import com.google.gson.JsonObject

/**
 * What a test run (RayTestRun) found, read from its steps: which profiles work on this network and, for each that does
 * not, the likely cause by how it failed; then what holds for the network or the node (the clock, the node's port,
 * UDP, the DNS tunnel's resolver, a relay that gets through where the direct path does not). Pure: the screen words it.
 */
object RayTestFindings {

    /** how a profile's traffic reaches the node: over TCP, over UDP (QUIC, WireGuard), or through DNS resolvers */
    enum class Carrier { TCP, UDP, DNS }

    enum class Cause {
        /** the download stopped after the first kilobytes */
        STALLS,

        /** no answer in time */
        TIMEOUT,

        /** cut during the handshake or right after it (a reset, an EOF) */
        RESET,

        /** the node refused the connection */
        REFUSED,

        /** a TLS or REALITY check failed */
        TLS,

        /** the node's name did not resolve */
        NAME,

        /** no route to the node */
        UNREACHABLE,

        /** the node's one probe slot was taken by another check: not a property of the network */
        BUSY,

        /** the node answered the probe with an HTTP error */
        HTTP,

        /** the running proxy uses it: not measured */
        IN_USE,

        OTHER,
    }

    data class Profile(
        val key: String,
        val name: String,
        val type: String,
        val carrier: Carrier,
        val relay: String?,
        val works: Boolean,
        val kbps: Long = 0,
        val firstByteMs: Long = 0,
        val cause: Cause? = null,
        val error: String? = null,
        val stalledAtKb: Long = -1,
        /** null: UDP was not measured through it */
        val udpOk: Boolean? = null,
        val udpLossPct: Double = 0.0,
    )

    /** what holds for the network or the node, apart from each profile */
    enum class Note {
        /** the run had no network */
        NO_NETWORK,

        /** the device's clock differs from the node's by [Finding.number] s */
        CLOCK,

        /** the subscription did not come ([Finding.detail]): the profiles may be old */
        NO_SUBSCRIPTION,

        /** the node did not answer directly, on any carrier of its knock ([Finding.detail]: the measurement's error) */
        NO_DIRECT,

        /** no UDP came back from the node on the direct path */
        UDP_BLOCKED,

        /** the direct path's UDP passes datagrams of at most [Finding.number] bytes, under QUIC's 1200 */
        UDP_SMALL,

        /** no resolver here passes the node's dnstt tunnel */
        DNS_NONE,

        /** the main zone of the node's dnstt tunnel is cut here, its spare zone [Finding.number] passes (dnszones-v1) */
        DNS_SPARE,

        /** the relay's profiles work, the direct ones do not */
        RELAY_ONLY,

        /** only the DNS tunnels work */
        DNS_ONLY,
    }

    data class Finding(val note: Note, val number: Long = 0, val detail: String? = null)

    data class Result(val profiles: List<Profile>, val notes: List<Finding>) {
        /** the profiles that work, the fastest first */
        val working get() = profiles.filter { it.works }.sortedByDescending { it.kbps }

        /** the profiles measured that do not work */
        val failing get() = profiles.filter { !it.works && it.cause != Cause.IN_USE }

        /** the failing profiles of a DNS tunnel: whatever their error, the path through the resolvers failed */
        val dnsFailing get() = failing.filter { it.carrier == Carrier.DNS }

        /** the other failing profiles by their cause, the most common first */
        val byCause: List<Pair<Cause, List<Profile>>>
            get() = failing.filter { it.carrier != Carrier.DNS }.groupBy { it.cause ?: Cause.OTHER }.toList()
                .sortedByDescending { it.second.size }

        /** the profiles that work over TCP but carry no UDP */
        val noUdp get() = working.filter { it.udpOk == false }

        fun has(note: Note) = notes.any { it.note == note }
    }

    /** QUIC's least datagram (RFC 9000 §14) */
    private const val QUIC_MIN_DATAGRAM = 1200

    /** what the summary takes as a clock off (RaySubscriptionResult.clockWrong): the knock's window */
    private const val CLOCK_SKEW_S = 30

    /** The run's steps, as RayTestRun writes them, read. */
    fun of(steps: List<JsonObject>): Result {
        val notes = mutableListOf<Finding>()
        val profiles = mutableListOf<Profile>()
        val started = mutableMapOf<String, JsonObject>()
        for (step in steps) {
            when (step.string("step")) {
                "start" -> if (step.string("net") == "none") notes += Finding(Note.NO_NETWORK)
                "end" -> if (step.string("error") == "no network" && !notes.any { it.note == Note.NO_NETWORK }) {
                    notes += Finding(Note.NO_NETWORK)
                }

                "direct" -> {
                    if (step.string("spa_carrier") == null) {
                        notes += Finding(Note.NO_DIRECT, detail = step.string("error"))
                    } else if (step.bool("udp_probed") == true && step.has("udp_mtu")) {
                        val mtu = step.long("udp_mtu") ?: 0
                        if (mtu <= 0) notes += Finding(Note.UDP_BLOCKED)
                        else if (mtu < QUIC_MIN_DATAGRAM) notes += Finding(Note.UDP_SMALL, number = mtu)
                    }
                }

                "subscription" -> {
                    step.string("error")?.let { notes += Finding(Note.NO_SUBSCRIPTION, detail = it) }
                    val skew = step.long("clock_skew_s") ?: 0
                    if (kotlin.math.abs(skew) > CLOCK_SKEW_S) notes += Finding(Note.CLOCK, number = skew)
                }

                "dns_pick" -> when {
                    step.string("kind") == null -> notes += Finding(Note.DNS_NONE)
                    (step.long("zone") ?: 0) > 0 -> notes += Finding(Note.DNS_SPARE, number = step.long("zone") ?: 0)
                }
                "profile_start" -> step.string("key")?.let { started[it] = step }
                "profile" -> {
                    val key = step.string("key") ?: continue
                    profiles += profile(key, started[key], step)
                }
            }
        }
        val measured = profiles.filter { it.cause != Cause.IN_USE }
        val direct = measured.filter { it.relay == null && it.carrier != Carrier.DNS }
        if (direct.isNotEmpty() && direct.none { it.works }) {
            if (measured.any { it.works && it.relay != null }) notes += Finding(Note.RELAY_ONLY)
            else if (measured.any { it.works && it.carrier == Carrier.DNS }) notes += Finding(Note.DNS_ONLY)
        }
        return Result(profiles, notes)
    }

    /**
     * The findings as one JSON line for the run's file, by the profiles' keys (their names may hold the node's): what
     * the node's admin reads next to the steps, as the user read it.
     */
    fun summary(id: String, result: Result): JsonObject = JsonObject().apply {
        fun keys(profiles: List<Profile>) = com.google.gson.JsonArray().apply { profiles.forEach { add(it.key) } }
        addProperty("run", id)
        addProperty("step", "findings")
        add("working", keys(result.working))
        add("failing", JsonObject().apply { result.byCause.forEach { (cause, profiles) -> add(cause.name.lowercase(), keys(profiles)) } })
        if (result.dnsFailing.isNotEmpty()) add("dns_failing", keys(result.dnsFailing))
        if (result.noUdp.isNotEmpty()) add("no_udp", keys(result.noUdp))
        add("notes", com.google.gson.JsonArray().apply {
            result.notes.forEach { finding ->
                add(JsonObject().apply {
                    addProperty("note", finding.note.name.lowercase())
                    if (finding.number != 0L) addProperty("number", finding.number)
                })
            }
        })
    }

    private fun profile(key: String, start: JsonObject?, step: JsonObject): Profile {
        val name = start?.string("name") ?: key
        val type = start?.string("type").orEmpty()
        val carrier = when (start?.string("carrier")) {
            "dns" -> Carrier.DNS
            "udp" -> Carrier.UDP
            else -> Carrier.TCP
        }
        val relay = start?.string("relay")
        step.string("error")?.let { error ->
            // the run's own: the running proxy holds the profile's key
            return Profile(key, name, type, carrier, relay, works = false, cause = Cause.IN_USE, error = error)
        }
        val works = step.bool("tcp_ok") == true
        val stalledAt = step.long("stalled_at") ?: -1
        val error = step.string("tcp_error")
        return Profile(
            key, name, type, carrier, relay,
            works = works,
            kbps = step.long("kbps") ?: 0,
            firstByteMs = step.long("first_byte_ms") ?: 0,
            cause = if (works) null else if (stalledAt >= 0) Cause.STALLS else classify(error),
            error = error,
            stalledAtKb = if (stalledAt >= 0) stalledAt / 1024 else -1,
            udpOk = step.bool("udp_ok"),
            udpLossPct = step.double("udp_loss_pct") ?: 0.0,
        )
    }

    // resp.Status: the code and its reason ("503 Service Unavailable"), not an address that begins the error
    private val HTTP_STATUS = Regex("""^\d{3} [a-z]""")

    /**
     * The likely cause of a failed TCP probe by its error, as Go words it through the core (the dial, the TLS of the
     * probe inside the tunnel, the read): the more telling words first, a name's lookup before the timeout it ended in.
     */
    fun classify(error: String?): Cause {
        val e = error?.lowercase()?.trim() ?: return Cause.OTHER
        return when {
            e == "stalled" -> Cause.STALLS
            HTTP_STATUS.containsMatchIn(e) -> if (e.startsWith("429")) Cause.BUSY else Cause.HTTP
            "connection refused" in e -> Cause.REFUSED
            "no such host" in e || "lookup " in e -> Cause.NAME
            "network is unreachable" in e || "no route to host" in e || "host is unreachable" in e -> Cause.UNREACHABLE
            "connection reset" in e || "reset by peer" in e || "broken pipe" in e || e.endsWith("eof") ||
                    "connection closed" in e || "forcibly closed" in e -> Cause.RESET
            "timeout" in e || "deadline exceeded" in e || "timed out" in e -> Cause.TIMEOUT
            "x509" in e || "certificate" in e || "tls:" in e || "reality" in e -> Cause.TLS
            else -> Cause.OTHER
        }
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

    private fun JsonObject.long(name: String): Long? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun JsonObject.double(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonObject.bool(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}
