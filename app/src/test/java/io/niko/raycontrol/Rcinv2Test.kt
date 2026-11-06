package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The short invite RCINV2: (api-v1.md §3a): the spec's vectors (raycontrol TestInvite2Vectors, computed in Python). */
class Rcinv2Test {

    private val v4 = "RCINV2:AIA4WADRA4AACAQDAQCQMBYIBEFAWDANBYHQAAEGJQAAABSUEAQSEIZEEUTCOKBJFIVSYLJOF4YDCMRTGQ2TMNZYHE5DWPB5HY7VLEYLRMAIQAGOUCQ2FI5EUWTKOQCBIJBUIRKGI5EESSSLJRGU4T2QKFJFGVCVKZLVQWK2LNOF2XS7AAD3YVLAMFRGGZDFMZTWQ2LKNNWG23TPOBYXE43UOV3HO6DZPJ5XY7L6P5UVOCUA"
    private val v6 = "RCINV2:AIBCAAINXAAAAAAAAAAAAAAAAAAAOAABAIBQIBIGA4EASCQLBQGQ4DYAACDEYAAAAZKCAIJCEMSCKJRHFAUSUKZMFUXC6MBRGIZTINJWG44DSOR3HQ6T4P2VSMFYWAEIADHKBINCUOSKLJVHIBAUEQ2EIVDEOSCJJJFUYTKOJ5IFCUSTKRKVMV2YLFNFWXC5LZPQAB54KVQGCYTDMRSWMZ3INFVGW3DNNZXXA4LSON2HK5TXPB4XU634PV7H62KXBKAA"
    private val name = "RCINV2:AICBA3TPMRSS4ZLYMFWXA3DFFZRW63IAAEBAGBAFAYDQQCIKBMGA2DQPAAAIMTAAAADFIIBBEIRSIJJGE4UCSKRLFQWS4LZQGEZDGNBVGY3TQOJ2HM6D2PR7KWJQXCYARAAM5IFBUKR2JJNGU5AECQSDIRCUMR2IJFFEWTCNJZHVAUKSKNKFKVSXLBMVUW24LVPF6AAHXRKWAYLCMNSGKZTHNBUWU23MNVXG64DROJZXI5LWO54HS6T3PR6X473JK4FIA"

    private fun bytes(from: Int, n: Int) = (from until from + n).joinToString("") { "%02x".format(it) }

    @Test
    fun theSpecsVectors() {
        for ((text, host, pin) in listOf(Triple(v4, "203.0.113.7", true), Triple(v6, "2001:db8::7", false), Triple(name, "node.example.com", false))) {
            val invite = RayImport.parseInvite("Invite:\n$text\n")
            assertEquals(host, invite.host)
            assertEquals(pin, invite.pinRequired)
            assertEquals(RaySpaParams(bytes(0x00, 16), 34380, 1620, bytes(0x20, 32), 21907, 2955, 136, 206), invite.spa)
            assertEquals(bytes(0xa0, 8), invite.inviteId)
            assertEquals(bytes(0x40, 32), invite.inviteKey)
            assertEquals(listOf(RayService(7, "api", "api")), invite.services)
            val apiPin = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (0x60 + it).toByte() })
            assertEquals(RayApiInfo(48213, apiPin), invite.api)
            assertEquals(1767312000L, invite.expires)
        }
        // either case, and a scanned code goes on as its token
        assertEquals("203.0.113.7", RayImport.parseInvite(v4.lowercase()).host)
        assertTrue(RayImport.matches("x $name y") && RayImport.isInvite(name))
        assertEquals(name, RayImport.token("Invite: $name\n"))
    }

    @Test
    fun whatTheReaderRefuses() {
        val raw = v4.substringAfter(':')
        for (bad in listOf(
            "RCINV2:" + raw.dropLast(8), // cut short
            "RCINV2:" + raw + "AAAAAAAA", // bytes after expires
            "RCINV2:AE" + raw.drop(2), // version 1 in the first byte
        )) {
            try {
                Rcinv2.parse(bad)
                fail("accepted $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
