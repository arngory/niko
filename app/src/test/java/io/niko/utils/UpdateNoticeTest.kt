package io.niko.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** UPDATE.json (client-min-v1 §6) as the app reads it, after the signature of SHA256SUMS holds. */
class UpdateNoticeTest {

    private fun parse(json: String) = UpdateNotice.parse(json.toByteArray())

    @Test
    fun readsTheRelease() {
        val notice = parse(
            """{"v": 1, "mandatory": false, "min_supported": "1.59.0", "message": {"en": "Nodes refuse 1.58", "ru": " Ноды отказывают 1.58 "}}"""
        )!!
        assertFalse(notice.mandatory)
        assertEquals("1.59.0", notice.minSupported)
        assertEquals("Ноды отказывают 1.58", notice.message("ru"))
        // a language it has no text in: English
        assertEquals("Nodes refuse 1.58", notice.message("de"))
        // below min_supported: no "later"; at it and above, and a preview of it below
        assertTrue(notice.required("1.58.0"))
        assertTrue(notice.required("1.59.0-pre.20261120.1"))
        assertFalse(notice.required("1.59.0"))
        assertFalse(notice.required("1.60.0"))
        // mandatory: whatever the version
        assertTrue(parse("""{"v": 1, "mandatory": true}""")!!.required("9.0.0"))
        assertNull(parse("""{"v": 1, "mandatory": true}""")!!.message("en"))
    }

    @Test
    fun whatIsNotOne() {
        assertNull(parse("""{"v": 2, "mandatory": true}"""))
        assertNull(parse("""{"v": 1}"""))
        assertNull(parse("""{"v": 1, "mandatory": "yes"}"""))
        assertNull(parse("""{"v": 1, "mandatory": false, "min_supported": "1.59"}"""))
        assertNull(parse("""{"v": 1, "mandatory": false, "min_supported": 159}"""))
        assertNull(parse("""[1]"""))
        assertNull(parse("not json"))
        assertNull(UpdateNotice.parse(ByteArray(UpdateNotice.MAX_BYTES + 1) { ' '.code.toByte() }))
        // what of the message does not read is left out, the rest stays
        val odd = parse("""{"v": 1, "mandatory": false, "message": {"EN": "x", "ru": "", "en": "ok", "de": 5}}""")!!
        assertEquals(mapOf("en" to "ok"), odd.message)
        // fields of a later version the app does not know are no reason to refuse
        assertFalse(parse("""{"v": 1, "mandatory": false, "later": true}""")!!.mandatory)
    }

    // as update-json.py counts: 1000 code points, an emoji one of them, never half a surrogate pair
    @Test
    fun theMessageCutByCodePoints() {
        val emoji = "\uD83D\uDE00"
        fun message(text: String) =
            UpdateNotice.parse(noticeJson(text).toByteArray())!!.message("en")!!
        // 600 emoji pass the CI check (600 code points, 1200 UTF-16 units): whole
        assertEquals(emoji.repeat(600), message(emoji.repeat(600)))
        // 1001 of them: the first 1000, the last one whole
        val cut = message(emoji.repeat(1001))
        assertEquals(1000, cut.codePointCount(0, cut.length))
        assertEquals(emoji.repeat(1000), cut)
    }

    private fun noticeJson(text: String) = org.json.JSONObject()
        .put("v", 1).put("mandatory", false).put("message", org.json.JSONObject().put("en", text)).toString()

    @Test
    fun onlyAsTheSignedListHasIt() {
        val bytes = """{"v": 1, "mandatory": true, "message": {"en": "now", "ru": "сейчас"}}""".toByteArray()
        val listed = mapOf("Niko-1.59.0-arm64-v8a.apk" to "00".repeat(32), UpdateNotice.NAME to Updates.sha256(bytes))
        assertTrue(Updates.checkedNotice(listed, bytes)!!.mandatory)
        // not listed: a release without one, whatever the file says
        assertNull(Updates.checkedNotice(listed - UpdateNotice.NAME, bytes))
        // listed with another hash: refused
        try {
            Updates.checkedNotice(listed, bytes + ' '.code.toByte())
            fail("a changed UPDATE.json was taken")
        } catch (e: Updates.Refused) {
            assertEquals(io.niko.R.string.update_bad_notice, e.reason)
        }
        // sha256sum's lines with it among the APKs, as release-sign.sh writes them
        val sums = Updates.sums("${"ab".repeat(32)}  Niko-1.59.0-x86.apk\n${"cd".repeat(32)}  UPDATE.json\n")
        assertEquals("cd".repeat(32), sums[UpdateNotice.NAME])
        assertEquals("Niko-1.59.0-x86.apk", Updates.apkFor(sums, listOf("x86"))?.first)
    }
}
