package io.niko.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Variant В draws what variant А draws: InboundSnapshotTest's goldens of the two are the same file, byte for byte,
 * in both themes (Paparazzi writes the same pixels to the same PNG; docs/research/list-screens-probe.md). Without
 * goldens (a run that records them) there is nothing to hold yet.
 */
class InboundVariantsTest {

    private val images = File("src/test/snapshots/images")

    private fun golden(test: String, mode: String) = File(images, "io.niko.ui.settings_InboundSnapshotTest_$test[$mode].png")

    @Test
    fun bothWaysDrawTheSame() {
        for (mode in listOf("light", "dark")) {
            val hand = golden("hand", mode)
            val spec = golden("spec", mode)
            assumeTrue("no goldens yet", hand.exists() && spec.exists())
            assertTrue("$mode: the description draws other pixels than the hand-made screen", hand.readBytes().contentEquals(spec.readBytes()))
        }
    }
}
