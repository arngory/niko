package io.niko.raycontrol

import kotlin.random.Random

/**
 * Spread in time: what many devices of one node (behind one NAT, after one node restart or network change) would do
 * at one moment, each does at its own. A node limits new connections per address (conn-limit-v1): retries in lockstep
 * are what it slows down.
 */
object Jitter {
    /** [base] seconds (or ms) times a random 0.5…1.5: a backoff's step, never in step with another device's. */
    fun around(base: Long, random: Random = Random.Default): Long =
        if (base <= 1) base else base / 2 + random.nextLong(base + 1)

    /** A random 0…[max]: a delay added to a moment every device shares (a rotation's `from`, a day's start). */
    fun upTo(max: Long, random: Random = Random.Default): Long = if (max <= 0) 0 else random.nextLong(max + 1)
}
