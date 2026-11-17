package io.niko.fmt

import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.tuic.TuicBean
import io.niko.fmt.v2ray.VMessBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // the options go to the outbounds that open a QUIC connection of their own
    @Test
    fun appliesToQuicOutbounds() {
        assertTrue(udpDesyncApplies("hysteria2", HysteriaBean().apply { initializeDefaultValues() }, false))
        assertTrue(udpDesyncApplies("tuic", TuicBean().apply { initializeDefaultValues() }, false))
        val naive = NaiveBean().apply { initializeDefaultValues() }
        // naive over HTTP/2: TCP alone
        naive.proto = "https"
        assertFalse(udpDesyncApplies("naive", naive, false))
        // ... unless its UDP goes as HTTP/3 datagrams: a second, QUIC connection
        assertTrue(udpDesyncApplies("naive", naive, true))
        naive.proto = "quic"
        assertTrue(udpDesyncApplies("naive", naive, false))
        // a TCP protocol never, whatever is asked of it
        assertFalse(udpDesyncApplies("vless", VMessBean().apply { initializeDefaultValues() }, true))
    }
}
