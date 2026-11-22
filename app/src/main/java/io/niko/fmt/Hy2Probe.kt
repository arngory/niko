package io.niko.fmt

/**
 * Hysteria2 probes (a debug setting): one change at a time against a profile as it is, so that the A/B runs raycontrol
 * asked for (2026-11-22, "the internet stands still" on hy2 after its first packets) tell which one matters. Each
 * probe holds for every Hysteria2 outbound of the core's config, the test's included; an export has none.
 */
object Hy2Probe {
    const val OFF = "0"

    /** a node's row to its service port (443) as the knock opens it, not the rotating slot port (spa-v2.md §11) */
    const val PORT_443 = "443"

    /** no rate declared either way: BBR, not Brutal */
    const val BBR = "bbr"

    /** 10 Mbit/s declared each way: Brutal at a rate any phone's network carries */
    const val RATE_10 = "10"

    /** QUIC packets of 1200 bytes, the least QUIC allows, path MTU discovery off */
    const val PACKET_1200 = "1200"

    /** the handshake without the Chrome parrot: quic-go's own Initial, as the original Hysteria 2 client sends it */
    const val NO_PARROT = "noparrot"

    /** Whether the rotating slot port stays off a node's row. */
    fun keepsServicePort(probe: String) = probe == PORT_443

    /** The outbound's raw options for [probe], with the connection log when [log]; these override the profile's own. */
    fun options(probe: String, log: Boolean): Map<String, Any> = buildMap {
        when (probe) {
            // 0 is no rate: omitempty in the core
            BBR -> {
                put("up_mbps", 0)
                put("down_mbps", 0)
            }

            RATE_10 -> {
                put("up_mbps", 10)
                put("down_mbps", 10)
            }

            PACKET_1200 -> {
                put("initial_packet_size", 1200)
                put("disable_path_mtu_discovery", true)
            }

            NO_PARROT -> put("disable_chrome_parrot", true)
        }
        if (log) {
            put("log_connections", true)
            put("brutal_debug", true)
        }
    }
}
