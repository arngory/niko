package io.niko.ui

import io.niko.fmt.socks.SOCKSBean
import io.niko.proxy.config.ConfigBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun eachBatchIsAnsweredOnceAndAlone() {
        val a = SOCKSBean().apply { initializeDefaultValues(); name = "a" }
        val b = SOCKSBean().apply { initializeDefaultValues(); name = "b" }
        val first = ImportReview.hold(listOf(a))
        val second = ImportReview.hold(listOf(b))
        // a second scan under the first one's question: each question has its own profiles
        assertEquals(listOf(a), ImportReview.peek(first))
        assertEquals(listOf(b), ImportReview.peek(second))
        // the first answer imports, a second one for the same batch does not
        assertTrue(ImportReview.drop(first))
        assertFalse(ImportReview.drop(first))
        assertEquals(emptyList<Any>(), ImportReview.peek(first))
        assertEquals(listOf(b), ImportReview.peek(second))
        assertTrue(ImportReview.drop(second))
    }
}
