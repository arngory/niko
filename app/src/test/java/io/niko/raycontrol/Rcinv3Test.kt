package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The invite by name RCINV3: (api-v1.md §3b, raycontrol v0.35.0): the spec's vectors (raycontrol TestInvite3Vectors,
 * computed in Python), with rcinv2's fields and the name k3f.example.com.
 */
class Rcinv3Test {

    private val v4 = "RCINV3:AMM4WADRA4HWWM3GFZSXQYLNOBWGKLTDN5WQAAICAMCAKBQHBAEQUCYMBUHA6AAAQZGAAAAGKQQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D6VMTBOFQBCAAZ2QKDIVDUSS2NJ2AIFBEGRCFIZDUQSKKJNGE2TSPKBIVEU2UKVLFOWCZLJNVYXK6L4AAPPCVMBQWEY3EMVTGO2DJNJVWY3LON5YHC4TTOR2XM53YPF5HW7D5PZ7WSVYKQA"
    private val v6 = "RCINV3:AMNCAAINXAAAAAAAAAAAAAAAAAAAOD3LGNTC4ZLYMFWXA3DFFZRW63IAAEBAGBAFAYDQQCIKBMGA2DQPAAAIMTAAAADFIIBBEIRSIJJGE4UCSKRLFQWS4LZQGEZDGNBVGY3TQOJ2HM6D2PR7KWJQXCYARAAM5IFBUKR2JJNGU5AECQSDIRCUMR2IJFFEWTCNJZHVAUKSKNKFKVSXLBMVUW24LVPF6AAHXRKWAYLCMNSGKZTHNBUWU23MNVXG64DROJZXI5LWO54HS6T3PR6X473JK4FIA"
    private val hostName = "RCINV3:AMKBA3TPMRSS4ZLYMFWXA3DFFZRW63IAAEBAGBAFAYDQQCIKBMGA2DQPAAAIMTAAAADFIIBBEIRSIJJGE4UCSKRLFQWS4LZQGEZDGNBVGY3TQOJ2HM6D2PR7KWJQXCYARAAM5IFBUKR2JJNGU5AECQSDIRCUMR2IJFFEWTCNJZHVAUKSKNKFKVSXLBMVUW24LVPF6AAHXRKWAYLCMNSGKZTHNBUWU23MNVXG64DROJZXI5LWO54HS6T3PR6X473JK4FIA"
    private val bare = "RCINV3:AMAMWADRA4AACAQDAQCQMBYIBEFAWDANBYHQAAEGJQAAABSUEAQSEIZEEUTCOKBJFIVSYLJOF4YDCMRTGQ2TMNZYHE5DWPB5HY7VLEYLRMAIQAGOUCQ2FI5EUWTKOQCBIJBUIRKGI5EESSSLJRGU4T2QKFJFGVCVKZLVQWK2LNOF2XS7AAD3YVLAMFRGGZDFMZTWQ2LKNNWG23TPOBYXE43UOV3HO6DZPJ5XY7L6P5UVOCUA"

    private fun bytes(from: Int, n: Int) = (from until from + n).joinToString("") { "%02x".format(it) }

    private data class Want(val text: String, val host: String, val pin: Boolean, val name: String?, val v3: Boolean)

    @Test
    fun theSpecsVectors() {
        for (w in listOf(
            Want(v4, "203.0.113.7", true, "k3f.example.com", true),
            Want(v6, "2001:db8::7", false, "k3f.example.com", true),
            Want(hostName, "node.example.com", false, null, true),
            Want(bare, "203.0.113.7", false, null, false),
        )) {
            val invite = RayImport.parseInvite("Invite:\n${w.text}\n")
            assertEquals(w.host, invite.host)
            assertEquals(w.pin, invite.pinRequired)
            assertEquals(w.name, invite.dnsName)
            // the host is the name's fallback (names-v1 §3)
            assertEquals(if (w.name != null) listOf(w.host) else emptyList<String>(), invite.addrs)
            assertEquals(RaySpaParams(bytes(0x00, 16), 34380, 1620, bytes(0x20, 32), 21907, 2955, 136, 206), invite.spa)
            assertEquals(bytes(0xa0, 8), invite.inviteId)
            assertEquals(bytes(0x40, 32), invite.inviteKey)
            assertEquals(listOf(RayService(7, "api", "api")), invite.services)
            val apiPin = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (0x60 + it).toByte() })
            assertEquals(RayApiInfo(48213, apiPin), invite.api)
            assertEquals(1767312000L, invite.expires)
            // bit 4: the node takes SPA v3 (spa-v3 §5)
            assertEquals(if (w.v3) listOf(2, 3) else listOf(2), invite.spaVersions)
        }
        assertEquals("k3f.example.com", RayImport.parseInvite(v4.lowercase()).dnsName)
        assertTrue(RayImport.matches("x $v4 y") && RayImport.isInvite(v4))
        assertEquals(v4, RayImport.token("Invite: $v4\n"))
    }

    @Test
    fun whatTheReaderRefuses() {
        val raw = decode(v4)
        val name = decode(hostName)
        for ((why, bad) in listOf(
            "cut short" to raw.copyOf(raw.size - 5),
            "bytes after expires" to raw + byteArrayOf(0),
            "version 2" to raw.copyOf().also { it[0] = 2 },
            "an unknown flag" to raw.copyOf().also { it[1] = (it[1].toInt() or 0x20).toByte() },
            // bit 3 beside a host name: flags of hostName with the name bit, its name read after
            "a node name beside a host name" to name.copyOf().also { it[1] = (it[1].toInt() or 0x08).toByte() }.let {
                it.copyOfRange(0, 2 + 1 + 16) + byteArrayOf(15) + "k3f.example.com".toByteArray() + it.copyOfRange(2 + 1 + 16, it.size)
            },
            // a name whose last label is digits reads as an address
            "a name that reads as an address" to withName(raw, "198.51.100.99"),
            "a name with an empty label" to withName(raw, "k3f..com"),
            "a name of one label" to withName(raw, "k3f"),
        )) {
            try {
                Rcinv3.parse("RCINV3:" + encode(bad))
                fail("accepted: $why")
            } catch (_: IllegalArgumentException) {
            }
        }
        // rcinv2's reader takes no version 3, under either prefix
        for (bad in listOf("RCINV2:" + v4.substringAfter(':'), "RCINV3:" + decode(bare).let { it[0] = 2; encode(it) })) {
            try {
                RayImport.parseInvite(bad)
                fail("accepted $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
        // the vector itself still reads with another valid name
        assertEquals("abc.example.org", Rcinv3.parse("RCINV3:" + encode(withName(raw, "abc.example.org"))).dnsName)
    }

    /** [raw] (an IPv4 rcinv3 with a name) with its name replaced. */
    private fun withName(raw: ByteArray, name: String): ByteArray {
        val at = 2 + 4
        val old = raw[at].toInt() and 0xff
        return raw.copyOfRange(0, at) + byteArrayOf(name.length.toByte()) + name.toByteArray() + raw.copyOfRange(at + 1 + old, raw.size)
    }

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun decode(token: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in token.substringAfter(':')) {
            buffer = (buffer shl 5) or alphabet.indexOf(c)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }

    private fun encode(raw: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in raw) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(alphabet[(buffer shr bits) and 0x1f])
            }
        }
        if (bits > 0) sb.append(alphabet[(buffer shl (5 - bits)) and 0x1f])
        return sb.toString()
    }
}
