package io.niko.fmt

import io.niko.fmt.hysteria.HysteriaBean
import org.junit.Assert.assertEquals
import org.junit.Test

class UdpDesyncTest {
    @Test
    fun beanRoundTrip() {
        val bean = HysteriaBean().apply {
            initializeDefaultValues()
            udpPrelude = 3
            udpPreludeSni = "allowed.example.ru"
            udpPreludeTtl = 5
            udpReorderInitial = true
        }
        val copy = KryoConverters.deserialize(HysteriaBean(), KryoConverters.serialize(bean))
        assertEquals(3, copy.udpPrelude)
        assertEquals("allowed.example.ru", copy.udpPreludeSni)
        assertEquals(5, copy.udpPreludeTtl)
        assertEquals(true, copy.udpReorderInitial)
        // a profile without them reads the defaults
        val plain = KryoConverters.deserialize(HysteriaBean(), KryoConverters.serialize(HysteriaBean().apply { initializeDefaultValues() }))
        assertEquals(0, plain.udpPrelude)
        assertEquals(false, plain.udpReorderInitial)
    }
}
