package io.niko.ui

import io.niko.fmt.socks.SOCKSBean
import io.niko.proxy.config.ConfigBean
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportReviewTest {

    @Test
    fun keysOverTheWholeConfig() {
        val custom = SOCKSBean().apply {
            initializeDefaultValues()
            customConfigJson = """{"inbounds+":[{"type":"mixed","listen":"0.0.0.0"}],"experimental":{"clash_api":{}}}"""
        }
        assertEquals(listOf("experimental", "inbounds"), ImportReview.keys(custom))

        val full = ConfigBean().apply {
            initializeDefaultValues()
            type = 0
            config = """{"route":{"final":"direct"},"outbounds":[]}"""
        }
        assertEquals(listOf("outbounds", "route"), ImportReview.keys(full))

        val outbound = ConfigBean().apply {
            initializeDefaultValues()
            type = 1
            config = """{"type":"direct"}"""
        }
        assertEquals(emptyList<String>(), ImportReview.keys(outbound))
        assertEquals(listOf("?"), ImportReview.keys(SOCKSBean().apply { initializeDefaultValues(); customConfigJson = "not json" }))
    }
}
