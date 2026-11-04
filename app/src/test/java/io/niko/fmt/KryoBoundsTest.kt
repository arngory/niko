package io.niko.fmt

import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.niko.fmt.socks.SOCKSBean
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

class KryoBoundsTest {

    private fun declaring(chars: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ByteBufferOutput(out).apply {
            writeInt(0) // version
            writeVarIntFlag(true, chars + 1, true) // a UTF-8 string of `chars` chars, none of them sent
            flush()
        }
        return out.toByteArray()
    }

    /** A few bytes that declare a huge string (one too big to allocate, one that would fit): refused, no allocation. */
    @Test
    fun declaredStringLargerThanTheData() {
        for (chars in listOf(Int.MAX_VALUE - 1, 100_000_000, 10)) {
            try {
                KryoConverters.strict { KryoConverters.deserialize(SOCKSBean(), declaring(chars)) }
                fail("$chars chars: accepted")
            } catch (e: IllegalArgumentException) {
            }
        }
    }

    /** Whole data still reads, through the same bounded input. */
    @Test
    fun roundTrip() {
        val bean = SOCKSBean().apply {
            initializeDefaultValues()
            serverAddress = "ünïcödé.example"
            serverPort = 1080
            username = "u"
            password = "p".repeat(1000)
        }
        val back = KryoConverters.strict { KryoConverters.deserialize(SOCKSBean(), KryoConverters.serialize(bean)) }
        assertEquals(bean.serverAddress, back.serverAddress)
        assertEquals(bean.password, back.password)
        assertEquals(1080, back.serverPort)
    }
}
