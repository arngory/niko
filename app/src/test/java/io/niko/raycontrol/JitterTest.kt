package io.niko.raycontrol

import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterTest {
    @Test
    fun spreadWithinBounds() {
        val random = Random(1)
        val around = (1..2000).map { Jitter.around(3600, random) }
        // 0.5…1.5 of the base, and spread over it, not one value
        assertTrue(around.all { it in 1800..5400 })
        assertTrue(around.toSet().size > 1000)
        val upTo = (1..2000).map { Jitter.upTo(900, random) }
        assertTrue(upTo.all { it in 0..900 } && upTo.toSet().size > 500)
        assertTrue(Jitter.upTo(0, random) == 0L && Jitter.around(1, random) == 1L)
    }
}
