package io.niko.fmt

import com.esotericsoftware.kryo.io.ByteBufferOutput
import io.niko.fmt.socks.SOCKSBean
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

class KryoBoundsTest {

    /** A few bytes that declare a string of ~2^31 chars: refused, not an OutOfMemoryError that kills the app. */
    @Test
    fun hugeDeclaredString() {
        val out = ByteArrayOutputStream()
        ByteBufferOutput(out).apply {
            writeInt(0) // version
            writeVarIntFlag(true, Int.MAX_VALUE, true) // a UTF-8 string of Int.MAX_VALUE - 1 chars
            flush()
        }
        try {
            KryoConverters.deserialize(SOCKSBean(), out.toByteArray())
            fail("accepted")
        } catch (e: IllegalArgumentException) {
        }
    }
}
